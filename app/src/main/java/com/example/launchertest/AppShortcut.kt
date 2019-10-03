package com.example.launchertest

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.core.view.iterator
import androidx.core.view.setPadding
import kotlin.math.min


class AppShortcut : TextView, MenuItem.OnMenuItemClickListener, View.OnClickListener {
companion object {
    const val DISMISS_RADIUS = 20
}

    var appInfo: AppInfo
        set(value) {
            field = value
            text = field.label
        }

    var icon: Drawable?
        get() = compoundDrawables[1] // get Clone
        set(value) = setCompoundDrawables(null, value, null, null)

    var desiredIconSize = 120
    var iconPaddingBottom = toPx(5).toInt()
    var padding = toPx(6).toInt()

    var menuHelper: MenuPopupHelper? = null
    var goingToRemove = false

    init {
        setPadding(padding)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        includeFontPadding = false
//        maxLines = 1
        setLines(1)
        setTextColor(Color.WHITE)
        setOnClickListener(this)
        ellipsize = TextUtils.TruncateAt.END
    }

    constructor(context: Context, appInfo: AppInfo) : super(context) {
        this.appInfo = appInfo
        this.icon = appInfo.icon?.constantState?.newDrawable(context.resources)
    }
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        this.appInfo = AppInfo("test", "test")
    }

    fun computeIconBounds(width: Int, height: Int): Rect {
        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom - (textSize).toInt() - iconPaddingBottom
        val maxSize = min(w, h)
        val iconSize = if (desiredIconSize != 0) min(desiredIconSize, maxSize) else maxSize
        val x = 0
        val y = h - iconSize
        return Rect(x, y, x+iconSize, y+iconSize)
    }

    fun applyIconBounds(iconBounds: Rect) {
        icon?.bounds = iconBounds
        setCompoundDrawables(null, icon, null, null)
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        applyIconBounds(computeIconBounds(w, h))
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onClick(v: View?) {
        context.startActivity(context.packageManager.getLaunchIntentForPackage(appInfo.packageName))
    }

    fun createDragShadow(): DragShadowBuilder {
        return object : DragShadowBuilder(this) {
            val paint = Paint().apply { colorFilter = PorterDuffColorFilter(Color.rgb(181, 232, 255), PorterDuff.Mode.MULTIPLY) }
            override fun onDrawShadow(canvas: Canvas?) {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val c = Canvas(bitmap)
                view.draw(c)
                canvas?.drawBitmap(bitmap, 0f,0f, paint)
            }
        }
    }

    fun showPopupMenu() {
        val builder = MenuBuilder(this.context)
        val inflater = MenuInflater(this.context)
        inflater.inflate(R.menu.shortcut_popup_menu, builder)
        for (item in builder.iterator()) {
            item.setOnMenuItemClickListener(this)
        }
        menuHelper = MenuPopupHelper(this.context, builder, this)
        menuHelper?.setForceShowIcon(true)
        menuHelper?.show()
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.popup_menu_info -> context.startActivity(intentToInfo())
            R.id.popup_menu_uninstall -> context.startActivity(intentToUninstall())
        }
        return true
    }

    private fun intentToInfo(): Intent {
        val uri = Uri.fromParts("package", appInfo.packageName, null)
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    private fun intentToUninstall(): Intent {
        val uri = Uri.fromParts("package", appInfo.packageName, null)
        val intent = Intent(Intent.ACTION_DELETE, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    private fun rasterize(drawable: Drawable, bitmap: Bitmap, canvas: Canvas, size: Int) {
        drawable.setBounds(0, 0, size, size)
        canvas.setBitmap(bitmap)
        drawable.draw(canvas)
    }

    override fun toString(): String {
        return "${this.hashCode().toString(16)} - ${appInfo.label}, icon_bounds: ${icon?.bounds}, parent: $parent"
    }
}