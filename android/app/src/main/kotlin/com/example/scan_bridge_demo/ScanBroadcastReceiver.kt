package com.example.scan_bridge_demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reçoit les broadcasts du scanner et :
 *  1) stocke le dernier code dans SharedPreferences
 *  2) relaie via un broadcast interne FORWARD_ACTION (capté par MainActivity → EventChannel)
 */
class ScanBroadcastReceiver : BroadcastReceiver() {

  private val FORWARD_ACTION = "com.linnovlab.SCAN_FORWARD"

  override fun onReceive(context: Context, intent: Intent) {
    val (code, length, type, aim) = extract(intent) ?: return

    // 1) stock
    val sp = context.getSharedPreferences("scan_bridge", Context.MODE_PRIVATE)
    sp.edit().putString("last_scan_code", code).apply()

    // 2) relai
    val fwd = Intent(FORWARD_ACTION)
    fwd.putExtra("code", code)
    fwd.putExtra("length", length)
    fwd.putExtra("barcodeType", type)
    fwd.putExtra("aimid", aim)
    context.sendBroadcast(fwd)
  }

  private fun extract(i: Intent): Quadruple<String, Int, String, String>? {
    // Zebra DataWedge
    i.getStringExtra("com.symbol.datawedge.data_string")?.let { s ->
      return Quadruple(s, s.length, i.getStringExtra("com.symbol.datawedge.label_type") ?: "", "")
    }
    // Clés génériques String
    listOf("barcode", "barCode", "data", "text").forEach { k ->
      i.getStringExtra(k)?.let { s -> return Quadruple(s, s.length, i.getStringExtra("barcodeType") ?: "", i.getStringExtra("aimid") ?: "") }
    }
    // Fallback bytes "barocode" + length (constructeurs chinois)
    val raw = i.getByteArrayExtra("barocode")
    val len = i.getIntExtra("length", raw?.size ?: 0)
    if (raw != null && len > 0) {
      val s = try { String(raw, 0, len, Charsets.UTF_8) } catch (_: Throwable) { String(raw, 0, len) }
      return Quadruple(s, len, i.getStringExtra("barcodeType") ?: "", i.getStringExtra("aimid") ?: "")
    }
    return null
  }

  // petit tuple
  data class Quadruple<A,B,C,D>(val first: A, val second: B, val third: C, val fourth: D)
}
