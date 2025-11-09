// File: ControlPanel.js
import React, { useState, useMemo } from 'react';
import { v4 as uuidv4 } from 'uuid';
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

  // Generate user_id & mock medical data for selected victim
  const userInfo = useMemo(() => {
    if (!selectedLocation || selectedLocation.type !== 'victim') return null;
    return {
      user_id: uuidv4(),
      asthma: Math.random() < 0.3,
      diabetes: Math.random() < 0.2,
      cardiac_conditions: Math.random() < 0.15,
    };
  }, [selectedLocation]);

  return (
    <div className="ControlPanel">
      <div className="panel-section">
        <h2>📊 Statistics</h2>
        {error && <div className="error-message">⚠️ {error}</div>}
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
              {selectedLocation.type === 'victim' && userInfo && (
                <>
                  <div className="info-row multiline">
                    <span className="info-label">User ID:</span>
                    <span className="info-value break-word">{userInfo.user_id}</span>
                  </div>

                  <div className="info-row multiline">
                    <span className="info-label">Medical Info:</span>
                    <div className="info-value-small">
                      <div>Asthma: {userInfo.asthma ? 'Yes' : 'No'}</div>
                      <div>Diabetes: {userInfo.diabetes ? 'Yes' : 'No'}</div>
                      <div>Cardiac: {userInfo.cardiac_conditions ? 'Yes' : 'No'}</div>
                    </div>
                  </div>
                </>
              )}

              <div className="info-row multiline">
                <span className="info-label">Last Recorded Location:</span>
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
          <p>💾 Data sync: Every 5 minutes</p>
          <p>
            📍 Initial data: 10 victims + 3–5 first responders (initialized on startup,
            locations saved to DB)
          </p>
        </div>
      </div>
    </div>
  );
}

export default ControlPanel;


