package com.example.pocketbridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.net.NetworkInterface
import java.security.SecureRandom

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var folderText: TextView
    private lateinit var tokenText: TextView
    private var folderUri: Uri? = null
    private val prefs by lazy { getSharedPreferences("pocketbridge", MODE_PRIVATE) }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            folderUri = uri
            prefs.edit().putString("folder_uri", uri.toString()).apply()
            updateFolder()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderUri = prefs.getString("folder_uri", null)?.let(Uri::parse)
        if (prefs.getString("token", null).isNullOrBlank()) prefs.edit().putString("token", newToken()).apply()
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 44, 36, 36) }
        fun text(s: String, size: Float = 16f) = TextView(this).apply { text = s; textSize = size; setPadding(0, 12, 0, 12) }
        layout.addView(text("PocketBridge", 28f))
        layout.addView(text("Remote file access for your own Android device."))
        folderText = text("No folder selected"); layout.addView(folderText)
        layout.addView(Button(this).apply { text = "Choose shared folder"; setOnClickListener { picker.launch(null) } })
        tokenText = text(""); layout.addView(tokenText)
        layout.addView(Button(this).apply { text = "Start file server"; setOnClickListener { startServer() } })
        layout.addView(Button(this).apply { text = "Stop file server"; setOnClickListener {
            stopService(Intent(this@MainActivity, FileServerService::class.java)); status.text = "Server stopped."
        } })
        status = text("Server is stopped."); layout.addView(status)
        layout.addView(text("Use a trusted private VPN such as Tailscale for remote access. Do not expose this HTTP server directly to the public internet. Keep your access token private."))
        setContentView(layout)
        updateFolder()
        tokenText.text = "Access token (keep private):\n${prefs.getString("token", "")}"
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
    }

    private fun updateFolder() {
        val name = folderUri?.let { DocumentFile.fromTreeUri(this, it)?.name }
        folderText.text = if (folderUri == null) "No folder selected" else "Shared folder: ${name ?: "selected"}"
    }

    private fun startServer() {
        val uri = folderUri ?: run { status.text = "Choose a folder first."; return }
        val i = Intent(this, FileServerService::class.java).apply {
            action = FileServerService.ACTION_START
            putExtra(FileServerService.EXTRA_TREE_URI, uri.toString())
            putExtra(FileServerService.EXTRA_TOKEN, prefs.getString("token", ""))
        }
        ContextCompat.startForegroundService(this, i)
        val addresses = try {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { n -> n.inetAddresses.toList() }
                .filter { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }.mapNotNull { it.hostAddress }.distinct()
        } catch (_: Exception) { emptyList() }
        status.text = "Starting on port 8765.\nAddresses: ${addresses.ifEmpty { listOf("Check your VPN address") }.joinToString()}\nAPI: /api/files?path="
    }

    private fun newToken(): String {
        val b = ByteArray(24); SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
}
