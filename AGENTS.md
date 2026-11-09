# AGENTS.md — Руководство для AI-агентов

## Инструкция для начала каждой сессии
> Прочитай этот файл и `PROGRESS.md`, назови последнюю выполненную задачу и предложи следующую.

---

## Описание проекта

**Facet** — кастомный музыкальный агрегатор в стиле Material 3.  
Агрегирует стриминговый контент с разных платформ в одном приложении.

**Распространение:** APK через GitHub/Telegram.  
**Не публикуется в Google Play** — парсинг стримингов нарушает их ToS. Никогда не предлагай публикацию в сторы.

---

## Текущий стек

| Компонент | Технология | Версия |
|-----------|-----------|--------|
| Язык | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material 3 | BOM 2024.11.00 |
| DI | Hilt | 2.52 |
| Навигация | Navigation Compose | 2.8.4 |
| Плеер | Media3 (ExoPlayer) | 1.5.0 |
| БД | Room | 2.6.1 |
| Стриминг (SC) | NewPipe Extractor | v0.26.5 |
| Изображения | Coil 3 | 3.0.4 |
| Сеть | OkHttp | 4.12.0 |
| Сериализация | kotlinx.serialization | 1.7.3 |
| Шифрование | Tink (AES-256-GCM + Android Keystore) | 1.15.0 |
| AGP | Android Gradle Plugin | 8.5.2 |
| compileSdk / minSdk | — | 35 / 26 |

---

## Архитектура

**Паттерн:** MVVM + Clean Architecture (Feature / Domain / Data)

```
com.facet.music
├── core/
│   ├── di/              # Hilt модули (RepositoryModule)
│   ├── network/         # OkHttp, NewPipeDownloader
│   └── ui/
│       ├── components/  # Переиспользуемые Compose-компоненты (TrackCard и др.)
│       └── theme/       # Color.kt, Type.kt, Theme.kt (Dynamic Color)
├── data/
│   ├── model/           # Доменные модели (Track, Source, LyricLine…)
│   ├── repository/      # Интерфейсы репозиториев
│   └── source/
│       └── soundcloud/  # SoundCloudApi (api-v2) + SoundCloudRepositoryImpl
├── feature/
│   ├── home/            # HomeScreen + HomeViewModel
│   ├── search/          # SearchScreen + SearchViewModel
│   ├── player/          # PlayerScreen + PlayerViewModel
│   ├── library/         # LibraryScreen + LibraryViewModel
│   └── wave/            # WaveScreen + WaveViewModel (Моя волна)
├── navigation/          # Screen.kt, AppNavGraph.kt
├── MainActivity.kt
└── FacetApp.kt        # @HiltAndroidApp, NewPipe.init()
```

**БД:** Room v19, `exportSchema = true` (схемы в `app/schemas`), миграции собраны в `DatabaseMigrations.kt` (`ALL_MIGRATIONS`); каждая миграция покрыта изолированным тестом в `DatabaseMigrationTest` (базовые схемы v1–v3, не экспортированные ранее, восстановлены DDL-заглушками). `MIGRATION_14_15` обнуляет `streamUrl`/`previewSeconds` у старых строк Deezer/Apple и удаляет их кэш поиска и аудио; `MIGRATION_15_16` удаляет `audio_cache` и создаёт `resolved_matches` (PK `originalTrackId`: `matchedSource`, `matchedUrl`, `confidence`, `createdAt`); `MIGRATION_16_17` создаёт `artist_cache` (PK `key`: `json`, `fetchedAt`), `followed_artists` (PK `artistKey`: `source`, `url`, `name`, `avatarUrl`, `followedAt`) и колонку `artistUrl` во всех таблицах с треками; `MIGRATION_17_18` создаёт `wave_impressions`, `wave_sessions`, `artist_blocklist` и колонку `play_history.playedFraction`. Observe-методы скачанного проверяют файл на диске и вычищают мусорные записи из БД; сбои БД в лайках не крашат приложение (`runCatching` + `rethrowCancellation`, ошибка в UI). Дизлайки живут в `disliked_tracks` (`DislikedTrackDao`) и исключают треки из рекомендаций (`RecommendationPlanner.filterDisliked`). Кэш поиска — `search_cache` (PK `query`: `source`, сырой `jsonResponse`, `timestamp`); `SearchCacheRepository` отдаёт ответ моложе 24 ч, просроченные записи удаляются лениво при чтении и при каждой записи.

---

## Источники контента

| Источник | Метод | Версия |
|----------|-------|--------|
| **SoundCloud** | api-v2 (лёгкий резолв) + NewPipe (фолбэк) | ✅ v1 |
| **YouTube** | NewPipe Extractor (полный стрим) | ✅ v1 |
| **Spotify** | Web API OAuth 2.0 PKCE (поиск; стрим через YouTube) | v2 |
| **Deezer** | Метаданные: новинки, треки альбома, импорт (без потока) | v1 |
| LRCLIB (тексты) | REST API (открытый) | ✅ v1 |
| Last.fm (похожие артисты) | Web API | ✅ v1 |
| Яндекс Музыка | Неофициальный API | v2 |
| ВК Музыка | VK API | v2 |

---

## Сессия 69, фаза 8, часть 2: UI и умное пополнение (09.11.2026)

- **Обратная связь.** Веса семян в сессии: скип <30 с ×0.5, два скипа одного семени выключают его, дослушал ×1.3, лайк ×2 и новый seed; дизлайк блокирует артиста и убирает его будущие треки. Три быстрых скипа заменяют непроигранную волну. SHOWN пишется при переходе, исход — при уходе.
- **Room v19.** `wave_sessions` + `mood`, `radioArtistKey`, `seedTracksJson`; `MIGRATION_18_19`. Сессия/курсор/семена восстанавливаются после смерти процесса.
- **Пополнение.** `PlaybackSessionCoordinator`: порог 5, пачка 10, single-flight, `ConnectivityObserver`, 3 попытки 2/5/15 с, `addMediaItems`.
- **Радио.** Трек/артист с фиксированными семенами, FAMILIAR=0, артист ≤25% и 1 из 4; для артиста — его популярные треки.
- **UI.** «Не нравится» и «Не рекомендовать исполнителя» в плеере; «Новая волна» с хаптиком; `WaveMood` (15/10, 8/20, 3/35) сохраняется в настройках; холодный старт 3–5 артистов (Last.fm + YouTube fallback); debug long-press показывает origin/seed/score.
- **8.12.** `russianPriority` и RU-строки удалены; главная оставлена на `RecommendationPlanner` (перевод на движок требует отдельного адаптера).
- **8.13.** `wave_summary` с агрегатами в Supabase без названий треков.
- **Проверка.** 56 suite / 322 теста; live SM-A266B и MI 8 (холодный старт). APK 21 801 967 байт, SHA-256 `a914b572f2672697d7c9bb82144e3370d9ffc9d9434bea7e5404064b5cd4e893` (без R8 из-за свопа; конфиг возвращён).

---

## Сессия 69, фаза 8, часть 1: ядро «Моей волны» (06.11.2026)

- **Пакет `core/wave`.** Чистый Kotlin: `WaveEngine` (кандидаты → жёсткие фильтры → скоринг → MMR → пачка), `CandidateSource` (Hilt `@IntoSet`), `TasteProfileProvider`, `WaveHistoryStore`, `Clock`, `RandomProvider`, `WaveConfig`. `WaveRepositoryImpl` — тонкая обёртка, старая логика квот удалена.
- **Нормализация.** `core/text/MusicNormalizer`: `normalizeArtist`, `trackKey` («артист|название»), жанровые токены, маркеры версий. `RecommendationPlanner` использует его; один трек из разных источников даёт один ключ.
- **Room v18.** `wave_impressions` (индексы `trackKey`/`artistKey`/`shownAt`), `wave_sessions` (10 последних), `artist_blocklist`, `play_history.playedFraction`; `MIGRATION_17_18`. Показы старше 90 дней чистятся раз в сутки при старте координатора.
- **Источники.** Related SoundCloud/YouTube (случайный сдвиг), Last.fm похожие артисты (softmax 0.7, топ-треки, резолв в YouTube через `TrackMatcher`), second hop, теги `tag.getTopTracks`, новые релизы подписок, знакомое (21/30 дней). Общий `WaveNetworkGate` — не больше 4 запросов, таймаут источника 6 с; ошибка/пусто не валят волну.
- **Фильтры.** Дизлайки, blocklist, показы 14 дней, проигрывания 7 дней, очередь, 60 с–12 мин, Go+/snipped, маркеры `full album`/`1 hour`/`mix 20xx`/`8d`/`nightcore`/`sped up`/`slowed`/`karaoke`/`instrumental`/`cover` (кроме любимых пользователем). Три скипа <30 с за 30 дней → `artist_blocklist` на 30 дней (`AUTO_SKIPS`).
- **Скоринг.** 0.30 artist + 0.15 genre + 0.20 origin + 0.20 novelty + 0.10 seed + 0.05 popularity − штрафы + Gumbel(0, 0.15). Первый трек — уверенный RELATED; знакомое ≤10%, не первым и не подряд; разведка 15–25%; MMR λ=0.7; один артист не чаще раза в окне 5 и ≤2 на 20; ≤3 треков одного семени подряд. При нехватке — повторная генерация, затем короткая пачка; добора лайками/историей нет.
- **Вкус.** Затухание 0.5^(дни/30) при чтении и записи; лайк +3, дослушал +1, скип в волне −1, скип в своих плейлистах −0.3, дизлайк −4. Топ-200 сырых весов досортировывается в Kotlin. `PlaybackSessionCoordinator` пишет PLAYED/SKIPPED/FINISHED, `PlayerViewModel` вызывает `onDislike`; refill волны — 10 треков.
- **Проверка.** `assembleDebug`/`assembleRelease` — BUILD SUCCESSFUL; 56 suite / 319 тестов, 0 failures. Новые тесты: `WaveEngineTest`, `RoomWaveHistoryStoreTest`, обновлённый `TasteRepositoryTest`, миграция 17→18. Release APK 7 942 342 байта, SHA-256 `58228c776dd48bedb4e9886d3728979e17af0dfb3836350f836d5bebabef3155`; live не проверялся — устройство было недоступно.

