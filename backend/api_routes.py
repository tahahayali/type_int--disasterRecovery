from flask import request, jsonify, Blueprint, send_from_directory, current_app
from datetime import datetime
import os
import threading
import struct
import base64
from database_manager import get_latest_locations, get_db_stats

# Create a blueprint for API routes
api = Blueprint('api', __name__)


@api.before_request
def check_db_connection():
    """Ensure that the MongoDB collection is available for routes that require it."""
    if current_app.config['DB_COLLECTION'] is None and request.path.startswith('/api/locations'):
        # For data retrieval, we can still serve the buffer but warn
        pass
    elif current_app.config['DB_COLLECTION'] is None and request.path.startswith('/api/stats'):
        return jsonify({"error": "MongoDB not connected"}), 500


@api.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    collection = current_app.config['DB_COLLECTION']
    buffer = current_app.config['PHONE_BUFFER']
    return jsonify({
        "status": "healthy",
        "mongodb_connected": collection is not None,
        "buffered_phones": len(buffer) if buffer is not None else 0
    }), 200


@api.route('/signup', methods=['POST'])
def signup():
    """User signup endpoint - creates a new user record in the database."""
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({"error": "No data provided"}), 400
        
        # Extract required fields
        uuid = data.get("uuid")
        user_type = data.get("type")
        name = data.get("name")
        age = data.get("age")
        height = data.get("height")
        weight = data.get("weight")
        medical = data.get("medical")
        
        # Validate required fields
        if not uuid or not user_type or not name:
            return jsonify({"error": "uuid, type, and name are required"}), 400
        
        # Validate uuid is 4 characters
        if len(uuid) != 4:
            return jsonify({"error": "uuid must be exactly 4 characters"}), 400
        
        # Validate type
        if user_type not in ["victim", "first_responder"]:
            return jsonify({"error": "type must be either 'victim' or 'first_responder'"}), 400
        
        # Get database reference
        db = current_app.config['DB']
        if db is None:
            return jsonify({"error": "Database not connected"}), 500
        
        users_collection = db["users"]
        
        # Check if user already exists
        existing_user = users_collection.find_one({"uuid": uuid})
        
        current_time = datetime.utcnow().isoformat()
        
        if existing_user:
            # User exists - update their profile information
            update_fields = {
                "type": user_type,
                "name": name,
                "updated_at": current_time
            }
            
            # Only update optional fields if provided
            if age is not None:
                update_fields["age"] = age
            if height is not None:
                update_fields["height"] = height
            if weight is not None:
                update_fields["weight"] = weight
            if medical is not None:
                update_fields["medical"] = medical
            
            # Update the existing user
            users_collection.update_one(
                {"uuid": uuid},
                {"$set": update_fields}
            )
            
            # Fetch updated user
            updated_user = users_collection.find_one({"uuid": uuid})
            updated_user.pop('_id', None)
            
            return jsonify({
                "status": "success",
                "message": "User profile updated successfully",
                "user": updated_user
            }), 200
        
        else:
            # Create new user document with skeleton structure
            user_document = {
                "uuid": uuid,
                "type": user_type,
                "name": name,
                "age": age if age is not None else None,
                "height": height if height is not None else None,
                "weight": weight if weight is not None else None,
                "medical": medical if medical is not None else None,
                "location": {
                    "lat": None,
                    "long": None,
                    "last_updated": None
                },
                "battery": {
                    "percentage": None,
                    "time_left_till_off": None,
                    "last_updated": None
                },
                "messages": [],
                "emergency_questionaire": None,
                "created_at": current_time,
                "updated_at": current_time
            }
            
            # Insert the user into the database
            result = users_collection.insert_one(user_document)
            
            # Remove the MongoDB _id from the response
            user_document.pop('_id', None)
            
            return jsonify({
                "status": "success",
                "message": "User registered successfully",
                "user": user_document
            }), 201
        
    except Exception as e:
        current_app.logger.error(f"Error during signup: {e}")
        return jsonify({"error": str(e)}), 500


