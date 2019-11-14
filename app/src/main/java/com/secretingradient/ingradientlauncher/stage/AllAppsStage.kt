package com.secretingradient.ingradientlauncher.stage

import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import com.secretingradient.ingradientlauncher.*
import com.secretingradient.ingradientlauncher.data.AppInfo
import com.secretingradient.ingradientlauncher.element.AppView
import com.secretingradient.ingradientlauncher.sensor.BaseSensor
import kotlinx.android.synthetic.main.stage_1_user.view.*
import kotlin.math.ceil

class AllAppsStage(launcherRootLayout: LauncherRootLayout) : BasePagerSnapStage(launcherRootLayout) {
    override val stageLayoutId = R.layout.stage_2_all_apps
    override val viewPagerId = R.id.all_apps_pager

    override var rowCount = getPrefs(context).getInt(Preferences.ALLAPPS_STAGE_ROW_COUNT, -1)
    override var columnCount = getPrefs(context).getInt(Preferences.ALLAPPS_STAGE_COLUMN_COUNT, -1)
    override var pageCount = ceil(dataKeeper.allAppsIds.size.toFloat() / (rowCount * columnCount)).toInt()

    override val pagerAdapter = PagerSnapAdapter()
    val dataset: List<AppInfo> = dataKeeper.allAppsDataset
    val currentSnapLayout: SnapLayout
        get() = stageRV.getChildAt(0) as SnapLayout
    var shouldIntercept
        set(value) {
            stageRootLayout.shouldIntercept = value
        }
        get() = stageRootLayout.shouldIntercept
    private var touchHandler = TouchHandler()
    private var sensors = mutableListOf<BaseSensor>()
    val currentPage: Int
        get() = stageVP.currentItem

    lateinit var search: View
    lateinit var sort: View

    override fun initInflate(stageRootLayout: StageRootLayout) {
        super.initInflate(stageRootLayout)
        pageSize = columnCount*rowCount
        stageRootLayout.setOnTouchListener(touchHandler)
        stageRootLayout.preDispatchListener = object : OnPreDispatchListener {
            override fun onPreDispatch(event: MotionEvent) {
                touchHandler.preDispatch(event)
            }
        }
        stageRootLayout.apply {
            sensors.add(up_sensor.apply { sensorListener = touchHandler.upSensorListener})
            sensors.add(info_sensor)
            sensors.add(uninstall_sensor)
        }
        touchHandler.hideSensors()
        search = stageRootLayout.findViewById<View>(R.id.search)
        sort = stageRootLayout.findViewById<View>(R.id.sort_view)
    }

    override fun bindPage(holder: SnapLayoutHolder, page: Int) {
        dataset.forEachIndexed {i, appInfo ->
            if (isPosInPage(i, page)) {
                val i2 = i % pageSize
                val pos = (i2 % columnCount)*2 + i2/columnCount*columnCount*4
                holder.snapLayout.addView(appInfo.createView(context) // avoid creating here
                    .apply { setSnapLayoutParams(pos, 2, 2)}) // bad way
            }
        }
    }

