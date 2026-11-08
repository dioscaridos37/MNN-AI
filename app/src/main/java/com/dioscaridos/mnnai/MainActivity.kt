package com.dioscaridos.mnnai

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var llm: MNNLlm? = null
    private var loading = false
    private var busy = false
    private val bg = Color.rgb(10, 13, 23)
    private val glass = Color.argb(185, 29, 37, 55)
    private val muted = Color.rgb(151, 164, 187)
    private val accent = Color.rgb(155, 190, 255)
    private val modelUrl = "https://huggingface.co/taobao-mnn/MNN-Pocket-0.3B-MNN/resolve/main/"
    private val modelFiles = listOf("config.json", "llm.mnn", "llm.mnn.weight", "llm_config.json", "tokenizer.mtok")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(14))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(18, 25, 42), bg, Color.rgb(15, 22, 37)))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(22)) }
        header.addView(TextView(this).apply { text = "DICA"; textSize = 29f; letterSpacing = .13f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(Color.WHITE) })
        header.addView(TextView(this).apply { text = "AI"; textSize = 29f; letterSpacing = .13f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(accent); translationY = -dp(8).toFloat() })
        status = TextView(this).apply { text = "●  MNN-POCKET 0.3B   ·   ПОДГОТОВКА"; textSize = 10f; letterSpacing = .06f; setTextColor(muted); translationY = -dp(8).toFloat() }
        header.addView(status)
        root.addView(header)
        scroll = ScrollView(this).apply { clipToPadding = false; isFillViewport = true }
        chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(16)) }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(7), dp(6), dp(7), dp(6)); background = rounded(glass, Color.argb(85, 180, 202, 255), 28); elevation = dp(8).toFloat() }
        input = EditText(this).apply {
            hint = "Спросите что-нибудь..."; setHintTextColor(Color.rgb(125, 139, 163)); setTextColor(Color.WHITE); textSize = 15f
            maxLines = 4; minLines = 1; setPadding(dp(13), dp(8), dp(8), dp(8)); background = null; imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(TextView(this).apply {
            text = "↑"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(Color.rgb(12, 20, 35)); background = rounded(accent, 0, 22); setOnClickListener { sendMessage() }
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, 0); addView(composer)
            addView(TextView(this@MainActivity).apply { text = "DICAAI  ·  ЛОКАЛЬНАЯ МОДЕЛЬ НА УСТРОЙСТВЕ"; textSize = 9f; letterSpacing = .07f; gravity = Gravity.CENTER; setTextColor(Color.rgb(100, 113, 137)); setPadding(0, dp(12), 0, 0) })
        }
        root.addView(bottom)
        setContentView(root)
        addMessage("DICAAI", "Загружаю локальную модель. Первый запуск требует загрузки примерно 173 МБ.", false)
        prepareModel()
    }

    private fun prepareModel() {
        if (loading || llm != null) return
        loading = true
        status.text = "●  ЗАГРУЗКА МОДЕЛИ…"
        scope.launch {
            try {
                val configPath = withContext(Dispatchers.IO) {
                    val dir = File(filesDir, "models/MNN-Pocket-0.3B-MNN")
                    dir.mkdirs()
                    modelFiles.forEachIndexed { index, name ->
                        val target = File(dir, name)
                        if (!target.exists() || target.length() < 100) {
                            downloadFile(name, target) { received, total ->
                                val percent = if (total > 0) (received * 100 / total).toInt() else 0
                                runOnUiThread { status.text = "●  ЗАГРУЗКА ${index + 1}/${modelFiles.size}: $name $percent%" }
                            }
                        }
                    }
                    File(dir, "config.json").absolutePath
                }
                status.text = "●  ЗАПУСК ЛОКАЛЬНОЙ МОДЕЛИ…"
                val loaded = MNNLlm.load(configPath)
                loaded.numThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 6)
                loaded.systemPrompt = "You are DICAAI. Answer the actual question in the user language. Do not repeat previous answers unless asked."
                llm = loaded
                status.text = "●  MNN-POCKET 0.3B   ·   ОНЛАЙН НА УСТРОЙСТВЕ"
                addMessage("DICAAI", "Модель загружена. Я готов отвечать локально.", false)
            } catch (e: Exception) {
                status.text = "●  ОШИБКА ЗАГРУЗКИ МОДЕЛИ"
                addMessage("DICAAI", "Не удалось запустить модель: ${e.message ?: "неизвестная ошибка"}. Проверьте подключение к интернету и свободное место.", false)
            } finally { loading = false }
        }
    }

    private fun downloadFile(name: String, target: File, progress: (Long, Long) -> Unit) {
        val connection = (URL(modelUrl + name).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000; readTimeout = 120000; instanceFollowRedirects = true; setRequestProperty("User-Agent", "DICAAI-Android")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: $name")
            val total = connection.contentLengthLong
            val temp = File(target.parentFile, "$name.part")
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
            if (!temp.renameTo(target)) error("Не удалось сохранить $name")
        } finally { connection.disconnect() }
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty() || busy) return
        addMessage("Вы", message, true)
        input.text.clear()
        val model = llm
        if (model == null) {
            addMessage("DICAAI", if (loading) "Модель ещё загружается. Подождите завершения первой загрузки." else "Модель не запущена. Повторяю подготовку.", false)
            if (!loading) prepareModel()
            return
        }
        busy = true
        status.text = "●  DICAAI ДУМАЕТ…"
        scope.launch {
            try {
                val answer = model.chat(message, maxNewTokens = 384).text.trim()
                addMessage("DICAAI", answer.ifBlank { "Модель вернула пустой ответ." }, false)
            } catch (e: Exception) {
                addMessage("DICAAI", "Ошибка генерации: ${e.message ?: "неизвестная ошибка"}", false)
            } finally {
                busy = false
                status.text = "●  MNN-POCKET 0.3B   ·   ОНЛАЙН НА УСТРОЙСТВЕ"
            }
        }
    }

    private fun addMessage(author: String, body: String, mine: Boolean) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(13), dp(16), dp(13))
            background = rounded(if (mine) Color.argb(210, 53, 78, 119) else glass, Color.argb(if (mine) 100 else 75, 190, 210, 255), 20)
            alpha = 0f; translationY = dp(10).toFloat()
        }
        card.addView(TextView(this).apply { text = author; textSize = 11f; letterSpacing = .04f; typeface = Typeface.DEFAULT_BOLD; setTextColor(if (mine) Color.rgb(207, 224, 255) else accent) })
        card.addView(TextView(this).apply { text = body; textSize = 15f; setTextColor(Color.rgb(239, 243, 251)); setPadding(0, dp(6), 0, 0); setLineSpacing(dp(3).toFloat(), 1f) })
        val params = LinearLayout.LayoutParams(if (mine) (resources.displayMetrics.widthPixels * .82f).toInt() else -1, -2)
        params.gravity = if (mine) Gravity.END else Gravity.START
        params.bottomMargin = dp(12)
        chat.addView(card, params)
        card.animate().alpha(1f).translationY(0f).setDuration(260).start()
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun rounded(color: Int, stroke: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        llm?.destroy()
        scope.cancel()
        super.onDestroy()
    }
}
