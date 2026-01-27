"""
Simple Flask Backend Test - Use this to verify your setup works
Once this works, switch to the full app.py
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
from datetime import datetime

app = Flask(__name__)
CORS(app)

@app.route('/')
def home():
    return jsonify({
        "message": "Music AI Backend is running!",
        "status": "ok"
    })

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "message": "Server is running correctly"
    })

@app.route('/api/analyze', methods=['POST'])
def analyze():
    """Test analyze endpoint"""
    try:
        data = request.get_json()

        if not data or 'text' not in data:
            return jsonify({"error": "Missing 'text' field in request"}), 400

        user_input = data['text']

        # Simple keyword-based emotion detection
        text_lower = user_input.lower()
        emotion = "neutral"

        if any(word in text_lower for word in ["happy", "joy", "excited", "great"]):
            emotion = "happy"
        elif any(word in text_lower for word in ["sad", "depressed", "down"]):
            emotion = "sad"
        elif any(word in text_lower for word in ["angry", "mad", "frustrated"]):
            emotion = "angry"
        elif any(word in text_lower for word in ["calm", "peaceful", "relaxed"]):
            emotion = "calm"
        elif any(word in text_lower for word in ["stressed", "anxious", "worried"]):
            emotion = "anxious"
        elif any(word in text_lower for word in ["energetic", "pumped", "motivated"]):
            emotion = "energetic"

        # Simple recommendations
        recommendations = [
            {
                "songTitle": "Test Song 1",
                "artist": "Test Artist 1",
                "reason": f"Good for {emotion} mood",
                "mood": emotion
            },
            {
                "songTitle": "Test Song 2",
                "artist": "Test Artist 2",
                "reason": f"Matches your {emotion} feeling",
                "mood": emotion
            }
        ]

        response = {
            "emotion": emotion,
            "emotionConfidence": 0.85,
            "conversationalReply": f"I can see you're feeling {emotion}. Let me help you with some music!",
            "explanation": f"For your {emotion} mood, I've selected songs that should resonate well.",
            "musicRecommendations": recommendations,
            "timestamp": datetime.now().isoformat()
        }

        return jsonify(response)

    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    print("=" * 50)
    print("🎵 Music AI Backend Test Server")
    print("=" * 50)
    print("Server starting on http://localhost:5000")
    print("\nTest endpoints:")
    print("  GET  http://localhost:5000/")
    print("  GET  http://localhost:5000/health")
    print("  POST http://localhost:5000/api/analyze")
    print("=" * 50)

    app.run(host='0.0.0.0', port=5000, debug=True)