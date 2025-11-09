# Quick Start Guide

## Docker Setup (Fastest Way)

### Prerequisites
- Docker Desktop or Docker Engine installed
- Docker Compose installed (usually comes with Docker Desktop)

### Steps

1. **Clone/Navigate to the project directory**
   ```bash
   cd type_int
   ```

2. **Build and start everything**
   ```bash
   docker-compose up --build
   ```

3. **Wait for services to start**
   - MongoDB will start first
   - App container will build (this may take a few minutes the first time)
   - Flask backend + React frontend will start

4. **Access the application**
   - Open your browser and go to: **http://localhost:5000**
   - The React frontend and Flask API are both served from this single port

5. **Generate test data**
   - Click "Generate Mock Data" button in the control panel
   - You should see markers appear on the map

### Useful Commands

```bash
# Start in background (detached mode)
docker-compose up -d --build

# View logs
docker-compose logs -f

# View logs for app only
docker-compose logs -f app

# Stop services
docker-compose down

# Stop and remove all data (fresh start)
docker-compose down -v

# Rebuild after code changes
docker-compose up --build
```

## Architecture

The unified setup consists of:
- **1 App Container**: Contains Flask backend + React frontend (built and served as static files)
- **1 MongoDB Container**: Database for storing location data

Both containers communicate over a Docker network.

## Troubleshooting

### Port 5000 already in use
```bash
# Change the port in docker-compose.yml
ports:
  - "5001:5000"  # Change 5001 to any available port
```

### MongoDB connection errors
```bash
# Check if MongoDB container is running
docker-compose ps

# Check MongoDB logs
docker-compose logs mongodb

# Restart MongoDB
docker-compose restart mongodb
```

### App not building
```bash
# Clean build (removes cache)
docker-compose build --no-cache

# Then start
docker-compose up
```

### Clear everything and start fresh
```bash
# Stop and remove all containers, networks, and volumes
docker-compose down -v

# Remove any built images
docker rmi disaster_connect_app

# Rebuild from scratch
docker-compose up --build
```

## Development Mode

For development with hot-reload, you can still run services separately:

```bash
# Terminal 1: Start MongoDB
docker-compose up mongodb

# Terminal 2: Start Flask backend
cd flask_dashboard
python commandcenterbackend.py

# Terminal 3: Start React frontend
cd frontend
npm start
```

This allows for faster iteration during development.

