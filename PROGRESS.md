# PROGRESS.md — История разработки

## Последнее обновление: 16.10.2026

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
- ✅ Анимированное схлопывание обложки (280dp $\to$ 56dp) при переключении режима текстов
- ✅ Сквиркл-лайк (18dp), залитые круглые кнопки Prev/Next (64dp), время `"-m:ss"`
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

## 🔜 Следующий шаг — Фаза 3 (Home charts and Library)

### Что нужно сделать
1. **Data**: `SoundCloudChartsRepository` через NewPipe-киоски SoundCloud («New & hot», «Top 50»). Кэш: последние чарты в Room-таблицу `chart_cache`, TTL обновления ~30 минут.
2. **CollectionsViewModel**: собирает секции Home:
   - Личные (когда есть данные): «Недавние» (`play_history`), «Лайкнутые» (`liked_tracks`), «Твои жанры» (группировка истории + лайков по genre).
   - Открытие (всегда): «New & hot», «Top 50» из чарт-кэша/сети.
3. **HomeScreen**: выразительная шапка-приветствие по времени суток с крупной типографикой; горизонтальные карусели (`LazyRow`) карточек подборок с заголовками секций; тап по треку = воспроизведение с очередью из этой подборки; скелетоны при загрузке чартов; pull-to-refresh.
4. **LibraryScreen**: сетка/список «Лайкнутые» из БД, тап — играет.
5. Сборка, пометка в `PROGRESS.md`, коммит и пуш.


