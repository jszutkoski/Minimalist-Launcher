package com.jszutkoski.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.DecimalFormatSymbols
import java.util.Calendar
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
            search.hint = context.getString(if (value) R.string.search_only_hint else R.string.search_hint)
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
    private val browse = context.text(context.getString(R.string.show_all_apps), 17f, Palette.SECONDARY, 400).apply {
        letterSpacing = 0.02f
        setPadding(0, dp(12), 0, dp(12))
        visibility = GONE
    }
    private var showAll = false

    /**
     * Sums are read and written the way of the country the phone is in, not of its language:
     * in Brazil "1.500" is fifteen hundred even on a phone in English. Looked up again each
     * time the list closes, in case the phone has travelled.
     */
    private var numbers = Region.numbers(context)

    /** Asked for but not up yet: a request made before the window has focus is dropped by Android. */
    private var keyboardWanted = false

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
            hint = context.getString(R.string.search_hint)
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
                    // An empty search lists every app: Go would open whichever comes first.
                    if (search.text.isBlank()) hideKeyboard() else adapter.items.firstOrNull()?.let { open(it, search) }
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
        keyboardWanted = true
        raiseKeyboard(FRAMES_TO_APPEAR)
    }

    /**
     * Once the list is on screen: an editor in a hidden view can't take focus, and the list only
     * appears with the first frame of its opening. Retried for a few frames, then left to
     * [onWindowFocus] if the window still has no focus (just back home, or the shade closing).
     */
    private fun raiseKeyboard(framesLeft: Int) {
        if (!keyboardWanted) return
        if (!isShown || !hasWindowFocus()) {
            if (framesLeft > 0) postOnAnimation { raiseKeyboard(framesLeft - 1) }
            return
        }
        search.requestFocus()
        search.windowInsetsController?.show(WindowInsets.Type.ime())
        context.getSystemService(InputMethodManager::class.java)?.showSoftInput(search, 0)
    }

    /** The window has focus again: a keyboard still wanted goes up now. */
    fun onWindowFocus() = raiseKeyboard(FRAMES_TO_APPEAR)

    /** The keyboard is up: nothing left to ask for. */
    fun onKeyboardShown() {
        keyboardWanted = false
    }

    fun hideKeyboard() {
        keyboardWanted = false
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
        search.clearFocus()
    }

    /** Back to an empty search at the top of the list. */
    fun reset() {
        hideKeyboard()
        numbers = Region.numbers(context)
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

    /**
     * A sum's answer or a timer or alarm, then apps, contacts and settings, the clock app's own
     * pages for its word alone, and the web last.
     */
    private fun filter(fromTyping: Boolean) {
        val query = search.text.toString()
        val q = query.trim()
        val waiting = searchOnly && !showAll && q.isEmpty()
        browse.visibility = if (waiting) VISIBLE else GONE
        val found = if (waiting) emptyList() else AppSearch.filter(apps, query)
        val people = ContactSearch.filter(contacts, q).takeIf { q.length >= 2 }.orEmpty().take(MAX_CONTACTS)
        val settings = SettingSearch.filter(q).take(MAX_SETTINGS)
        val (pages, clock) = ClockSearch.parse(q).partition { it is ClockCommand.Page }
        adapter.items = buildList {
            Calculator.evaluate(q, DecimalFormatSymbols.getInstance(numbers).decimalSeparator)
                ?.let { add(SearchResult.Answer(it, numbers)) }
            clock.mapTo(this, SearchResult::Clock)
            found.mapTo(this, SearchResult::AppHit)
            people.mapTo(this, SearchResult::ContactHit)
            settings.mapTo(this, SearchResult::SettingHit)
            pages.mapTo(this, SearchResult::Clock)
            if (q.isNotEmpty()) add(SearchResult.Web(q))
        }

        // Only when the query grew: deleting letters should never launch anything.
        val grew = query.length > lastQueryLength
        lastQueryLength = query.length
        if (fromTyping && grew && autoLaunch &&
            AppSearch.opensAlone(q, found.map { it.searchKey }, people.size, settings.size + clock.size + pages.size)
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

    /** Always consumes touches, so nothing reaches the home screen underneath. Drags, no click: back closes it. */
    @SuppressLint("ClickableViewAccessibility")
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

/** How many frames the keyboard waits for the list to appear before leaving it to window focus. */
private const val FRAMES_TO_APPEAR = 10
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
                title = "= ${Calculator.format(item.value, item.numbers)}"
                hint = context.getString(R.string.result_copy)
            }
            is SearchResult.ContactHit -> {
                title = item.contact.name
                hint = context.getString(R.string.result_contact)
                contact = item.contact.takeIf { whatsApp && it.whatsApp != null }
            }
            is SearchResult.SettingHit -> {
                title = context.getString(item.setting.label)
                hint = context.getString(
                    when {
                        !item.setting.isTorch -> R.string.result_settings
                        Torch.on -> R.string.result_turn_off
                        else -> R.string.result_turn_on
                    },
                )
            }
            is SearchResult.Clock -> when (val command = item.command) {
                is ClockCommand.Timer -> {
                    title = context.getString(R.string.result_timer, ClockSearch.describe(command.seconds))
                    hint = context.getString(R.string.result_start)
                }
                is ClockCommand.Alarm -> {
                    val at = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, command.hour)
                        set(Calendar.MINUTE, command.minute)
                    }
                    title = context.getString(R.string.result_alarm, DateFormat.getTimeFormat(context).format(at.time))
                    hint = context.getString(R.string.result_set)
                }
                is ClockCommand.Page -> {
                    title = context.getString(if (command.alarms) R.string.clock_alarms else R.string.clock_timers)
                    hint = context.getString(R.string.result_open)
                }
            }
            is SearchResult.Web -> {
                title = context.getString(R.string.result_web_title, item.query)
                color = Palette.SECONDARY
                hint = context.getString(R.string.result_web)
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
