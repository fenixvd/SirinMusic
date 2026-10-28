package ru.rainedev.sirinmusic.data

internal interface LoginRepository {
    suspend fun status(connection: ServerConnection): AuthStatus
    suspend fun token(connection: ServerConnection, username: String, password: String, profileId: String? = null): TokenReply
    suspend fun validate(connection: ServerConnection, anonymous: Boolean): Health
}

internal class MusikLoginRepository : LoginRepository {
    override suspend fun status(connection: ServerConnection) = MusikApi(connection).authStatus()
    override suspend fun token(connection: ServerConnection, username: String, password: String, profileId: String?) =
        MusikApi(connection).passwordToken(username, password, profileId)
    override suspend fun validate(connection: ServerConnection, anonymous: Boolean): Health {
        val api = MusikApi(connection)
        return if (anonymous) api.validateAnonymousConnection() else api.validateConnection()
    }
}
