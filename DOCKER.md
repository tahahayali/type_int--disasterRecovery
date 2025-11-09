# Docker Setup Guide

## Quick Start

```bash
# Build and start all services
docker-compose up --build

# Start in background (detached mode)
docker-compose up -d --build

# View logs
docker-compose logs -f

# View logs for specific service
docker-compose logs -f flask_backend
docker-compose logs -f react_frontend
docker-compose logs -f mongodb

# Stop all services
docker-compose down

# Stop and remove all data (including database)
docker-compose down -v
```

## Services

### MongoDB
- **Container**: `disaster_connect_mongodb`
- **Port**: `27017`
- **Database**: `disaster_connect`
- **Data Volume**: `mongodb_data` (persists data)

### Flask Backend
- **Container**: `disaster_connect_backend`
- **Port**: `5000`
- **API URL**: http://localhost:5000
- **Environment Variables**:
  - `MONGO_HOST=mongodb` (service name in Docker network)
  - `MONGO_PORT=27017`
  - `DB_NAME=disaster_connect`
  - `FLASK_ENV=production`

### React Frontend
- **Container**: `disaster_connect_frontend`
- **Port**: `3000` (mapped to nginx port 80)
- **URL**: http://localhost:3000
- **Environment Variables**:
  - `REACT_APP_API_URL=http://localhost:5000`

## Troubleshooting

### MongoDB not connecting
```bash
# Check if MongoDB container is running
docker-compose ps

# Check MongoDB logs
docker-compose logs mongodb

# Restart MongoDB
docker-compose restart mongodb
```

### Flask backend not starting
```bash
# Check Flask logs
docker-compose logs flask_backend

# Rebuild Flask container
docker-compose up -d --build flask_backend
```

### React frontend not loading
```bash
# Check React build logs
docker-compose logs react_frontend

# Rebuild React container
docker-compose up -d --build react_frontend
```

### Clear all data and start fresh
```bash
# Stop and remove everything including volumes
docker-compose down -v

# Rebuild and start
docker-compose up --build
```

## Development Mode

For development with hot-reload, you can modify `docker-compose.yml` to use volume mounts (already configured for Flask backend). The React frontend is built into a static image for production.

To enable React hot-reload in development, you would need to:
1. Mount the source code as a volume
2. Run `npm start` instead of serving the built files
3. Expose port 3000 directly

## Production Considerations

1. **Environment Variables**: Create a `.env` file for sensitive configuration
2. **SSL/TLS**: Add reverse proxy (nginx/traefik) for HTTPS
3. **Database Backups**: Set up regular MongoDB backups
4. **Monitoring**: Add health checks and monitoring tools
5. **Scaling**: Use Docker Swarm or Kubernetes for production scaling

## Network

All services are connected via the `disaster_network` bridge network. Services can communicate using their service names:
- Flask can connect to MongoDB using `mongodb:27017`
- React frontend makes requests to `http://localhost:5000` (from the browser)

