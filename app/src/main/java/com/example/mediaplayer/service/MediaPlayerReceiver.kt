package com.example.mediaplayer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MediaPlayerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            ACTION_TOGGLE_PLAYBACK -> {
                val serviceIntent = Intent(context, MediaPlayerService::class.java)
                context?.startService(serviceIntent)
            }
            ACTION_STOP -> {
                val serviceIntent = Intent(context, MediaPlayerService::class.java)
                context?.stopService(serviceIntent)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_PLAYBACK = "com.example.mediaplayer.TOGGLE_PLAYBACK"
        const val ACTION_STOP = "com.example.mediaplayer.STOP"
    }
} 