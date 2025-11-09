from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
from pymongo import MongoClient
from datetime import datetime, timedelta
import threading
import time
import random
import os
from typing import Dict, List

# Determine the base directory and static folder
# In Docker: Flask file is at /app/law_enforcement_dashboard.py, static is at /app/static
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STATIC_DIR = os.path.join(BASE_DIR, 'static')

# Create Flask app with static folder for React build
app = Flask(__name__, static_folder=STATIC_DIR, static_url_path='')
CORS(app)  # Enable CORS for React frontend

# MongoDB connection - use environment variable or default to localhost
MONGO_HOST = os.getenv("MONGO_HOST", "localhost")
MONGO_PORT = os.getenv("MONGO_PORT", "27017")
MONGO_URI = f"mongodb://{MONGO_HOST}:{MONGO_PORT}/"
DB_NAME = os.getenv("DB_NAME", "disaster_connect")
COLLECTION_NAME = "victim_locations"

try:
    client = MongoClient(MONGO_URI, serverSelectionTimeoutMS=5000)
    db = client[DB_NAME]
    collection = db[COLLECTION_NAME]
    # Create index on phone_id and timestamp for faster queries
    collection.create_index([("phone_id", 1), ("timestamp", -1)])
    collection.create_index([("timestamp", -1)])
    print("Connected to MongoDB successfully")
except Exception as e:
    print(f"MongoDB connection error: {e}")
    print("Please ensure MongoDB is running on localhost:27017")
    client = None
    db = None
    collection = None

# In-memory storage for recent phone data (before storing to DB)
phone_data_buffer: Dict[str, Dict] = {}

# First responder IDs for consistent tracking
FIRST_RESPONDER_IDS = [f"first_responder_{i+1}" for i in range(5)]

# Mock location data generator (for testing)
def generate_mock_location(person_type="victim"):
    """Generate mock location data for victims or first responders"""
    # Simulate locations around a disaster area (e.g., Buffalo, NY area)
    base_lat = 42.8864
    base_lon = -78.8784
    # Add random offset (within ~10km radius)
    lat_offset = random.uniform(-0.1, 0.1)
    lon_offset = random.uniform(-0.1, 0.1)
    
    # First responders typically have better battery and GPS accuracy
    if person_type == "first_responder":
        accuracy = random.uniform(3, 15)  # Better GPS accuracy
        battery_percentage = random.randint(60, 100)  # Higher battery (they have equipment)
    else:
        accuracy = random.uniform(5, 50)  # GPS accuracy in meters
        battery_percentage = random.randint(5, 100)  # Battery percentage (5-100%)
    
    return {
        "latitude": base_lat + lat_offset,
        "longitude": base_lon + lon_offset,
        "accuracy": accuracy,
        "battery_percentage": battery_percentage,
        "timestamp": datetime.utcnow().isoformat(),
        "type": person_type
    }

def store_locations_to_db():
    """Store all buffered phone data to MongoDB every 5 minutes"""
    global phone_data_buffer
    
    while True:
        time.sleep(300)  # Wait 5 minutes (300 seconds)
        
        if collection is None:
            print("MongoDB not connected, skipping database storage")
            continue
        
        if not phone_data_buffer:
            print("No phone data to store")
            continue
        
        try:
            # Prepare documents to insert
            documents = []
            current_time = datetime.utcnow()
            
            for phone_id, data in phone_data_buffer.items():
                doc = {
                    "phone_id": phone_id,
                    "location": {
                        "type": "Point",
                        "coordinates": [data.get("longitude", 0), data.get("latitude", 0)]
                    },
                    "latitude": data.get("latitude", 0),
                    "longitude": data.get("longitude", 0),
                    "accuracy": data.get("accuracy", 0),
                    "battery_percentage": data.get("battery_percentage", 0),
                    "type": data.get("type", "victim"),  # Add type field
                    "timestamp": current_time,
                    "last_seen": data.get("timestamp", current_time),
                    "phone_data": data.get("phone_data", {})
                }
                documents.append(doc)
            
            # Insert documents into MongoDB
            if documents:
                result = collection.insert_many(documents)
                print(f"Stored {len(result.inserted_ids)} location records to MongoDB")
                
                # Clear buffer after successful storage
                phone_data_buffer.clear()
                
        except Exception as e:
            print(f"Error storing locations to database: {e}")

