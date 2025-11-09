# BLE Message Format Specification

## Overview
Each BLE message is exactly **20 bytes** (160 bits), split into a 12-byte header and an 8-byte payload.

```
[12-byte Header] [8-byte Payload]
     ↓                  ↓
 Fixed format    Type-dependent content
```

---

## Header (12 bytes)

All header fields use **big-endian** byte order.

| Field        | Bytes | Type       | Description                                    |
|--------------|-------|------------|------------------------------------------------|
| `message_id` | 0-3   | uint32     | Unique message identifier (for deduplication)  |
| `sender_id`  | 4-7   | uint32     | Device identifier (persistent per device)      |
| `timestamp`  | 8-11  | uint32     | Unix timestamp in seconds                      |

### Example Header
```
Message ID: 0x12345678
Sender ID:  0xABCDEF00
Timestamp:  0x65A1B2C3 (1705099971 = Jan 12, 2024)

Bytes: 12 34 56 78 AB CD EF 00 65 A1 B2 C3
```

---

## Payload (8 bytes)

Format: `[1 byte type] [7 bytes body]`

| Byte | Purpose                  |
|------|--------------------------|
| 0    | Type (1, 2, 3, 4, or 5)  |
| 1-7  | Type-specific data       |

---

## Type 1: Location (GPS Coordinates)

**Format:** 7 bytes = 56 bits = 28-bit latitude + 28-bit longitude

### Encoding

1. **Map latitude** `[-90, +90]` → `[0, 2²⁸-1]`
   ```
   lat_u = ((lat + 90.0) / 180.0) × (2²⁸ - 1)
   ```

2. **Map longitude** `[-180, +180]` → `[0, 2²⁸-1]`
   ```
   lon_u = ((lon + 180.0) / 360.0) × (2²⁸ - 1)
   ```

3. **Pack into 56 bits** (big-endian):
   ```
   val56 = (lat_u << 28) | lon_u
   ```

### Resolution
- Latitude: ~7.5 cm
- Longitude: ~15 cm at equator

### Example
```
Location: 40.7128° N, -74.0060° W (New York City)

lat_u = ((40.7128 + 90) / 180) × (2²⁸-1) = 195,123,456
lon_u = ((-74.0060 + 180) / 360) × (2²⁸-1) = 79,123,456

Payload bytes:
[01] [0B] [A3] [C4] [E5] [04] [B7] [A0]
 ↑    └─────────56 bits (7 bytes)───────┘
type
```

---

## Type 2: Questionnaire (Yes/No Answers)

**Format:** 7 bytes = up to 7 yes/no answers

### Encoding

Each byte represents one answer:
- `0x00` = No
- `0x01` = Yes
- `0xFF` = Unknown/No answer

If fewer than 7 questions, pad remaining bytes with `0xFF`.

### Example
```
Questions: [Yes, No, Yes, Yes, No]

Payload bytes:
[02] [01] [00] [01] [01] [00] [FF] [FF]
 ↑    └────────7 answers─────────────┘
type  1    0    1    1    0   (pad)(pad)
```

---

## Type 3: Battery Status

**Format:** 2 bytes ASCII percentage + 5 bytes ASCII seconds

### Encoding

| Bytes | Field              | Format              | Range         |
|-------|--------------------|---------------------|---------------|
| 1-2   | Battery percentage | ASCII digits "00"-"99" | 0-99%      |
| 3-7   | Seconds remaining  | ASCII digits "00000"-"99999" | 0-99999s |

**Note:** 
- If battery is 100%, send "99" (or use "**" as special marker)
- Max time: 99999 seconds ≈ 27.7 hours
- If unknown, use "00000" or "99999" as sentinel

### Example
```
Battery: 85%, 7200 seconds left (2 hours)

Payload bytes:
[03] [38] [35] [30] [37] [32] [30] [30]
 ↑    '8'  '5'  '0'  '7'  '2'  '0'  '0'
type  └─85%─┘  └──────07200 sec──────┘
```

---

## Type 4: Message (Text/Data Chunk)

**Format:** 7 raw bytes (arbitrary data)

### Encoding

