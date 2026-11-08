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
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText

    private val bg = Color.rgb(10, 13, 23)
    private val glass = Color.argb(185, 29, 37, 55)
    private val muted = Color.rgb(151, 164, 187)
    private val accent = Color.rgb(155, 190, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(18, 25, 42), bg, Color.rgb(15, 22, 37))
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(22))
        }
        header.addView(TextView(this).apply {
            text = "DICA"
            textSize = 29f
            letterSpacing = .13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
        })
        header.addView(TextView(this).apply {
            text = "AI"
            textSize = 29f
            letterSpacing = .13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(accent)
            translationY = -dp(8).toFloat()
        })
        val status = TextView(this).apply {
            text = "●  MNN-POCKET 0.3B   ·   ЛОКАЛЬНАЯ МОДЕЛЬ"
            textSize = 10f
            letterSpacing = .06f
            setTextColor(muted)
            translationY = -dp(8).toFloat()
        }
        header.addView(status)
        root.addView(header)

        scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
        }
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(16))
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(6), dp(7), dp(6))
            background = rounded(glass, Color.argb(85, 180, 202, 255), 28)
            elevation = dp(8).toFloat()
        }
        input = EditText(this).apply {
            hint = "Спросите что-нибудь..."
            setHintTextColor(Color.rgb(125, 139, 163))
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 4
            minLines = 1
            setPadding(dp(13), dp(8), dp(8), dp(8))
            background = null
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
            }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        val send = TextView(this).apply {
            text = "↑"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(12, 20, 35))
            background = rounded(accent, 0, 22)
            setOnClickListener { sendMessage() }
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(46), dp(46)))
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
            addView(composer)
            addView(TextView(this@MainActivity).apply {
                text = "DICAAI  ·  ВСЕ ДАННЫЕ ОСТАЮТСЯ НА УСТРОЙСТВЕ"
                textSize = 9f
                letterSpacing = .07f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(100, 113, 137))
                setPadding(0, dp(12), 0, 0)
            })
        }
        root.addView(bottom)
        setContentView(root)

        addMessage("DICAAI", "Привет! Я DICAAI. Подключение локальной модели находится в разработке.", false)
        chat.alpha = 0f
        chat.animate().alpha(1f).setDuration(500).start()
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty()) return
        addMessage("Вы", message, true)
        addMessage("DICAAI", "Модель ещё подключается. Пока я не могу генерировать ответы.", false)
        input.text.clear()
    }

    private fun addMessage(author: String, body: String, mine: Boolean) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            background = rounded(
                if (mine) Color.argb(210, 53, 78, 119) else glass,
                Color.argb(if (mine) 100 else 75, 190, 210, 255),
                20
            )
            alpha = 0f
            translationY = dp(10).toFloat()
        }
        card.addView(TextView(this).apply {
            text = author
            textSize = 11f
            letterSpacing = .04f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (mine) Color.rgb(207, 224, 255) else accent)
        })
        card.addView(TextView(this).apply {
            text = body
            textSize = 15f
            setTextColor(Color.rgb(239, 243, 251))
            setPadding(0, dp(6), 0, 0)
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        val params = LinearLayout.LayoutParams(
            if (mine) (resources.displayMetrics.widthPixels * .82f).toInt() else -1, -2
        )
        params.gravity = if (mine) Gravity.END else Gravity.START
        params.bottomMargin = dp(12)
        chat.addView(card, params)
        card.animate().alpha(1f).translationY(0f).setDuration(260).start()
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun rounded(color: Int, stroke: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
