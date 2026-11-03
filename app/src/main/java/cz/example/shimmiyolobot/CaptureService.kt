package cz.example.shimmiyolobot

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : Service() {
    companion object {
        const val ACTION_START = "cz.example.shimmiyolobot.START"
        const val ACTION_STOP = "cz.example.shimmiyolobot.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val CHANNEL = "capture"
        const val NOTIFICATION_ID = 7
    }
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val processing = AtomicBoolean(false)
    private val analyzer = GameAnalyzer()
    private var lastFrame = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "YOLO bot", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || projection != null) return START_NOT_STICKY

        val stopIntent = Intent(this, CaptureService::class.java).setAction(ACTION_STOP)
        val pi = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("YOLO Bird bot – aktivní")
            .setContentText("Pro ukončení klepni na Zastavit")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Zastavit", pi).build())
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else intent.getParcelableExtra(EXTRA_RESULT_DATA)
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        if (resultCode != Activity.RESULT_OK) { stopSelf(); return START_NOT_STICKY }

        thread = HandlerThread("YoloFrames", Process.THREAD_PRIORITY_DISPLAY).also { it.start() }
        handler = Handler(thread!!.looper)
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            projection = mgr.getMediaProjection(resultCode, data)
            projection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, handler)
            @Suppress("DEPRECATION")
            val dm = android.util.DisplayMetrics().also {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(it)
            }
            val w = dm.widthPixels
            val h = dm.heightPixels
            imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
            imageReader!!.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastFrame < 45L || !processing.compareAndSet(false, true)) return@setOnImageAvailableListener
                    lastFrame = now
                    try {
                        val plane = image.planes[0]
                        val decision = analyzer.analyze(plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride, now)
                        if (decision.tap && TapAccessibilityService.ready()) {
                            // Tap inside the gameplay region, away from SHIMMI menus.
                            TapAccessibilityService.tap(image.width * .47f, image.height * .53f)
                        }
                    } finally { processing.set(false) }
                } catch (_: Exception) {
                    // A transient unreadable frame must not crash capture.
                } finally { image.close() }
            }, handler)
            virtualDisplay = projection!!.createVirtualDisplay(
                "Yolo capture", w, h, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, handler
            )
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        super.onDestroy()
    }
}
