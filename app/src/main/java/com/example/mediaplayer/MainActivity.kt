package com.example.mediaplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mediaplayer.adapter.MediaAdapter
import com.example.mediaplayer.databinding.ActivityMainBinding
import com.example.mediaplayer.model.MediaItem
import com.example.mediaplayer.model.MediaType
import com.example.mediaplayer.service.MediaPlayerService
import com.example.mediaplayer.utils.PermissionManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaAdapter: MediaAdapter
    private var mediaPlayerService: MediaPlayerService? = null
    private var isBound = false
    private var mediaItems = mutableListOf<MediaItem>()
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlayerService.MediaPlayerBinder
            mediaPlayerService = binder.getService()
            isBound = true
            updatePlaybackControls()
            startProgressUpdates()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            stopProgressUpdates()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            loadMediaFiles()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setupRecyclerView()
        setupClickListeners()
        setupSlider()
        checkAndRequestPermissions()
    }

    private fun setupRecyclerView() {
        mediaAdapter = MediaAdapter { mediaItem ->
            playMedia(mediaItem)
        }
        binding.mediaRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = mediaAdapter
        }
    }

    private fun setupSlider() {
        binding.seekBar.apply {
            valueFrom = 0f
            valueTo = 100f
            value = 0f
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            playPauseButton.setOnClickListener {
                mediaPlayerService?.togglePlayPause()
                updatePlaybackControls()
            }

            previousButton.setOnClickListener {
                // TODO: Implement previous track
            }

            nextButton.setOnClickListener {
                // TODO: Implement next track
            }

            seekBar.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    mediaPlayerService?.seekTo(value.toInt())
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (PermissionManager.hasStoragePermissions(this)) {
            loadMediaFiles()
        } else {
            requestPermissionLauncher.launch(
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_AUDIO
                    )
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            )
        }
    }

    private fun loadMediaFiles() {
        mediaItems.clear()
        
        val audioProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )
        
        val audioSelection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val audioSortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            audioProjection,
            audioSelection,
            null,
            audioSortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val duration = cursor.getLong(durationColumn)
                val path = cursor.getString(dataColumn)
                val albumId = cursor.getLong(albumIdColumn)

                // Get album art
                val albumArtUri = android.net.Uri.parse("content://media/external/audio/albumart/$albumId")
                val thumbnailPath = albumArtUri.toString()

                mediaItems.add(
                    MediaItem(
                        id = id,
                        title = title,
                        artist = artist,
                        duration = duration,
                        path = path,
                        type = MediaType.AUDIO,
                        thumbnailPath = thumbnailPath
                    )
                )
            }
        }

        mediaAdapter.submitList(mediaItems)
    }

    private fun playMedia(mediaItem: MediaItem) {
        if (!isBound) {
            bindMediaPlayerService()
        }
        mediaPlayerService?.playMedia(mediaItem)
        updatePlaybackControls()
    }

    private fun bindMediaPlayerService() {
        Intent(this, MediaPlayerService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun updatePlaybackControls() {
        binding.apply {
            mediaPlayerService?.let { service ->
                val currentPosition = service.getCurrentPosition()
                val duration = service.getDuration()
                
                if (duration > 0) {
                    seekBar.valueTo = duration.toFloat()
                    seekBar.value = currentPosition.toFloat()
                }
                
                currentTrackTitle.text = service.getCurrentMediaItem()?.title ?: "No media playing"
                currentTrackArtist.text = service.getCurrentMediaItem()?.artist ?: ""
                
                playPauseButton.setImageResource(
                    if (service.isPlaying()) R.drawable.ic_pause else R.drawable.ic_play
                )
            }
        }
    }

    private fun updateProgress() {
        mediaPlayerService?.let { service ->
            if (service.isPlaying()) {
                binding.seekBar.value = service.getCurrentPosition().toFloat()
            }
        }
    }

    private fun startProgressUpdates() {
        handler.post(updateProgressRunnable)
    }

    private fun stopProgressUpdates() {
        handler.removeCallbacks(updateProgressRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdates()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
} 