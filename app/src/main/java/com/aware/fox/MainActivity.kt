package com.aware.fox

import android.app.Activity
import android.app.AppOpsManager
import android.app.DatePickerDialog
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val PREFS = "aware_fox_prefs"
const val KEY_MESSAGE = "message"
const val KEY_DATE = "promise_date"
const val KEY_MEDIA_URI = "media_uri"
const val KEY_BLOCKED = "blocked_packages"
const val KEY_ACTIVE = "active"
const val KEY_UNLOCK_UNTIL = "unlock_until"
const val CHANNEL_ID = "aware_fox_channel"
const val NOTIF_ID = 4401

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private val appCheckboxes = mutableMapOf<String, CheckBox>()
    private lateinit var messageEdit: EditText
    private lateinit var dateButton: Button
    private lateinit var mediaButton: Button
    private var selectedDate: Long = 0L
    private var selectedMediaUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 96)
            setBackgroundColor(Color.parseColor("#0E0E10"))
        }
        scroll.addView(root)

        val title = TextView(this).apply {
            text = "🦊 AWARE FOX"
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FF6D00"))
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "حوّل إرادتك إلى قفل. كل مرة تفتح تطبيقاً مُدمن، سيرفض الهاتف حتى تتحقق من رمزك."
            textSize = 14f
            setTextColor(Color.parseColor("#B0B0B0"))
            setPadding(0, 12, 0, 8)
        }
        root.addView(subtitle)

        root.addView(sectionTitle("1) الصلاحيات"))

        val usageBtn = Button(this).apply {
            text = "منح صلاحية Usage Access"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        root.addView(usageBtn)

        val overlayBtn = Button(this).apply {
            text = "منح صلاحية Overlay (SYSTEM_ALERT_WINDOW)"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        root.addView(overlayBtn)

        val adminBtn = Button(this).apply {
            text = "تفعيل Device Admin (لمنع الإلغاء السهل)"
            setOnClickListener {
                try {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            ComponentName(this@MainActivity, AwareDeviceAdminReceiver::class.java)
                        )
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "AWARE FOX يستخدم Device Admin لمنع إلغاء التطبيق بسهولة."
                        )
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        root.addView(adminBtn)

        root.addView(sectionTitle("2) رسالة الوعد"))

        messageEdit = EditText(this).apply {
            hint = "اكتب رسالتك لنفسك... مثلاً: تذكّر لماذا بدأت."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setMinLines(4)
            setText(prefs.getString(KEY_MESSAGE, ""))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#666666"))
        }
        root.addView(messageEdit)

        root.addView(sectionTitle("3) تاريخ الوعد"))

        dateButton = Button(this).apply {
            text = "اختر التاريخ"
            setOnClickListener { showDatePicker() }
        }
        root.addView(dateButton)

        val savedDate = prefs.getLong(KEY_DATE, 0L)
        if (savedDate > 0L) updateDateButton(savedDate)

        root.addView(sectionTitle("4) صوت أو فيديو اختياري"))

        mediaButton = Button(this).apply {
            text = "اختر ملف صوت/فيديو"
            setOnClickListener { pickMedia() }
        }
        root.addView(mediaButton)

        selectedMediaUri = prefs.getString(KEY_MEDIA_URI, null)
        if (selectedMediaUri != null) mediaButton.text = "تم اختيار الملف ✓"

        root.addView(sectionTitle("5) التطبيقات التي تريد مراقبتها"))

        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = pm.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val blocked = prefs.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
        val appsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (appInfo in apps) {
            val cb = CheckBox(this).apply {
                text = pm.getApplicationLabel(appInfo).toString()
                setTextColor(Color.WHITE)
                isChecked = blocked.contains(appInfo.packageName)
            }
            appCheckboxes[appInfo.packageName] = cb
            appsContainer.addView(cb)
        }
        root.addView(appsContainer)

        val saveBtn = Button(this).apply {
            text = "SAVE & ACTIVATE"
            setBackgroundColor(Color.parseColor("#FF6D00"))
            setTextColor(Color.WHITE)
            setPadding(0, 32, 0, 32)
            setOnClickListener { saveAndActivate() }
        }
        val saveLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 48 }
        root.addView(saveBtn, saveLp)

        val stopBtn = Button(this).apply {
            text = "STOP & DISABLE"
            setOnClickListener {
                prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
                stopService(Intent(this@MainActivity, MonitorService::class.java))
                Toast.makeText(this@MainActivity, "تم الإيقاف", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(stopBtn)

        setContentView(scroll)

        if (prefs.getBoolean(KEY_ACTIVE, false)) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, MonitorService::class.java)
            )
        }
    }

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.parseColor("#FF6D00"))
        setPadding(0, 48, 0, 8)
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        if (selectedDate > 0L) cal.timeInMillis = selectedDate
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val c = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                updateDateButton(c.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateButton(millis: Long) {
        selectedDate = millis
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        dateButton.text = "التاريخ: " + fmt.format(Date(millis))
    }

    private fun pickMedia() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, 1001)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    selectedMediaUri = uri.toString()
                    mediaButton.text = "تم اختيار الملف ✓"
                } catch (e: SecurityException) {
                    Toast.makeText(this, "تعذّر تثبيت صلاحية الملف: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveAndActivate() {
        val message = messageEdit.text.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, "الرسالة مطلوبة", Toast.LENGTH_SHORT).show(); return
        }
        if (selectedDate <= 0L) {
            Toast.makeText(this, "اختر تاريخ الوعد", Toast.LENGTH_SHORT).show(); return
        }
        val blocked = appCheckboxes.filter { it.value.isChecked }.keys
        if (blocked.isEmpty()) {
            Toast.makeText(this, "اختر تطبيقاً واحداً على الأقل", Toast.LENGTH_SHORT).show(); return
        }

        prefs.edit()
            .putString(KEY_MESSAGE, message)
            .putLong(KEY_DATE, selectedDate)
            .putString(KEY_MEDIA_URI, selectedMediaUri)
            .putStringSet(KEY_BLOCKED, blocked)
            .putLong(KEY_UNLOCK_UNTIL, 0L)
            .putBoolean(KEY_ACTIVE, true)
            .apply()

        if (!hasOverlayPermission()) {
            Toast.makeText(this, "⚠️ صلاحية Overlay مفقودة — شاشة المنع لن تظهر", Toast.LENGTH_LONG).show()
        }
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "⚠️ صلاحية Usage Access مفقودة — التطبيقات لن تُكتشف", Toast.LENGTH_LONG).show()
        }

        ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
        Toast.makeText(this, "AWARE FOX نشط الآن 🦊", Toast.LENGTH_SHORT).show()
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastCheckTime = 0L
    private var lastBlockedAt = 0L

    private val pollRunnable = object : Runnable {
        override fun run() {
            try { checkForegroundApp() } catch (_: Exception) {}
            handler.postDelayed(this, 800L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AWARE FOX نشط")
            .setContentText("يراقب التطبيقات التي اخترتها")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIF_ID, notif)
        handler.post(pollRunnable)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "AWARE FOX",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "خدمة مراقبة التطبيقات" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun checkForegroundApp() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) { stopSelf(); return }
        val blocked = prefs.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
        if (blocked.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now < prefs.getLong(KEY_UNLOCK_UNTIL, 0L)) return
        if (now - lastBlockedAt < 3000L) return

        val usm = getSystemService(UsageStatsManager::class.java)
        val begin = if (lastCheckTime > 0L) maxOf(lastCheckTime, now - 5000L) else now - 5000L
        val events = usm.queryEvents(begin, now)
        val ev = UsageEvents.Event()
        var currentPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentPkg = ev.packageName
            }
        }
        lastCheckTime = now

        if (currentPkg != null && blocked.contains(currentPkg)) {
            lastBlockedAt = now
            val intent = Intent(this, BlockerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("blocked_pkg", currentPkg)
            }
            try { startActivity(intent) } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }
}

