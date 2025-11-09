# Law Enforcement Dashboard - Disaster Victim Tracker

A real-time dashboard for law enforcement to track victims in natural disasters using location data from mobile devices.

## Features

- 📍 Real-time location tracking on an interactive map
- 📱 Receive phone data from mobile devices
- 🗄️ Automatic MongoDB storage every 5 minutes
- 🎯 Hover/click markers to view victim coordinates
- 📊 Statistics dashboard
- 🔄 Auto-refresh every 30 seconds
- 🎲 Mock data generation for testing

## Prerequisites

### Option 1: Docker (Recommended)
- Docker Desktop or Docker Engine
- Docker Compose

### Option 2: Manual Setup
- Python 3.8+
- Node.js 14+ and npm
- MongoDB (running on localhost:27017)

## Setup Instructions

### Docker Setup (Recommended)

The easiest way to run the entire application is using Docker Compose. The setup uses a **single unified container** that serves both the Flask backend and React frontend:

```bash
# Build and start all services (MongoDB + Unified App Container)
docker-compose up --build

# Or run in detached mode
docker-compose up -d --build

# View logs
docker-compose logs -f

# View logs for specific service
docker-compose logs -f app
docker-compose logs -f mongodb

# Stop all services
docker-compose down

# Stop and remove volumes (clears database)
docker-compose down -v
```

The application will be available at:
- **Frontend & Backend**: http://localhost:5000 (Flask serves both the React app and API)
- **MongoDB**: localhost:27017

**Note**: The unified container builds the React frontend and serves it directly from Flask, so everything is accessible on port 5000.

### Manual Setup

### 1. Install MongoDB

Make sure MongoDB is installed and running:

```bash
# On Windows (using Chocolatey)
choco install mongodb

# Or download from https://www.mongodb.com/try/download/community

# Start MongoDB service
net start MongoDB
```

### 2. Backend Setup (Flask)

```bash
# Install Python dependencies
pip install -r requirements.txt

# Run the Flask server
cd flask_dashboard
python commandcenterbackend.py
```

The Flask server will run on `http://localhost:5000`

### 3. Frontend Setup (React)

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies
npm install

# Start the React development server
npm start
```

The React app will run on `http://localhost:3000`

## API Endpoints

### GET /api/health
Health check endpoint

### POST /api/phone-data
Receive phone data from mobile devices
```json
{
  "phone_id": "phone_123",
  "latitude": 42.8864,
  "longitude": -78.8784,
  "accuracy": 10
}
```

### GET /api/locations
Get all victim locations
- Query params: `hours` (default: 24), `limit` (default: 1000)

### POST /api/mock-data
Generate mock location data for testing
```json
{
  "count": 5
}
```

### GET /api/stats
Get statistics about stored locations

## Usage

### Docker (Recommended)
1. Run `docker-compose up --build`
2. Open `http://localhost:5000` in your browser
3. Click "Generate Mock Data" to create test victim locations
4. View locations on the map and hover/click to see coordinates

### Manual Setup
1. Start MongoDB
2. Start the Flask backend server
3. Start the React frontend (separate terminal)
4. Open `http://localhost:3000` in your browser
5. Click "Generate Mock Data" to create test victim locations
6. View locations on the map and hover/click to see coordinates

## Project Structure

```
type_int/
├── flask_dashboard/
│   └── law_enforcement_dashboard.py  # Flask backend
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   │   ├── MapView.js            # Map component
│   │   │   └── ControlPanel.js       # Control panel component
│   │   ├── App.js                    # Main app component
│   │   └── index.js                  # Entry point
│   └── package.json
├── requirements.txt                   # Python dependencies
└── README.md
```

## Technology Stack

- **Backend**: Flask, PyMongo, Flask-CORS
- **Frontend**: React, React-Leaflet, Axios
- **Database**: MongoDB
- **Map**: OpenStreetMap via Leaflet

## License

UBHacking 2025 UBopoly