def initialize_first_responders():
    """Initialize first responders on startup (3-5 responders) and store in buffer and database"""
    try:
        num_responders = random.randint(3, 5)
        current_time = datetime.utcnow()
        documents = []
        
        for i in range(num_responders):
            responder_id = FIRST_RESPONDER_IDS[i]
            location = generate_mock_location("first_responder")
            
            # Add to buffer for immediate availability
            phone_data_buffer[responder_id] = {
                "phone_id": responder_id,
                "latitude": location["latitude"],
                "longitude": location["longitude"],
                "accuracy": location["accuracy"],
                "battery_percentage": location["battery_percentage"],
                "type": "first_responder",
                "timestamp": current_time,
                "phone_data": {
                    "phone_id": responder_id,
                    "latitude": location["latitude"],
                    "longitude": location["longitude"],
                    "accuracy": location["accuracy"],
                    "battery_percentage": location["battery_percentage"],
                    "type": "first_responder",
                    "timestamp": location["timestamp"]
                }
            }
            
            # Also prepare for direct database storage
            if collection is not None:
                doc = {
                    "phone_id": responder_id,
                    "location": {
                        "type": "Point",
                        "coordinates": [location["longitude"], location["latitude"]]
                    },
                    "latitude": location["latitude"],
                    "longitude": location["longitude"],
                    "accuracy": location["accuracy"],
                    "battery_percentage": location["battery_percentage"],
                    "type": "first_responder",
                    "timestamp": current_time,
                    "last_seen": current_time,
                    "phone_data": {
                        "phone_id": responder_id,
                        "latitude": location["latitude"],
                        "longitude": location["longitude"],
                        "accuracy": location["accuracy"],
                        "battery_percentage": location["battery_percentage"],
                        "type": "first_responder",
                        "timestamp": location["timestamp"]
                    }
                }
                documents.append(doc)
        
        # Store directly to database if available
        if collection is not None and documents:
            try:
                collection.insert_many(documents)
                print(f"Initialized {num_responders} first responders on startup (stored in database)")
            except Exception as db_error:
                print(f"Initialized {num_responders} first responders on startup (database storage failed: {db_error})")
        else:
            print(f"Initialized {num_responders} first responders on startup (database not available, stored in buffer only)")
    except Exception as e:
        print(f"Error initializing first responders: {e}")

def initialize_victims():
    """Initialize 10 victims on startup and store in buffer and database"""
    try:
        num_victims = 10
        current_time = datetime.utcnow()
        documents = []
        
        for i in range(num_victims):
            victim_id = f"victim_{i+1}"
            location = generate_mock_location("victim")
            
            # Add to buffer for immediate availability
            phone_data_buffer[victim_id] = {
                "phone_id": victim_id,
                "latitude": location["latitude"],
                "longitude": location["longitude"],
                "accuracy": location["accuracy"],
                "battery_percentage": location["battery_percentage"],
                "type": "victim",
                "timestamp": current_time,
                "phone_data": {
                    "phone_id": victim_id,
                    "latitude": location["latitude"],
                    "longitude": location["longitude"],
                    "accuracy": location["accuracy"],
                    "battery_percentage": location["battery_percentage"],
                    "type": "victim",
                    "timestamp": location["timestamp"]
                }
            }
            
            # Also prepare for direct database storage
            if collection is not None:
                doc = {
                    "phone_id": victim_id,
                    "location": {
                        "type": "Point",
                        "coordinates": [location["longitude"], location["latitude"]]
                    },
                    "latitude": location["latitude"],
                    "longitude": location["longitude"],
                    "accuracy": location["accuracy"],
                    "battery_percentage": location["battery_percentage"],
                    "type": "victim",
                    "timestamp": current_time,
                    "last_seen": current_time,
                    "phone_data": {
                        "phone_id": victim_id,
                        "latitude": location["latitude"],
                        "longitude": location["longitude"],
                        "accuracy": location["accuracy"],
                        "battery_percentage": location["battery_percentage"],
                        "type": "victim",
                        "timestamp": location["timestamp"]
                    }
                }
                documents.append(doc)
        
        # Store directly to database if available
        if collection is not None and documents:
            try:
                collection.insert_many(documents)
                print(f"Initialized {num_victims} victims on startup (stored in database)")
            except Exception as db_error:
                print(f"Initialized {num_victims} victims on startup (database storage failed: {db_error})")
        else:
            print(f"Initialized {num_victims} victims on startup (database not available, stored in buffer only)")
    except Exception as e:
        print(f"Error initializing victims: {e}")

# Initialize first responders and victims on startup
initialize_first_responders()
initialize_victims()

# Start background thread for storing locations to database
# This will save any new location updates from the API to the database
storage_thread = threading.Thread(target=store_locations_to_db, daemon=True)
storage_thread.start()

# Note: Auto-generation of victims is disabled - only initial 10 victims are created
# New locations can still be added via the /api/phone-data endpoint and will be saved to DB

