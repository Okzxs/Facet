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

**БД:** Room v14, `exportSchema = true` (схемы в `app/schemas`), миграции собраны в `DatabaseMigrations.kt` (`ALL_MIGRATIONS`); каждая миграция покрыта изолированным тестом в `DatabaseMigrationTest` (базовые схемы v1–v3, не экспортированные ранее, восстановлены DDL-заглушками). Observe-методы скачанного проверяют файл на диске и вычищают мусорные записи из БД; сбои БД в лайках не крашат приложение (`runCatching` + `rethrowCancellation`, ошибка в UI). Дизлайки живут в `disliked_tracks` (`DislikedTrackDao`) и исключают треки из рекомендаций (`RecommendationPlanner.filterDisliked`). Кэш поиска — `search_cache` (PK `query`: `source`, сырой `jsonResponse`, `timestamp`); `SearchCacheRepository` отдаёт ответ моложе 24 ч, просроченные записи удаляются лениво при чтении и при каждой записи.

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
- Кнопки под обложкой (`DownloadSquircle`/`LikeSquircle`/`DislikeSquircle`) получают цвета палитры плеера через `ExpressiveHeroZone` (`actionAccent`, `actionContainer`, `actionIdleContainer`): акцент `coverAccent`, контейнер `playerColors.primaryContainer`, подложка покоя `coverAccent.copy(alpha = 0.14f)`. В плеере одна кнопка текста — пилюля на `PlayerControlIsland`; в шапке её нет, там «Сейчас играет» и артист со свечением (сессия 67, откат).
- Внешние источники: `core/source/MusicSourceProvider` (+ `MusicSourceRegistry`, регистрация `@IntoSet` в `core/di/MusicSourceModule`). `DeezerProvider` — `api.deezer.com/search` без авторизации, 30-сек MP3-превью + ISRC; `AppleMusicProvider` — `itunes.apple.com/search`, `previewUrl` M4A, `country=RU` для кириллицы. Полный трек у обоих невозможен (DRM/ARL) — полный стрим только через YouTube-матч. `StreamResolver`: прямая ссылка → `provider.resolve()` → `YouTubeMatcher` (порог 5.0, штрафы cover/remix/live, бонус official/topic, ISRC). `PlayerController` зовёт резолвер только для `Source.DEEZER`/`APPLE`. `ServiceType` = SOUNDCLOUD/YOUTUBE/DEEZER/APPLE и несёт `source`.
- Сеть внешних источников: `RateLimiter` (`awaitSlot()` / `acquire { }`, 1000 мс, `Mutex` + `delay` — главный поток не блокируется) по одному на источник через `@SourceRateLimiter` и `RateLimiterModule`; у Spotify лимитер общий для поиска и запроса токена. Успешный ответ поиска кэшируется сырым JSON в Room (`search_cache`, TTL 24 ч), ошибки сети не кэшируются, health-проверка идёт мимо кэша (`useCache = false`) — сессия 56.
- Кнопки источников в поиске — 5 иконок 42×42dp (радиус `MaterialTheme.shapes.medium`) в брендовых цветах с шагом 6dp; кнопка статусов 40dp. При выбранном «Все источники» ряд скрыт. Spotify без ключей неактивен (`ServiceHealth.isConfigured`). Иконки — VectorDrawable `ic_brand_*` из Simple Icons (CC0), у недоступного сервиса alpha 0.4, у активного фон 0.25 + рамка 2dp (сессии 55, 67).
- Локализация: весь UI-текст — только `stringResource`/`pluralStringResource`; EN — `values/strings.xml`, RU — `values-ru/strings.xml` (395 ключей, наборы обязаны совпадать). Не переводятся названия брендов, URL, логи и константы ошибок ViewModel/движка (`DownloadMessages`, `PlaybackErrorMessages`, `*_FAILED_MESSAGE`) — они приходят в UI готовыми строками (сессия 55).
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
- **Spotify-вход (OAuth PKCE)** — `core/spotify/SpotifyPkce.kt`, `SpotifyAuth.kt` (`facet://spotify-auth`), `SpotifyAuthRepositoryImpl` (токены в SharedPreferences `spotify_auth`, refresh, профиль, `/v1/me/playlists`). Redirect URI **обязан** совпадать с Dashboard; вход не требует `client_secret`, секрет нужен только поиску без входа. `SpotifyProvider` предпочитает пользовательский токен Client Credentials и на 401 обновляет его.
- **Действия плеера** — старый вид до `6eaaaf0`: сквирклы «Скачать/Лайк/Дизлайк» над островом, пилюля «Текст песни» на острове. Кнопки текста в шапке больше нет: там «Сейчас играет» и имя артиста с мягким свечением (сессия 67, откат).

## Иконка, сплэш и онбординг (сессия 62)

