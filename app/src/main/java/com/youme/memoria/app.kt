package com.youme.memoria

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.youme.memoria.Gallery.IndexingWorker

class MemoriaApp : Application() {

    val repo: PhotoRepository by lazy {
        PhotoRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        createIndexingChannel()
    }
    private fun createIndexingChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                IndexingWorker.CHANNEL_ID,
                "Photo indexing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while Memoria indexes your photos"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

}