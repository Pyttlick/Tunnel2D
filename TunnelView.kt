package com.example.tunnel2d

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.*
import kotlin.math.sqrt


class TunnelView : SurfaceView, SurfaceHolder.Callback {


    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        // Zavolej tady co potřebuješ, např:
        // initTunnel()
        // startGameLoop()
    }

    // ▪ Hra a vykreslování
    private var thread: GameThread? = null
    private val paint = Paint()
    private val path = Path()

    // ▪ Tunel
    private val tunnel = mutableListOf<Float>()
    private var dy = 0f
    private var dx = 0f
    private var targetDy = 0f
    // private var frameCount = 0
    private var maxTunnelLength = 0

    companion object {
        const val STEP = 2f
        const val TUNNEL_WIDTH = 120f
        const val FRAME_DELAY = 16L // ~60 FPS
        val tunnelColors = listOf(
            Color.parseColor("#808080"), // gray
            Color.parseColor("#A9A9A9"), // darkgray
            Color.parseColor("#D3D3D3"), // lightgray
            Color.parseColor("#696969"), // dimgray
            Color.parseColor("#708090"), // slategray

            Color.parseColor("#0000FF"), // blue
            Color.parseColor("#000080"), // navy
            Color.parseColor("#87CEEB"), // skyblue
            Color.parseColor("#4682B4"), // steelblue
            Color.parseColor("#4169E1"), // royalblue

            Color.parseColor("#008000"), // green
            Color.parseColor("#006400"), // darkgreen
            Color.parseColor("#2E8B57"), // seagreen
            Color.parseColor("#808000"), // olive
            Color.parseColor("#32CD32"), // limegreen

            Color.parseColor("#800080"), // purple
            Color.parseColor("#4B0082"), // indigo
            Color.parseColor("#9400D3"), // darkviolet
            Color.parseColor("#FFC0CB"), // pink
            Color.parseColor("#BA55D3"), // mediumorchid

            Color.parseColor("#FFA500"), // orange
            Color.parseColor("#FF4500"), // orangered
            Color.parseColor("#FF8C00"), // darkorange
            Color.parseColor("#FFD700"), // gold
            Color.parseColor("#D2691E")  // chocolate
        )

    }

    private val topMargin = 20f
    private val bottomMargin = 20f

    // ▪ Kutloch (raketka)
    private val kutlochBitmap = arrayOf(
        0b0000000000000000,
        0b0000000000000000,
        0b0000000000000000,
        0b0000000000000000,
        0b0001110000000000,
        0b0000011110000000,
        0b0000011111111110,
        0b0000001111111111,
        0b0000001111111111,
        0b0000011111111110,
        0b0000011110000000,
        0b0001110000000000,
        0b0000000000000000,
        0b0000000000000000,
        0b0000000000000000,
        0b0000000000000000,
    )
    private var kutlochX = 0f
    private var kutlochY = 0f
    private val kutlochSizePx = 1f

    // ▪ Skóre a životy
    private var score = 0
    private val scorePaint = Paint().apply {
        color = Color.WHITE
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
    }

    // ▪ plop
    private var plop = 0
    private val plopPaint = Paint().apply {
        color = Color.MAGENTA
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
    }

    private var lives = 5
    private val maxLives = 5
    private val lifeRadius = 10f
    private val lifeMargin = 10f
    val distance = sqrt(dx * dx + dy * dy)


    // ▪ Stav hry
 //   enum class GameState { RUNNING, EXPLODING, PAUSED } //první návrh
    // v "update" musí ošetřeny všechny stavy, jinak chyba "exhaust"
    enum class GameState {
        RUNNING,
        EXPLODING,
        PAUSED,        // výbuch
        PAUSE_MODE,    // uživatelská pauza
        GAME_OVER
    }

    private var gameState = GameState.RUNNING
    private var explosionStartTime = 0L
    private val explosionParticles = mutableListOf<ExplosionParticle>()

    var rovinka = 0
    var lastNewY: Int? = null
    val PLOCHAPODKOULI = 10
    val ROZBEH = 100
    var frameCount = 0 // potřebujeme pro ROZBEH

    // Ovládací stav joysticku
    private var joyDx = 0f
    private var joyDy = 0f
    private var isTouching = false

    data class Ball(var x: Float, var y: Float, val color: Int)
    val balls = mutableListOf<Ball>()
    val ballColors = listOf(
        Color.YELLOW,
        Color.CYAN,
        Color.MAGENTA,
        Color.GREEN,
        Color.WHITE,
        Color.RED,
        Color.BLUE,
        Color.LTGRAY,
        Color.DKGRAY,
        Color.BLACK,
        Color.GRAY,
        Color.rgb(255, 165, 0),   // ORANGE
        Color.rgb(128, 0, 128),   // PURPLE
        Color.rgb(0, 255, 127),   // SPRING GREEN
        Color.rgb(255, 20, 147)   // DEEP PINK
    )

    var lastPlop = -1
    private var substanceColor = Color.DKGRAY
    var tunnelColor: Int = Color.GRAY

    data class ExplosionParticle(
        var x: Float, var y: Float,
        val dx: Float, val dy: Float,
        val color: Int
    )

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        maxTunnelLength = width / STEP.toInt() + 2
        initTunnel()
        kutlochX = width /4f
        kutlochY = height / 2f
        tunnelColor = getRandomTunnelColor()
        substanceColor = tunnelColor
        thread = GameThread(holder, this).apply { running = true; start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        thread?.running = false
        thread?.join()
    }

    private fun initTunnel() {
        val centerY = height / 2f
        tunnel.clear()
        repeat(maxTunnelLength) { tunnel.add(centerY) }
    }

    fun update() {
        if (gameState == GameState.PAUSE_MODE) {
            return // Neaktualizujeme ani tunel
        }

        if (tunnel.size < 2) {
            Log.w("TunnelView", "update: tunel je moc krátký (${tunnel.size}), inicializuji znovu.")
            initTunnel()
            return
        }

        if (tunnel.isNotEmpty()) {
            tunnel.removeAt(0)
        }

        frameCount++
        if (frameCount % 5 == 0) targetDy = (-5..5).random() * 1f
        dy += (targetDy - dy)

        val lastY = tunnel.lastOrNull() ?: (height / 2f)
        val newY = (lastY + dy).coerceIn(topMargin + TUNNEL_WIDTH / 2, height - bottomMargin - TUNNEL_WIDTH / 2)
        tunnel.add(newY)

        when (gameState) {
                        GameState.RUNNING -> {
                            if (isTouching) {
                                val baseSpeed = 8f
                                val reverseMultiplier = if (joyDx < 0 || joyDy < 0) 1.5f else 1f
                                val maxSpeed = baseSpeed * reverseMultiplier

                                kutlochX += joyDx * maxSpeed
                                kutlochY += joyDy * maxSpeed

                                kutlochX = kutlochX.coerceIn(0f, width - 16 * kutlochSizePx)
                                kutlochY = kutlochY.coerceIn(0f, height - 16 * kutlochSizePx)
                            }


                            if (checkCollision()) {
                    lives--
                    if (lives <= 0) {
                        gameState = GameState.GAME_OVER
                        explosionStartTime = System.currentTimeMillis()
                        generateExplosionParticles()
                    } else {
                        gameState = GameState.EXPLODING
                        explosionStartTime = System.currentTimeMillis()
                        generateExplosionParticles()
                    }
                } else {
                    score++
                }
            }

            GameState.EXPLODING -> {
                val elapsed = System.currentTimeMillis() - explosionStartTime
                explosionParticles.forEach { it.x += it.dx; it.y += it.dy }

                if (elapsed > 3000) {
                    gameState = GameState.PAUSED
                }
            }

            GameState.PAUSED -> {
                if (System.currentTimeMillis() - explosionStartTime > 5000) {
                    threadSafeResetGame()
                }
            }
            GameState.PAUSE_MODE -> {
                return // nic nedělej, pauza
            }


                    GameState.GAME_OVER -> {
                // Po 5 sekundách restartuj hru
                if (System.currentTimeMillis() - explosionStartTime > 5000) {
                    threadSafeRestartFullGame()
                }
            }
        }


        detectRovinka(tunnel.last().toInt())
        updateBalls()
        collectBalls()
    }


    private fun generateExplosionParticles() {
        val centerX = kutlochX + 8 * kutlochSizePx
        val centerY = kutlochY + 8 * kutlochSizePx
        val colors = listOf(Color.YELLOW, Color.RED, Color.rgb(255, 165, 0))
        explosionParticles.clear()
        repeat(100) {
            val angle = Math.random() * 2 * Math.PI
            val speed = (1..5).random()
            explosionParticles.add(
                ExplosionParticle(
                    centerX, centerY,
                    (Math.cos(angle) * speed).toFloat(),
                    (Math.sin(angle) * speed).toFloat(),
                    colors.random()
                )
            )
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.drawColor(Color.BLACK)
        if (tunnel.size < 2) {
            Log.w("TunnelView", "draw: tunel je příliš krátký (${tunnel.size}), nic nekreslím.")
            return
        }

        // ▪ Vykresli horní a dolní substance oblast
        paint.color = substanceColor
        paint.style = Paint.Style.FILL

        for (i in 0 until tunnel.size - 1) {
            val x1 = i * STEP
            if (x1 > width) break
            val y1 = tunnel[i]
            val y2 = tunnel[i + 1]
            val y1Top = y1 - TUNNEL_WIDTH / 2
            val y2Top = y2 - TUNNEL_WIDTH / 2
            val y1Bot = y1 + TUNNEL_WIDTH / 2
            val y2Bot = y2 + TUNNEL_WIDTH / 2

            // Horní část (nad tunelem)
            path.reset()
            path.moveTo(x1, 0f)
            path.lineTo(x1 + STEP, 0f)
            path.lineTo(x1 + STEP, y2Top)
            path.lineTo(x1, y1Top)
            path.close()
            canvas.drawPath(path, paint)

            // Dolní část (pod tunelem)
            path.reset()
            path.moveTo(x1, y1Bot)
            path.lineTo(x1 + STEP, y2Bot)
            path.lineTo(x1 + STEP, height.toFloat())
            path.lineTo(x1, height.toFloat())
            path.close()
            canvas.drawPath(path, paint)
        }

        // ▪ Raketka a plamínek
        if (gameState == GameState.RUNNING ||
            gameState == GameState.PAUSE_MODE ||
            (gameState == GameState.EXPLODING && System.currentTimeMillis() - explosionStartTime < 1500)
        ) {
            drawKutloch(canvas)
        }


        // ▪ Fáze výbuchu: pixely
        explosionParticles.forEach {
            if (it.x in 0f..width.toFloat() && it.y in 0f..height.toFloat()) {
                paint.color = it.color
                canvas.drawRect(it.x, it.y, it.x + 2f, it.y + 2f, paint)
            }
        }

        drawBalls(canvas)

        // ▪ Skóre a životy
        canvas.drawText("Score: $score", 50f, 110f, scorePaint)
        drawLives(canvas)
        // ▪ Plops
        canvas.drawText("Koule: $plop", 50f, 95f, plopPaint)
        drawLives(canvas)

        // ▪ Pauza – výbuch dokončen
        if (gameState == GameState.PAUSED) {
            paint.color = Color.WHITE
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("ZNIČENÍ!", width / 2f, height / 2f, paint)
        }
        if (gameState == GameState.GAME_OVER) {
            paint.color = Color.RED
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("GAME OVER", width / 2f, height / 2f, paint)
        }

    }

    private fun drawKutloch(canvas: Canvas) {
        // ▪ Raketka
        paint.color = Color.CYAN
        for (row in kutlochBitmap.indices) {
            val bits = kutlochBitmap[row]
            for (col in 0 until 16) {
                if ((bits shr (15 - col)) and 1 == 1) {
                    val px = kutlochX + col * kutlochSizePx
                    val py = kutlochY + row * kutlochSizePx
                    canvas.drawRect(px, py, px + kutlochSizePx, py + kutlochSizePx, paint)
                }
            }
        }
// ▪ Modrý kruh kolem Kutlocha s jemným pulzováním (alpha efekt)
        val centerX = kutlochX + 8 * kutlochSizePx
        val centerY = kutlochY + 8 * kutlochSizePx

// Sinusová oscilace alpha hodnoty v intervalu 50–255, perioda 2 sekundy
        val timeMs = System.currentTimeMillis() % 2000L
        val phaseAlpha = (2 * Math.PI * timeMs / 2000).toFloat()
        val alpha = (Math.sin(phaseAlpha.toDouble()) * 0.5 + 0.5).toFloat() // 0.0–1.0
        val alphaInt = (alpha * 205 + 50).toInt().coerceIn(0, 255) // 50–255

        paint.color = Color.argb(alphaInt, 0, 0, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawCircle(centerX.toFloat(), centerY.toFloat(), 50f, paint)

        // ▪ Mihotající se plamínek za raketou
        val flamePath = Path()
        val flameBaseX = kutlochX - 16 * kutlochSizePx
        val flameYCenter = kutlochY + 8 * kutlochSizePx
        val flameLength = 20f
        val flameWidth = 5f

        flamePath.moveTo(flameBaseX + flameLength, flameYCenter)
        flamePath.lineTo(flameBaseX, flameYCenter - flameWidth / 2f)
        flamePath.lineTo(flameBaseX, flameYCenter + flameWidth / 2f)
        flamePath.close()

        // ▪ Čtyřbarevný sinusový cyklus: red, orange, yellow, blue
        val time = System.currentTimeMillis() % 4000L
        val phase = (2 * Math.PI * time / 4000).toFloat()

        val redWeight = ((Math.sin(phase.toDouble()) * 0.5 + 0.5)).toFloat()
        val orangeWeight = ((Math.sin(phase + Math.PI / 2) * 0.5 + 0.5)).toFloat()
        val yellowWeight = ((Math.sin(phase + Math.PI) * 0.5 + 0.5)).toFloat()
        val blueWeight = ((Math.sin(phase + 3 * Math.PI / 2) * 0.5 + 0.5)).toFloat()

        val sum = redWeight + orangeWeight + yellowWeight + blueWeight
        val rw = redWeight / sum
        val ow = orangeWeight / sum
        val yw = yellowWeight / sum
        val bw = blueWeight / sum

        val red = Color.RED
        val orange = Color.rgb(255, 165, 0)
        val yellow = Color.YELLOW
        val blue = Color.rgb(0, 0, 255)

        val r = (Color.red(red) * rw + Color.red(orange) * ow + Color.red(yellow) * yw + Color.red(blue) * bw).toInt()
        val g = (Color.green(red) * rw + Color.green(orange) * ow + Color.green(yellow) * yw + Color.green(blue) * bw).toInt()
        val b = (Color.blue(red) * rw + Color.blue(orange) * ow + Color.blue(yellow) * yw + Color.blue(blue) * bw).toInt()

        paint.color = Color.rgb(r, g, b)
        paint.style = Paint.Style.FILL
        canvas.drawPath(flamePath, paint)
    }


    private fun drawLives(canvas: Canvas) {
        for (i in 0 until lives) {
            val cx = lifeMargin + i * (lifeRadius * 2 + 10)
            val cy = 60f
            paint.style = Paint.Style.FILL
            paint.color = Color.RED
            canvas.drawCircle(cx + 50, cy, lifeRadius, paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.strokeWidth = 2f
            canvas.drawCircle(cx + 50, cy, lifeRadius, paint)
        }
    }
    private fun checkCollision(): Boolean {
        val rocketCenterY = kutlochY + 8 * kutlochSizePx
        val rocketXIndex = (kutlochX / STEP).toInt()
        if (rocketXIndex in tunnel.indices) {
            val tunnelCenterY = tunnel[rocketXIndex]
            val top = tunnelCenterY - TUNNEL_WIDTH / 2
            val bottom = tunnelCenterY + TUNNEL_WIDTH / 2
            return rocketCenterY < top || rocketCenterY > bottom
        }
        return false
    }
    fun detectRovinka(newY: Int) {
        if (frameCount < ROZBEH) return

        if (newY == lastNewY) {
            rovinka++
        } else {
            rovinka = 1
        }
        lastNewY = newY

        if (rovinka >= PLOCHAPODKOULI) {
            rovinka = 0
            placeBall(newY)
        }
    }

    fun getRandomTunnelColor(): Int {
        return tunnelColors.random()
    }

    fun placeBall(y: Int) {
        val color = ballColors.random()
        balls.add(Ball(width.toFloat() - 5, y + 60f, color))
    }

    fun updateBalls() {
        val step = 2f
        val toRemove = mutableListOf<Ball>()
        for (ball in balls) {
            ball.x -= step
            if (ball.x < -10) toRemove.add(ball)
        }
        balls.removeAll(toRemove)
    }

    fun drawBalls(canvas: Canvas) {
        for (ball in balls) {
            paint.color = ball.color
            canvas.drawCircle(ball.x, ball.y, 7f, paint)
        }
    }

    fun collectBalls() {
        val radius = 50f
        val toRemove = mutableListOf<Ball>()
        for (ball in balls) {
            if ((kutlochX - radius < ball.x && ball.x < kutlochX + radius) &&
                (kutlochY - radius < ball.y && ball.y < kutlochY + radius)) {
                toRemove.add(ball)
                plop++
                playCollectSound()
            }
        }
        balls.removeAll(toRemove)

        if (plop % 10 == 0 && plop != 0 && plop != lastPlop) {
            tunnelColor = getRandomTunnelColor()
            substanceColor = tunnelColor
            lastPlop = plop
        }
    }

    private fun playCollectSound() {
        // TODO: Přidej zvuk, až budeš mít MediaPlayer
    }

    fun togglePause() {
        gameState = when (gameState) {
            GameState.RUNNING -> GameState.PAUSE_MODE
            GameState.PAUSE_MODE -> GameState.RUNNING
            else -> gameState // v ostatních případech pauza neřešíme
        }
    }
//  fun isPaused(): Boolean = gameState == GameState.PAUSE_MODE
    fun isPaused(): Boolean {
        return gameState == GameState.PAUSE_MODE
    }


    @Synchronized
    private fun threadSafeResetGame() {
        if (gameState == GameState.PAUSED) {
            Log.i("TunnelView", "Provádím bezpečný reset hry.")
            gameState = GameState.RUNNING
            joyDx = 0f
            joyDy = 0f
            isTouching = false
            kutlochX = width /4f
            kutlochY = height / 2f
            plop = 0
            explosionParticles.clear()
            initTunnel()
        }
    }

    @Synchronized
    private fun threadSafeRestartFullGame() {
        Log.i("TunnelView", "GAME OVER – restartuji celou hru.")
        gameState = GameState.RUNNING
        joyDx = 0f
        joyDy = 0f
        isTouching = false
        kutlochX = width / 4f
        kutlochY = height / 2f
        score = 0
        plop = 0
        lives = maxLives
        explosionParticles.clear()
        initTunnel()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (gameState != GameState.RUNNING) return true

        val tpRadius = 150f
        val tpCenterX = 100f
        val tpCenterY = height - 100f

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = e.x - tpCenterX
                val dy = e.y - tpCenterY
                val distance = sqrt(dx * dx + dy * dy)

                if (distance < tpRadius) {
                    val strength = ((1f - distance / tpRadius).coerceIn(0f, 1f))
                    joyDx = (dx / tpRadius) * strength
                    joyDy = (dy / tpRadius) * strength
                    isTouching = true
                } else {
                    // mimo trackpoint → nereagujeme
                    isTouching = false
                    joyDx = 0f
                    joyDy = 0f
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                joyDx = 0f
                joyDy = 0f
            }
        }

        return true
    }


    private class GameThread(
        private val holder: SurfaceHolder,
        private val view: TunnelView
    ) : Thread() {
        @Volatile var running = false
        private var fpsStart = System.currentTimeMillis()
        private var frames = 0

        override fun run() {
            while (running) {
                val t0 = System.currentTimeMillis()
                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    synchronized(holder) {
                        view.update()
                        view.draw(canvas)
                    }
                    holder.unlockCanvasAndPost(canvas)
                }
                frames++
                val now = System.currentTimeMillis()
                if (now - fpsStart >= 1000) {
                    Log.d("TunnelView", "FPS: ${frames * 1000 / (now - fpsStart)}, Tunnel size: ${view.tunnel.size}")
                    fpsStart = now; frames = 0
                }
                val ft = System.currentTimeMillis() - t0
                val st = FRAME_DELAY - ft
                if (st > 0) sleep(st)
            }
        }
    }
}
