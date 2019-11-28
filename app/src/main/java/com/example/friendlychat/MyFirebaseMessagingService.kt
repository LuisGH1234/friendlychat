package com.example.friendlychat

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFMService"
        private const val FRIENDLY_ENGAGE_TOPIC = "friendly_engage"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // handle data payload of FCM message
        Log.d(TAG, "FCM Message Id: ${remoteMessage.messageId}")
        Log.d(TAG, "FCM Notification Message: ${remoteMessage.notification}")
        Log.d(TAG, "FCM Data Message ${remoteMessage.data}")
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM Token: $token")
        // Once a token is generated, we subscribe to topic
        FirebaseMessaging.getInstance().subscribeToTopic(FRIENDLY_ENGAGE_TOPIC)
    }

    /*override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }*/
}
