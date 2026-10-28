package ru.rainedev.sirinmusic.playback

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.rainedev.sirinmusic.SirinApp
import ru.rainedev.sirinmusic.MainActivity
import ru.rainedev.sirinmusic.PendingPlayerNavigation

/**
 * Делает из приложения настоящий телефонный плеер: шторка, экран блокировки,
 * кнопки на гарнитуре и Bluetooth, пауза при отключении наушников.
 * Сам ExoPlayer живёт в PlaybackController — здесь только сессия поверх него.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val likeCommand = SessionCommand("ru.rainedev.sirinmusic.LIKE", Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        val app = application as SirinApp

        // Обложка на локскрине грузится СВОИМ загрузчиком, не Coil — и /api/artwork
        // требует Bearer. Без этих заголовков картинки на локскрине не будет (401).
        val http = AuthenticatedDataSourceFactory(app.api)
        // Конструктор с Context не принимает свою DataSource.Factory, поэтому берём
        // вариант с executor — готовый есть в самом классе.
        val bitmaps = CacheBitmapLoader(
            DataSourceBitmapLoader(DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get(), http),
        )

        // UI управляет плеером напрямую, без MediaController. Поэтому onGetSession
        // при запуске не вызывается: регистрируем сессию для уведомления явно.
        session = MediaSession.Builder(this, app.playback.sessionPlayer)
            .setBitmapLoader(bitmaps)
            .setCallback(object : MediaSession.Callback {
                override fun onConnectAsync(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.ConnectionResult> {
                    val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                    if (controller.isTrusted) {
                        result.setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(likeCommand).build(),
                        )
                    }
                    return Futures.immediateFuture(result.build())
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == likeCommand.customAction) {
                        app.playback.like()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java)
                        .setAction(PendingPlayerNavigation.ACTION_OPEN_PLAYER)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
            .also { addSession(it) }

        serviceScope.launch {
            app.playback.ui.map { ui ->
                val id = ui.current?.key
                Triple(id, ui.ratings[id] == "like", id in ui.ratingPending)
            }.distinctUntilChanged().collect { (id, liked, pending) ->
                session?.setMediaButtonPreferences(listOf(
                    CommandButton.Builder(if (liked) CommandButton.ICON_THUMB_UP_FILLED else CommandButton.ICON_THUMB_UP_UNFILLED)
                        .setDisplayName(if (liked) strings(R.string.playback_service_1) else strings(R.string.playback_service_2))
                        .setSessionCommand(likeCommand)
                        .setEnabled(id != null && !liked && !pending)
                        .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                        .build(),
                ))
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Свайп задачи при паузе — закрываем сервис, чтобы не висеть в шторке зря.
        val player = session?.player
        // Buffering still represents a request to play; do not drop its foreground service.
        if (player == null || !player.playWhenReady || player.playbackState == Player.STATE_ENDED || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        session?.run { release() }
        session = null
        super.onDestroy()
    }
}
