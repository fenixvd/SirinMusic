package ru.rainedev.sirinmusic.ui

import ru.rainedev.sirinmusic.data.ConnectLink

/** The address and API token fields of the login screen. */
data class LoginForm(val url: String, val token: String)

data class LinkedLogin(val form: LoginForm, val signIn: Boolean)

/**
 * Fills the form from a scanned QR or a musik:// link. The token always comes from the
 * link: a link without one clears the field, so a token typed for another server is never
 * sent to the new address. Sign-in starts only when the link carried a token.
 */
fun LoginForm.withLink(link: ConnectLink): LinkedLogin {
    val token = link.token.orEmpty()
    return LinkedLogin(LoginForm(link.baseUrl, token), signIn = token.isNotEmpty())
}
