package com.secretingradient.ingradientlauncher.stage

import android.graphics.Color
import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.forEach
import com.secretingradient.ingradientlauncher.*
import com.secretingradient.ingradientlauncher.data.Data
import com.secretingradient.ingradientlauncher.data.Dataset
import com.secretingradient.ingradientlauncher.data.Info
import com.secretingradient.ingradientlauncher.element.AppView
import com.secretingradient.ingradientlauncher.element.FolderView
import com.secretingradient.ingradientlauncher.element.WidgetView
import com.secretingradient.ingradientlauncher.sensor.BaseSensor
import kotlinx.android.synthetic.main.stage_1_user.view.*

class UserStage(launcherRootLayout: LauncherRootLayout) : BasePagerSnapStage(launcherRootLayout) {
    private val FLIP_WIDTH = toPx(25).toInt()
    private val defaultAppSize = toPx(70).toInt()

    override val stageLayoutId = R.layout.stage_1_user
    override val viewPagerId = R.id.user_stage_pager

    override var columnCount = getPrefs(context).getInt(Preferences.USER_STAGE_COLUMN_COUNT, -1)
    override var rowCount = getPrefs(context).getInt(Preferences.USER_STAGE_ROW_COUNT, -1)
    override var pageCount = getPrefs(context).getInt(Preferences.USER_STAGE_PAGE_COUNT, -1)

    override val pagerAdapter = PagerSnapAdapter()
    override val dataset: Dataset<Data, Info> = dataKeeper.userStageDataset
    val currentSnapLayout: SnapLayout
        get() = stageRV.getChildAt(0) as SnapLayout
    var shouldIntercept
        set(value) {stageRootLayout.shouldIntercept = value}
        get() = stageRootLayout.shouldIntercept
    private lateinit var touchHandler: TouchHandler
    private var sensors = mutableListOf<BaseSensor>()
    val currentPage: Int
        get() = stageVP.currentItem

    lateinit var folderWindow: FolderWindow
    var isFolderOpen = false
//    private val folderTranslation = Point()

    override fun initInflate(stageRootLayout: StageRootLayout) {
        super.initInflate(stageRootLayout)
        touchHandler = TouchHandler()
        stageRootLayout.setOnTouchListener(touchHandler)
        stageRootLayout.preDispatchListener = object : OnPreDispatchListener {
            override fun onPreDispatch(event: MotionEvent) {
                touchHandler.preDispatch(event)
            }
        }

        stageRootLayout.apply {
            sensors.add(up_sensor.apply { sensorListener = touchHandler.upSensorListener})
            sensors.add(info_sensor)
            sensors.add(remove_sensor.apply { sensorListener = touchHandler.removeSensorListener})
            sensors.add(uninstall_sensor)
        }
        touchHandler.hideSensors()

        folderWindow = stageRootLayout.findViewById(R.id.folder_window)
        folderWindow.initData(dataset, defaultAppSize)

        stageRootLayout.clipChildren = false
//        stageVP.offscreenPageLimit = 2
    }

    fun setFolderAnchorView(v: View) {
//        getLocationOfViewGlobal(v, folderTranslation)
    }