def parse_message_bytes(message_bytes):
    """
    Parse a 20-byte message into its components.
    
    Format:
    - Bytes 0-3: message_id (4B uint, big-endian)
    - Bytes 4-7: sender_id (4B uint, big-endian)
    - Bytes 8-11: timestamp (4B uint, big-endian, Unix seconds)
    - Byte 12: payload_type (1B)
    - Bytes 13-19: payload_data (7B)
    
    Returns: dict with parsed fields
    """
    if len(message_bytes) != 20:
        raise ValueError(f"Message must be exactly 20 bytes, got {len(message_bytes)}")
    
    # Parse header (12 bytes)
    message_id = struct.unpack('>I', message_bytes[0:4])[0]
    sender_id = struct.unpack('>I', message_bytes[4:8])[0]
    timestamp = struct.unpack('>I', message_bytes[8:12])[0]
    
    # Parse payload (8 bytes)
    payload_type = message_bytes[12]
    payload_data = message_bytes[13:20]
    
    return {
        'message_id': message_id,
        'sender_id': sender_id,
        'timestamp': timestamp,
        'payload_type': payload_type,
        'payload_data': payload_data
    }


def parse_type1_location(payload_data):
    """
    Parse Type 1 payload: Location (7 bytes)
    Format: 28 bits lat + 28 bits lon (56 bits total)
    """
    # Read 7 bytes as a 56-bit big-endian integer
    value = 0
    for i in range(7):
        value = (value << 8) | payload_data[i]
    
    # Extract lat (upper 28 bits) and lon (lower 28 bits)
    lat_u = (value >> 28) & ((1 << 28) - 1)
    lon_u = value & ((1 << 28) - 1)
    
    # Convert to actual coordinates
    lat = (lat_u / ((1 << 28) - 1)) * 180.0 - 90.0
    lon = (lon_u / ((1 << 28) - 1)) * 360.0 - 180.0
    
    # Debug logging
    print(f"DEBUG Location: value={hex(value)}, lat_u={lat_u}, lon_u={lon_u}, lat={lat:.6f}, lon={lon:.6f}")
    
    return {'lat': lat, 'long': lon}


def parse_type2_questionnaire(payload_data):
    """
    Parse Type 2 payload: Questionnaire (7 bytes)
    Format: 7 yes/no answers (1 byte each: 0x00=No, 0x01=Yes, 0xFF=Unknown)
    """
    answers = []
    for i in range(7):
        byte_val = payload_data[i]
        if byte_val == 0x00:
            answers.append('0')
        elif byte_val == 0x01:
            answers.append('1')
        else:
            answers.append('0')  # Treat unknown as 0
    
    return ''.join(answers)


def parse_type3_battery(payload_data):
    """
    Parse Type 3 payload: Battery (7 bytes)
    Format: 2 bytes ASCII percentage + 5 bytes ASCII seconds left
    """
    # Parse percentage (2 ASCII digits)
    percentage_str = payload_data[0:2].decode('ascii', errors='ignore')
    try:
        percentage = int(percentage_str)
    except ValueError:
        percentage = 0
    
    # Parse seconds left (5 ASCII digits)
    seconds_str = payload_data[2:7].decode('ascii', errors='ignore')
    try:
        time_left_till_off = int(seconds_str)
    except ValueError:
        time_left_till_off = 0
    
    return {
        'percentage': percentage,
        'time_left_till_off': time_left_till_off
    }


def parse_type4_message(payload_data):
    """
    Parse Type 4 payload: Message (7 bytes)
    Format: Raw 7 bytes of text/data
    """
    # Try to decode as UTF-8, fall back to latin-1
    try:
        message_text = payload_data.decode('utf-8', errors='ignore').rstrip('\x00')
    except:
        message_text = payload_data.decode('latin-1', errors='ignore').rstrip('\x00')
    
    return message_text


