package com.dioscaridos.mnnai

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 17, 26)
        window.navigationBarColor = Color.rgb(13, 17, 26)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 24, 18, 14)
            setBackgroundColor(Color.rgb(13, 17, 26))
        }

        root.addView(TextView(this).apply {
            text = "MNN-AI"
            textSize = 27f
            setTextColor(Color.WHITE)
            setPadding(4, 8, 4, 2)
        })
        root.addView(TextView(this).apply {
            text = "MNN-Pocket-0.3B • локальная модель"
            textSize = 13f
            setTextColor(Color.rgb(155, 166, 185))
            setPadding(4, 0, 4, 18)
        })

        scroll = ScrollView(this)
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 8, 2, 8)
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addMessage("MNN-AI", "Привет! Интерфейс готов. Движок MNN и модель подключим следующим этапом.", false)

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(this).apply {
            hint = "Сообщение..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(14, 8, 14, 8)
            setBackgroundColor(Color.rgb(34, 42, 58))
        }
        composer.addView(input, LinearLayout.LayoutParams(0, 54, 1f))
        composer.addView(Button(this).apply {
            text = "➤"
            setOnClickListener {
                val message = input.text.toString().trim()
                if (message.isNotEmpty()) {
                    addMessage("Вы", message, true)
                    addMessage("MNN-AI", "Нейросетевой движок ещё не подключён.", false)
                    input.text.clear()
                }
            }
        })
        root.addView(composer)
        setContentView(root)
    }

    private fun addMessage(author: String, body: String, mine: Boolean) {
        val bubble = TextView(this).apply {
            text = "$author\n$body"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(14, 12, 14, 12)
            setBackgroundColor(if (mine) Color.rgb(42, 75, 125) else Color.rgb(34, 42, 58))
        }
        val params = LinearLayout.LayoutParams(
            if (mine) (resources.displayMetrics.widthPixels * 0.82f).toInt() else -1, -2
        )
        params.gravity = if (mine) Gravity.END else Gravity.START
        params.bottomMargin = 12
        chat.addView(bubble, params)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
}
