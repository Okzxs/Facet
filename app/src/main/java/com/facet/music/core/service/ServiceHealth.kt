package com.facet.music.core.service

data class ServiceHealth(
    val isAvailable: Boolean = true,
    val isChecking: Boolean = false,
    val isConfigured: Boolean = true,
)
