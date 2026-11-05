package com.facet.music.core.network

import javax.inject.Qualifier

/** Короткие таймауты и ретраи для JSON-API. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiClient

/** Длинное чтение для аудио, HLS-сегментов и скачивания. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MediaClient