---

## Сессия 69, фаза-фикс 7.1: «Все треки» по пять (06.11.2026)

- **Без автоподгрузки.** Секция «Все треки» больше не вызывает `loadMore()` при прокрутке. Изначально показываются первые **5** треков, внизу — маленькая круглая стрелка `ExpandMore` (`artist_show_more`).
- **Шаг.** `ArtistViewModel.showMoreTracks()` увеличивает `visibleTrackCount` на 5; `ArtistScreen` рендерит `allTracks.take(visibleTrackCount)`. Когда раскрыты все загруженные треки, стрелка догружает следующую страницу (`loadMore`) и показывает следующие пять; так до конца.
- **Состояние.** `ArtistUiState.visibleTrackCount` сбрасывается на 5 при смене артиста и сохраняется при обновлении/кэше того же профиля. Стрелка скрывается, когда показаны все треки и страниц больше нет.

---

## Сессия 69, фаза 7: экран артиста на YouTube и SoundCloud (06.11.2026)

- **Маршруты.** `Screen.ArtistDetail` — `artist/{source}/{encodedUrl}?name=…` (source/url/name в `SavedStateHandle`); старый `Screen.Artist` (`artist/{name}`) остался и через `ArtistRepository.resolveByName` ищет лучшего артиста (`ArtistMerger.bestMatch`) и открывает его. Оба маршрута ведут в один `ArtistScreen`/`ArtistViewModel`; док скрыт.
- **ArtistRepository.** `data/repository/ArtistRepository` + `data/source/artist/ArtistRepositoryImpl`: `artist`, `cached`, `store`, `popularTracks`, `releases`, `allTracks`, `releaseTracks`, `playlists`, `related`, `resolveByName`, `observeFollowed`, `setFollowed`. YouTube: `ChannelInfo` → вкладки `videos`/`albums`/`playlists`; у Topic-канала вкладок нет, поэтому треки и релизы добираются поиском YouTube Music (`searchPage` и новые `searchMusicAlbums`/`searchMusicPlaylists`, фильтры `MUSIC_ALBUMS`/`MUSIC_PLAYLISTS`). SoundCloud: `tracks`/`albums`/`playlists`, популярное — по `viewCount`.
- **Популярное.** Основной список сортируется по прослушиваниям; если у артиста есть профиль во втором источнике (`ArtistMerger.bestMatch`), списки сливаются через `TrackMerger.merge` и сортируются по `Track.playCount`. `Track.playCount` — transient-поле (в Room не хранится).
- **Кэш.** Комната `artist_cache` (TTL 6 ч): `cached()` отдаёт страницу мгновенно и обнуляет `nextPage` (токен живёт только в памяти), затем ViewModel обновляет её. Релизы/плейлисты/похожие догружаются после шапки; `store()` сохраняет обновлённую страницу.
- **Подписки.** `followed_artists` + `FollowedArtistDao`; `ArtistRepository.observeFollowed` отдаёт `Set<String>` ключей `SOURCE:url`, кнопка «Подписаться»/«Вы подписаны» в шапке и в `LargeTopAppBar`.
- **UI.** `LargeTopAppBar` + `exitUntilCollapsedScrollBehavior` + `nestedScroll`, прозрачная шапка с баннером/размытой обложкой, аватар 120dp, `displaySmall`, подписчики, «Слушать» (FilledButton), «Перемешать» (FilledTonalButton), «Волна по артисту» (OutlinedButton → существующий radio от трека), «Подписаться». Секции: «Популярное» (топ-10 с номерами), «Релизы» (`LazyRow` 160dp, клик — шторка треков альбома), «Все треки»: первые 5 + стрелка «ещё 5» до конца с догрузкой страниц, «Плейлисты» (шторка), «Похожие артисты». Ошибка/пусто — `EmptyState` с «Повторить».
- **Ссылка на артиста.** `Track.artistUrl` заполняется из `uploaderUrl` YouTube/SoundCloud (включая чарты и импорт), денормализуется в `liked_tracks`, `play_history`, `chart_cache`, `downloaded_tracks`, `playlist_tracks`, `queue`. Клик по артисту в плеере, поиске, медиатеке, плейлисте и волне открывает точный маршрут; без ссылки — поиск по имени.
- **Deezer.** `ArtistViewModel` больше не знает Deezer; из `DeezerMetadataClient` удалены `fetchArtist*`, `searchArtistTracks`, `fetchAlbumTracks` остался только для новинок главной.
- **Проверка.** `rg -i deezer feature/artist` пуст; live на SM-A266B: Daft Punk (YouTube) — популярное, релизы, шторка `Random Access Memories`, полный трек 6:09; KAASI (SoundCloud) — релизы/плейлисты/похожие, «Слушать» играет полный трек, подписка переживает перезапуск, подгрузка «Всех треков» работает.

---

## Сессия 69, фаза 6: поиск, артисты и мультивыбор (06.11.2026)

- **Artist.** `data/model/Artist` (`id`, `source`, `name`, `avatarUrl`, `subscriberCount`, `isVerified`, `url`); `ArtistMerger` нормализует имя (lowercase, ё→е, диакритика, « - Topic», official, VEVO), сливает источники, выбирает аватар YouTube → SoundCloud, сортирует по точному имени, verified и подписчикам. `bestMatch` использует точное имя, префикс от 3 символов и Jaro-Winkler ≥ 0.92.
- **Поиск артистов.** YouTube — `music_artists`, при отсутствии точного имени дополнительно `channels`; SoundCloud — `api-v2/search/users` (быстро) с NewPipe `users` как фолбэком. `PlayableSource.searchArtists` и `YouTubeRepository/SoundCloudRepository` отдают `Artist`.
- **Мультивыбор.** `SearchUiState.selectedSources: Set<ServiceType>` вместо `selectedService/allSources`; `FilterChip` для каждого источника + «Все»; последний источник снять нельзя (подсказка «Нужен хотя бы один источник»). Набор сохраняется в `AppSettings.searchSources` (`search_sources`, имена через запятую), неизвестные имена отбрасываются, пустой набор = все; старые `search_service`/`search_all_sources` мигрируют.
- **Выдача.** Артисты и треки ищутся параллельно в одном `channelFlow` (таймаут 6 с на источник). Над треками — «Исполнители» (LazyRow, круглые аватары 72dp, бейдж источника), при точном/префиксном совпадении — крупная карточка 96dp с «Открыть»/«Слушать» и секцией «Треки {артист}» из music_songs YouTube или треков пользователя SoundCloud по прослушиваниям. «Слушать» ставит эти треки в очередь и открывает плеер; «Все треки» ведёт на экран артиста.
- **Ранжирование.** `TrackMerger.merge(groups, query)` считает score по `SearchConfig`: точный артист +3, все токены запроса +2, позиция в источнике 1/(1+rank), дополнительный источник +0.3, штраф −1.5 за cover/remix/nightcore/sped up/slowed/karaoke/8d без такого слова в запросе. Сортировка стабильная; `mergeKeepingOrder` не двигает уже показанные строки.
- **Пагинация.** YouTube хранит `Page` и грузит `SearchInfo.getMoreItems`; SoundCloud — `offset` api-v2. У каждого источника свой `nextToken`; внизу списка индикатор, дедуп по ключу, `loadMore()` с защитой от повторов.
- **YouTube треки.** Основной фильтр `music_songs`; `videos` — второй запрос, только если меньше 10 результатов. Артист — `uploaderName` без « - Topic».
- **Подсказки.** `SearchSuggestions` объединяет `suggestionExtractor` YouTube/SoundCloud и локальные совпадения истории/лайков; дебаунс 250 мс, отмена предыдущего запроса, список до 5 строк под полем, клик сразу запускает поиск. `Translit` даёт второй проход (кириллица ↔ латиница), если треков меньше 5.
- **Скелетоны.** Артисты и треки залиты shimmer через `graphicsLayer` (анимация в layer, без рекомпозиции на кадр).
- **Проверка.** `assembleDebug`/`assembleRelease` — BUILD SUCCESSFUL, 54 suite / 303 теста, 0 failures; release APK 7 424 634 байт, SHA-256 `bb3d870e4c8daaa438c249f6f0a56f97b5e67c97d208e829cbb8e6e8d588f56a`. Live на SM-A266B: подсказки, «Исполнители» и крупная карточка ODESZA/Daft Punk, «Слушать» запускает треки артиста, мультивыбор (SoundCloud+Spotify) сохраняется после перезапуска, пагинация 30→50 без дублей.

