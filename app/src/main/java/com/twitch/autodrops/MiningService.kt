package com.twitch.autodrops

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class MiningService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val client = OkHttpClient()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        startNotification()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TwitchAutoDrops::Lock").apply {
            acquire(12 * 60 * 60 * 1000L) // держим фоновую активность до 12 часов
        }

        startDropCycle()
    }

    private fun startNotification() {
        val channelId = "twitch_farm_channel"
        val channel = NotificationChannel(channelId, "Twitch Auto Farm", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Twitch Drops Miner")
            .setContentText("Автопилот активен: фарм идет в фоне")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun startDropCycle() {
        val prefs = getSharedPreferences("twitch_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("auth_token", "") ?: return

        scope.launch {
            while (isActive) {
                try {
                    // Запрос инвентаря и доступных дропсов через официальный GQL Twitch
                    checkAndClaimDrops(token)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // Проверка каждые 2 минуты
                delay(120_000L)
            }
        }
    }

    private fun checkAndClaimDrops(token: String) {
        val jsonPayload = """
        [
            {
                "operationName": "Inventory",
                "variables": {},
                "extensions": {
                    "persistedQuery": {
                        "version": 1,
                        "sha256Hash": "d564177c9ff3fe4cb10e14a51e6008ce12185b19d45a0b73c4103e33f389d4ae"
                    }
                }
            }
        ]
        """.trimIndent()

        val request = Request.Builder()
            .url("https://gql.twitch.tv/gql")
            .addHeader("Client-Id", "kimne78kx3ncx6brgo4mv6wki5h1ko")
            .addHeader("Authorization", "OAuth $token")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return
                val rootArray = JSONArray(body)
                val inventory = rootArray.getJSONObject(0).getJSONObject("data")
                    .getJSONObject("currentUser").getJSONObject("inventory")
                val dropCampaigns = inventory.getJSONArray("dropCampaignsInProgress")

                for (i in 0 until dropCampaigns.length()) {
                    val campaign = dropCampaigns.getJSONObject(i)
                    val timeBasedDrops = campaign.getJSONArray("timeBasedDrops")

                    for (j in 0 until timeBasedDrops.length()) {
                        val drop = timeBasedDrops.getJSONObject(j)
                        val id = drop.getString("id")
                        val self = drop.getJSONObject("self")
                        val isClaimed = self.getBoolean("isClaimed")
                        val currentMinutes = self.getInt("currentMinutesWatched")
                        val requiredMinutes = drop.getInt("requiredMinutesWatched")

                        // Если прогресс 100%, но не забрано — забираем!
                        if (!isClaimed && currentMinutes >= requiredMinutes) {
                            claimDrop(token, id)
                        }
                    }
                }
            }
        }
    }

    private fun claimDrop(token: String, dropId: String) {
        val payload = """
        [
            {
                "operationName": "CollectDrop",
                "variables": {
                    "input": {
                        "dropInstanceID": "$dropId"
                    }
                },
                "extensions": {
                    "persistedQuery": {
                        "version": 1,
                        "sha256Hash": "2f8f3f01ab6b940026e6438883e6012e841f3e79e612f0a1ea335f639eb38c4d"
                    }
                }
            }
        ]
        """.trimIndent()

        val request = Request.Builder()
            .url("https://gql.twitch.tv/gql")
            .addHeader("Client-Id", "kimne78kx3ncx6brgo4mv6wki5h1ko")
            .addHeader("Authorization", "OAuth $token")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().close()
    }

    override fun onDestroy() {
        job.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
