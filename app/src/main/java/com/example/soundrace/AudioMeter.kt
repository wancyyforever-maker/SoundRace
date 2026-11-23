{\rtf1\ansi\ansicpg936\cocoartf2822
\cocoatextscaling0\cocoaplatform0{\fonttbl\f0\fswiss\fcharset0 Helvetica;}
{\colortbl;\red255\green255\blue255;}
{\*\expandedcolortbl;;}
\paperw11900\paperh16840\margl1440\margr1440\vieww11520\viewh8400\viewkind0
\pard\tx720\tx1440\tx2160\tx2880\tx3600\tx4320\tx5040\tx5760\tx6480\tx7200\tx7920\tx8640\pardirnatural\partightenfactor0

\f0\fs24 \cf0 package com.example.soundrace\
\
import android.annotation.SuppressLint\
import android.media.AudioFormat\
import android.media.AudioRecord\
import android.media.MediaRecorder\
import kotlinx.coroutines.*\
import kotlin.math.log10\
import kotlin.math.sqrt\
\
/** \uc0\u35835 \u21462 \u40614 \u20811 \u39118 \u65292 \u36755 \u20986  dBFS \u20998 \u36125 \u20540 \u65288 \u32422  -100 ~ 0\u65289  */\
class AudioMeter(private val scope: CoroutineScope) \{\
\
    var onDb: ((Float) -> Unit)? = null\
    private var job: Job? = null\
\
    @SuppressLint("MissingPermission")\
    fun start() \{\
        if (job != null) return\
        val rate = 44100\
        val minBuf = AudioRecord.getMinBufferSize(\
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT\
        )\
        // VOICE_RECOGNITION \uc0\u36890 \u24120 \u20250 \u20851 \u38381 \u33258 \u21160 \u22686 \u30410 \u21644 \u38477 \u22122 \u65292 \u20998 \u36125 \u21464 \u21270 \u26356 \u30495 \u23454 \
        val rec = AudioRecord(\
            MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,\
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2\
        )\
        job = scope.launch(Dispatchers.IO) \{\
            val buf = ShortArray(minBuf / 2)\
            rec.startRecording()\
            try \{\
                while (isActive) \{\
                    val n = rec.read(buf, 0, buf.size)\
                    if (n > 0) onDb?.invoke(toDb(buf, n))\
                \}\
            \} finally \{\
                rec.stop()\
                rec.release()\
            \}\
        \}\
    \}\
\
    fun stop() \{\
        job?.cancel()\
        job = null\
    \}\
\
    private fun toDb(buf: ShortArray, n: Int): Float \{\
        var sum = 0.0\
        for (i in 0 until n) \{\
            val v = buf[i] / 32768.0\
            sum += v * v\
        \}\
        val rms = sqrt(sum / n)\
        return if (rms < 1e-6) -100f else (20 * log10(rms)).toFloat()\
    \}\
\}}