class BlockerActivity : Activity() {
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val message = prefs.getString(KEY_MESSAGE, "") ?: ""
        val dateMillis = prefs.getLong(KEY_DATE, 0L)
        val mediaUri = prefs.getString(KEY_MEDIA_URI, null)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E0E10"))
            setPadding(64, 128, 64, 64)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(root)

        val title = TextView(this).apply {
            text = "🦊 STOP"
            textSize = 56f
            setTextColor(Color.parseColor("#FF6D00"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val sub = TextView(this).apply {
            text = "لقد وعدت نفسك."
            textSize = 18f
            setTextColor(Color.parseColor("#B0B0B0"))
            setPadding(0, 12, 0, 32)
            gravity = Gravity.CENTER
        }
        root.addView(sub)

        val msgView = TextView(this).apply {
            text = message
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 32, 0, 32)
            gravity = Gravity.CENTER
        }
        root.addView(msgView)

        if (dateMillis > 0L) {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dateView = TextView(this).apply {
                text = "⏳ الوعد: " + fmt.format(Date(dateMillis))
                textSize = 20f
                setTextColor(Color.parseColor("#FFB74D"))
                setPadding(0, 0, 0, 32)
                gravity = Gravity.CENTER
            }
            root.addView(dateView)
        }

        if (mediaUri != null) {
            try {
                val uri = Uri.parse(mediaUri)
                val mime = contentResolver.getType(uri) ?: ""
                if (mime.startsWith("video/")) {
                    val vv = VideoView(this)
                    vv.setVideoURI(uri)
                    vv.setOnPreparedListener { mp ->
                        mp.isLooping = true
                        vv.start()
                    }
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        560
                    )
                    root.addView(vv, lp)
                } else if (mime.startsWith("audio/")) {
                    val mp = MediaPlayer()
                    mp.setDataSource(this, uri)
                    mp.isLooping = true
                    mp.prepare()
                    mp.start()
                    mediaPlayer = mp
                    val audioLabel = TextView(this).apply {
                        text = "🎧 يتم تشغيل صوتك الآن..."
                        textSize = 16f
                        setTextColor(Color.parseColor("#FFB74D"))
                        setPadding(0, 16, 0, 16)
                        gravity = Gravity.CENTER
                    }
                    root.addView(audioLabel)
                }
            } catch (_: Exception) {}
        }

        val unlockBtn = Button(this).apply {
            text = "أتحقق من رمز الهاتف للدخول"
            setBackgroundColor(Color.parseColor("#FF6D00"))
            setTextColor(Color.WHITE)
            setOnClickListener { promptUnlock() }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 48 }
        root.addView(unlockBtn, lp)

        val footer = TextView(this).apply {
            text = "© AWARE FOX — جميع الحقوق محفوظة لصاحب التطبيق"
            textSize = 12f
            setTextColor(Color.parseColor("#707070"))
            setPadding(0, 96, 0, 0)
            gravity = Gravity.CENTER
        }
        root.addView(footer)

        setContentView(scroll)
    }

    private fun promptUnlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isDeviceSecure) {
            Toast.makeText(
                this,
                "⚠️ اضبط رمز قفل الشاشة أولاً من إعدادات الهاتف",
                Toast.LENGTH_LONG
            ).show()
            goBackAndClose()
            return
        }
        val intent = km.createConfirmDeviceCredentialIntent(
            "AWARE FOX",
            "تحقق من رمز الهاتف للمتابعة"
        )
        if (intent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 2001)
        } else {
            goBackAndClose()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == RESULT_OK) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(KEY_UNLOCK_UNTIL, System.currentTimeMillis() + 5 * 60 * 1000L)
                .apply()
            finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        goBackAndClose()
    }

    private fun goBackAndClose() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home)
        finish()
    }

    override fun onDestroy() {
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        super.onDestroy()
    }
}

class AwareDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {}
    override fun onDisabled(context: Context, intent: Intent) {}
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_ACTIVE, false)) {
                try {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, MonitorService::class.java)
                    )
                } catch (_: Exception) {}
            }
        }
    }
}