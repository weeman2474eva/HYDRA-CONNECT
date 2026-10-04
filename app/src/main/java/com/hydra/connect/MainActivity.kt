package com.hydra.connect

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ActivityCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var root: LinearLayout
    private var token = ""
    private var portal = JSONObject()
    private val bg = Color.rgb(7, 13, 18)
    private val panel = Color.rgb(16, 27, 34)
    private val panel2 = Color.rgb(22, 37, 45)
    private val accent = Color.rgb(27, 211, 190)
    private val muted = Color.rgb(166, 181, 190)
    private val notificationHandler = Handler(Looper.getMainLooper())
    private var lastMessageId = ""
    private var unreadMessages = 0
    private val messagePoll = object : Runnable {
        override fun run() {
            if (token.isNotBlank()) checkNewMessages()
            notificationHandler.postDelayed(this, 60_000)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        token = getSharedPreferences("hc", Context.MODE_PRIVATE).getString("token", "") ?: ""
        createNotificationChannel()
        requestNotificationPermission()
        lastMessageId = getSharedPreferences("hc", Context.MODE_PRIVATE).getString("last_message_id", "") ?: ""
        if (token.isBlank()) loginScreen() else loadHome()
        notificationHandler.postDelayed(messagePoll, 8_000)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 18, stroke: Int = 0, strokeColor: Int = Color.TRANSPARENT): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color); cornerRadius = dp(radius).toFloat()
            if (stroke > 0) setStroke(dp(stroke), strokeColor)
        }
    }

    private fun baseScreen(title: String, subtitle: String = "", showBack: Boolean = false) {
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(bg) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(root)
        setContentView(scroll)

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (showBack) {
            val back = actionButton("‹", true).apply {
                textSize = 30f
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) }
                setOnClickListener { loadHome() }
            }
            top.addView(back)
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label(title, 27f, Color.WHITE, true))
        if (subtitle.isNotBlank()) titles.addView(label(subtitle, 14f, muted, false))
        top.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)
        spacer(18)
    }

    private fun label(value: String, size: Float = 16f, color: Int = Color.WHITE, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            includeFontPadding = false
        }
    }

    private fun spacer(h: Int) { root.addView(Space(this), LinearLayout.LayoutParams(1, dp(h))) }

    private fun input(hintText: String, secret: Boolean = false, lines: Int = 1): EditText {
        return EditText(this).apply {
            hint = hintText; setTextColor(Color.WHITE); setHintTextColor(Color.rgb(112, 132, 143))
            textSize = 16f; setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(panel2, 14, 1, Color.rgb(45, 65, 74))
            minHeight = dp(54); isSingleLine = lines == 1
            if (lines > 1) { minLines = lines; gravity = Gravity.TOP }
            if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
    }

    private fun actionButton(textValue: String, subtle: Boolean = false): Button {
        return Button(this).apply {
            text = textValue; isAllCaps = false; textSize = 15f
            setTextColor(if (subtle) Color.WHITE else Color.rgb(4, 25, 24))
            background = rounded(if (subtle) panel2 else accent, 14)
            isFocusable = true
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnFocusChangeListener { v, focused ->
                v.background = rounded(if (focused) Color.WHITE else if (subtle) panel2 else accent, 14, if (focused) 2 else 0, accent)
                (v as Button).setTextColor(if (focused) Color.BLACK else if (subtle) Color.WHITE else Color.rgb(4, 25, 24))
                v.scaleX = if (focused) 1.04f else 1f; v.scaleY = if (focused) 1.04f else 1f
            }
        }
    }

    private fun loginScreen() {
        baseScreen("HYDRA CONNECT", "Your service. Your support. One place.")
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); background = rounded(panel, 22)
        }
        hero.addView(label("Welcome back", 23f, Color.WHITE, true))
        hero.addView(label("Sign in with your service username and password.", 14f, muted))
        val u = input("Username"); val p = input("Password", true); val status = label("", 13f, muted)
        hero.addView(Space(this), LinearLayout.LayoutParams(1, dp(18))); hero.addView(u)
        hero.addView(Space(this), LinearLayout.LayoutParams(1, dp(12))); hero.addView(p)
        val b = actionButton("Sign in")
        hero.addView(Space(this), LinearLayout.LayoutParams(1, dp(16))); hero.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        hero.addView(Space(this), LinearLayout.LayoutParams(1, dp(10))); hero.addView(status)
        root.addView(hero)
        b.setOnClickListener {
            if (u.text.isBlank() || p.text.isBlank()) { status.text = "Enter your username and password."; return@setOnClickListener }
            b.isEnabled = false; status.text = "Signing in…"
            post("login.php", JSONObject().put("username", u.text.toString()).put("password", p.text.toString()), false) { j ->
                b.isEnabled = true
                if (j.optBoolean("ok")) {
                    token = j.optString("token")
                    getSharedPreferences("hc", Context.MODE_PRIVATE).edit().putString("token", token).apply()
                    loadHome()
                } else status.text = j.optString("error", "Login failed")
            }
        }
    }

    private fun loadHome() {
        get("portal.php") { j ->
            if (!j.optBoolean("ok")) { logout(); return@get }
            portal = j
            homeScreen()
        }
    }

    override fun onDestroy() {
        notificationHandler.removeCallbacks(messagePoll)
        super.onDestroy()
    }

    private fun homeScreen() {
        val settings = portal.optJSONObject("settings") ?: JSONObject()
        val customer = portal.optJSONObject("customer") ?: JSONObject()

        val stage = FrameLayout(this).apply { setBackgroundColor(Color.rgb(1, 7, 11)) }
        stage.addView(HydraBackdrop(this), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.TRANSPARENT)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(32))
        }
        scroll.addView(root)
        stage.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(stage)

        // HYDRA hero
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(8, 42, 48), Color.rgb(4, 20, 27), Color.rgb(2, 11, 16))
            ).apply { cornerRadius = dp(26).toFloat(); setStroke(dp(1), Color.rgb(20, 91, 96)) }
        }
        val crest = HydraCrestView(this)
        hero.addView(crest, LinearLayout.LayoutParams(dp(112), dp(76)))
        hero.addView(Space(this), LinearLayout.LayoutParams(1, dp(4)))
        hero.addView(label(settings.optString("brand_name", "HYDRA CONNECT").uppercase(), 27f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; letterSpacing = .08f
        })
        hero.addView(label("EVERYTHING YOU NEED, IN ONE PLACE.", 10f, accent, true).apply {
            gravity = Gravity.CENTER; letterSpacing = .16f
        })
        root.addView(hero)
        spacer(14)

        // Service glass card
        val service = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(13, 39, 45), Color.rgb(8, 27, 34))
            ).apply { cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.rgb(28, 104, 108)) }
            isClickable = true
        }
        val serviceTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val svcLeft = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        svcLeft.addView(label("MY SERVICE", 10f, accent, true).apply { letterSpacing = .16f })
        svcLeft.addView(label(customer.optString("username", "Account"), 20f, Color.WHITE, true))
        serviceTop.addView(svcLeft, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val statusText = customer.optString("status", "active").uppercase()
        serviceTop.addView(label("●  $statusText", 11f, accent, true).apply {
            setPadding(dp(11), dp(7), dp(11), dp(7))
            background = rounded(Color.rgb(5, 54, 52), 20, 1, Color.rgb(19, 105, 99))
        })
        service.addView(serviceTop)
        service.addView(Space(this), LinearLayout.LayoutParams(1, dp(13)))
        val expRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        expRow.addView(label("EXPIRY", 10f, muted, true))
        val expiryRaw = customer.optString("expiry_at", "")
        val days = daysRemaining(expiryRaw)
        val expiryLabel = if (days != null) customer.optString("expiry_at", "Not available") + "  •  " + when {
            days < 0 -> "EXPIRED"
            days == 0L -> "EXPIRES TODAY"
            days == 1L -> "1 DAY LEFT"
            else -> "$days DAYS LEFT"
        } else customer.optString("expiry_at", "Not available")
        expRow.addView(label(expiryLabel, 13f, if (days != null && days <= 7) Color.rgb(255,190,92) else Color.WHITE, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
        expRow.addView(label("›", 26f, accent, true))
        service.addView(expRow)
        service.setOnClickListener { serviceScreen() }
        root.addView(service)
        spacer(17)

        root.addView(label("QUICK ACCESS", 11f, Color.rgb(116, 151, 159), true).apply {
            letterSpacing = .16f; setPadding(dp(2), 0, 0, dp(10))
        })

        val grid = GridLayout(this).apply { columnCount = 2; alignmentMode = GridLayout.ALIGN_BOUNDS }
        addPremiumTile(grid, "✦", "News & Updates", "Latest announcements", "news")
        addPremiumTile(grid, "✉", if (unreadMessages > 0) "Messages  •  $unreadMessages NEW" else "Messages", if (unreadMessages > 0) "New message waiting" else "Contact your seller", "messages")
        addPremiumTile(grid, "!", "Report a Problem", "Get help quickly", "ticket")
        addPremiumTile(grid, "●", "Service Status", settings.optString("service_status", "All systems operational"), "status")
        addPremiumTile(grid, "⬡", "App Launcher", "Open installed apps", "launcher")
        addPremiumTile(grid, "↓", "Downloads", "Recommended apps", "apps")
        addPremiumTile(grid, "↻", "Renew Service", "Request renewal", "renew")
        addPremiumTile(grid, "◉", "My Details", "Account information", "details")
        root.addView(grid)
        spacer(8)

        val sign = TextView(this).apply {
            text = "SIGN OUT"; gravity = Gravity.CENTER; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(143, 168, 174)); letterSpacing = .15f
            setPadding(dp(14), dp(15), dp(14), dp(15))
            background = rounded(Color.rgb(7, 22, 28), 16, 1, Color.rgb(24, 55, 63))
            isClickable = true
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Sign out?").setMessage("You will need to sign in again.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Sign out") { _, _ -> logout() }.show()
            }
        }
        root.addView(sign)
    }

    private fun addPremiumTile(grid: GridLayout, glyph: String, title: String, sub: String, action: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(15), dp(12), dp(13))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(11, 34, 41), Color.rgb(5, 20, 27))
            ).apply { cornerRadius = dp(20).toFloat(); setStroke(dp(1), Color.rgb(25, 66, 73)) }
            isClickable = true
        }
        val icon = NeonIconView(this, action)
        card.addView(icon, LinearLayout.LayoutParams(dp(64), dp(64)))
        card.addView(Space(this), LinearLayout.LayoutParams(1, dp(10)))
        card.addView(label(title, 15f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        card.addView(Space(this), LinearLayout.LayoutParams(1, dp(4)))
        card.addView(label(sub, 11f, Color.rgb(125, 153, 160)).apply { gravity = Gravity.CENTER; maxLines = 2 })
        card.setOnClickListener {
            when (action) {
                "news" -> newsScreen()
                "messages" -> messagesScreen()
                "ticket" -> ticketScreen()
                "status" -> statusScreen()
                "launcher" -> launcherScreen()
                "apps" -> appsScreen()
                "renew" -> renewScreen()
                "details" -> serviceScreen()
            }
        }
        val width = (resources.displayMetrics.widthPixels - dp(48)) / 2
        grid.addView(card, GridLayout.LayoutParams().apply {
            this.width = width; height = dp(156)
            setMargins(0, 0, dp(10), dp(10))
        })
    }

    private fun launcherScreen() {
        baseScreen("App Launcher", "Open apps installed on your phone", true)
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        if (apps.isEmpty()) { emptyState("No launchable apps were found."); return }
        val grid = GridLayout(this).apply { columnCount = 3; alignmentMode = GridLayout.ALIGN_BOUNDS }
        val width = (resources.displayMetrics.widthPixels - dp(60)) / 3
        for (info in apps) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(7), dp(12), dp(7), dp(10))
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(11,34,41), Color.rgb(5,20,27))).apply {
                    cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.rgb(25,66,73))
                }
                isClickable = true
            }
            item.addView(ImageView(this).apply { setImageDrawable(info.loadIcon(packageManager)); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(50), dp(50)))
            item.addView(Space(this), LinearLayout.LayoutParams(1, dp(8)))
            item.addView(label(info.loadLabel(packageManager).toString(), 11f, Color.WHITE, true).apply { gravity = Gravity.CENTER; maxLines = 2 })
            item.setOnClickListener {
                packageManager.getLaunchIntentForPackage(info.activityInfo.packageName)?.let { launch ->
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launch)
                }
            }
            grid.addView(item, GridLayout.LayoutParams().apply { this.width = width; height = dp(112); setMargins(0,0,dp(8),dp(8)) })
        }
        root.addView(grid)
    }

    private fun serviceScreen() {
        val c = portal.optJSONObject("customer") ?: JSONObject()
        baseScreen("My Service", "Account information", true)
        infoCard("Username", c.optString("username", "—"))
        infoCard("Status", c.optString("status", "—").replaceFirstChar { it.uppercase() })
        infoCard("Expiry", c.optString("expiry_at", "Not available"))
        if (c.optString("max_connections").isNotBlank()) infoCard("Connections", c.optString("max_connections"))
    }

    private fun infoCard(k: String, v: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(15), dp(18), dp(15)); background = rounded(panel, 16) }
        box.addView(label(k.uppercase(), 11f, accent, true)); box.addView(Space(this), LinearLayout.LayoutParams(1, dp(6))); box.addView(label(v, 18f, Color.WHITE, true))
        root.addView(box); spacer(10)
    }

    private fun newsScreen() {
        baseScreen("News & Updates", "Announcements from your service", true)
        val posts = portal.optJSONArray("posts") ?: JSONArray()
        if (posts.length() == 0) { emptyState("No announcements right now."); return }
        for (i in 0 until posts.length()) {
            val p = posts.optJSONObject(i) ?: continue
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)); background = rounded(panel, 17) }
            if (p.optInt("pinned") == 1) box.addView(label("PINNED", 10f, accent, true))
            box.addView(label(p.optString("title", "Update"), 18f, Color.WHITE, true))
            box.addView(Space(this), LinearLayout.LayoutParams(1, dp(7))); box.addView(label(p.optString("body"), 14f, muted))
            box.addView(Space(this), LinearLayout.LayoutParams(1, dp(10))); box.addView(label(p.optString("created_at"), 11f, Color.rgb(108, 128, 138)))
            root.addView(box); spacer(12)
        }
    }

    private fun messagesScreen() {
        unreadMessages = 0
        baseScreen("Messages", "Private messages with your seller", true)
        val loading = label("Loading messages…", 14f, muted); root.addView(loading)
        get("messages.php") { j ->
            if (!j.optBoolean("ok")) { loading.text = "Unable to load messages."; return@get }
            messagesRender(j.optJSONArray("messages") ?: JSONArray())
        }
    }

    private fun messagesRender(messages: JSONArray) {
        baseScreen("Messages", "Private messages with your seller", true)
        if (messages.length() == 0) root.addView(label("No messages yet. Start the conversation below.", 14f, muted))
        for (i in 0 until messages.length()) {
            val m = messages.optJSONObject(i) ?: continue
            val mine = m.optString("sender") == "customer"
            val bubble = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(12), dp(15), dp(12))
                background = rounded(if (mine) Color.rgb(13, 69, 65) else panel, 16)
            }
            bubble.addView(label(if (mine) "YOU" else "SELLER", 10f, if (mine) accent else muted, true))
            bubble.addView(label(m.optString("body"), 15f, Color.WHITE))
            bubble.addView(label(m.optString("created_at"), 10f, muted))
            val lp = LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.78).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = if (mine) Gravity.END else Gravity.START; bottomMargin = dp(10)
            }
            root.addView(bubble, lp)
        }
        spacer(8)
        val msg = input("Write a message…", false, 3); root.addView(msg)
        spacer(10)
        val send = actionButton("Send message"); root.addView(send, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        send.setOnClickListener {
            val body = msg.text.toString().trim(); if (body.isBlank()) return@setOnClickListener
            send.isEnabled = false
            post("messages.php", JSONObject().put("body", body), true) { j -> if (j.optBoolean("ok")) messagesRender(j.optJSONArray("messages") ?: JSONArray()) else { send.isEnabled = true; toast("Message could not be sent") } }
        }
    }

    private fun ticketScreen() {
        baseScreen("Report a Problem", "Tell us what is wrong and we will help", true)
        val category = Spinner(this)
        val cats = arrayOf("General", "Live TV", "Movies / Series", "Login", "App", "Other")
        category.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cats)
        category.background = rounded(panel2, 14); category.setPadding(dp(12), dp(8), dp(12), dp(8))
        root.addView(category, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))); spacer(10)
        val subject = input("Short description"); root.addView(subject); spacer(10)
        val body = input("Tell us what happened…", false, 5); root.addView(body); spacer(12)
        val send = actionButton("Send support report"); root.addView(send, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        send.setOnClickListener {
            if (subject.text.isBlank() || body.text.isBlank()) { toast("Please add a description and details"); return@setOnClickListener }
            send.isEnabled = false
            val j = JSONObject().put("category", cats[category.selectedItemPosition]).put("subject", subject.text.toString()).put("body", body.text.toString())
            post("ticket.php", j, true) { r -> if (r.optBoolean("ok")) successScreen("Report sent", "Your support report has been sent successfully.") else { send.isEnabled = true; toast("Could not send report") } }
        }
    }

    private fun statusScreen() {
        val s = portal.optJSONObject("settings") ?: JSONObject()
        baseScreen("Service Status", "Current service information", true)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(22), dp(28), dp(22), dp(28)); background = rounded(panel, 20) }
        box.addView(label("●", 30f, accent, true).apply { gravity = Gravity.CENTER })
        box.addView(label(s.optString("service_status", "All systems operational"), 21f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        box.addView(label("Updates from your seller will appear here.", 13f, muted).apply { gravity = Gravity.CENTER })
        root.addView(box)
    }

    private fun appsScreen() {
        baseScreen("Apps", "Recommended downloads and tools", true)
        val apps = portal.optJSONArray("apps") ?: JSONArray()
        if (apps.length() == 0) { emptyState("No apps have been added yet."); return }
        for (i in 0 until apps.length()) {
            val a = apps.optJSONObject(i) ?: continue
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)); background = rounded(panel, 17) }
            box.addView(label(a.optString("name", "App"), 18f, Color.WHITE, true))
            val version = a.optString("version"); if (version.isNotBlank()) box.addView(label("Version " + version, 12f, accent, true))
            box.addView(label(a.optString("description"), 13f, muted))
            val download = actionButton("Open download")
            box.addView(Space(this), LinearLayout.LayoutParams(1, dp(12))); box.addView(download, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
            download.setOnClickListener {
                val url = a.optString("download_url")
                if (url.startsWith("http://") || url.startsWith("https://")) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) else toast("No download link is available")
            }
            root.addView(box); spacer(12)
        }
    }

    private fun renewScreen() {
        val settings = portal.optJSONObject("settings") ?: JSONObject()
        baseScreen("Renew Service", "Send a renewal request to your seller", true)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); background = rounded(panel, 18) }
        box.addView(label(settings.optString("renew_message", "Need more time? Send a renewal request and your seller will contact you."), 15f, muted))
        val note = input("Optional note", false, 3); box.addView(Space(this), LinearLayout.LayoutParams(1, dp(16))); box.addView(note)
        val send = actionButton("Request renewal"); box.addView(Space(this), LinearLayout.LayoutParams(1, dp(12))); box.addView(send, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        root.addView(box)
        send.setOnClickListener {
            send.isEnabled = false
            post("renew.php", JSONObject().put("note", note.text.toString()), true) { j ->
                if (j.optBoolean("ok")) successScreen("Request sent", j.optString("message", "Your renewal request has been sent.")) else { send.isEnabled = true; toast(j.optString("error", "Could not send request")) }
            }
        }
    }

    private fun successScreen(title: String, message: String) {
        baseScreen(title, "", true)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(22), dp(28), dp(22), dp(28)); background = rounded(panel, 20) }
        box.addView(label("✓", 38f, accent, true).apply { gravity = Gravity.CENTER })
        box.addView(label(message, 16f, Color.WHITE).apply { gravity = Gravity.CENTER })
        root.addView(box)
    }

    private fun emptyState(message: String) {
        val box = label(message, 15f, muted).apply { gravity = Gravity.CENTER; setPadding(dp(20), dp(30), dp(20), dp(30)); background = rounded(panel, 18) }
        root.addView(box)
    }

    private inner class HydraBackdrop(context: Context) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            p.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.rgb(1,7,11), Color.rgb(3,22,28), Color.rgb(1,8,13)), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),p)
            p.shader = RadialGradient(width*.78f, height*.13f, width*.75f,
                intArrayOf(Color.argb(95,0,215,198), Color.argb(22,0,130,140), Color.TRANSPARENT),
                floatArrayOf(0f,.42f,1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(width*.78f,height*.13f,width*.75f,p)
            p.shader = null
            p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = Color.argb(28,48,225,211)
            val s = dp(34).toFloat(); val h = s*.86f
            var y = h
            while (y < height) {
                var x = if (((y/h).toInt()%2)==0) 0f else s*.75f
                while (x < width+s) {
                    val path=Path()
                    for(i in 0..6){
                        val a=Math.PI/3*i
                        val px=x+(s*.48f*Math.cos(a)).toFloat(); val py=y+(s*.48f*Math.sin(a)).toFloat()
                        if(i==0) path.moveTo(px,py) else path.lineTo(px,py)
                    }
                    canvas.drawPath(path,p); x+=s*1.5f
                }
                y+=h*1.45f
            }
            p.style=Paint.Style.FILL
        }
    }

    private inner class HydraCrestView(context: Context) : View(context) {
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas){
            super.onDraw(c)
            val cx=width/2f; val cy=height*.58f
            p.style=Paint.Style.STROKE; p.strokeCap=Paint.Cap.ROUND; p.strokeJoin=Paint.Join.ROUND
            p.strokeWidth=dp(4).toFloat(); p.color=accent
            p.setShadowLayer(dp(9).toFloat(),0f,0f,accent); setLayerType(LAYER_TYPE_SOFTWARE,p)
            val path=Path()
            path.moveTo(cx,cy+dp(18)); path.cubicTo(cx-dp(4),cy, cx-dp(2),cy-dp(18), cx-dp(19),cy-dp(27))
            path.cubicTo(cx-dp(34),cy-dp(35), cx-dp(42),cy-dp(20), cx-dp(29),cy-dp(14))
            path.moveTo(cx,cy+dp(18)); path.cubicTo(cx+dp(4),cy, cx+dp(2),cy-dp(18), cx+dp(19),cy-dp(27))
            path.cubicTo(cx+dp(34),cy-dp(35), cx+dp(42),cy-dp(20), cx+dp(29),cy-dp(14))
            path.moveTo(cx,cy+dp(13)); path.cubicTo(cx,cy-dp(5),cx-dp(8),cy-dp(23),cx,cy-dp(34))
            path.cubicTo(cx+dp(7),cy-dp(43),cx+dp(15),cy-dp(34),cx+dp(8),cy-dp(27))
            c.drawPath(path,p)
            p.style=Paint.Style.FILL; p.clearShadowLayer()
            c.drawCircle(cx,cy+dp(19),dp(4).toFloat(),p)
        }
    }

    private inner class NeonIconView(context: Context, private val kind:String) : View(context) {
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas){
            super.onDraw(c)
            val cx=width/2f; val cy=height/2f
            p.color=Color.rgb(5,45,50); p.style=Paint.Style.FILL
            c.drawCircle(cx,cy,width*.45f,p)
            p.color=Color.rgb(24,112,108); p.style=Paint.Style.STROKE; p.strokeWidth=dp(1).toFloat()
            c.drawCircle(cx,cy,width*.45f,p)
            p.color=accent; p.strokeWidth=dp(2).toFloat(); p.strokeCap=Paint.Cap.ROUND; p.strokeJoin=Paint.Join.ROUND
            p.setShadowLayer(dp(5).toFloat(),0f,0f,accent); setLayerType(LAYER_TYPE_SOFTWARE,p)
            val r=dp(15).toFloat()
            when(kind){
                "messages"->{ val q=RectF(cx-r,cy-r*.7f,cx+r,cy+r*.65f); c.drawRoundRect(q,dp(4).toFloat(),dp(4).toFloat(),p); c.drawLine(cx-r*.45f,cy+r*.65f,cx-r*.7f,cy+r,p) }
                "news"->{ c.drawRect(cx-r,cy-r,cx+r,cy+r,p); c.drawLine(cx-r*.65f,cy-r*.45f,cx+r*.65f,cy-r*.45f,p); c.drawLine(cx-r*.65f,cy,cx+r*.65f,cy,p); c.drawLine(cx-r*.65f,cy+r*.45f,cx+r*.2f,cy+r*.45f,p) }
                "ticket"->{ c.drawCircle(cx,cy,r,p); c.drawLine(cx,cy-r*.55f,cx,cy+r*.15f,p); c.drawCircle(cx,cy+r*.55f,dp(1).toFloat(),p) }
                "status"->{ c.drawCircle(cx,cy,r,p); c.drawCircle(cx,cy,dp(4).toFloat(),p); c.drawArc(RectF(cx-r*.55f,cy-r*.55f,cx+r*.55f,cy+r*.55f),210f,120f,false,p) }
                "launcher"->{ for(ix in -1..1) for(iy in -1..1) c.drawCircle(cx+ix*dp(9),cy+iy*dp(9),dp(2).toFloat(),p) }
                "apps"->{ c.drawRect(cx-r,cy-r*.8f,cx+r,cy+r*.8f,p); c.drawLine(cx,cy-r*.45f,cx,cy+r*.25f,p); c.drawLine(cx-dp(5),cy+r*.05f,cx,cy+r*.3f,p); c.drawLine(cx+dp(5),cy+r*.05f,cx,cy+r*.3f,p) }
                "renew"->{ c.drawArc(RectF(cx-r,cy-r,cx+r,cy+r),35f,285f,false,p); c.drawLine(cx+r*.75f,cy-r*.6f,cx+r*.95f,cy-r*.2f,p); c.drawLine(cx+r*.75f,cy-r*.6f,cx+r*.35f,cy-r*.55f,p) }
                else->{ c.drawCircle(cx,cy-r*.35f,dp(6).toFloat(),p); c.drawArc(RectF(cx-r*.65f,cy,cx+r*.65f,cy+r),180f,180f,false,p) }
            }
            p.clearShadowLayer()
        }
    }

    private fun logout() {
        getSharedPreferences("hc", Context.MODE_PRIVATE).edit().clear().apply(); token = ""; portal = JSONObject(); loginScreen()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (token.isBlank()) super.onBackPressed() else loadHome()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("hydra_messages", "Messages & service alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Private messages and important HYDRA CONNECT alerts"
                enableVibration(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7001)
        }
    }

    private fun checkNewMessages() {
        get("messages.php") { j ->
            if (!j.optBoolean("ok")) return@get
            val arr = j.optJSONArray("messages") ?: return@get
            if (arr.length() == 0) return@get
            val newest = arr.optJSONObject(arr.length() - 1) ?: return@get
            val id = newest.optString("id", newest.optString("created_at") + "|" + newest.optString("body"))
            val fromSeller = newest.optString("sender") != "customer"
            if (lastMessageId.isBlank()) {
                lastMessageId = id
                getSharedPreferences("hc", Context.MODE_PRIVATE).edit().putString("last_message_id", id).apply()
                return@get
            }
            if (id != lastMessageId) {
                lastMessageId = id
                getSharedPreferences("hc", Context.MODE_PRIVATE).edit().putString("last_message_id", id).apply()
                if (fromSeller) {
                    unreadMessages++
                    showMessageNotification(newest.optString("body", "You have a new message from your seller."))
                }
            }
        }
    }

    private fun showMessageNotification(body: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)
        launch.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pending = PendingIntent.getActivity(this, 81, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "hydra_messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("New HYDRA CONNECT message")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(8101, n)
    }

    private fun daysRemaining(raw: String): Long? {
        if (raw.isBlank()) return null
        val formats = arrayOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "dd/MM/yyyy")
        for (fmt in formats) try {
            val d = SimpleDateFormat(fmt, Locale.UK).parse(raw) ?: continue
            return ((d.time - Date().time) / 86_400_000L).coerceAtLeast(-999)
        } catch (_: Exception) {}
        return null
    }

    private fun get(ep: String, done: (JSONObject) -> Unit) {
        val r = Request.Builder().url(Config.API_BASE_URL + ep)
        if (token.isNotBlank()) r.header("Authorization", "Bearer " + token)
        client.newCall(r.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { runOnUiThread { toast("Unable to connect") } }
            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string() ?: "{}"; val j = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
                runOnUiThread { done(j) }
            }
        })
    }

    private fun post(ep: String, j: JSONObject, auth: Boolean, done: (JSONObject) -> Unit) {
        val body = j.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val r = Request.Builder().url(Config.API_BASE_URL + ep).post(body)
        if (auth) r.header("Authorization", "Bearer " + token)
        client.newCall(r.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { runOnUiThread { toast("Unable to connect") } }
            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string() ?: "{}"; val data = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
                runOnUiThread { done(data) }
            }
        })
    }
}
