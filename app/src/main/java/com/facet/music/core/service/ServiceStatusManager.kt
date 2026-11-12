package com.facet.music.core.service

import android.util.Log
import com.facet.music.core.source.PlayableSourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceStatusManager @Inject constructor(
    private val sourceRegistry: PlayableSourceRegistry,
    private val probe: ServiceProbe,
) {

    private val tag = "ServiceStatus"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val checkMutex = Mutex()

    @Volatile
    private var checkJob: Job? = null

    @Volatile
    private var lastCheckAtMs: Long = 0L

    private val _statuses = MutableStateFlow(
        ServiceType.entries.associateWith { ServiceHealth(isChecking = true) },
    )
    val statuses: StateFlow<Map<ServiceType, ServiceHealth>> = _statuses.asStateFlow()

    @Synchronized
    fun ensureFresh() {
        if (checkJob?.isActive == true) return
        if (System.currentTimeMillis() - lastCheckAtMs < FRESHNESS_MS) return
        checkJob = scope.launch { runChecks() }
    }

    @Synchronized
    fun refreshStatus() {
        checkJob?.cancel()
        checkJob = scope.launch { runChecks() }
    }

    fun markAvailable(type: ServiceType) {
        _statuses.update { current ->
            val health = current[type] ?: return@update current
            if (health.isChecking) {
                current
            } else {
                current + (type to health.copy(isAvailable = true))
            }
        }
    }

    internal suspend fun runChecks() {
        checkMutex.withLock {
            lastCheckAtMs = System.currentTimeMillis()
            _statuses.update { current ->
                ServiceType.entries.associateWith { type ->
                    (current[type] ?: ServiceHealth()).copy(isChecking = true)
                }
            }

            coroutineScope {
                ServiceType.entries.forEach { type ->
                    launch {
                        val health = checkService(type)
                        _statuses.update { current -> current + (type to health) }
                        Log.d(
                            tag,
                            "${type.displayName}: ${if (health.isAvailable) "онлайн" else "ошибка"}" +
                                if (!health.isConfigured) " (не настроен)" else "",
                        )
                    }
                }
            }
        }
    }

    private suspend fun checkService(type: ServiceType): ServiceHealth =
        try {
            val provider = sourceRegistry.provider(type.source)
            if (provider != null && !provider.isConfigured()) {
                Log.d(tag, "${type.displayName}: источник не настроен")
                ServiceHealth(isAvailable = false, isConfigured = false)
            } else {
                ServiceHealth(isAvailable = probe.isReachable(type))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "${type.displayName}: проверка упала", e)
            ServiceHealth(isAvailable = false)
        }

    private companion object {
        const val FRESHNESS_MS = 10 * 60 * 1000L
    }
}
