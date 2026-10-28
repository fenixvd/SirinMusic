package ru.rainedev.sirinmusic.data

internal interface ProfileRepository {
    suspend fun profile(): Profile
    suspend fun weekly(): WeeklyMetrics
    suspend fun recommendations(): Recommendations
    suspend fun contexts(): List<TasteContext>
    suspend fun rules(): List<RadioRule>
    suspend fun shares(): List<RadioShare>
    suspend fun saveExplore(lo: Double, hi: Double)
    suspend fun createContext(name: String, kind: String): TasteContext
    suspend fun activateContext(id: String, session: String, active: Boolean): List<String>
    suspend fun deleteContext(id: String)
    suspend fun restoreRule(id: String?)
    suspend fun createShare(): RadioShare
    suspend fun revokeShare(token: String)
}

internal class MusikProfileRepository(private val api: MusikApi) : ProfileRepository {
    override suspend fun profile() = api.profile()
    override suspend fun weekly() = api.weeklyMetrics()
    override suspend fun recommendations() = api.recommendations()
    override suspend fun contexts() = api.contexts().contexts
    override suspend fun rules() = api.rules().rules
    override suspend fun shares() = api.shares().shares
    override suspend fun saveExplore(lo: Double, hi: Double) = api.saveExplore(lo, hi)
    override suspend fun createContext(name: String, kind: String) = api.createContext(name, kind)
    override suspend fun activateContext(id: String, session: String, active: Boolean) = api.activateContext(id, session, active).contextIds
    override suspend fun deleteContext(id: String) = api.deleteContext(id)
    override suspend fun restoreRule(id: String?) { if (id == null) api.undoRule() else api.restoreRule(id) }
    override suspend fun createShare() = api.createShare()
    override suspend fun revokeShare(token: String) = api.revokeShare(token)
}