@app.route('/api/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        "status": "healthy",
        "mongodb_connected": client is not None and db is not None,
        "buffered_phones": len(phone_data_buffer)
    })

@app.route('/api/phone-data', methods=['POST'])
def receive_phone_data():
    """Receive phone data from mobile devices"""
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({"error": "No data provided"}), 400
        
        # Extract required fields
        phone_id = data.get("phone_id")
        latitude = data.get("latitude")
        longitude = data.get("longitude")
        
        if not phone_id:
            return jsonify({"error": "phone_id is required"}), 400
        
        if latitude is None or longitude is None:
            return jsonify({"error": "latitude and longitude are required"}), 400
        
        # Store in buffer (will be persisted to DB every 5 minutes)
        phone_data_buffer[phone_id] = {
            "phone_id": phone_id,
            "latitude": float(latitude),
            "longitude": float(longitude),
            "accuracy": data.get("accuracy", 0),
            "battery_percentage": data.get("battery_percentage", 0),
            "type": data.get("type", "victim"),  # Default to victim if not specified
            "timestamp": datetime.utcnow(),
            "phone_data": data
        }
        
        return jsonify({
            "status": "success",
            "message": "Phone data received",
            "phone_id": phone_id,
            "buffered": True
        }), 200
        
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/locations', methods=['GET'])
def get_locations():
    """Get all victim and first responder locations from the database and buffer"""
    try:
        # Get query parameters
        hours = request.args.get('hours', 24, type=int)  # Default: last 24 hours
        limit = request.args.get('limit', 1000, type=int)  # Default: 1000 records
        
        # Dictionary to store latest location for each phone_id
        latest_locations = {}
        
        # Get locations from database if available
        if collection is not None:
            # Calculate time threshold
            time_threshold = datetime.utcnow() - timedelta(hours=hours)
            
            # Query database for recent locations
            query = {"timestamp": {"$gte": time_threshold}}
            
            # Get latest location for each phone_id from database
            pipeline = [
                {"$match": query},
                {"$sort": {"timestamp": -1}},
                {"$group": {
                    "_id": "$phone_id",
                    "latest_location": {"$first": "$$ROOT"}
                }},
                {"$replaceRoot": {"newRoot": "$latest_location"}}
            ]
            
            cursor = collection.aggregate(pipeline)
            db_locations = list(cursor)
            
            # Add database locations to latest_locations dict
            for loc in db_locations:
                phone_id = loc.get("phone_id", "unknown")
                latest_locations[phone_id] = {
                    "phone_id": phone_id,
                    "latitude": loc.get("latitude", 0),
                    "longitude": loc.get("longitude", 0),
                    "coordinates": [loc.get("longitude", 0), loc.get("latitude", 0)],
                    "accuracy": loc.get("accuracy", 0),
                    "battery_percentage": loc.get("battery_percentage", 0),
                    "type": loc.get("type", "victim"),
                    "timestamp": loc.get("timestamp"),
                    "last_seen": loc.get("last_seen", loc.get("timestamp"))
                }
        
        # Also check buffer for any newer/unsaved locations (overrides database if newer)
        for phone_id, data in phone_data_buffer.items():
            buffer_timestamp = data.get("timestamp")
            if isinstance(buffer_timestamp, datetime):
                # If this phone_id is not in latest_locations, or buffer has newer data
                if phone_id not in latest_locations or (latest_locations[phone_id]["timestamp"] < buffer_timestamp if isinstance(latest_locations[phone_id]["timestamp"], datetime) else True):
                    latest_locations[phone_id] = {
                        "phone_id": phone_id,
                        "latitude": data.get("latitude", 0),
                        "longitude": data.get("longitude", 0),
                        "coordinates": [data.get("longitude", 0), data.get("latitude", 0)],
                        "accuracy": data.get("accuracy", 0),
                        "battery_percentage": data.get("battery_percentage", 0),
                        "type": data.get("type", "victim"),
                        "timestamp": buffer_timestamp,
                        "last_seen": buffer_timestamp
                    }
        
        # Convert to list and format timestamps
        result = []
        for phone_id, loc in latest_locations.items():
            result.append({
                "phone_id": loc["phone_id"],
                "latitude": loc["latitude"],
                "longitude": loc["longitude"],
                "coordinates": loc["coordinates"],
                "accuracy": loc["accuracy"],
                "battery_percentage": loc["battery_percentage"],
                "type": loc["type"],
                "timestamp": loc["timestamp"].isoformat() if isinstance(loc["timestamp"], datetime) else str(loc["timestamp"]),
                "last_seen": loc["last_seen"].isoformat() if isinstance(loc["last_seen"], datetime) else str(loc["last_seen"])
            })
        
        # Sort by timestamp (newest first) and apply limit
        result.sort(key=lambda x: x.get("timestamp", ""), reverse=True)
        result = result[:limit]
        
        return jsonify({
            "status": "success",
            "count": len(result),
            "locations": result
        }), 200
        
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/mock-data', methods=['POST'])
def generate_mock_data():
    """Generate and store mock location data for testing"""
    try:
        data = request.get_json() or {}
        num_phones = data.get("count", 5)
        num_responders = data.get("responders", 0)  # Optional: number of first responders to generate
        
        mock_phones = []
        # Generate victim mock data
        for i in range(num_phones):
            phone_id = f"mock_phone_{i+1}"
            location = generate_mock_location("victim")
            
            phone_data = {
                "phone_id": phone_id,
                "latitude": location["latitude"],
                "longitude": location["longitude"],
                "accuracy": location["accuracy"],
                "battery_percentage": location["battery_percentage"],
                "type": location.get("type", "victim"),
                "timestamp": location["timestamp"]
            }
            
            phone_data_buffer[phone_id] = {
                "phone_id": phone_id,
                "latitude": location["latitude"],
                "longitude": location["longitude"],
                "accuracy": location["accuracy"],
                "battery_percentage": location["battery_percentage"],
                "type": location.get("type", "victim"),
                "timestamp": datetime.utcnow(),
                "phone_data": phone_data
            }
            
            mock_phones.append(phone_data)
        
        # Generate first responder mock data if requested
        if num_responders > 0:
            for i in range(num_responders):
                responder_id = f"first_responder_{i+1}"
                location = generate_mock_location("first_responder")
                
                phone_data = {
                    "phone_id": responder_id,
                    "latitude": location["latitude"],
                    "longitude": location["longitude"],
                    "accuracy": location["accuracy"],
                    "battery_percentage": location["battery_percentage"],
                    "type": "first_responder",
                    "timestamp": location["timestamp"]
                }
                
                phone_data_buffer[responder_id] = {
                    "phone_id": responder_id,
                    "latitude": location["latitude"],
                    "longitude": location["longitude"],
                    "accuracy": location["accuracy"],
                    "battery_percentage": location["battery_percentage"],
                    "type": "first_responder",
                    "timestamp": datetime.utcnow(),
                    "phone_data": phone_data
                }
                
                mock_phones.append(phone_data)
        
        return jsonify({
            "status": "success",
            "message": f"Generated {num_phones} mock phone locations and {num_responders} first responders",
            "phones": mock_phones
        }), 200
        
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/stats', methods=['GET'])
def get_stats():
    """Get statistics about stored locations"""
    try:
        if collection is None:
            return jsonify({"error": "MongoDB not connected"}), 500
        
        total_count = collection.count_documents({})
        unique_phones = len(collection.distinct("phone_id"))
        
        # Get most recent location
        latest = collection.find_one(sort=[("timestamp", -1)])
        latest_time = latest.get("timestamp").isoformat() if latest and isinstance(latest.get("timestamp"), datetime) else None
        
        return jsonify({
            "status": "success",
            "total_locations": total_count,
            "unique_phones": unique_phones,
            "buffered_phones": len(phone_data_buffer),
            "latest_location_time": latest_time
        }), 200
        
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# Serve React App - catch all routes and serve index.html for client-side routing
# This must be last so API routes are matched first
@app.route('/', defaults={'path': ''})
@app.route('/<path:path>')
def serve_react_app(path):
    """Serve the React app for all non-API routes"""
    # Safety check: if somehow an API route reaches here, return 404
    if path.startswith('api'):
        return jsonify({"error": "API endpoint not found"}), 404
    
    # Serve static files (JS, CSS, images, etc.) from React build
    static_path = os.path.join(STATIC_DIR, path)
    if path != '' and os.path.exists(static_path) and os.path.isfile(static_path):
        return send_from_directory(STATIC_DIR, path)
    
    # Serve index.html for all other routes (React Router handles client-side routing)
    return send_from_directory(STATIC_DIR, 'index.html')

if __name__ == '__main__':
    print("Starting Law Enforcement Dashboard Server...")
    print("API Endpoints:")
    print("  GET  /api/health - Health check")
    print("  POST /api/phone-data - Receive phone data")
    print("  GET  /api/locations - Get all victim locations")
    print("  POST /api/mock-data - Generate mock location data")
    print("  GET  /api/stats - Get statistics")
    debug_mode = os.getenv("FLASK_ENV") != "production"
    app.run(debug=debug_mode, host='0.0.0.0', port=5000)

