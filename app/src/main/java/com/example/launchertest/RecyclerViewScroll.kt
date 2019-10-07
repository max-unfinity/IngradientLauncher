package com.example.launchertest

import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

class RecyclerViewScroll : RecyclerView, Runnable {
    companion object {
        val SCROLL_ZONE = toPx(40).toInt()
        val SCROLL_DX = 10
    }

    var dragPos = -1
    var scrollDirection = 0
    var stopPoint: PointF? = null
    var apps: MutableList<String> = mutableListOf()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    fun startDragWith(cell: DummyCell) {
        dragPos = getPosition(cell)
    }

    fun checkAndScroll(dragPoint: PointF) {
        println("cas")
        when {
            dragPoint.x > width - SCROLL_ZONE -> {
                startDragScroll(+1, dragPoint)
            }
            dragPoint.x < SCROLL_ZONE -> {
                startDragScroll(-1, dragPoint)
            }
            else -> stopDragScroll()
        }
    }

    private fun getAppAtPosition(position: Int): AppView? {
        return (findViewHolderForAdapterPosition(position) as? ScrollStage.AppShortcutHolder)?.cell?.app
    }

    private fun getPosition(v: DummyCell): Int {
        val pos = getChildAdapterPosition(v)
        return pos
//        return if (pos != -1) pos else throw LauncherException("can't get position. adapter pos == $pos for $v")
    }

    fun move(from: Int, to: Int) {
        val t = apps.removeAt(from)
        apps.add(to, t)
        adapter?.notifyItemMoved(from, to)
    }

    fun remove(position: Int) {
        apps.removeAt(position)
        adapter?.notifyItemRemoved(position)
    }

    fun insert(appId: String, position: Int) {
        apps.add(position, appId)
        adapter?.notifyItemInserted(position)
    }

    fun moveOrInsertDragged(toCell: DummyCell, appInfo: AppInfo) {
        val to = getPosition(toCell)
        if (to == -1)
            return
        if (dragPos != -1) {
            val from = dragPos
            move(from, to)
        } else {
            insert(appInfo.id, to)
        }
        dragPos = to
    }

    fun removeDragged() {
        if (dragPos != -1) {
            remove(dragPos)
            dragPos = -1
        }
    }

    private val scrollHandler = Handler()
    override fun run() {
        println("scroll")
        this.scrollBy(SCROLL_DX*scrollDirection, 0)
/*        synchronized(this) {
            if (stopPoint != null) {
                val t = findChildViewUnder(stopPoint!!.x, stopPoint!!.y)
                if (t != null) {
                    moveOrInsertDragged(t as DummyCell)
                }
            }
        }*/
        this.scrollHandler.post(this)
    }

    fun startDragScroll(scrollDirection: Int, stopPoint: PointF? = null) {
        if (stopPoint == null) {
            println("start")
            stopDragScroll()
            this.scrollDirection = scrollDirection
            this.stopPoint = stopPoint
            scrollHandler.post(this)
        }
    }

    fun stopDragScroll() {
        if (stopPoint != null) {
            println("stop")
            stopPoint = null
            scrollHandler.removeCallbacks(this)
        }
    }
}