---

## Сессия 69, фаза 5: сеть, NewPipe и SoundCloud (05.11.2026)

- **Два клиента.** `@ApiClient` (connect 10 / read 15 / callTimeout 20 с, `Accept: application/json`, `RetryInterceptor`) и `@MediaClient` (connect 15 / read 30, `callTimeout = 0`, `retryOnConnectionFailure`) строятся от общего базового `OkHttpClient` через `newBuilder()` — пул соединений и диспетчер общие. `DownloadTrackWorker` больше не создаёт свой `DOWNLOAD_CLIENT`, а берёт `@MediaClient` через `DownloadWorkerEntryPoint`. `NewPipeDownloader` тоже на медиа-клиенте.
- **Повторы.** `RetryInterceptor` только для GET: 429/503 — до двух повторов, остальные 5xx — один; `Retry-After` в секундах или HTTP-дате, не больше 10 с; иначе экспоненциальный backoff 500 мс с jitter.
- **Отмена NewPipe.** `withCancellation` оборачивает блок в `runInterruptible(Dispatchers.IO)`, ThreadLocal активных `Call` ставится внутри этого блока, а `Job.invokeOnCompletion(onCancelling = true, invokeImmediately = true)` отменяет `Call` в момент отмены. Тест `NewPipeDownloaderCancellationTest` подтверждает отмену зависшего запроса быстрее 500 мс.
- **SoundCloud без StreamInfo.** Основной путь — `SoundCloudApi` на api-v2: `/tracks/{id}` (если числовой id уже известен из поиска) или `/resolve?url=`, `media.transcodings`, затем подписанный URL с `client_id` и `track_authorization`. Приоритет: progressive `audio/mpeg` → HLS `audio/mpeg`/`audio/mpegurl` → HLS Opus/AAC; `snipped` и `ctr/cbc-encrypted-hls` пропускаются. Если первый endpoint отвечает 404 (частый `abr_sq`), пробуется следующий транскодинг. `StreamInfo` — только запасной путь при ошибке разбора JSON, не при сетевой ошибке.
- **client_id.** `SoundCloudClientIdProvider` кэширует значение в DataStore (`soundcloud_client`) с TTL 24 ч, память + single-flight `Mutex`; при 401/403/404 кэш сбрасывается и запрос повторяется один раз. Прогрев — в `applicationScope` после первого кадра (`ProcessLifecycleOwner`). `SoundcloudParsingHelper.clientId()` остаётся последним фолбэком, его статический кэш сбросить нельзя.
- **Ошибки.** `SoundCloudFailure` (GEO_BLOCKED, SUBSCRIPTION_ONLY, PROGRESSIVE_UNAVAILABLE, UNAVAILABLE, PARSE, HTTP) превращается в `ResolveException`; `catch (Throwable)` убран, `CancellationException` пробрасывается. Трек с `policy = SNIP`/`SUB_HIGH_TIER` получает `isSubscriptionOnly`, бейдж «Go+» в поиске и не попадает в волну; попытка играть даёт `SubscriptionOnly`, а не 30-секундный сниппет. `Track.externalId` хранит числовой id, `Track.isSubscriptionOnly` — флаг Go+ (новых колонок Room нет).
- **Сроки ссылок.** `StreamExpiry` разбирает `expire`/`expires`; `AudioStreamResult.expiresAtMs` доходит до `ResolvedStream`, YouTube и SoundCloud отдают реальный TTL. В логах нет URL.
- **Сводный поиск.** `SearchViewModel` отдаёт источники через `channelFlow` по мере ответа: первый непустой результат сразу на экране, новые вливаются `TrackMerger.mergeKeepingOrder` без перестановки уже показанных строк. На источник — `withTimeoutOrNull(6_000)`; «ничего не найдено» (успешные пустые ответы) отличается от «все источники с ошибкой».
- **Статусы и офлайн.** `ServiceStatusManager` больше не вызывается из `FacetApp.onCreate` и из ошибок поиска: проверка ленивая (поиск/панель статусов) и не чаще раза в 10 минут, через лёгкий HEAD (`ServiceProbe`), а не полноценный поиск. `ConnectivityObserver` (`ConnectivityManager.NetworkCallback`) даёт `StateFlow<Boolean>`; при отсутствии сети в поиске показывается офлайн-баннер, поиск не уходит в шторм запросов и повторяется при возврате сети.
- **Кэш Media3.** Каталог переименован в `media3_cache_v2`: старый кэш фазы 3 мог содержать progressive-поток под ключом трека и ломал HLS (`Input does not start with the #EXTM3U header`). Старый `media3_cache` удаляется в `FacetApp` в фоне; `PlaybackCache.remove(trackId)` вызывается и при 401/403/404/410.
- **Проверка.** `assembleDebug`/`assembleRelease` — BUILD SUCCESSFUL, 52 suite / 294 теста, 0 failures; release APK 7 389 634 байт, SHA-256 `6078b666a8ce5c85e50bd2aa0e557963ec010351648eba4e8669c9f906735703`. Live на SM-A266B: поиск SoundCloud через api-v2, HLS `type london` и ODESZA играют с первого нажатия, `abr_sq` 404 автоматически уходит на AAC, Go+ помечается бейджем и не играет 30 секунд, сводный поиск отдаёт 38 результатов с «+1», статусы — SoundCloud/YouTube онлайн, Spotify без ключей ожидаемо недоступен. Fresh install + старый кэш: восстановленная очередь ODESZA играет.

---

## Сессия 69, фаза 4: очередь в ExoPlayer, сессия и Now Bar (04.11.2026)

- **Очередь в плеере.** `PlayerController` — фасад с `List<Track>`, но очередь зеркалится в ExoPlayer: `setMediaItems`/`addMediaItems`/`removeMediaItem`/`moveMediaItems`, repeat через `Player.REPEAT_MODE_*`, `shuffleModeEnabled = false`. `currentIndex`/`currentTrack` обновляются из `onMediaItemTransition`/`onTimelineChanged`. `MediaItem` несёт `mediaId`, `facet://track/{id}`, `customCacheKey`, `MediaMetadata` (title/artist/albumTitle/artwork/duration) и сериализованный `Track` в `MediaMetadata.extras` (`TrackMediaItem`).
- **Никаких stop() при переходах.** `playTrackAt`/`setQueueAndPlay` не вызывают `pause()+stop()`: уведомление и Now Bar остаются, во время резолва плеер в BUFFERING. `playNext`/`playPrevious` идут через `seekToNextMediaItem`/`seekToPreviousMediaItem`; в `MusicService.ForwardingPlayer` осталась только логика «назад: >3 с — в начало».
- **Ошибки.** `onPlayerError` после исчерпания резолвера автоматически переходит к следующему треку (до 3 ошибок подряд), затем пауза и понятная ошибка. `retryAfterError()` возвращает на сохранённую позицию, а не с нуля.
- **`PlayerConnection`.** MediaController вынесен за интерфейс `PlayerConnection`/`MediaSessionPlayerConnection`; у `PlayerController` один `@Inject`-конструктор без nullable-зависимостей. Подключение идёт сразу, `connect()` ждёт до 3 с; команды до подключения не теряются, а ждут контроллер.
- **`PlaybackSessionCoordinator`.** Живёт в application-scope, стартует из `MusicService.onCreate`: история только после 30 с или 50 %, вкусовой фидбек с `TasteContext`, аналитика `TRACK_PLAY`, refill волны, предзагрузка следующего трека и сохранение очереди/индекса/позиции каждые 10 с и на паузе/смене трека. `PlayerViewModel` только отображает состояние.
- **Восстановление.** `QueueRepository` хранит `positionMs`; `MediaSession.Callback.onPlaybackResumption` возвращает сохранённую очередь, индекс и позицию. `PlaybackSessionCoordinator` продолжает после потери аудиофокуса (TikTok/звонок) при возврате в Facet в течение 15 минут, если включена настройка «Продолжать после других приложений».
- **Now Bar и уведомление.** `ArtworkBitmapLoader` (`CacheBitmapLoader` + масштаб до 512 px) в `MediaSession.Builder.setBitmapLoader`, кнопка лайка через `SessionCommand`/`CommandButton` (`setMediaButtonPreferences`), `onTaskRemoved` → `pauseAllPlayersAndStopSelf()` только если не играет. `isPlaying` — только из `onIsPlayingChanged`; `isBuffering` включает резолв, кнопка Play блокируется на время загрузки.
- **Фон и StrictMode.** Настройки: «Продолжать после других приложений» и «Работа в фоне» с переходом в системные настройки приложения. В debug включены `detectDiskReads`/`detectDiskWrites`/`detectNetwork`/`penaltyLog`; очередь Room и прогрев `SimpleCache` вынесены с main.
- **Проверка.** debug/release зелёные, 47 suite / 272 теста, 0 failures; release APK 7 240 235 байт, SHA-256 `19898081e7207747f1cd69136d95fd612e776813eb72550de19926ffb2ff9863`. Live: восстановленная очередь играет с позиции, next в HLS-очереди без ошибок, лайк в уведомлении переключается, TikTok забирает фокус → пауза, возврат в Facet → авто-продолжение с той же позиции.

