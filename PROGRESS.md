# PROGRESS.md — История разработки

## Последнее обновление: 20.10.2026

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

## 🔜 Следующий шаг — v1.1 «Моя волна» (алгоритм персонального потока)

### Что нужно сделать
1. **Алгоритм рекомендаций Wave**:
   - Анализ истории прослушиваний (`play_history`) и лайков (`liked_tracks`) для выявления доминантных жанров, ключевых артистов и тегов.
   - Автоматическая ротация запросов в SoundCloud через NewPipe для непрерывной генерации следующей порции треков.
2. **WaveViewModel & PlayerController интеграция**:
   - Автоматическая дозагрузка треков в очередь плеера при приближении к концу списка (бесконечный поток).
   - Поддержка настроений волны (бодрая, спокойная, по любимым, по открытиям) — чипы в `WaveScreen` уже подготовлены визуально.
3. **WaveScreen**: подключить кнопку «Запустить волну» (сейчас промо-заглушка с Canvas-анимацией).
4. Сборка, PROGRESS.md, MEMORY.md, коммит и пуш.


