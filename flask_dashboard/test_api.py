"""
Simple test script to test the Flask API endpoints
Run this after starting the Flask server to test the API
"""

import requests
import json
import time

API_BASE_URL = "http://localhost:5000"

def test_health_check():
    """Test health check endpoint"""
    print("Testing health check...")
    response = requests.get(f"{API_BASE_URL}/api/health")
    print(f"Status: {response.status_code}")
    print(f"Response: {json.dumps(response.json(), indent=2)}")
    print()

def test_mock_data():
    """Test mock data generation"""
    print("Testing mock data generation...")
    response = requests.post(
        f"{API_BASE_URL}/api/mock-data",
        json={"count": 5}
    )
    print(f"Status: {response.status_code}")
    print(f"Response: {json.dumps(response.json(), indent=2)}")
    print()

def test_phone_data():
    """Test receiving phone data"""
    print("Testing phone data reception...")
    data = {
        "phone_id": "test_phone_1",
        "latitude": 42.8864,
        "longitude": -78.8784,
        "accuracy": 10.5
    }
    response = requests.post(
        f"{API_BASE_URL}/api/phone-data",
        json=data
    )
    print(f"Status: {response.status_code}")
    print(f"Response: {json.dumps(response.json(), indent=2)}")
    print()

def test_get_locations():
    """Test getting locations"""
    print("Testing get locations...")
    response = requests.get(f"{API_BASE_URL}/api/locations")
    print(f"Status: {response.status_code}")
    data = response.json()
    print(f"Locations count: {data.get('count', 0)}")
    if data.get('locations'):
        print(f"First location: {json.dumps(data['locations'][0], indent=2, default=str)}")
    print()

def test_stats():
    """Test stats endpoint"""
    print("Testing stats...")
    response = requests.get(f"{API_BASE_URL}/api/stats")
    print(f"Status: {response.status_code}")
    print(f"Response: {json.dumps(response.json(), indent=2, default=str)}")
    print()

if __name__ == "__main__":
    print("=" * 50)
    print("Testing Law Enforcement Dashboard API")
    print("=" * 50)
    print()
    
    try:
        test_health_check()
        test_mock_data()
        time.sleep(1)  # Wait a bit
        test_phone_data()
        time.sleep(1)  # Wait a bit
        test_get_locations()
        test_stats()
        
        print("=" * 50)
        print("All tests completed!")
        print("=" * 50)
    except requests.exceptions.ConnectionError:
        print("Error: Could not connect to Flask server.")
        print("Make sure the Flask server is running on http://localhost:5000")
    except Exception as e:
        print(f"Error: {e}")