Simply copy 7 bytes of data (UTF-8 text, binary, etc.)

For messages longer than 7 bytes, send multiple Type-4 payloads. Receiver reassembles by grouping messages with same `(sender_id, message_id)`.

### Example
```
Text: "Hello!!" (7 bytes UTF-8)

Payload bytes:
[04] [48] [65] [6C] [6C] [6F] [21] [21]
 ↑    'H'  'e'  'l'  'l'  'o'  '!'  '!'
type  └──────────7 bytes text──────────┘
```

---

## Type 5: BEEP (Alert/Notification)

**Format:** 7 bytes, all zeros

### Encoding

Simple alert signal. All 7 data bytes are `0x00`.

Future extensions can use these 7 bytes for duration, volume, pattern, etc.

### Example
```
Payload bytes:
[05] [00] [00] [00] [00] [00] [00] [00]
 ↑    └─────────7 zero bytes─────────┘
type
```

---

## Complete Message Example

**Sending:** Location (NYC) from device `0xDEADBEEF` at timestamp `1705099971`

```
Header (12 bytes):
message_id: 0x12345678
sender_id:  0xDEADBEEF  
timestamp:  0x65A1B2C3

Payload (8 bytes):
type: 0x01 (Location)
body: 0x0BA3C4E504B7A0 (40.7128°N, -74.0060°W)

Full 20-byte message:
12 34 56 78 DE AD BE EF 65 A1 B2 C3 01 0B A3 C4 E5 04 B7 A0
└────header (12)────────────────────┘ └──payload (8)────────┘
```

---

## Mesh Network Behavior

### Message Propagation
1. Device A broadcasts message with unique `message_id`
2. Device B receives and checks `message_id`:
   - **First time?** → Display + rebroadcast
   - **Seen before?** → Rebroadcast only (max 3 times total)
3. Device C receives from B → same logic
4. Messages propagate through network up to 3 hops

### Deduplication
- Uses first 4 bytes (`message_id`) for fast lookup
- Prevents duplicate display on screen
- Allows controlled rebroadcasting for mesh propagation

---

## Implementation Notes

### Big-Endian Encoding
All multi-byte integers use big-endian (network byte order):
```kotlin
fun put32BE(dst: ByteArray, offset: Int, value: Int) {
    dst[offset]   = (value ushr 24).toByte()
    dst[offset+1] = (value ushr 16).toByte()
    dst[offset+2] = (value ushr 8).toByte()
    dst[offset+3] = value.toByte()
}
```

### 56-bit Packing (Type 1)
```kotlin
fun put56BE(dst: ByteArray, offset: Int, value: Long) {
    require(value ushr 56 == 0L)
    for (i in 0 until 7) {
        dst[offset + i] = ((value ushr (8 * (6 - i))) and 0xFF).toByte()
    }
}
```

### Why This Format?

✅ **Fixed size** (20 bytes) → Perfect for BLE advertising  
✅ **Simple parsing** → Fixed offsets, no variable length  
✅ **Efficient** → No wasted bytes, optimal encoding  
✅ **Extensible** → Room for new types (6-255)  
✅ **Human-readable** → ASCII in Type 3 for debugging  

---

## Usage in App

### Sending Messages

```kotlin
// Text message
"Hello!!" → Type 4

// Location
"loc:40.7128,-74.0060" → Type 1

// Battery
"bat:85,7200" → Type 3

// Survey
"survey:1010100" → Type 2
```

### Receiving Messages

App automatically:
1. Scans for BLE advertisements with service UUID
2. Parses 20-byte messages
3. Deduplicates using `message_id`
4. Displays new messages
5. Rebroadcasts (max 3 times)
6. Creates mesh network effect

---

## Future Extensions

### Type 3 Alternative (Binary Seconds)
If you need more range for battery time:
- Bytes 3-7: 5-byte big-endian uint (instead of ASCII)
- Range: 0 to 1,099,511,627,775 seconds ≈ 34,000 years
- No schema size change needed

### Additional Types
- Type 6-255: Reserved for future use
- 7 bytes available for each new type

---

**Version:** 1.0  
**Last Updated:** 2025-01-08

