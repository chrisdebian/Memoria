package com.youme.memoria.Gallery

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.youme.memoria.ImageSizeUtil
import com.youme.memoria.MainActivity
import com.youme.memoria.MemoriaApp
import com.youme.memoria.R
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

class IndexingWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val repo = (context.applicationContext as MemoriaApp).repo

    override suspend fun getForegroundInfo() = foregroundInfo(0, 0)

    override suspend fun doWork(): Result {
        setForeground(foregroundInfo(0, 0))
        try {
            val existing: HashSet<String> =
                repo.alreadyExistsList().mapTo(HashSet()) { it.uri }
            val all = repo.getImagesToIndex(context)
            val toProcess = all.filter { it.toString() !in existing }
            var done = existing.size

            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to all.size, KEY_ETA to 0L))


            var prvEtaMs = 0L
            repo.initializeImageModel()
            var avgMsPerImage = 0f
            toProcess.forEachIndexed { index, uri ->
                if (isStopped) return Result.success()
                coroutineContext.ensureActive()
                val startTime = System.currentTimeMillis()

                try {
                    val encodedImage =
                        repo.encodeImage(context, uri)
                    val sizeData = ImageSizeUtil.getImageDimensions(context,uri)
                    val photoMetaData = ImageSizeUtil.getPhotoMetadata(context,uri)
                    val cameraInfo = ImageSizeUtil.getCameraInfo(context,uri)
                    repo.saveEmbedding(
                        uri.toString(),
                        encodedImage,
                        sizeData?.second?:0,
                        sizeData?.first?:0,
                        photoMetaData?.first,
                        photoMetaData?.second,
                        cameraInfo.first,
                        cameraInfo.second

                    )
                    val took = (System.currentTimeMillis() - startTime).toFloat()
                    avgMsPerImage = if (avgMsPerImage == 0f) took else avgMsPerImage * 0.95f + took * 0.05f


                    done++
                    existing.add(uri.toString())

                    //update ui
                    setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to all.size, KEY_ETA to prvEtaMs))
                    setForeground(foregroundInfo(done, all.size))


                } catch (e: CancellationException) {
                    throw e

                } catch (e: Throwable) {
                    Log.e("ImageEncoding", "Failed: $uri", e)
                    repo.markAsFailed(uri.toString())
                }

                if (index % 10 == 0 || index == toProcess.lastIndex) {

                    val etaMs = (avgMsPerImage * (toProcess.size - index - 1)).toLong()
                    prvEtaMs = etaMs

                    throttleIfHot()
                }
            }
            return Result.success(workDataOf(KEY_DONE to done))
        } finally {
            withContext(NonCancellable) { repo.unloadModel() }
        }
    }
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            applicationContext,
            2,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    fun getThermalStatus(tempCelsius: Float): ThermalStatus {
        return when {
            tempCelsius < 35f -> ThermalStatus.GREEN
            tempCelsius < 42f -> ThermalStatus.ORANGE
            else -> ThermalStatus.RED
        }
    }
    fun getBatteryTemperature(): Float {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)

        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

        return  tempTenths / 10.0f
    }

    private suspend fun throttleIfHot() {
        when (getThermalStatus(getBatteryTemperature())) {
            ThermalStatus.RED -> while (getThermalStatus(getBatteryTemperature()) == ThermalStatus.RED) delay(5000.milliseconds)
            ThermalStatus.ORANGE -> delay(800.milliseconds)
            ThermalStatus.GREEN -> {}
        }
    }

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val pauseIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_image_search_24)
            .setContentTitle("Indexing your photos")
            .setContentText("$done / $total")
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .addAction(R.drawable.baseline_pause_24,"Pause",pauseIntent)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_ID, notif)
    }

    companion object {
        const val KEY_DONE = "done"; const val KEY_TOTAL = "total"; const val KEY_ETA = "eta"
        const val CHANNEL_ID = "indexing"; const val NOTIF_ID = 42
        const val UNIQUE_NAME = "photo_indexing"
    }
}