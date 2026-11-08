package com.dioscaridos.mnnai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.StatFs
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private data class ChatMessage(val role: String, val text: String)

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var settings: LinearLayout
    private lateinit var engine: LlamaEngine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val messages = mutableListOf<ChatMessage>()
    private var speech: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceOutput = false
    private var loading = false
    private var busy = false
    private var temperature = 0.7f
    private var maxTokens = 384
    private var threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

    private val bg = Color.rgb(7, 11, 22)
    private val muted = Color.rgb(143, 159, 187)
    private val blue = Color.rgb(164, 199, 255)
    private val mint = Color.rgb(137, 239, 217)
    private val modelFile by lazy { File(filesDir, "models/Vikhr-Qwen-2.5-1.5B-Q4_K_M.gguf") }
    private val modelUrl = "https://huggingface.co/Vikhrmodels/Vikhr-Qwen-2.5-1.5B-Instruct-GGUF/resolve/main/Vikhr-Qwen-2.5-1.5b-Instruct-Q4_K_M.gguf?download=true"
    private val expectedModelSize = 986_000_000L
    private val minimumModelSize = 850_000_000L
    private val preferences by lazy { getSharedPreferences("dix_ai", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        engine = LlamaEngine()

        val root = FrameLayout(this)
        root.addView(AuraBackground(), FrameLayout.LayoutParams(-1, -1))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(11))
        }
        root.addView(page, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = TextView(this).apply {
            text = "D"
            textSize = 25f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setTextColor(Color.rgb(10, 24, 42))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(blue, mint)).apply { cornerRadius = dp(18).toFloat() }
            elevation = dp(10).toFloat()
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(52), dp(52)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brand.addView(TextView(this).apply {
            text = "DIX AI"
            textSize = 23f
            letterSpacing = .05f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        brand.addView(TextView(this).apply {
            text = "ЛОКАЛЬНЫЙ ИНТЕЛЛЕКТ"
            textSize = 9f
            letterSpacing = .13f
            setTextColor(muted)
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        val clearButton = actionButton("＋", blue)
        clearButton.contentDescription = "Новый диалог"
        clearButton.setOnClickListener {
            press(it)
            clearChat()
        }
        header.addView(clearButton, LinearLayout.LayoutParams(dp(43), dp(43)))
        val settingsButton = actionButton("⚙", mint)
        settingsButton.contentDescription = "Настройки"
        settingsButton.setOnClickListener {
            press(it)
            settings.visibility = if (settings.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (settings.visibility == View.VISIBLE) spring(settings)
        }
        val settingsParams = LinearLayout.LayoutParams(dp(43), dp(43))
        settingsParams.leftMargin = dp(8)
        header.addView(settingsButton, settingsParams)
        page.addView(header)

        val stateCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = glassBg(Color.argb(180, 24, 37, 59), 18)
        }
        stateCard.addView(TextView(this).apply {
            text = "●"
            textSize = 10f
            setTextColor(mint)
            setPadding(0, 0, dp(8), 0)
        })
        status = TextView(this).apply {
            text = "VIKHR QWEN 1.5B · ПОДГОТОВКА"
            textSize = 9f
            letterSpacing = .055f
            setTextColor(Color.rgb(211, 225, 245))
        }
        stateCard.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        stateCard.addView(TextView(this).apply {
            text = "OFFLINE"
            textSize = 8f
            letterSpacing = .09f
            setTextColor(mint)
            background = glassBg(Color.argb(48, 70, 210, 185), 16)
            setPadding(dp(9), dp(6), dp(9), dp(6))
        })
        val stateParams = LinearLayout.LayoutParams(-1, -2)
        stateParams.topMargin = dp(15)
        page.addView(stateCard, stateParams)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(mint)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(55, 160, 190, 230))
            visibility = View.GONE
        }
        val progressParams = LinearLayout.LayoutParams(-1, dp(4))
        progressParams.topMargin = dp(7)
        page.addView(progress, progressParams)

        settings = buildSettings()
        settings.visibility = View.GONE
        val settingsParams = LinearLayout.LayoutParams(-1, -2)
        settingsParams.topMargin = dp(10)
        page.addView(settings, settingsParams)

        scroll = ScrollView(this).apply {
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, dp(15), 0, dp(8))
        }
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(16))
        }
        scroll.addView(chat)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        restoreChat()
        if (messages.isEmpty()) chat.addView(welcomeCard())

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = glassBg(Color.argb(222, 25, 38, 61), 27)
            elevation = dp(12).toFloat()
        }
        input = EditText(this).apply {
            hint = "Спросите о чём угодно..."
            setHintTextColor(Color.rgb(123, 142, 172))
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 4
            minLines = 1
            setPadding(dp(12), dp(9), dp(6), dp(9))
            background = null
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) {
                    sendMessage()
                    true
                } else false
            }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))

        val mic = actionButton("⌁", blue)
        mic.contentDescription = "Голосовой ввод"
        mic.setOnClickListener {
            press(it)
            startVoiceInput()
        }
        composer.addView(mic, LinearLayout.LayoutParams(dp(43), dp(43)))

        val send = TextView(this).apply {
            text = "↑"
            textSize = 27f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(10, 25, 41))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(blue, mint)).apply { cornerRadius = dp(20).toFloat() }
            elevation = dp(5).toFloat()
            setOnClickListener {
                press(it)
                sendMessage()
            }
        }
        val sendParams = LinearLayout.LayoutParams(dp(47), dp(47))
        sendParams.leftMargin = dp(6)
        composer.addView(send, sendParams)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
            addView(composer)
            addView(TextView(this@MainActivity).apply {
                text = "МОДЕЛЬ РАБОТАЕТ НА УСТРОЙСТВЕ · БЕЗ ПОДПИСКИ"
                textSize = 8f
                letterSpacing = .1f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(99, 118, 147))
                setPadding(0, dp(9), 0, 0)
            })
        }
        page.addView(bottom)
        setContentView(root)

        tts = TextToSpeech(this, this)
        prepareModel()
    }

    private fun buildSettings(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(12))
            background = glassBg(Color.argb(220, 27, 41, 65), 21)
        }
        box.addView(TextView(this).apply {
            text = "НАСТРОЙКИ ОТВЕТОВ"
            textSize = 10f
            letterSpacing = .13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(blue)
        })
        addSlider(box, "Температура", 14, 5, { p -> temperature = 0.2f + p * 0.1f }, { p -> "%.1f".format(Locale.US, 0.2f + p * 0.1f) })
        addSlider(box, "Длина ответа", 7, 2, { p -> maxTokens = 128 + p * 128 }, { p -> "${128 + p * 128} токенов" })
        val maxThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        addSlider(box, "Потоки процессора", maxThreads - 2, (threads - 2).coerceIn(0, maxThreads - 2), { p -> threads = p + 2; engine.setThreads(threads) }, { p -> (p + 2).toString() })
        val voiceSwitch = Switch(this).apply {
            text = "Озвучивать ответы"
            textSize = 12f
            setTextColor(Color.rgb(220, 231, 248))
            isChecked = voiceOutput
            setOnCheckedChangeListener { _, checked -> voiceOutput = checked }
            buttonTintList = android.content.res.ColorStateList.valueOf(mint)
            setPadding(0, dp(8), 0, 0)
        }
        box.addView(voiceSwitch)
        return box
    }

    private fun addSlider(
        box: LinearLayout,
        label: String,
        max: Int,
        initial: Int,
        changed: (Int) -> Unit,
        display: (Int) -> String
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(9), 0, 0)
        }
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        line.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.rgb(220, 231, 248))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val value = TextView(this).apply {
            text = display(initial)
            textSize = 11f
            setTextColor(mint)
        }
        line.addView(value)
        row.addView(line)
        val seek = SeekBar(this).apply {
            this.max = max
            progress = initial.coerceIn(0, max)
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(65, 170, 195, 230))
            thumbTintList = android.content.res.ColorStateList.valueOf(mint)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        changed(p)
                        value.text = display(p)
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {
                    if (bar != null) press(bar)
                }
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        row.addView(seek, LinearLayout.LayoutParams(-1, dp(34)))
        box.addView(row)
    }

    private fun welcomeCard(): View {
        val card = LinearLayout(this).apply {
            tag = "welcome"
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(17))
            background = glassBg(Color.argb(198, 29, 43, 68), 24)
            elevation = dp(8).toFloat()
        }
        card.addView(TextView(this).apply {
            text = "✦  DIX AI ГОТОВ К ДИАЛОГУ"
            textSize = 9f
            letterSpacing = .14f
            setTextColor(mint)
        })
        card.addView(TextView(this).apply {
            text = "Умный помощник.\nПрямо в твоём телефоне."
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(0, dp(10), 0, dp(7))
        })
        card.addView(TextView(this).apply {
            text = "Работаю локально, не отправляю переписку на сервер и не требую платной подписки."
            textSize = 13f
            setTextColor(muted)
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(15), 0, 0)
        }
        listOf("Придумай идею", "Объясни тему").forEachIndexed { index, label ->
            val chip = actionButton(label, if (index == 0) blue else mint)
            chip.textSize = 11f
            chip.setPadding(dp(10), 0, dp(10), 0)
            val params = LinearLayout.LayoutParams(-2, dp(37))
            if (index > 0) params.leftMargin = dp(8)
            chip.setOnClickListener {
                press(it)
                input.setText(if (index == 0) "Придумай необычную идею для Android-приложения" else "Объясни сложную тему простыми словами")
                input.setSelection(input.text.length)
            }
            chips.addView(chip, params)
        }
        card.addView(chips)
        spring(card)
        return card
    }

    private fun actionButton(label: String, color: Int): TextView = TextView(this).apply {
        text = label
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(color)
        background = glassBg(Color.argb(90, 63, 82, 116), 20)
        isClickable = true
        isFocusable = true
    }

    private fun prepareModel() {
        if (loading || engine.isLoaded) return
        loading = true
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = false
        status.text = "ПРОВЕРКА ФАЙЛОВ МОДЕЛИ"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    modelFile.parentFile?.mkdirs()
                    if (!modelFile.exists() || modelFile.length() < minimumModelSize) {
                        val free = StatFs(filesDir.absolutePath).availableBytes
                        if (free < expectedModelSize + 150_000_000L) {
                            error("Нужно освободить не менее 1,2 ГБ памяти телефона.")
                        }
                        downloadModel(modelFile)
                    }
                }
                status.text = "ЗАГРУЗКА МОДЕЛИ В ПАМЯТЬ"
                progress.isIndeterminate = true
                val loaded = withContext(Dispatchers.Default) {
                    engine.load(modelFile.absolutePath, threads, 4096)
                }
                if (!loaded) error("llama.cpp не смог открыть файл модели.")
                status.text = "VIKHR QWEN 1.5B · ГОТОВ К РАБОТЕ"
                progress.visibility = View.GONE
                if (messages.isEmpty()) addMessage("DIX AI", "Модель загружена. Я готов отвечать без интернета.", false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.text = "ОШИБКА ЗАПУСКА МОДЕЛИ"
                progress.isIndeterminate = false
                progress.progress = 0
                addMessage("DIX AI", e.message ?: "Не удалось загрузить модель.", false)
            } finally {
                loading = false
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private suspend fun downloadModel(target: File) {
        val partial = File(target.parentFile, target.name + ".part")
        var lastError: Exception? = null
        for (attempt in 0..3) {
            try {
                val offset = partial.length()
                val connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 180000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "DIX-AI-Android")
                    setRequestProperty("Accept-Encoding", "identity")
                    if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
                }
                try {
                    connection.connect()
                    val code = connection.responseCode
                    if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                        error("Сервер модели ответил HTTP $code")
                    }
                    val append = offset > 0 && code == HttpURLConnection.HTTP_PARTIAL
                    if (!append && partial.exists()) partial.delete()
                    val base = if (append) offset else 0L
                    val contentLength = connection.contentLengthLong
                    val contentRange = connection.getHeaderField("Content-Range")
                    val total = contentRange?.substringAfterLast("/")?.toLongOrNull()
                        ?: if (contentLength > 0) base + contentLength else expectedModelSize
                    connection.inputStream.use { source ->
                        FileOutputStream(partial, append).buffered().use { output ->
                            val buffer = ByteArray(256 * 1024)
                            var written = base
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                written += count
                                val percent = ((written * 100L) / total.coerceAtLeast(1L)).toInt().coerceIn(0, 100)
                                runOnUiThread {
                                    progress.isIndeterminate = false
                                    progress.progress = percent
                                    status.text = "СКАЧИВАНИЕ МОДЕЛИ · $percent%"
                                }
                            }
                        }
                    }
                    if (total > 0 && partial.length() != total) {
                        error("Загрузка прервалась: ${partial.length()} из $total байт.")
                    }
                    if (partial.length() < minimumModelSize) error("Файл модели слишком маленький или повреждён.")
                    if (target.exists()) target.delete()
                    if (!partial.renameTo(target)) error("Не удалось сохранить файл модели.")
                    return
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt == 3) break
            }
        }
        throw lastError ?: IllegalStateException("Не удалось скачать модель.")
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || busy) return
        if (chat.childCount > 0 && chat.getChildAt(0).tag == "welcome") chat.removeViewAt(0)
        messages.add(ChatMessage("user", text))
        addMessage("Вы", text, true, false)
        saveChat()
        input.text.clear()

        if (!engine.isLoaded) {
            addMessage("DIX AI", if (loading) "Модель ещё загружается. Дождитесь окончания загрузки." else "Модель не загружена. Запускаю загрузку.", false)
            if (!loading) prepareModel()
            return
        }

        busy = true
        status.text = "DIX AI ДУМАЕТ…"
        val prompt = buildPrompt()
        scope.launch {
            try {
                val answer = withContext(Dispatchers.Default) {
                    engine.generate(prompt, maxTokens, temperature)
                }.trim()
                val safeAnswer = answer.ifBlank { "Не удалось сформировать ответ. Попробуйте переформулировать вопрос." }
                messages.add(ChatMessage("assistant", safeAnswer))
                addMessage("DIX AI", safeAnswer, false, false)
                saveChat()
                if (voiceOutput && ttsReady) tts?.speak(safeAnswer, TextToSpeech.QUEUE_FLUSH, null, "dix-answer")
            } catch (e: Exception) {
                addMessage("DIX AI", "Ошибка генерации: ${e.message ?: "неизвестная ошибка"}", false)
            } finally {
                busy = false
                status.text = "VIKHR QWEN 1.5B · ГОТОВ К РАБОТЕ"
            }
        }
    }

    private fun buildPrompt(): String {
        val prompt = StringBuilder()
        prompt.append("<|im_start|>system\n")
        prompt.append("Ты DIX AI — полезный русскоязычный помощник. Отвечай по-русски, точно, естественно и по существу. Не выдумывай факты. Если не знаешь ответа, честно скажи. Пиши компактно, если пользователь не просит подробностей.<|im_end|>\n")
        messages.takeLast(10).forEach { message ->
            val role = if (message.role == "user") "user" else "assistant"
            prompt.append("<|im_start|>").append(role).append("\n")
            prompt.append(message.text).append("<|im_end|>\n")
        }
        prompt.append("<|im_start|>assistant\n")
        return prompt.toString()
    }

    private fun addMessage(author: String, body: String, mine: Boolean, persist: Boolean = true) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(12), dp(15), dp(13))
            background = glassBg(if (mine) Color.argb(208, 51, 78, 119) else Color.argb(182, 24, 36, 58), 21)
            alpha = 0f
            translationY = dp(13).toFloat()
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(TextView(this).apply {
            text = if (mine) "ВЫ" else "✦  $author"
            textSize = 9f
            letterSpacing = .12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (mine) blue else mint)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(TextView(this).apply {
            text = if (mine) "СООБЩЕНИЕ" else "VIKHR · OFFLINE"
            textSize = 8f
            letterSpacing = .07f
            setTextColor(Color.rgb(111, 132, 163))
        })
        card.addView(heading)
        card.addView(TextView(this).apply {
            text = body
            textSize = 15f
            setTextColor(Color.rgb(239, 244, 253))
            setPadding(0, dp(7), 0, 0)
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        val params = LinearLayout.LayoutParams(if (mine) (resources.displayMetrics.widthPixels * .86f).toInt() else -1, -2)
        params.gravity = if (mine) Gravity.END else Gravity.START
        params.bottomMargin = dp(11)
        chat.addView(card, params)
        spring(card)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        if (persist) {
            messages.add(ChatMessage(if (mine) "user" else "assistant", body))
            saveChat()
        }
    }

    private fun saveChat() {
        val array = JSONArray()
        messages.takeLast(40).forEach {
            array.put(JSONObject().put("role", it.role).put("text", it.text))
        }
        preferences.edit().putString("messages", array.toString()).apply()
    }

    private fun restoreChat() {
        val raw = preferences.getString("messages", null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val role = item.optString("role")
                val text = item.optString("text")
                if (text.isNotBlank()) {
                    messages.add(ChatMessage(role, text))
                    addMessage(if (role == "user") "Вы" else "DIX AI", text, role == "user", false)
                }
            }
        } catch (_: Exception) {
            messages.clear()
            preferences.edit().remove("messages").apply()
        }
    }

    private fun clearChat() {
        messages.clear()
        preferences.edit().remove("messages").apply()
        chat.removeAllViews()
        chat.addView(welcomeCard())
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            addMessage("DIX AI", "На устройстве нет службы распознавания речи.", false)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 410)
            return
        }
        try {
            if (speech == null) speech = SpeechRecognizer.createSpeechRecognizer(this)
            speech?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "СЛУШАЮ ВАС…" }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { status.text = if (engine.isLoaded) "VIKHR QWEN 1.5B · ГОТОВ" else "ЗАГРУЗКА МОДЕЛИ" }
                override fun onError(error: Int) { status.text = "ГОЛОСОВОЙ ВВОД ЗАВЕРШЁН" }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        input.setText(spoken)
                        input.setSelection(input.text.length)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            speech?.startListening(intent)
        } catch (e: Exception) {
            addMessage("DIX AI", "Не удалось запустить микрофон: ${e.message}", false)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 410 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoiceInput()
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun spring(view: View) {
        view.alpha = 0f
        view.translationY = dp(14).toFloat()
        view.animate().alpha(1f).setDuration(180).start()
        SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            spring = SpringForce(0f).apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            }
            start()
        }
    }

    private fun press(view: View) {
        SpringAnimation(view, DynamicAnimation.SCALE_X, .94f).apply {
            spring = SpringForce(.94f).apply { stiffness = 850f; dampingRatio = .55f }
            addEndListener { _, _, _, _ ->
                SpringAnimation(view, DynamicAnimation.SCALE_X, 1f).apply {
                    spring = SpringForce(1f).apply { stiffness = 520f; dampingRatio = .42f }
                    start()
                }
            }
            start()
        }
        SpringAnimation(view, DynamicAnimation.SCALE_Y, .94f).apply {
            spring = SpringForce(.94f).apply { stiffness = 850f; dampingRatio = .55f }
            addEndListener { _, _, _, _ ->
                SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f).apply {
                    spring = SpringForce(1f).apply { stiffness = 520f; dampingRatio = .42f }
                    start()
                }
            }
            start()
        }
    }

    private fun glassBg(color: Int, radius: Int): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            color,
            Color.argb(
                Color.alpha(color),
                (Color.red(color) * .82f).toInt(),
                (Color.green(color) * .9f).toInt(),
                (Color.blue(color) * 1.12f).toInt().coerceAtMost(255)
            )
        )
    ).apply {
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.argb(110, 170, 205, 255))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        speech?.destroy()
        tts?.stop()
        tts?.shutdown()
        engine.close()
        scope.cancel()
        super.onDestroy()
    }

    private inner class AuraBackground : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var phase = 0f
        private val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 7600
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }
        init { animator.start() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val x1 = w * (.84f - phase * .13f)
            val y1 = h * (.13f + phase * .08f)
            paint.shader = RadialGradient(x1, y1, w * .74f, intArrayOf(Color.argb(70, 72, 125, 225), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(x1, y1, w * .74f, paint)
            val x2 = w * (.08f + phase * .12f)
            val y2 = h * .8f
            paint.shader = RadialGradient(x2, y2, w * .58f, intArrayOf(Color.argb(42, 53, 190, 177), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(x2, y2, w * .58f, paint)
            paint.shader = null
        }
        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }
    }
}
