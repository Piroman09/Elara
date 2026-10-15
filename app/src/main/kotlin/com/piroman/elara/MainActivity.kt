package com.piroman.elara

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.piroman.elara.data.history.PlayHistoryManager
import com.piroman.elara.data.history.WrappedStatsManager
import com.piroman.elara.data.playlist.PlaylistManager
import com.piroman.elara.ui.MainViewModel
import com.piroman.elara.ui.components.MainScaffold
import com.piroman.elara.ui.theme.AuraSonicTheme
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {

    /**
     * The same instance `MainScaffold` obtains with `viewModel()`.
     *
     * Both resolve through this Activity's `ViewModelStore`, so this is not a
     * second ViewModel — it is the same one, reachable from a lifecycle callback.
     * That is the whole point: [onResume] has to be able to reach it.
     */
    private val viewModel: MainViewModel by viewModels()

    /**
     * Keeps Home's rows from going stale while the app sits in the background.
     *
     * The ViewModel outlives backgrounding, so its rows are fetched once and then
     * never again until the process dies — an app left open for days kept showing
     * the same rows. There is no pull-to-refresh, so the user could not ask for
     * newer ones either. This is the trigger that was missing; the decision about
     * whether a fetch is actually worth making lives in
     * [MainViewModel.refreshHomeIfStale], which is a no-op when the rows are
     * fresh and the first batch is still running.
     *
     * Safe on a cold start: the ViewModel is created here if composition has not
     * got to it yet, `init` kicks off the first batch, and the staleness check
     * then declines to fetch a second time.
     */
    override fun onResume() {
        super.onResume()
        viewModel.refreshHomeIfStale()
    }

    /**
     * Two launchers, deliberately — **not** one.
     *
     * `registerForActivityResult` binds a single request code, so launching two
     * different permissions through the same launcher makes the second launch
     * replace the first's pending result, and in practice only one dialog
     * appears. They are chained instead: the audio request is started from the
     * notification callback.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The answer is irrelevant here: the two permissions are independent,
            // so the audio request has to happen either way.
            requestAudioReadPermission()
        }

    private val audioReadPermissionLauncher =
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

        requestNotificationPermission()

        setContent {
            AuraSonicTheme {
                MainScaffold()
            }
        }
    }

    /**
     * Forces any queued persistence write out before the app can be killed.
     *
     * Both stores hand their write to a background thread so that editing a
     * playlist — or simply playing a track — never blocks the UI. The cost is
     * that a change made in the last moments before the process dies can be
     * lost, and "I made a playlist / played a song, closed the app, and it was
     * gone" is exactly that window. `onStop` is the last callback guaranteed to
     * run before the system may kill us, so it is where both writes are forced
     * out.
     *
     * History is flushed here too because `SharedPreferences.apply()` only
     * *queues* its write: the framework normally drains that queue at stop, but
     * a crash, a force-stop or an aggressive OEM task killer can beat it, and
     * the loss is completely silent. `PlayHistoryManager` therefore commits on
     * its own thread, and needs the same flush as the playlist file.
     *
     * Wrapped's month total joins them for the same reason: it is *batched*
     * while music plays (see [WrappedStatsManager.addListeningTime]), so
     * without this the seconds since the last write would be the ones lost to
     * a kill — and they are exactly the ones a user who just closed the app
     * would notice missing from this month's recap.
     *
     * Android solves the identical problem for `apply()` by blocking the main
     * thread in `ActivityThread.handleStopActivity`; this is the same trade, for
     * our own writes. Only the playlist file actually blocks — the other two
     * hand their write to a background thread.
     */
    override fun onStop() {
        super.onStop()
        PlaylistManager.getInstance(this).flushPendingSave()
        PlayHistoryManager.getInstance(this).flushPending()
        WrappedStatsManager.getInstance(this).flush()
    }

    /**
     * Android 13+ needs the runtime notification permission for the media
     * playback notification to be visible. Below that there is nothing to ask
     * for, so fall straight through to the audio permission.
     */
    private fun requestNotificationPermission() {
        val needsNotification =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (needsNotification) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestAudioReadPermission()
        }
    }

    /**
     * Audio-read permission. It gates **only** the best-effort copy of a
     * download into the public `Music/Elara` folder so other apps can see it
     * (`DownloadManager.copyToPublicMusic`) — *not* offline playback, which
     * reads the app-private copy under `filesDir/downloads` and needs no
     * permission at all. That is why a dropped request here degrades a nicety
     * rather than breaking downloads.
     */
    private fun requestAudioReadPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            audioReadPermissionLauncher.launch(permission)
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
