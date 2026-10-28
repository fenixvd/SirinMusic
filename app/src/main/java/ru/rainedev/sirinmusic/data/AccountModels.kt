package ru.rainedev.sirinmusic.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountUser(val id: String = "", @SerialName("display_name") val displayName: String = "", val roles: List<String> = emptyList())
@Serializable
data class AccountMe(val ok: Boolean = false, @SerialName("multi_user") val multiUser: Boolean = false, @SerialName("local_account") val localAccount: Boolean = false, val user: AccountUser = AccountUser(), @SerialName("active_profile") val activeProfile: AccountProfile? = null, val profiles: List<AccountProfile> = emptyList())
@Serializable
data class ManagedDevice(val id: String = "", val name: String = "", @SerialName("profile_id") val profileId: String = "", @SerialName("profile_name") val profileName: String = "", @SerialName("created_at") val createdAt: String = "", @SerialName("last_used_at") val lastUsedAt: String? = null, @SerialName("expires_at") val expiresAt: String = "", @SerialName("revoked_at") val revokedAt: String? = null)
@Serializable
data class ManagedDevices(val tokens: List<ManagedDevice> = emptyList())
@Serializable
class IssuedDevice(val token: String = "", val device: ManagedDevice = ManagedDevice(), val qr: String = "")
@Serializable
data class ManagedUser(val id: String = "", @SerialName("display_name") val displayName: String = "", val status: String = "", val admin: Boolean = false, val profiles: Int = 0)
@Serializable
data class ManagedInvitation(val id: String = "", val email: String = "", @SerialName("created_at") val createdAt: String = "", @SerialName("expires_at") val expiresAt: String = "", @SerialName("consumed_at") val consumedAt: String = "", @SerialName("revoked_at") val revokedAt: String = "")
@Serializable
class IssuedInvitation(val id: String = "", val secret: String = "", val url: String = "")
