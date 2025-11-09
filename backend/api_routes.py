from flask import request, jsonify, Blueprint, send_from_directory, current_app
from datetime import datetime
import os
import threading
from database_manager import get_latest_locations, get_db_stats, generate_mock_location

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


@api.route('/mock-data', methods=['POST'])
def generate_mock_data():
    """Generate and store mock location data for testing in the buffer."""
    try:
        data = request.get_json() or {}
        num_phones = data.get("count", 5)
        num_responders = data.get("responders", 0)

        buffer = current_app.config['PHONE_BUFFER']

        mock_phones = []

        # Generate victim mock data
        for i in range(num_phones):
            # Use a unique ID that won't clash with initialized victims
            phone_id = f"manual_mock_victim_{datetime.utcnow().timestamp()}_{i}"
            location = generate_mock_location("victim")

            # Store in buffer
            data = {
                "phone_id": phone_id,
                "latitude": location["latitude"],
                "longitude": location["longitude"],
                "accuracy": location["accuracy"],
                "battery_percentage": location["battery_percentage"],
                "type": location.get("type", "victim"),
                "questionnaire_data": location["questionnaire_data"],
                "timestamp": datetime.utcnow()
            }
            buffer[phone_id] = data
            mock_phones.append({**data, "timestamp": data["timestamp"].isoformat()})

        # Generate first responder mock data if requested
        for i in range(num_responders):
            responder_id = f"manual_mock_responder_{datetime.utcnow().timestamp()}_{i}"
            location = generate_mock_location("first_responder")

            # Store in buffer
            data = {
                "phone_id": responder_id,
                "latitude": location["latitude"],
                "longitude": location["longitude"],
                "accuracy": location["accuracy"],
                "battery_percentage": location["battery_percentage"],
                "type": "first_responder",
                "questionnaire_data": location["questionnaire_data"],
                "timestamp": datetime.utcnow()
            }
            buffer[responder_id] = data
            mock_phones.append({**data, "timestamp": data["timestamp"].isoformat()})

        return jsonify({
            "status": "success",
            "message": f"Generated {num_phones} mock victim locations and {num_responders} first responders (buffered)",
            "phones_buffered": len(buffer)
        }), 202

    except Exception as e:
        current_app.logger.error(f"Error generating mock data: {e}")
        return jsonify({"error": str(e)}), 500


@api.route('/stats', methods=['GET'])
def get_stats():
    """Get statistics about stored locations and current buffer."""
    try:
        collection = current_app.config['DB_COLLECTION']
        buffer = current_app.config['PHONE_BUFFER']

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