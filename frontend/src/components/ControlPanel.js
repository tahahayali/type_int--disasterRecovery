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

  const formatDateTime = (dateString) => {
    if (!dateString) return 'N/A';
    try {
      const date = new Date(dateString);
      return date.toLocaleString();
    } catch {
      return dateString;
    }
  };

  const getBatteryColor = (percentage) => {
    if (percentage >= 50) return '#10b981'; // Green
    if (percentage >= 20) return '#f59e0b'; // Yellow
    return '#ef4444'; // Red
  };

  const getBatteryIcon = (percentage) => {
    if (percentage >= 75) return '🔋';
    if (percentage >= 50) return '🔋';
    if (percentage >= 20) return '🪫';
    return '🪫';
  };

  return (
    <div className="ControlPanel">
      <div className="panel-section">
        <h2>📊 Statistics</h2>
        {error && (
          <div className="error-message">
            ⚠️ {error}
          </div>
        )}
        {stats ? (
          <div className="stats-grid">
            <div className="stat-item">
              <div className="stat-label">Active Victims</div>
              <div className="stat-value">{stats.unique_phones || 0}</div>
            </div>
            <div className="stat-item">
              <div className="stat-label">Total Records</div>
              <div className="stat-value">{stats.total_locations || 0}</div>
            </div>
            <div className="stat-item stat-item-full">
              <div className="stat-label">Pending Sync</div>
              <div className="stat-value-small">{stats.buffered_phones || 0} locations</div>
            </div>
          </div>
        ) : (
          <div className="loading-text">Loading statistics...</div>
        )}
      </div>

      {selectedLocation && (
        <div className="panel-section selected-location-section">
          <h2>{selectedLocation.type === 'first_responder' ? '👤 Selected First Responder' : '📍 Selected Victim'}</h2>
          <div className="location-card">
            <div className="location-header">
              <span className="victim-id">{selectedLocation.phone_id}</span>
              <div className="battery-indicator" style={{ 
                color: selectedLocation.type === 'first_responder' ? '#3b82f6' : getBatteryColor(selectedLocation.battery_percentage || 0) 
              }}>
                <span className="battery-icon">{getBatteryIcon(selectedLocation.battery_percentage || 0)}</span>
                <span className="battery-text">{selectedLocation.battery_percentage || 0}%</span>
              </div>
            </div>
            <div className="location-info">
              <div className="info-row">
                <span className="info-label">Coordinates</span>
                <span className="info-value">
                  {selectedLocation.latitude.toFixed(6)}, {selectedLocation.longitude.toFixed(6)}
                </span>
              </div>
              <div className="info-row">
                <span className="info-label">Accuracy</span>
                <span className="info-value">{selectedLocation.accuracy?.toFixed(1) || 'N/A'}m</span>
              </div>
              <div className="info-row">
                <span className="info-label">Last Seen</span>
                <span className="info-value-small">
                  {formatDateTime(selectedLocation.last_seen || selectedLocation.timestamp)}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="panel-section">
        <h2>⚙️ Controls</h2>
        <div className="control-group">
          <button 
            className="btn btn-primary" 
            onClick={onRefresh}
            disabled={loading}
          >
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
          <p>💾 Data sync: Every 5 minutes</p>
          <p>📍 Initial data: 10 victims + 3-5 first responders (initialized on startup, locations saved to DB)</p>
        </div>
      </div>
    </div>
  );
}

export default ControlPanel;
