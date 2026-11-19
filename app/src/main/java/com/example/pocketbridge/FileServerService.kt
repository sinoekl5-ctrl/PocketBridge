package com.example.pocketbridge

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class FileServerService : Service() {
    companion object {
        const val ACTION_START = "com.example.pocketbridge.START"
        const val EXTRA_TREE_URI = "tree_uri"
        const val EXTRA_TOKEN = "token"
        private const val CHANNEL = "pocketbridge_server"
    }
    private var server: PocketBridgeHttpServer? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "PocketBridge server", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) { stopSelf(); return START_NOT_STICKY }
        val tree = intent.getStringExtra(EXTRA_TREE_URI)
        val token = intent.getStringExtra(EXTRA_TOKEN)
        if (tree.isNullOrBlank() || token.isNullOrBlank()) { stopSelf(); return START_NOT_STICKY }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("PocketBridge is active").setContentText("File server running on port 8765")
            .setSmallIcon(android.R.drawable.ic_menu_upload).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(7, notification)
        try {
            server?.stop()
            server = PocketBridgeHttpServer(this, Uri.parse(tree), token).also { it.start() }
        } catch (_: Exception) { stopSelf() }
        return START_STICKY
    }

    override fun onDestroy() { server?.stop(); server = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
