package de.blinkt.openvpn.fragments

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/**
 * SSH profile editor. This stores connection parameters and can test TCP reachability.
 * A TCP check is not an SSH handshake and does not create a VPN tunnel.
 */
class SshProfileFragment : Fragment() {
    private val prefs by lazy { requireContext().getSharedPreferences("ssh_profiles", 0) }
    private val worker = Executors.newSingleThreadExecutor()
    private val fields = linkedMapOf<String, EditText>()

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val scroll = ScrollView(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 28)
        }
        scroll.addView(root)

        root.addView(TextView(context).apply {
            text = "SSH Tunnel • โปรไฟล์การเชื่อมต่อ"
            textSize = 22f
        })
        root.addView(TextView(context).apply {
            text = "บันทึกค่าของเซิร์ฟเวอร์ได้ในเครื่องนี้ และตรวจสอบได้เพียงว่า TCP port ตอบรับหรือไม่"
            textSize = 14f
            setPadding(0, 8, 0, 18)
        })

        addField(root, "profile_name", "ชื่อโปรไฟล์", "My SSH")
        addField(root, "host", "Server / Host", "")
        addField(root, "port", "SSH Port", "22")
        addField(root, "username", "Username", "")
        addField(root, "password", "Password (เก็บในเครื่อง)", "", true)
        addField(root, "payload", "Payload / HTTP request (ถ้าเซิร์ฟเวอร์รองรับ)", "")
        addField(root, "proxy", "Proxy host:port (ไม่บังคับ)", "")

        val status = TextView(context).apply {
            text = "สถานะ: ยังไม่ได้ทดสอบ"
            textSize = 15f
            setPadding(0, 12, 0, 12)
        }
        root.addView(status)

        root.addView(Button(context).apply {
            text = "บันทึกโปรไฟล์"
            setOnClickListener {
                val editor = prefs.edit()
                fields.forEach { (key, field) -> editor.putString(key, field.text.toString()) }
                editor.apply()
                Toast.makeText(context, "บันทึกโปรไฟล์ในเครื่องแล้ว", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(context).apply {
            text = "ทดสอบ TCP ไปยังเซิร์ฟเวอร์"
            setOnClickListener {
                val host = fields["host"]?.text?.toString()?.trim().orEmpty()
                val port = fields["port"]?.text?.toString()?.toIntOrNull()
                if (host.isBlank() || port == null || port !in 1..65535) {
                    status.text = "สถานะ: กรุณากรอก Host และ Port ที่ถูกต้อง"
                    return@setOnClickListener
                }
                status.text = "สถานะ: กำลังทดสอบ TCP…"
                worker.execute {
                    val message = try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(host, port), 5000)
                        }
                        "TCP เชื่อมต่อได้ที่ $host:$port — ยังไม่ได้ยืนยัน SSH login หรือ VPN"
                    } catch (e: Exception) {
                        "TCP เชื่อมต่อไม่สำเร็จ: ${e.message ?: e.javaClass.simpleName}"
                    }
                    activity?.runOnUiThread { if (isAdded) status.text = "สถานะ: $message" }
                }
            }
        })
        root.addView(TextView(context).apply {
            text = "หมายเหตุ: หน้านี้เป็นตัวจัดการโปรไฟล์และตัวตรวจ TCP เท่านั้น ยังไม่สร้าง SSH tunnel จริง และไม่ส่งข้อมูลผู้ใช้ไปยังเซิร์ฟเวอร์จนกว่าจะเพิ่ม backend SSH ที่เหมาะสม"
            textSize = 13f
            setPadding(0, 16, 0, 0)
        })

        fields.forEach { (key, field) ->
            field.setText(prefs.getString(key, field.text.toString()))
        }
        return scroll
    }

    private fun addField(root: LinearLayout, key: String, label: String, default: String, secret: Boolean = false) {
        val context = requireContext()
        root.addView(TextView(context).apply { text = label; textSize = 14f })
        val input = EditText(context).apply {
            hint = label
            setSingleLine(key != "payload")
            if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            if (key == "port") inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(default)
        }
        fields[key] = input
        root.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
