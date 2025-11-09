// File: ControlPanel.js
import React, { useState } from 'react';
import './ControlPanel.css';

function ControlPanel({ stats, loading, error, onGenerateMockData, onRefresh, selectedLocation }) {
  const [mockCount, setMockCount] = useState(5);
  const [isGenerating, setIsGenerating] = useState(false);

  const handleGenerateMock = async () => {
    setIsGenerating(true);
    await onGenerateMockData(mockCount);
    setIsGenerating(false);
  };

  const getBatteryColor = (percentage) => {
    if (percentage >= 50) return '#10b981';
    if (percentage >= 20) return '#f59e0b';
    return '#ef4444';
  };

  const getBatteryIcon = (percentage) => {
    if (percentage >= 75) return '🔋';
    if (percentage >= 50) return '🔋';
    if (percentage >= 20) return '🪫';
    return '🪫';
  };

  // Format battery time left
  const formatTimeLeft = (seconds) => {
    if (!seconds || seconds === 0) return 'Unknown';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }
    return `${minutes}m`;
  };

  return (
    <div className="ControlPanel">
      <div className="panel-section">
        <h2>📊 Statistics</h2>
        {error && <div className="error-message">⚠️ {error}</div>}
        {stats ? (
          <div className="stats-grid">
            <div className="stat-item">
              <div className="stat-label">Victims</div>
              <div className="stat-value">{stats.victims || 0}</div>
            </div>
            <div className="stat-item">
              <div className="stat-label">First Responders</div>
              <div className="stat-value">{stats.first_responders || 0}</div>
            </div>
            <div className="stat-item stat-item-full">
              <div className="stat-label">Total Users</div>
              <div className="stat-value-small">{stats.total_users || 0} users</div>
            </div>
          </div>
        ) : (
          <div className="loading-text">Loading statistics...</div>
        )}
      </div>

      {selectedLocation && (
        <div className="panel-section selected-location-section">
          <h2>
            {selectedLocation.type === 'first_responder'
              ? '👤 Selected First Responder'
              : '📍 Selected Victim'}
          </h2>
          <div className="location-card">
            <div className="location-header">
              <span className="victim-id">{selectedLocation.phone_id}</span>
              <div
                className="battery-indicator"
                style={{
                  color:
                    selectedLocation.type === 'first_responder'
                      ? '#3b82f6'
                      : getBatteryColor(selectedLocation.battery_percentage || 0),
                }}
              >
                <span className="battery-icon">
                  {getBatteryIcon(selectedLocation.battery_percentage || 0)}
                </span>
                <span className="battery-text">
                  {selectedLocation.battery_percentage || 0}%
                </span>
              </div>
            </div>

            <div className="location-info">
              <div className="info-row multiline">
                <span className="info-label">UUID:</span>
                <span className="info-value break-word">{selectedLocation.uuid}</span>
              </div>

              {selectedLocation.type === 'victim' && (
                <>
                  {selectedLocation.name && (
                    <div className="info-row multiline">
                      <span className="info-label">Name:</span>
                      <span className="info-value">{selectedLocation.name}</span>
                    </div>
                  )}

                  {(selectedLocation.age || selectedLocation.height || selectedLocation.weight) && (
                    <div className="info-row multiline">
                      <span className="info-label">Profile:</span>
                      <div className="info-value-small">
                        {selectedLocation.age && <div>Age: {selectedLocation.age}</div>}
                        {selectedLocation.height && <div>Height: {selectedLocation.height}</div>}
                        {selectedLocation.weight && <div>Weight: {selectedLocation.weight}</div>}
                      </div>
                    </div>
                  )}

                  {selectedLocation.medical && (
                    <div className="info-row multiline">
                      <span className="info-label">Medical Info:</span>
                      <div className="info-value-small">
                        {selectedLocation.medical}
                      </div>
                    </div>
                  )}

                  {selectedLocation.battery_time_left > 0 && (
                    <div className="info-row multiline">
                      <span className="info-label">Battery Time:</span>
                      <span className="info-value-small">
                        {formatTimeLeft(selectedLocation.battery_time_left)}
                      </span>
                    </div>
                  )}

                  {selectedLocation.messages && selectedLocation.messages.length > 0 && (
                    <div className="info-row multiline">
                      <span className="info-label">Messages:</span>
                      <span className="info-value-small">
                        {selectedLocation.messages.length} message(s)
                      </span>
                    </div>
                  )}
                </>
              )}

              <div className="info-row multiline">
                <span className="info-label">Location:</span>
                <div className="info-value break-word">
                  <div>Lat: {selectedLocation.latitude.toFixed(6)}</div>
                  <div>Lon: {selectedLocation.longitude.toFixed(6)}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="panel-section">
        <h2>⚙️ Controls</h2>
        <div className="control-group">
          <button className="btn btn-primary" onClick={onRefresh} disabled={loading}>
            {loading ? '⏳ Refreshing...' : '🔄 Refresh Now'}
          </button>
        </div>

        <div className="control-group">
          <label htmlFor="mock-count">Manual Test Data</label>
          <div className="input-group">
            <input
              id="mock-count"
              type="number"
              min="1"
              max="50"
              value={mockCount}
              onChange={(e) => setMockCount(parseInt(e.target.value) || 1)}
              className="input-field"
              placeholder="Count"
            />
            <button
              className="btn btn-secondary btn-inline"
              onClick={handleGenerateMock}
              disabled={isGenerating}
            >
              {isGenerating ? '⏳' : '➕ Generate'}
            </button>
          </div>
        </div>
      </div>

      <div className="panel-section info-section">
        <h2>ℹ️ System Info</h2>
        <div className="info-text">
          <p>🔄 Auto-refresh: Every 15 seconds</p>
          <p>💾 Real-time data from MongoDB</p>
          <p>📍 Use /api/signup to create users</p>
          <p>📡 Use /api/byte_string to update data</p>
        </div>
      </div>
    </div>
  );
}

export default ControlPanel;


