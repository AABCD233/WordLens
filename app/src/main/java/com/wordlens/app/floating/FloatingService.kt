package com.wordlens.app.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.wordlens.app.MainActivity
import com.wordlens.app.WordLensApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 悬浮查词：一个小搜索框浮在任意应用上方，查完直接在同一窗口里显示释义卡片，
 * 不需要切回词镜。
 *
 * 窗口默认可聚焦（否则没法打字），拖动靠顶部标题条，避免和输入框抢触摸。
 */
class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showPanel()
    }

    // ---------------- 悬浮面板 ----------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private var frame: android.widget.FrameLayout? = null
    private var bubble: TextView? = null
    private var collapsed = false

    private fun showPanel() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#F7FBF9"))
                setStroke(dp(1), Color.parseColor("#0F6B63"))
            }
            elevation = dp(8).toFloat()
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }

        // 顶部：拖动条 + 关闭
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "词镜 · 悬浮查词"
            setTextColor(Color.parseColor("#0F6B63"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val close = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#667570"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(10), dp(2), dp(2), dp(2))
            setOnClickListener { collapse() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(close)
        panel.addView(header)

        // 输入行
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        val input = EditText(this).apply {
            hint = "输入英文单词"
            setHintTextColor(Color.parseColor("#8A9691"))
            setTextColor(Color.parseColor("#17201E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine(true)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#EDF3F0"))
            }
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val go = TextView(this).apply {
            text = "查"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#0F6B63"))
            }
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val gap = View(this); gap.layoutParams = LinearLayout.LayoutParams(dp(6), 1)
        inputRow.addView(gap)
        inputRow.addView(go)
        panel.addView(inputRow)

        // 结果区
        val resultBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        val head = TextView(this).apply {
            setTextColor(Color.parseColor("#17201E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val sub = TextView(this).apply {
            setTextColor(Color.parseColor("#667570"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val body = TextView(this).apply {
            setTextColor(Color.parseColor("#17201E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(4), 0, 0)
        }
        val scroll = ScrollView(this).apply {
            addView(body)
            isVerticalScrollBarEnabled = true
        }
        resultBox.addView(head)
        resultBox.addView(sub)
        resultBox.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(220)
        ))
        panel.addView(resultBox)

        // 尺寸与位置
        val width = minOf(dp(300), (resources.displayMetrics.widthPixels * 0.86f).toInt())
        val p = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (resources.displayMetrics.heightPixels * 0.18f).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        params = p

        // 顶部条可拖动
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = p.x; startY = p.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = (startX + (e.rawX - downX)).toInt()
                    p.y = (startY + (e.rawY - downY)).toInt()
                    runCatching { wm.updateViewLayout(frame ?: panel, p) }
                    true
                }
                else -> true
            }
        }

        fun lookup() {
            val q = input.text.toString().trim()
            if (q.isEmpty()) return
            head.text = q
            sub.text = "查询中…"
            body.text = ""
            resultBox.visibility = View.VISIBLE
            hideKeyboard(input)
            scope.launch {
                val container = (application as WordLensApp).container
                val def = withContext(Dispatchers.IO) {
                    runCatching { container.dictionary.lookup(q) }.getOrNull()
                }
                if (def == null) {
                    sub.text = "没查到"
                    body.text = "离线词典没收录，也可能联网失败了。"
                    return@launch
                }
                head.text = def.lemma.ifBlank { q }
                val ph = def.phonetic.orEmpty()
                sub.text = if (ph.isBlank()) "" else "/$ph/"
                val zh = (def.zhLines + def.baseZhLines).joinToString("\n")
                val en = def.enMeanings.joinToString("\n") { m ->
                    m.partOfSpeech + " " + m.definitions.take(2).joinToString("；") { it.text }
                }
                body.text = listOf(zh, en).filter { it.isNotBlank() }.joinToString("\n\n")
                    .ifBlank { "（没有释义）" }
            }
        }

        go.setOnClickListener { lookup() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                lookup(); true
            } else false
        }

        // 外层容器：面板 + 四角缩放手柄
        val container = android.widget.FrameLayout(this)
        container.addView(panel, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        listOf(
            Gravity.TOP or Gravity.START to (1 to 1),
            Gravity.TOP or Gravity.END to (-1 to 1),
            Gravity.BOTTOM or Gravity.START to (1 to -1),
            Gravity.BOTTOM or Gravity.END to (-1 to -1)
        ).forEach { (g, dir) ->
            val handle = View(this).apply {
                setBackgroundColor(Color.parseColor("#330F6B63"))
            }
            val lp = android.widget.FrameLayout.LayoutParams(dp(18), dp(18), g)
            container.addView(handle, lp)
            var hx = 0f
            var hy = 0f
            var sw = 0
            var sh = 0
            handle.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        hx = e.rawX; hy = e.rawY
                        sw = p.width
                        sh = if (p.height > 0) p.height
                        else (container.height.takeIf { it > 0 } ?: dp(260))
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ((e.rawX - hx) * dir.first).toInt()
                        val dy = ((e.rawY - hy) * dir.second).toInt()
                        p.width = (sw + dx).coerceAtLeast(dp(200))
                        p.height = (sh + dy).coerceAtLeast(dp(120))
                        runCatching { wm.updateViewLayout(container, p) }
                        true
                    }
                    else -> true
                }
            }
        }

        frame = container
        runCatching { wm.addView(container, p) }
        root = panel
    }

    /** 收起成小球（不退出服务） */
    private fun collapse() {
        if (collapsed) return
        collapsed = true
        frame?.let { runCatching { wm.removeView(it) } }
        if (bubble != null) return
        val size = dp(46)
        val dot = TextView(this).apply {
            text = "词"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0F6B63"))
                setStroke(dp(2), Color.parseColor("#B8F2E8"))
            }
        }
        val bp = WindowManager.LayoutParams(
            size, size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dp(16)
            y = (resources.displayMetrics.heightPixels * 0.3f).toInt()
        }
        var dx0 = 0f
        var dy0 = 0f
        var sx0 = 0
        var sy0 = 0
        var moved = false
        dot.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dx0 = e.rawX; dy0 = e.rawY; sx0 = bp.x; sy0 = bp.y
                    moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - dx0
                    val dy = e.rawY - dy0
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    if (moved) {
                        bp.x = (sx0 + dx).toInt()
                        bp.y = (sy0 + dy).toInt()
                        runCatching { wm.updateViewLayout(dot, bp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) expand()
                    true
                }
                else -> false
            }
        }
        runCatching { wm.addView(dot, bp) }
        bubble = dot
    }

    /** 从小球展开回面板 */
    private fun expand() {
        collapsed = false
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        frame?.let {
            runCatching { wm.addView(it, params) }
        }
    }

    private fun hideKeyboard(v: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        runCatching { imm.hideSoftInputFromWindow(v.windowToken, 0) }
    }

    // ---------------- 通知 ----------------

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "悬浮查词", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "保持悬浮搜索框可用"
                    setShowBadge(false)
                }
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("词镜悬浮查词已开启")
            .setContentText("屏幕上会显示一个小搜索框")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        frame?.let { runCatching { wm.removeView(it) } }
        bubble?.let { runCatching { wm.removeView(it) } }
        frame = null
        bubble = null
        root = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_QUICK_SEARCH = "quick_search"
        private const val CHANNEL_ID = "wordlens_floating"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val i = Intent(context, FloatingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingService::class.java))
        }
    }
}
