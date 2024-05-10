package com.reactnative.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.telephony.SmsManager
import com.facebook.react.bridge.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class SmsModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private var smsCursor: Cursor? = null
    private var smsList: MutableMap<Long, String> = HashMap()
    private var mReactContext: ReactContext = reactContext
    private var cbAutoSendSuccess: Callback? = null
    private var cbAutoSendError: Callback? = null
    private var mActivity: Activity? = null

    override fun getName(): String {
        return "Sms"
    }

    @ReactMethod
    fun list(filter: String?, errorCallback: Callback?, successCallback: Callback?) {
        try {
            val filterJ = JSONObject(filter)
            val uriFilter = filterJ.optString("box", "inbox")
            val fread = filterJ.optInt("read", -1)
            val fid = filterJ.optInt("_id", -1)
            val ftid = filterJ.optInt("thread_id", -1)
            val faddress = filterJ.optString("address")
            val fcontent = filterJ.optString("body")
            val fContentRegex = filterJ.optString("bodyRegex")
            val indexFrom = filterJ.optInt("indexFrom", 0)
            val maxCount = filterJ.optInt("maxCount", -1)
            val selection = filterJ.optString("selection", "")
            val sortOrder = filterJ.optString("sortOrder", null)
            val maxDate = filterJ.optLong("maxDate", -1)
            val minDate = filterJ.optLong("minDate", -1)
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/$uriFilter"), null, selection, null, sortOrder
            )
            var c = 0
            val jsons = JSONArray()
            while (cursor?.moveToNext() == true) {
                var matchFilter = true
                if (fid > -1) matchFilter = fid == cursor.getInt(cursor.getColumnIndex("_id"))
                else if (ftid > -1) matchFilter = ftid == cursor.getInt(cursor.getColumnIndex("thread_id"))
                else if (fread > -1) matchFilter = fread == cursor.getInt(cursor.getColumnIndex("read"))
                else if (!faddress.isNullOrEmpty()) matchFilter =
                    faddress == cursor.getString(cursor.getColumnIndex("address")).trim()
                else if (!fcontent.isNullOrEmpty()) matchFilter =
                    fcontent == cursor.getString(cursor.getColumnIndex("body")).trim()
                if (!fContentRegex.isNullOrEmpty())
                    matchFilter = matchFilter && cursor.getString(cursor.getColumnIndex("body")).matches(fContentRegex.toRegex())
                if (maxDate > -1)
                    matchFilter = matchFilter && maxDate >= cursor.getLong(cursor.getColumnIndex("date"))
                if (minDate > -1)
                    matchFilter = matchFilter && minDate <= cursor.getLong(cursor.getColumnIndex("date"))
                if (matchFilter) {
                    if (c >= indexFrom) {
                        if (maxCount > 0 && c >= indexFrom + maxCount) break
                        val json = getJsonFromCursor(cursor)
                        jsons.put(json)
                    }
                    c++
                }
            }
            cursor?.close()
            try {
                successCallback?.invoke(c, jsons.toString())
            } catch (e: Exception) {
                errorCallback?.invoke(e.message)
            }
        } catch (e: JSONException) {
            errorCallback?.invoke(e.message)
        }
    }

    private fun getJsonFromCursor(cur: Cursor): JSONObject {
        val json = JSONObject()
        val nCol = cur.columnCount
        val keys = cur.columnNames
        try {
            for (j in 0 until nCol) when (cur.getType(j)) {
                Cursor.FIELD_TYPE_NULL -> json.put(keys[j], null)
                Cursor.FIELD_TYPE_INTEGER -> json.put(keys[j], cur.getLong(j))
                Cursor.FIELD_TYPE_FLOAT -> json.put(keys[j], cur.getFloat(j))
                Cursor.FIELD_TYPE_STRING -> json.put(keys[j], cur.getString(j))
                Cursor.FIELD_TYPE_BLOB -> json.put(keys[j], cur.getBlob(j))
            }
        } catch (e: Exception) {
            return JSONObject()
        }
        return json
    }

    @ReactMethod
    fun send(addresses: String?, text: String?, errorCallback: Callback?, successCallback: Callback?) {
        mActivity = currentActivity
        try {
            val jsonObject = JSONObject(addresses)
            val addressList = jsonObject.getJSONArray("addressList")
            val n: Int = addressList.length()
            if (n > 0) {
                val sentIntent = PendingIntent.getBroadcast(mActivity, 0, Intent("SENDING_SMS"), 0)
                val sms = SmsManager.getDefault()
                for (i in 0 until n) {
                    val address: String = addressList.optString(i)
                    if (address.isNotEmpty()) sms.sendTextMessage(address, null, text, sentIntent, null)
                }
            } else {
                val sentIntent = PendingIntent.getActivity(
                    mActivity, 0,
                    Intent("android.intent.action.VIEW"), 0
                )
                val intent = Intent("android.intent.action.VIEW")
                intent.putExtra("sms_body", text)
                intent.data = Uri.parse("sms:")
                try {
                    sentIntent.send(mActivity?.applicationContext, 0, intent)
                    successCallback?.invoke("OK")
                } catch (e: PendingIntent.CanceledException) {
                    errorCallback?.invoke(e.message)
                }
            }
        } catch (e: JSONException) {
            errorCallback?.invoke(e.message)
        }
    }

    @ReactMethod
    fun delete(id: Int?, errorCallback: Callback?, successCallback: Callback?) {
        try {
            val res = context.contentResolver.delete(Uri.parse("content://sms/$id"), null, null)
            if (res > 0) successCallback?.invoke("OK") else errorCallback?.invoke("SMS not found")
        } catch (e: Exception) {
            errorCallback?.invoke(e.message)
        }
    }

    private fun sendCallback(message: String?, success: Boolean) {
        if (success && cbAutoSendSuccess != null) {
            cbAutoSendSuccess!!.invoke(message)
            cbAutoSendSuccess = null
        } else if (!success && cbAutoSendError != null) {
            cbAutoSendError!!.invoke(message)
            cbAutoSendError = null
        }
    }

    @ReactMethod
    fun autoSend(phoneNumber: String?, message: String?, errorCallback: Callback?, successCallback: Callback?) {
        cbAutoSendSuccess = successCallback
        cbAutoSendError = errorCallback
        try {
            val SENT = "SMS_SENT"
            val DELIVERED = "SMS_DELIVERED"
            val sentPendingIntents: MutableList<PendingIntent> = ArrayList()
            val deliveredPendingIntents: MutableList<PendingIntent> = ArrayList()
            val sentPI = PendingIntent.getBroadcast(context, 0, Intent(SENT), 0)
            val deliveredPI = PendingIntent.getBroadcast(context, 0, Intent(DELIVERED), 0)
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(arg0: Context, arg1: Intent) {
                    when (resultCode) {
                        Activity.RESULT_OK -> sendCallback("SMS sent", true)
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> sendCallback("Generic failure", false)
                        SmsManager.RESULT_ERROR_NO_SERVICE -> sendCallback("No service", false)
                        SmsManager.RESULT_ERROR_NULL_PDU -> sendCallback("Null PDU", false)
                        SmsManager.RESULT_ERROR_RADIO_OFF -> sendCallback("Radio off", false)
                    }
                }
            }, IntentFilter(SENT))
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(arg0: Context, arg1: Intent) {
                    when (resultCode) {
                        Activity.RESULT_OK -> sendEvent(mReactContext, "sms_onDelivery", "SMS delivered")
                        Activity.RESULT_CANCELED -> sendEvent(mReactContext, "sms_onDelivery", "SMS not delivered")
                    }
                }
            }, IntentFilter(DELIVERED))
            val sms = SmsManager.getDefault()
            val parts = sms.divideMessage(message)
            for (i in parts.indices) {
                sentPendingIntents.add(i, sentPI)
                deliveredPendingIntents.add(i, deliveredPI)
            }
            sms.sendMultipartTextMessage(phoneNumber, null, parts, sentPendingIntents, deliveredPendingIntents)
            val values = ContentValues()
            values.put("address", phoneNumber)
            values.put("body", message)
            context.contentResolver.insert(Uri.parse("content://sms/sent"), values)
        } catch (e: Exception) {
            sendCallback(e.message, false)
        }
    }

    private fun sendEvent(reactContext: ReactContext, eventName: String, params: String) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }
}