---

## Сессия 69, фаза 3: единый резолвер и кэш Media3 (04.11.2026)

- **PlayableSource.** `MusicSourceProvider` заменён на `PlayableSource`: `source`, `capabilities`, `search(query, limit, page, useCache)`, `searchArtists`, `resolveStream(track): Result<ResolvedStream>`. YouTube и SoundCloud — полноценные источники потока (`YouTubePlayableSource`, `SoundCloudPlayableSource`), Spotify — только метаданные. Все три зарегистрированы через `@IntoSet` в `MusicSourceModule`; реестр — `PlayableSourceRegistry`.
- **PlaybackResolver — единственная точка резолва.** Порядок: скачанный файл → свой провайдер → параллельный матч SoundCloud/YouTube (`TrackMatcher`, допуск ±7 с и штрафы за cover/remix/live/nightcore/sped up/slowed/karaoke) → `resolved_matches` в Room → in-memory кэш с `expiresAtMs` (YouTube `expire`, SoundCloud 5 мин). Single-flight `Map<trackId, Deferred>` под `Mutex`: повторный Play, предзагрузка и `ResolvingDataSource` не запускают второй резолв. До трёх повторов с задержками 400/1200/3000 мс и jitter для сети/таймаутов/5xx/401/403; ошибки типизированы `ResolveError` (Network, NotAvailable, SubscriptionOnly, GeoBlocked, NoMatch, Unknown).
- **Кэш Media3.** `PlaybackCache` — один `SimpleCache` на процесс в `cacheDir/media3_cache`, `LeastRecentlyUsedCacheEvictor` 1 ГБ, `StandaloneDatabaseProvider`. Цепочка: `CacheDataSource` → `ResolvingDataSource` → `OkHttpDataSource` (медиа-клиент без `callTimeout`), `DefaultMediaSourceFactory` в `MusicService`. URI элемента — `facet://track/{encoded trackId}`, `setCustomCacheKey(trackId)`, HLS и progressive идут через кэш; `PlaybackLoadErrorPolicy` на 401/403/404/410 сбрасывает поток (максимум 2 раза) и повторяет без потери позиции.
- **Старый кэш удалён.** Нет `AudioCacheWorker`/`EntryPoint`, `AudioCacheRepository`/`Impl`, `AudioCacheDao`/`Entity`, `observeAudioCache`/`enqueueAudioCache`. `MIGRATION_15_16` делает `DROP TABLE audio_cache`; `FacetApp` при старте отменяет `WorkManager`-задачи с тегом `audio_cache` и удаляет `cacheDir/audio_cache`.
- **Скачивание.** `DownloadSourceResolver` и `DownloadEngine` ходят через `PlaybackResolver` с `requireProgressive = true`; `Id3Writer` пишет заголовок и фреймы во временный файл и потоково копирует аудио (без `readBytes()`), MIME обложки определяется по сигнатуре JPEG/PNG/WebP, теги — только MP3. Имена файлов — SHA-256 от `trackId` в hex.
- **Проверка.** `assembleDebug`/`assembleRelease` — BUILD SUCCESSFUL, `testDebugUnitTest` — 46 suite / 282 теста, 0 failures. Live на SM-A266B: миграция 15→16 без краша, `ParisLove` играет, SoundCloud HLS `type london` играет, кэш Media3 в настройках показывает 3.1 МБ / 10 записей, Now Bar видит метаданные.

---

## Сессия 69, фаза 2: Deezer и Apple без превью (03.11.2026)

- **Источники воспроизведения:** только полный SoundCloud/YouTube. Deezer — `DeezerMetadataClient` (новинки, треки альбома, импорт), `streamUrl` всегда `null`; Apple-провайдер и iTunes lookup удалены.
- **Поиск:** `ServiceType` = SOUNDCLOUD/YOUTUBE/SPOTIFY, три кнопки брендов; `Source.DEEZER/APPLE` в enum только для старых строк. `Source.isMetadataOnly` — единый признак для Deezer/Apple/Spotify: `StreamResolver` и `PlayerController` игнорируют `streamUrl` и идут в YouTube-матч.
- **БД:** v15, `MIGRATION_14_15` обнуляет `queue.streamUrl`/`previewSeconds` для Deezer/Apple и удаляет их `search_cache`/`audio_cache`; тест 14→15 и сквозной 1→15.
- **Импорт:** Apple Music-ссылки → `UNSUPPORTED_LINK`; Deezer-плейлисты импортируются как метаданные без превью.

---

## Настройки оформления

