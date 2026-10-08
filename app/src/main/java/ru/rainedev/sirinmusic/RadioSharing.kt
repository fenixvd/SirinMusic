package ru.rainedev.sirinmusic

import android.content.Context
import android.content.Intent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Отправляется только публичная ссылка /listen/, без авторизации клиента. */
internal fun publicRadioLink(value: String): String {
    val url = value.toHttpUrlOrNull() ?: error("Некорректная ссылка радио")
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null &&
        url.pathSegments.size == 2 && url.pathSegments[0] == "listen" && url.pathSegments[1].isNotBlank()) {
        "Сервер вернул некорректную ссылку радио"
    }
    return url.toString()
}

internal fun shareRadioLink(context: Context, value: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, publicRadioLink(value))
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться радио"))
}
