package com.example.mediaplayer.model

data class MediaItem(
    val id: Long,
    val title: String,
    val artist: String?,
    val duration: Long,
    val path: String,
    val type: MediaType,
    val thumbnailPath: String? = null
)

enum class MediaType {
    AUDIO,
    IMAGE
} 