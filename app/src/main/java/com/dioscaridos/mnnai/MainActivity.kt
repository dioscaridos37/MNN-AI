package com.dioscaridos.mnnai

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.mnn.sdk.MNNLlm
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var settings: LinearLayout
    private var model: MNNLlm? = null
    private var loading = false
    private var busy = false
    private var responseTokens = 384
    private var threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 6)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val bg = Color.rgb(8, 12, 23)
    private val glass = Color.argb(185, 27, 39, 61)
    private val muted = Color.rgb(145, 161, 188)
    private val blue = Color.rgb(160, 198, 255)
    private val mint = Color.rgb(139, 239, 218)
    private val modelUrl = "https://huggingface.co/taobao-mnn/MNN-Pocket-0.3B-MNN/resolve/main/"
    private val modelFiles = listOf("config.json", "llm.mnn", "llm.mnn.weight", "llm_config.json", "tokenizer.mtok")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = FrameLayout(this)
        root.addView(AuraView(), FrameLayout.LayoutParams(-1, -1))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(16), dp(19), dp(12))
        }
        root.addView(page, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = TextView(this).apply {
            text = "D"; textSize = 25f; gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setTextColor(Color.rgb(12, 26, 43))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(blue, mint)).apply { cornerRadius = dp(18).toFloat() }
            elevation = dp(10).toFloat()
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(52), dp(52)))
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        brand.addView(TextView(this).apply { text = "DIX AI"; textSize = 23f; letterSpacing = .05f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        brand.addView(TextView(this).apply { text = "ЛИЧНЫЙ ИНТЕЛЛЕКТ"; textSize = 9f; letterSpacing = .15f; setTextColor(muted) })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val settingsButton = pill("⚙", blue, 46)
        header.addView(settingsButton, LinearLayout.LayoutParams(dp(46), dp(46)))
        settingsButton.setOnClickListener {
            press(it)
            settings.visibility = if (settings.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (settings.visibility == View.VISIBLE) spring(settings)
        }
        page.addView(header)

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = glassBg(Color.argb(170, 25, 37, 58), 18)
        }
        statusCard.addView(TextView(this).apply { text = "●"; setTextColor(mint); textSize = 10f; setPadding(0, 0, dp(8), 0) })
        status = TextView(this).apply { text = "MNN-POCKET 0.3B · ПОДГОТОВКА"; textSize = 10f; letterSpacing = .06f; setTextColor(Color.rgb(206, 220, 242)) }
        statusCard.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        statusCard.addView(TextView(this).apply {
            text = "OFFLINE AI"; textSize = 8f; letterSpacing = .08f; setTextColor(mint)
            background = glassBg(Color.argb(45, 90, 220, 194), 16)
            setPadding(dp(9), dp(6), dp(9), dp(6))
        })
        val statusParams = LinearLayout.LayoutParams(-1, -2)
        statusParams.topMargin = dp(16)
        page.addView(statusCard, statusParams)

        settings = buildSettings()
        settings.visibility = View.GONE
        val settingsParams = LinearLayout.LayoutParams(-1, -2)
        settingsParams.topMargin = dp(10)
        page.addView(settings, settingsParams)

        scroll = ScrollView(this).apply { clipToPadding = false; overScrollMode = View.OVER_SCROLL_NEVER; setPadding(0, dp(15), 0, dp(8)) }
        chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(3), 0, dp(16)) }
        scroll.addView(chat)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        chat.addView(welcomeCard())

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(7), dp(7))
            background = glassBg(Color.argb(220, 26, 39, 62), 26)
            elevation = dp(12).toFloat()
        }
        input = EditText(this).apply {
            hint = "Напишите сообщение..."; setHintTextColor(Color.rgb(126, 145, 174))
            setTextColor(Color.WHITE); textSize = 15f; maxLines = 4; minLines = 1
            setPadding(dp(13), dp(9), dp(7), dp(9)); background = null
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEND) { send(); true } else false }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        val send = TextView(this).apply {
            text = "↑"; textSize = 27f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(12, 25, 42))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(blue, mint)).apply { cornerRadius = dp(20).toFloat() }
            setOnClickListener { press(this); send() }
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(48), dp(48)))
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, dp(9), 0, 0)
            addView(composer)
            addView(TextView(this@MainActivity).apply {
                text = "ДИАЛОГИ ОСТАЮТСЯ НА ВАШЕМ УСТРОЙСТВЕ"; textSize = 8f; letterSpacing = .12f
                gravity = Gravity.CENTER; setTextColor(Color.rgb(103, 120, 148)); setPadding(0, dp(10), 0, 0)
            })
        }
        page.addView(bottom)
        setContentView(root)
        addMessage("DIX AI", "Привет! Я DIX — твой локальный ИИ-помощник. Загружаю модель на устройство.", false)
        prepareModel()
    }

    private fun buildSettings(): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(13), dp(15), dp(12)); background = glassBg(Color.argb(220, 29, 42, 66), 21) }
        box.addView(TextView(this).apply { text = "ПАРАМЕТРЫ МОДЕЛИ"; textSize = 10f; letterSpacing = .13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(blue) })
        addSlider(box, "Длина ответа", 640, responseTokens - 128, { value -> responseTokens = value + 128 }, { value -> "$value токенов" }, 128)
        val maxThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 12)
        addSlider(box, "Потоки процессора", maxThreads - 2, threads - 2, { value -> threads = value + 2; model?.numThreads = threads }, { value -> (value + 2).toString() }, 2)
        return box
    }

    private fun addSlider(box: LinearLayout, label: String, max: Int, initial: Int, changed: (Int) -> Unit, display: (Int) -> String, offset: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, 0) }
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(Color.rgb(220, 231, 248)) }, LinearLayout.LayoutParams(0, -2, 1f))
        val value = TextView(this).apply { text = display(initial + offset); textSize = 11f; setTextColor(mint) }
        line.addView(value)
        row.addView(line)
        val seek = SeekBar(this).apply {
            this.max = max; progress = initial.coerceIn(0, max)
            progressTintList = ColorStateList.valueOf(blue)
            progressBackgroundTintList = ColorStateList.valueOf(Color.argb(70, 170, 195, 230))
            thumbTintList = ColorStateList.valueOf(mint)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) { changed(p); value.text = display(p + offset) } }
                override fun onStartTrackingTouch(bar: SeekBar?) { if (bar != null) press(bar) }
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        row.addView(seek, LinearLayout.LayoutParams(-1, dp(34)))
        box.addView(row)
    }

    private fun welcomeCard(): View {
        val card = LinearLayout(this).apply {
            tag = "welcome"
            tag = "welcome"
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(17))
            background = glassBg(Color.argb(196, 29, 43, 68), 24); elevation = dp(8).toFloat()
        }
        card.addView(TextView(this).apply { text = "✦  РАД ВАС ВИДЕТЬ"; textSize = 9f; letterSpacing = .16f; setTextColor(mint) })
        card.addView(TextView(this).apply {
            text = "Думайте масштабно.\nЯ помогу разобраться."; textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(Color.WHITE); setPadding(0, dp(10), 0, dp(7))
        })
        card.addView(TextView(this).apply {
            text = "Задавайте вопросы, придумывайте проекты или просите объяснить сложную тему."
            textSize = 13f; setTextColor(muted); setLineSpacing(dp(3).toFloat(), 1f)
        })
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(15), 0, 0) }
        listOf("Идея проекта", "Объясни тему").forEachIndexed { index, label ->
            val chip = pill(label, if (index == 0) blue else mint, 40)
            chip.setPadding(dp(12), 0, dp(12), 0)
            chip.setOnClickListener {
                press(it)
                input.setText(if (index == 0) "Придумай интересный проект для Android" else "Объясни мне сложную тему простыми словами")
                input.setSelection(input.text.length)
            }
            val params = LinearLayout.LayoutParams(-2, dp(38))
            if (index > 0) params.leftMargin = dp(8)
            chips.addView(chip, params)
        }
        card.addView(chips)
        spring(card)
        return card
    }

    private fun pill(text: String, color: Int, minWidth: Int): TextView = TextView(this).apply {
        this.text = text; textSize = 12f; gravity = Gravity.CENTER; setTextColor(color)
        background = glassBg(Color.argb(75, 90, 115, 150), 22)
        minimumWidth = dp(minWidth); isClickable = true; isFocusable = true
    }

    private fun prepareModel() {
        if (loading || model != null) return
        loading = true
        status.text = "MNN-POCKET 0.3B · ЗАГРУЗКА"
        scope.launch {
            try {
                val config = withContext(Dispatchers.IO) {
                    val dir = File(filesDir, "models/MNN-Pocket-0.3B-MNN")
                    dir.mkdirs()
                    modelFiles.forEachIndexed { index, name ->
                        val file = File(dir, name)
                        if (!file.exists() || file.length() < 100) download(name, file) { done, total ->
                            val percent = if (total > 0) done * 100 / total else 0
                            runOnUiThread { status.text = "ЗАГРУЗКА " + (index + 1) + "/" + modelFiles.size + " · " + percent + "%" }
                        }
                    }
                    File(dir, "config.json").absolutePath
                }
                status.text = "ЗАПУСК ЛОКАЛЬНОЙ МОДЕЛИ…"
                val loaded = withContext(Dispatchers.Default) { MNNLlm.load(config) }
                loaded.numThreads = threads
                loaded.systemPrompt = "You are DIX AI, a helpful assistant. Answer clearly and naturally in Russian unless the user uses another language. Avoid repeating yourself."
                model = loaded
                status.text = "MNN-POCKET 0.3B · ГОТОВ К РАБОТЕ"
                addMessage("DIX AI", "Модель загружена. Теперь я могу отвечать без интернета.", false)
            } catch (e: Exception) {
                status.text = "ОШИБКА ЗАГРУЗКИ МОДЕЛИ"
                addMessage("DIX AI", "Не удалось запустить модель: " + (e.message ?: "неизвестная ошибка") + ". Проверьте интернет и свободное место.", false)
            } finally { loading = false }
        }
    }

    private fun download(name: String, target: File, progress: (Long, Long) -> Unit) {
        val connection = (URL(modelUrl + name).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000; readTimeout = 120000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DIX-AI-Android")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) error("HTTP " + connection.responseCode + ": " + name)
            val total = connection.contentLengthLong
            val temp = File(target.parentFile, name + ".part")
            connection.inputStream.use { source ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(262144)
                    var received = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        progress(received, total)
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) error("Не удалось сохранить " + name)
        } finally { connection.disconnect() }
    }

    private fun send() {
        val message = input.text.toString().trim()
        if (message.isEmpty() || busy) return
        if (chat.childCount > 0 && chat.getChildAt(0).tag == "welcome") chat.removeViewAt(0)
        addMessage("Вы", message, true)
        input.text.clear()
        val active = model
        if (active == null) {
            addMessage("DIX AI", if (loading) "Модель ещё загружается. Подождите завершения первой загрузки." else "Модель не запущена. Повторяю подготовку.", false)
            if (!loading) prepareModel()
            return
        }
        busy = true
        status.text = "DIX AI ДУМАЕТ…"
        scope.launch {
            try {
                val answer = withContext(Dispatchers.Default) { active.chat(message, maxNewTokens = responseTokens).text.trim() }
                addMessage("DIX AI", answer.ifBlank { "Модель вернула пустой ответ." }, false)
            } catch (e: Exception) {
                addMessage("DIX AI", "Ошибка генерации: " + (e.message ?: "неизвестная ошибка"), false)
            } finally {
                busy = false
                status.text = "MNN-POCKET 0.3B · ГОТОВ К РАБОТЕ"
            }
        }
    }

    private fun addMessage(author: String, body: String, mine: Boolean) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(12), dp(15), dp(13))
            background = glassBg(if (mine) Color.argb(210, 53, 79, 119) else Color.argb(178, 25, 37, 59), 21)
            alpha = 0f; translationY = dp(14).toFloat()
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(TextView(this).apply {
            text = if (mine) "ВЫ" else "✦  " + author; textSize = 9f; letterSpacing = .12f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(if (mine) blue else mint)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(TextView(this).apply { text = if (mine) "СООБЩЕНИЕ" else "ЛОКАЛЬНЫЙ ИИ"; textSize = 8f; letterSpacing = .08f; setTextColor(Color.rgb(113, 132, 162)) })
        card.addView(heading)
        card.addView(TextView(this).apply {
            text = body; textSize = 15f; setTextColor(Color.rgb(239, 244, 253))
            setPadding(0, dp(7), 0, 0); setLineSpacing(dp(3).toFloat(), 1f)
        })
        val params = LinearLayout.LayoutParams(if (mine) (resources.displayMetrics.widthPixels * .86f).toInt() else -1, -2)
        params.gravity = if (mine) Gravity.END else Gravity.START
        params.bottomMargin = dp(11)
        chat.addView(card, params)
        spring(card)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun spring(view: View) {
        view.alpha = 0f
        view.translationY = dp(14).toFloat()
        view.animate().alpha(1f).setDuration(170).start()
        SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            spring = SpringForce(0f).apply { stiffness = SpringForce.STIFFNESS_MEDIUM; dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY }
            start()
        }
    }

    private fun press(view: View) {
        SpringAnimation(view, DynamicAnimation.SCALE_X, .94f).apply {
            spring = SpringForce(.94f).apply { stiffness = 850f; dampingRatio = .55f }
            addEndListener { _, _, _, _ -> SpringAnimation(view, DynamicAnimation.SCALE_X, 1f).apply { spring = SpringForce(1f).apply { stiffness = 520f; dampingRatio = .42f }; start() } }
            start()
        }
        SpringAnimation(view, DynamicAnimation.SCALE_Y, .94f).apply {
            spring = SpringForce(.94f).apply { stiffness = 850f; dampingRatio = .55f }
            addEndListener { _, _, _, _ -> SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f).apply { spring = SpringForce(1f).apply { stiffness = 520f; dampingRatio = .42f }; start() } }
            start()
        }
    }

    private fun glassBg(color: Int, radius: Int): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(color, Color.argb(Color.alpha(color), (Color.red(color) * .82f).toInt(), (Color.green(color) * .9f).toInt(), (Color.blue(color) * 1.1f).toInt().coerceAtMost(255)))
    ).apply {
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.argb(105, 170, 205, 255))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        model?.destroy()
        scope.cancel()
        super.onDestroy()
    }

    private inner class AuraView : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var phase = 0f
        private val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 7200; repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
        }
        init { animator.start() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val x1 = w * (.82f - phase * .12f)
            val y1 = h * (.12f + phase * .08f)
            paint.shader = RadialGradient(x1, y1, w * .72f, intArrayOf(Color.argb(68, 73, 125, 220), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(x1, y1, w * .72f, paint)
            val x2 = w * (.08f + phase * .14f)
            val y2 = h * .78f
            paint.shader = RadialGradient(x2, y2, w * .55f, intArrayOf(Color.argb(38, 55, 188, 176), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(x2, y2, w * .55f, paint)
            paint.shader = null
        }
        override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    }
}