@api.route('/byte_string', methods=['POST'])
def receive_byte_string():
    """
    Receive and process 20-byte message strings.
    Updates user records based on message type.
    """
    try:
        data = request.get_json()
        
        if not data or 'messages' not in data:
            return jsonify({"error": "No messages provided"}), 400
        
        messages = data.get('messages', [])
        
        if not isinstance(messages, list):
            return jsonify({"error": "messages must be a list"}), 400
        
        db = current_app.config['DB']
        if db is None:
            return jsonify({"error": "Database not connected"}), 500
        
        users_collection = db["users"]
        processed_count = 0
        errors = []
        
        for idx, message_str in enumerate(messages):
            try:
                # DEBUG: Log raw message
                current_app.logger.info(f"Received message {idx}: {message_str[:40]}...")
                
                # Decode the message (assuming hex encoding)
                try:
                    message_bytes = bytes.fromhex(message_str)
                except ValueError:
                    # Try base64 if hex fails
                    try:
                        message_bytes = base64.b64decode(message_str)
                    except:
                        errors.append(f"Message {idx}: Invalid encoding")
                        current_app.logger.error(f"Message {idx}: Invalid encoding - {message_str[:40]}")
                        continue
                
                # Parse the message
                parsed = parse_message_bytes(message_bytes)
                
                # Convert sender_id to UUID string
                # The sender_id is a 4-byte value that represents either:
                # 1. A 4-character ASCII string (like "8U4A", "MPHH")
                # 2. A numeric ID (like 1, 2, 1001) that should be zero-padded
                
                sender_id_int = parsed['sender_id']
                
                # Try to decode as ASCII first (for alphanumeric UUIDs)
                try:
                    sender_id_bytes = sender_id_int.to_bytes(4, byteorder='big')
                    current_app.logger.debug(f"Sender ID bytes: {sender_id_bytes.hex()} = {sender_id_bytes}")
                    # Try to decode as ASCII/UTF-8 and strip null bytes
                    sender_uuid = sender_id_bytes.decode('ascii').rstrip('\x00')
                    current_app.logger.debug(f"Decoded ASCII: '{sender_uuid}' (printable: {sender_uuid.isprintable() if sender_uuid else False})")
                    # If it's empty or contains non-printable chars, fall back to numeric
                    if not sender_uuid or not sender_uuid.isprintable() or len(sender_uuid) > 4:
                        raise ValueError("Not a valid ASCII UUID")
                    current_app.logger.info(f"Using ASCII UUID: '{sender_uuid}'")
                except (ValueError, UnicodeDecodeError) as e:
                    # Fall back to zero-padded numeric format
                    sender_uuid = f"{sender_id_int:04d}"
                    current_app.logger.info(f"Falling back to numeric UUID: '{sender_uuid}' (reason: {e})")
                
                payload_type = parsed['payload_type']
                timestamp_iso = datetime.utcfromtimestamp(parsed['timestamp']).isoformat()
                
                # DEBUG: Log what we're looking for
                current_app.logger.info(f"Processing message {idx}: sender_id={sender_id_int}, sender_uuid='{sender_uuid}', type={payload_type}")
                
                # Find user by UUID
                user = users_collection.find_one({"uuid": sender_uuid})
                if not user:
                    current_app.logger.warning(f"User with uuid '{sender_uuid}' not found (sender_id={parsed['sender_id']})")
                    errors.append(f"Message {idx}: User with uuid {sender_uuid} not found")
                    continue
                
                current_app.logger.info(f"Found user: {user.get('name')} ({sender_uuid})")
                
                # Update based on payload type
                update_fields = {"updated_at": datetime.utcnow().isoformat()}
                
                if payload_type == 1:
                    # Location update
                    location = parse_type1_location(parsed['payload_data'])
                    update_fields['location'] = {
                        'lat': location['lat'],
                        'long': location['long'],
                        'last_updated': timestamp_iso
                    }
                
                elif payload_type == 2:
                    # Questionnaire update
                    questionnaire = parse_type2_questionnaire(parsed['payload_data'])
                    update_fields['emergency_questionaire'] = questionnaire
                
                elif payload_type == 3:
                    # Battery update
                    battery = parse_type3_battery(parsed['payload_data'])
                    update_fields['battery'] = {
                        'percentage': battery['percentage'],
                        'time_left_till_off': battery['time_left_till_off'],
                        'last_updated': timestamp_iso
                    }
                
                elif payload_type == 4:
                    # Message update - append to messages array (with deduplication)
                    message_text = parse_type4_message(parsed['payload_data'])
                    message_id = parsed['message_id']
                    
                    # Check if this message_id already exists
                    existing_msg = users_collection.find_one(
                        {"uuid": sender_uuid, "messages.message_id": message_id}
                    )
                    
                    if existing_msg:
                        current_app.logger.info(f"Duplicate message {message_id} for user {sender_uuid}, skipping")
                        processed_count += 1
                        continue
                    
                    message_obj = {
                        'message_id': message_id,
                        'time': timestamp_iso,
                        'message': message_text
                    }
                    
                    # Use $push to append to messages array
                    users_collection.update_one(
                        {"uuid": sender_uuid},
                        {
                            "$push": {"messages": message_obj},
                            "$set": {"updated_at": datetime.utcnow().isoformat()}
                        }
                    )
                    processed_count += 1
                    continue  # Skip the regular update below
                
                elif payload_type == 5:
                    # BEEP - could log or handle specially, for now just track it
                    # Don't update any specific field, just the updated_at
                    pass
                
                else:
                    errors.append(f"Message {idx}: Unknown payload type {payload_type}")
                    continue
                
                # Perform the update for types 1, 2, 3, 5
                result = users_collection.update_one(
                    {"uuid": sender_uuid},
                    {"$set": update_fields}
                )
                current_app.logger.info(f"Update result: matched={result.matched_count}, modified={result.modified_count}, fields={list(update_fields.keys())}")
                processed_count += 1
                
            except Exception as e:
                errors.append(f"Message {idx}: {str(e)}")
                continue
        
        response = {
            "status": "success",
            "processed": processed_count,
            "total": len(messages)
        }
        
        if errors:
            response['errors'] = errors
        
        return jsonify(response), 200
        
    except Exception as e:
        current_app.logger.error(f"Error processing byte_string: {e}")
        return jsonify({"error": str(e)}), 500


