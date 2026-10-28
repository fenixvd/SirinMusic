package ru.rainedev.sirinmusic.data

interface ConnectionSettings {
    val baseUrl: String
    val token: String
    val clientId: String
    fun snapshot(): ServerConnection = ServerConnection(baseUrl, token, clientId)
}

data class ServerConnection(override val baseUrl: String, override val token: String, override val clientId: String) : ConnectionSettings {
    override fun snapshot(): ServerConnection = this
    override fun toString(): String = "ServerConnection(baseUrl=$baseUrl, token=<redacted>, clientId=$clientId)"
}
