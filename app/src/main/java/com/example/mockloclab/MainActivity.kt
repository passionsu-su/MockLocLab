package com.example.mockloclab

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var lat: EditText
    private lateinit var lon: EditText
    private lateinit var label: EditText
    private lateinit var status: TextView
    private lateinit var savedList: LinearLayout
    private val prefs by lazy { getSharedPreferences("places", MODE_PRIVATE) }
    private val requestCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestRuntimePermissions()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        root.addView(TextView(this).apply { text = "MockLoc Lab"; textSize = 26f })
        root.addView(TextView(this).apply {
            text = "Android 系统模拟定位测试工具 · Android 8–15"
            textSize = 14f
            setPadding(0, 6, 0, 14)
        })
        label = field("位置名称（可选）", "测试点")
        lat = field("纬度", "31.2304")
        lon = field("经度", "121.4737")
        root.addView(label)
        root.addView(lat)
        root.addView(lon)
        root.addView(button("打开高德地图查看此坐标") { openAmap() })
        root.addView(button("保存常用位置") { savePlace() })
        root.addView(button("开始模拟定位") { startMock() })
        root.addView(button("停止模拟定位") {
            stopService(Intent(this, MockLocationService::class.java))
            status.text = "已发送停止请求。"
        })
        root.addView(button("验证当前系统定位") { verifyMockLocation() })
        root.addView(button("打开开发者选项设置") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        })
        status = TextView(this).apply {
            text = "先在开发者选项中将“选择模拟位置信息应用”设为 MockLoc Lab。"
            textSize = 14f
            setPadding(0, 10, 0, 12)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "已保存位置（长按删除）"; textSize = 18f })
        val scroll = ScrollView(this)
        savedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(savedList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        renderSaved()
    }

    private fun field(hintText: String, value: String) = EditText(this).apply {
        hint = hintText
        setText(value)
        setSingleLine(true)
        textSize = 16f
    }

    private fun button(textValue: String, action: () -> Unit) = Button(this).apply {
        text = textValue
        setOnClickListener { action() }
    }

    private fun values(): Pair<Double, Double>? {
        val a = lat.text.toString().trim().toDoubleOrNull()
        val b = lon.text.toString().trim().toDoubleOrNull()
        if (a == null || b == null || a !in -90.0..90.0 || b !in -180.0..180.0) {
            status.text = "坐标无效：纬度范围 -90～90，经度范围 -180～180。"
            return null
        }
        return a to b
    }

    private fun openAmap() {
        val p = values() ?: return
        val name = label.text.toString().ifBlank { "测试点" }
        val uri = Uri.parse("androidamap://viewMap?sourceApplication=MockLocLab&lat=${p.first}&lon=${p.second}&poiname=${Uri.encode(name)}&dev=0")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_DEFAULT))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://uri.amap.com/marker?position=${p.second},${p.first}&name=${Uri.encode(name)}")))
        }
    }

    private fun startMock() {
        val p = values() ?: return
        if (!hasLocationPermission()) {
            requestRuntimePermissions()
            status.text = "请先授予定位权限，再点击开始。"
            return
        }
        val i = Intent(this, MockLocationService::class.java)
            .putExtra("lat", p.first).putExtra("lon", p.second)
            .putExtra("label", label.text.toString().ifBlank { "测试点" })
        try {
            startForegroundService(i)
            status.text = "已请求启动。若提示模拟定位权限错误，请确认开发者选项已选择 MockLoc Lab。"
        } catch (e: Exception) {
            status.text = "启动失败：${e.message ?: e.javaClass.simpleName}"
        }
    }


    @Suppress("DEPRECATION")
    private fun verifyMockLocation() {
        if (!hasLocationPermission()) {
            status.text = "尚未授予精确定位权限，请先授权。"
            requestRuntimePermissions()
            return
        }
        val manager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        val found = mutableListOf<String>()
        for (provider in listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER)) {
            try {
                val location = manager.getLastKnownLocation(provider)
                if (location != null) {
                    found.add("$provider: ${"%.6f".format(location.latitude)}, ${"%.6f".format(location.longitude)}")
                }
            } catch (_: SecurityException) {
                status.text = "系统拒绝读取定位。请确认已授予精确定位权限，并在开发者选项中选择 MockLoc Lab。"
                return
            } catch (_: Exception) {
                // Provider may not be available on every device.
            }
        }
        status.text = if (found.isEmpty()) {
            "暂未读取到定位结果。请确认开发者选项已选择 MockLoc Lab，启动模拟定位后等待数秒再验证。"
        } else {
            "系统当前可读取的定位：\n" + found.joinToString("\n")
        }
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(perms.toTypedArray(), requestCode)
    }

    private fun savePlace() {
        val p = values() ?: return
        val arr = try { JSONArray(prefs.getString("items", "[]")) } catch (_: Exception) { JSONArray() }
        arr.put(JSONObject().put("name", label.text.toString().ifBlank { "位置 ${arr.length() + 1}" }).put("lat", p.first).put("lon", p.second))
        prefs.edit().putString("items", arr.toString()).apply()
        renderSaved()
        status.text = "已保存位置。"
    }

    private fun renderSaved() {
        savedList.removeAllViews()
        val arr = try { JSONArray(prefs.getString("items", "[]")) } catch (_: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name", "位置")
            val a = obj.optDouble("lat")
            val b = obj.optDouble("lon")
            savedList.addView(Button(this).apply {
                text = "$name  ($a, $b)"
                setOnClickListener {
                    label.setText(name); lat.setText(a.toString()); lon.setText(b.toString())
                    status.text = "已载入位置；点击“开始模拟定位”生效。"
                }
                setOnLongClickListener {
                    val next = JSONArray()
                    for (j in 0 until arr.length()) if (j != i) next.put(arr.getJSONObject(j))
                    prefs.edit().putString("items", next.toString()).apply()
                    renderSaved()
                    Toast.makeText(this@MainActivity, "已删除位置", Toast.LENGTH_SHORT).show()
                    true
                }
            })
        }
        if (arr.length() == 0) savedList.addView(TextView(this).apply { text = "暂无保存的位置。" })
    }
}
