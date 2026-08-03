package org.abap2ui5.mobileshell

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM entry point (Phase 2). Only active when the app is built with a
 * google-services.json (see app/build.gradle.kts — the plugin is applied
 * conditionally so the shell still builds without Firebase configured).
 *
 * Flow: FCM token -> stored locally + registered with SAP Mobile Services
 * (MobileServicesPush) -> ABAP backend pushes via the MS backend REST API
 * (see /abap/zcl_test_mobile_push.clas.abap) -> notification tap deep-links
 * into the shell with the target abap2UI5 URL.
 */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        storeToken(this, token)
        MobileServicesPush.registerAsync(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["alert"] ?: ""
        // Convention: data key "url" carries the full abap2UI5 deep link,
        // e.g. https://host/sap/bc/z2ui5?sap-client=100&app=zcl_my_app
        val deepLink = message.data["url"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            deepLink?.let { putExtra(EXTRA_DEEPLINK_URL, it) }
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build())
    }

    companion object {
        const val EXTRA_DEEPLINK_URL = "deeplink_url"
        private const val CHANNEL_ID = "abap2ui5"
        private const val PREFS = "shell_prefs"
        private const val KEY_TOKEN = "push_token"

        fun storedToken(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TOKEN, null)

        fun storeToken(context: Context, token: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TOKEN, token).apply()
        }
    }
}
