"""
Flask API Server for Music Mood Detection & Recommendation System
Integrates: Chat AI, Music Emotion Detection (musicnn), Music Recommendation (XRec)
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import numpy as np
import tensorflow as tf
import torch
import librosa
import json
from datetime import datetime
import os
from werkzeug.utils import secure_filename

app = Flask(__name__)
CORS(app)

# Configuration
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB max file size
app.config['UPLOAD_FOLDER'] = 'uploads'
ALLOWED_AUDIO_EXTENSIONS = {'mp3', 'wav', 'flac', 'ogg'}

# Ensure upload folder exists
os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)

# ============================================================================
# MODEL LOADING (Placeholder - You'll replace with actual model loading)
# ============================================================================

class ModelManager:
    def __init__(self):
        self.chat_model = None
        self.emotion_model = None
        self.recommendation_model = None

    def load_chat_model(self):
        """Load conversational AI model"""
        print("Loading Chat AI model...")
        # TODO: Load your actual chat model here
        # For now, we'll use a simple rule-based system
        self.chat_model = "simple_chatbot"
        print("Chat AI model loaded successfully")

    def load_emotion_model(self):
        """Load musicnn emotion detection model"""
        print("Loading Music Emotion Detection model...")
        # TODO: Load musicnn model
        # Example: self.emotion_model = tf.keras.models.load_model('models/musicnn_model.h5')
        self.emotion_model = "musicnn_placeholder"
        print("Emotion Detection model loaded successfully")

    def load_recommendation_model(self):
        """Load XRec recommendation model"""
        print("Loading Music Recommendation model...")
        # TODO: Load XRec model
        # Example: self.recommendation_model = torch.load('models/xrec_model.pt')
        self.recommendation_model = "xrec_placeholder"
        print("Recommendation model loaded successfully")

# Initialize model manager
model_manager = ModelManager()

# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_AUDIO_EXTENSIONS

def extract_audio_features(audio_path):
    """Extract audio features using librosa for emotion detection"""
    try:
        # Load audio file
        y, sr = librosa.load(audio_path, duration=30)  # Load first 30 seconds

        # Extract features
        features = {
            'mfcc': librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13).mean(axis=1).tolist(),
            'spectral_centroid': float(librosa.feature.spectral_centroid(y=y, sr=sr).mean()),
            'spectral_rolloff': float(librosa.feature.spectral_rolloff(y=y, sr=sr).mean()),
            'zero_crossing_rate': float(librosa.feature.zero_crossing_rate(y).mean()),
            'tempo': float(librosa.beat.tempo(y=y, sr=sr)[0]),
            'chroma': librosa.feature.chroma_stft(y=y, sr=sr).mean(axis=1).tolist()
        }

        return features
    except Exception as e:
        print(f"Error extracting audio features: {str(e)}")
        return None

def predict_emotion_from_audio(audio_features):
    """Predict emotion from audio features using musicnn"""
    # TODO: Implement actual musicnn prediction
    # This is a placeholder that returns mock emotions

    emotions = {
        'happy': 0.7,
        'energetic': 0.6,
        'calm': 0.3,
        'sad': 0.2,
        'angry': 0.1
    }

    return emotions

def predict_emotion_from_text(text):
    """Predict emotion from user text input"""
    # TODO: Implement text-based emotion analysis
    # Simple keyword-based approach for now

    emotion_keywords = {
        'happy': ['happy', 'joy', 'excited', 'great', 'awesome'],
        'sad': ['sad', 'depressed', 'lonely', 'down', 'upset'],
        'calm': ['calm', 'relaxed', 'peaceful', 'chill', 'mellow'],
        'energetic': ['energetic', 'pumped', 'active', 'motivated'],
        'angry': ['angry', 'mad', 'frustrated', 'annoyed']
    }

    text_lower = text.lower()
    detected_emotions = {}

    for emotion, keywords in emotion_keywords.items():
        score = sum(1 for keyword in keywords if keyword in text_lower)
        if score > 0:
            detected_emotions[emotion] = min(score / len(keywords), 1.0)

    if not detected_emotions:
        detected_emotions = {'neutral': 1.0}

    return detected_emotions

# ============================================================================
# API ENDPOINTS
# ============================================================================

@app.route('/', methods=['GET'])
def home():
    """Health check endpoint"""
    return jsonify({
        'status': 'online',
        'service': 'Music Mood Detection & Recommendation API',
        'version': '1.0.0',
        'endpoints': {
            'health': '/',
            'chat': '/api/chat',
            'emotion_audio': '/api/emotion/audio',
            'emotion_text': '/api/emotion/text',
            'recommend': '/api/recommend',
            'user_profile': '/api/user/profile'
        }
    })

@app.route('/api/chat', methods=['POST'])
def chat():
    """
    Conversational AI endpoint
    Request: { "message": "user message", "user_id": "123" }
    Response: { "response": "AI response", "timestamp": "..." }
    """
    try:
        data = request.get_json()
        user_message = data.get('message', '')
        user_id = data.get('user_id', 'anonymous')

        # TODO: Implement actual chat model inference
        # For now, simple rule-based responses
        response_message = f"I understand you said: '{user_message}'. How can I help you with music recommendations?"

        return jsonify({
            'success': True,
            'response': response_message,
            'user_id': user_id,
            'timestamp': datetime.now().isoformat()
        })

    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 400

@app.route('/api/emotion/audio', methods=['POST'])
def detect_emotion_audio():
    """
    Detect emotion from audio file
    Request: multipart/form-data with 'audio' file
    Response: { "emotions": {...}, "dominant_emotion": "...", "confidence": 0.85 }
    """
    try:
        # Check if audio file is present
        if 'audio' not in request.files:
            return jsonify({
                'success': False,
                'error': 'No audio file provided'
            }), 400

        audio_file = request.files['audio']

        if audio_file.filename == '':
            return jsonify({
                'success': False,
                'error': 'Empty filename'
            }), 400

        if not allowed_file(audio_file.filename):
            return jsonify({
                'success': False,
                'error': f'Invalid file type. Allowed: {ALLOWED_AUDIO_EXTENSIONS}'
            }), 400

        # Save audio file temporarily
        filename = secure_filename(audio_file.filename)
        filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
        audio_file.save(filepath)

        # Extract audio features
        features = extract_audio_features(filepath)

        if features is None:
            return jsonify({
                'success': False,
                'error': 'Failed to extract audio features'
            }), 500

        # Predict emotion
        emotions = predict_emotion_from_audio(features)

        # Find dominant emotion
        dominant_emotion = max(emotions.items(), key=lambda x: x[1])

        # Clean up uploaded file
        os.remove(filepath)

        return jsonify({
            'success': True,
            'emotions': emotions,
            'dominant_emotion': dominant_emotion[0],
            'confidence': dominant_emotion[1],
            'timestamp': datetime.now().isoformat()
        })

    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

@app.route('/api/emotion/text', methods=['POST'])
def detect_emotion_text():
    """
    Detect emotion from text input
    Request: { "text": "I'm feeling great today!" }
    Response: { "emotions": {...}, "dominant_emotion": "...", "confidence": 0.75 }
    """
    try:
        data = request.get_json()
        text = data.get('text', '')

        if not text:
            return jsonify({
                'success': False,
                'error': 'No text provided'
            }), 400

        # Predict emotion from text
        emotions = predict_emotion_from_text(text)

        # Find dominant emotion
        dominant_emotion = max(emotions.items(), key=lambda x: x[1])

        return jsonify({
            'success': True,
            'emotions': emotions,
            'dominant_emotion': dominant_emotion[0],
            'confidence': dominant_emotion[1],
            'timestamp': datetime.now().isoformat()
        })

    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

@app.route('/api/recommend', methods=['POST'])
def recommend_music():
    """
    Get music recommendations based on emotion and listening history
    Request: {
        "user_id": "123",
        "emotion": "happy",
        "listening_history": ["song1", "song2"],
        "top_k": 10
    }
    Response: {
        "recommendations": [
            {"song_id": "...", "title": "...", "artist": "...", "explanation": "..."}
        ]
    }
    """
    try:
        data = request.get_json()
        user_id = data.get('user_id', 'anonymous')
        emotion = data.get('emotion', 'neutral')
        listening_history = data.get('listening_history', [])
        top_k = data.get('top_k', 10)

        # TODO: Implement actual XRec recommendation
        # Mock recommendations for now
        recommendations = []
        for i in range(top_k):
            recommendations.append({
                'song_id': f'song_{i+1}',
                'title': f'Song Title {i+1}',
                'artist': f'Artist {i+1}',
                'genre': 'Pop',
                'mood': emotion,
                'score': 0.9 - (i * 0.05),
                'explanation': f'Recommended because it matches your {emotion} mood and is similar to songs in your listening history.'
            })

        return jsonify({
            'success': True,
            'user_id': user_id,
            'emotion': emotion,
            'recommendations': recommendations,
            'timestamp': datetime.now().isoformat()
        })

    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

@app.route('/api/user/profile', methods=['POST'])
def update_user_profile():
    """
    Update user profile with listening history
    Request: {
        "user_id": "123",
        "song_id": "song_1",
        "rating": 5,
        "emotion": "happy"
    }
    Response: { "success": true, "message": "Profile updated" }
    """
    try:
        data = request.get_json()
        user_id = data.get('user_id')
        song_id = data.get('song_id')
        rating = data.get('rating', 0)
        emotion = data.get('emotion', 'neutral')

        # TODO: Store in database
        # For now, just acknowledge

        return jsonify({
            'success': True,
            'message': 'User profile updated successfully',
            'user_id': user_id,
            'timestamp': datetime.now().isoformat()
        })

    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

# ============================================================================
# ERROR HANDLERS
# ============================================================================

@app.errorhandler(404)
def not_found(e):
    return jsonify({
        'success': False,
        'error': 'Endpoint not found'
    }), 404

@app.errorhandler(500)
def internal_error(e):
    return jsonify({
        'success': False,
        'error': 'Internal server error'
    }), 500

# ============================================================================
# MAIN
# ============================================================================

if __name__ == '__main__':
    print("="*60)
    print("Starting Music Mood Detection & Recommendation API Server")
    print("="*60)

    # Load models
    model_manager.load_chat_model()
    model_manager.load_emotion_model()
    model_manager.load_recommendation_model()

    print("\nServer ready! Available endpoints:")
    print("  - GET  /                       : Health check")
    print("  - POST /api/chat               : Conversational AI")
    print("  - POST /api/emotion/audio      : Emotion from audio")
    print("  - POST /api/emotion/text       : Emotion from text")
    print("  - POST /api/recommend          : Music recommendations")
    print("  - POST /api/user/profile       : Update user profile")
    print("\n" + "="*60)

    app.run(host='0.0.0.0', port=5000, debug=True)