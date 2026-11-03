package cz.example.shimmiyolobot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

class TapAccessibilityService : AccessibilityService() {
    companion object {
        private var current: WeakReference<TapAccessibilityService>? = null
        fun tap(x: Float, y: Float): Boolean = current?.get()?.tapInternal(x, y) ?: false
        fun ready(): Boolean = current?.get() != null
    }
    private var lastTap = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = WeakReference(this)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() {
        current?.clear()
        super.onDestroy()
    }
    @Synchronized private fun tapInternal(x: Float, y: Float): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTap < 110L) return false
        val p = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(p, 0L, 35L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val accepted = dispatchGesture(gesture, null, null)
        if (accepted) lastTap = now
        return accepted
    }
}
