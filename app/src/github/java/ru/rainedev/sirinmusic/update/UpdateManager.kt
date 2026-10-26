package ru.rainedev.sirinmusic.update

import ru.rainedev.sirinmusic.strings
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import ru.rainedev.sirinmusic.BuildConfig
import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.UpdatesActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal data class UpdateState(
    val checking: Boolean = false, val downloading: Boolean = false,
    val progress: Float = 0f, val available: AvailableUpdate? = null,
    val ready: File? = null, val error: String? = null,
    val checkedAt: Long = 0, val automatic: Boolean = false, val onLaunch: Boolean = false,
)

internal class UpdateManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("sirin_updates", Context.MODE_PRIVATE)
    private val api = GitHubUpdates()
    private val mutex = Mutex()
    private val startup = StartupCheckGate()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val folder get() = File(context.cacheDir, "updates")
    private val restored = runCatching { Json.decodeFromString<AvailableUpdate>(prefs.getString("available", "")!!) }.getOrNull()
        ?.takeIf { ReleaseVersion.parse(it.version)?.let { v -> ReleaseVersion.parse(BuildConfig.VERSION_NAME)?.let { v > it } } == true }
    private val _state = MutableStateFlow(UpdateState(available = restored, checkedAt = prefs.getLong("checked_at", 0),
        automatic = prefs.getBoolean("automatic", false), onLaunch = prefs.getBoolean("on_launch", false)))
    val state = _state.asStateFlow()

    fun checkOnLaunch() {
        if (startup.take(_state.value.onLaunch)) scope.launch { check(notify = true) }
    }
    fun setOnLaunch(enabled: Boolean) {
        prefs.edit().putBoolean("on_launch", enabled).apply()
        _state.update { it.copy(onLaunch = enabled) }
    }
    fun schedule() {
        val work = WorkManager.getInstance(context)
        if (_state.value.automatic) work.enqueueUniquePeriodicWork("sirin-update-check", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        else work.cancelUniqueWork("sirin-update-check")
    }
    fun setAutomatic(enabled: Boolean) {
        prefs.edit().putBoolean("automatic", enabled).apply()
        _state.update { it.copy(automatic = enabled) }; schedule()
    }

    suspend fun check(notify: Boolean = false): Boolean = mutex.withLock {
        _state.update { it.copy(checking = true, error = null) }
        try {
            val update = api.latest(BuildConfig.VERSION_NAME)
            val now = System.currentTimeMillis()
            prefs.edit().putLong("checked_at", now).apply {
                if (update == null) remove("available") else putString("available", Json.encodeToString(update))
            }.apply()
            if (_state.value.available != update) withContext(Dispatchers.IO) { folder.deleteRecursively() }
            _state.update { it.copy(available = update, checkedAt = now, ready = if (it.available == update) it.ready else null) }
            if (notify) notifyAvailable()
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.update { it.copy(error = e.message ?: strings(R.string.update_manager_1)) }; false }
        finally { _state.update { it.copy(checking = false) } }
    }

    fun notifyAvailable() {
        val update = _state.value.available ?: return
        if (prefs.getString("notified", null) == update.version) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel("app_updates", strings(R.string.update_manager_2), NotificationManager.IMPORTANCE_DEFAULT))
        val intent = PendingIntent.getActivity(context, 152, Intent(context, UpdatesActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "app_updates")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(strings(R.string.update_manager_3, update.version))
            .setContentText(strings(R.string.update_manager_4))
            .setContentIntent(intent).setAutoCancel(true).build()
        manager.notify(152, notification)
        prefs.edit().putString("notified", update.version).apply()
    }

    suspend fun download(): File? = mutex.withLock {
        val update = _state.value.available ?: return@withLock null
        _state.update { it.copy(downloading = true, progress = 0f, error = null, ready = null) }
        val partial = File(folder, "update.part")
        val target = File(folder, "update.apk")
        try {
            withContext(Dispatchers.IO) { folder.mkdirs(); target.delete() }
            api.download(update, partial) { p -> _state.update { it.copy(progress = p) } }
            withContext(Dispatchers.IO) {
                validateApk(partial, update)
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            _state.update { it.copy(ready = target) }
            target
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.update { it.copy(error = e.message ?: strings(R.string.update_manager_5)) }; null }
        finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { partial.delete() }
            _state.update { it.copy(downloading = false) }
        }
    }

    @Suppress("DEPRECATION")
    private fun validateApk(file: File, update: AvailableUpdate) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: error(strings(R.string.update_manager_6))
        val installed = pm.getPackageInfo(context.packageName, flags)
        require(archive.packageName == context.packageName) { strings(R.string.update_manager_7) }
        require(archive.versionName == update.version) { strings(R.string.update_manager_8) }
        val remoteCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        val currentCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        require(remoteCode >= currentCode) { strings(R.string.update_manager_9) }
        require(archive.applicationInfo?.minSdkVersion?.let { it <= Build.VERSION.SDK_INT } == true) { strings(R.string.update_manager_10) }
        fun certificates(info: android.content.pm.PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) } }.toSet()
        }
        val expected = certificates(installed)
        require(expected.isNotEmpty() && certificates(archive) == expected) { strings(R.string.update_manager_11) }
    }

    suspend fun cleanOldDownloads() = mutex.withLock { withContext(Dispatchers.IO) {
        folder.listFiles()?.filter { _state.value.available == null || System.currentTimeMillis() - it.lastModified() > TimeUnit.DAYS.toMillis(1) }
            ?.forEach { it.delete() }
    } }
}

@androidx.annotation.Keep
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = (applicationContext as ru.rainedev.sirinmusic.SirinApp).updates.manager
        if (!manager.state.value.automatic) return Result.success()
        return if (manager.check(notify = true)) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
