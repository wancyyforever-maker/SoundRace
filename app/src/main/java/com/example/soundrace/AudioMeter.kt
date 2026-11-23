package com.example.soundrace

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlin.math.log10
import kotlin.math.sqrt

/** 读取麦克风，输出 dBFS 分贝值（约 -100 ~ 0） */
class AudioMeter(private val scope: CoroutineScope) {

    var onDb: ((Float) -> Unit)? = null
    private var job: Job? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (job != null) return
        val rate = 44100
        val minBuf = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        // VOICE_RECOGNITION 通常会关闭自动增益和降噪，分贝变化更真实
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
        )
        job = scope.launch(Dispatchers.IO) {
            val buf = ShortArray(minBuf / 2)
            rec.startRecording()
            try {
                while (isActive) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) onDb?.invoke(toDb(buf, n))
                }
            } finally {
                rec.stop()
                rec.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun toDb(buf: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) {
            val v = buf[i] / 32768.0
            sum += v * v
        }
        val rms = sqrt(sum / n)
        return if (rms < 1e-6) -100f else (20 * log10(rms)).toFloat()
    }
}
