package com.facet.music.core.service

import android.util.Log
import com.facet.music.core.source.PlayableSourceRegistry
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.YouTubeRepository
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

/**
 * Глобальный монитор доступности музыкальных сервисов.
 *
 * Проверка намеренно лёгкая: поиск `"test"` с лимитом 1. Этого достаточно,
 * чтобы понять, отвечает ли API, и не тратить трафик на полную страницу.
 * Состояние живёт в Singleton и переживает пересоздание ViewModel/экранов.
 */
@Singleton
class ServiceStatusManager @Inject constructor(
    private val soundCloudRepository: SoundCloudRepository,
    private val youTubeRepository: YouTubeRepository,
    private val sourceRegistry: PlayableSourceRegistry,
) {

    private val tag = "ServiceStatus"

    /** Отдельный scope: проверки не должны зависеть от UI. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val checkMutex = Mutex()

    @Volatile
    private var checkJob: Job? = null

    // Пока первая проверка не завершилась, показываем спиннер, а не ложную ошибку.
    private val _statuses = MutableStateFlow(
        ServiceType.entries.associateWith { ServiceHealth(isChecking = true) },
    )
    val statuses: StateFlow<Map<ServiceType, ServiceHealth>> = _statuses.asStateFlow()

    /**
     * Запускает проверку всех сервисов, если она ещё не идёт.
     * Вызывается при старте приложения и после ошибок поиска.
     */
    @Synchronized
    fun checkAllServices() {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch { runChecks() }
    }

    /**
     * Принудительное обновление из UI: отменяет текущую проверку
     * и запускает новую.
     */
    @Synchronized
    fun refreshStatus() {
        checkJob?.cancel()
        checkJob = scope.launch { runChecks() }
    }

    /**
     * Успешный поиск — тоже доказательство, что сервис жив.
     * Не перебиваем статус, если прямо сейчас идёт плановая проверка.
     */
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

    /**
     * Сама проверка. Видна тестам; в приложении вызывается только через
     * [checkAllServices] или [refreshStatus].
     */
    internal suspend fun runChecks() {
        checkMutex.withLock {
            _statuses.update { current ->
                ServiceType.entries.associateWith { type ->
                    (current[type] ?: ServiceHealth()).copy(isChecking = true)
                }
            }

            // Сервисы проверяются параллельно, чтобы меню не ждало два таймаута.
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
            // Источники с собственным провайдером проверяются через реестр,
            // остальные — через свои репозитории.
            val provider = sourceRegistry.provider(type.source)
            if (provider != null) {
                // Ненастроенный источник (Spotify без ключей) недоступен по
                // определению — сеть не дёргаем, чтобы не ждать таймаут.
                if (!provider.isConfigured()) {
                    Log.d(tag, "${type.displayName}: источник не настроен")
                    ServiceHealth(isAvailable = false, isConfigured = false)
                } else {
                    val result = provider.search(HEALTH_QUERY, HEALTH_LIMIT, useCache = false)
                    ServiceHealth(isAvailable = result.isSuccess)
                }
            } else {
                val result = when (type) {
                    ServiceType.SOUNDCLOUD -> soundCloudRepository.search(HEALTH_QUERY, HEALTH_LIMIT)
                    ServiceType.YOUTUBE -> youTubeRepository.search(HEALTH_QUERY, HEALTH_LIMIT)
                    ServiceType.SPOTIFY -> Result.failure(
                        IllegalStateException("Нет провайдера для ${type.displayName}"),
                    )
                }
                ServiceHealth(isAvailable = result.isSuccess)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "${type.displayName}: проверка упала", e)
            ServiceHealth(isAvailable = false)
        }

    private companion object {
        const val HEALTH_QUERY = "test"
        const val HEALTH_LIMIT = 1
    }
}
