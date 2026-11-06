package com.ff.bot

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.*
import kotlin.math.hypot

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var fab: View? = null
    private var panel: View? = null
    private var fov: FovView? = null
    private var picker: View? = null
    private var reader: ImageReader? = null
    private var vd: VirtualDisplay? = null
    private var proj: MediaProjection? = null
    @Volatile private var alive = true
    @Volatile private var shot: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel("ff", "ffbot", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        startForeground(1, notif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        if (fab == null) addFab()
        if (reader == null) startCapture(i)
        return START_STICKY
    }

    private fun addFab() {
        val v = TextView(this).apply {
            text = "FF"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            background = round(0xFF1E1E22.toInt(), 60f)
            setOnClickListener { panel() }
        }
        val p = base(60, 60).apply { x = 40; y = 320 }
        v.setOnTouchListener(Drag(p, v))
        wm.addView(v, p)
        fab = v
    }

    private fun panel() {
        panel?.let { wm.removeView(it); panel = null; return }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            background = round(0xF01A1A1E.toInt(), 28f)
        }
        box.addView(sw("Aimbot", Engine.aimEnabled) {
            Engine.aimEnabled = it
            if (it) showFov() else hideFov()
        })
        box.addView(sw("Trigger", Engine.triggerEnabled) { Engine.triggerEnabled = it })
        box.addView(sl("FOV", 80f, 480f, Engine.fov) { Engine.fov = it })
        box.addView(sl("Suavizado", 1f, 8f, Engine.smooth) { Engine.smooth = it })
        box.addView(sl("Ganancia", 0.2f, 3f, Engine.gain) { Engine.gain = it })
        box.addView(btn("Aprender color") { pick() })
        box.addView(btn("Cerrar") { panel() })
        val p = base(WindowManager.LayoutParams.WRAP_CONTENT,
                      WindowManager.LayoutParams.WRAP_CONTENT).apply { x = 40; y = 400 }
        wm.addView(box, p)
        panel = box
    }

    private fun showFov() {
        if (fov != null) return
        val v = FovView(this)
        val p = base(WindowManager.LayoutParams.MATCH_PARENT,
                     WindowManager.LayoutParams.MATCH_PARENT).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        wm.addView(v, p)
        fov = v
    }

    private fun hideFov() { fov?.let { wm.removeView(it); fov = null } }

    private fun pick() {
        val v = object : View(this) {
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_UP) {
                    val b = shot
                    if (b != null) {
                        val px = (e.rawX / 2).toInt().coerceIn(0, b.width - 1)
                        val py = (e.rawY / 2).toInt().coerceIn(0, b.height - 1)
                        val c = b.getPixel(px, py)
                        val h = FloatArray(3)
                        Color.RGBToHSV(Color.red(c), Color.green(c), Color.blue(c), h)
                        Engine.hue = h[0]
                        Engine.satMin = (h[1] * 0.8f).coerceAtLeast(0.35f)
                        Engine.valMin = (h[2] * 0.7f).coerceAtLeast(0.35f)
                        toast("Color fijado")
                    }
                    wm.removeView(this); picker = null
                }
                return true
            }
        }
        val p = base(WindowManager.LayoutParams.MATCH_PARENT,
                     WindowManager.LayoutParams.MATCH_PARENT)
        wm.addView(v, p)
        picker = v
    }

    private fun startCapture(i: Intent?) {
        val data = i?.getParcelableExtra<Intent>("data") ?: return
        val code = i.getIntExtra("code", -1)
        val dm = resources.displayMetrics
        val w = dm.widthPixels / 2
        val h = dm.heightPixels / 2

        proj = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
            .getMediaProjection(code, data)
        proj!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, Handler(Looper.getMainLooper()))

        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        vd = proj!!.createVirtualDisplay("ffbot", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, null)

        Thread {
            while (alive) {
                val img = reader?.acquireLatestImage()
                if (img == null) { Thread.sleep(4); continue }
                try {
                    val pl = img.planes[0]
                    val pad = pl.rowStride - pl.pixelStride * img.width
                    val b = Bitmap.createBitmap(img.width + pad / pl.pixelStride, img.height,
                                                Bitmap.Config.ARGB_8888)
                    b.copyPixelsFromBuffer(pl.buffer)
                    shot = b
                    if (Engine.aimEnabled) tick(b)
                } finally { img.close() }
            }
        }.start()
    }

    private fun tick(b: Bitmap) {
        val side = Engine.fov.toInt().coerceAtMost(minOf(b.width, b.height))
        val off: PointF = Engine.detect(b, side, (b.width - side) / 2, (b.height - side) / 2)
            ?: return
        val dx = off.x * Engine.fov * Engine.gain / Engine.smooth
        val dy = off.y * Engine.fov * Engine.gain / Engine.smooth
        if (Math.abs(dx) > 1.2f || Math.abs(dy) > 1.2f) TouchService.instance?.drag(dx, dy)
        if (Engine.triggerEnabled && hypot(off.x.toDouble(), off.y.toDouble()) < 0.03) {
            val dm = resources.displayMetrics
            TouchService.instance?.tap(dm.widthPixels / 2f, dm.heightPixels / 2f)
        }
    }

    private fun base(w: Int, h: Int) = WindowManager.LayoutParams(w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)

    private fun round(c: Int, r: Float) = GradientDrawable().apply {
        setColor(c); cornerRadius = r
    }

    private fun sw(t: String, on: Boolean, f: (Boolean) -> Unit) = Switch(this).apply {
        text = t; setTextColor(Color.WHITE); isChecked = on
        setOnCheckedChangeListener { _, v -> f(v) }
    }

    private fun sl(t: String, min: Float, max: Float, init: Float, f: (Float) -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = t; setTextColor(Color.WHITE) })
            addView(SeekBar(context).apply {
                this.max = 100
                progress = (((init - min) / (max - min)) * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                        f(min + (max - min) * p / 100f)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            })
        }

    private fun btn(t: String, f: () -> Unit) =
        Button(this).apply { text = t; setOnClickListener { f() } }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun notif() = Notification.Builder(this, "ff")
        .setContentTitle("FFBot activo")
        .setSmallIcon(android.R.drawable.ic_media_play).build()

    override fun onDestroy() {
        alive = false
        fab?.let { wm.removeView(it) }
        panel?.let { wm.removeView(it) }
        fov?.let { wm.removeView(it) }
        reader?.close(); vd?.release(); proj?.stop()
        super.onDestroy()
    }

    override fun onBind(i: Intent?) = null
}

class Drag(private val p: WindowManager.LayoutParams, private val v: View) :
    View.OnTouchListener {
    private var x0 = 0; private var y0 = 0; private var tx = 0; private var ty = 0
    override fun onTouch(view: View, e: MotionEvent): Boolean {
        val wm = v.context.getSystemService(WindowManager::class.java)
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { x0 = p.x; y0 = p.y; tx = e.rawX.toInt(); ty = e.rawY.toInt() }
            MotionEvent.ACTION_MOVE -> {
                p.x = x0 + (e.rawX.toInt() - tx); p.y = y0 + (e.rawY.toInt() - ty)
                wm.updateViewLayout(v, p)
            }
        }
        return false
    }
    }
