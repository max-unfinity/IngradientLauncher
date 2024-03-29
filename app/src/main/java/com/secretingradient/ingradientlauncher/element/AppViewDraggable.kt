package com.secretingradient.ingradientlauncher.element

import android.content.Context
import com.secretingradient.ingradientlauncher.SnapLayout
import com.secretingradient.ingradientlauncher.className
import com.secretingradient.ingradientlauncher.data.AppInfo
import com.secretingradient.ingradientlauncher.drag.DragTouchEvent
import com.secretingradient.ingradientlauncher.drag.DraggableElement
import com.secretingradient.ingradientlauncher.drag.DraggableElementImpl
import com.secretingradient.ingradientlauncher.drag.Hoverable
import java.lang.ref.WeakReference

class AppViewDraggable(context: Context, info: AppInfo? = null, delegate: DraggableElementImpl = DraggableElementImpl())
    : AppView(context, info), Hoverable, DraggableElement by delegate {

    init {
        delegate.ref = WeakReference(this)
    }

    override fun onHoverIn(event: DragTouchEvent) {
        println("onHoverIn ${this.className()} ${event.transformMatrixH}")
        val draggedView = event.draggableView
        if (draggedView is AppView) {
            val folder = transformToFolder(draggedView)
            event.setHoverableView(folder, event.transformMatrixH)
        }
    }

    override fun onHoverOut(event: DragTouchEvent) {
        println("onHoverOut ${this.className()} ${event.transformMatrixH}")
    }

    override fun onHoverMoved(event: DragTouchEvent) {
//        println("onHoverMoved ${this.className()} ${event.transformMatrix}")
    }

    override fun onHoverEnded(event: DragTouchEvent) {
        println("onHoverEnd ${this.className()} ${event.transformMatrixH}")
    }

    private fun transformToFolder(draggedAppView: AppView): FolderViewDraggable {
        val parentFrom = parent as SnapLayout
        val folder = FolderViewDraggable(context, this.info!!)
        parentFrom.removeView(this)
        parentFrom.addNewView(folder, (this.layoutParams as SnapLayout.SnapLayoutParams).position, 2, 2)
        return folder
    }
}