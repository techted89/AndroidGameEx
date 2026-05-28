package com.techted89.gameex

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.RadioGroup
import android.view.inputmethod.EditorInfo
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import java.io.File
import com.techted89.gameex.utils.ProcessUtils
import com.techted89.gameex.scripting.GameGuardianAPI

class FloatingOverlayService : Service() {

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var iconView: View
    private lateinit var dashboardView: View

    // Layout Params storage
    private lateinit var iconParams: WindowManager.LayoutParams
    private lateinit var dashboardParams: WindowManager.LayoutParams

    private var targetPid: Int = -1
    private var targetAppName: String = "Unknown"
    private var isDashboardVisible = false
    private var isFuzzyMode = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        targetPid = intent?.getIntExtra("PID", -1) ?: -1
        targetAppName = intent?.getStringExtra("APP_NAME") ?: "Unknown"
        GameGuardianAPI.setTargetPid(targetPid)

        createNotificationChannel()

        // Ensure we run as Foreground to prevent killing
        startForeground(1, NotificationCompat.Builder(this, "overlay_channel")
            .setContentTitle("Memory Editor Active")
            .setContentText("Attached to $targetAppName (PID: $targetPid)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build())

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "overlay_channel",
                "Overlay Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        GameGuardianAPI.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 1. Inflate Views
        val inflater = LayoutInflater.from(this)
        iconView = inflater.inflate(R.layout.overlay_icon, null)
        dashboardView = inflater.inflate(R.layout.overlay_dashboard, null)

        // 2. Initialize Layout Params (Icon State)
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        iconParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        // Initial position
        iconParams.gravity = Gravity.TOP or Gravity.START
        iconParams.x = 0
        iconParams.y = 100

        // 3. Initialize Dashboard Params
        dashboardParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            dimAmount = 0.5f
            gravity = Gravity.CENTER
        }

        // 4. Setup Listeners
        setupTouchDrag()
        setupDashboardLogic()

        // 5. Add Icon initially
        windowManager.addView(iconView, iconParams)
    }

