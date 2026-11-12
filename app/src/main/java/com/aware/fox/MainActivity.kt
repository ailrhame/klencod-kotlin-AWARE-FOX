package com.aware.fox

import android.app.Activity
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.DevicePolicyManager
import android.app.admin.DeviceAdminInfo
import android.app.admin.DevicePolicyManager.ADMIN_DISABLED_REASON_NONE
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val ADMIN_RECEIVER_REQUEST_CODE = 1001
        private const val OVERLAY_REQUEST_CODE = 1002
        private const val USAGE_STATS_REQUEST_CODE = 1003
        private const val FOREGROUND_SERVICE_ID = 1001
    }

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName
    private var isDeviceAdmin = false
    private var promiseDateInMillis: Long = 0
    private var activeAppPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupProgrammaticUI()
        
        componentName = ComponentName(this, FoxAdminReceiver::class.java)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        
        checkAdminStatus()
        startMonitoringService()
    }

    private fun setupProgrammaticUI() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212")) // Dark theme
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(30, 50, 30, 50)
        }

        val logoIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_lock_lock)
            layoutParams = LinearLayout.LayoutParams(150, 150).apply {
                setMargins(0, 0, 0, 20)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        val titleText = TextView(this).apply {
            text = "AWARE FOX"
            textSize = 32f
            setTextColor(Color.parseColor("#FFD700")) // Gold color
            gravity = Gravity.CENTER
            textStyle = android.graphics.Typeface.BOLD
            setPadding(0, 0, 0, 10)
        }

        val subtitleText = TextView(this).apply {
            text = "نظام الحماية الذكي"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        val btnSetup = Button(this).apply {
            text = "تفعيل المشرف وحظر التطبيقات"
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E53935"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200
            ).apply { setMargins(0, 0, 0, 15) }
            setOnClickListener { initiateAdminSetup() }
        }

        val btnCheck = Button(this).apply {
            text = "فحص حالة الإدمان"
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2196F3"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                150
            ).apply { setMargins(0, 0, 0, 15) }
            setOnClickListener { showPromisDialog() }
        }

        val rightsText = TextView(this).apply {
            text = "© 2026 AWARE FOX - جميع الحقوق محفوظة للمشرف الرقمي"
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(0, 50, 0, 0)
        }

        rootLayout.addView(logoIcon)
        rootLayout.addView(titleText)
        rootLayout.addView(subtitleText)
        rootLayout.addView(btnSetup)
        rootLayout.addView(btnCheck)
        rootLayout.addView(rightsText)

        setContentView(rootLayout)
    }

    private fun checkAdminStatus() {
        if (devicePolicyManager.isAdminActive(componentName)) {
            isDeviceAdmin = true
            Toast.makeText(this, "تم تفعيل المشرف بنجاح", Toast.LENGTH_SHORT).show()
        } else {
            isDeviceAdmin = false
        }
    }

    private fun initiateAdminSetup() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "لحظر التطبيقات وإدارة الشاشة بشكل مطلق")
        }
        startActivityForResult(intent, ADMIN_RECEIVER_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ADMIN_RECEIVER_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    isDeviceAdmin = true
                    Toast.makeText(this, "أنت الآن مشرف النظام", Toast.LENGTH_LONG).show()
                    activateFullProtection()
                } else {
                    Toast.makeText(this, "تم إلغاء التفعيل", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun activateFullProtection() {
        // Disable Chrome and other common addiction apps via Device Policy
        val packagesToDisable = listOf("com.android.chrome", "com.google.android.youtube", "com.netflix.mediaclient")
        for (pkg in packagesToDisable) {
            try {
                devicePolicyManager.setApplicationHidden(componentName, pkg, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Start the overlay service to monitor usage
        startForegroundService(Intent(this, AwareService::class.java))
    }

    private fun showPromisDialog() {
        // Simple programmatic dialog using AlertDialog builder logic manually or standard one
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("حدد تاريخ الوعد")
            .setMessage("سيتم قفل التطبيقات المختارة حتى هذا التاريخ.")
            .setPositiveButton("تحديد") { _, _ ->
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) } // Default +1 day
                promiseDateInMillis = cal.timeInMillis
                showToastAndStartService(cal.timeInMillis)
            }
            .setNegativeButton("إلغاء", null)
            .create()
        
        dialog.show()
    }

    private fun showToastAndStartService(dateMillis: Long) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val formattedDate = sdf.format(dateMillis)
        
        // Show a persistent toast-like view or notification
        val notificationView = TextView(this).apply {
            text = "الأمد المحدد: $formattedDate\nالتطبيقات محظورة حتى هذا التاريخ."
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            textSize = 16f
            padding = 20
            gravity = Gravity.CENTER
        }
        
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        windowManager.addView(notificationView, params)
        
        Handler(Looper.getMainLooper()).postDelayed({
            windowManager.removeView(notificationView)
        }, 10000)
        
        startMonitoringService(dateMillis)
    }

    private fun startMonitoringService(endDateMillis: Long = 0) {
        val intent = Intent(this, AwareService::class.java).apply {
            putExtra("END_DATE", endDateMillis)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // Inner Classes
    
    class FoxAdminReceiver : android.app.admin.DeviceAdminReceiver() {
        override fun onEnabled(context: Context?, intent: Intent?) {
            super.onEnabled(context, intent)
            Toast.makeText(context, "تم تفعيل المشرف", Toast.LENGTH_SHORT).show()
        }
        
        override fun onDisabled(context: Context?, intent: Intent?) {
            super.onDisabled(context, intent)
            Toast.makeText(context, "تم إلغاء تفعيل المشرف", Toast.LENGTH_SHORT).show()
        }
    }

    class AwareService : android.app.Service() {
        
        private var handler = Handler(Looper.getMainLooper())
        private var runnable: Runnable? = null
        private var endDateMillis: Long = 0

        override fun onCreate() {
            super.onCreate()
            
            val notificationIntent = Intent(this, FoxActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, notificationIntent, 
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notificationBuilder = android.app.NotificationCompat.Builder(this, "aware_fox_channel")
                .setContentTitle("AWARE FOX نشط")
                .setContentText("جاري مراقبة إدمانك...")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_revert, "إعادة التعيين", pendingIntent)

            startForeground(FOREGROUND_SERVICE_ID, notificationBuilder.build())

            // Logic to monitor active app
            runnable = object : Runnable {
                override fun run() {
                    checkAppUsage()
                    handler.postDelayed(this, 5000) // Check every 5 seconds
                }
            }
            handler.post(runnable!!)
        }

        private fun checkAppUsage() {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 10000 // Last 10 seconds
            
            val queryUsageStats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_BEST, 
                startTime, endTime
            )
            
            if (queryUsageStats.isNotEmpty()) {
                // Find the most used package in that interval
                val sortedList = LinkedHashMap<String, Long>()
                for (usageStats in queryUsageStats) {
                    val pkg = usageStats.packageName
                    val lastTime = usageStats.lastTimeUsed
                    if (sortedList.containsKey(pkg)) {
                        sortedList[pkg] = sortedList[pkg]!! + lastTime
                    } else {
                        sortedList[pkg] = lastTime
                    }
                }
                
                val topPackage = sortedList.entries.maxByOrNull { it.value }?.key
                if (topPackage != null && topPackage != packageName) {
                    // If user opened a forbidden app, block it
                    if (endDateMillis > 0 && System.currentTimeMillis() < endDateMillis) {
                        blockApp(topPackage)
                    }
                }
            }
        }

        private fun blockApp(packageName: String) {
            try {
                // Try to hide/disable the app temporarily if we have admin rights
                // Or just kill the task
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.killBackgroundProcesses(packageName)
                
                // Show an overlay blocking it
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val textView = TextView(this).apply {
                    text = "🚫 تم الحظر!\n$packageName"
                    setBackgroundColor(Color.RED)
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    gravity = Gravity.CENTER
                }
                
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.graphics.PixelFormat.TRANSPARENT
                )
                
                wm.addView(textView, params)
                
                // Remove overlay after 3 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        wm.removeView(textView)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, 3000)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            endDateMillis = intent?.getLongExtra("END_DATE", 0) ?: 0
            return START_STICKY
        }

        override fun onBind(intent: Intent?) = null
    }
    
    class FoxActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            finish()
        }
    }
}