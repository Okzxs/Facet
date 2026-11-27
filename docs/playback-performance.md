# Скорость загрузки: Facet vs rx-playa

Дата: 27.11.2026. Источник сравнения: https://github.com/rentalx-wifi/rx-playa (клон в /tmp/rx-playa).

## Что в rx-playa реально влияет на старт

- **Range-чтение.** `playback/ChunkedDataSource.kt` читает googlevideo кусками по 1 МиБ; заявленный замер: 15.5 kB/s без range против 5.7 MB/s с range.
- **Байтовый префетч следующего трека.** `AudioCache.prefetchQueue`: 1 МиБ головы, затем весь трек по 2 МиБ, задержка 8 с, отмена при смене очереди.
- **Один OkHttp-клиент для API и медиа**, `maxRequestsPerHost = 16`; DoH и DPI-desync — региональные ускорения.
- **Кэш в `noBackupFilesDir`**, а не `cacheDir` — система не вычищает.
- **Стартовый буфер 500 мс** и `FAR_BUFFER` 15 мин; два плеера для кроссфейда.
- **Go-стример (`streamer/`, `flashmusic/`)** — это собственный FLAC-каталог с транскодом и дисковым кэшем. К YouTube/SoundCloud он **не относится**; на старт обычного трека не влияет.

## Что уже есть в Facet

- Единый `PlaybackResolver`: скачанное → свой провайдер → канонический вариант → внешний матч → `resolved_matches` Room.
- Single-flight, TTL 5 мин, типизированные ошибки, ретраи 400/1200/3000 мс, persistent-матчи, `CacheDataSource` + `SimpleCache`, ключ по `trackId`, валидация потока, `FLAG_IGNORE_CACHE_ON_ERROR`.
- `preloadNext()` резолвит URL следующего, но байты не качает.

## Что внедрено сейчас

1. **Coil-кэш.** `FacetApp` реализует `SingletonImageLoader.Factory`: memory 25 %, disk 256 МБ в `cacheDir/image_cache`; `PlayerPalette` использует тот же singleton.
2. **Стартовый буфер.** `PlaybackAudioConfig`: `playBufferMs` снижен 2500 → 1000 (SMALL 2000 → 1000, LARGE 4000 → 1500). Первый звук не ждёт 2.5 с.
3. **Кэш аудио.** `PlaybackCache`: `cacheDir/media3_cache_v2` → `noBackupFilesDir/media3_cache_v3`, чтобы система не вычищала кэш.

## Что остаётся главным разрывом

- **Нет range-чтения**: наш `OkHttpDataSource` тянет поток целиком; на googlevideo это упирается в искусственное ограничение. Следующий шаг — адаптер `ChunkedDataSource` поверх `OkHttpDataSource` (1 МиБ, склейка в непрерывный поток).
- **Нет байтового префетча следующего трека** (`preloadNext` только резолвит URL).
- **YouTube через NewPipe `StreamInfo`**, а не InnerTube с per-client range/PoToken.

Вывод: архитектура резолва у Facet не хуже, а по persistent-матчам и компактности даже сильнее. Разница в старте — транспортная: range + prefetch + меньший стартовый буфер. Буфер и кэш уже поправлены; range-адаптер — следующий высокий приоритет.
