package com.r0ine.auraforge.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AbilityType {
    @SerialName("dash")
    DASH,

    @SerialName("leap")
    LEAP,

    @SerialName("shockwave")
    SHOCKWAVE,

    @SerialName("guard")
    GUARD;

    val key: String get() = name.lowercase()

    companion object {
        fun byKey(key: String): AbilityType? = entries.firstOrNull { it.key == key.lowercase() }
    }
}
