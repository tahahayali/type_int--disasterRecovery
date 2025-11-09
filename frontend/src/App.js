import React, { useState, useEffect } from 'react';
import MapView from './components/MapView';
import ControlPanel from './components/ControlPanel';
import './App.css';

// Use relative URL when served from Flask, or use env variable for development
const API_BASE_URL = process.env.REACT_APP_API_URL || '';

function App() {
  const [locations, setLocations] = useState([]);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedLocation, setSelectedLocation] = useState(null);

  const fetchLocations = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/locations`);
      const data = await response.json();
      if (data.status === 'success') {
        setLocations(data.locations);
        setError(null);
      } else {
        setError(data.error || 'Failed to fetch locations');
      }
    } catch (err) {
      setError('Failed to connect to server. Make sure Flask backend is running.');
      console.error('Error fetching locations:', err);
    } finally {
      setLoading(false);
    }
  };

  const fetchStats = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/stats`);
      const data = await response.json();
      if (data.status === 'success') {
        setStats(data);
      }
    } catch (err) {
      console.error('Error fetching stats:', err);
    }
  };

  const generateMockData = async (count = 5) => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/mock-data`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ count }),
      });
      const data = await response.json();
      if (data.status === 'success') {
        // Refresh locations after generating mock data
        setTimeout(() => {
          fetchLocations();
          fetchStats();
        }, 500);
      }
    } catch (err) {
      console.error('Error generating mock data:', err);
    }
  };

  useEffect(() => {
    // Initial fetch
    fetchLocations();
    fetchStats();

    // Set up auto-refresh every 15 seconds to match mock data generation
    const interval = setInterval(() => {
      fetchLocations();
      fetchStats();
    }, 15000);

    return () => clearInterval(interval);
  }, []);

  return (
    <div className="App">
      <header className="App-header">
        <h1>🚨 Emergency Response Dashboard</h1>
        <p>Real-time Victim Location Tracking</p>
      </header>
      <div className="App-content">
        <ControlPanel
          stats={stats}
          loading={loading}
          error={error}
          onGenerateMockData={generateMockData}
          onRefresh={fetchLocations}
          selectedLocation={selectedLocation}
        />
        <MapView
          locations={locations}
          loading={loading}
          onLocationSelect={setSelectedLocation}
          selectedLocation={selectedLocation}
        />
      </div>
    </div>
  );
}

export default App;

