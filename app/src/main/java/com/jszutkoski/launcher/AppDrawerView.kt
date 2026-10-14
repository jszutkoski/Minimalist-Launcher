package com.jszutkoski.launcher

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import kotlin.math.abs

/** Full-screen, searchable, alphabetical list of apps. Drag down at the top to close. */
class AppDrawerView(context: Context, private val callbacks: Callbacks) : LinearLayout(context) {
    interface Callbacks {
        fun onLaunch(app: App, source: View)
        fun onAppLongPress(app: App)
        /** Drag-to-close in progress; 1 is fully open, 0 is closed. */
        fun onCloseDrag(progress: Float)
        fun onCloseRelease(close: Boolean)
    }

    var autoLaunch = true

    var textScale = 1f
        set(value) {
            field = value
            search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f * value)
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * value)
            adapter.textScale = value
        }

    private var apps: List<App> = emptyList()
    private var lastQueryLength = 0

    private val adapter = AppAdapter(context)
    private val search = EditText(context)
    private val list = ListView(context)
    private val empty = context.text("No apps found", 17f, Palette.TERTIARY)
    private val hairline = View(context)

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val fling = context.dp(500f)
    private val dragDistance = context.dp(DRAG_DISTANCE_DP)
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var originY = 0f
    private var velocity: VelocityTracker? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Palette.BACKGROUND)

        search.apply {
            hint = "Search"
            setHintTextColor(Palette.TERTIARY)
            setTextColor(Palette.TEXT)
            typeface = weight(300)
            background = null
            setPadding(0, dp(14), 0, dp(14))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_FILTER
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            textCursorDrawable = GradientDrawable().apply {
                setColor(Palette.TEXT)
                setSize(dp(2), dp(28))
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = filter(fromTyping = true)
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    adapter.items.firstOrNull()?.let { callbacks.onLaunch(it, search) }
                    true
                } else {
                    false
                }
            }
        }
        addView(search, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        hairline.setBackgroundColor(Palette.HAIRLINE)
        addView(hairline, LayoutParams(LayoutParams.MATCH_PARENT, 1).apply { bottomMargin = dp(8) })

        list.apply {
            adapter = this@AppDrawerView.adapter
            divider = null
            selector = ColorDrawable(0)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setOnItemClickListener { _, view, position, _ -> callbacks.onLaunch(this@AppDrawerView.adapter.items[position], view) }
            setOnItemLongClickListener { _, _, position, _ ->
                callbacks.onAppLongPress(this@AppDrawerView.adapter.items[position])
                true
            }
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView, state: Int) {
                    if (state == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) hideKeyboard()
                }

                override fun onScroll(view: AbsListView, first: Int, visible: Int, total: Int) = Unit
            })
        }
        val listArea = FrameLayout(context)
        listArea.addView(list, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        empty.visibility = GONE
        listArea.addView(
            empty,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
                .apply { topMargin = dp(16) },
        )
        addView(listArea, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        textScale = 1f
    }

    fun setApps(apps: List<App>) {
        this.apps = apps
        filter(fromTyping = false)
    }

    fun applyInsets(left: Int, top: Int, right: Int, bottom: Int) {
        val side = dp(SIDE_MARGIN_DP)
        setPadding(left + side, top + dp(24), right + side, 0)
        list.setPadding(0, 0, 0, bottom + dp(24))
    }

    fun showKeyboard() {
        search.requestFocus()
        search.post {
            context.getSystemService(InputMethodManager::class.java)?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun hideKeyboard() {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
        search.clearFocus()
    }

    /** Back to an empty search at the top of the list. */
    fun reset() {
        hideKeyboard()
        if (search.text.isNotEmpty()) search.text.clear()
        list.setSelection(0)
    }

    private fun filter(fromTyping: Boolean) {
        val query = search.text.toString()
        val results = AppSearch.filter(apps, query)
        adapter.items = results
        empty.visibility = if (results.isEmpty() && apps.isNotEmpty()) VISIBLE else GONE

        // Only when the query grew: deleting letters should never launch anything.
        val grew = query.length > lastQueryLength
        lastQueryLength = query.length
        if (fromTyping && grew && autoLaunch && query.isNotBlank() && results.size == 1) {
            callbacks.onLaunch(results[0], search)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            dragging = false
            downX = ev.x
            downY = ev.y
            velocity?.recycle()
            velocity = VelocityTracker.obtain()
        }
        velocity?.addMovement(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        ev.actionMasked == MotionEvent.ACTION_MOVE && startDrag(ev)

    /** Always consumes touches, so nothing reaches the home screen underneath. */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (dragging || startDrag(ev)) {
                callbacks.onCloseDrag((1f - (ev.y - originY) / dragDistance).coerceIn(0f, 1f))
            }
            MotionEvent.ACTION_UP -> if (dragging) {
                val tracker = velocity
                tracker?.computeCurrentVelocity(1000)
                val vy = tracker?.yVelocity ?: 0f
                val moved = ev.y - originY
                callbacks.onCloseRelease(vy > fling || (vy > -fling && moved > dragDistance * 0.3f))
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> if (dragging) {
                callbacks.onCloseRelease(false)
                dragging = false
            }
        }
        return true
    }

    private fun startDrag(ev: MotionEvent): Boolean {
        if (dragging) return true
        val dx = ev.x - downX
        val dy = ev.y - downY
        if (dy > slop && dy > abs(dx) * 1.5f && !list.canScrollVertically(-1)) {
            dragging = true
            originY = ev.y
            hideKeyboard()
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return dragging
    }
}

private class AppAdapter(private val context: Context) : BaseAdapter() {
    var items: List<App> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var textScale = 1f
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount() = items.size

    override fun getItem(position: Int) = items[position]

    override fun getItemId(position: Int) = items[position].key.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView as? TextView ?: context.text("", 21f).apply {
            setPadding(0, dp(13), 0, dp(13))
        }
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f * textScale)
        view.text = items[position].label
        return view
    }
}
