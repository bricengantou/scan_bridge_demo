package com.example.scan_bridge_demo

import android.content.*
import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

  private val METHOD = "com.linnovlab/scan_bridge"
  private val EVENTS = "com.linnovlab/scan_bridge/stream"
  private val FORWARD_ACTION = "com.linnovlab.SCAN_FORWARD" // relai interne depuis le receiver statique

  private var sink: EventChannel.EventSink? = null

  private val forwardReceiver = object : BroadcastReceiver() {
    override fun onReceive(c: Context?, i: Intent?) {
      i ?: return
      if (i.action != FORWARD_ACTION) return
      val map = HashMap<String, Any>()
      map["code"] = i.getStringExtra("code") ?: ""
      map["length"] = i.getIntExtra("length", (map["code"] as String).length)
      map["barcodeType"] = i.getStringExtra("barcodeType") ?: ""
      map["aimId"] = i.getStringExtra("aimid") ?: ""
      map["raw"] = (map["code"] as String).toByteArray(Charsets.UTF_8)
      sink?.success(map)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    MethodChannel(flutterEngine!!.dartExecutor.binaryMessenger, METHOD)
      .setMethodCallHandler { call, result ->
        when (call.method) {
          // 1) Soft trigger Zebra (si DataWedge présent et profil configuré)
          "softScanTrigger" -> {
            val ok = trySoftTrigger()
            result.success(ok)
          }
          // 2) Ouvre l’app de scan (candidats connus + fallback aux paramètres)
          "openScannerApp" -> {
            val ok = tryOpenScannerApp()
            result.success(ok)
          }
          // 3) Lit le dernier scan stocké (si arrivé en arrière-plan)
          "readLastScan" -> {
            val sp = getSharedPreferences("scan_bridge", Context.MODE_PRIVATE)
            result.success(sp.getString("last_scan_code", "") ?: "")
          }
          else -> result.notImplemented()
        }
      }

    EventChannel(flutterEngine!!.dartExecutor.binaryMessenger, EVENTS)
      .setStreamHandler(object : EventChannel.StreamHandler {
        override fun onListen(args: Any?, events: EventChannel.EventSink?) {
          sink = events
          val f = IntentFilter(FORWARD_ACTION)
          if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(forwardReceiver, f, Context.RECEIVER_NOT_EXPORTED)
          } else {
            @Suppress("DEPRECATION")
            registerReceiver(forwardReceiver, f)
          }
        }
        override fun onCancel(args: Any?) {
          sink = null
          runCatching { unregisterReceiver(forwardReceiver) }
        }
      })
  }

  /** Envoie la commande Zebra DataWedge SOFT_SCAN_TRIGGER (START_SCANNING) si dispo. */
  private fun trySoftTrigger(): Boolean {
    return try {
      val intent = Intent()
      intent.action = "com.symbol.datawedge.api.ACTION"
      intent.putExtra("com.symbol.datawedge.api.SOFT_SCAN_TRIGGER", "START_SCANNING")
      sendBroadcast(intent)
      true
    } catch (_: Throwable) {
      false
    }
  }

  /** Ouvre l'app de scan : on essaie des packages connus, sinon réglages du scanner. */
  private fun tryOpenScannerApp(): Boolean {
    val pm = packageManager
    val candidates = listOf(
      "com.android.scanner",
      "com.scanner",
      "com.zq.scanner",
      "com.symbol.datawedge",      // Zebra DataWedge (ouvre l’UI de config)
      "com.honeywell.decodeconfig" // Honeywell
    )
    for (pkg in candidates) {
      try {
        val launch = pm.getLaunchIntentForPackage(pkg)
        if (launch != null) {
          startActivity(launch)
          return true
        }
      } catch (_: Throwable) {}
    }
    // Fallback : ouvre les paramètres appli & laisse l’utilisateur lancer l’app de scan
    return try {
      val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
      startActivity(intent)
      true
    } catch (_: Throwable) { false }
  }
}
