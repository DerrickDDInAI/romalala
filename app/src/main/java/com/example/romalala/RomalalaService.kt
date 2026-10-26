package com.example.romalala

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.util.concurrent.Executor

class RomalalaService : Service() {
    companion object {
        const val ACTION_STATE_CHANGED = "com.example.romalala.STATE_CHANGED"
        const val ACTION_START = "com.example.romalala.START"
        const val ACTION_STOP = "com.example.romalala.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        private const val CHANNEL = "romalala_capture"
        private const val NOTIFICATION_ID = 41
        private const val STALE_LYRICS_MS = 4000L

        @Volatile var isRunning = false
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var workerThread: HandlerThread
    private lateinit var workerHandler: Handler
    private lateinit var workerExecutor: Executor
    private val generation = CaptureGeneration()
    private val overlay by lazy { OverlayWindow(this) }
    private val hideStaleLyrics = Runnable { overlay.remove() }
    private var started = false // Main thread only.
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) requestStop()
        }
    }

    // Owned by the worker thread, including capture teardown.
    private var recognizer: TextRecognizer? = null
    private var converter: RomajiConverter? = null
    private val lyricContinuity = LyricContinuity()
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var busy = false
    private var stopping = false
    private var contentVisible = true
    private var lastFrameMillis = -1000L
    private var captureWidth = 0
    private var captureHeight = 0

    override fun onCreate() {
        super.onCreate()
        workerThread = HandlerThread("RomalalaOCR").also { it.start() }
        workerHandler = Handler(workerThread.looper)
        workerExecutor = Executor { workerHandler.post(it) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            requestStop()
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY
        if (intent?.action != ACTION_START) {
            requestStop()
            return START_NOT_STICKY
        }
        val consentData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        if (resultCode != Activity.RESULT_OK || consentData == null || !Settings.canDrawOverlays(this)) {
            requestStop("Allow screen sharing and the overlay, then try again.")
            return START_NOT_STICKY
        }
        try {
            // Consent -> foreground service -> projection. Never reuse a projection token.
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            started = true
            isRunning = true
            sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
            workerHandler.post { startCapture(resultCode, consentData) }
        } catch (error: RuntimeException) {
            Log.e("RomalalaService", "Could not start capture service", error)
            requestStop("Could not start screen sharing. Open Romalala and try again.")
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(
            this, 0, Intent(this, RomalalaService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause), getString(R.string.notification_stop), stop
            ).build())
            .build()
    }

    private fun startCapture(resultCode: Int, consentData: Intent) {
        if (generation.snapshot() < 0) return
        try {
            val capture = getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(resultCode, consentData)
            projection = capture
            val callback = object : MediaProjection.Callback() {
                override fun onStop() { requestStop() }
                override fun onCapturedContentResize(width: Int, height: Int) {
                    resizeCapture(width, height)
                }
                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                    contentVisible = isVisible
                    generation.invalidate()
                    publish(null, generation.snapshot())
                }
            }
            projectionCallback = callback
            capture.registerCallback(callback, workerHandler)
            val (width, height) = displaySize()
            captureWidth = width
            captureHeight = height
            reader = createReader(width, height)
            virtualDisplay = capture.createVirtualDisplay(
                "Romalala OCR", width, height, resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, workerHandler
            )
        } catch (error: Exception) {
            Log.e("RomalalaService", "Could not capture screen", error)
            requestStop("Screen sharing failed. Please start a new session.")
        }
    }

    private fun displaySize(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = wm.maximumWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Before API 34 there is no captured-content resize callback.
        if (Build.VERSION.SDK_INT < 34) workerHandler.post {
            val (width, height) = displaySize()
            resizeCapture(width, height)
        }
    }

    private fun createReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ processFrame(it) }, workerHandler)
        }

    private fun resizeCapture(width: Int, height: Int) {
        if (generation.snapshot() < 0 || width <= 0 || height <= 0 ||
            (width == captureWidth && height == captureHeight)) return
        val display = virtualDisplay ?: return
        generation.invalidate()
        publish(null, generation.snapshot())
        try {
            val replacement = createReader(width, height)
            val previous = reader
            try {
                display.resize(width, height, resources.configuration.densityDpi)
                display.surface = replacement.surface
            } catch (error: Exception) {
                replacement.close()
                throw error
            }
            reader = replacement
            captureWidth = width
            captureHeight = height
            previous?.setOnImageAvailableListener(null, null)
            previous?.close()
        } catch (error: Exception) {
            Log.e("RomalalaService", "Capture resize failed", error)
            requestStop("Screen size changed. Please start a new session.")
        }
    }

    private fun processFrame(source: ImageReader) {
        if (source !== reader || generation.snapshot() < 0) return
        val image = try { source.acquireLatestImage() } catch (_: IllegalStateException) { null }
            ?: return
        val now = SystemClock.elapsedRealtime()
        if (busy || !contentVisible || now - lastFrameMillis < 1000) {
            image.close()
            return
        }
        val frameGeneration = generation.snapshot()
        val captured = try {
            image.use {
                val plane = it.planes[0]
                FrameBitmap.copyRgba(plane.buffer, it.width, it.height,
                    plane.pixelStride, plane.rowStride)
            }
        } catch (error: Exception) {
            Log.w("RomalalaService", "Frame copy failed", error)
            publish(null, frameGeneration)
            return
        }
        // Retain layout anchors while bounding OCR cost on high-resolution phones.
        val bitmap = try {
            if (captured.width <= 960) captured else {
                val height = (captured.height.toLong() * 960 / captured.width).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(captured, 960, height, true).also { captured.recycle() }
            }
        } catch (error: Exception) {
            captured.recycle()
            Log.w("RomalalaService", "Frame scaling failed", error)
            publish(null, frameGeneration)
            return
        }
        if (now - lastFrameMillis >= STALE_LYRICS_MS) lyricContinuity.reset()
        lastFrameMillis = now
        busy = true
        try {
            val detector = recognizer ?: TextRecognition.getClient(
                JapaneseTextRecognizerOptions.Builder().build()
            ).also { recognizer = it }
            detector.process(InputImage.fromBitmap(bitmap, 0))
                .addOnCompleteListener(workerExecutor) { task ->
                    try {
                        if (generation.accepts(frameGeneration) && contentVisible) {
                            if (task.isSuccessful) {
                                val lines = task.result.textBlocks.flatMap { it.lines }.flatMap { line ->
                                    val box = line.boundingBox ?: return@flatMap emptyList()
                                    val glyphs = line.elements.flatMap { it.symbols }.mapNotNull { it.boundingBox }
                                    val appearance = LyricAppearance.whiteFraction(bitmap, glyphs.ifEmpty { listOf(box) })
                                    val row = RecognizedLine(line.text, box.left, box.top, box.right, box.bottom, appearance)
                                    // Some OCR versions put both seek times into a single line.
                                    val times = line.elements.filter {
                                        it.text.matches(Regex("-?\\d{1,3}:\\d{2}")) && it.text != line.text
                                    }.mapNotNull { element -> element.boundingBox?.let {
                                        RecognizedLine(element.text, it.left, it.top, it.right, it.bottom)
                                    } }
                                    listOf(row) + times
                                }
                                val detected = lyricContinuity.resolve(
                                    LyricSelector.detect(lines, bitmap.width, bitmap.height))
                                val content = detected?.let { selection ->
                                    val engine = converter ?: RomajiConverter().also { converter = it }
                                    OverlayContent.from(selection, engine::convert)
                                }
                                publish(content, frameGeneration)
                            } else {
                                Log.w("RomalalaService", "OCR failed", task.exception)
                                publish(null, frameGeneration)
                            }
                        }
                    } catch (error: Exception) {
                        Log.w("RomalalaService", "Conversion failed", error)
                        publish(null, frameGeneration)
                    } finally {
                        bitmap.recycle()
                        busy = false
                        finishWorkerIfIdle()
                    }
                }
        } catch (error: Exception) {
            bitmap.recycle()
            busy = false
            Log.w("RomalalaService", "Could not start OCR", error)
            publish(null, frameGeneration)
            finishWorkerIfIdle()
        }
    }

    private fun publish(content: OverlayContent?, frameGeneration: Long) {
        if (content == null && generation.accepts(frameGeneration)) lyricContinuity.reset()
        mainHandler.post {
            if (!generation.accepts(frameGeneration)) return@post
            mainHandler.removeCallbacks(hideStaleLyrics)
            try {
                overlay.update(content)
                if (content != null) mainHandler.postDelayed(hideStaleLyrics, STALE_LYRICS_MS)
            } catch (error: RuntimeException) {
                Log.w("RomalalaService", "Overlay unavailable", error)
                requestStop("Overlay permission is unavailable. Enable it and try again.")
            }
        }
    }

    private fun requestStop(message: String? = null) {
        generation.close() // Reject queued results before onDestroy runs.
        mainHandler.post {
            mainHandler.removeCallbacks(hideStaleLyrics)
            overlay.remove()
            if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onDestroy() {
        generation.close()
        isRunning = false
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
        unregisterReceiver(screenOffReceiver)
        mainHandler.removeCallbacksAndMessages(null)
        overlay.remove()
        stopForeground(STOP_FOREGROUND_REMOVE)
        workerHandler.post {
            stopping = true
            reader?.setOnImageAvailableListener(null, null)
            virtualDisplay?.release()
            virtualDisplay = null
            reader?.close()
            reader = null
            projectionCallback?.let { projection?.unregisterCallback(it) }
            projection?.stop()
            projection = null
            finishWorkerIfIdle()
        }
        super.onDestroy()
    }

    private fun finishWorkerIfIdle() {
        if (stopping && !busy) {
            recognizer?.close()
            recognizer = null
            converter = null
            workerThread.quitSafely()
        }
    }
}
