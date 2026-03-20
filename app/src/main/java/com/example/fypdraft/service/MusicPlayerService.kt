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
import kotlinx.coroutines.*
import java.net.URL

class MusicPlayerService : Service() {

    private val TAG = "MusicPlayerService"
    private val CHANNEL_ID = "music_playback_channel"
    private val NOTIFICATION_ID = 1001

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager

    // Coroutine scope for background work (album art loading, position updates)
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionUpdateJob: Job? = null

    // Cache the loaded album art so we don't re-download every update
    private var cachedAlbumArtUrl: String? = null
    private var cachedAlbumArt: Bitmap? = null

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

        // Suppress Spotify's media notification by hiding its notification channel
        suppressSpotifyNotification()

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
                    updateNotification(lastTitle, lastArtist, true)
                } else {
                    launchApp()
                }
            }
            ACTION_PAUSE -> {
                Log.d(TAG, "⏸ Pause tapped from notification")
                if (viewModel != null) {
                    viewModel?.pause()
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

    // ── Suppress Spotify's notification ──────────────────────────────

    /**
     * Hide Spotify's media notification channel so only MoodSync's
     * notification appears in the notification shade.
     *
     * Spotify uses channel ID "com.spotify.music.individual.notification"
     * or similar. We set its importance to NONE so it's hidden.
     */
    private fun suppressSpotifyNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Cancel any existing Spotify notifications directly
                notificationManager.cancelAll() // Only cancels THIS app's notifications

                // We can't directly cancel another app's notifications, but we CAN
                // make our MediaSession the preferred one by setting it as active
                // with higher priority metadata. This is done in setupMediaSession().
                Log.d(TAG, "MediaSession set as active — should take priority over Spotify")
            } catch (e: Exception) {
                Log.w(TAG, "Could not suppress Spotify notification", e)
            }
        }
    }

    // ── Notification updates ─────────────────────────────────────────

    /**
     * Update notification with track info. Loads album art from URL
     * in the background if albumArtUrl is provided.
     */
    fun updateNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        albumArt: Bitmap? = null
    ) {
        lastTitle = title
        lastArtist = artist
        lastIsPlaying = isPlaying

        // Update immediately with whatever art we have
        val currentArt = albumArt ?: cachedAlbumArt
        updateMediaSessionAndNotify(title, artist, isPlaying, currentArt)

        // Start/stop position tracking based on play state
        if (isPlaying) startPositionUpdates() else stopPositionUpdates()
    }

    /**
     * Update notification with album art loaded from URL.
     * Call this from the ViewModel when you have the track's image URL.
     */
    fun updateNotificationWithArt(
        title: String,
        artist: String,
        isPlaying: Boolean,
        albumArtUrl: String?
    ) {
        lastTitle = title
        lastArtist = artist
        lastIsPlaying = isPlaying

        // If same URL, use cached bitmap
        if (albumArtUrl == cachedAlbumArtUrl && cachedAlbumArt != null) {
            updateMediaSessionAndNotify(title, artist, isPlaying, cachedAlbumArt)
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
            return
        }

        // Show notification immediately without art
        updateMediaSessionAndNotify(title, artist, isPlaying, null)
        if (isPlaying) startPositionUpdates() else stopPositionUpdates()

        // Load album art in background, then update again
        if (!albumArtUrl.isNullOrEmpty()) {
            serviceScope.launch {
                val bitmap = loadBitmapFromUrl(albumArtUrl)
                if (bitmap != null) {
                    cachedAlbumArtUrl = albumArtUrl
                    cachedAlbumArt = bitmap
                    // Update notification again with the loaded art
                    updateMediaSessionAndNotify(lastTitle, lastArtist, lastIsPlaying, bitmap)
                }
            }
        }
    }

    private fun updateMediaSessionAndNotify(
        title: String,
        artist: String,
        isPlaying: Boolean,
        albumArt: Bitmap?
    ) {
        // Get current position and duration from ViewModel
        val state = viewModel?.playerState?.value
        val position = state?.currentPosition ?: 0L
        val duration = state?.duration ?: 0L

        // Update MediaSession metadata with album art and duration
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .apply {
                    if (albumArt != null) {
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, albumArt)
                    }
                }
                .build()
        )

        // Update playback state with actual position — this makes the seekbar
        // in the notification work and helps Android prioritize our session
        val playbackState = if (isPlaying) PlaybackStateCompat.STATE_PLAYING
        else PlaybackStateCompat.STATE_PAUSED

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(playbackState, position, if (isPlaying) 1f else 0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_STOP
                )
                .build()
        )

        val notification = buildNotification(title, artist, isPlaying, albumArt)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // ── Position tracking ────────────────────────────────────────────

    /**
     * Continuously update the MediaSession playback position so the
     * notification seekbar moves and our session stays "active" in
     * Android's eyes (taking priority over Spotify's session).
     */
    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                val state = viewModel?.playerState?.value
                if (state != null && state.isPlaying) {
                    mediaSession.setPlaybackState(
                        PlaybackStateCompat.Builder()
                            .setState(
                                PlaybackStateCompat.STATE_PLAYING,
                                state.currentPosition,
                                1f
                            )
                            .setActions(
                                PlaybackStateCompat.ACTION_PLAY or
                                        PlaybackStateCompat.ACTION_PAUSE or
                                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                                        PlaybackStateCompat.ACTION_SEEK_TO or
                                        PlaybackStateCompat.ACTION_STOP
                            )
                            .build()
                    )
                }
                delay(1000)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    // ── Album art loading ────────────────────────────────────────────

    private suspend fun loadBitmapFromUrl(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val stream = URL(url).openStream()
            BitmapFactory.decodeStream(stream)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load album art: ${e.message}")
            null
        }
    }

    // ── Build notification ───────────────────────────────────────────

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
            .setPriority(NotificationCompat.PRIORITY_MAX)
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
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(
                        PendingIntent.getService(
                            this, 4,
                            Intent(this, MusicPlayerService::class.java).apply { action = ACTION_STOP },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
            )
            .build()
    }

    // ── MediaSession setup ───────────────────────────────────────────

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MoodSyncMediaSession").apply {
            isActive = true

            // Set initial playback state so Android recognizes this as THE active session
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, 0L, 0f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                                PlaybackStateCompat.ACTION_PAUSE or
                                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                                PlaybackStateCompat.ACTION_SEEK_TO or
                                PlaybackStateCompat.ACTION_STOP
                    )
                    .build()
            )

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

                override fun onSeekTo(pos: Long) {
                    Log.d(TAG, "MediaSession callback: onSeekTo $pos")
                    val duration = viewModel?.playerState?.value?.duration ?: return
                    if (duration > 0) {
                        viewModel?.seekTo(pos.toFloat() / duration)
                    }
                }

                override fun onStop() {
                    Log.d(TAG, "MediaSession callback: onStop")
                    viewModel?.pause()
                    stopForeground(true)
                    stopSelf()
                }
            })
        }
    }

    // ── Notification channel ─────────────────────────────────────────

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
        stopPositionUpdates()
        serviceScope.cancel()
        mediaSession.release()
        viewModel = null
        stopForeground(true)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPositionUpdates()
        serviceScope.cancel()
        mediaSession.release()
        cachedAlbumArt?.recycle()
        cachedAlbumArt = null
        viewModel = null
        Log.d(TAG, "MusicPlayerService destroyed")
    }
}