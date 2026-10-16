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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import kotlin.math.abs

/**
 * Full-screen, searchable, alphabetical list of apps. Drag down at the top to close. The
 * search also answers sums and finds contacts, settings and the web, below the apps.
 */
class AppDrawerView(context: Context, private val callbacks: Callbacks) : LinearLayout(context) {
    interface Callbacks {
        fun onLaunch(app: App, source: View)
        fun onAppLongPress(app: App)
        /** Anything but an app: a sum, a contact, a setting or the web. */
        fun onResult(result: SearchResult, source: View)
        fun onWhatsApp(contact: Contact)
        /** Drag-to-close in progress; 1 is fully open, 0 is closed. */
        fun onCloseDrag(progress: Float)
        fun onCloseRelease(close: Boolean)
    }

    var autoLaunch = true

    /** Nothing listed until a letter is typed. */
    var searchOnly = false
        set(value) {
            if (field == value) return
            field = value
            search.hint = if (value) "Type an app name" else "Search"
            filter(fromTyping = false)
        }

    var textScale = 1f
        set(value) {
            field = value
            search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f * value)
            browse.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * value)
            adapter.textScale = value
        }

    /** Searched by name once two letters are typed; empty while contacts are off. */
    var contacts: List<Contact> = emptyList()
        set(value) {
            field = value
            filter(fromTyping = false)
        }

    /** WhatsApp is installed, so contacts get its glyph. */
    var whatsApp: Boolean
        get() = adapter.whatsApp
        set(value) {
            adapter.whatsApp = value
        }

    private var apps: List<App> = emptyList()
    private var lastQueryLength = 0

    private val adapter = ResultAdapter(context) { callbacks.onWhatsApp(it) }
    private val search = EditText(context)
    private val list = ListView(context)
    private val hairline = View(context)

    /** Search only, for when the name is forgotten: the whole list, until the drawer closes. */
    private val browse = context.text("Show all apps", 17f, Palette.SECONDARY, 400).apply {
        letterSpacing = 0.02f
        setPadding(0, dp(12), 0, dp(12))
        visibility = GONE
    }
    private var showAll = false

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
                    adapter.items.firstOrNull()?.let { open(it, search) }
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
            setOnItemClickListener { _, view, position, _ -> open(this@AppDrawerView.adapter.items[position], view) }
            setOnItemLongClickListener { _, _, position, _ ->
                val item = this@AppDrawerView.adapter.items[position]
                if (item is SearchResult.AppHit) callbacks.onAppLongPress(item.app)
                item is SearchResult.AppHit
            }
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView, state: Int) {
                    if (state == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) hideKeyboard()
                }

                override fun onScroll(view: AbsListView, first: Int, visible: Int, total: Int) = Unit
            })
        }
        browse.setOnClickListener {
            showAll = true
            // Browsing, not typing: the keyboard would cover half the list.
            hideKeyboard()
            filter(fromTyping = false)
        }
        addView(browse, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

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
        if (showAll) {
            showAll = false
            filter(fromTyping = false)
        }
        if (search.text.isNotEmpty()) search.text.clear()
        list.setSelection(0)
    }

    private fun open(result: SearchResult, source: View) {
        if (result is SearchResult.AppHit) callbacks.onLaunch(result.app, source) else callbacks.onResult(result, source)
    }

    /** A sum's answer, then apps, contacts and settings, and the web last. */
    private fun filter(fromTyping: Boolean) {
        val query = search.text.toString()
        val q = query.trim()
        val waiting = searchOnly && !showAll && q.isEmpty()
        browse.visibility = if (waiting) VISIBLE else GONE
        val found = if (waiting) emptyList() else AppSearch.filter(apps, query)
        val people = ContactSearch.filter(contacts, q).takeIf { q.length >= 2 }.orEmpty().take(MAX_CONTACTS)
        val settings = SettingSearch.filter(q).take(MAX_SETTINGS)
        adapter.items = buildList {
            Calculator.evaluate(q)?.let { add(SearchResult.Answer(it)) }
            found.mapTo(this, SearchResult::AppHit)
            people.mapTo(this, SearchResult::ContactHit)
            settings.mapTo(this, SearchResult::SettingHit)
            if (q.isNotEmpty()) add(SearchResult.Web(q))
        }

        // Only when the query grew: deleting letters should never launch anything. Only from
        // two letters that begin the name or a word of it, so a question can still be typed.
        // And only when the app is the one thing found, not a contact or setting.
        val grew = query.length > lastQueryLength
        lastQueryLength = query.length
        if (fromTyping && grew && autoLaunch && q.length >= 2 && found.size == 1 &&
            AppSearch.startsWord(found[0], q) && people.isEmpty() && settings.isEmpty()
        ) {
            callbacks.onLaunch(found[0], search)
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

private const val MAX_CONTACTS = 4
private const val MAX_SETTINGS = 3

private class ResultAdapter(private val context: Context, private val onWhatsApp: (Contact) -> Unit) : BaseAdapter() {
    var items: List<SearchResult> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var textScale = 1f
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var whatsApp = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    override fun getCount() = items.size

    override fun getItem(position: Int) = items[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getViewTypeCount() = 2

    override fun getItemViewType(position: Int) = if (items[position] is SearchResult.AppHit) 0 else 1

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = items[position]
        if (item !is SearchResult.AppHit) return row(item)
        val view = convertView as? TextView ?: context.text("", 21f).apply {
            setPadding(0, dp(13), 0, dp(13))
        }
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f * textScale)
        view.text = item.app.label
        return view
    }

    /** Title, a quiet word saying what it is, and for contacts on WhatsApp its glyph. Few, so built fresh. */
    private fun row(item: SearchResult): View {
        val title: String
        var color = Palette.TEXT
        val hint: String
        var contact: Contact? = null
        when (item) {
            is SearchResult.Answer -> {
                title = "= ${Calculator.format(item.value)}"
                hint = "Copy"
            }
            is SearchResult.ContactHit -> {
                title = item.contact.name
                hint = "Contact"
                contact = item.contact.takeIf { whatsApp && it.whatsApp != null }
            }
            is SearchResult.SettingHit -> {
                title = item.setting.label
                hint = if (item.setting.isTorch) (if (Torch.on) "Turn off" else "Turn on") else "Settings"
            }
            is SearchResult.Web -> {
                title = "Search \u201c${item.query}\u201d"
                color = Palette.SECONDARY
                hint = "Web"
            }
            is SearchResult.AppHit -> error("Apps use the plain row")
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(56)
        }
        row.addView(
            context.text(title, 21f * textScale, color).apply {
                setPadding(0, dp(13), 0, dp(13))
                isDuplicateParentStateEnabled = true
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            context.text(hint, 13f * textScale, Palette.TERTIARY, 400).apply {
                letterSpacing = 0.04f
                setPadding(context.dp(12), 0, 0, 0)
                isDuplicateParentStateEnabled = true
            },
        )
        if (contact != null) {
            row.addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_chat)
                    imageTintList = pressable(Palette.TEXT)
                    contentDescription = "WhatsApp"
                    val inset = context.dp(10)
                    setPadding(inset, inset, 0, inset)
                    setOnClickListener { onWhatsApp(contact) }
                },
                LinearLayout.LayoutParams(context.dp(46), context.dp(46)).apply { leftMargin = context.dp(6) },
            )
        }
        return row
    }
}
