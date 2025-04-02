package com.example.mediaplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mediaplayer.R
import com.example.mediaplayer.databinding.ItemMediaBinding
import com.example.mediaplayer.model.MediaItem
import java.util.concurrent.TimeUnit

class MediaAdapter(
    private val onItemClick: (MediaItem) -> Unit
) : ListAdapter<MediaItem, MediaAdapter.MediaViewHolder>(MediaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MediaViewHolder(
        private val binding: ItemMediaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(mediaItem: MediaItem) {
            binding.apply {
                titleText.text = mediaItem.title
                artistText.text = mediaItem.artist ?: "Unknown Artist"
                
                // Format duration
                val duration = TimeUnit.MILLISECONDS.toMinutes(mediaItem.duration)
                durationText.text = "$duration min"

                // Load album artwork
                mediaItem.thumbnailPath?.let { thumbnailPath ->
                    Glide.with(thumbnailImage)
                        .load(thumbnailPath)
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .centerCrop()
                        .into(thumbnailImage)
                } ?: run {
                    thumbnailImage.setImageResource(R.drawable.ic_music_note)
                }
            }
        }
    }

    private class MediaDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem == newItem
        }
    }
} 