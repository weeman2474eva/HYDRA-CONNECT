package com.hydra.connect

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
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

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        token = getSharedPreferences("hc", Context.MODE_PRIVATE).getString("token", "") ?: ""
        if (token.isBlank()) loginScreen() else loadHome()
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

    private fun homeScreen() {
        val settings = portal.optJSONObject("settings") ?: JSONObject()
        val customer = portal.optJSONObject("customer") ?: JSONObject()
        baseScreen(settings.optString("brand_name", "HYDRA CONNECT"), settings.optString("welcome", "Everything you need, in one place."))

        val account = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)); background = rounded(panel, 20)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(label("MY SERVICE", 12f, accent, true))
        left.addView(label(customer.optString("username", "Account"), 20f, Color.WHITE, true))
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val status = customer.optString("status", "active").uppercase()
        val chip = label(status, 12f, if (status == "ACTIVE") accent else Color.rgb(255, 180, 80), true).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8)); background = rounded(Color.rgb(13, 50, 49), 20)
        }
        row.addView(chip); account.addView(row)
        account.addView(Space(this), LinearLayout.LayoutParams(1, dp(12)))
        account.addView(label("Expires  •  " + customer.optString("expiry_at", "Not available"), 14f, muted))
        account.setOnClickListener { serviceScreen() }; account.isClickable = true; account.isFocusable = true
        root.addView(account)
        spacer(18)

        val grid = GridLayout(this).apply { columnCount = if (resources.configuration.screenWidthDp >= 700) 3 else 2; alignmentMode = GridLayout.ALIGN_BOUNDS }
        addTile(grid, "NEWS", "News & Updates", "Latest announcements", "news")
        addTile(grid, "CHAT", "Messages", "Contact your seller", "messages")
        addTile(grid, "HELP", "Report a Problem", "Get support", "ticket")
        addTile(grid, "LIVE", "Service Status", settings.optString("service_status", "All systems operational"), "status")
        addTile(grid, "APPS", "Apps", "Downloads & tools", "apps")
        addTile(grid, "RENEW", "Renew Service", "Request a renewal", "renew")
        root.addView(grid)
        spacer(18)
        val sign = actionButton("Sign out", true)
        root.addView(sign, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        sign.setOnClickListener { AlertDialog.Builder(this).setTitle("Sign out?").setMessage("You will need to sign in again.").setNegativeButton("Cancel", null).setPositiveButton("Sign out") { _, _ -> logout() }.show() }
    }

    private fun addTile(grid: GridLayout, badge: String, title: String, sub: String, action: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.START
            setPadding(dp(16), dp(16), dp(16), dp(16)); background = rounded(panel, 18, 1, Color.rgb(34, 51, 60))
            isClickable = true; isFocusable = true
        }
        val icon = label(badge, 11f, accent, true).apply { setPadding(dp(9), dp(6), dp(9), dp(6)); background = rounded(Color.rgb(13, 50, 49), 10) }
        card.addView(icon); card.addView(Space(this), LinearLayout.LayoutParams(1, dp(14)))
        card.addView(label(title, 17f, Color.WHITE, true)); card.addView(Space(this), LinearLayout.LayoutParams(1, dp(5)))
        card.addView(label(sub, 13f, muted))
        card.setOnFocusChangeListener { v, focused ->
            v.background = rounded(if (focused) Color.rgb(25, 55, 62) else panel, 18, if (focused) 2 else 1, if (focused) accent else Color.rgb(34, 51, 60))
            v.scaleX = if (focused) 1.035f else 1f; v.scaleY = if (focused) 1.035f else 1f
        }
        card.setOnClickListener {
            when (action) {
                "news" -> newsScreen()
                "messages" -> messagesScreen()
                "ticket" -> ticketScreen()
                "status" -> statusScreen()
                "apps" -> appsScreen()
                "renew" -> renewScreen()
            }
        }
        val cols = grid.columnCount
        val width = (resources.displayMetrics.widthPixels - dp(40) - dp(12) * (cols - 1)) / cols
        val lp = GridLayout.LayoutParams().apply { this.width = width; height = dp(158); setMargins(0, 0, if (grid.childCount % cols != cols - 1) dp(12) else 0, dp(12)) }
        grid.addView(card, lp)
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

    private fun logout() {
        getSharedPreferences("hc", Context.MODE_PRIVATE).edit().clear().apply(); token = ""; portal = JSONObject(); loginScreen()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (token.isBlank()) super.onBackPressed() else loadHome()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

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
