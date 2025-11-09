from flask import Flask
from flask_cors import CORS
from pymongo import MongoClient
import os

def init_mongo():
    """Initializes and returns the MongoDB client and database connection."""
    # MongoDB connection - use environment variable or default to localhost
    MONGO_HOST = os.getenv("MONGO_HOST", "localhost")
    MONGO_PORT = os.getenv("MONGO_PORT", "27017")
    MONGO_URI = f"mongodb://{MONGO_HOST}:{MONGO_PORT}/"
    DB_NAME = os.getenv("DB_NAME", "disaster_connect")

    client = None
    db = None
    collection = None

    try:
        client = MongoClient(MONGO_URI, serverSelectionTimeoutMS=5000)
        # The ismaster command is a lightweight way to verify a connection
        client.admin.command('ismaster')
        db = client[DB_NAME]
        collection = db["victim_locations"]

        # Ensure indexes for faster queries
        collection.create_index([("phone_id", 1), ("timestamp", -1)])
        collection.create_index([("timestamp", -1)])
        print("Connected to MongoDB successfully")
    except Exception as e:
        print(f"MongoDB connection error: {e}")
        print("Please ensure MongoDB is running or update MONGO_HOST/MONGO_PORT environment variables.")

    return client, db, collection


def create_app(db_collection=None, phone_buffer=None):
    """
    Creates and configures the Flask application.

    Args:
        db_collection: The MongoDB collection object for victim data.
        phone_buffer: The in-memory buffer for recent phone data.
    """
    # Determine the base directory and static folder
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
    STATIC_DIR = os.path.join(BASE_DIR, 'static')

    # Create Flask app with static folder for React build
    app = Flask(__name__, static_folder=STATIC_DIR, static_url_path='')
    CORS(app)  # Enable CORS for React frontend

    # Store the database and buffer references in the app config for use in routes
    app.config['DB_COLLECTION'] = db_collection
    app.config['PHONE_BUFFER'] = phone_buffer

    return app


if __name__ == '__main__':
    # This block is for direct execution of the factory (not typically how it's used)
    # The main file (main.py) will handle execution.
    print("This file contains the app factory and MongoDB initialization logic.")