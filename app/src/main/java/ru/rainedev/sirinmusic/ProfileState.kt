package ru.rainedev.sirinmusic

import ru.rainedev.sirinmusic.data.*

data class ProfileState(
    val profile: Profile? = null,
    val weekly: WeeklyMetrics? = null,
    val recommendations: Recommendations? = null,
    val contexts: List<TasteContext> = emptyList(),
    val rules: List<RadioRule> = emptyList(),
    val shares: List<RadioShare> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val loading: Set<String> = emptySet(),
    val busy: Set<String> = emptySet(),
    val shareToSend: RadioShare? = null,
    val contextCreated: Long = 0,
)