    private inner class TouchHandler : View.OnTouchListener {
        var selectedView: View? = null
        var lastHoveredView: View? = null
        val touchPoint = Point()
        val reusablePoint = Point()
        val gestureHelper = GestureHelper(context)
        var disallowHScroll = false
        var lastLayoutPosition = -1

        val upSensorListener = object : BaseSensor.SensorListener {
            override fun onSensor(v: View) {
                if (v is AppView)
                    transferEvent(v)
            }
            override fun onExitSensor() {}
            override fun onPerformAction(v: View) {}
        }

        init {
            gestureHelper.doOnLongClick = { downEvent ->
                if (selectedView != null)
                    startDrag(selectedView!!, touchPoint)
            }
        }

        fun preDispatch(event: MotionEvent) {
            gestureHelper.onTouchEvent(event)

            if (event.action == MotionEvent.ACTION_DOWN) {
                touchPoint.set(event.x.toInt(), event.y.toInt())

                selectedView = trySelect(findInnerViewUnder(touchPoint))
                lastHoveredView = selectedView
            }

            if (!shouldIntercept && (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL)) {
                endDrag()
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            // only called by stageRootLayout in EditMode, intercepting TouchEvent
            if (v !is StageRootLayout)
                return false

            touchPoint.set(event.x.toInt(), event.y.toInt())

            when (event.action) {

                MotionEvent.ACTION_MOVE -> {
                    if (selectedView != null) {
                        if (stageRootLayout.overlayView == null) // (when not in drag)
                            startDrag(selectedView!!, touchPoint)
                        val hoveredView = findHoveredViewAt(touchPoint)
                        if (isNewHoveredView(hoveredView)) {
                            onExitHover(lastHoveredView)
                            onHover(selectedView!!, hoveredView)
                        }
                        lastHoveredView = hoveredView
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (selectedView != null) {
                        val hoveredView = findHoveredViewAt(touchPoint)
                        if (hoveredView != null) {
                            onPerformAction(selectedView!!, hoveredView)
                            onExitHover(hoveredView)
                        }
                        lastHoveredView = hoveredView
                    }
                    endDrag()
                }
            }

            return true
        }

        fun findHoveredViewAt(touchPoint: Point): View? {
            return findViewUnderInList(sensors, touchPoint, lastHoveredView)
        }

        fun isNewHoveredView(hoveredView: View?): Boolean {
            return hoveredView != lastHoveredView || lastHoveredView == null
        }

        fun startDrag(selectedView: View, touchPoint: Point) {
            println("-- startDrag --")
            stageRootLayout.overlayView = selectedView
            stageRootLayout.setOverlayTranslation(touchPoint.x.toFloat(), touchPoint.y.toFloat())
            disallowHScroll()
            disallowVScroll()
            shouldIntercept = true
            lastLayoutPosition = -1
            lastHoveredView = null
            showSensors(255/2)
            hideUI()
        }

        fun endDrag() {
            println("endDrag")
            stageRootLayout.overlayView = null
            selectedView = null
            disallowHScroll(false)
            shouldIntercept = false
            hideSensors()
            showUI()
        }


        fun onExitHover(view: View?) {
            when (view) {
                is BaseSensor -> view.onExitSensor()
            }
        }

        fun onHover(selectedView: View, hoveredView: View?) {
            when {
                selectedView is AppView && hoveredView is BaseSensor -> hoveredView.onSensor(selectedView)
            }
        }

        fun onPerformAction(selectedView: View, hoveredView: View) {
            when {
                selectedView is AppView && hoveredView is BaseSensor -> hoveredView.onPerformAction(selectedView)
            }
        }


        fun trySelect(v: View?): View? {
            if (v is AppView) {
                onAppSelected(v)
                return v
            }
            return null
        }

        fun onAppSelected(v: AppView) {/*todo*/}

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

        fun showUI() {
            search.visibility = View.VISIBLE
            sort.visibility = View.VISIBLE
        }

        fun hideUI() {
            search.visibility = View.INVISIBLE
            sort.visibility = View.INVISIBLE
        }

        fun transferEvent(appView: AppView) {
            endDrag()
            launcherRootLayout.transferEvent(1, appView)
        }

        fun getElementPage(element: View): Int {
            val fromSnapLayout = element.parent as? SnapLayout ?: throw LauncherException("element $element not a child of SnapLayout. parent= ${element.parent}")
            return (stageRV.getChildViewHolder(fromSnapLayout) as SnapLayoutHolder).page
        }

        fun getPagedPositionOfView(v: View): Int {
            val layoutPosition = (v.layoutParams as SnapLayout.SnapLayoutParams).position
            return layoutPosition + getElementPage(v) * pageSize
        }

        fun getPositionOnSnapUnder(snapLayout: SnapLayout, touchPoint: Point): Int {
            toLocationInView(touchPoint, snapLayout, reusablePoint)
            return snapLayout.snapToGrid(reusablePoint, 2)
        }

        fun toPagedPosition(layoutPosition: Int, page: Int = currentPage): Int {
            return layoutPosition + page * pageSize
        }

        fun dispatchWithTransform(v: View, event: MotionEvent) {
            val transformedEvent = getTransformedEvent(event, v)
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
    }
}