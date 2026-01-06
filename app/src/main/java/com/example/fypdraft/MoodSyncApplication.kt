package com.example.fypdraft

import android.app.Application
import com.google.firebase.FirebaseApp

class MoodSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}