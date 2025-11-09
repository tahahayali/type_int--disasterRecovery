// File: MapView.js
import React, { useEffect, useRef, useMemo } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import { v4 as uuidv4 } from 'uuid';
import './MapView.css';

// Fix for default marker icons in React-Leaflet
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

// Battery color helper
const getBatteryColor = (percentage) => {
  if (percentage >= 50) return '#10b981';
  if (percentage >= 20) return '#f59e0b';
  return '#ef4444';
};

// Custom victim marker
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

// Custom first responder marker
const createFirstResponderIcon = (isSelected = false) => {
  const borderColor = isSelected ? '#1e40af' : '#3b82f6';
  const borderWidth = isSelected ? '4px' : '3px';
  return L.divIcon({
    className: 'first-responder-marker',
    html: `<div class="responder-marker-container" style="border-color: ${borderColor}; border-width: ${borderWidth};">
            <div class="responder-marker-pin" style="background: #3b82f6;">
              <span class="responder-icon">👤</span>
            </div>
          </div>`,
    iconSize: [60, 60],
    iconAnchor: [30, 60],
    popupAnchor: [0, -60],
  });
};

// Adjust map bounds when data changes
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
  const defaultCenter = [42.8864, -78.8784]; // Buffalo, NY
  const defaultZoom = 12;

  const handleMarkerClick = (location) => {
    onLocationSelect(location);
  };

  const getBatteryIcon = (percentage) => {
    if (percentage >= 50) return '🔋';
    if (percentage >= 20) return '🪫';
    return '🪫';
  };

  // Generate UUIDs for victims (persistent across renders)
  const victimUUIDs = useMemo(() => {
    const mapping = {};
    locations.forEach(loc => {
      if (loc.type === 'victim') {
        mapping[loc.phone_id] = uuidv4();
      }
    });
    return mapping;
  }, [locations]);

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
              const isFirstResponder = location.type === 'first_responder';
              const isSelected = selectedLocation?.phone_id === location.phone_id;
              const batteryPercentage = location.battery_percentage || 0;
              const uuid = victimUUIDs[location.phone_id];

              return (
                <Marker
                  key={location.phone_id || index}
                  position={[location.latitude, location.longitude]}
                  icon={
                    isFirstResponder
                      ? createFirstResponderIcon(isSelected)
                      : createVictimIcon(batteryPercentage, isSelected)
                  }
                  eventHandlers={{
                    click: () => handleMarkerClick(location),
                    mouseover: () => handleMarkerClick(location),
                  }}
                >
                  <Popup
                    className={isFirstResponder ? 'responder-popup' : 'victim-popup'}
                  >
                    <div className="popup-content">
                      <div
                        className="popup-header"
                        style={
                          isFirstResponder
                            ? {
                                background:
                                  'linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%)',
                              }
                            : {}
                        }
                      >
                        <h3>
                          {isFirstResponder
                            ? '👤 First Responder'
                            : '📍 Victim Location'}
                        </h3>
                      </div>

                      <div className="popup-body">
                        {isFirstResponder ? (
                          <div className="popup-row">
                            <span className="popup-label">Active Location:</span>
                            <div className="popup-coords">
                              <div>Lat: {location.latitude.toFixed(6)}</div>
                              <div>Lon: {location.longitude.toFixed(6)}</div>
                            </div>
                          </div>
                        ) : (
                          <>
                            <div className="popup-row">
                              <span className="popup-label">Phone ID:</span>
                              <span className="popup-value">{location.phone_id}</span>
                            </div>
                            <div className="popup-row">
                              <span className="popup-label">User ID:</span>
                              <span className="popup-value">{uuid}</span>
                            </div>
                            <div className="popup-row">
                              <span className="popup-label">Battery:</span>
                              <div
                                className="popup-battery"
                                style={{ color: getBatteryColor(batteryPercentage) }}
                              >
                                <span className="battery-icon">
                                  {getBatteryIcon(batteryPercentage)}
                                </span>
                                <span className="battery-percentage">
                                  {batteryPercentage}%
                                </span>
                              </div>
                            </div>
                            <div className="popup-row">
                              <span className="popup-label">Last Recorded Location:</span>
                              <div className="popup-coords">
                                <div>Lat: {location.latitude.toFixed(6)}</div>
                                <div>Lon: {location.longitude.toFixed(6)}</div>
                              </div>
                            </div>
                          </>
                        )}
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





