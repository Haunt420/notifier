package net.ultrasalt.lanbridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private lateinit var ip: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (18 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val heading = TextView(this).apply {
            text = "LAN Notification Bridge"
            textSize = 23f
        }
        layout.addView(heading)
        layout.addView(TextView(this).apply {
            text = "One-way forwarding: Artia sender → Galaxy A10 receiver"
            textSize = 14f
            setPadding(0, pad / 2, 0, pad)
        })
        layout.addView(TextView(this).apply {
            text = "SENDER — enter the receiver phone's Wi-Fi IPv4 address"
            textSize = 14f
        })
        ip = EditText(this).apply {
            hint = "Example: 192.168.1.105"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(getSharedPreferences("bridge", MODE_PRIVATE).getString("receiver_ip", ""))
        }
        layout.addView(ip)
        val save = Button(this).apply {
            text = "Save receiver address"
            setOnClickListener {
                getSharedPreferences("bridge", MODE_PRIVATE).edit()
                    .putString("receiver_ip", ip.text.toString().trim()).apply()
                status.text = "Receiver address saved."
            }
        }
        layout.addView(save)
        val permission = Button(this).apply {
            text = "Enable notification access (sender phone)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        layout.addView(permission)
        val start = Button(this).apply {
            text = "Start receiver (Galaxy A10)"
            setOnClickListener {
                val intent = Intent(this@MainActivity, LanReceiverService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                status.text = "Receiver service started. Find this phone's Wi-Fi IP below."
            }
        }
        layout.addView(start)
        val stop = Button(this).apply {
            text = "Stop receiver"
            setOnClickListener {
                stopService(Intent(this@MainActivity, LanReceiverService::class.java))
                status.text = "Receiver stopped."
            }
        }
        layout.addView(stop)
        val info = Button(this).apply {
            text = "Show this phone's Wi-Fi IP"
            setOnClickListener { status.text = "This phone's Wi-Fi IP: ${wifiIpAddress()}" }
        }
        layout.addView(info)
        status = TextView(this).apply {
            text = "Setup: install on both phones. On the Galaxy A10, start Receiver and note its IP. On the Artia, enter that IP and enable notification access."
            textSize = 14f
            setPadding(0, pad, 0, 0)
        }
        layout.addView(status)
        setContentView(ScrollView(this).apply { addView(layout) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiIpAddress(): String {
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val raw = wm.connectionInfo.ipAddress
        return if (raw == 0) "Not connected to Wi-Fi" else
            "${raw and 0xff}.${raw shr 8 and 0xff}.${raw shr 16 and 0xff}.${raw shr 24 and 0xff}"
    }
}
