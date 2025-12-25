package ee.carlrobert.codegpt.toolwindow.ui.mcp

import java.awt.AlphaComposite
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.Timer
import kotlin.math.pow

object McpAnimations {

    fun animateHeight(
        component: JComponent,
        targetHeight: Int,
        duration: Int = 200,
        onComplete: (() -> Unit)? = null
    ) {
        val startHeight = component.height
        animate(0f, 1f, duration) { progress ->
            val currentHeight = (startHeight + (targetHeight - startHeight) * progress).toInt()
            component.preferredSize = component.preferredSize.apply { height = currentHeight }
            component.revalidate()
            component.repaint()
            if (progress >= 1f) {
                onComplete?.invoke()
            }
        }
    }

    private fun animate(start: Float, end: Float, duration: Int, onUpdate: (Float) -> Unit) {
        val startTime = System.currentTimeMillis()
        val timer = Timer(16) { _ ->
            val elapsed = System.currentTimeMillis() - startTime
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            val value = start + (end - start) * easeInOutCubic(progress)
            onUpdate(value)
        }

        timer.addActionListener {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= duration) {
                timer.stop()
                onUpdate(end)
            }
        }

        timer.start()
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) {
            4 * t * t * t
        } else {
            1 - (-2.0 * t + 2.0).pow(3.0).toFloat() / 2
        }
    }
}

fun JComponent.paintWithAlpha(g: Graphics, paintAction: () -> Unit) {
    val alpha = getClientProperty("alpha") as? Float ?: 1f
    if (alpha < 1f) {
        val g2d = g.create() as Graphics2D
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
        paintAction()
        g2d.dispose()
    } else {
        paintAction()
    }
}