package com.example.soundrace

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.Locale
import java.util.UUID

/**
 * 蓝牙经典 SPP 通道。麦克风端连接主机，持续发送一行一行的分贝值。
 * 注意：这里传输的是分贝数据，而不是音频流，延迟低、占用小。
 */
class BtLink(private val adapter: BluetoothAdapter) {

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @Volatile var connected = false
    var onStatus: ((String) -> Unit)? = null
    var onDb: ((Float) -> Unit)? = null

    private var server: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null

    /** 主机端：等待麦克风端连接，并持续读取分贝 */
    @SuppressLint("MissingPermission")
    fun listen() {
        Thread {
            try {
                val srv = adapter.listenUsingRfcommWithServiceRecord("SoundRace", SPP_UUID)
                server = srv
                onStatus?.invoke("等待玩家2的麦克风端连接…")
                val s = srv.accept()
                srv.close()
                socket = s
                connected = true
                onStatus?.invoke("✅ 玩家2已连接，可以开始")
                BufferedReader(InputStreamReader(s.inputStream)).useLines { lines ->
                    lines.forEach { line -> line.toFloatOrNull()?.let { onDb?.invoke(it) } }
                }
            } catch (e: IOException) {
                onStatus?.invoke("蓝牙已断开")
            } finally {
                connected = false
                onDb?.invoke(-100f) // 断开后玩家2视为安静
            }
        }.start()
    }

    /** 麦克风端：连接已配对的主机 */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, done: (Boolean) -> Unit) {
        Thread {
            try {
                adapter.cancelDiscovery()
                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                s.connect()
                socket = s
                out = s.outputStream
                connected = true
                done(true)
            } catch (e: IOException) {
                onStatus?.invoke("连接失败：请确认两台手机已配对，且主机处于等待状态")
                done(false)
            }
        }.start()
    }

    fun send(db: Float) {
        val o = out ?: return
        try {
            o.write(String.format(Locale.US, "%.1f\n", db).toByteArray())
        } catch (e: IOException) {
            connected = false
            onStatus?.invoke("蓝牙已断开")
        }
    }

    fun close() {
        try {
            server?.close()
            socket?.close()
        } catch (_: IOException) {
        }
        connected = false
    }
}
