package ru.rainedev.sirinmusic

import ru.rainedev.sirinmusic.data.AccountProfile

data class LoginState(
    val checking: Boolean = false,
    val error: String? = null,
    val profiles: List<AccountProfile> = emptyList(),
)
