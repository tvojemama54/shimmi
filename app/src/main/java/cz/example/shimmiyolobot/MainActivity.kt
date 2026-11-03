package cz.example.shimmiyolobot

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val permissionRequestCode = 120

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 30)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "SHIMMI · YOLO BIRD — experimentální bot"
            textSize = 21f
        }
        val explanation = TextView(this).apply {
            text = "1. Povol službu Usnadnění přístupu.\n2. Spusť sdílení celé obrazovky.\n3. Přepni se do SHIMMI a rozehraj YOLO BIRD.\n4. Zastav přes oznámení.\n\nPrvní verze: detekce není spolehlivě otestována. V soutěži vždy respektuj pravidla."
            textSize = 16f
        }
        root.addView(title)
        root.addView(explanation)
        root.addView(Button(this).apply {
            text = "1. Povolit automatické ťukání"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "2. Spustit bota (sdílet celou obrazovku)"
            setOnClickListener {
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mgr.createScreenCaptureIntent(), permissionRequestCode)
            }
        })
        root.addView(Button(this).apply {
            text = "Zastavit bota"
            setOnClickListener { startService(Intent(this@MainActivity, CaptureService::class.java).setAction(CaptureService.ACTION_STOP)) }
        })
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    @Deprecated("Used for broad Android compatibility without external dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == permissionRequestCode && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, CaptureService::class.java)
                .setAction(CaptureService.ACTION_START)
                .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            startForegroundService(intent)
        }
    }
}
