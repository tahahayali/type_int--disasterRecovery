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
      const response = await fetch(`${API_BASE_URL}/api/all`);
      const data = await response.json();
      if (data.status === 'success') {
        // Transform user data to location format expected by MapView
        const transformedLocations = data.users
          .filter(user => user.location && user.location.lat !== null && user.location.long !== null)
          .map(user => ({
            phone_id: user.uuid,
            uuid: user.uuid,
            type: user.type,
            name: user.name,
            age: user.age,
            height: user.height,
            weight: user.weight,
            medical: user.medical,
            latitude: user.location.lat,
            longitude: user.location.long,
            location_last_updated: user.location.last_updated,
            battery_percentage: user.battery?.percentage || 0,
            battery_time_left: user.battery?.time_left_till_off || 0,
            battery_last_updated: user.battery?.last_updated,
            messages: user.messages || [],
            emergency_questionaire: user.emergency_questionaire,
            created_at: user.created_at,
            updated_at: user.updated_at
          }));
        setLocations(transformedLocations);
        setError(null);
      } else {
        setError(data.error || 'Failed to fetch users');
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
    // Mock data generation removed - users must sign up via /api/signup
    console.log('Mock data generation disabled. Use /api/signup to create users.');
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

