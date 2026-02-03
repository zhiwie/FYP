package com.example.fypdraft.data.api

/**
 * API Configuration for Backend Connection
 *
 * Backend is running on http://192.168.6.96:5000
 */
object ApiConfig {

    // ==========================================
    // Backend Configuration
    // ==========================================

    // Use your computer's local IP for Android emulator/device
    // 10.0.2.2 is the special IP for Android emulator to access host machine localhost
    // For real device on same network, use your computer's IP (192.168.x.x)

    // IMPORTANT: Change this based on your setup
    // Option 1: Android Emulator -> use "10.0.2.2"
    // Option 2: Real Device -> use your computer's IP (e.g., "192.168.6.96")
    const val BASE_URL = "http://192.168.6.96:5000"

    // For emulator, use:
    // const val BASE_URL = "http://10.0.2.2:5000"

    // ==========================================
    // API Endpoints
    // ==========================================

    const val HEALTH_ENDPOINT = "/health"
    const val ANALYZE_ENDPOINT = "/api/analyze"

    // Full URLs
    val HEALTH_URL = "$BASE_URL$HEALTH_ENDPOINT"
    val ANALYZE_URL = "$BASE_URL$ANALYZE_ENDPOINT"

    // ==========================================
    // Timeouts
    // ==========================================

    const val CONNECT_TIMEOUT = 30L // seconds
    const val READ_TIMEOUT = 30L // seconds
    const val WRITE_TIMEOUT = 30L // seconds
}