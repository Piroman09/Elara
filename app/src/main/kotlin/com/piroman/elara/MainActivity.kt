package com.aesthetic.spotify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.aesthetic.spotify.ui.components.MainScaffold
import com.aesthetic.spotify.ui.theme.AuraSonicTheme
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pinned to dark styles: the app is dark-only, and the no-arg
        // enableEdgeToEdge() would otherwise pick the icon tint from the system
        // theme — giving dark icons on our near-black background on any device
        // whose system is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        installCrashLogger()

        // Android 13+ requires the runtime notification permission for the
        // media playback notification to be visible.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Audio-read permission so downloaded files (public Music/Elara) can
        // be played back offline.
        val audioReadPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, audioReadPermission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(audioReadPermission)
        }

        setContent {
            AuraSonicTheme {
                MainScaffold()
            }
        }
    }

    /**
     * Writes any uncaught crash to Android/data/.../files/crash_log.txt so
     * failures on devices without adb can be reported back to us.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val entry = buildString {
                    append("\n==== CRASH ")
                    append(System.currentTimeMillis())
                    append(" (thread ")
                    append(thread.name)
                    append(") ====\n")
                    append(sw.toString())
                }
                val dir = getExternalFilesDir(null) ?: filesDir
                File(dir, "crash_log.txt").appendText(entry)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
