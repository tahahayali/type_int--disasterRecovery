#!/usr/bin/env python3
"""Generate correct 20-byte hex messages for Buffalo location data"""

import struct

def encode_location(lat, lon):
    """Encode lat/lon to 7-byte payload (56 bits total)"""
    # Map lat [-90,+90] to [0, 2^28-1]
    lat_u = int(((lat + 90.0) / 180.0) * ((1 << 28) - 1))
    # Map lon [-180,+180] to [0, 2^28-1]  
    lon_u = int(((lon + 180.0) / 360.0) * ((1 << 28) - 1))
    # Combine to 56 bits: upper 28 bits = lat, lower 28 bits = lon
    value = (lat_u << 28) | lon_u
    
    # Convert to 7 bytes big-endian
    result = []
    for i in range(6, -1, -1):
        result.append((value >> (8 * i)) & 0xFF)
    return bytes(result)

def build_message(msg_id, sender_id, timestamp, payload_type, payload_data):
    """Build 20-byte message: 4B msg_id + 4B sender + 4B timestamp + 1B type + 7B data"""
    # Header: 12 bytes
    msg = struct.pack('>III', msg_id, sender_id, timestamp)
    # Payload: 8 bytes (1 type + 7 data)
    msg += bytes([payload_type])
    msg += payload_data[:7]  # Ensure exactly 7 bytes
    
    assert len(msg) == 20, f"Message must be 20 bytes, got {len(msg)}"
    return msg.hex()

# Buffalo, NY coordinates
buffalo_coords = [
    (42.8864, -78.8784),  # Downtown Buffalo
    (42.8850, -78.8800),  # Slightly south
    (42.8880, -78.8770),  # Slightly north
]

messages = []

# Message 1: Victim 0001 (sender_id=1)
loc1 = encode_location(buffalo_coords[0][0], buffalo_coords[0][1])
msg1 = build_message(1, 1, 0x6747a000, 1, loc1)
messages.append(msg1)
print(f"Message 1 (Victim 0001): {msg1} ({len(bytes.fromhex(msg1))} bytes)")

# Message 2: Victim 0002 (sender_id=2)
loc2 = encode_location(buffalo_coords[1][0], buffalo_coords[1][1])
msg2 = build_message(2, 2, 0x6747a002, 1, loc2)
messages.append(msg2)
print(f"Message 2 (Victim 0002): {msg2} ({len(bytes.fromhex(msg2))} bytes)")

# Message 3: First Responder 1001 (sender_id=1001)
loc3 = encode_location(buffalo_coords[2][0], buffalo_coords[2][1])
msg3 = build_message(3, 1001, 0x6747a004, 1, loc3)
messages.append(msg3)
print(f"Message 3 (First Responder 1001): {msg3} ({len(bytes.fromhex(msg3))} bytes)")

# Generate curl command
print("\n" + "="*80)
print("Copy and paste this curl command:")
print("="*80)
print(f"""
curl -X POST http://localhost:5001/api/byte_string \\
  -H "Content-Type: application/json" \\
  -d '{{"messages": ["{messages[0]}", "{messages[1]}", "{messages[2]}"]}}'
""")

# Also generate battery, questionnaire, and message data
print("\n" + "="*80)
print("Additional data (Battery, Questionnaire, Messages):")
print("="*80)

# Battery messages
battery1 = build_message(4, 1, 0x6747a006, 3, b'2503600')  # 25%, 3600 seconds
battery2 = build_message(5, 2, 0x6747a008, 3, b'7518000')  # 75%, 18000 seconds
print(f"\nBattery data:")
print(f"""
curl -X POST http://localhost:5001/api/byte_string \\
  -H "Content-Type: application/json" \\
  -d '{{"messages": ["{battery1}", "{battery2}"]}}'
""")

# Questionnaire messages (7 bytes: 0x00=No, 0x01=Yes)
q1 = bytes([0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00])  # Injured, Trapped, Medical Emergency
q2 = bytes([0x00, 0x00, 0x01, 0x00, 0x00, 0x01, 0x00])  # Need water, need shelter
quest1 = build_message(6, 1, 0x6747a00a, 2, q1)
quest2 = build_message(7, 2, 0x6747a00c, 2, q2)
print(f"\nQuestionnaire data:")
print(f"""
curl -X POST http://localhost:5001/api/byte_string \\
  -H "Content-Type: application/json" \\
  -d '{{"messages": ["{quest1}", "{quest2}"]}}'
""")

# Text messages (7 bytes max)
text1 = b'HELP!!\x00\x00'  # Pad to 7 bytes
text2 = b'Need fd'  # 7 bytes
msg_text1 = build_message(8, 1, 0x6747a00e, 4, text1)
msg_text2 = build_message(9, 2, 0x6747a010, 4, text2)
print(f"\nText messages:")
print(f"""
curl -X POST http://localhost:5001/api/byte_string \\
  -H "Content-Type: application/json" \\
  -d '{{"messages": ["{msg_text1}", "{msg_text2}"]}}'
""")

print("\n" + "="*80)
print("✅ All messages generated! Run the commands above in order.")
print("="*80)

