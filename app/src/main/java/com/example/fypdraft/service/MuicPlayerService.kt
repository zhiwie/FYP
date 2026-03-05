package com.example.fypdraft.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.fypdraft.MainActivity
import com.example.fypdraft.R
import com.example.fypdraft.viewmodel.MusicPlayerViewModel

class MusicPlayerService : Service() {

    private val TAG = "MusicPlayerService"
    private val CHANNEL_ID = "music_playback_channel"
    private val NOTIFICATION_ID = 1001

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val ACTION_PLAY  = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_NEXT  = "ACTION_NEXT"
        const val ACTION_PREV  = "ACTION_PREV"
        const val ACTION_STOP  = "ACTION_STOP"

        @Volatile
        var viewModel: MusicPlayerViewModel? = null

        var lastTitle: String = "MoodSync"
        var lastArtist: String = "No track playing"
        var lastIsPlaying: Boolean = false

        fun startService(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, MusicPlayerService::class.java))
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }

    private val binder = MusicBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        setupMediaSession()

        val placeholder = buildNotification(lastTitle, lastArtist, lastIsPlaying, null)
        startForeground(NOTIFICATION_ID, placeholder)

        Log.d(TAG, "MusicPlayerService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand: action=$action, viewModel=${if (viewModel != null) "set" else "NULL"}")

        when (action) {
            ACTION_PLAY -> {
                Log.d(TAG, "▶ Play tapped from notification")
                if (viewModel != null) {
                    viewModel?.play()
                    // Update notification to show pause button immediately
                    updateNotification(lastTitle, lastArtist, true)
                } else {
                    Log.w(TAG, "ViewModel is null — launching app to restore session")
                    launchApp()
                }
            }
            ACTION_PAUSE -> {
                Log.d(TAG, "⏸ Pause tapped from notification")
                if (viewModel != null) {
                    viewModel?.pause()
                    // Update notification to show play button immediately
                    updateNotification(lastTitle, lastArtist, false)
                } else {
                    launchApp()
                }
            }
            ACTION_NEXT -> {
                Log.d(TAG, "⏭ Next tapped from notification")
                if (viewModel != null) {
                    viewModel?.playNext()
                } else {
                    launchApp()
                }
            }
            ACTION_PREV -> {
                Log.d(TAG, "⏮ Prev tapped from notification")
                if (viewModel != null) {
                    viewModel?.playPrevious()
                } else {
                    launchApp()
                }
            }
            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                Log.d(TAG, "Service restarted with no action, showing last known notification")
                updateNotification(lastTitle, lastArtist, lastIsPlaying)
            }
        }
        return START_NOT_STICKY
    }

    private fun launchApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    fun updateNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        albumArt: Bitmap? = null
    ) {
        lastTitle = title
        lastArtist = artist
        lastIsPlaying = isPlaying

        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .build()
        )

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING
        else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .build()
        )

        val notification = buildNotification(title, artist, isPlaying, albumArt)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        albumArt: Bitmap?
    ): Notification {

        val openAppPending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // FIX: Use distinct actions and FLAG_UPDATE_CURRENT so intents are always fresh
        val prevPending = PendingIntent.getService(
            this, 1,
            Intent(this, MusicPlayerService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPausePending = PendingIntent.getService(
            this, 2,
            Intent(this, MusicPlayerService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextPending = PendingIntent.getService(
            this, 3,
            Intent(this, MusicPlayerService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val art = albumArt
            ?: BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_background)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(art)
            .setContentIntent(openAppPending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPausePending
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MoodSyncMediaSession").apply {
            isActive = true
            // FIX: Handle media button events through MediaSession callbacks too
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "MediaSession callback: onPlay")
                    viewModel?.play()
                    updateNotification(lastTitle, lastArtist, true)
                }

                override fun onPause() {
                    Log.d(TAG, "MediaSession callback: onPause")
                    viewModel?.pause()
                    updateNotification(lastTitle, lastArtist, false)
                }

                override fun onSkipToNext() {
                    Log.d(TAG, "MediaSession callback: onSkipToNext")
                    viewModel?.playNext()
                }

                override fun onSkipToPrevious() {
                    Log.d(TAG, "MediaSession callback: onSkipToPrevious")
                    viewModel?.playPrevious()
                }

                override fun onStop() {
                    Log.d(TAG, "MediaSession callback: onStop")
                    stopForeground(true)
                    stopSelf()
                }
            })
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows currently playing music"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "App task removed — stopping service")
        mediaSession.release()
        viewModel = null
        stopForeground(true)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        viewModel = null
        Log.d(TAG, "MusicPlayerService destroyed")
    }
}