@api.route('/phone-data', methods=['POST'])
def receive_phone_data():
    """Receive phone data from mobile devices (including location and questionnaire)."""
    try:
        data = request.get_json()

        if not data:
            return jsonify({"error": "No data provided"}), 400

        # Extract required fields
        phone_id = data.get("phone_id")
        latitude = data.get("latitude")
        longitude = data.get("longitude")

        if not phone_id or latitude is None or longitude is None:
            return jsonify({"error": "phone_id, latitude, and longitude are required"}), 400

        buffer = current_app.config['PHONE_BUFFER']

        # Store in buffer (will be persisted to DB every 5 minutes)
        # Note: We store the full received JSON data along with required fields
        buffer[phone_id] = {
            "phone_id": phone_id,
            "latitude": float(latitude),
            "longitude": float(longitude),
            "accuracy": data.get("accuracy", 0),
            "battery_percentage": data.get("battery_percentage", 0),
            "type": data.get("type", "victim"),
            # Include optional questionnaire data
            "questionnaire_data": data.get("questionnaire_data", {}),
            "timestamp": datetime.utcnow(),
        }

        return jsonify({
            "status": "success",
            "message": "Phone data received and buffered",
            "phone_id": phone_id,
            "buffered_count": len(buffer)
        }), 202  # Use 202 Accepted since the data is buffered, not instantly persisted

    except Exception as e:
        # Log the exception for debugging
        current_app.logger.error(f"Error processing phone data: {e}")
        return jsonify({"error": str(e)}), 500