- `ThemeMode` (LIGHT/DARK/SYSTEM, по умолчанию SYSTEM), `customAccentColor` (ARGB или null) и `accentEnabled` (включён ли собственный акцент; по умолчанию false — цвета обоев) живут в `AppSettings` (SharedPreferences `facet_settings`, ключи `theme_mode`, `custom_accent_color`, `accent_enabled`).
- Приоритет цвета в `FacetTheme`: собственный акцент (только при `accentEnabled`, работает на **всех** версиях Android, перекрывает Dynamic Color) → Dynamic Color (Android 12+) → ручная палитра кружочков (только ниже 12) → fallback-палитра. Кружки палитры и RGB-пикер пишут в `customAccentColor` и автоматически включают `accentEnabled`; `accentColorArgb` — legacy-фолбэк для Android ниже 12.
- Ряд акцентов в настройках — горизонтально скроллируемый `Row` (`horizontalScroll`) с отступами по краям: 6 пресетов, а в конце `CustomColorDot` — круг с замкнутым радужным sweep-градиентом и иконкой палитры (вход в RGB-диалог); выбранный свой цвет заливает этот круг и даёт галочку. Непрокручиваемый `Row` на узких экранах сплющивал последний элемент (сессия 32).
- Ахроматический акцент (насыщенность HSL < `ACHROMATIC_SATURATION_THRESHOLD` = 0.05: чёрный, белый, серый) даёт строго монохромную палитру: `accentColorScheme` обнуляет насыщенность ролей, иначе hue=0 (красный) и принудительная насыщенность красили такой акцент в бордовый; та же защита в `PlayerPalette.computePlayerColors` для серых обложек (сессия 33).
- Недавние акценты: `AppSettings.recentAccentColors` (SharedPreferences `recent_accent_colors`, строка через запятую, LIFO, дедупликация, максимум 5 через `SettingsRepository.addRecentAccentColor`); в настройках ряд «Недавние» над основной палитрой, тап применяет цвет. В RGB-диалоге — HEX-поле с двусторонней связью со слайдерами и валидацией 6 hex-цифр (сессия 33).
- `MainActivity` читает `SettingsRepository.settings` и передаёт в `FacetTheme` параметры `themeMode`, `accentColor`, `customAccentColor`.
- Палитра плеера при заданном `customAccentColor` смешивается с ним на 40% (`blendPaletteWithAccent` в PlayerScreen), чтобы цвета обложки не конфликтовали с темой. Контрастные роли (`onPrimary`, `on*Container`) после смешивания пересчитываются от светлоты, а не лерпаются.
- `dynamicCoverColors` (тумблер «Динамические цвета обложки»): включён — `PlayerScreen` берёт `playerColors.primary` для тонировки фона (28% → 14%), подкраски панели, слайдера и кнопки Play; выключен — строго `background` и акцент темы. Смена трека анимируется `AnimatedContent` по `TrackDisplay`, морф обложки остаётся только на актуальном треке.
- Экраны-вкладки сами ставят себе фон (`Modifier.background(MaterialTheme.colorScheme.background)`) и инсеты: корневого Scaffold в `AppNavGraph` больше нет (док — оверлей, высота через `LocalBottomDockHeight`). Корневой Box навграфа тоже непрозрачный — иначе в переходах вкладок проглядывает окно.
- `FacetTheme` провайдит `LocalContentColor = colorScheme.onBackground`: MaterialTheme его не ставит, дефолт — `Color.Black`, и после удаления корневого Scaffold любой Text без явного цвета чернел на тёмном фоне. Scaffold плеера с `containerColor = Transparent` обязан задавать `contentColor` явно (`contentColorFor(Transparent)` = Unspecified).
- `windowBackground` задан в `values/themes.xml` и `values-night/themes.xml` (#FAFAFA / #121212, совпадает с Color.kt) — без ночного варианта окно оставалось белым и давало вспышку на холодном старте.
- Текст песни (`LyricLineItem` в `PlayerScreen.kt`): у строк списка **нет** `Modifier.animateItem()` — на каждый `animateScrollToItem` (смена активной строки) он запускает анимацию появления у уже видимых строк, и весь текст гаснет в ноль и проявляется ~250 мс. Полоска прогресса живёт в постоянном слоте `LyricBarSlot = 12.dp` и не меняет высоту строки — иначе список прыгает при каждой смене строки (сессия 51).
- Кнопки под обложкой (`DownloadSquircle`/`LikeSquircle`/`DislikeSquircle`) получают цвета палитры плеера через `ExpressiveHeroZone` (`actionAccent`, `actionContainer`, `actionIdleContainer`): акцент `coverAccent`, контейнер `playerColors.primaryContainer`, подложка покоя `coverAccent.copy(alpha = 0.14f)`. В плеере одна кнопка текста — пилюля на `PlayerControlIsland`; в шапке её нет, там «Сейчас играет» и артист со свечением (сессия 67, откат).
- Внешние источники: `core/source/PlayableSource` (+ `PlayableSourceRegistry`, регистрация `@IntoSet` в `core/di/MusicSourceModule`); YouTube, SoundCloud и Spotify (последний — только метаданные). Deezer — `DeezerMetadataClient`: новинки, альбомы, артисты и импорт плейлистов, `streamUrl = null`; Apple-провайдер удалён. `Source.DEEZER`/`APPLE` остались в enum только для чтения старых строк БД; `Source.isMetadataOnly` (Deezer/Apple/Spotify) заставляет `PlaybackResolver` не играть `streamUrl`, а искать полную версию через `TrackMatcher` (порог 5.0, допуск ±7 с, штрафы cover/remix/live/nightcore/sped up/slowed/karaoke, бонус official/topic, ISRC). `PlayerController` больше не содержит `when` по источникам: он всегда зовёт `PlaybackResolver`, а MediaItem получает URI `facet://track/{id}`. `ServiceType` = SOUNDCLOUD/YOUTUBE/SPOTIFY и несёт `source`.
- Сеть внешних источников: `RateLimiter` (`awaitSlot()` / `acquire { }`, 1000 мс, `Mutex` + `delay` — главный поток не блокируется) по одному на источник через `@SourceRateLimiter` и `RateLimiterModule`; у Spotify лимитер общий для поиска и запроса токена. Успешный ответ поиска кэшируется сырым JSON в Room (`search_cache`, TTL 24 ч), ошибки сети не кэшируются, health-проверка идёт мимо кэша (`useCache = false`) — сессия 56.
- Кнопки источников в поиске — 3 иконки 42×42dp (SoundCloud, YouTube, Spotify; радиус `MaterialTheme.shapes.medium`) в брендовых цветах с шагом 6dp; кнопка статусов 40dp. При выбранном «Все источники» ряд скрыт. Spotify без ключей неактивен (`ServiceHealth.isConfigured`). Иконки — VectorDrawable `ic_brand_*` из Simple Icons (CC0), у недоступного сервиса alpha 0.4, у активного фон 0.25 + рамка 2dp (сессии 55, 67, 69).
- Локализация: весь UI-текст — только `stringResource`/`pluralStringResource`; EN — `values/strings.xml`, RU — `values-ru/strings.xml` (418 ключей, наборы обязаны совпадать). Не переводятся названия брендов, URL, логи и константы ошибок ViewModel/движка (`DownloadMessages`, `PlaybackErrorMessages`, `*_FAILED_MESSAGE`) — они приходят в UI готовыми строками (сессия 55).
- Поиск: `SearchHistoryStore` (SharedPreferences `search_history`, 8 записей) — чипсы «Недавние»; пустая выдача предлагает другие источники; карточка показывает длительность и жанр. Рекомендации: исключаются лайки/история/скачанное, дедуп по `(artist, title)`, волна фильтрует дизлайки, холодный старт — чарт SoundCloud, YouTube-поиск — последний фолбэк (сессия 53).

---

## Жёсткие правила

### Версии
- **НЕ менять** версии библиотек без явного указания пользователя
- **НЕ обновлять** AGP выше 8.5.2 (совместимость с текущей средой)
- Окружение: Windows, слабый ноут (i3-5005u, AMD radeon R7 M360, 8GB RAM). Не предлагать Android Studio и эмулятор. Установка APK — на реальный телефон через adb по WiFi

### Kotlin / Compose
- `@Composable`-функции **нельзя вызывать** внутри `remember { }` или лямбд без Composable-контекста
- `isSystemInDarkTheme()` вызывать **до** `remember { }`, результат класть в state
- `collectAsState()` только внутри `@Composable`

### Процесс разработки
- Перед правками **кратко озвучивать план** (какие файлы затрагиваются)
- **Собирать проект** (`.\gradlew assembleDebug`) после каждой фичи
- Если сборка падает **дважды подряд на одной ошибке** — остановиться и спросить
- При падении сборки: **самостоятельно** исправлять файлы и пересобирать (до 2 попыток на одну ошибку), не спрашивая пользователя
- В НАЧАЛЕ сессии: сверить PROGRESS.md с фактическим кодом и дополнить, если он устарел
- Если обновление PROGRESS.md не удалось или зациклилось — НЕ повторять, пропустить: доки обновятся в начале следующей сессии
- Комментарии в коде **отсутствуют**: правило «только на русском» заменено в сессии 69 рефакторинга — финальная фаза удаляет все комментарии из Kotlin-кода
- Все служебные файлы (`MEMORY.md`, `PROGRESS.md` и т.д.) и документация пишутся **строго на русском языке**
- Не трогать UI-компоненты при изменении бизнес-логики

### Архитектура
- Придерживаться MVVM + Clean: UI → ViewModel → Repository → DataSource
- Не класть бизнес-логику в `@Composable`-функции
- Репозитории работают с доменными моделями (`Track`), не с DTO

### Релиз и подпись
- `keystore.jks` и `keystore.properties` лежат в корне и **никогда** не коммитятся (`.gitignore`: `*.jks`, `/keystore.properties`). Без этого ключа обновления установленного приложения невозможны.
- **Секреты шифруются.** Токены Spotify и `spotify_client_secret` лежат только в `SecureStore` (`core/security/SecureStore.kt`): AES-256-GCM через Tink, набор ключей защищён Android Keystore (`android-keystore://facet_master_key`). Открытые значения прошлых версий переносятся одноразово и удаляются. `allowBackup=false` плюс `res/xml/backup_rules.xml` и `res/xml/data_extraction_rules.xml` исключают shared_prefs, БД, файлы и кэш из облака и переноса между устройствами.
- Release: `applicationId com.facet.music`, `versionName` вида `1.0.0`, `isMinifyEnabled` + `isShrinkResources`, правила — `app/proguard-rules.pro` (NewPipe/Rhino, kotlinx.serialization, Room, enum-константы).
- Deep link Spotify `facet://spotify-auth` должен совпадать с Redirect URI в Spotify Dashboard; при смене схемы обновляются манифест, `SPOTIFY_REDIRECT_URI` и `SpotifyPkceTest`.

### Отправка отчётов в Telegram
После того как ты выполнил сборку, тесты, коммит и пуш, ты ОБЯЗАН отправить свой подробный отчёт мне в Telegram.
Для отправки отчета бери токен из переменной окружения TG_TOKEN (при необходимости загрузи её из `.env` в корне проекта; файл в `.gitignore`).

curl.exe -s -X POST "https://api.telegram.org/bot$env:TG_TOKEN/sendMessage" --data-urlencode "chat_id=7944683834" --data-urlencode "parse_mode=Markdown" --data-urlencode "text=Твой подробный отчёт тут"

---

## Доменная модель трека

```kotlin
data class Track(
    val id: String,            // SC: URL трека; другие: собственный ID
    val source: Source,        // SOUNDCLOUD, SPOTIFY, YANDEX, VK, LOCAL
    val title: String,
    val artist: String,
    val artistUrl: String? = null,   // канал исполнителя для экрана артиста
    val artworkUrl: String?,
    val streamUrl: String?,    // null до вызова resolveStreamUrl()
    val durationMs: Long,      // миллисекунды
    val genre: String?,
    val liked: Boolean = false,
    val transcodingUrl: String? = null, // SC: URL страницы трека для resolve
    val externalId: String? = null,     // SC: числовой id для /tracks/{id}
    val isSubscriptionOnly: Boolean = false, // SoundCloud Go+
)
```

---

## Юридический контекст

Приложение использует неофициальные API и парсинг, что нарушает ToS большинства стриминговых сервисов. Распространяется **только как APK** через GitHub Releases / Telegram. Никогда не упоминать публикацию в Google Play, App Store или RuStore.

---

## Импорт плейлистов по ссылке и экран артиста (сессия 58)

- **Парсер** `core/import/PlaylistLinkParser.kt`: `PlaylistLinkParser.parse(link)` → `PlaylistLinkParseResult.Success(service, id)` или `Failure(ImportError, message)`. Поддержаны Spotify (`open.spotify.com|spotify.com/playlist/<id>`), Яндекс (`music.yandex.ru/users/<user>/playlists/<id>` и `/playlists/<id>`), Deezer (`deezer.com[/ru]/playlist/<id>`), YouTube (`list=`) и SoundCloud (`/sets/`). Ссылку можно вставлять без схемы, query-параметры (`?si=`, `utm_*`) игнорируются. Apple Music-ссылки после фазы 2 не поддерживаются. Модели — `core/import/PlaylistImportModels.kt` (`ImportService`, `ImportError`, `ImportedPlaylist`).
- **Загрузчик** `data/source/imports/PlaylistImportService.kt` (интерфейс + `PlaylistImportServiceImpl`, `@Singleton`, биндинг в `RepositoryModule`) — `load(link): PlaylistImportResult`. Пакет называется `imports`, потому что `import` — зарезервированное слово, и Java-код Hilt с таким пакетом не компилируется.
- **Spotify**: `SpotifyProvider.fetchPlaylist(id)` (название + треки: `/v1/playlists/{id}` и `/v1/playlists/{id}/tracks?limit=100`) и `fetchPlaylistTracks(id)`; Client Credentials и общий лимитер. Без ключей — `ImportError.SPOTIFY_NOT_CONFIGURED` без похода в сеть.
- **Deezer**: `api.deezer.com/playlist/{id}`, разбор `PlaylistPayloadParser.deezerPlaylist` (`title`, `tracks.data` → `Track(source=DEEZER, streamUrl=null, previewSeconds=null)`, `cover_big`, isrc). Ответ с `error` → ошибка сети с сообщением. Лимитер `@SourceRateLimiter(RateLimiterKeys.DEEZER)`.
- **Apple/iTunes**: удалён в фазе 2 вместе с `AppleMusicProvider` и `appleLookupTracks`; ссылки Apple Music теперь дают `UNSUPPORTED_LINK`. Яндекс — `YANDEX_UNSUPPORTED` (закрытый API).
- **UI**: вкладка «Плейлисты» в `LibraryScreen` — кнопка «Импортировать по ссылке» (`Icons.Rounded.AddLink`), `AlertDialog` со ссылкой, превью (название, счётчик, первые 10 треков «название — артист»), прогресс `LinearProgressIndicator`, ошибки и успех — Snackbar. Сохранение — `PlaylistRepository.createPlaylist/addTrack`; состояние — `PlaylistImportState` в `LibraryViewModel`, тексты ошибок выбираются по `ImportError` (`import_error_*`).
- **Экран артиста (историческая запись сессии 58):** `feature/artist/ArtistScreen.kt` + `ArtistViewModel` до фазы 7 работали на Deezer (`fetchArtistTopTracks`, `fetchArtistAlbums`, `fetchRelatedArtists`, `fetchAlbumTracks`). В фазе 7 экран переехал на YouTube/SoundCloud и `ArtistRepository`; старый маршрут `Screen.Artist(name)` остался только для поиска по имени. Входы: имя артиста в плеере и пункт «К артисту» в меню трека; после 7.6 клик открывает точный маршрут по `artistUrl`.
- **Строки**: `import_*`, `artist_*` в `values/strings.xml` и `values-ru/strings.xml` (395 ключей, наборы совпадают).
- **Тесты**: `PlaylistLinkParserTest` (15), `PlaylistPayloadParserTest` (6), `PlaylistImportServiceTest` (5); всего на 1.0.0 — 43 suite / 283 теста, 0 failures.

---

## UX-паттерны и что взяли (сессия 59)

- **Riff** (`rootscripts/riff`): не Flutter/Kotlin, а Electron 41 + чистый JS/HTML/CSS с MD3-токенами. Полезное: экран очереди с бейджем количества и «Clear queue», восстановление очереди после перезапуска и прогрев следующего трека, таймер сна с пресетами 15/30/45/60 + «End of track» и статусом «Off», hero-экран артиста с вкладками и «Popular Songs», компактный мини-плеер.
- **Dotify** (Flutter + sqflite, реверс): постоянная таблица `queue` с позицией (`insertQueueItem`), секции `now_playing`/`next`/`played` в очереди, `player.sleep_timer` с `player.timer.stops_at` (точное время остановки), контекст «Playing from» (`player.origin.*`), кастомизация мини-плеера и офлайн-бейджи.
- **Взяли в сессии 59**: (1) «Играть следующим»/«В очередь» — меню по долгому тапу `TrackCard` через необязательные `onPlayNext`/`onAddToQueue`, короткий тап не менялся; (2) подсветка играющего трека и автопрокрутка к нему на экранах артиста и плейлиста; (3) таймер сна в overflow плеера (`SleepTimerSheet`, 15/30/60, «Остановится в HH:mm»).
- **Не взяли осознанно**: секции «проигранное»/«Playing from» (нужны изменения `PlayerState`), вопросы кастомизации мини-плеера и настраиваемых свайпов строк — шире одного этапа.

## Медиатека, артист и Spotify-вход (сессия 60)

- **Медиатека — один экран без вкладок** (`LibraryScreen`): шапка с `CollectionSummary`, чипс «Любимые треки» (`CollectionChipCard`), секция «Плейлисты» (сетка `PlaylistTile` в две колонки, создание и импорт), секция «Скачанное». Лайки живут на отдельном маршруте `Screen.LikedTracks` (`liked`, док скрыт) в `LikedTracksScreen`. Общие компоненты коллекций — `core/ui/components/CollectionComponents.kt` (`SectionHeader`, `EmptyState`, `CollectionChipCard`, `PlaylistCover`, `PlaylistTile`); новые экраны коллекций собираются из них, а не из локальных копий.
- **Обложка плейлиста** — `Playlist.coverUrl` из подзапроса `PlaylistWithCount` (первый трек с картинкой). Схема Room не менялась; при отсутствии обложки `PlaylistCover` рисует детерминированный градиент от названия.
- **Экран артиста** — Deezer: `fetchArtist`, `fetchArtistAlbums` (год из `release_date`, `nb_tracks` в ответе нет), `fetchRelatedArtists`, `fetchAlbumTracks`. Топ Deezer может содержать один трек — список дополняется поиском по имени с фильтром по исполнителю (`artist:"…"` публичный API не поддерживает). Горизонтальные карусели — `Row` + `horizontalScroll`, не `LazyRow` (внутри `LazyColumn` он обрезал подпись под обложкой).
- **Spotify-вход (OAuth PKCE)** — `core/spotify/SpotifyPkce.kt`, `SpotifyAuth.kt` (`facet://spotify-auth`), `SpotifyAuthRepositoryImpl` (токены в `SecureStore`, refresh, профиль, `/v1/me/playlists`). Redirect URI **обязан** совпадать с Dashboard; вход не требует `client_secret`, секрет нужен только поиску без входа. `SpotifyProvider` предпочитает пользовательский токен Client Credentials и на 401 обновляет его.
- **Действия плеера** — старый вид до `6eaaaf0`: сквирклы «Скачать/Лайк/Дизлайк» над островом, пилюля «Текст песни» на острове. Кнопки текста в шапке больше нет: там «Сейчас играет» и имя артиста с мягким свечением (сессия 67, откат).

## Иконка, сплэш и онбординг (сессия 62)

- Иконка — адаптивная: `drawable-nodpi/ic_launcher_foreground.png` (фиолетовая форма с тремя треугольниками, 432×432, фигура отцентрована, фон `#141318`); фон `@color/ic_launcher_background` (#141318); `mipmap-anydpi-v26` и `mipmap-anydpi-v33`. Слой `<monochrome>` **убран намеренно**: Samsung с активной темой иконок перекрашивал фирменную фиолетовую иконку в зелёный монохром. Манифест: `android:icon` / `android:roundIcon`. Смена рисунка — только foreground PNG (сессия 68).
- Сплэш Android 12+ — framework-атрибуты в `values-v31/themes.xml` и `values-night-v31/themes.xml` (`windowSplashScreenBackground`, `windowSplashScreenAnimatedIcon`), без библиотеки core-splashscreen.
- Онбординг — `feature/onboarding/OnboardingScreen.kt`, показывается один раз по флагу `AppSettings.onboardingCompleted` (SharedPreferences `onboarding_completed`). `MainActivity` ждёт первого чтения настроек, поэтому при старте возможен короткий фон без UI. Кнопка «Начать» пишет флаг, поток настроек сам переключает на навграф.
- `POST_NOTIFICATIONS` запрашивается в `MainActivity` на Android 13+ после онбординга. `android:allowBackup="false"`.
- Лицензия — `LICENSE` (GPLv3, требование NewPipe Extractor), в настройках под версией кнопка «Лицензии» с диалогом компонентов.

## Аудио-фичи и сводный поиск (сессия 63)

- Детюн: `core/player/PlaybackTuning.kt` — частоты 428/430/432/440/448/450/453 Гц, `cents(f) = 1200·log2(f/440)`, `pitchFactor(f) = 2^(cents/1200)`. `PlaybackVariation` — NORMAL, SPEED_UP (1.25×/1.2), SLOWED (0.8×/0.85), NIGHTCORE (1.3×/1.3). Reverb в SLOWED не реализован: `PlaybackParameters` умеет только скорость и питч.
- `PlayerController.setTuning/setVariation` пересобирают параметры: `speed = playbackSpeed × variation.speed`, `pitch = variation.pitch × pitchFactor(tuningHz)`; `applyPlaybackParameters()` вызывается и при переподключении MediaController. В `PlayerState` — `tuningHz` и `variation`.
- Радио от трека: `WaveRepository.startRadio(seed)` создаёт сессию с `radioMode = true` (семена — артист/жанр трека + похожие артисты Last.fm, без квот лайков и истории); `PlayerViewModel.startWaveFromTrack()` играет первую пачку, дальше refill выполняет `PlaybackSessionCoordinator`.
- UI плеера: меню «Детюн» (AlertDialog со списком частот) и «Вариации» (ModalBottomSheet); под артистом строка «Источник: …». Кнопка текста — пилюля на `PlayerControlIsland`; в шапке её нет, там «Сейчас играет» и артист со свечением (сессия 67, откат).
- Сводный поиск: `core/search/TrackMerger.kt` — дедуп по нормализованным артисту/названию (скобочные пометки вырезаются), приоритет полного потока над превью, `Track.sourceCount`; `SearchViewModel.allSources` запускает параллельные запросы по всем `ServiceType` через `async/awaitAll`, ошибка одного источника не рушит выдачу. Чипс «Все источники» в `SearchScreen`, бейдж «+N» в `TrackCard`.

## Spotify, очередь и импорт ссылок (сессия 63, продолжение)

- Импорт любимых Spotify: `SpotifyProvider.fetchLikedTracks()` — `/v1/me/tracks` страницами по 50 (до 1000), нужен пользовательский токен; scope `user-library-read` добавлен в `SPOTIFY_SCOPES` (после обновления scopes нужен повторный вход). `LibraryViewModel.importSpotifyLikes()` дедуплицирует по существующим лайкам, `LibraryScreen` показывает чипс «Импортировать любимые»: без входа он неактивен и под ним подсказка «Требуется вход в Spotify».
- Очередь в Room: v13, таблица `queue` (`QueueEntity`, `QueueDao`, `MIGRATION_12_13`), схема `13.json`. `QueueRepositoryImpl` хранит треки, индекс и позицию текущего (индекс/позиция — в SharedPreferences `queue_state`). `PlayerController` восстанавливает очередь при старте без автоплея (`restoreQueueFromDb`), `PlaybackSessionCoordinator` сохраняет её каждые 10 с, на паузе и смене трека; `MediaSession.Callback.onPlaybackResumption` отдаёт сохранённую очередь системе.
- Шторка очереди: секции «Сейчас играет», «Далее», «Проиграно»; ключи элементов учитывают секцию.
- Импорт YouTube/SoundCloud: `PlaylistLinkParser` понимает `list=` у YouTube и `/sets/` у SoundCloud (id — канонический URL). `PlaylistImportService` грузит оба через NewPipe `PlaylistExtractor` (`service.playlistLHFactory.fromUrl` → `fetchPage` → `getPage(page.nextPage)`, до 5 страниц), `StreamInfoItem` → `Track` с `transcodingUrl = url`.
- Тесты: `QueueRepositoryTest` (4), `PlaylistLinkParserExtraTest` (5), миграции 12→13 и сквозная 1→13.

## Кэш, единый резолвер и ID3 (сессия 69, фаза 3)

- Кэш воспроизведения — Media3 `SimpleCache` в `cacheDir/media3_cache` (один на процесс, LRU 1 ГБ, `StandaloneDatabaseProvider`). `PlaybackCache` отдаёт размер и число ключей для настроек; `PlayerViewModel` больше не ставит `AudioCacheWorker` после 20 секунд.
- `PlaybackResolver` — единственная точка получения ссылки: скачанный файл → свой `PlayableSource` → матч SoundCloud/YouTube (`TrackMatcher`) → `resolved_matches` → in-memory `ResolvedStream` с TTL. Single-flight и типизированные `ResolveError`; повторный Play не отменяет первый резолв.
- `MusicService` строит `CacheDataSource` → `ResolvingDataSource` → `OkHttpDataSource` с `DefaultMediaSourceFactory`; URI элемента `facet://track/{encoded trackId}`, ключ кэша — id трека. `PlaybackLoadErrorPolicy` на 401/403/404/410 сбрасывает поток и повторяет (максимум 2 раза).
- Старый `audio_cache` удалён миграцией 15→16; `FacetApp` чистит каталог и задачи WorkManager с тегом `audio_cache`.
- Качество скачивания: `AppSettings.downloadQualityKbps` (128/256/320, по умолчанию 320), ключ `download_quality`; `YouTubeRepositoryImpl` выбирает M4A-поток, ближайший к выбранному битрейту. `DownloadSourceResolver` требует progressive-поток через `PlaybackResolver`.
- ID3: `core/download/Id3Writer.kt` — ID3v2.3, TIT2/TPE1/APIC, UTF-16 с BOM, старая шапка ID3 заменяется; запись потоковая (header+frames во временный файл, аудио копируется буфером), MIME обложки — по сигнатуре. `DownloadEngine` пишет теги только для `MimeTypes.AUDIO_MPEG` (MP3), обложка качается из `artworkUrl` (до 3 МБ); M4A/WebM остаются без тегов. Имя скачанного файла — SHA-256 от `trackId`.

## Редизайн и импорт (сессия 64)

- Fallback-палитра — `accentColorScheme(Color(0xFF3A4E6E), darkTheme)`; Dynamic Color и пользовательский акцент важнее. Скругления 12/16/24 в `AppShapes`, типографика M3 на Google Sans Flex.
- Главная: `WavePromoCard` (вкладка «Волна»), `NewReleasesBlock` (Deezer `/editorial/0/releases`, альбом грузится при нажатии, событие `HomeViewModel.openPlayer`), `ChartsBlock` (SoundCloud top50), `GenrePlaylistsBlock`, `ExtrasSkeleton`.
- Импорт: `TextPlaylistParser` (строка = трек, « - » делит артиста и название, без дефиса — поиск как есть), `ProfileLinkParser` (Spotify `/user/<id>`, SoundCloud `/<user>`), `PlaylistImportService.loadProfile` (Spotify `/users/{id}/playlists`, SoundCloud — вкладки канала NewPipe), экран `feature/imports/ImportScreen` с вкладками «Профиль/Список», вход из медиатеки (маршрут `import`).
- Релизные тексты: `CHANGELOG.md`, `docs/github-release-1.0.0.md`.

## Навигация и импорт SoundCloud (сессия 65)

- Переход с главной на «Волну» — как переключение вкладки: `navigate(Wave) { popUpTo(Home) { saveState = true }; launchSingleTop = true; restoreState = true }`. Иначе Wave остаётся поверх Home и back возвращает на неё.
- Лайки SoundCloud: публичная `/likes` не парсится (JS), API v2 требует `client_id`. `SoundCloudLikesProvider` резолвит профиль (`/resolve`), тянет `/users/{id}/likes` по 50 (все страницы); id хранится в `AppSettings.soundcloudClientId` (ключ `soundcloud_client_id`). OAuth не добавлен: нужен secret зарегистрированного приложения.
- Медиатека: импорт — ExtendedFAB над доком (`padding(bottom = LocalBottomDockHeight.current)`), не чипс в списке.

## Лайки SoundCloud через api-v2 (сессия 66)

- Официальный API SoundCloud требует приложения/Artist Pro — OAuth и client_secret не используются.
- `SoundCloudLikesProvider`: `client_id` из `SoundcloudParsingHelper.clientId()` (NewPipe), `/resolve` → `/users/{id}/track_likes` на `api-v2.soundcloud.com`, пагинация по `next_href` до 5000 треков (лимит 200 снят в сессии 67), порядок — как в SoundCloud (новые первыми). `SoundCloudLikesParser` разбирает `collection`/`next_href`, вложенный `track` и `created_at`.
- UI импорта: поле client_id убрано; «Загрузить лайки» → превью (5 строк + счётчик) → «Импортировать N лайков» с дедупом. Подпись: работает для публичных профилей, приватный — лайки недоступны. Импорт текстом остаётся запасным путём.

## Плоский редизайн и релиз 1.0.0 (сессия 67)

- **Плеер (после отката).** Дизайн возвращён к состоянию до `6eaaaf0`: `PlayerControlIsland` с прежним транспортом (скорость, пред, Play, след, очередь) и пилюлей текста; ряд действий с подписями и `PlayerActionItem` удалены. В шапке осталась одна строка «Сейчас играет» + артист со свечением, кнопки текста в шапке нет.
- **Слайдер.** Вернулась волна `ExpressivePlayerSlider` (Phase B), плоская дорожка 4dp отменена.
- **Медиатека.** `LargeTopAppBar` + `exitUntilCollapsedScrollBehavior` и `nestedScroll`; `LibraryHeader` удалён, `CollectionSummary` — первая строка списка. Сетка плейлистов 2 колонки с отступами 20/16, FAB поднят на `LocalBottomDockHeight + 12dp`, нижний `contentPadding` 96dp + док, чтобы кнопка не перекрывала последний элемент.
- **Настройки.** Плоский список: `SettingsSectionTitle` (`titleSmall`, `primary`), `SettingsDivider` (inset 20dp), `SettingsSwitchRow`; все `Surface`-карточки убраны, включая Spotify-секцию. Секции: Оформление, Язык, Интеграции, Разрешения, Загрузки и кэш (`settings_downloads_section`), Приватность. Секция «Моя волна» и RU-приоритет удалены в сессии 68.
- **Поиск.** `SearchSkeletonList` залит бегущим градиентом (`rememberShimmerBrush`, `infiniteRepeatable` 1200 мс) вместо статичных блоков.
- **Релиз.** 43 suite / 283 теста, 0 failures; EN/RU по 395 ключей; release APK 6 518 329 байт, SHA-256 `bd61317908792a0e6a92a585958990cb8649c7cd8dc64502e52b27fcd23e417d`; установлен по mDNS `adb-R5CY552H83H-nxMtL5`. Живой прогон — на пользователе.

## Сессия 67, продолжение: откат плеера и живые фиксы

- **Плеер откатан** к `6eaaaf0^`: волновой `ExpressivePlayerSlider`, старый остров (скорость/пред/Play/след/очередь) и сквирклы действий. В шапке только «Сейчас играет» + артист с `Shadow(coverAccent, blur 18f)`; кнопка текста одна — на острове.
- **Мини-плеер:** маленький крестик (`player_close_mini`) рядом с Play; `PlayerViewModel.clearQueue()` → `PlayerController.clearQueue()` останавливает MediaController, очищает очередь и Room. В `AppNavGraph` флаг `isMiniPlayerDismissed` скрывает островок, но оставляет `lastTrack` для exit-анимации; при новом треке сбрасывается.
- **Поиск:** кнопки источников 42dp с шагом 6dp, статус 40dp, поле 56dp; ряд сервисов скрыт при `allSources`; `ServiceHealth.isConfigured` (Spotify без ключей) делает кнопку неактивной. Клавиатура гасится в `AppNavGraph` на `LaunchedEffect(isPlayerOpen)` через `LocalFocusManager` + `LocalSoftwareKeyboardController`.
- **Импорт:** `ImportScreen` переписан на `TabRow` + MD3-карточки (`ImportCard`): профиль, лайки SoundCloud (только для SC), плейлисты, список. Ошибки лайков — `LikesError` + HTTP-код: `import_sc_likes_empty/private/api_error/network`.
- **SoundCloud likes:** реальный ответ api-v2 — элементы `collection` это лайки `{ "track": { … } }`; `SoundCloudLikesParser` разворачивает `track` и берёт `full_duration`. Проверено live: профиль `octobersveryown` → 13 лайков. 401/403 после успешного resolve → `SoundCloudLikesPrivateException` («профиль приватный»), 401 на resolve → HTTP-код в UI.
- **Навигация:** `onTabSelect` сначала `popBackStack(route, false)` (Import → Library), иначе `navigate` с `saveState/restoreState`; выбранная вкладка в доке теперь тоже кликабельна. `selectedRoute` для `import` подсвечивает «Библиотеку».
- **Волна от трека:** `startWaveFromTrack` ставит сид первым в очередь, затем похожие; `QueueOrigin` (`MAIN`/`WAVE`/`WAVE_FROM_TRACK`) в `PlayerState`, подпись в шапке `PlayerQueueSheet` («Обычная очередь»/«Моя волна»/«Волна по треку»). `WaveViewModel` помечает очередь `WAVE`.
- **Spotify:** с 11.02.2026 новый Client ID требует Premium, Development Mode — один Client ID и до 5 пользователей; тексты в настройках/README обновлены, кнопка Spotify без ключей неактивна.
- **Лайки SoundCloud, лимит и порядок:** `MAX_LIKES` поднят с 200 до 5000, пагинация идёт до `next_href = null`; в `SoundCloudLikesParser` появился `LikeItem.likedAtMillis` из `created_at`, `SoundCloudLikesProvider` отдаёт `likeTimes`. Импорт пишет реальную дату (`LikedTracksRepository.likeTrack(likedAt = …)`), а существующим трекам обновляет только `likedAt` через `updateLikeTime` — избранное сортируется по дате лайка, как в SoundCloud, без потери сезона/геометки. Повторный импорт синхронизирует и порядок старых 200.
- **Анимации (сессия 67, финал):** `animateItem()` в медиатеке, поиске и очереди; `AnimatedContent` для вкладок импорта; `AnimatedVisibility`/`animateContentSize` для FAB, превью лайков и карточек; FAB и Spotify-блок скрываются с fade/scale. Свайп-закрытие плеера не тронут.
- **Импортные входы:** `AppSettings.importCompleted` и `spotifyLikesImported` (ключи `import_completed`, `spotify_likes_imported`). FAB скрыт, если импорт уже был или в медиатеке есть лайки/плейлисты; Spotify-блок — только при подключённом Spotify и без импорта. Вход в импорт остаётся в заголовке «Плейлисты» → «Импорт».
- **Волна:** `buildSeedArtists`/`buildSeedGenres` считают частоту артистов/жанров в лайках (`LIKE_WEIGHT = 1.0`), история — `0.4`, Taste — `×0.5`; импортированные 200 лайков реально поднимают артиста в топ-4.
- **Свайп по обложке:** вверх — следующий трек, вниз — предыдущий (порог 56dp, HUD «Следующий/Предыдущий трек», вибро). `playPrevious(force = true)` игнорирует «>3 c → в начало». Корневой dismiss-жест пропускает зону обложки, но работает по шапке/фону; `artworkBounds` читается через `rememberUpdatedState`, а во время закрытия не обновляется — иначе смена bounds перезапускала жест и ломала закрытие.
- **Обложки:** `core/ui/artwork/ArtworkUrl.kt` — SoundCloud `-large`/`-crop` → `-t500x500`, YouTube → `maxresdefault`, Deezer → `1000x1000`, Apple → `600x600bb`; плеер грузит `size(1024)` с fallback на исходный URL, палитра считает по увеличенной версии.

## Сессия 68: новая иконка, статистика, HLS и карусель артиста (01.11.2026)

- **Иконка.** Адаптивная иконка — фиолетовая скруглённая форма с тремя треугольниками: `drawable-nodpi/ic_launcher_foreground.png` (432×432, фон `#141318`). Исходник — `1793479352436.jpg` пользователя (1024×1024): фигура была смещена вправо-вверх (центр 558×475 вместо 512×512), поэтому сдвинута к центру и уменьшена до safe zone (максимальный радиус 32.8dp при лимите 33dp). `<monochrome>` удалён: Samsung перекрашивал иконку в зелёный монохром. XML-призма удалена. `values/colors.xml` → `#141318`; сплэш Android 12+ использует `@mipmap/ic_launcher`.
- **RU-приоритет удалён.** Из настроек убрана секция «Моя волна», из `TasteRepository` — `isRussianPriority`/`setRussianPriority`. `WaveRepositoryImpl` больше не читает флаг: языковое смещение всегда выключено, `WaveSession.isRussianPriority` остаётся `false` для совместимости состояния. Русскоязычные тесты волны удалены.
- **Волна.** `LIKES_SHARE` снижен с `0.3` до `0.15`: при медиатеке 200+ треков лайки занимали 6 из 20 слотов и повторялись. Остальные квоты прежние: история 20%, жанры 10%, похожие — остаток.
- **Поддержка.** В настройках строка «Поддержать разработку» открывает `https://www.donationalerts.com/r/facetapp` в системном браузере; SDK и ключи не используются.
- **Артист.** «Популярные треки» — `HorizontalPager` страницами по 5 с точками-индикаторами; пустые слоты последней страницы держат высоту, автопрокрутка к играющему треку открывает нужную страницу (`index / 5`).
- **Анонимная статистика.** `AppSettings.analyticsEnabled` (ключ `analytics_enabled`, по умолчанию false), переключатель в секции «Приватность». `core/analytics/AnalyticsTracker` шлёт пакеты по 10 событий (`app_open`, `track_play`) на Supabase `POST /rest/v1/events`; поля только `event`, `install_id` (случайный UUID в `facet_analytics`), `app_version`; очередь до 50, отложенная отправка 8 с, при выключении очередь очищается. Ключ publishable, RLS разрешает только вставку.
- **HLS-only SoundCloud.** NewPipe `StreamInfo.getInfo` падает на треках без progressive MP3 (`Could not get any stream`). Для онлайн-плеера добавлен фолбэк `resolveHlsViaApi`: `api-v2/resolve` → `media.transcodings` → HLS-endpoint → подписанный `url`; выбирается максимальный пресет (`aac_160k` → `aac_96k` → `abr_sq`), MIME `application/x-mpegURL`. Для скачивания (`requireProgressive = true`) HLS по-прежнему запрещён. Проверено live: `type london` (onda andar) играет, лог `isHls=true`, `preset="aac_96k"`.
- **Мини-плеер на «Любимых».** `AppNavGraph` показывает островок и при скрытом доке: `isLikedTracksScreen` добавляется в условие `showMiniPlayer`; позиция островка считается от `WindowInsets.navigationBars`, а не от высоты скрытого дока (`miniPlayerBottom` анимируется). `LikedTracksScreen(bottomInset = miniPlayerReserve)` увеличивает нижний `contentPadding`, чтобы последний трек не уходил под островок. Настройки/плейлист/артист по-прежнему без мини-плеера.
- **Релиз.** `versionCode = 5`, `versionName = "1.0.0"`. Смена versionCode нужна, чтобы установить обновление поверх предыдущей сборки и обновить иконку в лончере. 43 suite / 281 тест, 0 failures; EN/RU по 395 ключей. На тестовом SM-A266B тема иконок Samsung подменяла Facet зелёным сгенерированным значком; для проверки `current_sec_appicon_theme_package` сброшен, иконка стала фиолетовой.
