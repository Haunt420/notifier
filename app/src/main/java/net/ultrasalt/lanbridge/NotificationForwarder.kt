package net.ultrasalt.lanbridge

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Bundle
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class NotificationForwarder : NotificationListenerService() {
    private val sender = Executors.newSingleThreadExecutor()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras: Bundle = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = (extras.getCharSequence("android.bigText")
            ?: extras.getCharSequence("android.text")
            ?: extras.getCharSequence("android.subText"))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val prefs = getSharedPreferences("bridge", MODE_PRIVATE)
        val host = prefs.getString("receiver_ip", "")?.trim().orEmpty()
        if (host.isBlank()) return

        val payload = JSONObject().apply {
            put("package", sbn.packageName)
            put("title", title)
            put("text", text)
            put("posted", sbn.postTime)
            put("key", sbn.key)
        }.toString()

        sender.execute {
            try {
                val conn = (URL("http://$host:8765/notify").openConnection() as HttpURLConnection)
                conn.requestMethod = "POST"
                conn.connectTimeout = 2500
                conn.readTimeout = 2500
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                conn.inputStream.close()
                conn.disconnect()
            } catch (_: Exception) {
                // No background polling: a failed send is dropped to preserve battery.
            }
        }
    }

    override fun onDestroy() {
        sender.shutdown()
        super.onDestroy()
    }
}
