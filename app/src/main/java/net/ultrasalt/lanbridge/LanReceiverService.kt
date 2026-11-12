package net.ultrasalt.lanbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LanReceiverService : Service() {
    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private val workers = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CHANNEL).setContentTitle("LAN Notification Bridge")
                .setContentText("Listening for notifications on Wi-Fi")
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim).setContentIntent(open).build()
        else Notification.Builder(this).setContentTitle("LAN Notification Bridge")
            .setContentText("Listening for notifications on Wi-Fi")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim).setContentIntent(open).build()
        startForeground(1, notification)
        running.set(true)
        workers.execute { listen() }
    }

    private fun listen() {
        try {
            server = ServerSocket(8765)
            server?.reuseAddress = true
            while (running.get()) {
                val socket = server?.accept() ?: break
                handle(socket)
            }
        } catch (_: Exception) {
            // Service can be restarted by the user if the socket fails.
        }
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 4000
            val input = BufferedInputStream(s.getInputStream())
            val header = StringBuilder()
            var matched = 0
            val marker = "\r\n\r\n"
            while (header.length < 8192 && matched < 4) {
                val c = input.read()
                if (c < 0) break
                header.append(c.toChar())
                matched = when {
                    c.toChar() == marker[matched] -> matched + 1
                    c.toChar() == '\r' -> 1
                    else -> 0
                }
            }
            val h = header.toString()
            val length = Regex("(?i)Content-Length:\\s*(\\d+)").find(h)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (!h.startsWith("POST /notify") || length <= 0 || length > 32768) {
                reply(s, 400, "bad request"); return
            }
            val body = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val n = input.read(body, offset, length - offset)
                if (n <= 0) break
                offset += n
            }
            if (offset != length) { reply(s, 400, "incomplete"); return }
            try {
                showNotification(JSONObject(String(body, Charsets.UTF_8)))
                reply(s, 200, "ok")
            } catch (_: Exception) {
                reply(s, 400, "invalid payload")
            }
        }
    }

    private fun reply(socket: Socket, code: Int, message: String) {
        val bytes = message.toByteArray(Charsets.UTF_8)
        val out = BufferedOutputStream(socket.getOutputStream())
        out.write("HTTP/1.1 $code ${if (code == 200) "OK" else "Bad Request"}\r\n".toByteArray())
        out.write("Content-Type: text/plain\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes); out.flush()
    }

    private fun showNotification(data: JSONObject) {
        val pkg = data.optString("package", "Unknown app")
        val title = data.optString("title", "").ifBlank { pkg }
        val body = data.optString("text", "")
        val id = (pkg + data.optString("key", "")).hashCode()
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, ALERT_CHANNEL)
        else Notification.Builder(this)
        val n = builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true).build()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, n)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Bridge service", NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Forwarded notifications", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running.set(false)
        try { server?.close() } catch (_: Exception) {}
        workers.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "lan_bridge_service"
        private const val ALERT_CHANNEL = "forwarded_notifications"
    }
}
