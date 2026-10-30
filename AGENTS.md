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
│       └── soundcloud/  # SoundCloudRepositoryImpl (через NewPipe)
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

**БД:** Room v12, `exportSchema = true` (схемы в `app/schemas`), миграции собраны в `DatabaseMigrations.kt` (`ALL_MIGRATIONS`); каждая миграция покрыта изолированным тестом в `DatabaseMigrationTest` (базовые схемы v1–v3, не экспортированные ранее, восстановлены DDL-заглушками). Observe-методы скачанного проверяют файл на диске и вычищают мусорные записи из БД; сбои БД в лайках не крашат приложение (`runCatching` + `rethrowCancellation`, ошибка в UI). Дизлайки живут в `disliked_tracks` (`DislikedTrackDao`) и исключают треки из рекомендаций (`RecommendationPlanner.filterDisliked`). Кэш поиска — `search_cache` (PK `query`: `source`, сырой `jsonResponse`, `timestamp`); `SearchCacheRepository` отдаёт ответ моложе 24 ч, просроченные записи удаляются лениво при чтении и при каждой записи.

---

## Источники контента

| Источник | Метод | Версия |
|----------|-------|--------|
| **SoundCloud** | NewPipe Extractor | ✅ v1 |
| LRCLIB (тексты) | REST API (открытый) | ✅ v1 |
| Last.fm (похожие артисты) | Web API | ✅ v1 |
| Spotify | Web API OAuth 2.0 PKCE | v2 |
| Яндекс Музыка | Неофициальный API | v2 |
| ВК Музыка | VK API | v2 |
| YouTube Music | innertube | v2 |

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
- Кнопки под обложкой (`DownloadSquircle`/`LikeSquircle`/`DislikeSquircle`) получают цвета палитры плеера через `ExpressiveHeroZone` (`actionAccent`, `actionContainer`, `actionIdleContainer`): акцент `coverAccent`, контейнер `playerColors.primaryContainer`, подложка покоя `coverAccent.copy(alpha = 0.14f)`; всё анимируется 420 мс. При выключенных динамических цветах — акцент и контейнер темы, серая подложка `onSurface 8%`. Подпись режима («Текст песни»/«Обложка») красится `coverAccent` (сессия 52).
- Внешние источники: `core/source/MusicSourceProvider` (+ `MusicSourceRegistry`, регистрация `@IntoSet` в `core/di/MusicSourceModule`). `DeezerProvider` — `api.deezer.com/search` без авторизации, 30-сек MP3-превью + ISRC; `AppleMusicProvider` — `itunes.apple.com/search`, `previewUrl` M4A, `country=RU` для кириллицы. Полный трек у обоих невозможен (DRM/ARL) — полный стрим только через YouTube-матч. `StreamResolver`: прямая ссылка → `provider.resolve()` → `YouTubeMatcher` (порог 5.0, штрафы cover/remix/live, бонус official/topic, ISRC). `PlayerController` зовёт резолвер только для `Source.DEEZER`/`APPLE`. `ServiceType` = SOUNDCLOUD/YOUTUBE/DEEZER/APPLE и несёт `source`.
- Сеть внешних источников: `RateLimiter` (`awaitSlot()` / `acquire { }`, 1000 мс, `Mutex` + `delay` — главный поток не блокируется) по одному на источник через `@SourceRateLimiter` и `RateLimiterModule`; у Spotify лимитер общий для поиска и запроса токена. Успешный ответ поиска кэшируется сырым JSON в Room (`search_cache`, TTL 24 ч), ошибки сети не кэшируются, health-проверка идёт мимо кэша (`useCache = false`) — сессия 56.
- Кнопки источников в поиске — 5 иконок 48×48dp (радиус `MaterialTheme.shapes.medium`) в брендовых цветах и без горизонтального скролла: 5×48 + 4×10 = 280dp, кнопка статусов 46dp справа; иконки — VectorDrawable `ic_brand_*` из Simple Icons (CC0), у недоступного сервиса alpha 0.4, у активного фон 0.25 + рамка 2dp (сессия 55).
- Локализация: весь UI-текст — только `stringResource`/`pluralStringResource`; EN — `values/strings.xml`, RU — `values-ru/strings.xml` (310 ключей, наборы обязаны совпадать). Не переводятся названия брендов, URL, логи и константы ошибок ViewModel/движка (`DownloadMessages`, `PlaybackErrorMessages`, `*_FAILED_MESSAGE`) — они приходят в UI готовыми строками (сессия 55).
- Смена языка: `MainActivity` пересоздаёт Activity через `LaunchedEffect(settings.appLanguage)`, а `SettingsScreen` дополнительно показывает Snackbar «Язык изменён» с action «Перезапустить» (`findActivity()?.recreate()`) — сессия 55.
- Поиск: `SearchHistoryStore` (SharedPreferences `search_history`, 8 записей) — чипсы «Недавние»; пустая выдача предлагает другие источники; карточка показывает «превью 30 с». Рекомендации: исключаются лайки/история/скачанное, дедуп по `(artist, title)`, волна фильтрует дизлайки, холодный старт — чарт SoundCloud, YouTube-поиск — последний фолбэк (сессия 53).

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
- Комментарии в коде — **только на русском**
- Все служебные файлы (`MEMORY.md`, `PROGRESS.md` и т.д.) и документация пишутся **строго на русском языке**
- Не трогать UI-компоненты при изменении бизнес-логики