- Иконка — адаптивная: `drawable-nodpi/ic_launcher_foreground.png` (фиолетовая форма с тремя треугольниками, 432×432, фон `#141318`) и `drawable-nodpi/ic_launcher_monochrome.png` (белая альфа-маска); фон `@color/ic_launcher_background` (#141318); `mipmap-anydpi-v26` и `mipmap-anydpi-v33` (monochrome). Манифест: `android:icon` / `android:roundIcon`. Смена рисунка — только эти две картинки (сессия 68).
- Сплэш Android 12+ — framework-атрибуты в `values-v31/themes.xml` и `values-night-v31/themes.xml` (`windowSplashScreenBackground`, `windowSplashScreenAnimatedIcon`), без библиотеки core-splashscreen.
- Онбординг — `feature/onboarding/OnboardingScreen.kt`, показывается один раз по флагу `AppSettings.onboardingCompleted` (SharedPreferences `onboarding_completed`). `MainActivity` ждёт первого чтения настроек, поэтому при старте возможен короткий фон без UI. Кнопка «Начать» пишет флаг, поток настроек сам переключает на навграф.
- `POST_NOTIFICATIONS` запрашивается в `MainActivity` на Android 13+ после онбординга. `android:allowBackup="false"`.
- Лицензия — `LICENSE` (GPLv3, требование NewPipe Extractor), в настройках под версией кнопка «Лицензии» с диалогом компонентов.

## Аудио-фичи и сводный поиск (сессия 63)

- Детюн: `core/player/PlaybackTuning.kt` — частоты 428/430/432/440/448/450/453 Гц, `cents(f) = 1200·log2(f/440)`, `pitchFactor(f) = 2^(cents/1200)`. `PlaybackVariation` — NORMAL, SPEED_UP (1.25×/1.2), SLOWED (0.8×/0.85), NIGHTCORE (1.3×/1.3). Reverb в SLOWED не реализован: `PlaybackParameters` умеет только скорость и питч.
- `PlayerController.setTuning/setVariation` пересобирают параметры: `speed = playbackSpeed × variation.speed`, `pitch = variation.pitch × pitchFactor(tuningHz)`; `applyPlaybackParameters()` вызывается и при переподключении MediaController. В `PlayerState` — `tuningHz` и `variation`.
- Радио от трека: `WaveRepository.startRadio(seed)` создаёт сессию с `radioMode = true` (семена — артист/жанр трека + похожие артисты Last.fm, без квот лайков и истории); `PlayerViewModel.startWaveFromTrack()` играет первую пачку, дальше работает `observeWaveRefill`.
- UI плеера: меню «Детюн» (AlertDialog со списком частот) и «Вариации» (ModalBottomSheet); под артистом строка «Источник: …». Кнопка текста — пилюля на `PlayerControlIsland`; в шапке её нет, там «Сейчас играет» и артист со свечением (сессия 67, откат).
- Сводный поиск: `core/search/TrackMerger.kt` — дедуп по нормализованным артисту/названию (скобочные пометки вырезаются), приоритет полного потока над превью, `Track.sourceCount`; `SearchViewModel.allSources` запускает параллельные запросы по всем `ServiceType` через `async/awaitAll`, ошибка одного источника не рушит выдачу. Чипс «Все источники» в `SearchScreen`, бейдж «+N» в `TrackCard`.

## Spotify, очередь и импорт ссылок (сессия 63, продолжение)

- Импорт любимых Spotify: `SpotifyProvider.fetchLikedTracks()` — `/v1/me/tracks` страницами по 50 (до 1000), нужен пользовательский токен; scope `user-library-read` добавлен в `SPOTIFY_SCOPES` (после обновления scopes нужен повторный вход). `LibraryViewModel.importSpotifyLikes()` дедуплицирует по существующим лайкам, `LibraryScreen` показывает чипс «Импортировать любимые»: без входа он неактивен и под ним подсказка «Требуется вход в Spotify».
- Очередь в Room: v13, таблица `queue` (`QueueEntity`, `QueueDao`, `MIGRATION_12_13`), схема `13.json`. `QueueRepositoryImpl` хранит треки и индекс текущего (индекс — в SharedPreferences `queue_state`). `PlayerController` восстанавливает очередь при старте без автоплея (`restoreQueueFromDb`) и пишет изменения с debounce 500 мс (`persistQueue`); первый Play на восстановленной очереди запускает трек, потому что в контроллере ещё нет медиаэлемента.
- Шторка очереди: секции «Сейчас играет», «Далее», «Проиграно»; ключи элементов учитывают секцию.
- Импорт YouTube/SoundCloud: `PlaylistLinkParser` понимает `list=` у YouTube и `/sets/` у SoundCloud (id — канонический URL). `PlaylistImportService` грузит оба через NewPipe `PlaylistExtractor` (`service.playlistLHFactory.fromUrl` → `fetchPage` → `getPage(page.nextPage)`, до 5 страниц), `StreamInfoItem` → `Track` с `transcodingUrl = url`.
- Тесты: `QueueRepositoryTest` (4), `PlaylistLinkParserExtraTest` (5), миграции 12→13 и сквозная 1→13.

## Кэш, качество и ID3 (сессия 63, финал)

- Кэш прослушанного: Room v14, `audio_cache` (`AudioCacheEntity`/`AudioCacheDao`, `MIGRATION_13_14`). Файлы — в `cacheDir/audio_cache`, имя по хэшу id; `AudioCacheRepositoryImpl.fileFor` проверяет диск, чистит мёртвые записи и обновляет `lastAccessAt`; `enforceLimit` вытесняет LRU до 500 МБ. `AudioCacheWorker` резолвит поток заново (ссылки протухают), HLS не кэширует. `PlayerViewModel.observeAudioCache()` ставит трек в очередь после 20 секунд; `StreamResolver` проверяет кэш первым.
- Качество скачивания: `AppSettings.downloadQualityKbps` (128/256/320, по умолчанию 320), ключ `download_quality`; `YouTubeRepositoryImpl` выбирает M4A-поток, ближайший к выбранному битрейту.
- ID3: `core/download/Id3Writer.kt` — ID3v2.3, TIT2/TPE1/APIC, UTF-16 с BOM, старая шапка ID3 заменяется. `DownloadEngine` пишет теги только для `MimeTypes.AUDIO_MPEG` (MP3), обложка качается из `artworkUrl` (до 3 МБ).

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

- **Иконка.** Адаптивная иконка — фиолетовая скруглённая форма с тремя треугольниками: `drawable-nodpi/ic_launcher_foreground.png` (432×432, фон `#141318`) и `drawable-nodpi/ic_launcher_monochrome.png` (белая альфа-маска для themed icons). Исходник — `14207.jpg` пользователя (432×432): фигура отцентрована и увеличена относительно первой версии. XML-призма удалена. `values/colors.xml` → `#141318`; сплэш Android 12+ использует `@mipmap/ic_launcher`.
- **RU-приоритет удалён.** Из настроек убрана секция «Моя волна», из `TasteRepository` — `isRussianPriority`/`setRussianPriority`. `WaveRepositoryImpl` больше не читает флаг: языковое смещение всегда выключено, `WaveSession.isRussianPriority` остаётся `false` для совместимости состояния. Русскоязычные тесты волны удалены.
- **Волна.** `LIKES_SHARE` снижен с `0.3` до `0.15`: при медиатеке 200+ треков лайки занимали 6 из 20 слотов и повторялись. Остальные квоты прежние: история 20%, жанры 10%, похожие — остаток.
- **Поддержка.** В настройках строка «Поддержать разработку» открывает `https://www.donationalerts.com/r/facetapp` в системном браузере; SDK и ключи не используются.
- **Артист.** «Популярные треки» — `HorizontalPager` страницами по 5 с точками-индикаторами; пустые слоты последней страницы держат высоту, автопрокрутка к играющему треку открывает нужную страницу (`index / 5`).
- **Анонимная статистика.** `AppSettings.analyticsEnabled` (ключ `analytics_enabled`, по умолчанию false), переключатель в секции «Приватность». `core/analytics/AnalyticsTracker` шлёт пакеты по 10 событий (`app_open`, `track_play`) на Supabase `POST /rest/v1/events`; поля только `event`, `install_id` (случайный UUID в `facet_analytics`), `app_version`; очередь до 50, отложенная отправка 8 с, при выключении очередь очищается. Ключ publishable, RLS разрешает только вставку.
- **HLS-only SoundCloud.** NewPipe `StreamInfo.getInfo` падает на треках без progressive MP3 (`Could not get any stream`). Для онлайн-плеера добавлен фолбэк `resolveHlsViaApi`: `api-v2/resolve` → `media.transcodings` → HLS-endpoint → подписанный `url`; выбирается максимальный пресет (`aac_160k` → `aac_96k` → `abr_sq`), MIME `application/x-mpegURL`. Для скачивания (`requireProgressive = true`) HLS по-прежнему запрещён. Проверено live: `type london` (onda andar) играет, лог `isHls=true`, `preset="aac_96k"`.
- **Мини-плеер на «Любимых».** `AppNavGraph` показывает островок и при скрытом доке: `isLikedTracksScreen` добавляется в условие `showMiniPlayer`; позиция островка считается от `WindowInsets.navigationBars`, а не от высоты скрытого дока (`miniPlayerBottom` анимируется). `LikedTracksScreen(bottomInset = miniPlayerReserve)` увеличивает нижний `contentPadding`, чтобы последний трек не уходил под островок. Настройки/плейлист/артист по-прежнему без мини-плеера.
- **Релиз.** `versionCode = 3`, `versionName = "1.0.0"`. Смена versionCode нужна, чтобы установить обновление поверх предыдущей сборки. 43 suite / 281 тест, 0 failures; EN/RU по 395 ключей.
