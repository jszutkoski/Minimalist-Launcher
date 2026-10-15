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
import android.widget.LinearLayout

/** A floating card at the bottom of the screen: a quiet title and a column of text actions. */
class Sheet(activity: Activity, title: String?) {
    val dialog = Dialog(activity)
    val column = LinearLayout(activity)
    private val context = activity

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

    fun show() = dialog.show()
}
