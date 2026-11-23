package com.example.soundrace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class RaceView(context: Context) : View(context) {

    enum class Kind(val emoji: String, val tip: String) {
        BANANA("🍌", "哎呀！踩到香蕉皮，原地转圈圈～"),
        ROCK("🪨", "哎哟！撞到石头，弹起来啦🤕"),
        MUD("🕳", "噗叽！掉进泥坑，变成扁扁的🥞"),
        FAN("🌀", "呼——被大风扇吹回去啦💨")
    }

    class Obstacle(val pos: Float, val kind: Kind)

    class Racer(val name: String, val color: Int) {
        @Volatile var db = -100f
        var floor = -60f
        var avatar: Bitmap? = null
        var x = 0f
        var speed = 0f
        var stumbleKind: Kind? = null
        var stumbleStart = 0L
        var stumbleEnd = 0L
        var finishTime = 0L
        val hit = HashSet<Int>()

        fun level(): Float = ((db - floor) / 30f).coerceIn(0f, 1f)
    }

    enum class Phase { CALIBRATE, COUNTDOWN, RACING, FINISHED }

    companion object {
        const val MAX_SPEED = 0.12f
        const val TIP_MS = 1500L
        const val RACE_TIMEOUT = 60_000L
    }

    val racers = listOf(Racer("玩家1", 0xFFFF5A5F.toInt()), Racer("玩家2", 0xFF3A86FF.toInt()))
    var onFinished: ((String) -> Unit)? = null

    private var obstacles: List<Obstacle> = emptyList()
    private var phase = Phase.CALIBRATE
    private var phaseStart = 0L
    private var nowMs = 0L
    private var lastFrame = 0L
    private var running = false
    private val calib = List(2) { mutableListOf<Float>() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
            lastFrame = now
            update(now, dt)
            invalidate()
            postOnAnimation(this)
        }
    }

    fun start() {
        obstacles = generateObstacles()
        racers.forEach { r ->
            r.x = 0f
            r.speed = 0f
            r.stumbleKind = null
            r.stumbleStart = 0L
            r.stumbleEnd = 0L
            r.finishTime = 0L
            r.hit.clear()
        }
        calib.forEach { it.clear() }
        setPhase(Phase.CALIBRATE, SystemClock.uptimeMillis())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastFrame = SystemClock.uptimeMillis()
        postOnAnimation(ticker)
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    private fun generateObstacles(): List<Obstacle> {
        val list = mutableListOf<Obstacle>()
        var p = 0.12f
        while (p < 0.92f) {
            list += Obstacle(p, Kind.values().random())
            p += 0.08f + Random.nextFloat() * 0.07f
        }
        return list
    }

    private fun setPhase(p: Phase, now: Long) {
        phase = p
        phaseStart = now
    }

    private fun update(now: Long, dt: Float) {
        nowMs = now
        val elapsed = now - phaseStart
        when (phase) {
            Phase.CALIBRATE -> {
                racers.forEachIndexed { i, r -> if (r.db > -90f) calib[i].add(r.db) }
                if (elapsed > 2500) {
                    racers.forEachIndexed { i, r ->
                        r.floor = if (calib[i].isEmpty()) -60f else calib[i].average().toFloat() + 6f
                    }
                    setPhase(Phase.COUNTDOWN, now)
                }
            }
            Phase.COUNTDOWN -> if (elapsed > 3000) setPhase(Phase.RACING, now)
            Phase.RACING -> {
                racers.forEach { step(it, now, dt) }
                val allDone = racers.all { it.finishTime > 0 }
                if (allDone || elapsed > RACE_TIMEOUT) {
                    phase = Phase.FINISHED
                    val winner = racers.filter { it.finishTime > 0 }.minByOrNull { it.finishTime }
                        ?: racers.maxByOrNull { it.x }!!
                    onFinished?.invoke("🏆 " + winner.name + " 获胜！")
                }
            }
            Phase.FINISHED -> {}
        }
    }

    private fun step(r: Racer, now: Long, dt: Float) {
        if (r.finishTime > 0) return
        if (now < r.stumbleEnd) {
            r.speed = 0f
            return
        }
        val target = r.level() * MAX_SPEED
        r.speed += (target - r.speed) * (dt * 5f).coerceAtMost(1f)
        r.x += r.speed * dt

        obstacles.forEachIndexed { i, o ->
            if (r.x >= o.pos && r.hit.add(i)) {
                r.stumbleKind = o.kind
                r.stumbleStart = now
                r.stumbleEnd = now + TIP_MS
                if (o.kind == Kind.FAN) r.x = (r.x - 0.05f).coerceAtLeast(0f)
            }
        }
        if (r.x >= 1f && r.finishTime == 0L) r.finishTime = now
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * 0.06f
        val right = w * 0.94f
        val trackW = right - left
        val laneH = h * 0.26f

        fun laneTop(i: Int): Float = h * 0.18f + i * (laneH + h * 0.06f)
        fun laneCy(i: Int): Float = laneTop(i) + laneH / 2

        c.drawColor(0xFF1B1F3B.toInt())

        // 赛道
        paint.style = Paint.Style.FILL
        for (i in racers.indices) {
            paint.color = 0xFF2A2F55.toInt()
            c.drawRoundRect(left, laneTop(i), right, laneTop(i) + laneH, 24f, 24f, paint)
        }

        // 终点线
        paint.color = Color.WHITE
        c.drawRect(right - 10f, h * 0.15f, right, h * 0.79f, paint)

        // 障碍
        paint.textSize = laneH * 0.45f
        paint.textAlign = Paint.Align.CENTER
        for (o in obstacles) {
            val ox = left + o.pos * trackW
            for (i in racers.indices) {
                c.drawText(o.kind.emoji, ox, laneCy(i) + paint.textSize / 3, paint)
            }
        }

        // 选手
        racers.forEachIndexed { i, r ->
            val cy = laneCy(i)
            val radius = laneH * 0.3f
            val cx = left + r.x.coerceIn(0f, 1f) * trackW
            var dx = 0f
            var dy = 0f
            var rot = 0f
            var sx = 1f
            var sy = 1f
            val kind = r.stumbleKind
            val stunned = kind != null && nowMs < r.stumbleEnd

            if (kind != null && stunned) {
                val t = ((nowMs - r.stumbleStart).toFloat() / TIP_MS).coerceIn(0f, 1f)
                when (kind) {
                    Kind.BANANA -> rot = 720f * t
                    Kind.ROCK -> dy = -abs(sin(t * 3f * Math.PI.toFloat())) * radius * 1.2f
                    Kind.MUD -> {
                        sx = 1.35f
                        sy = 0.6f
                    }
                    Kind.FAN -> dx = sin(t * 6f * Math.PI.toFloat()) * 14f
                }
            }

            c.save()
            c.translate(cx + dx, cy + dy)
            c.rotate(rot)
            c.scale(sx, sy)
            drawAvatar(c, r, radius)
            c.restore()

            if (kind != null && stunned) {
                paint.textSize = h * 0.045f
                paint.textAlign = Paint.Align.CENTER
                paint.color = Color.WHITE
                c.drawText(kind.tip, cx.coerceIn(w * 0.25f, w * 0.75f), cy - radius - h * 0.03f, paint)
            }
        }

        // 底部音量条
        racers.forEachIndexed { i, r ->
            val y = h * 0.87f + i * h * 0.06f
            paint.color = 0xFF2A2F55.toInt()
            c.drawRoundRect(left, y, left + trackW * 0.5f, y + h * 0.03f, 8f, 8f, paint)
            paint.color = r.color
            c.drawRoundRect(left, y, left + trackW * 0.5f * r.level(), y + h * 0.03f, 8f, 8f, paint)
            paint.color = Color.WHITE
            paint.textSize = h * 0.035f
            paint.textAlign = Paint.Align.LEFT
            val percent = (r.level() * 100).toInt()
            c.drawText(r.name + "  " + percent + "%", left + trackW * 0.52f, y + h * 0.03f, paint)
        }

        // 阶段提示
        val label: String? = when (phase) {
            Phase.CALIBRATE -> "请保持安静，校准环境音…"
            Phase.COUNTDOWN -> {
                val s = 3 - ((nowMs - phaseStart) / 1000).toInt()
                if (s > 0) s.toString() else "GO!"
            }
            else -> null
        }
        if (label != null) {
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = if (phase == Phase.COUNTDOWN) h * 0.3f else h * 0.08f
            c.drawText(label, w / 2, h * 0.55f, paint)
        }
    }

    private fun drawAvatar(c: Canvas, r: Racer, radius: Float) {
        val bmp: Bitmap? = r.avatar
        if (bmp != null) {
            c.save()
            val clip = Path()
            clip.addCircle(0f, 0f, radius, Path.Direction.CW)
            c.clipPath(clip)
            c.drawBitmap(bmp, null, RectF(-radius, -radius, radius, radius), paint)
            c.restore()
        } else {
            paint.style = Paint.Style.FILL
            paint.color = r.color
            c.drawCircle(0f, 0f, radius, paint)
            paint.color = Color.WHITE
            paint.textSize = radius
            paint.textAlign = Paint.Align.CENTER
            c.drawText(r.name.last().toString(), 0f, radius / 3, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = r.color
        c.drawCircle(0f, 0f, radius, paint)
        paint.style = Paint.Style.FILL
    }
}
