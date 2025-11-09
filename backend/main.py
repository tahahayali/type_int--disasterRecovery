import os
import threading
from flask import send_from_directory, jsonify
from app_factory import create_app, init_mongo
from database_manager import store_locations_to_db, initialize_data
from api_routes import api
from typing import Dict, List

# --- Global State ---
# In-memory storage for recent phone data (before storing to DB every 5 min)
phone_data_buffer: Dict[str, Dict] = {}

# --- Initialization ---
# 1. Initialize MongoDB connection
client, db, collection = init_mongo()

# 2. Create the Flask app, passing the database reference and buffer
app = create_app(db_collection=collection, phone_buffer=phone_data_buffer)

# 3. Register the API blueprint
app.register_blueprint(api, url_prefix='/api')

# 4. Initialize mock/default data (victims and first responders)
# This runs once at startup
with app.app_context():
    if collection is not None:
        initialize_data(collection, phone_data_buffer)
    else:
        print("Warning: MongoDB not available. Initial data is only stored in buffer.")

# 5. Start background thread for storing locations to database
if collection is not None:
    storage_thread = threading.Thread(
        target=store_locations_to_db,
        args=(collection, phone_data_buffer),
        daemon=True
    )
    storage_thread.start()
    print("Background database storage thread started (5-minute sync).")
else:
    print("Warning: Database storage thread not started (MongoDB connection failed).")

# --- React App Serving (Catch-all Route) ---
# Determine the base directory and static folder
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STATIC_DIR = os.path.join(BASE_DIR, 'static')


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


# --- Run Server ---
if __name__ == '__main__':
    print("Starting Law Enforcement Dashboard Server...")
    debug_mode = os.getenv("FLASK_ENV") != "production"
    app.run(debug=debug_mode, host='0.0.0.0', port=5000, use_reloader=False)