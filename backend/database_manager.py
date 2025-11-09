from datetime import datetime, timedelta
import threading
import time
import random
from typing import Dict, List
from pymongo.collection import Collection
from bson.objectid import ObjectId


def store_locations_to_db(collection: Collection, phone_data_buffer: Dict[str, Dict]):
    """Store all buffered phone data to MongoDB every 5 minutes (300 seconds)"""

    while True:
        time.sleep(300)

        if collection is None:
            print("MongoDB not connected, skipping database storage")
            continue

        if not phone_data_buffer:
            print("No phone data to store")
            continue

        try:
            documents = []
            current_time = datetime.utcnow()

            # Use a temporary copy of keys to iterate safely
            keys_to_process = list(phone_data_buffer.keys())

            for phone_id in keys_to_process:
                data = phone_data_buffer.pop(phone_id, None)  # Pop to clear from buffer
                if data is None: continue

                doc = {
                    "phone_id": phone_id,
                    # GeoJSON format for MongoDB Geo-queries
                    "location": {
                        "type": "Point",
                        "coordinates": [data.get("longitude", 0), data.get("latitude", 0)]
                    },
                    "latitude": data.get("latitude", 0),
                    "longitude": data.get("longitude", 0),
                    "accuracy": data.get("accuracy", 0),
                    "battery_percentage": data.get("battery_percentage", 0),
                    "type": data.get("type", "victim"),
                    "questionnaire_data": data.get("questionnaire_data", {}),
                    "timestamp": current_time,
                    "last_seen": data.get("timestamp", current_time),
                }
                documents.append(doc)

            # Insert documents into MongoDB
            if documents:
                result = collection.insert_many(documents)
                print(f"Stored {len(result.inserted_ids)} location records to MongoDB")

        except Exception as e:
            print(f"Error storing locations to database: {e}")
            # Re-add items back to buffer if insertion failed (simple retry logic)
            # NOTE: For hackathon, this is simple enough. In production, use a queue.
            for doc in documents:
                phone_data_buffer[doc["phone_id"]] = doc
            print(f"Re-added {len(documents)} items to buffer due to DB error.")


def get_latest_locations(collection: Collection, phone_data_buffer: Dict[str, Dict], hours: int = 24,
                         limit: int = 1000) -> List[Dict]:
    """Fetches the latest unique location for each phone_id from DB and Buffer."""

    latest_locations = {}

    # 1. Get locations from database
    if collection is not None:
        time_threshold = datetime.utcnow() - timedelta(hours=hours)

        # Aggregation pipeline to get the latest record for each phone_id
        pipeline = [
            {"$match": {"timestamp": {"$gte": time_threshold}}},
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
                "accuracy": loc.get("accuracy", 0),
                "battery_percentage": loc.get("battery_percentage", 0),
                "type": loc.get("type", "victim"),
                "questionnaire_data": loc.get("questionnaire_data", {}),
                "timestamp": loc.get("timestamp"),
                "last_seen": loc.get("last_seen", loc.get("timestamp"))
            }

    # 2. Check buffer for any newer/unsaved locations (overrides database if newer)
    for phone_id, data in phone_data_buffer.items():
        buffer_timestamp = data.get("timestamp")

        # Check if buffer item is newer than the database item, or if DB item doesn't exist
        is_newer = True
        if phone_id in latest_locations and isinstance(latest_locations[phone_id]["timestamp"], datetime):
            is_newer = latest_locations[phone_id]["timestamp"] < buffer_timestamp

        if is_newer:
            latest_locations[phone_id] = {
                "phone_id": phone_id,
                "latitude": data.get("latitude", 0),
                "longitude": data.get("longitude", 0),
                "accuracy": data.get("accuracy", 0),
                "battery_percentage": data.get("battery_percentage", 0),
                "type": data.get("type", "victim"),
                "questionnaire_data": data.get("questionnaire_data", {}),
                "timestamp": buffer_timestamp,
                "last_seen": buffer_timestamp
            }

    # 3. Convert to list, sort, and format timestamps
    result = []
    for loc in latest_locations.values():
        result.append({
            "phone_id": loc["phone_id"],
            "latitude": loc["latitude"],
            "longitude": loc["longitude"],
            "accuracy": loc["accuracy"],
            "battery_percentage": loc["battery_percentage"],
            "type": loc["type"],
            "questionnaire_data": loc["questionnaire_data"],
            "timestamp": loc["timestamp"].isoformat() if isinstance(loc["timestamp"], datetime) else str(
                loc["timestamp"]),
            "last_seen": loc["last_seen"].isoformat() if isinstance(loc["last_seen"], datetime) else str(
                loc["last_seen"])
        })

    # Sort by timestamp (newest first) and apply limit
    result.sort(key=lambda x: x.get("timestamp", ""), reverse=True)
    return result[:limit]


def get_db_stats(collection: Collection, phone_data_buffer: Dict[str, Dict]):
    """Get statistics about stored locations."""
    if collection is None:
        return None

    total_count = collection.count_documents({})
    unique_phones = len(collection.distinct("phone_id"))

    # Get most recent location time from DB
    latest = collection.find_one(sort=[("timestamp", -1)])
    latest_time = latest.get("timestamp").isoformat() if latest and isinstance(latest.get("timestamp"),
                                                                               datetime) else None

    return {
        "total_locations": total_count,
        "unique_phones": unique_phones,
        "buffered_phones": len(phone_data_buffer),
        "latest_location_time": latest_time
    }