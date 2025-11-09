# Multi-stage build: Build React app, then create final container with Flask + React
# Build context: Root directory (type_int--disasterRecovery)

# =============================================================================
# Stage 1: Build React Frontend
# =============================================================================
FROM node:18-alpine AS react-builder

WORKDIR /build/frontend

# Copy package files first for better layer caching
COPY frontend/package*.json ./

# Install npm dependencies (including dev dependencies for build)
# Use npm install instead of npm ci to handle lock file sync issues gracefully
# This resolves dependencies based on package.json and updates lock file if needed
RUN npm install && \
    npm cache clean --force

# Copy all frontend source files
COPY frontend/ ./

# Build React app for production
RUN npm run build

# Verify build output exists
RUN test -d build && echo "React build successful" || (echo "React build failed" && exit 1)

# =============================================================================
# Stage 2: Flask Backend + Serve React (Production)
# =============================================================================
FROM python:3.11-slim

WORKDIR /app

# Set environment variables
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    FLASK_ENV=production \
    PYTHONPATH=/app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Copy Python requirements and install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt && \
    pip cache purge

# Copy Flask application from flask_dashboard directory
COPY backend/old_files/flask_dashboard/commandcenterbackend.py .

# Copy built React app from builder stage to static directory
COPY --from=react-builder /build/frontend/build ./static

# Verify static files exist
RUN test -f static/index.html && echo "Static files copied successfully" || (echo "Static files not found" && exit 1)

# Expose Flask port
EXPOSE 5000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD python -c "import socket; s=socket.socket(); s.connect(('localhost', 5000)); s.close()" || exit 1

# Run the Flask application
CMD ["python", "commandcenterbackend.py"]