    private inner class TouchHandler : View.OnTouchListener {
        var inEditMode = false
        var selectedView: View? = null
        var lastHoveredView: View? = null
        val touchPoint = Point()
        val reusablePoint = Point()
        val ghostView = ImageView(context).apply { setBackgroundColor(Color.LTGRAY); layoutParams = SnapLayout.SnapLayoutParams(0, 2, 2) }
        val gestureHelper = GestureHelper(context)
        var disallowHScroll = false
        var layoutPosition = -1
        var lastLayoutPosition = -1
        var previewFolder: FolderView? = null
        var flipPageDone = false
        var wasMoveAfterStartEditMode = false  // crutch
        var isTouchInFolder = false
        var needToStartDragInFolder = false

        val upSensorListener = object : BaseSensor.SensorListener {
            override fun onSensor(v: View) {
                if (v is AppView)
                    transferEvent(v)
            }
            override fun onExitSensor() {}
            override fun onPerformAction(v: View) {}
        }
        val removeSensorListener = object : BaseSensor.SensorListener {
            override fun onSensor(v: View) {}
            override fun onExitSensor() {}
            override fun onPerformAction(v: View) {
                removeView(v)
            }
        }

        init {
            gestureHelper.doOnLongClick = { downEvent ->
                startEditMode()
                wasMoveAfterStartEditMode = false
                if (isFolderOpen && downEvent != null) {
                    needToStartDragInFolder = true
                }
                if (!isFolderOpen && selectedView != null) {
                    startDrag(selectedView!!, touchPoint)
                }
            }
        }

        fun preDispatch(event: MotionEvent) {
            gestureHelper.onTouchEvent(event)

            if (event.action == MotionEvent.ACTION_DOWN) {
                needToStartDragInFolder = false
                touchPoint.set(event.x.toInt(), event.y.toInt())
                if (isTouchOutsideFolder(touchPoint))
                    closeFolder()
                else
                    disallowHScroll()
                selectedView = trySelect(findInnerViewUnder(touchPoint))
                lastHoveredView = selectedView
            }

            if (gestureHelper.gesture == Gesture.TAP_UP && selectedView is FolderView) {
                openFolder(selectedView as FolderView)
            }

            if (!wasMoveAfterStartEditMode && event.action == MotionEvent.ACTION_UP)
                endDrag()

            if (inEditMode && !disallowHScroll && selectedView == null)
                stageRV.onTouchEvent(event)
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            // only called by stageRootLayout in EditMode, intercepting TouchEvent
            if (v !is StageRootLayout && !inEditMode)
                return false

            touchPoint.set(event.x.toInt(), event.y.toInt())

            if (!isTouchOutsideFolder(touchPoint)) {
                selectedView = null
                isTouchInFolder = true
                if (needToStartDragInFolder) {
                    needToStartDragInFolder = false
                    startDragInFolder(event)
                }
                else
                    dispatchWithTransform(folderWindow, event)
            } else if (isTouchInFolder) {
                isTouchInFolder = false
                receiveFromFolder(folderWindow, event)
            }

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    disallowVScroll()
                }

                MotionEvent.ACTION_MOVE -> {
                    wasMoveAfterStartEditMode = true
                    if (selectedView != null) {
                        if (stageRootLayout.overlayView == null) // (when not in drag)
                            startDrag(selectedView!!, touchPoint)
                        val hoveredView = findHoveredViewAt(touchPoint, lastHoveredView)
                        if (hoveredView is SnapLayout) {
                            layoutPosition = getPositionOnSnapUnder(hoveredView, touchPoint)
                        }
                        if (isNewHoveredView(hoveredView)) {
                            onExitHover(lastHoveredView)
                            onHover(selectedView!!, hoveredView, layoutPosition)
                        }
                        // if it is folder creation, then app was replaced to folder and we set lastHoveredView to folderView
                        lastHoveredView = if (selectedView is AppView && hoveredView is AppView) previewFolder else hoveredView
                        lastLayoutPosition = layoutPosition
                        flipPageIfNeeded(touchPoint)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (selectedView != null) {
                        val hoveredView = findHoveredViewAt(touchPoint, lastHoveredView)
                        if (hoveredView is SnapLayout) {
                            layoutPosition = getPositionOnSnapUnder(hoveredView, touchPoint)
                        }
                        if (hoveredView != null) {
                            onPerformAction(selectedView!!, hoveredView, layoutPosition)
                            onExitHover(hoveredView)
                        }
                        lastHoveredView = hoveredView
                        lastLayoutPosition = layoutPosition
                    }
                    if (gestureHelper.gesture == Gesture.TAP_UP && !isFolderOpen) {// not after longPress
                        endEditMode()
                    }
                    endDrag()
                }

            }

            return true
        }

        fun findHoveredViewAt(touchPoint: Point, lastHoveredView: View?): View? {
            // hovered view can't be neither selectedView nor ghostView
//            if (!isAllChildrenLaidOut(currentSnapLayout))
//                return null
            val v = findInnerViewUnder(touchPoint, lastHoveredView)
            return if (v != selectedView && v != ghostView) v else currentSnapLayout
        }

        fun isNewHoveredView(hoveredView: View?): Boolean {
            return layoutPosition != lastLayoutPosition || hoveredView != lastHoveredView || lastHoveredView == null
        }

        fun startDrag(selectedView: View, touchPoint: Point) {
            println("-- startDrag --")
            stageRootLayout.overlayView = selectedView
            stageRootLayout.setOverlayTranslation(touchPoint.x.toFloat(), touchPoint.y.toFloat())
            selectedView.visibility = View.INVISIBLE
            disallowHScroll()
            disallowVScroll()
            lastLayoutPosition = -1
            lastHoveredView = null
            flipPageDone = false
        }

        fun endDrag() {
            println("endDrag")
            selectedView?.visibility = View.VISIBLE
            stageRootLayout.overlayView = null
            selectedView = null
            disallowHScroll(false)
            cancelPreviewFolder()
            flipPageDone = false
        }

