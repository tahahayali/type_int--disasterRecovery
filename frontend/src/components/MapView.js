// File: MapView.js
import React, { useEffect, useRef, useMemo, useState } from 'react';
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

const getBatteryColor = (percentage) => {
  if (percentage >= 50) return '#10b981';
  if (percentage >= 20) return '#f59e0b';
  return '#ef4444';
};

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
      const bounds = locations.map((loc) => [loc.latitude, loc.longitude]);
      if (bounds.length > 0) {
        map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
      }
    }
  }, [locations, map]);
  return null;
}

function MapView({ locations, loading, onLocationSelect, selectedLocation }) {
  const mapRef = useRef(null);
  const [savedVictims, setSavedVictims] = useState([]); // NEW: track saved victims
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

  const formatTimeLeft = (seconds) => {
    if (!seconds || seconds === 0) return 'Unknown';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }
    return `${minutes}m`;
  };

  const parseQuestionnaire = (questionnaireStr) => {
    if (!questionnaireStr || questionnaireStr.length !== 7) return null;
    const questions = [
      'Injured?',
      'Trapped?',
      'Medical Emergency?',
      'Need Water?',
      'Need Food?',
      'Need Shelter?',
      'Other Help?'
    ];
    return questions.map((q, i) => ({
      question: q,
      answer: questionnaireStr[i] === '1' ? 'Yes' : 'No'
    }));
  };

  // Filter out saved victims so they don’t show on the map
  const visibleLocations = useMemo(() => {
    return locations.filter(
      (loc) => loc.type !== 'victim' || !savedVictims.includes(loc.phone_id)
    );
  }, [locations, savedVictims]);

  const handleMarkAsSaved = (phone_id) => {
    setSavedVictims((prev) => [...prev, phone_id]);
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
            <MapBoundsUpdater locations={visibleLocations} />

            {visibleLocations.map((location, index) => {
              const isFirstResponder = location.type === 'first_responder';
              const isSelected = selectedLocation?.phone_id === location.phone_id;
              const batteryPercentage = location.battery_percentage || 0;
              const questionnaireData = parseQuestionnaire(location.emergency_questionaire);

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
                    maxHeight={400}
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
                            : '🆘 Victim Details'}
                        </h3>
                      </div>

                      <div className="popup-body">
                        {isFirstResponder ? (
                          <>
                            <div className="popup-row">
                              <span className="popup-label">ID:</span>
                              <span className="popup-value">{location.uuid}</span>
                            </div>
                            {location.name && (
                              <div className="popup-row">
                                <span className="popup-label">Name:</span>
                                <span className="popup-value">{location.name}</span>
                              </div>
                            )}
                            <div className="popup-row">
                              <span className="popup-label">Location:</span>
                              <div className="popup-coords">
                                <div>Lat: {location.latitude.toFixed(6)}</div>
                                <div>Lon: {location.longitude.toFixed(6)}</div>
                              </div>
                            </div>
                          </>
                        ) : (
                          <>
                            <div className="popup-row">
                              <span className="popup-label">UUID:</span>
                              <span className="popup-value">{location.uuid}</span>
                            </div>
                            
                            {location.name && (
                              <div className="popup-row">
                                <span className="popup-label">Name:</span>
                                <span className="popup-value">{location.name}</span>
                              </div>
                            )}
                            
                            {(location.age || location.height || location.weight) && (
                              <div className="popup-row">
                                <span className="popup-label">Profile:</span>
                                <div className="popup-value">
                                  {location.age && <div>Age: {location.age}</div>}
                                  {location.height && <div>Height: {location.height}</div>}
                                  {location.weight && <div>Weight: {location.weight}</div>}
                                </div>
                              </div>
                            )}
                            
                            {location.medical && (
                              <div className="popup-row">
                                <span className="popup-label">Medical:</span>
                                <span className="popup-value">{location.medical}</span>
                              </div>
                            )}
                            
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
                                {location.battery_time_left > 0 && (
                                  <span className="battery-time">
                                    ({formatTimeLeft(location.battery_time_left)})
                                  </span>
                                )}
                              </div>
                            </div>
                            
                            <div className="popup-row">
                              <span className="popup-label">Location:</span>
                              <div className="popup-coords">
                                <div>Lat: {location.latitude.toFixed(6)}</div>
                                <div>Lon: {location.longitude.toFixed(6)}</div>
                              </div>
                            </div>
                            
                            {questionnaireData && (
                              <div className="popup-row">
                                <span className="popup-label">Emergency Status:</span>
                                <div className="questionnaire-data">
                                  {questionnaireData.map((item, idx) => (
                                    <div key={idx} className="questionnaire-item">
                                      <span className={item.answer === 'Yes' ? 'status-yes' : 'status-no'}>
                                        {item.answer === 'Yes' ? '✓' : '✗'}
                                      </span>
                                      <span>{item.question}</span>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}
                            
                            {location.messages && location.messages.length > 0 && (
                              <div className="popup-row">
                                <span className="popup-label">Messages:</span>
                                <div className="messages-container">
                                  {location.messages.slice(-3).map((msg, idx) => (
                                    <div key={idx} className="message-item">
                                      <div className="message-time">
                                        {new Date(msg.time).toLocaleTimeString()}
                                      </div>
                                      <div className="message-text">{msg.message}</div>
                                    </div>
                                  ))}
                                  {location.messages.length > 3 && (
                                    <div className="message-more">
                                      +{location.messages.length - 3} more
                                    </div>
                                  )}
                                </div>
                              </div>
                            )}

                            <div className="popup-actions">
                              <button
                                className="btn-save"
                                onClick={() => handleMarkAsSaved(location.phone_id)}
                              >
                                ✅ Mark as Saved
                              </button>
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






