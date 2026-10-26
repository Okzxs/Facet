# PROGRESS.md — История разработки

## Последнее обновление: 26.10.2026 (сессия 14)

## Финальный фикс таймлайна и FFT — сессия 14
- Вместо самописных жестов используется Material3 1.3.1 `Slider`: Canvas только в `track`, штатные thumb, semantics, клавиатура, RTL, disabled и минимальная зона 48dp. Отдельная полоса над слайдером удалена, активная волна сходится к центру thumb; буфер и неактивная дорожка используют те же координаты.
- Общая модель `PlayerSliderState` для preview и таймкода. Start/Stop/Cancel и finish проходят через один interactionSource в порядке FIFO; Cancel возвращает текущий playback без seek. `key(trackId)` пересоздаёт жест, проверка актуального trackId не позволяет старому release перемотать новый трек. Accessibility change не включает drag.
- `BassDsp.kt`: signed complex RMS только бинов 1..N/2-1 в 40–180 Гц, fs из callback samplingRate/1000, N=fft.size. Нормализация на 128 — относительный масштаб signed-byte, не измерение громкости. Единственная временная envelope: attack 15 мс, release 140 мс, alpha=1-exp(-dt/tau), монотонный elapsedRealtimeNanos; tween энергии удалён. FFT без callbacks более 250 мс затухает к нулю.
- Visualizer освобождается при pause, уходе из RESUMED, смене опубликованного sessionId/трека, потере разрешения и disposal. Частично созданный экземпляр очищается при исключении; поздние FFT отсекает generation. Разрешение проверяется на resume и каждые 250 мс в foreground; без RECORD_AUDIO — плавный декоративный ambient. Энергия читается в draw, callback не пишет Compose-state; beat sync не заявляется.
- Ограничение: неизменённый `PlaybackAudioSession` не публикует Flow, не слушает audio-session callback и не сбрасывает id в 0. UI опрашивает только опубликованный фактический id и использует его как ключ ресурса; смену сессии, которую provider не опубликовал, гарантировать нельзя. Движок, service/provider, поиск, скачивание и зависимости не менялись.
- Проверка: JDK 17 `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`, `gradlew.bat :app:tasks --all` — exit 0. Все запуски с `--console=plain --max-workers=1 -Dorg.gradle.jvmargs="-Xmx2048m -Dfile.encoding=UTF-8"`.
- `gradlew.bat testDebugUnitTest assembleDebug lintDebug`: JVM-тесты и APK реально выполнены; общий exit 1 только из-за lint. XML: 133 testcase / 133 executed, 0 failures, 0 errors, 0 skipped (24 новых DSP/state/RTL-теста). `lintDebug`: 2 UnsafeOptInUsageError в неизменённых `PlaybackAudioSession.kt:30` и `PlayerController.kt:153`, 25 warnings; правила не ослаблялись, запрещённый движок не правился. Отдельное подтверждение `gradlew.bat testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, exit 0, 5 секунд, 53 up-to-date (не повторное исполнение тестов).
- APK проверен: `D:\MyMusic\app\build\outputs\apk\debug\app-debug.apk`, 27 502 042 байт, создан 26.10.2026 18:28:28 +03:00. `git diff --check` без ошибок. Первая попытка tasks использовала системную Java 11 и упала; исправлено только окружение. Одна промежуточная сборка упала на ещё не объявленном SliderFinishInteraction; после завершения правки компиляция прошла. Лимит повторных одинаковых ошибок не достигнут.
- Compose UI-тесты не добавлялись: в проекте нет androidTest и compose-ui-test зависимости; новые зависимости запрещены. На телефоне остаются Cancel/перехват жеста, смена трека до release, TalkBack/клавиатура, RTL/disabled, соседние кнопки, pause/background/resume, отзыв разрешения, реальные FFT/ROM и смена session. Успешные JVM-тесты не заменяют эти проверки.
- Ветка `main`; без commit/push. Пользовательские `.opencode/`, `.postman/`, `postman/` не затронуты; появившаяся в status `.kotlin/` содержит локальные служебные файлы компилятора и не включается в публикацию.

## 📌 Актуальный срез
- 133 реально выполненных JVM-теста, 0 failures/0 skipped; `assembleDebug` подтверждён, APK 27 502 042 байт. Lint не проходит по двум opt-in ошибкам в неизменённом движке.
- Room v8, схемы `4.json`–`8.json`; ONNX/YAMNet и `core/audio` удалены revert-коммитом `9e74fb6`.
- «Для вас»: related-треки YouTube/SoundCloud через NewPipe, 4–6 треков-сидов, кап 2/3, fallback только при <10 и только по строгому артисту; глобальный популярный запрос — только на пустой истории.
- Скачивание: единый `DownloadSourceResolver` выбирает только progressive-источник (SoundCloud — HLS_ONLY, поэтому SC-треки качаются через YouTube), строгий матч артиста и названия, TTL 30 мин для неудачных источников; кнопка скачивания есть в медиатеке и полноэкранном плеере. На главной кнопки убраны — лента не даёт полного контекста для резолвера.
- Плеер: режим текста отполирован в Phase A — спокойный фон (scrim `surface` 0.65 поверх размытой обложки), иерархия строк 24/18/16sp, горизонтальная караоке-заливка, подъём активной строки на 24dp. Поиск, ExoPlayer и `PlayerViewModel` не менялись.

---

## ✅ Сессия 1 — Скелет проекта (15.10.2026)

### Что сделано
- Настроен стек зависимостей: Hilt 2.52, Media3 1.5.0, Room 2.6.1, Retrofit, Coil 3, NewPipe Extractor v0.24.3
- Создана тема Material 3 с Dynamic Color (Android 12+) и violet-purple палитрой для старых версий
- Реализована навигация: `BottomNavigationBar` (4 вкладки) + full-screen плеер со slide-up анимацией
- Созданы заглушки всех экранов: Home, Search, Library, Wave, Player
- `MyMusicApp` (`@HiltAndroidApp`) инициализирует NewPipe
- Настроен edge-to-edge режим

### Файлы
```
MyMusicApp.kt, MainActivity.kt
navigation/Screen.kt, navigation/AppNavGraph.kt
core/ui/theme/Color.kt, Type.kt, Theme.kt
feature/home/HomeScreen.kt
feature/search/SearchScreen.kt
feature/library/LibraryScreen.kt
feature/wave/WaveScreen.kt
feature/player/PlayerScreen.kt
```

---

## ✅ Сессия 2 — SoundCloud поиск через NewPipe (15.10.2026)

### Что сделано
- Реализован слой данных для SoundCloud:
  - `SoundCloudRepository` (интерфейс): `search()` + `resolveStreamUrl()`
  - `SoundCloudRepositoryImpl`: использует `SearchInfo.getInfo()` и `StreamInfo.getInfo()` из NewPipe
  - `NewPipeDownloader`: OkHttp-реализация `Downloader` для NewPipe
- Hilt модули: `NetworkModule` (OkHttp, Retrofit для LRCLIB), `RepositoryModule` (@Binds)
- `SearchViewModel`: debounce 400 мс на Flow запросов, минимум 2 символа
- `SearchScreen`: 4 состояния UI (подсказка / загрузка / ошибка+повтор / список)
- `TrackCard`: переиспользуемый компонент (M3 ListItem + Coil AsyncImage)

### Отказались от
- Самописного парсера `client_id` из JS-бандлов SC (нестабилен, SC меняет структуру)
- Retrofit для SoundCloud (заменён NewPipe)

### Файлы
```
data/model/Track.kt
data/repository/SoundCloudRepository.kt
data/source/soundcloud/SoundCloudRepositoryImpl.kt
core/network/NewPipeDownloader.kt
core/network/NetworkModule.kt
core/di/RepositoryModule.kt
feature/search/SearchViewModel.kt
feature/search/SearchScreen.kt (обновлён)
core/ui/components/TrackCard.kt
```

---

## ✅ Сессия 3 — Media3 HLS воспроизведение (16.10.2026)

### Что сделано
- Добавлена зависимость `media3-exoplayer-hls:1.5.0`
- Обновлён NewPipe Extractor до `v0.26.5`

### Файлы
```
app/build.gradle.kts, AGENTS.md
```

### Текущее состояние
- Поиск SoundCloud + HLS-воспроизведение работают, треки играют в фоне

---

## ✅ Сессия 4 — Полировка UI по чек-листу (16.10.2026)

### Что сделано
1. **Coil (TrackCard, PlayerScreen, MiniPlayer)**: подключён `crossfade(200)`, фиксированные контейнеры (`Modifier.size`), тонированные плейсхолдеры (`surfaceVariant`) и error-плейсхолдер с векторной иконкой ноты (`Icons.Rounded.MusicNote`). Скачки лейаута исключены.
2. **Слайдер плеера**: обновление позиции троттлится (~6-7 раз/сек, 150 мс); во время перетаскивания значение удерживается в локальном state без конфликтов с пальцем, seek вызывается строго в `onValueChangeFinished`.
3. **MiniPlayer**: анимированное появление и скрытие через `AnimatedVisibility` (slide + fade, 250 мс), список результатов поиска не перекрывается благодаря `contentPadding` (80.dp).
4. **Переключение вкладок**: настроен плавный `fadeIn/fadeOut` (200 мс); состояние сохраняется через `saveState`/`restoreState` и `launchSingleTop`.
5. **Открытие/закрытие плеера**: плавная slide-up / slide-down анимация (300 мс) с fade без мерцаний и дублирования.
6. **Поиск**: внедрены скелетон-карточки при загрузке вместо одиночного спиннера; сохранено состояние ошибки с кнопкой повтора.
7. **LazyColumn**: задан `key = { it.id }`, межкартчные отступы через `Arrangement.spacedBy(4.dp)`, `contentPadding` снизу с учётом высоты `MiniPlayer`.
8. **Рекомпозиции**: секция слайдера и таймеров вынесена в изолированный `@Composable` (`PlayerProgressSection`), тик прогресса не триггерит рекомпозицию обложки и всей страницы.
9. **Отступы и типографика**: приведены к токенам `MaterialTheme.typography` и `MaterialTheme.colorScheme`, устранены хардкод-размеры.
10. **Edge-to-edge**: проверены и гармонизированы системные insets (TopAppBar с padding в Scaffold).
11. **Точечные фиксы производительности**: `SubcomposeAsyncImage` заменён на легковесный `AsyncImage` с `placeholder = ColorPainter(surfaceVariant)` и вектором ноты для `error` (устранены лишние фазы субкомпозиции в списках); скелетоны поиска выполнены статичными без шиммер-нагрузки; анимация вкладок облегчена до `fadeIn(200)` на enter и `ExitTransition.None` на exit; `contentPadding` списков синхронизирован со `Scaffold`.

### Файлы
```
core/ui/components/TrackCard.kt
core/ui/components/MiniPlayer.kt
feature/player/PlayerScreen.kt
feature/search/SearchScreen.kt
navigation/AppNavGraph.kt
```

---

## 📊 Текущее состояние

> Блок актуален с 21.10.2026. Ранее здесь висело описание времён сессии 4 — очередь, волна и плейлисты там значились как нереализованные, это устарело.

### Работает
- ✅ Сборка (`assembleDebug`) и 53 JVM-теста (`.\gradlew test`, Robolectric + MockK, без эмулятора)
- ✅ Навигация между вкладками с плавными анимациями, edge-to-edge, фиолетовая Material 3 Expressive тема
- ✅ Поиск SoundCloud через NewPipe Extractor (без client_id), скелетоны и состояния ошибки
- ✅ Главная: Top 10 SoundCloud с кэшем в Room (TTL 30 мин), «Продолжить», «Воспоминания» по сезону, недавние, любимые, секции по жанрам
- ✅ Плеер: ExoPlayer живёт только в `MediaSessionService`, UI работает через `MediaController`; HLS, фоновое воспроизведение, уведомление с обложкой, audio focus, wake mode
- ✅ Очередь: шторка `PlayerQueueSheet`, shuffle, repeat OFF/ALL/ONE, скорость 0.5–2.0×, удаление треков, переход к выбранному
- ✅ Тексты LRCLIB: синхронный караоке-режим, автоцентрирование, тап по строке = перемотка
- ✅ Лайки в Room v5 с геопозицией и сезоном, миграции 1→5 с экспортом схем (без destructive fallback)
- ✅ Офлайн-режим: скачивание треков через WorkManager в `filesDir/downloads`, Room v6 (`downloaded_tracks`), локальный файл в приоритете воспроизведения, кнопка скачивания в плеере и галочка в карточках
- ✅ «Моя волна»: бесконечная сессия, Last.fm + лайки + история + жанры (40/30/20/10), локальный feedback вкусов, автодозагрузка пачками по 12, RU-приоритет с языковой фильтрацией
- ✅ Слайдер-волна с корректной зоной касания (фикс клика Play/Pause)

### Пока не реализовано
- ❌ Управление скачанным: удаление из UI, лимит хранилища, отдельный экран «Скачанное»
- ❌ Плейлисты (нет ни в UI, ни в БД) — это v2
- ❌ Настроения волны («Бодрая / Спокойная / По любимым / Открытия»)
- ❌ Показ координат и сезона в карточках «Воспоминаний», действие «Открыть на карте»
- ❌ Источники Spotify, Яндекс Музыка, ВК Музыка, YouTube Music (v2)

---

## ✅ Сессия 7 — плеер и тексты по референсу exteraless (16.10.2026)

Выжимка архитектурных решений, метрик, цветовых ролей и алгоритмов зафиксирована в [reference-notes.md](file:///D:/MyMusic/docs/reference-notes.md).

### Что сделано
1. **Изучение референса**:
   - Исследован форк Telegram `exteraless` (компоненты `Md3Player`, `PlayerSheet`, `LyricsView`, `Lyrics`, `ControlsRow`, `PlayerMiniView`).
   - Паттерны, метрики и алгоритмы задокументированы в `docs/reference-notes.md` без прямого копирования кода (GPL).
2. **Слой данных текстов песен (LRC & LRCLIB)**:
   - Создана модель `LyricLine` и `LyricsResult` в `data/model/LyricLine.kt`.
   - Реализован устойчивый парсер `LrcParser` с поддержкой `[mm:ss.xx]`, `[mm:ss.xxx]`, `[offset:+-ms]`, очисткой тегов слогов и фолбэком на несинхронный текст.
   - Подключён клиент `LrclibApi` и репозиторий `LyricsRepositoryImpl` (сначала точный поиск `get?track_name=&artist_name=&duration=`, затем фолбэк на `search`).
3. **Редизайн PlayerScreen**:
   - **Шапка**: двухстрочный заголовок ("СЕЙЧАС ИГРАЕТ" + название трека), кнопка назад и меню опций.
   - **Обложка и режим текста**:
     - В обычном режиме: полноразмерная обложка 280dp со скруглением 28dp и тенью.
     - В режиме текста: обложка плавно схлопывается в компактную миниатюру 56×56 dp (скругление 16dp), освобождая пространство для списка строк `LazyColumn`.
     - Альфа-градиент строк: активная 1.0f (bold), соседняя 0.52f, остальные 0.35f; плавный автоскролл к текущей строке.
     - Интерактивность: тап по строке = немедленный переход `seekTo(line.timeMs)`. Подпись "Синхронный текст · LRCLIB".
   - **Лайк**: сквиркл 48×48 dp с радиусом 24dp рядом с названием и артистом.
   - **Управление (ControlsRow)**: центральная кнопка Play/Pause с динамическим морфингом радиуса (44dp в паузе / 28dp в воспроизведении).
   - **Пилюли действий**: контейнер высотой 52dp из 3 элементов:
     - Скорость (1× / 1.25× / 1.5× / 2× / 0.5×) с реальным переключением в `ExoPlayer` через `setPlaybackSpeed`.
     - Текст (пилюля подсвечивается `secondaryContainer` при активном режиме).
     - Далее (кнопка очереди).

### Файлы
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

## ✅ Сессия 8 — Анимации и тонирование плеера (16.10.2026)

### Что сделано
1. **Динамическое тонирование фона (Palette)**:
   - Добавлена зависимость `androidx.palette:palette-ktx:1.0.0`.
   - Создан утилитный компонент `PlayerPalette.kt` (`rememberPlayerDynamicColors`) с кэшированием палитры по `artworkUrl` на IO-диспетчере, предотвращающим повторные пересчёты на тиках прогресса.
   - Фон плеера плавно тонируется градиентом от доминантного цвета обложки к системному фону `surface`.
2. **Анимированное схлопывание обложки**:
   - Реализована плавная трансформация размера обложки: 280dp $\leftrightarrow$ 56dp (`animateDpAsState`, 320 мс, `FastOutSlowInEasing`).
   - Скругление анимируется от 28dp до 16dp. В режиме текста миниатюра обложки органично встаёт в строку с названием и артистом.
3. **Геометрия и элементы управления по референсу `exteraless`**:
   - Кнопка «Лайк»: сквиркл `RoundedCornerShape(18.dp)`.
   - Кнопки «Prev» и «Next»: залитые кнопки размером `64.dp`.
   - Оставшееся время справа отображается в формате со знаком минус (`"-m:ss"`).
4. **Синхронизированные тексты**:
   - Плавный автоскролл к текущей строке через `animateScrollToItem`.
   - Неоновое свечение активной строки через `TextStyle(shadow = Shadow(color, blurRadius = 8f))`.
5. **Плавность навигации**:
   - Синхронизированы переходы вкладок и slide-up/down появление плеера (300 мс).

### Файлы
```
app/build.gradle.kts
core/ui/theme/PlayerPalette.kt
feature/player/PlayerScreen.kt
navigation/AppNavGraph.kt
```

---

## 🐛 Фикс LRCLIB (16.10.2026, поздняя сессия)

### Что было сломано
1. **Отсутствие User-Agent**: `OkHttpClient` отправлял дефолтный `User-Agent: okhttp/...`, из-за чего Cloudflare/LRCLIB возвращал `429 Too Many Requests` (RateLimited / Bot detection).
2. **Шум в метаданных SoundCloud**: названия треков на SoundCloud часто имеют вид `"Artist - Title (Official Video)"` или содержат суффиксы `[HD]`, `(Audio)`, а `uploaderName` может быть неофициальным каналом. Запросы в `/api/get` и `/api/search` с «сырыми» строками отдавали 404 или пустой список.
3. **Бросание HttpException**: методы `LrclibApi` возвращали модель напрямую, и штатный 404 (трек не найден) падал в общий catch без доступа к коду ответа и `errorBody`.
4. **Отсутствие retry/ленивой подгрузки**: если плеер открывался из мини-плеера или первый запрос падал, пользователь не мог повторить поиск текста без перезапуска трека.

### Как починено
1. **Сетевой слой (`NetworkModule.kt`)**: добавлен интерцептор с кастомным `User-Agent: MyMusicApp/1.0 (https://github.com/souler/mymusic)` и заголовком `Accept: application/json`.
2. **Retrofit API (`LrclibApi.kt`)**: эндпоинты обёрнуты в `Response<...>`, добавлен параметр `q` для полнотекстового поиска.
3. **Очистка метаданных и каскадный поиск (`LyricsRepositoryImpl.kt`)**:
   - `cleanTrackMetadata`: извлечение исполнителя из формата `"Artist - Title"`, удаление мусорных суффиксов `(Official Video)`, `(Audio)`, `(Remastered)` и суффикса `"Official"` у артиста.
   - Каскадный поиск: точный `GET /api/get` с длительностью $\to$ `GET /api/get` без длительности (на SC хронометраж часто расходится на пару секунд) $\to$ `GET /api/search?track_name=&artist_name=` $\to$ полнотекстовый `GET /api/search?q=Artist Title` $\to$ фолбэк на исходные строки.
   - Детальное логирование с тегом `LRCLIB`: `Requesting`, `Response code + body preview`, `No lyrics found`, `Error: errorBody`.
4. **ViewModel и UI (`PlayerViewModel.kt`, `PlayerScreen.kt`)**:
   - Метод `loadLyricsIfNeeded(track)` для догрузки текстов при открытии экрана или тапе на пилюлю «Текст».
   - `LaunchedEffect(track?.id)` в `PlayerScreen` гарантирует инициализацию загрузки текста.
   - Добавлена кнопка «Повторить поиск текста» в состоянии ошибки / отсутствия текста.

### Файлы
```
core/network/NetworkModule.kt
data/source/lyrics/LrclibApi.kt
data/source/lyrics/LyricsRepositoryImpl.kt
feature/player/PlayerViewModel.kt
feature/player/PlayerScreen.kt
```

---

## 📊 Текущее состояние

### Работает
- ✅ Сборка проекта (`BUILD SUCCESSFUL`)
- ✅ Навигация между вкладками с плавными анимациями (200 мс)
- ✅ Material 3 тема (Dynamic Color + fallback палитра)
- ✅ Поиск SoundCloud через NewPipe Extractor (без client_id)
- ✅ Отображение результатов с плавной загрузкой обложек (Coil crossfade, error-нота, скелетоны)
- ✅ 4 состояния SearchScreen (пусто / скелетон-загрузка / ошибка / список)
- ✅ Плавный переход в full-screen плеер (slide-up + fade, 300 мс)
- ✅ Реальное воспроизведение треков (ExoPlayer/Media3 + HLS в фоне)
- ✅ MiniPlayer с анимацией slide + fade (250 мс) и изолированным прогресс-баром
- ✅ Плавный слайдер плеера без дёрганий и лишних рекомпозиций
- ✅ Динамический градиентный фон плеера от обложки (Palette API с кэшированием)
- ✅ Переход «Обложка ↔ Текст» через AnimatedContent (spring slide + fade), обложка 248dp со свечением и параллаксом
- ✅ Нативный PlayerSlider (Canvas, буфер, пружинный ползунок) вместо внешней библиотеки WaveSlider
- ✅ Сквиркл-лайк (18dp), круглые кнопки Prev/Next (56dp), Play 84dp с морфом, время `"-m:ss"`
- ✅ Синхронизированные и обычные тексты песен через LRCLIB со свечением активной строки и перемоткой по тапу
- ✅ Переключение скорости воспроизведения (0.5× – 2.0×) через ExoPlayer

### НЕ работает (заглушки)
- ❌ Очередь воспроизведения (Next/Previous/Shuffle/Repeat)
- ❌ «Моя волна» (алгоритм не реализован)
- ❌ Плейлисты и библиотека (Room не настроен)
- ❌ Другие источники (Spotify, Яндекс, VK) — запланированы на v2

---

## ✅ Фаза 0 — Рисёрч, дизайн-спека M3 Expressive + творческая свобода (16.10.2026)

### Что сделано
1. **Изучение актуального Material 3 Expressive**:
   - Проанализированы обновления M3 Expressive (2025–2026), кинематика пружин (Spring Physics), система скруглений (Shape Scale) и семантические роли контейнеров (`surfaceContainerLowest` ... `surfaceContainerHighest`).
2. **Дизайн-спецификация (`docs/design-spec.md`)**:
   - Зафиксирована целостная система визуального языка для всех экранов приложения: Home, Search, Library, Wave, Player, MiniPlayer, QueueSheet, NavigationBar.
   - Описаны палитры, формы (сквирклы 18dp, радиусы 28dp, сегментированные пилюли 52dp), типографическая шкала и параметры физики анимаций (длительности, кривые `FastOutSlowInEasing`, пружины с `dampingRatio = 0.82f`).
3. **Творческие решения с обоснованием**:
   - Номерные плакатные карточки для чартов «Top 50» с полупрозрачными позициями треков.
   - Сегментированный тактильный бар пилюль в плеере по референсу `exteraless`.
   - Выразительный сквиркл лайка (18dp) с динамическим акцентным заполнением.
   - Динамический градиент фона плеера от обложки (Palette API).
   - Живая Canvas-синусоида для тизера «Моя волна» вместо статичной заглушки.
4. **Аудит зависимостей**:
   - Подтверждена 100% совместимость требований Expressive-спецификации с текущим Compose BOM 2024.11.00 без изменения версий библиотек (сохранение стабильности сборки на AGP 8.5.2 и Kotlin 2.0.21).

### Файлы
```
docs/design-spec.md
```

---

## ✅ Фаза 1 — Room: лайки и история (16.10.2026)

### Что сделано
1. **База данных Room 2.6.1 (`AppDatabase.kt`)**:
   - Создана база данных `mymusic.db` с fallback-миграцией.
   - Таблица `liked_tracks` (`LikedTrackEntity`): сохранение ID, источника, названия, артиста, обложки, длительности, жанра, URL транскодинга и timestamp лайка `likedAt`.
   - Таблица `play_history` (`PlayHistoryEntity`): сохранение истории воспроизведения с автоматическим триммингом до последних 50 записей через `@Transaction` в `PlayHistoryDao`.
2. **Слой доступа к данным (DAO)**:
   - `LikedTracksDao`: реактивный поток `getLikedTracks()`, проверка `isLiked(id): Flow<Boolean>`, вставка и удаление.
   - `PlayHistoryDao`: реактивный поток `getPlayHistory(limit = 50)`, метод `recordPlay` (вставка + очистка старых записей сверх лимита 50).
3. **Репозитории и DI (`data/local/`, `core/di/`)**:
   - Интерфейсы: `LikedTracksRepository` и `PlayHistoryRepository` в `data/repository/`.
   - Реализации: `LikedTracksRepositoryImpl` и `PlayHistoryRepositoryImpl` с маппингом `LikedTrackEntity`/`PlayHistoryEntity` $\leftrightarrow$ доменная модель `Track`.
   - Hilt-модули: `DatabaseModule` (`@Provides` для БД и DAO) и обновлённый `RepositoryModule` (`@Binds` для репозиториев).
4. **Связка с плеером**:
   - `PlayerViewModel`: реактивное свойство `isLiked: StateFlow<Boolean>`, отслеживающее статус текущего трека в БД через `flatMapLatest`; метод `toggleLike()` для добавления/удаления трека в Room; автоматическая запись трека в `play_history` при старте воспроизведения (`loadAndPlay`).
   - `PlayerScreen`: кнопка-сквиркл лайка подключена к `viewModel.isLiked` и `viewModel.toggleLike()`.
5. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
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

## ✅ Фаза 2 — Очередь воспроизведения (17.10.2026)

### Что сделано
1. **Управление очередью в PlayerController**:
   - Полноценная очередь `List<Track>` с отслеживанием текущего индекса `currentIndex`.
   - Режимы повтора `repeatMode` (`OFF` $\to$ `ALL` $\to$ `ONE` $\to$ `OFF`) и воспроизведения после окончания трека (`onTrackEnded`).
   - Режим перемешивания `isShuffle` с сохранением `originalQueue` для корректного восстановления исходного порядка треков при выключении.
   - Методы перехода: `playNext(loop)` и `playPrevious()` с логикой возврата в начало трека, если прошло более 3 секунд.
   - Метод удаления трека `removeTrackAt(index)` с автокоррекцией текущего индекса и остановкой плеера при опустошении очереди.
2. **Интеграция с системным сервисом и уведомлением (MusicService)**:
   - В `ForwardingPlayer` переопределены и объявлены доступными команды `SEEK_TO_NEXT`, `SEEK_TO_NEXT_MEDIA_ITEM`, `SEEK_TO_PREVIOUS`, `SEEK_TO_PREVIOUS_MEDIA_ITEM`.
   - Кнопки переключения треков в шторке уведомлений и на заблокированном экране вызывают методы очереди `playerController.playNext()` и `playerController.playPrevious()`.
3. **Шторка очереди воспроизведения (PlayerQueueSheet)**:
   - Модальная шторка `ModalBottomSheet` высотой 78% экрана со скруглением верхних углов 28dp и drag handle 32×4 dp.
   - Заголовок «Очередь» с количеством треков и кнопками быстрого включения/выключения Shuffle и Repeat.
   - Список треков с автоскроллом к активному элементу при открытии.
   - Активный трек визуально выделен тонированной плашкой `primaryContainer` и иконкой Play.
   - Двусторонний свайп влево и вправо для быстрого удаления трека из очереди (`SwipeToDismissBox`) с анимацией красной подложки и корзины.
4. **Связка с UI плеера и поиском**:
   - Пилюля «Далее» в сегментированном баре `PlayerScreen` открывает `PlayerQueueSheet`.
   - При выборе трека в результатах поиска (`SearchScreen`) в плеер передаётся полный список найденных треков как контекстная очередь.
   - Кнопки «Prev» и «Next» в `PlayerScreen` вызывают `viewModel.playPrevious()` и `viewModel.playNext()`.
5. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
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

## ✅ Фаза 2.5 — Хотфикс плеера (18.10.2026)

### Что сделано
1. **Починена кнопка Play/Pause в развернутом плеере (`PlayerScreen.kt`)**:
   - Устранено блокирующее условие `enabled = !isResolving`, из-за которого кнопка зависала в отключенном состоянии после загрузки трека.
   - Кнопка 88dp кликабельна всегда и переключает воспроизведение через `viewModel.togglePlayPause()`.
   - Иконка корректно синхронизирована с состоянием `isPlaying` (`PlayArrow` / `Pause`).
2. **Мгновенный сброс позиции при смене трека**:
   - В `PlayerViewModel`: при смене трека через `loadAndPlay` или очередь UI-состояние позиции немедленно обнуляется (`positionMs = 0L`).
   - В `PlayerProgressSection`: при изменении `trackId` значения слайдера (`playbackFraction`, `playbackPositionMs`, `dragPosition`) мгновенно сбрасываются в 0, исключая зависание старого таймкода.
   - В `PlayerController`: перед вызовом `prepare()` при запуске трека явно вызывается `seekTo(0)`.
3. **Починена зависающая полоска буферизации**:
   - В `PlayerController` и `PlayerViewModel` реализован отдельный поток `isBuffering: StateFlow<Boolean>`, отслеживающий состояние `Player.STATE_BUFFERING`.
   - В `PlayerScreen` спиннер в центральной кнопке крутится ТОЛЬКО когда `isBuffering == true`.
   - Старый `LinearProgressIndicator` полностью удалён из разметки.
4. **Бесшовный переход буферизации в таймлайн (кастомный M3 Slider)**:
   - В `PlayerProgressSection` применён кастомный параметр `track` у `Slider` из Material 3:
     1. Базовый неактивный слой (общая длительность) цвета `surfaceVariant`.
     2. Полупрозрачный слой буферизации `primary.copy(alpha = 0.38f)` пропорционально `bufferedPosition / duration`.
     3. Активный слой прогресса воспроизведения акцентного цвета `primary`.
5. **Оптимизация сетевых запросов и таймаутов (`NetworkModule.kt`)**:
   - В `OkHttpClient` таймауты подключения, чтения и записи установлены в 10 секунд, общий `callTimeout` в 15 секунд, что предотвращает блокировку интерфейса при нестабильной сети.
6. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
```
core/network/NetworkModule.kt
core/player/PlayerController.kt
feature/player/PlayerViewModel.kt
feature/player/PlayerScreen.kt
```

---

## ✅ Фаза 2.6 — Полировка переключения треков и слайдера (18.10.2026)

### Что сделано
1. **Мгновенное глушение прошлого трека при смене (`PlayerController.kt`)**:
   - В начале методов `playTrackAt`, `resolveAndPlay` и `play` вызывается немедленная остановка: `exoPlayer.pause()`, `exoPlayer.stop()`, `exoPlayer.seekTo(0)`.
   - Прошлый трек замолкает сразу в момент нажатия, до начала сетевого запроса `resolveStreamUrl`, исключая эффект "хвоста" воспроизведения.
2. **Плавный переход «Загрузка $\to$ Таймлайн» (`PlayerScreen.kt`)**:
   - В `PlayerProgressSection` внедрён контейнер фиксированной высоты `44.dp` с выравниванием по центру (`Alignment.Center`), что гарантирует стабильность положения таймкодов без прыжков UI.
   - Переключение между тонким индикатором загрузки (`LinearProgressIndicator` 4dp) и основным слайдером (`Slider` с буферизацией) анимировано через `Crossfade` (300 мс, `FastOutSlowInEasing`).
   - Условие показа полоски загрузки: `isBuffering && displayPosition <= 0.005f`.
3. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
```
core/player/PlayerController.kt
feature/player/PlayerScreen.kt
```

---

## ✅ Фаза 2.7 — Хотфикс UI и состояний плеера (18.10.2026)

### Что сделано
1. **Сброс ошибок при смене трека и переходе в STATE_READY**:
   - В `PlayerController.kt` добавлено поле `playbackState` в `PlayerState`, а при переходе в `Player.STATE_READY` ошибка автоматически сбрасывается (`error = null`).
   - В `PlayerViewModel.kt` при смене трека состояние ошибок немедленно очищается, а также синхронизируется с переходом в `STATE_READY`.
   - В `PlayerScreen.kt` плашка с ошибкой гарантированно скрывается, как только плеер переходит в статус `Player.STATE_READY`.
2. **Починена палитра обложки (Palette)**:
   - В `PlayerPalette.kt` внедрено явное состояние загрузки `isLoading`: пока обложка загружается — отображается дефолтный фон; как только обложка загрузилась — применяется доминантный цвет.
   - Добавлена устойчивая обработка ошибок (при сетевых сбоях или невозможности извлечь палитру остаётся дефолтный фон).
   - Для светлых и высококонтрастных обложек увеличены веса альфы (`0.72f` вверху, `0.28f` внизу), предотвращая поглощение цвета тёмным системным фоном `surface`.
   - В `PlayerScreen.kt` переходы градиента фона анимированы через `animateColorAsState` (400 мс).
3. **Стандартный Slider и плавный Crossfade (500 мс)**:
   - В `PlayerProgressSection` удалён кастомный Canvas-трек и возвращён стандартный компонент `Slider`.
   - Реализована буферизация под слайдером через `LinearProgressIndicator` (фон `surfaceVariant`, прогресс `primary`, буферизация `primary.copy(alpha = 0.3f)`).
   - Переход «загрузка $\to$ таймлайн» выполнен через `Crossfade(targetState = isBuffering && currentPosition == 0L)` с длительностью анимации 500 мс (`FastOutSlowInEasing`).
4. **Обрезка текста**:
   - В заголовке трека установлено `maxLines = 2` с `TextOverflow.Ellipsis`.
5. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
```
core/player/PlayerController.kt
feature/player/PlayerViewModel.kt
core/ui/theme/PlayerPalette.kt
feature/player/PlayerScreen.kt
```

---

## ✅ Фаза 3 — Главный экран с подборками и Медиатека (19.10.2026)

### Что сделано
1. **Слой данных чартов SoundCloud и кэш Room**:
   - Создана сущность `ChartCacheEntity` и DAO `ChartCacheDao` с составным первичным ключом `(chartType, id)` и транзакционной заменой `replaceChart`.
   - База данных `AppDatabase` обновлена до версии 2 с регистрацией `ChartCacheEntity` и `chartCacheDao()`.
   - Создан интерфейс `SoundCloudChartsRepository` и реализация `SoundCloudChartsRepositoryImpl` через NewPipe-киоски (`https://soundcloud.com/charts/new` для "New & hot" и `/top` для "Top 50").
   - Внедрён 30-минутный TTL кэширования: свежий кэш отдаётся мгновенно офлайн; при устаревании или pull-to-refresh происходит сетевой запрос к NewPipe с сохранением в Room; при сбое сети работает offline-first фолбэк на кэш.
   - Hilt-модули `DatabaseModule` и `RepositoryModule` обновлены для предоставления `ChartCacheDao` и `SoundCloudChartsRepository`.
2. **HomeViewModel**:
   - Непрерывное наблюдение за реактивными потоками кэша чартов, истории прослушиваний (`play_history`) и лайков (`liked_tracks`).
   - Автоматическая группировка персональных предпочтений по жанрам (`genreSections`).
   - Разделение секций: личные («Недавние», «Любимые треки», «Твои жанры») показываются только при наличии данных; чарты («New & Hot», «Top 50») доступны всегда со скелетонами загрузки.
   - Поддержка `refresh()` с флагом `forceRefresh = true` для жеста pull-to-refresh.
3. **HomeScreen (Material 3 Expressive + Jetpack Compose)**:
   - Выразительная шапка-приветствие по времени суток с динамическим эмодзи («Доброе утро ☀️», «Добрый день 🌤️», «Добрый вечер 🌆», «Доброй ночи 🌙») и крупным шрифтом `headlineLarge`.
   - Горизонтальные карусели `LazyRow` с карточками 148×148 dp со скруглением `RoundedCornerShape(20.dp)` и тенью 6dp.
   - Номерные карточки для секции «Top 50 SoundCloud» с крупным плакатным номером позиции (1..50) по дизайн-спеке.
   - Скелетон-карусели при первой загрузке без мерцания.
   - Жест Pull-to-refresh через `PullToRefreshBox`.
   - Тап по любому треку запускает воспроизведение трека с полной очередью из этой подборки.
4. **LibraryScreen и LibraryViewModel**:
   - Заменена статическая заглушка на полноценный экран медиатеки любимых треков из базы данных Room.
   - Счетчик треков в заголовке шапки ("X треков в коллекции").
   - Кнопки быстрых действий: «Слушать всё» и «Перемешать».
   - Список карточек `TrackCard` с возможностью быстрого снятия лайка через `IconButton`.
   - Выразительный Empty State с подсказкой при пустой медиатеке.
   - Тап по треку открывает плеер с очередью из всей коллекции.
5. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
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

## ✅ Фаза 4 — Material 3 Expressive редизайн (19.10.2026)

Примечание: в прежнем плане «Фаза 4» означала алгоритм «Моей волны». По решению пользователя эта фаза отведена под Expressive-редизайн интерфейса, а алгоритм волны перенесён в v1.1. Механика плеера (LRCLIB, Palette, морфинг кнопки) не затрагивалась.

### Что сделано
1. **ExpressiveNavigationBar** (`core/ui/components/ExpressiveNavigationBar.kt`):
   - Плавающая нижняя панель `surfaceContainer` со скруглением 24dp, тенью и отступом 8dp над системной навигацией.
   - Пружинная пилюля-индикатор активной вкладки (~56×32 dp, радиус 16dp) с анимацией смещения (`spring`, dampingRatio 0.78) через `BoxWithConstraints`.
   - Анимация масштаба и цвета иконок активной вкладки, типографика 11sp, тактильный отклик `TextHandleMove`.
2. **SearchScreen**:
   - Крупная выразительная шапка «ПОИСК» (headlineLarge, ExtraBold).
   - `ExpressiveSearchField`: морфинг при фокусе (цвет контейнера, скругление 28dp, тень 8dp), иконка очистки с пружинным появлением.
   - Жанровые чипы-подсказки, состояния Idle / Loading (скелетоны) / Error / Empty / Results, reveal-анимация списка.
   - Убран двойной системный inset (вложенный Scaffold с `contentWindowInsets = 0`).
3. **WaveScreen** (промо-заглушка «скоро»):
   - Живой Canvas-фон: три синусоиды, радиальный градиентный орб с частицами на орбитах, `rememberInfiniteTransition`.
   - Бейдж «СКОРО» с градиентом, чипы настроений волны (горизонтальный `LazyRow`, в будущем — режимы), карточка-анонс.
   - Полноэкранный reveal-переход через `AnimatedVisibility` (пружина + fade).
4. **HomeScreen**:
   - Удалён вложенный Scaffold — контент больше не получает двойные insets.
   - Все секции обёрнуты в `RevealSection` (staggered-появление с задержкой 55 мс на секцию, пружина, с резервированием layout-размера).
   - `SectionHeader` с градиентной акцентной полосой; expressивные заголовки секций.
   - `TrackCarouselCard`: пружинный scale 0.95 при нажатии и тональный слой-подсветка.
   - `TopChartCarousel`: анимированный цвет контейнера при нажатии, top-3 карточки в `primaryContainer`.
5. **TrackCard** (общий компонент Search и Library):
   - Поверхность 20dp с тональным откликом на нажатие (`collectIsPressedAsState` + `animateColorAsState`), пружинный scale 0.975.
   - Обложка 56dp/12dp, длительность и жанр в подписи.
6. **AppNavGraph**:
   - Spring-переходы вкладок и плеера (fade + scale / spring slide вместо tween), обновлён вызов `WaveScreen()`.
7. **Type.kt** — выразительная типографическая шкала:
   - ExtraBold-заголовки с плотным трекингом (-0.5sp), Bold labelSmall (11sp), SemiBold labelLarge/titleMedium.
8. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/core/ui/components/ExpressiveNavigationBar.kt (новый)
app/src/main/java/com/my/music/core/ui/components/TrackCard.kt
app/src/main/java/com/my/music/core/ui/theme/Type.kt
app/src/main/java/com/my/music/feature/home/HomeScreen.kt
app/src/main/java/com/my/music/feature/search/SearchScreen.kt
app/src/main/java/com/my/music/feature/wave/WaveScreen.kt
app/src/main/java/com/my/music/navigation/AppNavGraph.kt
```

---

## ✅ Фаза 4.1 — Правки после теста на реальном устройстве (19.10.2026)

### Что сделано
1. **WaveScreen**: центр орба перенесён в геометрический центр hero-зоны (`size.height / 2f`), поэтому иконка `Waves` через `Alignment.Center` стоит ровно в центре круга.
2. **PlayerScreen, верхняя панель**: `TopAppBar` заменён на `CenterAlignedTopAppBar`; «СЕЙЧАС ИГРАЕТ» и название трека центрируются между кнопками «Назад» и «Меню» (`TextAlign.Center`, `fillMaxWidth`, ellipsis).
3. **PlayerPalette**: приоритет свотчей изменён на выразительные (`vibrant` → `lightVibrant` → `dominant` → …); для светлых обложек (`luminance > 0.35`) насыщенность поднимается ×1.4, светлота ограничивается 0.50–0.66, альфы фона увеличены до 0.88/0.40 (тёмные — 0.62/0.22). Серые «металлические» обложки теперь дают ощутимый цвет фона.
4. **Тексты песен**: вместо простого fade — `animateColorAsState` (420 мс) от тусклого `onSurface` (alpha 0.30/0.55) к белому `onSurface` (alpha 1.0); дополнительно анимируются вес шрифта (400 → 700, пружина), масштаб (1.0 → 1.05) и свечение (`Transparent` → `primary` 0.5, blur 10).
5. **Слайдер**: в `PlayerProgressSection` добавлен флаг `isResolving`; `LinearProgressIndicator` показывается при `(isBuffering || isResolving) && позиция == 0`, затем `Crossfade` (500 мс) плавно заменяет его на `Slider` с буферным слоем `primary.copy(alpha = 0.3f)`.
6. **Чарты SoundCloud**: в `SoundCloudChartsRepositoryImpl` добавлен фильтр мусора — отбрасываются треки длительностью 1–29 сек и явные нули прослушиваний (фильтр по plays применяется только если `viewCount` реально парсится); если после фильтрации список пуст — возвращается исходный. Секция Top 50 остаётся полной (50 позиций).
7. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/feature/wave/WaveScreen.kt
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
app/src/main/java/com/my/music/core/ui/theme/PlayerPalette.kt
app/src/main/java/com/my/music/data/source/soundcloud/SoundCloudChartsRepositoryImpl.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.2 — Дружелюбные сетевые ошибки плеера (19.10.2026)

### Что сделано
1. **Единый маппер ошибок (`core/player/PlaybackErrorMessages.kt`)**:
   - `UnknownHostException`, `ConnectException`, `SocketException`, сообщения «Unable to resolve host» / «No address associated with hostname» → «Нет подключения к интернету. Проверьте сеть.»
   - `SocketTimeoutException` / таймауты → «Превышено время ожидания. Попробуйте снова.»
   - `SSLException` → «Ошибка защищённого соединения. Попробуйте снова.»
   - Всё остальное → «Не удалось воспроизвести трек. Попробуйте снова.»
   - Для `PlaybackException` дополнительно учитываются коды `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` / `_TIMEOUT`.
2. **PlayerController**:
   - Ошибка `resolveStreamUrl` маппится в дружелюбный текст; сырое исключение пишется только в Logcat.
   - Добавлен `onPlayerError` для ошибок ExoPlayer во время воспроизведения — на экран также уходит только понятное сообщение.
   - При ошибке сбрасываются `isLoading`, `isBuffering`, `isPlaying`.
   - Нажатие Play/Pause при наличии ошибки сразу очищает её и повторно запускает текущий трек (`recordHistory = false`, чтобы не дублировать историю).
   - При переходе в `STATE_READY` ошибка гарантированно очищается.
3. **PlayerViewModel**:
   - `togglePlayPause()` немедленно очищает `uiState.error`.
   - Ошибки загрузки текстов из LRCLIB также маппятся в «Не удалось загрузить текст. Попробуйте снова.» — сырые Java-ошибки не попадают в панель текстов.
   - Выбор нового трека уже сбрасывает ошибку (`playTrackWithQueue` / `playTrackAt`).
4. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/core/player/PlaybackErrorMessages.kt (новый)
app/src/main/java/com/my/music/core/player/PlayerController.kt
app/src/main/java/com/my/music/feature/player/PlayerViewModel.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.3 — Финальные визуальные штрихи: WaveSlider, караоке-текст, Google Sans Flex (19.10.2026)

### Что сделано
1. **Анимированная синусоида в PlayerScreen (WaveSlider)**:
   - В `app/build.gradle.kts` добавлена зависимость `com.github.galaxygoldfish:waveslider:v0.0.4` (в задании указана 0.3.3, но в JitPack её нет — последняя собранная версия v0.0.4). JitPack уже был подключён в `settings.gradle.kts`, правок не потребовалось.
   - Стандартный `Slider` в `PlayerProgressSection` заменён на `WaveSlider` (amplitude = 10F, frequency = 0.07F, colors из MaterialTheme/палитры трека).
   - Буферизация сохранена: тонкая линия `primary.copy(alpha = 0.3f)` рисуется под волной; Crossfade «индикатор загрузки → слайдер» (500 мс) не тронут.
2. **Караоке-эффект в текстах (по мотивам Accompanist KaraokeLineText, без копирования кода)**:
   - В цикле опроса позиции (150 мс) вычисляется прогресс внутри активной строки.
   - Активная строка заполняется `Brush.linearGradient` слева направо: проигранная часть — белая (`onSurface`), непропетая — тусклая (alpha 0.30), с мягким краем 5%.
   - Прошедшие строки — заливка 1.0 (белый 55% alpha), будущие — 0.0; заливка анимируется `animateFloatAsState` (160 мс, LinearEasing).
   - Свечение, анимация веса (400→700) и масштаба (1.05) активной строки сохранены.
3. **Google Sans Flex**:
   - Variable-шрифт `GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf` (OFL) скачан в `app/src/main/res/font/google_sans_flex.ttf`; лицензия — `app/src/main/assets/licenses/OFL_GoogleSansFlex.txt`.
   - В `Type.kt` создан `FontFamily` из 5 весов (400/500/600/700/800) через `FontVariation.Settings` (ось wght, API 26+) и применён ко всей типографике.
   - Отказались от `ui-text-google-fonts`/`GoogleFont("Google Sans Flex")`: провайдер шрифтов требует GMS и сертификаты, в оффлайне может не загрузиться; локальный TTF надёжнее (в задании допускался вариант «или скачай .ttf»).
4. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
```
app/build.gradle.kts
app/src/main/res/font/google_sans_flex.ttf (новый)
app/src/main/assets/licenses/OFL_GoogleSansFlex.txt (новый)
app/src/main/java/com/my/music/core/ui/theme/Type.kt
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.4 — Чистый фон WaveSlider и замирание волны на паузе (19.10.2026)

### Что сделано
1. **Прозрачный фон слайдера (`PlayerProgressSection`)**:
   - `inactiveTrackColor` переведён на `Color.Transparent` — серая полоса `surfaceVariant` (линия от бегунка до конца, которую рисует библиотека) больше не видна за волной.
   - Удалён слой буферизации (`LinearProgressIndicator` `primary.copy(alpha = 0.3f)`) — волна теперь рисуется прямо на градиенте плеера, без подложек (вместе с ним убран параметр `bufferedFraction`).
2. **Замирание волны на паузе**:
   - Амплитуда волны связана с `isPlaying` через `animateFloatAsState`: на паузе плавно (500 мс, `FastOutSlowInEasing`) опускается с 10f до 0f, при возобновлении — поднимается обратно.
   - На паузе волна замирает абсолютно прямой линией.
3. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.5 — Полоска длительности и компактный плеер (19.10.2026)

### Что сделано
1. **Полоска длительности под волной (`PlayerProgressSection`)**:
   - Под WaveSlider добавлен тонкий Canvas-слой (2dp, скруглённые концы): вся ширина — длительность трека (primary, alpha 0.18), проигранная часть — ярче (primary, alpha 0.65).
   - Полоска синхронизирована с `displayPosition` (двигается и при перетаскивании), рисуется за волной, фон остаётся прозрачным.
2. **Компактность плеера**:
   - Обложка 280dp → 248dp (анимация схлопывания в текстах: 248dp → 56dp).
   - Отступы: над обложкой 12→8, под обложкой 16→12, у заголовка 12→8, вокруг кнопок 8→6 и 16→12, нижний 16→12.
   - Кнопки: Play/Pause 88→80dp (морф радиуса 40→26dp, иконка 40→36, спиннер 32→30), Prev/Next 64→56dp (иконки 32→28).
   - Пилюли действий 52→46dp (радиусы 26→23dp).
3. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.6 — Expressive-редизайн PlayerScreen: нативный слайдер и AnimatedContent (19.10.2026)

### Что сделано
1. **Удалена библиотека WaveSlider**:
   - Из `app/build.gradle.kts` убрана зависимость `com.github.galaxygoldfish:waveslider:v0.0.4`; JitPack в `settings.gradle.kts` оставлен (не относится к библиотеке).
2. **Новый слайдер `PlayerSlider` (`core/ui/components/PlayerSlider.kt`)**:
   - Чистый Compose Canvas: толстая скруглённая дорожка 8dp, буферизация полупрозрачным слоем (`activeColor` alpha 0.28), проигранная часть — акцентный цвет.
   - Ползунок — вертикальная пилюля 10×30dp с пружинным увеличением ×1.35 и ореолом при перетаскивании; жесты drag + tap, без внешних библиотек.
3. **Переход «Обложка ↔ Текст» через `AnimatedContent`**:
   - Верхняя (обложка) и центральная зоны объединены в один `AnimatedContent` с весом 1f.
   - Пружинный переход (dampingRatio 0.82, StiffnessMediumLow): текст въезжает снизу (it/3), обложка уходит вверх (-it/4), с fade; обратный переход — зеркальный.
   - Новые file-scope композаблы `CoverModeContent` и `LyricsModeContent`.
4. **Объём обложки**:
   - Свечение позади обложки: radialGradient (`primary` alpha 0.35), scale 1.16 через `matchParentSize` (layout не раздувает).
   - Параллакс: `translationY = scroll * 0.45f` при вертикальном скролле зоны.
   - Тень 18dp с ambient/spot `primary`; типографика: название `headlineLarge` Bold, артист `bodyLarge` (alpha 0.60); в текстовом режиме — миниатюра 56dp/16dp с тенью 6dp и спейсером 14dp.
5. **Кнопки управления**:
   - Play/Pause 84dp (морф радиуса 42/30dp, иконка 38dp, спиннер 32dp), Prev/Next 56dp; ряд центрирован `spacedBy(28.dp)`.
   - Новый хелпер `rememberPressScale` — пружинный press-scale 0.90 при нажатии (InteractionSource) для всех трёх кнопок.
6. **Мелочи**: пилюли действий — зазор 4→8dp; спейсеры секций 6→10 и 12→16 (больше воздуха); loading-полоска прогресса приведена к толщине дорожки 8dp (трек `onSurface` alpha 0.12), трек слайдера `onSurface` alpha 0.16, ползунок `onSurface`.
7. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
```
app/build.gradle.kts
app/src/main/java/com/my/music/core/ui/components/PlayerSlider.kt (новый)
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.7 — Дерзкий редизайн PlayerScreen: 3 варианта компоновки, остров управления, слайдер-точки (19.10.2026)

### Что сделано
1. **3 варианта композиции** (переключатель `PLAYER_LAYOUT_VARIANT` в начале `PlayerScreen.kt`, значения 1/2/3):
   - Вариант 1 «Редакция» (по умолчанию): наклонённая обложка 88% ширины по центру (-5°); стеклянная карточка с Display-названием свисает с нижнего края обложки.
   - Вариант 2 «Смещение»: обложка 62% ширины свисает за правый край (+16dp, наклон +7°), карточка с названием — слева.
   - Вариант 3 «На обложке»: обложка 94%, название лежит прямо на ней в матово-стеклянной плашке (alpha 0.55).
2. **«Уход на задний план» при входе в тексты**: обложка — постоянный слой-задник; при переключении она пружинно уменьшается до 0.58, уходит вниз на +26dp, тускнеет до alpha 0.30 и уходит в blur 26dp (`Modifier.blur`, реальный блюр на Android 12+); передний план меняется через `AnimatedContent` (spring slide + fade).
3. **Плавающий «остров» управления (`ExpressivePlayerComponents.kt`)**:
   - Каплевидная форма (44dp сверху / 22dp снизу), цветная тень от палитры, рамка 1dp;
   - Ряд: скорость (круг 44dp с текстом «1×»), пред/след 52dp, Play 74dp с морфом радиуса (круг ↔ сквиркл) и спиннером буферизации, очередь (круг 44dp);
   - Сверху к острову «прирос» язычок «Текст» (иконка Lyrics, выступает на 19dp над кромкой, в активном режиме подсвечивается `primaryContainer`);
   - Press-scale 0.90 на всех кнопках острова (общий хелпер `rememberPressScale`).
4. **Новый слайдер `ExpressivePlayerSlider` (`ExpressivePlayerSlider.kt`)**:
   - «Цепочка точек»: пройденные — крупнее и ярче, буферные — alpha 0.35, будущие — тусклые; ближайшая к бегунку точка плавно раздувается;
   - Ползунок-пилюля пружинно «подпрыгивает» при захвате (spring dampingRatio 0.30 с овершутом) и «сквошится»; ореол раскрывается вместе с отскоком.
5. **Стеклянный режим текстов**: шапка-витрина (миниатюра 52dp + titleLarge) и полупрозрачная панель строк (`surfaceContainerHigh` alpha 0.42–0.62, рамки 1dp) поверх размытой обложки — эффект матового стекла.
6. **Типографика**: название — `displaySmall` (36sp Google Sans Flex Bold); верхняя панель — только labelSmall «СЕЙЧАС ИГРАЕТ» (убран дублирующий заголовок).
7. Удалён старый `PlayerSlider.kt` (заменён на ExpressivePlayerSlider); логика ViewModel/Controller не тронута.
8. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Как посмотреть варианты
В `PlayerScreen.kt` поменять `PLAYER_LAYOUT_VARIANT` (1/2/3) и пересобрать.

### Файлы
```
app/src/main/java/com/my/music/core/ui/components/ExpressivePlayerSlider.kt (новый)
app/src/main/java/com/my/music/core/ui/components/ExpressivePlayerComponents.kt (новый)
app/src/main/java/com/my/music/core/ui/components/PlayerSlider.kt (удалён)
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.8 — Полировка контраста плеера и экран настроек (19.10.2026)

### Что сделано
1. **Полировка PlayerScreen (контраст и наложения)**:
   - Лейбл «СЕЙЧАС ИГРАЕТ» получил явный цвет `onSurface` alpha 0.80 — больше не растворяется на градиентном фоне.
   - Отступы: у острова управления верхний паддинг 24→30dp (запас под язычок «Текст»), спейсер под островом 14→18dp, в CoverForeground добавлен гарантированный спейсер 20dp под карточкой, в LyricsForeground — 18dp до острова и внутренний вертикальный паддинг панели строк, contentPadding списка текстов (16/24dp), подпись «LRCLIB» с увеличенным отступом, плашка ошибки — с вертикальным паддингом.
   - Контраст матового стекла в текстах: шапка витрины alpha 0.62→0.82, панель строк 0.42→0.72 — строки не сливаются с размытой обложкой.
2. **Слайдер — сплошная волна**: `ExpressivePlayerSlider` переведён с «цепочки точек» на сплошную скруглённую дорожку 8dp (трек → буфер `activeColor` alpha 0.28 → проигранная часть), ползунок-пилюля 10×30dp с пружинным увеличением и ореолом при захвате (как в рабочей версии Фазы 4.6, но в Expressive-оформлении).
3. **Слой настроек**:
   - `AppSettings` (`dynamicCoverColors`, `accentColorArgb`) и интерфейс `SettingsRepository`.
   - `SettingsRepositoryImpl` на SharedPreferences (`mymusic_settings`), запись в IO-диспетчере, `@Binds` в `RepositoryModule`.
4. **Тема от акцентного цвета**:
   - `Theme.kt`: функция `accentColorScheme(accent, darkTheme)` генерирует primary-группу из seed через HSL (secondary/tertiary со сдвигом оттенка 40°, нейтральные поверхности — из фиксированных схем); `MyMusicTheme` получил параметр `accentColor: Color?` с приоритетом над Dynamic Color.
   - `MainActivity` внедряет `SettingsRepository` и передаёт акцент в тему, когда динамические цвета обложки выключены.
5. **Плеер и настройки динамики**:
   - `PlayerViewModel.appSettings` (StateFlow из репозитория).
   - `PlayerScreen` при выключенной динамике не грузит палитру обложки (`artworkUrl = null`) — фон и акценты плеера строятся от выбранного акцента темы.
6. **Экран настроек (`SettingsScreen` + `SettingsViewModel`)**:
   - Секция «ОФОРМЛЕНИЕ»: переключатель «Динамические цвета обложки» и палитра из 6 базовых акцентов (фиолетовый, синий, бирюзовый, зелёный, оранжевый, красный) в виде кружков; выбранный раздувается пружиной и получает контур с галочкой, при включённой динамике палитра приглушена и неактивна.
   - Маршрут `Screen.Settings` — полноэкранный (нижняя панель скрыта), кнопка-шестерёнка добавлена в шапку `HomeScreen`.
7. **Сборка**: `.\gradlew assembleDebug` завершилась со статусом `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
app/src/main/java/com/my/music/feature/player/PlayerViewModel.kt
app/src/main/java/com/my/music/core/ui/components/ExpressivePlayerSlider.kt
app/src/main/java/com/my/music/data/repository/SettingsRepository.kt (новый)
app/src/main/java/com/my/music/data/local/SettingsRepositoryImpl.kt (новый)
app/src/main/java/com/my/music/core/di/RepositoryModule.kt
app/src/main/java/com/my/music/core/ui/theme/Theme.kt
app/src/main/java/com/my/music/core/ui/theme/AccentPalette.kt (новый)
app/src/main/java/com/my/music/MainActivity.kt
app/src/main/java/com/my/music/feature/settings/SettingsScreen.kt (новый)
app/src/main/java/com/my/music/feature/settings/SettingsViewModel.kt (новый)
app/src/main/java/com/my/music/feature/home/HomeScreen.kt
app/src/main/java/com/my/music/navigation/Screen.kt
app/src/main/java/com/my/music/navigation/AppNavGraph.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.9 — Expressive-караоке: «жидкие чернила» и перелив (19.10.2026)

### Что сделано
1. **Караоке-строка «жидкие чернила»** (`LyricLineItem` — вынесен в отдельный композабл):
   - Вместо жёсткой границы заливки — smoothstep-кромка (полуширина 2.8%): переход «пропето / не пропето» стал мягким.
   - **Тлеющий фронт**: тёмное свечение прямо на границе заливки, подкрашенное `highlightColor` (primary палитры обложки или акцент темы).
   - **Шиммер-перелив**: светлая волна (полуширина 9%) пробегает по строке за 2800 мс — только у активной строки и только когда играет музыка.
   - Градиент строится по критическим точкам (кромка + фронт волны) с компонентным смешением цветов — до 8 стопов.
2. **Единая пружина фокуса** (dampingRatio 0.62 — лёгкий Expressive-овершут) ведёт всё сразу:
   - вес 600 → 800, насыщенность остальных строк — 600;
   - масштаб 1.0 → 1.05 от левого края (`TransformOrigin(0f, 0.5f)` — текст не «уезжает» при росте);
   - свечение primary: alpha 0 → 0.55, blur 3 → 16;
   - альфы: неактивные строки — **0.4** (как просил пользователь), «непропетое» у активной — 0.62, «пропетое» — 1.0.
3. **Производительность**:
   - прогресс заливки читается ТОЛЬКО активной строкой (условная ветка `when` вызывает лямбду прогресса — неактивные не подписаны на 150-мс опрос позиции);
   - шиммер живёт в `rememberInfiniteTransition` внутри условия «активная + играет» — на паузе анимация не крутится;
   - у прошлых/будущих строк кисть константна (полностью тусклая), перерисовки при смене строки минимальны.
4. **Автоскролл**: штатная пружинная анимация `animateScrollToItem` foundation (кастомный `animationSpec` у этого API отсутствует).
5. **Проброс `isPlaying`**: PlayerScreen → ExpressiveHeroZone → LyricsForeground → LyricsListView (управляет переливом).
6. **Сборка**: `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.10 — Благородное караоке: центрирование строки и мягкий стиль (19.10.2026)

### Что сделано
1. **Центрирование активной строки, как в Apple Music**:
   - `LazyColumn` измеряет свою видимую высоту через `onSizeChanged`;
   - `contentPadding` = (высота/2 − полстроки) сверху и снизу — любую строку можно поставить в самый центр списка;
   - автоскролл `animateScrollToItem(idx, scrollOffset = -центр)` — активная строка всегда встаёт ровно по центру видимой области;
   - при открытии текстов и смене трека — мгновенный `scrollToItem` без «пролёта» через весь список (`activeIndex` сбрасывается при смене текста).
2. **Смягчение стиля** (вместо «кислотного» — спокойное, дворянское):
   - пружина фокуса: `dampingRatio` 0.62 → **0.85** — строка плавно растёт без подпрыгивания;
   - свечение: blur 16 → **8**, alpha 0.55 → **0.2** — мягкое выделение вместо горения;
   - вес шрифта: 600→800 → **500→700** — мягче и читаемее;
   - шиммер едва заметный: интенсивность 0.5 → **0.2**, полоса шире (0.12) — просто лёгкий блик;
   - тлеющий фронт чернил приглушён: `tipColor` alpha **0.7**.
3. **Сборка**: `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.11 — Починка текстов, возврат волны и соседние строки (19.10.2026)

### Что сделано
1. **Скролл текстов**:
   - `LazyColumn.contentPadding` — строго `PaddingValues(top = 50.dp, bottom = 50.dp)`;
   - автоскролл `animateScrollToItem(idx, scrollOffset = -центр)` — активная строка встаёт по центру видимой области, 50dp-отступы не дают упираться в границы карточки;
   - `Text` явно `softWrap = true`, `maxLines = Int.MAX_VALUE` — длинные строки не обрезаются;
   - подпись «Синхронный текст · LRCLIB» — за пределами LazyColumn (закреплена под списком).
2. **Возврат волны**: `ExpressivePlayerSlider` снова волновой — анимированная синусоида (фаза 1600 мс, читается в фазе отрисовки, без рекомпозиций), сужение к краям, проигранная часть — акцентная волна слева направо, буфер — плотнее, бегунок-точка с пружиной и ореолом. На паузе амплитуда плавно (500 мс) уходит в 0 — волна выпрямляется в линию. `isPlaying` проброшен из `PlayerProgressSection`.
3. **Караоке**: активная строка закрашивается `Brush.linearGradient` слева направо (smoothstep-кромка + тлеющий фронт сохранены); альфы строк: неактивные **0.4**, соседние с активной **0.7** (новый параметр `isNeighbor`).
4. **Сборка**: `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/core/ui/components/ExpressivePlayerSlider.kt
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.12 — Чистая M3-типографика текстов вместо караоке (20.10.2026)

### Что сделано
1. **Удалён караоке-эффект**: убраны `Brush.linearGradient`, расчёт прогресса закрашивания, smoothstep-кромка, тлеющий фронт, шиммер и все хелперы (`buildKaraokeBrush`, `karaokeColorAt`, `smoothBlend`, `blendColors`, константы `KARAOKE_*`).
2. **Два состояния строки**:
   - активная — крупнее (анимация 16→22sp, как `titleLarge`), `SemiBold` (400→600), акцентный цвет палитры (при тёмном акценте — яркий `onSurface` по порогу luminance 0.35);
   - неактивные синхронного текста — `bodyLarge` 16sp, `Normal`, `onSurfaceVariant` alpha 0.5; обычный (несинхронный) текст — полностью читаемый `onSurfaceVariant`.
3. **Плавные переходы**: `animateColorAsState` (320 мс) для цвета, `animateFloatAsState` (пружина 0.8) для размера и `tween` для веса; строка растёт и меняет цвет без резкого скачка.
4. **Автоскролл**: опрос позиции обновляет только `activeIndex`, а прокрутка вынесена в `LaunchedEffect(activeIndex, viewportHeightPx)` — `animateScrollToItem` с центрирующим `scrollOffset`; первое позиционирование — мгновенный `scrollToItem` (без пролёта через весь текст).
5. **Чистка**: из цепочки текстов убраны неиспользуемые `isPlaying`, `activeProgress`, `isPast`, `isNeighbor`, замер ширины текста и `graphicsLayer`-масштаб.
6. **Сборка**: `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.13 — Стабильный лейаут и караоке «по словам» (20.10.2026)

### Что сделано
1. **Убраны прыжки активной строки**: `fontSize` больше не анимируется — у всех строк базовые 18sp / lineHeight 24sp. Активная строка растёт `graphicsLayer`-масштабом 1.0 → 1.3 (пружина 0.8, `TransformOrigin(0f, 0.5f)`); высота лейаута и позиция списка не меняются.
2. **Контраст неактивного текста**: `onSurfaceVariant` без альфы (вместо alpha 0.5) — строки читаемы, но менее акцентны.
3. **Караоке «по словам»**:
   - строка разбивается на слова `split(" ")` и выводится через `FlowRow` (перенос длинных строк сохранён);
   - в `LyricsListView` снова считается `activeProgress = (позиция − начало строки) / длительность строки`;
   - слово окрашивается в `highlightColor`, если `index < progress × words.size`, иначе — в `onSurfaceVariant`;
   - цвет каждого слова анимируется `animateColorAsState` (180 мс); прогресс читает только активная строка.
4. **Автоскролл**: `LaunchedEffect(activeIndex, viewportHeightPx)` с центрирующим `scrollOffset` сохранён; постоянная высота строк исключает дёрганье при смене индекса.
5. **Сборка**: `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.14 — Возврат стандартного text layout и нижний отступ (20.10.2026)

### Что сделано
1. **Убрано масштабирование**: `graphicsLayer`-scale 1.3 полностью удалён из `LyricLineItem` — текст больше не выходит за правый край, `FlowRow` корректно переносит слова.
2. **Акцент активной строки без масштаба**:
   - активная — **20sp, SemiBold**; непропетые слова `onSurface` (белые), пропетые по прогрессу плавно уходят в акцент `highlightColor`;
   - неактивные — **18sp, Normal**, `onSurfaceVariant` без альфы;
   - общий `lineHeight = 26.sp` стабилизирует высоту строки.
3. **Нижний отступ списка**: `contentPadding = PaddingValues(top = 50.dp, bottom = 100.dp)` — последняя строка не заходит под подпись и нижнюю панель.
4. **Подпись** «Синхронный текст · LRCLIB» остаётся вне `LazyColumn` — под списком, прижата к низу карточки и не перекрывает скролл.
5. **Сборка**: `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.15 — Гибрид: плавный градиент при стабильном лейауте (20.10.2026)

### Что сделано
1. **Возвращена плавная заливка всей строки**: вместо `FlowRow` с покраской отдельных слов — единый `Text` с `TextStyle.brush`. `Brush.linearGradient` растягивается по границам строки, прогресс анимируется `animateFloatAsState` (240 мс, `LinearEasing`).
2. **Цвета градиента**: слева пропетая часть — `highlightColor` (primary), справа непропетая — `onSurfaceVariant`; между ними мягкая кромка ±5% ширины строки (4 стопа без дублей).
3. **Стабильный лейаут**: `graphicsLayer`/scale не используются; у всех строк фиксированные 18sp и `lineHeight 26.sp`; активная выделяется только `SemiBold` и градиентом. Перенос длинных строк — штатный text layout.
4. **Сохранены фиксы**: `contentPadding(top = 50.dp, bottom = 100.dp)`, подпись «Синхронный текст · LRCLIB» вне `LazyColumn`, центрирующий автоскролл через `LaunchedEffect(activeIndex, viewportHeightPx)`.
5. **Сборка**: `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 4.16 — Бегунок по волне, фиолетовый дефолт и артист в шапке (20.10.2026)

### Что сделано
1. **Thumb на волне**: в `ExpressivePlayerSlider` Y-координата бегунка и ореола считается по той же синусоиде, что и Path: `centerY − amp × sin(πu) × sin(2π·span·u/wavelength + phase)`. Кружок едет по гребням, а на паузе (амплитуда 0) возвращается на центральную линию.
2. **Фиолетовая тема по умолчанию**:
   - `AppSettings.dynamicCoverColors` и чтение из SharedPreferences — дефолт `false`;
   - `MyMusicTheme.dynamicColor` — дефолт `false` (фиксированная фиолетовая палитра вместо системного Material You);
   - выключенный переключатель → акцент из настроек (по умолчанию фиолетовый), включённый → палитра обложки в плеере.
3. **Шапка плеера**: верхняя панель теперь двухстрочная — «СЕЙЧАС ИГРАЕТ» (labelSmall, alpha 0.6) и имя артиста (titleLarge, Bold) с мягким свечением `Shadow(blurRadius = 8f, color = primary alpha 0.55)`; длинные имена обрезаются ellipsis.
4. **Сборка**: `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.

### Файлы
```
app/src/main/java/com/my/music/core/ui/components/ExpressivePlayerSlider.kt
app/src/main/java/com/my/music/data/repository/SettingsRepository.kt
app/src/main/java/com/my/music/data/local/SettingsRepositoryImpl.kt
app/src/main/java/com/my/music/core/ui/theme/Theme.kt
app/src/main/java/com/my/music/MainActivity.kt
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 5 — Тесты и QA-прочёс (20.10.2026)

### Что сделано
1. **Тестовая песочница (JVM, без эмулятора)**:
   - В `app/build.gradle.kts` добавлены `junit:4.13.2`, `robolectric:4.14.1`, `kotlinx-coroutines-test:1.9.0`, `androidx.arch.core:core-testing:2.2.0`, `io.mockk:mockk:1.13.13`, `androidx.test:core-ktx:1.6.1`, `androidx.room:room-testing:2.6.1`.
   - Включены `testOptions.unitTests.isIncludeAndroidResources = true` и `isReturnDefaultValues = true`.
2. **Написаны 31 JVM-тест**:
   - `LrcParserTest` (10): `[mm:ss.xx]`, `[mm:ss.x]`, `[mm:ss.xxx]`, несколько меток в строке, `[offset:+ms]`/`[offset:-ms]` с клампом в 0, караоке-теги `<mm:ss.xx>`, сортировка, фолбэк на несинхронный текст, CRLF, пустые/метаданные-only входы.
   - `PlayerControllerQueueTest` (14): `playNext` и `playPrevious` для Repeat OFF/ALL/ONE, авто-окончание трека через `Player.Listener`, цикл переключения режима, возврат в начало после 3 секунд, shuffle сохраняет текущий трек первым и восстанавливает исходный порядок при выключении.
   - `HomeViewModelGenreTest` (4): группировка «Твои жанры» из истории и лайков, дедупликация по `id`, trim жанра, отбрасывание пустых жанров, сортировка по размеру, лимит 20 недавних, реактивность чартов. Отдельного `CollectionsViewModel` в проекте нет — логика живёт в `HomeViewModel`.
   - `LikedTracksRepositoryTest` (3, Robolectric + Room `inMemoryDatabaseBuilder`): добавление/удаление, `toggleLike`, реактивный Flow (пусто → лайк → снятие), сортировка `likedAt DESC`.
3. **QA-фиксы**:
   - `PlayerController.playNext()` по умолчанию больше не зацикливает очередь: ручной Next при Repeat OFF на последнем треке остаётся на месте; зацикливание — только у ALL/авто-перехода.
   - `PlayerController.release()` отменяет внутренний `CoroutineScope`; запись в историю обёрнута в try/catch (сбой Room не ломает воспроизведение).
   - `runCatching` больше не глотает `CancellationException`: добавлен `core/util/ResultExt.kt`, применён в SoundCloud-репозиториях, чартах, LRCLIB и `HomeViewModel`.
   - `PlayerPalette`: кэш палитр стал потокобезопасным LRU на 32 записи (раньше — неограниченный `mutableMapOf` с гонками main/IO), `ImageLoader` создаётся один раз на процесс вместо нового на каждый трек.
   - `PlayerQueueSheet`: key списка теперь по `track.id`, а не по индексу — состояние не «переезжает» при удалении.
   - Добавлены стабильные ключи в статические списки Wave и скелетонов Home.
   - Удалена мёртвая ветка `e is StreamInfo` в логе `resolveStreamUrl`.
4. **Проверки**:
   - `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.
   - `.\gradlew test` — `BUILD SUCCESSFUL`, 31/31 тестов в debug и release, 0 failures, 0 errors.
   - `.\gradlew lint` — `BUILD SUCCESSFUL`, 0 critical/error, 20 warning (предложения обновить версии, exported MediaSessionService, non-lambda offset, отсутствие иконки приложения). Версии по правилам проекта не менялись.

### Файлы
```
app/build.gradle.kts
app/src/test/java/com/my/music/testutil/MainDispatcherRule.kt (новый)
app/src/test/java/com/my/music/data/source/lyrics/LrcParserTest.kt (новый)
app/src/test/java/com/my/music/core/player/PlayerControllerQueueTest.kt (новый)
app/src/test/java/com/my/music/feature/home/HomeViewModelGenreTest.kt (новый)
app/src/test/java/com/my/music/data/local/LikedTracksRepositoryTest.kt (новый)
app/src/main/java/com/my/music/core/util/ResultExt.kt (новый)
app/src/main/java/com/my/music/core/player/PlayerController.kt
app/src/main/java/com/my/music/data/source/soundcloud/SoundCloudRepositoryImpl.kt
app/src/main/java/com/my/music/data/source/soundcloud/SoundCloudChartsRepositoryImpl.kt
app/src/main/java/com/my/music/data/source/lyrics/LyricsRepositoryImpl.kt
app/src/main/java/com/my/music/feature/home/HomeViewModel.kt
app/src/main/java/com/my/music/core/ui/theme/PlayerPalette.kt
app/src/main/java/com/my/music/core/ui/components/PlayerQueueSheet.kt
app/src/main/java/com/my/music/feature/wave/WaveScreen.kt
app/src/main/java/com/my/music/feature/home/HomeScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 6 — Воспоминания, Моя волна и Top 10 (20.10.2026)

### Что сделано
1. **База данных и «Воспоминания»**:
   - `LikedTrackEntity` дополнена полями `latitude: Double?`, `longitude: Double?`, `season: String?`; маппер `Track.toLikedEntity()` принимает эти значения (по умолчанию null).
   - `AppDatabase` обновлена до версии **4**; миграции не пишутся — в `DatabaseModule` уже включён `fallbackToDestructiveMigration()`.
   - `LikedTracksDao.getLikedTracksBySeason(season)` + `LikedTracksRepository.getLikedTracksBySeason()` — реактивный поток лайков текущего сезона.
   - `SeasonProvider` (core/util): Winter (дек–фев), Spring (мар–май), Summer (июн–авг), Autumn (сен–ноя); сезон хранится строкой.
   - `LocationProvider` + `FusedLocationProvider`: `FusedLocationProviderClient.getLastLocation()` с таймаутом 4 с, проверкой `ACCESS_FINE/COARSE_LOCATION`, безопасным фолбэком в null (нет разрешения/ошибка/таймаут). Добавлена зависимость `play-services-location:21.3.0` и разрешения в манифест.
   - `PlayerViewModel.toggleLike()`: при постановке лайка запрашивает последнюю геопозицию и текущий сезон и сохраняет их в Room; при снятии лайка метаданные не нужны. Сбой геолокации не блокирует лайк.
   - `HomeViewModel` наблюдает `getLikedTracksBySeason(текущий сезон)` и отдаёт `memories`; `HomeScreen` показывает карусель «Воспоминания» перед «Недавними».
2. **«Моя волна»**:
   - Новый `WaveRepository` / `WaveRepositoryImpl`: до 10 случайных лайков, до 5 недавних треков истории, до 5 находок по жанрам из лайков (поиск SoundCloud через NewPipe), фолбэк на Top 10 при отсутствии жанров; итог перемешивается и дедуплицируется, считается вклад каждого источника.
   - `WaveViewModel`: генерирует подборку, сохраняет разбивку по источникам и сразу запускает очередь через общий `PlayerController.setQueueAndPlay()`; повторное нажатие во время генерации игнорируется.
   - `WaveScreen` переписан: заглушки «Скоро» и фейковые чипы настроений убраны, добавлена большая градиентная кнопка «Включить волну» с пульсирующим ореолом, press-анимацией и индикатором загрузки; под кнопкой — чипы «N любимых / N из истории / N находок» и список треков с обложками, номерами и запуском с выбранной позиции.
   - Ошибки сети показываются карточкой; пустые данные дают подсказку «добавьте лайки или послушайте треки».
3. **Top 50 → Top 10**:
   - Заголовок на главной заменён на «Top 10 SoundCloud», обновлены комментарии (карусель 1..10, KDoc чартов и кэша).
   - Внутренние идентификаторы (`top50`, `TOP_50`) оставлены как ключ кэша Room для совместимости; API SoundCloud отдаёт 10 треков, поэтому нумерация и так ограничена 1..10.
4. **Проверки**:
   - `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.
   - `.\gradlew test` — `BUILD SUCCESSFUL`, 38/38 тестов в debug и release (31 прежний + SeasonProvider 2 + WaveRepository 3 + Room-сезон 1 + Воспоминания HomeViewModel 1), 0 failures, 0 errors.
   - `.\gradlew lint` — `BUILD SUCCESSFUL`, 0 critical/error, 21 warning (17 — предложения обновить версии, включая play-services-location; 2 — non-lambda offset; отсутствие иконки приложения; exported MediaSessionService).
5. **Ограничение (исправлено в фазе 6.1)**: runtime-запрос геопозиции в этой фазе не добавлялся (по ТЗ менялся только манифест), поэтому координаты сохранялись лишь при ручной выдаче доступа в системных настройках.

### Файлы
```
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/my/music/core/di/RepositoryModule.kt
app/src/main/java/com/my/music/core/location/LocationProvider.kt (новый)
app/src/main/java/com/my/music/core/location/FusedLocationProvider.kt (новый)
app/src/main/java/com/my/music/core/util/SeasonProvider.kt (новый)
app/src/main/java/com/my/music/data/local/AppDatabase.kt
app/src/main/java/com/my/music/data/local/dao/LikedTracksDao.kt
app/src/main/java/com/my/music/data/local/entity/LikedTrackEntity.kt
app/src/main/java/com/my/music/data/local/LikedTracksRepositoryImpl.kt
app/src/main/java/com/my/music/data/repository/LikedTracksRepository.kt
app/src/main/java/com/my/music/data/repository/WaveRepository.kt (новый)
app/src/main/java/com/my/music/data/source/wave/WaveRepositoryImpl.kt (новый)
app/src/main/java/com/my/music/feature/player/PlayerViewModel.kt
app/src/main/java/com/my/music/feature/home/HomeViewModel.kt
app/src/main/java/com/my/music/feature/home/HomeScreen.kt
app/src/main/java/com/my/music/feature/wave/WaveViewModel.kt (новый)
app/src/main/java/com/my/music/feature/wave/WaveScreen.kt
app/src/main/java/com/my/music/data/repository/SoundCloudChartsRepository.kt
app/src/main/java/com/my/music/data/local/entity/ChartCacheEntity.kt
app/src/test/java/com/my/music/core/util/SeasonProviderTest.kt (новый)
app/src/test/java/com/my/music/data/source/wave/WaveRepositoryTest.kt (новый)
app/src/test/java/com/my/music/data/local/LikedTracksRepositoryTest.kt
app/src/test/java/com/my/music/feature/home/HomeViewModelGenreTest.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 6.1 — Runtime-разрешение геопозиции (20.10.2026)

### Что сделано
1. **Общий хелпер разрешений**:
   - Новый `LocationPermissions` (core/location): список `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`, проверка любого из них (`hasPermission`) и переход в карточку приложения через `ACTION_APPLICATION_DETAILS_SETTINGS`.
   - `FusedLocationProvider` переведён на общий хелпер — логика проверки больше не дублируется.
2. **Плеер**:
   - `rememberLauncherForActivityResult(RequestMultiplePermissions())`: при первом лайке без доступа показывается стандартный диалог Android; после выдачи координаты сразу сохраняются.
   - Лайк не теряется при отказе: `pendingLike` срабатывает после ответа и сохраняет трек без координат.
   - Ненавязчивый баннер «Геопозиция выключена — „Воспоминания“ не сохранят место лайка»; кнопка «Разрешить» вызывает диалог, после отказа превращается в «Настройки» и открывает системные настройки приложения.
   - Статус разрешения перечитывается при возврате в приложение (`Lifecycle.Event.ON_RESUME`), поэтому баннер исчезает после выдачи доступа в настройках.
3. **Экран настроек**:
   - Новая секция «ГЕОПОЗИЦИЯ» со статусом «Геопозиция: Разрешено / Запрещено», иконкой и пояснением, зачем нужен доступ.
   - Кнопка «Проверить разрешения»: перечитывает статус, а при запрете открывает настройки приложения; статус также обновляется при возврате из системных настроек.
4. **Проверки**:
   - `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.
   - `.\gradlew test` — `BUILD SUCCESSFUL`, 41/41 тест в debug и release (добавлены 3 теста `LocationPermissionsTest`: отказ по умолчанию, достаточность приблизительного доступа, корректный intent системных настроек), 0 failures, 0 errors.
   - `.\gradlew lint` — `BUILD SUCCESSFUL`, 0 critical/error, 21 warning (без новых замечаний).

### Файлы
```
app/src/main/java/com/my/music/core/location/LocationPermissions.kt (новый)
app/src/main/java/com/my/music/core/location/FusedLocationProvider.kt
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
app/src/main/java/com/my/music/feature/settings/SettingsScreen.kt
app/src/test/java/com/my/music/core/location/LocationPermissionsTest.kt (новый)
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 6.2 — Expressive-редизайн экрана текста (20.10.2026)

### Что сделано
1. **Чистый экран без панелей**:
   - Удалены обе стеклянные поверхности: верхняя карточка-витрина и нижняя панель вокруг строк.
   - Осталась только парящая шапка: миниатюра обложки 42dp, название и артист без подложки.
   - Удалена служебная подпись «Синхронный текст · LRCLIB» внизу — экран стал чище.
2. **Типографика**:
   - Базовая строка увеличена до 24sp / lineHeight 34sp (было 18/26); длинные строки по-прежнему свободно переносятся.
   - Физический размер шрифта одинаков у всех строк — лейаут не прыгает.
   - Активная строка: Bold, масштаб 1.08 через `graphicsLayer` с пружиной, мягкое свечение `Shadow` (primary, blur 18), градиент от primary к onSurface по прогрессу.
   - Неактивные строки: onSurface с alpha 0.36; для несинхронного текста — alpha 0.82 без выделения.
   - Дальние строки (дистанция ≥ 3) дополнительно размываются на 1.5–3.5dp (RenderEffect на API 31+, ниже — только прозрачность).
3. **Отступы и маски**:
   - `contentPadding` 64dp сверху / 136dp снизу для синхронного текста (28/136 — для обычного), межстрочный интервал 18dp.
   - Верхняя градиентная маска 44dp и нижняя 124dp растворяют строки в цвете `surface` — текст не упирается в контролы.
4. **Сохранённая механика**:
   - Переход «обложка ↔ текст» с blur-уходом фона не изменялся.
   - Логика автоскролла и центрирования прежняя; обновлён только `halfLinePx` под новую высоту строки (22dp).
5. **Проверки**:
   - `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.
   - `.\gradlew test` — `BUILD SUCCESSFUL`, 41/41 тест, 0 failures, 0 errors.
   - `.\gradlew lint` — `BUILD SUCCESSFUL`, 0 critical/error, 21 warning (без новых замечаний).

### Файлы
```
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 6.5 — Настоящий фоновый плеер и безопасные миграции Room (20.10.2026)

### Что сделано
1. **MediaSessionService как единственный владелец ExoPlayer**:
   - `MusicService` создаёт `ExoPlayer` в `onGetSession()`, оборачивает его в `MediaSession` и возвращает контроллерам.
   - Сервис сам показывает MediaStyle-уведомление, получает foreground-статус и переживает уход приложения в фон.
   - `ExoPlayer` больше не является Hilt-синглтоном: удалён `PlayerModule`, чтобы UI не мог случайно освободить или создать второй плеер.
   - `onDestroy()` освобождает сначала `MediaSession`, затем `ExoPlayer` — без «мёртвого» общего плеера.
2. **PlayerController переведён на MediaController**:
   - Контроллер подключается к сервису через `SessionToken` и `MediaController.Builder` с application context.
   - Все команды (play/pause, seek, скорость, позиция, буфер) идут через `MediaController`.
   - Сохранены очередь, shuffle/repeat, ручной Next при `Repeat OFF`, запись истории и повтор трека после ошибки.
   - Кнопки Prev/Next из уведомления по-прежнему работают с очередью через `ForwardingPlayer`.
3. **Метаданные уведомления**:
   - В `MediaItem` добавлены `mediaId`, `MediaMetadata` с title, artist и artworkUri — в шторке отображаются название, исполнитель и обложка.
4. **Фон и блокировка экрана**:
   - ExoPlayer настроен на `AudioAttributes` с обработкой аудиофокуса.
   - Включены `setHandleAudioBecomingNoisy(true)` и `setWakeMode(C.WAKE_MODE_NETWORK)` (partial wake lock + Wi-Fi lock).
   - В манифест добавлен `WAKE_LOCK`.
5. **Безопасные миграции Room**:
   - Убран `fallbackToDestructiveMigration()`.
   - Включён `exportSchema = true`, путь схем — `app/schemas` через `room.schemaLocation`.
   - Добавлены реальные миграции: 1→2 создаёт `chart_cache`, 2→3 добавляет `latitude`/`longitude`, 3→4 добавляет `season`.
   - Фактическая история: версия 3 не выпускалась отдельно, поэтому координаты закреплены за v3, а сезон — за v4; существующие лайки и история не теряются.
   - Добавлен JVM-тест миграции v1→v4 с проверкой сохранности лайка, истории и создания кэша чартов.
6. **Транзакционный лайк**:
   - `LikedTracksDao.toggleLikedTrack()` помечен `@Transaction`: чтение и запись выполняются атомарно, двойной тап не оставляет БД в неверном состоянии.
   - `LikedTracksRepositoryImpl.toggleLike()` переведён на новый метод DAO.
7. **Проверки**:
   - `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.
   - `.\gradlew test` — `BUILD SUCCESSFUL`, 43/43 теста в debug и release, 0 failures, 0 errors.
   - `.\gradlew lint` — `BUILD SUCCESSFUL`, 0 errors, 21 warning (без новых замечаний).

### Файлы
```
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/my/music/core/player/MusicService.kt
app/src/main/java/com/my/music/core/player/PlayerController.kt
app/src/main/java/com/my/music/core/player/PlayerModule.kt (удалён)
app/src/main/java/com/my/music/data/local/AppDatabase.kt
app/src/main/java/com/my/music/data/local/DatabaseModule.kt
app/src/main/java/com/my/music/data/local/DatabaseMigrations.kt (новый)
app/src/main/java/com/my/music/data/local/dao/LikedTracksDao.kt
app/src/main/java/com/my/music/data/local/LikedTracksRepositoryImpl.kt
app/schemas/com.my.music.data.local.AppDatabase/4.json (новый)
app/src/test/java/com/my/music/core/player/PlayerControllerQueueTest.kt
app/src/test/java/com/my/music/data/local/DatabaseMigrationTest.kt (новый)
PROGRESS.md
MEMORY.md
```

---

## ✅ Фаза 7 — Умная «Моя волна»: сессии, Last.fm и feedback (21.10.2026)

### Что сделано
1. **Сессионная бесконечная волна**:
   - В `WaveRepository` добавлены `WaveSession`, `WaveBatch` и `StateFlow<WaveSession?> activeSession`; методы `startSession()`, `nextBatch()`, `reset()`.
   - Старый `generateWave()` сохранён как совместимый вызов «новая сессия + первая пачка».
   - Микс пачки: 40% похожие артисты Last.fm, 30% лайки, 20% история, 10% жанровые находки; пустые источники добираются из остальных, последний фолбэк — Top 10 SoundCloud.
   - Внутри сессии ведётся `shownTrackIds`, поэтому треки не повторяются; накопительные счётчики источников обновляются после каждой пачки.
   - `reset()` сразу обнуляет `activeSession`, затем ждёт mutex — летящий `nextBatch` не может дописать старую пачку в новую очередь.
2. **Last.fm и семена вкусов**:
   - Добавлены `LastFmApi`, DTO, `LastFmRepository` и `LastFmRepositoryImpl` (`artist.getsimilar`).
   - Семена артистов: топ вкусов + история + лайки; семена жанров: топ вкусов + лайки + история.
   - Похожие артисты ищутся в SoundCloud через NewPipe; ключ Last.fm берётся из `BuildConfig.LASTFM_API_KEY` (gradle property или env `LASTFM_API_KEY`). Без ключа волна работает на локальных источниках и чарте.
3. **Локальный профиль вкусов и feedback**:
   - Room v5: `genre_weights`, `artist_weights`, `user_preferences`; миграция `MIGRATION_4_5`, схема `5.json`.
   - `TasteRepository`: дослушивание >80% — `+1`, скип <30 секунд — `-1`, лайк — `+3`, снятие лайка/дизлайк — `-3`; жанры и артисты нормализуются.
   - `PlayerViewModel` считает реально проигранное время и пишет feedback при смене трека; лайки и снятие лайков также обновляют веса.
4. **Автодозагрузка в плеере**:
   - При 3 оставшихся треках `PlayerViewModel` запрашивает пачку из 12 и дописывает её через `PlayerController.appendToQueue()` без остановки текущего трека.
   - `appendToQueue()` отбрасывает пустые id и дубликаты, сохраняет исходную очередь для корректного выключения shuffle.
   - Ручной запуск трека из поиска или медиатеки сбрасывает сессию волны, чтобы автодозагрузка не подмешивала старую волну.
5. **UI волны и настройки**:
   - `WaveViewModel` синхронизирует список с очередью плеера, показывает накопительные счётчики всей сессии, жанры, RU-приоритет и состояние завершения.
   - `WaveScreen`: бейджи «RU в приоритете»/«Все языки» и жанров, счётчик похожих, кнопки «Новая волна» и «Сбросить», сообщение о завершении волны.
   - В настройках добавлен переключатель «Русская музыка в приоритете» (`pref_ru_language`), при включении кириллические треки идут первыми.
6. **Проверки**:
   - `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.
   - `.\gradlew test` — `BUILD SUCCESSFUL`, 50/50 тестов в debug и release, 0 failures, 0 errors (+7 тестов: вкусы, сессия/дедупликация, RU-приоритет, appendToQueue, миграция v5).
   - `.\gradlew lint` — `BUILD SUCCESSFUL`, 0 errors, 21 warning (без новых замечаний).

### Ограничения
- Last.fm-ключ не входит в репозиторий; без него качество волны ниже, но приложение полностью работоспособно.
- Настроения волны (бодрая/спокойная/по любимым/открытия) пока не реализованы — это следующий этап.
- Координаты и сезон по-прежнему сохраняются в лайках, но в карточках «Воспоминаний» ещё не отображаются.

### Файлы
```
app/build.gradle.kts
app/schemas/com.my.music.data.local.AppDatabase/5.json (новый)
app/src/main/java/com/my/music/core/di/RepositoryModule.kt
app/src/main/java/com/my/music/core/network/NetworkModule.kt
app/src/main/java/com/my/music/core/player/PlayerController.kt
app/src/main/java/com/my/music/data/local/AppDatabase.kt
app/src/main/java/com/my/music/data/local/DatabaseMigrations.kt
app/src/main/java/com/my/music/data/local/DatabaseModule.kt
app/src/main/java/com/my/music/data/local/TasteRepositoryImpl.kt (новый)
app/src/main/java/com/my/music/data/local/dao/TasteDao.kt (новый)
app/src/main/java/com/my/music/data/local/entity/TasteEntities.kt (новый)
app/src/main/java/com/my/music/data/repository/LastFmRepository.kt (новый)
app/src/main/java/com/my/music/data/repository/TasteRepository.kt (новый)
app/src/main/java/com/my/music/data/repository/WaveRepository.kt
app/src/main/java/com/my/music/data/source/lastfm/LastFmApi.kt (новый)
app/src/main/java/com/my/music/data/source/lastfm/LastFmRepositoryImpl.kt (новый)
app/src/main/java/com/my/music/data/source/wave/WaveRepositoryImpl.kt
app/src/main/java/com/my/music/feature/player/PlayerViewModel.kt
app/src/main/java/com/my/music/feature/settings/SettingsScreen.kt
app/src/main/java/com/my/music/feature/settings/SettingsViewModel.kt
app/src/main/java/com/my/music/feature/wave/WaveScreen.kt
app/src/main/java/com/my/music/feature/wave/WaveViewModel.kt
app/src/test/java/com/my/music/core/player/PlayerControllerQueueTest.kt
app/src/test/java/com/my/music/data/local/DatabaseMigrationTest.kt
app/src/test/java/com/my/music/data/local/TasteRepositoryTest.kt (новый)
app/src/test/java/com/my/music/data/source/wave/WaveRepositoryTest.kt
README.md
PROGRESS.md
MEMORY.md
```

---

## ✅ Хотфикс — клик Play/Pause и качество волны (21.10.2026)

### Что сделано
1. **Кнопка Play/Pause больше не перематывает трек**:
   - `ExpressivePlayerSlider`: сенсорная высота уменьшена с 46 до 38dp, а тап и горизонтальный драг принимаются только в центральной полосе ±14dp вокруг волны. Раньше невидимая полоса слайдера перехватывала касания, предназначенные кнопкам под ней.
   - `PlayerControlIsland`: зазор между волной и рядом кнопок увеличен с 8 до 14dp — даже промах пальцем выше кнопки не попадает в зону перемотки.
   - `PlayerProgressSection`: заглушка загрузки выровнена по высоте (38dp), чтобы при Crossfade «загрузка → слайдер» лейаут не прыгал.
2. **Волна: язык, артист и жанр**:
   - Кандидаты каждого источника делятся на строгий и запасной пул. При RU-приоритете кириллица заполняет квоты первой, латиница подключается только при нехватке своих треков (раньше она просто сортировалась в конец, но всё равно попадала в пачку).
   - Last.fm: похожие артисты с кириллицей проверяются первыми; результаты поиска SoundCloud фильтруются по реальному совпадению артиста (без регистра и пунктуации), чужие треки — только фолбэк.
   - Жанры: находка считается валидной только при совпадении жанра по словам и с алиасами (рэп/rap, трэп/trap, хаус/house и т.д.); мусорные подписи («Music», «Podcast», «SoundCloud») не используются как семена.
   - Соотношение 40/30/20/10 сохранено, порядок добора: строгие в квотах → строгие сверх квот → запасные → чарты.
3. **Проверки**:
   - `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.
   - `.\gradlew test` — `BUILD SUCCESSFUL`, 53/53 теста в debug и release, 0 failures, 0 errors (+3 теста: язык при RU-приоритете, точный жанр, совпадение артиста).

### Файлы
```
app/src/main/java/com/my/music/core/ui/components/ExpressivePlayerSlider.kt
app/src/main/java/com/my/music/core/ui/components/ExpressivePlayerComponents.kt
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
app/src/main/java/com/my/music/data/source/wave/WaveRepositoryImpl.kt
app/src/test/java/com/my/music/data/source/wave/WaveRepositoryTest.kt
PROGRESS.md
MEMORY.md
```

---

## ✅ Сессия 9 — Офлайн-режим: скачивание треков (21.10.2026)

### Что сделано
1. **Room v6 — таблица скачанных треков**:
   - `DownloadedTrackEntity` (`downloaded_tracks`): `id`, `title`, `artist`, `artworkUrl`, `durationMs`, `localFilePath`, `downloadedAt`.
   - `DownloadedTracksDao`: `insert` (REPLACE), `delete`, `getById`, `isDownloaded(): Flow<Boolean>`, `getDownloadedIds(): Flow<List<String>>`, `getAll(): Flow<List<DownloadedTrackEntity>>`.
   - `AppDatabase` → версия 6; `MIGRATION_5_6` создаёт таблицу; схема `app/schemas/com.my.music.data.local.AppDatabase/6.json` экспортирована.
   - Доменная модель `DownloadedTrack` + мапперы; `DownloadRepository` / `DownloadRepositoryImpl`: проверка существования файла, автоочистка битых записей, удаление файла вместе с записью; Hilt-биндинги в `RepositoryModule` и `DatabaseModule`.
2. **WorkManager — фоновое скачивание**:
   - Добавлена зависимость `androidx.work:work-runtime-ktx:2.9.1` (единственная новая версия в проекте).
   - `DownloadTrackWorker` (CoroutineWorker): резолвит поток через `SoundCloudRepository.resolveStreamUrl`, качает OkHttp в `filesDir/downloads/`, публикует прогресс в WorkManager, затем сохраняет запись в Room. Используется отдельный OkHttp-клиент: у общего `callTimeout = 15 с`, который убивал длинные загрузки.
   - HLS (m3u8) одним mp3-файлом не сохранить — воркер завершает задачу ошибкой; прогрессивный MP3 резолвится приоритетно.
   - Имя файла — безопасная версия `track.id` + короткий хэш + `.mp3`: у SoundCloud `id` — это URL со слэшами и двоеточиями.
   - `DownloadManager`: `download(track)` ставит уникальную работу (`ExistingWorkPolicy.KEEP`, constraint `NetworkType.CONNECTED`), `cancel(trackId)`, `observeState(trackId): Flow<DownloadState>` (Room + WorkInfo).
   - Зависимости воркера — через Hilt `@EntryPoint` (`DownloadWorkerEntryPoint`), без дополнительной зависимости `androidx.hilt:hilt-work`.
3. **Плеер — локальный файл в приоритете**:
   - `PlayerController.resolveAndPlay()` сначала спрашивает `DownloadRepository.getLocalFile(track.id)`; если файл существует — `MediaItem` строится из `file://`-URI, сеть не используется. Нет файла — прежний путь через `resolveStreamUrl`.
   - В JVM-тестах конструктор принимает `downloadRepository = null` — 17 тестов очереди не менялись.
4. **UI**:
   - `PlayerScreen`: рядом с лайком — кнопка скачивания 46dp с тремя состояниями: стрелка вниз → круговой прогресс (детерминированный при известном размере) → галочка `DownloadDone`. Клик: скачать / отменить / ничего для уже готового трека.
   - `PlayerViewModel`: `downloadState: StateFlow<DownloadState>` через `flatMapLatest` по текущему треку и `toggleDownload()`.
   - `TrackCard`: маленькая галочка на обложке скачанного трека; `LibraryViewModel` и `SearchViewModel` отдают `downloadedIds: StateFlow<Set<String>>` из Room.
   - В манифест добавлен `ACCESS_NETWORK_STATE` — он нужен сетевому constraint'у WorkManager.
5. **Проверки**:
   - `.\gradlew assembleDebug` — `BUILD SUCCESSFUL` после каждого из четырёх шагов.
   - `.\gradlew test` — 53/53 теста, 0 failures (тест миграций обновлён под v6 и дополнительно проверяет таблицу `downloaded_tracks`).

### Ограничения
- Скачивается только прогрессивный MP3; треки, доступные лишь по HLS, офлайн недоступны.
- Обложка офлайн не сохраняется (в Room только URL для UI).
- Удаление скачанного трека пока возможно только через очистку данных приложения — отдельного экрана «Скачанное» нет.

### Файлы
```
app/build.gradle.kts
app/src/main/AndroidManifest.xml
data/model/DownloadedTrack.kt (новый)
data/local/entity/DownloadedTrackEntity.kt (новый)
data/local/dao/DownloadedTracksDao.kt (новый)
data/local/DownloadRepositoryImpl.kt (новый)
data/repository/DownloadRepository.kt (новый)
data/local/AppDatabase.kt
data/local/DatabaseMigrations.kt
data/local/DatabaseModule.kt
core/di/RepositoryModule.kt
core/download/DownloadState.kt (новый)
core/download/DownloadTrackWorker.kt (новый)
core/download/DownloadManager.kt (новый)
core/download/DownloadWorkerEntryPoint.kt (новый)
core/player/PlayerController.kt
feature/player/PlayerViewModel.kt
feature/player/PlayerScreen.kt
core/ui/components/TrackCard.kt
feature/library/LibraryViewModel.kt
feature/library/LibraryScreen.kt
feature/search/SearchViewModel.kt
feature/search/SearchScreen.kt
app/src/test/java/com/my/music/data/local/DatabaseMigrationTest.kt
app/schemas/com.my.music.data.local.AppDatabase/6.json (новый)
PROGRESS.md
MEMORY.md
```

---

## ✅ Хотфикс — Media3 DownloadManager для HLS (21.10.2026)

### Причина
- Воркер падал с `Result.failure`: SoundCloud отдаёт трек с `deliveryMethod=HLS`, а старый OkHttp-загрузчик умел скачивать только один прогрессивный файл.

### Что сделано
1. **Зависимости**: явно добавлены `androidx.media3:media3-datasource:1.5.0` и `androidx.media3:media3-database:1.5.0` (exoplayer и exoplayer-hls уже были).
2. **Кэш и DownloadManager**:
   - `OfflineMediaModule` (Hilt, `@Singleton`): `StandaloneDatabaseProvider` (в Media3 это замена `ExoDatabaseProvider`), `SimpleCache(filesDir/media_cache, NoOpCacheEvictor)`, `CacheDataSource.Factory` для плеера и Media3 `DownloadManager` с upstream `DefaultDataSource`/`DefaultHttpDataSource` (UA `MyMusicApp/1.0`).
   - Кэш один на процесс: и загрузчик, и `MusicService` используют один `SimpleCache`.
3. **`OfflineDownloadManager`**:
   - `enqueue(track, stream)`: `DownloadRequest.Builder(track.id, uri).setMimeType(...)`; HLS → `MimeTypes.APPLICATION_M3U8`, MP3 → `AUDIO_MPEG`; повторная постановка активной/готовой загрузки пропускается.
   - `observeDownload(trackId)`: `Flow<MediaDownloadState>` через `DownloadManager.Listener` + начальное состояние из `DownloadIndex` на IO.
   - `awaitCompletion(...)`: ожидание терминального состояния с публикацией прогресса (блокирующего API в Media3 нет).
   - `remove(trackId)`, `isCompleted(trackId)`.
4. **`DownloadTrackWorker`** переписан: resolve → `enqueue` → `awaitCompletion` → Room `save(mediaUrl, mimeType)`; отмена удаляет недокачанные данные; любая ошибка → `Result.failure` (WorkInfo FAILED → UI Failed).
5. **Room v7**:
   - `DownloadedTrackEntity`/`DownloadedTrack`: `mediaUrl` + `mimeType` вместо `localFilePath`; `DownloadRepository.save(track, mediaUrl, mimeType)`; `getDownloadedTrack` проверяет наличие данных в кэше Media3 (или файла после миграции).
   - `MIGRATION_6_7`: пересоздание таблицы, старые `localFilePath` → `file://...`, `mimeType = audio/mpeg`; `AppDatabase` v7, схема `7.json`.
6. **Плеер и UI**:
   - `MusicService`: `ExoPlayer` создаётся с `DefaultMediaSourceFactory(cacheDataSourceFactory)` — скачанные HLS-сегменты и MP3 играют из общего кэша без сети.
   - `PlayerController.resolveAndPlay`: скачанный трек играет по `mediaUrl`, `isHls` определяется по `mimeType`.
   - `DownloadManager` (обёртка): `observeState` объединяет Room + `MediaDownloadState` + WorkInfo; активная задача приоритетнее старых записей unique work.
   - `PlayerScreen`: `SnackbarHost` + `LaunchedEffect(downloadState)` с текстом «Не удалось скачать трек (доступен только онлайн)».

### Проверки
- `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.
- `.\gradlew testDebugUnitTest` — 54/54, 0 failures, 0 errors (+1 тест миграции 6→7: конвертация пути в `file://`-URL и `audio/mpeg`).

### Ограничения
- HLS не превращается в один mp3: офлайн-воспроизведение возможно только через `CacheDataSource` в этом приложении (файл не экспортируется).
- Обложка офлайн не сохраняется (в Room только URL для UI).
- Отдельного экрана «Скачанное» и удаления из UI по-прежнему нет — удаление возможно через очистку данных приложения.

### Файлы
```
app/build.gradle.kts
app/src/main/java/com/my/music/core/download/OfflineMediaModule.kt (новый)
app/src/main/java/com/my/music/core/download/OfflineDownloadManager.kt (новый)
app/src/main/java/com/my/music/core/download/MediaDownloadState.kt (новый)
app/src/main/java/com/my/music/core/download/DownloadTrackWorker.kt
app/src/main/java/com/my/music/core/download/DownloadManager.kt
app/src/main/java/com/my/music/core/download/DownloadWorkerEntryPoint.kt
app/src/main/java/com/my/music/core/player/MusicService.kt
app/src/main/java/com/my/music/core/player/PlayerController.kt
app/src/main/java/com/my/music/data/local/AppDatabase.kt
app/src/main/java/com/my/music/data/local/DatabaseMigrations.kt
app/src/main/java/com/my/music/data/local/DownloadRepositoryImpl.kt
app/src/main/java/com/my/music/data/local/entity/DownloadedTrackEntity.kt
app/src/main/java/com/my/music/data/model/DownloadedTrack.kt
app/src/main/java/com/my/music/data/repository/DownloadRepository.kt
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
app/src/test/java/com/my/music/data/local/DatabaseMigrationTest.kt
app/schemas/com.my.music.data.local.AppDatabase/7.json (новый)
PROGRESS.md
MEMORY.md
```

---

## ✅ Хотфикс — кнопка скачивания и перезапуск после отмены (22.10.2026)

### Причина
- UI залипал на `InProgress` из-за старой записи Media3/WorkInfo: клики уходили в `cancel()`, а не в новую задачу. В логах — `WM-WorkerWrapper: Work [...] was cancelled`, повторного запуска `DownloadTrackWorker` не было.
- `ExistingWorkPolicy.KEEP` не гарантировал новую задачу после отменённой цепочки.
- `DownloadState.Failed` не различал отмену и реальную ошибку, поэтому не было крестика и возврата к кнопке скачивания.

### Что сделано
1. **`DownloadManager`**:
   - WorkInfo — главный источник состояния. Последняя попытка выбирается по тегу `download-at:<millis>`, потому что порядок списка WorkManager не гарантирован.
   - `CANCELLED`/`FAILED` → `DownloadState.Error(CANCELLED/FAILED)`, `SUCCEEDED` → `Downloaded`. Терминальный WorkInfo приоритетнее старого прогресса Media3; осиротевшая запись Media3 без активной задачи прогрессом не считается.
   - `ExistingWorkPolicy.REPLACE`, защита от двойного тапа 700 мс, `KEY_FORCE_RESTART=true`.
   - `cancel()` теперь сразу удаляет запись Media3 (а не только отменяет WorkManager).
2. **`OfflineDownloadManager`**:
   - `enqueue(forceRestart)` удаляет старую запись Media3 и ждёт её исчезновения; `COMPLETED` не перекачивается (воркер восстановит Room).
   - `STATE_REMOVING` в `awaitCompletion` больше не даёт мгновенную ошибку — ожидается новая запись.
3. **`DownloadTrackWorker`**:
   - `Log.d` первой строкой `doWork()` — в logcat видно сам факт запуска (тег `DownloadWorker`).
   - Передаёт `forceRestart`; при `CancellationException` не удаляет Media3, иначе REPLACE-попытка убивала бы новую загрузку.
4. **`PlayerViewModel`**: `Error` держится 1 секунду и сменяется на `NotDownloaded`; клик по крестику запускает новую попытку.
5. **`PlayerScreen`**: крестик `Icons.Rounded.Close` с `AnimatedContent`-морфингом обратно в стрелку; Snackbar «Не удалось скачать трек (доступен только онлайн)» показывается только для реальной ошибки (`FAILED`), не для отмены.
6. **Тест** `DownloadStateResolutionTest` (10 кейсов приоритета состояний).

### Проверки
- `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.
- `.\gradlew testDebugUnitTest` — 64/64, 0 failures, 0 errors.
- APK: `C:\Users\souler\Downloads\MyMusic-debug.apk`.
- Диагностика на устройстве: `adb logcat -s DownloadWorker DownloadFlow OfflineDownload`.

### Следующий шаг
- ~~Искать прогрессивный MP3-поток SoundCloud для скачивания~~ — выполнено в разделе «Скачивание только progressive MP3 через OkHttp» ниже.

### Файлы
```
app/src/main/java/com/my/music/core/download/DownloadState.kt
app/src/main/java/com/my/music/core/download/DownloadManager.kt
app/src/main/java/com/my/music/core/download/OfflineDownloadManager.kt
app/src/main/java/com/my/music/core/download/DownloadTrackWorker.kt
app/src/main/java/com/my/music/feature/player/PlayerViewModel.kt
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
app/src/test/java/com/my/music/core/download/DownloadStateResolutionTest.kt (новый)
PROGRESS.md
MEMORY.md
```

---

## ✅ Скачивание только progressive MP3 через OkHttp (22.10.2026)

### Причина
- HLS — это набор сегментов, а не один файл. Media3 DownloadManager складывал их в `SimpleCache`, из-за чего офлайн-скачивание зависало, а готового mp3 не появлялось.
- Для офлайна нужен ровно один progressive MP3-файл; если его нет — честная ошибка вместо бесконечной загрузки.

### Что сделано
1. **`SoundCloudRepository.resolveStreamUrl(..., requireProgressive = false)`**:
   - progressive MP3 с максимальным битрейтом выбирается всегда первым;
   - для скачивания передаётся `requireProgressive = true`: если progressive нет, возвращается ошибка «Трек доступен только для онлайн-прослушивания»;
   - для онлайн-плеера HLS-фолбэк сохранён, иначе HLS-only треки перестали бы играть.
2. **`DownloadTrackWorker`**:
   - вся логика Media3 DownloadManager удалена;
   - OkHttp качает progressive MP3 в `filesDir/downloads/<safe>_<hash>.mp3`;
   - загрузка идёт в `.part`, после успеха файл переименовывается; прогресс публикуется через `setProgress`;
   - при отмене `.part` удаляется, при HLS-only — `Result.failure`, UI показывает крестик и Snackbar.
3. **Удалены** `OfflineDownloadManager`, `OfflineMediaModule`, `MediaDownloadState`; `MusicService` больше не использует `SimpleCache` и `CacheDataSource`.
4. **`DownloadManager`** упрощён: Room + WorkInfo, прогресс из воркера; `ExistingWorkPolicy.REPLACE` и защита от двойного тапа 700 мс сохранены.
5. **`DownloadRepositoryImpl`** проверяет только локальные `file://`-файлы; старые HLS-записи из Room инвалидируются при обращении.
6. **`DownloadStateResolutionTest`** переписан под новую сигнатуру (10 кейсов).

### Проверки
- `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.
- `.\gradlew testDebugUnitTest` — 64/64, 0 failures, 0 errors.
- APK: `C:\Users\souler\Downloads\MyMusic-debug.apk`.
- Диагностика на устройстве: `adb logcat -s DownloadWorker DownloadFlow`.

### Файлы
```
app/src/main/java/com/my/music/core/download/DownloadManager.kt
app/src/main/java/com/my/music/core/download/DownloadTrackWorker.kt
app/src/main/java/com/my/music/core/download/DownloadWorkerEntryPoint.kt
app/src/main/java/com/my/music/core/download/OfflineDownloadManager.kt (удалён)
app/src/main/java/com/my/music/core/download/OfflineMediaModule.kt (удалён)
app/src/main/java/com/my/music/core/download/MediaDownloadState.kt (удалён)
app/src/main/java/com/my/music/core/player/MusicService.kt
app/src/main/java/com/my/music/data/local/DownloadRepositoryImpl.kt
app/src/main/java/com/my/music/data/local/entity/DownloadedTrackEntity.kt
app/src/main/java/com/my/music/data/model/DownloadedTrack.kt
app/src/main/java/com/my/music/data/repository/DownloadRepository.kt
app/src/main/java/com/my/music/data/repository/SoundCloudRepository.kt
app/src/main/java/com/my/music/data/source/soundcloud/SoundCloudRepositoryImpl.kt
app/src/test/java/com/my/music/core/download/DownloadStateResolutionTest.kt
PROGRESS.md
MEMORY.md
```

---

## 🔜 Следующий шаг — экран «Скачанное» и очистка старых данных

### Что нужно сделать
1. **Экран/секция «Скачанное»**: список локальных mp3, удаление файла и записи Room, индикация занятого места.
2. **Очистка legacy**: удалить оставшийся каталог `filesDir/media_cache` и старые HLS-записи из Room, чтобы они не занимали место и не показывались как скачанные.
3. **Проверка на устройстве**: скачать progressive-трек, включить авиарежим и воспроизвести; для HLS-only трека проверить крестик и Snackbar «Не удалось скачать трек (доступен только онлайн)».
4. **Настроения волны**: чипы «Бодрая / Спокойная / По любимым / Открытия», влияющие на жанры, темп и долю новых треков.
5. **Воспоминания**: показывать сохранённые координаты и сезон в карточках, добавить действие «Открыть на карте».
6. Сборка, тесты, PROGRESS.md, MEMORY.md, коммит и пуш.

---

## 📋 План — YouTube Music и глобальные статусы сервисов (22.10.2026)

### Общий план
1. **База и модель**: добавить `Source.YOUTUBE`, убедиться, что `Track` и Room-конвертеры работают с новым источником.
2. **Репозиторий**: `YouTubeRepository` через NewPipe (`YoutubeService`): поиск + progressive M4A/WEBM_OPUS без HLS.
3. **UI поиска**: переключатель SoundCloud / YouTube, единый список результатов, ошибки и пустые состояния под выбранный источник.
4. **Плеер и загрузчик**: научить `PlayerController` и `DownloadTrackWorker` выбирать репозиторий по `Track.source` и корректно определять MIME (MP3/M4A/WEBM). **Выполнено в шаге 4 (см. выше).**
5. **Импорт плейлистов (на будущее)**: через NewPipe вытаскивать плейлисты YouTube и добавлять в очередь/медиатеку.
6. **Статусы сервисов**: `ServiceStatusManager` + меню статусов в поиске, ручное обновление, предупреждение о недоступном источнике.

### Текущая итерация — только шаги 1–3
- [x] `Source.YOUTUBE` в модели.
- [x] `YouTubeRepository` + `YouTubeRepositoryImpl` + Hilt-биндинг.
- [x] `ServiceStatusManager`, `ServiceType`, `ServiceHealth`.
- [x] `SearchViewModel`: выбранный источник + подписка на статусы.
- [x] `SearchScreen`: переключатель источников, меню статусов, предупреждение о недоступном сервисе.
- [x] Сборка, тесты, коммит, пуш.

### Что сделано (22.10.2026)
- `Source.YOUTUBE` добавлен в `Track.kt`; Room хранит имя enum, миграция не нужна.
- `YouTubeRepository` + `YouTubeRepositoryImpl`:
  - поиск через `ServiceList.YouTube` с фильтром `videos`;
  - маппинг `StreamInfoItem` → `Track` (`id`/`transcodingUrl` = URL видео, артист без суффикса ` - Topic`);
  - `resolveStreamUrl()` выбирает только `DeliveryMethod.PROGRESSIVE_HTTP` + `M4A`/`WEBMA_OPUS`, сначала M4A по максимальному битрейту, затем Opus; `isHls = false`;
  - Hilt-биндинг в `RepositoryModule`.
- `ServiceStatusManager` (Singleton):
  - `ServiceType { SOUNDCLOUD, YOUTUBE }`, `ServiceHealth(isAvailable, isChecking)`;
  - `StateFlow<Map<ServiceType, ServiceHealth>>`, параллельная проверка поиском `"test"` с лимитом 1;
  - `checkAllServices()` при старте (`MyMusicApp` после `NewPipe.init`) и после ошибок поиска;
  - `refreshStatus()` из меню, защита от параллельных проверок, `markAvailable()` после успешного поиска;
  - 5 JVM-тестов на успех/failure/exception/начальное состояние.
- `SearchViewModel`:
  - выбранный `ServiceType`, поиск через соответствующий репозиторий;
  - общий поток `(query, service)` с debounce 400 мс, отмена устаревших запросов;
  - `retry()` действительно перезапускает поиск (раньше повторный `onQueryChange` с тем же текстом игнорировался);
  - подписка на глобальные статусы, `refreshStatuses()`.
- `SearchScreen`:
  - переключатель SoundCloud / YouTube с пружинными анимациями и красной обводкой недоступного сервиса;
  - иконка `Radar` с цветным индикатором и MD3 `DropdownMenu` (список сервисов, точки «Онлайн»/«Ошибка», спиннер «Проверка», кнопка «Обновить статусы»);
  - предупреждающий баннер для выбранного недоступного сервиса;
  - динамические подписи источника в idle/empty/results, кнопка «Повторить» через `retry()`.

### Проверка
- `.\gradlew assembleDebug testDebugUnitTest` — BUILD SUCCESSFUL.
- 69 JVM-тестов, 0 failures, 0 errors.
- Живая проверка через временный тест: поиск YouTube вернул результаты, `resolveStreamUrl()` отдал progressive M4A (`itag=140`, `isHls=false`). Временный тест удалён.
- Ограничение текущей итерации: `PlayerController` и `DownloadTrackWorker` всё ещё используют `SoundCloudRepository`, поэтому воспроизведение и скачивание YouTube-результатов пока не работают — это шаг 4.

### Явно вне текущей итерации
- Скачивание YouTube-треков и правки `DownloadTrackWorker`.
- Правки `PlayerController` и MIME в плеере.
- Импорт YouTube-плейлистов.
- Экран «Скачанное» и очистка legacy-кэша.

---

## ✅ Шаг 4 — плеер и скачивание YouTube + умный редирект (22.10.2026)

### Что сделано
1. **MIME в результате resolve**:
   - `AudioStreamResult` дополнен `mimeType`; старые вызовы без формата получают безопасный фолбэк.
   - SoundCloud отдаёт `AUDIO_MPEG` для progressive и `APPLICATION_M3U8` для HLS.
   - YouTube отдаёт `AUDIO_MP4` для M4A и `AUDIO_WEBM` для Opus-фолбэка.
2. **`PlayerController`**:
   - `resolveAndPlay()` выбирает репозиторий по `Track.source`: YouTube → `YouTubeRepository`, SoundCloud → `SoundCloudRepository`.
   - ExoPlayer получает `audio/mp4` для YouTube M4A, `audio/webm` для WEBM и `audio/mpeg` для SoundCloud.
   - Офлайн-воспроизведение берёт MIME из Room, поэтому скачанный M4A больше не теряет формат.
3. **`DownloadTrackWorker`**:
   - Поток резолвится через репозиторий выбранного источника; для SoundCloud сохранён `requireProgressive = true`.
   - Имя файла зависит от MIME: `.mp3`, `.m4a` или `.webm`; в Room пишется тот же MIME.
   - HLS и ошибки resolve/download/save по-прежнему завершают задачу через `Result.failure`, WorkInfo становится `FAILED`, UI показывает крестик.
4. **Умный редирект**:
   - При `WorkInfo.FAILED` для SoundCloud-трека показывается Snackbar «Не удалось скачать (только онлайн). Найти на YouTube?» с действием «Найти».
   - Для YouTube-ошибки остаётся нейтральное сообщение без действия.
   - `NavigationEvents` (Singleton, `SharedFlow` с replay = 1) передаёт запрос `"Артист - Название"` и `YOUTUBE` в `SearchViewModel`.
   - `SearchViewModel` обрабатывает намерение сразу, без debounce, очищает старые результаты и переключает источник; replay-кэш сбрасывается после обработки.
   - `AppNavGraph` открывает вкладку поиска с сохранением состояния нижней навигации.
5. **Тесты**:
   - `PlayerControllerQueueTest`: YouTube-трек уходит в `YouTubeRepository`, MIME `AUDIO_MP4`; офлайн-M4A сохраняет MIME из Room.
   - `DownloadWorkerFormatTest`: расширения `.m4a`/`.mp3`/`.webm`, фолбэки MIME и безопасное имя файла.
   - `SearchViewModelExternalRequestTest`: внешний запрос сразу выбирает YouTube, не ждёт debounce и не повторяется при повторном открытии экрана.

### Файлы
```
app/src/main/java/com/my/music/core/navigation/NavigationEvents.kt (новый)
app/src/main/java/com/my/music/core/player/PlayerController.kt
app/src/main/java/com/my/music/core/download/DownloadTrackWorker.kt
app/src/main/java/com/my/music/core/download/DownloadWorkerEntryPoint.kt
app/src/main/java/com/my/music/data/model/AudioStreamResult.kt
app/src/main/java/com/my/music/data/model/DownloadedTrack.kt
app/src/main/java/com/my/music/data/local/DownloadRepositoryImpl.kt
app/src/main/java/com/my/music/data/local/entity/DownloadedTrackEntity.kt
app/src/main/java/com/my/music/data/source/soundcloud/SoundCloudRepositoryImpl.kt
app/src/main/java/com/my/music/data/source/youtube/YouTubeRepositoryImpl.kt
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
app/src/main/java/com/my/music/feature/player/PlayerViewModel.kt
app/src/main/java/com/my/music/feature/search/SearchViewModel.kt
app/src/main/java/com/my/music/navigation/AppNavGraph.kt
app/src/test/java/com/my/music/core/player/PlayerControllerQueueTest.kt
app/src/test/java/com/my/music/core/download/DownloadWorkerFormatTest.kt (новый)
app/src/test/java/com/my/music/feature/search/SearchViewModelExternalRequestTest.kt (новый)
PROGRESS.md
MEMORY.md
```

### Проверка
- `.\gradlew assembleDebug testDebugUnitTest` — BUILD SUCCESSFUL.
- 78 JVM-тестов, 0 failures, 0 errors.
- APK: `C:\Users\souler\Downloads\MyMusic-debug.apk`.

---

## ✅ Сессия 5 — MD3 Expressive: Dynamic Color и новый Home (22.10.2026)

### Что сделано
1. **Фундамент темы**:
   - на Android 12+ `MyMusicTheme` всегда использует `dynamicDarkColorScheme` / `dynamicLightColorScheme` от системы;
   - ручной акцент из настроек применяется только на Android ниже 12;
   - fallback-палитра переведена с фиолетовой на тёплый глубокий фон + коралловый акцент, добавлены все `surfaceContainer*`, `surfaceDim/Bright`, `outlineVariant` и inverse-роли;
   - создан `Shape.kt`: настоящая squircle-форма через суперэллипс, крупные M3-скругления, Pill и Bento-токены; `shapes` подключены к `MaterialTheme`;
   - `Type.kt` усилен: ExtraBold Display/Headline, крупнее Display Small, плотный трекинг.
2. **Главный экран полностью переписан**:
   - шапка без emoji и refresh: дата, крупное приветствие, одна иконка настроек, pull-to-refresh;
   - hero «Продолжить слушать» из последнего трека истории: компактная карточка, пружинный scale 0.95, морфинг скругления и цвета;
   - Bento-раздел «Чарты»: два тональных блока `Top RU` / `Top US` с крупными номерами 01/02/03 и кликабельными треками;
   - «Хроника»: горизонтальная карусель с чередованием карточек 160dp / 120dp, разной высотой и вертикальным смещением;
   - «Воспоминания»: одна крупная Bento-карточка с сезонным акцентом, координатами, счётчиком, стеком обложек и запуском очереди;
   - ниже — Bento-пара «Любимое» и «Ваш жанр» с реальными счётчиками;
   - появление секций — staggered на Spring с `dampingRatio = 0.8f`.
3. **Данные**:
   - добавлена доменная модель `SeasonMemory` и `LikedTracksRepository.getSeasonMemories()`, координаты/сезон больше не теряются при чтении из Room;
   - `HomeViewModel` больше не грузит старые SoundCloud-чарты для Home; Top RU/US загружаются параллельно через `YouTubeRepository.search()` (`Top 100 Russia music`, `Top 100 USA music`, лимит 8, фильтр длительности);
   - при ошибке одного региона второй сохраняется, при неудачном refresh старые чарты не затираются;
   - приложение больше не зависит от обложки на главном: цвета берутся из системной Dynamic Color или fallback-палитры.
4. **Настройки**:
   - подпись акцента объясняет, что на Android 12+ используются системные цвета;
   - ручные акценты доступны только на старых версиях, дефолтный акцент — коралловый.
5. **Тесты**:
   - обновлён `HomeViewModelGenreTest` под `SeasonMemory` и YouTube-репозиторий;
   - добавлен `HomeViewModelChartsTest`: успешная параллельная загрузка, частичная ошибка, сохранение старых чартов при неудачном refresh;
   - `LikedTracksRepositoryTest` проверяет координаты и сезон через `getSeasonMemories()`.

### Файлы
```
app/src/main/java/com/my/music/core/ui/theme/Color.kt
app/src/main/java/com/my/music/core/ui/theme/Theme.kt
app/src/main/java/com/my/music/core/ui/theme/Type.kt
app/src/main/java/com/my/music/core/ui/theme/Shape.kt (новый)
app/src/main/java/com/my/music/core/ui/theme/AccentPalette.kt
app/src/main/java/com/my/music/MainActivity.kt
app/src/main/java/com/my/music/feature/home/HomeScreen.kt (полностью переписан)
app/src/main/java/com/my/music/feature/home/HomeViewModel.kt
app/src/main/java/com/my/music/feature/settings/SettingsScreen.kt
app/src/main/java/com/my/music/data/model/SeasonMemory.kt (новый)
app/src/main/java/com/my/music/data/local/entity/LikedTrackEntity.kt
app/src/main/java/com/my/music/data/local/LikedTracksRepositoryImpl.kt
app/src/main/java/com/my/music/data/repository/LikedTracksRepository.kt
app/src/main/java/com/my/music/data/repository/SettingsRepository.kt
app/src/test/java/com/my/music/feature/home/HomeViewModelGenreTest.kt
app/src/test/java/com/my/music/feature/home/HomeViewModelChartsTest.kt (новый)
app/src/test/java/com/my/music/data/local/LikedTracksRepositoryTest.kt
PROGRESS.md
MEMORY.md
```

### Проверка
- `.\gradlew assembleDebug testDebugUnitTest` — BUILD SUCCESSFUL.
- 80 JVM-тестов, 0 failures, 0 errors.
- `lintDebug`: новых замечаний в файлах Home/темы нет; единственная ошибка — существующий Media3 `UnstableApi` в `PlayerController` (плеер по условию не трогали).
- Плеер и поиск не изменялись.

---

## ✅ Сессия 6 — Компактный Home (23.10.2026)

### Что сделано
- Home переписан по новому ТЗ: максимум 4 блока, без Bento-сетки, наложений, вылетов за край и декоративных градиентов.
- Блок 1 — компактный hero «Продолжить слушать»: обложка слева, текст справа, Play-бейдж, press-scale 0.95.
- Блок 2 — один блок «Чарты» с переключателем `Top RU` / `Top US`; внутри 3 трека с крупными номерами, ошибка и skeleton остаются внутри блока.
- Блок 3 — «Недавнее»: горизонтальная лента обложек 126dp, подписи поверх с плоским скримом (без градиента).
- Блок 4 — опционально «Воспоминания» (сезон + количество) или «Любимое» (количество); при отсутствии данных не показывается.
- Отступы: 26dp между блоками, 12–14dp внутри; разные скругления: hero 28dp, чарты 20dp, компактная карточка 16dp, обложки — squircle.
- Появление блоков — лёгкий spring `dampingRatio = 0.8`, только alpha + подъём 12dp.
- Плеер и поиск не изменялись. `Theme.kt`, `Color.kt`, `Shape.kt` уже соответствовали ТЗ, поэтому не переписывались.

### Файлы
```
app/src/main/java/com/my/music/feature/home/HomeScreen.kt (полностью переписан)
PROGRESS.md
MEMORY.md
```

### Проверка
- `.\gradlew assembleDebug` — BUILD SUCCESSFUL.
- `.\gradlew testDebugUnitTest` — BUILD SUCCESSFUL, 80 JVM-тестов, 0 failures, 0 errors.
- `lintDebug`: новых замечаний в HomeScreen нет; остаётся существующая ошибка Media3 `UnstableApi` в `PlayerController` (плеер не трогали).
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

---

## ✅ Сессия 7 — Рекомендации вместо чартов (23.10.2026)

### Что сделано
- Блок «Чарты» (`Top RU` / `Top US`) полностью удалён из Home.
- Вместо него добавлен блок «Для вас»: горизонтальная `LazyRow` с рекомендациями.
- Карточка рекомендации: обложка 140dp в `SquircleShape`, название (до 2 строк) и артист под обложкой, press-scale 0.96.
- `HomeViewModel` строит персональные запросы по последним лайкам и истории: первый жанр и первый артист, два параллельных запроса `YouTubeRepository.search()` (`"<жанр> music mix"`, `"<артист> best songs"`).
- Если лайков/истории нет — загружаются популярные треки (`popular music 2026`).
- Если персонализированный поиск пуст или упал, включается тот же популярный fallback.
- Результаты фильтруются по длительности 45–900 секунд, дедуплицируются и исключают треки, уже находящиеся в лайках/истории.
- Повторные запросы защищены ключом персонализации: одинаковые жанр/артист не запускают новую загрузку; при новых сигналах старый запрос отменяется.
- Ошибка загрузки не затирает уже показанные рекомендации; для пустого состояния есть кнопка «Повторить»/«Обновить».
- В «Недавнем» обложки переведены на `RoundedCornerShape(18.dp)`, скрим усилен до `0.56f`, подписи получили больший контраст.
- Hero, опциональная карточка, отступы, reveal-анимации и press-scale остальных блоков не менялись. Плеер и поиск не трогались.

### Файлы
```
app/src/main/java/com/my/music/feature/home/HomeViewModel.kt
app/src/main/java/com/my/music/feature/home/HomeScreen.kt
app/src/test/java/com/my/music/feature/home/HomeViewModelRecommendationsTest.kt (новый)
app/src/test/java/com/my/music/feature/home/HomeViewModelChartsTest.kt (удалён)
PROGRESS.md
MEMORY.md
```

### Проверка
- `.\gradlew assembleDebug` — BUILD SUCCESSFUL.
- `.\gradlew testDebugUnitTest` — BUILD SUCCESSFUL, 81 JVM-тест, 0 failures, 0 errors.
- `lintDebug`: новых замечаний в Home нет; остаётся существующая ошибка Media3 `UnstableApi` в `PlayerController` (плеер не трогали).
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

---

## ✅ Сессия 8 — Полировка рекомендаций и «Воспоминаний» (23.10.2026)

### Что сделано
- «Для вас»: запросы переведены на `"<жанр> official audio"` и `"<артист> official audio"`, fallback — `"popular songs official audio"`.
- Добавлен фильтр нестудийных записей: переводы, ремиксы, каверы, live, караоке, инструменталы, акустика, slowed/nightcore, многоязычные версии и треки без букв в названии.
- Рекомендации больше не исключают прослушанные или лайкнутые треки. Лента загружается один раз за жизнь `HomeViewModel`, повторно — только через Pull-to-refresh.
- Если персональных результатов меньше 10, они добираются популярными треками до 15. Максимум карусели — 15 карточек.
- Позиция основного списка и карусели рекомендаций сохраняется через `rememberLazyListState()`; `RevealBlock` не повторяет анимацию при возврате с плеера.
- «Воспоминания» переделаны в единую карточку без выступа: шапка с сезоном, количеством треков и кнопкой Play, разделитель и список до 5 треков.
- В списке «Воспоминаний» квадратные обложки 40dp, название, артист и длительность. Play запускает очередь с первого трека, тап по треку — с выбранного.
- Плеер и поиск не изменялись.

### Файлы
```
app/src/main/java/com/my/music/feature/home/HomeViewModel.kt
app/src/main/java/com/my/music/feature/home/HomeScreen.kt
app/src/test/java/com/my/music/feature/home/HomeViewModelRecommendationsTest.kt
PROGRESS.md
MEMORY.md
```

### Проверка
- `.\gradlew testDebugUnitTest` — BUILD SUCCESSFUL, 84 JVM-теста, 0 failures, 0 errors.
- `.\gradlew assembleDebug` — BUILD SUCCESSFUL.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

### Следующий шаг
- Импорт плейлистов YouTube или экран «Скачанное».

---

## ✅ Сессия 9 — Рекомендации v2: сиды, валидация и recently_shown (24.10.2026)

### Что сделано
- «Для вас» переведены с одного жанра/артиста на топ-8 артистов-сидов из лайков и истории. Лайк весит больше прослушивания, свежие записи — больше старых; повтор одного и того же трека не удваивает вес.
- Имя артиста нормализуется: нижний регистр, удаление `feat.`/`ft.`/`prod.`/`produced by`, схлопывание пробелов.
- На каждый refresh выбираются случайные 4–5 артистов из топ-8. Для каждого генерируются 1–2 запроса из `"<artist> official audio"`, `"<artist> <жанр>"`, `"<artist> official"`, плюс 1–2 общих запроса `"<жанр> official audio"`.
- Добавлена строгая валидация: трек принимается только при совпадении нормализованного `track.artist` с сид-артистом. Если артист пустой или `Unknown`, сравнивается начало названия (`startsWith`), а не подстрока. Кейс «имя сида в title чужого трека» больше не проходит.
- Фильтр переводов, ремиксов, каверов, live, караоке, инструментала, акустики, slowed/nightcore и многоязычных версий сохранён.
- Room поднят до v8: таблица `recently_shown` (`trackId`, `shownAt`) с TTL 24 часа. Показанные треки исключаются из следующей пачки, старые записи вычищаются при записи новой.
- Пул кандидатов перемешивается перед обрезкой. В финальной ленте не больше 3 треков одного артиста; при нехватке 10 уникальных лента добивается популярными с тем же капом и исключением `recently_shown`.
- Pull-to-refresh делает force-загрузку, заменяет ленту новой пачкой и не накладывает её поверх старой.
- Прослушанные и лайкнутые треки не исчезают из уже показанной ленты; возврат с плеера не перезагружает Home, позиции и reveal-анимация сохраняются.
- Плеер и поиск не изменялись.

### Файлы
```
app/src/main/java/com/my/music/feature/home/RecommendationPlanner.kt (новый)
app/src/main/java/com/my/music/feature/home/HomeViewModel.kt
app/src/main/java/com/my/music/data/local/entity/RecentlyShownEntity.kt (новый)
app/src/main/java/com/my/music/data/local/dao/RecentlyShownDao.kt (новый)
app/src/main/java/com/my/music/data/repository/RecentlyShownRepository.kt (новый)
app/src/main/java/com/my/music/data/local/RecentlyShownRepositoryImpl.kt (новый)
app/src/main/java/com/my/music/data/local/AppDatabase.kt
app/src/main/java/com/my/music/data/local/DatabaseMigrations.kt
app/src/main/java/com/my/music/data/local/DatabaseModule.kt
app/src/main/java/com/my/music/core/di/RepositoryModule.kt
app/schemas/com.my.music.data.local.AppDatabase/8.json (новый)
app/src/test/java/com/my/music/feature/home/RecommendationPlannerTest.kt (новый)
app/src/test/java/com/my/music/feature/home/HomeViewModelRecommendationsTest.kt
app/src/test/java/com/my/music/feature/home/HomeViewModelGenreTest.kt
app/src/test/java/com/my/music/data/local/RecentlyShownRepositoryTest.kt (новый)
app/src/test/java/com/my/music/data/local/DatabaseMigrationTest.kt
PROGRESS.md
MEMORY.md
```

### Проверка
- `.\gradlew testDebugUnitTest` — BUILD SUCCESSFUL, 92 JVM-теста, 0 failures, 0 errors.
- `.\gradlew assembleDebug` — BUILD SUCCESSFUL.
- Room-схема v8 экспортирована, миграция 1→8 проверена Robolectric-тестом.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

### Следующий шаг
- Импорт плейлистов YouTube или экран «Скачанное».

---

## ✅ Сессия 10 — Откат ONNX/YAMNet и related-рекомендации (24.10.2026)

### Причина
- Локальный ONNX/YAMNet (коммит `4ea6975`) раздувал APK до ~73 МБ и требовал тяжёлого аудио-анализа на слабом устройстве. Для рекомендаций достаточно алгоритмической related-выдачи NewPipe.
- Прямые related-треки дают реальных новых исполнителей вместо текстовых поисковых запросов и уменьшают количество мусора.

### Что сделано
1. **Откат Phase 1**:
   - Создан revert-коммит `9e74fb6`; Room снова v8, удалены `onnxruntime-android`, `yamnet.onnx`, `core/audio`, `AudioFeatureWorker`, `9.json`.
   - Схемы БД: только `4.json`–`8.json`; опубликованная история не переписывалась.
2. **Related через NewPipe**:
   - В `YouTubeRepository` и `SoundCloudRepository` добавлен `getRelated(trackUrl, limit = 30)`.
   - Реализации вызывают `StreamInfo.getInfo(service, trackUrl)`, берут `relatedItems`, при пустоте — `relatedStreams`, фильтруют `StreamInfoItem`, дедуплицируют по URL и маппят существующим `toDomain()`.
3. **Сиды**:
   - Последние 20 лайков и 20 прослушиваний; лайк весит `4.0`, прослушивание `1.0`, свежесть убывает с индексом; дубликат из истории не учитывается, если трек уже лайкнут.
   - На refresh случайно выбираются 4–6 треков из топ-20, при возможности — разных артистов.
4. **Сборка ленты**:
   - Для каждого сида параллельно запрашивается до 30 related-треков; исключаются все выбранные сиды и `recently_shown` (TTL 24 ч).
   - Сохранены фильтры мусора по названию и длительности 45 с–10 мин. Треки других артистов из related не отбрасываются.
   - Финал перемешивается; не больше 2 треков на артиста, не больше 3 — для артиста, доминирующего в персональной выборке (≥3 трека и ≥25%).
   - Лимит ленты — 15 треков; при related <10 добираем fallback-кандидатами.
5. **Fallback**:
   - Точечный поиск по 4–6 топ-артистам, строгая проверка только поля `track.artist`; имя сида в чужом title не проходит.
   - Глобальный `POPULAR_QUERY` используется только при полностью пустых лайках и истории (холодный старт).
   - Если персональные данные есть, а related/fallback ничего не дали, возвращается пустая успешная лента или ошибка сети — без популярного запроса.
6. **Стабильность UI**:
   - `HomeViewModel` снимает снимок лайков/истории на старте загрузки; изменения во время прослушивания не перезагружают уже показанную ленту.
   - Обновление — только Pull-to-refresh; позиции `LazyColumn` и карусели сохраняются.
   - Плеер и поиск не изменялись.

### Проверка
- `.\gradlew testDebugUnitTest` — `BUILD SUCCESSFUL`, 100 JVM-тестов, 0 failures, 0 errors.
- `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 27 218 807 байт (~27,2 МБ); `onnx`/`yamnet` внутри отсутствуют.
- Живая проверка через временный JVM-тест: YouTube и SoundCloud вернули по 10 related-элементов; временный тест удалён.
- `HomeViewModelRecommendationsTest` — 12 тестов, `RecommendationPlannerTest` — 9 тестов; обновлён `HomeViewModelGenreTest`.

### Файлы
```
app/src/main/java/com/my/music/data/repository/YouTubeRepository.kt
app/src/main/java/com/my/music/data/repository/SoundCloudRepository.kt
app/src/main/java/com/my/music/data/source/youtube/YouTubeRepositoryImpl.kt
app/src/main/java/com/my/music/data/source/soundcloud/SoundCloudRepositoryImpl.kt
app/src/main/java/com/my/music/feature/home/HomeViewModel.kt
app/src/main/java/com/my/music/feature/home/RecommendationPlanner.kt
app/src/test/java/com/my/music/feature/home/HomeViewModelRecommendationsTest.kt
app/src/test/java/com/my/music/feature/home/RecommendationPlannerTest.kt
app/src/test/java/com/my/music/feature/home/HomeViewModelGenreTest.kt
PROGRESS.md
MEMORY.md
```

### Следующий шаг
- Проверить related-ленту на реальном устройстве, затем импорт плейлистов YouTube или экран «Скачанное».

---

## ✅ Сессия 11 — Phase C: единый резолвер источника скачивания (25.10.2026)

### Причина
- SoundCloud отдаёт только HLS, из которого нельзя собрать один офлайн-файл. Раньше такие треки либо не качались, либо требовали ручного поиска на YouTube.
- Кнопка скачивания была только в плеере; на главной и в медиатеке её не было.

### Что сделано
1. **Контракт источников**:
   - `DownloadCapability` — `PROGRESSIVE`, `HLS_ONLY`, `NONE`; каждый репозиторий отдаёт `downloadCapability`.
   - `DownloadSource` — общий интерфейс `source` / `capability` / `search()` / `resolve()`.
   - `YouTubeDownloadSource` (`PROGRESSIVE`) и `SoundCloudDownloadSource` (`HLS_ONLY`) собраны через Hilt `@IntoSet` в `DownloadSourceModule`.
2. **`DownloadSourceResolver`**:
   - Сначала пробует текущий источник, если он progressive и у трека есть ссылка; затем остальные progressive-источники.
   - Строгий матч: нормализованный артист должен совпасть целиком, название — точно, с допуском только на `feat.`, `remaster`, `official audio/video`, `lyrics`, `visualizer`, `explicit`, `clean` и точный префикс `Артист — Название` (YouTube-формат). Подстроки запрещены.
   - `RecentlyFailedSources` — TTL 30 минут на пару «трек + источник»; `Mutex` на `trackId` не даёт запускать параллельный поиск при быстрых повторных тапах.
   - Возвращает `ResolvedDownload`: исходные метаданные трека + фактический progressive-URL, MIME и источник.
3. **`DownloadManager`**:
   - `download()` стал `suspend`: резолвер вызывается до постановки `DownloadTrackWorker`; в задачу передаются `KEY_DIRECT_STREAM_URL`, `KEY_DIRECT_MIME_TYPE`, `KEY_RESOLVED_SOURCE`.
   - Ошибка резолва возвращается в UI без создания воркера; активный резолв отменяется вместе с задачей.
   - `observeStates(ids)` отдаёт состояния сразу для списка карточек одним запросом WorkManager по тегу `download-track:<id>`.
4. **Worker**:
   - Скачивает готовый прямой progressive-URL; старый путь `resolveStream` оставлен для задач, поставленных до обновления и для повторного запуска.
   - В Room сохраняются исходные `Track.id`/`source`, а файл и MIME берутся с фактического источника.
5. **UI**:
   - Общий `TrackDownloadButton` добавлен в hero-карточку, «Для вас», «Недавнее», «Воспоминания» и медиатеку; в плеере кнопка уже была.
   - При успехе — бейдж прогресса/галочка, при неудаче — Snackbar «Не удалось найти источник» (Player, Home, Library). Диалогов выбора источника нет.

### Проверка
- `.\gradlew testDebugUnitTest assembleDebug` — `BUILD SUCCESSFUL`, 109 JVM-тестов, 0 failures, 0 errors.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 27 406 949 байт (~27,4 МБ).
- `DownloadSourceResolverTest` — 10 тестов: SC отсеивается как HLS, YT выбирается, ложное название отсеивается, `recently_failed` блокирует повтор, отсутствие источников даёт ошибку, YouTube-префикс артиста допускается.
- Живая проверка матча на реальной выдаче YouTube: для `Midnight City — M83` совпал только официальный ролик канала M83 (`match=true`), остальные кандидаты отсеяны. Прямой `resolveStreamUrl` с этой машины вернул `LOGIN_REQUIRED: Please sign in` — bot-защита YouTube, не ошибка резолвера; на мобильном IP путь остаётся рабочим. Временный live-тест удалён.

### Файлы
```
app/src/main/java/com/my/music/core/download/DownloadCapability.kt
app/src/main/java/com/my/music/core/download/DownloadSource.kt
app/src/main/java/com/my/music/core/download/DownloadSourceResolver.kt
app/src/main/java/com/my/music/core/download/RecentlyFailedSources.kt
app/src/main/java/com/my/music/core/download/YouTubeDownloadSource.kt
app/src/main/java/com/my/music/core/download/SoundCloudDownloadSource.kt
app/src/main/java/com/my/music/core/download/DownloadMessages.kt
app/src/main/java/com/my/music/core/download/DownloadManager.kt
app/src/main/java/com/my/music/core/download/DownloadTrackWorker.kt
app/src/main/java/com/my/music/core/di/DownloadSourceModule.kt
app/src/main/java/com/my/music/core/ui/components/TrackDownloadButton.kt
app/src/main/java/com/my/music/data/repository/SoundCloudRepository.kt
app/src/main/java/com/my/music/data/repository/YouTubeRepository.kt
app/src/main/java/com/my/music/data/source/soundcloud/SoundCloudRepositoryImpl.kt
app/src/main/java/com/my/music/data/source/youtube/YouTubeRepositoryImpl.kt
app/src/main/java/com/my/music/feature/home/HomeScreen.kt
app/src/main/java/com/my/music/feature/home/HomeViewModel.kt
app/src/main/java/com/my/music/feature/library/LibraryScreen.kt
app/src/main/java/com/my/music/feature/library/LibraryViewModel.kt
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
app/src/main/java/com/my/music/feature/player/PlayerViewModel.kt
app/src/test/java/com/my/music/core/download/DownloadSourceResolverTest.kt
app/src/test/java/com/my/music/feature/home/HomeViewModelGenreTest.kt
app/src/test/java/com/my/music/feature/home/HomeViewModelRecommendationsTest.kt
PROGRESS.md
MEMORY.md
```

### Следующий шаг
- Phase A: редизайн экрана синхронизированного текста (описать текущее состояние и дождаться команды «Продолжай Phase A»).

---

## ✅ Сессия 11.1 — Чистка кнопок скачивания на главной (25.10.2026)

### Причина
- В карточках главной кнопка скачивания всегда падала с ошибкой «Не удалось найти источник»: лента не передаёт полный контекст трека, нужный резолверу.
- Скачивание остаётся в плеере и медиатеке, где контекст полный.

### Что сделано
- Из `HomeScreen` полностью убраны `TrackDownloadButton`, состояния скачивания и Snackbar ошибки: hero-карточка, «Для вас», «Недавнее», «Воспоминания».
- Удалён лишний `Box`-обёртка и `SnackbarHost`; `PullToRefreshBox` снова корневой контейнер экрана.
- Из `HomeViewModel` удалены `DownloadManager`, `downloadStates`, `downloadError`, `downloadTrack()` и `consumeDownloadError()`.
- Отступы сохранены: в hero остался `Spacer(8.dp)` перед `PlayBadge`, в остальных карточках кнопки были оверлеями и не влияли на лейаут.
- Тесты `HomeViewModelGenreTest` и `HomeViewModelRecommendationsTest` очищены от моков `DownloadManager`.

### Проверка
- `.\gradlew testDebugUnitTest assembleDebug` — `BUILD SUCCESSFUL`, 109 JVM-тестов, 0 failures, 0 errors.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 27 406 949 байт (~27,4 МБ).
- `DownloadSourceResolver`, `DownloadManager`, `DownloadTrackWorker`, плеер и медиатека не менялись.

### Файлы
```
app/src/main/java/com/my/music/feature/home/HomeScreen.kt
app/src/main/java/com/my/music/feature/home/HomeViewModel.kt
app/src/test/java/com/my/music/feature/home/HomeViewModelGenreTest.kt
app/src/test/java/com/my/music/feature/home/HomeViewModelRecommendationsTest.kt
PROGRESS.md
MEMORY.md
```

### Следующий шаг
- Phase A: аудит текущего экрана текста и редизайн после команды «Продолжай Phase A».

---

## ✅ Сессия 12 — Phase A: полировка режима синхронного текста (26.10.2026)

### Причина
- Текст в режиме «Текст» жил поверх пёстрого фона: радиальное свечение палитры и размытая обложка давали цветные пятна, из-за которых строки читались неровно.
- Иерархия строк была слабой: все строки 24sp, разница только в альфе; активная строка дополнительно масштабировалась `graphicsLayer`, из-за чего вылезала за край.
- Дальние строки размывались `blur` — на слабом железе это дорого и даёт «грязные» края.

### Что сделано
1. **Фон режима текста**:
   - Радиальное свечение палитры под обложкой гаснет вместе с обложкой (`alpha = 1f - recede`), в режиме текста его нет.
   - Поверх размытой обложки добавлен фиксированный scrim `surface` alpha `0.65 * recede` — фон тёмный и спокойный на любой обложке.
   - Верхняя градиентная маска списка увеличена с 44dp до 60dp, нижняя (124dp) сохранена.
2. **Иерархия строк** (`LyricLineItem`):
   - Активная — 24sp Bold, `highlightColor` через караоке-кисть, свечение `Shadow(alpha = 0.38f, blurRadius = 18f)`.
   - Соседние (N±1) — 18sp Medium, `onSurfaceVariant` alpha 0.55.
   - Дальние (N±2 и больше) — 16sp Normal alpha 0.35.
   - Убран `blur` у дальних строк и `graphicsLayer`-масштаб активной строки (`TransformOrigin` больше не нужен).
   - Несинхронизированный текст — 24sp Normal alpha 0.82 без караоке и без свечения.
3. **Караоке-заливка**:
   - `buildLyricsBrush` переведён с диагонального `Brush.linearGradient` на `Brush.horizontalGradient`, крайние случаи — `SolidColor`.
   - Замеры на устройстве: активная строка 249 по всей ширине пропетой части и ~229–234 в непропетой, перенесённая строка («twilight») 219–236 — заливка работает на обеих визуальных строках.
4. **Геометрия списка**:
   - `centerLiftPx = 24.dp`: активная строка стоит выше геометрического центра, поэтому перенесённая вторая строка не попадает под нижнюю маску.
   - Первое позиционирование — `scrollToItem`, дальше `animateScrollToItem`; опрос позиции 150 мс; тап по строке = `seekTo(line.timeMs)`.
   - Пилюля «Текст» осталась переключателем, отдельного route/BottomSheet и кнопки закрытия нет.

### Диагностика (почему вторая строка выглядела серой)
- Гипотеза о «кисть не заливает вторую строку» не подтвердилась: двухслойный рендер с `DstIn`-маской и `CompositingStrategy.Offscreen` дал тот же результат и был откачен.
- Красный диагностический тест показал, что вторая визуальная строка темнее первой не из-за `alpha` и не из-за разбиения текста, а потому что попадает под нижнюю градиентную маску 124dp.
- Исправление — подъём активной строки на 24dp при сохранении маски.

### Проверка
- `.\gradlew testDebugUnitTest assembleDebug` — `BUILD SUCCESSFUL`, 109 JVM-тестов, 0 failures, 0 skipped.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 27 406 949 байт (~27,4 МБ); хэш установленного APK совпал со сборочным.
- Live-тест на SM-A266B:
  - M83 — Midnight City: иерархия 24/18/16 читается, фон тёмный, автоскролл и тап-перемотка работают, перенесённая строка заливается.
  - The Beatles — Here Comes The Sun (светлая обложка): фон остаётся тёмным, активная строка белая со свечением, соседние и дальние приглушены.
  - Hans Zimmer — Time: состояние «Инструментальный трек» без изменений.
  - Moby — Porcelain (Above & Beyond Remix): несинхронизированный текст 24sp Normal без караоке.
  - Трек без текста: состояние ошибки и кнопка «Повторить поиск текста» без изменений.
- Напоминание: после `adb install` обязателен `am force-stop`, иначе на экране остаётся старый код.

### Файлы
```
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

### Следующий шаг
- Phase B — только после команды «Продолжай Phase B». До неё изменения не начинать.

---

## ✅ Сессия 13 — Phase A fix + Phase B: фон текста, стабильный таймлайн, синусоида-пульс (26.10.2026)

### Причина
- В режиме «Текст» размытая обложка масштабировалась до 0.58 и висела по центру — на светлой обложке были видны прямоугольные края «серого кирпича».
- Морф `LinearProgressIndicator` ↔ `Slider` срывался на паузе, а фаза волны слайдера при остановке `rememberInfiniteTransition` скачком возвращалась в 0 — ползунок дёргался.
- Запрошена мягкая пульсация синусоиды над слайдером от реального баса.

### Что сделано
1. **Фон текста** (`PlayerScreen`):
   - Полноэкранный размытый слой: `matchParentSize()` + `graphicsLayer { scale 1.25 }` + `blur(44.dp, Unbounded)`; прямоугольные края уходят за экран.
   - Hero-обложка в текстах гаснет полностью (`alpha = lerp(1f, 0f, recede)`), scrim `surface` alpha `0.65 * lyricsBackdrop` перенесён из hero-зоны в корень экрана.
   - Верхняя/нижняя маски списка заменены на `DstIn`-затухание контента (`CompositingStrategy.Offscreen`): маска режет прозрачность строк, а не заливает фон цветом surface — на светлой обложке больше нет тёмного прямоугольника.
2. **Таймлайн**:
   - `ExpressivePlayerSlider` переведён на `rememberPlaybackPhase(active = isPlaying, periodMs = 1600)` (`withFrameNanos`): на паузе фаза замирает, а не сбрасывается; амплитуда плавно уходит в линию за 500 мс.
   - Морф прогресса защёлкнут через `hasStarted` на `trackId`: `LinearProgressIndicator` показывается только на старте трека и не возвращается на паузе/буферизации.
3. **Синусоида-пульс** (`SinePulseBar`):
   - Полоса 16dp над слайдером, stroke 1.5dp, `primary` alpha 0.5, края сходятся к линии; hitbox слайдера не изменён.
   - `BassSampler`: `Visualizer` на аудиосессии ExoPlayer, FFT-бины 1–11, средняя амплитуда /96, асимметричный lerp (атака 0.35, спад 0.10).
   - `PlaybackAudioSession` публикует `audioSessionId` из `MusicService` в UI того же процесса.
   - В манифест добавлен `RECORD_AUDIO`; разрешение спрашивается один раз за процесс, при отказе — ambient `0.42 + 0.26*sin(1.15t) + 0.10*sin(0.53t+1.2)`.
   - На паузе амплитуда плавно гаснет в линию (220 мс), на смене трека — gate fade out/in (120 мс + 360 мс).

### Проверка
- `.\gradlew testDebugUnitTest assembleDebug` — `BUILD SUCCESSFUL`, 109 JVM-тестов, 0 failures, 0 skipped.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 27 417 678 байт.
- Live-тест на SM-A266B:
  - Moby — Porcelain (тёмная бирюзовая обложка) и ONDA ANDAR — type london (светлая бежевая): фон текстов — мягкое полноэкранное пятно без прямоугольных границ; верхние/нижние строки растворяются через DstIn.
  - Пауза: ползунок не прыгает по X, волна слайдера плавно выпрямляется, синусоида гаснет в линию; резюм возвращает волну с той же фазы.
  - Синусоида: амплитуда 11–16 px при FFT-энергии, на паузе 0.
  - Источник энергии: **Visualizer** (logcat `SinePulse: Visualizer активен, сессия 19153`); ambient-ветка проверена отзывом `RECORD_AUDIO` — logcat `Нет RECORD_AUDIO — ambient-фолбэк`.
- Напоминание: после `adb install` обязателен `am force-stop`.

### Файлы
```
app/src/main/AndroidManifest.xml
app/src/main/java/com/my/music/core/player/MusicService.kt
app/src/main/java/com/my/music/core/player/PlaybackAudioSession.kt
app/src/main/java/com/my/music/core/ui/components/ExpressivePlayerSlider.kt
app/src/main/java/com/my/music/core/ui/components/PlaybackPhase.kt
app/src/main/java/com/my/music/core/ui/components/SinePulseBar.kt
app/src/main/java/com/my/music/feature/player/PlayerScreen.kt
PROGRESS.md
MEMORY.md
```

### Следующий шаг
- Ожидает следующей команды: экран «Скачанное» или импорт плейлистов YouTube.
