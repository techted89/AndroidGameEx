package com.techted89.gameex

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProcessSelectorActivity : AppCompatActivity() {

    private var allProcesses: List<ProcessInfo> = emptyList()

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Handle result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_process_selector)

        checkOverlayPermission()

        val recycler = findViewById<RecyclerView>(R.id.recycler_processes)
        recycler.layoutManager = LinearLayoutManager(this)

        val etSearch = findViewById<EditText>(R.id.et_search_process)
        val spinnerFilter = findViewById<Spinner>(R.id.spinner_filter)
        val fabRefresh = findViewById<FloatingActionButton>(R.id.fab_refresh)

        // Setup Search Listener
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterProcesses(recycler)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup Spinner Listener
        // Using built-in simple spinner item for basic theming compatibility
        // The array is loaded from XML via android:entries, but we need the listener
        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterProcesses(recycler)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        fabRefresh.setOnClickListener {
            loadProcesses(recycler)
        }

        loadProcesses(recycler)
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            // [LEGACY/UNUSED] startActivityForResult(intent, 0)
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun loadProcesses(recycler: RecyclerView) {
        CoroutineScope(Dispatchers.IO).launch {
            // Need root to see all processes ideally, but basic ps might work for now
            // Or requesting root first
            RootUtils.requestRoot()
            // Pass context to enrich process info
            val processes = RootUtils.getRunningProcesses(this@ProcessSelectorActivity)

            withContext(Dispatchers.Main) {
                allProcesses = processes
                filterProcesses(recycler)
            }
        }
    }

    private fun filterProcesses(recycler: RecyclerView) {
        val etSearch = findViewById<EditText>(R.id.et_search_process)
        val spinnerFilter = findViewById<Spinner>(R.id.spinner_filter)

        val query = etSearch.text.toString().trim()
        val filterType = spinnerFilter.selectedItem?.toString() ?: "All"

        val filtered = allProcesses.filter { process ->
            val matchesName = process.appName.contains(query, ignoreCase = true) ||
                              process.processName.contains(query, ignoreCase = true)

            val matchesType = when (filterType) {
                "System Apps" -> process.isSystemApp
                "User Apps" -> !process.isSystemApp
                "Hooks" -> false // Placeholder filter for requested feature
                else -> true
            }
            matchesName && matchesType
        }

        // Sorting: User apps first, then alphabetical by App Name
        val sorted = filtered.sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))

        recycler.adapter = ProcessAdapter(sorted) { process ->
            launchOverlayService(process)
        }
    }

    private fun launchOverlayService(process: ProcessInfo) {
        val intent = Intent(this, FloatingOverlayService::class.java)
        intent.putExtra("PID", process.pid)
        // Pass package name/process name as PNAME
        intent.putExtra("PNAME", process.processName)
        // Pass app name as APP_NAME
        intent.putExtra("APP_NAME", process.appName)
        startService(intent)
        // Optionally finish() or minimize app
        moveTaskToBack(true)
    }
}