        fun startEditMode() {
            inEditMode = true
            shouldIntercept = true
            folderWindow.inEditMode = true
            disallowVScroll()
            disallowHScroll()
            showSensors(255/2)
        }

        fun endEditMode() {
            inEditMode = false
            folderWindow.inEditMode = false
            shouldIntercept = false
            hideSensors()
        }

        fun onExitHover(view: View?) {
            when (view) {
                is BaseSensor -> view.onExitSensor()
                is FolderView -> cancelPreviewFolder()
                is SnapLayout -> view.removeView(ghostView)
            }
        }

        fun onHover(selectedView: View, hoveredView: View?, movePosition: Int) {
            when {
                selectedView is AppView && hoveredView is AppView -> createPreviewFolder(hoveredView)
                isElement(selectedView) && hoveredView is BaseSensor -> hoveredView.onSensor(selectedView)
                isElement(selectedView) && hoveredView is SnapLayout -> moveGhostView(hoveredView, movePosition)
//                hoveredView == null -> { cancelPreviewFolder() }
            }
        }

        fun onPerformAction(selectedView: View, hoveredView: View, movePosition: Int) {
            when {
                selectedView is AppView && hoveredView is FolderView -> { addToFolder(hoveredView, selectedView); removeView(selectedView) }
                isElement(selectedView) && hoveredView is BaseSensor -> hoveredView.onPerformAction(selectedView)
                isElement(selectedView) && hoveredView is SnapLayout -> moveOrAddView(selectedView, hoveredView, movePosition)
            }
        }

        fun moveGhostView(snapLayout: SnapLayout, layoutPosition: Int) {
            val parent = ghostView.parent as ViewGroup?
            if (parent != snapLayout) {
                parent?.removeView(ghostView)
                snapLayout.addView(ghostView)
            }
            snapLayout.moveView(ghostView, layoutPosition)
        }

        fun moveOrAddView(element: View, toSnapLayout: SnapLayout, toLayoutPosition: Int) {
            if (element == ghostView) throw LauncherException("for ghostView use moveGhostView")
            val parent = element.parent
            val toPage = (stageRV.getChildViewHolder(toSnapLayout) as SnapLayoutHolder).page
            val to = toPagedPosition(toLayoutPosition, toPage)

            if (parent is SnapLayout) {
                // todo b01-p (patched) {call of wrong function. It should be directed to addToFolder()}
                if (dataset[to] != null)
                    return

                val from = getPagedPositionOfView(element)
                (element.layoutParams as SnapLayout.SnapLayoutParams).position = toLayoutPosition
                parent.removeView(element)
                toSnapLayout.addView(element)
                dataset.move(from, to)
            } else if (element is AppView){
                if (parent is ViewGroup) {
                    parent.removeView(element)
                }
                // todo b01-p (patched) {call of wrong function. It should be directed to addToFolder()}
                if (dataset[to] != null)
                    return
                toSnapLayout.addNewView(element, toLayoutPosition, 2, 2)
                dataset.put(to, element.info!!)
            }
        }

        fun createPreviewFolder(appView: AppView) {
            cancelPreviewFolder()
            val folder = FolderView(context, appView.info!!)
            currentSnapLayout.removeView(appView)
            currentSnapLayout.addNewView(folder, (appView.layoutParams as SnapLayout.SnapLayoutParams).position, 2, 2)
            previewFolder = folder
        }

        fun addToFolder(folder: FolderView, appView: AppView) {
            val folderPos = getPagedPositionOfView(folder)
            folder.addApps(appView.info!!)
            previewFolder = null
            dataset.put(folderPos, folder.info, true)
        }

        fun removeView(v: View) {
            val parent = v.parent as? ViewGroup ?: return
            if (parent is SnapLayout) {
                val pos = getPagedPositionOfView(v)
                dataset.remove(pos)
            }
            parent.removeView(v)
        }

        fun cancelPreviewFolder() {
            previewFolder?.let {
                currentSnapLayout.removeView(it)
                val appView = AppView(context, it.apps[0])
                currentSnapLayout.addNewView(appView, (it.layoutParams as SnapLayout.SnapLayoutParams).position, 2, 2)
                it.clear()
                previewFolder = null
                lastHoveredView = appView
            }
        }

        fun openFolder(folder: FolderView) {
            isFolderOpen = true
            folderWindow.setContent(folder, getPagedPositionOfView(folder))
            folderWindow.visibility = View.VISIBLE
            setFolderAnchorView(folder)
        }

