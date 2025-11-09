import React, { useEffect, useRef } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import './MapView.css';

// Fix for default marker icons in React-Leaflet
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

// Get battery color based on percentage
const getBatteryColor = (percentage) => {
  if (percentage >= 50) return '#10b981'; // Green
  if (percentage >= 20) return '#f59e0b'; // Yellow/Orange
  return '#ef4444'; // Red
};

// Custom victim marker icon with battery indicator
const createVictimIcon = (batteryPercentage = 50, isSelected = false) => {
  const batteryColor = getBatteryColor(batteryPercentage);
  const borderColor = isSelected ? '#3b82f6' : batteryColor;
  const borderWidth = isSelected ? '3px' : '2px';
  
  return L.divIcon({
    className: 'victim-marker',
    html: `<div class="marker-container" style="border-color: ${borderColor}; border-width: ${borderWidth};">
            <div class="marker-pin" style="background: ${batteryColor};">
              <span class="marker-battery">${batteryPercentage}%</span>
            </div>
          </div>`,
    iconSize: [40, 40],
    iconAnchor: [20, 40],
    popupAnchor: [0, -40],
  });
};

// Component to handle map bounds updates
function MapBoundsUpdater({ locations }) {
  const map = useMap();
  
  useEffect(() => {
    if (locations && locations.length > 0) {
      const bounds = locations.map(loc => [loc.latitude, loc.longitude]);
      if (bounds.length > 0) {
        map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
      }
    }
  }, [locations, map]);
  
  return null;
}

function MapView({ locations, loading, onLocationSelect, selectedLocation }) {
  const mapRef = useRef(null);
  const defaultCenter = [42.8864, -78.8784]; // Buffalo, NY area
  const defaultZoom = 12;

  const handleMarkerClick = (location) => {
    onLocationSelect(location);
  };

  const formatDateTime = (dateString) => {
    if (!dateString) return 'Unknown';
    try {
      const date = new Date(dateString);
      return date.toLocaleString('en-US', { 
        month: 'short', 
        day: 'numeric', 
        hour: '2-digit', 
        minute: '2-digit' 
      });
    } catch {
      return dateString;
    }
  };

  const getBatteryIcon = (percentage) => {
    if (percentage >= 75) return '🔋';
    if (percentage >= 50) return '🔋';
    if (percentage >= 20) return '🪫';
    return '🪫';
  };

  const getBatteryStatus = (percentage) => {
    if (percentage >= 50) return 'Good';
    if (percentage >= 20) return 'Low';
    return 'Critical';
  };

  return (
    <div className="MapView">
      {loading && locations.length === 0 ? (
        <div className="loading-container">
          <div className="loading-spinner"></div>
          <p>Loading victim locations...</p>
        </div>
      ) : (
        <>
          {locations.length === 0 && !loading && (
            <div className="empty-state">
              <div className="empty-icon">📍</div>
              <p>No victim locations found</p>
              <p className="empty-subtitle">Waiting for location data...</p>
            </div>
          )}
          <MapContainer
            center={defaultCenter}
            zoom={defaultZoom}
            style={{ height: '100%', width: '100%' }}
            ref={mapRef}
            scrollWheelZoom={true}
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapBoundsUpdater locations={locations} />
            
            {locations.map((location, index) => {
              const batteryPercentage = location.battery_percentage || 0;
              const isSelected = selectedLocation?.phone_id === location.phone_id;
              
              return (
                <Marker
                  key={location.phone_id || index}
                  position={[location.latitude, location.longitude]}
                  icon={createVictimIcon(batteryPercentage, isSelected)}
                  eventHandlers={{
                    click: () => handleMarkerClick(location),
                    mouseover: () => handleMarkerClick(location),
                  }}
                >
                  <Popup className="victim-popup">
                    <div className="popup-content">
                      <div className="popup-header">
                        <h3>👤 Victim Location</h3>
                        <div className="popup-battery" style={{ color: getBatteryColor(batteryPercentage) }}>
                          <span className="battery-icon">{getBatteryIcon(batteryPercentage)}</span>
                          <span className="battery-percentage">{batteryPercentage}%</span>
                          <span className="battery-status">{getBatteryStatus(batteryPercentage)}</span>
                        </div>
                      </div>
                      <div className="popup-body">
                        <div className="popup-row">
                          <span className="popup-label">ID:</span>
                          <span className="popup-value">{location.phone_id}</span>
                        </div>
                        <div className="popup-row">
                          <span className="popup-label">Coordinates:</span>
                          <div className="popup-coords">
                            <div>Lat: {location.latitude.toFixed(6)}</div>
                            <div>Lon: {location.longitude.toFixed(6)}</div>
                          </div>
                        </div>
                        <div className="popup-row">
                          <span className="popup-label">Accuracy:</span>
                          <span className="popup-value">{location.accuracy?.toFixed(1) || 'N/A'}m</span>
                        </div>
                        <div className="popup-row">
                          <span className="popup-label">Last Seen:</span>
                          <span className="popup-value-small">{formatDateTime(location.last_seen || location.timestamp)}</span>
                        </div>
                      </div>
                    </div>
                  </Popup>
                </Marker>
              );
            })}
          </MapContainer>
        </>
      )}
    </div>
  );
}

export default MapView;
