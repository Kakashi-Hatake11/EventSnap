package com.appybits.eventsnap

import androidx.core.content.edit
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.appybits.eventsnap.ui.navigation.NavGraph
import com.appybits.eventsnap.ui.theme.EventSnapTheme
import dagger.hilt.android.AndroidEntryPoint
import com.google.firebase.messaging.FirebaseMessaging

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        com.appybits.eventsnap.utils.NotificationHelper.createNotificationChannel(this)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
//        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
//            if (task.isSuccessful) {
//                android.util.Log.d("FCM_TOKEN", "My token is: ${task.result}")
//            }
//        }

        val sharedPrefs = getSharedPreferences("event_snap_prefs", MODE_PRIVATE)
        val initialRole = sharedPrefs.getString("user_role", null)
        val initialEventId = sharedPrefs.getString("event_id", null)
        val initialUserId = sharedPrefs.getString("user_id", null)
        
        setContent {
            EventSnapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(
                        initialRole = initialRole,
                        initialEventId = initialEventId,
                        initialUserId = initialUserId,
                        onRoleSelected = { role, eventId, userId ->
                            sharedPrefs.edit { 
                                putString("user_role", role)
                                putString("event_id", eventId)
                                putString("user_id", userId)
                            }
                        }
                    )
                }
            }
        }
    }
}