        fun closeFolder() {
            isFolderOpen = false
            folderWindow.visibility = View.GONE
        }

        fun isTouchOutsideFolder(touchPoint: Point): Boolean {
            if (!isFolderOpen)
                return true
            findChildrenUnder(touchPoint).forEach {
                if (it is FolderWindow)
                    return false
            }
            return true
        }

        fun trySelect(v: View?): View? {
            if (!isElement(v))
                return null
            when (v) {
                is AppView -> onAppSelected(v)
                is FolderView -> onFolderSelected(v)
                is WidgetView -> onWidgetSelected(v)
            }
            return v
        }

        fun onAppSelected(v: View) {/*todo*/}
        fun onFolderSelected(v: View) {/*todo*/}
        fun onWidgetSelected(v: View) {/*todo*/}

        fun disallowHScroll(disallow: Boolean = true) {
            disallowHScroll = disallow
        }

        fun showSensors(opacity: Int) {
            sensors.forEach {
                it.visibility = View.VISIBLE
                it.drawable.alpha = opacity
            }
        }

        fun hideSensors() {
            sensors.forEach {
                it.visibility = View.INVISIBLE
            }
        }

        fun transferEvent(appView: AppView) {
            launcherRootLayout.transferEvent(0, appView)
            (selectedView!!.parent as? ViewGroup)?.removeView(selectedView)
            endDrag()
            endEditMode()
        }

        fun flipPageIfNeeded(touchPoint: Point) {
            var direction = 0
            if (touchPoint.x > stageRootLayout.width - FLIP_WIDTH)
                direction = 1
            else if (touchPoint.x < FLIP_WIDTH)
                direction = -1

            if (direction != 0 && !flipPageDone)
                flipPage(direction)
            else if (direction == 0)
                flipPageDone = false
        }

        fun flipPage(direction: Int) {
            stageVP.currentItem += direction
            cancelPreviewFolder()
            closeFolder()
            flipPageDone = true
        }

        fun getElementPage(element: View): Int {
            val fromSnapLayout = element.parent as? SnapLayout ?: throw LauncherException("element $element not a child of SnapLayout. parent= ${element.parent}")
            return (stageRV.getChildViewHolder(fromSnapLayout) as SnapLayoutHolder).page
        }

        fun getPagedPositionOfView(v: View): Int {
            val layoutPosition = (v.layoutParams as SnapLayout.SnapLayoutParams).position
            return layoutPosition + getElementPage(v) * pagerAdapter.pageSize
        }

        fun getPositionOnSnapUnder(snapLayout: SnapLayout, touchPoint: Point): Int {
            toLocationInView(touchPoint, snapLayout, reusablePoint)
            return snapLayout.snapToGrid(reusablePoint, 2)
        }

        fun toPagedPosition(layoutPosition: Int, page: Int = currentPage): Int {
            return layoutPosition + page * pagerAdapter.pageSize
        }

        fun dispatchWithTransform(v: View, event: MotionEvent) {
            val transformedEvent = getTransformedEvent(event, folderWindow)
            v.onTouchEvent(transformedEvent)
            transformedEvent.recycle()
        }

        fun getTransformedEvent(event: MotionEvent, v: View): MotionEvent {
            reusablePoint.set(event.x.toInt(), event.y.toInt())
            toLocationInView(reusablePoint, v, reusablePoint)
            val e = MotionEvent.obtain(event)
            e.setLocation(reusablePoint.x.toFloat(), reusablePoint.y.toFloat())
            return e
        }

        fun receiveFromFolder(folderWindow: FolderWindow, event: MotionEvent) {
            if (folderWindow.selectedApp != null) {
                selectedView = trySelect(folderWindow.selectedApp!!.info!!.createView(context))
                stageRootLayout.addView(selectedView, defaultAppSize, defaultAppSize)
                folderWindow.removeSelectedApp()
                closeFolder()
            }
        }

        fun isAllChildrenLaidOut(v: ViewGroup = stageRootLayout): Boolean {
            v.forEach {
                if (it.measuredWidth == 0)
                    return false
                if (it is ViewGroup && !isElement(it) && !isAllChildrenLaidOut(it))
                    return false
            }
            return true
        }

        fun startDragInFolder(event: MotionEvent) {
            val transformedEvent = getTransformedEvent(event, folderWindow)
            transformedEvent.action = MotionEvent.ACTION_DOWN
            folderWindow.startDrag(transformedEvent)
            transformedEvent.recycle()
        }
    }

}