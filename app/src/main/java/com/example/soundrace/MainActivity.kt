package com.example.soundrace

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val bt: BluetoothAdapter by lazy {
        getSystemService(BluetoothManager::class.java).adapter
    }
    private val meter by lazy { AudioMeter(scope) }
    private lateinit var root: FrameLayout
    private var link: BtLink? = null
    private val avatars = arrayOfNulls<Bitmap>(2)
    private var photoIdx = 0

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { showMenu() }

    private val camLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        avatars[photoIdx] = bmp
        photoIdx++
        askPhoto()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this).apply { setBackgroundColor(0xFF1B1F3B.toInt()) }
        setContentView(root)
        permLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        )
    }

    override fun onDestroy() {
        meter.stop()
        link?.close()
        scope.cancel()
        super.onDestroy()
    }

    // ---------- 界面工具 ----------

    private fun label(s: String, size: Float = 18f) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
    }

    private fun btn(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s
        setOnClickListener { onClick() }
    }

    private fun show(v: View) {
        root.removeAllViews()
        root.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun page(vararg views: View) {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 32, 48, 32)
        }
        views.forEach {
            col.addView(
                it,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 10, 0, 10) }
            )
        }
        show(col)
    }

    // ---------- 菜单 ----------

    private fun showMenu() {
        link?.close()
        link = null
        meter.stop()
        page(
            label("🏁 声浪竞速", 30f),
            label("大声喊，让你的头像冲过终点！", 16f),
            btn("🎮 我是主机（屏幕 + 玩家1）") { hostFlow() },
            btn("🎤 我是麦克风端（玩家2）") { companionFlow() }
        )
    }

    // ---------- 主机 ----------

    private fun hostFlow() {
        avatars.fill(null)
        photoIdx = 0
        askPhoto()
    }

    private fun askPhoto() {
        if (photoIdx >= 2) {
            hostWait()
            return
        }
        page(
            label("拍摄玩家${photoIdx + 1}的头像", 20f),
            btn("📷 拍照") { camLauncher.launch(null) },
            btn("跳过（使用默认头像）") { photoIdx++; askPhoto() }
        )
    }

    private fun hostWait() {
        val status = label("等待玩家2的麦克风端连接…")
        val l = BtLink(bt).also { link = it }
        l.onStatus = { msg -> runOnUiThread { status.text = msg } }
        page(
            label("🎮 主机（玩家1 = 本机麦克风）", 20f),
            status,
            label("未连接玩家2时，玩家2的赛道保持静止", 14f),
            btn("▶ 开始竞速") { startRace() },
            btn("返回") { showMenu() }
        )
        l.listen()
    }

    private fun startRace() {
        val race = RaceView(this)
        avatars.forEachIndexed { i, b -> race.racers[i].avatar = b }

        race.onFinished = { msg ->
            runOnUiThread {
                meter.stop()
                page(
                    label(msg, 32f),
                    btn("🔁 再来一局") { startRace() },
                    btn("返回主菜单") { showMenu() }
                )
            }
        }

        meter.onDb = { db -> race.racers[0].db = db }
        link?.onDb = { db -> race.racers[1].db = db }

        show(race)
        race.start()
        meter.start()
    }

    // ---------- 麦克风端 ----------

    @SuppressLint("MissingPermission")
    private fun companionFlow() {
        val items = mutableListOf<View>(label("选择主机手机（需先在系统蓝牙里配对）", 18f))
        bt.bondedDevices.forEach { d ->
            items += btn(d.name ?: d.address) { connectTo(d) }
        }
        items += btn("返回") { showMenu() }
        page(*items.toTypedArray())
    }

    private fun connectTo(d: BluetoothDevice) {
        val status = label("正在连接 ${d.name ?: d.address}…")
        page(status)
        val l = BtLink(bt).also { link = it }
        l.onStatus = { msg -> runOnUiThread { status.text = msg } }
        l.connect(d) { ok -> if (ok) runOnUiThread { micScreen(l) } }
    }

    private fun micScreen(l: BtLink) {
        val level = label("🎤 正在采集声音…", 22f)
        page(
            level,
            label("请对着手机大声喊！请保持屏幕亮着", 14f),
            btn("断开") { l.close(); meter.stop(); showMenu() }
        )
        meter.onDb = { db ->
            l.send(db)
            runOnUiThread { level.text = "%.0f dB".format(db) }
        }
        meter.start()
    }
}
