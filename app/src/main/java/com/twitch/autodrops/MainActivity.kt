package com.twitch.autodrops

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 64)
        }

        val statusText = TextView(this).apply {
            text = "Проверка авторизации..."
            textSize = 16f
        }

        val actionBtn = Button(this).apply {
            text = "Запустить авто-фарм"
            isEnabled = false
        }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }

        layout.addView(statusText)
        layout.addView(actionBtn)
        layout.addView(webView)
        setContentView(layout)

        val prefs = getSharedPreferences("twitch_prefs", Context.MODE_PRIVATE)
        val savedToken = prefs.getString("auth_token", null)

        if (savedToken != null) {
            statusText.text = "Авторизован! Токен сохранён."
            actionBtn.isEnabled = true
            webView.visibility = WebView.GONE
        } else {
            statusText.text = "Войдите в аккаунт Twitch ниже:"
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val cookies = CookieManager.getInstance().getCookie(url) ?: return
                    for (cookie in cookies.split(";")) {
                        val pair = cookie.trim().split("=")
                        if (pair.size == 2 && pair[0] == "auth-token") {
                            val token = pair[1]
                            prefs.edit().putString("auth_token", token).apply()
                            statusText.text = "Успешный вход! Токен перехвачен."
                            actionBtn.isEnabled = true
                            webView.visibility = WebView.GONE
                            break
                        }
                    }
                }
            }
            webView.loadUrl("https://www.twitch.tv/login")
        }

        actionBtn.setOnClickListener {
            val intent = Intent(this, MiningService::class.java)
            startForegroundService(intent)
            statusText.text = "Бот запущен в фоне! Можно выключать экран."
        }
    }
}
