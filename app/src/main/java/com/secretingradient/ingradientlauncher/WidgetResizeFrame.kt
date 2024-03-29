package com.secretingradient.ingradientlauncher

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.children
import com.secretingradient.ingradientlauncher.element.WidgetView
import kotlin.math.abs
import kotlin.math.max

class WidgetResizeFrame(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {

    val reusableRect = Rect()
    private val INDEX_LEFT = 0
    private val INDEX_TOP = 1
    private val INDEX_RIGHT = 2
    private val INDEX_BOTTOM = 3
    var side = 0
    var minWidth = 100
    var minHeight = 100
    private var snapLayout: SnapLayout? = null
    private val snapX
        get() = snapLayout!!.snapStepX
    private val snapY
        get() = snapLayout!!.snapStepY
    private var resizableWidget: WidgetView? = null

    init {
        layoutParams = ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    fun attachToWidget(widget: WidgetView) {
        detachFromWidget()
        snapLayout = widget.parent as? SnapLayout ?: throw LauncherException("widget must be added to snapLayout")
        resizableWidget = widget
        widget.clipChildren = false
        (widget.parent as? ViewGroup)?.clipChildren = false
//        widget.clipToPadding = false
//        (widget.parent as? ViewGroup)?.clipToPadding = false
        widget.addView(this)
    }

    fun detachFromWidget() {
        resizableWidget?.clipChildren = true
        (resizableWidget?.parent as? ViewGroup)?.clipChildren = true
//        resizableWidget?.clipToPadding = true
//        (resizableWidget?.parent as? ViewGroup)?.clipToPadding = true
        (parent as? ViewGroup)?.removeView(this)
        snapLayout = null
        resizableWidget = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val widget = resizableWidget ?: return false
        val dx = event.x.toInt()
        val dy = event.y.toInt()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                side = 0
                for (it in children) {
                    if (it is ImageView) {
                        it.getHitRect(reusableRect)
                        reusableRect.inset(-20, -20)
                        if (reusableRect.contains(dx, dy))
                            break
                        side++
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (side) {
                    INDEX_LEFT -> {
                        if ((width - dx) >= minWidth) {
                            translationX += dx
                            layoutParams.width = width - dx
                        }
                    }
                    INDEX_TOP -> {
                        if ((height - dy) >= minHeight) {
                            translationY += dy
                            layoutParams.height = height - dy
                        }
                    }
                    INDEX_RIGHT -> {
                        layoutParams.width = max(minWidth, dx)
                    }
                    INDEX_BOTTOM -> {
                        layoutParams.height = max(minHeight, dy)
                    }
                }
                resizeWidgetIfNeeded(widget, side)
                requestLayout()
            }
            MotionEvent.ACTION_UP -> {
                fitToWidget()
            }

        }
        return true
    }

    fun resizeWidgetIfNeeded(widget: WidgetView, side: Int) {
        val lp = widget.layoutParams as SnapLayout.SnapLayoutParams
        val snapX = snapX
        val snapY = snapY
        val deltaL = translationX.toInt()
        val deltaR = layoutParams.width - lp.snapWidth * snapX
        val deltaT = translationY.toInt()
        val deltaB = layoutParams.height - lp.snapHeight * snapY

        var changed = true
        when {
            side == INDEX_LEFT && abs(deltaL) > snapX -> {
                lp.position += deltaL / snapX
                lp.snapWidth -= deltaL / snapX
            }
            side == INDEX_RIGHT && abs(deltaR) > snapX -> {
                lp.snapWidth += deltaR / snapX
            }
            side == INDEX_TOP && abs(deltaT) > snapY -> {
                lp.position += deltaT / snapY * snapLayout!!.snapCountX
                lp.snapHeight -= deltaT / snapY
            }
            side == INDEX_BOTTOM && abs(deltaB) > snapY -> {
                lp.snapHeight += deltaB / snapY
            }
            else -> changed = false
        }

        if (changed) {
            // currently only for portrait screen
            lp.computeSnapBounds(snapLayout!!)
            widget.widget.updateAppWidgetSize(null, lp.snapWidth * snapX, lp.snapHeight * snapY, lp.snapWidth * snapX, lp.snapHeight * snapY)
            widget.requestLayout()
            fitToWidget()
        }
    }

    fun fitToWidget() {
        translationX = 0f
        translationY = 0f
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        requestLayout()
    }
}