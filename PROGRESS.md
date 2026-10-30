# PROGRESS.md вЂ” РСЃС‚РѕСЂРёСЏ СЂР°Р·СЂР°Р±РѕС‚РєРё

## Последнее обновление: 30.10.2026 (сессия 62)

## Иконка, сплэш, онбординг и лицензии — сессия 62

### 1. Иконка «Грань»
- Адаптивная иконка: `drawable/ic_launcher_foreground.xml` (треугольник с медианой, фигура целиком в safe zone 66dp), фон `@color/ic_launcher_background` (#111318), `mipmap-anydpi-v26/ic_launcher.xml` и `ic_launcher_round.xml`. На Android 13+ — `mipmap-anydpi-v33` с `<monochrome>` (`ic_launcher_monochrome.xml`) для тематических иконок.
- Манифест: `android:icon` и `android:roundIcon`; лейбл — `@string/app_name`.
- Три варианта («Грань», «Призма», «Кристалл») отрисованы в PNG и отправлены в Telegram. В приложении пока «Грань» — самый минималистичный; замена — только `ic_launcher_foreground.xml` и `ic_launcher_monochrome.xml`.

### 2. Сплэш
- `values-v31/themes.xml` и `values-night-v31/themes.xml`: `windowSplashScreenBackground` = `window_background`, `windowSplashScreenAnimatedIcon` = `ic_launcher_foreground`. Новых зависимостей нет — только framework-атрибуты Android 12+.

### 3. Онбординг первого запуска
- `feature/onboarding/OnboardingScreen.kt` + `OnboardingViewModel`: знак в кружке `primaryContainer`, название, слоган, три возможности (все источники, офлайн, волна) и кнопка «Начать».
- `AppSettings.onboardingCompleted` (SharedPreferences `onboarding_completed`), `SettingsRepository.setOnboardingCompleted`; `MainActivity` показывает онбординг, пока флаг не выставлен, и ждёт первого чтения настроек (`settingsLoaded`) — иначе экран мигал бы на каждом холодном старте.
- Строки: 8 ключей `onboarding_*` в EN и RU.

### 4. Уведомления и бэкап
- `MainActivity`: запрос `POST_NOTIFICATIONS` на Android 13+ после онбординга через `rememberLauncherForActivityResult`.
- `android:allowBackup="false"` — токены и настройки не уходят в облачный бэкап.

### 5. Лицензия
- `LICENSE` — GNU GPLv3 (требование NewPipe Extractor).
- В настройках под версией — кнопка «Лицензии»: диалог со списком компонентов и ссылкой на LICENSE (5 ключей `settings_licenses_*`).

### Проверки
- `assembleDebug testDebugUnitTest` — BUILD SUCCESSFUL, 33 suites / 235 тестов, 0 failures (было 233): +2 теста онбординга в `SettingsRepositoryImplTest`.
- `assembleRelease` — BUILD SUCCESSFUL (6m02s), release APK 6 381 849 байт, подпись прежняя (CN=Facet Music).
- `aapt dump badging`: label `Facet`, adaptive icon `res/IG.xml`, package `com.facet.music` 1.0.0.
- Установлено на SM-A266B (`192.168.1.106:42099`): Success, 1.0.0.
- Не проверено вживую (телефон занят): иконка на лончере, онбординг, запрос уведомлений, диалог лицензий.

## Переезд на Facet, release-подпись и R8 — сессия 61

### 1. Переименование
- Пакет `com.my.music` → `com.facet.music` в main/test и в `app/schemas` (`com.facet.music.data.local.AppDatabase`), `namespace` и `applicationId` — `com.facet.music`, `rootProject.name` — `Facet`.
- Классы: `MyMusicApp` → `FacetApp`, `MyMusicTheme` → `FacetTheme`, `Theme.MyMusic` → `Theme.Facet`; SharedPreferences `mymusic_settings` → `facet_settings`, БД `mymusic.db` → `facet.db`.
- Deep link Spotify: `mymusic://spotify-auth` → `facet://spotify-auth` (манифест, `SPOTIFY_REDIRECT_URI`, тест PKCE). **В Spotify Dashboard Redirect URI нужно заменить на `facet://spotify-auth`.**
- Название: `app_name` = Facet в `values/` и `values-ru/`, лейбл манифеста — `@string/app_name`, `settings_version` → «Facet · version …», User-Agent `Facet/1.0`.
- `versionName` = `1.0.0`, `versionCode` = 1. Старая установка `com.my.music` остаётся отдельным приложением, данные не переносятся — релиза ещё не было.

### 2. Release-подпись
- Сгенерирован `keystore.jks` (RSA 4096, alias `facet`, срок 10 000 дней) и `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Оба файла в `.gitignore` (`*.jks` и `/keystore.properties`), в репозиторий не попадают.
- `app/build.gradle.kts`: `signingConfigs.release` читает `keystore.properties`; `buildTypes.release` — `isMinifyEnabled = true`, `isShrinkResources = true`, `signingConfig` подключается только при наличии файла (на чистой машине release соберётся неподписанным, но не упадёт).
- Проверка `apksigner`: CN=Facet Music, SHA-256 `636d280cd3156f4bd1fe19ba36dc997d21d4ab3ab37b0a93419926a2eb7404c3`.

### 3. R8
- `app/proguard-rules.pro`: `-dontwarn java.beans.**` и `javax.script.**` (Rhino из NewPipe), keep `org.schabi.newpipe.extractor.**` и `com.grack.nanojson.**`, правила kotlinx.serialization (`**$$serializer`, `Companion`, `KSerializer serializer(...)`), Room (`RoomDatabase`, `@Entity`), сохранение имён enum-констант (иначе Room-конвертеры и `valueOf` ломаются на обфускации), `-dontwarn` для OkHttp/Okio TLS-провайдеров, Media3, Coil, Hilt, `SourceFile`/`LineNumberTable` для читаемых стектрейсов.
- `assembleRelease` — BUILD SUCCESSFUL (9m33s), `lintVitalRelease` прошёл.

### Проверки
- `clean assembleDebug testDebugUnitTest` — BUILD SUCCESSFUL (6m23s), 33 suites / 233 теста, 0 failures/errors/skipped.
- Debug APK — 27 939 639 байт; release APK — 6 354 929 байт (`app/build/outputs/apk/release/app-release.apk`).
- `aapt dump badging`: package `com.facet.music`, versionName `1.0.0`, label `Facet`, minSdk 26 / targetSdk 35.
- Установлено на SM-A266B (`192.168.1.106:42099`): `Success`, в системе `com.facet.music` 1.0.0.
- Вживую не проверялось (телефон занят): первый запуск release-сборки после R8, Spotify-вход с новым Redirect URI.

## Медиатека, карточка артиста, вход в Spotify и кнопка текста — сессия 60

### 1. Медиатека одним экраном
- Вкладки «Медиатека / Скачанное / Плейлисты» удалены: `LibraryScreen` — один `LazyColumn`. Сверху шапка «Медиатека» с подписью `CollectionSummary` («N треков · M плейлистов»), затем крупный чипс «Любимые треки» (`CollectionChipCard`: градиент `primaryContainer → secondaryContainer`, сердце в кружке, счётчик и суммарная длительность), секция «Плейлисты» (сетка в две колонки из `PlaylistTile`, карточка пустого состояния с «Создать плейлист»/«Импорт», диалоги создания и импорта по ссылке) и секция «Скачанное» (строки `TrackCard` с удалением файла). Низ списка распирает `LocalBottomDockHeight`.
- Новый экран `LikedTracksScreen` (маршрут `liked`, нижний док скрыт): hero-плитка 104dp с сердцем, название, счётчик и длительность, «Слушать всё» и иконка «Перемешать» (подпись кнопки переносилась на вторую строку, поэтому перемешивание — `FilledTonalIconButton` 52dp), список лайков с лайком, скачиванием и меню по долгому тапу.
- Общие компоненты `core/ui/components/CollectionComponents.kt`: `SectionHeader`, `EmptyState`, `CollectionChipCard`, `PlaylistCover`, `PlaylistTile`, `CollectionSummary` — единый ритм заголовков, отступов и пустых состояний на всех экранах коллекций.
- Обложка плейлиста: в проекцию `PlaylistWithCount` добавлен `coverUrl` — подзапрос за обложкой первого трека с картинкой; схема Room не менялась, миграция не нужна.
- `PlaylistScreen` получил шапку с обложкой 104dp, названием и счётчиком, кнопки «Слушать всё»/«Перемешать», удаление плейлиста через overflow → подтверждение → `deleted` → возврат в медиатеку (удаление перенесено с плитки на экран).

### 2. Карточка артиста
- Deezer: `fetchArtist` (`nb_fan`, `nb_album`, `picture_xl`), `fetchArtistAlbums` (год берётся из `release_date`: `nb_tracks` в ответе `/artist/{id}/albums` отсутствует), `fetchRelatedArtists`, `fetchAlbumTracks` (`/album/{id}` → `tracks.data`, один запрос на альбом).
- Топ Deezer у части артистов содержит один трек (у Daft Punk `total: 1`), поэтому при меньше чем пяти треках список дополняется поиском `q=<артист>` с фильтром по имени исполнителя. Расширенный синтаксис `artist:"…"` публичный API не поддерживает — возвращает пустой ответ.
- Экран: иммерсивная шапка (размытый аватар-задник `blur(56dp)`, градиентный скрим до цвета фона), круглая аватарка 124dp, имя, статистика «5.2M слушателей · 34 альбома», кнопки «Слушать всё» и «Перемешать». Секции: «Популярные треки», «Альбомы» (карусель, тап играет альбом целиком), «Похожие артисты» (Deezer related, переход на экран артиста).
- Горизонтальные ряды сделаны `Row` + `horizontalScroll`, а не `LazyRow`: внутри `LazyColumn` ленивый ряд обрезал подпись под обложкой. `Modifier.clip` на карточке альбома срезал первую цифру года — клип убран.
- Автопрокрутка к играющему треку сохранена; шапка и заголовок секции занимают первые два элемента списка.

### 3. Вход в Spotify (OAuth 2.0 + PKCE)
- `core/spotify/SpotifyPkce.kt`: verifier 64 символа, `challenge` = BASE64URL(SHA-256) без паддинга, `state`; в тестах сверяется с вектором RFC 7636.
- `core/spotify/SpotifyAuth.kt`: `SPOTIFY_REDIRECT_URI = facet://spotify-auth`, scopes (`user-read-private user-read-email playlist-read-private playlist-read-collaborative`), сборка URL согласия.
- `SpotifyAuthRepositoryImpl`: обмен кода на токены, refresh при истечении, профиль `/v1/me`, `/v1/me/playlists`, токены в SharedPreferences `spotify_auth`, `accessToken(forceRefresh)` под `Mutex`.
- `MainActivity`: `launchMode="singleTop"`, intent-filter на `facet://spotify-auth`, обработка в `onCreate` и `onNewIntent`, тост об успехе/ошибке. Deep link проверен: приложение открывается, при неверном `state` показывает «Не удалось войти в Spotify».
- `SpotifyProvider`: пользовательский токен приоритетнее Client Credentials, на 401 — refresh и один повтор; `isConfigured` учитывает вход. С входом поиск и личные плейлисты работают без `client_secret`.
- Настройки: карточка Spotify с кнопкой «Инструкция», статусом, полями ключей, кнопкой входа/выхода, подсказкой Redirect URI и предупреждением о квоте. `SpotifyGuideDialog` — четыре шага (создать приложение, заполнить форму, скопировать ключи, вставить), кнопка «Открыть Dashboard» и пояснение про DRM.
- Медиатека: после входа появляется секция «Плейлисты Spotify» (карусель обложек), тап открывает превью импорта — переиспользуется обычный разбор ссылки, сохранение идёт тем же путём.

### 4. Кнопка текста в плеере
- Режим «Текст песни» переехал в шапку плеера: `FilledTonalButton` высотой 40dp с иконкой `Lyrics`/`Album` и подписью, в активном режиме — `primaryContainer`. Нижняя подпись под островом управления удалена, мёртвый `LyricsTab` и неиспользуемые параметры `PlayerControlIsland` вычищены.

### Проверки
- `assembleDebug` — BUILD SUCCESSFUL, APK 28 078 112 байт.
- `testDebugUnitTest` — 33 suites / 233 теста, 0 failures/errors/skipped (было 32/228): добавлен `SpotifyPkceTest` (5 кейсов, включая вектор RFC 7636).
- Строки: 299 ключей в EN и 299 в RU, наборы совпадают, дублей нет.
- Установлено на телефон `192.168.1.106:42099` (SM-A266B). Вживую проверено: медиатека и чипс любимых, создание и удаление плейлиста, экран артиста (Daft Punk: 5.2M слушателей, 34 альбома, карусель альбомов, похожие артисты), кнопка «Текст песни» и режим текста, инструкция Spotify, deep link возврата.
- Не проверено: реальный вход Spotify и строка «Плейлисты Spotify» (нет Client ID и аккаунта), импорт по ссылке (код не менялся).

## Последнее обновление: 30.10.2026 (сессия 59)

## UX-улучшения по мотивам Riff и Dotify — сессия 59

### Что изучали
- **Riff** (`rootscripts/riff`, клон в TEMP): это не Flutter и не Kotlin — Electron 41 + чистый JS/HTML/CSS (`riff` 43.2.2, `src/main/main.js`, `src/renderer/app.js` ~25 000 строк, `styles.css` ~10 600 строк, MD3-токены в CSS, hls.js и Tone.js). Находки: (1) отдельный экран очереди с бейджем количества на пункте навигации, заголовком «Playback queue», кнопкой «Clear queue» и подсказкой при пустой очереди; (2) очередь переживает перезапуск (`restoreQueue`, `[PlayerProvider] Restoring queue`), хранит историю индексов и заранее прогревает следующий трек (`prefetchNextQueueTrack`); (3) таймер сна в аудио-настройках: статус «Off», чипсы 15/30/45/60 мин, «End of track», «Turn off» и подсказка «Fades out and pauses when the timer runs out»; (4) экран артиста — hero-обложка, галочка verified, Follow/Block, блок About с «Show all», горизонтальные вкладки «Треки/Альбомы» и «Popular Songs»; (5) компактный мини-плеер из заголовка окна и таймлайн в плейбаре.
- **Dotify** (готовый реверс в `%TEMP%\dotify`, Flutter + sqflite): (1) постоянная таблица `queue` с позицией (`idx_queue_position`, `addQueueItem`/`insertQueueItem`) — очередь с ручной вставкой; (2) секции очереди `player.queue.now_playing` / `.next` / `.played` / `.clear` / `.empty` — видно не только «сейчас» и «дальше», но и проигранное; (3) таймер сна `player.sleep_timer`, `player.timer.start/.cancel/.min/.stops_at` — показывается точное время остановки; (4) контекст «Playing from» (`player.origin.album/playlist/search/wave/queue`); (5) кастомизация мини-плеера (`settings.mini_player.progress` — line/cover/background, пресеты standard/minimal/pill/deck), офлайн-бейджи (`common.offline`, «Download to cache»/«Download to local») и настраиваемые свайпы строки («Configure track swipe actions»).

### Что сделали (3 улучшения)
1. **«Играть следующим» / «В очередь» в списках** — `TrackCard` получил необязательные `onPlayNext`/`onAddToQueue`: при их наличии долгий тап открывает M3-меню (пункты «Играть следующим», «В очередь», «К артисту»), короткий тап не менялся. Чистая логика вставки — `core/player/QueueInsertion.kt` (`insertNextTrack`: вставка сразу после текущего, перенос дубликата с пересчётом индекса, игнор пустого id). `PlayerController.playNext(track)`/`addToQueue(track)` возвращают Boolean, `PlayerViewModel` пробрасывает их, `AppNavGraph` раздаёт колбэки в поиск, медиатеку (обе вкладки), плейлист и артиста; подтверждение — Snackbar («Играет следующим…», «Добавлено в очередь…», «Уже в очереди…»). Файлы: `TrackCard.kt`, `core/player/QueueInsertion.kt`, `PlayerController.kt`, `PlayerViewModel.kt`, `AppNavGraph.kt`, `SearchScreen.kt`, `LibraryScreen.kt`, `PlaylistScreen.kt`, `ArtistScreen.kt`.
2. **«Сейчас играет» на экране артиста + автопрокрутка** — `ArtistViewModel` получил `PlayerController` и потоки `currentTrackId`/`isPlaying`; играющая строка подсвечивается и получает эквалайзер (пауза — три точки), при открытии экран однократно прокручивается к текущему треку, как шторка очереди. Та же автопрокрутка добавлена в `PlaylistScreen` (`rememberLazyListState` + `LaunchedEffect(currentTrackId, tracks)`), где подсветка уже была. Файлы: `ArtistViewModel.kt`, `ArtistScreen.kt`, `PlaylistScreen.kt`.
3. **Таймер сна в overflow-меню плеера** — пункт «Таймер сна» (при активном таймере «Таймер сна · до HH:mm») открывает новый переиспользуемый `SleepTimerSheet` в `core/ui/components`: 15/30/60 мин, отметка выбранного, «Отключить таймер», подпись «Остановится в HH:mm · осталось N мин». `core/player/SleepTimer.kt` — чистые функции `sleepTimerEndTime`/`sleepTimerRemainingMinutes` и `SleepTimerState`; `PlayerViewModel.startSleepTimer`/`cancelSleepTimer` держат job и по истечении зовут новый `PlayerController.pause()` (пауза без переключения). Подтверждения — Snackbar. Файлы: `core/player/SleepTimer.kt`, `SleepTimerSheet.kt`, `PlayerViewModel.kt`, `PlayerScreen.kt`, `PlayerController.kt`.

### Проверки
- `assembleDebug` — BUILD SUCCESSFUL, APK 27 976 672 байт.
- `testDebugUnitTest` — 32 suites / 228 тестов, 0 failures/errors/skipped (было 30/212): `QueueInsertionTest` (6), `SleepTimerTest` (5), 5 новых кейсов в `PlayerControllerQueueTest`.
- Строки: 260 ключей в EN и 260 в RU, наборы совпадают, дублей нет.
- Установка на телефон **не выполнена**: `adb devices` пуст, `adb mdns services` пуст, известные адреса 192.168.1.106:44537 и 192.168.0.102:* не отвечают.

## Последнее обновление: 30.10.2026 (сессия 58)

## Импорт плейлистов по ссылке и экран артиста — сессия 58
- **Парсер ссылок** `core/import/PlaylistLinkParser.kt` + модели `core/import/PlaylistImportModels.kt` (`ImportService`, `ImportError`, `PlaylistLinkParseResult`, `ImportedPlaylist`): Spotify (`open.spotify.com`/`spotify.com` + `/playlist/<id>`), Яндекс (`users/<user>/playlists/<id>` и `/playlists/<id>`), Apple (`music.apple.com/.../playlist/.../pl.<uuid>`; `?i=<numeric>` — отдельный трек), Deezer (`/playlist/<id>`, `/ru/playlist/<id>`). Ссылку можно вставлять без схемы; query-параметры (`?si=`, `utm_*`) игнорируются. Ошибки — `ImportError`, локализованный текст выбирает UI (`import_error_*`).
- **Загрузчик** `data/source/imports/PlaylistImportService.kt`: интерфейс + `PlaylistImportServiceImpl` (`@Singleton`, биндинг в `RepositoryModule`), `load(link): PlaylistImportResult`. Пакет называется `imports`, а не `import`: `import` — зарезервированное слово, Java-фабрики Hilt с таким пакетом не компилируются.
- **Spotify**: в `SpotifyProvider` добавлены `fetchPlaylist(id): Result<SpotifyPlaylist>` (`/v1/playlists/{id}` — название, `/v1/playlists/{id}/tracks?limit=100` — треки) и `fetchPlaylistTracks(id)`. Токен Client Credentials и лимитер общие с поиском; без ключей — `SPOTIFY_NOT_CONFIGURED` («Добавьте ключи Spotify в настройках»), сеть не вызывается.
- **Deezer**: `GET api.deezer.com/playlist/{id}`, разбор `PlaylistPayloadParser.deezerPlaylist` (название, `tracks.data`: title, artist.name, album.cover_big, duration, preview → `Track(source=DEEZER, streamUrl=preview, previewSeconds=30)`), объект `error` превращается в понятную ошибку. Лимитер `@SourceRateLimiter(RateLimiterKeys.DEEZER)`.
- **Apple/iTunes**: трек по `i=` через `itunes.apple.com/lookup?id={id}&entity=song` (`PlaylistPayloadParser.appleLookupTracks`, только `wrapperType=track`, обложка 600×600, preview 30 с), лимитер Apple. Плейлист Apple (`pl.…`) без Apple Music API — `APPLE_PLAYLIST_UNSUPPORTED`; Яндекс — `YANDEX_UNSUPPORTED` (закрытый API).
- **UI импорта** (`LibraryScreen` + `LibraryViewModel`): на вкладке «Плейлисты» кнопка «Импортировать по ссылке» (`Icons.Rounded.AddLink`) → `AlertDialog` с `OutlinedTextField` и кнопками «Отмена/Продолжить»; загрузка → диалог превью (название, `N треков`, первые 10 строк «название — артист»), «Импортировать/Отмена» → сохранение (`LinearProgressIndicator`) через `PlaylistRepository.createPlaylist/addTrack`; ошибки и успех — Snackbar. Состояния — `PlaylistImportState` в ViewModel.
- **Экран артиста**: `feature/artist/ArtistScreen.kt` + `ArtistViewModel`, маршрут `Screen.Artist(name)` (`ARTIST_NAME_ARG`, `Uri.encode`) и регистрация в `AppNavGraph` (нижний док скрыт). Контент: круглая обложка первого трека, имя, «Слушать всё», популярные треки. `DeezerProvider.fetchArtistTopTracks(name, limit=20)` — `search/artist` → `artist/{id}/top?limit=20`; при пустом ответе — `YouTubeRepository.search("<artist> official audio")`.
- **Переходы**: имя артиста под названием в `PlayerScreen` (кликабельно только при наличии трека; оверлей плеера закрывается, воспроизведение не прерывается) и долгий тап по `TrackCard` (новый `onArtistClick: (() -> Unit)? = null`, `combinedClickable` только при переданном колбэке) в поиске, медиатеке (обе вкладки) и плейлисте. Короткий тап и свайпы не менялись.
- **Строки**: добавлены `import_*`, `artist_*`, `artist_open_cd` в `values/strings.xml` и `values-ru/strings.xml` — по 246 ключей, наборы совпадают, дублей нет.
- **Тесты**: `PlaylistLinkParserTest` (14), `PlaylistPayloadParserTest` (6), `PlaylistImportServiceTest` (5). Итог: 30 suites / 212 тестов, 0 failures/errors/skipped.
- Проверки: `assembleDebug` — BUILD SUCCESSFUL, APK 27 846 020 байт; `testDebugUnitTest` — BUILD SUCCESSFUL. Установка не выполнена: `adb devices` пуст, `adb mdns services` пуст, Android-устройств в системе нет.

## Последнее обновление: 30.10.2026 (сессия 57)

## Плейлисты (Room v12) — сессия 57
- Room v12: таблицы `playlists` (id, name, createdAt) и `playlist_tracks` (playlistId + trackId в составном PK, FK на playlists с CASCADE, индекс по playlistId, денормализованные поля трека — title/artist/artworkUrl/durationMs/source/transcodingUrl, поэтому плейлист открывается без сети). Миграция `MIGRATION_11_12` в `ALL_MIGRATIONS`, схема `12.json`, изолированный тест 11→12 и сквозной до 12.
- `PlaylistRepository` + `PlaylistRepositoryImpl` (observe/create/addTrack/removeTrack/deletePlaylist/rename), `PlaylistDao`, маппинг entity↔`Track`.
- UI: `AddToPlaylistSheet` (ModalBottomSheet, список плейлистов + «Создать»), точки входа — overflow-меню плеера и необязательная иконка `onAddToPlaylist` в `TrackCard` (поиск и обе вкладки библиотеки); третья вкладка «Плейлисты» в библиотеке и экран `PlaylistScreen` (`feature/playlist/`) со списком треков и удалением, маршрут `Screen.Playlist(id)`.
- Строки — в `values/strings.xml` и `values-ru/strings.xml` (наборы совпадают, кириллица проверена скриптом).
- Проверки: `assembleDebug` — BUILD SUCCESSFUL, APK 27 948 920 байт; `testDebugUnitTest` — 27 suites / 187 тестов, 0 failures (после сброса битого KSP-кэша, оставшегося от внезапного выключения ноутбука).

## Последнее обновление: 30.10.2026 (сессия 56)

## Кэш поиска, лимитер 1 rps и миграция 10→11 — сессия 56
- **Room v11**: новая таблица `search_cache` (PK `query`, колонки `source`, `jsonResponse`, `timestamp`), сущность `SearchCacheEntity`, DAO `SearchCacheDao` (`get(query, source)`, `upsert` через REPLACE, `deleteOlderThan`, точечный `delete`, `clearAll`), абстрактный `searchCacheDao()` в `AppDatabase`. Схема экспортируется в `app/schemas/com.facet.music.data.local.AppDatabase/11.json`; `createSql` из 11.json совпадает с `MIGRATION_10_11` один-в-один.
- **Миграция 10→11**: `DatabaseMigrations.kt` создаёт `search_cache` и добавлена в `ALL_MIGRATIONS`. Изолированный тест 10→11 проверяет таблицу, колонки и первичный ключ, сквозной тест 1→11 пишет и читает кэш после всех миграций, отдельный тест сверяет состав колонок и PK с `11.json`; саму схему при открытии мигрированной базы валидирует Room.
- **Репозиторий кэша**: интерфейс `SearchCacheRepository` (`get` с TTL по умолчанию 24 ч, `put`, `clear`) и `SearchCacheRepositoryImpl` в `data/local`; просроченная запись удаляется при чтении, каждая запись подметает строки старше суток через `deleteOlderThan`. Биндинг — в `RepositoryModule`, DAO — в `DatabaseModule`. Кэш лежит в Room, поэтому переживает перезапуск приложения.
- **Провайдеры**: Deezer/Apple/Spotify перед сетью смотрят кэш по `query` + `source`, после успешного разбора сохраняют сырой JSON; разбор вынесен в `parseTracks(body: String)` и одинаков для свежего и закэшированного ответа. Битый кэш молча игнорируется и заменяется сетевым ответом, ошибки сети не кэшируются (запись идёт только после успешного разбора). Apple кэширует тело удачного фолбэка без `country`; Spotify отдаёт кэш до проверки ключей; `resolve()` у Deezer/Apple кэш не использует (ссылки превью одноразовые), но проходит лимитер.
- **RateLimiter**: `core/network/RateLimiter.kt` — резервирование слотов под `Mutex` + `delay` (главный поток не блокируется), API `awaitSlot()` / `acquire { }`, минимум 1000 мс; часы вынесены параметром ради виртуального времени в тестах. По одному лимитеру на источник через квалификатор `@SourceRateLimiter` и `RateLimiterModule`. У Spotify лимитер общий для поиска и запроса токена; `token()` переведён с `synchronized` на `Mutex` (обновление стало suspend), повтор на 401 сохранён.
- **Health-проверка мимо кэша**: в `MusicSourceProvider.search` добавлен параметр `useCache` (по умолчанию true), `ServiceStatusManager` зовёт `useCache = false` — иначе статус «онлайн» показывался бы из кэша без сети.
- **Настройки**: в `SpotifyIntegrationCard` абзац про Extended Quota Mode; ключ `settings_spotify_quota_warning` в EN и RU (195 ключей, наборы совпадают).
- **Тесты**: `RateLimiterTest` (5: интервал 1000 мс, два интервала на три вызова, очередь параллельных вызовов, первый вызов без ожидания, свой интервал) на виртуальном времени, `SearchCacheRepositoryTest` (7: put/get, просрочка, чужой источник, TTL, чистка при записи, clear, TTL по умолчанию), миграция 10→11 и сверка с 11.json в `DatabaseMigrationTest`.
- Проверки: `assembleDebug` — BUILD SUCCESSFUL, APK 27 792 341 байт; `testDebugUnitTest` — 26 suites / 178 тестов, 0 failures/errors/skipped. Установка на телефон не выполнена: `adb devices` пуст, Android-устройств в системе нет.

## Иконки источников 48dp и локализация 100% — сессия 55
- **Выбор источника (SearchScreen)**: ряд кнопок 64dp с аббревиатурами и горизонтальным скроллом заменён на 5 иконок-кнопок 48×48dp (радиус 14dp = `MaterialTheme.shapes.medium`) и кнопку статусов 46dp справа. Внутренний `Row` — `Arrangement.spacedBy(10.dp)`, промежуток считается в `BoxWithConstraints` и сжимается до 4dp только на узких экранах: прокрутки нет (5×48 + 4×10 = 280dp, вместе со статусом 326dp при ширине 411dp), кнопки не имеют `weight` и не сплющиваются.
- **Иконки брендов**: из Simple Icons (CC0) скачаны официальные SVG (`youtube`, `soundcloud`, `deezer`, `apple`, `spotify`) и сконвертированы в `res/drawable/ic_brand_*.xml` (24×24 viewBox, по одному `<path>`, `fillColor="@android:color/white"`, цвет задаёт `Icon(tint = …)`). Рабочий путь репозитория — `simple-icons/simple-icons/develop/icons/` (указанный в задаче `simpleicons/simpleicons/master` отдаёт 404). Маппинг — `ServiceType.brandIconRes()`, текстовые аббревиатуры удалены.
- **Состояния кнопки**: фон — брендовый цвет alpha 0.15, активная — 0.25 и рамка 2dp цвета бренда, переходы `animateColorAsState` 180 мс. Недоступный сервис (health) показывает ту же иконку с alpha 0.4 и остаётся нажимаемым. `contentDescription` — `search_source_cd` («Источник: %1$s»); названия брендов не переводятся.
- **Локализация 100%**: все UI-строки поиска, медиатеки, настроек, главной, плеера, волны, нижней навигации и `core/ui/components` переведены на `stringResource`/`pluralStringResource`. `strings.xml` — 194 ключа в EN (`values/`) и 194 в RU (`values-ru/`), наборы совпадают один-в-один, дублей нет. Плюралы: `track_count` (треки) и `home_memories_subtitle` (сезон + число треков); самодельные `pluralTracks`/`tracksWord` и склейка «ч/мин» удалены — длительность собирается из `duration_hours_minutes`/`duration_minutes`.
- **Подписи вкладок**: `BottomNavItem.label: String` → `@StringRes labelRes: Int`, `ExpressiveBottomDock` читает `stringResource`; у `HomeSectionHeader` удалён неиспользуемый `eyebrow` (капс-надзаголовки ушли вместе с русскими строками).
- **Язык и Snackbar (SettingsScreen)**: смена языка вызывает `viewModel.setAppLanguage` и показывает Snackbar «Язык изменён» с action «Перезапустить» (`SnackbarHostState` + `SnackbarResult.ActionPerformed` → `context.findActivity()?.recreate()`); `findActivity()` разворачивает `ContextWrapper`, потому что `LocalContext` обёрнут локалью. Основной путь — автопересоздание Activity через `LaunchedEffect(settings.appLanguage)` в `MainActivity`.
- **Акценты**: `AccentOption.name` → `@StringRes nameRes` (7 подписей для скринридера в ресурсах).
- **Не переведено осознанно** (правило «не выносить константы ViewModel/логи»): `WaveViewModel.EMPTY_WAVE_MESSAGE`/`GENERATE_ERROR`, `LibraryViewModel.LIKE_FAILED_MESSAGE`/`DELETE_FAILED_MESSAGE`, `PlayerViewModel.LIKE_FAILED_MESSAGE`/`DISLIKE_FAILED_MESSAGE`, `DownloadMessages.*`, `PlaybackErrorMessages.*`, `PlayerController.PLAYER_UNAVAILABLE_MESSAGE`, `UNKNOWN_ARTIST` у Spotify/Deezer/Apple и алгоритмические словари жанров в `WaveRepositoryImpl`/`HomeViewModel`/`YouTubeMatcher`. Эти строки приходят в UI из ViewModel/движка ошибок и остаются русскими.
- Проверки: `assembleDebug` — BUILD SUCCESSFUL; `testDebugUnitTest` — 24 suites / 164 теста, 0 failures/errors/skipped. Установка на телефон **не выполнена**: `adb devices` пуст, Android-устройств в системе нет (проверено `Get-PnpDevice`), известные Wi-Fi-адреса 192.168.1.106:* не отвечают — скриншоты `stage1_*.png` не сняты.

## Spotify, локализация, «Скачанное» и кнопки источников — сессия 54
- **Выбор источника**: вместо текстовых чипсов — ряд квадратных кнопок 64×64dp (радиус 16dp) в брендовых цветах YouTube / SoundCloud / Deezer / Apple / Spotify: фон 18% цвета бренда, активный — заливка 28% + рамка 2dp, аббревиатуры YT/SC/DZ/AM/SP, недоступный сервис приглушён.
- **Spotify (S3)**: `SpotifyProvider` (Client Credentials Flow, кэш токена, 401 → перезапрос, `/v1/search`, ISRC, `streamUrl = null` — стрим через `YouTubeMatcher`); ключи `spotifyClientId`/`spotifyClientSecret` в `AppSettings`, в настройках секция «Интеграции» с двумя полями и «Сохранить»; `ServiceType.SPOTIFY`, резолв в `PlayerController` через `StreamResolver`.
- **Локализация**: `AppSettings.appLanguage` (system/ru/en), `core/i18n/AppLocale` без AppCompat (`attachBaseContext` + `createConfigurationContext`), пересоздание Activity при смене языка, сегментированный выбор в настройках; строки в `res/values/strings.xml` и `res/values-ru/strings.xml` (≈150 ключей: навигация, поиск, новые секции; часть старых экранов пока с хардкодом).
- **«Скачанное»**: в `LibraryScreen` сегменты «Медиатека / Скачанное», `LibraryViewModel.downloadedTracks`, список офлайн-файлов, удаление с устройства (файл + запись Room) через диалог подтверждения, пустое состояние с подсказкой и кнопкой «Найти музыку».
- Проверки: `assembleDebug` — BUILD SUCCESSFUL, APK 27 647 838 байт; тесты 24 suites / 164, 0 failures. Установка на телефон не выполнена: устройство отключилось от Wi-Fi (adb: no devices) — APK готов, поставить при следующем подключении.
- Из репозитория убрана служебная `.opencode/` (закоммичена по ошибке промежуточным агентом), добавлена в `.gitignore`.

## РџРѕСЃР»РµРґРЅРµРµ РѕР±РЅРѕРІР»РµРЅРёРµ: 30.10.2026 (СЃРµСЃСЃРёСЏ 53)

## Deezer + Apple Music, С„РёРєСЃС‹ СЂРµРєРѕРјРµРЅРґР°С†РёР№ Рё UX РёР· Dotify вЂ” СЃРµСЃСЃРёСЏ 53
- **S1. Р¤СѓРЅРґР°РјРµРЅС‚ РёСЃС‚РѕС‡РЅРёРєРѕРІ**: РЅРѕРІС‹Р№ `core/source/MusicSourceProvider` (source + `SourceCapabilities`: search/preview/fullStream/requiresAuth/supportsDownload) Рё `MusicSourceRegistry` (Set-РїСЂРѕРІР°Р№РґРµСЂРѕРІ С‡РµСЂРµР· `@IntoSet`). РќРѕРІС‹Р№ РёСЃС‚РѕС‡РЅРёРє РґРѕР±Р°РІР»СЏРµС‚СЃСЏ РѕРґРЅРёРј Р±РёРЅРґРёРЅРіРѕРј вЂ” РїРѕРёСЃРє, СЃС‚Р°С‚СѓСЃС‹ Рё СЂРµР·РѕР»РІРµСЂ РїРѕРґС…РІР°С‚С‹РІР°СЋС‚ РµРіРѕ СЃР°РјРё. `core/source/StreamResolver`: 1) РїСЂСЏРјР°СЏ СЃСЃС‹Р»РєР° С‚СЂРµРєР° (РїСЂРµРІСЊСЋ), 2) РїСЂРѕРІР°Р№РґРµСЂ РѕР±РЅРѕРІР»СЏРµС‚ СЃСЃС‹Р»РєСѓ, 3) РјР°С‚С‡ РЅР° YouTube С‡РµСЂРµР· `YouTubeMatcher` (СЃРєРѕСЂРёРЅРі Р°СЂС‚РёСЃС‚Р°/РЅР°Р·РІР°РЅРёСЏ/РґР»РёС‚РµР»СЊРЅРѕСЃС‚Рё, С€С‚СЂР°С„ Р·Р° cover/remix/live/slowed, Р±РѕРЅСѓСЃ official/topic, ISRC; РїРѕСЂРѕРі 5.0 вЂ” Р»СѓС‡С€Рµ В«РЅРµ РЅР°Р№РґРµРЅРѕВ», С‡РµРј С‡СѓР¶РѕР№ С‚СЂРµРє).
- **S2. Deezer Рё Apple/iTunes**: `DeezerProvider` (`api.deezer.com/search`, 30-СЃРµРє. MP3-РїСЂРµРІСЊСЋ, ISRC) Рё `AppleMusicProvider` (`itunes.apple.com/search`, `previewUrl` M4A, Р°РїСЃРєРµР№Р» РѕР±Р»РѕР¶РєРё РґРѕ 600Г—600, `country=RU` РґР»СЏ РєРёСЂРёР»Р»РёС†С‹). РћР±Р° Р±РµР· Р°РІС‚РѕСЂРёР·Р°С†РёРё; РІ `Track` РґРѕР±Р°РІР»РµРЅС‹ `isrc` Рё `previewSeconds`, РІ `Source` вЂ” `DEEZER` Рё `APPLE`. `PlayerController` СЂРµР·РѕР»РІРёС‚ РёС… С‡РµСЂРµР· `StreamResolver` (null-safe, С‚РµСЃС‚РѕРІС‹Рµ РєРѕРЅСЃС‚СЂСѓРєС‚РѕСЂС‹ РЅРµ С‚СЂРѕРЅСѓС‚С‹), MIME: Apple в†’ M4A, Deezer в†’ MP3.
- **Р РµРєРѕРјРµРЅРґР°С†РёРё (P0/P1 РёР· Р°СѓРґРёС‚Р°)**: РёР· РєР°РЅРґРёРґР°С‚РѕРІ Р”РѕРјР° РёСЃРєР»СЋС‡Р°СЋС‚СЃСЏ `liked_tracks`, `play_history` Рё `downloaded_tracks`; РґРµРґСѓРї РїРѕ РЅРѕСЂРјР°Р»РёР·РѕРІР°РЅРЅРѕРјСѓ `(artist, title)` РІ Р”РѕРјРµ, РІРѕР»РЅРµ Рё С„РѕР»Р±СЌРєРµ; РІ РІРѕР»РЅСѓ РґРѕР±Р°РІР»РµРЅ `filterDisliked` (РґРёР·Р»Р°Р№РєРё Р±РѕР»СЊС€Рµ РЅРµ РІРѕР·РІСЂР°С‰Р°СЋС‚СЃСЏ); С…РѕР»РѕРґРЅС‹Р№ СЃС‚Р°СЂС‚ вЂ” `SoundCloudChartsRepository.getTop50()` РІРјРµСЃС‚Рѕ `"popular songs official audio"`, YouTube-РїРѕРёСЃРє РѕСЃС‚Р°Р»СЃСЏ РїРѕСЃР»РµРґРЅРёРј С„РѕР»Р±СЌРєРѕРј.
- **UX РёР· Dotify (Р°РґР°РїС‚РёСЂРѕРІР°РЅРѕ РїРѕРґ M3)**: 1) В«РќРµРґР°РІРЅРёРµВ» Р·Р°РїСЂРѕСЃС‹ С‡РёРїСЃР°РјРё РїРѕРґ РїРѕР»РµРј РїРѕРёСЃРєР° (`SearchHistoryStore`, SharedPreferences, 8 Р·Р°РїРёСЃРµР№, В«РћС‡РёСЃС‚РёС‚СЊВ»); 2) РІ РїСѓСЃС‚РѕР№ РІС‹РґР°С‡Рµ вЂ” В«РџРѕРёСЃРєР°С‚СЊ РІ РґСЂСѓРіРѕРј РёСЃС‚РѕС‡РЅРёРєРµВ» СЃ С‡РёРїСЃР°РјРё РѕСЃС‚Р°Р»СЊРЅС‹С… СЃРµСЂРІРёСЃРѕРІ; 3) РІ РєР°СЂС‚РѕС‡РєРµ С‚СЂРµРєР° РІРёРґРЅРѕ В«РїСЂРµРІСЊСЋ 30 СЃВ» вЂ” С‡РµСЃС‚РЅР°СЏ РёРЅРґРёРєР°С†РёСЏ С‚РѕРіРѕ, С‡С‚Рѕ РёРіСЂР°РµС‚ РѕС‚СЂС‹РІРѕРє. Р§РёРїСЃС‹ РёСЃС‚РѕС‡РЅРёРєРѕРІ СЃС‚Р°Р»Рё РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅРѕ СЃРєСЂРѕР»Р»РёСЂСѓРµРјС‹РјРё (4 СЃРµСЂРІРёСЃР° РЅРµ РІР»РµР·Р°СЋС‚ РІ СЃС‚СЂРѕРєСѓ), СЃС‚Р°С‚СѓСЃС‹ СЃРµСЂРІРёСЃРѕРІ СЂР°СЃС€РёСЂРµРЅС‹ РґРѕ SoundCloud/YouTube/Deezer/Apple.
- РџСЂРѕРІРµСЂРєРё: `assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 572 301 Р±Р°Р№С‚; С‚РµСЃС‚С‹ 24 suites / 164, 0 failures/errors/skipped (РґРѕР±Р°РІР»РµРЅС‹ `YouTubeMatcherTest` вЂ” 6 РєРµР№СЃРѕРІ, 2 РєРµР№СЃР° С…РѕР»РѕРґРЅРѕРіРѕ СЃС‚Р°СЂС‚Р° Рё РґРµРґСѓРїР° РІ `HomeViewModelRecommendationsTest`).
- Р–РёРІР°СЏ РїСЂРѕРІРµСЂРєР° РЅР° 192.168.1.106:44537: С‡РёРїСЃС‹ Deezer/Apple Music, РїРѕРёСЃРє В«cachalotВ» РІ Deezer вЂ” 20 СЂРµР·СѓР»СЊС‚Р°С‚РѕРІ СЃ Р±РµР№РґР¶РµРј В«РїСЂРµРІСЊСЋ 30 СЃВ», РїСЂРµРІСЊСЋ РёРіСЂР°РµС‚ (0:17, РѕС‡РµСЂРµРґСЊ Р»РёСЃС‚Р°РµС‚СЃСЏ РґР°Р»СЊС€Рµ), СЃС‚Р°С‚СѓСЃС‹ РІСЃРµС… С‡РµС‚С‹СЂС‘С… СЃРµСЂРІРёСЃРѕРІ В«РћРЅР»Р°Р№РЅВ» (Р·РЅР°С‡РёС‚ Рё iTunes-РїРѕРёСЃРє РІРµСЂРЅСѓР» СЂРµР·СѓР»СЊС‚Р°С‚), В«РќРµРґР°РІРЅРёРµВ» СЃ С‡РёРїСЃРѕРј В«cachalotВ» Рё В«РћС‡РёСЃС‚РёС‚СЊВ».

## РљРЅРѕРїРєРё РїРѕРґ РѕР±Р»РѕР¶РєРѕР№ РІ РїР°Р»РёС‚СЂРµ РїР»РµРµСЂР° вЂ” СЃРµСЃСЃРёСЏ 52
- РљРЅРѕРїРєРё В«СЃРєР°С‡Р°С‚СЊ / Р»Р°Р№Рє / РґРёР·Р»Р°Р№РєВ» РїРѕРґ РѕР±Р»РѕР¶РєРѕР№ Р±РѕР»СЊС€Рµ РЅРµ Р±РµСЂСѓС‚ С†РІРµС‚ С‚РµРјС‹: РїСЂРё РІРєР»СЋС‡С‘РЅРЅС‹С… РґРёРЅР°РјРёС‡РµСЃРєРёС… С†РІРµС‚Р°С… РѕР±Р»РѕР¶РєРё Р°РєС†РµРЅС‚ вЂ” `coverAccent`, РєРѕРЅС‚РµР№РЅРµСЂ Р°РєС‚РёРІРЅРѕРіРѕ СЃРѕСЃС‚РѕСЏРЅРёСЏ вЂ” `playerColors.primaryContainer`, РїРѕРґР»РѕР¶РєР° РїРѕРєРѕСЏ вЂ” `coverAccent.copy(alpha = 0.14f)` РІРјРµСЃС‚Рѕ СЃРµСЂРѕР№ `onSurface.copy(alpha = 0.08f)`.
- РћР±Рµ РєСЂР°СЃРєРё Р°РЅРёРјРёСЂСѓСЋС‚СЃСЏ 420 РјСЃ (`heroActionContainer`, `heroActionIdleContainer`) СЃРёРЅС…СЂРѕРЅРЅРѕ СЃ Р°РєС†РµРЅС‚РѕРј, РїРѕСЌС‚РѕРјСѓ РїСЂРё СЃРјРµРЅРµ С‚СЂРµРєР° РєРЅРѕРїРєРё РїРµСЂРµС‚РµРєР°СЋС‚ РІРјРµСЃС‚Рµ СЃ С„РѕРЅРѕРј Рё РєРЅРѕРїРєРѕР№ Play, Р° РЅРµ РїРµСЂРµРєР»СЋС‡Р°СЋС‚СЃСЏ СЂС‹РІРєРѕРј.
- Р¦РІРµС‚Р° РїСЂРѕРєРёРЅСѓС‚С‹ РІ `ExpressiveHeroZone` С‚СЂРµРјСЏ РїР°СЂР°РјРµС‚СЂР°РјРё (`actionAccent`, `actionContainer`, `actionIdleContainer`); `LikeSquircle`/`DislikeSquircle`/`DownloadSquircle` РїРѕР»СѓС‡РёР»Рё РїР°СЂР°РјРµС‚СЂ `idleContainerColor` СЃ РїСЂРµР¶РЅРёРј СЃРµСЂС‹Рј Р·РЅР°С‡РµРЅРёРµРј РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ, С‡С‚РѕР±С‹ РјС‘СЂС‚РІС‹Рµ РІР°СЂРёР°РЅС‚С‹ (`CoverForeground`, `TrackGlassCard`) РєРѕРјРїРёР»РёСЂРѕРІР°Р»РёСЃСЊ Р±РµР· РїСЂР°РІРѕРє.
- РџРѕРґРїРёСЃСЊ В«РўРµРєСЃС‚ РїРµСЃРЅРёВ»/В«РћР±Р»РѕР¶РєР°В» С‚РѕР¶Рµ РІ РїР°Р»РёС‚СЂРµ: `TextButton(colors = ButtonDefaults.textButtonColors(contentColor = coverAccent))`; РїСЂРё РІС‹РєР»СЋС‡РµРЅРЅС‹С… РґРёРЅР°РјРёС‡РµСЃРєРёС… С†РІРµС‚Р°С… `coverAccent` СЂР°РІРµРЅ `primary` С‚РµРјС‹, РїРѕСЌС‚РѕРјСѓ РІРёРґ РЅРµ РјРµРЅСЏРµС‚СЃСЏ.
- Р”РёР·Р»Р°Р№Рє СЃРѕС…СЂР°РЅСЏРµС‚ СЃРµРјР°РЅС‚РёС‡РµСЃРєРёР№ РєСЂР°СЃРЅС‹Р№ РІ Р°РєС‚РёРІРЅРѕРј СЃРѕСЃС‚РѕСЏРЅРёРё (`errorContainer`/`error`), РІ РїРѕРєРѕРµ РёСЃРїРѕР»СЊР·СѓРµС‚ РѕР±С‰СѓСЋ РїРѕРґР»РѕР¶РєСѓ.
- РџСЂРѕРІРµСЂРєРё: `assembleDebug` вЂ” BUILD SUCCESSFUL; С‚РµСЃС‚С‹ 23 suites / 156, 0 failures/errors/skipped; СѓСЃС‚Р°РЅРѕРІР»РµРЅРѕ РЅР° 192.168.1.106:44537. Р–РёРІР°СЏ РїСЂРѕРІРµСЂРєР°: `credits song` (СЃРёРЅСЏСЏ РѕР±Р»РѕР¶РєР°) вЂ” Р»Р°Р№Рє, СЃРєР°С‡РёРІР°РЅРёРµ Рё Play РІ РѕРґРЅРѕРј СЃРёРЅРµРј Р°РєС†РµРЅС‚Рµ, РїРѕРґР»РѕР¶РєРё СЃ Р»С‘РіРєРёРј СЃРёРЅРёРј С‚РѕРЅРѕРј; РїРѕСЃР»Рµ РІС‹РєР»СЋС‡РµРЅРёСЏ С‚СѓРјР±Р»РµСЂР° вЂ” СЃРµСЂС‹Рµ РїРѕРґР»РѕР¶РєРё Рё Р·РµР»С‘РЅС‹Р№ Р°РєС†РµРЅС‚ С‚РµРјС‹; С‚СѓРјР±Р»РµСЂ РІРѕР·РІСЂР°С‰С‘РЅ РІ РёСЃС…РѕРґРЅРѕРµ СЃРѕСЃС‚РѕСЏРЅРёРµ, Р»Р°Р№Рє РІРѕСЃСЃС‚Р°РЅРѕРІР»РµРЅ.

## РџСЂРѕРїР°РґР°РЅРёРµ С‚РµРєСЃС‚Р° РїСЂРё СЃРјРµРЅРµ СЃС‚СЂРѕРєРё вЂ” СЃРµСЃСЃРёСЏ 51
- РЎРёРјРїС‚РѕРј: РїСЂРё РїРµСЂРµРєР»СЋС‡РµРЅРёРё СЃС‚СЂРѕРєРё РІРµСЃСЊ С‚РµРєСЃС‚ РЅР° РјРіРЅРѕРІРµРЅРёРµ В«РёСЃС‡РµР·Р°Р»В» Рё РІРѕР·РІСЂР°С‰Р°Р»СЃСЏ, Р°РЅРёРјР°С†РёСЏ РІС‹РіР»СЏРґРµР»Р° СЂРІР°РЅРѕР№. Р’РѕСЃРїСЂРѕРёР·РІРµРґРµРЅРѕ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ Р·Р°РїРёСЃСЊСЋ СЌРєСЂР°РЅР° (30 СЃ, `screenrecord`): 7 РїСЂРѕСЃР°РґРѕРє В«С‡РµСЂРЅРёР»В» С‚РµРєСЃС‚Р° РґРѕ 14% РјРµРґРёР°РЅС‹, СЂРѕРІРЅРѕ РІ РјРѕРјРµРЅС‚С‹ СЃРјРµРЅС‹ Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё; РїСЂРѕСЃР°РґРєР° РґРµСЂР¶Р°Р»Р°СЃСЊ ~0.2 СЃ Рё РІРѕСЃСЃС‚Р°РЅР°РІР»РёРІР°Р»Р°СЃСЊ ~250 РјСЃ.
- РџСЂРёС‡РёРЅР°: `Modifier.animateItem()` Сѓ СЃС‚СЂРѕРє `LazyColumn`. РЎРјРµРЅР° Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё РІС‹Р·С‹РІР°РµС‚ `animateScrollToItem`, Рё Р°РЅРёРјР°С‚РѕСЂ РїРѕСЏРІР»РµРЅРёСЏ (`fadeIn` spring `StiffnessMediumLow`) РіР°СЃРёР» РІ РЅРѕР»СЊ СѓР¶Рµ РІРёРґРёРјС‹Рµ СЃС‚СЂРѕРєРё, Р° РїРѕС‚РѕРј РїСЂРѕСЏРІР»СЏР» РёС… Р·Р°РЅРѕРІРѕ. РЎРїРёСЃРѕРє РЅРµ РґРѕР±Р°РІР»СЏРµС‚ Рё РЅРµ СѓРґР°Р»СЏРµС‚ СЃС‚СЂРѕРєРё, РїРѕСЌС‚РѕРјСѓ СЌС‚Р° Р°РЅРёРјР°С†РёСЏ РЅРµ РЅСѓР¶РЅР° (РёР·РІРµСЃС‚РЅС‹Рµ Р±Р°РіРё Google: 352584409, 455884472).
- Р¤РёРєСЃ 1: `animateItem()` СѓР±СЂР°РЅ; РІ РєРѕРґРµ РѕСЃС‚Р°РІР»РµРЅ РєРѕРјРјРµРЅС‚Р°СЂРёР№-РїСЂРµРґСѓРїСЂРµР¶РґРµРЅРёРµ, С‡С‚РѕР±С‹ РЅРµ РІРµСЂРЅСѓР»Рё.
- Р¤РёРєСЃ 2: РІС‹СЃРѕС‚Р° СЃС‚СЂРѕРєРё Р±РѕР»СЊС€Рµ РЅРµ Р·Р°РІРёСЃРёС‚ РѕС‚ Р°РєС‚РёРІРЅРѕСЃС‚Рё. `AnimatedVisibility` СЃ `expandVertically`/`shrinkVertically` Р·Р°РјРµРЅС‘РЅ РЅР° РїРѕСЃС‚РѕСЏРЅРЅС‹Р№ СЃР»РѕС‚ `LyricBarSlot = 12.dp`, РІРЅСѓС‚СЂРё РєРѕС‚РѕСЂРѕРіРѕ РїРѕР»РѕСЃРєР° РїСЂРѕСЏРІР»СЏРµС‚СЃСЏ Р°Р»СЊС„РѕР№ (200 РјСЃ РІС…РѕРґ, 140 РјСЃ РІС‹С…РѕРґ). Р Р°РЅСЊС€Рµ РїРѕСЏРІР»РµРЅРёРµ/РёСЃС‡РµР·РЅРѕРІРµРЅРёРµ РїРѕР»РѕСЃРєРё РјРµРЅСЏР»Рѕ РІС‹СЃРѕС‚Сѓ СЌР»РµРјРµРЅС‚Р° РЅР° 10dp, Рё СЃРїРёСЃРѕРє РїСЂС‹РіР°Р» РЅР° РєР°Р¶РґРѕР№ СЃРјРµРЅРµ СЃС‚СЂРѕРєРё.
- РџРѕР±РѕС‡РЅРѕ: РІРµСЂС‚РёРєР°Р»СЊРЅС‹Р№ padding СЃС‚СЂРѕРєРё 6dp в†’ `top = 2.dp` (СЃР»РѕС‚ СЃР°Рј РґР°С‘С‚ РІРѕР·РґСѓС…); РёРјРїРѕСЂС‚С‹ `AnimatedVisibility`, `expandVertically`, `shrinkVertically` СѓРґР°Р»РµРЅС‹.
- РџСЂРѕРІРµСЂРєР° РїРѕСЃР»Рµ С„РёРєСЃР° С‚РѕР№ Р¶Рµ РјРµС‚РѕРґРёРєРѕР№ (30 СЃ Р·Р°РїРёСЃРё): РЅРё РѕРґРЅРѕР№ РїСЂРѕСЃР°РґРєРё РЅРёР¶Рµ 50% РјРµРґРёР°РЅС‹ РїРѕСЃР»Рµ СЃС‚Р°СЂС‚РѕРІРѕРіРѕ РїРѕР·РёС†РёРѕРЅРёСЂРѕРІР°РЅРёСЏ СЃРїРёСЃРєР°.
- РџСЂРѕРІРµСЂРєРё: `assembleDebug` вЂ” BUILD SUCCESSFUL; С‚РµСЃС‚С‹ 23 suites / 156, 0 failures/errors/skipped; СѓСЃС‚Р°РЅРѕРІР»РµРЅРѕ РЅР° 192.168.1.106:44537.

## РљРёРЅРµРјР°С‚РѕРіСЂР°С„РёС‡РЅР°СЏ Р°РЅРёРјР°С†РёСЏ С‚РµРєСЃС‚РѕРІ вЂ” СЃРµСЃСЃРёСЏ 50
- `LyricLineItem`: СЂР°Р·РјРµСЂ Р°РЅРёРјРёСЂСѓРµС‚СЃСЏ `animateFloatAsState` (spring 0.9 / MediumLow): Р°РєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР° РїР»Р°РІРЅРѕ РІС‹СЂР°СЃС‚Р°РµС‚ РґРѕ 22sp, СЃРѕСЃРµРґРЅРёРµ вЂ” 17sp, РґР°Р»СЊРЅРёРµ вЂ” 15sp; `lineHeight` СЃС‡РёС‚Р°РµС‚СЃСЏ РѕС‚ С‚РµРєСѓС‰РµРіРѕ СЂР°Р·РјРµСЂР°.
- Р¦РІРµС‚ СЃС‚СЂРѕРєРё вЂ” `animateColorAsState` 280 РјСЃ (onSurfaceVariant в†’ onSurface), РІРµСЃ РїРµСЂРµРєР»СЋС‡Р°РµС‚СЃСЏ РЅР° SemiBold Сѓ Р°РєС‚РёРІРЅРѕР№.
- РџСЂРѕР·СЂР°С‡РЅРѕСЃС‚СЊ вЂ” `tween` 300 РјСЃ; Р·РЅР°С‡РµРЅРёРµ С‡РёС‚Р°РµС‚СЃСЏ РІРЅСѓС‚СЂРё `graphicsLayer`, РїРѕСЌС‚РѕРјСѓ Р°РЅРёРјР°С†РёСЏ РЅРµ РІС‹Р·С‹РІР°РµС‚ СЂРµРєРѕРјРїРѕР·РёС†РёСЋ РЅР° РєР°Р¶РґС‹Р№ РєР°РґСЂ.
- РџРѕР»РѕСЃРєР° РїСЂРѕРіСЂРµСЃСЃР°: `AnimatedVisibility` СЃ fade + `expandVertically`/`shrinkVertically` (220/160 РјСЃ). РџСЂРё СЃРјРµРЅРµ СЃС‚СЂРѕРєРё СЃС‚Р°СЂР°СЏ РїРѕР»РѕСЃРєР° СѓРµР·Р¶Р°РµС‚, РЅРѕРІР°СЏ РІС‹РµР·Р¶Р°РµС‚; Р·Р°Р»РёРІРєР° РІРЅСѓС‚СЂРё Р°РЅРёРјРёСЂСѓРµС‚СЃСЏ 240 РјСЃ.
- `LazyColumn`: Рє РєР°Р¶РґРѕР№ СЃС‚СЂРѕРєРµ РґРѕР±Р°РІР»РµРЅ `Modifier.animateItem()` вЂ” СЃРґРІРёРіРё СЃС‚СЂРѕРє РїСЂРё СЂРѕСЃС‚Рµ Р°РєС‚РёРІРЅРѕР№ Р°РЅРёРјРёСЂСѓСЋС‚СЃСЏ, Р° РЅРµ РїРµСЂРµСЃРєР°РєРёРІР°СЋС‚.
- РЎРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ РЅРµ С‚СЂРѕРЅСѓС‚Р°: `activeIndex` РїРѕ С‚Р°Р№РјРєРѕРґСѓ Рё Р°РІС‚РѕС†РµРЅС‚СЂРёСЂРѕРІР°РЅРёРµ РѕСЃС‚Р°Р»РёСЃСЊ РїСЂРµР¶РЅРёРјРё.
- РџСЂРѕРІРµСЂРєРё: `assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 568 986 Р±Р°Р№С‚; С‚РµСЃС‚С‹ 23 suites / 156, 0 failures/errors/skipped; СѓСЃС‚Р°РЅРѕРІР»РµРЅРѕ РЅР° 192.168.1.106:44537.

## Р”РёРЅР°РјРёС‡РµСЃРєРёРµ С†РІРµС‚Р° РѕР±Р»РѕР¶РєРё Рё Р°РЅРёРјР°С†РёСЏ СЃРјРµРЅС‹ С‚СЂРµРєР° вЂ” СЃРµСЃСЃРёСЏ 49
- Р”РёРЅР°РјРёС‡РµСЃРєР°СЏ С‚РѕРЅРёСЂРѕРІРєР° СѓСЃРёР»РµРЅР°: РїСЂРё РІРєР»СЋС‡С‘РЅРЅРѕРј С‚СѓРјР±Р»РµСЂРµ С„РѕРЅ РїР»РµРµСЂР° вЂ” РІРµСЂС‚РёРєР°Р»СЊРЅС‹Р№ РіСЂР°РґРёРµРЅС‚ 28% в†’ 14% С†РІРµС‚Р° РѕР±Р»РѕР¶РєРё, РїР°РЅРµР»СЊ СѓРїСЂР°РІР»РµРЅРёСЏ РїРѕРґРєСЂР°С€РµРЅР° РЅР° 14%; СЃР»Р°Р№РґРµСЂ Рё РєРЅРѕРїРєР° Play РёСЃРїРѕР»СЊР·СѓСЋС‚ С†РІРµС‚ РѕР±Р»РѕР¶РєРё (`coverAccent`), РєРѕРЅС‚СЂР°СЃС‚ РёРєРѕРЅРєРё Play СЃС‡РёС‚Р°РµС‚СЃСЏ РїРѕ СЏСЂРєРѕСЃС‚Рё. РђРєС†РµРЅС‚ Р°РЅРёРјРёСЂСѓРµС‚СЃСЏ 420 РјСЃ, РїРѕСЌС‚РѕРјСѓ РїСЂРё СЃРјРµРЅРµ С‚СЂРµРєР° С„РѕРЅ Рё РєРЅРѕРїРєРё РїРµСЂРµС‚РµРєР°СЋС‚ РїР»Р°РІРЅРѕ. РџСЂРё РІС‹РєР»СЋС‡РµРЅРЅРѕРј С‚СѓРјР±Р»РµСЂРµ вЂ” СЃС‚СЂРѕРіРѕ `colorScheme.background`, СЃР»Р°Р№РґРµСЂ Рё Play вЂ” Р°РєС†РµРЅС‚РѕРј С‚РµРјС‹.
- РЎРјРµРЅР° С‚СЂРµРєР°: РѕР±Р»РѕР¶РєР° + РЅР°Р·РІР°РЅРёРµ + Р°СЂС‚РёСЃС‚ РѕР±С‘СЂРЅСѓС‚С‹ РІ `AnimatedContent` РїРѕ `TrackDisplay` (РєР»СЋС‡ С‚СЂРµРєР° + С‚РµРєСЃС‚С‹ + artwork). РЎС‚Р°СЂС‹Р№ Р±Р»РѕРє СѓС…РѕРґРёС‚ fade + slide РІРІРµСЂС…, РЅРѕРІС‹Р№ РїСЂРёС…РѕРґРёС‚ fade + slide СЃРЅРёР·Сѓ (340 РјСЃ / 220 РјСЃ, `SizeTransform(clip = false)`). Shared-element РјРѕСЂС„ РІРµС€Р°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РЅР° Р°РєС‚СѓР°Р»СЊРЅС‹Р№ С‚СЂРµРє, РїРѕСЌС‚РѕРјСѓ СЃРІРѕСЂР°С‡РёРІР°РЅРёРµ РїР»РµРµСЂР° РЅРµ Р»РѕРјР°РµС‚СЃСЏ.
- РџСЂРѕРІРµСЂРєРё: `assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 568 967 Р±Р°Р№С‚; С‚РµСЃС‚С‹ 23 suites / 156, 0 failures/errors/skipped; СѓСЃС‚Р°РЅРѕРІР»РµРЅРѕ РЅР° 192.168.1.106:44537.

## РЎРёРЅС…СЂРѕРЅРЅС‹Рµ С‚РµРєСЃС‚С‹, Р°РєС†РµРЅС‚, РЅР°СЃС‚СЂРѕР№РєРё Рё СЃРІР°Р№Рї вЂ” СЃРµСЃСЃРёСЏ 48
- РўРµРєСЃС‚С‹: СѓР±СЂР°РЅ РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅС‹Р№ РіСЂР°РґРёРµРЅС‚ РїРѕ Р±СѓРєРІР°Рј вЂ” РЅР° РїРµСЂРµРЅРµСЃС‘РЅРЅРѕР№ СЃС‚СЂРѕРєРµ РѕРЅ Р·Р°РєСЂР°С€РёРІР°Р» Рё РІС‚РѕСЂСѓСЋ СЃС‚СЂРѕРєСѓ С†РµР»РёРєРѕРј. РџСЂРѕРіСЂРµСЃСЃ С‚РµРїРµСЂСЊ РїРѕРєР°Р·С‹РІР°РµС‚СЃСЏ С‚РѕРЅРєРѕР№ РїРѕР»РѕСЃРѕР№ 2dp РїРѕРґ Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРѕР№; С‚РµРєСЃС‚ С†РµРЅС‚СЂРёСЂРѕРІР°РЅ, Р°РєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР° 22sp SemiBold, СЃРѕСЃРµРґРЅРёРµ 17sp Medium, РґР°Р»СЊРЅРёРµ 15sp СЃ alpha-РёРµСЂР°СЂС…РёРµР№, СЃРІРµС‡РµРЅРёРµ СѓР±СЂР°РЅРѕ.
- В«Р”РёРЅР°РјРёС‡РµСЃРєРёРµ С†РІРµС‚Р° РѕР±Р»РѕР¶РєРёВ» С‚РµРїРµСЂСЊ СЂР°Р±РѕС‚Р°СЋС‚: РїСЂРё РІРєР»СЋС‡РµРЅРёРё С„РѕРЅ РїР»РµРµСЂР° РїРѕР»СѓС‡Р°РµС‚ РјСЏРіРєСѓСЋ С‚РѕРЅРёСЂРѕРІРєСѓ С†РІРµС‚РѕРј РѕР±Р»РѕР¶РєРё (10% РІ РІРµСЂС…РЅРµР№ С‚РѕС‡РєРµ), РїСЂРё РІС‹РєР»СЋС‡РµРЅРёРё вЂ” С‡РёСЃС‚С‹Р№ `background`. РџР°Р»РёС‚СЂР° СЃС‡РёС‚Р°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РїСЂРё РІРєР»СЋС‡С‘РЅРЅРѕР№ РѕРїС†РёРё.
- РђРєС†РµРЅС‚РЅС‹Р№ С†РІРµС‚: РЅРѕРІС‹Р№ С„Р»Р°Рі `accentEnabled` (`AppSettings`, `SettingsRepositoryImpl`, SharedPreferences `accent_enabled`, РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ false). Р’С‹РєР»СЋС‡РµРЅ вЂ” `MainActivity` РїРµСЂРµРґР°С‘С‚ null РІ `customAccentColor`, Рё С‚РµРјР° Р±РµСЂС‘С‚ Dynamic Color СЃ РѕР±РѕРµРІ; РІРєР»СЋС‡С‘РЅ вЂ” СЃРІРѕР№ С†РІРµС‚. РўР°Рї РїРѕ РєСЂСѓР¶РєСѓ РїР°Р»РёС‚СЂС‹ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё РІРєР»СЋС‡Р°РµС‚ Р°РєС†РµРЅС‚, СЃР±СЂРѕСЃ С†РІРµС‚Р° РІС‹РєР»СЋС‡Р°РµС‚ РµРіРѕ.
- РќР°СЃС‚СЂРѕР№РєРё: СЃРµРєС†РёРё РїРµСЂРµСЃС‚Р°РІР»РµРЅС‹ (РћС„РѕСЂРјР»РµРЅРёРµ в†’ РњРѕСЏ РІРѕР»РЅР° в†’ Р Р°Р·СЂРµС€РµРЅРёСЏ), РєР°СЂС‚РѕС‡РєРё Р±РµР· РѕР±РІРѕРґРѕРє, Р·Р°РіРѕР»РѕРІРєРё `titleSmall`, РїРµСЂРµРєР»СЋС‡Р°С‚РµР»СЊ Р°РєС†РµРЅС‚Р° СЃ РїРѕСЏСЃРЅРµРЅРёРµРј, РїР°Р»РёС‚СЂР° РїСЂРё РІС‹РєР»СЋС‡РµРЅРЅРѕРј Р°РєС†РµРЅС‚Рµ РїСЂРёРіР»СѓС€Р°РµС‚СЃСЏ (alpha 0.38), РіРµРѕРїРѕР·РёС†РёСЏ РїРµСЂРµРµС…Р°Р»Р° РІ В«Р Р°Р·СЂРµС€РµРЅРёСЏВ», РІ С€Р°РїРєРµ СЃС‚Р°РЅРґР°СЂС‚РЅР°СЏ СЃС‚СЂРµР»РєР° `ArrowBack`.
- РЎРІР°Р№Рї РІРЅРёР·: РїРѕСЂРѕРі Р·Р°РєСЂС‹С‚РёСЏ СЃРЅРёР¶РµРЅ СЃ 45% РґРѕ 30% РІС‹СЃРѕС‚С‹ СЌРєСЂР°РЅР°.
- РњРѕСЂС„ РѕР±Р»РѕР¶РєРё: СЃ СЌРєСЂР°РЅР° РїР»РµРµСЂР° Рё РјРёРЅРё-РїР»РµРµСЂР° СЃРЅСЏС‚С‹ `scaleIn`/`scaleOut` вЂ” РѕСЃС‚Р°СЋС‚СЃСЏ С‚РѕР»СЊРєРѕ fade Рё СЃР°Рј shared-element РјРѕСЂС„, РїРѕСЌС‚РѕРјСѓ РѕР±Р»РѕР¶РєР° РЅРµ В«РµРґРµС‚В» РґРІР°Р¶РґС‹; РјРёРЅРё-РїР»РµРµСЂ РїРѕСЏРІР»СЏРµС‚СЃСЏ РјСЏРіРєРёРј fade + scale 0.96.
- РџСЂРѕРІРµСЂРєРё: `assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 568 968 Р±Р°Р№С‚; С‚РµСЃС‚С‹ 23 suites / 156, 0 failures/errors/skipped; СѓСЃС‚Р°РЅРѕРІР»РµРЅРѕ РЅР° SM-A266B (mDNS adb; Р°РґСЂРµСЃ 192.168.0.102:39883 Р±С‹Р» РЅРµРґРѕСЃС‚СѓРїРµРЅ).

## Р§РёСЃС‚С‹Р№ Material 3 вЂ” СЃРµСЃСЃРёСЏ 47
- РџР°Р»РёС‚СЂР° РїРµСЂРµРІРµРґРµРЅР° РІ СЃС‚СЂРѕРіРѕ РЅРµР№С‚СЂР°Р»СЊРЅСѓСЋ: СЃРІРµС‚Р»Р°СЏ `#FAFAFA`, С‚С‘РјРЅР°СЏ `#121212`, РєРѕРЅС‚РµР№РЅРµСЂС‹ вЂ” С‡РёСЃС‚С‹Рµ СЃРµСЂС‹Рµ Р±РµР· С‚С‘РїР»РѕРіРѕ/Р·РµР»С‘РЅРѕРіРѕ СѓРєР»РѕРЅР° (`Color.kt`). РђРєС†РµРЅС‚ РЅР° Android 12+ Р±РµСЂС‘С‚СЃСЏ РёР· Dynamic Color (РѕР±РѕРё), fallback вЂ” Google Blue; РЅРµР№С‚СЂР°Р»СЊРЅС‹Рµ СЂРѕР»Рё РїСЂРёР±РёС‚С‹ РІ `withAiryNeutrals`, С‡С‚РѕР±С‹ РѕР±РѕРё РЅРµ РїРµСЂРµРєСЂР°С€РёРІР°Р»Рё С„РѕРЅ.
- РўРёРїРѕРіСЂР°С„РёРєР° вЂ” РєР°РЅРѕРЅРёС‡РЅР°СЏ С€РєР°Р»Р° M3: display 57/45/36, headline 32/28/24, title 22/16/14, body 16/14/12, label 14/12/11; SemiBold РІ display/headline, Medium РІ title/label, Regular РІ body. Р СѓС‡РЅС‹С… `fontWeight` РІ СЌРєСЂР°РЅР°С… Р±РѕР»СЊС€Рµ РЅРµС‚.
- Р¤РѕСЂРјС‹: СЃС‚СЂРѕРіРѕ 12/16/24 (`AppShapes`) РїР»СЋСЃ `PillShape` РґР»СЏ РєРЅРѕРїРѕРє. РЈР±СЂР°РЅС‹ Р°СЃРёРјРјРµС‚СЂРёСЏ РѕСЃС‚СЂРѕРІР° 44/22, РєР°СЃС‚РѕРјРЅС‹Рµ СЂР°РґРёСѓСЃС‹ Рё squircle.
- РќР°РІРёРіР°С†РёСЏ: РІРјРµСЃС‚Рѕ РєР°СЃС‚РѕРјРЅРѕРіРѕ РґРѕРєР° вЂ” СЃС‚Р°РЅРґР°СЂС‚РЅС‹Р№ `NavigationBar` + `NavigationBarItem` (`ExpressiveBottomDock.kt`); `ExpressiveNavigationBar.kt` СѓРґР°Р»С‘РЅ.
- Home: hero-РєР°СЂС‚РѕС‡РєР° `surfaceContainerHigh` (РѕР±Р»РѕР¶РєР° 88, С‚РµРєСЃС‚, `FilledIconButton` 48); СЃРµРєС†РёРё вЂ” `titleLarge` Р±РµР· РЅР°РґР·Р°РіРѕР»РѕРІРєРѕРІ; В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏВ» Рё В«Р›СЋР±РёРјРѕРµВ» вЂ” РЅРµР№С‚СЂР°Р»СЊРЅС‹Рµ `surfaceContainerLow` Р±РµР· РіРёРіР°РЅС‚СЃРєРёС… С†РёС„СЂ Рё СЂР°Р·РґРµР»РёС‚РµР»РµР№.
- РџР»РµРµСЂ: СѓР±СЂР°РЅС‹ РіСЂР°РґРёРµРЅС‚ РѕС‚ РѕР±Р»РѕР¶РєРё, СЃС‚РµРєР»Рѕ, СЃРІРµС‡РµРЅРёРµ Рё С‚РµРєСЃС‚РѕРІС‹Рµ С‚РµРЅРё; С„РѕРЅ вЂ” `colorScheme.background`, РѕР±Р»РѕР¶РєР° 24dp, РЅР°Р·РІР°РЅРёРµ `headlineSmall`, Р°СЂС‚РёСЃС‚ `bodyLarge`, РїР°РЅРµР»СЊ СѓРїСЂР°РІР»РµРЅРёСЏ вЂ” РїСЂРѕСЃС‚Р°СЏ РєР°СЂС‚РѕС‡РєР° 24dp РЅР° `surfaceContainerLow`, Play 64dp, РѕСЃС‚Р°Р»СЊРЅС‹Рµ РєРЅРѕРїРєРё 48dp. РўРµРєСЃС‚С‹ РїРµСЃРµРЅ вЂ” Р±РµР· СЃРІРµС‡РµРЅРёСЏ, Р°РєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР° Р°РєС†РµРЅС‚РѕРј.
- РћС‚СЃС‚СѓРїС‹: СЃРµС‚РєР° 16/24, РјРµР¶РґСѓ СЃРµРєС†РёСЏРјРё 32dp; РєР°СЂС‚РѕС‡РєРё `surfaceContainerLow` Р±РµР· С‚РµРЅРµР№ Рё РѕР±РІРѕРґРѕРє.
- Р›РѕРіРёРєР° СЃРІР°Р№РїР°, С…РѕР»РѕРґРЅРѕРіРѕ СЃС‚Р°СЂС‚Р°, РЅР°РІРёРіР°С†РёРё, СЃРєР°С‡РёРІР°РЅРёСЏ Рё СЂРµРєРѕРјРµРЅРґР°С†РёР№ РЅРµ РјРµРЅСЏР»Р°СЃСЊ: `PlayerSwipeState`, `AppNavGraph`, `PlayerController`, `SettingsRepositoryImpl` РЅРµ РІ diff.
- РџСЂРѕРІРµСЂРєРё: `assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 624 863 Р±Р°Р№С‚; С‚РµСЃС‚С‹ 23 suites / 156, 0 failures/errors/skipped; `adb install -r` РЅР° 192.168.0.102:39883 вЂ” Success.

## Р РµРґРёР·Р°Р№РЅ В«Light Airy MD3В» вЂ” СЃРµСЃСЃРёСЏ 46
- `Type.kt`: СѓР±СЂР°РЅ РІРµСЃ 800 РёР· СЃРµРјРµР№СЃС‚РІР°, display/headline РїРµСЂРµРІРµРґРµРЅС‹ СЃ ExtraBold РЅР° SemiBold, title* вЂ” РЅР° Medium, labelSmall вЂ” Medium СЃ С‚СЂРµРєРёРЅРіРѕРј 1.2; bodyLarge С‚СЂРµРєРёРЅРі 0.5 в†’ 0.25. РЎРЅСЏС‚Рѕ 30 СЂСѓС‡РЅС‹С… `fontWeight`-РїРµСЂРµРѕРїСЂРµРґРµР»РµРЅРёР№ РІ СЌРєСЂР°РЅР°С…, С‚РµРїРµСЂСЊ РІРµСЃ Р·Р°РґР°С‘С‚ С€РєР°Р»Р°.
- `Color.kt`: РЅРµР№С‚СЂР°Р»СЊРЅР°СЏ РіР°РјРјР° вЂ” СЃРІРµС‚Р»С‹Р№ С„РѕРЅ `#FAF9F6`, С‚С‘РјРЅС‹Р№ `#1A1918`, РєРѕРЅС‚РµР№РЅРµСЂС‹ surfaceContainer* РїРµСЂРµРІРµРґРµРЅС‹ СЃ СЂРѕР·РѕРІС‹С… РЅР° С‚С‘РїР»С‹Рµ РЅРµР№С‚СЂР°Р»СЊРЅС‹Рµ; СЃРјСЏРіС‡РµРЅС‹ primaryContainer/secondaryContainer/tertiaryContainer.
- `Theme.kt`: `surfaceTint = Color.Transparent` (elevation РЅРµ РїРѕРґРјРµС€РёРІР°РµС‚ Р°РєС†РµРЅС‚); РЅРѕРІР°СЏ `withAiryNeutrals()` РїСЂРёР±РёРІР°РµС‚ РЅРµР№С‚СЂР°Р»СЊРЅС‹Рµ СЂРѕР»Рё РїРѕРІРµСЂС… Р»СЋР±РѕР№ СЃС…РµРјС‹ вЂ” Dynamic Color РѕСЃС‚Р°РІР»СЏРµС‚ Р°РєС†РµРЅС‚ СЃ РѕР±РѕРµРІ, РЅРѕ РЅРµ РїРµСЂРµРєСЂР°С€РёРІР°РµС‚ С„РѕРЅ. `values/colors.xml` Рё `values-night/colors.xml` СЃРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°РЅС‹.
- `Shape.kt`: РµРґРёРЅР°СЏ С€РєР°Р»Р° M3 4/8/12/16/24 (`AppShapes`) + `PillShape`; `SquircleShape`, `HeroShape`, `BentoCardShape` СѓРґР°Р»РµРЅС‹. 63 РјРµСЃС‚Р° СЃ СЂР°РґРёСѓСЃР°РјРё РїРµСЂРµРІРµРґРµРЅС‹ РЅР° `MaterialTheme.shapes.*`; Р°СЃРёРјРјРµС‚СЂРёСЏ РѕСЃС‚Р°Р»Р°СЃСЊ С‚РѕР»СЊРєРѕ Сѓ РѕСЃС‚СЂРѕРІР° СѓРїСЂР°РІР»РµРЅРёСЏ (44/22 Рё СЏР·С‹С‡РѕРє), Сѓ СЃС‚РµРєР»СЏРЅРЅРѕР№ РєР°СЂС‚РѕС‡РєРё РїР»РµРµСЂР° вЂ” СЃРёРјРјРµС‚СЂРёС‡РЅС‹Рµ 24dp.
- РўРµРЅРё: СѓРґР°Р»РµРЅС‹ `Modifier.shadow` Сѓ РѕР±Р»РѕР¶РєРё (22dp), СЃС‚РµРєР»СЏРЅРЅРѕР№ РєР°СЂС‚РѕС‡РєРё (20dp), РѕСЃС‚СЂРѕРІР° СѓРїСЂР°РІР»РµРЅРёСЏ (26dp), РјРёРЅРё-РїР»РµРµСЂР° (8dp), РїРѕР»СЏ РїРѕРёСЃРєР° (3в†’10dp) Рё `shadowElevation` РјРµРЅСЋ (12в†’4dp). РРµСЂР°СЂС…РёСЏ С‚РµРїРµСЂСЊ РЅР° `surfaceContainer*` + hairline 1dp. РЈР±СЂР°РЅС‹ С‚РµРєСЃС‚РѕРІС‹Рµ `Shadow` Сѓ Р°СЂС‚РёСЃС‚Р° Рё Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё С‚РµРєСЃС‚Р°.
- В«Р’РѕР·РґСѓС…В»: РєСЂР°СЏ СЌРєСЂР°РЅРѕРІ 20 в†’ 24dp, Р±Р»РѕРєРё Home 32 в†’ 40dp, РІРЅСѓС‚СЂРµРЅРЅРёРµ РїР°РґРґРёРЅРіРё РєР°СЂС‚РѕС‡РµРє 12/14/22 в†’ 18/18/26dp, СЃРїРёСЃРєРё 10вЂ“12 в†’ 14вЂ“16dp, РЅРёР· РїРѕРґ РґРѕРє СѓРЅРёС„РёС†РёСЂРѕРІР°РЅ РЅР° 24dp.
- РџР»РµРµСЂ: Р·Р°РіРѕР»РѕРІРѕРє С‚СЂРµРєР° СЃ 40sp ExtraBold РЅР° 30sp SemiBold, СЃРІРµС‡РµРЅРёРµ РѕР±Р»РѕР¶РєРё 0.45 в†’ 0.20, РѕСЃС‚СЂРѕРІ РїРѕР»СѓС‡РёР» С‚РѕРЅР°Р»СЊРЅС‹Р№ `containerColor` С‡РµСЂРµР· `compositeOver`.
- Р›РѕРіРёРєР° СЃРІР°Р№РїРѕРІ Рё С…РѕР»РѕРґРЅРѕРіРѕ СЃС‚Р°СЂС‚Р° РЅРµ С‚СЂРѕРЅСѓС‚Р°: `PlayerSwipeState.kt`, `AppNavGraph.kt`, `PlayerController`, `SettingsRepositoryImpl` РЅРµ РјРµРЅСЏР»РёСЃСЊ; РІСЃРµ `graphicsLayer`-Р±Р»РѕРєРё (translationY/alpha/scale) РЅР° РјРµСЃС‚Рµ.
- РЎР±РѕСЂРєР°: `assembleDebug` вЂ” BUILD SUCCESSFUL (2m 25s), APK 27 461 047 Р±Р°Р№С‚. РўРµСЃС‚С‹: 23 suites / 156 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `adb install -r` РЅР° 192.168.1.106:45609 вЂ” Success.


## РЈРґР°Р»РµРЅРёРµ motion blur вЂ” СЃРµСЃСЃРёСЏ 45
- Р¤РёС‡Р° motion blur СѓРґР°Р»РµРЅР° РёР· РїСЂРѕРµРєС‚Р° РїРѕР»РЅРѕСЃС‚СЊСЋ РїРѕ СЂРµС€РµРЅРёСЋ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ: `core/ui/MotionBlur.kt` СЃС‚С‘СЂС‚ (`git rm`), РґСЂСѓРіРёС… РїРѕС‚СЂРµР±РёС‚РµР»РµР№ `Modifier.motionBlur` РЅРµ Р±С‹Р»Рѕ.
- `PlayerSwipeState.kt`: СѓР±СЂР°РЅС‹ СЃРІРѕР№СЃС‚РІРѕ `blurRadius`, РєРѕРЅСЃС‚Р°РЅС‚Р° `MAX_BLUR_RADIUS = 15f` Рё СѓРїРѕРјРёРЅР°РЅРёРµ Р±Р»СЋСЂР° РІ KDoc. Р›РѕРіРёРєР° СЃРІР°Р№РїР° (`offsetPx` С‡РµСЂРµР· `mutableFloatStateOf`, `progress`, РїСЂСѓР¶РёРЅР° `settle()` РІ `settleJob`, `beginDrag/dragTo/dismiss/reset`) СЃРѕС…СЂР°РЅРµРЅР° Р±РµР· РёР·РјРµРЅРµРЅРёР№ вЂ” РЅР° РЅРµР№ РґРµСЂР¶РёС‚СЃСЏ РїР»Р°РІРЅРѕСЃС‚СЊ 120 Р“С†.
- `AppNavGraph.kt`: СЃ `SharedTransitionLayout` СЃРЅСЏС‚ `.motionBlur { maxOf(playerSwipe.blurRadius, playerBlur.value) }` (РѕСЃС‚Р°Р»СЃСЏ С‡РёСЃС‚С‹Р№ `Modifier.fillMaxSize()`); СѓРґР°Р»РµРЅС‹ `playerVisibility`/`playerTransitionBlur`/`playerBlur` Рё РёРјРїРѕСЂС‚С‹ `animateFloat`, `rememberTransition`, `motionBlur`. `MutableTransitionState` + `SideEffect` СЃРѕС…СЂР°РЅРµРЅС‹ вЂ” `playerVisibleState` РїРѕ-РїСЂРµР¶РЅРµРјСѓ РїРµСЂРµРґР°С‘С‚СЃСЏ РІ `AnimatedVisibility(visibleState = ...)` РїР»РµРµСЂР°.
- `PlayerScreen.kt` РЅРµ РјРµРЅСЏР»СЃСЏ: `blurRadius = 8f/18f` С‚Р°Рј вЂ” РїР°СЂР°РјРµС‚СЂС‹ `Shadow` (СЃРІРµС‡РµРЅРёРµ РёРјРµРЅРё Р°СЂС‚РёСЃС‚Р° Рё Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё С‚РµРєСЃС‚Р°), Рє motion blur РѕС‚РЅРѕС€РµРЅРёСЏ РЅРµ РёРјРµСЋС‚.
- РћРїС‚РёРјРёР·Р°С†РёРё РЅРµ С‚СЂРѕРЅСѓС‚С‹: РѕС‚Р»РѕР¶РµРЅРЅРѕРµ РїРѕРґРєР»СЋС‡РµРЅРёРµ `PlayerController` (`delay(600)` + `ensureConnected()`), Р°СЃРёРЅС…СЂРѕРЅРЅС‹Р№ `SettingsRepositoryImpl` (lazy prefs, IO, `loaded.await()`), СЃРІР°Р№Рї С‚РѕР»СЊРєРѕ С‡РµСЂРµР· `graphicsLayer` (translationY/scale/alpha), РјРѕСЂС„ РѕР±Р»РѕР¶РєРё С‡РµСЂРµР· `SharedTransitionLayout`/`sharedElement` вЂ” РІСЃС‘ СЂР°Р±РѕС‚Р°РµС‚ Р±РµР· СЃР»РѕСЏ СЂР°Р·РјС‹С‚РёСЏ.
- РўРµСЃС‚С‹: 23 suites / 156 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL (3m 23s), APK 27 611 487 Р±Р°Р№С‚. `adb install -r` РЅР° 192.168.1.106:45609 вЂ” Success. Р–РёРІРѕР№ РїСЂРѕРіРѕРЅ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## Cold start, 120 Р“С† Рё РїРѕС‡РёРЅРєР° motion blur вЂ” СЃРµСЃСЃРёСЏ 44
- Motion blur (`core/ui/MotionBlur.kt`): РІ `graphicsLayer` РґРѕР±Р°РІР»РµРЅ `CompositingStrategy.Offscreen`, РїРѕРєР° СЌС„С„РµРєС‚ Р°РєС‚РёРІРµРЅ (Р±РµР· СЃРѕР±СЃС‚РІРµРЅРЅРѕРіРѕ offscreen-СЃР»РѕСЏ `RenderEffect` РЅР° С‡Р°СЃС‚Рё СѓСЃС‚СЂРѕР№СЃС‚РІ РёРіРЅРѕСЂРёСЂСѓРµС‚СЃСЏ), Рё СЃР±СЂРѕСЃ РІ `Auto` РїСЂРё СЂР°РґРёСѓСЃРµ в‰¤ 0.1f. РџРѕСЏРІРёР»Р°СЃСЊ Р»СЏРјР±РґР°-РїРµСЂРµРіСЂСѓР·РєР° `motionBlur(() -> Float)`: СЂР°РґРёСѓСЃ С‡РёС‚Р°РµС‚СЃСЏ РІ layer-Р±Р»РѕРєРµ, РїРѕСЌС‚РѕРјСѓ Р°РЅРёРјР°С†РёСЏ РЅРµ РІС‹Р·С‹РІР°РµС‚ СЂРµРєРѕРјРїРѕР·РёС†РёСЋ; СЃС‚Р°СЂР°СЏ `motionBlur(Float)` РѕСЃС‚Р°Р»Р°СЃСЊ С‚РѕРЅРєРѕР№ РѕР±С‘СЂС‚РєРѕР№.
- Р‘Р»СЋСЂ РїРµСЂРµРµС…Р°Р» РёР· `PlayerScreen` РІ `AppNavGraph` вЂ” РЅР° СЃР°Рј `SharedTransitionLayout` (Р»РёС€РЅРёР№ `Box`-РєРѕРЅС‚РµР№РЅРµСЂ РЅРµ РїРѕРЅР°РґРѕР±РёР»СЃСЏ: `modifier` Сѓ layout-С„СѓРЅРєС†РёРё СѓР¶Рµ РµСЃС‚СЊ). РЎР»РѕР№ layout Р·Р°С…РІР°С‚С‹РІР°РµС‚ Рё РєРѕРЅС‚РµРЅС‚, Рё РѕРІРµСЂР»РµР№ shared-СЌР»РµРјРµРЅС‚РѕРІ, РіРґРµ РІРѕ РІСЂРµРјСЏ РјРѕСЂС„Р° Р¶РёРІСѓС‚ РїР°РЅРµР»Рё. РС‚РѕРіРѕРІС‹Р№ СЂР°РґРёСѓСЃ вЂ” `maxOf(playerSwipe.blurRadius, playerBlur)`.
- РќРѕРІС‹Р№ `feature/player/PlayerSwipeState.kt`: СЃРѕСЃС‚РѕСЏРЅРёРµ СЃРІР°Р№РїР° РїРѕРґРЅСЏС‚Рѕ РёР· `PlayerScreen` РІ `AppNavGraph`, С‡С‚РѕР±С‹ Рё Р±Р»СЋСЂ, Рё СЃРјРµС‰РµРЅРёРµ С‡РёС‚Р°Р»РёСЃСЊ РІ `graphicsLayer` Р±РµР· СЂРµРєРѕРјРїРѕР·РёС†РёРё С‚СЏР¶С‘Р»С‹С… РїР°РЅРµР»РµР№. `offsetPx` вЂ” `mutableFloatStateOf`, Р° РЅРµ `Animatable`: `snapTo` вЂ” suspend-С„СѓРЅРєС†РёСЏ, Р° РёР· `awaitEachGesture` (restricted suspend scope) РµС‘ РІС‹Р·С‹РІР°С‚СЊ РЅРµР»СЊР·СЏ, РєРѕРјРїРёР»СЏС‚РѕСЂ РїР°РґР°Р» СЃ В«Restricted suspending functionsвЂ¦В». РџСЂСѓР¶РёРЅР° РїРѕСЃР»Рµ РѕС‚РїСѓСЃРєР°РЅРёСЏ вЂ” `animate()` РІ `settleJob`, РЅРѕРІРѕРµ РєР°СЃР°РЅРёРµ РµС‘ РѕС‚РјРµРЅСЏРµС‚. Р•СЃС‚СЊ `progress`, `blurRadius` (`MAX_BLUR_RADIUS = 15f`), `screenWidthPx/HeightPx`, `isDragging`, `isDismissed`, `reset()`.
- 120 Р“С†: СЃРјРµС‰РµРЅРёРµ РєРѕСЂРЅСЏ Рё РїР°РЅРµР»РµР№ вЂ” С‚РѕР»СЊРєРѕ `graphicsLayer.translationY`/`alpha`; РѕР±Р»РѕР¶РєР° вЂ” `graphicsLayer` (scale/translationX/translationY), `Modifier.offset` РІ Р¶РµСЃС‚Рµ РЅРµ РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ. РќР° РІСЂРµРјСЏ drag `sharedElement` РѕР±Р»РѕР¶РєРё РѕС‚РєР»СЋС‡Р°РµС‚СЃСЏ (`if (swipeState.isDragging) Modifier else coverModifier`) Рё РІРєР»СЋС‡Р°РµС‚СЃСЏ РїРѕСЃР»Рµ РѕС‚РїСѓСЃРєР°РЅРёСЏ, С‡С‚РѕР±С‹ `SharedTransitionLayout` РЅРµ РїРµСЂРµСЃС‡РёС‚С‹РІР°Р» РјРѕСЂС„ РєР°Р¶РґС‹Р№ РєР°РґСЂ.
- Enter/exit Р±Р»СЋСЂ: `MutableTransitionState` + `rememberTransition` РѕР±СЉСЏРІР»РµРЅС‹ СЃРЅР°СЂСѓР¶Рё `AnimatedVisibility` (РїР»РµРµСЂСѓ РїРµСЂРµРґР°РЅ `visibleState = playerVisibleState`), СЂР°РґРёСѓСЃ `0f в†’ 15f` С‚РѕР»СЊРєРѕ РЅР° Р·Р°РєСЂС‹С‚РёРё; `derivedStateOf { if (playerVisibleState.isIdle) 0f else transitionBlur }` РіР°СЃРёС‚ СЌС„С„РµРєС‚ РІ РїРѕРєРѕРµ, РёРЅР°С‡Рµ СЂР°Р·РјС‹С‚С‹Рј РѕСЃС‚Р°РІР°Р»СЃСЏ РјРёРЅРё-РїР»РµРµСЂ.
- Cold start: `PlayerController` Р±РѕР»СЊС€Рµ РЅРµ РїРѕРґРєР»СЋС‡Р°РµС‚СЃСЏ Рє `MusicService` РІ `init` вЂ” `scope.launch { delay(COLD_START_CONNECT_DELAY_MS = 600); ensureConnected() }`; `ensureConnected()` РІС‹Р·С‹РІР°РµС‚СЃСЏ РёР· `playTrackAt`, `resolveAndPlay`, `play`, `pauseOrResume`. Р Р°РЅРЅРёР№ С‚Р°Рї РїРѕ С‚СЂРµРєСѓ РЅРµ С‚РµСЂСЏРµС‚СЃСЏ: `playStream` Р¶РґС‘С‚ `controllerReady`, Р° `ensureConnected()` Р·Р°РїСѓСЃРєР°РµС‚ СЃРѕРµРґРёРЅРµРЅРёРµ СЃСЂР°Р·Сѓ.
- `SettingsRepositoryImpl`: `getSharedPreferences` вЂ” `by lazy`, С‡С‚РµРЅРёРµ РІ `Dispatchers.IO` РёР· `init`, `settings = _settings.filterNotNull()` (РґРѕ Р·Р°РіСЂСѓР·РєРё UI Р¶РёРІС‘С‚ РЅР° `initial = AppSettings()` РІ `MainActivity`). РЎРµС‚С‚РµСЂС‹ Р¶РґСѓС‚ `loaded.await()` Рё РїРёС€СѓС‚ СЃРѕСЃС‚РѕСЏРЅРёРµ Р°С‚РѕРјР°СЂРЅРѕ С‡РµСЂРµР· `update`/`updateAndGet` вЂ” РёРЅР°С‡Рµ РїР°СЂР°Р»Р»РµР»СЊРЅР°СЏ СЃРјРµРЅР° С‚РµРјС‹ Рё Р°РєС†РµРЅС‚Р° С‚РµСЂСЏР»Р°СЃСЊ (read-modify-write РїРѕ СЃРЅРёРјРєСѓ).
- `MainActivity`, `HomeViewModel`, `ServiceStatusManager` СѓР¶Рµ СЂР°Р±РѕС‚Р°Р»Рё Р°СЃРёРЅС…СЂРѕРЅРЅРѕ (Room-С„Р»РѕСѓ РІ `viewModelScope`, РїСЂРѕРІРµСЂРєР° СЃРµСЂРІРёСЃРѕРІ РІ `Dispatchers.IO`) вЂ” РїСЂР°РІРѕРє РЅРµ РїРѕС‚СЂРµР±РѕРІР°Р»Рё.
- РўРµСЃС‚С‹: 23 suites / 156 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 611 495 Р±Р°Р№С‚. `adb install -r` РЅР° 192.168.1.106:34187 вЂ” Success. Р–РёРІРѕР№ РїСЂРѕРіРѕРЅ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ, РІРёР·СѓР°Р»СЊРЅР°СЏ РїСЂРѕРІРµСЂРєР° Р±Р»СЋСЂР° Рё 120 Р“С† вЂ” РЅР° РЅС‘Рј.

## РњРѕСЂС„ РѕР±Р»РѕР¶РєРё РІРѕ РІСЂРµРјСЏ СЃРІР°Р№РїР° Рё motion blur вЂ” СЃРµСЃСЃРёСЏ 43
- `PlayerScreen.kt`: РґРѕР±Р°РІР»РµРЅ `screenWidthPx` (РёР· `onSizeChanged`). РџСЂРѕРіСЂРµСЃСЃ СЃРІР°Р№РїР° СЃС‡РёС‚Р°РµС‚СЃСЏ РѕС‚ Р°РЅРёРјРёСЂРѕРІР°РЅРЅРѕРіРѕ `playerOffsetPx`, Р° РЅРµ РѕС‚ `dragOffsetPx`: РїРѕРєР° РїР°Р»РµС† РЅР° СЌРєСЂР°РЅРµ Р·РЅР°С‡РµРЅРёСЏ СЃРѕРІРїР°РґР°СЋС‚ (`snap`), Р° РїРѕСЃР»Рµ РѕС‚РјРµРЅС‹ Р¶РµСЃС‚Р° РїСЂСѓР¶РёРЅР° РїР»Р°РІРЅРѕ РІРѕР·РІСЂР°С‰Р°РµС‚ Рё РїР»РµРµСЂ, Рё РѕР±Р»РѕР¶РєСѓ.
- РћР±Р»РѕР¶РєР° РїРѕР»СѓС‡РёР»Р° `coverSwipeModifier`: `scaleX/scaleY = 1f - 0.75f * progress`, `translationX = -screenWidthPx * 0.35f * progress`, `translationY = screenHeightPx * 0.4f * progress`, `clip = false`. РњРѕРґРёС„РёРєР°С‚РѕСЂ РїСЂРёРјРµРЅС‘РЅ Рє РєРѕРЅС‚РµР№РЅРµСЂСѓ РѕР±Р»РѕР¶РєРё (РІРјРµСЃС‚Рµ СЃРѕ СЃРІРµС‡РµРЅРёРµРј), Р° РЅРµ Рє `Surface` СЃ `sharedElement` вЂ” С‚Р°Рє РјРѕСЂС„ РЅРµ РЅР°С‡РёРЅР°РµС‚СЃСЏ РёР· РґРІРѕР№РЅРѕР№ С‚СЂР°РЅСЃС„РѕСЂРјР°С†РёРё.
- Р’Р°Р¶РЅРѕ: `translationY` СЃРѕРґРµСЂР¶РёС‚ С‚РѕР»СЊРєРѕ РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅС‹Р№ СЃРґРІРёРі, РїРѕС‚РѕРјСѓ С‡С‚Рѕ РєРѕСЂРЅРµРІРѕР№ `Box` СѓР¶Рµ РµРґРµС‚ РЅР° `playerOffsetPx`. Р¤РѕСЂРјСѓР»Р° РёР· Р·Р°РґР°С‡Рё (`dragOffsetPx + ...`) РґР°Р»Р° Р±С‹ РґРІРѕР№РЅРѕРµ СЃРјРµС‰РµРЅРёРµ Рё СѓРІРµР»Р° РѕР±Р»РѕР¶РєСѓ Р·Р° СЌРєСЂР°РЅ СЂР°РЅСЊС€Рµ РїРѕСЂРѕРіР°.
- РџСЂРё `isDismissed` СЃРІР°Р№Рї-РїСЂРѕРіСЂРµСЃСЃ РѕР±Р»РѕР¶РєРё СЃР±СЂР°СЃС‹РІР°РµС‚СЃСЏ РІ 0: РґР°Р»СЊРЅРµР№С€РёР№ РїРѕР»С‘С‚ РІ РјРёРЅРё-РїР»РµРµСЂ РІС‹РїРѕР»РЅСЏРµС‚ shared-РїРµСЂРµС…РѕРґ.
- РќРѕРІС‹Р№ `core/ui/MotionBlur.kt`: `Modifier.motionBlur(blurRadius)` РґР»СЏ Android 12+ (`RenderEffect.createBlurEffect` СЃ `Shader.TileMode.CLAMP`); РїСЂРё СЂР°РґРёСѓСЃРµ в‰¤ 0.1f `renderEffect = null`, РЅР° API РЅРёР¶Рµ 31 РјРѕРґРёС„РёРєР°С‚РѕСЂ РЅРёС‡РµРіРѕ РЅРµ РґРµР»Р°РµС‚.
- Motion blur РїСЂРёРјРµРЅС‘РЅ Рє РєРѕСЂРЅРµРІРѕРјСѓ `Box` РїР»РµРµСЂР°: РїСЂРё СЃРІР°Р№РїРµ `swipeProgress * 15f`, РїСЂРё enter/exit вЂ” `transitionBlur` РёР· `AnimatedVisibilityScope.transition.animateFloat` (`EnterExitState.Visible в†’ 0`, РѕСЃС‚Р°Р»СЊРЅС‹Рµ в†’ 15), РёС‚РѕРі вЂ” `maxOf`. Р’ РїРѕРєРѕРµ СЂРѕРІРЅРѕ 0.
- РўРµСЃС‚С‹: 23 suites / 156 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 835 081 Р±Р°Р№С‚. APK СѓСЃС‚Р°РЅРѕРІР»РµРЅ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРѕ С‡РµСЂРµР· `adb install -r` вЂ” Success; Р¶РёРІРѕР№ РїСЂРѕРіРѕРЅ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## Р РѕРІРЅР°СЏ РѕР±Р»РѕР¶РєР° Рё z-order РїР°РЅРµР»РµР№ РїСЂРё РјРѕСЂС„Рµ вЂ” СЃРµСЃСЃРёСЏ 42
- `PlayerScreen.kt`: СѓР±СЂР°РЅ РЅР°РєР»РѕРЅ РѕР±Р»РѕР¶РєРё РїРѕР»РЅРѕСЃС‚СЊСЋ вЂ” РїРѕР»Рµ `HeroStyle.rotationZ` СѓРґР°Р»РµРЅРѕ, `graphicsLayer` Р±РѕР»СЊС€Рµ РЅРµ Р·Р°РґР°С‘С‚ `rotationZ` (РІР°СЂРёР°РЅС‚С‹ 2/3 С‚РѕР¶Рµ Р±РµР· РЅР°РєР»РѕРЅР°). Р¤РѕСЂРјР° РѕР±Р»РѕР¶РєРё вЂ” РµРґРёРЅС‹Р№ `RoundedCornerShape(24.dp)` РІРјРµСЃС‚Рѕ Р°СЃРёРјРјРµС‚СЂРёС‡РЅС‹С… 56/24/56/24.
- Р’ hero-`Box` РґРѕР±Р°РІР»РµРЅС‹ СЏРІРЅС‹Рµ СЃР»РѕРё: РѕР±Р»РѕР¶РєР° `Modifier.zIndex(0f)`, `AnimatedContent` СЃ РєР°СЂС‚РѕС‡РєРѕР№ С‚СЂРµРєР°/С‚РµРєСЃС‚Р°РјРё вЂ” `Modifier.zIndex(1f)`. РџР°РЅРµР»СЊ РѕР±СЉСЏРІР»РµРЅР° РїРѕСЃР»Рµ РѕР±Р»РѕР¶РєРё, РїРѕСЌС‚РѕРјСѓ РІ РѕР±С‹С‡РЅРѕРј СЃРѕСЃС‚РѕСЏРЅРёРё РІСЃРµРіРґР° СЂРёСЃСѓРµС‚СЃСЏ РїРѕРІРµСЂС….
- РџСЂРёС‡РёРЅР° РЅР°Р»РѕР¶РµРЅРёСЏ РїСЂРё РјРѕСЂС„Рµ: РѕРІРµСЂР»РµР№ `SharedTransitionLayout` СЂРёСЃСѓРµС‚СЃСЏ РІС‹С€Рµ РІСЃРµРіРѕ РєРѕРЅС‚РµРЅС‚Р°, Рё `zIndex` РІРЅСѓС‚СЂРё `PlayerScreen` РЅР° РЅРµРіРѕ РЅРµ РІР»РёСЏРµС‚. Р РµС€РµРЅРёРµ: РїР°РЅРµР»Рё (РєР°СЂС‚РѕС‡РєР° С‚СЂРµРєР° Рё РѕСЃС‚СЂРѕРІ СѓРїСЂР°РІР»РµРЅРёСЏ) РЅР° РІСЂРµРјСЏ enter/exit РїРѕРґРЅРёРјР°СЋС‚СЃСЏ РІ РѕРІРµСЂР»РµР№ С‡РµСЂРµР· `renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)` Рё РїРѕР»СѓС‡Р°СЋС‚ `animateEnterExit(enter = playerEnter, exit = playerExit)`.
- `renderInOverlay = { !isPlayerOpen || animatedScope.transition.isRunning }`: РІ РѕРІРµСЂР»РµРµ РїР°РЅРµР»Рё С‚РѕР»СЊРєРѕ РІРѕ РІСЂРµРјСЏ РїРµСЂРµС…РѕРґР°, РІ РїРѕРєРѕРµ РѕРЅРё РѕСЃС‚Р°СЋС‚СЃСЏ РЅР° СЃРІРѕРёС… РјРµСЃС‚Р°С… Рё РЅРµ РїРµСЂРµРєСЂС‹РІР°СЋС‚ СЃРЅРµРєР±Р°СЂ/РјРёРЅРё-РїР»РµРµСЂ.
- РћРІРµСЂР»РµР№ РЅРµ РЅР°СЃР»РµРґСѓРµС‚ `graphicsLayer` СЃРІР°Р№РїР°, РїРѕСЌС‚РѕРјСѓ `panelModifier` РґРѕР±Р°РІР»СЏРµС‚ `graphicsLayer` СЃ `translationY = playerOffsetPx` Рё С‚РѕР№ Р¶Рµ alpha РїСЂРё `isClosing` вЂ” РїР°РЅРµР»Рё РЅРµ РїСЂС‹РіР°СЋС‚ РїСЂРё Р·Р°РєСЂС‹С‚РёРё Р¶РµСЃС‚РѕРј.
- Р’ РѕРІРµСЂР»РµР№ РїРѕРґРЅРёРјР°СЋС‚СЃСЏ С‚РѕР»СЊРєРѕ РїР°РЅРµР»Рё, Р° РЅРµ РІСЃСЏ РєРѕР»РѕРЅРєР°: РёРЅР°С‡Рµ СЃРІРµС‡РµРЅРёРµ РѕР±Р»РѕР¶РєРё РѕРєР°Р·Р°Р»РѕСЃСЊ Р±С‹ РЅР°Рґ РЅРµР№.
- `PlayerScreen` РїРѕР»СѓС‡РёР» РїР°СЂР°РјРµС‚СЂС‹ `foregroundModifier: Modifier = Modifier` Рё `isClosing: Boolean = false`; `ExpressiveHeroZone` вЂ” `panelModifier`. Р’ `AppNavGraph` enter/exit РїР»РµРµСЂР° РІС‹РЅРµСЃРµРЅС‹ РІ Р»РѕРєР°Р»СЊРЅС‹Рµ `playerEnter`/`playerExit` Рё РїРµСЂРµРёСЃРїРѕР»СЊР·СѓСЋС‚СЃСЏ РІ `animateEnterExit`.
- РўРµСЃС‚С‹: 23 suites / 156 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 835 081 Р±Р°Р№С‚ (РїРѕСЂСЏРґРѕРє РёРјРїРѕСЂС‚Р° РЅРµ РёР·РјРµРЅРёР» Р±Р°Р№С‚РєРѕРґ). Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ; РІРёР·СѓР°Р»СЊРЅС‹Р№ СЂРµР·СѓР»СЊС‚Р°С‚ РјРѕСЂС„Р° РЅРµ РїСЂРѕРІРµСЂСЏР»СЃСЏ.

## РЎРІР°Р№Рї-РїРµСЂРµРєР»СЋС‡РµРЅРёРµ С‚СЂРµРєРѕРІ Рё РјРѕСЂС„ РѕР±Р»РѕР¶РєРё вЂ” СЃРµСЃСЃРёСЏ 41
- `MiniPlayer.kt`: РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅС‹Р№ СЃРІР°Р№Рї РїРµСЂРµРІРµРґС‘РЅ СЃ РїРµСЂРµРјРѕС‚РєРё РЅР° РїРµСЂРµРєР»СЋС‡РµРЅРёРµ С‚СЂРµРєРѕРІ. Р’Р»РµРІРѕ вЂ” СЃР»РµРґСѓСЋС‰РёР№ (`onNext` в†’ `PlayerViewModel.playNext()`), РІРїСЂР°РІРѕ вЂ” РїСЂРµРґС‹РґСѓС‰РёР№ (`onPrevious` в†’ `playPrevious()`); `onSeekToFraction` СѓРґР°Р»С‘РЅ.
- РџРѕСЂРѕРі РІС‹РЅРµСЃРµРЅ РІ С‡РёСЃС‚СѓСЋ С„СѓРЅРєС†РёСЋ `swipeTrackDirection(offsetPx, widthPx, velocityX)`: 30% С€РёСЂРёРЅС‹ РѕСЃС‚СЂРѕРІРєР° РёР»Рё СЃРєРѕСЂРѕСЃС‚СЊ Р±РѕР»СЊС€Рµ 1000 px/s (`VelocityTracker`); +1 вЂ” СЃР»РµРґСѓСЋС‰РёР№, -1 вЂ” РїСЂРµРґС‹РґСѓС‰РёР№, 0 вЂ” РІРѕР·РІСЂР°С‚.
- РћР±Р»РѕР¶РєР° Рё С‚РµРєСЃС‚ РµРґСѓС‚ Р·Р° РїР°Р»СЊС†РµРј С‡РµСЂРµР· `Animatable` + `graphicsLayer.translationX` СЃ Р»С‘РіРєРёРј fade (РґРѕ 25%), РєРЅРѕРїРєР° Play РѕСЃС‚Р°С‘С‚СЃСЏ РЅР° РјРµСЃС‚Рµ; РїРѕСЃР»Рµ РѕС‚РїСѓСЃРєР°РЅРёСЏ РёР»Рё РѕС‚РјРµРЅС‹ вЂ” `spring(DampingRatioMediumBouncy, StiffnessMediumLow)`. РќРѕРІС‹Р№ Р¶РµСЃС‚ РїСЂРµСЂС‹РІР°РµС‚ РїСЂСѓР¶РёРЅСѓ С‡РµСЂРµР· `offsetX.stop()`.
- `AppNavGraph`: РєРѕРЅС‚РµР№РЅРµСЂ РјРёРЅРё-РїР»РµРµСЂР° Рё РїР»РµРµСЂР° РѕР±С‘СЂРЅСѓС‚ РІ `SharedTransitionLayout`; РѕР±Рµ РѕР±Р»РѕР¶РєРё РїРѕР»СѓС‡Р°СЋС‚ `Modifier.sharedElement(rememberSharedContentState(PlayerCoverSharedKey), animatedVisibilityScope)` СЃ РѕР±С‰РёРј РєР»СЋС‡РѕРј `"player-cover"`.
- Р’С…РѕРґ РїРѕР»РЅРѕСЌРєСЂР°РЅРЅРѕРіРѕ РїР»РµРµСЂР° Р·Р°РјРµРЅС‘РЅ СЃ `slideInVertically` РЅР° `fadeIn + scaleIn(0.96f)`, С‡С‚РѕР±С‹ РѕР±Р»РѕР¶РєР° Р»РµС‚РµР»Р° РІ СѓР¶Рµ РІРёРґРёРјС‹Р№ СЌРєСЂР°РЅ; РІС‹С…РѕРґ РѕСЃС‚Р°Р»СЃСЏ `scaleOut(0.9f) + fadeOut`.
- `PlayerScreen` Рё `FloatingMiniPlayer` РїРѕР»СѓС‡РёР»Рё РїР°СЂР°РјРµС‚СЂ `coverModifier`; РІ СЂРµР¶РёРјРµ С‚РµРєСЃС‚РѕРІ shared-element Рє СЃРєСЂС‹С‚РѕР№ РѕР±Р»РѕР¶РєРµ РЅРµ РїСЂРёРјРµРЅСЏРµС‚СЃСЏ, С‡С‚РѕР±С‹ РѕРЅР° РЅРµ В«РѕР¶РёРІР°Р»Р°В».
- `sharedBounds` РґР»СЏ РЅР°Р·РІР°РЅРёСЏ С‚СЂРµРєР° РЅРµ РґРµР»Р°Р»Рё: СЂР°Р·РЅС‹Рµ РјР°СЃС€С‚Р°Р±С‹ С‚РёРїРѕРіСЂР°С„РёРєРё РґР°СЋС‚ Р·Р°РјРµС‚РЅС‹Р№ reflow РїСЂРё РїРµСЂРµР»С‘С‚Рµ.
- РўРµСЃС‚С‹: 23 suites / 156 С‚РµСЃС‚РѕРІ, 0 failures/errors (РґРѕР±Р°РІР»РµРЅ `MiniPlayerSwipeTest` вЂ” 8 С‚РµСЃС‚РѕРІ РїРѕСЂРѕРіРѕРІ Рё СЃРєРѕСЂРѕСЃС‚Рё). `assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 835 081 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РќР°РІРёРіР°С†РёСЏ Рё РјРёРЅРё-РїР»РµРµСЂ СЂР°Р·РґРµР»РµРЅС‹, СЃРІР°Р№Рї-РїРµСЂРµРјРѕС‚РєР° вЂ” СЃРµСЃСЃРёСЏ 40
- `ExpressiveBottomDock` С‚РµРїРµСЂСЊ С‚РѕР»СЊРєРѕ РЅР°РІРёРіР°С†РёРѕРЅРЅС‹Р№ Р±Р°СЂ: `Surface` РІРѕ РІСЃСЋ С€РёСЂРёРЅСѓ, С„РѕРЅ `surfaceContainerHigh` (alpha 0.97) РґРѕС…РѕРґРёС‚ РґРѕ РЅРёР¶РЅРµР№ РєСЂРѕРјРєРё, `navigationBarsPadding()` РІРЅСѓС‚СЂРё, РІС‹СЃРѕС‚Р° РєРѕРЅС‚РµРЅС‚Р° `DockNavHeight = 72dp`, РѕР±РІРѕРґРєР° 1dp. РРєРѕРЅРєРё РІС‹СЂРѕСЃР»Рё СЃ 22dp РґРѕ 28dp, РїРѕРґРїРёСЃРё вЂ” СЃ 11sp РґРѕ 12sp, РёРЅРґРёРєР°С‚РѕСЂ СЃРѕРІРјРµС‰С‘РЅ СЃ РєРІР°РґСЂР°С‚РѕРј РёРєРѕРЅРєРё (`indicatorTop = 13dp`).
- `MiniPlayer.kt` РїРµСЂРµРїРёСЃР°РЅ РІ `FloatingMiniPlayer`: РѕС‚РґРµР»СЊРЅС‹Р№ РѕСЃС‚СЂРѕРІРѕРє РІС‹СЃРѕС‚РѕР№ 64dp, СЂР°РґРёСѓСЃ 30dp, С‚РµРЅСЊ 8dp, РѕР±Р»РѕР¶РєР° 48dp, РЅР°Р·РІР°РЅРёРµ/Р°СЂС‚РёСЃС‚, РєРЅРѕРїРєР° Play/Pause 40dp (Р·РѕРЅР° 48dp), РїРѕР»РѕСЃРєР° РїСЂРѕРіСЂРµСЃСЃР° 2dp РІРЅРёР·Сѓ СЃ inset 24dp. Р‘РѕРєРѕРІС‹Рµ РѕС‚СЃС‚СѓРїС‹ 16dp Рё РїРѕР·РёС†РёСЏ РЅР°Рґ Р±Р°СЂРѕРј Р·Р°РґР°СЋС‚СЃСЏ РІ `AppNavGraph`.
- РџРµСЂРµРјРѕС‚РєР° СЃРІР°Р№РїРѕРј: `detectHorizontalDragGestures` РЅР° РѕСЃС‚СЂРѕРІРєРµ, РїСЂРѕРіСЂРµСЃСЃ СЃР»РµРґСѓРµС‚ Р·Р° РїР°Р»СЊС†РµРј С‡РµСЂРµР· `size.width`, РїСЂРё РѕС‚РїСѓСЃРєР°РЅРёРё РІС‹Р·С‹РІР°РµС‚СЃСЏ `PlayerViewModel.seekTo(fraction)`, РїСЂРё РѕС‚РјРµРЅРµ Р¶РµСЃС‚Р° РїРѕР»РѕСЃРєР° РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ Рє СЃС‚Р°СЂС‚РѕРІРѕР№ РґРѕР»Рµ. РћРїСЂРѕСЃ РїРѕР·РёС†РёРё РѕСЃС‚Р°РЅР°РІР»РёРІР°РµС‚СЃСЏ РЅР° РІСЂРµРјСЏ Р¶РµСЃС‚Р° Рё РІРѕР·РѕР±РЅРѕРІР»СЏРµС‚СЃСЏ С‡РµСЂРµР· 120 РјСЃ РїРѕСЃР»Рµ seek, С‡С‚РѕР±С‹ РїРѕР»РѕСЃРєР° РЅРµ РїСЂС‹РіР°Р»Р° РЅР°Р·Р°Рґ.
- `AppNavGraph`: Р±Р°СЂ Рё РјРёРЅРё-РїР»РµРµСЂ вЂ” РґРІР° РЅРµР·Р°РІРёСЃРёРјС‹С… `AnimatedVisibility`. Р’С‹СЃРѕС‚Р° Р±Р°СЂР° РёР·РјРµСЂСЏРµС‚СЃСЏ С‡РµСЂРµР· `onSizeChanged`; РѕСЃС‚СЂРѕРІРѕРє РІРёСЃРёС‚ РЅР° `navBarHeight + 12dp`, РІРёРґРµРЅ РїСЂРё `showBottomBar && track != null && navBarHeightPx > 0`. `lastTrack` СѓРґРµСЂР¶РёРІР°РµС‚ РєРѕРЅС‚РµРЅС‚ РЅР° РІСЂРµРјСЏ exit-Р°РЅРёРјР°С†РёРё. `LocalBottomDockHeight = navBarHeight + Р°РЅРёРјРёСЂРѕРІР°РЅРЅС‹Р№ СЂРµР·РµСЂРІ (64 + 12dp)`, РїРѕСЌС‚РѕРјСѓ РєРѕРЅС‚РµРЅС‚ РІРєР»Р°РґРѕРє РЅРµ РїСЂС‹РіР°РµС‚ РїСЂРё РїРѕСЏРІР»РµРЅРёРё РѕСЃС‚СЂРѕРІРєР°.
- РЎРІРѕСЂР°С‡РёРІР°РЅРёРµ РїР»РµРµСЂР°: exit Р·Р°РјРµРЅС‘РЅ СЃ РїРѕР»РЅРѕРіРѕ `slideOutVertically` РЅР° `scaleOut(0.9f, TransformOrigin(0.5f, 1f)) + fadeOut`; РјРёРЅРё-РїР»РµРµСЂ РІС…РѕРґРёС‚ `slideInVertically + scaleIn(0.85f, bottom origin) + fadeIn`. Р’РёР·СѓР°Р»СЊРЅРѕ СЌРєСЂР°РЅ СЃР¶РёРјР°РµС‚СЃСЏ Рє РЅРёР·Сѓ Рё С‚Р°РµС‚, Р° РѕСЃС‚СЂРѕРІРѕРє РІС‹РµР·Р¶Р°РµС‚ РёР·-РїРѕРґ Р±Р°СЂР°.
- РўРµСЃС‚С‹: 22 suites / 148 С‚РµСЃС‚РѕРІ, 0 failures/errors. `assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 635 291 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РџР»Р°РІР°СЋС‰РёР№ РЅРёР¶РЅРёР№ РґРѕРє вЂ” СЃРµСЃСЃРёСЏ 39
- `ExpressiveBottomDock`: Р±РѕРєРѕРІС‹Рµ РѕС‚СЃС‚СѓРїС‹ СѓРІРµР»РёС‡РµРЅС‹ СЃ 16dp РґРѕ 24dp, СЃРЅРёР·Сѓ вЂ” `navigationBarsPadding()` + 12dp (Р±С‹Р»Рѕ 8dp), СЃРІРµСЂС…Сѓ РѕСЃС‚Р°Р»РѕСЃСЊ 8dp. Р¤РѕРЅ `surfaceContainerHigh` Р±РѕР»СЊС€Рµ РЅРµ С‚СЏРЅРµС‚СЃСЏ РґРѕ РєСЂР°С‘РІ СЌРєСЂР°РЅР° Рё РЅРµ РїСЂРёР»РёРїР°РµС‚ Рє СЃРёСЃС‚РµРјРЅРѕР№ РїР°РЅРµР»Рё вЂ” РґРѕРє С‡РёС‚Р°РµС‚СЃСЏ РєР°Рє РѕС‚РґРµР»СЊРЅС‹Р№ РѕСЃС‚СЂРѕРІРѕРє.
- Р Р°РґРёСѓСЃ 30dp, РѕР±РІРѕРґРєР° 1dp Рё С‚РµРЅСЊ 8dp РЅРµ РјРµРЅСЏР»РёСЃСЊ; `DockNavHeight = 64dp`, `DockMiniHeight = 56dp`, РѕР±Р»РѕР¶РєР° 44dp Рё РєРЅРѕРїРєР° Play 48dp СЃРѕС…СЂР°РЅРµРЅС‹ вЂ” РЅР° С€РёСЂРёРЅРµ 320вЂ“360dp РєРѕРЅС‚РµРЅС‚ РїРѕРјРµС‰Р°РµС‚СЃСЏ, РЅР°РІРёРіР°С†РёСЏ РїРѕ-РїСЂРµР¶РЅРµРјСѓ РґРµР»РёС‚СЃСЏ С‡РµСЂРµР· `weight(1f)`.
- РђРЅРёРјР°С†РёСЏ РјРёРЅРё-РїР»РµРµСЂР° РїРµСЂРµРІРµРґРµРЅР° СЃ `expandVertically`/`shrinkVertically` РЅР° `slideInVertically`/`slideOutVertically` + fade: СЃС‚СЂРѕРєР° РІС‹РµР·Р¶Р°РµС‚ РёР·-РїРѕРґ СЂСЏРґР° РЅР°РІРёРіР°С†РёРё. Р”РѕР±Р°РІР»РµРЅРѕ РѕС‚РґРµР»СЊРЅРѕРµ РѕРєРЅРѕ РІС‹СЃРѕС‚РѕР№ 56dp РЅР°Рґ РЅР°РІРёРіР°С†РёРµР№ СЃ `clipToBounds()`, С‡С‚РѕР±С‹ РІРѕ РІСЂРµРјСЏ РІС‹РµР·РґР° СЃС‚СЂРѕРєР° РЅРµ СЂРёСЃРѕРІР°Р»Р°СЃСЊ РїРѕРІРµСЂС… РёРєРѕРЅРѕРє; С„РѕРЅ РґРѕРєР° СЂР°СЃС‚С‘С‚ С‡РµСЂРµР· `reveal` (spring NoBouncy/Medium), РїРѕСЌС‚РѕРјСѓ РІС‹СЃРѕС‚Р° Рё slide СЃРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°РЅС‹.
- `LocalBottomDockHeight` Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё РІС‹СЂРѕСЃ РЅР° 4dp (РЅРёР¶РЅРёР№ padding), СЌРєСЂР°РЅС‹ СѓР¶Рµ РёСЃРїРѕР»СЊР·СѓСЋС‚ РµРіРѕ РІ `contentPadding` вЂ” РїСЂР°РІРѕРє РЅРµ РїРѕС‚СЂРµР±РѕРІР°Р»РѕСЃСЊ.
- РўРµСЃС‚С‹: 22 suites / 148 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 635 309 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РЎРІР°Р№Рї РїР»РµРµСЂР°: РїРµСЂРµС…РІР°С‚ РїРµСЂРІРѕРіРѕ РєР°СЃР°РЅРёСЏ РІРјРµСЃС‚Рѕ nestedScroll вЂ” СЃРµСЃСЃРёСЏ 38
- `NestedScrollConnection` СѓРґР°Р»С‘РЅ РїРѕР»РЅРѕСЃС‚СЊСЋ: `dismissNestedScrollConnection`, `Modifier.nestedScroll()`, РєРѕСЂРЅРµРІРѕР№ `detectVerticalDragGestures` Рё РёРјРїРѕСЂС‚С‹ `NestedScrollConnection`/`NestedScrollSource`/`nestedScroll`/`Velocity`/`Offset`. РџСЂРёС‡РёРЅР°: РЅР° С‚РµРєСЃС‚Рµ Рё РїСѓСЃС‚С‹С… Р·РѕРЅР°С… Р¶РµСЃС‚ РЅРµ СЃСЂР°Р±Р°С‚С‹РІР°Р».
- РќРѕРІС‹Р№ Р¶РµСЃС‚ вЂ” `Modifier.pointerInput(Unit) { awaitEachGesture { ... } }` РЅР° РєРѕСЂРЅРµРІРѕРј `Box`, РєРѕС‚РѕСЂС‹Р№ РѕР±РѕСЂР°С‡РёРІР°РµС‚ `Scaffold` РІСЃРµРіРѕ РїР»РµРµСЂР°. `awaitFirstDown(pass = PointerEventPass.Initial)` РїРѕР»СѓС‡Р°РµС‚ РєР°СЃР°РЅРёРµ СЂР°РЅСЊС€Рµ `LazyColumn`/`verticalScroll`; РїСЂРё СЃРІРѕС‘Рј Р¶РµСЃС‚Рµ РёР·РјРµРЅРµРЅРёСЏ РїРѕС‚СЂРµР±Р»СЏСЋС‚СЃСЏ РІ Initial-РїСЂРѕС…РѕРґРµ, РїРѕСЌС‚РѕРјСѓ drag СЃРїРёСЃРєР° РѕС‚РјРµРЅСЏРµС‚СЃСЏ (`awaitPointerSlopOrCancellation` РїСЂРѕРІРµСЂСЏРµС‚ `isConsumed`).
- Р РµС€РµРЅРёРµ Рѕ Р¶РµСЃС‚Рµ РїСЂРёРЅРёРјР°РµС‚СЃСЏ РїРѕ РЅР°РєРѕРїР»РµРЅРЅРѕРјСѓ `totalDy`: РІРІРµСЂС… (`totalDy <= 0`) РІСЃРµРіРґР° РѕС‚РґР°С‘С‚СЃСЏ СЃРїРёСЃРєСѓ/РѕР±Р»РѕР¶РєРµ; РІРЅРёР· РґРІРёРіР°РµС‚ РїР»РµРµСЂ, РєСЂРѕРјРµ РєР°СЃР°РЅРёСЏ РІРЅСѓС‚СЂРё `LazyColumn` РїСЂРё СѓР¶Рµ РїСЂРѕСЃРєСЂРѕР»Р»РµРЅРЅРѕРј С‚РµРєСЃС‚Рµ (`isLyricsMode && inLyricsList && !lyricsListState.canScrollBackward`). РЁР°РїРєР°, РѕР±Р»РѕР¶РєР° Рё РїСѓСЃС‚С‹Рµ Р·РѕРЅС‹ РґРІРёРіР°СЋС‚ РїР»РµРµСЂ РґР°Р¶Рµ РїСЂРё РїСЂРѕСЃРєСЂРѕР»Р»РµРЅРЅРѕРј С‚РµРєСЃС‚Рµ.
- РћР±Р»Р°СЃС‚Рё РѕРїСЂРµРґРµР»СЏСЋС‚СЃСЏ СЂРµР°Р»СЊРЅС‹РјРё РіСЂР°РЅРёС†Р°РјРё: `controlIslandBounds` Рё `lyricsListBounds` С‡РµСЂРµР· `onGloballyPositioned { it.boundsInRoot() }`; РєР°СЃР°РЅРёРµ РёР· РѕСЃС‚СЂРѕРІР° СѓРїСЂР°РІР»РµРЅРёСЏ РёРіРЅРѕСЂРёСЂСѓРµС‚СЃСЏ. `LazyListState` РїРѕРґРЅСЏС‚ РёР· `LyricsListView` РІ `PlayerScreen` Рё РїСЂРѕР±СЂРѕС€РµРЅ С‡РµСЂРµР· `ExpressiveHeroZone` в†’ `LyricsForeground`; РіСЂР°РЅРёС†С‹ СЃРїРёСЃРєР° РїРµСЂРµРґР°СЋС‚СЃСЏ callback'РѕРј `onListBoundsChanged` Рё СЃР±СЂР°СЃС‹РІР°СЋС‚СЃСЏ РІ `null` С‡РµСЂРµР· `DisposableEffect`, РєРѕРіРґР° СЃРїРёСЃРѕРє СѓС…РѕРґРёС‚ РёР· РєРѕРјРїРѕР·РёС†РёРё.
- РћСЃС‚СЂРѕРІ СѓРїСЂР°РІР»РµРЅРёСЏ СЃРѕС…СЂР°РЅСЏРµС‚ С„СѓРЅРєС†РёРѕРЅР°Р»СЊРЅС‹Р№ guard `detectVerticalDragGestures { change, _ -> change.consume() }` (РїСѓСЃС‚РѕР№ `pointerInput` РЅРёС‡РµРіРѕ РЅРµ Р±Р»РѕРєРёСЂСѓРµС‚): РєРЅРѕРїРєРё Рё СЃР»Р°Р№РґРµСЂ РЅРµ Р·Р°РїСѓСЃРєР°СЋС‚ Р·Р°РєСЂС‹С‚РёРµ.
- РџРѕСЂРѕРі `DISMISS_THRESHOLD = 0.45f` СЃРѕС…СЂР°РЅС‘РЅ: `finishPlayerDrag()` Р·Р°РєСЂС‹РІР°РµС‚ РїСЂРё `dragOffsetPx > screenHeightPx * 0.45f`, РёРЅР°С‡Рµ `settlePlayer()` РІРѕР·РІСЂР°С‰Р°РµС‚ РїСЂСѓР¶РёРЅРѕР№; РѕС‚РјРµРЅР° Р¶РµСЃС‚Р° РІ `finally` С‚Р°РєР¶Рµ РІРѕР·РІСЂР°С‰Р°РµС‚ РїР»РµРµСЂ.
- РўРµСЃС‚С‹: 22 suites / 148 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 635 309 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РЎРІР°Р№Рї РїР»РµРµСЂР°: РїРѕСЂРѕРі 45% Рё Р·Р°РєСЂС‹С‚РёРµ РїРѕРІРµСЂС… С‚РµРєСЃС‚РѕРІ вЂ” СЃРµСЃСЃРёСЏ 37
- РџРѕСЂРѕРі Р·Р°РєСЂС‹С‚РёСЏ СЃРЅРёР¶РµРЅ СЃ 85% РґРѕ 45% РІС‹СЃРѕС‚С‹ СЌРєСЂР°РЅР° (`DISMISS_THRESHOLD = 0.45f`). Р›РѕРіРёРєР° РѕС‚РїСѓСЃРєР°РЅРёСЏ РІС‹РЅРµСЃРµРЅР° РІ `finishPlayerDrag()` Рё РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РѕР±РѕРёРјРё РїСѓС‚СЏРјРё: Р¶РµСЃС‚РѕРј РЅР° СЃРІРѕР±РѕРґРЅРѕР№ Р·РѕРЅРµ Рё nested-scroll'РѕРј СЃРїРёСЃРєР° С‚РµРєСЃС‚Р°.
- РџСЂРёС‡РёРЅР° В«СЃРІР°Р№Рї СЂР°Р±РѕС‚Р°РµС‚ С‚РѕР»СЊРєРѕ Р·Р° С€Р°РїРєСѓВ»: Р¶РµСЃС‚ СЃ СЃРµСЃСЃРёРё 36 СѓР¶Рµ Р¶РёР» РЅР° РєРѕСЂРЅРµРІРѕРј `Scaffold`, РЅРѕ `LazyColumn` С‚РµРєСЃС‚РѕРІ Рё `verticalScroll` РѕР±Р»РѕР¶РєРё РїРѕС‚СЂРµР±Р»СЏР»Рё РІРµСЂС‚РёРєР°Р»СЊРЅС‹Рµ РёР·РјРµРЅРµРЅРёСЏ СѓРєР°Р·Р°С‚РµР»СЏ СЂР°РЅСЊС€Рµ СЂРѕРґРёС‚РµР»СЏ, РїРѕСЌС‚РѕРјСѓ РєРѕСЂРЅРµРІРѕР№ `detectVerticalDragGestures` РѕС‚РјРµРЅСЏР»СЃСЏ.
- РќР° РєРѕСЂРЅРµРІРѕР№ `Scaffold` РґРѕР±Р°РІР»РµРЅ `Modifier.nestedScroll(dismissNestedScrollConnection)`:
  - `onPreScroll`: РїРѕРєР° РїР»РµРµСЂ СЃРјРµС‰С‘РЅ, СЃРЅР°С‡Р°Р»Р° РґРІРёРіР°РµС‚ РїР»РµРµСЂ, Р° РЅРµ СЃРїРёСЃРѕРє вЂ” РєРѕРЅС‚РµРЅС‚ РЅРµ РїСЂРѕРєСЂСѓС‡РёРІР°РµС‚СЃСЏ РїРѕРґ РІРѕР·РІСЂР°С‰Р°СЋС‰РµР№СЃСЏ С€С‚РѕСЂРєРѕР№;
  - `onPostScroll`: Р·Р°Р±РёСЂР°РµС‚ РѕСЃС‚Р°С‚РѕРє С‚СЏРіРё РІРЅРёР·, РєРѕРіРґР° СЃРїРёСЃРѕРє СѓР¶Рµ РІ РЅР°С‡Р°Р»Рµ (`available.y < 0`), вЂ” СЃРІР°Р№Рї СЂР°Р±РѕС‚Р°РµС‚ РїРѕРІРµСЂС… С‚РµРєСЃС‚Р° РїРµСЃРЅРё;
  - `onPreFling`: РІС‹С€Рµ РїРѕСЂРѕРіР° Р·Р°РєСЂС‹РІР°РµС‚, РёРЅР°С‡Рµ РІРѕР·РІСЂР°С‰Р°РµС‚ РїСЂСѓР¶РёРЅРѕР№, Рё РіР°СЃРёС‚ СЃРєРѕСЂРѕСЃС‚СЊ СЃРїРёСЃРєР°, С‡С‚РѕР±С‹ С‚РµРєСЃС‚ РЅРµ СѓР»РµС‚Р°Р» РѕС‚РґРµР»СЊРЅРѕР№ Р°РЅРёРјР°С†РёРµР№;
  - РїСЂРѕРіСЂР°РјРјРЅС‹Рµ СЃРєСЂРѕР»Р»С‹ (`SideEffect`, Р°РІС‚РѕС†РµРЅС‚СЂРёСЂРѕРІР°РЅРёРµ Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё) РёРіРЅРѕСЂРёСЂСѓСЋС‚СЃСЏ.
- РћСЃС‚СЂРѕРІ СѓРїСЂР°РІР»РµРЅРёСЏ РїРѕ-РїСЂРµР¶РЅРµРјСѓ РіР°СЃРёС‚ РІРµСЂС‚РёРєР°Р»СЊРЅС‹Рµ РґСЂР°РіРё СЃРІРѕРёРј `pointerInput` (`controlIslandSwipeGuard`): РµРіРѕ РєРЅРѕРїРєРё Рё СЃР»Р°Р№РґРµСЂ РЅРµ Р·Р°РїСѓСЃРєР°СЋС‚ Р·Р°РєСЂС‹С‚РёРµ. РЁР°РїРєР° Рё РїСѓСЃС‚С‹Рµ Р·РѕРЅС‹ РѕР±СЂР°Р±Р°С‚С‹РІР°СЋС‚СЃСЏ РєРѕСЂРЅРµРІС‹Рј `detectVerticalDragGestures`.
- РўРµСЃС‚С‹: 22 suites / 148 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 635 282 Р±Р°Р№С‚Р°. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РњРµР»РєРёРµ UI-С„РёРєСЃС‹, СЃРІР°Р№Рї РІРЅРёР· РїРѕ РІСЃРµРјСѓ РїР»РµРµСЂСѓ Рё РґРёР·Р»Р°Р№РєРё вЂ” СЃРµСЃСЃРёСЏ 36
- РџР»РµРµСЂ, СЃР»РѕС‚ СЃРєР°С‡РёРІР°РЅРёСЏ: Сѓ Р»РѕРєР°Р»СЊРЅРѕРіРѕ С‚СЂРµРєР° (`Source.LOCAL`) РєРЅРѕРїРєР° СЃРєР°С‡РёРІР°РЅРёСЏ РЅРµ РїРѕРєР°Р·С‹РІР°РµС‚СЃСЏ, РЅРѕ РµС‘ РјРµСЃС‚Рѕ СЂРµР·РµСЂРІРёСЂСѓРµС‚СЃСЏ `Spacer(Modifier.size(46.dp))` вЂ” СЂСЏРґ В«СЃРєР°С‡Р°С‚СЊ/Р»Р°Р№Рє/РґРёР·Р»Р°Р№РєВ» Р±РѕР»СЊС€Рµ РЅРµ РїСЂС‹РіР°РµС‚ РјРµР¶РґСѓ С‚СЂРµРєР°РјРё. РџСЂРѕР±СЂРѕС€РµРЅРѕ С‡РµСЂРµР· `canDownload` РІ `ExpressiveHeroZone` в†’ `CoverForeground` Рё `TrackGlassCard` (РѕР±Р° РІР°СЂРёР°РЅС‚Р° РѕР±Р»РѕР¶РєРё).
- `TrackCard`: РЅР°Р·РІР°РЅРёРµ Рё Р°РІС‚РѕСЂ РѕР±СЉРµРґРёРЅРµРЅС‹ РІ РѕРґРЅСѓ `Column` РІРЅСѓС‚СЂРё `headlineContent` (Р±РµР· `supportingContent`), РїРѕСЌС‚РѕРјСѓ Сѓ РєРѕСЂРѕС‚РєРёС… РЅР°Р·РІР°РЅРёР№ РёСЃС‡РµР·Р»Р° РїСѓСЃС‚Р°СЏ РґС‹СЂР° РјРµР¶РґСѓ СЃС‚СЂРѕРєР°РјРё; РІ `HomeScreen.ContinueListeningCard` РєРѕР»РѕРЅРєР° С†РµРЅС‚СЂРёСЂСѓРµС‚СЃСЏ РїРѕ РІС‹СЃРѕС‚Рµ РѕР±Р»РѕР¶РєРё (`Arrangement.Center`).
- `TrackCard`: РЅРѕРІС‹Р№ РїР°СЂР°РјРµС‚СЂ `isPaused` вЂ” РЅР° РїР°СѓР·Рµ РІРјРµСЃС‚Рѕ Р±РµРіСѓС‰РµРіРѕ СЌРєРІР°Р»Р°Р№Р·РµСЂР° СЂРёСЃСѓСЋС‚СЃСЏ С‚СЂРё СЃС‚Р°С‚РёС‡РЅС‹Рµ С‚РѕС‡РєРё `PausedEqualizerDots` С‚РѕРіРѕ Р¶Рµ СЂР°Р·РјРµСЂР° 16dp. `LibraryViewModel.isPlaying` Р±РµСЂС‘С‚СЃСЏ РёР· `PlayerController.state`, `LibraryScreen` РїРµСЂРµРґР°С‘С‚ `isPaused` РґР»СЏ С‚РµРєСѓС‰РµРіРѕ С‚СЂРµРєР°.
- РџР»РµРµСЂ РІС‹РЅРµСЃРµРЅ РёР· `NavHost` РІ РѕРІРµСЂР»РµР№ `AppNavGraph` (`isPlayerOpen`, `AnimatedVisibility` СЃРѕ slide): РІРєР»Р°РґРєР° РѕСЃС‚Р°С‘С‚СЃСЏ СЃРєРѕРјРїРѕРЅРѕРІР°РЅРЅРѕР№ РїРѕРґ РїР»РµРµСЂРѕРј Рё СЂРµР°Р»СЊРЅРѕ РїСЂРѕРіР»СЏРґС‹РІР°РµС‚ РїСЂРё СЃРІР°Р№РїРµ. РЎРёСЃС‚РµРјРЅС‹Р№ В«РЅР°Р·Р°РґВ» Р·Р°РєСЂС‹РІР°РµС‚ РѕРІРµСЂР»РµР№ С‡РµСЂРµР· `BackHandler`, РЅРµ С‚СЂРѕРіР°СЏ back stack; РїРµСЂРµС…РѕРґ В«РќР°Р№С‚Рё РЅР° YouTubeВ» СЃРЅР°С‡Р°Р»Р° Р·Р°РєСЂС‹РІР°РµС‚ РѕРІРµСЂР»РµР№.
- РЎРІР°Р№Рї РІРЅРёР·: Р¶РµСЃС‚ `detectVerticalDragGestures` Р¶РёРІС‘С‚ РЅР° РІСЃС‘Рј `Scaffold` РїР»РµРµСЂР°, РѕСЃС‚СЂРѕРІ СѓРїСЂР°РІР»РµРЅРёСЏ РіР°СЃРёС‚ РІРµСЂС‚РёРєР°Р»СЊРЅС‹Рµ РґСЂР°РіРё (`controlIslandSwipeGuard`), РїРѕСЌС‚РѕРјСѓ РєРЅРѕРїРєРё Рё СЃР»Р°Р№РґРµСЂ РЅРµ Р·Р°РєСЂС‹РІР°СЋС‚ СЌРєСЂР°РЅ. РџРѕСЂРѕРі вЂ” `DISMISS_THRESHOLD = 0.85f` РІС‹СЃРѕС‚С‹ СЌРєСЂР°РЅР°: РЅРёР¶Рµ вЂ” РІРѕР·РІСЂР°С‚ РїСЂСѓР¶РёРЅРѕР№, РЅР° РїРѕСЂРѕРіРµ Рё РІС‹С€Рµ вЂ” `onBack()`. РЎРјРµС‰РµРЅРёРµ СЃР»РµРґСѓРµС‚ Р·Р° РїР°Р»СЊС†РµРј Р±РµР· Р°РЅРёРјР°С†РёРё (`snap()`), РІРѕР·РІСЂР°С‚ вЂ” `animateFloatAsState` + `spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)`; `graphicsLayer` РґРІРёРіР°РµС‚ РІРµСЃСЊ РїР»РµРµСЂ Рё РїР»Р°РІРЅРѕ РіР°СЃРёС‚ Р°Р»СЊС„Сѓ `1f - offsetY / screenHeight`, РѕС‚РєСЂС‹РІР°СЏ РІРєР»Р°РґРєСѓ РїРѕРґ РЅРёРј.
- Room v10: РЅРѕРІР°СЏ `disliked_tracks` (`DislikedTrackEntity`: `trackId` PK, `dislikedAt`), `DislikedTrackDao` (`insert` REPLACE, `delete`, `getAll`, `isDisliked` Flow), `MIGRATION_9_10`, DAO РІ `DatabaseModule`, СЃС…РµРјР° `10.json` СЃРіРµРЅРµСЂРёСЂРѕРІР°РЅР°.
- Р РµРєРѕРјРµРЅРґР°С†РёРё: `RecommendationPlanner.filterDisliked` СѓР±РёСЂР°РµС‚ РґРёР·Р»Р°Р№РєРЅСѓС‚С‹Рµ С‚СЂРµРєРё; `HomeViewModel` Р·Р°РіСЂСѓР¶Р°РµС‚ ID РёР· DAO Рё С„РёР»СЊС‚СЂСѓРµС‚ related-, fallback- Рё popular-РєР°РЅРґРёРґР°С‚РѕРІ РґРѕ РѕС‚Р±РѕСЂР°, С‡С‚РѕР±С‹ РґРёР·Р»Р°Р№РєРё РЅРµ Р·Р°РЅРёРјР°Р»Рё СЃР»РѕС‚С‹.
- РџР»РµРµСЂ: `PlayerViewModel.isDisliked` (Flow РёР· DAO РїРѕ С‚РµРєСѓС‰РµРјСѓ С‚СЂРµРєСѓ) Рё `toggleDislike()`; РґРёР·Р»Р°Р№Рє СЃРЅРёРјР°РµС‚ Р»Р°Р№Рє Рё taste-С„РёРґР±РµРє, Р»Р°Р№Рє СЃРЅРёРјР°РµС‚ РґРёР·Р»Р°Р№Рє. Р’ РѕР±РѕРёС… layouts РґРѕР±Р°РІР»РµРЅ `DislikeSquircle` СЃ `Icons.Filled.ThumbDown`, РєСЂР°СЃРЅР°СЏ РїРѕРґСЃРІРµС‚РєР° (`errorContainer`/`error`) РїСЂРё Р°РєС‚РёРІРЅРѕРј РґРёР·Р»Р°Р№РєРµ.
- РўРµСЃС‚С‹: 22 suites / 148 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `DatabaseMigrationTest` вЂ” РЅРѕРІС‹Р№ РєРµР№СЃ 9в†’10 Рё СЃРєРІРѕР·РЅРѕР№ 1в†’10 СЃ РїСЂРѕРІРµСЂРєРѕР№ Р·Р°РїРёСЃРё РІ `disliked_tracks`; `HomeViewModelRecommendationsTest` вЂ” С„РёР»СЊС‚СЂР°С†РёСЏ РґРёР·Р»Р°Р№РєРЅСѓС‚РѕРіРѕ С‚СЂРµРєР° РёР· РІС‹РґР°С‡Рё. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 611 476 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## Р РµРґРёР·Р°Р№РЅ РџР°РєРµС‚ 4: Library/Settings + СЃРІР°Р№Рї РІРЅРёР· РІ РїР»РµРµСЂРµ вЂ” СЃРµСЃСЃРёСЏ 35
- `LibraryScreen`: С€Р°РїРєР° СЃ eyebrow В«РљРћР›Р›Р•РљР¦РРЇВ» (11sp Bold primary, С‚СЂРµРєРёРЅРі 1.4), Р·Р°РіРѕР»РѕРІРѕРє В«РњРµРґРёР°С‚РµРєР°В» 34sp ExtraBold (С‚СЂРµРєРёРЅРі в€’0.5), РїРѕРґРїРёСЃСЊ СЃ СЂСѓСЃСЃРєРѕР№ РїР»СЋСЂР°Р»РёР·Р°С†РёРµР№ В«X С‚СЂРµРє/С‚СЂРµРєР°/С‚СЂРµРєРѕРІ В· Y С‡ Z РјРёРЅВ»; РґР»СЏ РїСѓСЃС‚РѕР№ РєРѕР»Р»РµРєС†РёРё РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊ РЅРµ РїРѕРєР°Р·С‹РІР°РµС‚СЃСЏ. Р‘С‹СЃС‚СЂС‹Рµ РґРµР№СЃС‚РІРёСЏ В«РЎР»СѓС€Р°С‚СЊ РІСЃС‘В» Рё В«РџРµСЂРµРјРµС€Р°С‚СЊВ» РІС‹СЃРѕС‚РѕР№ 52dp РІ РїСЂРѕРїРѕСЂС†РёРё 1.6:1, `CircleShape`. РЎРїРёСЃРѕРє вЂ” `TrackCard` СЃ РѕР±Р»РѕР¶РєРѕР№ 60dp Рё СЂР°РґРёСѓСЃРѕРј 16dp, РїРѕРґСЃРІРµС‚РєРѕР№ РёРіСЂР°СЋС‰РµРіРѕ С‚СЂРµРєР° (`primaryContainer` 35%) Рё Р°РЅРёРјРёСЂРѕРІР°РЅРЅС‹Рј СЌРєРІР°Р»Р°Р№Р·РµСЂРѕРј. Empty state: squircle-РїР»Р°С€РєР° 96dp СЃ СЃРµСЂРґРµС‡РєРѕРј, Р·Р°РіРѕР»РѕРІРѕРє 22sp, РїРѕСЏСЃРЅРµРЅРёРµ Рё РєРЅРѕРїРєР° В«РќР°Р№С‚Рё РјСѓР·С‹РєСѓВ» (РЅРѕРІС‹Р№ `onOpenSearch` РІ `AppNavGraph`, РїРµСЂРµС…РѕРґ РєР°Рє Сѓ РІРєР»Р°РґРєРё Search СЃ СЃРѕС…СЂР°РЅРµРЅРёРµРј СЃРѕСЃС‚РѕСЏРЅРёСЏ).
- `TrackCard`: РЅРѕРІС‹Рµ РїР°СЂР°РјРµС‚СЂС‹ `isPlaying`, `artworkSize`, `artworkCornerDp` (РґРµС„РѕР»С‚С‹ 56/12 СЃРѕС…СЂР°РЅСЏСЋС‚ РІРёРґ Home/Search/Wave); `PlayingEqualizerIcon` РЅР° Canvas СЃ `rememberInfiniteTransition` (С‚СЂРё РїРѕР»РѕСЃРєРё, sin-РІРѕР»РЅР°, Р±РµР· СЂРµРєРѕРјРїРѕР·РёС†РёР№).
- `LibraryViewModel`: `currentTrackId` РёР· `PlayerController.state` С‡РµСЂРµР· `map` + `distinctUntilChanged` + `stateIn(WhileSubscribed(5000))` вЂ” СЃС‚СЂРѕРєР° РёРіСЂР°СЋС‰РµРіРѕ С‚СЂРµРєР° РїРѕРґСЃРІРµС‡РёРІР°РµС‚СЃСЏ Р±РµР· Р»РёС€РЅРёС… СЌРјРёСЃСЃРёР№.
- `SettingsScreen`: РІРµСЂС‚РёРєР°Р»СЊРЅС‹Рµ РѕС‚СЃС‚СѓРїС‹ РјРµР¶РґСѓ СЃРµРєС†РёСЏРјРё 32dp, Р·Р°РіРѕР»РѕРІРєРё СЃРµРєС†РёР№ 12sp Bold primary, РєР°СЂС‚РѕС‡РєРё 30dp СЃ С‚РѕРЅРєРёРј border 1dp (`onSurface` 4%), РєРЅРѕРїРєР° РїСЂРѕРІРµСЂРєРё СЂР°Р·СЂРµС€РµРЅРёР№ 44dp, С„СѓС‚РµСЂ В«Facet В· РІРµСЂСЃРёСЏ XВ» РёР· `BuildConfig.VERSION_NAME`. Р“РѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅС‹Рµ РѕС‚СЃС‚СѓРїС‹ 20dp СЃРѕС…СЂР°РЅРµРЅС‹ вЂ” РєР°Рє Сѓ Home Рё РєР°СЂС‚РѕС‡РµРє.
- `PlayerScreen`: СЃРІР°Р№Рї РІРЅРёР· РґР»СЏ Р·Р°РєСЂС‹С‚РёСЏ. Р–РµСЃС‚ Р¶РёРІС‘С‚ С‚РѕР»СЊРєРѕ РІ РІРµСЂС…РЅРµР№ Р·РѕРЅРµ вЂ” `TopAppBar` Рё hero-Р·РѕРЅР° РІ СЂРµР¶РёРјРµ РѕР±Р»РѕР¶РєРё; РІ СЂРµР¶РёРјРµ С‚РµРєСЃС‚РѕРІ СЃРїРёСЃРѕРє СЃРѕС…СЂР°РЅСЏРµС‚ СЃРІРѕР№ СЃРєСЂРѕР»Р», РїСЂРё РѕС‚РєСЂС‹С‚РѕР№ С€С‚РѕСЂРєРµ РѕС‡РµСЂРµРґРё Р¶РµСЃС‚ РѕС‚РєР»СЋС‡С‘РЅ. `detectVerticalDragGestures`, РїРѕСЂРѕРі 20% РІС‹СЃРѕС‚С‹ СЌРєСЂР°РЅР° РёР»Рё СЃРєРѕСЂРѕСЃС‚СЊ >1200 px/s (`VelocityTracker` РїРѕ РїРѕСЃР»РµРґРЅРёРј СЃРѕР±С‹С‚РёСЏРј, Р° РЅРµ РїРѕ РѕРґРЅРѕРјСѓ РєР°РґСЂСѓ). РџСЂРё РїРµСЂРµС‚Р°СЃРєРёРІР°РЅРёРё РєРѕРЅС‚РµРЅС‚ (С€Р°РїРєР° + hero/РєРѕРЅС‚СЂРѕР»С‹) СѓРµР·Р¶Р°РµС‚ РІРЅРёР· С‡РµСЂРµР· `graphicsLayer.translationY` Рё С‚РµСЂСЏРµС‚ РґРѕ 30% Р°Р»СЊС„С‹; РіСЂР°РґРёРµРЅС‚РЅС‹Р№ С„РѕРЅ РѕСЃС‚Р°С‘С‚СЃСЏ РЅР° РјРµСЃС‚Рµ, С‡С‚РѕР±С‹ РЅРµ РїСЂРѕРіР»СЏРґС‹РІР°Р»Рѕ РїСѓСЃС‚РѕРµ РѕРєРЅРѕ. РќРёР¶Рµ РїРѕСЂРѕРіР° вЂ” РІРѕР·РІСЂР°С‚ РїСЂСѓР¶РёРЅРѕР№ `NoBouncy`/`MediumLow` (РЅРµР·Р°РІРµСЂС€С‘РЅРЅС‹Р№ РІРѕР·РІСЂР°С‚ РѕС‚РјРµРЅСЏРµС‚СЃСЏ РЅРѕРІС‹Рј РєР°СЃР°РЅРёРµРј), РІС‹С€Рµ РїРѕСЂРѕРіР° вЂ” `onBack`, РґР°Р»СЊС€Рµ СЌРєСЂР°РЅ СѓРµР·Р¶Р°РµС‚ С€С‚Р°С‚РЅС‹Рј `popExit`-СЃР»Р°Р№РґРѕРј NavHost, Р±РµР· РІС‚РѕСЂРѕР№ Р°РЅРёРјР°С†РёРё Рё РїСѓСЃС‚РѕР№ РїР°СѓР·С‹. `BackHandler` Рё guard `isDismissing` Р·Р°С‰РёС‰Р°СЋС‚ РѕС‚ РґРІРѕР№РЅРѕРіРѕ pop.
- РўРµСЃС‚С‹: 22 suites / 146 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 633 153 Р±Р°Р№С‚Р°. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.
- РђСЂС…РёС‚РµРєС‚СѓСЂРЅС‹Рµ С„РёРєСЃС‹ СЃРµСЃСЃРёР№ 31вЂ“34 (Room v9 Рё РјРёРіСЂР°С†РёРё, `PlayerController`, Р·Р°РіСЂСѓР·РєРё, С‚РµРєСЃС‚С‹) РЅРµ С‚СЂРѕРЅСѓС‚С‹.

## Р”РѕР±РёРІРєРё Р°СѓРґРёС‚Р°: СЃРєР°С‡Р°РЅРЅРѕРµ, РёРЅРґРµРєСЃС‹ Room, С‚РµРєСЃС‚С‹, РѕС€РёР±РєРё Р»Р°Р№РєРѕРІ, С‚РµСЃС‚С‹ РјРёРіСЂР°С†РёР№ вЂ” СЃРµСЃСЃРёСЏ 34
- B4 (СЃРєР°С‡Р°РЅРЅРѕРµ): `observeDownloadedIds`/`observeDownloadedTracks` СЃС‚СЂРѕСЏС‚ СЃРїРёСЃРѕРє РёР· `getAll()` Рё РїСЂРѕРІРµСЂСЏСЋС‚ С„Р°Р№Р» РЅР° РґРёСЃРєРµ (`isPlayable`): РјСѓСЃРѕСЂРЅС‹Рµ Р·Р°РїРёСЃРё (СѓРґР°Р»С‘РЅРЅС‹Р№ С„Р°Р№Р», РЅР°СЃР»РµРґРёРµ HLS) РІС‹С‡РёС‰Р°СЋС‚СЃСЏ РёР· Р‘Р” С‡РµСЂРµР· `pruneUnplayable` Рё Р±РѕР»СЊС€Рµ РЅРµ СЃРІРµС‚СЏС‚СЃСЏ РєР°Рє В«СЃРєР°С‡Р°РЅРѕВ»; СѓРґР°Р»РµРЅРёРµ РїРµСЂРµР·Р°РїСѓСЃРєР°РµС‚ Flow Room, СЃР»РµРґСѓСЋС‰Р°СЏ СЌРјРёСЃСЃРёСЏ РїСЂРёС…РѕРґРёС‚ СѓР¶Рµ С‡РёСЃС‚РѕР№.
- C (Room): РёРЅРґРµРєСЃС‹ `liked_tracks.season`, `liked_tracks.likedAt`, `play_history.playedAt`, `genre_weights.weight` (`Index` РІ СЃСѓС‰РЅРѕСЃС‚СЏС…); РІРµСЂСЃРёСЏ Р‘Р” 9, `MIGRATION_8_9` СЃРѕР·РґР°С‘С‚ С‡РµС‚С‹СЂРµ РёРЅРґРµРєСЃР° СЃ РёРјРµРЅР°РјРё, РєРѕС‚РѕСЂС‹Рµ Room РіРµРЅРµСЂРёСЂСѓРµС‚ СЃР°Рј; СЃС…РµРјР° `9.json` СЃРіРµРЅРµСЂРёСЂРѕРІР°РЅР°, РёРјРµРЅР° СЃРІРµСЂРµРЅС‹ СЃ РјРёРіСЂР°С†РёРµР№.
- C (С‚РµРєСЃС‚С‹): Р·Р°РіСЂСѓР·РєР° С‚РµРєСЃС‚РѕРІ РІ `PlayerViewModel` РІС‹РЅРµСЃРµРЅР° РёР· `collect` РІ РѕС‚РґРµР»СЊРЅС‹Р№ РїРѕС‚РѕРє СЃ `flatMapLatest` (`flow<Unit> { loadLyrics(track) }`): СЃС‚Р°СЂС‹Р№ Р·Р°РїСЂРѕСЃ LRCLIB РѕС‚РјРµРЅСЏРµС‚СЃСЏ РїСЂРё СЃРјРµРЅРµ С‚СЂРµРєР° Рё Р±РѕР»СЊС€Рµ РЅРµ Р±Р»РѕРєРёСЂСѓРµС‚ РѕР±СЂР°Р±РѕС‚РєСѓ СЃР»РµРґСѓСЋС‰РµР№ СЃРјРµРЅС‹; СЃР±СЂРѕСЃ РїРѕР·РёС†РёРё Рё РѕС€РёР±РѕРє РѕСЃС‚Р°Р»СЃСЏ РІ РїР°СЂР°Р»Р»РµР»СЊРЅРѕРј collect.
- C (РѕС€РёР±РєРё Р»Р°Р№РєРѕРІ): `toggleLike` (РїР»РµРµСЂ) Рё `removeLike` (РјРµРґРёР°С‚РµРєР°) РѕР±С‘СЂРЅСѓС‚С‹ РІ `runCatching` + `rethrowCancellation`: СЃР±РѕР№ Р‘Р” РЅРµ РєСЂР°С€РёС‚ РїСЂРёР»РѕР¶РµРЅРёРµ, Р° РїРѕРєР°Р·С‹РІР°РµС‚ СЃРѕРѕР±С‰РµРЅРёРµ В«РќРµ СѓРґР°Р»РѕСЃСЊ РѕР±РЅРѕРІРёС‚СЊ Р»Р°Р№РєвЂ¦В» вЂ” РІ РїР»РµРµСЂРµ С‡РµСЂРµР· `uiState.error`, РІ РјРµРґРёР°С‚РµРєРµ С‡РµСЂРµР· РЅРѕРІС‹Р№ `likeError` + Snackbar; taste-С„РёРґР±РµРє (`onLike`/`onUnlike`) РѕР±С‘СЂРЅСѓС‚ РѕС‚РґРµР»СЊРЅРѕ Рё С‚РѕР»СЊРєРѕ Р»РѕРіРёСЂСѓРµС‚СЃСЏ.
- B7 (С‚РµСЃС‚С‹ РјРёРіСЂР°С†РёР№): РІ `DatabaseMigrationTest` С€РµСЃС‚СЊ РёР·РѕР»РёСЂРѕРІР°РЅРЅС‹С… С‚РµСЃС‚РѕРІ РЅР° raw-SQL-Р·Р°РіР»СѓС€РєР°С… СЃС…РµРј v1вЂ“v3 (РІ `app/schemas` РѕРЅРё РЅРµ СЌРєСЃРїРѕСЂС‚РёСЂРѕРІР°Р»РёСЃСЊ): 2в†’3 (РєРѕРѕСЂРґРёРЅР°С‚С‹), 3в†’4 (СЃРµР·РѕРЅ), 4в†’5 (С‚Р°Р±Р»РёС†С‹ РІРєСѓСЃРѕРІ), 5в†’6 (downloaded_tracks), 7в†’8 (recently_shown + РёРЅРґРµРєСЃ), 8в†’9 (С‡РµС‚С‹СЂРµ РёРЅРґРµРєСЃР°); СЃРєРІРѕР·РЅРѕР№ С‚РµСЃС‚ С‚РµРїРµСЂСЊ 1в†’9 Рё С‚РѕР¶Рµ РїСЂРѕРІРµСЂСЏРµС‚ РёРЅРґРµРєСЃС‹.
- РўРµСЃС‚С‹: 22 suites / 146 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped (DatabaseMigrationTest вЂ” 8 РєРµР№СЃРѕРІ). `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 611 487 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РњРѕРЅРѕС…СЂРѕРјРЅС‹Р№ Р°С…СЂРѕРјР°С‚РёС‡РµСЃРєРёР№ Р°РєС†РµРЅС‚, HEX-РІРІРѕРґ Рё РЅРµРґР°РІРЅРёРµ С†РІРµС‚Р° вЂ” СЃРµСЃСЃРёСЏ 33
- Р‘Р°Рі В«Р±РѕСЂРґРѕРІС‹Р№ РёР· С‡С‘СЂРЅРѕРіРѕВ»: Сѓ С‡С‘СЂРЅРѕРіРѕ/Р±РµР»РѕРіРѕ/СЃРµСЂРѕРіРѕ РѕС‚С‚РµРЅРѕРє РІ HSL СЂР°РІРµРЅ 0 (РєСЂР°СЃРЅС‹Р№), Р° `accentColorScheme` РїРѕРґСЃС‚Р°РІР»СЏР» РІ СЂРѕР»Рё Р¶С‘СЃС‚РєРѕ Р·Р°РґР°РЅРЅСѓСЋ РЅР°СЃС‹С‰РµРЅРЅРѕСЃС‚СЊ вЂ” Р°С…СЂРѕРјР°С‚РёС‡РµСЃРєРёР№ seed РєСЂР°СЃРёР»СЃСЏ РІ Р±РѕСЂРґРѕРІС‹Р№. Р”РѕР±Р°РІР»РµРЅ РїРѕСЂРѕРі `ACHROMATIC_SATURATION_THRESHOLD = 0.05f`: РЅРёР¶Рµ РЅРµРіРѕ РЅР°СЃС‹С‰РµРЅРЅРѕСЃС‚СЊ СЂРѕР»РµР№ РѕР±РЅСѓР»СЏРµС‚СЃСЏ (СЃС‚СЂРѕРіРѕ РјРѕРЅРѕС…СЂРѕРјРЅР°СЏ РїР°Р»РёС‚СЂР°), Р° СЃРІРµС‚Р»РѕС‚Р° СЂРѕР»РµР№ С‚СЏРЅРµС‚СЃСЏ Рє СЃРІРµС‚Р»РѕС‚Рµ seed РЅР° В±0.08, РїРѕСЌС‚РѕРјСѓ С‡С‘СЂРЅС‹Р№ Рё Р±РµР»С‹Р№ РґР°СЋС‚ СЂР°Р·Р»РёС‡РёРјС‹Рµ РѕС‚С‚РµРЅРєРё СЃРµСЂРѕРіРѕ РїСЂРё СЃРѕС…СЂР°РЅРЅРѕРј РєРѕРЅС‚СЂР°СЃС‚Рµ РїР°СЂ. РўР° Р¶Рµ Р·Р°С‰РёС‚Р° РІ `PlayerPalette.computePlayerColors`: СЃРµСЂС‹Рµ РѕР±Р»РѕР¶РєРё Р±РѕР»СЊС€Рµ РЅРµ С‚РѕРЅРёСЂСѓСЋС‚СЃСЏ Р±РѕСЂРґРѕРІС‹Рј (СЂР°РЅСЊС€Рµ РєР»Р°РјРї РїСЂРёРЅСѓРґРёС‚РµР»СЊРЅРѕ РїРѕРґРЅРёРјР°Р» РЅР°СЃС‹С‰РµРЅРЅРѕСЃС‚СЊ РґРѕ 0.20).
- HEX-РІРІРѕРґ РІ RGB-РїРёРєРµСЂРµ: `OutlinedTextField` СЂСЏРґРѕРј СЃ РїСЂРµРІСЊСЋ, РґРІСѓСЃС‚РѕСЂРѕРЅРЅСЏСЏ СЃРІСЏР·СЊ вЂ” РєРѕСЂСЂРµРєС‚РЅС‹Р№ РєРѕРґ (#RRGGBB РёР»Рё RRGGBB, СЂРµРіРёСЃС‚СЂ РЅРµ РІР°Р¶РµРЅ) РґРІРёРіР°РµС‚ СЃР»Р°Р№РґРµСЂС‹ Рё РїСЂРµРІСЊСЋ, СЃР»Р°Р№РґРµСЂС‹ РїРµСЂРµРїРёСЃС‹РІР°СЋС‚ РєРѕРґ; РЅРµРєРѕСЂСЂРµРєС‚РЅС‹Р№ РІРІРѕРґ С†РІРµС‚ РЅРµ РїСЂРёРјРµРЅСЏРµС‚, РїРѕР»Рµ РїРµСЂРµС…РѕРґРёС‚ РІ `isError` СЃ РїРѕРґСЃРєР°Р·РєРѕР№. Р’РІРѕРґ РѕРіСЂР°РЅРёС‡РµРЅ 7 СЃРёРјРІРѕР»Р°РјРё (В«#В» + 6 С†РёС„СЂ), СЂР°Р·Р±РѕСЂ вЂ” `parseHexColor`.
- РќРµРґР°РІРЅРёРµ С†РІРµС‚Р°: `AppSettings.recentAccentColors` (РєР»СЋС‡ SharedPreferences `recent_accent_colors`, СЃС‚СЂРѕРєР° С‡РµСЂРµР· Р·Р°РїСЏС‚СѓСЋ), `SettingsRepository.addRecentAccentColor` вЂ” LIFO, РґРµРґСѓРїР»РёРєР°С†РёСЏ, РѕР±СЂРµР·РєР° РґРѕ 5 (`MAX_RECENT_ACCENTS`); РІС‹Р·С‹РІР°РµС‚СЃСЏ РёР· `setAccentColor` (РїСЂРµСЃРµС‚С‹) Рё `setCustomAccentColor` (РЅРµ null). Р’ СЃРµРєС†РёРё В«РћС„РѕСЂРјР»РµРЅРёРµВ» РЅР°Рґ РѕСЃРЅРѕРІРЅРѕР№ РїР°Р»РёС‚СЂРѕР№ РїРѕСЏРІРёР»СЃСЏ СЂСЏРґ В«РќРµРґР°РІРЅРёРµВ» СЃ РјР°Р»РµРЅСЊРєРёРјРё РєСЂСѓР¶РєР°РјРё (Р·РѕРЅР° 36dp, РєСЂСѓР¶РѕРє 22в†’26dp СЃ РіР°Р»РѕС‡РєРѕР№ РЅР° РІС‹Р±СЂР°РЅРЅРѕРј), С‚Р°Рї РїСЂРёРјРµРЅСЏРµС‚ С†РІРµС‚ РјРіРЅРѕРІРµРЅРЅРѕ.
- РўРµСЃС‚С‹: РЅРѕРІС‹Рµ `AccentSchemeTest` (6 РєРµР№СЃРѕРІ: РјРѕРЅРѕС…СЂРѕРјРЅРѕСЃС‚СЊ С‡С‘СЂРЅРѕРіРѕ/Р±РµР»РѕРіРѕ/СЃРµСЂРѕРіРѕ РІ РѕР±РµРёС… С‚РµРјР°С…, СЂР°Р·Р»РёС‡РёРјРѕСЃС‚СЊ СЃРµСЂРѕРіРѕ, СЃРѕС…СЂР°РЅРµРЅРёРµ hue РЅР°СЃС‹С‰РµРЅРЅС‹Рј seed, С‡РёС‚Р°РµРјРѕСЃС‚СЊ primary) Рё `SettingsRepositoryImplTest` (5 РєРµР№СЃРѕРІ: LIFO, РґРµРґСѓРїР»РёРєР°С†РёСЏ, РѕР±СЂРµР·РєР° РґРѕ 5, РїРµСЂСЃРёСЃС‚РµРЅС‚РЅРѕСЃС‚СЊ). РС‚РѕРіРѕ 22 suites / 140 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 611 486 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## Р’РµСЂСЃС‚РєР° В«РђРєС†РµРЅС‚РЅРѕРіРѕ С†РІРµС‚Р°В»: СЃРєСЂРѕР»Р»РёСЂСѓРµРјР°СЏ РїР°Р»РёС‚СЂР° Рё РєСЂСѓРі СЃ РїР°Р»РёС‚СЂРѕР№ вЂ” СЃРµСЃСЃРёСЏ 32
- Р‘Р°Рі: 7 РєСЂСѓР¶РєРѕРІ С„РёРєСЃРёСЂРѕРІР°РЅРЅРѕРіРѕ СЂР°Р·РјРµСЂР° (6 РїСЂРµСЃРµС‚РѕРІ + В«РЎРІРѕР№ С†РІРµС‚В») Р»РµР¶Р°Р»Рё РІ РѕР±С‹С‡РЅРѕРј `Row` РІРЅСѓС‚СЂРё РєР°СЂС‚РѕС‡РєРё В«РћС„РѕСЂРјР»РµРЅРёРµВ» вЂ” РЅР° СѓР·РєРёС… СЌРєСЂР°РЅР°С… СЂСЏРґ РІС‹С…РѕРґРёР» Р·Р° РґРѕСЃС‚СѓРїРЅСѓСЋ С€РёСЂРёРЅСѓ, РїРѕСЃР»РµРґРЅРёР№ СЌР»РµРјРµРЅС‚ СЃРїР»СЋС‰РёРІР°Р»Рѕ РІ РІРµСЂС‚РёРєР°Р»СЊРЅСѓСЋ РїРѕР»РѕСЃРєСѓ, Р·РѕРЅР° РЅР°Р¶Р°С‚РёСЏ СЃСЉРµР·Р¶Р°Р»Р°.
- `SettingsScreen.kt`: СЂСЏРґ Р°РєС†РµРЅС‚РѕРІ РїРµСЂРµРІРµРґС‘РЅ РЅР° `Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()))` вЂ” РєСЂСѓР¶РєРё СЃРѕС…СЂР°РЅСЏСЋС‚ СЂР°Р·РјРµСЂ Рё СЃРєСЂРѕР»Р»СЏС‚СЃСЏ РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅРѕ; РєСЂР°Р№РЅРёРµ `Spacer(8.dp)` РІРјРµСЃС‚Рµ СЃ `spacedBy(10.dp)` РґР°СЋС‚ 18dp РѕС‚ РєСЂР°СЏ РєР°СЂС‚РѕС‡РєРё, РїСЂРё СЃРєСЂРѕР»Р»Рµ РєСЂСѓР¶РєРё РЅРµ РїСЂРёР»РёРїР°СЋС‚ Рє РіСЂР°РЅРёС†Р°Рј СЌРєСЂР°РЅР° (РєР°СЂС‚РѕС‡РєР° СЃР°РјР° РѕС‚СЃС‚РѕРёС‚ РЅР° 20dp). РћСЂС‚РѕРіРѕРЅР°Р»СЊРЅС‹Р№ СЃРєСЂРѕР»Р» РІРЅСѓС‚СЂРё РІРµСЂС‚РёРєР°Р»СЊРЅРѕРіРѕ `verticalScroll` СЌРєСЂР°РЅР° СЂР°Р±РѕС‚Р°РµС‚ С€С‚Р°С‚РЅРѕ.
- РЎС‚Р°СЂС‹Р№ `CustomAccentDot` СѓРґР°Р»С‘РЅ. Р’ РєРѕРЅС†Рµ СЃРїРёСЃРєР° РЅРѕРІС‹Р№ `CustomColorDot`: Р·РѕРЅР° РЅР°Р¶Р°С‚РёСЏ 44dp Рё РєСЂСѓР¶РѕРє 34в†’40dp РєР°Рє Сѓ РїСЂРµСЃРµС‚РѕРІ, Р·Р°Р»РёРІРєР° вЂ” Р·Р°РјРєРЅСѓС‚С‹Р№ СЂР°РґСѓР¶РЅС‹Р№ `Brush.sweepGradient` СЃ Р±РµР»РѕР№ РёРєРѕРЅРєРѕР№ РїР°Р»РёС‚СЂС‹; РїРѕСЃР»Рµ РїСЂРёРјРµРЅРµРЅРёСЏ СЃРІРѕРµРіРѕ С†РІРµС‚Р° РєСЂСѓРі Р·Р°Р»РёРІР°РµС‚СЃСЏ СЌС‚РёРј С†РІРµС‚РѕРј Рё РїРѕР»СѓС‡Р°РµС‚ РіР°Р»РѕС‡РєСѓ СЃ РєРѕР»СЊС†РѕРј `onSurface`. Р’РјРµСЃС‚Рѕ `indication = null` вЂ” С€С‚Р°С‚РЅР°СЏ СЂСЏР±СЊ (`clickable` Р±РµР· РїРµСЂРµРѕРїСЂРµРґРµР»РµРЅРёСЏ), РєСЂСѓРі С‡РёС‚Р°РµС‚СЃСЏ РєР°Рє РєРЅРѕРїРєР°; С‚Р°Рї РѕС‚РєСЂС‹РІР°РµС‚ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёР№ RGB-РґРёР°Р»РѕРі СЃРѕ СЃР»Р°Р№РґРµСЂР°РјРё.
- РџРѕРґСЃРєР°Р·РєР° РїРѕРґ Р·Р°РіРѕР»РѕРІРєРѕРј РѕР±РЅРѕРІР»РµРЅР°: В«Р’С‹Р±РµСЂРёС‚Рµ С†РІРµС‚ РёР· РїР°Р»РёС‚СЂС‹ РёР»Рё РЅР°Р¶РјРёС‚Рµ РЅР° РєСЂСѓРі СЃ РїР°Р»РёС‚СЂРѕР№ РІ РєРѕРЅС†Рµ СЃРїРёСЃРєР°, С‡С‚РѕР±С‹ Р·Р°РґР°С‚СЊ СЃРІРѕР№ RGBВ».
- РўРµСЃС‚С‹: 20 suites / 129 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 645 526 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РљРѕРјРїР»РµРєСЃРЅРѕРµ СЂРµРІСЊСЋ: Р°СѓРґРёС‚, СЂРµРіСЂРµСЃСЃРёРё UI Рё Р°СЂС…РёС‚РµРєС‚СѓСЂРЅС‹Рµ РїСЂР°РІРєРё вЂ” СЃРµСЃСЃРёСЏ 31
- Р­РўРђРџ 1 (Р°СѓРґРёС‚, РѕС‚С‡С‘С‚ РІС‹РґР°РЅ РґРѕ РїСЂР°РІРѕРє): РєСЂРёС‚РёС‡РЅРѕ вЂ” СЃРёРЅРіР»С‚РѕРЅ `PlayerController` РЅРµ РїРµСЂРµРїРѕРґРєР»СЋС‡Р°Р»СЃСЏ Рє РїРµСЂРµСЃРѕР·РґР°РЅРЅРѕРјСѓ `MusicService` (РјС‘СЂС‚РІС‹Р№ binder РґРѕ РїРµСЂРµР·Р°РїСѓСЃРєР° РїСЂРѕС†РµСЃСЃР°, `release()` вЂ” РјС‘СЂС‚РІС‹Р№ РєРѕРґ); РїСЂРѕРІР°Р» `buildAsync()` Р·Р°РІРµСЂС€Р°Р» Deferred РЅР°РІСЃРµРіРґР°; `LocalContentColor` РІРѕ РІСЃС‘Рј РїСЂРёР»РѕР¶РµРЅРёРё = `Color.Black` РїРѕСЃР»Рµ СѓРґР°Р»РµРЅРёСЏ РєРѕСЂРЅРµРІРѕРіРѕ Scaffold (СЃРµСЃСЃРёСЏ 29) вЂ” РїРµСЂРІРѕРїСЂРёС‡РёРЅР° В«С‡С‘СЂРЅС‹С… Р·Р°РіРѕР»РѕРІРєРѕРІВ»; `Theme.Facet` Р±РµР· values-night в†’ Р±РµР»РѕРµ РѕРєРЅРѕ; РёРЅС‚РµСЂСЃРµРїС‚РѕСЂ OkHttp РїРµСЂРµР·Р°РїРёСЃС‹РІР°Р» User-Agent РІСЃРµС… Р·Р°РїСЂРѕСЃРѕРІ NewPipe; РІ Р·Р°РіСЂСѓР·РєР°С… РЅРµ Р±С‹Р»Рѕ `Result.retry()`/backoff. Р§РёСЃС‚Рѕ: GlobalScope/runBlocking/main-thread IO, Activity-РєРѕРЅС‚РµРєСЃС‚ РІ Hilt, С†РёРєР»С‹ РІ DI-РіСЂР°С„Рµ, N+1, fallbackToDestructiveMigration, РєСЌС€ РїСЂРѕС‚СѓС…С€РёС… stream-URL, СѓС‚РµС‡РєРё РїРѕС‚РѕРєРѕРІ.
- Р‘РµР»Р°СЏ РІСЃРїС‹С€РєР°: РЅРµРїСЂРѕР·СЂР°С‡РЅС‹Р№ `background` РЅР° РєРѕСЂРЅРµРІРѕРј Box РІ `AppNavGraph`; РЅРѕРІС‹Р№ `values-night/themes.xml` Рё СЏРІРЅС‹Р№ `android:windowBackground` (#FFF8F5 / #171210, СЃРѕРІРїР°РґР°РµС‚ СЃ Color.kt) вЂ” РѕРєРЅРѕ Р±РѕР»СЊС€Рµ РЅРµ Р±РµР»РѕРµ РЅРё РЅР° С…РѕР»РѕРґРЅРѕРј СЃС‚Р°СЂС‚Рµ, РЅРё РІ РїРµСЂРµС…РѕРґР°С… РІРєР»Р°РґРѕРє.
- Р РµР¶РёРј В«РўРµРєСЃС‚В»: СѓР±СЂР°РЅ РІС‚РѕСЂРѕР№ `Spacer(weight(1f))` вЂ” РІ СЂРµР¶РёРјРµ С‚РµРєСЃС‚РѕРІ РґРІР° СЂР°РІРЅС‹С… РІРµСЃР° РґРµР»РёР»Рё РѕСЃС‚Р°С‚РѕРє РїРѕРїРѕР»Р°Рј, РѕСЃС‚СЂРѕРІ СѓРїСЂР°РІР»РµРЅРёСЏ СѓРµР·Р¶Р°Р» РІ СЃРµСЂРµРґРёРЅСѓ, СЃРЅРёР·Сѓ РѕСЃС‚Р°РІР°Р»Р°СЃСЊ РґС‹СЂР°. РўРµРїРµСЂСЊ СЃРїРёСЃРѕРє Р·Р°РЅРёРјР°РµС‚ `weight(1f)`, РѕСЃС‚СЂРѕРІ РїСЂРёР¶Р°С‚ Рє РЅРёР·Сѓ (`wrapContentHeight`); РІ СЂРµР¶РёРјРµ РѕР±Р»РѕР¶РєРё РїРѕРІРµРґРµРЅРёРµ СЃРµСЃСЃРёРё 30 СЃРѕС…СЂР°РЅРµРЅРѕ.
- Р§С‘СЂРЅС‹Рµ Р·Р°РіРѕР»РѕРІРєРё: `FacetTheme` РїСЂРѕРІР°Р№РґРёС‚ `LocalContentColor = onBackground` (С‚Рѕ, С‡С‚Рѕ РґР°РІР°Р» СѓРґР°Р»С‘РЅРЅС‹Р№ Scaffold); СЏРІРЅС‹Р№ `onSurface` РЅР° В«РќР°СЃС‚СЂРѕР№РєРёВ» Рё РЅР°Р·РІР°РЅРёСЏ СЃРµРєС†РёР№; `contentColor = onSurface` РІ Scaffold РїР»РµРµСЂР°, РёРЅР°С‡Рµ `contentColorFor(Color.Transparent)` = Unspecified.
- РђРєС†РµРЅС‚РЅС‹Р№ С†РІРµС‚: РєСЂСѓР¶РєРё РїР°Р»РёС‚СЂС‹ Р±С‹Р»Рё С„РёР·РёС‡РµСЃРєРё РІС‹РєР»СЋС‡РµРЅС‹ РЅР° Android 12+ (`enabled = !supportsDynamicColors`), С‚Р°Рї РЅРµ РґРѕС…РѕРґРёР» РґРѕ ViewModel. РўРµРїРµСЂСЊ РІРєР»СЋС‡РµРЅС‹ РІСЃРµРіРґР° Рё РїРёС€СѓС‚ РІ `customAccentColor` вЂ” РѕРЅ РёРјРµРµС‚ РІС‹СЃС€РёР№ РїСЂРёРѕСЂРёС‚РµС‚ РІ С‚РµРјРµ Рё РїРµСЂРµРєСЂС‹РІР°РµС‚ Dynamic Color; `accentColorArgb` РѕСЃС‚Р°Р»СЃСЏ legacy РґР»СЏ <12. РљРѕР»СЊС†Рѕ В«РІС‹Р±СЂР°РЅВ» РѕРґРЅРѕ: РїСЂРµСЃРµС‚ РёР»Рё В«РЎРІРѕР№ С†РІРµС‚В».
- РџР°Р»РёС‚СЂР° РїР»РµРµСЂР°: СѓР±СЂР°РЅ Р±СѓСЃС‚ РЅР°СЃС‹С‰РµРЅРЅРѕСЃС‚Рё Г—1.4 (РєРёСЃР»РѕС‚РЅС‹Рµ С†РІРµС‚Р° РЅР° СЃРµСЂС‹С… РѕР±Р»РѕР¶РєР°С…) вЂ” Г—1.15 СЃ РєР»Р°РјРїРѕРј 0.20вЂ“0.58, СЃРІРµС‚Р»РѕС‚Р° 0.38вЂ“0.62; `onPrimaryContainer`/`onSecondaryContainer` Р±РѕР»СЊС€Рµ РЅРµ СЂР°РІРЅС‹ СЃР°РјРѕРјСѓ С‚РёРЅС‚Сѓ (С‚РµРєСЃС‚ СЃР»РёРІР°Р»СЃСЏ СЃ РєРѕРЅС‚РµР№РЅРµСЂРѕРј) вЂ” РєРѕРЅС‚СЂР°СЃС‚РЅР°СЏ РїР°СЂР° РѕС‚ СЃРІРµС‚Р»РѕС‚С‹; СЂР°РјРїР° С„РѕРЅР° СЃРіР»Р°Р¶РµРЅР° (0.58/0.26 РІРјРµСЃС‚Рѕ 0.88/0.40) Рё РїРѕР»СѓС‡РёР»Р° СЏРІРЅС‹Рµ colorStops 0/0.55/1; `blendPaletteWithAccent` РїРµСЂРµСЃС‡РёС‚С‹РІР°РµС‚ РєРѕРЅС‚СЂР°СЃС‚РЅС‹Рµ СЂРѕР»Рё РїРѕСЃР»Рµ СЃРјРµС€РёРІР°РЅРёСЏ; `CancellationException` РІ `computePlayerColors` СЂРµР±СЂРѕСѓР·РёС‚СЃСЏ.
- РђСЂС…РёС‚РµРєС‚СѓСЂР° 1 (`PlayerController`): `MediaController.Builder.setListener` + `onDisconnected` в†’ СЃРЅСЏС‚СЊ СЃР»СѓС€Р°С‚РµР»РµР№, РѕСЃРІРѕР±РѕРґРёС‚СЊ РјС‘СЂС‚РІС‹Р№ РєРѕРЅС‚СЂРѕР»Р»РµСЂ, РїРµСЂРµСЃРѕР·РґР°С‚СЊ Deferred Рё РїРµСЂРµРїРѕРґРєР»СЋС‡РёС‚СЊСЃСЏ; РїСЂРѕРІР°Р» РїРѕРґРєР»СЋС‡РµРЅРёСЏ вЂ” СЂРµС‚СЂР°Р№ СЃ СЌРєСЃРїРѕРЅРµРЅС†РёР°Р»СЊРЅРѕР№ Р·Р°РґРµСЂР¶РєРѕР№ (РґРѕ 5 РїРѕРїС‹С‚РѕРє) Рё РѕС€РёР±РєРѕР№ РІ state РІРјРµСЃС‚Рѕ РІРµС‡РЅРѕРіРѕ РјРѕР»С‡Р°РЅРёСЏ; `release()` СЂРµР°Р»СЊРЅРѕ СЃРЅРёРјР°РµС‚ СЃР»СѓС€Р°С‚РµР»РµР№. `MusicService.onTaskRemoved`: stopSelf С‚РѕР»СЊРєРѕ РµСЃР»Рё РЅРёС‡РµРіРѕ РЅРµ РёРіСЂР°РµС‚.
- РђСЂС…РёС‚РµРєС‚СѓСЂР° 2 (Р·Р°РіСЂСѓР·РєРё): С‚СЂР°РЅР·РёРµРЅС‚РЅС‹Рµ СЃР±РѕРё (IOException, 408/425/429/5xx, РЅРµСЃРѕРІРїР°РІС€РёР№ СЂР°Р·РјРµСЂ) в†’ `Result.retry()` + `setBackoffCriteria(EXPONENTIAL, 30s)`, РґРѕ 3 РїРѕРїС‹С‚РѕРє; РїРѕСЃС‚РѕСЏРЅРЅС‹Рµ (В«С‚РѕР»СЊРєРѕ РѕРЅР»Р°Р№РЅВ», РЅРµС‚ РєР°С‚Р°Р»РѕРіР°) в†’ СЃСЂР°Р·Сѓ failure. РўРµРєСЃС‚ РёР· `outputData[KEY_ERROR]` РґРѕРµР·Р¶Р°РµС‚ РґРѕ UI: `DownloadState.Error.message` в†’ `DownloadMessages.friendly` в†’ Snackbar РїР»РµРµСЂР°. `lastTapAt` в†’ `ConcurrentHashMap` СЃ TTL; РїРѕРІС‚РѕСЂРЅС‹Р№ С‚Р°Рї РїРѕ РєР°С‡Р°СЋС‰РµРјСѓСЃСЏ С‚СЂРµРєСѓ РЅРµ РѕР±СЂС‹РІР°РµС‚ RUNNING-РІРѕСЂРєРµСЂ (РїСЂРѕРІРµСЂРєР° `InProgress`).
- РђСЂС…РёС‚РµРєС‚СѓСЂР° 3 (СЃРµС‚СЊ): Р·Р°РіРѕР»РѕРІРєРё РїРѕРґСЃС‚Р°РІР»СЏСЋС‚СЃСЏ С‚РѕР»СЊРєРѕ РµСЃР»Рё РёС… РЅРµС‚ вЂ” NewPipe СЃРѕС…СЂР°РЅСЏРµС‚ Р±СЂР°СѓР·РµСЂРЅС‹Р№ User-Agent, РґСѓР±Р»Рё UA РёСЃРєР»СЋС‡РµРЅС‹.
- РўРµСЃС‚С‹: 20 suites / 129 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 526 739 Р±Р°Р№С‚. APK СѓСЃС‚Р°РЅРѕРІР»РµРЅ РЅР° SM-A266B РїРѕ WiFi (`adb install -r` вЂ” Success), Р¶РёРІРѕР№ РїСЂРѕРіРѕРЅ РЅРµ РІС‹РїРѕР»РЅРµРЅ: С‚РµР»РµС„РѕРЅ РЅР° СЌРєСЂР°РЅРµ Р±Р»РѕРєРёСЂРѕРІРєРё, СЂР°Р·Р±Р»РѕРєРёСЂРѕРІР°С‚СЊ СѓРґР°Р»С‘РЅРЅРѕ РЅРµР»СЊР·СЏ; `svc power stayon` РІРѕР·РІСЂР°С‰С‘РЅ РІ false.
- РћСЃС‚Р°Р»РѕСЃСЊ (РЅРµ РєСЂРёС‚РёС‡РЅРѕ): РјРёРіСЂР°С†РёРё 2в†’3вЂ¦7в†’8 Р±РµР· РёР·РѕР»РёСЂРѕРІР°РЅРЅС‹С… С‚РµСЃС‚РѕРІ Рё РЅРµСЌРєСЃРїРѕСЂС‚РёСЂРѕРІР°РЅРЅС‹Рµ СЃС…РµРјС‹ v1вЂ“v3; РёРЅРґРµРєСЃС‹ РЅР° `liked_tracks.season/likedAt` Рё `play_history.playedAt`; `loadLyrics` inline РІ collect (РЅСѓР¶РµРЅ `flatMapLatest`); Snackbar СЃ РїСЂРёС‡РёРЅРѕР№ РїСЂРѕРІР°Р»Р° С‚РѕР»СЊРєРѕ РІ РїР»РµРµСЂРµ, РІ РјРµРґРёР°С‚РµРєРµ вЂ” РєСЂРµСЃС‚РёРє Р±РµР· С‚РµРєСЃС‚Р°; РіРѕРЅРєР° `resetReplayCache` РІ NavigationEvents.

## Р¤РёРєСЃС‹ РїРѕСЃР»Рµ СЂРµС„Р°РєС‚РѕСЂРёРЅРіР° РЅР°РІРёРіР°С†РёРё + РєР°СЃС‚РѕРјРёР·Р°С†РёСЏ С‚РµРј вЂ” СЃРµСЃСЃРёСЏ 30
- Р‘РµР»С‹Рµ СЌРєСЂР°РЅС‹: РїРѕСЃР»Рµ СѓРґР°Р»РµРЅРёСЏ РєРѕСЂРЅРµРІРѕРіРѕ Scaffold (СЃРµСЃСЃРёСЏ 29) СЌРєСЂР°РЅС‹ РѕСЃС‚Р°Р»РёСЃСЊ Р±РµР· С„РѕРЅР°. Р’ РєРѕСЂРµРЅСЊ Home, Wave, Settings, Library Рё Search РґРѕР±Р°РІР»РµРЅ СЏРІРЅС‹Р№ `Modifier.background(MaterialTheme.colorScheme.background)` (РґР»СЏ Search/Library вЂ” РЅР° СЃР°Рј Scaffold).
- РџР»РµРµСЂ, С‡С‘СЂРЅС‹Р№ Р·Р°РіРѕР»РѕРІРѕРє: РЅР°Р·РІР°РЅРёРµ С‚СЂРµРєР° РІ СЃС‚РµРєР»СЏРЅРЅРѕР№ РєР°СЂС‚РѕС‡РєРµ СЃС‚Р°Р»Рѕ С‡С‘СЂРЅС‹Рј вЂ” `contentColorFor` РїРѕР»СѓРїСЂРѕР·СЂР°С‡РЅРѕРіРѕ РєРѕРЅС‚РµР№РЅРµСЂР° (РїР°Р»РёС‚СЂР° СЃРІРµС‚Р»РѕР№ РѕР±Р»РѕР¶РєРё) РґР°РІР°Р» С‡С‘СЂРЅС‹Р№ С‚РµРєСЃС‚ РЅР° С‚С‘РјРЅРѕРј С„РѕРЅРµ. Р¦РІРµС‚ Р·Р°РґР°РЅ СЏРІРЅРѕ: `onSurface` РІ `TrackGlassCard` Рё РІ РІР°СЂРёР°РЅС‚Рµ 3 (РЅР°Р·РІР°РЅРёРµ РЅР° РѕР±Р»РѕР¶РєРµ).
- РџР»РµРµСЂ, В«РґС‹СЂР°В»: hero-Р·РѕРЅР° Р±РѕР»СЊС€Рµ РЅРµ СЂР°СЃС‚СЏРіРёРІР°РµС‚СЃСЏ РЅР° РІРµСЃСЊ СЃРІРѕР±РѕРґРЅС‹Р№ СЌРєСЂР°РЅ РІ СЂРµР¶РёРјРµ РѕР±Р»РѕР¶РєРё вЂ” `weight(1f)` РѕСЃС‚Р°РІР»РµРЅ С‚РѕР»СЊРєРѕ РґР»СЏ СЂРµР¶РёРјР° С‚РµРєСЃС‚РѕРІ (СЃРїРёСЃРєСѓ РЅСѓР¶РЅР° РѕРіСЂР°РЅРёС‡РµРЅРЅР°СЏ РІС‹СЃРѕС‚Р°), `AnimatedContent` Рё `CoverForeground` РѕР±РѕСЂР°С‡РёРІР°СЋС‚ РєРѕРЅС‚РµРЅС‚ РїРѕ РІС‹СЃРѕС‚Рµ, Р° РѕСЃС‚Р°С‚РѕРє РІС‹СЃРѕС‚С‹ СѓС…РѕРґРёС‚ РІ `Spacer(weight(1f))` РїРѕРґ РѕСЃС‚СЂРѕРІ. РљР°СЂС‚РѕС‡РєР° С‚СЂРµРєР° Рё РѕСЃС‚СЂРѕРІ СѓРїСЂР°РІР»РµРЅРёСЏ РёРґСѓС‚ РїР»РѕС‚РЅРѕ, Р±РµР· РїСѓСЃС‚РѕРіРѕ Р·Р°Р·РѕСЂР° РјРµР¶РґСѓ РЅРёРјРё.
- РўРµРјС‹: РІ `AppSettings` РґРѕР±Р°РІР»РµРЅС‹ `themeMode` (LIGHT/DARK/SYSTEM, РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ SYSTEM) Рё `customAccentColor` (Int?, null РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ); РІ `SettingsRepository` вЂ” `setThemeMode`/`setCustomAccentColor`, С…СЂР°РЅРµРЅРёРµ РІ SharedPreferences (`theme_mode`, `custom_accent_color`).
- `FacetTheme`: darkTheme РІС‹Р±РёСЂР°РµС‚СЃСЏ РїРѕ `ThemeMode` (isSystemInDarkTheme С‚РѕР»СЊРєРѕ РґР»СЏ SYSTEM); РїСЂРёРѕСЂРёС‚РµС‚ С†РІРµС‚Р° вЂ” СЃРѕР±СЃС‚РІРµРЅРЅС‹Р№ Р°РєС†РµРЅС‚ (РІСЃРµ РІРµСЂСЃРёРё Android) в†’ Dynamic Color (12+) в†’ СЂСѓС‡РЅР°СЏ РїР°Р»РёС‚СЂР° (<12) в†’ fallback.
- РќР°СЃС‚СЂРѕР№РєРё: РІ В«РћС„РѕСЂРјР»РµРЅРёРёВ» вЂ” `SingleChoiceSegmentedButtonRow` В«РЎРІРµС‚Р»Р°СЏ/РўС‘РјРЅР°СЏ/РЎРёСЃС‚РµРјР°В» Рё РєСЂСѓР¶РѕРє В«РЎРІРѕР№ С†РІРµС‚В», РѕС‚РєСЂС‹РІР°СЋС‰РёР№ РґРёР°Р»РѕРі СЃ С‚СЂРµРјСЏ RGB-СЃР»Р°Р№РґРµСЂР°РјРё, Р¶РёРІС‹Рј РїСЂРµРІСЊСЋ + HEX, РєРЅРѕРїРєР°РјРё В«РџСЂРёРјРµРЅРёС‚СЊВ»/В«РћС‚РјРµРЅР°В»/В«РЎР±СЂРѕСЃРёС‚СЊВ». РЎРѕР±СЃС‚РІРµРЅРЅС‹Р№ С†РІРµС‚ СЂР°Р±РѕС‚Р°РµС‚ РЅР° РІСЃРµС… РІРµСЂСЃРёСЏС… Android Рё РїРµСЂРµРєСЂС‹РІР°РµС‚ СЃРёСЃС‚РµРјРЅС‹Рµ С†РІРµС‚Р°.
- РџР°Р»РёС‚СЂР° РїР»РµРµСЂР°: РїСЂРё Р·Р°РґР°РЅРЅРѕРј `customAccentColor` РїР°Р»РёС‚СЂР° РѕР±Р»РѕР¶РєРё СЃРјРµС€РёРІР°РµС‚СЃСЏ СЃ РЅРёРј РЅР° 40% (`blendPaletteWithAccent`), РїРѕР»СѓРїСЂРѕР·СЂР°С‡РЅС‹Рµ СЂРѕР»Рё СЃРѕС…СЂР°РЅСЏСЋС‚ Р°Р»СЊС„Сѓ вЂ” РіСЂР°РґРёРµРЅС‚С‹ Рё РїРѕРґСЃРІРµС‚РєРё РЅРµ РєРѕРЅС„Р»РёРєС‚СѓСЋС‚ СЃ С‚РµРјРѕР№.
- РўРµСЃС‚С‹: 20 suites / 129 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 517 791 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РђСЂС…РёС‚РµРєС‚СѓСЂРЅС‹Р№ С„РёРєСЃ РїРµСЂРµС…РѕРґР° РІ РїР»РµРµСЂ: РґРѕРє-РѕРІРµСЂР»РµР№ + РІРµСЂС‚РёРєР°Р»СЊРЅС‹Р№ СЃР»Р°Р№Рґ (РѕС‚ Claude) вЂ” СЃРµСЃСЃРёСЏ 29
- РџРµСЂРІРѕРїСЂРёС‡РёРЅР° В«25-РіРѕ РєР°РґСЂР°В» (Р° РЅРµ Р°РЅРёРјР°С†РёСЏ): РґРѕРє Р¶РёР» РІ СЃР»РѕС‚Рµ `bottomBar` РєРѕСЂРЅРµРІРѕРіРѕ `Scaffold`, РїРѕСЌС‚РѕРјСѓ РїСЂРё РІС…РѕРґРµ/РІС‹С…РѕРґРµ РёР· РїР»РµРµСЂР° `showBottomBar` РїРµСЂРµРєР»СЋС‡Р°Р»СЃСЏ, `innerPadding` РїСЂС‹РіР°Р», Рё `NavHost` РїРµСЂРµРёР·РјРµСЂСЏР» РѕР±Р° СЌРєСЂР°РЅР° РІ РѕРґРёРЅ РєР°РґСЂ вЂ” РѕС‚СЃСЋРґР° В«СЂР°Р·РІР°Р»В» (РєРЅРѕРїРєРё Рё С‚РµРєСЃС‚ РёСЃС‡РµР·Р°Р»Рё, С„РѕРЅ/РѕР±Р»РѕР¶РєР° РґС‘СЂРіР°Р»РёСЃСЊ). РџРѕСЌС‚РѕРјСѓ РЅРё `ExitTransition.None` (СЃРµСЃСЃРёСЏ 27), РЅРё fade (СЃРµСЃСЃРёСЏ 28) РєР°РґСЂ РЅРµ СѓР±РёСЂР°Р»Рё: fade Рє С‚РѕРјСѓ Р¶Рµ РґРѕР±Р°РІР»СЏР» РґР¶Р°РЅРє, РѕР±РѕСЂР°С‡РёРІР°СЏ РІРµСЃСЊ С‚СЏР¶С‘Р»С‹Р№ СЃР°Р±С‚СЂРё РІ offscreen alpha-СЃР»РѕР№.
- `AppNavGraph`: РєРѕСЂРЅРµРІРѕР№ `Scaffold` Р·Р°РјРµРЅС‘РЅ РЅР° `Box`. `NavHost` С‚РµРїРµСЂСЊ РІСЃРµРіРґР° РІРѕ РІРµСЃСЊ СЌРєСЂР°РЅ (`fillMaxSize`) Рё РќР• РјРµРЅСЏРµС‚ СЂР°Р·РјРµСЂ РїСЂРё РїРѕРєР°Р·Рµ/СЃРєСЂС‹С‚РёРё РґРѕРєР°. Р”РѕРє РІС‹РЅРµСЃРµРЅ РІ РѕРІРµСЂР»РµР№ (`AnimatedVisibility`, slide СЃРЅРёР·Сѓ + fade, РІС‹СЂР°РІРЅРёРІР°РЅРёРµ РїРѕ РЅРёР·Сѓ). Р’С‹СЃРѕС‚Р° РґРѕРєР° РјРµСЂСЏРµС‚СЃСЏ `onSizeChanged` Рё СЂР°Р·РґР°С‘С‚СЃСЏ РІРєР»Р°РґРєР°Рј С‡РµСЂРµР· `LocalBottomDockHeight` (РЅРѕРІС‹Р№ `navigation/LocalBottomDock.kt`).
- РџРµСЂРµС…РѕРґ РїР»РµРµСЂР°: РІРјРµСЃС‚Рѕ fade вЂ” РІРµСЂС‚РёРєР°Р»СЊРЅС‹Р№ slide (`slideInVertically`/`slideOutVertically`, 300 РјСЃ, `FastOutSlowInEasing`) вЂ” РґРµС€С‘РІР°СЏ С‚СЂР°РЅСЃР»СЏС†РёСЏ Р±РµР· offscreen-СЃР»РѕСЏ. РќРёР¶РЅРёР№ СЌРєСЂР°РЅ РґРµСЂР¶РёС‚СЃСЏ РЅР° РјРµСЃС‚Рµ Рё РІРёРґРёРјС‹Рј (`fadeIn/fadeOut(alpha = 1f)` РІ `tabEnter/tabExitTransition` РґР»СЏ РјР°СЂС€СЂСѓС‚Р° Player) вЂ” РїР»РµРµСЂ РЅР°РµР·Р¶Р°РµС‚ В«С€С‚РѕСЂРєРѕР№В», Р·Р° РЅРёРј РЅРµ РјРµР»СЊРєР°РµС‚ РїСѓСЃС‚РѕР№ С„РѕРЅ.
- `PlayerPalette`: РґРѕР±Р°РІР»РµРЅ `prewarmPlayerPalette(context, url)` вЂ” РїСЂРѕРіСЂРµРІ РїР°Р»РёС‚СЂС‹ РѕР±Р»РѕР¶РєРё РІ РєСЌС€ РїСЂРё С‚Р°РїРµ РїРѕ С‚СЂРµРєСѓ (РІС‹Р·РѕРІ РІ `openPlayer`). Рљ РїРµСЂРІРѕРјСѓ РєР°РґСЂСѓ PlayerScreen С†РІРµС‚ СѓР¶Рµ РІ РєСЌС€Рµ, `rememberPlayerDynamicColors` Р±РµСЂС‘С‚ РµРіРѕ СЃРёРЅС…СЂРѕРЅРЅРѕ вЂ” Р±РµР· `isLoading` Рё Р±РµР· 400 РјСЃ Р°РЅРёРјР°С†РёРё С„РѕРЅР° РїСЂСЏРјРѕ РІРѕ РІСЂРµРјСЏ РїРµСЂРµС…РѕРґР°. Р­РєСЃС‚СЂР°РєС†РёСЏ РІС‹РЅРµСЃРµРЅР° РІ РѕР±С‰РёР№ `computePlayerColors` Р±РµР· РґСѓР±Р»РёСЂРѕРІР°РЅРёСЏ Р»РѕРіРёРєРё.
- РРЅСЃРµС‚С‹: РєРѕСЂРЅРµРІРѕРіРѕ Scaffold Р±РѕР»СЊС€Рµ РЅРµС‚, РїРѕСЌС‚РѕРјСѓ СЃС‚Р°С‚СѓСЃ-Р±Р°СЂ СЃС‚Р°РІСЏС‚ СЃР°РјРё СЌРєСЂР°РЅС‹ вЂ” Home/Wave С‡РµСЂРµР· `statusBarsPadding`/`WindowInsets.statusBars`, Search/Library С‡РµСЂРµР· `contentWindowInsets = WindowInsets.statusBars`, Settings С‡РµСЂРµР· `systemBarsPadding`. РќРёР¶РЅРёР№ РѕС‚СЃС‚СѓРї РІРєР»Р°РґРѕРє = `LocalBottomDockHeight` (РІС‹СЃРѕС‚Р° РїР»Р°РІР°СЋС‰РµРіРѕ РґРѕРєР°, СѓР¶Рµ РІРєР»СЋС‡Р°РµС‚ `navigationBarsPadding`).
- Р—Р°С‚СЂРѕРЅСѓС‚Рѕ: `AppNavGraph.kt`, `LocalBottomDock.kt` (РЅРѕРІС‹Р№), `PlayerPalette.kt`, `HomeScreen.kt`, `WaveScreen.kt`, `SearchScreen.kt`, `LibraryScreen.kt`, `SettingsScreen.kt`.
- РЎР±РѕСЂРєР°: `assembleDebug` вЂ” BUILD SUCCESSFUL; `testDebugUnitTest` вЂ” BUILD SUCCESSFUL (РїРѕРґ JDK 17 РёР· Eclipse Adoptium; JAVA_HOME Р·Р°РґР°РІР°Р»СЃСЏ С‚РѕР»СЊРєРѕ РґР»СЏ РєРѕРјР°РЅРґС‹, С‚.Рє. AGP С‚СЂРµР±СѓРµС‚ Java 17). Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ вЂ” РЅР° РїРѕР»СЊР·РѕРІР°С‚РµР»Рµ.

## Р¤РµР№Рґ РІРјРµСЃС‚Рѕ СЃР»Р°Р№РґР° Рё СѓР±СЂР°РЅ blur С„РѕРЅР° вЂ” СЃРµСЃСЃРёСЏ 28
- РџСЂРѕР±Р»РµРјР°: С„РёРєСЃ СЃРµСЃСЃРёРё 27 РґР°Р» РЅР°Р»РѕР¶РµРЅРёРµ СЌРєСЂР°РЅРѕРІ вЂ” РїРѕР»СѓРїСЂРѕР·СЂР°С‡РЅС‹Р№ РІРµСЂС… РіСЂР°РґРёРµРЅС‚Р° С„РѕРЅР° РїР»РµРµСЂР° РїСЂРѕСЃРІРµС‡РёРІР°Р» РіР»Р°РІРЅСѓСЋ РїСЂРё РїРµСЂРµС…РѕРґР°С…; РїР»СЋСЃ РїСЂРё РѕС‚РєСЂС‹С‚РёРё РїР»РµРµСЂР° Р±С‹Р»Рё РїСЂРѕСЃР°РґРєРё FPS РѕС‚ `Modifier.blur`.
- `AppNavGraph`: РґР»СЏ РјР°СЂС€СЂСѓС‚Р° Player СѓР±СЂР°РЅС‹ None-РїРµСЂРµС…РѕРґС‹ Рё slide вЂ” С‚РµРїРµСЂСЊ Р±С‹СЃС‚СЂС‹Р№ РєСЂРѕСЃСЃ-С„РµР№Рґ `fadeIn`/`fadeOut(tween(150))` РІ РѕР±Рµ СЃС‚РѕСЂРѕРЅС‹: РїР»РµРµСЂ СЃ РЅРµРїСЂРѕР·СЂР°С‡РЅС‹Рј С„РѕРЅРѕРј РїРµСЂРµРєСЂС‹РІР°РµС‚ РіР»Р°РІРЅСѓСЋ, Р° РЅРµ СЃР»РёРїР°РµС‚СЃСЏ СЃ РЅРµР№. `tabEnterTransition`/`tabExitTransition` РїСЂРё РїРѕР»РЅРѕСЌРєСЂР°РЅРЅРѕРј РјР°СЂС€СЂСѓС‚Рµ С‚РѕР¶Рµ РІРѕР·РІСЂР°С‰РµРЅС‹ Рє fade (150 РјСЃ) РІРјРµСЃС‚Рѕ None. РЎР»Р°Р№Рґ РјРµР¶РґСѓ РІРєР»Р°РґРєР°РјРё (300 РјСЃ) Рё `launchSingleTop` Сѓ `openPlayer` СЃРѕС…СЂР°РЅРµРЅС‹.
- `PlayerScreen`: РїРѕР»РЅРѕСЃС‚СЊСЋ СѓР±СЂР°РЅ `Modifier.blur` вЂ” Рё СЃ РїРѕР»РЅРѕСЌРєСЂР°РЅРЅРѕРіРѕ С„РѕРЅР° СЂРµР¶РёРјР° С‚РµРєСЃС‚Р° (44dp), Рё СЃ hero-РѕР±Р»РѕР¶РєРё (26dp Г— recede). Р¤РѕРЅ С‚РµРєСЃС‚РѕРІ С‚РµРїРµСЂСЊ РіСЂР°РґРёРµРЅС‚ `primary 45% в†’ primaryContainer 22% в†’ surface` СЃ РїСЂРµР¶РЅРёРј scrim 0.65. Recede-Р°РЅРёРјР°С†РёСЏ РѕР±Р»РѕР¶РєРё СЃРѕС…СЂР°РЅРµРЅР° (СЃРјРµС‰РµРЅРёРµ, РїРѕРІРѕСЂРѕС‚, РјР°СЃС€С‚Р°Р±, Р°Р»СЊС„Р°) вЂ” С‚РѕР»СЊРєРѕ Р±РµР· СЂР°Р·РјС‹С‚РёСЏ.
- РќРµРїСЂРѕР·СЂР°С‡РЅРѕСЃС‚СЊ: С‚РѕРЅРёСЂРѕРІРєР° С„РѕРЅР° РєРѕРјРїРѕР·РёС‚РёС‚СЃСЏ РїРѕРІРµСЂС… surface (`compositeOver`), Р° РіСЂР°РґРёРµРЅС‚ СЂРёСЃСѓРµС‚СЃСЏ РЅР° СЃР°РјРѕРј Scaffold (РІРєР»СЋС‡Р°СЏ РїРѕР»РѕСЃСѓ РїРѕРґ РїСЂРѕР·СЂР°С‡РЅРѕР№ TopAppBar) вЂ” РєРѕСЂРЅРµРІРѕР№ С„РѕРЅ РїР»РµРµСЂР° РїРѕР»РЅРѕСЃС‚СЊСЋ РЅРµРїСЂРѕР·СЂР°С‡РЅС‹Р№, РіР»Р°РІРЅР°СЏ Р±РѕР»СЊС€Рµ РЅРµ РїСЂРѕСЃРІРµС‡РёРІР°РµС‚.
- РўРµСЃС‚С‹: 20 suites / 129 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 621 184 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РђС‚РѕРјР°СЂРЅС‹Р№ РІС‹С…РѕРґ РёР· РїР»РµРµСЂР° вЂ” СЃРµСЃСЃРёСЏ 27
- Р‘Р°Рі В«СЂР°Р·РІР°Р»РёРІР°СЋС‰РµРіРѕСЃСЏВ» РїР»РµРµСЂР° (25-Р№ РєР°РґСЂ): РїСЂРё pop РёР· PlayerScreen РІРєР»Р°РґРєР° РїРѕРґ РЅРёРј РІСЉРµР·Р¶Р°Р»Р° СЃ fade (180/120 РјСЃ), Р° РґРѕРє РїРѕСЏРІР»СЏР»СЃСЏ РјРіРЅРѕРІРµРЅРЅРѕ Рё РЅР°РєСЂС‹РІР°Р» РЅРёР· СЌРєСЂР°РЅР° вЂ” РЅР° РїСЂРѕРјРµР¶СѓС‚РѕС‡РЅС‹С… РєР°РґСЂР°С… РІРёСЃРµР» В«РїРѕР»СѓРїР»РµРµСЂВ» Р±РµР· РєРѕРЅС‚СЂРѕР»РѕРІ Рё СЃР»Р°Р№РґРµСЂР°. РџРѕР»СЊР·РѕРІР°С‚РµР»СЊ Р·Р°С„РёРєСЃРёСЂРѕРІР°Р» СЃРєСЂРёРЅС€РѕС‚.
- `AppNavGraph`: `tabEnterTransition`/`tabExitTransition` РїСЂРё `direction == 0` (РїРѕР»РЅРѕСЌРєСЂР°РЅРЅС‹Р№ РјР°СЂС€СЂСѓС‚ СЃ Р»СЋР±РѕР№ СЃС‚РѕСЂРѕРЅС‹) РѕС‚РґР°СЋС‚ `EnterTransition.None`/`ExitTransition.None` РІРјРµСЃС‚Рѕ fade вЂ” pop РёР· РїР»РµРµСЂР° С‚РµРїРµСЂСЊ Р°С‚РѕРјР°СЂРЅС‹Р№: РїР»РµРµСЂ РёСЃС‡РµР·Р°РµС‚ Рё Home СЃ РґРѕРєРѕРј РїРѕСЏРІР»СЏСЋС‚СЃСЏ РІ РѕРґРЅРѕРј РєР°РґСЂРµ, Р±РµР· РїСЂРѕРјРµР¶СѓС‚РѕС‡РЅС‹С… СЃРѕСЃС‚РѕСЏРЅРёР№. РЎР»Р°Р№Рґ РјРµР¶РґСѓ РІРєР»Р°РґРєР°РјРё (300 РјСЃ) СЃРѕС…СЂР°РЅС‘РЅ, РІС…РѕРґ РїР»РµРµСЂР° (slide СЃРЅРёР·Сѓ + fade) РЅРµ С‚СЂРѕРЅСѓС‚.
- `openPlayer`: РґРѕР±Р°РІР»РµРЅ `launchSingleTop = true` вЂ” Р·Р°С‰РёС‚Р° РѕС‚ РґСѓР±Р»РµР№ Player РІ СЃС‚РµРєРµ, РіРґРµ РїРѕРІС‚РѕСЂРЅС‹Р№ pop РїСЂРѕРёРіСЂС‹РІР°Р» Р±С‹ slide-РІС…РѕРґ РїР»РµРµСЂР° РїРѕРІРµСЂС… СЃР°РјРѕРіРѕ СЃРµР±СЏ.
- `PlayerScreen`: РїСЂРѕРІРµСЂРµРЅС‹ РІСЃРµ РІРЅСѓС‚СЂРµРЅРЅРёРµ Р°РЅРёРјР°С†РёРё вЂ” exit-Р°РЅРёРјР°С†РёР№, СЂРµР°РіРёСЂСѓСЋС‰РёС… РЅР° Р·Р°РєСЂС‹С‚РёРµ СЌРєСЂР°РЅР°, РЅРµС‚: `AnimatedContent` РіРµСЂРѕСЏ СЂР°Р±РѕС‚Р°РµС‚ С‚РѕР»СЊРєРѕ РїРѕ `isLyricsMode`, `Crossfade` вЂ” РїРѕ СЃРѕСЃС‚РѕСЏРЅРёСЋ Р·Р°РіСЂСѓР·РєРё, `animate*AsState` вЂ” РїРѕ РєРѕРЅС‚РµРЅС‚РЅС‹Рј СЃРѕСЃС‚РѕСЏРЅРёСЏРј. РЁС‚РѕСЂРєР° РѕС‡РµСЂРµРґРё Р·Р°РєСЂС‹РІР°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РјРіРЅРѕРІРµРЅРЅС‹Рј СѓРґР°Р»РµРЅРёРµРј РёР· РєРѕРјРїРѕР·РёС†РёРё (snap, Р±РµР· hide-Р°РЅРёРјР°С†РёРё) вЂ” Р·Р°С„РёРєСЃРёСЂРѕРІР°РЅРѕ РєРѕРјРјРµРЅС‚Р°СЂРёРµРј, С‡С‚РѕР±С‹ Р°РЅРёРјР°С†РёСЏ СЃРєСЂС‹С‚РёСЏ РЅРµ РІРµСЂРЅСѓР»Р°СЃСЊ.
- РџСЂРѕРІРµСЂРµРЅРѕ РїРѕ Р±Р°Р№С‚РєРѕРґСѓ navigation-compose 2.8.4: `ExitTransition.None` Сѓ РїР»РµРµСЂР° РїСЂРёРјРµРЅСЏРµС‚СЃСЏ С‡РµСЃС‚РЅРѕ (РЅРµС‚ РґРµС„РѕР»С‚РЅРѕРіРѕ fadeOut РїРѕРІРµСЂС…), Р° pop-РїРµСЂРµС…РѕРґ РїСЂРё РїРѕР»РЅРѕСЌРєСЂР°РЅРЅРѕРј РјР°СЂС€СЂСѓС‚Рµ СЃРѕР±РёСЂР°РµС‚СЃСЏ РёР· popEnter/popExit.
- РўРµСЃС‚С‹: 20 suites / 129 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 621 184 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РџР»Р°РІРЅС‹Рµ РїРµСЂРµС…РѕРґС‹ РІРєР»Р°РґРѕРє Рё С„РёРєСЃС‹ РґРѕРєР° вЂ” СЃРµСЃСЃРёСЏ 26
- `AppNavGraph`: РїРµСЂРµС…РѕРґС‹ РјРµР¶РґСѓ РІРєР»Р°РґРєР°РјРё вЂ” РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅС‹Р№ СЃР»Р°Р№Рґ РїРѕ РёРЅРґРµРєСЃСѓ РІРєР»Р°РґРєРё (РІС‹РµР·Рґ СЃ РЅСѓР¶РЅРѕР№ СЃС‚РѕСЂРѕРЅС‹ РЅР° С‚СЂРµС‚СЊ С€РёСЂРёРЅС‹, 300 РјСЃ, `FastOutSlowInEasing`) + fade; РЅР°РїСЂР°РІР»РµРЅРёРµ РІС‹С‡РёСЃР»СЏРµС‚СЃСЏ С‡РµСЂРµР· `tabIndex()` РїРѕ `bottomNavItems`. Р”Р»СЏ РїРѕР»РЅРѕСЌРєСЂР°РЅРЅС‹С… РјР°СЂС€СЂСѓС‚РѕРІ Рё РІРѕР·РІСЂР°С‚РѕРІ СЃ РЅРёС… вЂ” С‚РѕР»СЊРєРѕ РјСЏРіРєРёР№ fade (РІС…РѕРґ 180 РјСЃ, РІС‹С…РѕРґ 120 РјСЃ) Р±РµР· СЃР»Р°Р№РґР°.
- `PlayerScreen`: `exitTransition` Рё `popExitTransition` Р·Р°РјРµРЅРµРЅС‹ РЅР° `ExitTransition.None` вЂ” С‚СЏР¶С‘Р»С‹Р№ СЌРєСЂР°РЅ (blur, Р±РѕР»СЊС€Р°СЏ РѕР±Р»РѕР¶РєР°) Р±РѕР»СЊС€Рµ РЅРµ Р°РЅРёРјРёСЂСѓРµС‚СЃСЏ РїСЂРё СЃРІРѕСЂР°С‡РёРІР°РЅРёРё, РїСЂРѕСЃР°РґРєР° РєР°РґСЂРѕРІ СѓР±СЂР°РЅР° (РІ СЃРµСЃСЃРёРё 25 РІРѕР·РІСЂР°С‚ РёР· РїР»РµРµСЂР° РґР°РІР°Р» 27.5% janky). Р’С…РѕРґ РѕСЃС‚Р°Р»СЃСЏ СЃР»Р°Р№РґРѕРј СЃРЅРёР·Сѓ + fade.
- `ExpressiveBottomDock`: РєРѕРЅС‚РµРЅС‚ РґРѕРєР° РѕР±С‘СЂРЅСѓС‚ РІ `Box` СЃ `matchParentSize()` Рё `clip(shape)` вЂ” РїРѕР»РѕСЃРєР° РїСЂРѕРіСЂРµСЃСЃР° РјРёРЅРё-РїР»РµРµСЂР° РѕР±СЂРµР·Р°РµС‚СЃСЏ РїРѕ СЃРєСЂСѓРіР»С‘РЅРЅРѕРјСѓ РєРѕРЅС‚СѓСЂСѓ Рё РЅРµ РІС‹РїРёСЂР°РµС‚ Р·Р° РєСЂР°СЏ РґРѕРєР°.
- РўРµСЃС‚С‹: 20 suites / 129 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 621 184 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РџРѕР»РёСЂРѕРІРєР° Р°РЅРёРјР°С†РёР№ РЅРёР¶РЅРµРіРѕ РґРѕРєР° вЂ” СЃРµСЃСЃРёСЏ 25
- `ExpressiveBottomDock`: СЂСЏРґ РЅР°РІРёРіР°С†РёРё Р·Р°РєСЂРµРїР»С‘РЅ Сѓ РЅРёР¶РЅРµР№ РєСЂРѕРјРєРё Рё Р±РѕР»СЊС€Рµ РЅРµ СѓС‡Р°СЃС‚РІСѓРµС‚ РІ Р°РЅРёРјР°С†РёРё РјРёРЅРё-РїР»РµРµСЂР°. Р¤РѕРЅРѕРІР°СЏ РїРѕРІРµСЂС…РЅРѕСЃС‚СЊ `DockBackground` СЂР°СЃС‚С‘С‚ РѕС‚РґРµР»СЊРЅРѕ (64 в†’ 120dp), С‡РёС‚Р°СЏ `State<Float>` С‚РѕР»СЊРєРѕ РІРЅСѓС‚СЂРё СЃРµР±СЏ; РјРёРЅРё-РїР»РµРµСЂ СЂР°СЃРєСЂС‹РІР°РµС‚СЃСЏ С‡РµСЂРµР· `AnimatedVisibility` (`expandVertically` + `fadeIn`, РІС‹С…РѕРґ вЂ” `shrinkVertically` + `fadeOut`). Р’С‹СЃРѕС‚Р° РґРѕРєР° РІРјРµСЃС‚Рµ СЃ РґРµС‚СЊРјРё РЅРµ Р°РЅРёРјРёСЂСѓРµС‚СЃСЏ, РЅР°РІРёРіР°С†РёСЏ РЅРµ СЃРјРµС‰Р°РµС‚СЃСЏ.
- РџСЂСѓР¶РёРЅС‹: РёРЅРґРёРєР°С‚РѕСЂ вЂ” `Spring.DampingRatioNoBouncy` / `StiffnessMediumLow` (РєР°Рє РїСЂРѕСЃРёР» РїРѕР»СЊР·РѕРІР°С‚РµР»СЊ), РёРєРѕРЅРєР° вЂ” NoBouncy / Medium, СЂР°СЃРєСЂС‹С‚РёРµ РґРѕРєР° Рё РјРёРЅРё-РїР»РµРµСЂР° вЂ” NoBouncy / Medium. РЈР±СЂР°РЅС‹ РѕС‚СЃРєРѕРє 0.58 Рё СЂР°СЃСЃРёРЅС…СЂРѕРЅ С„Р°Р·.
- `ExpressiveNavigationBar`: СЃРјРµС‰РµРЅРёРµ РёРЅРґРёРєР°С‚РѕСЂР° С‡РёС‚Р°РµС‚СЃСЏ РІ `graphicsLayer` (С„Р°Р·Р° РѕС‚СЂРёСЃРѕРІРєРё), РїРѕСЌС‚РѕРјСѓ СЂСЏРґ РЅРµ СЂРµРєРѕРјРїРѕР·РёСЂСѓРµС‚СЃСЏ РЅР° РєР°Р¶РґРѕРј РєР°РґСЂРµ; РјР°СЃС€С‚Р°Р± РёРєРѕРЅРєРё Рё Р°Р»СЊС„Р° РїРѕРґРїРёСЃРё вЂ” С‚РѕР¶Рµ `graphicsLayer`. Р“СЂР°РґРёРµРЅС‚ РёРЅРґРёРєР°С‚РѕСЂР° Р·Р°РїРѕРјРёРЅР°РµС‚СЃСЏ, РєРѕР»Р±СЌРєРё РІРєР»Р°РґРѕРє СЃС‚Р°Р±РёР»СЊРЅС‹ (`remember` + `rememberUpdatedState`), РёРЅРґРµРєСЃ вЂ” `remember(items, selectedRoute)`.
- `AppNavGraph`: `selectedRoute` Рё `showBottomBar` РІС‹С‡РёСЃР»СЏСЋС‚СЃСЏ С‡РµСЂРµР· `derivedStateOf`; `onTabSelect` Рё `onDockExpand` СЃС‚Р°Р±РёР»СЊРЅС‹ (`remember(navController)`).
- `MiniPlayer`: РїСЂРѕРіСЂРµСЃСЃ Р±РѕР»СЊС€Рµ РЅРµ РѕРїСЂР°С€РёРІР°РµС‚СЃСЏ РЅР° РїР°СѓР·Рµ (`while (isPlaying)`), РїРµСЂРІС‹Р№ Р·Р°РјРµСЂ РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ СЃСЂР°Р·Сѓ.
- РўРµРЅСЊ РґРѕРєР° РїРµСЂРµРЅРµСЃРµРЅР° СЃ `Modifier.shadow(..., clip = false)` РЅР° `Surface(shadowElevation = 8.dp)` вЂ” РѕРґРёРЅ СЃР»РѕР№ РІРјРµСЃС‚Рѕ РґРІСѓС….
- РўРµСЃС‚С‹: 20 suites / 129 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 515 714 Р±Р°Р№С‚.
- Live SM-A266B: APK СѓСЃС‚Р°РЅРѕРІР»РµРЅ, РїСЂРёР»РѕР¶РµРЅРёРµ Р·Р°РїСѓС‰РµРЅРѕ. РџРѕСЏРІР»РµРЅРёРµ РјРёРЅРё-РїР»РµРµСЂР° С‡РµСЂРµР· В«РњРѕСЋ РІРѕР»РЅСѓВ» (Р±РµР· РїРµСЂРµС…РѕРґР° РІ РїР»РµРµСЂ): 599 РєР°РґСЂРѕРІ, 1.5% janky, 95-Р№ РїРµСЂС†РµРЅС‚РёР»СЊ 14 РјСЃ. РџРµСЂРµРєР»СЋС‡РµРЅРёСЏ РІРєР»Р°РґРѕРє СЃ РѕС‚РєСЂС‹С‚С‹Рј РјРёРЅРё-РїР»РµРµСЂРѕРј: 5.9% janky, 90-Р№ 9 РјСЃ; СЃРµСЂРёСЏ РёР· 5 Р±С‹СЃС‚СЂС‹С… С‚Р°РїРѕРІ: 3.4% janky, 95-Р№ 12 РјСЃ. РљСЂР°С€РµР№ Рё `FATAL EXCEPTION` РЅРµС‚. Р’РѕР·РІСЂР°С‚ РёР· РїРѕР»РЅРѕСЌРєСЂР°РЅРЅРѕРіРѕ РїР»РµРµСЂР° РґР°С‘С‚ 27.5% janky вЂ” СЌС‚Рѕ С‚СЏР¶С‘Р»С‹Р№ slide/fade РІСЃРµРіРѕ `PlayerScreen`, Р° РЅРµ РґРѕРє; РёР·РѕР»РёСЂРѕРІР°РЅРЅС‹Р№ РґРѕРє РґР°С‘С‚ 1.5%.
- РЎРєСЂС‹С‚РёРµ РјРёРЅРё-РїР»РµРµСЂР° РѕС‚РґРµР»СЊРЅРѕ РЅРµ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРѕ: РІ РїСЂРёР»РѕР¶РµРЅРёРё РЅРµС‚ РґРµР№СЃС‚РІРёСЏ В«СЃС‚РѕРї/РѕС‡РёСЃС‚РёС‚СЊВ» (С‚РѕР»СЊРєРѕ РїР°СѓР·Р°), Р° РѕС‡РёСЃС‚РєР° РѕС‡РµСЂРµРґРё РґРѕСЃС‚СѓРїРЅР° Р»РёС€СЊ РЅР° СЌРєСЂР°РЅРµ РїР»РµРµСЂР°, РіРґРµ РґРѕРє СЃРєСЂС‹С‚. Р’С‹С…РѕРґ РёСЃРїРѕР»СЊР·СѓРµС‚ СЃРёРјРјРµС‚СЂРёС‡РЅС‹Р№ `shrinkVertically` + `fadeOut`.

## Р РµРґРёР·Р°Р№РЅ РџР°РєРµС‚ 1 (Home) + РџР°РєРµС‚ 3 (РµРґРёРЅС‹Р№ РЅРёР¶РЅРёР№ РґРѕРє) вЂ” СЃРµСЃСЃРёСЏ 24
- Home РїРѕР»СѓС‡РёР» С‚СЂС‘С…СѓСЂРѕРІРЅРµРІСѓСЋ РёРµСЂР°СЂС…РёСЋ: `HomeSectionHeader` СЃ eyebrow 11sp Bold РІ `primary` (С‚СЂРµРєРёРЅРі 1.4) Рё Р·Р°РіРѕР»РѕРІРєРѕРј 22sp Bold. РўРµРєСЃС‚С‹: В«Р Р•РљРћРњР•РќР”РђР¦РР / Р”Р»СЏ РІР°СЃВ», В«РРЎРўРћР РРЇ / РќРµРґР°РІРЅРµРµВ», В«РђР РҐРР’ / Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏВ», В«РљРћР›Р›Р•РљР¦РРЇ / Р›СЋР±РёРјРѕРµВ». `BlockGap` СѓРІРµР»РёС‡РµРЅ СЃ 26 РґРѕ 32dp.
- Hero В«РџСЂРѕРґРѕР»Р¶РёС‚СЊВ»: СЂР°РґРёСѓСЃ 30dp, РіСЂР°РґРёРµРЅС‚ `primaryContainer` 45% в†’ `surfaceContainerHigh`, РѕР±Р»РѕР¶РєР° 96dp squircle, Р·Р°РіРѕР»РѕРІРѕРє `headlineSmall` (26sp) РјР°РєСЃРёРјСѓРј РІ 2 СЃС‚СЂРѕРєРё, Play 48dp СЃ РёРєРѕРЅРєРѕР№ 24dp. Eyebrow СЃРѕРєСЂР°С‰С‘РЅ РґРѕ В«РџР РћР”РћР›Р–РРўР¬В», РёРЅР°С‡Рµ РѕР±СЂРµР·Р°Р»СЃСЏ РЅР° СѓР·РєРёС… СЌРєСЂР°РЅР°С….
- В«РќРµРґР°РІРЅРµРµВ»: СѓР±СЂР°РЅР° С‡С‘СЂРЅР°СЏ РїР»Р°С€РєР° СЃ Р±РµР»С‹Рј С‚РµРєСЃС‚РѕРј; РѕР±Р»РѕР¶РєР° 132dp СЃ СЂР°РґРёСѓСЃРѕРј 22dp, РїРѕРґРїРёСЃРё РїРѕРґ РѕР±Р»РѕР¶РєРѕР№ 13sp SemiBold / 11sp.
- В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏВ»: СЂР°РґРёСѓСЃ 30dp, РїСЂРµРІСЊСЋ 3 С‚СЂРµРєР° (`MEMORY_PREVIEW_LIMIT = 3`), СЃРµР·РѕРЅ 26sp, СЃС‡С‘С‚С‡РёРє `displaySmall` 40sp, РІРµСЂС‚РёРєР°Р»СЊРЅС‹Рµ РѕС‚СЃС‚СѓРїС‹ РјРµР¶РґСѓ Р±Р»РѕРєР°РјРё 32dp. `LikedCard` РїСЂРёРІРµРґС‘РЅ Рє С‚РѕРјСѓ Р¶Рµ СЃС‚РёР»СЋ: СЂР°РґРёСѓСЃ 22dp, РІРЅРµС€РЅРёР№ Р·Р°РіРѕР»РѕРІРѕРє, РІРЅСѓС‚СЂРµРЅРЅСЏСЏ РїРѕРґРїРёСЃСЊ СѓР±СЂР°РЅР°.
- РќРѕРІС‹Р№ `ExpressiveBottomDock.kt`: РѕРґРёРЅ РѕР±СЉРµРєС‚ СЃ РѕС‚СЃС‚СѓРїР°РјРё 16dp, СЂР°РґРёСѓСЃРѕРј 30dp, С‚РµРЅСЊСЋ 8dp, С„РѕРЅРѕРј `surfaceContainerHigh` 96% Рё Р±РѕСЂРґРµСЂРѕРј 1dp `onSurface` 6%. Р’РЅСѓС‚СЂРё вЂ” mini-player Рё СЂСЏРґ РЅР°РІРёРіР°С†РёРё; РїРѕСЏРІР»РµРЅРёРµ С‚СЂРµРєР° Р°РЅРёРјРёСЂСѓРµС‚СЃСЏ СЂРѕСЃС‚РѕРј РЅР° 56dp (РїСЂСѓР¶РёРЅР° 0.8 / MediumLow), РЅР°РІРёРіР°С†РёСЏ РѕСЃС‚Р°С‘С‚СЃСЏ РЅР° РјРµСЃС‚Рµ.
- `MiniPlayer.kt` РїРµСЂРµРїРёСЃР°РЅ РЅР° `internal MiniPlayerRow` РІС‹СЃРѕС‚РѕР№ 56dp: РѕР±Р»РѕР¶РєР° 44dp (СЂР°РґРёСѓСЃ 14dp), РЅР°Р·РІР°РЅРёРµ 14sp SemiBold, Р°СЂС‚РёСЃС‚ 11sp, Play 40dp СЃ Р·РѕРЅРѕР№ РєР°СЃР°РЅРёСЏ 48dp, РїСЂРѕРіСЂРµСЃСЃ 2dp РІРЅСѓС‚СЂРё РєР»РёРїР°, СЂР°Р·РґРµР»РёС‚РµР»СЊ 1dp СЃ inset 16dp. РЎРѕР±СЃС‚РІРµРЅРЅРѕР№ РїР°РЅРµР»Рё Рё С‚РµРЅРё Р±РѕР»СЊС€Рµ РЅРµС‚.
- `ExpressiveNavigationBar.kt` СЃС‚Р°Р» СЂСЏРґРѕРј 64dp Р±РµР· С„РѕРЅР° Рё С‚РµРЅРё: РїСЂСѓР¶РёРЅРЅС‹Р№ РёРЅРґРёРєР°С‚РѕСЂ damping 0.58 / `StiffnessMedium`, РёРєРѕРЅРєР° РІС‹Р±СЂР°РЅРЅРѕРіРѕ 1.18, РїРѕРґРїРёСЃСЊ 11sp СЃ `lineHeight = 16.sp` РґР»СЏ С‚РѕС‡РЅРѕР№ СѓРєР»Р°РґРєРё РІ 64dp.
- `AppNavGraph.kt` РїРѕРґРєР»СЋС‡Р°РµС‚ `ExpressiveBottomDock` РІРјРµСЃС‚Рѕ РїР°СЂС‹ `MiniPlayer` + `ExpressiveNavigationBar`. `PlayerScreen`, `LibraryScreen`, `SettingsScreen` РЅРµ С‚СЂРѕРЅСѓС‚С‹.
- РўРµСЃС‚С‹: 20 suites / 129 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped (UI-С‚РµСЃС‚РѕРІ РЅРµС‚, РёР·РјРµРЅРµРЅРёСЏ РїСЂРѕРІРµСЂРµРЅС‹ СЃР±РѕСЂРєРѕР№). `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 616 075 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## SoundCloud progressive + СѓРјРЅС‹Р№ fallback YouTube вЂ” СЃРµСЃСЃРёСЏ 23
- `SoundCloudRepositoryImpl.downloadCapability` РїРµСЂРµРІРµРґС‘РЅ СЃ `HLS_ONLY` РЅР° `PROGRESSIVE`. `SoundCloudDownloadSource` СѓР¶Рµ РІС‹Р·С‹РІР°РµС‚ `resolveStreamUrl(requireProgressive = true)`, РїРѕСЌС‚РѕРјСѓ SC-С‚СЂРµРє СЃ progressive MP3 РєР°С‡Р°РµС‚СЃСЏ РЅР°РїСЂСЏРјСѓСЋ, Р±РµР· РѕР±СЂР°С‰РµРЅРёСЏ Рє YouTube. Р•СЃР»Рё Сѓ С‚СЂРµРєР° С‚РѕР»СЊРєРѕ HLS, resolve РІРѕР·РІСЂР°С‰Р°РµС‚ РѕС€РёР±РєСѓ, Рё СЂРµР·РѕР»РІРµСЂ РїРµСЂРµРєР»СЋС‡Р°РµС‚СЃСЏ РЅР° YouTube.
- `DownloadSourceResolver`: РїРѕСЃР»Рµ СЃС‚СЂРѕРіРѕРіРѕ РјР°С‚С‡Р° РґР»СЏ YouTube РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ РЅРµСЃС‚СЂРѕРіРёР№ РїРѕРёСЃРє. Р—Р°РїСЂРѕСЃ `РђСЂС‚РёСЃС‚ РќР°Р·РІР°РЅРёРµ` РѕС‡РёС‰Р°РµС‚СЃСЏ РѕС‚ `official audio`, `remaster`, `feat.` Рё РґСЂСѓРіРёС… СЃР»СѓР¶РµР±РЅС‹С… РїРѕРјРµС‚РѕРє; Р±РµСЂСѓС‚СЃСЏ С‚РѕРї-10 СЂРµР·СѓР»СЊС‚Р°С‚РѕРІ; РєР°РЅРґРёРґР°С‚ РїСЂРёРЅРёРјР°РµС‚СЃСЏ, РµСЃР»Рё `abs(durationMs - seed.durationMs) <= 15_000` (РїСЂРё РЅРµРёР·РІРµСЃС‚РЅРѕР№ РґР»РёС‚РµР»СЊРЅРѕСЃС‚Рё seed С„РёР»СЊС‚СЂ РЅРµ РїСЂРёРјРµРЅСЏРµС‚СЃСЏ, РєР°РЅРґРёРґР°С‚ Р±РµР· РґР»РёС‚РµР»СЊРЅРѕСЃС‚Рё РѕС‚Р±СЂР°СЃС‹РІР°РµС‚СЃСЏ) Рё РёРјСЏ Р°СЂС‚РёСЃС‚Р° РІСЃС‚СЂРµС‡Р°РµС‚СЃСЏ РІ РЅР°Р·РІР°РЅРёРё РІРёРґРµРѕ РёР»Рё РёРјРµРЅРё РєР°РЅР°Р»Р° Р±РµР· СѓС‡С‘С‚Р° СЂРµРіРёСЃС‚СЂР°. Р РµР·РѕР»РІРёС‚СЃСЏ РїРµСЂРІС‹Р№ РїРѕРґС…РѕРґСЏС‰РёР№ РєР°РЅРґРёРґР°С‚.
- Р•СЃР»Рё РѕС‡РёС‰РµРЅРЅС‹Р№ Р·Р°РїСЂРѕСЃ СЃРѕРІРїР°РґР°РµС‚ СЃРѕ СЃС‚СЂРѕРіРёРј, РІС‚РѕСЂРѕР№ СЃРµС‚РµРІРѕР№ Р·Р°РїСЂРѕСЃ РЅРµ РґРµР»Р°РµС‚СЃСЏ вЂ” РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ СѓР¶Рµ РїРѕР»СѓС‡РµРЅРЅР°СЏ РІС‹РґР°С‡Р°. `RecentlyFailedSources.markFailed` РІС‹Р·С‹РІР°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РїРѕСЃР»Рµ РЅРµСѓРґР°С‡Рё Рё СЃС‚СЂРѕРіРѕРіРѕ, Рё РЅРµСЃС‚СЂРѕРіРѕРіРѕ РјР°С‚С‡РµР№.
- Р•РґРёРЅС‹Р№ С‚РµРєСЃС‚ РѕС€РёР±РєРё: `DownloadMessages.SOURCE_NOT_FOUND` = В«РќРµ СѓРґР°Р»РѕСЃСЊ РЅР°Р№С‚Рё СЌС‚РѕС‚ С‚СЂРµРє РЅР° YouTube РґР»СЏ СЃРєР°С‡РёРІР°РЅРёСЏ. Р’РѕР·РјРѕР¶РЅРѕ, РѕРЅ СѓРґР°Р»РµРЅ РёР»Рё РґРѕСЃС‚СѓРїРµРЅ С‚РѕР»СЊРєРѕ РІ SoundCloudВ». РћРЅ Р¶Рµ РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РєР°Рє РґРµС„РѕР»С‚ `DownloadSourceUnavailableException` Рё РІ Snackbar SoundCloud РІ `PlayerScreen`; РґРµР№СЃС‚РІРёРµ В«РќР°Р№С‚РёВ» СЃРѕС…СЂР°РЅРµРЅРѕ РґР»СЏ СЂСѓС‡РЅРѕРіРѕ РїРѕРёСЃРєР°.
- РўРµСЃС‚С‹: `DownloadSourceResolverTest` вЂ” 16 РєРµР№СЃРѕРІ (SC progressive Р±РµР· YouTube, SC HLS-only в†’ YouTube, СЃС‚СЂРѕРіРёРµ РјР°С‚С‡Рё, РЅРµСЃС‚СЂРѕРіРёР№ РјР°С‚С‡ СЃ СЂР°Р·РЅРёС†РµР№ 5 СЃ, РѕС‚РєР°Р· РїСЂРё СЂР°Р·РЅРёС†Рµ >15 СЃ, Р°СЂС‚РёСЃС‚ РІ РЅР°Р·РІР°РЅРёРё/РєР°РЅР°Р»Рµ, HLS-СЂРµР·СѓР»СЊС‚Р°С‚, TTL, С‚РѕС‡РЅС‹Р№ С‚РµРєСЃС‚ РѕС€РёР±РєРё); РЅРѕРІС‹Р№ `SoundCloudRepositoryCapabilityTest` вЂ” 1 РєРµР№СЃ. Р’СЃРµРіРѕ 20 suites / 129 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 508 441 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## Р¤РёРєСЃС‹ СЃРєР°С‡РёРІР°РЅРёСЏ: СЃРІРµР¶РёР№ СЂРµР·РѕР»РІ, Р°С‚РѕРјР°СЂРЅР°СЏ Р·Р°РїРёСЃСЊ, РѕС‚РјРµРЅР° вЂ” СЃРµСЃСЃРёСЏ 22
- `DownloadManager` Р±РѕР»СЊС€Рµ РЅРµ СЂРµР·РѕР»РІРёС‚ РёСЃС‚РѕС‡РЅРёРє РґРѕ РїРѕСЃС‚Р°РЅРѕРІРєРё Р·Р°РґР°С‡Рё: РІ WorkData РїРµСЂРµРґР°СЋС‚СЃСЏ С‚РѕР»СЊРєРѕ РёРґРµРЅС‚РёС‡РЅРѕСЃС‚СЊ С‚СЂРµРєР° Рё СЃСЃС‹Р»РєРё РґР»СЏ СЂРµР·РѕР»РІР°; РєР»СЋС‡Рё `KEY_DIRECT_STREAM_URL`, `KEY_DIRECT_MIME_TYPE`, `KEY_RESOLVED_SOURCE` СѓРґР°Р»РµРЅС‹. РћС€РёР±РєРё РёСЃС‚РѕС‡РЅРёРєР° С‚РµРїРµСЂСЊ РїСЂРёС…РѕРґСЏС‚ С‡РµСЂРµР· FAILED WorkInfo.
- `DownloadTrackWorker` СЃС‚Р°Р» С‚РѕРЅРєРёРј: РїРѕР»СѓС‡Р°РµС‚ `DownloadSourceResolver` С‡РµСЂРµР· `DownloadWorkerEntryPoint` Рё РґРµР»РµРіРёСЂСѓРµС‚ СЃРєР°С‡РёРІР°РЅРёРµ РІ РЅРѕРІС‹Р№ `DownloadEngine`.
- `DownloadEngine`: РїРµСЂРµРґ РєР°Р¶РґРѕР№ РїРѕРїС‹С‚РєРѕР№ Р·Р°РЅРѕРІРѕ СЂРµР·РѕР»РІРёС‚ URL; РїСЂРё HTTP 403/410 РґРµР»Р°РµС‚ СЂРѕРІРЅРѕ РѕРґРёРЅ РїРѕРІС‚РѕСЂРЅС‹Р№ СЂРµР·РѕР»РІ Рё РѕРґРЅСѓ РїРѕРІС‚РѕСЂРЅСѓСЋ Р·Р°РіСЂСѓР·РєСѓ, Р·Р°С‚РµРј РІРѕР·РІСЂР°С‰Р°РµС‚ `HttpStatusException` СЃ РєРѕРґРѕРј. HLS, РїСѓСЃС‚РѕР№ С„Р°Р№Р» Рё РЅРµРґРѕСЃС‚СѓРїРЅС‹Р№ РєР°С‚Р°Р»РѕРі РґР°СЋС‚ РїРѕРЅСЏС‚РЅСѓСЋ `DownloadException`.
- Р—Р°РїРёСЃСЊ: `.part` СЃ СѓРЅРёРєР°Р»СЊРЅС‹Рј `workId` в†’ РїСЂРѕРІРµСЂРєР° `Content-Length` Рё РЅРµРЅСѓР»РµРІРѕРіРѕ СЂР°Р·РјРµСЂР° в†’ `Files.move(REPLACE_EXISTING, ATOMIC_MOVE)` РїРѕРІРµСЂС… С†РµР»РµРІРѕРіРѕ С„Р°Р№Р»Р°. Room РѕР±РЅРѕРІР»СЏРµС‚СЃСЏ С‚РѕР»СЊРєРѕ РїРѕСЃР»Рµ rename; РїСЂРё Р»СЋР±РѕР№ РѕС€РёР±РєРµ СѓРґР°Р»СЏРµС‚СЃСЏ С‚РѕР»СЊРєРѕ `.part`, СЃС‚Р°СЂС‹Р№ СЂР°Р±РѕС‡РёР№ С„Р°Р№Р» РЅРµ С‚СЂРѕРіР°РµС‚СЃСЏ. РџСЂРё РѕС€РёР±РєРµ СЃРѕС…СЂР°РЅРµРЅРёСЏ РІ Room РіРѕС‚РѕРІС‹Р№ С„Р°Р№Р» РЅРµ СѓРґР°Р»СЏРµС‚СЃСЏ.
- РћС‚РјРµРЅР°: `DownloadEngine.downloadToFile` РІРµС€Р°РµС‚ `Job.invokeOnCompletion` в†’ `Call.cancel()`; `NewPipeDownloader.withCancellation` С‡РµСЂРµР· ThreadLocal РѕС‚РјРµРЅСЏРµС‚ Р°РєС‚РёРІРЅС‹Рµ NewPipe `Call`. Р’СЃРµ РІС‹Р·РѕРІС‹ `SearchInfo`/`StreamInfo`/`KioskInfo.getInfo` РІ SoundCloud, YouTube Рё С‡Р°СЂС‚Р°С… РѕР±С‘СЂРЅСѓС‚С‹ РІ РЅРµРіРѕ; РѕС‚РјРµРЅР° РЅРµ Р»РѕРіРёСЂСѓРµС‚СЃСЏ РєР°Рє СЃРµС‚РµРІР°СЏ РѕС€РёР±РєР°.
- `NetworkModule`: `HttpLoggingInterceptor` (BASIC) РїРѕРґРєР»СЋС‡Р°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РїСЂРё `BuildConfig.DEBUG`; РІ release С‚РѕРєРµРЅС‹ Рё РїРѕРґРїРёСЃР°РЅРЅС‹Рµ URL РЅРµ РїРѕРїР°РґР°СЋС‚ РІ Р»РѕРіРё.
- РўРµСЃС‚С‹: РЅРѕРІС‹Р№ `DownloadEngineTest` вЂ” 4 РєРµР№СЃР°: 403 в†’ РїРѕРІС‚РѕСЂРЅС‹Р№ СЂРµР·РѕР»РІ Рё СѓСЃРїРµС…; РїРѕРІС‚РѕСЂРЅС‹Р№ 403 в†’ failure РїРѕСЃР»Рµ РѕРґРЅРѕР№ РїРѕРїС‹С‚РєРё; HTTP 500 в†’ СЃС‚Р°СЂС‹Р№ С„Р°Р№Р» С†РµР», Room РЅРµ РѕР±РЅРѕРІР»С‘РЅ, `.part` СѓРґР°Р»С‘РЅ; СѓСЃРїРµС€РЅР°СЏ Р°С‚РѕРјР°СЂРЅР°СЏ Р·Р°РјРµРЅР° СЃСѓС‰РµСЃС‚РІСѓСЋС‰РµРіРѕ С„Р°Р№Р»Р°. 19 suites / 121 С‚РµСЃС‚, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 503 796 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РџРѕР»РЅС‹Р№ РѕС‚РєР°С‚ DSP Рё РІРёР·СѓР°Р»РёР·Р°С‚РѕСЂР° вЂ” СЃРµСЃСЃРёСЏ 21
- РЈРґР°Р»РµРЅС‹ `AudioDsp.kt`, `AudioEnergy.kt`, `AudioDspTest.kt` Рё `PlaybackAudioSession.kt`; РёР· `MusicService` СѓР±СЂР°РЅР° РїСѓР±Р»РёРєР°С†РёСЏ Р°СѓРґРёРѕСЃРµСЃСЃРёРё.
- `ExpressivePlayerSlider`: РІРѕР»РЅР° Phase B СЃ РїРѕСЃС‚РѕСЏРЅРЅРѕР№ Р°РјРїР»РёС‚СѓРґРѕР№ 9dp, РґР»РёРЅР° 44dp, СЃСѓР¶РµРЅРёРµ Рє РєСЂР°СЏРј, РєСЂСѓРіР»С‹Р№ Р±РµРіСѓРЅРѕРє РЅР° РіСЂРµР±РЅРµ. РђРјРїР»РёС‚СѓРґР° Р±РѕР»СЊС€Рµ РЅРµ Р·Р°РІРёСЃРёС‚ РѕС‚ Р·РІСѓРєР°; РЅР° РїР°СѓР·Рµ РІРѕР»РЅР° РїР»Р°РІРЅРѕ РІС‹РїСЂСЏРјР»СЏРµС‚СЃСЏ Р·Р° 500 РјСЃ, С„Р°Р·Р° Р·Р°РјРёСЂР°РµС‚. РћР±С‘СЂС‚РєР° Material3 Slider СЃ drag-cancel, С‚Р°РїР°РјРё Рё СЃРІР°Р№РїР°РјРё СЃРѕС…СЂР°РЅРµРЅР°.
- `PlayerScreen`: СѓРґР°Р»РµРЅС‹ Р·Р°РїСЂРѕСЃ RECORD_AUDIO, `AudioPermissionGate`, `rememberAudioEnergy` Рё РїР°СЂР°РјРµС‚СЂС‹ `canVisualize`/`visualizerDelayMs`.
- `SettingsRepository`/`AppSettings`, `SettingsRepositoryImpl`, `SettingsViewModel`, `SettingsScreen`: СѓРґР°Р»РµРЅР° РЅР°СЃС‚СЂРѕР№РєР° Р·Р°РґРµСЂР¶РєРё РІРёР·СѓР°Р»РёР·Р°С†РёРё Рё СЃРµРєС†РёСЏ В«Р’РёР·СѓР°Р»РёР·Р°С†РёСЏВ».
- `AndroidManifest.xml`: СѓРґР°Р»РµРЅРѕ СЂР°Р·СЂРµС€РµРЅРёРµ RECORD_AUDIO.
- РўРµСЃС‚С‹: `AudioDspTest` СѓРґР°Р»С‘РЅ; 18 suites / 117 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 406 945 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РљР°Р»РёР±СЂРѕРІРєР° Р·Р°РґРµСЂР¶РєРё РІРѕР»РЅС‹ вЂ” СЃРµСЃСЃРёСЏ 20
- `AppSettings.visualizerDelayMs` (РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ 0, РґРёР°РїР°Р·РѕРЅ -200..+200) С…СЂР°РЅРёС‚СЃСЏ РІ SharedPreferences `facet_settings` РїРѕРґ РєР»СЋС‡РѕРј `visualizer_delay_ms`; РїСЂРё Р·Р°РіСЂСѓР·РєРµ Р·РЅР°С‡РµРЅРёРµ РѕРіСЂР°РЅРёС‡РёРІР°РµС‚СЃСЏ РґРёР°РїР°Р·РѕРЅРѕРј, `SettingsRepository.setVisualizerDelayMs` РїРёС€РµС‚ РµРіРѕ РІ IO-РґРёСЃРїРµС‚С‡РµСЂРµ.
- `SettingsScreen`: РЅРѕРІР°СЏ СЃРµРєС†РёСЏ В«Р’РР—РЈРђР›РР—РђР¦РРЇВ» СЃРѕ СЃР»Р°Р№РґРµСЂРѕРј В«Р—Р°РґРµСЂР¶РєР° РІРёР·СѓР°Р»РёР·Р°С†РёРёВ» -200..+200 РјСЃ, Р¶РёРІРѕР№ РїРѕРґРїРёСЃСЊСЋ Рё Р»РѕРєР°Р»СЊРЅС‹Рј draft вЂ” РІ РЅР°СЃС‚СЂРѕР№РєРё Р·РЅР°С‡РµРЅРёРµ РїРёС€РµС‚СЃСЏ РїРѕ РѕС‚РїСѓСЃРєР°РЅРёСЋ РїРѕР»Р·СѓРЅРєР°.
- `AudioDsp.EnergyDelayBuffer`: РєРѕР»СЊС†РµРІРѕР№ Р±СѓС„РµСЂ РЅР° 500 РјСЃ (64 СЃР»РѕС‚Р°) СЃ РјРѕРЅРѕС‚РѕРЅРЅС‹РјРё С‚Р°Р№РјСЃС‚Р°РјРїР°РјРё; `push` РІС‹С‚РµСЃРЅСЏРµС‚ СЃС‚Р°СЂС‹Рµ РєР°РґСЂС‹, `valueAt(now, delayMs)` РѕС‚РґР°С‘С‚ СЃР°РјС‹Р№ СЃРІРµР¶РёР№ РєР°РґСЂ РЅРµ РїРѕР·Р¶Рµ `now - delay`; РїСѓСЃС‚РѕР№ Р±СѓС„РµСЂ Рё Р·Р°РґРµСЂР¶РєР° Р±РѕР»СЊС€Рµ РѕРєРЅР° вЂ” 0; РѕС‚СЂРёС†Р°С‚РµР»СЊРЅР°СЏ Р·Р°РґРµСЂР¶РєР° С‡РёС‚Р°РµС‚ СЃР°РјС‹Р№ СЃРІРµР¶РёР№ РєР°РґСЂ.
- `AudioEnergy`: РєР°Р¶РґС‹Р№ FFT-РєР°РґСЂ РїРёС€РµС‚СЃСЏ РІ Р±СѓС„РµСЂ СЃ `SystemClock.elapsedRealtimeNanos`; РїСЂРё РѕС‚СЂРёСЃРѕРІРєРµ С‡РёС‚Р°РµС‚СЃСЏ `sampler.energyAt(now, visualizerDelayMs)`. Р•СЃР»Рё FFT РЅРµ РїСЂРёС…РѕРґРёР» РґРѕР»СЊС€Рµ 120 РјСЃ вЂ” 0. Р‘СѓС„РµСЂ РѕС‡РёС‰Р°РµС‚СЃСЏ РІ `start()`/`release()` (РїР°СѓР·Р°, СЃРјРµРЅР° С‚СЂРµРєР°, РїРѕС‚РµСЂСЏ СЂР°Р·СЂРµС€РµРЅРёСЏ, lifecycle).
- `PlayerScreen`: `visualizerDelayMs` РёР· `appSettings` РїСЂРѕРєРёРЅСѓС‚ РІ `PlayerProgressSection` в†’ `rememberAudioEnergy`; `ExpressivePlayerSlider` РїРѕР»СѓС‡Р°РµС‚ СѓР¶Рµ Р·Р°РґРµСЂР¶Р°РЅРЅРѕРµ `State<Float>`, РµРіРѕ РІРёР·СѓР°Р»СЊРЅР°СЏ С„РѕСЂРјСѓР»Р° РЅРµ РјРµРЅСЏР»Р°СЃСЊ.
- РўРµСЃС‚С‹: 6 РЅРѕРІС‹С… РІ `AudioDspTest` вЂ” С‡С‚РµРЅРёРµ Р·РЅР°С‡РµРЅРёСЏ 100 РјСЃ РЅР°Р·Р°Рґ, РІС‹С‚РµСЃРЅРµРЅРёРµ Р·Р° РѕРєРЅРѕ 500 РјСЃ, СЃРѕС…СЂР°РЅРµРЅРёРµ РєР°РґСЂРѕРІ РІРЅСѓС‚СЂРё РѕРєРЅР°, РїСѓСЃС‚РѕР№ Р±СѓС„РµСЂ/reset, РѕС‚СЂРёС†Р°С‚РµР»СЊРЅР°СЏ Р·Р°РґРµСЂР¶РєР° Рё СЃР°РЅРёС‚РёР·Р°С†РёСЏ. 19 suites / 144 С‚РµСЃС‚Р°, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 406 941 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РђРґР°РїС‚РёРІРЅС‹Р№ РїРѕСЂРѕРі Рё РјСѓР·С‹РєР°Р»СЊРЅС‹Рµ РїРѕР»РѕСЃС‹ вЂ” СЃРµСЃСЃРёСЏ 19
- `AudioDsp`: РїРѕР»РѕСЃС‹ РїРµСЂРµРґРµР»Р°РЅС‹ РїРѕРґ СЂРёС‚Рј вЂ” Kick 30вЂ“120 Р“С† (Р±РѕС‡РєР°/Р±Р°СЃ), Snare_Vocal 150вЂ“600 Р“С† (СЃРЅРµР№СЂ/РєР»СЌРї/С‚РµР»Рѕ РІРѕРєР°Р»Р°), Presence 1500вЂ“6000 Р“С† (С‰РµР»С‡РѕРє СЃРЅРµР№СЂР° Рё СЃРѕРіР»Р°СЃРЅС‹Рµ). РџСЂРѕРјРµР¶СѓС‚РєРё 120вЂ“150 Рё 600вЂ“1500 Р“С† РЅРµ СѓС‡РёС‚С‹РІР°СЋС‚СЃСЏ.
- Р’РµСЃР° spectral flux: 1.2 / 1.5 / 1.0 вЂ” РЅРµР±РѕР»СЊС€РѕР№ Р°РєС†РµРЅС‚ РЅР° СЃРЅРµР№СЂРµ Рё РІРѕРєР°Р»Рµ.
- `TransientDetector` С…СЂР°РЅРёС‚ СЃРєРѕР»СЊР·СЏС‰РµРµ СЃСЂРµРґРЅРµРµ СЌРЅРµСЂРіРёРё Р·Р° 12 РєР°РґСЂРѕРІ (~600 РјСЃ РїСЂРё 20 Р“С† FFT). РЈРґР°СЂРѕРј СЃС‡РёС‚Р°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ СЂРѕСЃС‚, РіРґРµ С‚РµРєСѓС‰Р°СЏ СЌРЅРµСЂРіРёСЏ РІС‹С€Рµ СЃСЂРµРґРЅРµРіРѕ РІ 1.5 СЂР°Р·Р° Рё РІС‹С€Рµ РЅРёР¶РЅРµРіРѕ РїРѕСЂРѕРіР° 0.04; СЃР»Р°Р±С‹Р№ С„РѕРЅ Рё РјРµР»РєРёР№ С€СѓРј РІРѕР»РЅСѓ РЅРµ РґРІРёРіР°СЋС‚.
- `AudioEnvelope`: Р°С‚Р°РєР° 8 РјСЃ, СЃРїР°Рґ 200 РјСЃ вЂ” РІРѕР»РЅР° СЂРµР·РєРѕ РїРѕРґРїСЂС‹РіРёРІР°РµС‚ РЅР° СѓРґР°СЂ Рё РїР»Р°РІРЅРѕ РѕРїСѓСЃРєР°РµС‚СЃСЏ, Р±РµР· РјРµР»РєРѕР№ РІРёР±СЂР°С†РёРё.
- Р’РёР·СѓР°Р»СЊРЅР°СЏ С„РѕСЂРјСѓР»Р° `ExpressivePlayerSlider` РЅРµ РјРµРЅСЏР»Р°СЃСЊ: `0.55 + 1.30 * sqrt(live)`, РѕРіСЂР°РЅРёС‡РµРЅРёРµ 1.65. РЎРёР»СЊРЅС‹Р№ СѓРґР°СЂ РґР°С‘С‚ ~1.5x Р°РјРїР»РёС‚СѓРґС‹, presence РґРѕР±Р°РІР»СЏРµС‚ РјСЏРіРєРёР№ Р°РєС†РµРЅС‚.
- РўРµСЃС‚С‹: 21 РІ `AudioDspTest` вЂ” РЅРѕРІС‹Рµ РіСЂР°РЅРёС†С‹ РїРѕР»РѕСЃ, РІРµСЃР° flux, РёРіРЅРѕСЂРёСЂРѕРІР°РЅРёРµ СЃР»Р°Р±РѕРіРѕ С€СѓРјР°, РѕС‚СЃРµС‡РµРЅРёРµ СЃР»Р°Р±РѕРіРѕ СЂРѕСЃС‚Р° Р°РґР°РїС‚РёРІРЅС‹Рј РїРѕСЂРѕРіРѕРј, СЃРёР»СЊРЅС‹Р№ СѓРґР°СЂ Рё РїР»Р°РІРЅРѕРµ Р·Р°С‚СѓС…Р°РЅРёРµ. 19 suites / 138 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 502 039 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РўСЂР°РЅР·РёРµРЅС‚С‹ РІРјРµСЃС‚Рѕ РѕР±С‰РµР№ РіСЂРѕРјРєРѕСЃС‚Рё вЂ” СЃРµСЃСЃРёСЏ 18
- `AudioDsp`: РІРјРµСЃС‚Рѕ Full Spectrum RMS вЂ” С‚СЂРё РїРѕР»РѕСЃС‹: Low 40вЂ“150 Р“С† (Р±РѕС‡РєР°/Р±Р°СЃ), Mid 150вЂ“1000 Р“С† (СЃРЅРµР№СЂ/РєР»СЌРї/РІРѕРєР°Р»), High 3000вЂ“10000 Р“С† (С…СЌС‚С‹/С‚Р°СЂРµР»РєРё). Р”Р»СЏ РєР°Р¶РґРѕР№ СЃС‡РёС‚Р°РµС‚СЃСЏ СЃСЂРµРґРЅРёР№ RMS РєРѕРјРїР»РµРєСЃРЅС‹С… Р±РёРЅРѕРІ; 150 Р“С† РѕС‚РЅРµСЃРµРЅРѕ Рє Low.
- `spectralFlux` СЃСѓРјРјРёСЂСѓРµС‚ С‚РѕР»СЊРєРѕ РїРѕР»РѕР¶РёС‚РµР»СЊРЅС‹Рµ РїСЂРёСЂРѕСЃС‚С‹ РїРѕР»РѕСЃ СЃ РІРµСЃР°РјРё 1.0/1.3/2.0 (РєРѕРјРїРµРЅСЃР°С†РёСЏ С‡РёСЃР»Р° Р±РёРЅРѕРІ Рё СЃРїР°РґР° РІС‹СЃРѕРєРёС… С‡Р°СЃС‚РѕС‚), СЂРµР·СѓР»СЊС‚Р°С‚ РѕРіСЂР°РЅРёС‡РµРЅ 0..1. РџР°РґРµРЅРёРµ Рё РЅРµРёР·РјРµРЅРЅР°СЏ СЌРЅРµСЂРіРёСЏ РІРєР»Р°РґР° РЅРµ РґР°СЋС‚ вЂ” Р±РµР· РЅРѕРІРѕРіРѕ СѓРґР°СЂР° Р·РЅР°С‡РµРЅРёРµ СЃСЂР°Р·Сѓ РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ Рє РЅСѓР»СЋ.
- `TransientDetector` С…СЂР°РЅРёС‚ СЌРЅРµСЂРіРёСЋ РїСЂРµРґС‹РґСѓС‰РµРіРѕ FFT-РєР°РґСЂР°; РїРµСЂРІС‹Р№ РєР°РґСЂ Р·Р°РґР°С‘С‚ Р±Р°Р·Сѓ, `reset()` РѕС‡РёС‰Р°РµС‚ РёСЃС‚РѕСЂРёСЋ РїСЂРё СЃРјРµРЅРµ С‚СЂРµРєР°, СЃРµСЃСЃРёРё Рё РѕСЃРІРѕР±РѕР¶РґРµРЅРёРё Visualizer.
- `AudioEnvelope`: Р°С‚Р°РєР° 8 РјСЃ, СЃРїР°Рґ 100 РјСЃ вЂ” РІРѕР»РЅР° СЂРµР·РєРѕ РїРѕРґРїСЂС‹РіРёРІР°РµС‚ РЅР° СѓРґР°СЂ Рё Р±С‹СЃС‚СЂРѕ РѕРїР°РґР°РµС‚. `AudioSample` Р¶РёРІС‘С‚ 120 РјСЃ (Р·Р°РїР°СЃ РЅР° РґРІР° РїСЂРѕРїСѓС‰РµРЅРЅС‹С… FFT-РєР°РґСЂР° РїСЂРё 20 Р“С†).
- `AudioEnergy`: `AudioSampler` РёСЃРїРѕР»СЊР·СѓРµС‚ `TransientDetector`, СЃР±СЂРѕСЃ РґРµС‚РµРєС‚РѕСЂР° РІ `start()`/`release()`; Р»РѕРіРёРєР° Visualizer, СЂР°Р·СЂРµС€РµРЅРёСЏ, lifecycle Рё ambient-С„РѕР»Р±СЌРєР° РЅРµ РјРµРЅСЏР»Р°СЃСЊ.
- `ExpressivePlayerSlider`: Р°РјРїР»РёС‚СѓРґР° `9dp * waveHeight * (0.55 + 1.30 * sqrt(live))` СЃ РѕРіСЂР°РЅРёС‡РµРЅРёРµРј 1.65; РєРѕСЂРЅРµРІР°СЏ РєСЂРёРІР°СЏ РїРѕРґРЅРёРјР°РµС‚ С‚РёС…РёРµ С…СЌС‚С‹, РЅР° РїР°СѓР·Рµ `waveHeight` Р·Р° 500 РјСЃ СѓС…РѕРґРёС‚ РІ 0.
- РўРµСЃС‚С‹: 18 РІ `AudioDspTest`, РІРєР»СЋС‡Р°СЏ СЂР°Р·Р±РёРµРЅРёРµ РїРѕ РїРѕР»РѕСЃР°Рј, РіСЂР°РЅРёС†С‹ 40/150/1000/3000/10000 Р“С†, РІРµСЃР° flux, РґРµС‚РµРєС‚РѕСЂ В«СѓРґР°СЂ в†’ С‚РёС€РёРЅР°В» Рё СЃС†РµРЅР°СЂРёР№ В«РїРёРє в†’ Р±С‹СЃС‚СЂРѕРµ Р·Р°С‚СѓС…Р°РЅРёРµВ». 19 suites / 135 С‚РµСЃС‚РѕРІ, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 502 039 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.

## РџРѕР»РЅС‹Р№ СЃРїРµРєС‚СЂ РІРјРµСЃС‚Рѕ Р±Р°СЃР° РІ РїСѓР»СЊСЃР°С†РёРё РІРѕР»РЅС‹ вЂ” СЃРµСЃСЃРёСЏ 17
- `BassDsp.kt` в†’ `AudioDsp.kt`, `BassEnergy.kt` в†’ `AudioEnergy.kt`, `BassDspTest.kt` в†’ `AudioDspTest.kt`; СЃС‚Р°СЂС‹Рµ РёРјРµРЅР° СѓРґР°Р»РµРЅС‹.
- `audioRms`: СЃСЂРµРґРЅРёР№ RMS РєРѕРјРїР»РµРєСЃРЅС‹С… signed-Р±РёРЅРѕРІ РІ РїРѕР»РѕСЃРµ 40 Р“С†вЂ“16 РєР“С† (РєРѕСЂРµРЅСЊ РёР· СЃСѓРјРјС‹ РєРІР°РґСЂР°С‚РѕРІ, РґРµР»С‘РЅРЅС‹Р№ РЅР° С‡РёСЃР»Рѕ Р±РёРЅРѕРІ), DC Рё Nyquist РёСЃРєР»СЋС‡РµРЅС‹, РЅРѕСЂРјР°Р»РёР·Р°С†РёСЏ РЅР° 128 Рё clamp 0..1. РўРµРїРµСЂСЊ РІРєР»Р°Рґ РґР°СЋС‚ СЃРёРЅС‚С‹ Рё С…СЌС‚С‹, Р° РЅРµ С‚РѕР»СЊРєРѕ РЅРёР·С‹.
- `AudioEnvelope` СЃРѕС…СЂР°РЅРёР» Р°С‚Р°РєСѓ 15 РјСЃ Рё СЃРїР°Рґ 140 РјСЃ вЂ” РІРѕР»РЅР° РґС‹С€РёС‚ РїР»Р°РІРЅРѕ, Р±РµР· СЃСѓРґРѕСЂРѕР¶РЅС‹С… СЃРєР°С‡РєРѕРІ.
- `rememberAudioEnergy`/`AudioSampler` Р·Р°РјРµРЅРёР»Рё bass-РІРµСЂСЃРёРё; С‚РµРі Р»РѕРіРѕРІ `AudioEnergy`, Р»РѕРіРёРєР° Visualizer, ambient-С„РѕР»Р±СЌРєР°, СЂР°Р·СЂРµС€РµРЅРёСЏ Рё Р¶РёР·РЅРµРЅРЅРѕРіРѕ С†РёРєР»Р° РЅРµ РјРµРЅСЏР»Р°СЃСЊ.
- `ExpressivePlayerSlider`: РїР°СЂР°РјРµС‚СЂ `energy` в†’ `audioEnergy`; Р°РјРїР»РёС‚СѓРґР° `9dp * waveHeight * (0.65 + 1.20 * sqrt(live))` СЃ РѕРіСЂР°РЅРёС‡РµРЅРёРµРј 1.65. РљРѕСЂРЅРµРІР°СЏ РєСЂРёРІР°СЏ РєРѕРјРїРµРЅСЃРёСЂСѓРµС‚ С‚Рѕ, С‡С‚Рѕ СЃСЂРµРґРЅРёР№ RMS С€РёСЂРѕРєРѕР№ РїРѕР»РѕСЃС‹ РЅРёР¶Рµ СѓР·РєРѕРїРѕР»РѕСЃРЅРѕРіРѕ Р±Р°СЃРѕРІРѕРіРѕ; РЅР° РїР°СѓР·Рµ `waveHeight` РїРѕ-РїСЂРµР¶РЅРµРјСѓ Р·Р° 500 РјСЃ СѓС…РѕРґРёС‚ РІ 0.
- РўРµСЃС‚С‹: `AudioDspTest` РїСЂРѕРІРµСЂСЏРµС‚ РІС‹СЃРѕРєРѕС‡Р°СЃС‚РѕС‚РЅС‹Рµ Р±РёРЅС‹ (5 Рё 10 РєР“С†), СЂР°РІРµРЅСЃС‚РІРѕ РІРєР»Р°РґР° РЅРёР·РѕРІ Рё РІРµСЂС…РѕРІ, РіСЂР°РЅРёС†С‹ 40 Р“С†/16 РєР“С†, СЃСЂРµРґРЅРµРµ РїРѕ 371 Р±РёРЅСѓ, РЅР°СЃС‹С‰РµРЅРёРµ, DC/Nyquist Рё envelope. 19 suites / 134 С‚РµСЃС‚Р°, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, APK 27 502 061 Р±Р°Р№С‚. Р–РёРІРѕР№ С‚РµСЃС‚ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РЅРµ РІС‹РїРѕР»РЅСЏР»СЃСЏ РїРѕ РїСЂРѕСЃСЊР±Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ вЂ” РїСЂРѕРІРµСЂРєР° РЅР° С‚РµР»РµС„РѕРЅРµ Р·Р° РЅРёРј.

## Р’РѕР·РІСЂР°С‚ РІРѕР»РЅС‹ Phase B РІРЅСѓС‚СЂСЊ Material3 Slider вЂ” СЃРµСЃСЃРёСЏ 16
- РџРѕР»СЊР·РѕРІР°С‚РµР»СЊ РѕС‚РєР»РѕРЅРёР» РїСЂСЏРјСѓСЋ Р»РёРЅРёСЋ СЃРµСЃСЃРёРё 15: РІРёР·СѓР°Р» С‚Р°Р№РјР»Р°Р№РЅР° СЃРЅРѕРІР° РІРѕР»РЅР° Phase B, РЅРѕ С‚РµС…РЅРёС‡РµСЃРєР°СЏ РѕР±С‘СЂС‚РєР° Material3 `Slider` СЃРѕС…СЂР°РЅРµРЅР° Р±РµР· РёР·РјРµРЅРµРЅРёР№.
- `ExpressivePlayerSlider`: Canvas РІ СЃР»РѕС‚Рµ `track` РІС‹СЃРѕС‚РѕР№ 38dp СЂРёСЃСѓРµС‚ СЃРёРЅСѓСЃРѕРёРґСѓ СЃ РґР»РёРЅРѕР№ РІРѕР»РЅС‹ 44dp Рё СЃСѓР¶РµРЅРёРµРј `sin(PI*u)` Рє РєСЂР°СЏРј, stroke 3dp СЃ РєСЂСѓРіР»С‹РјРё РєРѕРЅС†Р°РјРё. РќРµРїСЂРѕРїРµС‚Р°РЅРЅР°СЏ С‡Р°СЃС‚СЊ вЂ” СЃРµСЂР°СЏ РІРѕР»РЅР°, Р±СѓС„РµСЂ вЂ” С‚Р° Р¶Рµ РІРѕР»РЅР° РїР»РѕС‚РЅРµРµ, РїСЂРѕРїРµС‚Р°РЅРЅР°СЏ вЂ” РІРѕР»РЅР° РІ Р°РєС†РµРЅС‚Рµ; РєР»РёРїС‹ РїРѕ `startX`/`progressX`/`bufferX` СѓС‡РёС‚С‹РІР°СЋС‚ RTL С‡РµСЂРµР· `trackX`.
- Thumb Material3 Р·Р°РјРµРЅС‘РЅ РїСЂРѕР·СЂР°С‡РЅС‹Рј `Spacer(20.dp)` вЂ” РѕРЅ РґРµСЂР¶РёС‚ РѕС‚СЃС‚СѓРї РґРѕСЂРѕР¶РєРё Рё Р·РѕРЅСѓ РєР°СЃР°РЅРёСЏ; РІРёРґРёРјС‹Р№ РєСЂСѓРіР»С‹Р№ Р±РµРіСѓРЅРѕРє 5dp СЂРёСЃСѓРµС‚СЃСЏ РІ `track` РЅР° РіСЂРµР±РЅРµ РІРѕР»РЅС‹ РїРѕ С‚РѕР№ Р¶Рµ С„РѕСЂРјСѓР»Рµ СЃРёРЅСѓСЃР°. РџСЂРё Р·Р°С…РІР°С‚Рµ СЂР°Р±РѕС‚Р°РµС‚ РїСЂСѓР¶РёРЅР° `spring(0.35f, StiffnessLow)` Рё РѕСЂРµРѕР», РєР°Рє РІ Phase B.
- Р‘Р°СЃРѕРІС‹Р№ РёРјРїСѓР»СЊСЃ (`BassDsp`, 40вЂ“180 Р“С†, envelope) РјРµРЅСЏРµС‚ С‚РѕР»СЊРєРѕ Р°РјРїР»РёС‚СѓРґСѓ: `9.dp * waveHeight * (0.72f + 0.88f * live)`, С„РѕСЂРјР° СЃРёРЅСѓСЃРѕРёРґС‹ РЅРµ РёСЃРєР°Р¶Р°РµС‚СЃСЏ. РќР° РїР°СѓР·Рµ `waveHeight` Р·Р° 500 РјСЃ СѓС…РѕРґРёС‚ РІ 0 (РІРѕР»РЅР° РІС‹РїСЂСЏРјР»СЏРµС‚СЃСЏ), С„Р°Р·Р° `rememberPlaybackPhase` Р·Р°РјРёСЂР°РµС‚ РЅР° С‚РµРєСѓС‰РµРј Р·РЅР°С‡РµРЅРёРё.
- `PlayerScreen`: РїРѕР·РёС†РёСЏ Рё Р±СѓС„РµСЂ РїРѕ-РїСЂРµР¶РЅРµРјСѓ СЃР±СЂР°СЃС‹РІР°СЋС‚СЃСЏ РЅР° СЃРјРµРЅСѓ `trackId` вЂ” РјРѕСЂС„ `LinearProgressIndicator` в†” `Slider` СЂР°Р±РѕС‚Р°РµС‚.
- РџСЂРѕРІРµСЂРєР°: `gradlew testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, 19 suites / 133 С‚РµСЃС‚Р°, 0 failures/errors/skipped; APK 27 502 061 Р±Р°Р№С‚.
- Live SM-A266B: РІРѕР»РЅР° РїР»Р°РІРЅР°СЏ Рё СЂР°РІРЅРѕРјРµСЂРЅР°СЏ, Р±РµРіСѓРЅРѕРє РєСЂСѓРіР»С‹Р№ РЅР° РіСЂРµР±РЅРµ, Р°РјРїР»РёС‚СѓРґР° РґС‹С€РёС‚ 22.5в†’37 px РїСЂРё Р±Р°СЃРµ Рё 5 px РЅР° РїР°СѓР·Рµ, drag СЃР»РµРґСѓРµС‚ Р·Р° РїР°Р»СЊС†РµРј СЃ РѕСЂРµРѕР»РѕРј, tap-РїРµСЂРµРјРѕС‚РєР° СЂР°Р±РѕС‚Р°РµС‚, `BassEnergy: Visualizer: СЃРµСЃСЃРёСЏ 4001/4033`.

## РћС‚РєР°С‚ РІРёР·СѓР°Р»Р° С‚Р°Р№РјР»Р°Р№РЅР° Рє Phase B вЂ” СЃРµСЃСЃРёСЏ 15
- РџРѕР»СЊР·РѕРІР°С‚РµР»СЊ РѕС‚РєР»РѕРЅРёР» РІРѕР»РЅРѕРІРѕР№ Canvas СЃРµСЃСЃРёРё 14: СЃРёРЅСѓСЃРѕРёРґР° РЅР° РІСЃСЋ С€РёСЂРёРЅСѓ РґРѕСЂРѕР¶РєРё Рё РІРµСЂС‚РёРєР°Р»СЊРЅС‹Р№ thumb-В«РїР°Р»РєР°В» РІРµСЂРЅСѓР»РёСЃСЊ Рє СЃРїРѕРєРѕР№РЅРѕРјСѓ РІРёРґСѓ Phase B.
- `ExpressivePlayerSlider` СЃРѕС…СЂР°РЅРёР» РѕР±С‘СЂС‚РєСѓ Material3 1.3.1 `Slider` (interactionSource, semantics, РєР»Р°РІРёР°С‚СѓСЂР°, RTL, drag-cancel, РјРёРЅРёРјР°Р»СЊРЅР°СЏ Р·РѕРЅР° 48dp) Рё СЃР»РѕС‚ `track`: РЅРµРїСЂРѕРїРµС‚Р°РЅРЅР°СЏ С‡Р°СЃС‚СЊ вЂ” СЃРµСЂР°СЏ Р»РёРЅРёСЏ 2.5dp, Р±СѓС„РµСЂ вЂ” С‚Р° Р¶Рµ Р»РёРЅРёСЏ РїР»РѕС‚РЅРµРµ, РїСЂРѕРїРµС‚Р°РЅРЅР°СЏ вЂ” СЃРїР»РѕС€РЅР°СЏ Р·Р°Р»РёРІРєР° `primary`. Р¤РѕСЂРјР° Р»РёРЅРёРё Р±РѕР»СЊС€Рµ РЅРµ РёСЃРєР°Р¶Р°РµС‚СЃСЏ.
- Р‘Р°СЃРѕРІС‹Р№ РёРјРїСѓР»СЊСЃ (`BassDsp`, 40вЂ“180 Р“С†, РѕРґРЅР° РІСЂРµРјРµРЅРЅР°СЏ envelope) РїСЂРёРІСЏР·Р°РЅ Рє С‚РѕР»С‰РёРЅРµ 2.5в†’3dp Рё РјСЏРіРєРѕРјСѓ РґРІСѓС…СЃР»РѕР№РЅРѕРјСѓ СЃРІРµС‡РµРЅРёСЋ (alpha 0.10/0.14 РѕС‚ СЌРЅРµСЂРіРёРё). РќР° РїР°СѓР·Рµ `calm`-Р°РЅРёРјР°С†РёСЏ 500 РјСЃ Рё СЃРїР°Рґ envelope РІРѕР·РІСЂР°С‰Р°СЋС‚ Р»РёРЅРёСЋ Рє РїРѕРєРѕСЋ: СЃРІРµС‡РµРЅРёРµ Рё СѓС‚РѕР»С‰РµРЅРёРµ РёСЃС‡РµР·Р°СЋС‚ Р±РµР· СЃРєР°С‡РєР°.
- Thumb вЂ” СЃРѕР±СЃС‚РІРµРЅРЅС‹Р№ РєСЂСѓРіР»С‹Р№ Р±РµРіСѓРЅРѕРє 11dp СЃ РїСЂСѓР¶РёРЅРЅС‹Рј СЂРѕСЃС‚РѕРј 30% Рё РѕСЂРµРѕР»РѕРј РїСЂРё Р·Р°С…РІР°С‚Рµ (РєР°Рє РІ Phase B), РІРјРµСЃС‚Рѕ РІРµСЂС‚РёРєР°Р»СЊРЅРѕРіРѕ handle Material3.
- РњРѕСЂС„ `LinearProgressIndicator` в†” `Slider` РІРѕСЃСЃС‚Р°РЅРѕРІР»РµРЅ: РЅР° СЃРјРµРЅРµ С‚СЂРµРєР° РїРѕР·РёС†РёСЏ Рё Р±СѓС„РµСЂ РѕР±РЅСѓР»СЏСЋС‚СЃСЏ (`remember(trackId) { 0 }`), РёРЅР°С‡Рµ СЃС‚Р°СЂР°СЏ РїРѕР·РёС†РёСЏ РїРµСЂРµРµР·Р¶Р°Р»Р° РІ РЅРѕРІС‹Р№ С‚СЂРµРє Рё `hasStarted` Р·Р°С‰С‘Р»РєРёРІР°Р»СЃСЏ СЃСЂР°Р·Сѓ, РЅРµ РґР°РІР°СЏ РїРѕРєР°Р·Р°С‚СЊ РёРЅРґРёРєР°С‚РѕСЂ.
- `SinePulseBar.kt` РїРµСЂРµРёРјРµРЅРѕРІР°РЅ РІ `BassEnergy.kt` (РѕС‚РґРµР»СЊРЅРѕР№ РїРѕР»РѕСЃС‹ РЅР°Рґ СЃР»Р°Р№РґРµСЂРѕРј Р±РѕР»СЊС€Рµ РЅРµС‚); РґРѕР±Р°РІР»РµРЅС‹ Р»РѕРіРё `BassEnergy` РґР»СЏ РїСЂРѕРІРµСЂРєРё РёСЃС‚РѕС‡РЅРёРєР° СЌРЅРµСЂРіРёРё РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ.
- РџСЂРѕРІРµСЂРєР°: `gradlew testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, 133 JVM-С‚РµСЃС‚Р°, 0 failures/errors/skipped; APK 27 502 042 Р±Р°Р№С‚.
- Live SM-A266B: Р»РёРЅРёСЏ СЂРѕРІРЅР°СЏ, thumb РєСЂСѓРіР»С‹Р№, СЃРІРµС‡РµРЅРёРµ РґС‹С€РёС‚ (СЏСЂРєРѕСЃС‚СЊ РѕСЂРµРѕР»Р° 37в†’66в†’104 РїСЂРё Р±Р°СЃРµ, 0 РЅР° РїР°СѓР·Рµ), drag Рё tap-РїРµСЂРµРјРѕС‚РєР° СЂР°Р±РѕС‚Р°СЋС‚, РёРЅРґРёРєР°С‚РѕСЂ Р·Р°РіСЂСѓР·РєРё РїРѕСЏРІР»СЏРµС‚СЃСЏ РЅР° СЃС‚Р°СЂС‚Рµ С‚СЂРµРєР° Рё РјРѕСЂС„РёС‚ РІ СЃР»Р°Р№РґРµСЂ, `BassEnergy: Visualizer: СЃРµСЃСЃРёСЏ 3961`.

## Р¤РёРЅР°Р»СЊРЅС‹Р№ С„РёРєСЃ С‚Р°Р№РјР»Р°Р№РЅР° Рё FFT вЂ” СЃРµСЃСЃРёСЏ 14
- Р’РјРµСЃС‚Рѕ СЃР°РјРѕРїРёСЃРЅС‹С… Р¶РµСЃС‚РѕРІ РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ Material3 1.3.1 `Slider`: Canvas С‚РѕР»СЊРєРѕ РІ `track`, С€С‚Р°С‚РЅС‹Рµ thumb, semantics, РєР»Р°РІРёР°С‚СѓСЂР°, RTL, disabled Рё РјРёРЅРёРјР°Р»СЊРЅР°СЏ Р·РѕРЅР° 48dp. РћС‚РґРµР»СЊРЅР°СЏ РїРѕР»РѕСЃР° РЅР°Рґ СЃР»Р°Р№РґРµСЂРѕРј СѓРґР°Р»РµРЅР°, Р°РєС‚РёРІРЅР°СЏ РІРѕР»РЅР° СЃС…РѕРґРёС‚СЃСЏ Рє С†РµРЅС‚СЂСѓ thumb; Р±СѓС„РµСЂ Рё РЅРµР°РєС‚РёРІРЅР°СЏ РґРѕСЂРѕР¶РєР° РёСЃРїРѕР»СЊР·СѓСЋС‚ С‚Рµ Р¶Рµ РєРѕРѕСЂРґРёРЅР°С‚С‹.
- РћР±С‰Р°СЏ РјРѕРґРµР»СЊ `PlayerSliderState` РґР»СЏ preview Рё С‚Р°Р№РјРєРѕРґР°. Start/Stop/Cancel Рё finish РїСЂРѕС…РѕРґСЏС‚ С‡РµСЂРµР· РѕРґРёРЅ interactionSource РІ РїРѕСЂСЏРґРєРµ FIFO; Cancel РІРѕР·РІСЂР°С‰Р°РµС‚ С‚РµРєСѓС‰РёР№ playback Р±РµР· seek. `key(trackId)` РїРµСЂРµСЃРѕР·РґР°С‘С‚ Р¶РµСЃС‚, РїСЂРѕРІРµСЂРєР° Р°РєС‚СѓР°Р»СЊРЅРѕРіРѕ trackId РЅРµ РїРѕР·РІРѕР»СЏРµС‚ СЃС‚Р°СЂРѕРјСѓ release РїРµСЂРµРјРѕС‚Р°С‚СЊ РЅРѕРІС‹Р№ С‚СЂРµРє. Accessibility change РЅРµ РІРєР»СЋС‡Р°РµС‚ drag.
- `BassDsp.kt`: signed complex RMS С‚РѕР»СЊРєРѕ Р±РёРЅРѕРІ 1..N/2-1 РІ 40вЂ“180 Р“С†, fs РёР· callback samplingRate/1000, N=fft.size. РќРѕСЂРјР°Р»РёР·Р°С†РёСЏ РЅР° 128 вЂ” РѕС‚РЅРѕСЃРёС‚РµР»СЊРЅС‹Р№ РјР°СЃС€С‚Р°Р± signed-byte, РЅРµ РёР·РјРµСЂРµРЅРёРµ РіСЂРѕРјРєРѕСЃС‚Рё. Р•РґРёРЅСЃС‚РІРµРЅРЅР°СЏ РІСЂРµРјРµРЅРЅР°СЏ envelope: attack 15 РјСЃ, release 140 РјСЃ, alpha=1-exp(-dt/tau), РјРѕРЅРѕС‚РѕРЅРЅС‹Р№ elapsedRealtimeNanos; tween СЌРЅРµСЂРіРёРё СѓРґР°Р»С‘РЅ. FFT Р±РµР· callbacks Р±РѕР»РµРµ 250 РјСЃ Р·Р°С‚СѓС…Р°РµС‚ Рє РЅСѓР»СЋ.
- Visualizer РѕСЃРІРѕР±РѕР¶РґР°РµС‚СЃСЏ РїСЂРё pause, СѓС…РѕРґРµ РёР· RESUMED, СЃРјРµРЅРµ РѕРїСѓР±Р»РёРєРѕРІР°РЅРЅРѕРіРѕ sessionId/С‚СЂРµРєР°, РїРѕС‚РµСЂРµ СЂР°Р·СЂРµС€РµРЅРёСЏ Рё disposal. Р§Р°СЃС‚РёС‡РЅРѕ СЃРѕР·РґР°РЅРЅС‹Р№ СЌРєР·РµРјРїР»СЏСЂ РѕС‡РёС‰Р°РµС‚СЃСЏ РїСЂРё РёСЃРєР»СЋС‡РµРЅРёРё; РїРѕР·РґРЅРёРµ FFT РѕС‚СЃРµРєР°РµС‚ generation. Р Р°Р·СЂРµС€РµРЅРёРµ РїСЂРѕРІРµСЂСЏРµС‚СЃСЏ РЅР° resume Рё РєР°Р¶РґС‹Рµ 250 РјСЃ РІ foreground; Р±РµР· RECORD_AUDIO вЂ” РїР»Р°РІРЅС‹Р№ РґРµРєРѕСЂР°С‚РёРІРЅС‹Р№ ambient. Р­РЅРµСЂРіРёСЏ С‡РёС‚Р°РµС‚СЃСЏ РІ draw, callback РЅРµ РїРёС€РµС‚ Compose-state; beat sync РЅРµ Р·Р°СЏРІР»СЏРµС‚СЃСЏ.
- РћРіСЂР°РЅРёС‡РµРЅРёРµ: РЅРµРёР·РјРµРЅС‘РЅРЅС‹Р№ `PlaybackAudioSession` РЅРµ РїСѓР±Р»РёРєСѓРµС‚ Flow, РЅРµ СЃР»СѓС€Р°РµС‚ audio-session callback Рё РЅРµ СЃР±СЂР°СЃС‹РІР°РµС‚ id РІ 0. UI РѕРїСЂР°С€РёРІР°РµС‚ С‚РѕР»СЊРєРѕ РѕРїСѓР±Р»РёРєРѕРІР°РЅРЅС‹Р№ С„Р°РєС‚РёС‡РµСЃРєРёР№ id Рё РёСЃРїРѕР»СЊР·СѓРµС‚ РµРіРѕ РєР°Рє РєР»СЋС‡ СЂРµСЃСѓСЂСЃР°; СЃРјРµРЅСѓ СЃРµСЃСЃРёРё, РєРѕС‚РѕСЂСѓСЋ provider РЅРµ РѕРїСѓР±Р»РёРєРѕРІР°Р», РіР°СЂР°РЅС‚РёСЂРѕРІР°С‚СЊ РЅРµР»СЊР·СЏ. Р”РІРёР¶РѕРє, service/provider, РїРѕРёСЃРє, СЃРєР°С‡РёРІР°РЅРёРµ Рё Р·Р°РІРёСЃРёРјРѕСЃС‚Рё РЅРµ РјРµРЅСЏР»РёСЃСЊ.
- РџСЂРѕРІРµСЂРєР°: JDK 17 `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`, `gradlew.bat :app:tasks --all` вЂ” exit 0. Р’СЃРµ Р·Р°РїСѓСЃРєРё СЃ `--console=plain --max-workers=1 -Dorg.gradle.jvmargs="-Xmx2048m -Dfile.encoding=UTF-8"`.
- `gradlew.bat testDebugUnitTest assembleDebug lintDebug`: JVM-С‚РµСЃС‚С‹ Рё APK СЂРµР°Р»СЊРЅРѕ РІС‹РїРѕР»РЅРµРЅС‹; РѕР±С‰РёР№ exit 1 С‚РѕР»СЊРєРѕ РёР·-Р·Р° lint. XML: 133 testcase / 133 executed, 0 failures, 0 errors, 0 skipped (24 РЅРѕРІС‹С… DSP/state/RTL-С‚РµСЃС‚Р°). `lintDebug`: 2 UnsafeOptInUsageError РІ РЅРµРёР·РјРµРЅС‘РЅРЅС‹С… `PlaybackAudioSession.kt:30` Рё `PlayerController.kt:153`, 25 warnings; РїСЂР°РІРёР»Р° РЅРµ РѕСЃР»Р°Р±Р»СЏР»РёСЃСЊ, Р·Р°РїСЂРµС‰С‘РЅРЅС‹Р№ РґРІРёР¶РѕРє РЅРµ РїСЂР°РІРёР»СЃСЏ. РћС‚РґРµР»СЊРЅРѕРµ РїРѕРґС‚РІРµСЂР¶РґРµРЅРёРµ `gradlew.bat testDebugUnitTest assembleDebug` вЂ” BUILD SUCCESSFUL, exit 0, 5 СЃРµРєСѓРЅРґ, 53 up-to-date (РЅРµ РїРѕРІС‚РѕСЂРЅРѕРµ РёСЃРїРѕР»РЅРµРЅРёРµ С‚РµСЃС‚РѕРІ).
- APK РїСЂРѕРІРµСЂРµРЅ: `D:\Facet\app\build\outputs\apk\debug\app-debug.apk`, 27 502 042 Р±Р°Р№С‚, СЃРѕР·РґР°РЅ 26.10.2026 18:28:28 +03:00. `git diff --check` Р±РµР· РѕС€РёР±РѕРє. РџРµСЂРІР°СЏ РїРѕРїС‹С‚РєР° tasks РёСЃРїРѕР»СЊР·РѕРІР°Р»Р° СЃРёСЃС‚РµРјРЅСѓСЋ Java 11 Рё СѓРїР°Р»Р°; РёСЃРїСЂР°РІР»РµРЅРѕ С‚РѕР»СЊРєРѕ РѕРєСЂСѓР¶РµРЅРёРµ. РћРґРЅР° РїСЂРѕРјРµР¶СѓС‚РѕС‡РЅР°СЏ СЃР±РѕСЂРєР° СѓРїР°Р»Р° РЅР° РµС‰С‘ РЅРµ РѕР±СЉСЏРІР»РµРЅРЅРѕРј SliderFinishInteraction; РїРѕСЃР»Рµ Р·Р°РІРµСЂС€РµРЅРёСЏ РїСЂР°РІРєРё РєРѕРјРїРёР»СЏС†РёСЏ РїСЂРѕС€Р»Р°. Р›РёРјРёС‚ РїРѕРІС‚РѕСЂРЅС‹С… РѕРґРёРЅР°РєРѕРІС‹С… РѕС€РёР±РѕРє РЅРµ РґРѕСЃС‚РёРіРЅСѓС‚.
- Compose UI-С‚РµСЃС‚С‹ РЅРµ РґРѕР±Р°РІР»СЏР»РёСЃСЊ: РІ РїСЂРѕРµРєС‚Рµ РЅРµС‚ androidTest Рё compose-ui-test Р·Р°РІРёСЃРёРјРѕСЃС‚Рё; РЅРѕРІС‹Рµ Р·Р°РІРёСЃРёРјРѕСЃС‚Рё Р·Р°РїСЂРµС‰РµРЅС‹. РќР° С‚РµР»РµС„РѕРЅРµ РѕСЃС‚Р°СЋС‚СЃСЏ Cancel/РїРµСЂРµС…РІР°С‚ Р¶РµСЃС‚Р°, СЃРјРµРЅР° С‚СЂРµРєР° РґРѕ release, TalkBack/РєР»Р°РІРёР°С‚СѓСЂР°, RTL/disabled, СЃРѕСЃРµРґРЅРёРµ РєРЅРѕРїРєРё, pause/background/resume, РѕС‚Р·С‹РІ СЂР°Р·СЂРµС€РµРЅРёСЏ, СЂРµР°Р»СЊРЅС‹Рµ FFT/ROM Рё СЃРјРµРЅР° session. РЈСЃРїРµС€РЅС‹Рµ JVM-С‚РµСЃС‚С‹ РЅРµ Р·Р°РјРµРЅСЏСЋС‚ СЌС‚Рё РїСЂРѕРІРµСЂРєРё.
- Р’РµС‚РєР° `main`; Р±РµР· commit/push. РџРѕР»СЊР·РѕРІР°С‚РµР»СЊСЃРєРёРµ `.opencode/`, `.postman/`, `postman/` РЅРµ Р·Р°С‚СЂРѕРЅСѓС‚С‹; РїРѕСЏРІРёРІС€Р°СЏСЃСЏ РІ status `.kotlin/` СЃРѕРґРµСЂР¶РёС‚ Р»РѕРєР°Р»СЊРЅС‹Рµ СЃР»СѓР¶РµР±РЅС‹Рµ С„Р°Р№Р»С‹ РєРѕРјРїРёР»СЏС‚РѕСЂР° Рё РЅРµ РІРєР»СЋС‡Р°РµС‚СЃСЏ РІ РїСѓР±Р»РёРєР°С†РёСЋ.

## рџ“Њ РђРєС‚СѓР°Р»СЊРЅС‹Р№ СЃСЂРµР·
- 129 СЂРµР°Р»СЊРЅРѕ РІС‹РїРѕР»РЅРµРЅРЅС‹С… JVM-С‚РµСЃС‚РѕРІ (20 suites), 0 failures/errors/skipped; `assembleDebug` РїРѕРґС‚РІРµСЂР¶РґС‘РЅ, APK 27 508 441 Р±Р°Р№С‚. Lint РІ СЌС‚РѕР№ СЃРµСЃСЃРёРё РЅРµ Р·Р°РїСѓСЃРєР°Р»СЃСЏ.
- Room v8, СЃС…РµРјС‹ `4.json`вЂ“`8.json`; ONNX/YAMNet Рё `core/audio` СѓРґР°Р»РµРЅС‹ revert-РєРѕРјРјРёС‚РѕРј `9e74fb6`.
- В«Р”Р»СЏ РІР°СЃВ»: related-С‚СЂРµРєРё YouTube/SoundCloud С‡РµСЂРµР· NewPipe, 4вЂ“6 С‚СЂРµРєРѕРІ-СЃРёРґРѕРІ, РєР°Рї 2/3, fallback С‚РѕР»СЊРєРѕ РїСЂРё <10 Рё С‚РѕР»СЊРєРѕ РїРѕ СЃС‚СЂРѕРіРѕРјСѓ Р°СЂС‚РёСЃС‚Сѓ; РіР»РѕР±Р°Р»СЊРЅС‹Р№ РїРѕРїСѓР»СЏСЂРЅС‹Р№ Р·Р°РїСЂРѕСЃ вЂ” С‚РѕР»СЊРєРѕ РЅР° РїСѓСЃС‚РѕР№ РёСЃС‚РѕСЂРёРё.
- РЎРєР°С‡РёРІР°РЅРёРµ: `DownloadSourceResolver` РІС‹Р±РёСЂР°РµС‚ С‚РѕР»СЊРєРѕ progressive-РёСЃС‚РѕС‡РЅРёРє; SoundCloud вЂ” PROGRESSIVE (HLS-only С‚СЂРµРє РїР°РґР°РµС‚ Рё СѓС…РѕРґРёС‚ РЅР° YouTube), YouTube вЂ” PROGRESSIVE. РЎРЅР°С‡Р°Р»Р° СЃС‚СЂРѕРіРёР№ РјР°С‚С‡ Р°СЂС‚РёСЃС‚Р° Рё РЅР°Р·РІР°РЅРёСЏ; РґР»СЏ YouTube вЂ” РЅРµСЃС‚СЂРѕРіРёР№ fallback: Р·Р°РїСЂРѕСЃ Р±РµР· СЃР»СѓР¶РµР±РЅС‹С… РїРѕРјРµС‚РѕРє, С‚РѕРї-10, СЂР°Р·РЅРёС†Р° РґР»РёС‚РµР»СЊРЅРѕСЃС‚Рё в‰¤15 СЃ, Р°СЂС‚РёСЃС‚ РІ РЅР°Р·РІР°РЅРёРё РёР»Рё РєР°РЅР°Р»Рµ. TTL 30 РјРёРЅ РґР»СЏ РЅРµСѓРґР°С‡РЅС‹С… РёСЃС‚РѕС‡РЅРёРєРѕРІ. Р РµР·РѕР»РІ СЃРІРµР¶РµРіРѕ URL РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ РІ `DownloadTrackWorker` РїРµСЂРµРґ РєР°Р¶РґРѕР№ РїРѕРїС‹С‚РєРѕР№; РїСЂРё 403/410 вЂ” РѕРґРёРЅ РїРѕРІС‚РѕСЂРЅС‹Р№ СЂРµР·РѕР»РІ. Р—Р°РїРёСЃСЊ `.part` в†’ Р°С‚РѕРјР°СЂРЅС‹Р№ rename, Room РїРѕСЃР»Рµ rename; РѕС‚РјРµРЅР° РѕС‚РјРµРЅСЏРµС‚ OkHttp/NewPipe `Call`; HTTP BASIC-Р»РѕРіРё С‚РѕР»СЊРєРѕ РІ debug. РљРЅРѕРїРєР° СЃРєР°С‡РёРІР°РЅРёСЏ РµСЃС‚СЊ РІ РјРµРґРёР°С‚РµРєРµ Рё РїРѕР»РЅРѕСЌРєСЂР°РЅРЅРѕРј РїР»РµРµСЂРµ.
- РџР»РµРµСЂ: СЂРµР¶РёРј С‚РµРєСЃС‚Р° РѕС‚РїРѕР»РёСЂРѕРІР°РЅ РІ Phase A вЂ” СЃРїРѕРєРѕР№РЅС‹Р№ С„РѕРЅ (scrim `surface` 0.65 РїРѕРІРµСЂС… СЂР°Р·РјС‹С‚РѕР№ РѕР±Р»РѕР¶РєРё), РёРµСЂР°СЂС…РёСЏ СЃС‚СЂРѕРє 24/18/16sp, РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅР°СЏ РєР°СЂР°РѕРєРµ-Р·Р°Р»РёРІРєР°, РїРѕРґСЉС‘Рј Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё РЅР° 24dp. РўР°Р№РјР»Р°Р№РЅ вЂ” РІРѕР»РЅР° Phase B РЅР° Material3 Slider: СЃРµСЂР°СЏ СЃРёРЅСѓСЃРѕРёРґР° СЃ РґР»РёРЅРѕР№ 44dp Рё СЃСѓР¶РµРЅРёРµРј Рє РєСЂР°СЏРј, РїСЂРѕРїРµС‚Р°РЅРЅР°СЏ С‡Р°СЃС‚СЊ РІ `primary`, РєСЂСѓРіР»С‹Р№ Р±РµРіСѓРЅРѕРє РЅР° РіСЂРµР±РЅРµ; Р°РјРїР»РёС‚СѓРґР° РїРѕСЃС‚РѕСЏРЅРЅР°СЏ 9dp, РЅР° РїР°СѓР·Рµ РІРѕР»РЅР° РїР»Р°РІРЅРѕ РІС‹РїСЂСЏРјР»СЏРµС‚СЃСЏ. DSP/Visualizer СѓРґР°Р»РµРЅС‹ РІ СЃРµСЃСЃРёРё 21, `RECORD_AUDIO` РІ РјР°РЅРёС„РµСЃС‚Рµ РЅРµС‚.

---

## вњ… РЎРµСЃСЃРёСЏ 1 вЂ” РЎРєРµР»РµС‚ РїСЂРѕРµРєС‚Р° (15.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
- РќР°СЃС‚СЂРѕРµРЅ СЃС‚РµРє Р·Р°РІРёСЃРёРјРѕСЃС‚РµР№: Hilt 2.52, Media3 1.5.0, Room 2.6.1, Retrofit, Coil 3, NewPipe Extractor v0.24.3
- РЎРѕР·РґР°РЅР° С‚РµРјР° Material 3 СЃ Dynamic Color (Android 12+) Рё violet-purple РїР°Р»РёС‚СЂРѕР№ РґР»СЏ СЃС‚Р°СЂС‹С… РІРµСЂСЃРёР№
- Р РµР°Р»РёР·РѕРІР°РЅР° РЅР°РІРёРіР°С†РёСЏ: `BottomNavigationBar` (4 РІРєР»Р°РґРєРё) + full-screen РїР»РµРµСЂ СЃРѕ slide-up Р°РЅРёРјР°С†РёРµР№
- РЎРѕР·РґР°РЅС‹ Р·Р°РіР»СѓС€РєРё РІСЃРµС… СЌРєСЂР°РЅРѕРІ: Home, Search, Library, Wave, Player
- `FacetApp` (`@HiltAndroidApp`) РёРЅРёС†РёР°Р»РёР·РёСЂСѓРµС‚ NewPipe
- РќР°СЃС‚СЂРѕРµРЅ edge-to-edge СЂРµР¶РёРј

### Р¤Р°Р№Р»С‹
```
FacetApp.kt, MainActivity.kt
navigation/Screen.kt, navigation/AppNavGraph.kt
core/ui/theme/Color.kt, Type.kt, Theme.kt
feature/home/HomeScreen.kt
feature/search/SearchScreen.kt
feature/library/LibraryScreen.kt
feature/wave/WaveScreen.kt
feature/player/PlayerScreen.kt
```

---

## вњ… РЎРµСЃСЃРёСЏ 2 вЂ” SoundCloud РїРѕРёСЃРє С‡РµСЂРµР· NewPipe (15.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
- Р РµР°Р»РёР·РѕРІР°РЅ СЃР»РѕР№ РґР°РЅРЅС‹С… РґР»СЏ SoundCloud:
  - `SoundCloudRepository` (РёРЅС‚РµСЂС„РµР№СЃ): `search()` + `resolveStreamUrl()`
  - `SoundCloudRepositoryImpl`: РёСЃРїРѕР»СЊР·СѓРµС‚ `SearchInfo.getInfo()` Рё `StreamInfo.getInfo()` РёР· NewPipe
  - `NewPipeDownloader`: OkHttp-СЂРµР°Р»РёР·Р°С†РёСЏ `Downloader` РґР»СЏ NewPipe
- Hilt РјРѕРґСѓР»Рё: `NetworkModule` (OkHttp, Retrofit РґР»СЏ LRCLIB), `RepositoryModule` (@Binds)
- `SearchViewModel`: debounce 400 РјСЃ РЅР° Flow Р·Р°РїСЂРѕСЃРѕРІ, РјРёРЅРёРјСѓРј 2 СЃРёРјРІРѕР»Р°
- `SearchScreen`: 4 СЃРѕСЃС‚РѕСЏРЅРёСЏ UI (РїРѕРґСЃРєР°Р·РєР° / Р·Р°РіСЂСѓР·РєР° / РѕС€РёР±РєР°+РїРѕРІС‚РѕСЂ / СЃРїРёСЃРѕРє)
- `TrackCard`: РїРµСЂРµРёСЃРїРѕР»СЊР·СѓРµРјС‹Р№ РєРѕРјРїРѕРЅРµРЅС‚ (M3 ListItem + Coil AsyncImage)

### РћС‚РєР°Р·Р°Р»РёСЃСЊ РѕС‚
- РЎР°РјРѕРїРёСЃРЅРѕРіРѕ РїР°СЂСЃРµСЂР° `client_id` РёР· JS-Р±Р°РЅРґР»РѕРІ SC (РЅРµСЃС‚Р°Р±РёР»РµРЅ, SC РјРµРЅСЏРµС‚ СЃС‚СЂСѓРєС‚СѓСЂСѓ)
- Retrofit РґР»СЏ SoundCloud (Р·Р°РјРµРЅС‘РЅ NewPipe)

### Р¤Р°Р№Р»С‹
```
data/model/Track.kt
data/repository/SoundCloudRepository.kt
data/source/soundcloud/SoundCloudRepositoryImpl.kt
core/network/NewPipeDownloader.kt
core/network/NetworkModule.kt
core/di/RepositoryModule.kt
feature/search/SearchViewModel.kt
feature/search/SearchScreen.kt (РѕР±РЅРѕРІР»С‘РЅ)
core/ui/components/TrackCard.kt
```

---

## вњ… РЎРµСЃСЃРёСЏ 3 вЂ” Media3 HLS РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёРµ (16.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
- Р”РѕР±Р°РІР»РµРЅР° Р·Р°РІРёСЃРёРјРѕСЃС‚СЊ `media3-exoplayer-hls:1.5.0`
- РћР±РЅРѕРІР»С‘РЅ NewPipe Extractor РґРѕ `v0.26.5`

### Р¤Р°Р№Р»С‹
```
app/build.gradle.kts, AGENTS.md
```

### РўРµРєСѓС‰РµРµ СЃРѕСЃС‚РѕСЏРЅРёРµ
- РџРѕРёСЃРє SoundCloud + HLS-РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёРµ СЂР°Р±РѕС‚Р°СЋС‚, С‚СЂРµРєРё РёРіСЂР°СЋС‚ РІ С„РѕРЅРµ

---

## вњ… РЎРµСЃСЃРёСЏ 4 вЂ” РџРѕР»РёСЂРѕРІРєР° UI РїРѕ С‡РµРє-Р»РёСЃС‚Сѓ (16.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Coil (TrackCard, PlayerScreen, MiniPlayer)**: РїРѕРґРєР»СЋС‡С‘РЅ `crossfade(200)`, С„РёРєСЃРёСЂРѕРІР°РЅРЅС‹Рµ РєРѕРЅС‚РµР№РЅРµСЂС‹ (`Modifier.size`), С‚РѕРЅРёСЂРѕРІР°РЅРЅС‹Рµ РїР»РµР№СЃС…РѕР»РґРµСЂС‹ (`surfaceVariant`) Рё error-РїР»РµР№СЃС…РѕР»РґРµСЂ СЃ РІРµРєС‚РѕСЂРЅРѕР№ РёРєРѕРЅРєРѕР№ РЅРѕС‚С‹ (`Icons.Rounded.MusicNote`). РЎРєР°С‡РєРё Р»РµР№Р°СѓС‚Р° РёСЃРєР»СЋС‡РµРЅС‹.
2. **РЎР»Р°Р№РґРµСЂ РїР»РµРµСЂР°**: РѕР±РЅРѕРІР»РµРЅРёРµ РїРѕР·РёС†РёРё С‚СЂРѕС‚С‚Р»РёС‚СЃСЏ (~6-7 СЂР°Р·/СЃРµРє, 150 РјСЃ); РІРѕ РІСЂРµРјСЏ РїРµСЂРµС‚Р°СЃРєРёРІР°РЅРёСЏ Р·РЅР°С‡РµРЅРёРµ СѓРґРµСЂР¶РёРІР°РµС‚СЃСЏ РІ Р»РѕРєР°Р»СЊРЅРѕРј state Р±РµР· РєРѕРЅС„Р»РёРєС‚РѕРІ СЃ РїР°Р»СЊС†РµРј, seek РІС‹Р·С‹РІР°РµС‚СЃСЏ СЃС‚СЂРѕРіРѕ РІ `onValueChangeFinished`.
3. **MiniPlayer**: Р°РЅРёРјРёСЂРѕРІР°РЅРЅРѕРµ РїРѕСЏРІР»РµРЅРёРµ Рё СЃРєСЂС‹С‚РёРµ С‡РµСЂРµР· `AnimatedVisibility` (slide + fade, 250 РјСЃ), СЃРїРёСЃРѕРє СЂРµР·СѓР»СЊС‚Р°С‚РѕРІ РїРѕРёСЃРєР° РЅРµ РїРµСЂРµРєСЂС‹РІР°РµС‚СЃСЏ Р±Р»Р°РіРѕРґР°СЂСЏ `contentPadding` (80.dp).
4. **РџРµСЂРµРєР»СЋС‡РµРЅРёРµ РІРєР»Р°РґРѕРє**: РЅР°СЃС‚СЂРѕРµРЅ РїР»Р°РІРЅС‹Р№ `fadeIn/fadeOut` (200 РјСЃ); СЃРѕСЃС‚РѕСЏРЅРёРµ СЃРѕС…СЂР°РЅСЏРµС‚СЃСЏ С‡РµСЂРµР· `saveState`/`restoreState` Рё `launchSingleTop`.
5. **РћС‚РєСЂС‹С‚РёРµ/Р·Р°РєСЂС‹С‚РёРµ РїР»РµРµСЂР°**: РїР»Р°РІРЅР°СЏ slide-up / slide-down Р°РЅРёРјР°С†РёСЏ (300 РјСЃ) СЃ fade Р±РµР· РјРµСЂС†Р°РЅРёР№ Рё РґСѓР±Р»РёСЂРѕРІР°РЅРёСЏ.
6. **РџРѕРёСЃРє**: РІРЅРµРґСЂРµРЅС‹ СЃРєРµР»РµС‚РѕРЅ-РєР°СЂС‚РѕС‡РєРё РїСЂРё Р·Р°РіСЂСѓР·РєРµ РІРјРµСЃС‚Рѕ РѕРґРёРЅРѕС‡РЅРѕРіРѕ СЃРїРёРЅРЅРµСЂР°; СЃРѕС…СЂР°РЅРµРЅРѕ СЃРѕСЃС‚РѕСЏРЅРёРµ РѕС€РёР±РєРё СЃ РєРЅРѕРїРєРѕР№ РїРѕРІС‚РѕСЂР°.
7. **LazyColumn**: Р·Р°РґР°РЅ `key = { it.id }`, РјРµР¶РєР°СЂС‚С‡РЅС‹Рµ РѕС‚СЃС‚СѓРїС‹ С‡РµСЂРµР· `Arrangement.spacedBy(4.dp)`, `contentPadding` СЃРЅРёР·Сѓ СЃ СѓС‡С‘С‚РѕРј РІС‹СЃРѕС‚С‹ `MiniPlayer`.
8. **Р РµРєРѕРјРїРѕР·РёС†РёРё**: СЃРµРєС†РёСЏ СЃР»Р°Р№РґРµСЂР° Рё С‚Р°Р№РјРµСЂРѕРІ РІС‹РЅРµСЃРµРЅР° РІ РёР·РѕР»РёСЂРѕРІР°РЅРЅС‹Р№ `@Composable` (`PlayerProgressSection`), С‚РёРє РїСЂРѕРіСЂРµСЃСЃР° РЅРµ С‚СЂРёРіРіРµСЂРёС‚ СЂРµРєРѕРјРїРѕР·РёС†РёСЋ РѕР±Р»РѕР¶РєРё Рё РІСЃРµР№ СЃС‚СЂР°РЅРёС†С‹.
9. **РћС‚СЃС‚СѓРїС‹ Рё С‚РёРїРѕРіСЂР°С„РёРєР°**: РїСЂРёРІРµРґРµРЅС‹ Рє С‚РѕРєРµРЅР°Рј `MaterialTheme.typography` Рё `MaterialTheme.colorScheme`, СѓСЃС‚СЂР°РЅРµРЅС‹ С…Р°СЂРґРєРѕРґ-СЂР°Р·РјРµСЂС‹.
10. **Edge-to-edge**: РїСЂРѕРІРµСЂРµРЅС‹ Рё РіР°СЂРјРѕРЅРёР·РёСЂРѕРІР°РЅС‹ СЃРёСЃС‚РµРјРЅС‹Рµ insets (TopAppBar СЃ padding РІ Scaffold).
11. **РўРѕС‡РµС‡РЅС‹Рµ С„РёРєСЃС‹ РїСЂРѕРёР·РІРѕРґРёС‚РµР»СЊРЅРѕСЃС‚Рё**: `SubcomposeAsyncImage` Р·Р°РјРµРЅС‘РЅ РЅР° Р»РµРіРєРѕРІРµСЃРЅС‹Р№ `AsyncImage` СЃ `placeholder = ColorPainter(surfaceVariant)` Рё РІРµРєС‚РѕСЂРѕРј РЅРѕС‚С‹ РґР»СЏ `error` (СѓСЃС‚СЂР°РЅРµРЅС‹ Р»РёС€РЅРёРµ С„Р°Р·С‹ СЃСѓР±РєРѕРјРїРѕР·РёС†РёРё РІ СЃРїРёСЃРєР°С…); СЃРєРµР»РµС‚РѕРЅС‹ РїРѕРёСЃРєР° РІС‹РїРѕР»РЅРµРЅС‹ СЃС‚Р°С‚РёС‡РЅС‹РјРё Р±РµР· С€РёРјРјРµСЂ-РЅР°РіСЂСѓР·РєРё; Р°РЅРёРјР°С†РёСЏ РІРєР»Р°РґРѕРє РѕР±Р»РµРіС‡РµРЅР° РґРѕ `fadeIn(200)` РЅР° enter Рё `ExitTransition.None` РЅР° exit; `contentPadding` СЃРїРёСЃРєРѕРІ СЃРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°РЅ СЃРѕ `Scaffold`.

### Р¤Р°Р№Р»С‹
```
core/ui/components/TrackCard.kt
core/ui/components/MiniPlayer.kt
feature/player/PlayerScreen.kt
feature/search/SearchScreen.kt
navigation/AppNavGraph.kt
```

---

## рџ“Љ РўРµРєСѓС‰РµРµ СЃРѕСЃС‚РѕСЏРЅРёРµ

> Р‘Р»РѕРє Р°РєС‚СѓР°Р»РµРЅ СЃ 21.10.2026. Р Р°РЅРµРµ Р·РґРµСЃСЊ РІРёСЃРµР»Рѕ РѕРїРёСЃР°РЅРёРµ РІСЂРµРјС‘РЅ СЃРµСЃСЃРёРё 4 вЂ” РѕС‡РµСЂРµРґСЊ, РІРѕР»РЅР° Рё РїР»РµР№Р»РёСЃС‚С‹ С‚Р°Рј Р·РЅР°С‡РёР»РёСЃСЊ РєР°Рє РЅРµСЂРµР°Р»РёР·РѕРІР°РЅРЅС‹Рµ, СЌС‚Рѕ СѓСЃС‚Р°СЂРµР»Рѕ.

### Р Р°Р±РѕС‚Р°РµС‚
- вњ… РЎР±РѕСЂРєР° (`assembleDebug`) Рё 53 JVM-С‚РµСЃС‚Р° (`.\gradlew test`, Robolectric + MockK, Р±РµР· СЌРјСѓР»СЏС‚РѕСЂР°)
- вњ… РќР°РІРёРіР°С†РёСЏ РјРµР¶РґСѓ РІРєР»Р°РґРєР°РјРё СЃ РїР»Р°РІРЅС‹РјРё Р°РЅРёРјР°С†РёСЏРјРё, edge-to-edge, С„РёРѕР»РµС‚РѕРІР°СЏ Material 3 Expressive С‚РµРјР°
- вњ… РџРѕРёСЃРє SoundCloud С‡РµСЂРµР· NewPipe Extractor (Р±РµР· client_id), СЃРєРµР»РµС‚РѕРЅС‹ Рё СЃРѕСЃС‚РѕСЏРЅРёСЏ РѕС€РёР±РєРё
- вњ… Р“Р»Р°РІРЅР°СЏ: Top 10 SoundCloud СЃ РєСЌС€РµРј РІ Room (TTL 30 РјРёРЅ), В«РџСЂРѕРґРѕР»Р¶РёС‚СЊВ», В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏВ» РїРѕ СЃРµР·РѕРЅСѓ, РЅРµРґР°РІРЅРёРµ, Р»СЋР±РёРјС‹Рµ, СЃРµРєС†РёРё РїРѕ Р¶Р°РЅСЂР°Рј
- вњ… РџР»РµРµСЂ: ExoPlayer Р¶РёРІС‘С‚ С‚РѕР»СЊРєРѕ РІ `MediaSessionService`, UI СЂР°Р±РѕС‚Р°РµС‚ С‡РµСЂРµР· `MediaController`; HLS, С„РѕРЅРѕРІРѕРµ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёРµ, СѓРІРµРґРѕРјР»РµРЅРёРµ СЃ РѕР±Р»РѕР¶РєРѕР№, audio focus, wake mode
- вњ… РћС‡РµСЂРµРґСЊ: С€С‚РѕСЂРєР° `PlayerQueueSheet`, shuffle, repeat OFF/ALL/ONE, СЃРєРѕСЂРѕСЃС‚СЊ 0.5вЂ“2.0Г—, СѓРґР°Р»РµРЅРёРµ С‚СЂРµРєРѕРІ, РїРµСЂРµС…РѕРґ Рє РІС‹Р±СЂР°РЅРЅРѕРјСѓ
- вњ… РўРµРєСЃС‚С‹ LRCLIB: СЃРёРЅС…СЂРѕРЅРЅС‹Р№ РєР°СЂР°РѕРєРµ-СЂРµР¶РёРј, Р°РІС‚РѕС†РµРЅС‚СЂРёСЂРѕРІР°РЅРёРµ, С‚Р°Рї РїРѕ СЃС‚СЂРѕРєРµ = РїРµСЂРµРјРѕС‚РєР°
- вњ… Р›Р°Р№РєРё РІ Room v5 СЃ РіРµРѕРїРѕР·РёС†РёРµР№ Рё СЃРµР·РѕРЅРѕРј, РјРёРіСЂР°С†РёРё 1в†’5 СЃ СЌРєСЃРїРѕСЂС‚РѕРј СЃС…РµРј (Р±РµР· destructive fallback)
- вњ… РћС„Р»Р°Р№РЅ-СЂРµР¶РёРј: СЃРєР°С‡РёРІР°РЅРёРµ С‚СЂРµРєРѕРІ С‡РµСЂРµР· WorkManager РІ `filesDir/downloads`, Room v6 (`downloaded_tracks`), Р»РѕРєР°Р»СЊРЅС‹Р№ С„Р°Р№Р» РІ РїСЂРёРѕСЂРёС‚РµС‚Рµ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёСЏ, РєРЅРѕРїРєР° СЃРєР°С‡РёРІР°РЅРёСЏ РІ РїР»РµРµСЂРµ Рё РіР°Р»РѕС‡РєР° РІ РєР°СЂС‚РѕС‡РєР°С…
- вњ… В«РњРѕСЏ РІРѕР»РЅР°В»: Р±РµСЃРєРѕРЅРµС‡РЅР°СЏ СЃРµСЃСЃРёСЏ, Last.fm + Р»Р°Р№РєРё + РёСЃС‚РѕСЂРёСЏ + Р¶Р°РЅСЂС‹ (40/30/20/10), Р»РѕРєР°Р»СЊРЅС‹Р№ feedback РІРєСѓСЃРѕРІ, Р°РІС‚РѕРґРѕР·Р°РіСЂСѓР·РєР° РїР°С‡РєР°РјРё РїРѕ 12, RU-РїСЂРёРѕСЂРёС‚РµС‚ СЃ СЏР·С‹РєРѕРІРѕР№ С„РёР»СЊС‚СЂР°С†РёРµР№
- вњ… РЎР»Р°Р№РґРµСЂ-РІРѕР»РЅР° СЃ РєРѕСЂСЂРµРєС‚РЅРѕР№ Р·РѕРЅРѕР№ РєР°СЃР°РЅРёСЏ (С„РёРєСЃ РєР»РёРєР° Play/Pause)

### РџРѕРєР° РЅРµ СЂРµР°Р»РёР·РѕРІР°РЅРѕ
- вќЊ РЈРїСЂР°РІР»РµРЅРёРµ СЃРєР°С‡Р°РЅРЅС‹Рј: СѓРґР°Р»РµРЅРёРµ РёР· UI, Р»РёРјРёС‚ С…СЂР°РЅРёР»РёС‰Р°, РѕС‚РґРµР»СЊРЅС‹Р№ СЌРєСЂР°РЅ В«РЎРєР°С‡Р°РЅРЅРѕРµВ»
- вќЊ РџР»РµР№Р»РёСЃС‚С‹ (РЅРµС‚ РЅРё РІ UI, РЅРё РІ Р‘Р”) вЂ” СЌС‚Рѕ v2
- вќЊ РќР°СЃС‚СЂРѕРµРЅРёСЏ РІРѕР»РЅС‹ (В«Р‘РѕРґСЂР°СЏ / РЎРїРѕРєРѕР№РЅР°СЏ / РџРѕ Р»СЋР±РёРјС‹Рј / РћС‚РєСЂС‹С‚РёСЏВ»)
- вќЊ РџРѕРєР°Р· РєРѕРѕСЂРґРёРЅР°С‚ Рё СЃРµР·РѕРЅР° РІ РєР°СЂС‚РѕС‡РєР°С… В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёР№В», РґРµР№СЃС‚РІРёРµ В«РћС‚РєСЂС‹С‚СЊ РЅР° РєР°СЂС‚РµВ»
- вќЊ РСЃС‚РѕС‡РЅРёРєРё Spotify, РЇРЅРґРµРєСЃ РњСѓР·С‹РєР°, Р’Рљ РњСѓР·С‹РєР°, YouTube Music (v2)

---

## вњ… РЎРµСЃСЃРёСЏ 7 вЂ” РїР»РµРµСЂ Рё С‚РµРєСЃС‚С‹ РїРѕ СЂРµС„РµСЂРµРЅСЃСѓ exteraless (16.10.2026)

Р’С‹Р¶РёРјРєР° Р°СЂС…РёС‚РµРєС‚СѓСЂРЅС‹С… СЂРµС€РµРЅРёР№, РјРµС‚СЂРёРє, С†РІРµС‚РѕРІС‹С… СЂРѕР»РµР№ Рё Р°Р»РіРѕСЂРёС‚РјРѕРІ Р·Р°С„РёРєСЃРёСЂРѕРІР°РЅР° РІ [reference-notes.md](file:///D:/Facet/docs/reference-notes.md).

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РР·СѓС‡РµРЅРёРµ СЂРµС„РµСЂРµРЅСЃР°**:
   - РСЃСЃР»РµРґРѕРІР°РЅ С„РѕСЂРє Telegram `exteraless` (РєРѕРјРїРѕРЅРµРЅС‚С‹ `Md3Player`, `PlayerSheet`, `LyricsView`, `Lyrics`, `ControlsRow`, `PlayerMiniView`).
   - РџР°С‚С‚РµСЂРЅС‹, РјРµС‚СЂРёРєРё Рё Р°Р»РіРѕСЂРёС‚РјС‹ Р·Р°РґРѕРєСѓРјРµРЅС‚РёСЂРѕРІР°РЅС‹ РІ `docs/reference-notes.md` Р±РµР· РїСЂСЏРјРѕРіРѕ РєРѕРїРёСЂРѕРІР°РЅРёСЏ РєРѕРґР° (GPL).
2. **РЎР»РѕР№ РґР°РЅРЅС‹С… С‚РµРєСЃС‚РѕРІ РїРµСЃРµРЅ (LRC & LRCLIB)**:
   - РЎРѕР·РґР°РЅР° РјРѕРґРµР»СЊ `LyricLine` Рё `LyricsResult` РІ `data/model/LyricLine.kt`.
   - Р РµР°Р»РёР·РѕРІР°РЅ СѓСЃС‚РѕР№С‡РёРІС‹Р№ РїР°СЂСЃРµСЂ `LrcParser` СЃ РїРѕРґРґРµСЂР¶РєРѕР№ `[mm:ss.xx]`, `[mm:ss.xxx]`, `[offset:+-ms]`, РѕС‡РёСЃС‚РєРѕР№ С‚РµРіРѕРІ СЃР»РѕРіРѕРІ Рё С„РѕР»Р±СЌРєРѕРј РЅР° РЅРµСЃРёРЅС…СЂРѕРЅРЅС‹Р№ С‚РµРєСЃС‚.
   - РџРѕРґРєР»СЋС‡С‘РЅ РєР»РёРµРЅС‚ `LrclibApi` Рё СЂРµРїРѕР·РёС‚РѕСЂРёР№ `LyricsRepositoryImpl` (СЃРЅР°С‡Р°Р»Р° С‚РѕС‡РЅС‹Р№ РїРѕРёСЃРє `get?track_name=&artist_name=&duration=`, Р·Р°С‚РµРј С„РѕР»Р±СЌРє РЅР° `search`).
3. **Р РµРґРёР·Р°Р№РЅ PlayerScreen**:
   - **РЁР°РїРєР°**: РґРІСѓС…СЃС‚СЂРѕС‡РЅС‹Р№ Р·Р°РіРѕР»РѕРІРѕРє ("РЎР•Р™Р§РђРЎ РР“Р РђР•Рў" + РЅР°Р·РІР°РЅРёРµ С‚СЂРµРєР°), РєРЅРѕРїРєР° РЅР°Р·Р°Рґ Рё РјРµРЅСЋ РѕРїС†РёР№.
   - **РћР±Р»РѕР¶РєР° Рё СЂРµР¶РёРј С‚РµРєСЃС‚Р°**:
     - Р’ РѕР±С‹С‡РЅРѕРј СЂРµР¶РёРјРµ: РїРѕР»РЅРѕСЂР°Р·РјРµСЂРЅР°СЏ РѕР±Р»РѕР¶РєР° 280dp СЃРѕ СЃРєСЂСѓРіР»РµРЅРёРµРј 28dp Рё С‚РµРЅСЊСЋ.
     - Р’ СЂРµР¶РёРјРµ С‚РµРєСЃС‚Р°: РѕР±Р»РѕР¶РєР° РїР»Р°РІРЅРѕ СЃС…Р»РѕРїС‹РІР°РµС‚СЃСЏ РІ РєРѕРјРїР°РєС‚РЅСѓСЋ РјРёРЅРёР°С‚СЋСЂСѓ 56Г—56 dp (СЃРєСЂСѓРіР»РµРЅРёРµ 16dp), РѕСЃРІРѕР±РѕР¶РґР°СЏ РїСЂРѕСЃС‚СЂР°РЅСЃС‚РІРѕ РґР»СЏ СЃРїРёСЃРєР° СЃС‚СЂРѕРє `LazyColumn`.
     - РђР»СЊС„Р°-РіСЂР°РґРёРµРЅС‚ СЃС‚СЂРѕРє: Р°РєС‚РёРІРЅР°СЏ 1.0f (bold), СЃРѕСЃРµРґРЅСЏСЏ 0.52f, РѕСЃС‚Р°Р»СЊРЅС‹Рµ 0.35f; РїР»Р°РІРЅС‹Р№ Р°РІС‚РѕСЃРєСЂРѕР»Р» Рє С‚РµРєСѓС‰РµР№ СЃС‚СЂРѕРєРµ.
     - РРЅС‚РµСЂР°РєС‚РёРІРЅРѕСЃС‚СЊ: С‚Р°Рї РїРѕ СЃС‚СЂРѕРєРµ = РЅРµРјРµРґР»РµРЅРЅС‹Р№ РїРµСЂРµС…РѕРґ `seekTo(line.timeMs)`. РџРѕРґРїРёСЃСЊ "РЎРёРЅС…СЂРѕРЅРЅС‹Р№ С‚РµРєСЃС‚ В· LRCLIB".
   - **Р›Р°Р№Рє**: СЃРєРІРёСЂРєР» 48Г—48 dp СЃ СЂР°РґРёСѓСЃРѕРј 24dp СЂСЏРґРѕРј СЃ РЅР°Р·РІР°РЅРёРµРј Рё Р°СЂС‚РёСЃС‚РѕРј.
   - **РЈРїСЂР°РІР»РµРЅРёРµ (ControlsRow)**: С†РµРЅС‚СЂР°Р»СЊРЅР°СЏ РєРЅРѕРїРєР° Play/Pause СЃ РґРёРЅР°РјРёС‡РµСЃРєРёРј РјРѕСЂС„РёРЅРіРѕРј СЂР°РґРёСѓСЃР° (44dp РІ РїР°СѓР·Рµ / 28dp РІ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёРё).
   - **РџРёР»СЋР»Рё РґРµР№СЃС‚РІРёР№**: РєРѕРЅС‚РµР№РЅРµСЂ РІС‹СЃРѕС‚РѕР№ 52dp РёР· 3 СЌР»РµРјРµРЅС‚РѕРІ:
     - РЎРєРѕСЂРѕСЃС‚СЊ (1Г— / 1.25Г— / 1.5Г— / 2Г— / 0.5Г—) СЃ СЂРµР°Р»СЊРЅС‹Рј РїРµСЂРµРєР»СЋС‡РµРЅРёРµРј РІ `ExoPlayer` С‡РµСЂРµР· `setPlaybackSpeed`.
     - РўРµРєСЃС‚ (РїРёР»СЋР»СЏ РїРѕРґСЃРІРµС‡РёРІР°РµС‚СЃСЏ `secondaryContainer` РїСЂРё Р°РєС‚РёРІРЅРѕРј СЂРµР¶РёРјРµ).
     - Р”Р°Р»РµРµ (РєРЅРѕРїРєР° РѕС‡РµСЂРµРґРё).

### Р¤Р°Р№Р»С‹
```
docs/reference-notes.md
data/model/LyricLine.kt
data/source/lyrics/LrcParser.kt
data/source/lyrics/LrclibApi.kt
data/source/lyrics/LyricsRepositoryImpl.kt
data/repository/LyricsRepository.kt
core/network/NetworkModule.kt
core/di/RepositoryModule.kt
core/player/PlayerController.kt
feature/player/PlayerViewModel.kt
feature/player/PlayerScreen.kt
```

---

## вњ… РЎРµСЃСЃРёСЏ 8 вЂ” РђРЅРёРјР°С†РёРё Рё С‚РѕРЅРёСЂРѕРІР°РЅРёРµ РїР»РµРµСЂР° (16.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Р”РёРЅР°РјРёС‡РµСЃРєРѕРµ С‚РѕРЅРёСЂРѕРІР°РЅРёРµ С„РѕРЅР° (Palette)**:
   - Р”РѕР±Р°РІР»РµРЅР° Р·Р°РІРёСЃРёРјРѕСЃС‚СЊ `androidx.palette:palette-ktx:1.0.0`.
   - РЎРѕР·РґР°РЅ СѓС‚РёР»РёС‚РЅС‹Р№ РєРѕРјРїРѕРЅРµРЅС‚ `PlayerPalette.kt` (`rememberPlayerDynamicColors`) СЃ РєСЌС€РёСЂРѕРІР°РЅРёРµРј РїР°Р»РёС‚СЂС‹ РїРѕ `artworkUrl` РЅР° IO-РґРёСЃРїРµС‚С‡РµСЂРµ, РїСЂРµРґРѕС‚РІСЂР°С‰Р°СЋС‰РёРј РїРѕРІС‚РѕСЂРЅС‹Рµ РїРµСЂРµСЃС‡С‘С‚С‹ РЅР° С‚РёРєР°С… РїСЂРѕРіСЂРµСЃСЃР°.
   - Р¤РѕРЅ РїР»РµРµСЂР° РїР»Р°РІРЅРѕ С‚РѕРЅРёСЂСѓРµС‚СЃСЏ РіСЂР°РґРёРµРЅС‚РѕРј РѕС‚ РґРѕРјРёРЅР°РЅС‚РЅРѕРіРѕ С†РІРµС‚Р° РѕР±Р»РѕР¶РєРё Рє СЃРёСЃС‚РµРјРЅРѕРјСѓ С„РѕРЅСѓ `surface`.
2. **РђРЅРёРјРёСЂРѕРІР°РЅРЅРѕРµ СЃС…Р»РѕРїС‹РІР°РЅРёРµ РѕР±Р»РѕР¶РєРё**:
   - Р РµР°Р»РёР·РѕРІР°РЅР° РїР»Р°РІРЅР°СЏ С‚СЂР°РЅСЃС„РѕСЂРјР°С†РёСЏ СЂР°Р·РјРµСЂР° РѕР±Р»РѕР¶РєРё: 280dp $\leftrightarrow$ 56dp (`animateDpAsState`, 320 РјСЃ, `FastOutSlowInEasing`).
   - РЎРєСЂСѓРіР»РµРЅРёРµ Р°РЅРёРјРёСЂСѓРµС‚СЃСЏ РѕС‚ 28dp РґРѕ 16dp. Р’ СЂРµР¶РёРјРµ С‚РµРєСЃС‚Р° РјРёРЅРёР°С‚СЋСЂР° РѕР±Р»РѕР¶РєРё РѕСЂРіР°РЅРёС‡РЅРѕ РІСЃС‚Р°С‘С‚ РІ СЃС‚СЂРѕРєСѓ СЃ РЅР°Р·РІР°РЅРёРµРј Рё Р°СЂС‚РёСЃС‚РѕРј.
3. **Р“РµРѕРјРµС‚СЂРёСЏ Рё СЌР»РµРјРµРЅС‚С‹ СѓРїСЂР°РІР»РµРЅРёСЏ РїРѕ СЂРµС„РµСЂРµРЅСЃСѓ `exteraless`**:
   - РљРЅРѕРїРєР° В«Р›Р°Р№РєВ»: СЃРєРІРёСЂРєР» `RoundedCornerShape(18.dp)`.
   - РљРЅРѕРїРєРё В«PrevВ» Рё В«NextВ»: Р·Р°Р»РёС‚С‹Рµ РєРЅРѕРїРєРё СЂР°Р·РјРµСЂРѕРј `64.dp`.
   - РћСЃС‚Р°РІС€РµРµСЃСЏ РІСЂРµРјСЏ СЃРїСЂР°РІР° РѕС‚РѕР±СЂР°Р¶Р°РµС‚СЃСЏ РІ С„РѕСЂРјР°С‚Рµ СЃРѕ Р·РЅР°РєРѕРј РјРёРЅСѓСЃ (`"-m:ss"`).
4. **РЎРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°РЅРЅС‹Рµ С‚РµРєСЃС‚С‹**:
   - РџР»Р°РІРЅС‹Р№ Р°РІС‚РѕСЃРєСЂРѕР»Р» Рє С‚РµРєСѓС‰РµР№ СЃС‚СЂРѕРєРµ С‡РµСЂРµР· `animateScrollToItem`.
   - РќРµРѕРЅРѕРІРѕРµ СЃРІРµС‡РµРЅРёРµ Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё С‡РµСЂРµР· `TextStyle(shadow = Shadow(color, blurRadius = 8f))`.
5. **РџР»Р°РІРЅРѕСЃС‚СЊ РЅР°РІРёРіР°С†РёРё**:
   - РЎРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°РЅС‹ РїРµСЂРµС…РѕРґС‹ РІРєР»Р°РґРѕРє Рё slide-up/down РїРѕСЏРІР»РµРЅРёРµ РїР»РµРµСЂР° (300 РјСЃ).

### Р¤Р°Р№Р»С‹
```
app/build.gradle.kts
core/ui/theme/PlayerPalette.kt
feature/player/PlayerScreen.kt
navigation/AppNavGraph.kt
```

---

## рџђ› Р¤РёРєСЃ LRCLIB (16.10.2026, РїРѕР·РґРЅСЏСЏ СЃРµСЃСЃРёСЏ)

### Р§С‚Рѕ Р±С‹Р»Рѕ СЃР»РѕРјР°РЅРѕ
1. **РћС‚СЃСѓС‚СЃС‚РІРёРµ User-Agent**: `OkHttpClient` РѕС‚РїСЂР°РІР»СЏР» РґРµС„РѕР»С‚РЅС‹Р№ `User-Agent: okhttp/...`, РёР·-Р·Р° С‡РµРіРѕ Cloudflare/LRCLIB РІРѕР·РІСЂР°С‰Р°Р» `429 Too Many Requests` (RateLimited / Bot detection).
2. **РЁСѓРј РІ РјРµС‚Р°РґР°РЅРЅС‹С… SoundCloud**: РЅР°Р·РІР°РЅРёСЏ С‚СЂРµРєРѕРІ РЅР° SoundCloud С‡Р°СЃС‚Рѕ РёРјРµСЋС‚ РІРёРґ `"Artist - Title (Official Video)"` РёР»Рё СЃРѕРґРµСЂР¶Р°С‚ СЃСѓС„С„РёРєСЃС‹ `[HD]`, `(Audio)`, Р° `uploaderName` РјРѕР¶РµС‚ Р±С‹С‚СЊ РЅРµРѕС„РёС†РёР°Р»СЊРЅС‹Рј РєР°РЅР°Р»РѕРј. Р—Р°РїСЂРѕСЃС‹ РІ `/api/get` Рё `/api/search` СЃ В«СЃС‹СЂС‹РјРёВ» СЃС‚СЂРѕРєР°РјРё РѕС‚РґР°РІР°Р»Рё 404 РёР»Рё РїСѓСЃС‚РѕР№ СЃРїРёСЃРѕРє.
3. **Р‘СЂРѕСЃР°РЅРёРµ HttpException**: РјРµС‚РѕРґС‹ `LrclibApi` РІРѕР·РІСЂР°С‰Р°Р»Рё РјРѕРґРµР»СЊ РЅР°РїСЂСЏРјСѓСЋ, Рё С€С‚Р°С‚РЅС‹Р№ 404 (С‚СЂРµРє РЅРµ РЅР°Р№РґРµРЅ) РїР°РґР°Р» РІ РѕР±С‰РёР№ catch Р±РµР· РґРѕСЃС‚СѓРїР° Рє РєРѕРґСѓ РѕС‚РІРµС‚Р° Рё `errorBody`.
4. **РћС‚СЃСѓС‚СЃС‚РІРёРµ retry/Р»РµРЅРёРІРѕР№ РїРѕРґРіСЂСѓР·РєРё**: РµСЃР»Рё РїР»РµРµСЂ РѕС‚РєСЂС‹РІР°Р»СЃСЏ РёР· РјРёРЅРё-РїР»РµРµСЂР° РёР»Рё РїРµСЂРІС‹Р№ Р·Р°РїСЂРѕСЃ РїР°РґР°Р», РїРѕР»СЊР·РѕРІР°С‚РµР»СЊ РЅРµ РјРѕРі РїРѕРІС‚РѕСЂРёС‚СЊ РїРѕРёСЃРє С‚РµРєСЃС‚Р° Р±РµР· РїРµСЂРµР·Р°РїСѓСЃРєР° С‚СЂРµРєР°.

### РљР°Рє РїРѕС‡РёРЅРµРЅРѕ
1. **РЎРµС‚РµРІРѕР№ СЃР»РѕР№ (`NetworkModule.kt`)**: РґРѕР±Р°РІР»РµРЅ РёРЅС‚РµСЂС†РµРїС‚РѕСЂ СЃ РєР°СЃС‚РѕРјРЅС‹Рј `User-Agent: FacetApp/1.0 (https://github.com/souler/facet)` Рё Р·Р°РіРѕР»РѕРІРєРѕРј `Accept: application/json`.
2. **Retrofit API (`LrclibApi.kt`)**: СЌРЅРґРїРѕРёРЅС‚С‹ РѕР±С‘СЂРЅСѓС‚С‹ РІ `Response<...>`, РґРѕР±Р°РІР»РµРЅ РїР°СЂР°РјРµС‚СЂ `q` РґР»СЏ РїРѕР»РЅРѕС‚РµРєСЃС‚РѕРІРѕРіРѕ РїРѕРёСЃРєР°.
3. **РћС‡РёСЃС‚РєР° РјРµС‚Р°РґР°РЅРЅС‹С… Рё РєР°СЃРєР°РґРЅС‹Р№ РїРѕРёСЃРє (`LyricsRepositoryImpl.kt`)**:
   - `cleanTrackMetadata`: РёР·РІР»РµС‡РµРЅРёРµ РёСЃРїРѕР»РЅРёС‚РµР»СЏ РёР· С„РѕСЂРјР°С‚Р° `"Artist - Title"`, СѓРґР°Р»РµРЅРёРµ РјСѓСЃРѕСЂРЅС‹С… СЃСѓС„С„РёРєСЃРѕРІ `(Official Video)`, `(Audio)`, `(Remastered)` Рё СЃСѓС„С„РёРєСЃР° `"Official"` Сѓ Р°СЂС‚РёСЃС‚Р°.
   - РљР°СЃРєР°РґРЅС‹Р№ РїРѕРёСЃРє: С‚РѕС‡РЅС‹Р№ `GET /api/get` СЃ РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊСЋ $\to$ `GET /api/get` Р±РµР· РґР»РёС‚РµР»СЊРЅРѕСЃС‚Рё (РЅР° SC С…СЂРѕРЅРѕРјРµС‚СЂР°Р¶ С‡Р°СЃС‚Рѕ СЂР°СЃС…РѕРґРёС‚СЃСЏ РЅР° РїР°СЂСѓ СЃРµРєСѓРЅРґ) $\to$ `GET /api/search?track_name=&artist_name=` $\to$ РїРѕР»РЅРѕС‚РµРєСЃС‚РѕРІС‹Р№ `GET /api/search?q=Artist Title` $\to$ С„РѕР»Р±СЌРє РЅР° РёСЃС…РѕРґРЅС‹Рµ СЃС‚СЂРѕРєРё.
   - Р”РµС‚Р°Р»СЊРЅРѕРµ Р»РѕРіРёСЂРѕРІР°РЅРёРµ СЃ С‚РµРіРѕРј `LRCLIB`: `Requesting`, `Response code + body preview`, `No lyrics found`, `Error: errorBody`.
4. **ViewModel Рё UI (`PlayerViewModel.kt`, `PlayerScreen.kt`)**:
   - РњРµС‚РѕРґ `loadLyricsIfNeeded(track)` РґР»СЏ РґРѕРіСЂСѓР·РєРё С‚РµРєСЃС‚РѕРІ РїСЂРё РѕС‚РєСЂС‹С‚РёРё СЌРєСЂР°РЅР° РёР»Рё С‚Р°РїРµ РЅР° РїРёР»СЋР»СЋ В«РўРµРєСЃС‚В».
   - `LaunchedEffect(track?.id)` РІ `PlayerScreen` РіР°СЂР°РЅС‚РёСЂСѓРµС‚ РёРЅРёС†РёР°Р»РёР·Р°С†РёСЋ Р·Р°РіСЂСѓР·РєРё С‚РµРєСЃС‚Р°.
   - Р”РѕР±Р°РІР»РµРЅР° РєРЅРѕРїРєР° В«РџРѕРІС‚РѕСЂРёС‚СЊ РїРѕРёСЃРє С‚РµРєСЃС‚Р°В» РІ СЃРѕСЃС‚РѕСЏРЅРёРё РѕС€РёР±РєРё / РѕС‚СЃСѓС‚СЃС‚РІРёСЏ С‚РµРєСЃС‚Р°.

### Р¤Р°Р№Р»С‹
```
core/network/NetworkModule.kt
data/source/lyrics/LrclibApi.kt
data/source/lyrics/LyricsRepositoryImpl.kt
feature/player/PlayerViewModel.kt
feature/player/PlayerScreen.kt
```

---

## рџ“Љ РўРµРєСѓС‰РµРµ СЃРѕСЃС‚РѕСЏРЅРёРµ

### Р Р°Р±РѕС‚Р°РµС‚
- вњ… РЎР±РѕСЂРєР° РїСЂРѕРµРєС‚Р° (`BUILD SUCCESSFUL`)
- вњ… РќР°РІРёРіР°С†РёСЏ РјРµР¶РґСѓ РІРєР»Р°РґРєР°РјРё СЃ РїР»Р°РІРЅС‹РјРё Р°РЅРёРјР°С†РёСЏРјРё (200 РјСЃ)
- вњ… Material 3 С‚РµРјР° (Dynamic Color + fallback РїР°Р»РёС‚СЂР°)
- вњ… РџРѕРёСЃРє SoundCloud С‡РµСЂРµР· NewPipe Extractor (Р±РµР· client_id)
- вњ… РћС‚РѕР±СЂР°Р¶РµРЅРёРµ СЂРµР·СѓР»СЊС‚Р°С‚РѕРІ СЃ РїР»Р°РІРЅРѕР№ Р·Р°РіСЂСѓР·РєРѕР№ РѕР±Р»РѕР¶РµРє (Coil crossfade, error-РЅРѕС‚Р°, СЃРєРµР»РµС‚РѕРЅС‹)
- вњ… 4 СЃРѕСЃС‚РѕСЏРЅРёСЏ SearchScreen (РїСѓСЃС‚Рѕ / СЃРєРµР»РµС‚РѕРЅ-Р·Р°РіСЂСѓР·РєР° / РѕС€РёР±РєР° / СЃРїРёСЃРѕРє)
- вњ… РџР»Р°РІРЅС‹Р№ РїРµСЂРµС…РѕРґ РІ full-screen РїР»РµРµСЂ (slide-up + fade, 300 РјСЃ)
- вњ… Р РµР°Р»СЊРЅРѕРµ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёРµ С‚СЂРµРєРѕРІ (ExoPlayer/Media3 + HLS РІ С„РѕРЅРµ)
- вњ… MiniPlayer СЃ Р°РЅРёРјР°С†РёРµР№ slide + fade (250 РјСЃ) Рё РёР·РѕР»РёСЂРѕРІР°РЅРЅС‹Рј РїСЂРѕРіСЂРµСЃСЃ-Р±Р°СЂРѕРј
- вњ… РџР»Р°РІРЅС‹Р№ СЃР»Р°Р№РґРµСЂ РїР»РµРµСЂР° Р±РµР· РґС‘СЂРіР°РЅРёР№ Рё Р»РёС€РЅРёС… СЂРµРєРѕРјРїРѕР·РёС†РёР№
- вњ… Р”РёРЅР°РјРёС‡РµСЃРєРёР№ РіСЂР°РґРёРµРЅС‚РЅС‹Р№ С„РѕРЅ РїР»РµРµСЂР° РѕС‚ РѕР±Р»РѕР¶РєРё (Palette API СЃ РєСЌС€РёСЂРѕРІР°РЅРёРµРј)
- вњ… РџРµСЂРµС…РѕРґ В«РћР±Р»РѕР¶РєР° в†” РўРµРєСЃС‚В» С‡РµСЂРµР· AnimatedContent (spring slide + fade), РѕР±Р»РѕР¶РєР° 248dp СЃРѕ СЃРІРµС‡РµРЅРёРµРј Рё РїР°СЂР°Р»Р»Р°РєСЃРѕРј
- вњ… РќР°С‚РёРІРЅС‹Р№ PlayerSlider (Canvas, Р±СѓС„РµСЂ, РїСЂСѓР¶РёРЅРЅС‹Р№ РїРѕР»Р·СѓРЅРѕРє) РІРјРµСЃС‚Рѕ РІРЅРµС€РЅРµР№ Р±РёР±Р»РёРѕС‚РµРєРё WaveSlider
- вњ… РЎРєРІРёСЂРєР»-Р»Р°Р№Рє (18dp), РєСЂСѓРіР»С‹Рµ РєРЅРѕРїРєРё Prev/Next (56dp), Play 84dp СЃ РјРѕСЂС„РѕРј, РІСЂРµРјСЏ `"-m:ss"`
- вњ… РЎРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°РЅРЅС‹Рµ Рё РѕР±С‹С‡РЅС‹Рµ С‚РµРєСЃС‚С‹ РїРµСЃРµРЅ С‡РµСЂРµР· LRCLIB СЃРѕ СЃРІРµС‡РµРЅРёРµРј Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё Рё РїРµСЂРµРјРѕС‚РєРѕР№ РїРѕ С‚Р°РїСѓ
- вњ… РџРµСЂРµРєР»СЋС‡РµРЅРёРµ СЃРєРѕСЂРѕСЃС‚Рё РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёСЏ (0.5Г— вЂ“ 2.0Г—) С‡РµСЂРµР· ExoPlayer

### РќР• СЂР°Р±РѕС‚Р°РµС‚ (Р·Р°РіР»СѓС€РєРё)
- вќЊ РћС‡РµСЂРµРґСЊ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёСЏ (Next/Previous/Shuffle/Repeat)
- вќЊ В«РњРѕСЏ РІРѕР»РЅР°В» (Р°Р»РіРѕСЂРёС‚Рј РЅРµ СЂРµР°Р»РёР·РѕРІР°РЅ)
- вќЊ РџР»РµР№Р»РёСЃС‚С‹ Рё Р±РёР±Р»РёРѕС‚РµРєР° (Room РЅРµ РЅР°СЃС‚СЂРѕРµРЅ)
- вќЊ Р”СЂСѓРіРёРµ РёСЃС‚РѕС‡РЅРёРєРё (Spotify, РЇРЅРґРµРєСЃ, VK) вЂ” Р·Р°РїР»Р°РЅРёСЂРѕРІР°РЅС‹ РЅР° v2

---

## вњ… Р¤Р°Р·Р° 0 вЂ” Р РёСЃС‘СЂС‡, РґРёР·Р°Р№РЅ-СЃРїРµРєР° M3 Expressive + С‚РІРѕСЂС‡РµСЃРєР°СЏ СЃРІРѕР±РѕРґР° (16.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РР·СѓС‡РµРЅРёРµ Р°РєС‚СѓР°Р»СЊРЅРѕРіРѕ Material 3 Expressive**:
   - РџСЂРѕР°РЅР°Р»РёР·РёСЂРѕРІР°РЅС‹ РѕР±РЅРѕРІР»РµРЅРёСЏ M3 Expressive (2025вЂ“2026), РєРёРЅРµРјР°С‚РёРєР° РїСЂСѓР¶РёРЅ (Spring Physics), СЃРёСЃС‚РµРјР° СЃРєСЂСѓРіР»РµРЅРёР№ (Shape Scale) Рё СЃРµРјР°РЅС‚РёС‡РµСЃРєРёРµ СЂРѕР»Рё РєРѕРЅС‚РµР№РЅРµСЂРѕРІ (`surfaceContainerLowest` ... `surfaceContainerHighest`).
2. **Р”РёР·Р°Р№РЅ-СЃРїРµС†РёС„РёРєР°С†РёСЏ (`docs/design-spec.md`)**:
   - Р—Р°С„РёРєСЃРёСЂРѕРІР°РЅР° С†РµР»РѕСЃС‚РЅР°СЏ СЃРёСЃС‚РµРјР° РІРёР·СѓР°Р»СЊРЅРѕРіРѕ СЏР·С‹РєР° РґР»СЏ РІСЃРµС… СЌРєСЂР°РЅРѕРІ РїСЂРёР»РѕР¶РµРЅРёСЏ: Home, Search, Library, Wave, Player, MiniPlayer, QueueSheet, NavigationBar.
   - РћРїРёСЃР°РЅС‹ РїР°Р»РёС‚СЂС‹, С„РѕСЂРјС‹ (СЃРєРІРёСЂРєР»С‹ 18dp, СЂР°РґРёСѓСЃС‹ 28dp, СЃРµРіРјРµРЅС‚РёСЂРѕРІР°РЅРЅС‹Рµ РїРёР»СЋР»Рё 52dp), С‚РёРїРѕРіСЂР°С„РёС‡РµСЃРєР°СЏ С€РєР°Р»Р° Рё РїР°СЂР°РјРµС‚СЂС‹ С„РёР·РёРєРё Р°РЅРёРјР°С†РёР№ (РґР»РёС‚РµР»СЊРЅРѕСЃС‚Рё, РєСЂРёРІС‹Рµ `FastOutSlowInEasing`, РїСЂСѓР¶РёРЅС‹ СЃ `dampingRatio = 0.82f`).
3. **РўРІРѕСЂС‡РµСЃРєРёРµ СЂРµС€РµРЅРёСЏ СЃ РѕР±РѕСЃРЅРѕРІР°РЅРёРµРј**:
   - РќРѕРјРµСЂРЅС‹Рµ РїР»Р°РєР°С‚РЅС‹Рµ РєР°СЂС‚РѕС‡РєРё РґР»СЏ С‡Р°СЂС‚РѕРІ В«Top 50В» СЃ РїРѕР»СѓРїСЂРѕР·СЂР°С‡РЅС‹РјРё РїРѕР·РёС†РёСЏРјРё С‚СЂРµРєРѕРІ.
   - РЎРµРіРјРµРЅС‚РёСЂРѕРІР°РЅРЅС‹Р№ С‚Р°РєС‚РёР»СЊРЅС‹Р№ Р±Р°СЂ РїРёР»СЋР»СЊ РІ РїР»РµРµСЂРµ РїРѕ СЂРµС„РµСЂРµРЅСЃСѓ `exteraless`.
   - Р’С‹СЂР°Р·РёС‚РµР»СЊРЅС‹Р№ СЃРєРІРёСЂРєР» Р»Р°Р№РєР° (18dp) СЃ РґРёРЅР°РјРёС‡РµСЃРєРёРј Р°РєС†РµРЅС‚РЅС‹Рј Р·Р°РїРѕР»РЅРµРЅРёРµРј.
   - Р”РёРЅР°РјРёС‡РµСЃРєРёР№ РіСЂР°РґРёРµРЅС‚ С„РѕРЅР° РїР»РµРµСЂР° РѕС‚ РѕР±Р»РѕР¶РєРё (Palette API).
   - Р–РёРІР°СЏ Canvas-СЃРёРЅСѓСЃРѕРёРґР° РґР»СЏ С‚РёР·РµСЂР° В«РњРѕСЏ РІРѕР»РЅР°В» РІРјРµСЃС‚Рѕ СЃС‚Р°С‚РёС‡РЅРѕР№ Р·Р°РіР»СѓС€РєРё.
4. **РђСѓРґРёС‚ Р·Р°РІРёСЃРёРјРѕСЃС‚РµР№**:
   - РџРѕРґС‚РІРµСЂР¶РґРµРЅР° 100% СЃРѕРІРјРµСЃС‚РёРјРѕСЃС‚СЊ С‚СЂРµР±РѕРІР°РЅРёР№ Expressive-СЃРїРµС†РёС„РёРєР°С†РёРё СЃ С‚РµРєСѓС‰РёРј Compose BOM 2024.11.00 Р±РµР· РёР·РјРµРЅРµРЅРёСЏ РІРµСЂСЃРёР№ Р±РёР±Р»РёРѕС‚РµРє (СЃРѕС…СЂР°РЅРµРЅРёРµ СЃС‚Р°Р±РёР»СЊРЅРѕСЃС‚Рё СЃР±РѕСЂРєРё РЅР° AGP 8.5.2 Рё Kotlin 2.0.21).

### Р¤Р°Р№Р»С‹
```
docs/design-spec.md
```

---

## вњ… Р¤Р°Р·Р° 1 вЂ” Room: Р»Р°Р№РєРё Рё РёСЃС‚РѕСЂРёСЏ (16.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Р‘Р°Р·Р° РґР°РЅРЅС‹С… Room 2.6.1 (`AppDatabase.kt`)**:
   - РЎРѕР·РґР°РЅР° Р±Р°Р·Р° РґР°РЅРЅС‹С… `facet.db` СЃ fallback-РјРёРіСЂР°С†РёРµР№.
   - РўР°Р±Р»РёС†Р° `liked_tracks` (`LikedTrackEntity`): СЃРѕС…СЂР°РЅРµРЅРёРµ ID, РёСЃС‚РѕС‡РЅРёРєР°, РЅР°Р·РІР°РЅРёСЏ, Р°СЂС‚РёСЃС‚Р°, РѕР±Р»РѕР¶РєРё, РґР»РёС‚РµР»СЊРЅРѕСЃС‚Рё, Р¶Р°РЅСЂР°, URL С‚СЂР°РЅСЃРєРѕРґРёРЅРіР° Рё timestamp Р»Р°Р№РєР° `likedAt`.
   - РўР°Р±Р»РёС†Р° `play_history` (`PlayHistoryEntity`): СЃРѕС…СЂР°РЅРµРЅРёРµ РёСЃС‚РѕСЂРёРё РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёСЏ СЃ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРёРј С‚СЂРёРјРјРёРЅРіРѕРј РґРѕ РїРѕСЃР»РµРґРЅРёС… 50 Р·Р°РїРёСЃРµР№ С‡РµСЂРµР· `@Transaction` РІ `PlayHistoryDao`.
2. **РЎР»РѕР№ РґРѕСЃС‚СѓРїР° Рє РґР°РЅРЅС‹Рј (DAO)**:
   - `LikedTracksDao`: СЂРµР°РєС‚РёРІРЅС‹Р№ РїРѕС‚РѕРє `getLikedTracks()`, РїСЂРѕРІРµСЂРєР° `isLiked(id): Flow<Boolean>`, РІСЃС‚Р°РІРєР° Рё СѓРґР°Р»РµРЅРёРµ.
   - `PlayHistoryDao`: СЂРµР°РєС‚РёРІРЅС‹Р№ РїРѕС‚РѕРє `getPlayHistory(limit = 50)`, РјРµС‚РѕРґ `recordPlay` (РІСЃС‚Р°РІРєР° + РѕС‡РёСЃС‚РєР° СЃС‚Р°СЂС‹С… Р·Р°РїРёСЃРµР№ СЃРІРµСЂС… Р»РёРјРёС‚Р° 50).
3. **Р РµРїРѕР·РёС‚РѕСЂРёРё Рё DI (`data/local/`, `core/di/`)**:
   - РРЅС‚РµСЂС„РµР№СЃС‹: `LikedTracksRepository` Рё `PlayHistoryRepository` РІ `data/repository/`.
   - Р РµР°Р»РёР·Р°С†РёРё: `LikedTracksRepositoryImpl` Рё `PlayHistoryRepositoryImpl` СЃ РјР°РїРїРёРЅРіРѕРј `LikedTrackEntity`/`PlayHistoryEntity` $\leftrightarrow$ РґРѕРјРµРЅРЅР°СЏ РјРѕРґРµР»СЊ `Track`.
   - Hilt-РјРѕРґСѓР»Рё: `DatabaseModule` (`@Provides` РґР»СЏ Р‘Р” Рё DAO) Рё РѕР±РЅРѕРІР»С‘РЅРЅС‹Р№ `RepositoryModule` (`@Binds` РґР»СЏ СЂРµРїРѕР·РёС‚РѕСЂРёРµРІ).
4. **РЎРІСЏР·РєР° СЃ РїР»РµРµСЂРѕРј**:
   - `PlayerViewModel`: СЂРµР°РєС‚РёРІРЅРѕРµ СЃРІРѕР№СЃС‚РІРѕ `isLiked: StateFlow<Boolean>`, РѕС‚СЃР»РµР¶РёРІР°СЋС‰РµРµ СЃС‚Р°С‚СѓСЃ С‚РµРєСѓС‰РµРіРѕ С‚СЂРµРєР° РІ Р‘Р” С‡РµСЂРµР· `flatMapLatest`; РјРµС‚РѕРґ `toggleLike()` РґР»СЏ РґРѕР±Р°РІР»РµРЅРёСЏ/СѓРґР°Р»РµРЅРёСЏ С‚СЂРµРєР° РІ Room; Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєР°СЏ Р·Р°РїРёСЃСЊ С‚СЂРµРєР° РІ `play_history` РїСЂРё СЃС‚Р°СЂС‚Рµ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёСЏ (`loadAndPlay`).
   - `PlayerScreen`: РєРЅРѕРїРєР°-СЃРєРІРёСЂРєР» Р»Р°Р№РєР° РїРѕРґРєР»СЋС‡РµРЅР° Рє `viewModel.isLiked` Рё `viewModel.toggleLike()`.
5. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
data/local/entity/LikedTrackEntity.kt
data/local/entity/PlayHistoryEntity.kt
data/local/dao/LikedTracksDao.kt
data/local/dao/PlayHistoryDao.kt
data/local/AppDatabase.kt
data/local/DatabaseModule.kt
data/repository/LikedTracksRepository.kt
data/local/LikedTracksRepositoryImpl.kt
data/repository/PlayHistoryRepository.kt
data/local/PlayHistoryRepositoryImpl.kt
core/di/RepositoryModule.kt
feature/player/PlayerViewModel.kt
feature/player/PlayerScreen.kt
```

---

## вњ… Р¤Р°Р·Р° 2 вЂ” РћС‡РµСЂРµРґСЊ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёСЏ (17.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РЈРїСЂР°РІР»РµРЅРёРµ РѕС‡РµСЂРµРґСЊСЋ РІ PlayerController**:
   - РџРѕР»РЅРѕС†РµРЅРЅР°СЏ РѕС‡РµСЂРµРґСЊ `List<Track>` СЃ РѕС‚СЃР»РµР¶РёРІР°РЅРёРµРј С‚РµРєСѓС‰РµРіРѕ РёРЅРґРµРєСЃР° `currentIndex`.
   - Р РµР¶РёРјС‹ РїРѕРІС‚РѕСЂР° `repeatMode` (`OFF` $\to$ `ALL` $\to$ `ONE` $\to$ `OFF`) Рё РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёСЏ РїРѕСЃР»Рµ РѕРєРѕРЅС‡Р°РЅРёСЏ С‚СЂРµРєР° (`onTrackEnded`).
   - Р РµР¶РёРј РїРµСЂРµРјРµС€РёРІР°РЅРёСЏ `isShuffle` СЃ СЃРѕС…СЂР°РЅРµРЅРёРµРј `originalQueue` РґР»СЏ РєРѕСЂСЂРµРєС‚РЅРѕРіРѕ РІРѕСЃСЃС‚Р°РЅРѕРІР»РµРЅРёСЏ РёСЃС…РѕРґРЅРѕРіРѕ РїРѕСЂСЏРґРєР° С‚СЂРµРєРѕРІ РїСЂРё РІС‹РєР»СЋС‡РµРЅРёРё.
   - РњРµС‚РѕРґС‹ РїРµСЂРµС…РѕРґР°: `playNext(loop)` Рё `playPrevious()` СЃ Р»РѕРіРёРєРѕР№ РІРѕР·РІСЂР°С‚Р° РІ РЅР°С‡Р°Р»Рѕ С‚СЂРµРєР°, РµСЃР»Рё РїСЂРѕС€Р»Рѕ Р±РѕР»РµРµ 3 СЃРµРєСѓРЅРґ.
   - РњРµС‚РѕРґ СѓРґР°Р»РµРЅРёСЏ С‚СЂРµРєР° `removeTrackAt(index)` СЃ Р°РІС‚РѕРєРѕСЂСЂРµРєС†РёРµР№ С‚РµРєСѓС‰РµРіРѕ РёРЅРґРµРєСЃР° Рё РѕСЃС‚Р°РЅРѕРІРєРѕР№ РїР»РµРµСЂР° РїСЂРё РѕРїСѓСЃС‚РѕС€РµРЅРёРё РѕС‡РµСЂРµРґРё.
2. **РРЅС‚РµРіСЂР°С†РёСЏ СЃ СЃРёСЃС‚РµРјРЅС‹Рј СЃРµСЂРІРёСЃРѕРј Рё СѓРІРµРґРѕРјР»РµРЅРёРµРј (MusicService)**:
   - Р’ `ForwardingPlayer` РїРµСЂРµРѕРїСЂРµРґРµР»РµРЅС‹ Рё РѕР±СЉСЏРІР»РµРЅС‹ РґРѕСЃС‚СѓРїРЅС‹РјРё РєРѕРјР°РЅРґС‹ `SEEK_TO_NEXT`, `SEEK_TO_NEXT_MEDIA_ITEM`, `SEEK_TO_PREVIOUS`, `SEEK_TO_PREVIOUS_MEDIA_ITEM`.
   - РљРЅРѕРїРєРё РїРµСЂРµРєР»СЋС‡РµРЅРёСЏ С‚СЂРµРєРѕРІ РІ С€С‚РѕСЂРєРµ СѓРІРµРґРѕРјР»РµРЅРёР№ Рё РЅР° Р·Р°Р±Р»РѕРєРёСЂРѕРІР°РЅРЅРѕРј СЌРєСЂР°РЅРµ РІС‹Р·С‹РІР°СЋС‚ РјРµС‚РѕРґС‹ РѕС‡РµСЂРµРґРё `playerController.playNext()` Рё `playerController.playPrevious()`.
3. **РЁС‚РѕСЂРєР° РѕС‡РµСЂРµРґРё РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёСЏ (PlayerQueueSheet)**:
   - РњРѕРґР°Р»СЊРЅР°СЏ С€С‚РѕСЂРєР° `ModalBottomSheet` РІС‹СЃРѕС‚РѕР№ 78% СЌРєСЂР°РЅР° СЃРѕ СЃРєСЂСѓРіР»РµРЅРёРµРј РІРµСЂС…РЅРёС… СѓРіР»РѕРІ 28dp Рё drag handle 32Г—4 dp.
   - Р—Р°РіРѕР»РѕРІРѕРє В«РћС‡РµСЂРµРґСЊВ» СЃ РєРѕР»РёС‡РµСЃС‚РІРѕРј С‚СЂРµРєРѕРІ Рё РєРЅРѕРїРєР°РјРё Р±С‹СЃС‚СЂРѕРіРѕ РІРєР»СЋС‡РµРЅРёСЏ/РІС‹РєР»СЋС‡РµРЅРёСЏ Shuffle Рё Repeat.
   - РЎРїРёСЃРѕРє С‚СЂРµРєРѕРІ СЃ Р°РІС‚РѕСЃРєСЂРѕР»Р»РѕРј Рє Р°РєС‚РёРІРЅРѕРјСѓ СЌР»РµРјРµРЅС‚Сѓ РїСЂРё РѕС‚РєСЂС‹С‚РёРё.
   - РђРєС‚РёРІРЅС‹Р№ С‚СЂРµРє РІРёР·СѓР°Р»СЊРЅРѕ РІС‹РґРµР»РµРЅ С‚РѕРЅРёСЂРѕРІР°РЅРЅРѕР№ РїР»Р°С€РєРѕР№ `primaryContainer` Рё РёРєРѕРЅРєРѕР№ Play.
   - Р”РІСѓСЃС‚РѕСЂРѕРЅРЅРёР№ СЃРІР°Р№Рї РІР»РµРІРѕ Рё РІРїСЂР°РІРѕ РґР»СЏ Р±С‹СЃС‚СЂРѕРіРѕ СѓРґР°Р»РµРЅРёСЏ С‚СЂРµРєР° РёР· РѕС‡РµСЂРµРґРё (`SwipeToDismissBox`) СЃ Р°РЅРёРјР°С†РёРµР№ РєСЂР°СЃРЅРѕР№ РїРѕРґР»РѕР¶РєРё Рё РєРѕСЂР·РёРЅС‹.
4. **РЎРІСЏР·РєР° СЃ UI РїР»РµРµСЂР° Рё РїРѕРёСЃРєРѕРј**:
   - РџРёР»СЋР»СЏ В«Р”Р°Р»РµРµВ» РІ СЃРµРіРјРµРЅС‚РёСЂРѕРІР°РЅРЅРѕРј Р±Р°СЂРµ `PlayerScreen` РѕС‚РєСЂС‹РІР°РµС‚ `PlayerQueueSheet`.
   - РџСЂРё РІС‹Р±РѕСЂРµ С‚СЂРµРєР° РІ СЂРµР·СѓР»СЊС‚Р°С‚Р°С… РїРѕРёСЃРєР° (`SearchScreen`) РІ РїР»РµРµСЂ РїРµСЂРµРґР°С‘С‚СЃСЏ РїРѕР»РЅС‹Р№ СЃРїРёСЃРѕРє РЅР°Р№РґРµРЅРЅС‹С… С‚СЂРµРєРѕРІ РєР°Рє РєРѕРЅС‚РµРєСЃС‚РЅР°СЏ РѕС‡РµСЂРµРґСЊ.
   - РљРЅРѕРїРєРё В«PrevВ» Рё В«NextВ» РІ `PlayerScreen` РІС‹Р·С‹РІР°СЋС‚ `viewModel.playPrevious()` Рё `viewModel.playNext()`.
5. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
core/player/RepeatMode.kt
core/player/PlayerController.kt
core/player/MusicService.kt
core/ui/components/PlayerQueueSheet.kt
feature/player/PlayerViewModel.kt
feature/player/PlayerScreen.kt
feature/search/SearchScreen.kt
navigation/AppNavGraph.kt
```

---

## вњ… Р¤Р°Р·Р° 2.5 вЂ” РҐРѕС‚С„РёРєСЃ РїР»РµРµСЂР° (18.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РџРѕС‡РёРЅРµРЅР° РєРЅРѕРїРєР° Play/Pause РІ СЂР°Р·РІРµСЂРЅСѓС‚РѕРј РїР»РµРµСЂРµ (`PlayerScreen.kt`)**:
   - РЈСЃС‚СЂР°РЅРµРЅРѕ Р±Р»РѕРєРёСЂСѓСЋС‰РµРµ СѓСЃР»РѕРІРёРµ `enabled = !isResolving`, РёР·-Р·Р° РєРѕС‚РѕСЂРѕРіРѕ РєРЅРѕРїРєР° Р·Р°РІРёСЃР°Р»Р° РІ РѕС‚РєР»СЋС‡РµРЅРЅРѕРј СЃРѕСЃС‚РѕСЏРЅРёРё РїРѕСЃР»Рµ Р·Р°РіСЂСѓР·РєРё С‚СЂРµРєР°.
   - РљРЅРѕРїРєР° 88dp РєР»РёРєР°Р±РµР»СЊРЅР° РІСЃРµРіРґР° Рё РїРµСЂРµРєР»СЋС‡Р°РµС‚ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёРµ С‡РµСЂРµР· `viewModel.togglePlayPause()`.
   - РРєРѕРЅРєР° РєРѕСЂСЂРµРєС‚РЅРѕ СЃРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°РЅР° СЃ СЃРѕСЃС‚РѕСЏРЅРёРµРј `isPlaying` (`PlayArrow` / `Pause`).
2. **РњРіРЅРѕРІРµРЅРЅС‹Р№ СЃР±СЂРѕСЃ РїРѕР·РёС†РёРё РїСЂРё СЃРјРµРЅРµ С‚СЂРµРєР°**:
   - Р’ `PlayerViewModel`: РїСЂРё СЃРјРµРЅРµ С‚СЂРµРєР° С‡РµСЂРµР· `loadAndPlay` РёР»Рё РѕС‡РµСЂРµРґСЊ UI-СЃРѕСЃС‚РѕСЏРЅРёРµ РїРѕР·РёС†РёРё РЅРµРјРµРґР»РµРЅРЅРѕ РѕР±РЅСѓР»СЏРµС‚СЃСЏ (`positionMs = 0L`).
   - Р’ `PlayerProgressSection`: РїСЂРё РёР·РјРµРЅРµРЅРёРё `trackId` Р·РЅР°С‡РµРЅРёСЏ СЃР»Р°Р№РґРµСЂР° (`playbackFraction`, `playbackPositionMs`, `dragPosition`) РјРіРЅРѕРІРµРЅРЅРѕ СЃР±СЂР°СЃС‹РІР°СЋС‚СЃСЏ РІ 0, РёСЃРєР»СЋС‡Р°СЏ Р·Р°РІРёСЃР°РЅРёРµ СЃС‚Р°СЂРѕРіРѕ С‚Р°Р№РјРєРѕРґР°.
   - Р’ `PlayerController`: РїРµСЂРµРґ РІС‹Р·РѕРІРѕРј `prepare()` РїСЂРё Р·Р°РїСѓСЃРєРµ С‚СЂРµРєР° СЏРІРЅРѕ РІС‹Р·С‹РІР°РµС‚СЃСЏ `seekTo(0)`.
3. **РџРѕС‡РёРЅРµРЅР° Р·Р°РІРёСЃР°СЋС‰Р°СЏ РїРѕР»РѕСЃРєР° Р±СѓС„РµСЂРёР·Р°С†РёРё**:
   - Р’ `PlayerController` Рё `PlayerViewModel` СЂРµР°Р»РёР·РѕРІР°РЅ РѕС‚РґРµР»СЊРЅС‹Р№ РїРѕС‚РѕРє `isBuffering: StateFlow<Boolean>`, РѕС‚СЃР»РµР¶РёРІР°СЋС‰РёР№ СЃРѕСЃС‚РѕСЏРЅРёРµ `Player.STATE_BUFFERING`.
   - Р’ `PlayerScreen` СЃРїРёРЅРЅРµСЂ РІ С†РµРЅС‚СЂР°Р»СЊРЅРѕР№ РєРЅРѕРїРєРµ РєСЂСѓС‚РёС‚СЃСЏ РўРћР›Р¬РљРћ РєРѕРіРґР° `isBuffering == true`.
   - РЎС‚Р°СЂС‹Р№ `LinearProgressIndicator` РїРѕР»РЅРѕСЃС‚СЊСЋ СѓРґР°Р»С‘РЅ РёР· СЂР°Р·РјРµС‚РєРё.
4. **Р‘РµСЃС€РѕРІРЅС‹Р№ РїРµСЂРµС…РѕРґ Р±СѓС„РµСЂРёР·Р°С†РёРё РІ С‚Р°Р№РјР»Р°Р№РЅ (РєР°СЃС‚РѕРјРЅС‹Р№ M3 Slider)**:
   - Р’ `PlayerProgressSection` РїСЂРёРјРµРЅС‘РЅ РєР°СЃС‚РѕРјРЅС‹Р№ РїР°СЂР°РјРµС‚СЂ `track` Сѓ `Slider` РёР· Material 3:
     1. Р‘Р°Р·РѕРІС‹Р№ РЅРµР°РєС‚РёРІРЅС‹Р№ СЃР»РѕР№ (РѕР±С‰Р°СЏ РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊ) С†РІРµС‚Р° `surfaceVariant`.
     2. РџРѕР»СѓРїСЂРѕР·СЂР°С‡РЅС‹Р№ СЃР»РѕР№ Р±СѓС„РµСЂРёР·Р°С†РёРё `primary.copy(alpha = 0.38f)` РїСЂРѕРїРѕСЂС†РёРѕРЅР°Р»СЊРЅРѕ `bufferedPosition / duration`.
     3. РђРєС‚РёРІРЅС‹Р№ СЃР»РѕР№ РїСЂРѕРіСЂРµСЃСЃР° РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёСЏ Р°РєС†РµРЅС‚РЅРѕРіРѕ С†РІРµС‚Р° `primary`.
5. **РћРїС‚РёРјРёР·Р°С†РёСЏ СЃРµС‚РµРІС‹С… Р·Р°РїСЂРѕСЃРѕРІ Рё С‚Р°Р№РјР°СѓС‚РѕРІ (`NetworkModule.kt`)**:
   - Р’ `OkHttpClient` С‚Р°Р№РјР°СѓС‚С‹ РїРѕРґРєР»СЋС‡РµРЅРёСЏ, С‡С‚РµРЅРёСЏ Рё Р·Р°РїРёСЃРё СѓСЃС‚Р°РЅРѕРІР»РµРЅС‹ РІ 10 СЃРµРєСѓРЅРґ, РѕР±С‰РёР№ `callTimeout` РІ 15 СЃРµРєСѓРЅРґ, С‡С‚Рѕ РїСЂРµРґРѕС‚РІСЂР°С‰Р°РµС‚ Р±Р»РѕРєРёСЂРѕРІРєСѓ РёРЅС‚РµСЂС„РµР№СЃР° РїСЂРё РЅРµСЃС‚Р°Р±РёР»СЊРЅРѕР№ СЃРµС‚Рё.
6. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
core/network/NetworkModule.kt
core/player/PlayerController.kt
feature/player/PlayerViewModel.kt
feature/player/PlayerScreen.kt
```

---

## вњ… Р¤Р°Р·Р° 2.6 вЂ” РџРѕР»РёСЂРѕРІРєР° РїРµСЂРµРєР»СЋС‡РµРЅРёСЏ С‚СЂРµРєРѕРІ Рё СЃР»Р°Р№РґРµСЂР° (18.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РњРіРЅРѕРІРµРЅРЅРѕРµ РіР»СѓС€РµРЅРёРµ РїСЂРѕС€Р»РѕРіРѕ С‚СЂРµРєР° РїСЂРё СЃРјРµРЅРµ (`PlayerController.kt`)**:
   - Р’ РЅР°С‡Р°Р»Рµ РјРµС‚РѕРґРѕРІ `playTrackAt`, `resolveAndPlay` Рё `play` РІС‹Р·С‹РІР°РµС‚СЃСЏ РЅРµРјРµРґР»РµРЅРЅР°СЏ РѕСЃС‚Р°РЅРѕРІРєР°: `exoPlayer.pause()`, `exoPlayer.stop()`, `exoPlayer.seekTo(0)`.
   - РџСЂРѕС€Р»С‹Р№ С‚СЂРµРє Р·Р°РјРѕР»РєР°РµС‚ СЃСЂР°Р·Сѓ РІ РјРѕРјРµРЅС‚ РЅР°Р¶Р°С‚РёСЏ, РґРѕ РЅР°С‡Р°Р»Р° СЃРµС‚РµРІРѕРіРѕ Р·Р°РїСЂРѕСЃР° `resolveStreamUrl`, РёСЃРєР»СЋС‡Р°СЏ СЌС„С„РµРєС‚ "С…РІРѕСЃС‚Р°" РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёСЏ.
2. **РџР»Р°РІРЅС‹Р№ РїРµСЂРµС…РѕРґ В«Р—Р°РіСЂСѓР·РєР° $\to$ РўР°Р№РјР»Р°Р№РЅВ» (`PlayerScreen.kt`)**:
   - Р’ `PlayerProgressSection` РІРЅРµРґСЂС‘РЅ РєРѕРЅС‚РµР№РЅРµСЂ С„РёРєСЃРёСЂРѕРІР°РЅРЅРѕР№ РІС‹СЃРѕС‚С‹ `44.dp` СЃ РІС‹СЂР°РІРЅРёРІР°РЅРёРµРј РїРѕ С†РµРЅС‚СЂСѓ (`Alignment.Center`), С‡С‚Рѕ РіР°СЂР°РЅС‚РёСЂСѓРµС‚ СЃС‚Р°Р±РёР»СЊРЅРѕСЃС‚СЊ РїРѕР»РѕР¶РµРЅРёСЏ С‚Р°Р№РјРєРѕРґРѕРІ Р±РµР· РїСЂС‹Р¶РєРѕРІ UI.
   - РџРµСЂРµРєР»СЋС‡РµРЅРёРµ РјРµР¶РґСѓ С‚РѕРЅРєРёРј РёРЅРґРёРєР°С‚РѕСЂРѕРј Р·Р°РіСЂСѓР·РєРё (`LinearProgressIndicator` 4dp) Рё РѕСЃРЅРѕРІРЅС‹Рј СЃР»Р°Р№РґРµСЂРѕРј (`Slider` СЃ Р±СѓС„РµСЂРёР·Р°С†РёРµР№) Р°РЅРёРјРёСЂРѕРІР°РЅРѕ С‡РµСЂРµР· `Crossfade` (300 РјСЃ, `FastOutSlowInEasing`).
   - РЈСЃР»РѕРІРёРµ РїРѕРєР°Р·Р° РїРѕР»РѕСЃРєРё Р·Р°РіСЂСѓР·РєРё: `isBuffering && displayPosition <= 0.005f`.
3. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
core/player/PlayerController.kt
feature/player/PlayerScreen.kt
```

---

## вњ… Р¤Р°Р·Р° 2.7 вЂ” РҐРѕС‚С„РёРєСЃ UI Рё СЃРѕСЃС‚РѕСЏРЅРёР№ РїР»РµРµСЂР° (18.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РЎР±СЂРѕСЃ РѕС€РёР±РѕРє РїСЂРё СЃРјРµРЅРµ С‚СЂРµРєР° Рё РїРµСЂРµС…РѕРґРµ РІ STATE_READY**:
   - Р’ `PlayerController.kt` РґРѕР±Р°РІР»РµРЅРѕ РїРѕР»Рµ `playbackState` РІ `PlayerState`, Р° РїСЂРё РїРµСЂРµС…РѕРґРµ РІ `Player.STATE_READY` РѕС€РёР±РєР° Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё СЃР±СЂР°СЃС‹РІР°РµС‚СЃСЏ (`error = null`).
   - Р’ `PlayerViewModel.kt` РїСЂРё СЃРјРµРЅРµ С‚СЂРµРєР° СЃРѕСЃС‚РѕСЏРЅРёРµ РѕС€РёР±РѕРє РЅРµРјРµРґР»РµРЅРЅРѕ РѕС‡РёС‰Р°РµС‚СЃСЏ, Р° С‚Р°РєР¶Рµ СЃРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµС‚СЃСЏ СЃ РїРµСЂРµС…РѕРґРѕРј РІ `STATE_READY`.
   - Р’ `PlayerScreen.kt` РїР»Р°С€РєР° СЃ РѕС€РёР±РєРѕР№ РіР°СЂР°РЅС‚РёСЂРѕРІР°РЅРЅРѕ СЃРєСЂС‹РІР°РµС‚СЃСЏ, РєР°Рє С‚РѕР»СЊРєРѕ РїР»РµРµСЂ РїРµСЂРµС…РѕРґРёС‚ РІ СЃС‚Р°С‚СѓСЃ `Player.STATE_READY`.
2. **РџРѕС‡РёРЅРµРЅР° РїР°Р»РёС‚СЂР° РѕР±Р»РѕР¶РєРё (Palette)**:
   - Р’ `PlayerPalette.kt` РІРЅРµРґСЂРµРЅРѕ СЏРІРЅРѕРµ СЃРѕСЃС‚РѕСЏРЅРёРµ Р·Р°РіСЂСѓР·РєРё `isLoading`: РїРѕРєР° РѕР±Р»РѕР¶РєР° Р·Р°РіСЂСѓР¶Р°РµС‚СЃСЏ вЂ” РѕС‚РѕР±СЂР°Р¶Р°РµС‚СЃСЏ РґРµС„РѕР»С‚РЅС‹Р№ С„РѕРЅ; РєР°Рє С‚РѕР»СЊРєРѕ РѕР±Р»РѕР¶РєР° Р·Р°РіСЂСѓР·РёР»Р°СЃСЊ вЂ” РїСЂРёРјРµРЅСЏРµС‚СЃСЏ РґРѕРјРёРЅР°РЅС‚РЅС‹Р№ С†РІРµС‚.
   - Р”РѕР±Р°РІР»РµРЅР° СѓСЃС‚РѕР№С‡РёРІР°СЏ РѕР±СЂР°Р±РѕС‚РєР° РѕС€РёР±РѕРє (РїСЂРё СЃРµС‚РµРІС‹С… СЃР±РѕСЏС… РёР»Рё РЅРµРІРѕР·РјРѕР¶РЅРѕСЃС‚Рё РёР·РІР»РµС‡СЊ РїР°Р»РёС‚СЂСѓ РѕСЃС‚Р°С‘С‚СЃСЏ РґРµС„РѕР»С‚РЅС‹Р№ С„РѕРЅ).
   - Р”Р»СЏ СЃРІРµС‚Р»С‹С… Рё РІС‹СЃРѕРєРѕРєРѕРЅС‚СЂР°СЃС‚РЅС‹С… РѕР±Р»РѕР¶РµРє СѓРІРµР»РёС‡РµРЅС‹ РІРµСЃР° Р°Р»СЊС„С‹ (`0.72f` РІРІРµСЂС…Сѓ, `0.28f` РІРЅРёР·Сѓ), РїСЂРµРґРѕС‚РІСЂР°С‰Р°СЏ РїРѕРіР»РѕС‰РµРЅРёРµ С†РІРµС‚Р° С‚С‘РјРЅС‹Рј СЃРёСЃС‚РµРјРЅС‹Рј С„РѕРЅРѕРј `surface`.
   - Р’ `PlayerScreen.kt` РїРµСЂРµС…РѕРґС‹ РіСЂР°РґРёРµРЅС‚Р° С„РѕРЅР° Р°РЅРёРјРёСЂРѕРІР°РЅС‹ С‡РµСЂРµР· `animateColorAsState` (400 РјСЃ).
3. **РЎС‚Р°РЅРґР°СЂС‚РЅС‹Р№ Slider Рё РїР»Р°РІРЅС‹Р№ Crossfade (500 РјСЃ)**:
   - Р’ `PlayerProgressSection` СѓРґР°Р»С‘РЅ РєР°СЃС‚РѕРјРЅС‹Р№ Canvas-С‚СЂРµРє Рё РІРѕР·РІСЂР°С‰С‘РЅ СЃС‚Р°РЅРґР°СЂС‚РЅС‹Р№ РєРѕРјРїРѕРЅРµРЅС‚ `Slider`.
   - Р РµР°Р»РёР·РѕРІР°РЅР° Р±СѓС„РµСЂРёР·Р°С†РёСЏ РїРѕРґ СЃР»Р°Р№РґРµСЂРѕРј С‡РµСЂРµР· `LinearProgressIndicator` (С„РѕРЅ `surfaceVariant`, РїСЂРѕРіСЂРµСЃСЃ `primary`, Р±СѓС„РµСЂРёР·Р°С†РёСЏ `primary.copy(alpha = 0.3f)`).
   - РџРµСЂРµС…РѕРґ В«Р·Р°РіСЂСѓР·РєР° $\to$ С‚Р°Р№РјР»Р°Р№РЅВ» РІС‹РїРѕР»РЅРµРЅ С‡РµСЂРµР· `Crossfade(targetState = isBuffering && currentPosition == 0L)` СЃ РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊСЋ Р°РЅРёРјР°С†РёРё 500 РјСЃ (`FastOutSlowInEasing`).
4. **РћР±СЂРµР·РєР° С‚РµРєСЃС‚Р°**:
   - Р’ Р·Р°РіРѕР»РѕРІРєРµ С‚СЂРµРєР° СѓСЃС‚Р°РЅРѕРІР»РµРЅРѕ `maxLines = 2` СЃ `TextOverflow.Ellipsis`.
5. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
core/player/PlayerController.kt
feature/player/PlayerViewModel.kt
core/ui/theme/PlayerPalette.kt
feature/player/PlayerScreen.kt
```

---

## вњ… Р¤Р°Р·Р° 3 вЂ” Р“Р»Р°РІРЅС‹Р№ СЌРєСЂР°РЅ СЃ РїРѕРґР±РѕСЂРєР°РјРё Рё РњРµРґРёР°С‚РµРєР° (19.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РЎР»РѕР№ РґР°РЅРЅС‹С… С‡Р°СЂС‚РѕРІ SoundCloud Рё РєСЌС€ Room**:
   - РЎРѕР·РґР°РЅР° СЃСѓС‰РЅРѕСЃС‚СЊ `ChartCacheEntity` Рё DAO `ChartCacheDao` СЃ СЃРѕСЃС‚Р°РІРЅС‹Рј РїРµСЂРІРёС‡РЅС‹Рј РєР»СЋС‡РѕРј `(chartType, id)` Рё С‚СЂР°РЅР·Р°РєС†РёРѕРЅРЅРѕР№ Р·Р°РјРµРЅРѕР№ `replaceChart`.
   - Р‘Р°Р·Р° РґР°РЅРЅС‹С… `AppDatabase` РѕР±РЅРѕРІР»РµРЅР° РґРѕ РІРµСЂСЃРёРё 2 СЃ СЂРµРіРёСЃС‚СЂР°С†РёРµР№ `ChartCacheEntity` Рё `chartCacheDao()`.
   - РЎРѕР·РґР°РЅ РёРЅС‚РµСЂС„РµР№СЃ `SoundCloudChartsRepository` Рё СЂРµР°Р»РёР·Р°С†РёСЏ `SoundCloudChartsRepositoryImpl` С‡РµСЂРµР· NewPipe-РєРёРѕСЃРєРё (`https://soundcloud.com/charts/new` РґР»СЏ "New & hot" Рё `/top` РґР»СЏ "Top 50").
   - Р’РЅРµРґСЂС‘РЅ 30-РјРёРЅСѓС‚РЅС‹Р№ TTL РєСЌС€РёСЂРѕРІР°РЅРёСЏ: СЃРІРµР¶РёР№ РєСЌС€ РѕС‚РґР°С‘С‚СЃСЏ РјРіРЅРѕРІРµРЅРЅРѕ РѕС„Р»Р°Р№РЅ; РїСЂРё СѓСЃС‚Р°СЂРµРІР°РЅРёРё РёР»Рё pull-to-refresh РїСЂРѕРёСЃС…РѕРґРёС‚ СЃРµС‚РµРІРѕР№ Р·Р°РїСЂРѕСЃ Рє NewPipe СЃ СЃРѕС…СЂР°РЅРµРЅРёРµРј РІ Room; РїСЂРё СЃР±РѕРµ СЃРµС‚Рё СЂР°Р±РѕС‚Р°РµС‚ offline-first С„РѕР»Р±СЌРє РЅР° РєСЌС€.
   - Hilt-РјРѕРґСѓР»Рё `DatabaseModule` Рё `RepositoryModule` РѕР±РЅРѕРІР»РµРЅС‹ РґР»СЏ РїСЂРµРґРѕСЃС‚Р°РІР»РµРЅРёСЏ `ChartCacheDao` Рё `SoundCloudChartsRepository`.
2. **HomeViewModel**:
   - РќРµРїСЂРµСЂС‹РІРЅРѕРµ РЅР°Р±Р»СЋРґРµРЅРёРµ Р·Р° СЂРµР°РєС‚РёРІРЅС‹РјРё РїРѕС‚РѕРєР°РјРё РєСЌС€Р° С‡Р°СЂС‚РѕРІ, РёСЃС‚РѕСЂРёРё РїСЂРѕСЃР»СѓС€РёРІР°РЅРёР№ (`play_history`) Рё Р»Р°Р№РєРѕРІ (`liked_tracks`).
   - РђРІС‚РѕРјР°С‚РёС‡РµСЃРєР°СЏ РіСЂСѓРїРїРёСЂРѕРІРєР° РїРµСЂСЃРѕРЅР°Р»СЊРЅС‹С… РїСЂРµРґРїРѕС‡С‚РµРЅРёР№ РїРѕ Р¶Р°РЅСЂР°Рј (`genreSections`).
   - Р Р°Р·РґРµР»РµРЅРёРµ СЃРµРєС†РёР№: Р»РёС‡РЅС‹Рµ (В«РќРµРґР°РІРЅРёРµВ», В«Р›СЋР±РёРјС‹Рµ С‚СЂРµРєРёВ», В«РўРІРѕРё Р¶Р°РЅСЂС‹В») РїРѕРєР°Р·С‹РІР°СЋС‚СЃСЏ С‚РѕР»СЊРєРѕ РїСЂРё РЅР°Р»РёС‡РёРё РґР°РЅРЅС‹С…; С‡Р°СЂС‚С‹ (В«New & HotВ», В«Top 50В») РґРѕСЃС‚СѓРїРЅС‹ РІСЃРµРіРґР° СЃРѕ СЃРєРµР»РµС‚РѕРЅР°РјРё Р·Р°РіСЂСѓР·РєРё.
   - РџРѕРґРґРµСЂР¶РєР° `refresh()` СЃ С„Р»Р°РіРѕРј `forceRefresh = true` РґР»СЏ Р¶РµСЃС‚Р° pull-to-refresh.
3. **HomeScreen (Material 3 Expressive + Jetpack Compose)**:
   - Р’С‹СЂР°Р·РёС‚РµР»СЊРЅР°СЏ С€Р°РїРєР°-РїСЂРёРІРµС‚СЃС‚РІРёРµ РїРѕ РІСЂРµРјРµРЅРё СЃСѓС‚РѕРє СЃ РґРёРЅР°РјРёС‡РµСЃРєРёРј СЌРјРѕРґР·Рё (В«Р”РѕР±СЂРѕРµ СѓС‚СЂРѕ вЂпёЏВ», В«Р”РѕР±СЂС‹Р№ РґРµРЅСЊ рџЊ¤пёЏВ», В«Р”РѕР±СЂС‹Р№ РІРµС‡РµСЂ рџЊ†В», В«Р”РѕР±СЂРѕР№ РЅРѕС‡Рё рџЊ™В») Рё РєСЂСѓРїРЅС‹Рј С€СЂРёС„С‚РѕРј `headlineLarge`.
   - Р“РѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅС‹Рµ РєР°СЂСѓСЃРµР»Рё `LazyRow` СЃ РєР°СЂС‚РѕС‡РєР°РјРё 148Г—148 dp СЃРѕ СЃРєСЂСѓРіР»РµРЅРёРµРј `RoundedCornerShape(20.dp)` Рё С‚РµРЅСЊСЋ 6dp.
   - РќРѕРјРµСЂРЅС‹Рµ РєР°СЂС‚РѕС‡РєРё РґР»СЏ СЃРµРєС†РёРё В«Top 50 SoundCloudВ» СЃ РєСЂСѓРїРЅС‹Рј РїР»Р°РєР°С‚РЅС‹Рј РЅРѕРјРµСЂРѕРј РїРѕР·РёС†РёРё (1..50) РїРѕ РґРёР·Р°Р№РЅ-СЃРїРµРєРµ.
   - РЎРєРµР»РµС‚РѕРЅ-РєР°СЂСѓСЃРµР»Рё РїСЂРё РїРµСЂРІРѕР№ Р·Р°РіСЂСѓР·РєРµ Р±РµР· РјРµСЂС†Р°РЅРёСЏ.
   - Р–РµСЃС‚ Pull-to-refresh С‡РµСЂРµР· `PullToRefreshBox`.
   - РўР°Рї РїРѕ Р»СЋР±РѕРјСѓ С‚СЂРµРєСѓ Р·Р°РїСѓСЃРєР°РµС‚ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёРµ С‚СЂРµРєР° СЃ РїРѕР»РЅРѕР№ РѕС‡РµСЂРµРґСЊСЋ РёР· СЌС‚РѕР№ РїРѕРґР±РѕСЂРєРё.
4. **LibraryScreen Рё LibraryViewModel**:
   - Р—Р°РјРµРЅРµРЅР° СЃС‚Р°С‚РёС‡РµСЃРєР°СЏ Р·Р°РіР»СѓС€РєР° РЅР° РїРѕР»РЅРѕС†РµРЅРЅС‹Р№ СЌРєСЂР°РЅ РјРµРґРёР°С‚РµРєРё Р»СЋР±РёРјС‹С… С‚СЂРµРєРѕРІ РёР· Р±Р°Р·С‹ РґР°РЅРЅС‹С… Room.
   - РЎС‡РµС‚С‡РёРє С‚СЂРµРєРѕРІ РІ Р·Р°РіРѕР»РѕРІРєРµ С€Р°РїРєРё ("X С‚СЂРµРєРѕРІ РІ РєРѕР»Р»РµРєС†РёРё").
   - РљРЅРѕРїРєРё Р±С‹СЃС‚СЂС‹С… РґРµР№СЃС‚РІРёР№: В«РЎР»СѓС€Р°С‚СЊ РІСЃС‘В» Рё В«РџРµСЂРµРјРµС€Р°С‚СЊВ».
   - РЎРїРёСЃРѕРє РєР°СЂС‚РѕС‡РµРє `TrackCard` СЃ РІРѕР·РјРѕР¶РЅРѕСЃС‚СЊСЋ Р±С‹СЃС‚СЂРѕРіРѕ СЃРЅСЏС‚РёСЏ Р»Р°Р№РєР° С‡РµСЂРµР· `IconButton`.
   - Р’С‹СЂР°Р·РёС‚РµР»СЊРЅС‹Р№ Empty State СЃ РїРѕРґСЃРєР°Р·РєРѕР№ РїСЂРё РїСѓСЃС‚РѕР№ РјРµРґРёР°С‚РµРєРµ.
   - РўР°Рї РїРѕ С‚СЂРµРєСѓ РѕС‚РєСЂС‹РІР°РµС‚ РїР»РµРµСЂ СЃ РѕС‡РµСЂРµРґСЊСЋ РёР· РІСЃРµР№ РєРѕР»Р»РµРєС†РёРё.
5. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
data/local/entity/ChartCacheEntity.kt
data/local/dao/ChartCacheDao.kt
data/local/AppDatabase.kt
data/local/DatabaseModule.kt
data/repository/SoundCloudChartsRepository.kt
data/source/soundcloud/SoundCloudChartsRepositoryImpl.kt
core/di/RepositoryModule.kt
feature/home/HomeViewModel.kt
feature/home/HomeScreen.kt
feature/library/LibraryViewModel.kt
feature/library/LibraryScreen.kt
navigation/AppNavGraph.kt
```

---

## вњ… Р¤Р°Р·Р° 4 вЂ” Material 3 Expressive СЂРµРґРёР·Р°Р№РЅ (19.10.2026)

РџСЂРёРјРµС‡Р°РЅРёРµ: РІ РїСЂРµР¶РЅРµРј РїР»Р°РЅРµ В«Р¤Р°Р·Р° 4В» РѕР·РЅР°С‡Р°Р»Р° Р°Р»РіРѕСЂРёС‚Рј В«РњРѕРµР№ РІРѕР»РЅС‹В». РџРѕ СЂРµС€РµРЅРёСЋ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ СЌС‚Р° С„Р°Р·Р° РѕС‚РІРµРґРµРЅР° РїРѕРґ Expressive-СЂРµРґРёР·Р°Р№РЅ РёРЅС‚РµСЂС„РµР№СЃР°, Р° Р°Р»РіРѕСЂРёС‚Рј РІРѕР»РЅС‹ РїРµСЂРµРЅРµСЃС‘РЅ РІ v1.1. РњРµС…Р°РЅРёРєР° РїР»РµРµСЂР° (LRCLIB, Palette, РјРѕСЂС„РёРЅРі РєРЅРѕРїРєРё) РЅРµ Р·Р°С‚СЂР°РіРёРІР°Р»Р°СЃСЊ.

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **ExpressiveNavigationBar** (`core/ui/components/ExpressiveNavigationBar.kt`):
   - РџР»Р°РІР°СЋС‰Р°СЏ РЅРёР¶РЅСЏСЏ РїР°РЅРµР»СЊ `surfaceContainer` СЃРѕ СЃРєСЂСѓРіР»РµРЅРёРµРј 24dp, С‚РµРЅСЊСЋ Рё РѕС‚СЃС‚СѓРїРѕРј 8dp РЅР°Рґ СЃРёСЃС‚РµРјРЅРѕР№ РЅР°РІРёРіР°С†РёРµР№.
   - РџСЂСѓР¶РёРЅРЅР°СЏ РїРёР»СЋР»СЏ-РёРЅРґРёРєР°С‚РѕСЂ Р°РєС‚РёРІРЅРѕР№ РІРєР»Р°РґРєРё (~56Г—32 dp, СЂР°РґРёСѓСЃ 16dp) СЃ Р°РЅРёРјР°С†РёРµР№ СЃРјРµС‰РµРЅРёСЏ (`spring`, dampingRatio 0.78) С‡РµСЂРµР· `BoxWithConstraints`.
   - РђРЅРёРјР°С†РёСЏ РјР°СЃС€С‚Р°Р±Р° Рё С†РІРµС‚Р° РёРєРѕРЅРѕРє Р°РєС‚РёРІРЅРѕР№ РІРєР»Р°РґРєРё, С‚РёРїРѕРіСЂР°С„РёРєР° 11sp, С‚Р°РєС‚РёР»СЊРЅС‹Р№ РѕС‚РєР»РёРє `TextHandleMove`.
2. **SearchScreen**:
   - РљСЂСѓРїРЅР°СЏ РІС‹СЂР°Р·РёС‚РµР»СЊРЅР°СЏ С€Р°РїРєР° В«РџРћРРЎРљВ» (headlineLarge, ExtraBold).
   - `ExpressiveSearchField`: РјРѕСЂС„РёРЅРі РїСЂРё С„РѕРєСѓСЃРµ (С†РІРµС‚ РєРѕРЅС‚РµР№РЅРµСЂР°, СЃРєСЂСѓРіР»РµРЅРёРµ 28dp, С‚РµРЅСЊ 8dp), РёРєРѕРЅРєР° РѕС‡РёСЃС‚РєРё СЃ РїСЂСѓР¶РёРЅРЅС‹Рј РїРѕСЏРІР»РµРЅРёРµРј.
   - Р–Р°РЅСЂРѕРІС‹Рµ С‡РёРїС‹-РїРѕРґСЃРєР°Р·РєРё, СЃРѕСЃС‚РѕСЏРЅРёСЏ Idle / Loading (СЃРєРµР»РµС‚РѕРЅС‹) / Error / Empty / Results, reveal-Р°РЅРёРјР°С†РёСЏ СЃРїРёСЃРєР°.
   - РЈР±СЂР°РЅ РґРІРѕР№РЅРѕР№ СЃРёСЃС‚РµРјРЅС‹Р№ inset (РІР»РѕР¶РµРЅРЅС‹Р№ Scaffold СЃ `contentWindowInsets = 0`).
3. **WaveScreen** (РїСЂРѕРјРѕ-Р·Р°РіР»СѓС€РєР° В«СЃРєРѕСЂРѕВ»):
   - Р–РёРІРѕР№ Canvas-С„РѕРЅ: С‚СЂРё СЃРёРЅСѓСЃРѕРёРґС‹, СЂР°РґРёР°Р»СЊРЅС‹Р№ РіСЂР°РґРёРµРЅС‚РЅС‹Р№ РѕСЂР± СЃ С‡Р°СЃС‚РёС†Р°РјРё РЅР° РѕСЂР±РёС‚Р°С…, `rememberInfiniteTransition`.
   - Р‘РµР№РґР¶ В«РЎРљРћР РћВ» СЃ РіСЂР°РґРёРµРЅС‚РѕРј, С‡РёРїС‹ РЅР°СЃС‚СЂРѕРµРЅРёР№ РІРѕР»РЅС‹ (РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅС‹Р№ `LazyRow`, РІ Р±СѓРґСѓС‰РµРј вЂ” СЂРµР¶РёРјС‹), РєР°СЂС‚РѕС‡РєР°-Р°РЅРѕРЅСЃ.
   - РџРѕР»РЅРѕСЌРєСЂР°РЅРЅС‹Р№ reveal-РїРµСЂРµС…РѕРґ С‡РµСЂРµР· `AnimatedVisibility` (РїСЂСѓР¶РёРЅР° + fade).
4. **HomeScreen**:
   - РЈРґР°Р»С‘РЅ РІР»РѕР¶РµРЅРЅС‹Р№ Scaffold вЂ” РєРѕРЅС‚РµРЅС‚ Р±РѕР»СЊС€Рµ РЅРµ РїРѕР»СѓС‡Р°РµС‚ РґРІРѕР№РЅС‹Рµ insets.
   - Р’СЃРµ СЃРµРєС†РёРё РѕР±С‘СЂРЅСѓС‚С‹ РІ `RevealSection` (staggered-РїРѕСЏРІР»РµРЅРёРµ СЃ Р·Р°РґРµСЂР¶РєРѕР№ 55 РјСЃ РЅР° СЃРµРєС†РёСЋ, РїСЂСѓР¶РёРЅР°, СЃ СЂРµР·РµСЂРІРёСЂРѕРІР°РЅРёРµРј layout-СЂР°Р·РјРµСЂР°).
   - `SectionHeader` СЃ РіСЂР°РґРёРµРЅС‚РЅРѕР№ Р°РєС†РµРЅС‚РЅРѕР№ РїРѕР»РѕСЃРѕР№; expressРёРІРЅС‹Рµ Р·Р°РіРѕР»РѕРІРєРё СЃРµРєС†РёР№.
   - `TrackCarouselCard`: РїСЂСѓР¶РёРЅРЅС‹Р№ scale 0.95 РїСЂРё РЅР°Р¶Р°С‚РёРё Рё С‚РѕРЅР°Р»СЊРЅС‹Р№ СЃР»РѕР№-РїРѕРґСЃРІРµС‚РєР°.
   - `TopChartCarousel`: Р°РЅРёРјРёСЂРѕРІР°РЅРЅС‹Р№ С†РІРµС‚ РєРѕРЅС‚РµР№РЅРµСЂР° РїСЂРё РЅР°Р¶Р°С‚РёРё, top-3 РєР°СЂС‚РѕС‡РєРё РІ `primaryContainer`.
5. **TrackCard** (РѕР±С‰РёР№ РєРѕРјРїРѕРЅРµРЅС‚ Search Рё Library):
   - РџРѕРІРµСЂС…РЅРѕСЃС‚СЊ 20dp СЃ С‚РѕРЅР°Р»СЊРЅС‹Рј РѕС‚РєР»РёРєРѕРј РЅР° РЅР°Р¶Р°С‚РёРµ (`collectIsPressedAsState` + `animateColorAsState`), РїСЂСѓР¶РёРЅРЅС‹Р№ scale 0.975.
   - РћР±Р»РѕР¶РєР° 56dp/12dp, РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊ Рё Р¶Р°РЅСЂ РІ РїРѕРґРїРёСЃРё.
6. **AppNavGraph**:
   - Spring-РїРµСЂРµС…РѕРґС‹ РІРєР»Р°РґРѕРє Рё РїР»РµРµСЂР° (fade + scale / spring slide РІРјРµСЃС‚Рѕ tween), РѕР±РЅРѕРІР»С‘РЅ РІС‹Р·РѕРІ `WaveScreen()`.
7. **Type.kt** вЂ” РІС‹СЂР°Р·РёС‚РµР»СЊРЅР°СЏ С‚РёРїРѕРіСЂР°С„РёС‡РµСЃРєР°СЏ С€РєР°Р»Р°:
   - ExtraBold-Р·Р°РіРѕР»РѕРІРєРё СЃ РїР»РѕС‚РЅС‹Рј С‚СЂРµРєРёРЅРіРѕРј (-0.5sp), Bold labelSmall (11sp), SemiBold labelLarge/titleMedium.
8. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/core/ui/components/ExpressiveNavigationBar.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/ui/components/TrackCard.kt
app/src/main/java/com/facet/music/core/ui/theme/Type.kt
app/src/main/java/com/facet/music/feature/home/HomeScreen.kt
app/src/main/java/com/facet/music/feature/search/SearchScreen.kt
app/src/main/java/com/facet/music/feature/wave/WaveScreen.kt
app/src/main/java/com/facet/music/navigation/AppNavGraph.kt
```

---

## вњ… Р¤Р°Р·Р° 4.1 вЂ” РџСЂР°РІРєРё РїРѕСЃР»Рµ С‚РµСЃС‚Р° РЅР° СЂРµР°Р»СЊРЅРѕРј СѓСЃС‚СЂРѕР№СЃС‚РІРµ (19.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **WaveScreen**: С†РµРЅС‚СЂ РѕСЂР±Р° РїРµСЂРµРЅРµСЃС‘РЅ РІ РіРµРѕРјРµС‚СЂРёС‡РµСЃРєРёР№ С†РµРЅС‚СЂ hero-Р·РѕРЅС‹ (`size.height / 2f`), РїРѕСЌС‚РѕРјСѓ РёРєРѕРЅРєР° `Waves` С‡РµСЂРµР· `Alignment.Center` СЃС‚РѕРёС‚ СЂРѕРІРЅРѕ РІ С†РµРЅС‚СЂРµ РєСЂСѓРіР°.
2. **PlayerScreen, РІРµСЂС…РЅСЏСЏ РїР°РЅРµР»СЊ**: `TopAppBar` Р·Р°РјРµРЅС‘РЅ РЅР° `CenterAlignedTopAppBar`; В«РЎР•Р™Р§РђРЎ РР“Р РђР•РўВ» Рё РЅР°Р·РІР°РЅРёРµ С‚СЂРµРєР° С†РµРЅС‚СЂРёСЂСѓСЋС‚СЃСЏ РјРµР¶РґСѓ РєРЅРѕРїРєР°РјРё В«РќР°Р·Р°РґВ» Рё В«РњРµРЅСЋВ» (`TextAlign.Center`, `fillMaxWidth`, ellipsis).
3. **PlayerPalette**: РїСЂРёРѕСЂРёС‚РµС‚ СЃРІРѕС‚С‡РµР№ РёР·РјРµРЅС‘РЅ РЅР° РІС‹СЂР°Р·РёС‚РµР»СЊРЅС‹Рµ (`vibrant` в†’ `lightVibrant` в†’ `dominant` в†’ вЂ¦); РґР»СЏ СЃРІРµС‚Р»С‹С… РѕР±Р»РѕР¶РµРє (`luminance > 0.35`) РЅР°СЃС‹С‰РµРЅРЅРѕСЃС‚СЊ РїРѕРґРЅРёРјР°РµС‚СЃСЏ Г—1.4, СЃРІРµС‚Р»РѕС‚Р° РѕРіСЂР°РЅРёС‡РёРІР°РµС‚СЃСЏ 0.50вЂ“0.66, Р°Р»СЊС„С‹ С„РѕРЅР° СѓРІРµР»РёС‡РµРЅС‹ РґРѕ 0.88/0.40 (С‚С‘РјРЅС‹Рµ вЂ” 0.62/0.22). РЎРµСЂС‹Рµ В«РјРµС‚Р°Р»Р»РёС‡РµСЃРєРёРµВ» РѕР±Р»РѕР¶РєРё С‚РµРїРµСЂСЊ РґР°СЋС‚ РѕС‰СѓС‚РёРјС‹Р№ С†РІРµС‚ С„РѕРЅР°.
4. **РўРµРєСЃС‚С‹ РїРµСЃРµРЅ**: РІРјРµСЃС‚Рѕ РїСЂРѕСЃС‚РѕРіРѕ fade вЂ” `animateColorAsState` (420 РјСЃ) РѕС‚ С‚СѓСЃРєР»РѕРіРѕ `onSurface` (alpha 0.30/0.55) Рє Р±РµР»РѕРјСѓ `onSurface` (alpha 1.0); РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅРѕ Р°РЅРёРјРёСЂСѓСЋС‚СЃСЏ РІРµСЃ С€СЂРёС„С‚Р° (400 в†’ 700, РїСЂСѓР¶РёРЅР°), РјР°СЃС€С‚Р°Р± (1.0 в†’ 1.05) Рё СЃРІРµС‡РµРЅРёРµ (`Transparent` в†’ `primary` 0.5, blur 10).
5. **РЎР»Р°Р№РґРµСЂ**: РІ `PlayerProgressSection` РґРѕР±Р°РІР»РµРЅ С„Р»Р°Рі `isResolving`; `LinearProgressIndicator` РїРѕРєР°Р·С‹РІР°РµС‚СЃСЏ РїСЂРё `(isBuffering || isResolving) && РїРѕР·РёС†РёСЏ == 0`, Р·Р°С‚РµРј `Crossfade` (500 РјСЃ) РїР»Р°РІРЅРѕ Р·Р°РјРµРЅСЏРµС‚ РµРіРѕ РЅР° `Slider` СЃ Р±СѓС„РµСЂРЅС‹Рј СЃР»РѕРµРј `primary.copy(alpha = 0.3f)`.
6. **Р§Р°СЂС‚С‹ SoundCloud**: РІ `SoundCloudChartsRepositoryImpl` РґРѕР±Р°РІР»РµРЅ С„РёР»СЊС‚СЂ РјСѓСЃРѕСЂР° вЂ” РѕС‚Р±СЂР°СЃС‹РІР°СЋС‚СЃСЏ С‚СЂРµРєРё РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊСЋ 1вЂ“29 СЃРµРє Рё СЏРІРЅС‹Рµ РЅСѓР»Рё РїСЂРѕСЃР»СѓС€РёРІР°РЅРёР№ (С„РёР»СЊС‚СЂ РїРѕ plays РїСЂРёРјРµРЅСЏРµС‚СЃСЏ С‚РѕР»СЊРєРѕ РµСЃР»Рё `viewCount` СЂРµР°Р»СЊРЅРѕ РїР°СЂСЃРёС‚СЃСЏ); РµСЃР»Рё РїРѕСЃР»Рµ С„РёР»СЊС‚СЂР°С†РёРё СЃРїРёСЃРѕРє РїСѓСЃС‚ вЂ” РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ РёСЃС…РѕРґРЅС‹Р№. РЎРµРєС†РёСЏ Top 50 РѕСЃС‚Р°С‘С‚СЃСЏ РїРѕР»РЅРѕР№ (50 РїРѕР·РёС†РёР№).
7. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/wave/WaveScreen.kt
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
app/src/main/java/com/facet/music/core/ui/theme/PlayerPalette.kt
app/src/main/java/com/facet/music/data/source/soundcloud/SoundCloudChartsRepositoryImpl.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.2 вЂ” Р”СЂСѓР¶РµР»СЋР±РЅС‹Рµ СЃРµС‚РµРІС‹Рµ РѕС€РёР±РєРё РїР»РµРµСЂР° (19.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Р•РґРёРЅС‹Р№ РјР°РїРїРµСЂ РѕС€РёР±РѕРє (`core/player/PlaybackErrorMessages.kt`)**:
   - `UnknownHostException`, `ConnectException`, `SocketException`, СЃРѕРѕР±С‰РµРЅРёСЏ В«Unable to resolve hostВ» / В«No address associated with hostnameВ» в†’ В«РќРµС‚ РїРѕРґРєР»СЋС‡РµРЅРёСЏ Рє РёРЅС‚РµСЂРЅРµС‚Сѓ. РџСЂРѕРІРµСЂСЊС‚Рµ СЃРµС‚СЊ.В»
   - `SocketTimeoutException` / С‚Р°Р№РјР°СѓС‚С‹ в†’ В«РџСЂРµРІС‹С€РµРЅРѕ РІСЂРµРјСЏ РѕР¶РёРґР°РЅРёСЏ. РџРѕРїСЂРѕР±СѓР№С‚Рµ СЃРЅРѕРІР°.В»
   - `SSLException` в†’ В«РћС€РёР±РєР° Р·Р°С‰РёС‰С‘РЅРЅРѕРіРѕ СЃРѕРµРґРёРЅРµРЅРёСЏ. РџРѕРїСЂРѕР±СѓР№С‚Рµ СЃРЅРѕРІР°.В»
   - Р’СЃС‘ РѕСЃС‚Р°Р»СЊРЅРѕРµ в†’ В«РќРµ СѓРґР°Р»РѕСЃСЊ РІРѕСЃРїСЂРѕРёР·РІРµСЃС‚Рё С‚СЂРµРє. РџРѕРїСЂРѕР±СѓР№С‚Рµ СЃРЅРѕРІР°.В»
   - Р”Р»СЏ `PlaybackException` РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅРѕ СѓС‡РёС‚С‹РІР°СЋС‚СЃСЏ РєРѕРґС‹ `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` / `_TIMEOUT`.
2. **PlayerController**:
   - РћС€РёР±РєР° `resolveStreamUrl` РјР°РїРїРёС‚СЃСЏ РІ РґСЂСѓР¶РµР»СЋР±РЅС‹Р№ С‚РµРєСЃС‚; СЃС‹СЂРѕРµ РёСЃРєР»СЋС‡РµРЅРёРµ РїРёС€РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РІ Logcat.
   - Р”РѕР±Р°РІР»РµРЅ `onPlayerError` РґР»СЏ РѕС€РёР±РѕРє ExoPlayer РІРѕ РІСЂРµРјСЏ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёСЏ вЂ” РЅР° СЌРєСЂР°РЅ С‚Р°РєР¶Рµ СѓС…РѕРґРёС‚ С‚РѕР»СЊРєРѕ РїРѕРЅСЏС‚РЅРѕРµ СЃРѕРѕР±С‰РµРЅРёРµ.
   - РџСЂРё РѕС€РёР±РєРµ СЃР±СЂР°СЃС‹РІР°СЋС‚СЃСЏ `isLoading`, `isBuffering`, `isPlaying`.
   - РќР°Р¶Р°С‚РёРµ Play/Pause РїСЂРё РЅР°Р»РёС‡РёРё РѕС€РёР±РєРё СЃСЂР°Р·Сѓ РѕС‡РёС‰Р°РµС‚ РµС‘ Рё РїРѕРІС‚РѕСЂРЅРѕ Р·Р°РїСѓСЃРєР°РµС‚ С‚РµРєСѓС‰РёР№ С‚СЂРµРє (`recordHistory = false`, С‡С‚РѕР±С‹ РЅРµ РґСѓР±Р»РёСЂРѕРІР°С‚СЊ РёСЃС‚РѕСЂРёСЋ).
   - РџСЂРё РїРµСЂРµС…РѕРґРµ РІ `STATE_READY` РѕС€РёР±РєР° РіР°СЂР°РЅС‚РёСЂРѕРІР°РЅРЅРѕ РѕС‡РёС‰Р°РµС‚СЃСЏ.
3. **PlayerViewModel**:
   - `togglePlayPause()` РЅРµРјРµРґР»РµРЅРЅРѕ РѕС‡РёС‰Р°РµС‚ `uiState.error`.
   - РћС€РёР±РєРё Р·Р°РіСЂСѓР·РєРё С‚РµРєСЃС‚РѕРІ РёР· LRCLIB С‚Р°РєР¶Рµ РјР°РїРїСЏС‚СЃСЏ РІ В«РќРµ СѓРґР°Р»РѕСЃСЊ Р·Р°РіСЂСѓР·РёС‚СЊ С‚РµРєСЃС‚. РџРѕРїСЂРѕР±СѓР№С‚Рµ СЃРЅРѕРІР°.В» вЂ” СЃС‹СЂС‹Рµ Java-РѕС€РёР±РєРё РЅРµ РїРѕРїР°РґР°СЋС‚ РІ РїР°РЅРµР»СЊ С‚РµРєСЃС‚РѕРІ.
   - Р’С‹Р±РѕСЂ РЅРѕРІРѕРіРѕ С‚СЂРµРєР° СѓР¶Рµ СЃР±СЂР°СЃС‹РІР°РµС‚ РѕС€РёР±РєСѓ (`playTrackWithQueue` / `playTrackAt`).
4. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/core/player/PlaybackErrorMessages.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/player/PlayerController.kt
app/src/main/java/com/facet/music/feature/player/PlayerViewModel.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.3 вЂ” Р¤РёРЅР°Р»СЊРЅС‹Рµ РІРёР·СѓР°Р»СЊРЅС‹Рµ С€С‚СЂРёС…Рё: WaveSlider, РєР°СЂР°РѕРєРµ-С‚РµРєСЃС‚, Google Sans Flex (19.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РђРЅРёРјРёСЂРѕРІР°РЅРЅР°СЏ СЃРёРЅСѓСЃРѕРёРґР° РІ PlayerScreen (WaveSlider)**:
   - Р’ `app/build.gradle.kts` РґРѕР±Р°РІР»РµРЅР° Р·Р°РІРёСЃРёРјРѕСЃС‚СЊ `com.github.galaxygoldfish:waveslider:v0.0.4` (РІ Р·Р°РґР°РЅРёРё СѓРєР°Р·Р°РЅР° 0.3.3, РЅРѕ РІ JitPack РµС‘ РЅРµС‚ вЂ” РїРѕСЃР»РµРґРЅСЏСЏ СЃРѕР±СЂР°РЅРЅР°СЏ РІРµСЂСЃРёСЏ v0.0.4). JitPack СѓР¶Рµ Р±С‹Р» РїРѕРґРєР»СЋС‡С‘РЅ РІ `settings.gradle.kts`, РїСЂР°РІРѕРє РЅРµ РїРѕС‚СЂРµР±РѕРІР°Р»РѕСЃСЊ.
   - РЎС‚Р°РЅРґР°СЂС‚РЅС‹Р№ `Slider` РІ `PlayerProgressSection` Р·Р°РјРµРЅС‘РЅ РЅР° `WaveSlider` (amplitude = 10F, frequency = 0.07F, colors РёР· MaterialTheme/РїР°Р»РёС‚СЂС‹ С‚СЂРµРєР°).
   - Р‘СѓС„РµСЂРёР·Р°С†РёСЏ СЃРѕС…СЂР°РЅРµРЅР°: С‚РѕРЅРєР°СЏ Р»РёРЅРёСЏ `primary.copy(alpha = 0.3f)` СЂРёСЃСѓРµС‚СЃСЏ РїРѕРґ РІРѕР»РЅРѕР№; Crossfade В«РёРЅРґРёРєР°С‚РѕСЂ Р·Р°РіСЂСѓР·РєРё в†’ СЃР»Р°Р№РґРµСЂВ» (500 РјСЃ) РЅРµ С‚СЂРѕРЅСѓС‚.
2. **РљР°СЂР°РѕРєРµ-СЌС„С„РµРєС‚ РІ С‚РµРєСЃС‚Р°С… (РїРѕ РјРѕС‚РёРІР°Рј Accompanist KaraokeLineText, Р±РµР· РєРѕРїРёСЂРѕРІР°РЅРёСЏ РєРѕРґР°)**:
   - Р’ С†РёРєР»Рµ РѕРїСЂРѕСЃР° РїРѕР·РёС†РёРё (150 РјСЃ) РІС‹С‡РёСЃР»СЏРµС‚СЃСЏ РїСЂРѕРіСЂРµСЃСЃ РІРЅСѓС‚СЂРё Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё.
   - РђРєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР° Р·Р°РїРѕР»РЅСЏРµС‚СЃСЏ `Brush.linearGradient` СЃР»РµРІР° РЅР°РїСЂР°РІРѕ: РїСЂРѕРёРіСЂР°РЅРЅР°СЏ С‡Р°СЃС‚СЊ вЂ” Р±РµР»Р°СЏ (`onSurface`), РЅРµРїСЂРѕРїРµС‚Р°СЏ вЂ” С‚СѓСЃРєР»Р°СЏ (alpha 0.30), СЃ РјСЏРіРєРёРј РєСЂР°РµРј 5%.
   - РџСЂРѕС€РµРґС€РёРµ СЃС‚СЂРѕРєРё вЂ” Р·Р°Р»РёРІРєР° 1.0 (Р±РµР»С‹Р№ 55% alpha), Р±СѓРґСѓС‰РёРµ вЂ” 0.0; Р·Р°Р»РёРІРєР° Р°РЅРёРјРёСЂСѓРµС‚СЃСЏ `animateFloatAsState` (160 РјСЃ, LinearEasing).
   - РЎРІРµС‡РµРЅРёРµ, Р°РЅРёРјР°С†РёСЏ РІРµСЃР° (400в†’700) Рё РјР°СЃС€С‚Р°Р±Р° (1.05) Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё СЃРѕС…СЂР°РЅРµРЅС‹.
3. **Google Sans Flex**:
   - Variable-С€СЂРёС„С‚ `GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf` (OFL) СЃРєР°С‡Р°РЅ РІ `app/src/main/res/font/google_sans_flex.ttf`; Р»РёС†РµРЅР·РёСЏ вЂ” `app/src/main/assets/licenses/OFL_GoogleSansFlex.txt`.
   - Р’ `Type.kt` СЃРѕР·РґР°РЅ `FontFamily` РёР· 5 РІРµСЃРѕРІ (400/500/600/700/800) С‡РµСЂРµР· `FontVariation.Settings` (РѕСЃСЊ wght, API 26+) Рё РїСЂРёРјРµРЅС‘РЅ РєРѕ РІСЃРµР№ С‚РёРїРѕРіСЂР°С„РёРєРµ.
   - РћС‚РєР°Р·Р°Р»РёСЃСЊ РѕС‚ `ui-text-google-fonts`/`GoogleFont("Google Sans Flex")`: РїСЂРѕРІР°Р№РґРµСЂ С€СЂРёС„С‚РѕРІ С‚СЂРµР±СѓРµС‚ GMS Рё СЃРµСЂС‚РёС„РёРєР°С‚С‹, РІ РѕС„С„Р»Р°Р№РЅРµ РјРѕР¶РµС‚ РЅРµ Р·Р°РіСЂСѓР·РёС‚СЊСЃСЏ; Р»РѕРєР°Р»СЊРЅС‹Р№ TTF РЅР°РґС‘Р¶РЅРµРµ (РІ Р·Р°РґР°РЅРёРё РґРѕРїСѓСЃРєР°Р»СЃСЏ РІР°СЂРёР°РЅС‚ В«РёР»Рё СЃРєР°С‡Р°Р№ .ttfВ»).
4. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/build.gradle.kts
app/src/main/res/font/google_sans_flex.ttf (РЅРѕРІС‹Р№)
app/src/main/assets/licenses/OFL_GoogleSansFlex.txt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/ui/theme/Type.kt
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.4 вЂ” Р§РёСЃС‚С‹Р№ С„РѕРЅ WaveSlider Рё Р·Р°РјРёСЂР°РЅРёРµ РІРѕР»РЅС‹ РЅР° РїР°СѓР·Рµ (19.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РџСЂРѕР·СЂР°С‡РЅС‹Р№ С„РѕРЅ СЃР»Р°Р№РґРµСЂР° (`PlayerProgressSection`)**:
   - `inactiveTrackColor` РїРµСЂРµРІРµРґС‘РЅ РЅР° `Color.Transparent` вЂ” СЃРµСЂР°СЏ РїРѕР»РѕСЃР° `surfaceVariant` (Р»РёРЅРёСЏ РѕС‚ Р±РµРіСѓРЅРєР° РґРѕ РєРѕРЅС†Р°, РєРѕС‚РѕСЂСѓСЋ СЂРёСЃСѓРµС‚ Р±РёР±Р»РёРѕС‚РµРєР°) Р±РѕР»СЊС€Рµ РЅРµ РІРёРґРЅР° Р·Р° РІРѕР»РЅРѕР№.
   - РЈРґР°Р»С‘РЅ СЃР»РѕР№ Р±СѓС„РµСЂРёР·Р°С†РёРё (`LinearProgressIndicator` `primary.copy(alpha = 0.3f)`) вЂ” РІРѕР»РЅР° С‚РµРїРµСЂСЊ СЂРёСЃСѓРµС‚СЃСЏ РїСЂСЏРјРѕ РЅР° РіСЂР°РґРёРµРЅС‚Рµ РїР»РµРµСЂР°, Р±РµР· РїРѕРґР»РѕР¶РµРє (РІРјРµСЃС‚Рµ СЃ РЅРёРј СѓР±СЂР°РЅ РїР°СЂР°РјРµС‚СЂ `bufferedFraction`).
2. **Р—Р°РјРёСЂР°РЅРёРµ РІРѕР»РЅС‹ РЅР° РїР°СѓР·Рµ**:
   - РђРјРїР»РёС‚СѓРґР° РІРѕР»РЅС‹ СЃРІСЏР·Р°РЅР° СЃ `isPlaying` С‡РµСЂРµР· `animateFloatAsState`: РЅР° РїР°СѓР·Рµ РїР»Р°РІРЅРѕ (500 РјСЃ, `FastOutSlowInEasing`) РѕРїСѓСЃРєР°РµС‚СЃСЏ СЃ 10f РґРѕ 0f, РїСЂРё РІРѕР·РѕР±РЅРѕРІР»РµРЅРёРё вЂ” РїРѕРґРЅРёРјР°РµС‚СЃСЏ РѕР±СЂР°С‚РЅРѕ.
   - РќР° РїР°СѓР·Рµ РІРѕР»РЅР° Р·Р°РјРёСЂР°РµС‚ Р°Р±СЃРѕР»СЋС‚РЅРѕ РїСЂСЏРјРѕР№ Р»РёРЅРёРµР№.
3. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.5 вЂ” РџРѕР»РѕСЃРєР° РґР»РёС‚РµР»СЊРЅРѕСЃС‚Рё Рё РєРѕРјРїР°РєС‚РЅС‹Р№ РїР»РµРµСЂ (19.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РџРѕР»РѕСЃРєР° РґР»РёС‚РµР»СЊРЅРѕСЃС‚Рё РїРѕРґ РІРѕР»РЅРѕР№ (`PlayerProgressSection`)**:
   - РџРѕРґ WaveSlider РґРѕР±Р°РІР»РµРЅ С‚РѕРЅРєРёР№ Canvas-СЃР»РѕР№ (2dp, СЃРєСЂСѓРіР»С‘РЅРЅС‹Рµ РєРѕРЅС†С‹): РІСЃСЏ С€РёСЂРёРЅР° вЂ” РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊ С‚СЂРµРєР° (primary, alpha 0.18), РїСЂРѕРёРіСЂР°РЅРЅР°СЏ С‡Р°СЃС‚СЊ вЂ” СЏСЂС‡Рµ (primary, alpha 0.65).
   - РџРѕР»РѕСЃРєР° СЃРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°РЅР° СЃ `displayPosition` (РґРІРёРіР°РµС‚СЃСЏ Рё РїСЂРё РїРµСЂРµС‚Р°СЃРєРёРІР°РЅРёРё), СЂРёСЃСѓРµС‚СЃСЏ Р·Р° РІРѕР»РЅРѕР№, С„РѕРЅ РѕСЃС‚Р°С‘С‚СЃСЏ РїСЂРѕР·СЂР°С‡РЅС‹Рј.
2. **РљРѕРјРїР°РєС‚РЅРѕСЃС‚СЊ РїР»РµРµСЂР°**:
   - РћР±Р»РѕР¶РєР° 280dp в†’ 248dp (Р°РЅРёРјР°С†РёСЏ СЃС…Р»РѕРїС‹РІР°РЅРёСЏ РІ С‚РµРєСЃС‚Р°С…: 248dp в†’ 56dp).
   - РћС‚СЃС‚СѓРїС‹: РЅР°Рґ РѕР±Р»РѕР¶РєРѕР№ 12в†’8, РїРѕРґ РѕР±Р»РѕР¶РєРѕР№ 16в†’12, Сѓ Р·Р°РіРѕР»РѕРІРєР° 12в†’8, РІРѕРєСЂСѓРі РєРЅРѕРїРѕРє 8в†’6 Рё 16в†’12, РЅРёР¶РЅРёР№ 16в†’12.
   - РљРЅРѕРїРєРё: Play/Pause 88в†’80dp (РјРѕСЂС„ СЂР°РґРёСѓСЃР° 40в†’26dp, РёРєРѕРЅРєР° 40в†’36, СЃРїРёРЅРЅРµСЂ 32в†’30), Prev/Next 64в†’56dp (РёРєРѕРЅРєРё 32в†’28).
   - РџРёР»СЋР»Рё РґРµР№СЃС‚РІРёР№ 52в†’46dp (СЂР°РґРёСѓСЃС‹ 26в†’23dp).
3. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.6 вЂ” Expressive-СЂРµРґРёР·Р°Р№РЅ PlayerScreen: РЅР°С‚РёРІРЅС‹Р№ СЃР»Р°Р№РґРµСЂ Рё AnimatedContent (19.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РЈРґР°Р»РµРЅР° Р±РёР±Р»РёРѕС‚РµРєР° WaveSlider**:
   - РР· `app/build.gradle.kts` СѓР±СЂР°РЅР° Р·Р°РІРёСЃРёРјРѕСЃС‚СЊ `com.github.galaxygoldfish:waveslider:v0.0.4`; JitPack РІ `settings.gradle.kts` РѕСЃС‚Р°РІР»РµРЅ (РЅРµ РѕС‚РЅРѕСЃРёС‚СЃСЏ Рє Р±РёР±Р»РёРѕС‚РµРєРµ).
2. **РќРѕРІС‹Р№ СЃР»Р°Р№РґРµСЂ `PlayerSlider` (`core/ui/components/PlayerSlider.kt`)**:
   - Р§РёСЃС‚С‹Р№ Compose Canvas: С‚РѕР»СЃС‚Р°СЏ СЃРєСЂСѓРіР»С‘РЅРЅР°СЏ РґРѕСЂРѕР¶РєР° 8dp, Р±СѓС„РµСЂРёР·Р°С†РёСЏ РїРѕР»СѓРїСЂРѕР·СЂР°С‡РЅС‹Рј СЃР»РѕРµРј (`activeColor` alpha 0.28), РїСЂРѕРёРіСЂР°РЅРЅР°СЏ С‡Р°СЃС‚СЊ вЂ” Р°РєС†РµРЅС‚РЅС‹Р№ С†РІРµС‚.
   - РџРѕР»Р·СѓРЅРѕРє вЂ” РІРµСЂС‚РёРєР°Р»СЊРЅР°СЏ РїРёР»СЋР»СЏ 10Г—30dp СЃ РїСЂСѓР¶РёРЅРЅС‹Рј СѓРІРµР»РёС‡РµРЅРёРµРј Г—1.35 Рё РѕСЂРµРѕР»РѕРј РїСЂРё РїРµСЂРµС‚Р°СЃРєРёРІР°РЅРёРё; Р¶РµСЃС‚С‹ drag + tap, Р±РµР· РІРЅРµС€РЅРёС… Р±РёР±Р»РёРѕС‚РµРє.
3. **РџРµСЂРµС…РѕРґ В«РћР±Р»РѕР¶РєР° в†” РўРµРєСЃС‚В» С‡РµСЂРµР· `AnimatedContent`**:
   - Р’РµСЂС…РЅСЏСЏ (РѕР±Р»РѕР¶РєР°) Рё С†РµРЅС‚СЂР°Р»СЊРЅР°СЏ Р·РѕРЅС‹ РѕР±СЉРµРґРёРЅРµРЅС‹ РІ РѕРґРёРЅ `AnimatedContent` СЃ РІРµСЃРѕРј 1f.
   - РџСЂСѓР¶РёРЅРЅС‹Р№ РїРµСЂРµС…РѕРґ (dampingRatio 0.82, StiffnessMediumLow): С‚РµРєСЃС‚ РІСЉРµР·Р¶Р°РµС‚ СЃРЅРёР·Сѓ (it/3), РѕР±Р»РѕР¶РєР° СѓС…РѕРґРёС‚ РІРІРµСЂС… (-it/4), СЃ fade; РѕР±СЂР°С‚РЅС‹Р№ РїРµСЂРµС…РѕРґ вЂ” Р·РµСЂРєР°Р»СЊРЅС‹Р№.
   - РќРѕРІС‹Рµ file-scope РєРѕРјРїРѕР·Р°Р±Р»С‹ `CoverModeContent` Рё `LyricsModeContent`.
4. **РћР±СЉС‘Рј РѕР±Р»РѕР¶РєРё**:
   - РЎРІРµС‡РµРЅРёРµ РїРѕР·Р°РґРё РѕР±Р»РѕР¶РєРё: radialGradient (`primary` alpha 0.35), scale 1.16 С‡РµСЂРµР· `matchParentSize` (layout РЅРµ СЂР°Р·РґСѓРІР°РµС‚).
   - РџР°СЂР°Р»Р»Р°РєСЃ: `translationY = scroll * 0.45f` РїСЂРё РІРµСЂС‚РёРєР°Р»СЊРЅРѕРј СЃРєСЂРѕР»Р»Рµ Р·РѕРЅС‹.
   - РўРµРЅСЊ 18dp СЃ ambient/spot `primary`; С‚РёРїРѕРіСЂР°С„РёРєР°: РЅР°Р·РІР°РЅРёРµ `headlineLarge` Bold, Р°СЂС‚РёСЃС‚ `bodyLarge` (alpha 0.60); РІ С‚РµРєСЃС‚РѕРІРѕРј СЂРµР¶РёРјРµ вЂ” РјРёРЅРёР°С‚СЋСЂР° 56dp/16dp СЃ С‚РµРЅСЊСЋ 6dp Рё СЃРїРµР№СЃРµСЂРѕРј 14dp.
5. **РљРЅРѕРїРєРё СѓРїСЂР°РІР»РµРЅРёСЏ**:
   - Play/Pause 84dp (РјРѕСЂС„ СЂР°РґРёСѓСЃР° 42/30dp, РёРєРѕРЅРєР° 38dp, СЃРїРёРЅРЅРµСЂ 32dp), Prev/Next 56dp; СЂСЏРґ С†РµРЅС‚СЂРёСЂРѕРІР°РЅ `spacedBy(28.dp)`.
   - РќРѕРІС‹Р№ С…РµР»РїРµСЂ `rememberPressScale` вЂ” РїСЂСѓР¶РёРЅРЅС‹Р№ press-scale 0.90 РїСЂРё РЅР°Р¶Р°С‚РёРё (InteractionSource) РґР»СЏ РІСЃРµС… С‚СЂС‘С… РєРЅРѕРїРѕРє.
6. **РњРµР»РѕС‡Рё**: РїРёР»СЋР»Рё РґРµР№СЃС‚РІРёР№ вЂ” Р·Р°Р·РѕСЂ 4в†’8dp; СЃРїРµР№СЃРµСЂС‹ СЃРµРєС†РёР№ 6в†’10 Рё 12в†’16 (Р±РѕР»СЊС€Рµ РІРѕР·РґСѓС…Р°); loading-РїРѕР»РѕСЃРєР° РїСЂРѕРіСЂРµСЃСЃР° РїСЂРёРІРµРґРµРЅР° Рє С‚РѕР»С‰РёРЅРµ РґРѕСЂРѕР¶РєРё 8dp (С‚СЂРµРє `onSurface` alpha 0.12), С‚СЂРµРє СЃР»Р°Р№РґРµСЂР° `onSurface` alpha 0.16, РїРѕР»Р·СѓРЅРѕРє `onSurface`.
7. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/build.gradle.kts
app/src/main/java/com/facet/music/core/ui/components/PlayerSlider.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.7 вЂ” Р”РµСЂР·РєРёР№ СЂРµРґРёР·Р°Р№РЅ PlayerScreen: 3 РІР°СЂРёР°РЅС‚Р° РєРѕРјРїРѕРЅРѕРІРєРё, РѕСЃС‚СЂРѕРІ СѓРїСЂР°РІР»РµРЅРёСЏ, СЃР»Р°Р№РґРµСЂ-С‚РѕС‡РєРё (19.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **3 РІР°СЂРёР°РЅС‚Р° РєРѕРјРїРѕР·РёС†РёРё** (РїРµСЂРµРєР»СЋС‡Р°С‚РµР»СЊ `PLAYER_LAYOUT_VARIANT` РІ РЅР°С‡Р°Р»Рµ `PlayerScreen.kt`, Р·РЅР°С‡РµРЅРёСЏ 1/2/3):
   - Р’Р°СЂРёР°РЅС‚ 1 В«Р РµРґР°РєС†РёСЏВ» (РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ): РЅР°РєР»РѕРЅС‘РЅРЅР°СЏ РѕР±Р»РѕР¶РєР° 88% С€РёСЂРёРЅС‹ РїРѕ С†РµРЅС‚СЂСѓ (-5В°); СЃС‚РµРєР»СЏРЅРЅР°СЏ РєР°СЂС‚РѕС‡РєР° СЃ Display-РЅР°Р·РІР°РЅРёРµРј СЃРІРёСЃР°РµС‚ СЃ РЅРёР¶РЅРµРіРѕ РєСЂР°СЏ РѕР±Р»РѕР¶РєРё.
   - Р’Р°СЂРёР°РЅС‚ 2 В«РЎРјРµС‰РµРЅРёРµВ»: РѕР±Р»РѕР¶РєР° 62% С€РёСЂРёРЅС‹ СЃРІРёСЃР°РµС‚ Р·Р° РїСЂР°РІС‹Р№ РєСЂР°Р№ (+16dp, РЅР°РєР»РѕРЅ +7В°), РєР°СЂС‚РѕС‡РєР° СЃ РЅР°Р·РІР°РЅРёРµРј вЂ” СЃР»РµРІР°.
   - Р’Р°СЂРёР°РЅС‚ 3 В«РќР° РѕР±Р»РѕР¶РєРµВ»: РѕР±Р»РѕР¶РєР° 94%, РЅР°Р·РІР°РЅРёРµ Р»РµР¶РёС‚ РїСЂСЏРјРѕ РЅР° РЅРµР№ РІ РјР°С‚РѕРІРѕ-СЃС‚РµРєР»СЏРЅРЅРѕР№ РїР»Р°С€РєРµ (alpha 0.55).
2. **В«РЈС…РѕРґ РЅР° Р·Р°РґРЅРёР№ РїР»Р°РЅВ» РїСЂРё РІС…РѕРґРµ РІ С‚РµРєСЃС‚С‹**: РѕР±Р»РѕР¶РєР° вЂ” РїРѕСЃС‚РѕСЏРЅРЅС‹Р№ СЃР»РѕР№-Р·Р°РґРЅРёРє; РїСЂРё РїРµСЂРµРєР»СЋС‡РµРЅРёРё РѕРЅР° РїСЂСѓР¶РёРЅРЅРѕ СѓРјРµРЅСЊС€Р°РµС‚СЃСЏ РґРѕ 0.58, СѓС…РѕРґРёС‚ РІРЅРёР· РЅР° +26dp, С‚СѓСЃРєРЅРµРµС‚ РґРѕ alpha 0.30 Рё СѓС…РѕРґРёС‚ РІ blur 26dp (`Modifier.blur`, СЂРµР°Р»СЊРЅС‹Р№ Р±Р»СЋСЂ РЅР° Android 12+); РїРµСЂРµРґРЅРёР№ РїР»Р°РЅ РјРµРЅСЏРµС‚СЃСЏ С‡РµСЂРµР· `AnimatedContent` (spring slide + fade).
3. **РџР»Р°РІР°СЋС‰РёР№ В«РѕСЃС‚СЂРѕРІВ» СѓРїСЂР°РІР»РµРЅРёСЏ (`ExpressivePlayerComponents.kt`)**:
   - РљР°РїР»РµРІРёРґРЅР°СЏ С„РѕСЂРјР° (44dp СЃРІРµСЂС…Сѓ / 22dp СЃРЅРёР·Сѓ), С†РІРµС‚РЅР°СЏ С‚РµРЅСЊ РѕС‚ РїР°Р»РёС‚СЂС‹, СЂР°РјРєР° 1dp;
   - Р СЏРґ: СЃРєРѕСЂРѕСЃС‚СЊ (РєСЂСѓРі 44dp СЃ С‚РµРєСЃС‚РѕРј В«1Г—В»), РїСЂРµРґ/СЃР»РµРґ 52dp, Play 74dp СЃ РјРѕСЂС„РѕРј СЂР°РґРёСѓСЃР° (РєСЂСѓРі в†” СЃРєРІРёСЂРєР») Рё СЃРїРёРЅРЅРµСЂРѕРј Р±СѓС„РµСЂРёР·Р°С†РёРё, РѕС‡РµСЂРµРґСЊ (РєСЂСѓРі 44dp);
   - РЎРІРµСЂС…Сѓ Рє РѕСЃС‚СЂРѕРІСѓ В«РїСЂРёСЂРѕСЃВ» СЏР·С‹С‡РѕРє В«РўРµРєСЃС‚В» (РёРєРѕРЅРєР° Lyrics, РІС‹СЃС‚СѓРїР°РµС‚ РЅР° 19dp РЅР°Рґ РєСЂРѕРјРєРѕР№, РІ Р°РєС‚РёРІРЅРѕРј СЂРµР¶РёРјРµ РїРѕРґСЃРІРµС‡РёРІР°РµС‚СЃСЏ `primaryContainer`);
   - Press-scale 0.90 РЅР° РІСЃРµС… РєРЅРѕРїРєР°С… РѕСЃС‚СЂРѕРІР° (РѕР±С‰РёР№ С…РµР»РїРµСЂ `rememberPressScale`).
4. **РќРѕРІС‹Р№ СЃР»Р°Р№РґРµСЂ `ExpressivePlayerSlider` (`ExpressivePlayerSlider.kt`)**:
   - В«Р¦РµРїРѕС‡РєР° С‚РѕС‡РµРєВ»: РїСЂРѕР№РґРµРЅРЅС‹Рµ вЂ” РєСЂСѓРїРЅРµРµ Рё СЏСЂС‡Рµ, Р±СѓС„РµСЂРЅС‹Рµ вЂ” alpha 0.35, Р±СѓРґСѓС‰РёРµ вЂ” С‚СѓСЃРєР»С‹Рµ; Р±Р»РёР¶Р°Р№С€Р°СЏ Рє Р±РµРіСѓРЅРєСѓ С‚РѕС‡РєР° РїР»Р°РІРЅРѕ СЂР°Р·РґСѓРІР°РµС‚СЃСЏ;
   - РџРѕР»Р·СѓРЅРѕРє-РїРёР»СЋР»СЏ РїСЂСѓР¶РёРЅРЅРѕ В«РїРѕРґРїСЂС‹РіРёРІР°РµС‚В» РїСЂРё Р·Р°С…РІР°С‚Рµ (spring dampingRatio 0.30 СЃ РѕРІРµСЂС€СѓС‚РѕРј) Рё В«СЃРєРІРѕС€РёС‚СЃСЏВ»; РѕСЂРµРѕР» СЂР°СЃРєСЂС‹РІР°РµС‚СЃСЏ РІРјРµСЃС‚Рµ СЃ РѕС‚СЃРєРѕРєРѕРј.
5. **РЎС‚РµРєР»СЏРЅРЅС‹Р№ СЂРµР¶РёРј С‚РµРєСЃС‚РѕРІ**: С€Р°РїРєР°-РІРёС‚СЂРёРЅР° (РјРёРЅРёР°С‚СЋСЂР° 52dp + titleLarge) Рё РїРѕР»СѓРїСЂРѕР·СЂР°С‡РЅР°СЏ РїР°РЅРµР»СЊ СЃС‚СЂРѕРє (`surfaceContainerHigh` alpha 0.42вЂ“0.62, СЂР°РјРєРё 1dp) РїРѕРІРµСЂС… СЂР°Р·РјС‹С‚РѕР№ РѕР±Р»РѕР¶РєРё вЂ” СЌС„С„РµРєС‚ РјР°С‚РѕРІРѕРіРѕ СЃС‚РµРєР»Р°.
6. **РўРёРїРѕРіСЂР°С„РёРєР°**: РЅР°Р·РІР°РЅРёРµ вЂ” `displaySmall` (36sp Google Sans Flex Bold); РІРµСЂС…РЅСЏСЏ РїР°РЅРµР»СЊ вЂ” С‚РѕР»СЊРєРѕ labelSmall В«РЎР•Р™Р§РђРЎ РР“Р РђР•РўВ» (СѓР±СЂР°РЅ РґСѓР±Р»РёСЂСѓСЋС‰РёР№ Р·Р°РіРѕР»РѕРІРѕРє).
7. РЈРґР°Р»С‘РЅ СЃС‚Р°СЂС‹Р№ `PlayerSlider.kt` (Р·Р°РјРµРЅС‘РЅ РЅР° ExpressivePlayerSlider); Р»РѕРіРёРєР° ViewModel/Controller РЅРµ С‚СЂРѕРЅСѓС‚Р°.
8. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### РљР°Рє РїРѕСЃРјРѕС‚СЂРµС‚СЊ РІР°СЂРёР°РЅС‚С‹
Р’ `PlayerScreen.kt` РїРѕРјРµРЅСЏС‚СЊ `PLAYER_LAYOUT_VARIANT` (1/2/3) Рё РїРµСЂРµСЃРѕР±СЂР°С‚СЊ.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/core/ui/components/ExpressivePlayerSlider.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/ui/components/ExpressivePlayerComponents.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/ui/components/PlayerSlider.kt (СѓРґР°Р»С‘РЅ)
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.8 вЂ” РџРѕР»РёСЂРѕРІРєР° РєРѕРЅС‚СЂР°СЃС‚Р° РїР»РµРµСЂР° Рё СЌРєСЂР°РЅ РЅР°СЃС‚СЂРѕРµРє (19.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РџРѕР»РёСЂРѕРІРєР° PlayerScreen (РєРѕРЅС‚СЂР°СЃС‚ Рё РЅР°Р»РѕР¶РµРЅРёСЏ)**:
   - Р›РµР№Р±Р» В«РЎР•Р™Р§РђРЎ РР“Р РђР•РўВ» РїРѕР»СѓС‡РёР» СЏРІРЅС‹Р№ С†РІРµС‚ `onSurface` alpha 0.80 вЂ” Р±РѕР»СЊС€Рµ РЅРµ СЂР°СЃС‚РІРѕСЂСЏРµС‚СЃСЏ РЅР° РіСЂР°РґРёРµРЅС‚РЅРѕРј С„РѕРЅРµ.
   - РћС‚СЃС‚СѓРїС‹: Сѓ РѕСЃС‚СЂРѕРІР° СѓРїСЂР°РІР»РµРЅРёСЏ РІРµСЂС…РЅРёР№ РїР°РґРґРёРЅРі 24в†’30dp (Р·Р°РїР°СЃ РїРѕРґ СЏР·С‹С‡РѕРє В«РўРµРєСЃС‚В»), СЃРїРµР№СЃРµСЂ РїРѕРґ РѕСЃС‚СЂРѕРІРѕРј 14в†’18dp, РІ CoverForeground РґРѕР±Р°РІР»РµРЅ РіР°СЂР°РЅС‚РёСЂРѕРІР°РЅРЅС‹Р№ СЃРїРµР№СЃРµСЂ 20dp РїРѕРґ РєР°СЂС‚РѕС‡РєРѕР№, РІ LyricsForeground вЂ” 18dp РґРѕ РѕСЃС‚СЂРѕРІР° Рё РІРЅСѓС‚СЂРµРЅРЅРёР№ РІРµСЂС‚РёРєР°Р»СЊРЅС‹Р№ РїР°РґРґРёРЅРі РїР°РЅРµР»Рё СЃС‚СЂРѕРє, contentPadding СЃРїРёСЃРєР° С‚РµРєСЃС‚РѕРІ (16/24dp), РїРѕРґРїРёСЃСЊ В«LRCLIBВ» СЃ СѓРІРµР»РёС‡РµРЅРЅС‹Рј РѕС‚СЃС‚СѓРїРѕРј, РїР»Р°С€РєР° РѕС€РёР±РєРё вЂ” СЃ РІРµСЂС‚РёРєР°Р»СЊРЅС‹Рј РїР°РґРґРёРЅРіРѕРј.
   - РљРѕРЅС‚СЂР°СЃС‚ РјР°С‚РѕРІРѕРіРѕ СЃС‚РµРєР»Р° РІ С‚РµРєСЃС‚Р°С…: С€Р°РїРєР° РІРёС‚СЂРёРЅС‹ alpha 0.62в†’0.82, РїР°РЅРµР»СЊ СЃС‚СЂРѕРє 0.42в†’0.72 вЂ” СЃС‚СЂРѕРєРё РЅРµ СЃР»РёРІР°СЋС‚СЃСЏ СЃ СЂР°Р·РјС‹С‚РѕР№ РѕР±Р»РѕР¶РєРѕР№.
2. **РЎР»Р°Р№РґРµСЂ вЂ” СЃРїР»РѕС€РЅР°СЏ РІРѕР»РЅР°**: `ExpressivePlayerSlider` РїРµСЂРµРІРµРґС‘РЅ СЃ В«С†РµРїРѕС‡РєРё С‚РѕС‡РµРєВ» РЅР° СЃРїР»РѕС€РЅСѓСЋ СЃРєСЂСѓРіР»С‘РЅРЅСѓСЋ РґРѕСЂРѕР¶РєСѓ 8dp (С‚СЂРµРє в†’ Р±СѓС„РµСЂ `activeColor` alpha 0.28 в†’ РїСЂРѕРёРіСЂР°РЅРЅР°СЏ С‡Р°СЃС‚СЊ), РїРѕР»Р·СѓРЅРѕРє-РїРёР»СЋР»СЏ 10Г—30dp СЃ РїСЂСѓР¶РёРЅРЅС‹Рј СѓРІРµР»РёС‡РµРЅРёРµРј Рё РѕСЂРµРѕР»РѕРј РїСЂРё Р·Р°С…РІР°С‚Рµ (РєР°Рє РІ СЂР°Р±РѕС‡РµР№ РІРµСЂСЃРёРё Р¤Р°Р·С‹ 4.6, РЅРѕ РІ Expressive-РѕС„РѕСЂРјР»РµРЅРёРё).
3. **РЎР»РѕР№ РЅР°СЃС‚СЂРѕРµРє**:
   - `AppSettings` (`dynamicCoverColors`, `accentColorArgb`) Рё РёРЅС‚РµСЂС„РµР№СЃ `SettingsRepository`.
   - `SettingsRepositoryImpl` РЅР° SharedPreferences (`facet_settings`), Р·Р°РїРёСЃСЊ РІ IO-РґРёСЃРїРµС‚С‡РµСЂРµ, `@Binds` РІ `RepositoryModule`.
4. **РўРµРјР° РѕС‚ Р°РєС†РµРЅС‚РЅРѕРіРѕ С†РІРµС‚Р°**:
   - `Theme.kt`: С„СѓРЅРєС†РёСЏ `accentColorScheme(accent, darkTheme)` РіРµРЅРµСЂРёСЂСѓРµС‚ primary-РіСЂСѓРїРїСѓ РёР· seed С‡РµСЂРµР· HSL (secondary/tertiary СЃРѕ СЃРґРІРёРіРѕРј РѕС‚С‚РµРЅРєР° 40В°, РЅРµР№С‚СЂР°Р»СЊРЅС‹Рµ РїРѕРІРµСЂС…РЅРѕСЃС‚Рё вЂ” РёР· С„РёРєСЃРёСЂРѕРІР°РЅРЅС‹С… СЃС…РµРј); `FacetTheme` РїРѕР»СѓС‡РёР» РїР°СЂР°РјРµС‚СЂ `accentColor: Color?` СЃ РїСЂРёРѕСЂРёС‚РµС‚РѕРј РЅР°Рґ Dynamic Color.
   - `MainActivity` РІРЅРµРґСЂСЏРµС‚ `SettingsRepository` Рё РїРµСЂРµРґР°С‘С‚ Р°РєС†РµРЅС‚ РІ С‚РµРјСѓ, РєРѕРіРґР° РґРёРЅР°РјРёС‡РµСЃРєРёРµ С†РІРµС‚Р° РѕР±Р»РѕР¶РєРё РІС‹РєР»СЋС‡РµРЅС‹.
5. **РџР»РµРµСЂ Рё РЅР°СЃС‚СЂРѕР№РєРё РґРёРЅР°РјРёРєРё**:
   - `PlayerViewModel.appSettings` (StateFlow РёР· СЂРµРїРѕР·РёС‚РѕСЂРёСЏ).
   - `PlayerScreen` РїСЂРё РІС‹РєР»СЋС‡РµРЅРЅРѕР№ РґРёРЅР°РјРёРєРµ РЅРµ РіСЂСѓР·РёС‚ РїР°Р»РёС‚СЂСѓ РѕР±Р»РѕР¶РєРё (`artworkUrl = null`) вЂ” С„РѕРЅ Рё Р°РєС†РµРЅС‚С‹ РїР»РµРµСЂР° СЃС‚СЂРѕСЏС‚СЃСЏ РѕС‚ РІС‹Р±СЂР°РЅРЅРѕРіРѕ Р°РєС†РµРЅС‚Р° С‚РµРјС‹.
6. **Р­РєСЂР°РЅ РЅР°СЃС‚СЂРѕРµРє (`SettingsScreen` + `SettingsViewModel`)**:
   - РЎРµРєС†РёСЏ В«РћР¤РћР РњР›Р•РќРР•В»: РїРµСЂРµРєР»СЋС‡Р°С‚РµР»СЊ В«Р”РёРЅР°РјРёС‡РµСЃРєРёРµ С†РІРµС‚Р° РѕР±Р»РѕР¶РєРёВ» Рё РїР°Р»РёС‚СЂР° РёР· 6 Р±Р°Р·РѕРІС‹С… Р°РєС†РµРЅС‚РѕРІ (С„РёРѕР»РµС‚РѕРІС‹Р№, СЃРёРЅРёР№, Р±РёСЂСЋР·РѕРІС‹Р№, Р·РµР»С‘РЅС‹Р№, РѕСЂР°РЅР¶РµРІС‹Р№, РєСЂР°СЃРЅС‹Р№) РІ РІРёРґРµ РєСЂСѓР¶РєРѕРІ; РІС‹Р±СЂР°РЅРЅС‹Р№ СЂР°Р·РґСѓРІР°РµС‚СЃСЏ РїСЂСѓР¶РёРЅРѕР№ Рё РїРѕР»СѓС‡Р°РµС‚ РєРѕРЅС‚СѓСЂ СЃ РіР°Р»РѕС‡РєРѕР№, РїСЂРё РІРєР»СЋС‡С‘РЅРЅРѕР№ РґРёРЅР°РјРёРєРµ РїР°Р»РёС‚СЂР° РїСЂРёРіР»СѓС€РµРЅР° Рё РЅРµР°РєС‚РёРІРЅР°.
   - РњР°СЂС€СЂСѓС‚ `Screen.Settings` вЂ” РїРѕР»РЅРѕСЌРєСЂР°РЅРЅС‹Р№ (РЅРёР¶РЅСЏСЏ РїР°РЅРµР»СЊ СЃРєСЂС‹С‚Р°), РєРЅРѕРїРєР°-С€РµСЃС‚РµСЂС‘РЅРєР° РґРѕР±Р°РІР»РµРЅР° РІ С€Р°РїРєСѓ `HomeScreen`.
7. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` Р·Р°РІРµСЂС€РёР»Р°СЃСЊ СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
app/src/main/java/com/facet/music/feature/player/PlayerViewModel.kt
app/src/main/java/com/facet/music/core/ui/components/ExpressivePlayerSlider.kt
app/src/main/java/com/facet/music/data/repository/SettingsRepository.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/local/SettingsRepositoryImpl.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/di/RepositoryModule.kt
app/src/main/java/com/facet/music/core/ui/theme/Theme.kt
app/src/main/java/com/facet/music/core/ui/theme/AccentPalette.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/MainActivity.kt
app/src/main/java/com/facet/music/feature/settings/SettingsScreen.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/feature/settings/SettingsViewModel.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/feature/home/HomeScreen.kt
app/src/main/java/com/facet/music/navigation/Screen.kt
app/src/main/java/com/facet/music/navigation/AppNavGraph.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.9 вЂ” Expressive-РєР°СЂР°РѕРєРµ: В«Р¶РёРґРєРёРµ С‡РµСЂРЅРёР»Р°В» Рё РїРµСЂРµР»РёРІ (19.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РљР°СЂР°РѕРєРµ-СЃС‚СЂРѕРєР° В«Р¶РёРґРєРёРµ С‡РµСЂРЅРёР»Р°В»** (`LyricLineItem` вЂ” РІС‹РЅРµСЃРµРЅ РІ РѕС‚РґРµР»СЊРЅС‹Р№ РєРѕРјРїРѕР·Р°Р±Р»):
   - Р’РјРµСЃС‚Рѕ Р¶С‘СЃС‚РєРѕР№ РіСЂР°РЅРёС†С‹ Р·Р°Р»РёРІРєРё вЂ” smoothstep-РєСЂРѕРјРєР° (РїРѕР»СѓС€РёСЂРёРЅР° 2.8%): РїРµСЂРµС…РѕРґ В«РїСЂРѕРїРµС‚Рѕ / РЅРµ РїСЂРѕРїРµС‚РѕВ» СЃС‚Р°Р» РјСЏРіРєРёРј.
   - **РўР»РµСЋС‰РёР№ С„СЂРѕРЅС‚**: С‚С‘РјРЅРѕРµ СЃРІРµС‡РµРЅРёРµ РїСЂСЏРјРѕ РЅР° РіСЂР°РЅРёС†Рµ Р·Р°Р»РёРІРєРё, РїРѕРґРєСЂР°С€РµРЅРЅРѕРµ `highlightColor` (primary РїР°Р»РёС‚СЂС‹ РѕР±Р»РѕР¶РєРё РёР»Рё Р°РєС†РµРЅС‚ С‚РµРјС‹).
   - **РЁРёРјРјРµСЂ-РїРµСЂРµР»РёРІ**: СЃРІРµС‚Р»Р°СЏ РІРѕР»РЅР° (РїРѕР»СѓС€РёСЂРёРЅР° 9%) РїСЂРѕР±РµРіР°РµС‚ РїРѕ СЃС‚СЂРѕРєРµ Р·Р° 2800 РјСЃ вЂ” С‚РѕР»СЊРєРѕ Сѓ Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё Рё С‚РѕР»СЊРєРѕ РєРѕРіРґР° РёРіСЂР°РµС‚ РјСѓР·С‹РєР°.
   - Р“СЂР°РґРёРµРЅС‚ СЃС‚СЂРѕРёС‚СЃСЏ РїРѕ РєСЂРёС‚РёС‡РµСЃРєРёРј С‚РѕС‡РєР°Рј (РєСЂРѕРјРєР° + С„СЂРѕРЅС‚ РІРѕР»РЅС‹) СЃ РєРѕРјРїРѕРЅРµРЅС‚РЅС‹Рј СЃРјРµС€РµРЅРёРµРј С†РІРµС‚РѕРІ вЂ” РґРѕ 8 СЃС‚РѕРїРѕРІ.
2. **Р•РґРёРЅР°СЏ РїСЂСѓР¶РёРЅР° С„РѕРєСѓСЃР°** (dampingRatio 0.62 вЂ” Р»С‘РіРєРёР№ Expressive-РѕРІРµСЂС€СѓС‚) РІРµРґС‘С‚ РІСЃС‘ СЃСЂР°Р·Сѓ:
   - РІРµСЃ 600 в†’ 800, РЅР°СЃС‹С‰РµРЅРЅРѕСЃС‚СЊ РѕСЃС‚Р°Р»СЊРЅС‹С… СЃС‚СЂРѕРє вЂ” 600;
   - РјР°СЃС€С‚Р°Р± 1.0 в†’ 1.05 РѕС‚ Р»РµРІРѕРіРѕ РєСЂР°СЏ (`TransformOrigin(0f, 0.5f)` вЂ” С‚РµРєСЃС‚ РЅРµ В«СѓРµР·Р¶Р°РµС‚В» РїСЂРё СЂРѕСЃС‚Рµ);
   - СЃРІРµС‡РµРЅРёРµ primary: alpha 0 в†’ 0.55, blur 3 в†’ 16;
   - Р°Р»СЊС„С‹: РЅРµР°РєС‚РёРІРЅС‹Рµ СЃС‚СЂРѕРєРё вЂ” **0.4** (РєР°Рє РїСЂРѕСЃРёР» РїРѕР»СЊР·РѕРІР°С‚РµР»СЊ), В«РЅРµРїСЂРѕРїРµС‚РѕРµВ» Сѓ Р°РєС‚РёРІРЅРѕР№ вЂ” 0.62, В«РїСЂРѕРїРµС‚РѕРµВ» вЂ” 1.0.
3. **РџСЂРѕРёР·РІРѕРґРёС‚РµР»СЊРЅРѕСЃС‚СЊ**:
   - РїСЂРѕРіСЂРµСЃСЃ Р·Р°Р»РёРІРєРё С‡РёС‚Р°РµС‚СЃСЏ РўРћР›Р¬РљРћ Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРѕР№ (СѓСЃР»РѕРІРЅР°СЏ РІРµС‚РєР° `when` РІС‹Р·С‹РІР°РµС‚ Р»СЏРјР±РґСѓ РїСЂРѕРіСЂРµСЃСЃР° вЂ” РЅРµР°РєС‚РёРІРЅС‹Рµ РЅРµ РїРѕРґРїРёСЃР°РЅС‹ РЅР° 150-РјСЃ РѕРїСЂРѕСЃ РїРѕР·РёС†РёРё);
   - С€РёРјРјРµСЂ Р¶РёРІС‘С‚ РІ `rememberInfiniteTransition` РІРЅСѓС‚СЂРё СѓСЃР»РѕРІРёСЏ В«Р°РєС‚РёРІРЅР°СЏ + РёРіСЂР°РµС‚В» вЂ” РЅР° РїР°СѓР·Рµ Р°РЅРёРјР°С†РёСЏ РЅРµ РєСЂСѓС‚РёС‚СЃСЏ;
   - Сѓ РїСЂРѕС€Р»С‹С…/Р±СѓРґСѓС‰РёС… СЃС‚СЂРѕРє РєРёСЃС‚СЊ РєРѕРЅСЃС‚Р°РЅС‚РЅР° (РїРѕР»РЅРѕСЃС‚СЊСЋ С‚СѓСЃРєР»Р°СЏ), РїРµСЂРµСЂРёСЃРѕРІРєРё РїСЂРё СЃРјРµРЅРµ СЃС‚СЂРѕРєРё РјРёРЅРёРјР°Р»СЊРЅС‹.
4. **РђРІС‚РѕСЃРєСЂРѕР»Р»**: С€С‚Р°С‚РЅР°СЏ РїСЂСѓР¶РёРЅРЅР°СЏ Р°РЅРёРјР°С†РёСЏ `animateScrollToItem` foundation (РєР°СЃС‚РѕРјРЅС‹Р№ `animationSpec` Сѓ СЌС‚РѕРіРѕ API РѕС‚СЃСѓС‚СЃС‚РІСѓРµС‚).
5. **РџСЂРѕР±СЂРѕСЃ `isPlaying`**: PlayerScreen в†’ ExpressiveHeroZone в†’ LyricsForeground в†’ LyricsListView (СѓРїСЂР°РІР»СЏРµС‚ РїРµСЂРµР»РёРІРѕРј).
6. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.10 вЂ” Р‘Р»Р°РіРѕСЂРѕРґРЅРѕРµ РєР°СЂР°РѕРєРµ: С†РµРЅС‚СЂРёСЂРѕРІР°РЅРёРµ СЃС‚СЂРѕРєРё Рё РјСЏРіРєРёР№ СЃС‚РёР»СЊ (19.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Р¦РµРЅС‚СЂРёСЂРѕРІР°РЅРёРµ Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё, РєР°Рє РІ Apple Music**:
   - `LazyColumn` РёР·РјРµСЂСЏРµС‚ СЃРІРѕСЋ РІРёРґРёРјСѓСЋ РІС‹СЃРѕС‚Сѓ С‡РµСЂРµР· `onSizeChanged`;
   - `contentPadding` = (РІС‹СЃРѕС‚Р°/2 в€’ РїРѕР»СЃС‚СЂРѕРєРё) СЃРІРµСЂС…Сѓ Рё СЃРЅРёР·Сѓ вЂ” Р»СЋР±СѓСЋ СЃС‚СЂРѕРєСѓ РјРѕР¶РЅРѕ РїРѕСЃС‚Р°РІРёС‚СЊ РІ СЃР°РјС‹Р№ С†РµРЅС‚СЂ СЃРїРёСЃРєР°;
   - Р°РІС‚РѕСЃРєСЂРѕР»Р» `animateScrollToItem(idx, scrollOffset = -С†РµРЅС‚СЂ)` вЂ” Р°РєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР° РІСЃРµРіРґР° РІСЃС‚Р°С‘С‚ СЂРѕРІРЅРѕ РїРѕ С†РµРЅС‚СЂСѓ РІРёРґРёРјРѕР№ РѕР±Р»Р°СЃС‚Рё;
   - РїСЂРё РѕС‚РєСЂС‹С‚РёРё С‚РµРєСЃС‚РѕРІ Рё СЃРјРµРЅРµ С‚СЂРµРєР° вЂ” РјРіРЅРѕРІРµРЅРЅС‹Р№ `scrollToItem` Р±РµР· В«РїСЂРѕР»С‘С‚Р°В» С‡РµСЂРµР· РІРµСЃСЊ СЃРїРёСЃРѕРє (`activeIndex` СЃР±СЂР°СЃС‹РІР°РµС‚СЃСЏ РїСЂРё СЃРјРµРЅРµ С‚РµРєСЃС‚Р°).
2. **РЎРјСЏРіС‡РµРЅРёРµ СЃС‚РёР»СЏ** (РІРјРµСЃС‚Рѕ В«РєРёСЃР»РѕС‚РЅРѕРіРѕВ» вЂ” СЃРїРѕРєРѕР№РЅРѕРµ, РґРІРѕСЂСЏРЅСЃРєРѕРµ):
   - РїСЂСѓР¶РёРЅР° С„РѕРєСѓСЃР°: `dampingRatio` 0.62 в†’ **0.85** вЂ” СЃС‚СЂРѕРєР° РїР»Р°РІРЅРѕ СЂР°СЃС‚С‘С‚ Р±РµР· РїРѕРґРїСЂС‹РіРёРІР°РЅРёСЏ;
   - СЃРІРµС‡РµРЅРёРµ: blur 16 в†’ **8**, alpha 0.55 в†’ **0.2** вЂ” РјСЏРіРєРѕРµ РІС‹РґРµР»РµРЅРёРµ РІРјРµСЃС‚Рѕ РіРѕСЂРµРЅРёСЏ;
   - РІРµСЃ С€СЂРёС„С‚Р°: 600в†’800 в†’ **500в†’700** вЂ” РјСЏРіС‡Рµ Рё С‡РёС‚Р°РµРјРµРµ;
   - С€РёРјРјРµСЂ РµРґРІР° Р·Р°РјРµС‚РЅС‹Р№: РёРЅС‚РµРЅСЃРёРІРЅРѕСЃС‚СЊ 0.5 в†’ **0.2**, РїРѕР»РѕСЃР° С€РёСЂРµ (0.12) вЂ” РїСЂРѕСЃС‚Рѕ Р»С‘РіРєРёР№ Р±Р»РёРє;
   - С‚Р»РµСЋС‰РёР№ С„СЂРѕРЅС‚ С‡РµСЂРЅРёР» РїСЂРёРіР»СѓС€С‘РЅ: `tipColor` alpha **0.7**.
3. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.11 вЂ” РџРѕС‡РёРЅРєР° С‚РµРєСЃС‚РѕРІ, РІРѕР·РІСЂР°С‚ РІРѕР»РЅС‹ Рё СЃРѕСЃРµРґРЅРёРµ СЃС‚СЂРѕРєРё (19.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РЎРєСЂРѕР»Р» С‚РµРєСЃС‚РѕРІ**:
   - `LazyColumn.contentPadding` вЂ” СЃС‚СЂРѕРіРѕ `PaddingValues(top = 50.dp, bottom = 50.dp)`;
   - Р°РІС‚РѕСЃРєСЂРѕР»Р» `animateScrollToItem(idx, scrollOffset = -С†РµРЅС‚СЂ)` вЂ” Р°РєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР° РІСЃС‚Р°С‘С‚ РїРѕ С†РµРЅС‚СЂСѓ РІРёРґРёРјРѕР№ РѕР±Р»Р°СЃС‚Рё, 50dp-РѕС‚СЃС‚СѓРїС‹ РЅРµ РґР°СЋС‚ СѓРїРёСЂР°С‚СЊСЃСЏ РІ РіСЂР°РЅРёС†С‹ РєР°СЂС‚РѕС‡РєРё;
   - `Text` СЏРІРЅРѕ `softWrap = true`, `maxLines = Int.MAX_VALUE` вЂ” РґР»РёРЅРЅС‹Рµ СЃС‚СЂРѕРєРё РЅРµ РѕР±СЂРµР·Р°СЋС‚СЃСЏ;
   - РїРѕРґРїРёСЃСЊ В«РЎРёРЅС…СЂРѕРЅРЅС‹Р№ С‚РµРєСЃС‚ В· LRCLIBВ» вЂ” Р·Р° РїСЂРµРґРµР»Р°РјРё LazyColumn (Р·Р°РєСЂРµРїР»РµРЅР° РїРѕРґ СЃРїРёСЃРєРѕРј).
2. **Р’РѕР·РІСЂР°С‚ РІРѕР»РЅС‹**: `ExpressivePlayerSlider` СЃРЅРѕРІР° РІРѕР»РЅРѕРІРѕР№ вЂ” Р°РЅРёРјРёСЂРѕРІР°РЅРЅР°СЏ СЃРёРЅСѓСЃРѕРёРґР° (С„Р°Р·Р° 1600 РјСЃ, С‡РёС‚Р°РµС‚СЃСЏ РІ С„Р°Р·Рµ РѕС‚СЂРёСЃРѕРІРєРё, Р±РµР· СЂРµРєРѕРјРїРѕР·РёС†РёР№), СЃСѓР¶РµРЅРёРµ Рє РєСЂР°СЏРј, РїСЂРѕРёРіСЂР°РЅРЅР°СЏ С‡Р°СЃС‚СЊ вЂ” Р°РєС†РµРЅС‚РЅР°СЏ РІРѕР»РЅР° СЃР»РµРІР° РЅР°РїСЂР°РІРѕ, Р±СѓС„РµСЂ вЂ” РїР»РѕС‚РЅРµРµ, Р±РµРіСѓРЅРѕРє-С‚РѕС‡РєР° СЃ РїСЂСѓР¶РёРЅРѕР№ Рё РѕСЂРµРѕР»РѕРј. РќР° РїР°СѓР·Рµ Р°РјРїР»РёС‚СѓРґР° РїР»Р°РІРЅРѕ (500 РјСЃ) СѓС…РѕРґРёС‚ РІ 0 вЂ” РІРѕР»РЅР° РІС‹РїСЂСЏРјР»СЏРµС‚СЃСЏ РІ Р»РёРЅРёСЋ. `isPlaying` РїСЂРѕР±СЂРѕС€РµРЅ РёР· `PlayerProgressSection`.
3. **РљР°СЂР°РѕРєРµ**: Р°РєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР° Р·Р°РєСЂР°С€РёРІР°РµС‚СЃСЏ `Brush.linearGradient` СЃР»РµРІР° РЅР°РїСЂР°РІРѕ (smoothstep-РєСЂРѕРјРєР° + С‚Р»РµСЋС‰РёР№ С„СЂРѕРЅС‚ СЃРѕС…СЂР°РЅРµРЅС‹); Р°Р»СЊС„С‹ СЃС‚СЂРѕРє: РЅРµР°РєС‚РёРІРЅС‹Рµ **0.4**, СЃРѕСЃРµРґРЅРёРµ СЃ Р°РєС‚РёРІРЅРѕР№ **0.7** (РЅРѕРІС‹Р№ РїР°СЂР°РјРµС‚СЂ `isNeighbor`).
4. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/core/ui/components/ExpressivePlayerSlider.kt
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.12 вЂ” Р§РёСЃС‚Р°СЏ M3-С‚РёРїРѕРіСЂР°С„РёРєР° С‚РµРєСЃС‚РѕРІ РІРјРµСЃС‚Рѕ РєР°СЂР°РѕРєРµ (20.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РЈРґР°Р»С‘РЅ РєР°СЂР°РѕРєРµ-СЌС„С„РµРєС‚**: СѓР±СЂР°РЅС‹ `Brush.linearGradient`, СЂР°СЃС‡С‘С‚ РїСЂРѕРіСЂРµСЃСЃР° Р·Р°РєСЂР°С€РёРІР°РЅРёСЏ, smoothstep-РєСЂРѕРјРєР°, С‚Р»РµСЋС‰РёР№ С„СЂРѕРЅС‚, С€РёРјРјРµСЂ Рё РІСЃРµ С…РµР»РїРµСЂС‹ (`buildKaraokeBrush`, `karaokeColorAt`, `smoothBlend`, `blendColors`, РєРѕРЅСЃС‚Р°РЅС‚С‹ `KARAOKE_*`).
2. **Р”РІР° СЃРѕСЃС‚РѕСЏРЅРёСЏ СЃС‚СЂРѕРєРё**:
   - Р°РєС‚РёРІРЅР°СЏ вЂ” РєСЂСѓРїРЅРµРµ (Р°РЅРёРјР°С†РёСЏ 16в†’22sp, РєР°Рє `titleLarge`), `SemiBold` (400в†’600), Р°РєС†РµРЅС‚РЅС‹Р№ С†РІРµС‚ РїР°Р»РёС‚СЂС‹ (РїСЂРё С‚С‘РјРЅРѕРј Р°РєС†РµРЅС‚Рµ вЂ” СЏСЂРєРёР№ `onSurface` РїРѕ РїРѕСЂРѕРіСѓ luminance 0.35);
   - РЅРµР°РєС‚РёРІРЅС‹Рµ СЃРёРЅС…СЂРѕРЅРЅРѕРіРѕ С‚РµРєСЃС‚Р° вЂ” `bodyLarge` 16sp, `Normal`, `onSurfaceVariant` alpha 0.5; РѕР±С‹С‡РЅС‹Р№ (РЅРµСЃРёРЅС…СЂРѕРЅРЅС‹Р№) С‚РµРєСЃС‚ вЂ” РїРѕР»РЅРѕСЃС‚СЊСЋ С‡РёС‚Р°РµРјС‹Р№ `onSurfaceVariant`.
3. **РџР»Р°РІРЅС‹Рµ РїРµСЂРµС…РѕРґС‹**: `animateColorAsState` (320 РјСЃ) РґР»СЏ С†РІРµС‚Р°, `animateFloatAsState` (РїСЂСѓР¶РёРЅР° 0.8) РґР»СЏ СЂР°Р·РјРµСЂР° Рё `tween` РґР»СЏ РІРµСЃР°; СЃС‚СЂРѕРєР° СЂР°СЃС‚С‘С‚ Рё РјРµРЅСЏРµС‚ С†РІРµС‚ Р±РµР· СЂРµР·РєРѕРіРѕ СЃРєР°С‡РєР°.
4. **РђРІС‚РѕСЃРєСЂРѕР»Р»**: РѕРїСЂРѕСЃ РїРѕР·РёС†РёРё РѕР±РЅРѕРІР»СЏРµС‚ С‚РѕР»СЊРєРѕ `activeIndex`, Р° РїСЂРѕРєСЂСѓС‚РєР° РІС‹РЅРµСЃРµРЅР° РІ `LaunchedEffect(activeIndex, viewportHeightPx)` вЂ” `animateScrollToItem` СЃ С†РµРЅС‚СЂРёСЂСѓСЋС‰РёРј `scrollOffset`; РїРµСЂРІРѕРµ РїРѕР·РёС†РёРѕРЅРёСЂРѕРІР°РЅРёРµ вЂ” РјРіРЅРѕРІРµРЅРЅС‹Р№ `scrollToItem` (Р±РµР· РїСЂРѕР»С‘С‚Р° С‡РµСЂРµР· РІРµСЃСЊ С‚РµРєСЃС‚).
5. **Р§РёСЃС‚РєР°**: РёР· С†РµРїРѕС‡РєРё С‚РµРєСЃС‚РѕРІ СѓР±СЂР°РЅС‹ РЅРµРёСЃРїРѕР»СЊР·СѓРµРјС‹Рµ `isPlaying`, `activeProgress`, `isPast`, `isNeighbor`, Р·Р°РјРµСЂ С€РёСЂРёРЅС‹ С‚РµРєСЃС‚Р° Рё `graphicsLayer`-РјР°СЃС€С‚Р°Р±.
6. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.13 вЂ” РЎС‚Р°Р±РёР»СЊРЅС‹Р№ Р»РµР№Р°СѓС‚ Рё РєР°СЂР°РѕРєРµ В«РїРѕ СЃР»РѕРІР°РјВ» (20.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РЈР±СЂР°РЅС‹ РїСЂС‹Р¶РєРё Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё**: `fontSize` Р±РѕР»СЊС€Рµ РЅРµ Р°РЅРёРјРёСЂСѓРµС‚СЃСЏ вЂ” Сѓ РІСЃРµС… СЃС‚СЂРѕРє Р±Р°Р·РѕРІС‹Рµ 18sp / lineHeight 24sp. РђРєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР° СЂР°СЃС‚С‘С‚ `graphicsLayer`-РјР°СЃС€С‚Р°Р±РѕРј 1.0 в†’ 1.3 (РїСЂСѓР¶РёРЅР° 0.8, `TransformOrigin(0f, 0.5f)`); РІС‹СЃРѕС‚Р° Р»РµР№Р°СѓС‚Р° Рё РїРѕР·РёС†РёСЏ СЃРїРёСЃРєР° РЅРµ РјРµРЅСЏСЋС‚СЃСЏ.
2. **РљРѕРЅС‚СЂР°СЃС‚ РЅРµР°РєС‚РёРІРЅРѕРіРѕ С‚РµРєСЃС‚Р°**: `onSurfaceVariant` Р±РµР· Р°Р»СЊС„С‹ (РІРјРµСЃС‚Рѕ alpha 0.5) вЂ” СЃС‚СЂРѕРєРё С‡РёС‚Р°РµРјС‹, РЅРѕ РјРµРЅРµРµ Р°РєС†РµРЅС‚РЅС‹.
3. **РљР°СЂР°РѕРєРµ В«РїРѕ СЃР»РѕРІР°РјВ»**:
   - СЃС‚СЂРѕРєР° СЂР°Р·Р±РёРІР°РµС‚СЃСЏ РЅР° СЃР»РѕРІР° `split(" ")` Рё РІС‹РІРѕРґРёС‚СЃСЏ С‡РµСЂРµР· `FlowRow` (РїРµСЂРµРЅРѕСЃ РґР»РёРЅРЅС‹С… СЃС‚СЂРѕРє СЃРѕС…СЂР°РЅС‘РЅ);
   - РІ `LyricsListView` СЃРЅРѕРІР° СЃС‡РёС‚Р°РµС‚СЃСЏ `activeProgress = (РїРѕР·РёС†РёСЏ в€’ РЅР°С‡Р°Р»Рѕ СЃС‚СЂРѕРєРё) / РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊ СЃС‚СЂРѕРєРё`;
   - СЃР»РѕРІРѕ РѕРєСЂР°С€РёРІР°РµС‚СЃСЏ РІ `highlightColor`, РµСЃР»Рё `index < progress Г— words.size`, РёРЅР°С‡Рµ вЂ” РІ `onSurfaceVariant`;
   - С†РІРµС‚ РєР°Р¶РґРѕРіРѕ СЃР»РѕРІР° Р°РЅРёРјРёСЂСѓРµС‚СЃСЏ `animateColorAsState` (180 РјСЃ); РїСЂРѕРіСЂРµСЃСЃ С‡РёС‚Р°РµС‚ С‚РѕР»СЊРєРѕ Р°РєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР°.
4. **РђРІС‚РѕСЃРєСЂРѕР»Р»**: `LaunchedEffect(activeIndex, viewportHeightPx)` СЃ С†РµРЅС‚СЂРёСЂСѓСЋС‰РёРј `scrollOffset` СЃРѕС…СЂР°РЅС‘РЅ; РїРѕСЃС‚РѕСЏРЅРЅР°СЏ РІС‹СЃРѕС‚Р° СЃС‚СЂРѕРє РёСЃРєР»СЋС‡Р°РµС‚ РґС‘СЂРіР°РЅСЊРµ РїСЂРё СЃРјРµРЅРµ РёРЅРґРµРєСЃР°.
5. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.14 вЂ” Р’РѕР·РІСЂР°С‚ СЃС‚Р°РЅРґР°СЂС‚РЅРѕРіРѕ text layout Рё РЅРёР¶РЅРёР№ РѕС‚СЃС‚СѓРї (20.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РЈР±СЂР°РЅРѕ РјР°СЃС€С‚Р°Р±РёСЂРѕРІР°РЅРёРµ**: `graphicsLayer`-scale 1.3 РїРѕР»РЅРѕСЃС‚СЊСЋ СѓРґР°Р»С‘РЅ РёР· `LyricLineItem` вЂ” С‚РµРєСЃС‚ Р±РѕР»СЊС€Рµ РЅРµ РІС‹С…РѕРґРёС‚ Р·Р° РїСЂР°РІС‹Р№ РєСЂР°Р№, `FlowRow` РєРѕСЂСЂРµРєС‚РЅРѕ РїРµСЂРµРЅРѕСЃРёС‚ СЃР»РѕРІР°.
2. **РђРєС†РµРЅС‚ Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё Р±РµР· РјР°СЃС€С‚Р°Р±Р°**:
   - Р°РєС‚РёРІРЅР°СЏ вЂ” **20sp, SemiBold**; РЅРµРїСЂРѕРїРµС‚С‹Рµ СЃР»РѕРІР° `onSurface` (Р±РµР»С‹Рµ), РїСЂРѕРїРµС‚С‹Рµ РїРѕ РїСЂРѕРіСЂРµСЃСЃСѓ РїР»Р°РІРЅРѕ СѓС…РѕРґСЏС‚ РІ Р°РєС†РµРЅС‚ `highlightColor`;
   - РЅРµР°РєС‚РёРІРЅС‹Рµ вЂ” **18sp, Normal**, `onSurfaceVariant` Р±РµР· Р°Р»СЊС„С‹;
   - РѕР±С‰РёР№ `lineHeight = 26.sp` СЃС‚Р°Р±РёР»РёР·РёСЂСѓРµС‚ РІС‹СЃРѕС‚Сѓ СЃС‚СЂРѕРєРё.
3. **РќРёР¶РЅРёР№ РѕС‚СЃС‚СѓРї СЃРїРёСЃРєР°**: `contentPadding = PaddingValues(top = 50.dp, bottom = 100.dp)` вЂ” РїРѕСЃР»РµРґРЅСЏСЏ СЃС‚СЂРѕРєР° РЅРµ Р·Р°С…РѕРґРёС‚ РїРѕРґ РїРѕРґРїРёСЃСЊ Рё РЅРёР¶РЅСЋСЋ РїР°РЅРµР»СЊ.
4. **РџРѕРґРїРёСЃСЊ** В«РЎРёРЅС…СЂРѕРЅРЅС‹Р№ С‚РµРєСЃС‚ В· LRCLIBВ» РѕСЃС‚Р°С‘С‚СЃСЏ РІРЅРµ `LazyColumn` вЂ” РїРѕРґ СЃРїРёСЃРєРѕРј, РїСЂРёР¶Р°С‚Р° Рє РЅРёР·Сѓ РєР°СЂС‚РѕС‡РєРё Рё РЅРµ РїРµСЂРµРєСЂС‹РІР°РµС‚ СЃРєСЂРѕР»Р».
5. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.15 вЂ” Р“РёР±СЂРёРґ: РїР»Р°РІРЅС‹Р№ РіСЂР°РґРёРµРЅС‚ РїСЂРё СЃС‚Р°Р±РёР»СЊРЅРѕРј Р»РµР№Р°СѓС‚Рµ (20.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Р’РѕР·РІСЂР°С‰РµРЅР° РїР»Р°РІРЅР°СЏ Р·Р°Р»РёРІРєР° РІСЃРµР№ СЃС‚СЂРѕРєРё**: РІРјРµСЃС‚Рѕ `FlowRow` СЃ РїРѕРєСЂР°СЃРєРѕР№ РѕС‚РґРµР»СЊРЅС‹С… СЃР»РѕРІ вЂ” РµРґРёРЅС‹Р№ `Text` СЃ `TextStyle.brush`. `Brush.linearGradient` СЂР°СЃС‚СЏРіРёРІР°РµС‚СЃСЏ РїРѕ РіСЂР°РЅРёС†Р°Рј СЃС‚СЂРѕРєРё, РїСЂРѕРіСЂРµСЃСЃ Р°РЅРёРјРёСЂСѓРµС‚СЃСЏ `animateFloatAsState` (240 РјСЃ, `LinearEasing`).
2. **Р¦РІРµС‚Р° РіСЂР°РґРёРµРЅС‚Р°**: СЃР»РµРІР° РїСЂРѕРїРµС‚Р°СЏ С‡Р°СЃС‚СЊ вЂ” `highlightColor` (primary), СЃРїСЂР°РІР° РЅРµРїСЂРѕРїРµС‚Р°СЏ вЂ” `onSurfaceVariant`; РјРµР¶РґСѓ РЅРёРјРё РјСЏРіРєР°СЏ РєСЂРѕРјРєР° В±5% С€РёСЂРёРЅС‹ СЃС‚СЂРѕРєРё (4 СЃС‚РѕРїР° Р±РµР· РґСѓР±Р»РµР№).
3. **РЎС‚Р°Р±РёР»СЊРЅС‹Р№ Р»РµР№Р°СѓС‚**: `graphicsLayer`/scale РЅРµ РёСЃРїРѕР»СЊР·СѓСЋС‚СЃСЏ; Сѓ РІСЃРµС… СЃС‚СЂРѕРє С„РёРєСЃРёСЂРѕРІР°РЅРЅС‹Рµ 18sp Рё `lineHeight 26.sp`; Р°РєС‚РёРІРЅР°СЏ РІС‹РґРµР»СЏРµС‚СЃСЏ С‚РѕР»СЊРєРѕ `SemiBold` Рё РіСЂР°РґРёРµРЅС‚РѕРј. РџРµСЂРµРЅРѕСЃ РґР»РёРЅРЅС‹С… СЃС‚СЂРѕРє вЂ” С€С‚Р°С‚РЅС‹Р№ text layout.
4. **РЎРѕС…СЂР°РЅРµРЅС‹ С„РёРєСЃС‹**: `contentPadding(top = 50.dp, bottom = 100.dp)`, РїРѕРґРїРёСЃСЊ В«РЎРёРЅС…СЂРѕРЅРЅС‹Р№ С‚РµРєСЃС‚ В· LRCLIBВ» РІРЅРµ `LazyColumn`, С†РµРЅС‚СЂРёСЂСѓСЋС‰РёР№ Р°РІС‚РѕСЃРєСЂРѕР»Р» С‡РµСЂРµР· `LaunchedEffect(activeIndex, viewportHeightPx)`.
5. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 4.16 вЂ” Р‘РµРіСѓРЅРѕРє РїРѕ РІРѕР»РЅРµ, С„РёРѕР»РµС‚РѕРІС‹Р№ РґРµС„РѕР»С‚ Рё Р°СЂС‚РёСЃС‚ РІ С€Р°РїРєРµ (20.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Thumb РЅР° РІРѕР»РЅРµ**: РІ `ExpressivePlayerSlider` Y-РєРѕРѕСЂРґРёРЅР°С‚Р° Р±РµРіСѓРЅРєР° Рё РѕСЂРµРѕР»Р° СЃС‡РёС‚Р°РµС‚СЃСЏ РїРѕ С‚РѕР№ Р¶Рµ СЃРёРЅСѓСЃРѕРёРґРµ, С‡С‚Рѕ Рё Path: `centerY в€’ amp Г— sin(ПЂu) Г— sin(2ПЂВ·spanВ·u/wavelength + phase)`. РљСЂСѓР¶РѕРє РµРґРµС‚ РїРѕ РіСЂРµР±РЅСЏРј, Р° РЅР° РїР°СѓР·Рµ (Р°РјРїР»РёС‚СѓРґР° 0) РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ РЅР° С†РµРЅС‚СЂР°Р»СЊРЅСѓСЋ Р»РёРЅРёСЋ.
2. **Р¤РёРѕР»РµС‚РѕРІР°СЏ С‚РµРјР° РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ**:
   - `AppSettings.dynamicCoverColors` Рё С‡С‚РµРЅРёРµ РёР· SharedPreferences вЂ” РґРµС„РѕР»С‚ `false`;
   - `FacetTheme.dynamicColor` вЂ” РґРµС„РѕР»С‚ `false` (С„РёРєСЃРёСЂРѕРІР°РЅРЅР°СЏ С„РёРѕР»РµС‚РѕРІР°СЏ РїР°Р»РёС‚СЂР° РІРјРµСЃС‚Рѕ СЃРёСЃС‚РµРјРЅРѕРіРѕ Material You);
   - РІС‹РєР»СЋС‡РµРЅРЅС‹Р№ РїРµСЂРµРєР»СЋС‡Р°С‚РµР»СЊ в†’ Р°РєС†РµРЅС‚ РёР· РЅР°СЃС‚СЂРѕРµРє (РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ С„РёРѕР»РµС‚РѕРІС‹Р№), РІРєР»СЋС‡С‘РЅРЅС‹Р№ в†’ РїР°Р»РёС‚СЂР° РѕР±Р»РѕР¶РєРё РІ РїР»РµРµСЂРµ.
3. **РЁР°РїРєР° РїР»РµРµСЂР°**: РІРµСЂС…РЅСЏСЏ РїР°РЅРµР»СЊ С‚РµРїРµСЂСЊ РґРІСѓС…СЃС‚СЂРѕС‡РЅР°СЏ вЂ” В«РЎР•Р™Р§РђРЎ РР“Р РђР•РўВ» (labelSmall, alpha 0.6) Рё РёРјСЏ Р°СЂС‚РёСЃС‚Р° (titleLarge, Bold) СЃ РјСЏРіРєРёРј СЃРІРµС‡РµРЅРёРµРј `Shadow(blurRadius = 8f, color = primary alpha 0.55)`; РґР»РёРЅРЅС‹Рµ РёРјРµРЅР° РѕР±СЂРµР·Р°СЋС‚СЃСЏ ellipsis.
4. **РЎР±РѕСЂРєР°**: `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/core/ui/components/ExpressivePlayerSlider.kt
app/src/main/java/com/facet/music/data/repository/SettingsRepository.kt
app/src/main/java/com/facet/music/data/local/SettingsRepositoryImpl.kt
app/src/main/java/com/facet/music/core/ui/theme/Theme.kt
app/src/main/java/com/facet/music/MainActivity.kt
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 5 вЂ” РўРµСЃС‚С‹ Рё QA-РїСЂРѕС‡С‘СЃ (20.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РўРµСЃС‚РѕРІР°СЏ РїРµСЃРѕС‡РЅРёС†Р° (JVM, Р±РµР· СЌРјСѓР»СЏС‚РѕСЂР°)**:
   - Р’ `app/build.gradle.kts` РґРѕР±Р°РІР»РµРЅС‹ `junit:4.13.2`, `robolectric:4.14.1`, `kotlinx-coroutines-test:1.9.0`, `androidx.arch.core:core-testing:2.2.0`, `io.mockk:mockk:1.13.13`, `androidx.test:core-ktx:1.6.1`, `androidx.room:room-testing:2.6.1`.
   - Р’РєР»СЋС‡РµРЅС‹ `testOptions.unitTests.isIncludeAndroidResources = true` Рё `isReturnDefaultValues = true`.
2. **РќР°РїРёСЃР°РЅС‹ 31 JVM-С‚РµСЃС‚**:
   - `LrcParserTest` (10): `[mm:ss.xx]`, `[mm:ss.x]`, `[mm:ss.xxx]`, РЅРµСЃРєРѕР»СЊРєРѕ РјРµС‚РѕРє РІ СЃС‚СЂРѕРєРµ, `[offset:+ms]`/`[offset:-ms]` СЃ РєР»Р°РјРїРѕРј РІ 0, РєР°СЂР°РѕРєРµ-С‚РµРіРё `<mm:ss.xx>`, СЃРѕСЂС‚РёСЂРѕРІРєР°, С„РѕР»Р±СЌРє РЅР° РЅРµСЃРёРЅС…СЂРѕРЅРЅС‹Р№ С‚РµРєСЃС‚, CRLF, РїСѓСЃС‚С‹Рµ/РјРµС‚Р°РґР°РЅРЅС‹Рµ-only РІС…РѕРґС‹.
   - `PlayerControllerQueueTest` (14): `playNext` Рё `playPrevious` РґР»СЏ Repeat OFF/ALL/ONE, Р°РІС‚Рѕ-РѕРєРѕРЅС‡Р°РЅРёРµ С‚СЂРµРєР° С‡РµСЂРµР· `Player.Listener`, С†РёРєР» РїРµСЂРµРєР»СЋС‡РµРЅРёСЏ СЂРµР¶РёРјР°, РІРѕР·РІСЂР°С‚ РІ РЅР°С‡Р°Р»Рѕ РїРѕСЃР»Рµ 3 СЃРµРєСѓРЅРґ, shuffle СЃРѕС…СЂР°РЅСЏРµС‚ С‚РµРєСѓС‰РёР№ С‚СЂРµРє РїРµСЂРІС‹Рј Рё РІРѕСЃСЃС‚Р°РЅР°РІР»РёРІР°РµС‚ РёСЃС…РѕРґРЅС‹Р№ РїРѕСЂСЏРґРѕРє РїСЂРё РІС‹РєР»СЋС‡РµРЅРёРё.
   - `HomeViewModelGenreTest` (4): РіСЂСѓРїРїРёСЂРѕРІРєР° В«РўРІРѕРё Р¶Р°РЅСЂС‹В» РёР· РёСЃС‚РѕСЂРёРё Рё Р»Р°Р№РєРѕРІ, РґРµРґСѓРїР»РёРєР°С†РёСЏ РїРѕ `id`, trim Р¶Р°РЅСЂР°, РѕС‚Р±СЂР°СЃС‹РІР°РЅРёРµ РїСѓСЃС‚С‹С… Р¶Р°РЅСЂРѕРІ, СЃРѕСЂС‚РёСЂРѕРІРєР° РїРѕ СЂР°Р·РјРµСЂСѓ, Р»РёРјРёС‚ 20 РЅРµРґР°РІРЅРёС…, СЂРµР°РєС‚РёРІРЅРѕСЃС‚СЊ С‡Р°СЂС‚РѕРІ. РћС‚РґРµР»СЊРЅРѕРіРѕ `CollectionsViewModel` РІ РїСЂРѕРµРєС‚Рµ РЅРµС‚ вЂ” Р»РѕРіРёРєР° Р¶РёРІС‘С‚ РІ `HomeViewModel`.
   - `LikedTracksRepositoryTest` (3, Robolectric + Room `inMemoryDatabaseBuilder`): РґРѕР±Р°РІР»РµРЅРёРµ/СѓРґР°Р»РµРЅРёРµ, `toggleLike`, СЂРµР°РєС‚РёРІРЅС‹Р№ Flow (РїСѓСЃС‚Рѕ в†’ Р»Р°Р№Рє в†’ СЃРЅСЏС‚РёРµ), СЃРѕСЂС‚РёСЂРѕРІРєР° `likedAt DESC`.
3. **QA-С„РёРєСЃС‹**:
   - `PlayerController.playNext()` РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ Р±РѕР»СЊС€Рµ РЅРµ Р·Р°С†РёРєР»РёРІР°РµС‚ РѕС‡РµСЂРµРґСЊ: СЂСѓС‡РЅРѕР№ Next РїСЂРё Repeat OFF РЅР° РїРѕСЃР»РµРґРЅРµРј С‚СЂРµРєРµ РѕСЃС‚Р°С‘С‚СЃСЏ РЅР° РјРµСЃС‚Рµ; Р·Р°С†РёРєР»РёРІР°РЅРёРµ вЂ” С‚РѕР»СЊРєРѕ Сѓ ALL/Р°РІС‚Рѕ-РїРµСЂРµС…РѕРґР°.
   - `PlayerController.release()` РѕС‚РјРµРЅСЏРµС‚ РІРЅСѓС‚СЂРµРЅРЅРёР№ `CoroutineScope`; Р·Р°РїРёСЃСЊ РІ РёСЃС‚РѕСЂРёСЋ РѕР±С‘СЂРЅСѓС‚Р° РІ try/catch (СЃР±РѕР№ Room РЅРµ Р»РѕРјР°РµС‚ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёРµ).
   - `runCatching` Р±РѕР»СЊС€Рµ РЅРµ РіР»РѕС‚Р°РµС‚ `CancellationException`: РґРѕР±Р°РІР»РµРЅ `core/util/ResultExt.kt`, РїСЂРёРјРµРЅС‘РЅ РІ SoundCloud-СЂРµРїРѕР·РёС‚РѕСЂРёСЏС…, С‡Р°СЂС‚Р°С…, LRCLIB Рё `HomeViewModel`.
   - `PlayerPalette`: РєСЌС€ РїР°Р»РёС‚СЂ СЃС‚Р°Р» РїРѕС‚РѕРєРѕР±РµР·РѕРїР°СЃРЅС‹Рј LRU РЅР° 32 Р·Р°РїРёСЃРё (СЂР°РЅСЊС€Рµ вЂ” РЅРµРѕРіСЂР°РЅРёС‡РµРЅРЅС‹Р№ `mutableMapOf` СЃ РіРѕРЅРєР°РјРё main/IO), `ImageLoader` СЃРѕР·РґР°С‘С‚СЃСЏ РѕРґРёРЅ СЂР°Р· РЅР° РїСЂРѕС†РµСЃСЃ РІРјРµСЃС‚Рѕ РЅРѕРІРѕРіРѕ РЅР° РєР°Р¶РґС‹Р№ С‚СЂРµРє.
   - `PlayerQueueSheet`: key СЃРїРёСЃРєР° С‚РµРїРµСЂСЊ РїРѕ `track.id`, Р° РЅРµ РїРѕ РёРЅРґРµРєСЃСѓ вЂ” СЃРѕСЃС‚РѕСЏРЅРёРµ РЅРµ В«РїРµСЂРµРµР·Р¶Р°РµС‚В» РїСЂРё СѓРґР°Р»РµРЅРёРё.
   - Р”РѕР±Р°РІР»РµРЅС‹ СЃС‚Р°Р±РёР»СЊРЅС‹Рµ РєР»СЋС‡Рё РІ СЃС‚Р°С‚РёС‡РµСЃРєРёРµ СЃРїРёСЃРєРё Wave Рё СЃРєРµР»РµС‚РѕРЅРѕРІ Home.
   - РЈРґР°Р»РµРЅР° РјС‘СЂС‚РІР°СЏ РІРµС‚РєР° `e is StreamInfo` РІ Р»РѕРіРµ `resolveStreamUrl`.
4. **РџСЂРѕРІРµСЂРєРё**:
   - `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.
   - `.\gradlew test` вЂ” `BUILD SUCCESSFUL`, 31/31 С‚РµСЃС‚РѕРІ РІ debug Рё release, 0 failures, 0 errors.
   - `.\gradlew lint` вЂ” `BUILD SUCCESSFUL`, 0 critical/error, 20 warning (РїСЂРµРґР»РѕР¶РµРЅРёСЏ РѕР±РЅРѕРІРёС‚СЊ РІРµСЂСЃРёРё, exported MediaSessionService, non-lambda offset, РѕС‚СЃСѓС‚СЃС‚РІРёРµ РёРєРѕРЅРєРё РїСЂРёР»РѕР¶РµРЅРёСЏ). Р’РµСЂСЃРёРё РїРѕ РїСЂР°РІРёР»Р°Рј РїСЂРѕРµРєС‚Р° РЅРµ РјРµРЅСЏР»РёСЃСЊ.

### Р¤Р°Р№Р»С‹
```
app/build.gradle.kts
app/src/test/java/com/facet/music/testutil/MainDispatcherRule.kt (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/data/source/lyrics/LrcParserTest.kt (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/core/player/PlayerControllerQueueTest.kt (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/feature/home/HomeViewModelGenreTest.kt (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/data/local/LikedTracksRepositoryTest.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/util/ResultExt.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/player/PlayerController.kt
app/src/main/java/com/facet/music/data/source/soundcloud/SoundCloudRepositoryImpl.kt
app/src/main/java/com/facet/music/data/source/soundcloud/SoundCloudChartsRepositoryImpl.kt
app/src/main/java/com/facet/music/data/source/lyrics/LyricsRepositoryImpl.kt
app/src/main/java/com/facet/music/feature/home/HomeViewModel.kt
app/src/main/java/com/facet/music/core/ui/theme/PlayerPalette.kt
app/src/main/java/com/facet/music/core/ui/components/PlayerQueueSheet.kt
app/src/main/java/com/facet/music/feature/wave/WaveScreen.kt
app/src/main/java/com/facet/music/feature/home/HomeScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 6 вЂ” Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏ, РњРѕСЏ РІРѕР»РЅР° Рё Top 10 (20.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Р‘Р°Р·Р° РґР°РЅРЅС‹С… Рё В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏВ»**:
   - `LikedTrackEntity` РґРѕРїРѕР»РЅРµРЅР° РїРѕР»СЏРјРё `latitude: Double?`, `longitude: Double?`, `season: String?`; РјР°РїРїРµСЂ `Track.toLikedEntity()` РїСЂРёРЅРёРјР°РµС‚ СЌС‚Рё Р·РЅР°С‡РµРЅРёСЏ (РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ null).
   - `AppDatabase` РѕР±РЅРѕРІР»РµРЅР° РґРѕ РІРµСЂСЃРёРё **4**; РјРёРіСЂР°С†РёРё РЅРµ РїРёС€СѓС‚СЃСЏ вЂ” РІ `DatabaseModule` СѓР¶Рµ РІРєР»СЋС‡С‘РЅ `fallbackToDestructiveMigration()`.
   - `LikedTracksDao.getLikedTracksBySeason(season)` + `LikedTracksRepository.getLikedTracksBySeason()` вЂ” СЂРµР°РєС‚РёРІРЅС‹Р№ РїРѕС‚РѕРє Р»Р°Р№РєРѕРІ С‚РµРєСѓС‰РµРіРѕ СЃРµР·РѕРЅР°.
   - `SeasonProvider` (core/util): Winter (РґРµРєвЂ“С„РµРІ), Spring (РјР°СЂвЂ“РјР°Р№), Summer (РёСЋРЅвЂ“Р°РІРі), Autumn (СЃРµРЅвЂ“РЅРѕСЏ); СЃРµР·РѕРЅ С…СЂР°РЅРёС‚СЃСЏ СЃС‚СЂРѕРєРѕР№.
   - `LocationProvider` + `FusedLocationProvider`: `FusedLocationProviderClient.getLastLocation()` СЃ С‚Р°Р№РјР°СѓС‚РѕРј 4 СЃ, РїСЂРѕРІРµСЂРєРѕР№ `ACCESS_FINE/COARSE_LOCATION`, Р±РµР·РѕРїР°СЃРЅС‹Рј С„РѕР»Р±СЌРєРѕРј РІ null (РЅРµС‚ СЂР°Р·СЂРµС€РµРЅРёСЏ/РѕС€РёР±РєР°/С‚Р°Р№РјР°СѓС‚). Р”РѕР±Р°РІР»РµРЅР° Р·Р°РІРёСЃРёРјРѕСЃС‚СЊ `play-services-location:21.3.0` Рё СЂР°Р·СЂРµС€РµРЅРёСЏ РІ РјР°РЅРёС„РµСЃС‚.
   - `PlayerViewModel.toggleLike()`: РїСЂРё РїРѕСЃС‚Р°РЅРѕРІРєРµ Р»Р°Р№РєР° Р·Р°РїСЂР°С€РёРІР°РµС‚ РїРѕСЃР»РµРґРЅСЋСЋ РіРµРѕРїРѕР·РёС†РёСЋ Рё С‚РµРєСѓС‰РёР№ СЃРµР·РѕРЅ Рё СЃРѕС…СЂР°РЅСЏРµС‚ РёС… РІ Room; РїСЂРё СЃРЅСЏС‚РёРё Р»Р°Р№РєР° РјРµС‚Р°РґР°РЅРЅС‹Рµ РЅРµ РЅСѓР¶РЅС‹. РЎР±РѕР№ РіРµРѕР»РѕРєР°С†РёРё РЅРµ Р±Р»РѕРєРёСЂСѓРµС‚ Р»Р°Р№Рє.
   - `HomeViewModel` РЅР°Р±Р»СЋРґР°РµС‚ `getLikedTracksBySeason(С‚РµРєСѓС‰РёР№ СЃРµР·РѕРЅ)` Рё РѕС‚РґР°С‘С‚ `memories`; `HomeScreen` РїРѕРєР°Р·С‹РІР°РµС‚ РєР°СЂСѓСЃРµР»СЊ В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏВ» РїРµСЂРµРґ В«РќРµРґР°РІРЅРёРјРёВ».
2. **В«РњРѕСЏ РІРѕР»РЅР°В»**:
   - РќРѕРІС‹Р№ `WaveRepository` / `WaveRepositoryImpl`: РґРѕ 10 СЃР»СѓС‡Р°Р№РЅС‹С… Р»Р°Р№РєРѕРІ, РґРѕ 5 РЅРµРґР°РІРЅРёС… С‚СЂРµРєРѕРІ РёСЃС‚РѕСЂРёРё, РґРѕ 5 РЅР°С…РѕРґРѕРє РїРѕ Р¶Р°РЅСЂР°Рј РёР· Р»Р°Р№РєРѕРІ (РїРѕРёСЃРє SoundCloud С‡РµСЂРµР· NewPipe), С„РѕР»Р±СЌРє РЅР° Top 10 РїСЂРё РѕС‚СЃСѓС‚СЃС‚РІРёРё Р¶Р°РЅСЂРѕРІ; РёС‚РѕРі РїРµСЂРµРјРµС€РёРІР°РµС‚СЃСЏ Рё РґРµРґСѓРїР»РёС†РёСЂСѓРµС‚СЃСЏ, СЃС‡РёС‚Р°РµС‚СЃСЏ РІРєР»Р°Рґ РєР°Р¶РґРѕРіРѕ РёСЃС‚РѕС‡РЅРёРєР°.
   - `WaveViewModel`: РіРµРЅРµСЂРёСЂСѓРµС‚ РїРѕРґР±РѕСЂРєСѓ, СЃРѕС…СЂР°РЅСЏРµС‚ СЂР°Р·Р±РёРІРєСѓ РїРѕ РёСЃС‚РѕС‡РЅРёРєР°Рј Рё СЃСЂР°Р·Сѓ Р·Р°РїСѓСЃРєР°РµС‚ РѕС‡РµСЂРµРґСЊ С‡РµСЂРµР· РѕР±С‰РёР№ `PlayerController.setQueueAndPlay()`; РїРѕРІС‚РѕСЂРЅРѕРµ РЅР°Р¶Р°С‚РёРµ РІРѕ РІСЂРµРјСЏ РіРµРЅРµСЂР°С†РёРё РёРіРЅРѕСЂРёСЂСѓРµС‚СЃСЏ.
   - `WaveScreen` РїРµСЂРµРїРёСЃР°РЅ: Р·Р°РіР»СѓС€РєРё В«РЎРєРѕСЂРѕВ» Рё С„РµР№РєРѕРІС‹Рµ С‡РёРїС‹ РЅР°СЃС‚СЂРѕРµРЅРёР№ СѓР±СЂР°РЅС‹, РґРѕР±Р°РІР»РµРЅР° Р±РѕР»СЊС€Р°СЏ РіСЂР°РґРёРµРЅС‚РЅР°СЏ РєРЅРѕРїРєР° В«Р’РєР»СЋС‡РёС‚СЊ РІРѕР»РЅСѓВ» СЃ РїСѓР»СЊСЃРёСЂСѓСЋС‰РёРј РѕСЂРµРѕР»РѕРј, press-Р°РЅРёРјР°С†РёРµР№ Рё РёРЅРґРёРєР°С‚РѕСЂРѕРј Р·Р°РіСЂСѓР·РєРё; РїРѕРґ РєРЅРѕРїРєРѕР№ вЂ” С‡РёРїС‹ В«N Р»СЋР±РёРјС‹С… / N РёР· РёСЃС‚РѕСЂРёРё / N РЅР°С…РѕРґРѕРєВ» Рё СЃРїРёСЃРѕРє С‚СЂРµРєРѕРІ СЃ РѕР±Р»РѕР¶РєР°РјРё, РЅРѕРјРµСЂР°РјРё Рё Р·Р°РїСѓСЃРєРѕРј СЃ РІС‹Р±СЂР°РЅРЅРѕР№ РїРѕР·РёС†РёРё.
   - РћС€РёР±РєРё СЃРµС‚Рё РїРѕРєР°Р·С‹РІР°СЋС‚СЃСЏ РєР°СЂС‚РѕС‡РєРѕР№; РїСѓСЃС‚С‹Рµ РґР°РЅРЅС‹Рµ РґР°СЋС‚ РїРѕРґСЃРєР°Р·РєСѓ В«РґРѕР±Р°РІСЊС‚Рµ Р»Р°Р№РєРё РёР»Рё РїРѕСЃР»СѓС€Р°Р№С‚Рµ С‚СЂРµРєРёВ».
3. **Top 50 в†’ Top 10**:
   - Р—Р°РіРѕР»РѕРІРѕРє РЅР° РіР»Р°РІРЅРѕР№ Р·Р°РјРµРЅС‘РЅ РЅР° В«Top 10 SoundCloudВ», РѕР±РЅРѕРІР»РµРЅС‹ РєРѕРјРјРµРЅС‚Р°СЂРёРё (РєР°СЂСѓСЃРµР»СЊ 1..10, KDoc С‡Р°СЂС‚РѕРІ Рё РєСЌС€Р°).
   - Р’РЅСѓС‚СЂРµРЅРЅРёРµ РёРґРµРЅС‚РёС„РёРєР°С‚РѕСЂС‹ (`top50`, `TOP_50`) РѕСЃС‚Р°РІР»РµРЅС‹ РєР°Рє РєР»СЋС‡ РєСЌС€Р° Room РґР»СЏ СЃРѕРІРјРµСЃС‚РёРјРѕСЃС‚Рё; API SoundCloud РѕС‚РґР°С‘С‚ 10 С‚СЂРµРєРѕРІ, РїРѕСЌС‚РѕРјСѓ РЅСѓРјРµСЂР°С†РёСЏ Рё С‚Р°Рє РѕРіСЂР°РЅРёС‡РµРЅР° 1..10.
4. **РџСЂРѕРІРµСЂРєРё**:
   - `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.
   - `.\gradlew test` вЂ” `BUILD SUCCESSFUL`, 38/38 С‚РµСЃС‚РѕРІ РІ debug Рё release (31 РїСЂРµР¶РЅРёР№ + SeasonProvider 2 + WaveRepository 3 + Room-СЃРµР·РѕРЅ 1 + Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏ HomeViewModel 1), 0 failures, 0 errors.
   - `.\gradlew lint` вЂ” `BUILD SUCCESSFUL`, 0 critical/error, 21 warning (17 вЂ” РїСЂРµРґР»РѕР¶РµРЅРёСЏ РѕР±РЅРѕРІРёС‚СЊ РІРµСЂСЃРёРё, РІРєР»СЋС‡Р°СЏ play-services-location; 2 вЂ” non-lambda offset; РѕС‚СЃСѓС‚СЃС‚РІРёРµ РёРєРѕРЅРєРё РїСЂРёР»РѕР¶РµРЅРёСЏ; exported MediaSessionService).
5. **РћРіСЂР°РЅРёС‡РµРЅРёРµ (РёСЃРїСЂР°РІР»РµРЅРѕ РІ С„Р°Р·Рµ 6.1)**: runtime-Р·Р°РїСЂРѕСЃ РіРµРѕРїРѕР·РёС†РёРё РІ СЌС‚РѕР№ С„Р°Р·Рµ РЅРµ РґРѕР±Р°РІР»СЏР»СЃСЏ (РїРѕ РўР— РјРµРЅСЏР»СЃСЏ С‚РѕР»СЊРєРѕ РјР°РЅРёС„РµСЃС‚), РїРѕСЌС‚РѕРјСѓ РєРѕРѕСЂРґРёРЅР°С‚С‹ СЃРѕС…СЂР°РЅСЏР»РёСЃСЊ Р»РёС€СЊ РїСЂРё СЂСѓС‡РЅРѕР№ РІС‹РґР°С‡Рµ РґРѕСЃС‚СѓРїР° РІ СЃРёСЃС‚РµРјРЅС‹С… РЅР°СЃС‚СЂРѕР№РєР°С….

### Р¤Р°Р№Р»С‹
```
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/facet/music/core/di/RepositoryModule.kt
app/src/main/java/com/facet/music/core/location/LocationProvider.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/location/FusedLocationProvider.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/util/SeasonProvider.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/local/AppDatabase.kt
app/src/main/java/com/facet/music/data/local/dao/LikedTracksDao.kt
app/src/main/java/com/facet/music/data/local/entity/LikedTrackEntity.kt
app/src/main/java/com/facet/music/data/local/LikedTracksRepositoryImpl.kt
app/src/main/java/com/facet/music/data/repository/LikedTracksRepository.kt
app/src/main/java/com/facet/music/data/repository/WaveRepository.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/source/wave/WaveRepositoryImpl.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/feature/player/PlayerViewModel.kt
app/src/main/java/com/facet/music/feature/home/HomeViewModel.kt
app/src/main/java/com/facet/music/feature/home/HomeScreen.kt
app/src/main/java/com/facet/music/feature/wave/WaveViewModel.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/feature/wave/WaveScreen.kt
app/src/main/java/com/facet/music/data/repository/SoundCloudChartsRepository.kt
app/src/main/java/com/facet/music/data/local/entity/ChartCacheEntity.kt
app/src/test/java/com/facet/music/core/util/SeasonProviderTest.kt (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/data/source/wave/WaveRepositoryTest.kt (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/data/local/LikedTracksRepositoryTest.kt
app/src/test/java/com/facet/music/feature/home/HomeViewModelGenreTest.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 6.1 вЂ” Runtime-СЂР°Р·СЂРµС€РµРЅРёРµ РіРµРѕРїРѕР·РёС†РёРё (20.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РћР±С‰РёР№ С…РµР»РїРµСЂ СЂР°Р·СЂРµС€РµРЅРёР№**:
   - РќРѕРІС‹Р№ `LocationPermissions` (core/location): СЃРїРёСЃРѕРє `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`, РїСЂРѕРІРµСЂРєР° Р»СЋР±РѕРіРѕ РёР· РЅРёС… (`hasPermission`) Рё РїРµСЂРµС…РѕРґ РІ РєР°СЂС‚РѕС‡РєСѓ РїСЂРёР»РѕР¶РµРЅРёСЏ С‡РµСЂРµР· `ACTION_APPLICATION_DETAILS_SETTINGS`.
   - `FusedLocationProvider` РїРµСЂРµРІРµРґС‘РЅ РЅР° РѕР±С‰РёР№ С…РµР»РїРµСЂ вЂ” Р»РѕРіРёРєР° РїСЂРѕРІРµСЂРєРё Р±РѕР»СЊС€Рµ РЅРµ РґСѓР±Р»РёСЂСѓРµС‚СЃСЏ.
2. **РџР»РµРµСЂ**:
   - `rememberLauncherForActivityResult(RequestMultiplePermissions())`: РїСЂРё РїРµСЂРІРѕРј Р»Р°Р№РєРµ Р±РµР· РґРѕСЃС‚СѓРїР° РїРѕРєР°Р·С‹РІР°РµС‚СЃСЏ СЃС‚Р°РЅРґР°СЂС‚РЅС‹Р№ РґРёР°Р»РѕРі Android; РїРѕСЃР»Рµ РІС‹РґР°С‡Рё РєРѕРѕСЂРґРёРЅР°С‚С‹ СЃСЂР°Р·Сѓ СЃРѕС…СЂР°РЅСЏСЋС‚СЃСЏ.
   - Р›Р°Р№Рє РЅРµ С‚РµСЂСЏРµС‚СЃСЏ РїСЂРё РѕС‚РєР°Р·Рµ: `pendingLike` СЃСЂР°Р±Р°С‚С‹РІР°РµС‚ РїРѕСЃР»Рµ РѕС‚РІРµС‚Р° Рё СЃРѕС…СЂР°РЅСЏРµС‚ С‚СЂРµРє Р±РµР· РєРѕРѕСЂРґРёРЅР°С‚.
   - РќРµРЅР°РІСЏР·С‡РёРІС‹Р№ Р±Р°РЅРЅРµСЂ В«Р“РµРѕРїРѕР·РёС†РёСЏ РІС‹РєР»СЋС‡РµРЅР° вЂ” вЂћР’РѕСЃРїРѕРјРёРЅР°РЅРёСЏвЂњ РЅРµ СЃРѕС…СЂР°РЅСЏС‚ РјРµСЃС‚Рѕ Р»Р°Р№РєР°В»; РєРЅРѕРїРєР° В«Р Р°Р·СЂРµС€РёС‚СЊВ» РІС‹Р·С‹РІР°РµС‚ РґРёР°Р»РѕРі, РїРѕСЃР»Рµ РѕС‚РєР°Р·Р° РїСЂРµРІСЂР°С‰Р°РµС‚СЃСЏ РІ В«РќР°СЃС‚СЂРѕР№РєРёВ» Рё РѕС‚РєСЂС‹РІР°РµС‚ СЃРёСЃС‚РµРјРЅС‹Рµ РЅР°СЃС‚СЂРѕР№РєРё РїСЂРёР»РѕР¶РµРЅРёСЏ.
   - РЎС‚Р°С‚СѓСЃ СЂР°Р·СЂРµС€РµРЅРёСЏ РїРµСЂРµС‡РёС‚С‹РІР°РµС‚СЃСЏ РїСЂРё РІРѕР·РІСЂР°С‚Рµ РІ РїСЂРёР»РѕР¶РµРЅРёРµ (`Lifecycle.Event.ON_RESUME`), РїРѕСЌС‚РѕРјСѓ Р±Р°РЅРЅРµСЂ РёСЃС‡РµР·Р°РµС‚ РїРѕСЃР»Рµ РІС‹РґР°С‡Рё РґРѕСЃС‚СѓРїР° РІ РЅР°СЃС‚СЂРѕР№РєР°С….
3. **Р­РєСЂР°РЅ РЅР°СЃС‚СЂРѕРµРє**:
   - РќРѕРІР°СЏ СЃРµРєС†РёСЏ В«Р“Р•РћРџРћР—РР¦РРЇВ» СЃРѕ СЃС‚Р°С‚СѓСЃРѕРј В«Р“РµРѕРїРѕР·РёС†РёСЏ: Р Р°Р·СЂРµС€РµРЅРѕ / Р—Р°РїСЂРµС‰РµРЅРѕВ», РёРєРѕРЅРєРѕР№ Рё РїРѕСЏСЃРЅРµРЅРёРµРј, Р·Р°С‡РµРј РЅСѓР¶РµРЅ РґРѕСЃС‚СѓРї.
   - РљРЅРѕРїРєР° В«РџСЂРѕРІРµСЂРёС‚СЊ СЂР°Р·СЂРµС€РµРЅРёСЏВ»: РїРµСЂРµС‡РёС‚С‹РІР°РµС‚ СЃС‚Р°С‚СѓСЃ, Р° РїСЂРё Р·Р°РїСЂРµС‚Рµ РѕС‚РєСЂС‹РІР°РµС‚ РЅР°СЃС‚СЂРѕР№РєРё РїСЂРёР»РѕР¶РµРЅРёСЏ; СЃС‚Р°С‚СѓСЃ С‚Р°РєР¶Рµ РѕР±РЅРѕРІР»СЏРµС‚СЃСЏ РїСЂРё РІРѕР·РІСЂР°С‚Рµ РёР· СЃРёСЃС‚РµРјРЅС‹С… РЅР°СЃС‚СЂРѕРµРє.
4. **РџСЂРѕРІРµСЂРєРё**:
   - `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.
   - `.\gradlew test` вЂ” `BUILD SUCCESSFUL`, 41/41 С‚РµСЃС‚ РІ debug Рё release (РґРѕР±Р°РІР»РµРЅС‹ 3 С‚РµСЃС‚Р° `LocationPermissionsTest`: РѕС‚РєР°Р· РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ, РґРѕСЃС‚Р°С‚РѕС‡РЅРѕСЃС‚СЊ РїСЂРёР±Р»РёР·РёС‚РµР»СЊРЅРѕРіРѕ РґРѕСЃС‚СѓРїР°, РєРѕСЂСЂРµРєС‚РЅС‹Р№ intent СЃРёСЃС‚РµРјРЅС‹С… РЅР°СЃС‚СЂРѕРµРє), 0 failures, 0 errors.
   - `.\gradlew lint` вЂ” `BUILD SUCCESSFUL`, 0 critical/error, 21 warning (Р±РµР· РЅРѕРІС‹С… Р·Р°РјРµС‡Р°РЅРёР№).

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/core/location/LocationPermissions.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/location/FusedLocationProvider.kt
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
app/src/main/java/com/facet/music/feature/settings/SettingsScreen.kt
app/src/test/java/com/facet/music/core/location/LocationPermissionsTest.kt (РЅРѕРІС‹Р№)
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 6.2 вЂ” Expressive-СЂРµРґРёР·Р°Р№РЅ СЌРєСЂР°РЅР° С‚РµРєСЃС‚Р° (20.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Р§РёСЃС‚С‹Р№ СЌРєСЂР°РЅ Р±РµР· РїР°РЅРµР»РµР№**:
   - РЈРґР°Р»РµРЅС‹ РѕР±Рµ СЃС‚РµРєР»СЏРЅРЅС‹Рµ РїРѕРІРµСЂС…РЅРѕСЃС‚Рё: РІРµСЂС…РЅСЏСЏ РєР°СЂС‚РѕС‡РєР°-РІРёС‚СЂРёРЅР° Рё РЅРёР¶РЅСЏСЏ РїР°РЅРµР»СЊ РІРѕРєСЂСѓРі СЃС‚СЂРѕРє.
   - РћСЃС‚Р°Р»Р°СЃСЊ С‚РѕР»СЊРєРѕ РїР°СЂСЏС‰Р°СЏ С€Р°РїРєР°: РјРёРЅРёР°С‚СЋСЂР° РѕР±Р»РѕР¶РєРё 42dp, РЅР°Р·РІР°РЅРёРµ Рё Р°СЂС‚РёСЃС‚ Р±РµР· РїРѕРґР»РѕР¶РєРё.
   - РЈРґР°Р»РµРЅР° СЃР»СѓР¶РµР±РЅР°СЏ РїРѕРґРїРёСЃСЊ В«РЎРёРЅС…СЂРѕРЅРЅС‹Р№ С‚РµРєСЃС‚ В· LRCLIBВ» РІРЅРёР·Сѓ вЂ” СЌРєСЂР°РЅ СЃС‚Р°Р» С‡РёС‰Рµ.
2. **РўРёРїРѕРіСЂР°С„РёРєР°**:
   - Р‘Р°Р·РѕРІР°СЏ СЃС‚СЂРѕРєР° СѓРІРµР»РёС‡РµРЅР° РґРѕ 24sp / lineHeight 34sp (Р±С‹Р»Рѕ 18/26); РґР»РёРЅРЅС‹Рµ СЃС‚СЂРѕРєРё РїРѕ-РїСЂРµР¶РЅРµРјСѓ СЃРІРѕР±РѕРґРЅРѕ РїРµСЂРµРЅРѕСЃСЏС‚СЃСЏ.
   - Р¤РёР·РёС‡РµСЃРєРёР№ СЂР°Р·РјРµСЂ С€СЂРёС„С‚Р° РѕРґРёРЅР°РєРѕРІ Сѓ РІСЃРµС… СЃС‚СЂРѕРє вЂ” Р»РµР№Р°СѓС‚ РЅРµ РїСЂС‹РіР°РµС‚.
   - РђРєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР°: Bold, РјР°СЃС€С‚Р°Р± 1.08 С‡РµСЂРµР· `graphicsLayer` СЃ РїСЂСѓР¶РёРЅРѕР№, РјСЏРіРєРѕРµ СЃРІРµС‡РµРЅРёРµ `Shadow` (primary, blur 18), РіСЂР°РґРёРµРЅС‚ РѕС‚ primary Рє onSurface РїРѕ РїСЂРѕРіСЂРµСЃСЃСѓ.
   - РќРµР°РєС‚РёРІРЅС‹Рµ СЃС‚СЂРѕРєРё: onSurface СЃ alpha 0.36; РґР»СЏ РЅРµСЃРёРЅС…СЂРѕРЅРЅРѕРіРѕ С‚РµРєСЃС‚Р° вЂ” alpha 0.82 Р±РµР· РІС‹РґРµР»РµРЅРёСЏ.
   - Р”Р°Р»СЊРЅРёРµ СЃС‚СЂРѕРєРё (РґРёСЃС‚Р°РЅС†РёСЏ в‰Ґ 3) РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅРѕ СЂР°Р·РјС‹РІР°СЋС‚СЃСЏ РЅР° 1.5вЂ“3.5dp (RenderEffect РЅР° API 31+, РЅРёР¶Рµ вЂ” С‚РѕР»СЊРєРѕ РїСЂРѕР·СЂР°С‡РЅРѕСЃС‚СЊ).
3. **РћС‚СЃС‚СѓРїС‹ Рё РјР°СЃРєРё**:
   - `contentPadding` 64dp СЃРІРµСЂС…Сѓ / 136dp СЃРЅРёР·Сѓ РґР»СЏ СЃРёРЅС…СЂРѕРЅРЅРѕРіРѕ С‚РµРєСЃС‚Р° (28/136 вЂ” РґР»СЏ РѕР±С‹С‡РЅРѕРіРѕ), РјРµР¶СЃС‚СЂРѕС‡РЅС‹Р№ РёРЅС‚РµСЂРІР°Р» 18dp.
   - Р’РµСЂС…РЅСЏСЏ РіСЂР°РґРёРµРЅС‚РЅР°СЏ РјР°СЃРєР° 44dp Рё РЅРёР¶РЅСЏСЏ 124dp СЂР°СЃС‚РІРѕСЂСЏСЋС‚ СЃС‚СЂРѕРєРё РІ С†РІРµС‚Рµ `surface` вЂ” С‚РµРєСЃС‚ РЅРµ СѓРїРёСЂР°РµС‚СЃСЏ РІ РєРѕРЅС‚СЂРѕР»С‹.
4. **РЎРѕС…СЂР°РЅС‘РЅРЅР°СЏ РјРµС…Р°РЅРёРєР°**:
   - РџРµСЂРµС…РѕРґ В«РѕР±Р»РѕР¶РєР° в†” С‚РµРєСЃС‚В» СЃ blur-СѓС…РѕРґРѕРј С„РѕРЅР° РЅРµ РёР·РјРµРЅСЏР»СЃСЏ.
   - Р›РѕРіРёРєР° Р°РІС‚РѕСЃРєСЂРѕР»Р»Р° Рё С†РµРЅС‚СЂРёСЂРѕРІР°РЅРёСЏ РїСЂРµР¶РЅСЏСЏ; РѕР±РЅРѕРІР»С‘РЅ С‚РѕР»СЊРєРѕ `halfLinePx` РїРѕРґ РЅРѕРІСѓСЋ РІС‹СЃРѕС‚Сѓ СЃС‚СЂРѕРєРё (22dp).
5. **РџСЂРѕРІРµСЂРєРё**:
   - `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.
   - `.\gradlew test` вЂ” `BUILD SUCCESSFUL`, 41/41 С‚РµСЃС‚, 0 failures, 0 errors.
   - `.\gradlew lint` вЂ” `BUILD SUCCESSFUL`, 0 critical/error, 21 warning (Р±РµР· РЅРѕРІС‹С… Р·Р°РјРµС‡Р°РЅРёР№).

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 6.5 вЂ” РќР°СЃС‚РѕСЏС‰РёР№ С„РѕРЅРѕРІС‹Р№ РїР»РµРµСЂ Рё Р±РµР·РѕРїР°СЃРЅС‹Рµ РјРёРіСЂР°С†РёРё Room (20.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **MediaSessionService РєР°Рє РµРґРёРЅСЃС‚РІРµРЅРЅС‹Р№ РІР»Р°РґРµР»РµС† ExoPlayer**:
   - `MusicService` СЃРѕР·РґР°С‘С‚ `ExoPlayer` РІ `onGetSession()`, РѕР±РѕСЂР°С‡РёРІР°РµС‚ РµРіРѕ РІ `MediaSession` Рё РІРѕР·РІСЂР°С‰Р°РµС‚ РєРѕРЅС‚СЂРѕР»Р»РµСЂР°Рј.
   - РЎРµСЂРІРёСЃ СЃР°Рј РїРѕРєР°Р·С‹РІР°РµС‚ MediaStyle-СѓРІРµРґРѕРјР»РµРЅРёРµ, РїРѕР»СѓС‡Р°РµС‚ foreground-СЃС‚Р°С‚СѓСЃ Рё РїРµСЂРµР¶РёРІР°РµС‚ СѓС…РѕРґ РїСЂРёР»РѕР¶РµРЅРёСЏ РІ С„РѕРЅ.
   - `ExoPlayer` Р±РѕР»СЊС€Рµ РЅРµ СЏРІР»СЏРµС‚СЃСЏ Hilt-СЃРёРЅРіР»С‚РѕРЅРѕРј: СѓРґР°Р»С‘РЅ `PlayerModule`, С‡С‚РѕР±С‹ UI РЅРµ РјРѕРі СЃР»СѓС‡Р°Р№РЅРѕ РѕСЃРІРѕР±РѕРґРёС‚СЊ РёР»Рё СЃРѕР·РґР°С‚СЊ РІС‚РѕСЂРѕР№ РїР»РµРµСЂ.
   - `onDestroy()` РѕСЃРІРѕР±РѕР¶РґР°РµС‚ СЃРЅР°С‡Р°Р»Р° `MediaSession`, Р·Р°С‚РµРј `ExoPlayer` вЂ” Р±РµР· В«РјС‘СЂС‚РІРѕРіРѕВ» РѕР±С‰РµРіРѕ РїР»РµРµСЂР°.
2. **PlayerController РїРµСЂРµРІРµРґС‘РЅ РЅР° MediaController**:
   - РљРѕРЅС‚СЂРѕР»Р»РµСЂ РїРѕРґРєР»СЋС‡Р°РµС‚СЃСЏ Рє СЃРµСЂРІРёСЃСѓ С‡РµСЂРµР· `SessionToken` Рё `MediaController.Builder` СЃ application context.
   - Р’СЃРµ РєРѕРјР°РЅРґС‹ (play/pause, seek, СЃРєРѕСЂРѕСЃС‚СЊ, РїРѕР·РёС†РёСЏ, Р±СѓС„РµСЂ) РёРґСѓС‚ С‡РµСЂРµР· `MediaController`.
   - РЎРѕС…СЂР°РЅРµРЅС‹ РѕС‡РµСЂРµРґСЊ, shuffle/repeat, СЂСѓС‡РЅРѕР№ Next РїСЂРё `Repeat OFF`, Р·Р°РїРёСЃСЊ РёСЃС‚РѕСЂРёРё Рё РїРѕРІС‚РѕСЂ С‚СЂРµРєР° РїРѕСЃР»Рµ РѕС€РёР±РєРё.
   - РљРЅРѕРїРєРё Prev/Next РёР· СѓРІРµРґРѕРјР»РµРЅРёСЏ РїРѕ-РїСЂРµР¶РЅРµРјСѓ СЂР°Р±РѕС‚Р°СЋС‚ СЃ РѕС‡РµСЂРµРґСЊСЋ С‡РµСЂРµР· `ForwardingPlayer`.
3. **РњРµС‚Р°РґР°РЅРЅС‹Рµ СѓРІРµРґРѕРјР»РµРЅРёСЏ**:
   - Р’ `MediaItem` РґРѕР±Р°РІР»РµРЅС‹ `mediaId`, `MediaMetadata` СЃ title, artist Рё artworkUri вЂ” РІ С€С‚РѕСЂРєРµ РѕС‚РѕР±СЂР°Р¶Р°СЋС‚СЃСЏ РЅР°Р·РІР°РЅРёРµ, РёСЃРїРѕР»РЅРёС‚РµР»СЊ Рё РѕР±Р»РѕР¶РєР°.
4. **Р¤РѕРЅ Рё Р±Р»РѕРєРёСЂРѕРІРєР° СЌРєСЂР°РЅР°**:
   - ExoPlayer РЅР°СЃС‚СЂРѕРµРЅ РЅР° `AudioAttributes` СЃ РѕР±СЂР°Р±РѕС‚РєРѕР№ Р°СѓРґРёРѕС„РѕРєСѓСЃР°.
   - Р’РєР»СЋС‡РµРЅС‹ `setHandleAudioBecomingNoisy(true)` Рё `setWakeMode(C.WAKE_MODE_NETWORK)` (partial wake lock + Wi-Fi lock).
   - Р’ РјР°РЅРёС„РµСЃС‚ РґРѕР±Р°РІР»РµРЅ `WAKE_LOCK`.
5. **Р‘РµР·РѕРїР°СЃРЅС‹Рµ РјРёРіСЂР°С†РёРё Room**:
   - РЈР±СЂР°РЅ `fallbackToDestructiveMigration()`.
   - Р’РєР»СЋС‡С‘РЅ `exportSchema = true`, РїСѓС‚СЊ СЃС…РµРј вЂ” `app/schemas` С‡РµСЂРµР· `room.schemaLocation`.
   - Р”РѕР±Р°РІР»РµРЅС‹ СЂРµР°Р»СЊРЅС‹Рµ РјРёРіСЂР°С†РёРё: 1в†’2 СЃРѕР·РґР°С‘С‚ `chart_cache`, 2в†’3 РґРѕР±Р°РІР»СЏРµС‚ `latitude`/`longitude`, 3в†’4 РґРѕР±Р°РІР»СЏРµС‚ `season`.
   - Р¤Р°РєС‚РёС‡РµСЃРєР°СЏ РёСЃС‚РѕСЂРёСЏ: РІРµСЂСЃРёСЏ 3 РЅРµ РІС‹РїСѓСЃРєР°Р»Р°СЃСЊ РѕС‚РґРµР»СЊРЅРѕ, РїРѕСЌС‚РѕРјСѓ РєРѕРѕСЂРґРёРЅР°С‚С‹ Р·Р°РєСЂРµРїР»РµРЅС‹ Р·Р° v3, Р° СЃРµР·РѕРЅ вЂ” Р·Р° v4; СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёРµ Р»Р°Р№РєРё Рё РёСЃС‚РѕСЂРёСЏ РЅРµ С‚РµСЂСЏСЋС‚СЃСЏ.
   - Р”РѕР±Р°РІР»РµРЅ JVM-С‚РµСЃС‚ РјРёРіСЂР°С†РёРё v1в†’v4 СЃ РїСЂРѕРІРµСЂРєРѕР№ СЃРѕС…СЂР°РЅРЅРѕСЃС‚Рё Р»Р°Р№РєР°, РёСЃС‚РѕСЂРёРё Рё СЃРѕР·РґР°РЅРёСЏ РєСЌС€Р° С‡Р°СЂС‚РѕРІ.
6. **РўСЂР°РЅР·Р°РєС†РёРѕРЅРЅС‹Р№ Р»Р°Р№Рє**:
   - `LikedTracksDao.toggleLikedTrack()` РїРѕРјРµС‡РµРЅ `@Transaction`: С‡С‚РµРЅРёРµ Рё Р·Р°РїРёСЃСЊ РІС‹РїРѕР»РЅСЏСЋС‚СЃСЏ Р°С‚РѕРјР°СЂРЅРѕ, РґРІРѕР№РЅРѕР№ С‚Р°Рї РЅРµ РѕСЃС‚Р°РІР»СЏРµС‚ Р‘Р” РІ РЅРµРІРµСЂРЅРѕРј СЃРѕСЃС‚РѕСЏРЅРёРё.
   - `LikedTracksRepositoryImpl.toggleLike()` РїРµСЂРµРІРµРґС‘РЅ РЅР° РЅРѕРІС‹Р№ РјРµС‚РѕРґ DAO.
7. **РџСЂРѕРІРµСЂРєРё**:
   - `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.
   - `.\gradlew test` вЂ” `BUILD SUCCESSFUL`, 43/43 С‚РµСЃС‚Р° РІ debug Рё release, 0 failures, 0 errors.
   - `.\gradlew lint` вЂ” `BUILD SUCCESSFUL`, 0 errors, 21 warning (Р±РµР· РЅРѕРІС‹С… Р·Р°РјРµС‡Р°РЅРёР№).

### Р¤Р°Р№Р»С‹
```
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/facet/music/core/player/MusicService.kt
app/src/main/java/com/facet/music/core/player/PlayerController.kt
app/src/main/java/com/facet/music/core/player/PlayerModule.kt (СѓРґР°Р»С‘РЅ)
app/src/main/java/com/facet/music/data/local/AppDatabase.kt
app/src/main/java/com/facet/music/data/local/DatabaseModule.kt
app/src/main/java/com/facet/music/data/local/DatabaseMigrations.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/local/dao/LikedTracksDao.kt
app/src/main/java/com/facet/music/data/local/LikedTracksRepositoryImpl.kt
app/schemas/com.facet.music.data.local.AppDatabase/4.json (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/core/player/PlayerControllerQueueTest.kt
app/src/test/java/com/facet/music/data/local/DatabaseMigrationTest.kt (РЅРѕРІС‹Р№)
PROGRESS.md
MEMORY.md
```

---

## вњ… Р¤Р°Р·Р° 7 вЂ” РЈРјРЅР°СЏ В«РњРѕСЏ РІРѕР»РЅР°В»: СЃРµСЃСЃРёРё, Last.fm Рё feedback (21.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РЎРµСЃСЃРёРѕРЅРЅР°СЏ Р±РµСЃРєРѕРЅРµС‡РЅР°СЏ РІРѕР»РЅР°**:
   - Р’ `WaveRepository` РґРѕР±Р°РІР»РµРЅС‹ `WaveSession`, `WaveBatch` Рё `StateFlow<WaveSession?> activeSession`; РјРµС‚РѕРґС‹ `startSession()`, `nextBatch()`, `reset()`.
   - РЎС‚Р°СЂС‹Р№ `generateWave()` СЃРѕС…СЂР°РЅС‘РЅ РєР°Рє СЃРѕРІРјРµСЃС‚РёРјС‹Р№ РІС‹Р·РѕРІ В«РЅРѕРІР°СЏ СЃРµСЃСЃРёСЏ + РїРµСЂРІР°СЏ РїР°С‡РєР°В».
   - РњРёРєСЃ РїР°С‡РєРё: 40% РїРѕС…РѕР¶РёРµ Р°СЂС‚РёСЃС‚С‹ Last.fm, 30% Р»Р°Р№РєРё, 20% РёСЃС‚РѕСЂРёСЏ, 10% Р¶Р°РЅСЂРѕРІС‹Рµ РЅР°С…РѕРґРєРё; РїСѓСЃС‚С‹Рµ РёСЃС‚РѕС‡РЅРёРєРё РґРѕР±РёСЂР°СЋС‚СЃСЏ РёР· РѕСЃС‚Р°Р»СЊРЅС‹С…, РїРѕСЃР»РµРґРЅРёР№ С„РѕР»Р±СЌРє вЂ” Top 10 SoundCloud.
   - Р’РЅСѓС‚СЂРё СЃРµСЃСЃРёРё РІРµРґС‘С‚СЃСЏ `shownTrackIds`, РїРѕСЌС‚РѕРјСѓ С‚СЂРµРєРё РЅРµ РїРѕРІС‚РѕСЂСЏСЋС‚СЃСЏ; РЅР°РєРѕРїРёС‚РµР»СЊРЅС‹Рµ СЃС‡С‘С‚С‡РёРєРё РёСЃС‚РѕС‡РЅРёРєРѕРІ РѕР±РЅРѕРІР»СЏСЋС‚СЃСЏ РїРѕСЃР»Рµ РєР°Р¶РґРѕР№ РїР°С‡РєРё.
   - `reset()` СЃСЂР°Р·Сѓ РѕР±РЅСѓР»СЏРµС‚ `activeSession`, Р·Р°С‚РµРј Р¶РґС‘С‚ mutex вЂ” Р»РµС‚СЏС‰РёР№ `nextBatch` РЅРµ РјРѕР¶РµС‚ РґРѕРїРёСЃР°С‚СЊ СЃС‚Р°СЂСѓСЋ РїР°С‡РєСѓ РІ РЅРѕРІСѓСЋ РѕС‡РµСЂРµРґСЊ.
2. **Last.fm Рё СЃРµРјРµРЅР° РІРєСѓСЃРѕРІ**:
   - Р”РѕР±Р°РІР»РµРЅС‹ `LastFmApi`, DTO, `LastFmRepository` Рё `LastFmRepositoryImpl` (`artist.getsimilar`).
   - РЎРµРјРµРЅР° Р°СЂС‚РёСЃС‚РѕРІ: С‚РѕРї РІРєСѓСЃРѕРІ + РёСЃС‚РѕСЂРёСЏ + Р»Р°Р№РєРё; СЃРµРјРµРЅР° Р¶Р°РЅСЂРѕРІ: С‚РѕРї РІРєСѓСЃРѕРІ + Р»Р°Р№РєРё + РёСЃС‚РѕСЂРёСЏ.
   - РџРѕС…РѕР¶РёРµ Р°СЂС‚РёСЃС‚С‹ РёС‰СѓС‚СЃСЏ РІ SoundCloud С‡РµСЂРµР· NewPipe; РєР»СЋС‡ Last.fm Р±РµСЂС‘С‚СЃСЏ РёР· `BuildConfig.LASTFM_API_KEY` (gradle property РёР»Рё env `LASTFM_API_KEY`). Р‘РµР· РєР»СЋС‡Р° РІРѕР»РЅР° СЂР°Р±РѕС‚Р°РµС‚ РЅР° Р»РѕРєР°Р»СЊРЅС‹С… РёСЃС‚РѕС‡РЅРёРєР°С… Рё С‡Р°СЂС‚Рµ.
3. **Р›РѕРєР°Р»СЊРЅС‹Р№ РїСЂРѕС„РёР»СЊ РІРєСѓСЃРѕРІ Рё feedback**:
   - Room v5: `genre_weights`, `artist_weights`, `user_preferences`; РјРёРіСЂР°С†РёСЏ `MIGRATION_4_5`, СЃС…РµРјР° `5.json`.
   - `TasteRepository`: РґРѕСЃР»СѓС€РёРІР°РЅРёРµ >80% вЂ” `+1`, СЃРєРёРї <30 СЃРµРєСѓРЅРґ вЂ” `-1`, Р»Р°Р№Рє вЂ” `+3`, СЃРЅСЏС‚РёРµ Р»Р°Р№РєР°/РґРёР·Р»Р°Р№Рє вЂ” `-3`; Р¶Р°РЅСЂС‹ Рё Р°СЂС‚РёСЃС‚С‹ РЅРѕСЂРјР°Р»РёР·СѓСЋС‚СЃСЏ.
   - `PlayerViewModel` СЃС‡РёС‚Р°РµС‚ СЂРµР°Р»СЊРЅРѕ РїСЂРѕРёРіСЂР°РЅРЅРѕРµ РІСЂРµРјСЏ Рё РїРёС€РµС‚ feedback РїСЂРё СЃРјРµРЅРµ С‚СЂРµРєР°; Р»Р°Р№РєРё Рё СЃРЅСЏС‚РёРµ Р»Р°Р№РєРѕРІ С‚Р°РєР¶Рµ РѕР±РЅРѕРІР»СЏСЋС‚ РІРµСЃР°.
4. **РђРІС‚РѕРґРѕР·Р°РіСЂСѓР·РєР° РІ РїР»РµРµСЂРµ**:
   - РџСЂРё 3 РѕСЃС‚Р°РІС€РёС…СЃСЏ С‚СЂРµРєР°С… `PlayerViewModel` Р·Р°РїСЂР°С€РёРІР°РµС‚ РїР°С‡РєСѓ РёР· 12 Рё РґРѕРїРёСЃС‹РІР°РµС‚ РµС‘ С‡РµСЂРµР· `PlayerController.appendToQueue()` Р±РµР· РѕСЃС‚Р°РЅРѕРІРєРё С‚РµРєСѓС‰РµРіРѕ С‚СЂРµРєР°.
   - `appendToQueue()` РѕС‚Р±СЂР°СЃС‹РІР°РµС‚ РїСѓСЃС‚С‹Рµ id Рё РґСѓР±Р»РёРєР°С‚С‹, СЃРѕС…СЂР°РЅСЏРµС‚ РёСЃС…РѕРґРЅСѓСЋ РѕС‡РµСЂРµРґСЊ РґР»СЏ РєРѕСЂСЂРµРєС‚РЅРѕРіРѕ РІС‹РєР»СЋС‡РµРЅРёСЏ shuffle.
   - Р СѓС‡РЅРѕР№ Р·Р°РїСѓСЃРє С‚СЂРµРєР° РёР· РїРѕРёСЃРєР° РёР»Рё РјРµРґРёР°С‚РµРєРё СЃР±СЂР°СЃС‹РІР°РµС‚ СЃРµСЃСЃРёСЋ РІРѕР»РЅС‹, С‡С‚РѕР±С‹ Р°РІС‚РѕРґРѕР·Р°РіСЂСѓР·РєР° РЅРµ РїРѕРґРјРµС€РёРІР°Р»Р° СЃС‚Р°СЂСѓСЋ РІРѕР»РЅСѓ.
5. **UI РІРѕР»РЅС‹ Рё РЅР°СЃС‚СЂРѕР№РєРё**:
   - `WaveViewModel` СЃРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµС‚ СЃРїРёСЃРѕРє СЃ РѕС‡РµСЂРµРґСЊСЋ РїР»РµРµСЂР°, РїРѕРєР°Р·С‹РІР°РµС‚ РЅР°РєРѕРїРёС‚РµР»СЊРЅС‹Рµ СЃС‡С‘С‚С‡РёРєРё РІСЃРµР№ СЃРµСЃСЃРёРё, Р¶Р°РЅСЂС‹, RU-РїСЂРёРѕСЂРёС‚РµС‚ Рё СЃРѕСЃС‚РѕСЏРЅРёРµ Р·Р°РІРµСЂС€РµРЅРёСЏ.
   - `WaveScreen`: Р±РµР№РґР¶Рё В«RU РІ РїСЂРёРѕСЂРёС‚РµС‚РµВ»/В«Р’СЃРµ СЏР·С‹РєРёВ» Рё Р¶Р°РЅСЂРѕРІ, СЃС‡С‘С‚С‡РёРє РїРѕС…РѕР¶РёС…, РєРЅРѕРїРєРё В«РќРѕРІР°СЏ РІРѕР»РЅР°В» Рё В«РЎР±СЂРѕСЃРёС‚СЊВ», СЃРѕРѕР±С‰РµРЅРёРµ Рѕ Р·Р°РІРµСЂС€РµРЅРёРё РІРѕР»РЅС‹.
   - Р’ РЅР°СЃС‚СЂРѕР№РєР°С… РґРѕР±Р°РІР»РµРЅ РїРµСЂРµРєР»СЋС‡Р°С‚РµР»СЊ В«Р СѓСЃСЃРєР°СЏ РјСѓР·С‹РєР° РІ РїСЂРёРѕСЂРёС‚РµС‚РµВ» (`pref_ru_language`), РїСЂРё РІРєР»СЋС‡РµРЅРёРё РєРёСЂРёР»Р»РёС‡РµСЃРєРёРµ С‚СЂРµРєРё РёРґСѓС‚ РїРµСЂРІС‹РјРё.
6. **РџСЂРѕРІРµСЂРєРё**:
   - `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.
   - `.\gradlew test` вЂ” `BUILD SUCCESSFUL`, 50/50 С‚РµСЃС‚РѕРІ РІ debug Рё release, 0 failures, 0 errors (+7 С‚РµСЃС‚РѕРІ: РІРєСѓСЃС‹, СЃРµСЃСЃРёСЏ/РґРµРґСѓРїР»РёРєР°С†РёСЏ, RU-РїСЂРёРѕСЂРёС‚РµС‚, appendToQueue, РјРёРіСЂР°С†РёСЏ v5).
   - `.\gradlew lint` вЂ” `BUILD SUCCESSFUL`, 0 errors, 21 warning (Р±РµР· РЅРѕРІС‹С… Р·Р°РјРµС‡Р°РЅРёР№).

### РћРіСЂР°РЅРёС‡РµРЅРёСЏ
- Last.fm-РєР»СЋС‡ РЅРµ РІС…РѕРґРёС‚ РІ СЂРµРїРѕР·РёС‚РѕСЂРёР№; Р±РµР· РЅРµРіРѕ РєР°С‡РµСЃС‚РІРѕ РІРѕР»РЅС‹ РЅРёР¶Рµ, РЅРѕ РїСЂРёР»РѕР¶РµРЅРёРµ РїРѕР»РЅРѕСЃС‚СЊСЋ СЂР°Р±РѕС‚РѕСЃРїРѕСЃРѕР±РЅРѕ.
- РќР°СЃС‚СЂРѕРµРЅРёСЏ РІРѕР»РЅС‹ (Р±РѕРґСЂР°СЏ/СЃРїРѕРєРѕР№РЅР°СЏ/РїРѕ Р»СЋР±РёРјС‹Рј/РѕС‚РєСЂС‹С‚РёСЏ) РїРѕРєР° РЅРµ СЂРµР°Р»РёР·РѕРІР°РЅС‹ вЂ” СЌС‚Рѕ СЃР»РµРґСѓСЋС‰РёР№ СЌС‚Р°Рї.
- РљРѕРѕСЂРґРёРЅР°С‚С‹ Рё СЃРµР·РѕРЅ РїРѕ-РїСЂРµР¶РЅРµРјСѓ СЃРѕС…СЂР°РЅСЏСЋС‚СЃСЏ РІ Р»Р°Р№РєР°С…, РЅРѕ РІ РєР°СЂС‚РѕС‡РєР°С… В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёР№В» РµС‰С‘ РЅРµ РѕС‚РѕР±СЂР°Р¶Р°СЋС‚СЃСЏ.

### Р¤Р°Р№Р»С‹
```
app/build.gradle.kts
app/schemas/com.facet.music.data.local.AppDatabase/5.json (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/di/RepositoryModule.kt
app/src/main/java/com/facet/music/core/network/NetworkModule.kt
app/src/main/java/com/facet/music/core/player/PlayerController.kt
app/src/main/java/com/facet/music/data/local/AppDatabase.kt
app/src/main/java/com/facet/music/data/local/DatabaseMigrations.kt
app/src/main/java/com/facet/music/data/local/DatabaseModule.kt
app/src/main/java/com/facet/music/data/local/TasteRepositoryImpl.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/local/dao/TasteDao.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/local/entity/TasteEntities.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/repository/LastFmRepository.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/repository/TasteRepository.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/repository/WaveRepository.kt
app/src/main/java/com/facet/music/data/source/lastfm/LastFmApi.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/source/lastfm/LastFmRepositoryImpl.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/source/wave/WaveRepositoryImpl.kt
app/src/main/java/com/facet/music/feature/player/PlayerViewModel.kt
app/src/main/java/com/facet/music/feature/settings/SettingsScreen.kt
app/src/main/java/com/facet/music/feature/settings/SettingsViewModel.kt
app/src/main/java/com/facet/music/feature/wave/WaveScreen.kt
app/src/main/java/com/facet/music/feature/wave/WaveViewModel.kt
app/src/test/java/com/facet/music/core/player/PlayerControllerQueueTest.kt
app/src/test/java/com/facet/music/data/local/DatabaseMigrationTest.kt
app/src/test/java/com/facet/music/data/local/TasteRepositoryTest.kt (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/data/source/wave/WaveRepositoryTest.kt
README.md
PROGRESS.md
MEMORY.md
```

---

## вњ… РҐРѕС‚С„РёРєСЃ вЂ” РєР»РёРє Play/Pause Рё РєР°С‡РµСЃС‚РІРѕ РІРѕР»РЅС‹ (21.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РљРЅРѕРїРєР° Play/Pause Р±РѕР»СЊС€Рµ РЅРµ РїРµСЂРµРјР°С‚С‹РІР°РµС‚ С‚СЂРµРє**:
   - `ExpressivePlayerSlider`: СЃРµРЅСЃРѕСЂРЅР°СЏ РІС‹СЃРѕС‚Р° СѓРјРµРЅСЊС€РµРЅР° СЃ 46 РґРѕ 38dp, Р° С‚Р°Рї Рё РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅС‹Р№ РґСЂР°Рі РїСЂРёРЅРёРјР°СЋС‚СЃСЏ С‚РѕР»СЊРєРѕ РІ С†РµРЅС‚СЂР°Р»СЊРЅРѕР№ РїРѕР»РѕСЃРµ В±14dp РІРѕРєСЂСѓРі РІРѕР»РЅС‹. Р Р°РЅСЊС€Рµ РЅРµРІРёРґРёРјР°СЏ РїРѕР»РѕСЃР° СЃР»Р°Р№РґРµСЂР° РїРµСЂРµС…РІР°С‚С‹РІР°Р»Р° РєР°СЃР°РЅРёСЏ, РїСЂРµРґРЅР°Р·РЅР°С‡РµРЅРЅС‹Рµ РєРЅРѕРїРєР°Рј РїРѕРґ РЅРµР№.
   - `PlayerControlIsland`: Р·Р°Р·РѕСЂ РјРµР¶РґСѓ РІРѕР»РЅРѕР№ Рё СЂСЏРґРѕРј РєРЅРѕРїРѕРє СѓРІРµР»РёС‡РµРЅ СЃ 8 РґРѕ 14dp вЂ” РґР°Р¶Рµ РїСЂРѕРјР°С… РїР°Р»СЊС†РµРј РІС‹С€Рµ РєРЅРѕРїРєРё РЅРµ РїРѕРїР°РґР°РµС‚ РІ Р·РѕРЅСѓ РїРµСЂРµРјРѕС‚РєРё.
   - `PlayerProgressSection`: Р·Р°РіР»СѓС€РєР° Р·Р°РіСЂСѓР·РєРё РІС‹СЂРѕРІРЅРµРЅР° РїРѕ РІС‹СЃРѕС‚Рµ (38dp), С‡С‚РѕР±С‹ РїСЂРё Crossfade В«Р·Р°РіСЂСѓР·РєР° в†’ СЃР»Р°Р№РґРµСЂВ» Р»РµР№Р°СѓС‚ РЅРµ РїСЂС‹РіР°Р».
2. **Р’РѕР»РЅР°: СЏР·С‹Рє, Р°СЂС‚РёСЃС‚ Рё Р¶Р°РЅСЂ**:
   - РљР°РЅРґРёРґР°С‚С‹ РєР°Р¶РґРѕРіРѕ РёСЃС‚РѕС‡РЅРёРєР° РґРµР»СЏС‚СЃСЏ РЅР° СЃС‚СЂРѕРіРёР№ Рё Р·Р°РїР°СЃРЅРѕР№ РїСѓР». РџСЂРё RU-РїСЂРёРѕСЂРёС‚РµС‚Рµ РєРёСЂРёР»Р»РёС†Р° Р·Р°РїРѕР»РЅСЏРµС‚ РєРІРѕС‚С‹ РїРµСЂРІРѕР№, Р»Р°С‚РёРЅРёС†Р° РїРѕРґРєР»СЋС‡Р°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РїСЂРё РЅРµС…РІР°С‚РєРµ СЃРІРѕРёС… С‚СЂРµРєРѕРІ (СЂР°РЅСЊС€Рµ РѕРЅР° РїСЂРѕСЃС‚Рѕ СЃРѕСЂС‚РёСЂРѕРІР°Р»Р°СЃСЊ РІ РєРѕРЅРµС†, РЅРѕ РІСЃС‘ СЂР°РІРЅРѕ РїРѕРїР°РґР°Р»Р° РІ РїР°С‡РєСѓ).
   - Last.fm: РїРѕС…РѕР¶РёРµ Р°СЂС‚РёСЃС‚С‹ СЃ РєРёСЂРёР»Р»РёС†РµР№ РїСЂРѕРІРµСЂСЏСЋС‚СЃСЏ РїРµСЂРІС‹РјРё; СЂРµР·СѓР»СЊС‚Р°С‚С‹ РїРѕРёСЃРєР° SoundCloud С„РёР»СЊС‚СЂСѓСЋС‚СЃСЏ РїРѕ СЂРµР°Р»СЊРЅРѕРјСѓ СЃРѕРІРїР°РґРµРЅРёСЋ Р°СЂС‚РёСЃС‚Р° (Р±РµР· СЂРµРіРёСЃС‚СЂР° Рё РїСѓРЅРєС‚СѓР°С†РёРё), С‡СѓР¶РёРµ С‚СЂРµРєРё вЂ” С‚РѕР»СЊРєРѕ С„РѕР»Р±СЌРє.
   - Р–Р°РЅСЂС‹: РЅР°С…РѕРґРєР° СЃС‡РёС‚Р°РµС‚СЃСЏ РІР°Р»РёРґРЅРѕР№ С‚РѕР»СЊРєРѕ РїСЂРё СЃРѕРІРїР°РґРµРЅРёРё Р¶Р°РЅСЂР° РїРѕ СЃР»РѕРІР°Рј Рё СЃ Р°Р»РёР°СЃР°РјРё (СЂСЌРї/rap, С‚СЂСЌРї/trap, С…Р°СѓСЃ/house Рё С‚.Рґ.); РјСѓСЃРѕСЂРЅС‹Рµ РїРѕРґРїРёСЃРё (В«MusicВ», В«PodcastВ», В«SoundCloudВ») РЅРµ РёСЃРїРѕР»СЊР·СѓСЋС‚СЃСЏ РєР°Рє СЃРµРјРµРЅР°.
   - РЎРѕРѕС‚РЅРѕС€РµРЅРёРµ 40/30/20/10 СЃРѕС…СЂР°РЅРµРЅРѕ, РїРѕСЂСЏРґРѕРє РґРѕР±РѕСЂР°: СЃС‚СЂРѕРіРёРµ РІ РєРІРѕС‚Р°С… в†’ СЃС‚СЂРѕРіРёРµ СЃРІРµСЂС… РєРІРѕС‚ в†’ Р·Р°РїР°СЃРЅС‹Рµ в†’ С‡Р°СЂС‚С‹.
3. **РџСЂРѕРІРµСЂРєРё**:
   - `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.
   - `.\gradlew test` вЂ” `BUILD SUCCESSFUL`, 53/53 С‚РµСЃС‚Р° РІ debug Рё release, 0 failures, 0 errors (+3 С‚РµСЃС‚Р°: СЏР·С‹Рє РїСЂРё RU-РїСЂРёРѕСЂРёС‚РµС‚Рµ, С‚РѕС‡РЅС‹Р№ Р¶Р°РЅСЂ, СЃРѕРІРїР°РґРµРЅРёРµ Р°СЂС‚РёСЃС‚Р°).

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/core/ui/components/ExpressivePlayerSlider.kt
app/src/main/java/com/facet/music/core/ui/components/ExpressivePlayerComponents.kt
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
app/src/main/java/com/facet/music/data/source/wave/WaveRepositoryImpl.kt
app/src/test/java/com/facet/music/data/source/wave/WaveRepositoryTest.kt
PROGRESS.md
MEMORY.md
```

---

## вњ… РЎРµСЃСЃРёСЏ 9 вЂ” РћС„Р»Р°Р№РЅ-СЂРµР¶РёРј: СЃРєР°С‡РёРІР°РЅРёРµ С‚СЂРµРєРѕРІ (21.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Room v6 вЂ” С‚Р°Р±Р»РёС†Р° СЃРєР°С‡Р°РЅРЅС‹С… С‚СЂРµРєРѕРІ**:
   - `DownloadedTrackEntity` (`downloaded_tracks`): `id`, `title`, `artist`, `artworkUrl`, `durationMs`, `localFilePath`, `downloadedAt`.
   - `DownloadedTracksDao`: `insert` (REPLACE), `delete`, `getById`, `isDownloaded(): Flow<Boolean>`, `getDownloadedIds(): Flow<List<String>>`, `getAll(): Flow<List<DownloadedTrackEntity>>`.
   - `AppDatabase` в†’ РІРµСЂСЃРёСЏ 6; `MIGRATION_5_6` СЃРѕР·РґР°С‘С‚ С‚Р°Р±Р»РёС†Сѓ; СЃС…РµРјР° `app/schemas/com.facet.music.data.local.AppDatabase/6.json` СЌРєСЃРїРѕСЂС‚РёСЂРѕРІР°РЅР°.
   - Р”РѕРјРµРЅРЅР°СЏ РјРѕРґРµР»СЊ `DownloadedTrack` + РјР°РїРїРµСЂС‹; `DownloadRepository` / `DownloadRepositoryImpl`: РїСЂРѕРІРµСЂРєР° СЃСѓС‰РµСЃС‚РІРѕРІР°РЅРёСЏ С„Р°Р№Р»Р°, Р°РІС‚РѕРѕС‡РёСЃС‚РєР° Р±РёС‚С‹С… Р·Р°РїРёСЃРµР№, СѓРґР°Р»РµРЅРёРµ С„Р°Р№Р»Р° РІРјРµСЃС‚Рµ СЃ Р·Р°РїРёСЃСЊСЋ; Hilt-Р±РёРЅРґРёРЅРіРё РІ `RepositoryModule` Рё `DatabaseModule`.
2. **WorkManager вЂ” С„РѕРЅРѕРІРѕРµ СЃРєР°С‡РёРІР°РЅРёРµ**:
   - Р”РѕР±Р°РІР»РµРЅР° Р·Р°РІРёСЃРёРјРѕСЃС‚СЊ `androidx.work:work-runtime-ktx:2.9.1` (РµРґРёРЅСЃС‚РІРµРЅРЅР°СЏ РЅРѕРІР°СЏ РІРµСЂСЃРёСЏ РІ РїСЂРѕРµРєС‚Рµ).
   - `DownloadTrackWorker` (CoroutineWorker): СЂРµР·РѕР»РІРёС‚ РїРѕС‚РѕРє С‡РµСЂРµР· `SoundCloudRepository.resolveStreamUrl`, РєР°С‡Р°РµС‚ OkHttp РІ `filesDir/downloads/`, РїСѓР±Р»РёРєСѓРµС‚ РїСЂРѕРіСЂРµСЃСЃ РІ WorkManager, Р·Р°С‚РµРј СЃРѕС…СЂР°РЅСЏРµС‚ Р·Р°РїРёСЃСЊ РІ Room. РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РѕС‚РґРµР»СЊРЅС‹Р№ OkHttp-РєР»РёРµРЅС‚: Сѓ РѕР±С‰РµРіРѕ `callTimeout = 15 СЃ`, РєРѕС‚РѕСЂС‹Р№ СѓР±РёРІР°Р» РґР»РёРЅРЅС‹Рµ Р·Р°РіСЂСѓР·РєРё.
   - HLS (m3u8) РѕРґРЅРёРј mp3-С„Р°Р№Р»РѕРј РЅРµ СЃРѕС…СЂР°РЅРёС‚СЊ вЂ” РІРѕСЂРєРµСЂ Р·Р°РІРµСЂС€Р°РµС‚ Р·Р°РґР°С‡Сѓ РѕС€РёР±РєРѕР№; РїСЂРѕРіСЂРµСЃСЃРёРІРЅС‹Р№ MP3 СЂРµР·РѕР»РІРёС‚СЃСЏ РїСЂРёРѕСЂРёС‚РµС‚РЅРѕ.
   - РРјСЏ С„Р°Р№Р»Р° вЂ” Р±РµР·РѕРїР°СЃРЅР°СЏ РІРµСЂСЃРёСЏ `track.id` + РєРѕСЂРѕС‚РєРёР№ С…СЌС€ + `.mp3`: Сѓ SoundCloud `id` вЂ” СЌС‚Рѕ URL СЃРѕ СЃР»СЌС€Р°РјРё Рё РґРІРѕРµС‚РѕС‡РёСЏРјРё.
   - `DownloadManager`: `download(track)` СЃС‚Р°РІРёС‚ СѓРЅРёРєР°Р»СЊРЅСѓСЋ СЂР°Р±РѕС‚Сѓ (`ExistingWorkPolicy.KEEP`, constraint `NetworkType.CONNECTED`), `cancel(trackId)`, `observeState(trackId): Flow<DownloadState>` (Room + WorkInfo).
   - Р—Р°РІРёСЃРёРјРѕСЃС‚Рё РІРѕСЂРєРµСЂР° вЂ” С‡РµСЂРµР· Hilt `@EntryPoint` (`DownloadWorkerEntryPoint`), Р±РµР· РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅРѕР№ Р·Р°РІРёСЃРёРјРѕСЃС‚Рё `androidx.hilt:hilt-work`.
3. **РџР»РµРµСЂ вЂ” Р»РѕРєР°Р»СЊРЅС‹Р№ С„Р°Р№Р» РІ РїСЂРёРѕСЂРёС‚РµС‚Рµ**:
   - `PlayerController.resolveAndPlay()` СЃРЅР°С‡Р°Р»Р° СЃРїСЂР°С€РёРІР°РµС‚ `DownloadRepository.getLocalFile(track.id)`; РµСЃР»Рё С„Р°Р№Р» СЃСѓС‰РµСЃС‚РІСѓРµС‚ вЂ” `MediaItem` СЃС‚СЂРѕРёС‚СЃСЏ РёР· `file://`-URI, СЃРµС‚СЊ РЅРµ РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ. РќРµС‚ С„Р°Р№Р»Р° вЂ” РїСЂРµР¶РЅРёР№ РїСѓС‚СЊ С‡РµСЂРµР· `resolveStreamUrl`.
   - Р’ JVM-С‚РµСЃС‚Р°С… РєРѕРЅСЃС‚СЂСѓРєС‚РѕСЂ РїСЂРёРЅРёРјР°РµС‚ `downloadRepository = null` вЂ” 17 С‚РµСЃС‚РѕРІ РѕС‡РµСЂРµРґРё РЅРµ РјРµРЅСЏР»РёСЃСЊ.
4. **UI**:
   - `PlayerScreen`: СЂСЏРґРѕРј СЃ Р»Р°Р№РєРѕРј вЂ” РєРЅРѕРїРєР° СЃРєР°С‡РёРІР°РЅРёСЏ 46dp СЃ С‚СЂРµРјСЏ СЃРѕСЃС‚РѕСЏРЅРёСЏРјРё: СЃС‚СЂРµР»РєР° РІРЅРёР· в†’ РєСЂСѓРіРѕРІРѕР№ РїСЂРѕРіСЂРµСЃСЃ (РґРµС‚РµСЂРјРёРЅРёСЂРѕРІР°РЅРЅС‹Р№ РїСЂРё РёР·РІРµСЃС‚РЅРѕРј СЂР°Р·РјРµСЂРµ) в†’ РіР°Р»РѕС‡РєР° `DownloadDone`. РљР»РёРє: СЃРєР°С‡Р°С‚СЊ / РѕС‚РјРµРЅРёС‚СЊ / РЅРёС‡РµРіРѕ РґР»СЏ СѓР¶Рµ РіРѕС‚РѕРІРѕРіРѕ С‚СЂРµРєР°.
   - `PlayerViewModel`: `downloadState: StateFlow<DownloadState>` С‡РµСЂРµР· `flatMapLatest` РїРѕ С‚РµРєСѓС‰РµРјСѓ С‚СЂРµРєСѓ Рё `toggleDownload()`.
   - `TrackCard`: РјР°Р»РµРЅСЊРєР°СЏ РіР°Р»РѕС‡РєР° РЅР° РѕР±Р»РѕР¶РєРµ СЃРєР°С‡Р°РЅРЅРѕРіРѕ С‚СЂРµРєР°; `LibraryViewModel` Рё `SearchViewModel` РѕС‚РґР°СЋС‚ `downloadedIds: StateFlow<Set<String>>` РёР· Room.
   - Р’ РјР°РЅРёС„РµСЃС‚ РґРѕР±Р°РІР»РµРЅ `ACCESS_NETWORK_STATE` вЂ” РѕРЅ РЅСѓР¶РµРЅ СЃРµС‚РµРІРѕРјСѓ constraint'Сѓ WorkManager.
5. **РџСЂРѕРІРµСЂРєРё**:
   - `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL` РїРѕСЃР»Рµ РєР°Р¶РґРѕРіРѕ РёР· С‡РµС‚С‹СЂС‘С… С€Р°РіРѕРІ.
   - `.\gradlew test` вЂ” 53/53 С‚РµСЃС‚Р°, 0 failures (С‚РµСЃС‚ РјРёРіСЂР°С†РёР№ РѕР±РЅРѕРІР»С‘РЅ РїРѕРґ v6 Рё РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅРѕ РїСЂРѕРІРµСЂСЏРµС‚ С‚Р°Р±Р»РёС†Сѓ `downloaded_tracks`).

### РћРіСЂР°РЅРёС‡РµРЅРёСЏ
- РЎРєР°С‡РёРІР°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РїСЂРѕРіСЂРµСЃСЃРёРІРЅС‹Р№ MP3; С‚СЂРµРєРё, РґРѕСЃС‚СѓРїРЅС‹Рµ Р»РёС€СЊ РїРѕ HLS, РѕС„Р»Р°Р№РЅ РЅРµРґРѕСЃС‚СѓРїРЅС‹.
- РћР±Р»РѕР¶РєР° РѕС„Р»Р°Р№РЅ РЅРµ СЃРѕС…СЂР°РЅСЏРµС‚СЃСЏ (РІ Room С‚РѕР»СЊРєРѕ URL РґР»СЏ UI).
- РЈРґР°Р»РµРЅРёРµ СЃРєР°С‡Р°РЅРЅРѕРіРѕ С‚СЂРµРєР° РїРѕРєР° РІРѕР·РјРѕР¶РЅРѕ С‚РѕР»СЊРєРѕ С‡РµСЂРµР· РѕС‡РёСЃС‚РєСѓ РґР°РЅРЅС‹С… РїСЂРёР»РѕР¶РµРЅРёСЏ вЂ” РѕС‚РґРµР»СЊРЅРѕРіРѕ СЌРєСЂР°РЅР° В«РЎРєР°С‡Р°РЅРЅРѕРµВ» РЅРµС‚.

### Р¤Р°Р№Р»С‹
```
app/build.gradle.kts
app/src/main/AndroidManifest.xml
data/model/DownloadedTrack.kt (РЅРѕРІС‹Р№)
data/local/entity/DownloadedTrackEntity.kt (РЅРѕРІС‹Р№)
data/local/dao/DownloadedTracksDao.kt (РЅРѕРІС‹Р№)
data/local/DownloadRepositoryImpl.kt (РЅРѕРІС‹Р№)
data/repository/DownloadRepository.kt (РЅРѕРІС‹Р№)
data/local/AppDatabase.kt
data/local/DatabaseMigrations.kt
data/local/DatabaseModule.kt
core/di/RepositoryModule.kt
core/download/DownloadState.kt (РЅРѕРІС‹Р№)
core/download/DownloadTrackWorker.kt (РЅРѕРІС‹Р№)
core/download/DownloadManager.kt (РЅРѕРІС‹Р№)
core/download/DownloadWorkerEntryPoint.kt (РЅРѕРІС‹Р№)
core/player/PlayerController.kt
feature/player/PlayerViewModel.kt
feature/player/PlayerScreen.kt
core/ui/components/TrackCard.kt
feature/library/LibraryViewModel.kt
feature/library/LibraryScreen.kt
feature/search/SearchViewModel.kt
feature/search/SearchScreen.kt
app/src/test/java/com/facet/music/data/local/DatabaseMigrationTest.kt
app/schemas/com.facet.music.data.local.AppDatabase/6.json (РЅРѕРІС‹Р№)
PROGRESS.md
MEMORY.md
```

---

## вњ… РҐРѕС‚С„РёРєСЃ вЂ” Media3 DownloadManager РґР»СЏ HLS (21.10.2026)

### РџСЂРёС‡РёРЅР°
- Р’РѕСЂРєРµСЂ РїР°РґР°Р» СЃ `Result.failure`: SoundCloud РѕС‚РґР°С‘С‚ С‚СЂРµРє СЃ `deliveryMethod=HLS`, Р° СЃС‚Р°СЂС‹Р№ OkHttp-Р·Р°РіСЂСѓР·С‡РёРє СѓРјРµР» СЃРєР°С‡РёРІР°С‚СЊ С‚РѕР»СЊРєРѕ РѕРґРёРЅ РїСЂРѕРіСЂРµСЃСЃРёРІРЅС‹Р№ С„Р°Р№Р».

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Р—Р°РІРёСЃРёРјРѕСЃС‚Рё**: СЏРІРЅРѕ РґРѕР±Р°РІР»РµРЅС‹ `androidx.media3:media3-datasource:1.5.0` Рё `androidx.media3:media3-database:1.5.0` (exoplayer Рё exoplayer-hls СѓР¶Рµ Р±С‹Р»Рё).
2. **РљСЌС€ Рё DownloadManager**:
   - `OfflineMediaModule` (Hilt, `@Singleton`): `StandaloneDatabaseProvider` (РІ Media3 СЌС‚Рѕ Р·Р°РјРµРЅР° `ExoDatabaseProvider`), `SimpleCache(filesDir/media_cache, NoOpCacheEvictor)`, `CacheDataSource.Factory` РґР»СЏ РїР»РµРµСЂР° Рё Media3 `DownloadManager` СЃ upstream `DefaultDataSource`/`DefaultHttpDataSource` (UA `FacetApp/1.0`).
   - РљСЌС€ РѕРґРёРЅ РЅР° РїСЂРѕС†РµСЃСЃ: Рё Р·Р°РіСЂСѓР·С‡РёРє, Рё `MusicService` РёСЃРїРѕР»СЊР·СѓСЋС‚ РѕРґРёРЅ `SimpleCache`.
3. **`OfflineDownloadManager`**:
   - `enqueue(track, stream)`: `DownloadRequest.Builder(track.id, uri).setMimeType(...)`; HLS в†’ `MimeTypes.APPLICATION_M3U8`, MP3 в†’ `AUDIO_MPEG`; РїРѕРІС‚РѕСЂРЅР°СЏ РїРѕСЃС‚Р°РЅРѕРІРєР° Р°РєС‚РёРІРЅРѕР№/РіРѕС‚РѕРІРѕР№ Р·Р°РіСЂСѓР·РєРё РїСЂРѕРїСѓСЃРєР°РµС‚СЃСЏ.
   - `observeDownload(trackId)`: `Flow<MediaDownloadState>` С‡РµСЂРµР· `DownloadManager.Listener` + РЅР°С‡Р°Р»СЊРЅРѕРµ СЃРѕСЃС‚РѕСЏРЅРёРµ РёР· `DownloadIndex` РЅР° IO.
   - `awaitCompletion(...)`: РѕР¶РёРґР°РЅРёРµ С‚РµСЂРјРёРЅР°Р»СЊРЅРѕРіРѕ СЃРѕСЃС‚РѕСЏРЅРёСЏ СЃ РїСѓР±Р»РёРєР°С†РёРµР№ РїСЂРѕРіСЂРµСЃСЃР° (Р±Р»РѕРєРёСЂСѓСЋС‰РµРіРѕ API РІ Media3 РЅРµС‚).
   - `remove(trackId)`, `isCompleted(trackId)`.
4. **`DownloadTrackWorker`** РїРµСЂРµРїРёСЃР°РЅ: resolve в†’ `enqueue` в†’ `awaitCompletion` в†’ Room `save(mediaUrl, mimeType)`; РѕС‚РјРµРЅР° СѓРґР°Р»СЏРµС‚ РЅРµРґРѕРєР°С‡Р°РЅРЅС‹Рµ РґР°РЅРЅС‹Рµ; Р»СЋР±Р°СЏ РѕС€РёР±РєР° в†’ `Result.failure` (WorkInfo FAILED в†’ UI Failed).
5. **Room v7**:
   - `DownloadedTrackEntity`/`DownloadedTrack`: `mediaUrl` + `mimeType` РІРјРµСЃС‚Рѕ `localFilePath`; `DownloadRepository.save(track, mediaUrl, mimeType)`; `getDownloadedTrack` РїСЂРѕРІРµСЂСЏРµС‚ РЅР°Р»РёС‡РёРµ РґР°РЅРЅС‹С… РІ РєСЌС€Рµ Media3 (РёР»Рё С„Р°Р№Р»Р° РїРѕСЃР»Рµ РјРёРіСЂР°С†РёРё).
   - `MIGRATION_6_7`: РїРµСЂРµСЃРѕР·РґР°РЅРёРµ С‚Р°Р±Р»РёС†С‹, СЃС‚Р°СЂС‹Рµ `localFilePath` в†’ `file://...`, `mimeType = audio/mpeg`; `AppDatabase` v7, СЃС…РµРјР° `7.json`.
6. **РџР»РµРµСЂ Рё UI**:
   - `MusicService`: `ExoPlayer` СЃРѕР·РґР°С‘С‚СЃСЏ СЃ `DefaultMediaSourceFactory(cacheDataSourceFactory)` вЂ” СЃРєР°С‡Р°РЅРЅС‹Рµ HLS-СЃРµРіРјРµРЅС‚С‹ Рё MP3 РёРіСЂР°СЋС‚ РёР· РѕР±С‰РµРіРѕ РєСЌС€Р° Р±РµР· СЃРµС‚Рё.
   - `PlayerController.resolveAndPlay`: СЃРєР°С‡Р°РЅРЅС‹Р№ С‚СЂРµРє РёРіСЂР°РµС‚ РїРѕ `mediaUrl`, `isHls` РѕРїСЂРµРґРµР»СЏРµС‚СЃСЏ РїРѕ `mimeType`.
   - `DownloadManager` (РѕР±С‘СЂС‚РєР°): `observeState` РѕР±СЉРµРґРёРЅСЏРµС‚ Room + `MediaDownloadState` + WorkInfo; Р°РєС‚РёРІРЅР°СЏ Р·Р°РґР°С‡Р° РїСЂРёРѕСЂРёС‚РµС‚РЅРµРµ СЃС‚Р°СЂС‹С… Р·Р°РїРёСЃРµР№ unique work.
   - `PlayerScreen`: `SnackbarHost` + `LaunchedEffect(downloadState)` СЃ С‚РµРєСЃС‚РѕРј В«РќРµ СѓРґР°Р»РѕСЃСЊ СЃРєР°С‡Р°С‚СЊ С‚СЂРµРє (РґРѕСЃС‚СѓРїРµРЅ С‚РѕР»СЊРєРѕ РѕРЅР»Р°Р№РЅ)В».

### РџСЂРѕРІРµСЂРєРё
- `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.
- `.\gradlew testDebugUnitTest` вЂ” 54/54, 0 failures, 0 errors (+1 С‚РµСЃС‚ РјРёРіСЂР°С†РёРё 6в†’7: РєРѕРЅРІРµСЂС‚Р°С†РёСЏ РїСѓС‚Рё РІ `file://`-URL Рё `audio/mpeg`).

### РћРіСЂР°РЅРёС‡РµРЅРёСЏ
- HLS РЅРµ РїСЂРµРІСЂР°С‰Р°РµС‚СЃСЏ РІ РѕРґРёРЅ mp3: РѕС„Р»Р°Р№РЅ-РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёРµ РІРѕР·РјРѕР¶РЅРѕ С‚РѕР»СЊРєРѕ С‡РµСЂРµР· `CacheDataSource` РІ СЌС‚РѕРј РїСЂРёР»РѕР¶РµРЅРёРё (С„Р°Р№Р» РЅРµ СЌРєСЃРїРѕСЂС‚РёСЂСѓРµС‚СЃСЏ).
- РћР±Р»РѕР¶РєР° РѕС„Р»Р°Р№РЅ РЅРµ СЃРѕС…СЂР°РЅСЏРµС‚СЃСЏ (РІ Room С‚РѕР»СЊРєРѕ URL РґР»СЏ UI).
- РћС‚РґРµР»СЊРЅРѕРіРѕ СЌРєСЂР°РЅР° В«РЎРєР°С‡Р°РЅРЅРѕРµВ» Рё СѓРґР°Р»РµРЅРёСЏ РёР· UI РїРѕ-РїСЂРµР¶РЅРµРјСѓ РЅРµС‚ вЂ” СѓРґР°Р»РµРЅРёРµ РІРѕР·РјРѕР¶РЅРѕ С‡РµСЂРµР· РѕС‡РёСЃС‚РєСѓ РґР°РЅРЅС‹С… РїСЂРёР»РѕР¶РµРЅРёСЏ.

### Р¤Р°Р№Р»С‹
```
app/build.gradle.kts
app/src/main/java/com/facet/music/core/download/OfflineMediaModule.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/download/OfflineDownloadManager.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/download/MediaDownloadState.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/download/DownloadTrackWorker.kt
app/src/main/java/com/facet/music/core/download/DownloadManager.kt
app/src/main/java/com/facet/music/core/download/DownloadWorkerEntryPoint.kt
app/src/main/java/com/facet/music/core/player/MusicService.kt
app/src/main/java/com/facet/music/core/player/PlayerController.kt
app/src/main/java/com/facet/music/data/local/AppDatabase.kt
app/src/main/java/com/facet/music/data/local/DatabaseMigrations.kt
app/src/main/java/com/facet/music/data/local/DownloadRepositoryImpl.kt
app/src/main/java/com/facet/music/data/local/entity/DownloadedTrackEntity.kt
app/src/main/java/com/facet/music/data/model/DownloadedTrack.kt
app/src/main/java/com/facet/music/data/repository/DownloadRepository.kt
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
app/src/test/java/com/facet/music/data/local/DatabaseMigrationTest.kt
app/schemas/com.facet.music.data.local.AppDatabase/7.json (РЅРѕРІС‹Р№)
PROGRESS.md
MEMORY.md
```

---

## вњ… РҐРѕС‚С„РёРєСЃ вЂ” РєРЅРѕРїРєР° СЃРєР°С‡РёРІР°РЅРёСЏ Рё РїРµСЂРµР·Р°РїСѓСЃРє РїРѕСЃР»Рµ РѕС‚РјРµРЅС‹ (22.10.2026)

### РџСЂРёС‡РёРЅР°
- UI Р·Р°Р»РёРїР°Р» РЅР° `InProgress` РёР·-Р·Р° СЃС‚Р°СЂРѕР№ Р·Р°РїРёСЃРё Media3/WorkInfo: РєР»РёРєРё СѓС…РѕРґРёР»Рё РІ `cancel()`, Р° РЅРµ РІ РЅРѕРІСѓСЋ Р·Р°РґР°С‡Сѓ. Р’ Р»РѕРіР°С… вЂ” `WM-WorkerWrapper: Work [...] was cancelled`, РїРѕРІС‚РѕСЂРЅРѕРіРѕ Р·Р°РїСѓСЃРєР° `DownloadTrackWorker` РЅРµ Р±С‹Р»Рѕ.
- `ExistingWorkPolicy.KEEP` РЅРµ РіР°СЂР°РЅС‚РёСЂРѕРІР°Р» РЅРѕРІСѓСЋ Р·Р°РґР°С‡Сѓ РїРѕСЃР»Рµ РѕС‚РјРµРЅС‘РЅРЅРѕР№ С†РµРїРѕС‡РєРё.
- `DownloadState.Failed` РЅРµ СЂР°Р·Р»РёС‡Р°Р» РѕС‚РјРµРЅСѓ Рё СЂРµР°Р»СЊРЅСѓСЋ РѕС€РёР±РєСѓ, РїРѕСЌС‚РѕРјСѓ РЅРµ Р±С‹Р»Рѕ РєСЂРµСЃС‚РёРєР° Рё РІРѕР·РІСЂР°С‚Р° Рє РєРЅРѕРїРєРµ СЃРєР°С‡РёРІР°РЅРёСЏ.

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **`DownloadManager`**:
   - WorkInfo вЂ” РіР»Р°РІРЅС‹Р№ РёСЃС‚РѕС‡РЅРёРє СЃРѕСЃС‚РѕСЏРЅРёСЏ. РџРѕСЃР»РµРґРЅСЏСЏ РїРѕРїС‹С‚РєР° РІС‹Р±РёСЂР°РµС‚СЃСЏ РїРѕ С‚РµРіСѓ `download-at:<millis>`, РїРѕС‚РѕРјСѓ С‡С‚Рѕ РїРѕСЂСЏРґРѕРє СЃРїРёСЃРєР° WorkManager РЅРµ РіР°СЂР°РЅС‚РёСЂРѕРІР°РЅ.
   - `CANCELLED`/`FAILED` в†’ `DownloadState.Error(CANCELLED/FAILED)`, `SUCCEEDED` в†’ `Downloaded`. РўРµСЂРјРёРЅР°Р»СЊРЅС‹Р№ WorkInfo РїСЂРёРѕСЂРёС‚РµС‚РЅРµРµ СЃС‚Р°СЂРѕРіРѕ РїСЂРѕРіСЂРµСЃСЃР° Media3; РѕСЃРёСЂРѕС‚РµРІС€Р°СЏ Р·Р°РїРёСЃСЊ Media3 Р±РµР· Р°РєС‚РёРІРЅРѕР№ Р·Р°РґР°С‡Рё РїСЂРѕРіСЂРµСЃСЃРѕРј РЅРµ СЃС‡РёС‚Р°РµС‚СЃСЏ.
   - `ExistingWorkPolicy.REPLACE`, Р·Р°С‰РёС‚Р° РѕС‚ РґРІРѕР№РЅРѕРіРѕ С‚Р°РїР° 700 РјСЃ, `KEY_FORCE_RESTART=true`.
   - `cancel()` С‚РµРїРµСЂСЊ СЃСЂР°Р·Сѓ СѓРґР°Р»СЏРµС‚ Р·Р°РїРёСЃСЊ Media3 (Р° РЅРµ С‚РѕР»СЊРєРѕ РѕС‚РјРµРЅСЏРµС‚ WorkManager).
2. **`OfflineDownloadManager`**:
   - `enqueue(forceRestart)` СѓРґР°Р»СЏРµС‚ СЃС‚Р°СЂСѓСЋ Р·Р°РїРёСЃСЊ Media3 Рё Р¶РґС‘С‚ РµС‘ РёСЃС‡РµР·РЅРѕРІРµРЅРёСЏ; `COMPLETED` РЅРµ РїРµСЂРµРєР°С‡РёРІР°РµС‚СЃСЏ (РІРѕСЂРєРµСЂ РІРѕСЃСЃС‚Р°РЅРѕРІРёС‚ Room).
   - `STATE_REMOVING` РІ `awaitCompletion` Р±РѕР»СЊС€Рµ РЅРµ РґР°С‘С‚ РјРіРЅРѕРІРµРЅРЅСѓСЋ РѕС€РёР±РєСѓ вЂ” РѕР¶РёРґР°РµС‚СЃСЏ РЅРѕРІР°СЏ Р·Р°РїРёСЃСЊ.
3. **`DownloadTrackWorker`**:
   - `Log.d` РїРµСЂРІРѕР№ СЃС‚СЂРѕРєРѕР№ `doWork()` вЂ” РІ logcat РІРёРґРЅРѕ СЃР°Рј С„Р°РєС‚ Р·Р°РїСѓСЃРєР° (С‚РµРі `DownloadWorker`).
   - РџРµСЂРµРґР°С‘С‚ `forceRestart`; РїСЂРё `CancellationException` РЅРµ СѓРґР°Р»СЏРµС‚ Media3, РёРЅР°С‡Рµ REPLACE-РїРѕРїС‹С‚РєР° СѓР±РёРІР°Р»Р° Р±С‹ РЅРѕРІСѓСЋ Р·Р°РіСЂСѓР·РєСѓ.
4. **`PlayerViewModel`**: `Error` РґРµСЂР¶РёС‚СЃСЏ 1 СЃРµРєСѓРЅРґСѓ Рё СЃРјРµРЅСЏРµС‚СЃСЏ РЅР° `NotDownloaded`; РєР»РёРє РїРѕ РєСЂРµСЃС‚РёРєСѓ Р·Р°РїСѓСЃРєР°РµС‚ РЅРѕРІСѓСЋ РїРѕРїС‹С‚РєСѓ.
5. **`PlayerScreen`**: РєСЂРµСЃС‚РёРє `Icons.Rounded.Close` СЃ `AnimatedContent`-РјРѕСЂС„РёРЅРіРѕРј РѕР±СЂР°С‚РЅРѕ РІ СЃС‚СЂРµР»РєСѓ; Snackbar В«РќРµ СѓРґР°Р»РѕСЃСЊ СЃРєР°С‡Р°С‚СЊ С‚СЂРµРє (РґРѕСЃС‚СѓРїРµРЅ С‚РѕР»СЊРєРѕ РѕРЅР»Р°Р№РЅ)В» РїРѕРєР°Р·С‹РІР°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РґР»СЏ СЂРµР°Р»СЊРЅРѕР№ РѕС€РёР±РєРё (`FAILED`), РЅРµ РґР»СЏ РѕС‚РјРµРЅС‹.
6. **РўРµСЃС‚** `DownloadStateResolutionTest` (10 РєРµР№СЃРѕРІ РїСЂРёРѕСЂРёС‚РµС‚Р° СЃРѕСЃС‚РѕСЏРЅРёР№).

### РџСЂРѕРІРµСЂРєРё
- `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.
- `.\gradlew testDebugUnitTest` вЂ” 64/64, 0 failures, 0 errors.
- APK: `C:\Users\souler\Downloads\Facet-debug.apk`.
- Р”РёР°РіРЅРѕСЃС‚РёРєР° РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ: `adb logcat -s DownloadWorker DownloadFlow OfflineDownload`.

### РЎР»РµРґСѓСЋС‰РёР№ С€Р°Рі
- ~~РСЃРєР°С‚СЊ РїСЂРѕРіСЂРµСЃСЃРёРІРЅС‹Р№ MP3-РїРѕС‚РѕРє SoundCloud РґР»СЏ СЃРєР°С‡РёРІР°РЅРёСЏ~~ вЂ” РІС‹РїРѕР»РЅРµРЅРѕ РІ СЂР°Р·РґРµР»Рµ В«РЎРєР°С‡РёРІР°РЅРёРµ С‚РѕР»СЊРєРѕ progressive MP3 С‡РµСЂРµР· OkHttpВ» РЅРёР¶Рµ.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/core/download/DownloadState.kt
app/src/main/java/com/facet/music/core/download/DownloadManager.kt
app/src/main/java/com/facet/music/core/download/OfflineDownloadManager.kt
app/src/main/java/com/facet/music/core/download/DownloadTrackWorker.kt
app/src/main/java/com/facet/music/feature/player/PlayerViewModel.kt
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
app/src/test/java/com/facet/music/core/download/DownloadStateResolutionTest.kt (РЅРѕРІС‹Р№)
PROGRESS.md
MEMORY.md
```

---

## вњ… РЎРєР°С‡РёРІР°РЅРёРµ С‚РѕР»СЊРєРѕ progressive MP3 С‡РµСЂРµР· OkHttp (22.10.2026)

### РџСЂРёС‡РёРЅР°
- HLS вЂ” СЌС‚Рѕ РЅР°Р±РѕСЂ СЃРµРіРјРµРЅС‚РѕРІ, Р° РЅРµ РѕРґРёРЅ С„Р°Р№Р». Media3 DownloadManager СЃРєР»Р°РґС‹РІР°Р» РёС… РІ `SimpleCache`, РёР·-Р·Р° С‡РµРіРѕ РѕС„Р»Р°Р№РЅ-СЃРєР°С‡РёРІР°РЅРёРµ Р·Р°РІРёСЃР°Р»Рѕ, Р° РіРѕС‚РѕРІРѕРіРѕ mp3 РЅРµ РїРѕСЏРІР»СЏР»РѕСЃСЊ.
- Р”Р»СЏ РѕС„Р»Р°Р№РЅР° РЅСѓР¶РµРЅ СЂРѕРІРЅРѕ РѕРґРёРЅ progressive MP3-С„Р°Р№Р»; РµСЃР»Рё РµРіРѕ РЅРµС‚ вЂ” С‡РµСЃС‚РЅР°СЏ РѕС€РёР±РєР° РІРјРµСЃС‚Рѕ Р±РµСЃРєРѕРЅРµС‡РЅРѕР№ Р·Р°РіСЂСѓР·РєРё.

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **`SoundCloudRepository.resolveStreamUrl(..., requireProgressive = false)`**:
   - progressive MP3 СЃ РјР°РєСЃРёРјР°Р»СЊРЅС‹Рј Р±РёС‚СЂРµР№С‚РѕРј РІС‹Р±РёСЂР°РµС‚СЃСЏ РІСЃРµРіРґР° РїРµСЂРІС‹Рј;
   - РґР»СЏ СЃРєР°С‡РёРІР°РЅРёСЏ РїРµСЂРµРґР°С‘С‚СЃСЏ `requireProgressive = true`: РµСЃР»Рё progressive РЅРµС‚, РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ РѕС€РёР±РєР° В«РўСЂРµРє РґРѕСЃС‚СѓРїРµРЅ С‚РѕР»СЊРєРѕ РґР»СЏ РѕРЅР»Р°Р№РЅ-РїСЂРѕСЃР»СѓС€РёРІР°РЅРёСЏВ»;
   - РґР»СЏ РѕРЅР»Р°Р№РЅ-РїР»РµРµСЂР° HLS-С„РѕР»Р±СЌРє СЃРѕС…СЂР°РЅС‘РЅ, РёРЅР°С‡Рµ HLS-only С‚СЂРµРєРё РїРµСЂРµСЃС‚Р°Р»Рё Р±С‹ РёРіСЂР°С‚СЊ.
2. **`DownloadTrackWorker`**:
   - РІСЃСЏ Р»РѕРіРёРєР° Media3 DownloadManager СѓРґР°Р»РµРЅР°;
   - OkHttp РєР°С‡Р°РµС‚ progressive MP3 РІ `filesDir/downloads/<safe>_<hash>.mp3`;
   - Р·Р°РіСЂСѓР·РєР° РёРґС‘С‚ РІ `.part`, РїРѕСЃР»Рµ СѓСЃРїРµС…Р° С„Р°Р№Р» РїРµСЂРµРёРјРµРЅРѕРІС‹РІР°РµС‚СЃСЏ; РїСЂРѕРіСЂРµСЃСЃ РїСѓР±Р»РёРєСѓРµС‚СЃСЏ С‡РµСЂРµР· `setProgress`;
   - РїСЂРё РѕС‚РјРµРЅРµ `.part` СѓРґР°Р»СЏРµС‚СЃСЏ, РїСЂРё HLS-only вЂ” `Result.failure`, UI РїРѕРєР°Р·С‹РІР°РµС‚ РєСЂРµСЃС‚РёРє Рё Snackbar.
3. **РЈРґР°Р»РµРЅС‹** `OfflineDownloadManager`, `OfflineMediaModule`, `MediaDownloadState`; `MusicService` Р±РѕР»СЊС€Рµ РЅРµ РёСЃРїРѕР»СЊР·СѓРµС‚ `SimpleCache` Рё `CacheDataSource`.
4. **`DownloadManager`** СѓРїСЂРѕС‰С‘РЅ: Room + WorkInfo, РїСЂРѕРіСЂРµСЃСЃ РёР· РІРѕСЂРєРµСЂР°; `ExistingWorkPolicy.REPLACE` Рё Р·Р°С‰РёС‚Р° РѕС‚ РґРІРѕР№РЅРѕРіРѕ С‚Р°РїР° 700 РјСЃ СЃРѕС…СЂР°РЅРµРЅС‹.
5. **`DownloadRepositoryImpl`** РїСЂРѕРІРµСЂСЏРµС‚ С‚РѕР»СЊРєРѕ Р»РѕРєР°Р»СЊРЅС‹Рµ `file://`-С„Р°Р№Р»С‹; СЃС‚Р°СЂС‹Рµ HLS-Р·Р°РїРёСЃРё РёР· Room РёРЅРІР°Р»РёРґРёСЂСѓСЋС‚СЃСЏ РїСЂРё РѕР±СЂР°С‰РµРЅРёРё.
6. **`DownloadStateResolutionTest`** РїРµСЂРµРїРёСЃР°РЅ РїРѕРґ РЅРѕРІСѓСЋ СЃРёРіРЅР°С‚СѓСЂСѓ (10 РєРµР№СЃРѕРІ).

### РџСЂРѕРІРµСЂРєРё
- `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.
- `.\gradlew testDebugUnitTest` вЂ” 64/64, 0 failures, 0 errors.
- APK: `C:\Users\souler\Downloads\Facet-debug.apk`.
- Р”РёР°РіРЅРѕСЃС‚РёРєР° РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ: `adb logcat -s DownloadWorker DownloadFlow`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/core/download/DownloadManager.kt
app/src/main/java/com/facet/music/core/download/DownloadTrackWorker.kt
app/src/main/java/com/facet/music/core/download/DownloadWorkerEntryPoint.kt
app/src/main/java/com/facet/music/core/download/OfflineDownloadManager.kt (СѓРґР°Р»С‘РЅ)
app/src/main/java/com/facet/music/core/download/OfflineMediaModule.kt (СѓРґР°Р»С‘РЅ)
app/src/main/java/com/facet/music/core/download/MediaDownloadState.kt (СѓРґР°Р»С‘РЅ)
app/src/main/java/com/facet/music/core/player/MusicService.kt
app/src/main/java/com/facet/music/data/local/DownloadRepositoryImpl.kt
app/src/main/java/com/facet/music/data/local/entity/DownloadedTrackEntity.kt
app/src/main/java/com/facet/music/data/model/DownloadedTrack.kt
app/src/main/java/com/facet/music/data/repository/DownloadRepository.kt
app/src/main/java/com/facet/music/data/repository/SoundCloudRepository.kt
app/src/main/java/com/facet/music/data/source/soundcloud/SoundCloudRepositoryImpl.kt
app/src/test/java/com/facet/music/core/download/DownloadStateResolutionTest.kt
PROGRESS.md
MEMORY.md
```

---

## рџ”њ РЎР»РµРґСѓСЋС‰РёР№ С€Р°Рі вЂ” СЌРєСЂР°РЅ В«РЎРєР°С‡Р°РЅРЅРѕРµВ» Рё РѕС‡РёСЃС‚РєР° СЃС‚Р°СЂС‹С… РґР°РЅРЅС‹С…

### Р§С‚Рѕ РЅСѓР¶РЅРѕ СЃРґРµР»Р°С‚СЊ
1. **Р­РєСЂР°РЅ/СЃРµРєС†РёСЏ В«РЎРєР°С‡Р°РЅРЅРѕРµВ»**: СЃРїРёСЃРѕРє Р»РѕРєР°Р»СЊРЅС‹С… mp3, СѓРґР°Р»РµРЅРёРµ С„Р°Р№Р»Р° Рё Р·Р°РїРёСЃРё Room, РёРЅРґРёРєР°С†РёСЏ Р·Р°РЅСЏС‚РѕРіРѕ РјРµСЃС‚Р°.
2. **РћС‡РёСЃС‚РєР° legacy**: СѓРґР°Р»РёС‚СЊ РѕСЃС‚Р°РІС€РёР№СЃСЏ РєР°С‚Р°Р»РѕРі `filesDir/media_cache` Рё СЃС‚Р°СЂС‹Рµ HLS-Р·Р°РїРёСЃРё РёР· Room, С‡С‚РѕР±С‹ РѕРЅРё РЅРµ Р·Р°РЅРёРјР°Р»Рё РјРµСЃС‚Рѕ Рё РЅРµ РїРѕРєР°Р·С‹РІР°Р»РёСЃСЊ РєР°Рє СЃРєР°С‡Р°РЅРЅС‹Рµ.
3. **РџСЂРѕРІРµСЂРєР° РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ**: СЃРєР°С‡Р°С‚СЊ progressive-С‚СЂРµРє, РІРєР»СЋС‡РёС‚СЊ Р°РІРёР°СЂРµР¶РёРј Рё РІРѕСЃРїСЂРѕРёР·РІРµСЃС‚Рё; РґР»СЏ HLS-only С‚СЂРµРєР° РїСЂРѕРІРµСЂРёС‚СЊ РєСЂРµСЃС‚РёРє Рё Snackbar В«РќРµ СѓРґР°Р»РѕСЃСЊ СЃРєР°С‡Р°С‚СЊ С‚СЂРµРє (РґРѕСЃС‚СѓРїРµРЅ С‚РѕР»СЊРєРѕ РѕРЅР»Р°Р№РЅ)В».
4. **РќР°СЃС‚СЂРѕРµРЅРёСЏ РІРѕР»РЅС‹**: С‡РёРїС‹ В«Р‘РѕРґСЂР°СЏ / РЎРїРѕРєРѕР№РЅР°СЏ / РџРѕ Р»СЋР±РёРјС‹Рј / РћС‚РєСЂС‹С‚РёСЏВ», РІР»РёСЏСЋС‰РёРµ РЅР° Р¶Р°РЅСЂС‹, С‚РµРјРї Рё РґРѕР»СЋ РЅРѕРІС‹С… С‚СЂРµРєРѕРІ.
5. **Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏ**: РїРѕРєР°Р·С‹РІР°С‚СЊ СЃРѕС…СЂР°РЅС‘РЅРЅС‹Рµ РєРѕРѕСЂРґРёРЅР°С‚С‹ Рё СЃРµР·РѕРЅ РІ РєР°СЂС‚РѕС‡РєР°С…, РґРѕР±Р°РІРёС‚СЊ РґРµР№СЃС‚РІРёРµ В«РћС‚РєСЂС‹С‚СЊ РЅР° РєР°СЂС‚РµВ».
6. РЎР±РѕСЂРєР°, С‚РµСЃС‚С‹, PROGRESS.md, MEMORY.md, РєРѕРјРјРёС‚ Рё РїСѓС€.

---

## рџ“‹ РџР»Р°РЅ вЂ” YouTube Music Рё РіР»РѕР±Р°Р»СЊРЅС‹Рµ СЃС‚Р°С‚СѓСЃС‹ СЃРµСЂРІРёСЃРѕРІ (22.10.2026)

### РћР±С‰РёР№ РїР»Р°РЅ
1. **Р‘Р°Р·Р° Рё РјРѕРґРµР»СЊ**: РґРѕР±Р°РІРёС‚СЊ `Source.YOUTUBE`, СѓР±РµРґРёС‚СЊСЃСЏ, С‡С‚Рѕ `Track` Рё Room-РєРѕРЅРІРµСЂС‚РµСЂС‹ СЂР°Р±РѕС‚Р°СЋС‚ СЃ РЅРѕРІС‹Рј РёСЃС‚РѕС‡РЅРёРєРѕРј.
2. **Р РµРїРѕР·РёС‚РѕСЂРёР№**: `YouTubeRepository` С‡РµСЂРµР· NewPipe (`YoutubeService`): РїРѕРёСЃРє + progressive M4A/WEBM_OPUS Р±РµР· HLS.
3. **UI РїРѕРёСЃРєР°**: РїРµСЂРµРєР»СЋС‡Р°С‚РµР»СЊ SoundCloud / YouTube, РµРґРёРЅС‹Р№ СЃРїРёСЃРѕРє СЂРµР·СѓР»СЊС‚Р°С‚РѕРІ, РѕС€РёР±РєРё Рё РїСѓСЃС‚С‹Рµ СЃРѕСЃС‚РѕСЏРЅРёСЏ РїРѕРґ РІС‹Р±СЂР°РЅРЅС‹Р№ РёСЃС‚РѕС‡РЅРёРє.
4. **РџР»РµРµСЂ Рё Р·Р°РіСЂСѓР·С‡РёРє**: РЅР°СѓС‡РёС‚СЊ `PlayerController` Рё `DownloadTrackWorker` РІС‹Р±РёСЂР°С‚СЊ СЂРµРїРѕР·РёС‚РѕСЂРёР№ РїРѕ `Track.source` Рё РєРѕСЂСЂРµРєС‚РЅРѕ РѕРїСЂРµРґРµР»СЏС‚СЊ MIME (MP3/M4A/WEBM). **Р’С‹РїРѕР»РЅРµРЅРѕ РІ С€Р°РіРµ 4 (СЃРј. РІС‹С€Рµ).**
5. **РРјРїРѕСЂС‚ РїР»РµР№Р»РёСЃС‚РѕРІ (РЅР° Р±СѓРґСѓС‰РµРµ)**: С‡РµСЂРµР· NewPipe РІС‹С‚Р°СЃРєРёРІР°С‚СЊ РїР»РµР№Р»РёСЃС‚С‹ YouTube Рё РґРѕР±Р°РІР»СЏС‚СЊ РІ РѕС‡РµСЂРµРґСЊ/РјРµРґРёР°С‚РµРєСѓ.
6. **РЎС‚Р°С‚СѓСЃС‹ СЃРµСЂРІРёСЃРѕРІ**: `ServiceStatusManager` + РјРµРЅСЋ СЃС‚Р°С‚СѓСЃРѕРІ РІ РїРѕРёСЃРєРµ, СЂСѓС‡РЅРѕРµ РѕР±РЅРѕРІР»РµРЅРёРµ, РїСЂРµРґСѓРїСЂРµР¶РґРµРЅРёРµ Рѕ РЅРµРґРѕСЃС‚СѓРїРЅРѕРј РёСЃС‚РѕС‡РЅРёРєРµ.

### РўРµРєСѓС‰Р°СЏ РёС‚РµСЂР°С†РёСЏ вЂ” С‚РѕР»СЊРєРѕ С€Р°РіРё 1вЂ“3
- [x] `Source.YOUTUBE` РІ РјРѕРґРµР»Рё.
- [x] `YouTubeRepository` + `YouTubeRepositoryImpl` + Hilt-Р±РёРЅРґРёРЅРі.
- [x] `ServiceStatusManager`, `ServiceType`, `ServiceHealth`.
- [x] `SearchViewModel`: РІС‹Р±СЂР°РЅРЅС‹Р№ РёСЃС‚РѕС‡РЅРёРє + РїРѕРґРїРёСЃРєР° РЅР° СЃС‚Р°С‚СѓСЃС‹.
- [x] `SearchScreen`: РїРµСЂРµРєР»СЋС‡Р°С‚РµР»СЊ РёСЃС‚РѕС‡РЅРёРєРѕРІ, РјРµРЅСЋ СЃС‚Р°С‚СѓСЃРѕРІ, РїСЂРµРґСѓРїСЂРµР¶РґРµРЅРёРµ Рѕ РЅРµРґРѕСЃС‚СѓРїРЅРѕРј СЃРµСЂРІРёСЃРµ.
- [x] РЎР±РѕСЂРєР°, С‚РµСЃС‚С‹, РєРѕРјРјРёС‚, РїСѓС€.

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ (22.10.2026)
- `Source.YOUTUBE` РґРѕР±Р°РІР»РµРЅ РІ `Track.kt`; Room С…СЂР°РЅРёС‚ РёРјСЏ enum, РјРёРіСЂР°С†РёСЏ РЅРµ РЅСѓР¶РЅР°.
- `YouTubeRepository` + `YouTubeRepositoryImpl`:
  - РїРѕРёСЃРє С‡РµСЂРµР· `ServiceList.YouTube` СЃ С„РёР»СЊС‚СЂРѕРј `videos`;
  - РјР°РїРїРёРЅРі `StreamInfoItem` в†’ `Track` (`id`/`transcodingUrl` = URL РІРёРґРµРѕ, Р°СЂС‚РёСЃС‚ Р±РµР· СЃСѓС„С„РёРєСЃР° ` - Topic`);
  - `resolveStreamUrl()` РІС‹Р±РёСЂР°РµС‚ С‚РѕР»СЊРєРѕ `DeliveryMethod.PROGRESSIVE_HTTP` + `M4A`/`WEBMA_OPUS`, СЃРЅР°С‡Р°Р»Р° M4A РїРѕ РјР°РєСЃРёРјР°Р»СЊРЅРѕРјСѓ Р±РёС‚СЂРµР№С‚Сѓ, Р·Р°С‚РµРј Opus; `isHls = false`;
  - Hilt-Р±РёРЅРґРёРЅРі РІ `RepositoryModule`.
- `ServiceStatusManager` (Singleton):
  - `ServiceType { SOUNDCLOUD, YOUTUBE }`, `ServiceHealth(isAvailable, isChecking)`;
  - `StateFlow<Map<ServiceType, ServiceHealth>>`, РїР°СЂР°Р»Р»РµР»СЊРЅР°СЏ РїСЂРѕРІРµСЂРєР° РїРѕРёСЃРєРѕРј `"test"` СЃ Р»РёРјРёС‚РѕРј 1;
  - `checkAllServices()` РїСЂРё СЃС‚Р°СЂС‚Рµ (`FacetApp` РїРѕСЃР»Рµ `NewPipe.init`) Рё РїРѕСЃР»Рµ РѕС€РёР±РѕРє РїРѕРёСЃРєР°;
  - `refreshStatus()` РёР· РјРµРЅСЋ, Р·Р°С‰РёС‚Р° РѕС‚ РїР°СЂР°Р»Р»РµР»СЊРЅС‹С… РїСЂРѕРІРµСЂРѕРє, `markAvailable()` РїРѕСЃР»Рµ СѓСЃРїРµС€РЅРѕРіРѕ РїРѕРёСЃРєР°;
  - 5 JVM-С‚РµСЃС‚РѕРІ РЅР° СѓСЃРїРµС…/failure/exception/РЅР°С‡Р°Р»СЊРЅРѕРµ СЃРѕСЃС‚РѕСЏРЅРёРµ.
- `SearchViewModel`:
  - РІС‹Р±СЂР°РЅРЅС‹Р№ `ServiceType`, РїРѕРёСЃРє С‡РµСЂРµР· СЃРѕРѕС‚РІРµС‚СЃС‚РІСѓСЋС‰РёР№ СЂРµРїРѕР·РёС‚РѕСЂРёР№;
  - РѕР±С‰РёР№ РїРѕС‚РѕРє `(query, service)` СЃ debounce 400 РјСЃ, РѕС‚РјРµРЅР° СѓСЃС‚Р°СЂРµРІС€РёС… Р·Р°РїСЂРѕСЃРѕРІ;
  - `retry()` РґРµР№СЃС‚РІРёС‚РµР»СЊРЅРѕ РїРµСЂРµР·Р°РїСѓСЃРєР°РµС‚ РїРѕРёСЃРє (СЂР°РЅСЊС€Рµ РїРѕРІС‚РѕСЂРЅС‹Р№ `onQueryChange` СЃ С‚РµРј Р¶Рµ С‚РµРєСЃС‚РѕРј РёРіРЅРѕСЂРёСЂРѕРІР°Р»СЃСЏ);
  - РїРѕРґРїРёСЃРєР° РЅР° РіР»РѕР±Р°Р»СЊРЅС‹Рµ СЃС‚Р°С‚СѓСЃС‹, `refreshStatuses()`.
- `SearchScreen`:
  - РїРµСЂРµРєР»СЋС‡Р°С‚РµР»СЊ SoundCloud / YouTube СЃ РїСЂСѓР¶РёРЅРЅС‹РјРё Р°РЅРёРјР°С†РёСЏРјРё Рё РєСЂР°СЃРЅРѕР№ РѕР±РІРѕРґРєРѕР№ РЅРµРґРѕСЃС‚СѓРїРЅРѕРіРѕ СЃРµСЂРІРёСЃР°;
  - РёРєРѕРЅРєР° `Radar` СЃ С†РІРµС‚РЅС‹Рј РёРЅРґРёРєР°С‚РѕСЂРѕРј Рё MD3 `DropdownMenu` (СЃРїРёСЃРѕРє СЃРµСЂРІРёСЃРѕРІ, С‚РѕС‡РєРё В«РћРЅР»Р°Р№РЅВ»/В«РћС€РёР±РєР°В», СЃРїРёРЅРЅРµСЂ В«РџСЂРѕРІРµСЂРєР°В», РєРЅРѕРїРєР° В«РћР±РЅРѕРІРёС‚СЊ СЃС‚Р°С‚СѓСЃС‹В»);
  - РїСЂРµРґСѓРїСЂРµР¶РґР°СЋС‰РёР№ Р±Р°РЅРЅРµСЂ РґР»СЏ РІС‹Р±СЂР°РЅРЅРѕРіРѕ РЅРµРґРѕСЃС‚СѓРїРЅРѕРіРѕ СЃРµСЂРІРёСЃР°;
  - РґРёРЅР°РјРёС‡РµСЃРєРёРµ РїРѕРґРїРёСЃРё РёСЃС‚РѕС‡РЅРёРєР° РІ idle/empty/results, РєРЅРѕРїРєР° В«РџРѕРІС‚РѕСЂРёС‚СЊВ» С‡РµСЂРµР· `retry()`.

### РџСЂРѕРІРµСЂРєР°
- `.\gradlew assembleDebug testDebugUnitTest` вЂ” BUILD SUCCESSFUL.
- 69 JVM-С‚РµСЃС‚РѕРІ, 0 failures, 0 errors.
- Р–РёРІР°СЏ РїСЂРѕРІРµСЂРєР° С‡РµСЂРµР· РІСЂРµРјРµРЅРЅС‹Р№ С‚РµСЃС‚: РїРѕРёСЃРє YouTube РІРµСЂРЅСѓР» СЂРµР·СѓР»СЊС‚Р°С‚С‹, `resolveStreamUrl()` РѕС‚РґР°Р» progressive M4A (`itag=140`, `isHls=false`). Р’СЂРµРјРµРЅРЅС‹Р№ С‚РµСЃС‚ СѓРґР°Р»С‘РЅ.
- РћРіСЂР°РЅРёС‡РµРЅРёРµ С‚РµРєСѓС‰РµР№ РёС‚РµСЂР°С†РёРё: `PlayerController` Рё `DownloadTrackWorker` РІСЃС‘ РµС‰С‘ РёСЃРїРѕР»СЊР·СѓСЋС‚ `SoundCloudRepository`, РїРѕСЌС‚РѕРјСѓ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёРµ Рё СЃРєР°С‡РёРІР°РЅРёРµ YouTube-СЂРµР·СѓР»СЊС‚Р°С‚РѕРІ РїРѕРєР° РЅРµ СЂР°Р±РѕС‚Р°СЋС‚ вЂ” СЌС‚Рѕ С€Р°Рі 4.

### РЇРІРЅРѕ РІРЅРµ С‚РµРєСѓС‰РµР№ РёС‚РµСЂР°С†РёРё
- РЎРєР°С‡РёРІР°РЅРёРµ YouTube-С‚СЂРµРєРѕРІ Рё РїСЂР°РІРєРё `DownloadTrackWorker`.
- РџСЂР°РІРєРё `PlayerController` Рё MIME РІ РїР»РµРµСЂРµ.
- РРјРїРѕСЂС‚ YouTube-РїР»РµР№Р»РёСЃС‚РѕРІ.
- Р­РєСЂР°РЅ В«РЎРєР°С‡Р°РЅРЅРѕРµВ» Рё РѕС‡РёСЃС‚РєР° legacy-РєСЌС€Р°.

---

## вњ… РЁР°Рі 4 вЂ” РїР»РµРµСЂ Рё СЃРєР°С‡РёРІР°РЅРёРµ YouTube + СѓРјРЅС‹Р№ СЂРµРґРёСЂРµРєС‚ (22.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **MIME РІ СЂРµР·СѓР»СЊС‚Р°С‚Рµ resolve**:
   - `AudioStreamResult` РґРѕРїРѕР»РЅРµРЅ `mimeType`; СЃС‚Р°СЂС‹Рµ РІС‹Р·РѕРІС‹ Р±РµР· С„РѕСЂРјР°С‚Р° РїРѕР»СѓС‡Р°СЋС‚ Р±РµР·РѕРїР°СЃРЅС‹Р№ С„РѕР»Р±СЌРє.
   - SoundCloud РѕС‚РґР°С‘С‚ `AUDIO_MPEG` РґР»СЏ progressive Рё `APPLICATION_M3U8` РґР»СЏ HLS.
   - YouTube РѕС‚РґР°С‘С‚ `AUDIO_MP4` РґР»СЏ M4A Рё `AUDIO_WEBM` РґР»СЏ Opus-С„РѕР»Р±СЌРєР°.
2. **`PlayerController`**:
   - `resolveAndPlay()` РІС‹Р±РёСЂР°РµС‚ СЂРµРїРѕР·РёС‚РѕСЂРёР№ РїРѕ `Track.source`: YouTube в†’ `YouTubeRepository`, SoundCloud в†’ `SoundCloudRepository`.
   - ExoPlayer РїРѕР»СѓС‡Р°РµС‚ `audio/mp4` РґР»СЏ YouTube M4A, `audio/webm` РґР»СЏ WEBM Рё `audio/mpeg` РґР»СЏ SoundCloud.
   - РћС„Р»Р°Р№РЅ-РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёРµ Р±РµСЂС‘С‚ MIME РёР· Room, РїРѕСЌС‚РѕРјСѓ СЃРєР°С‡Р°РЅРЅС‹Р№ M4A Р±РѕР»СЊС€Рµ РЅРµ С‚РµСЂСЏРµС‚ С„РѕСЂРјР°С‚.
3. **`DownloadTrackWorker`**:
   - РџРѕС‚РѕРє СЂРµР·РѕР»РІРёС‚СЃСЏ С‡РµСЂРµР· СЂРµРїРѕР·РёС‚РѕСЂРёР№ РІС‹Р±СЂР°РЅРЅРѕРіРѕ РёСЃС‚РѕС‡РЅРёРєР°; РґР»СЏ SoundCloud СЃРѕС…СЂР°РЅС‘РЅ `requireProgressive = true`.
   - РРјСЏ С„Р°Р№Р»Р° Р·Р°РІРёСЃРёС‚ РѕС‚ MIME: `.mp3`, `.m4a` РёР»Рё `.webm`; РІ Room РїРёС€РµС‚СЃСЏ С‚РѕС‚ Р¶Рµ MIME.
   - HLS Рё РѕС€РёР±РєРё resolve/download/save РїРѕ-РїСЂРµР¶РЅРµРјСѓ Р·Р°РІРµСЂС€Р°СЋС‚ Р·Р°РґР°С‡Сѓ С‡РµСЂРµР· `Result.failure`, WorkInfo СЃС‚Р°РЅРѕРІРёС‚СЃСЏ `FAILED`, UI РїРѕРєР°Р·С‹РІР°РµС‚ РєСЂРµСЃС‚РёРє.
4. **РЈРјРЅС‹Р№ СЂРµРґРёСЂРµРєС‚**:
   - РџСЂРё `WorkInfo.FAILED` РґР»СЏ SoundCloud-С‚СЂРµРєР° РїРѕРєР°Р·С‹РІР°РµС‚СЃСЏ Snackbar В«РќРµ СѓРґР°Р»РѕСЃСЊ СЃРєР°С‡Р°С‚СЊ (С‚РѕР»СЊРєРѕ РѕРЅР»Р°Р№РЅ). РќР°Р№С‚Рё РЅР° YouTube?В» СЃ РґРµР№СЃС‚РІРёРµРј В«РќР°Р№С‚РёВ».
   - Р”Р»СЏ YouTube-РѕС€РёР±РєРё РѕСЃС‚Р°С‘С‚СЃСЏ РЅРµР№С‚СЂР°Р»СЊРЅРѕРµ СЃРѕРѕР±С‰РµРЅРёРµ Р±РµР· РґРµР№СЃС‚РІРёСЏ.
   - `NavigationEvents` (Singleton, `SharedFlow` СЃ replay = 1) РїРµСЂРµРґР°С‘С‚ Р·Р°РїСЂРѕСЃ `"РђСЂС‚РёСЃС‚ - РќР°Р·РІР°РЅРёРµ"` Рё `YOUTUBE` РІ `SearchViewModel`.
   - `SearchViewModel` РѕР±СЂР°Р±Р°С‚С‹РІР°РµС‚ РЅР°РјРµСЂРµРЅРёРµ СЃСЂР°Р·Сѓ, Р±РµР· debounce, РѕС‡РёС‰Р°РµС‚ СЃС‚Р°СЂС‹Рµ СЂРµР·СѓР»СЊС‚Р°С‚С‹ Рё РїРµСЂРµРєР»СЋС‡Р°РµС‚ РёСЃС‚РѕС‡РЅРёРє; replay-РєСЌС€ СЃР±СЂР°СЃС‹РІР°РµС‚СЃСЏ РїРѕСЃР»Рµ РѕР±СЂР°Р±РѕС‚РєРё.
   - `AppNavGraph` РѕС‚РєСЂС‹РІР°РµС‚ РІРєР»Р°РґРєСѓ РїРѕРёСЃРєР° СЃ СЃРѕС…СЂР°РЅРµРЅРёРµРј СЃРѕСЃС‚РѕСЏРЅРёСЏ РЅРёР¶РЅРµР№ РЅР°РІРёРіР°С†РёРё.
5. **РўРµСЃС‚С‹**:
   - `PlayerControllerQueueTest`: YouTube-С‚СЂРµРє СѓС…РѕРґРёС‚ РІ `YouTubeRepository`, MIME `AUDIO_MP4`; РѕС„Р»Р°Р№РЅ-M4A СЃРѕС…СЂР°РЅСЏРµС‚ MIME РёР· Room.
   - `DownloadWorkerFormatTest`: СЂР°СЃС€РёСЂРµРЅРёСЏ `.m4a`/`.mp3`/`.webm`, С„РѕР»Р±СЌРєРё MIME Рё Р±РµР·РѕРїР°СЃРЅРѕРµ РёРјСЏ С„Р°Р№Р»Р°.
   - `SearchViewModelExternalRequestTest`: РІРЅРµС€РЅРёР№ Р·Р°РїСЂРѕСЃ СЃСЂР°Р·Сѓ РІС‹Р±РёСЂР°РµС‚ YouTube, РЅРµ Р¶РґС‘С‚ debounce Рё РЅРµ РїРѕРІС‚РѕСЂСЏРµС‚СЃСЏ РїСЂРё РїРѕРІС‚РѕСЂРЅРѕРј РѕС‚РєСЂС‹С‚РёРё СЌРєСЂР°РЅР°.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/core/navigation/NavigationEvents.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/player/PlayerController.kt
app/src/main/java/com/facet/music/core/download/DownloadTrackWorker.kt
app/src/main/java/com/facet/music/core/download/DownloadWorkerEntryPoint.kt
app/src/main/java/com/facet/music/data/model/AudioStreamResult.kt
app/src/main/java/com/facet/music/data/model/DownloadedTrack.kt
app/src/main/java/com/facet/music/data/local/DownloadRepositoryImpl.kt
app/src/main/java/com/facet/music/data/local/entity/DownloadedTrackEntity.kt
app/src/main/java/com/facet/music/data/source/soundcloud/SoundCloudRepositoryImpl.kt
app/src/main/java/com/facet/music/data/source/youtube/YouTubeRepositoryImpl.kt
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
app/src/main/java/com/facet/music/feature/player/PlayerViewModel.kt
app/src/main/java/com/facet/music/feature/search/SearchViewModel.kt
app/src/main/java/com/facet/music/navigation/AppNavGraph.kt
app/src/test/java/com/facet/music/core/player/PlayerControllerQueueTest.kt
app/src/test/java/com/facet/music/core/download/DownloadWorkerFormatTest.kt (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/feature/search/SearchViewModelExternalRequestTest.kt (РЅРѕРІС‹Р№)
PROGRESS.md
MEMORY.md
```

### РџСЂРѕРІРµСЂРєР°
- `.\gradlew assembleDebug testDebugUnitTest` вЂ” BUILD SUCCESSFUL.
- 78 JVM-С‚РµСЃС‚РѕРІ, 0 failures, 0 errors.
- APK: `C:\Users\souler\Downloads\Facet-debug.apk`.

---

## вњ… РЎРµСЃСЃРёСЏ 5 вЂ” MD3 Expressive: Dynamic Color Рё РЅРѕРІС‹Р№ Home (22.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Р¤СѓРЅРґР°РјРµРЅС‚ С‚РµРјС‹**:
   - РЅР° Android 12+ `FacetTheme` РІСЃРµРіРґР° РёСЃРїРѕР»СЊР·СѓРµС‚ `dynamicDarkColorScheme` / `dynamicLightColorScheme` РѕС‚ СЃРёСЃС‚РµРјС‹;
   - СЂСѓС‡РЅРѕР№ Р°РєС†РµРЅС‚ РёР· РЅР°СЃС‚СЂРѕРµРє РїСЂРёРјРµРЅСЏРµС‚СЃСЏ С‚РѕР»СЊРєРѕ РЅР° Android РЅРёР¶Рµ 12;
   - fallback-РїР°Р»РёС‚СЂР° РїРµСЂРµРІРµРґРµРЅР° СЃ С„РёРѕР»РµС‚РѕРІРѕР№ РЅР° С‚С‘РїР»С‹Р№ РіР»СѓР±РѕРєРёР№ С„РѕРЅ + РєРѕСЂР°Р»Р»РѕРІС‹Р№ Р°РєС†РµРЅС‚, РґРѕР±Р°РІР»РµРЅС‹ РІСЃРµ `surfaceContainer*`, `surfaceDim/Bright`, `outlineVariant` Рё inverse-СЂРѕР»Рё;
   - СЃРѕР·РґР°РЅ `Shape.kt`: РЅР°СЃС‚РѕСЏС‰Р°СЏ squircle-С„РѕСЂРјР° С‡РµСЂРµР· СЃСѓРїРµСЂСЌР»Р»РёРїСЃ, РєСЂСѓРїРЅС‹Рµ M3-СЃРєСЂСѓРіР»РµРЅРёСЏ, Pill Рё Bento-С‚РѕРєРµРЅС‹; `shapes` РїРѕРґРєР»СЋС‡РµРЅС‹ Рє `MaterialTheme`;
   - `Type.kt` СѓСЃРёР»РµРЅ: ExtraBold Display/Headline, РєСЂСѓРїРЅРµРµ Display Small, РїР»РѕС‚РЅС‹Р№ С‚СЂРµРєРёРЅРі.
2. **Р“Р»Р°РІРЅС‹Р№ СЌРєСЂР°РЅ РїРѕР»РЅРѕСЃС‚СЊСЋ РїРµСЂРµРїРёСЃР°РЅ**:
   - С€Р°РїРєР° Р±РµР· emoji Рё refresh: РґР°С‚Р°, РєСЂСѓРїРЅРѕРµ РїСЂРёРІРµС‚СЃС‚РІРёРµ, РѕРґРЅР° РёРєРѕРЅРєР° РЅР°СЃС‚СЂРѕРµРє, pull-to-refresh;
   - hero В«РџСЂРѕРґРѕР»Р¶РёС‚СЊ СЃР»СѓС€Р°С‚СЊВ» РёР· РїРѕСЃР»РµРґРЅРµРіРѕ С‚СЂРµРєР° РёСЃС‚РѕСЂРёРё: РєРѕРјРїР°РєС‚РЅР°СЏ РєР°СЂС‚РѕС‡РєР°, РїСЂСѓР¶РёРЅРЅС‹Р№ scale 0.95, РјРѕСЂС„РёРЅРі СЃРєСЂСѓРіР»РµРЅРёСЏ Рё С†РІРµС‚Р°;
   - Bento-СЂР°Р·РґРµР» В«Р§Р°СЂС‚С‹В»: РґРІР° С‚РѕРЅР°Р»СЊРЅС‹С… Р±Р»РѕРєР° `Top RU` / `Top US` СЃ РєСЂСѓРїРЅС‹РјРё РЅРѕРјРµСЂР°РјРё 01/02/03 Рё РєР»РёРєР°Р±РµР»СЊРЅС‹РјРё С‚СЂРµРєР°РјРё;
   - В«РҐСЂРѕРЅРёРєР°В»: РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅР°СЏ РєР°СЂСѓСЃРµР»СЊ СЃ С‡РµСЂРµРґРѕРІР°РЅРёРµРј РєР°СЂС‚РѕС‡РµРє 160dp / 120dp, СЂР°Р·РЅРѕР№ РІС‹СЃРѕС‚РѕР№ Рё РІРµСЂС‚РёРєР°Р»СЊРЅС‹Рј СЃРјРµС‰РµРЅРёРµРј;
   - В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏВ»: РѕРґРЅР° РєСЂСѓРїРЅР°СЏ Bento-РєР°СЂС‚РѕС‡РєР° СЃ СЃРµР·РѕРЅРЅС‹Рј Р°РєС†РµРЅС‚РѕРј, РєРѕРѕСЂРґРёРЅР°С‚Р°РјРё, СЃС‡С‘С‚С‡РёРєРѕРј, СЃС‚РµРєРѕРј РѕР±Р»РѕР¶РµРє Рё Р·Р°РїСѓСЃРєРѕРј РѕС‡РµСЂРµРґРё;
   - РЅРёР¶Рµ вЂ” Bento-РїР°СЂР° В«Р›СЋР±РёРјРѕРµВ» Рё В«Р’Р°С€ Р¶Р°РЅСЂВ» СЃ СЂРµР°Р»СЊРЅС‹РјРё СЃС‡С‘С‚С‡РёРєР°РјРё;
   - РїРѕСЏРІР»РµРЅРёРµ СЃРµРєС†РёР№ вЂ” staggered РЅР° Spring СЃ `dampingRatio = 0.8f`.
3. **Р”Р°РЅРЅС‹Рµ**:
   - РґРѕР±Р°РІР»РµРЅР° РґРѕРјРµРЅРЅР°СЏ РјРѕРґРµР»СЊ `SeasonMemory` Рё `LikedTracksRepository.getSeasonMemories()`, РєРѕРѕСЂРґРёРЅР°С‚С‹/СЃРµР·РѕРЅ Р±РѕР»СЊС€Рµ РЅРµ С‚РµСЂСЏСЋС‚СЃСЏ РїСЂРё С‡С‚РµРЅРёРё РёР· Room;
   - `HomeViewModel` Р±РѕР»СЊС€Рµ РЅРµ РіСЂСѓР·РёС‚ СЃС‚Р°СЂС‹Рµ SoundCloud-С‡Р°СЂС‚С‹ РґР»СЏ Home; Top RU/US Р·Р°РіСЂСѓР¶Р°СЋС‚СЃСЏ РїР°СЂР°Р»Р»РµР»СЊРЅРѕ С‡РµСЂРµР· `YouTubeRepository.search()` (`Top 100 Russia music`, `Top 100 USA music`, Р»РёРјРёС‚ 8, С„РёР»СЊС‚СЂ РґР»РёС‚РµР»СЊРЅРѕСЃС‚Рё);
   - РїСЂРё РѕС€РёР±РєРµ РѕРґРЅРѕРіРѕ СЂРµРіРёРѕРЅР° РІС‚РѕСЂРѕР№ СЃРѕС…СЂР°РЅСЏРµС‚СЃСЏ, РїСЂРё РЅРµСѓРґР°С‡РЅРѕРј refresh СЃС‚Р°СЂС‹Рµ С‡Р°СЂС‚С‹ РЅРµ Р·Р°С‚РёСЂР°СЋС‚СЃСЏ;
   - РїСЂРёР»РѕР¶РµРЅРёРµ Р±РѕР»СЊС€Рµ РЅРµ Р·Р°РІРёСЃРёС‚ РѕС‚ РѕР±Р»РѕР¶РєРё РЅР° РіР»Р°РІРЅРѕРј: С†РІРµС‚Р° Р±РµСЂСѓС‚СЃСЏ РёР· СЃРёСЃС‚РµРјРЅРѕР№ Dynamic Color РёР»Рё fallback-РїР°Р»РёС‚СЂС‹.
4. **РќР°СЃС‚СЂРѕР№РєРё**:
   - РїРѕРґРїРёСЃСЊ Р°РєС†РµРЅС‚Р° РѕР±СЉСЏСЃРЅСЏРµС‚, С‡С‚Рѕ РЅР° Android 12+ РёСЃРїРѕР»СЊР·СѓСЋС‚СЃСЏ СЃРёСЃС‚РµРјРЅС‹Рµ С†РІРµС‚Р°;
   - СЂСѓС‡РЅС‹Рµ Р°РєС†РµРЅС‚С‹ РґРѕСЃС‚СѓРїРЅС‹ С‚РѕР»СЊРєРѕ РЅР° СЃС‚Р°СЂС‹С… РІРµСЂСЃРёСЏС…, РґРµС„РѕР»С‚РЅС‹Р№ Р°РєС†РµРЅС‚ вЂ” РєРѕСЂР°Р»Р»РѕРІС‹Р№.
5. **РўРµСЃС‚С‹**:
   - РѕР±РЅРѕРІР»С‘РЅ `HomeViewModelGenreTest` РїРѕРґ `SeasonMemory` Рё YouTube-СЂРµРїРѕР·РёС‚РѕСЂРёР№;
   - РґРѕР±Р°РІР»РµРЅ `HomeViewModelChartsTest`: СѓСЃРїРµС€РЅР°СЏ РїР°СЂР°Р»Р»РµР»СЊРЅР°СЏ Р·Р°РіСЂСѓР·РєР°, С‡Р°СЃС‚РёС‡РЅР°СЏ РѕС€РёР±РєР°, СЃРѕС…СЂР°РЅРµРЅРёРµ СЃС‚Р°СЂС‹С… С‡Р°СЂС‚РѕРІ РїСЂРё РЅРµСѓРґР°С‡РЅРѕРј refresh;
   - `LikedTracksRepositoryTest` РїСЂРѕРІРµСЂСЏРµС‚ РєРѕРѕСЂРґРёРЅР°С‚С‹ Рё СЃРµР·РѕРЅ С‡РµСЂРµР· `getSeasonMemories()`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/core/ui/theme/Color.kt
app/src/main/java/com/facet/music/core/ui/theme/Theme.kt
app/src/main/java/com/facet/music/core/ui/theme/Type.kt
app/src/main/java/com/facet/music/core/ui/theme/Shape.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/core/ui/theme/AccentPalette.kt
app/src/main/java/com/facet/music/MainActivity.kt
app/src/main/java/com/facet/music/feature/home/HomeScreen.kt (РїРѕР»РЅРѕСЃС‚СЊСЋ РїРµСЂРµРїРёСЃР°РЅ)
app/src/main/java/com/facet/music/feature/home/HomeViewModel.kt
app/src/main/java/com/facet/music/feature/settings/SettingsScreen.kt
app/src/main/java/com/facet/music/data/model/SeasonMemory.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/local/entity/LikedTrackEntity.kt
app/src/main/java/com/facet/music/data/local/LikedTracksRepositoryImpl.kt
app/src/main/java/com/facet/music/data/repository/LikedTracksRepository.kt
app/src/main/java/com/facet/music/data/repository/SettingsRepository.kt
app/src/test/java/com/facet/music/feature/home/HomeViewModelGenreTest.kt
app/src/test/java/com/facet/music/feature/home/HomeViewModelChartsTest.kt (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/data/local/LikedTracksRepositoryTest.kt
PROGRESS.md
MEMORY.md
```

### РџСЂРѕРІРµСЂРєР°
- `.\gradlew assembleDebug testDebugUnitTest` вЂ” BUILD SUCCESSFUL.
- 80 JVM-С‚РµСЃС‚РѕРІ, 0 failures, 0 errors.
- `lintDebug`: РЅРѕРІС‹С… Р·Р°РјРµС‡Р°РЅРёР№ РІ С„Р°Р№Р»Р°С… Home/С‚РµРјС‹ РЅРµС‚; РµРґРёРЅСЃС‚РІРµРЅРЅР°СЏ РѕС€РёР±РєР° вЂ” СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёР№ Media3 `UnstableApi` РІ `PlayerController` (РїР»РµРµСЂ РїРѕ СѓСЃР»РѕРІРёСЋ РЅРµ С‚СЂРѕРіР°Р»Рё).
- РџР»РµРµСЂ Рё РїРѕРёСЃРє РЅРµ РёР·РјРµРЅСЏР»РёСЃСЊ.

---

## вњ… РЎРµСЃСЃРёСЏ 6 вЂ” РљРѕРјРїР°РєС‚РЅС‹Р№ Home (23.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
- Home РїРµСЂРµРїРёСЃР°РЅ РїРѕ РЅРѕРІРѕРјСѓ РўР—: РјР°РєСЃРёРјСѓРј 4 Р±Р»РѕРєР°, Р±РµР· Bento-СЃРµС‚РєРё, РЅР°Р»РѕР¶РµРЅРёР№, РІС‹Р»РµС‚РѕРІ Р·Р° РєСЂР°Р№ Рё РґРµРєРѕСЂР°С‚РёРІРЅС‹С… РіСЂР°РґРёРµРЅС‚РѕРІ.
- Р‘Р»РѕРє 1 вЂ” РєРѕРјРїР°РєС‚РЅС‹Р№ hero В«РџСЂРѕРґРѕР»Р¶РёС‚СЊ СЃР»СѓС€Р°С‚СЊВ»: РѕР±Р»РѕР¶РєР° СЃР»РµРІР°, С‚РµРєСЃС‚ СЃРїСЂР°РІР°, Play-Р±РµР№РґР¶, press-scale 0.95.
- Р‘Р»РѕРє 2 вЂ” РѕРґРёРЅ Р±Р»РѕРє В«Р§Р°СЂС‚С‹В» СЃ РїРµСЂРµРєР»СЋС‡Р°С‚РµР»РµРј `Top RU` / `Top US`; РІРЅСѓС‚СЂРё 3 С‚СЂРµРєР° СЃ РєСЂСѓРїРЅС‹РјРё РЅРѕРјРµСЂР°РјРё, РѕС€РёР±РєР° Рё skeleton РѕСЃС‚Р°СЋС‚СЃСЏ РІРЅСѓС‚СЂРё Р±Р»РѕРєР°.
- Р‘Р»РѕРє 3 вЂ” В«РќРµРґР°РІРЅРµРµВ»: РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅР°СЏ Р»РµРЅС‚Р° РѕР±Р»РѕР¶РµРє 126dp, РїРѕРґРїРёСЃРё РїРѕРІРµСЂС… СЃ РїР»РѕСЃРєРёРј СЃРєСЂРёРјРѕРј (Р±РµР· РіСЂР°РґРёРµРЅС‚Р°).
- Р‘Р»РѕРє 4 вЂ” РѕРїС†РёРѕРЅР°Р»СЊРЅРѕ В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏВ» (СЃРµР·РѕРЅ + РєРѕР»РёС‡РµСЃС‚РІРѕ) РёР»Рё В«Р›СЋР±РёРјРѕРµВ» (РєРѕР»РёС‡РµСЃС‚РІРѕ); РїСЂРё РѕС‚СЃСѓС‚СЃС‚РІРёРё РґР°РЅРЅС‹С… РЅРµ РїРѕРєР°Р·С‹РІР°РµС‚СЃСЏ.
- РћС‚СЃС‚СѓРїС‹: 26dp РјРµР¶РґСѓ Р±Р»РѕРєР°РјРё, 12вЂ“14dp РІРЅСѓС‚СЂРё; СЂР°Р·РЅС‹Рµ СЃРєСЂСѓРіР»РµРЅРёСЏ: hero 28dp, С‡Р°СЂС‚С‹ 20dp, РєРѕРјРїР°РєС‚РЅР°СЏ РєР°СЂС‚РѕС‡РєР° 16dp, РѕР±Р»РѕР¶РєРё вЂ” squircle.
- РџРѕСЏРІР»РµРЅРёРµ Р±Р»РѕРєРѕРІ вЂ” Р»С‘РіРєРёР№ spring `dampingRatio = 0.8`, С‚РѕР»СЊРєРѕ alpha + РїРѕРґСЉС‘Рј 12dp.
- РџР»РµРµСЂ Рё РїРѕРёСЃРє РЅРµ РёР·РјРµРЅСЏР»РёСЃСЊ. `Theme.kt`, `Color.kt`, `Shape.kt` СѓР¶Рµ СЃРѕРѕС‚РІРµС‚СЃС‚РІРѕРІР°Р»Рё РўР—, РїРѕСЌС‚РѕРјСѓ РЅРµ РїРµСЂРµРїРёСЃС‹РІР°Р»РёСЃСЊ.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/home/HomeScreen.kt (РїРѕР»РЅРѕСЃС‚СЊСЋ РїРµСЂРµРїРёСЃР°РЅ)
PROGRESS.md
MEMORY.md
```

### РџСЂРѕРІРµСЂРєР°
- `.\gradlew assembleDebug` вЂ” BUILD SUCCESSFUL.
- `.\gradlew testDebugUnitTest` вЂ” BUILD SUCCESSFUL, 80 JVM-С‚РµСЃС‚РѕРІ, 0 failures, 0 errors.
- `lintDebug`: РЅРѕРІС‹С… Р·Р°РјРµС‡Р°РЅРёР№ РІ HomeScreen РЅРµС‚; РѕСЃС‚Р°С‘С‚СЃСЏ СЃСѓС‰РµСЃС‚РІСѓСЋС‰Р°СЏ РѕС€РёР±РєР° Media3 `UnstableApi` РІ `PlayerController` (РїР»РµРµСЂ РЅРµ С‚СЂРѕРіР°Р»Рё).
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

---

## вњ… РЎРµСЃСЃРёСЏ 7 вЂ” Р РµРєРѕРјРµРЅРґР°С†РёРё РІРјРµСЃС‚Рѕ С‡Р°СЂС‚РѕРІ (23.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
- Р‘Р»РѕРє В«Р§Р°СЂС‚С‹В» (`Top RU` / `Top US`) РїРѕР»РЅРѕСЃС‚СЊСЋ СѓРґР°Р»С‘РЅ РёР· Home.
- Р’РјРµСЃС‚Рѕ РЅРµРіРѕ РґРѕР±Р°РІР»РµРЅ Р±Р»РѕРє В«Р”Р»СЏ РІР°СЃВ»: РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅР°СЏ `LazyRow` СЃ СЂРµРєРѕРјРµРЅРґР°С†РёСЏРјРё.
- РљР°СЂС‚РѕС‡РєР° СЂРµРєРѕРјРµРЅРґР°С†РёРё: РѕР±Р»РѕР¶РєР° 140dp РІ `SquircleShape`, РЅР°Р·РІР°РЅРёРµ (РґРѕ 2 СЃС‚СЂРѕРє) Рё Р°СЂС‚РёСЃС‚ РїРѕРґ РѕР±Р»РѕР¶РєРѕР№, press-scale 0.96.
- `HomeViewModel` СЃС‚СЂРѕРёС‚ РїРµСЂСЃРѕРЅР°Р»СЊРЅС‹Рµ Р·Р°РїСЂРѕСЃС‹ РїРѕ РїРѕСЃР»РµРґРЅРёРј Р»Р°Р№РєР°Рј Рё РёСЃС‚РѕСЂРёРё: РїРµСЂРІС‹Р№ Р¶Р°РЅСЂ Рё РїРµСЂРІС‹Р№ Р°СЂС‚РёСЃС‚, РґРІР° РїР°СЂР°Р»Р»РµР»СЊРЅС‹С… Р·Р°РїСЂРѕСЃР° `YouTubeRepository.search()` (`"<Р¶Р°РЅСЂ> music mix"`, `"<Р°СЂС‚РёСЃС‚> best songs"`).
- Р•СЃР»Рё Р»Р°Р№РєРѕРІ/РёСЃС‚РѕСЂРёРё РЅРµС‚ вЂ” Р·Р°РіСЂСѓР¶Р°СЋС‚СЃСЏ РїРѕРїСѓР»СЏСЂРЅС‹Рµ С‚СЂРµРєРё (`popular music 2026`).
- Р•СЃР»Рё РїРµСЂСЃРѕРЅР°Р»РёР·РёСЂРѕРІР°РЅРЅС‹Р№ РїРѕРёСЃРє РїСѓСЃС‚ РёР»Рё СѓРїР°Р», РІРєР»СЋС‡Р°РµС‚СЃСЏ С‚РѕС‚ Р¶Рµ РїРѕРїСѓР»СЏСЂРЅС‹Р№ fallback.
- Р РµР·СѓР»СЊС‚Р°С‚С‹ С„РёР»СЊС‚СЂСѓСЋС‚СЃСЏ РїРѕ РґР»РёС‚РµР»СЊРЅРѕСЃС‚Рё 45вЂ“900 СЃРµРєСѓРЅРґ, РґРµРґСѓРїР»РёС†РёСЂСѓСЋС‚СЃСЏ Рё РёСЃРєР»СЋС‡Р°СЋС‚ С‚СЂРµРєРё, СѓР¶Рµ РЅР°С…РѕРґСЏС‰РёРµСЃСЏ РІ Р»Р°Р№РєР°С…/РёСЃС‚РѕСЂРёРё.
- РџРѕРІС‚РѕСЂРЅС‹Рµ Р·Р°РїСЂРѕСЃС‹ Р·Р°С‰РёС‰РµРЅС‹ РєР»СЋС‡РѕРј РїРµСЂСЃРѕРЅР°Р»РёР·Р°С†РёРё: РѕРґРёРЅР°РєРѕРІС‹Рµ Р¶Р°РЅСЂ/Р°СЂС‚РёСЃС‚ РЅРµ Р·Р°РїСѓСЃРєР°СЋС‚ РЅРѕРІСѓСЋ Р·Р°РіСЂСѓР·РєСѓ; РїСЂРё РЅРѕРІС‹С… СЃРёРіРЅР°Р»Р°С… СЃС‚Р°СЂС‹Р№ Р·Р°РїСЂРѕСЃ РѕС‚РјРµРЅСЏРµС‚СЃСЏ.
- РћС€РёР±РєР° Р·Р°РіСЂСѓР·РєРё РЅРµ Р·Р°С‚РёСЂР°РµС‚ СѓР¶Рµ РїРѕРєР°Р·Р°РЅРЅС‹Рµ СЂРµРєРѕРјРµРЅРґР°С†РёРё; РґР»СЏ РїСѓСЃС‚РѕРіРѕ СЃРѕСЃС‚РѕСЏРЅРёСЏ РµСЃС‚СЊ РєРЅРѕРїРєР° В«РџРѕРІС‚РѕСЂРёС‚СЊВ»/В«РћР±РЅРѕРІРёС‚СЊВ».
- Р’ В«РќРµРґР°РІРЅРµРјВ» РѕР±Р»РѕР¶РєРё РїРµСЂРµРІРµРґРµРЅС‹ РЅР° `RoundedCornerShape(18.dp)`, СЃРєСЂРёРј СѓСЃРёР»РµРЅ РґРѕ `0.56f`, РїРѕРґРїРёСЃРё РїРѕР»СѓС‡РёР»Рё Р±РѕР»СЊС€РёР№ РєРѕРЅС‚СЂР°СЃС‚.
- Hero, РѕРїС†РёРѕРЅР°Р»СЊРЅР°СЏ РєР°СЂС‚РѕС‡РєР°, РѕС‚СЃС‚СѓРїС‹, reveal-Р°РЅРёРјР°С†РёРё Рё press-scale РѕСЃС‚Р°Р»СЊРЅС‹С… Р±Р»РѕРєРѕРІ РЅРµ РјРµРЅСЏР»РёСЃСЊ. РџР»РµРµСЂ Рё РїРѕРёСЃРє РЅРµ С‚СЂРѕРіР°Р»РёСЃСЊ.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/home/HomeViewModel.kt
app/src/main/java/com/facet/music/feature/home/HomeScreen.kt
app/src/test/java/com/facet/music/feature/home/HomeViewModelRecommendationsTest.kt (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/feature/home/HomeViewModelChartsTest.kt (СѓРґР°Р»С‘РЅ)
PROGRESS.md
MEMORY.md
```

### РџСЂРѕРІРµСЂРєР°
- `.\gradlew assembleDebug` вЂ” BUILD SUCCESSFUL.
- `.\gradlew testDebugUnitTest` вЂ” BUILD SUCCESSFUL, 81 JVM-С‚РµСЃС‚, 0 failures, 0 errors.
- `lintDebug`: РЅРѕРІС‹С… Р·Р°РјРµС‡Р°РЅРёР№ РІ Home РЅРµС‚; РѕСЃС‚Р°С‘С‚СЃСЏ СЃСѓС‰РµСЃС‚РІСѓСЋС‰Р°СЏ РѕС€РёР±РєР° Media3 `UnstableApi` РІ `PlayerController` (РїР»РµРµСЂ РЅРµ С‚СЂРѕРіР°Р»Рё).
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

---

## вњ… РЎРµСЃСЃРёСЏ 8 вЂ” РџРѕР»РёСЂРѕРІРєР° СЂРµРєРѕРјРµРЅРґР°С†РёР№ Рё В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёР№В» (23.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
- В«Р”Р»СЏ РІР°СЃВ»: Р·Р°РїСЂРѕСЃС‹ РїРµСЂРµРІРµРґРµРЅС‹ РЅР° `"<Р¶Р°РЅСЂ> official audio"` Рё `"<Р°СЂС‚РёСЃС‚> official audio"`, fallback вЂ” `"popular songs official audio"`.
- Р”РѕР±Р°РІР»РµРЅ С„РёР»СЊС‚СЂ РЅРµСЃС‚СѓРґРёР№РЅС‹С… Р·Р°РїРёСЃРµР№: РїРµСЂРµРІРѕРґС‹, СЂРµРјРёРєСЃС‹, РєР°РІРµСЂС‹, live, РєР°СЂР°РѕРєРµ, РёРЅСЃС‚СЂСѓРјРµРЅС‚Р°Р»С‹, Р°РєСѓСЃС‚РёРєР°, slowed/nightcore, РјРЅРѕРіРѕСЏР·С‹С‡РЅС‹Рµ РІРµСЂСЃРёРё Рё С‚СЂРµРєРё Р±РµР· Р±СѓРєРІ РІ РЅР°Р·РІР°РЅРёРё.
- Р РµРєРѕРјРµРЅРґР°С†РёРё Р±РѕР»СЊС€Рµ РЅРµ РёСЃРєР»СЋС‡Р°СЋС‚ РїСЂРѕСЃР»СѓС€Р°РЅРЅС‹Рµ РёР»Рё Р»Р°Р№РєРЅСѓС‚С‹Рµ С‚СЂРµРєРё. Р›РµРЅС‚Р° Р·Р°РіСЂСѓР¶Р°РµС‚СЃСЏ РѕРґРёРЅ СЂР°Р· Р·Р° Р¶РёР·РЅСЊ `HomeViewModel`, РїРѕРІС‚РѕСЂРЅРѕ вЂ” С‚РѕР»СЊРєРѕ С‡РµСЂРµР· Pull-to-refresh.
- Р•СЃР»Рё РїРµСЂСЃРѕРЅР°Р»СЊРЅС‹С… СЂРµР·СѓР»СЊС‚Р°С‚РѕРІ РјРµРЅСЊС€Рµ 10, РѕРЅРё РґРѕР±РёСЂР°СЋС‚СЃСЏ РїРѕРїСѓР»СЏСЂРЅС‹РјРё С‚СЂРµРєР°РјРё РґРѕ 15. РњР°РєСЃРёРјСѓРј РєР°СЂСѓСЃРµР»Рё вЂ” 15 РєР°СЂС‚РѕС‡РµРє.
- РџРѕР·РёС†РёСЏ РѕСЃРЅРѕРІРЅРѕРіРѕ СЃРїРёСЃРєР° Рё РєР°СЂСѓСЃРµР»Рё СЂРµРєРѕРјРµРЅРґР°С†РёР№ СЃРѕС…СЂР°РЅСЏРµС‚СЃСЏ С‡РµСЂРµР· `rememberLazyListState()`; `RevealBlock` РЅРµ РїРѕРІС‚РѕСЂСЏРµС‚ Р°РЅРёРјР°С†РёСЋ РїСЂРё РІРѕР·РІСЂР°С‚Рµ СЃ РїР»РµРµСЂР°.
- В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏВ» РїРµСЂРµРґРµР»Р°РЅС‹ РІ РµРґРёРЅСѓСЋ РєР°СЂС‚РѕС‡РєСѓ Р±РµР· РІС‹СЃС‚СѓРїР°: С€Р°РїРєР° СЃ СЃРµР·РѕРЅРѕРј, РєРѕР»РёС‡РµСЃС‚РІРѕРј С‚СЂРµРєРѕРІ Рё РєРЅРѕРїРєРѕР№ Play, СЂР°Р·РґРµР»РёС‚РµР»СЊ Рё СЃРїРёСЃРѕРє РґРѕ 5 С‚СЂРµРєРѕРІ.
- Р’ СЃРїРёСЃРєРµ В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёР№В» РєРІР°РґСЂР°С‚РЅС‹Рµ РѕР±Р»РѕР¶РєРё 40dp, РЅР°Р·РІР°РЅРёРµ, Р°СЂС‚РёСЃС‚ Рё РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊ. Play Р·Р°РїСѓСЃРєР°РµС‚ РѕС‡РµСЂРµРґСЊ СЃ РїРµСЂРІРѕРіРѕ С‚СЂРµРєР°, С‚Р°Рї РїРѕ С‚СЂРµРєСѓ вЂ” СЃ РІС‹Р±СЂР°РЅРЅРѕРіРѕ.
- РџР»РµРµСЂ Рё РїРѕРёСЃРє РЅРµ РёР·РјРµРЅСЏР»РёСЃСЊ.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/home/HomeViewModel.kt
app/src/main/java/com/facet/music/feature/home/HomeScreen.kt
app/src/test/java/com/facet/music/feature/home/HomeViewModelRecommendationsTest.kt
PROGRESS.md
MEMORY.md
```

### РџСЂРѕРІРµСЂРєР°
- `.\gradlew testDebugUnitTest` вЂ” BUILD SUCCESSFUL, 84 JVM-С‚РµСЃС‚Р°, 0 failures, 0 errors.
- `.\gradlew assembleDebug` вЂ” BUILD SUCCESSFUL.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

### РЎР»РµРґСѓСЋС‰РёР№ С€Р°Рі
- РРјРїРѕСЂС‚ РїР»РµР№Р»РёСЃС‚РѕРІ YouTube РёР»Рё СЌРєСЂР°РЅ В«РЎРєР°С‡Р°РЅРЅРѕРµВ».

---

## вњ… РЎРµСЃСЃРёСЏ 9 вЂ” Р РµРєРѕРјРµРЅРґР°С†РёРё v2: СЃРёРґС‹, РІР°Р»РёРґР°С†РёСЏ Рё recently_shown (24.10.2026)

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
- В«Р”Р»СЏ РІР°СЃВ» РїРµСЂРµРІРµРґРµРЅС‹ СЃ РѕРґРЅРѕРіРѕ Р¶Р°РЅСЂР°/Р°СЂС‚РёСЃС‚Р° РЅР° С‚РѕРї-8 Р°СЂС‚РёСЃС‚РѕРІ-СЃРёРґРѕРІ РёР· Р»Р°Р№РєРѕРІ Рё РёСЃС‚РѕСЂРёРё. Р›Р°Р№Рє РІРµСЃРёС‚ Р±РѕР»СЊС€Рµ РїСЂРѕСЃР»СѓС€РёРІР°РЅРёСЏ, СЃРІРµР¶РёРµ Р·Р°РїРёСЃРё вЂ” Р±РѕР»СЊС€Рµ СЃС‚Р°СЂС‹С…; РїРѕРІС‚РѕСЂ РѕРґРЅРѕРіРѕ Рё С‚РѕРіРѕ Р¶Рµ С‚СЂРµРєР° РЅРµ СѓРґРІР°РёРІР°РµС‚ РІРµСЃ.
- РРјСЏ Р°СЂС‚РёСЃС‚Р° РЅРѕСЂРјР°Р»РёР·СѓРµС‚СЃСЏ: РЅРёР¶РЅРёР№ СЂРµРіРёСЃС‚СЂ, СѓРґР°Р»РµРЅРёРµ `feat.`/`ft.`/`prod.`/`produced by`, СЃС…Р»РѕРїС‹РІР°РЅРёРµ РїСЂРѕР±РµР»РѕРІ.
- РќР° РєР°Р¶РґС‹Р№ refresh РІС‹Р±РёСЂР°СЋС‚СЃСЏ СЃР»СѓС‡Р°Р№РЅС‹Рµ 4вЂ“5 Р°СЂС‚РёСЃС‚РѕРІ РёР· С‚РѕРї-8. Р”Р»СЏ РєР°Р¶РґРѕРіРѕ РіРµРЅРµСЂРёСЂСѓСЋС‚СЃСЏ 1вЂ“2 Р·Р°РїСЂРѕСЃР° РёР· `"<artist> official audio"`, `"<artist> <Р¶Р°РЅСЂ>"`, `"<artist> official"`, РїР»СЋСЃ 1вЂ“2 РѕР±С‰РёС… Р·Р°РїСЂРѕСЃР° `"<Р¶Р°РЅСЂ> official audio"`.
- Р”РѕР±Р°РІР»РµРЅР° СЃС‚СЂРѕРіР°СЏ РІР°Р»РёРґР°С†РёСЏ: С‚СЂРµРє РїСЂРёРЅРёРјР°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РїСЂРё СЃРѕРІРїР°РґРµРЅРёРё РЅРѕСЂРјР°Р»РёР·РѕРІР°РЅРЅРѕРіРѕ `track.artist` СЃ СЃРёРґ-Р°СЂС‚РёСЃС‚РѕРј. Р•СЃР»Рё Р°СЂС‚РёСЃС‚ РїСѓСЃС‚РѕР№ РёР»Рё `Unknown`, СЃСЂР°РІРЅРёРІР°РµС‚СЃСЏ РЅР°С‡Р°Р»Рѕ РЅР°Р·РІР°РЅРёСЏ (`startsWith`), Р° РЅРµ РїРѕРґСЃС‚СЂРѕРєР°. РљРµР№СЃ В«РёРјСЏ СЃРёРґР° РІ title С‡СѓР¶РѕРіРѕ С‚СЂРµРєР°В» Р±РѕР»СЊС€Рµ РЅРµ РїСЂРѕС…РѕРґРёС‚.
- Р¤РёР»СЊС‚СЂ РїРµСЂРµРІРѕРґРѕРІ, СЂРµРјРёРєСЃРѕРІ, РєР°РІРµСЂРѕРІ, live, РєР°СЂР°РѕРєРµ, РёРЅСЃС‚СЂСѓРјРµРЅС‚Р°Р»Р°, Р°РєСѓСЃС‚РёРєРё, slowed/nightcore Рё РјРЅРѕРіРѕСЏР·С‹С‡РЅС‹С… РІРµСЂСЃРёР№ СЃРѕС…СЂР°РЅС‘РЅ.
- Room РїРѕРґРЅСЏС‚ РґРѕ v8: С‚Р°Р±Р»РёС†Р° `recently_shown` (`trackId`, `shownAt`) СЃ TTL 24 С‡Р°СЃР°. РџРѕРєР°Р·Р°РЅРЅС‹Рµ С‚СЂРµРєРё РёСЃРєР»СЋС‡Р°СЋС‚СЃСЏ РёР· СЃР»РµРґСѓСЋС‰РµР№ РїР°С‡РєРё, СЃС‚Р°СЂС‹Рµ Р·Р°РїРёСЃРё РІС‹С‡РёС‰Р°СЋС‚СЃСЏ РїСЂРё Р·Р°РїРёСЃРё РЅРѕРІРѕР№.
- РџСѓР» РєР°РЅРґРёРґР°С‚РѕРІ РїРµСЂРµРјРµС€РёРІР°РµС‚СЃСЏ РїРµСЂРµРґ РѕР±СЂРµР·РєРѕР№. Р’ С„РёРЅР°Р»СЊРЅРѕР№ Р»РµРЅС‚Рµ РЅРµ Р±РѕР»СЊС€Рµ 3 С‚СЂРµРєРѕРІ РѕРґРЅРѕРіРѕ Р°СЂС‚РёСЃС‚Р°; РїСЂРё РЅРµС…РІР°С‚РєРµ 10 СѓРЅРёРєР°Р»СЊРЅС‹С… Р»РµРЅС‚Р° РґРѕР±РёРІР°РµС‚СЃСЏ РїРѕРїСѓР»СЏСЂРЅС‹РјРё СЃ С‚РµРј Р¶Рµ РєР°РїРѕРј Рё РёСЃРєР»СЋС‡РµРЅРёРµРј `recently_shown`.
- Pull-to-refresh РґРµР»Р°РµС‚ force-Р·Р°РіСЂСѓР·РєСѓ, Р·Р°РјРµРЅСЏРµС‚ Р»РµРЅС‚Сѓ РЅРѕРІРѕР№ РїР°С‡РєРѕР№ Рё РЅРµ РЅР°РєР»Р°РґС‹РІР°РµС‚ РµС‘ РїРѕРІРµСЂС… СЃС‚Р°СЂРѕР№.
- РџСЂРѕСЃР»СѓС€Р°РЅРЅС‹Рµ Рё Р»Р°Р№РєРЅСѓС‚С‹Рµ С‚СЂРµРєРё РЅРµ РёСЃС‡РµР·Р°СЋС‚ РёР· СѓР¶Рµ РїРѕРєР°Р·Р°РЅРЅРѕР№ Р»РµРЅС‚С‹; РІРѕР·РІСЂР°С‚ СЃ РїР»РµРµСЂР° РЅРµ РїРµСЂРµР·Р°РіСЂСѓР¶Р°РµС‚ Home, РїРѕР·РёС†РёРё Рё reveal-Р°РЅРёРјР°С†РёСЏ СЃРѕС…СЂР°РЅСЏСЋС‚СЃСЏ.
- РџР»РµРµСЂ Рё РїРѕРёСЃРє РЅРµ РёР·РјРµРЅСЏР»РёСЃСЊ.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/home/RecommendationPlanner.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/feature/home/HomeViewModel.kt
app/src/main/java/com/facet/music/data/local/entity/RecentlyShownEntity.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/local/dao/RecentlyShownDao.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/repository/RecentlyShownRepository.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/local/RecentlyShownRepositoryImpl.kt (РЅРѕРІС‹Р№)
app/src/main/java/com/facet/music/data/local/AppDatabase.kt
app/src/main/java/com/facet/music/data/local/DatabaseMigrations.kt
app/src/main/java/com/facet/music/data/local/DatabaseModule.kt
app/src/main/java/com/facet/music/core/di/RepositoryModule.kt
app/schemas/com.facet.music.data.local.AppDatabase/8.json (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/feature/home/RecommendationPlannerTest.kt (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/feature/home/HomeViewModelRecommendationsTest.kt
app/src/test/java/com/facet/music/feature/home/HomeViewModelGenreTest.kt
app/src/test/java/com/facet/music/data/local/RecentlyShownRepositoryTest.kt (РЅРѕРІС‹Р№)
app/src/test/java/com/facet/music/data/local/DatabaseMigrationTest.kt
PROGRESS.md
MEMORY.md
```

### РџСЂРѕРІРµСЂРєР°
- `.\gradlew testDebugUnitTest` вЂ” BUILD SUCCESSFUL, 92 JVM-С‚РµСЃС‚Р°, 0 failures, 0 errors.
- `.\gradlew assembleDebug` вЂ” BUILD SUCCESSFUL.
- Room-СЃС…РµРјР° v8 СЌРєСЃРїРѕСЂС‚РёСЂРѕРІР°РЅР°, РјРёРіСЂР°С†РёСЏ 1в†’8 РїСЂРѕРІРµСЂРµРЅР° Robolectric-С‚РµСЃС‚РѕРј.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

### РЎР»РµРґСѓСЋС‰РёР№ С€Р°Рі
- РРјРїРѕСЂС‚ РїР»РµР№Р»РёСЃС‚РѕРІ YouTube РёР»Рё СЌРєСЂР°РЅ В«РЎРєР°С‡Р°РЅРЅРѕРµВ».

---

## вњ… РЎРµСЃСЃРёСЏ 10 вЂ” РћС‚РєР°С‚ ONNX/YAMNet Рё related-СЂРµРєРѕРјРµРЅРґР°С†РёРё (24.10.2026)

### РџСЂРёС‡РёРЅР°
- Р›РѕРєР°Р»СЊРЅС‹Р№ ONNX/YAMNet (РєРѕРјРјРёС‚ `4ea6975`) СЂР°Р·РґСѓРІР°Р» APK РґРѕ ~73 РњР‘ Рё С‚СЂРµР±РѕРІР°Р» С‚СЏР¶С‘Р»РѕРіРѕ Р°СѓРґРёРѕ-Р°РЅР°Р»РёР·Р° РЅР° СЃР»Р°Р±РѕРј СѓСЃС‚СЂРѕР№СЃС‚РІРµ. Р”Р»СЏ СЂРµРєРѕРјРµРЅРґР°С†РёР№ РґРѕСЃС‚Р°С‚РѕС‡РЅРѕ Р°Р»РіРѕСЂРёС‚РјРёС‡РµСЃРєРѕР№ related-РІС‹РґР°С‡Рё NewPipe.
- РџСЂСЏРјС‹Рµ related-С‚СЂРµРєРё РґР°СЋС‚ СЂРµР°Р»СЊРЅС‹С… РЅРѕРІС‹С… РёСЃРїРѕР»РЅРёС‚РµР»РµР№ РІРјРµСЃС‚Рѕ С‚РµРєСЃС‚РѕРІС‹С… РїРѕРёСЃРєРѕРІС‹С… Р·Р°РїСЂРѕСЃРѕРІ Рё СѓРјРµРЅСЊС€Р°СЋС‚ РєРѕР»РёС‡РµСЃС‚РІРѕ РјСѓСЃРѕСЂР°.

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РћС‚РєР°С‚ Phase 1**:
   - РЎРѕР·РґР°РЅ revert-РєРѕРјРјРёС‚ `9e74fb6`; Room СЃРЅРѕРІР° v8, СѓРґР°Р»РµРЅС‹ `onnxruntime-android`, `yamnet.onnx`, `core/audio`, `AudioFeatureWorker`, `9.json`.
   - РЎС…РµРјС‹ Р‘Р”: С‚РѕР»СЊРєРѕ `4.json`вЂ“`8.json`; РѕРїСѓР±Р»РёРєРѕРІР°РЅРЅР°СЏ РёСЃС‚РѕСЂРёСЏ РЅРµ РїРµСЂРµРїРёСЃС‹РІР°Р»Р°СЃСЊ.
2. **Related С‡РµСЂРµР· NewPipe**:
   - Р’ `YouTubeRepository` Рё `SoundCloudRepository` РґРѕР±Р°РІР»РµРЅ `getRelated(trackUrl, limit = 30)`.
   - Р РµР°Р»РёР·Р°С†РёРё РІС‹Р·С‹РІР°СЋС‚ `StreamInfo.getInfo(service, trackUrl)`, Р±РµСЂСѓС‚ `relatedItems`, РїСЂРё РїСѓСЃС‚РѕС‚Рµ вЂ” `relatedStreams`, С„РёР»СЊС‚СЂСѓСЋС‚ `StreamInfoItem`, РґРµРґСѓРїР»РёС†РёСЂСѓСЋС‚ РїРѕ URL Рё РјР°РїРїСЏС‚ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёРј `toDomain()`.
3. **РЎРёРґС‹**:
   - РџРѕСЃР»РµРґРЅРёРµ 20 Р»Р°Р№РєРѕРІ Рё 20 РїСЂРѕСЃР»СѓС€РёРІР°РЅРёР№; Р»Р°Р№Рє РІРµСЃРёС‚ `4.0`, РїСЂРѕСЃР»СѓС€РёРІР°РЅРёРµ `1.0`, СЃРІРµР¶РµСЃС‚СЊ СѓР±С‹РІР°РµС‚ СЃ РёРЅРґРµРєСЃРѕРј; РґСѓР±Р»РёРєР°С‚ РёР· РёСЃС‚РѕСЂРёРё РЅРµ СѓС‡РёС‚С‹РІР°РµС‚СЃСЏ, РµСЃР»Рё С‚СЂРµРє СѓР¶Рµ Р»Р°Р№РєРЅСѓС‚.
   - РќР° refresh СЃР»СѓС‡Р°Р№РЅРѕ РІС‹Р±РёСЂР°СЋС‚СЃСЏ 4вЂ“6 С‚СЂРµРєРѕРІ РёР· С‚РѕРї-20, РїСЂРё РІРѕР·РјРѕР¶РЅРѕСЃС‚Рё вЂ” СЂР°Р·РЅС‹С… Р°СЂС‚РёСЃС‚РѕРІ.
4. **РЎР±РѕСЂРєР° Р»РµРЅС‚С‹**:
   - Р”Р»СЏ РєР°Р¶РґРѕРіРѕ СЃРёРґР° РїР°СЂР°Р»Р»РµР»СЊРЅРѕ Р·Р°РїСЂР°С€РёРІР°РµС‚СЃСЏ РґРѕ 30 related-С‚СЂРµРєРѕРІ; РёСЃРєР»СЋС‡Р°СЋС‚СЃСЏ РІСЃРµ РІС‹Р±СЂР°РЅРЅС‹Рµ СЃРёРґС‹ Рё `recently_shown` (TTL 24 С‡).
   - РЎРѕС…СЂР°РЅРµРЅС‹ С„РёР»СЊС‚СЂС‹ РјСѓСЃРѕСЂР° РїРѕ РЅР°Р·РІР°РЅРёСЋ Рё РґР»РёС‚РµР»СЊРЅРѕСЃС‚Рё 45 СЃвЂ“10 РјРёРЅ. РўСЂРµРєРё РґСЂСѓРіРёС… Р°СЂС‚РёСЃС‚РѕРІ РёР· related РЅРµ РѕС‚Р±СЂР°СЃС‹РІР°СЋС‚СЃСЏ.
   - Р¤РёРЅР°Р» РїРµСЂРµРјРµС€РёРІР°РµС‚СЃСЏ; РЅРµ Р±РѕР»СЊС€Рµ 2 С‚СЂРµРєРѕРІ РЅР° Р°СЂС‚РёСЃС‚Р°, РЅРµ Р±РѕР»СЊС€Рµ 3 вЂ” РґР»СЏ Р°СЂС‚РёСЃС‚Р°, РґРѕРјРёРЅРёСЂСѓСЋС‰РµРіРѕ РІ РїРµСЂСЃРѕРЅР°Р»СЊРЅРѕР№ РІС‹Р±РѕСЂРєРµ (в‰Ґ3 С‚СЂРµРєР° Рё в‰Ґ25%).
   - Р›РёРјРёС‚ Р»РµРЅС‚С‹ вЂ” 15 С‚СЂРµРєРѕРІ; РїСЂРё related <10 РґРѕР±РёСЂР°РµРј fallback-РєР°РЅРґРёРґР°С‚Р°РјРё.
5. **Fallback**:
   - РўРѕС‡РµС‡РЅС‹Р№ РїРѕРёСЃРє РїРѕ 4вЂ“6 С‚РѕРї-Р°СЂС‚РёСЃС‚Р°Рј, СЃС‚СЂРѕРіР°СЏ РїСЂРѕРІРµСЂРєР° С‚РѕР»СЊРєРѕ РїРѕР»СЏ `track.artist`; РёРјСЏ СЃРёРґР° РІ С‡СѓР¶РѕРј title РЅРµ РїСЂРѕС…РѕРґРёС‚.
   - Р“Р»РѕР±Р°Р»СЊРЅС‹Р№ `POPULAR_QUERY` РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ С‚РѕР»СЊРєРѕ РїСЂРё РїРѕР»РЅРѕСЃС‚СЊСЋ РїСѓСЃС‚С‹С… Р»Р°Р№РєР°С… Рё РёСЃС‚РѕСЂРёРё (С…РѕР»РѕРґРЅС‹Р№ СЃС‚Р°СЂС‚).
   - Р•СЃР»Рё РїРµСЂСЃРѕРЅР°Р»СЊРЅС‹Рµ РґР°РЅРЅС‹Рµ РµСЃС‚СЊ, Р° related/fallback РЅРёС‡РµРіРѕ РЅРµ РґР°Р»Рё, РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ РїСѓСЃС‚Р°СЏ СѓСЃРїРµС€РЅР°СЏ Р»РµРЅС‚Р° РёР»Рё РѕС€РёР±РєР° СЃРµС‚Рё вЂ” Р±РµР· РїРѕРїСѓР»СЏСЂРЅРѕРіРѕ Р·Р°РїСЂРѕСЃР°.
6. **РЎС‚Р°Р±РёР»СЊРЅРѕСЃС‚СЊ UI**:
   - `HomeViewModel` СЃРЅРёРјР°РµС‚ СЃРЅРёРјРѕРє Р»Р°Р№РєРѕРІ/РёСЃС‚РѕСЂРёРё РЅР° СЃС‚Р°СЂС‚Рµ Р·Р°РіСЂСѓР·РєРё; РёР·РјРµРЅРµРЅРёСЏ РІРѕ РІСЂРµРјСЏ РїСЂРѕСЃР»СѓС€РёРІР°РЅРёСЏ РЅРµ РїРµСЂРµР·Р°РіСЂСѓР¶Р°СЋС‚ СѓР¶Рµ РїРѕРєР°Р·Р°РЅРЅСѓСЋ Р»РµРЅС‚Сѓ.
   - РћР±РЅРѕРІР»РµРЅРёРµ вЂ” С‚РѕР»СЊРєРѕ Pull-to-refresh; РїРѕР·РёС†РёРё `LazyColumn` Рё РєР°СЂСѓСЃРµР»Рё СЃРѕС…СЂР°РЅСЏСЋС‚СЃСЏ.
   - РџР»РµРµСЂ Рё РїРѕРёСЃРє РЅРµ РёР·РјРµРЅСЏР»РёСЃСЊ.

### РџСЂРѕРІРµСЂРєР°
- `.\gradlew testDebugUnitTest` вЂ” `BUILD SUCCESSFUL`, 100 JVM-С‚РµСЃС‚РѕРІ, 0 failures, 0 errors.
- `.\gradlew assembleDebug` вЂ” `BUILD SUCCESSFUL`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 27 218 807 Р±Р°Р№С‚ (~27,2 РњР‘); `onnx`/`yamnet` РІРЅСѓС‚СЂРё РѕС‚СЃСѓС‚СЃС‚РІСѓСЋС‚.
- Р–РёРІР°СЏ РїСЂРѕРІРµСЂРєР° С‡РµСЂРµР· РІСЂРµРјРµРЅРЅС‹Р№ JVM-С‚РµСЃС‚: YouTube Рё SoundCloud РІРµСЂРЅСѓР»Рё РїРѕ 10 related-СЌР»РµРјРµРЅС‚РѕРІ; РІСЂРµРјРµРЅРЅС‹Р№ С‚РµСЃС‚ СѓРґР°Р»С‘РЅ.
- `HomeViewModelRecommendationsTest` вЂ” 12 С‚РµСЃС‚РѕРІ, `RecommendationPlannerTest` вЂ” 9 С‚РµСЃС‚РѕРІ; РѕР±РЅРѕРІР»С‘РЅ `HomeViewModelGenreTest`.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/data/repository/YouTubeRepository.kt
app/src/main/java/com/facet/music/data/repository/SoundCloudRepository.kt
app/src/main/java/com/facet/music/data/source/youtube/YouTubeRepositoryImpl.kt
app/src/main/java/com/facet/music/data/source/soundcloud/SoundCloudRepositoryImpl.kt
app/src/main/java/com/facet/music/feature/home/HomeViewModel.kt
app/src/main/java/com/facet/music/feature/home/RecommendationPlanner.kt
app/src/test/java/com/facet/music/feature/home/HomeViewModelRecommendationsTest.kt
app/src/test/java/com/facet/music/feature/home/RecommendationPlannerTest.kt
app/src/test/java/com/facet/music/feature/home/HomeViewModelGenreTest.kt
PROGRESS.md
MEMORY.md
```

### РЎР»РµРґСѓСЋС‰РёР№ С€Р°Рі
- РџСЂРѕРІРµСЂРёС‚СЊ related-Р»РµРЅС‚Сѓ РЅР° СЂРµР°Р»СЊРЅРѕРј СѓСЃС‚СЂРѕР№СЃС‚РІРµ, Р·Р°С‚РµРј РёРјРїРѕСЂС‚ РїР»РµР№Р»РёСЃС‚РѕРІ YouTube РёР»Рё СЌРєСЂР°РЅ В«РЎРєР°С‡Р°РЅРЅРѕРµВ».

---

## вњ… РЎРµСЃСЃРёСЏ 11 вЂ” Phase C: РµРґРёРЅС‹Р№ СЂРµР·РѕР»РІРµСЂ РёСЃС‚РѕС‡РЅРёРєР° СЃРєР°С‡РёРІР°РЅРёСЏ (25.10.2026)

### РџСЂРёС‡РёРЅР°
- SoundCloud РѕС‚РґР°С‘С‚ С‚РѕР»СЊРєРѕ HLS, РёР· РєРѕС‚РѕСЂРѕРіРѕ РЅРµР»СЊР·СЏ СЃРѕР±СЂР°С‚СЊ РѕРґРёРЅ РѕС„Р»Р°Р№РЅ-С„Р°Р№Р». Р Р°РЅСЊС€Рµ С‚Р°РєРёРµ С‚СЂРµРєРё Р»РёР±Рѕ РЅРµ РєР°С‡Р°Р»РёСЃСЊ, Р»РёР±Рѕ С‚СЂРµР±РѕРІР°Р»Рё СЂСѓС‡РЅРѕРіРѕ РїРѕРёСЃРєР° РЅР° YouTube.
- РљРЅРѕРїРєР° СЃРєР°С‡РёРІР°РЅРёСЏ Р±С‹Р»Р° С‚РѕР»СЊРєРѕ РІ РїР»РµРµСЂРµ; РЅР° РіР»Р°РІРЅРѕР№ Рё РІ РјРµРґРёР°С‚РµРєРµ РµС‘ РЅРµ Р±С‹Р»Рѕ.

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **РљРѕРЅС‚СЂР°РєС‚ РёСЃС‚РѕС‡РЅРёРєРѕРІ**:
   - `DownloadCapability` вЂ” `PROGRESSIVE`, `HLS_ONLY`, `NONE`; РєР°Р¶РґС‹Р№ СЂРµРїРѕР·РёС‚РѕСЂРёР№ РѕС‚РґР°С‘С‚ `downloadCapability`.
   - `DownloadSource` вЂ” РѕР±С‰РёР№ РёРЅС‚РµСЂС„РµР№СЃ `source` / `capability` / `search()` / `resolve()`.
   - `YouTubeDownloadSource` (`PROGRESSIVE`) Рё `SoundCloudDownloadSource` (`HLS_ONLY`) СЃРѕР±СЂР°РЅС‹ С‡РµСЂРµР· Hilt `@IntoSet` РІ `DownloadSourceModule`.
2. **`DownloadSourceResolver`**:
   - РЎРЅР°С‡Р°Р»Р° РїСЂРѕР±СѓРµС‚ С‚РµРєСѓС‰РёР№ РёСЃС‚РѕС‡РЅРёРє, РµСЃР»Рё РѕРЅ progressive Рё Сѓ С‚СЂРµРєР° РµСЃС‚СЊ СЃСЃС‹Р»РєР°; Р·Р°С‚РµРј РѕСЃС‚Р°Р»СЊРЅС‹Рµ progressive-РёСЃС‚РѕС‡РЅРёРєРё.
   - РЎС‚СЂРѕРіРёР№ РјР°С‚С‡: РЅРѕСЂРјР°Р»РёР·РѕРІР°РЅРЅС‹Р№ Р°СЂС‚РёСЃС‚ РґРѕР»Р¶РµРЅ СЃРѕРІРїР°СЃС‚СЊ С†РµР»РёРєРѕРј, РЅР°Р·РІР°РЅРёРµ вЂ” С‚РѕС‡РЅРѕ, СЃ РґРѕРїСѓСЃРєРѕРј С‚РѕР»СЊРєРѕ РЅР° `feat.`, `remaster`, `official audio/video`, `lyrics`, `visualizer`, `explicit`, `clean` Рё С‚РѕС‡РЅС‹Р№ РїСЂРµС„РёРєСЃ `РђСЂС‚РёСЃС‚ вЂ” РќР°Р·РІР°РЅРёРµ` (YouTube-С„РѕСЂРјР°С‚). РџРѕРґСЃС‚СЂРѕРєРё Р·Р°РїСЂРµС‰РµРЅС‹.
   - `RecentlyFailedSources` вЂ” TTL 30 РјРёРЅСѓС‚ РЅР° РїР°СЂСѓ В«С‚СЂРµРє + РёСЃС‚РѕС‡РЅРёРєВ»; `Mutex` РЅР° `trackId` РЅРµ РґР°С‘С‚ Р·Р°РїСѓСЃРєР°С‚СЊ РїР°СЂР°Р»Р»РµР»СЊРЅС‹Р№ РїРѕРёСЃРє РїСЂРё Р±С‹СЃС‚СЂС‹С… РїРѕРІС‚РѕСЂРЅС‹С… С‚Р°РїР°С….
   - Р’РѕР·РІСЂР°С‰Р°РµС‚ `ResolvedDownload`: РёСЃС…РѕРґРЅС‹Рµ РјРµС‚Р°РґР°РЅРЅС‹Рµ С‚СЂРµРєР° + С„Р°РєС‚РёС‡РµСЃРєРёР№ progressive-URL, MIME Рё РёСЃС‚РѕС‡РЅРёРє.
3. **`DownloadManager`**:
   - `download()` СЃС‚Р°Р» `suspend`: СЂРµР·РѕР»РІРµСЂ РІС‹Р·С‹РІР°РµС‚СЃСЏ РґРѕ РїРѕСЃС‚Р°РЅРѕРІРєРё `DownloadTrackWorker`; РІ Р·Р°РґР°С‡Сѓ РїРµСЂРµРґР°СЋС‚СЃСЏ `KEY_DIRECT_STREAM_URL`, `KEY_DIRECT_MIME_TYPE`, `KEY_RESOLVED_SOURCE`.
   - РћС€РёР±РєР° СЂРµР·РѕР»РІР° РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ РІ UI Р±РµР· СЃРѕР·РґР°РЅРёСЏ РІРѕСЂРєРµСЂР°; Р°РєС‚РёРІРЅС‹Р№ СЂРµР·РѕР»РІ РѕС‚РјРµРЅСЏРµС‚СЃСЏ РІРјРµСЃС‚Рµ СЃ Р·Р°РґР°С‡РµР№.
   - `observeStates(ids)` РѕС‚РґР°С‘С‚ СЃРѕСЃС‚РѕСЏРЅРёСЏ СЃСЂР°Р·Сѓ РґР»СЏ СЃРїРёСЃРєР° РєР°СЂС‚РѕС‡РµРє РѕРґРЅРёРј Р·Р°РїСЂРѕСЃРѕРј WorkManager РїРѕ С‚РµРіСѓ `download-track:<id>`.
4. **Worker**:
   - РЎРєР°С‡РёРІР°РµС‚ РіРѕС‚РѕРІС‹Р№ РїСЂСЏРјРѕР№ progressive-URL; СЃС‚Р°СЂС‹Р№ РїСѓС‚СЊ `resolveStream` РѕСЃС‚Р°РІР»РµРЅ РґР»СЏ Р·Р°РґР°С‡, РїРѕСЃС‚Р°РІР»РµРЅРЅС‹С… РґРѕ РѕР±РЅРѕРІР»РµРЅРёСЏ Рё РґР»СЏ РїРѕРІС‚РѕСЂРЅРѕРіРѕ Р·Р°РїСѓСЃРєР°.
   - Р’ Room СЃРѕС…СЂР°РЅСЏСЋС‚СЃСЏ РёСЃС…РѕРґРЅС‹Рµ `Track.id`/`source`, Р° С„Р°Р№Р» Рё MIME Р±РµСЂСѓС‚СЃСЏ СЃ С„Р°РєС‚РёС‡РµСЃРєРѕРіРѕ РёСЃС‚РѕС‡РЅРёРєР°.
5. **UI**:
   - РћР±С‰РёР№ `TrackDownloadButton` РґРѕР±Р°РІР»РµРЅ РІ hero-РєР°СЂС‚РѕС‡РєСѓ, В«Р”Р»СЏ РІР°СЃВ», В«РќРµРґР°РІРЅРµРµВ», В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏВ» Рё РјРµРґРёР°С‚РµРєСѓ; РІ РїР»РµРµСЂРµ РєРЅРѕРїРєР° СѓР¶Рµ Р±С‹Р»Р°.
   - РџСЂРё СѓСЃРїРµС…Рµ вЂ” Р±РµР№РґР¶ РїСЂРѕРіСЂРµСЃСЃР°/РіР°Р»РѕС‡РєР°, РїСЂРё РЅРµСѓРґР°С‡Рµ вЂ” Snackbar В«РќРµ СѓРґР°Р»РѕСЃСЊ РЅР°Р№С‚Рё РёСЃС‚РѕС‡РЅРёРєВ» (Player, Home, Library). Р”РёР°Р»РѕРіРѕРІ РІС‹Р±РѕСЂР° РёСЃС‚РѕС‡РЅРёРєР° РЅРµС‚.

### РџСЂРѕРІРµСЂРєР°
- `.\gradlew testDebugUnitTest assembleDebug` вЂ” `BUILD SUCCESSFUL`, 109 JVM-С‚РµСЃС‚РѕРІ, 0 failures, 0 errors.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 27 406 949 Р±Р°Р№С‚ (~27,4 РњР‘).
- `DownloadSourceResolverTest` вЂ” 10 С‚РµСЃС‚РѕРІ: SC РѕС‚СЃРµРёРІР°РµС‚СЃСЏ РєР°Рє HLS, YT РІС‹Р±РёСЂР°РµС‚СЃСЏ, Р»РѕР¶РЅРѕРµ РЅР°Р·РІР°РЅРёРµ РѕС‚СЃРµРёРІР°РµС‚СЃСЏ, `recently_failed` Р±Р»РѕРєРёСЂСѓРµС‚ РїРѕРІС‚РѕСЂ, РѕС‚СЃСѓС‚СЃС‚РІРёРµ РёСЃС‚РѕС‡РЅРёРєРѕРІ РґР°С‘С‚ РѕС€РёР±РєСѓ, YouTube-РїСЂРµС„РёРєСЃ Р°СЂС‚РёСЃС‚Р° РґРѕРїСѓСЃРєР°РµС‚СЃСЏ.
- Р–РёРІР°СЏ РїСЂРѕРІРµСЂРєР° РјР°С‚С‡Р° РЅР° СЂРµР°Р»СЊРЅРѕР№ РІС‹РґР°С‡Рµ YouTube: РґР»СЏ `Midnight City вЂ” M83` СЃРѕРІРїР°Р» С‚РѕР»СЊРєРѕ РѕС„РёС†РёР°Р»СЊРЅС‹Р№ СЂРѕР»РёРє РєР°РЅР°Р»Р° M83 (`match=true`), РѕСЃС‚Р°Р»СЊРЅС‹Рµ РєР°РЅРґРёРґР°С‚С‹ РѕС‚СЃРµСЏРЅС‹. РџСЂСЏРјРѕР№ `resolveStreamUrl` СЃ СЌС‚РѕР№ РјР°С€РёРЅС‹ РІРµСЂРЅСѓР» `LOGIN_REQUIRED: Please sign in` вЂ” bot-Р·Р°С‰РёС‚Р° YouTube, РЅРµ РѕС€РёР±РєР° СЂРµР·РѕР»РІРµСЂР°; РЅР° РјРѕР±РёР»СЊРЅРѕРј IP РїСѓС‚СЊ РѕСЃС‚Р°С‘С‚СЃСЏ СЂР°Р±РѕС‡РёРј. Р’СЂРµРјРµРЅРЅС‹Р№ live-С‚РµСЃС‚ СѓРґР°Р»С‘РЅ.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/core/download/DownloadCapability.kt
app/src/main/java/com/facet/music/core/download/DownloadSource.kt
app/src/main/java/com/facet/music/core/download/DownloadSourceResolver.kt
app/src/main/java/com/facet/music/core/download/RecentlyFailedSources.kt
app/src/main/java/com/facet/music/core/download/YouTubeDownloadSource.kt
app/src/main/java/com/facet/music/core/download/SoundCloudDownloadSource.kt
app/src/main/java/com/facet/music/core/download/DownloadMessages.kt
app/src/main/java/com/facet/music/core/download/DownloadManager.kt
app/src/main/java/com/facet/music/core/download/DownloadTrackWorker.kt
app/src/main/java/com/facet/music/core/di/DownloadSourceModule.kt
app/src/main/java/com/facet/music/core/ui/components/TrackDownloadButton.kt
app/src/main/java/com/facet/music/data/repository/SoundCloudRepository.kt
app/src/main/java/com/facet/music/data/repository/YouTubeRepository.kt
app/src/main/java/com/facet/music/data/source/soundcloud/SoundCloudRepositoryImpl.kt
app/src/main/java/com/facet/music/data/source/youtube/YouTubeRepositoryImpl.kt
app/src/main/java/com/facet/music/feature/home/HomeScreen.kt
app/src/main/java/com/facet/music/feature/home/HomeViewModel.kt
app/src/main/java/com/facet/music/feature/library/LibraryScreen.kt
app/src/main/java/com/facet/music/feature/library/LibraryViewModel.kt
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
app/src/main/java/com/facet/music/feature/player/PlayerViewModel.kt
app/src/test/java/com/facet/music/core/download/DownloadSourceResolverTest.kt
app/src/test/java/com/facet/music/feature/home/HomeViewModelGenreTest.kt
app/src/test/java/com/facet/music/feature/home/HomeViewModelRecommendationsTest.kt
PROGRESS.md
MEMORY.md
```

### РЎР»РµРґСѓСЋС‰РёР№ С€Р°Рі
- Phase A: СЂРµРґРёР·Р°Р№РЅ СЌРєСЂР°РЅР° СЃРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°РЅРЅРѕРіРѕ С‚РµРєСЃС‚Р° (РѕРїРёСЃР°С‚СЊ С‚РµРєСѓС‰РµРµ СЃРѕСЃС‚РѕСЏРЅРёРµ Рё РґРѕР¶РґР°С‚СЊСЃСЏ РєРѕРјР°РЅРґС‹ В«РџСЂРѕРґРѕР»Р¶Р°Р№ Phase AВ»).

---

## вњ… РЎРµСЃСЃРёСЏ 11.1 вЂ” Р§РёСЃС‚РєР° РєРЅРѕРїРѕРє СЃРєР°С‡РёРІР°РЅРёСЏ РЅР° РіР»Р°РІРЅРѕР№ (25.10.2026)

### РџСЂРёС‡РёРЅР°
- Р’ РєР°СЂС‚РѕС‡РєР°С… РіР»Р°РІРЅРѕР№ РєРЅРѕРїРєР° СЃРєР°С‡РёРІР°РЅРёСЏ РІСЃРµРіРґР° РїР°РґР°Р»Р° СЃ РѕС€РёР±РєРѕР№ В«РќРµ СѓРґР°Р»РѕСЃСЊ РЅР°Р№С‚Рё РёСЃС‚РѕС‡РЅРёРєВ»: Р»РµРЅС‚Р° РЅРµ РїРµСЂРµРґР°С‘С‚ РїРѕР»РЅС‹Р№ РєРѕРЅС‚РµРєСЃС‚ С‚СЂРµРєР°, РЅСѓР¶РЅС‹Р№ СЂРµР·РѕР»РІРµСЂСѓ.
- РЎРєР°С‡РёРІР°РЅРёРµ РѕСЃС‚Р°С‘С‚СЃСЏ РІ РїР»РµРµСЂРµ Рё РјРµРґРёР°С‚РµРєРµ, РіРґРµ РєРѕРЅС‚РµРєСЃС‚ РїРѕР»РЅС‹Р№.

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
- РР· `HomeScreen` РїРѕР»РЅРѕСЃС‚СЊСЋ СѓР±СЂР°РЅС‹ `TrackDownloadButton`, СЃРѕСЃС‚РѕСЏРЅРёСЏ СЃРєР°С‡РёРІР°РЅРёСЏ Рё Snackbar РѕС€РёР±РєРё: hero-РєР°СЂС‚РѕС‡РєР°, В«Р”Р»СЏ РІР°СЃВ», В«РќРµРґР°РІРЅРµРµВ», В«Р’РѕСЃРїРѕРјРёРЅР°РЅРёСЏВ».
- РЈРґР°Р»С‘РЅ Р»РёС€РЅРёР№ `Box`-РѕР±С‘СЂС‚РєР° Рё `SnackbarHost`; `PullToRefreshBox` СЃРЅРѕРІР° РєРѕСЂРЅРµРІРѕР№ РєРѕРЅС‚РµР№РЅРµСЂ СЌРєСЂР°РЅР°.
- РР· `HomeViewModel` СѓРґР°Р»РµРЅС‹ `DownloadManager`, `downloadStates`, `downloadError`, `downloadTrack()` Рё `consumeDownloadError()`.
- РћС‚СЃС‚СѓРїС‹ СЃРѕС…СЂР°РЅРµРЅС‹: РІ hero РѕСЃС‚Р°Р»СЃСЏ `Spacer(8.dp)` РїРµСЂРµРґ `PlayBadge`, РІ РѕСЃС‚Р°Р»СЊРЅС‹С… РєР°СЂС‚РѕС‡РєР°С… РєРЅРѕРїРєРё Р±С‹Р»Рё РѕРІРµСЂР»РµСЏРјРё Рё РЅРµ РІР»РёСЏР»Рё РЅР° Р»РµР№Р°СѓС‚.
- РўРµСЃС‚С‹ `HomeViewModelGenreTest` Рё `HomeViewModelRecommendationsTest` РѕС‡РёС‰РµРЅС‹ РѕС‚ РјРѕРєРѕРІ `DownloadManager`.

### РџСЂРѕРІРµСЂРєР°
- `.\gradlew testDebugUnitTest assembleDebug` вЂ” `BUILD SUCCESSFUL`, 109 JVM-С‚РµСЃС‚РѕРІ, 0 failures, 0 errors.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 27 406 949 Р±Р°Р№С‚ (~27,4 РњР‘).
- `DownloadSourceResolver`, `DownloadManager`, `DownloadTrackWorker`, РїР»РµРµСЂ Рё РјРµРґРёР°С‚РµРєР° РЅРµ РјРµРЅСЏР»РёСЃСЊ.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/home/HomeScreen.kt
app/src/main/java/com/facet/music/feature/home/HomeViewModel.kt
app/src/test/java/com/facet/music/feature/home/HomeViewModelGenreTest.kt
app/src/test/java/com/facet/music/feature/home/HomeViewModelRecommendationsTest.kt
PROGRESS.md
MEMORY.md
```

### РЎР»РµРґСѓСЋС‰РёР№ С€Р°Рі
- Phase A: Р°СѓРґРёС‚ С‚РµРєСѓС‰РµРіРѕ СЌРєСЂР°РЅР° С‚РµРєСЃС‚Р° Рё СЂРµРґРёР·Р°Р№РЅ РїРѕСЃР»Рµ РєРѕРјР°РЅРґС‹ В«РџСЂРѕРґРѕР»Р¶Р°Р№ Phase AВ».

---

## вњ… РЎРµСЃСЃРёСЏ 12 вЂ” Phase A: РїРѕР»РёСЂРѕРІРєР° СЂРµР¶РёРјР° СЃРёРЅС…СЂРѕРЅРЅРѕРіРѕ С‚РµРєСЃС‚Р° (26.10.2026)

### РџСЂРёС‡РёРЅР°
- РўРµРєСЃС‚ РІ СЂРµР¶РёРјРµ В«РўРµРєСЃС‚В» Р¶РёР» РїРѕРІРµСЂС… РїС‘СЃС‚СЂРѕРіРѕ С„РѕРЅР°: СЂР°РґРёР°Р»СЊРЅРѕРµ СЃРІРµС‡РµРЅРёРµ РїР°Р»РёС‚СЂС‹ Рё СЂР°Р·РјС‹С‚Р°СЏ РѕР±Р»РѕР¶РєР° РґР°РІР°Р»Рё С†РІРµС‚РЅС‹Рµ РїСЏС‚РЅР°, РёР·-Р·Р° РєРѕС‚РѕСЂС‹С… СЃС‚СЂРѕРєРё С‡РёС‚Р°Р»РёСЃСЊ РЅРµСЂРѕРІРЅРѕ.
- РРµСЂР°СЂС…РёСЏ СЃС‚СЂРѕРє Р±С‹Р»Р° СЃР»Р°Р±РѕР№: РІСЃРµ СЃС‚СЂРѕРєРё 24sp, СЂР°Р·РЅРёС†Р° С‚РѕР»СЊРєРѕ РІ Р°Р»СЊС„Рµ; Р°РєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР° РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅРѕ РјР°СЃС€С‚Р°Р±РёСЂРѕРІР°Р»Р°СЃСЊ `graphicsLayer`, РёР·-Р·Р° С‡РµРіРѕ РІС‹Р»РµР·Р°Р»Р° Р·Р° РєСЂР°Р№.
- Р”Р°Р»СЊРЅРёРµ СЃС‚СЂРѕРєРё СЂР°Р·РјС‹РІР°Р»РёСЃСЊ `blur` вЂ” РЅР° СЃР»Р°Р±РѕРј Р¶РµР»РµР·Рµ СЌС‚Рѕ РґРѕСЂРѕРіРѕ Рё РґР°С‘С‚ В«РіСЂСЏР·РЅС‹РµВ» РєСЂР°СЏ.

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Р¤РѕРЅ СЂРµР¶РёРјР° С‚РµРєСЃС‚Р°**:
   - Р Р°РґРёР°Р»СЊРЅРѕРµ СЃРІРµС‡РµРЅРёРµ РїР°Р»РёС‚СЂС‹ РїРѕРґ РѕР±Р»РѕР¶РєРѕР№ РіР°СЃРЅРµС‚ РІРјРµСЃС‚Рµ СЃ РѕР±Р»РѕР¶РєРѕР№ (`alpha = 1f - recede`), РІ СЂРµР¶РёРјРµ С‚РµРєСЃС‚Р° РµРіРѕ РЅРµС‚.
   - РџРѕРІРµСЂС… СЂР°Р·РјС‹С‚РѕР№ РѕР±Р»РѕР¶РєРё РґРѕР±Р°РІР»РµРЅ С„РёРєСЃРёСЂРѕРІР°РЅРЅС‹Р№ scrim `surface` alpha `0.65 * recede` вЂ” С„РѕРЅ С‚С‘РјРЅС‹Р№ Рё СЃРїРѕРєРѕР№РЅС‹Р№ РЅР° Р»СЋР±РѕР№ РѕР±Р»РѕР¶РєРµ.
   - Р’РµСЂС…РЅСЏСЏ РіСЂР°РґРёРµРЅС‚РЅР°СЏ РјР°СЃРєР° СЃРїРёСЃРєР° СѓРІРµР»РёС‡РµРЅР° СЃ 44dp РґРѕ 60dp, РЅРёР¶РЅСЏСЏ (124dp) СЃРѕС…СЂР°РЅРµРЅР°.
2. **РРµСЂР°СЂС…РёСЏ СЃС‚СЂРѕРє** (`LyricLineItem`):
   - РђРєС‚РёРІРЅР°СЏ вЂ” 24sp Bold, `highlightColor` С‡РµСЂРµР· РєР°СЂР°РѕРєРµ-РєРёСЃС‚СЊ, СЃРІРµС‡РµРЅРёРµ `Shadow(alpha = 0.38f, blurRadius = 18f)`.
   - РЎРѕСЃРµРґРЅРёРµ (NВ±1) вЂ” 18sp Medium, `onSurfaceVariant` alpha 0.55.
   - Р”Р°Р»СЊРЅРёРµ (NВ±2 Рё Р±РѕР»СЊС€Рµ) вЂ” 16sp Normal alpha 0.35.
   - РЈР±СЂР°РЅ `blur` Сѓ РґР°Р»СЊРЅРёС… СЃС‚СЂРѕРє Рё `graphicsLayer`-РјР°СЃС€С‚Р°Р± Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё (`TransformOrigin` Р±РѕР»СЊС€Рµ РЅРµ РЅСѓР¶РµРЅ).
   - РќРµСЃРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°РЅРЅС‹Р№ С‚РµРєСЃС‚ вЂ” 24sp Normal alpha 0.82 Р±РµР· РєР°СЂР°РѕРєРµ Рё Р±РµР· СЃРІРµС‡РµРЅРёСЏ.
3. **РљР°СЂР°РѕРєРµ-Р·Р°Р»РёРІРєР°**:
   - `buildLyricsBrush` РїРµСЂРµРІРµРґС‘РЅ СЃ РґРёР°РіРѕРЅР°Р»СЊРЅРѕРіРѕ `Brush.linearGradient` РЅР° `Brush.horizontalGradient`, РєСЂР°Р№РЅРёРµ СЃР»СѓС‡Р°Рё вЂ” `SolidColor`.
   - Р—Р°РјРµСЂС‹ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ: Р°РєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР° 249 РїРѕ РІСЃРµР№ С€РёСЂРёРЅРµ РїСЂРѕРїРµС‚РѕР№ С‡Р°СЃС‚Рё Рё ~229вЂ“234 РІ РЅРµРїСЂРѕРїРµС‚РѕР№, РїРµСЂРµРЅРµСЃС‘РЅРЅР°СЏ СЃС‚СЂРѕРєР° (В«twilightВ») 219вЂ“236 вЂ” Р·Р°Р»РёРІРєР° СЂР°Р±РѕС‚Р°РµС‚ РЅР° РѕР±РµРёС… РІРёР·СѓР°Р»СЊРЅС‹С… СЃС‚СЂРѕРєР°С….
4. **Р“РµРѕРјРµС‚СЂРёСЏ СЃРїРёСЃРєР°**:
   - `centerLiftPx = 24.dp`: Р°РєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР° СЃС‚РѕРёС‚ РІС‹С€Рµ РіРµРѕРјРµС‚СЂРёС‡РµСЃРєРѕРіРѕ С†РµРЅС‚СЂР°, РїРѕСЌС‚РѕРјСѓ РїРµСЂРµРЅРµСЃС‘РЅРЅР°СЏ РІС‚РѕСЂР°СЏ СЃС‚СЂРѕРєР° РЅРµ РїРѕРїР°РґР°РµС‚ РїРѕРґ РЅРёР¶РЅСЋСЋ РјР°СЃРєСѓ.
   - РџРµСЂРІРѕРµ РїРѕР·РёС†РёРѕРЅРёСЂРѕРІР°РЅРёРµ вЂ” `scrollToItem`, РґР°Р»СЊС€Рµ `animateScrollToItem`; РѕРїСЂРѕСЃ РїРѕР·РёС†РёРё 150 РјСЃ; С‚Р°Рї РїРѕ СЃС‚СЂРѕРєРµ = `seekTo(line.timeMs)`.
   - РџРёР»СЋР»СЏ В«РўРµРєСЃС‚В» РѕСЃС‚Р°Р»Р°СЃСЊ РїРµСЂРµРєР»СЋС‡Р°С‚РµР»РµРј, РѕС‚РґРµР»СЊРЅРѕРіРѕ route/BottomSheet Рё РєРЅРѕРїРєРё Р·Р°РєСЂС‹С‚РёСЏ РЅРµС‚.

### Р”РёР°РіРЅРѕСЃС‚РёРєР° (РїРѕС‡РµРјСѓ РІС‚РѕСЂР°СЏ СЃС‚СЂРѕРєР° РІС‹РіР»СЏРґРµР»Р° СЃРµСЂРѕР№)
- Р“РёРїРѕС‚РµР·Р° Рѕ В«РєРёСЃС‚СЊ РЅРµ Р·Р°Р»РёРІР°РµС‚ РІС‚РѕСЂСѓСЋ СЃС‚СЂРѕРєСѓВ» РЅРµ РїРѕРґС‚РІРµСЂРґРёР»Р°СЃСЊ: РґРІСѓС…СЃР»РѕР№РЅС‹Р№ СЂРµРЅРґРµСЂ СЃ `DstIn`-РјР°СЃРєРѕР№ Рё `CompositingStrategy.Offscreen` РґР°Р» С‚РѕС‚ Р¶Рµ СЂРµР·СѓР»СЊС‚Р°С‚ Рё Р±С‹Р» РѕС‚РєР°С‡РµРЅ.
- РљСЂР°СЃРЅС‹Р№ РґРёР°РіРЅРѕСЃС‚РёС‡РµСЃРєРёР№ С‚РµСЃС‚ РїРѕРєР°Р·Р°Р», С‡С‚Рѕ РІС‚РѕСЂР°СЏ РІРёР·СѓР°Р»СЊРЅР°СЏ СЃС‚СЂРѕРєР° С‚РµРјРЅРµРµ РїРµСЂРІРѕР№ РЅРµ РёР·-Р·Р° `alpha` Рё РЅРµ РёР·-Р·Р° СЂР°Р·Р±РёРµРЅРёСЏ С‚РµРєСЃС‚Р°, Р° РїРѕС‚РѕРјСѓ С‡С‚Рѕ РїРѕРїР°РґР°РµС‚ РїРѕРґ РЅРёР¶РЅСЋСЋ РіСЂР°РґРёРµРЅС‚РЅСѓСЋ РјР°СЃРєСѓ 124dp.
- РСЃРїСЂР°РІР»РµРЅРёРµ вЂ” РїРѕРґСЉС‘Рј Р°РєС‚РёРІРЅРѕР№ СЃС‚СЂРѕРєРё РЅР° 24dp РїСЂРё СЃРѕС…СЂР°РЅРµРЅРёРё РјР°СЃРєРё.

### РџСЂРѕРІРµСЂРєР°
- `.\gradlew testDebugUnitTest assembleDebug` вЂ” `BUILD SUCCESSFUL`, 109 JVM-С‚РµСЃС‚РѕРІ, 0 failures, 0 skipped.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 27 406 949 Р±Р°Р№С‚ (~27,4 РњР‘); С…СЌС€ СѓСЃС‚Р°РЅРѕРІР»РµРЅРЅРѕРіРѕ APK СЃРѕРІРїР°Р» СЃРѕ СЃР±РѕСЂРѕС‡РЅС‹Рј.
- Live-С‚РµСЃС‚ РЅР° SM-A266B:
  - M83 вЂ” Midnight City: РёРµСЂР°СЂС…РёСЏ 24/18/16 С‡РёС‚Р°РµС‚СЃСЏ, С„РѕРЅ С‚С‘РјРЅС‹Р№, Р°РІС‚РѕСЃРєСЂРѕР»Р» Рё С‚Р°Рї-РїРµСЂРµРјРѕС‚РєР° СЂР°Р±РѕС‚Р°СЋС‚, РїРµСЂРµРЅРµСЃС‘РЅРЅР°СЏ СЃС‚СЂРѕРєР° Р·Р°Р»РёРІР°РµС‚СЃСЏ.
  - The Beatles вЂ” Here Comes The Sun (СЃРІРµС‚Р»Р°СЏ РѕР±Р»РѕР¶РєР°): С„РѕРЅ РѕСЃС‚Р°С‘С‚СЃСЏ С‚С‘РјРЅС‹Рј, Р°РєС‚РёРІРЅР°СЏ СЃС‚СЂРѕРєР° Р±РµР»Р°СЏ СЃРѕ СЃРІРµС‡РµРЅРёРµРј, СЃРѕСЃРµРґРЅРёРµ Рё РґР°Р»СЊРЅРёРµ РїСЂРёРіР»СѓС€РµРЅС‹.
  - Hans Zimmer вЂ” Time: СЃРѕСЃС‚РѕСЏРЅРёРµ В«РРЅСЃС‚СЂСѓРјРµРЅС‚Р°Р»СЊРЅС‹Р№ С‚СЂРµРєВ» Р±РµР· РёР·РјРµРЅРµРЅРёР№.
  - Moby вЂ” Porcelain (Above & Beyond Remix): РЅРµСЃРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°РЅРЅС‹Р№ С‚РµРєСЃС‚ 24sp Normal Р±РµР· РєР°СЂР°РѕРєРµ.
  - РўСЂРµРє Р±РµР· С‚РµРєСЃС‚Р°: СЃРѕСЃС‚РѕСЏРЅРёРµ РѕС€РёР±РєРё Рё РєРЅРѕРїРєР° В«РџРѕРІС‚РѕСЂРёС‚СЊ РїРѕРёСЃРє С‚РµРєСЃС‚Р°В» Р±РµР· РёР·РјРµРЅРµРЅРёР№.
- РќР°РїРѕРјРёРЅР°РЅРёРµ: РїРѕСЃР»Рµ `adb install` РѕР±СЏР·Р°С‚РµР»РµРЅ `am force-stop`, РёРЅР°С‡Рµ РЅР° СЌРєСЂР°РЅРµ РѕСЃС‚Р°С‘С‚СЃСЏ СЃС‚Р°СЂС‹Р№ РєРѕРґ.

### Р¤Р°Р№Р»С‹
```
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

### РЎР»РµРґСѓСЋС‰РёР№ С€Р°Рі
- Phase B вЂ” С‚РѕР»СЊРєРѕ РїРѕСЃР»Рµ РєРѕРјР°РЅРґС‹ В«РџСЂРѕРґРѕР»Р¶Р°Р№ Phase BВ». Р”Рѕ РЅРµС‘ РёР·РјРµРЅРµРЅРёСЏ РЅРµ РЅР°С‡РёРЅР°С‚СЊ.

---

## вњ… РЎРµСЃСЃРёСЏ 13 вЂ” Phase A fix + Phase B: С„РѕРЅ С‚РµРєСЃС‚Р°, СЃС‚Р°Р±РёР»СЊРЅС‹Р№ С‚Р°Р№РјР»Р°Р№РЅ, СЃРёРЅСѓСЃРѕРёРґР°-РїСѓР»СЊСЃ (26.10.2026)

### РџСЂРёС‡РёРЅР°
- Р’ СЂРµР¶РёРјРµ В«РўРµРєСЃС‚В» СЂР°Р·РјС‹С‚Р°СЏ РѕР±Р»РѕР¶РєР° РјР°СЃС€С‚Р°Р±РёСЂРѕРІР°Р»Р°СЃСЊ РґРѕ 0.58 Рё РІРёСЃРµР»Р° РїРѕ С†РµРЅС‚СЂСѓ вЂ” РЅР° СЃРІРµС‚Р»РѕР№ РѕР±Р»РѕР¶РєРµ Р±С‹Р»Рё РІРёРґРЅС‹ РїСЂСЏРјРѕСѓРіРѕР»СЊРЅС‹Рµ РєСЂР°СЏ В«СЃРµСЂРѕРіРѕ РєРёСЂРїРёС‡Р°В».
- РњРѕСЂС„ `LinearProgressIndicator` в†” `Slider` СЃСЂС‹РІР°Р»СЃСЏ РЅР° РїР°СѓР·Рµ, Р° С„Р°Р·Р° РІРѕР»РЅС‹ СЃР»Р°Р№РґРµСЂР° РїСЂРё РѕСЃС‚Р°РЅРѕРІРєРµ `rememberInfiniteTransition` СЃРєР°С‡РєРѕРј РІРѕР·РІСЂР°С‰Р°Р»Р°СЃСЊ РІ 0 вЂ” РїРѕР»Р·СѓРЅРѕРє РґС‘СЂРіР°Р»СЃСЏ.
- Р—Р°РїСЂРѕС€РµРЅР° РјСЏРіРєР°СЏ РїСѓР»СЊСЃР°С†РёСЏ СЃРёРЅСѓСЃРѕРёРґС‹ РЅР°Рґ СЃР»Р°Р№РґРµСЂРѕРј РѕС‚ СЂРµР°Р»СЊРЅРѕРіРѕ Р±Р°СЃР°.

### Р§С‚Рѕ СЃРґРµР»Р°РЅРѕ
1. **Р¤РѕРЅ С‚РµРєСЃС‚Р°** (`PlayerScreen`):
   - РџРѕР»РЅРѕСЌРєСЂР°РЅРЅС‹Р№ СЂР°Р·РјС‹С‚С‹Р№ СЃР»РѕР№: `matchParentSize()` + `graphicsLayer { scale 1.25 }` + `blur(44.dp, Unbounded)`; РїСЂСЏРјРѕСѓРіРѕР»СЊРЅС‹Рµ РєСЂР°СЏ СѓС…РѕРґСЏС‚ Р·Р° СЌРєСЂР°РЅ.
   - Hero-РѕР±Р»РѕР¶РєР° РІ С‚РµРєСЃС‚Р°С… РіР°СЃРЅРµС‚ РїРѕР»РЅРѕСЃС‚СЊСЋ (`alpha = lerp(1f, 0f, recede)`), scrim `surface` alpha `0.65 * lyricsBackdrop` РїРµСЂРµРЅРµСЃС‘РЅ РёР· hero-Р·РѕРЅС‹ РІ РєРѕСЂРµРЅСЊ СЌРєСЂР°РЅР°.
   - Р’РµСЂС…РЅСЏСЏ/РЅРёР¶РЅСЏСЏ РјР°СЃРєРё СЃРїРёСЃРєР° Р·Р°РјРµРЅРµРЅС‹ РЅР° `DstIn`-Р·Р°С‚СѓС…Р°РЅРёРµ РєРѕРЅС‚РµРЅС‚Р° (`CompositingStrategy.Offscreen`): РјР°СЃРєР° СЂРµР¶РµС‚ РїСЂРѕР·СЂР°С‡РЅРѕСЃС‚СЊ СЃС‚СЂРѕРє, Р° РЅРµ Р·Р°Р»РёРІР°РµС‚ С„РѕРЅ С†РІРµС‚РѕРј surface вЂ” РЅР° СЃРІРµС‚Р»РѕР№ РѕР±Р»РѕР¶РєРµ Р±РѕР»СЊС€Рµ РЅРµС‚ С‚С‘РјРЅРѕРіРѕ РїСЂСЏРјРѕСѓРіРѕР»СЊРЅРёРєР°.
2. **РўР°Р№РјР»Р°Р№РЅ**:
   - `ExpressivePlayerSlider` РїРµСЂРµРІРµРґС‘РЅ РЅР° `rememberPlaybackPhase(active = isPlaying, periodMs = 1600)` (`withFrameNanos`): РЅР° РїР°СѓР·Рµ С„Р°Р·Р° Р·Р°РјРёСЂР°РµС‚, Р° РЅРµ СЃР±СЂР°СЃС‹РІР°РµС‚СЃСЏ; Р°РјРїР»РёС‚СѓРґР° РїР»Р°РІРЅРѕ СѓС…РѕРґРёС‚ РІ Р»РёРЅРёСЋ Р·Р° 500 РјСЃ.
   - РњРѕСЂС„ РїСЂРѕРіСЂРµСЃСЃР° Р·Р°С‰С‘Р»РєРЅСѓС‚ С‡РµСЂРµР· `hasStarted` РЅР° `trackId`: `LinearProgressIndicator` РїРѕРєР°Р·С‹РІР°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РЅР° СЃС‚Р°СЂС‚Рµ С‚СЂРµРєР° Рё РЅРµ РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ РЅР° РїР°СѓР·Рµ/Р±СѓС„РµСЂРёР·Р°С†РёРё.
3. **РЎРёРЅСѓСЃРѕРёРґР°-РїСѓР»СЊСЃ** (`SinePulseBar`):
   - РџРѕР»РѕСЃР° 16dp РЅР°Рґ СЃР»Р°Р№РґРµСЂРѕРј, stroke 1.5dp, `primary` alpha 0.5, РєСЂР°СЏ СЃС…РѕРґСЏС‚СЃСЏ Рє Р»РёРЅРёРё; hitbox СЃР»Р°Р№РґРµСЂР° РЅРµ РёР·РјРµРЅС‘РЅ.
   - `BassSampler`: `Visualizer` РЅР° Р°СѓРґРёРѕСЃРµСЃСЃРёРё ExoPlayer, FFT-Р±РёРЅС‹ 1вЂ“11, СЃСЂРµРґРЅСЏСЏ Р°РјРїР»РёС‚СѓРґР° /96, Р°СЃРёРјРјРµС‚СЂРёС‡РЅС‹Р№ lerp (Р°С‚Р°РєР° 0.35, СЃРїР°Рґ 0.10).
   - `PlaybackAudioSession` РїСѓР±Р»РёРєСѓРµС‚ `audioSessionId` РёР· `MusicService` РІ UI С‚РѕРіРѕ Р¶Рµ РїСЂРѕС†РµСЃСЃР°.
   - Р’ РјР°РЅРёС„РµСЃС‚ РґРѕР±Р°РІР»РµРЅ `RECORD_AUDIO`; СЂР°Р·СЂРµС€РµРЅРёРµ СЃРїСЂР°С€РёРІР°РµС‚СЃСЏ РѕРґРёРЅ СЂР°Р· Р·Р° РїСЂРѕС†РµСЃСЃ, РїСЂРё РѕС‚РєР°Р·Рµ вЂ” ambient `0.42 + 0.26*sin(1.15t) + 0.10*sin(0.53t+1.2)`.
   - РќР° РїР°СѓР·Рµ Р°РјРїР»РёС‚СѓРґР° РїР»Р°РІРЅРѕ РіР°СЃРЅРµС‚ РІ Р»РёРЅРёСЋ (220 РјСЃ), РЅР° СЃРјРµРЅРµ С‚СЂРµРєР° вЂ” gate fade out/in (120 РјСЃ + 360 РјСЃ).

### РџСЂРѕРІРµСЂРєР°
- `.\gradlew testDebugUnitTest assembleDebug` вЂ” `BUILD SUCCESSFUL`, 109 JVM-С‚РµСЃС‚РѕРІ, 0 failures, 0 skipped.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 27 417 678 Р±Р°Р№С‚.
- Live-С‚РµСЃС‚ РЅР° SM-A266B:
  - Moby вЂ” Porcelain (С‚С‘РјРЅР°СЏ Р±РёСЂСЋР·РѕРІР°СЏ РѕР±Р»РѕР¶РєР°) Рё ONDA ANDAR вЂ” type london (СЃРІРµС‚Р»Р°СЏ Р±РµР¶РµРІР°СЏ): С„РѕРЅ С‚РµРєСЃС‚РѕРІ вЂ” РјСЏРіРєРѕРµ РїРѕР»РЅРѕСЌРєСЂР°РЅРЅРѕРµ РїСЏС‚РЅРѕ Р±РµР· РїСЂСЏРјРѕСѓРіРѕР»СЊРЅС‹С… РіСЂР°РЅРёС†; РІРµСЂС…РЅРёРµ/РЅРёР¶РЅРёРµ СЃС‚СЂРѕРєРё СЂР°СЃС‚РІРѕСЂСЏСЋС‚СЃСЏ С‡РµСЂРµР· DstIn.
  - РџР°СѓР·Р°: РїРѕР»Р·СѓРЅРѕРє РЅРµ РїСЂС‹РіР°РµС‚ РїРѕ X, РІРѕР»РЅР° СЃР»Р°Р№РґРµСЂР° РїР»Р°РІРЅРѕ РІС‹РїСЂСЏРјР»СЏРµС‚СЃСЏ, СЃРёРЅСѓСЃРѕРёРґР° РіР°СЃРЅРµС‚ РІ Р»РёРЅРёСЋ; СЂРµР·СЋРј РІРѕР·РІСЂР°С‰Р°РµС‚ РІРѕР»РЅСѓ СЃ С‚РѕР№ Р¶Рµ С„Р°Р·С‹.
  - РЎРёРЅСѓСЃРѕРёРґР°: Р°РјРїР»РёС‚СѓРґР° 11вЂ“16 px РїСЂРё FFT-СЌРЅРµСЂРіРёРё, РЅР° РїР°СѓР·Рµ 0.
  - РСЃС‚РѕС‡РЅРёРє СЌРЅРµСЂРіРёРё: **Visualizer** (logcat `SinePulse: Visualizer Р°РєС‚РёРІРµРЅ, СЃРµСЃСЃРёСЏ 19153`); ambient-РІРµС‚РєР° РїСЂРѕРІРµСЂРµРЅР° РѕС‚Р·С‹РІРѕРј `RECORD_AUDIO` вЂ” logcat `РќРµС‚ RECORD_AUDIO вЂ” ambient-С„РѕР»Р±СЌРє`.
- РќР°РїРѕРјРёРЅР°РЅРёРµ: РїРѕСЃР»Рµ `adb install` РѕР±СЏР·Р°С‚РµР»РµРЅ `am force-stop`.

### Р¤Р°Р№Р»С‹
```
app/src/main/AndroidManifest.xml
app/src/main/java/com/facet/music/core/player/MusicService.kt
app/src/main/java/com/facet/music/core/player/PlaybackAudioSession.kt
app/src/main/java/com/facet/music/core/ui/components/ExpressivePlayerSlider.kt
app/src/main/java/com/facet/music/core/ui/components/PlaybackPhase.kt
app/src/main/java/com/facet/music/core/ui/components/SinePulseBar.kt
app/src/main/java/com/facet/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

### РЎР»РµРґСѓСЋС‰РёР№ С€Р°Рі
- РћР¶РёРґР°РµС‚ СЃР»РµРґСѓСЋС‰РµР№ РєРѕРјР°РЅРґС‹: СЌРєСЂР°РЅ В«РЎРєР°С‡Р°РЅРЅРѕРµВ» РёР»Рё РёРјРїРѕСЂС‚ РїР»РµР№Р»РёСЃС‚РѕРІ YouTube.