### Архитектура
- Придерживаться MVVM + Clean: UI → ViewModel → Repository → DataSource
- Не класть бизнес-логику в `@Composable`-функции
- Репозитории работают с доменными моделями (`Track`), не с DTO

### Релиз и подпись
- `keystore.jks` и `keystore.properties` лежат в корне и **никогда** не коммитятся (`.gitignore`: `*.jks`, `/keystore.properties`). Без этого ключа обновления установленного приложения невозможны.
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
    val artworkUrl: String?,
    val streamUrl: String?,    // null до вызова resolveStreamUrl()
    val durationMs: Long,      // миллисекунды
    val genre: String?,
    val liked: Boolean = false,
    val transcodingUrl: String? = null, // SC: URL страницы трека для resolve
)
```

---

## Юридический контекст

Приложение использует неофициальные API и парсинг, что нарушает ToS большинства стриминговых сервисов. Распространяется **только как APK** через GitHub Releases / Telegram. Никогда не упоминать публикацию в Google Play, App Store или RuStore.

---

## Импорт плейлистов по ссылке и экран артиста (сессия 58)

- **Парсер** `core/import/PlaylistLinkParser.kt`: `PlaylistLinkParser.parse(link)` → `PlaylistLinkParseResult.Success(service, id, trackId?)` или `Failure(ImportError, message)`. Поддержаны Spotify (`open.spotify.com|spotify.com/playlist/<id>`), Яндекс (`music.yandex.ru/users/<user>/playlists/<id>` и `/playlists/<id>`), Apple (`music.apple.com/.../playlist/.../pl.<uuid>`, а `?i=<numeric>` — отдельный трек), Deezer (`deezer.com[/ru]/playlist/<id>`). Ссылку можно вставлять без схемы, query-параметры (`?si=`, `utm_*`) игнорируются. Модели — `core/import/PlaylistImportModels.kt` (`ImportService`, `ImportError`, `ImportedPlaylist`).
- **Загрузчик** `data/source/imports/PlaylistImportService.kt` (интерфейс + `PlaylistImportServiceImpl`, `@Singleton`, биндинг в `RepositoryModule`) — `load(link): PlaylistImportResult`. Пакет называется `imports`, потому что `import` — зарезервированное слово, и Java-код Hilt с таким пакетом не компилируется.
- **Spotify**: `SpotifyProvider.fetchPlaylist(id)` (название + треки: `/v1/playlists/{id}` и `/v1/playlists/{id}/tracks?limit=100`) и `fetchPlaylistTracks(id)`; Client Credentials и общий лимитер. Без ключей — `ImportError.SPOTIFY_NOT_CONFIGURED` без похода в сеть.
- **Deezer**: `api.deezer.com/playlist/{id}`, разбор `PlaylistPayloadParser.deezerPlaylist` (`title`, `tracks.data` → `Track(source=DEEZER, streamUrl=preview, previewSeconds=30)`, `cover_big`, isrc). Ответ с `error` → ошибка сети с сообщением. Лимитер `@SourceRateLimiter(RateLimiterKeys.DEEZER)`.
- **Apple/iTunes**: `PlaylistPayloadParser.appleLookupTracks` для `itunes.apple.com/lookup?id={trackId}&entity=song` (только `wrapperType=track`, обложка 600×600, preview 30 с). Плейлист Apple (`pl.…`) без Apple Music API не импортируется — `APPLE_PLAYLIST_UNSUPPORTED`. Яндекс — `YANDEX_UNSUPPORTED` (закрытый API).
- **UI**: вкладка «Плейлисты» в `LibraryScreen` — кнопка «Импортировать по ссылке» (`Icons.Rounded.AddLink`), `AlertDialog` со ссылкой, превью (название, счётчик, первые 10 треков «название — артист»), прогресс `LinearProgressIndicator`, ошибки и успех — Snackbar. Сохранение — `PlaylistRepository.createPlaylist/addTrack`; состояние — `PlaylistImportState` в `LibraryViewModel`, тексты ошибок выбираются по `ImportError` (`import_error_*`).
- **Экран артиста**: `feature/artist/ArtistScreen.kt` + `ArtistViewModel`, маршрут `Screen.Artist(name)` (`ARTIST_NAME_ARG`, `Uri.encode`) в `AppNavGraph` (док скрыт). Популярные треки — `DeezerProvider.fetchArtistTopTracks(name)` (`search/artist` → `artist/{id}/top?limit=20`), при пустом ответе `YouTubeRepository.search("<artist> official audio")`; «Слушать всё» играет весь список. Входы: тап по имени артиста в `PlayerScreen` (закрывает оверлей плеера, воспроизведение не прерывается) и долгий тап по `TrackCard` (`onArtistClick`, `combinedClickable` только если колбэк передан) в поиске, медиатеке и плейлисте. С сессии 59 долгий тап открывает меню действий, а переход к артисту — его пункт «К артисту».
- **Строки**: `import_*`, `artist_*` в `values/strings.xml` и `values-ru/strings.xml` (310 ключей, наборы совпадают).
- **Тесты**: `PlaylistLinkParserTest` (14), `PlaylistPayloadParserTest` (6), `PlaylistImportServiceTest` (5); всего 30 suites / 212 тестов, 0 failures.

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
- **Spotify-вход (OAuth PKCE)** — `core/spotify/SpotifyPkce.kt`, `SpotifyAuth.kt` (`facet://spotify-auth`), `SpotifyAuthRepositoryImpl` (токены в SharedPreferences `spotify_auth`, refresh, профиль, `/v1/me/playlists`). Redirect URI **обязан** совпадать с Dashboard; вход не требует `client_secret`, секрет нужен только поиску без входа. `SpotifyProvider` предпочитает пользовательский токен Client Credentials и на 401 обновляет его.
- **Кнопка «Текст песни»** — в шапке плеера (`FilledTonalButton` 40dp), активный режим — `primaryContainer`; нижней подписи под островом и мёртвого `LyricsTab` больше нет.

