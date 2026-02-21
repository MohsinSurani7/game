package com.example.confusingmazegame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class ConfusingGameView(context: Context) : View(context) {
    private val bgPaint = Paint().apply { color = Color.rgb(15, 17, 30) }
    private val platformPaint = Paint().apply { color = Color.rgb(75, 210, 140) }
    private val fakePaint = Paint().apply { color = Color.rgb(80, 85, 95) }
    private val trapPaint = Paint().apply { color = Color.rgb(240, 85, 85) }
    private val playerPaint = Paint().apply { color = Color.WHITE }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 46f
        isAntiAlias = true
    }

    private val player = RectF(80f, 80f, 130f, 130f)
    private val velocity = Vector2(0f, 0f)
    private val gravity = 0.86f

    private var level = 1
    private var attempts = 0
    private var leftPressed = false
    private var rightPressed = false
    private var touchY = 0f

    private var cameraY = 0f
    private var deadlyFogY = 0f
    private var levelSeed = 41

    private val platforms = mutableListOf<Platform>()
    private val random = Random(levelSeed)

    init {
        generateLevel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPaint(bgPaint)

        updatePhysics()
        updateCamera()

        for (platform in platforms) {
            val drawRect = RectF(
                platform.rect.left,
                platform.rect.top - cameraY,
                platform.rect.right,
                platform.rect.bottom - cameraY
            )
            if (drawRect.bottom < -60f || drawRect.top > height + 60f) continue

            val paint = when {
                platform.deadly -> trapPaint
                platform.fake -> fakePaint
                else -> platformPaint
            }
            canvas.drawRoundRect(drawRect, 12f, 12f, paint)

            if (platform.hiddenUntilTouch && !platform.revealed) {
                val cover = Paint().apply { color = bgPaint.color }
                canvas.drawRect(drawRect, cover)
            }
        }

        val playerScreen = RectF(player.left, player.top - cameraY, player.right, player.bottom - cameraY)
        canvas.drawRoundRect(playerScreen, 20f, 20f, playerPaint)

        val fogScreenY = deadlyFogY - cameraY
        canvas.drawRect(0f, fogScreenY, width.toFloat(), height.toFloat(), trapPaint)

        canvas.drawText("Level: $level", 24f, 56f, textPaint)
        canvas.drawText("Attempts: $attempts", 24f, 108f, textPaint)
        canvas.drawText("Touch left/right to move, tap upper half to jump", 24f, height - 32f, textPaint)

        postInvalidateOnAnimation()
    }

    private fun updatePhysics() {
        val moveSpeed = 8f + (level * 0.28f)
        velocity.x = when {
            leftPressed && !rightPressed -> -moveSpeed
            rightPressed && !leftPressed -> moveSpeed
            else -> 0f
        }

        velocity.y += gravity
        player.offset(velocity.x, velocity.y)

        if (player.left < 0f) player.offset(-player.left, 0f)
        if (player.right > width) player.offset(width - player.right, 0f)

        var grounded = false
        platforms.forEach { platform ->
            if (platform.hiddenUntilTouch && !platform.revealed && player.centerY() > platform.rect.top - 16f) {
                platform.revealed = true
            }
            if (platform.fake && platform.revealed) return@forEach

            val horizontalHit = player.right > platform.rect.left && player.left < platform.rect.right
            val verticalHit = player.bottom >= platform.rect.top && player.bottom <= platform.rect.bottom + 26f
            if (horizontalHit && verticalHit && velocity.y >= 0f) {
                if (platform.deadly) {
                    respawn()
                    return
                }
                player.offset(0f, platform.rect.top - player.bottom)
                velocity.y = 0f
                grounded = true
                if (platform.fake) {
                    platform.revealed = true
                }
                if (platform.isGoal) {
                    level += 1
                    levelSeed += 13
                    generateLevel()
                    return
                }
            }
        }

        if (!grounded && player.bottom < deadlyFogY - 40f && random.nextFloat() < 0.015f * min(level, 12)) {
            // Surprise reverse gravity pulse for confusion.
            velocity.y -= 14f
        }

        deadlyFogY += 0.55f + level * 0.06f
        if (player.top > deadlyFogY || player.top > cameraY + height + 220f) {
            respawn()
        }
    }

    private fun updateCamera() {
        val target = player.centerY() - height * 0.45f
        cameraY += (target - cameraY) * 0.09f
        cameraY = max(0f, cameraY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                touchY = event.y
                val half = width / 2f
                leftPressed = event.x < half
                rightPressed = event.x >= half
                if (touchY < height / 2f && kotlin.math.abs(velocity.y) < 0.5f) {
                    velocity.y = -16f - min(level, 8)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                leftPressed = false
                rightPressed = false
            }
        }
        return true
    }

    private fun generateLevel() {
        random.nextInt() // stir state
        platforms.clear()

        val gap = 170f
        val baseWidth = 220f
        var y = 320f
        var x = width.coerceAtLeast(900) * 0.1f

        platforms += Platform(RectF(40f, 180f, 360f, 220f), fake = false, deadly = false, hiddenUntilTouch = false)

        repeat(22 + level * 2) { index ->
            x += random.nextInt(-180, 180)
            x = x.coerceIn(40f, max(40f, width - baseWidth - 40f).toFloat())
            y += gap + random.nextInt(-45, 95)

            val fake = random.nextFloat() < min(0.45f, 0.12f + level * 0.03f)
            val deadly = !fake && random.nextFloat() < min(0.25f, 0.05f + level * 0.02f)
            val hidden = !deadly && random.nextFloat() < min(0.4f, 0.08f + level * 0.025f)

            val widthShift = random.nextInt(-70, 40)
            val platformWidth = (baseWidth + widthShift - level * 3).coerceAtLeast(110f)

            platforms += Platform(
                rect = RectF(x, y, x + platformWidth, y + 34f),
                fake = fake,
                deadly = deadly,
                hiddenUntilTouch = hidden,
                isGoal = false
            )

            if (index % 5 == 0 && level > 2) {
                // Unexpected side trap near normal platforms.
                platforms += Platform(
                    rect = RectF(x + platformWidth + 18f, y - 20f, x + platformWidth + 100f, y + 14f),
                    fake = false,
                    deadly = true,
                    hiddenUntilTouch = false,
                    isGoal = false
                )
            }
        }

        val top = platforms.maxOf { it.rect.top } + 180f
        platforms += Platform(
            rect = RectF(width * 0.5f - 120f, top, width * 0.5f + 120f, top + 36f),
            fake = false,
            deadly = false,
            hiddenUntilTouch = false,
            isGoal = true
        )

        player.set(80f, 80f, 130f, 130f)
        velocity.x = 0f
        velocity.y = 0f
        cameraY = 0f
        deadlyFogY = 900f
    }

    private fun respawn() {
        attempts += 1
        player.set(80f, 80f, 130f, 130f)
        velocity.x = 0f
        velocity.y = 0f
        cameraY = 0f
        deadlyFogY = 900f
        platforms.forEach {
            if (it.fake || it.hiddenUntilTouch) {
                it.revealed = false
            }
        }
    }
}

data class Platform(
    val rect: RectF,
    val fake: Boolean,
    val deadly: Boolean,
    val hiddenUntilTouch: Boolean,
    val isGoal: Boolean = false,
    var revealed: Boolean = false
)

data class Vector2(
    var x: Float,
    var y: Float
)
