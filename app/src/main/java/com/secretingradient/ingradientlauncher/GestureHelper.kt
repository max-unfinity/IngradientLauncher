package com.secretingradient.ingradientlauncher

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent

enum class Gesture {
    TAP_UP,
    EXACTLY_DOUBLE_TAP_DOWN, // ?
    FLING_UP,
    SCROLL_X_MOVE,
    SCROLL_Y_MOVE,
    WANDERING_MOVE,
}

class GestureHelper(context: Context) {
    val launcher = (context as LauncherActivity).launcher

    private val gestureListener = DetectorListener()

    private val gestureDetector = GestureDetector(context, gestureListener)

    var gesture: Gesture? = null

//    var doOnLongClick: (downEvent: MotionEvent?) -> Unit = {}

//    var onHelperGesture: GestureHelperListener? = null
    val currentGestureListener
        get() = launcher.currentStage as? GestureHelperListener

    var slopOvercame = false
        private set

    private var isDownEventCaught = false
    
    private var wasLongPress = false

    private var scrollDirection: Gesture? = null

    fun onTouchEvent(event: MotionEvent) {
        val action = event.action
        if (wasLongPress && action != MotionEvent.ACTION_DOWN) {
            return
        }
        if (action == MotionEvent.ACTION_MOVE && !slopOvercame) {
            gesture = Gesture.WANDERING_MOVE
        }
        gestureDetector.onTouchEvent(event)
        if (action != MotionEvent.ACTION_DOWN && !isDownEventCaught) {
            throw LauncherException("can't recognize gesture, because onDown event was not caught")
        }
    }

    private fun reset() {
        gesture = null
        scrollDirection = null
        isDownEventCaught = false
        slopOvercame = false
        wasLongPress = false
    }

    interface GestureHelperListener {
        fun onLongClick(downEvent: MotionEvent)
    }

    private inner class DetectorListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean {
            reset()
            isDownEventCaught = true
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scrollDirection == null) {
                slopOvercame = true
                scrollDirection = if (dx > dy) Gesture.SCROLL_X_MOVE else Gesture.SCROLL_Y_MOVE
            }
            gesture = scrollDirection
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
            gesture = Gesture.FLING_UP
            return true
        }

        override fun onSingleTapUp(e: MotionEvent?): Boolean {
            gesture = Gesture.TAP_UP
            return true
        }

        override fun onDoubleTap(e: MotionEvent?): Boolean {
            gesture = Gesture.EXACTLY_DOUBLE_TAP_DOWN
            return true
        }

        override fun onLongPress(downEvent: MotionEvent?) {
            // this prevents case when gesture == TAP_UP after long press
            if (downEvent == null) throw LauncherException("downEvent is null")
            if (!slopOvercame) {
                gesture = null
                wasLongPress = true
                currentGestureListener?.onLongClick(downEvent)
            }
        }
    }
}