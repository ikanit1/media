package com.example.mediaplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.example.mediaplayer.MainActivity
import com.example.mediaplayer.R
import com.example.mediaplayer.model.MediaItem

class MediaPlayerService : android.app.Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val binder = MediaPlayerBinder()
    private var currentMediaItem: MediaItem? = null
    private var isPlaying = false
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager
    private var playlist: List<MediaItem> = emptyList()
    private var currentTrackIndex: Int = -1

    // Public getters
    fun getCurrentMediaItem(): MediaItem? = currentMediaItem
    fun isPlaying(): Boolean = isPlaying

    companion object {
        const val CHANNEL_ID = "media_player_channel"
        const val NOTIFICATION_ID = 1

        const val ACTION_TOGGLE_PLAYBACK = "com.example.mediaplayer.TOGGLE_PLAYBACK"
        const val ACTION_NEXT = "com.example.mediaplayer.NEXT"
        const val ACTION_PREVIOUS = "com.example.mediaplayer.PREVIOUS"
        const val ACTION_STOP = "com.example.mediaplayer.STOP"
    }

    inner class MediaPlayerBinder : Binder() {
        fun getService(): MediaPlayerService = this@MediaPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAYBACK -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MediaPlayerService").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    togglePlayPause()
                }

                override fun onPause() {
                    togglePlayPause()
                }

                override fun onSkipToNext() {
                    playNext()
                }

                override fun onSkipToPrevious() {
                    playPrevious()
                }

                override fun onStop() {
                    stopSelf()
                }
            })
            setActive(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for media playback"
            }
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun setPlaylist(items: List<MediaItem>) {
        playlist = items
        currentTrackIndex = if (currentMediaItem != null) {
            items.indexOfFirst { it.path == currentMediaItem?.path }
        } else {
            -1
        }
    }

    private fun playNext() {
        if (playlist.isEmpty()) return
        
        val nextIndex = if (currentTrackIndex < playlist.size - 1) {
            currentTrackIndex + 1
        } else {
            0 // Зацикливание
        }
        
        playTrackAtIndex(nextIndex)
    }

    private fun playPrevious() {
        if (playlist.isEmpty()) return
        
        val prevIndex = if (currentTrackIndex > 0) {
            currentTrackIndex - 1
        } else {
            playlist.size - 1 // Зацикливание
        }
        
        playTrackAtIndex(prevIndex)
    }

    private fun playTrackAtIndex(index: Int) {
        if (index in playlist.indices) {
            currentTrackIndex = index
            playMedia(playlist[index])
        }
    }

    private fun setOnErrorListener(mediaPlayer: MediaPlayer) {
        mediaPlayer.setOnErrorListener { mp, what, extra ->
            when (what) {
                MediaPlayer.MEDIA_ERROR_SERVER_DIED -> {
                    mp.release()
                    val item = currentMediaItem
                    if (item != null) {
                        playMedia(item)
                    }
                    true
                }
                MediaPlayer.MEDIA_ERROR_MALFORMED -> {
                    isPlaying = false
                    updateNotification()
                    updateMediaSession()
                    playNext()
                    true
                }
                else -> {
                    isPlaying = false
                    updateNotification()
                    updateMediaSession()
                    true
                }
            }
        }
    }

    fun playMedia(mediaItem: MediaItem) {
        try {
            currentMediaItem = mediaItem
            mediaPlayer?.release()
            
            val player = MediaPlayer()
            player.setDataSource(mediaItem.path)
            player.setOnCompletionListener {
                isPlaying = false
                updateNotification()
                updateMediaSession()
                playNext() // Автоматически переключаем на следующий трек
            }
            setOnErrorListener(player)
            player.prepare()
            player.start()
            mediaPlayer = player
            
            isPlaying = true
            updateNotification()
            updateMediaSession()
        } catch (e: Exception) {
            e.printStackTrace()
            isPlaying = false
            updateNotification()
            updateMediaSession()
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (isPlaying) {
                player.pause()
            } else {
                player.start()
            }
            isPlaying = !isPlaying
            updateNotification()
            updateMediaSession()
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        updateMediaSession()
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

    private fun updateNotification() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val currentItem = currentMediaItem ?: return createEmptyNotification()

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play
        val playPauseIntent = Intent(ACTION_TOGGLE_PLAYBACK)
        val playPausePendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(ACTION_NEXT)
        val nextPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            nextIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = Intent(ACTION_PREVIOUS)
        val previousPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            previousIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_play)
            .setContentTitle(currentItem.title)
            .setContentText(currentItem.artist)
            .setContentIntent(contentPendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(R.drawable.ic_notification_skip_previous, "Previous", previousPendingIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePendingIntent)
            .addAction(R.drawable.ic_notification_skip_next, "Next", nextPendingIntent)
            .addAction(R.drawable.ic_notification_close, "Close", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createEmptyNotification(): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_play)
            .setContentTitle("Media Player")
            .setContentText("No media playing")
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateMediaSession() {
        if (!::mediaSession.isInitialized) return
        
        val currentItem = currentMediaItem ?: return

        try {
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentItem.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentItem.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentItem.duration)
                .build()

            val state = if (isPlaying) {
                PlaybackStateCompat.STATE_PLAYING
            } else {
                PlaybackStateCompat.STATE_PAUSED
            }

            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(state, getCurrentPosition().toLong(), 1f)

            mediaSession.setMetadata(metadata)
            mediaSession.setPlaybackState(stateBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            if (::mediaSession.isInitialized) {
                mediaSession.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
} 
