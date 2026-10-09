package com.bubble.vpn

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var powerView: PowerView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#080D1A")
        window.navigationBarColor = Color.parseColor("#080D1A")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#080D1A"))
            val pad = dp(32)
            setPadding(pad, pad, pad, pad)
        }

        val titleText = TextView(this).apply {
            text = "BubbleVPN"
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        powerView = PowerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(200), dp(200)).apply {
                topMargin = dp(48)
                bottomMargin = dp(36)
            }
            setOnClickListener {
                toggleConnection()
            }
        }

        // Simple, clean status text: only "Connected" or "Disconnected"
        // No IP, no URL, no file browser prompts, zero clutter
        statusText = TextView(this).apply {
            text = "Disconnected"
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }

        root.addView(titleText)
        root.addView(powerView)
        root.addView(statusText)

        setContentView(root)
        checkPermissions()
        syncUiState(FtpService.isRunning)
    }

    override fun onResume() {
        super.onResume()
        syncUiState(FtpService.isRunning)
    }

    private fun toggleConnection() {
        if (!hasStoragePermission()) {
            checkPermissions()
            return
        }

        if (FtpService.isRunning) {
            stopService(Intent(this, FtpService::class.java))
            syncUiState(false)
        } else {
            val intent = Intent(this, FtpService::class.java)
            startService(intent)
            syncUiState(true)
        }
    }

    private fun syncUiState(connected: Boolean) {
        powerView.isConnected = connected
        if (connected) {
            statusText.text = "Connected"
            statusText.setTextColor(Color.parseColor("#10B981"))
        } else {
            statusText.text = "Disconnected"
            statusText.setTextColor(Color.parseColor("#64748B"))
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            val perms = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            requestPermissions(perms, 100)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