@api.route('/locations', methods=['GET'])
def get_locations():
    """Get all latest victim and first responder locations from the database and buffer."""
    try:
        hours = request.args.get('hours', 24, type=int)
        limit = request.args.get('limit', 1000, type=int)

        collection = current_app.config['DB_COLLECTION']
        buffer = current_app.config['PHONE_BUFFER']

        if collection is None and not buffer:
            return jsonify({"status": "success", "count": 0, "locations": []}), 200

        locations = get_latest_locations(collection, buffer, hours, limit)

        return jsonify({
            "status": "success",
            "count": len(locations),
            "locations": locations
        }), 200

    except Exception as e:
        current_app.logger.error(f"Error fetching locations: {e}")
        return jsonify({"error": str(e)}), 500


@api.route('/all', methods=['GET'])
def get_all_users():
    """Get all user records from the database."""
    try:
        db = current_app.config['DB']
        if db is None:
            return jsonify({"error": "Database not connected"}), 500
        
        users_collection = db["users"]
        
        # Fetch all users from the database
        users = list(users_collection.find({}))
        
        # Convert MongoDB documents to JSON-serializable format
        users_list = []
        for user in users:
            # Sort messages by time (oldest first)
            messages = user.get("messages", [])
            if messages:
                try:
                    messages = sorted(messages, key=lambda m: m.get('time', ''))
                except Exception as e:
                    current_app.logger.warning(f"Could not sort messages for user {user.get('uuid')}: {e}")
            
            user_dict = {
                "uuid": user.get("uuid"),
                "type": user.get("type"),
                "name": user.get("name"),
                "age": user.get("age"),
                "height": user.get("height"),
                "weight": user.get("weight"),
                "medical": user.get("medical"),
                "location": user.get("location", {
                    "lat": None,
                    "long": None,
                    "last_updated": None
                }),
                "battery": user.get("battery", {
                    "percentage": None,
                    "time_left_till_off": None,
                    "last_updated": None
                }),
                "messages": messages,
                "emergency_questionaire": user.get("emergency_questionaire"),
                "created_at": user.get("created_at"),
                "updated_at": user.get("updated_at")
            }
            users_list.append(user_dict)
        
        return jsonify({
            "status": "success",
            "count": len(users_list),
            "users": users_list
        }), 200
        
    except Exception as e:
        current_app.logger.error(f"Error fetching all users: {e}")
        return jsonify({"error": str(e)}), 500


@api.route('/stats', methods=['GET'])
def get_stats():
    """Get statistics about stored locations and current buffer."""
    try:
        collection = current_app.config['DB_COLLECTION']
        buffer = current_app.config['PHONE_BUFFER']
        db = current_app.config['DB']
        
        # Get stats from users collection if available
        if db is not None:
            users_collection = db["users"]
            total_users = users_collection.count_documents({})
            victims = users_collection.count_documents({"type": "victim"})
            first_responders = users_collection.count_documents({"type": "first_responder"})
            
            # Get most recent update
            latest_user = users_collection.find_one(sort=[("updated_at", -1)])
            latest_time = latest_user.get("updated_at") if latest_user else None
            
            return jsonify({
                "status": "success",
                "total_users": total_users,
                "victims": victims,
                "first_responders": first_responders,
                "unique_phones": total_users,
                "buffered_phones": len(buffer) if buffer else 0,
                "latest_update_time": latest_time
            }), 200
        
        stats = get_db_stats(collection, buffer)

        if stats:
            return jsonify({"status": "success", **stats}), 200
        else:
            # If DB is not connected, provide minimal stats from buffer
            return jsonify({
                "status": "success",
                "total_locations": 0,
                "unique_phones": len(buffer) if buffer else 0,
                "buffered_phones": len(buffer) if buffer else 0,
                "latest_location_time": None
            }), 200

    except Exception as e:
        current_app.logger.error(f"Error fetching stats: {e}")
        return jsonify({"error": str(e)}), 500