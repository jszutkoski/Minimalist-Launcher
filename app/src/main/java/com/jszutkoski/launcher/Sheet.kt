package com.jszutkoski.launcher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** A floating card at the bottom of the screen: a quiet title and a column of text actions. */
class Sheet(activity: Activity, title: String?) {
    val dialog = Dialog(activity)
    val column = LinearLayout(activity)
    private val context = activity

    /** Runs once the sheet closes, however it closes. */
    var onDismiss: (() -> Unit)? = null

    init {
        column.orientation = LinearLayout.VERTICAL
        column.background = GradientDrawable().apply {
            setColor(Palette.SURFACE)
            cornerRadius = activity.dp(28f)
        }
        column.setPadding(activity.dp(28), activity.dp(22), activity.dp(28), activity.dp(14))
        if (title != null) {
            column.addView(
                activity.text(title, 14f, Palette.SECONDARY, 400).apply {
                    letterSpacing = 0.02f
                    setPadding(0, 0, 0, dp(6))
                },
            )
        }

        val frame = FrameLayout(activity)
        val margin = activity.dp(12)
        frame.setPadding(margin, margin, margin, margin)
        frame.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(frame)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setDimAmount(0.6f)
            setWindowAnimations(android.R.style.Animation_InputMethod)
        }
        dialog.setOnDismissListener {
            open -= this
            onDismiss?.invoke()
        }
    }

    fun option(label: String, color: Int = Palette.TEXT, action: () -> Unit) {
        column.addView(
            context.text(label, 19f, color).apply {
                setPadding(0, dp(13), 0, dp(13))
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    /**
     * A tall scrolling list of [apps]. With [chosen], the apps it picks out are bright and the
     * rest dim, and the list redraws after each tap, for choosing several.
     */
    fun appList(apps: List<App>, chosen: ((App) -> Boolean)? = null, onPick: (App) -> Unit) {
        val adapter = object : ArrayAdapter<App>(context, 0, apps) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView as? TextView ?: context.text("", 19f).apply { setPadding(0, dp(12), 0, dp(12)) }
                val app = apps[position]
                row.text = app.label
                if (chosen != null) row.setTextColor(pressable(if (chosen(app)) Palette.TEXT else Palette.TERTIARY))
                return row
            }
        }
        val list = ListView(context).apply {
            divider = null
            selector = ColorDrawable(0)
            isVerticalScrollBarEnabled = false
            this.adapter = adapter
            setOnItemClickListener { _, _, position, _ ->
                onPick(apps[position])
                adapter.notifyDataSetChanged()
            }
        }
        val height = (context.resources.displayMetrics.heightPixels * 0.55f).toInt()
        column.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
    }

    /** A hairline between groups of options. */
    fun divider() {
        column.addView(
            View(context).apply { setBackgroundColor(Palette.TRACK) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                topMargin = context.dp(8)
                bottomMargin = context.dp(8)
            },
        )
    }

    fun show() {
        open += this
        dialog.show()
    }

    companion object {
        private val open = mutableListOf<Sheet>()

        /** Closes an activity's open sheets: pressing home or the screen going off closes menus. */
        fun dismissAll(activity: Activity) {
            open.filter { it.context === activity }.forEach { it.dialog.dismiss() }
        }
    }
}