    private fun setupTouchDrag() {
        iconView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = iconParams.x
                        initialY = iconParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        iconParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        iconParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(iconView, iconParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = (event.rawX - initialTouchX).toInt()
                        val diffY = (event.rawY - initialTouchY).toInt()

                        if (abs(diffX) < 10 && abs(diffY) < 10) {
                            showDashboard()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun setupDashboardLogic() {
        updateTargetInfo()

        val btnMinimize = dashboardView.findViewById<ImageButton>(R.id.btn_minimize)
        val btnPauseGame = dashboardView.findViewById<ImageButton>(R.id.btn_pause_game)
        val btnStealth = dashboardView.findViewById<ImageButton>(R.id.btn_stealth)

        // Tab Buttons
        val tabScan = dashboardView.findViewById<Button>(R.id.tab_scan)
        val tabResults = dashboardView.findViewById<Button>(R.id.tab_results)
        val tabEditor = dashboardView.findViewById<Button>(R.id.tab_editor)
        val tabScript = dashboardView.findViewById<Button>(R.id.tab_script)

        // Mode Views
        val viewScan = dashboardView.findViewById<View>(R.id.view_scan)
        val viewResults = dashboardView.findViewById<View>(R.id.view_results)
        val viewEditor = dashboardView.findViewById<View>(R.id.view_editor)
        val viewScript = dashboardView.findViewById<View>(R.id.view_script)

        // Scan Mode Elements
        val btnScan = viewScan.findViewById<Button>(R.id.btn_scan)
        val btnNextScan = viewScan.findViewById<Button>(R.id.btn_next_scan)
        val etSearchValue = viewScan.findViewById<EditText>(R.id.et_search_value)
        val tilSearchValue = viewScan.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_search_value)
        val progressScan = viewScan.findViewById<View>(R.id.progress_scan)
        val chipGroupType = viewScan.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_group_type)
        val rgSearchMode = viewScan.findViewById<RadioGroup>(R.id.rg_search_mode)
        val layoutFuzzyControls = viewScan.findViewById<View>(R.id.layout_fuzzy_controls)

        // Fuzzy Buttons
        val btnFuzzyChanged = viewScan.findViewById<Button>(R.id.btn_fuzzy_changed)
        val btnFuzzyUnchanged = viewScan.findViewById<Button>(R.id.btn_fuzzy_unchanged)
        val btnFuzzyIncreased = viewScan.findViewById<Button>(R.id.btn_fuzzy_increased)
        val btnFuzzyDecreased = viewScan.findViewById<Button>(R.id.btn_fuzzy_decreased)

        // Speed Hack
        val toggleSpeed = dashboardView.findViewById<android.widget.ToggleButton>(R.id.toggle_speed)
        toggleSpeed.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(this, "Speed Hack: ${if (isChecked) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }

        // Results Mode Elements
        val rvResults = viewResults.findViewById<RecyclerView>(R.id.rv_results)
        val layoutEmptyState = viewResults.findViewById<View>(R.id.layout_empty_state)

        rvResults.layoutManager = LinearLayoutManager(this)
        val adapter = MemoryResultAdapter()
        rvResults.adapter = adapter

        // Editor/Script Mode setup omitted for brevity...
        val rvModulesList = viewEditor.findViewById<RecyclerView>(R.id.rv_modules_list)
        rvModulesList.layoutManager = LinearLayoutManager(this)
        // [LEGACY/UNUSED] val etScriptInput = viewScript.findViewById<EditText>(R.id.et_script_input)
        // [LEGACY/UNUSED] val tvScriptOutput = viewScript.findViewById<android.widget.TextView>(R.id.tv_script_output)

        // Initial State
        layoutEmptyState.visibility = View.VISIBLE
        rvResults.visibility = View.GONE

        // Tab Switching Logic
        fun switchTab(mode: String) {
            tabScan.setBackgroundResource(0)
            tabResults.setBackgroundResource(0)
            tabEditor.setBackgroundResource(0)
            tabScript.setBackgroundResource(0)
            val whiteColor = ContextCompat.getColor(this@FloatingOverlayService, android.R.color.white)
            val hackerGreenColor = ContextCompat.getColor(this@FloatingOverlayService, R.color.primary_hacker_green)

            tabScan.setTextColor(whiteColor)
            tabResults.setTextColor(whiteColor)
            tabEditor.setTextColor(whiteColor)
            tabScript.setTextColor(whiteColor)

            viewScan.visibility = View.GONE
            viewResults.visibility = View.GONE
            viewEditor.visibility = View.GONE
            viewScript.visibility = View.GONE

            when(mode) {
                "SCAN" -> {
                    tabScan.setBackgroundResource(R.drawable.tab_indicator_active)
                    tabScan.setTextColor(hackerGreenColor)
                    viewScan.visibility = View.VISIBLE
                }
                "RESULTS" -> {
                    tabResults.setBackgroundResource(R.drawable.tab_indicator_active)
                    tabResults.setTextColor(hackerGreenColor)
                    viewResults.visibility = View.VISIBLE
                }
                "EDITOR" -> {
                    tabEditor.setBackgroundResource(R.drawable.tab_indicator_active)
                    tabEditor.setTextColor(hackerGreenColor)
                    viewEditor.visibility = View.VISIBLE
                    // Refresh modules list logic...
                }
                "SCRIPT" -> {
                    tabScript.setBackgroundResource(R.drawable.tab_indicator_active)
                    tabScript.setTextColor(hackerGreenColor)
                    viewScript.visibility = View.VISIBLE
                }
            }
        }

        tabScan.setOnClickListener { switchTab("SCAN") }
        tabResults.setOnClickListener { switchTab("RESULTS") }
        tabEditor.setOnClickListener { switchTab("EDITOR") }
        tabScript.setOnClickListener { switchTab("SCRIPT") }

        btnMinimize.setOnClickListener { showIcon() }
        btnStealth.setOnClickListener { showStealthDialog() }
        btnPauseGame.setOnClickListener {
            // Pause logic...
        }

        // Search Mode Toggle
        rgSearchMode.setOnCheckedChangeListener { _, checkedId ->
            isFuzzyMode = checkedId == R.id.rb_fuzzy
            if (isFuzzyMode) {
                tilSearchValue.visibility = View.GONE
                chipGroupType.visibility = View.GONE // Fuzzy usually implies unknown type, or float/dword. We'll hide for simplicity.
                layoutFuzzyControls.visibility = View.GONE // Hidden until scan starts
                btnScan.text = "Start Fuzzy Scan"
                btnNextScan.visibility = View.GONE
            } else {
                tilSearchValue.visibility = View.VISIBLE
                chipGroupType.visibility = View.VISIBLE
                layoutFuzzyControls.visibility = View.GONE
                btnScan.text = "New Scan"
                btnNextScan.visibility = View.GONE // Reset state
            }
        }

        fun executeFuzzyFilter(mode: Int) {
            progressScan.visibility = View.VISIBLE
            serviceScope.launch(Dispatchers.IO) {
                val count = NativeScanner.filterFuzzy(targetPid, mode)
                val addresses = NativeScanner.getResults(100)

                withContext(Dispatchers.Main) {
                    progressScan.visibility = View.GONE
                    Toast.makeText(this@FloatingOverlayService, "Found: $count", Toast.LENGTH_SHORT).show()

                    val results = addresses.map { addr ->
                        val bytes = NativeScanner.readMemory(targetPid, addr, 4) // Default 4 bytes
                        val hexVal = bytes.joinToString("") { "%02X".format(it) }
                        MemoryResult(addr, "$hexVal (Fuzzy)")
                    }
                    adapter.updateData(results)
                    switchTab("RESULTS")
                }
            }
        }

        btnFuzzyChanged.setOnClickListener { executeFuzzyFilter(NativeScanner.FUZZY_CHANGED) }
        btnFuzzyUnchanged.setOnClickListener { executeFuzzyFilter(NativeScanner.FUZZY_UNCHANGED) }
        btnFuzzyIncreased.setOnClickListener { executeFuzzyFilter(NativeScanner.FUZZY_INCREASED) }
        btnFuzzyDecreased.setOnClickListener { executeFuzzyFilter(NativeScanner.FUZZY_DECREASED) }

        fun performScan(isNext: Boolean = false) {
            if (isFuzzyMode) {
                // Start Fuzzy Scan
                progressScan.visibility = View.VISIBLE
                serviceScope.launch(Dispatchers.IO) {
                    NativeScanner.startFuzzyScan(targetPid)
                    withContext(Dispatchers.Main) {
                        progressScan.visibility = View.GONE
                        Toast.makeText(this@FloatingOverlayService, "Fuzzy Scan Started. Change value in game.", Toast.LENGTH_SHORT).show()
                        btnScan.visibility = View.GONE
                        layoutFuzzyControls.visibility = View.VISIBLE
                    }
                }
                return
            }

            val valueStr = etSearchValue.text.toString()
            if (valueStr.isEmpty()) {
                etSearchValue.error = "Enter a value"
                return
            }

            val selectedType = when (chipGroupType.checkedChipId) {
                R.id.chip_type_float -> NativeScanner.TYPE_FLOAT
                R.id.chip_type_double -> NativeScanner.TYPE_DOUBLE
                R.id.chip_type_byte -> NativeScanner.TYPE_BYTE
                else -> NativeScanner.TYPE_DWORD
            }

            progressScan.visibility = View.VISIBLE
            btnScan.isEnabled = false
            btnNextScan.isEnabled = false

            serviceScope.launch(Dispatchers.IO) {
                var scanError: String? = null
                val results = try {
                    if (isNext) {
                        NativeScanner.filterMemory(targetPid, valueStr, selectedType)
                    } else {
                        NativeScanner.searchMemory(targetPid, valueStr, selectedType)
                    }

                    val addresses = NativeScanner.getResults(100)
                    val readSize = when(selectedType) {
                        NativeScanner.TYPE_DOUBLE, NativeScanner.TYPE_QWORD -> 8
                        NativeScanner.TYPE_BYTE -> 1
                        NativeScanner.TYPE_WORD -> 2
                        else -> 4
                    }

                    addresses.map { addr ->
                        val bytes = NativeScanner.readMemory(targetPid, addr, readSize)
                        val hexVal = bytes.joinToString("") { "%02X".format(it) }
                        MemoryResult(addr, "$hexVal")
                    }
                } catch (e: Exception) {
                    scanError = e.message
                    emptyList()
                }

                withContext(Dispatchers.Main) {
                    if (scanError != null) Toast.makeText(this@FloatingOverlayService, "Error: $scanError", Toast.LENGTH_SHORT).show()
                    adapter.updateData(results)
                    progressScan.visibility = View.GONE
                    btnScan.isEnabled = true
                    btnNextScan.isEnabled = true
                    switchTab("RESULTS")

                    if (results.isNotEmpty()) {
                        btnNextScan.visibility = View.VISIBLE
                    }
                }
            }
        }

        btnScan.setOnClickListener {
            btnNextScan.visibility = View.GONE
            performScan(isNext = false)
        }

        btnNextScan.setOnClickListener {
            performScan(isNext = true)
        }

        etSearchValue.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performScan()
                true
            } else {
                false
            }
        }
    }

    private fun updateTargetInfo() {
        if (!::dashboardView.isInitialized) return
        val tvInfo = dashboardView.findViewById<TextView>(R.id.tv_target_info)
        tvInfo.text = if (targetPid != -1) "Target: $targetAppName (PID: $targetPid)" else "Target: None"
    }

    private fun showDashboard() {
        if (isDashboardVisible) return
        updateTargetInfo()
        windowManager.removeView(iconView)
        windowManager.addView(dashboardView, dashboardParams)
        isDashboardVisible = true
    }

    private fun showIcon() {
        if (!isDashboardVisible) return
        windowManager.removeView(dashboardView)
        windowManager.addView(iconView, iconParams)
        isDashboardVisible = false
    }

    private fun showStealthDialog() {
        // ... existing implementation
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (isDashboardVisible) windowManager.removeView(dashboardView)
        else windowManager.removeView(iconView)
    }
}
