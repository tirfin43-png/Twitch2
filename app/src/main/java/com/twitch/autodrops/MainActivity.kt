package com.twitch.autodrops

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }

        val statusText = TextView(this).apply {
            text = "Загрузка страницы входа Twitch..."
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }

        val actionBtn = Button(this).apply {
            text = "ЗАПУСТИТЬ АВТО-ФАРМ"
            isEnabled = false
            visibility = View.GONE
        }

        val webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                // Маскируем WebView под обычный мобильный Chrome, чтобы обойти блокировку Twitch
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        layout.addView(statusText)
        layout.addView(progressBar)
        layout.addView(actionBtn)
        layout.addView(webView)
        setContentView(layout)

        val prefs = getSharedPreferences("twitch_prefs", Context.MODE_PRIVATE)
        val savedToken = prefs.getString("auth_token", null)

        if (savedToken != null) {
            statusText.text = "Авторизован! Бот готов к работе."
            progressBar.visibility = View.GONE
            actionBtn.visibility = View.VISIBLE
            actionBtn.isEnabled = true
            webView.visibility = View.GONE
        } else {
            webView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 80) {
                        progressBar.visibility = View.GONE
                        statusText.text = "Войдите в свой аккаунт Twitch ниже:"
                    }
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val cookies = CookieManager.getInstance().getCookie("https://www.twitch.tv") ?: ""
                    for (cookie in cookies.split(";")) {
                        val pair = cookie.trim().split("=")
                        if (pair.size == 2 && pair[0] == "auth-token") {
                            val token = pair[1]
                            prefs.edit().putString("auth_token", token).apply()
                            statusText.text = "Успешный вход! Токен перехвачен."
                            actionBtn.visibility = View.VISIBLE
                            actionBtn.isEnabled = true
                            webView.visibility = View.GONE
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
            statusText.text = "Фарм запущен в фоне! Экран можно блокировать."
            actionBtn.isEnabled = false
            actionBtn.text = "ФАРМ АКТИВЕН"
        }
    }
}