## Иконка, сплэш и онбординг (сессия 62)

- Иконка — адаптивная: `drawable/ic_launcher_foreground.xml` (треугольник с медианой, safe zone 66dp) и `ic_launcher_monochrome.xml`; фон `@color/ic_launcher_background` (#111318); `mipmap-anydpi-v26` и `mipmap-anydpi-v33` (monochrome). Манифест: `android:icon` / `android:roundIcon`. Смена рисунка — только эти два drawable.
- Сплэш Android 12+ — framework-атрибуты в `values-v31/themes.xml` и `values-night-v31/themes.xml` (`windowSplashScreenBackground`, `windowSplashScreenAnimatedIcon`), без библиотеки core-splashscreen.
- Онбординг — `feature/onboarding/OnboardingScreen.kt`, показывается один раз по флагу `AppSettings.onboardingCompleted` (SharedPreferences `onboarding_completed`). `MainActivity` ждёт первого чтения настроек, поэтому при старте возможен короткий фон без UI. Кнопка «Начать» пишет флаг, поток настроек сам переключает на навграф.
- `POST_NOTIFICATIONS` запрашивается в `MainActivity` на Android 13+ после онбординга. `android:allowBackup="false"`.
- Лицензия — `LICENSE` (GPLv3, требование NewPipe Extractor), в настройках под версией кнопка «Лицензии» с диалогом компонентов.

## Аудио-фичи и сводный поиск (сессия 63)

- Детюн: `core/player/PlaybackTuning.kt` — частоты 428/430/432/440/448/450/453 Гц, `cents(f) = 1200·log2(f/440)`, `pitchFactor(f) = 2^(cents/1200)`. `PlaybackVariation` — NORMAL, SPEED_UP (1.25×/1.2), SLOWED (0.8×/0.85), NIGHTCORE (1.3×/1.3). Reverb в SLOWED не реализован: `PlaybackParameters` умеет только скорость и питч.
- `PlayerController.setTuning/setVariation` пересобирают параметры: `speed = playbackSpeed × variation.speed`, `pitch = variation.pitch × pitchFactor(tuningHz)`; `applyPlaybackParameters()` вызывается и при переподключении MediaController. В `PlayerState` — `tuningHz` и `variation`.
- Радио от трека: `WaveRepository.startRadio(seed)` создаёт сессию с `radioMode = true` (семена — артист/жанр трека + похожие артисты Last.fm, без квот лайков и истории); `PlayerViewModel.startWaveFromTrack()` играет первую пачку, дальше работает `observeWaveRefill`.
- UI плеера: меню «Детюн» (AlertDialog со списком частот) и «Вариации» (ModalBottomSheet); под артистом строка «Источник: …»; кнопка «Текст песни» на `PlayerControlIsland` (параметры `isLyricsMode`/`onLyricsClick`).
- Сводный поиск: `core/search/TrackMerger.kt` — дедуп по нормализованным артисту/названию (скобочные пометки вырезаются), приоритет полного потока над превью, `Track.sourceCount`; `SearchViewModel.allSources` запускает параллельные запросы по всем `ServiceType` через `async/awaitAll`, ошибка одного источника не рушит выдачу. Чипс «Все источники» в `SearchScreen`, бейдж «+N» в `TrackCard`.
