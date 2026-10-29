# PROGRESS.md — История разработки

## Последнее обновление: 29.10.2026 (сессия 52)

## Кнопки под обложкой в палитре плеера — сессия 52
- Кнопки «скачать / лайк / дизлайк» под обложкой больше не берут цвет темы: при включённых динамических цветах обложки акцент — `coverAccent`, контейнер активного состояния — `playerColors.primaryContainer`, подложка покоя — `coverAccent.copy(alpha = 0.14f)` вместо серой `onSurface.copy(alpha = 0.08f)`.
- Обе краски анимируются 420 мс (`heroActionContainer`, `heroActionIdleContainer`) синхронно с акцентом, поэтому при смене трека кнопки перетекают вместе с фоном и кнопкой Play, а не переключаются рывком.
- Цвета прокинуты в `ExpressiveHeroZone` тремя параметрами (`actionAccent`, `actionContainer`, `actionIdleContainer`); `LikeSquircle`/`DislikeSquircle`/`DownloadSquircle` получили параметр `idleContainerColor` с прежним серым значением по умолчанию, чтобы мёртвые варианты (`CoverForeground`, `TrackGlassCard`) компилировались без правок.
- Подпись «Текст песни»/«Обложка» тоже в палитре: `TextButton(colors = ButtonDefaults.textButtonColors(contentColor = coverAccent))`; при выключенных динамических цветах `coverAccent` равен `primary` темы, поэтому вид не меняется.
- Дизлайк сохраняет семантический красный в активном состоянии (`errorContainer`/`error`), в покое использует общую подложку.
- Проверки: `assembleDebug` — BUILD SUCCESSFUL; тесты 23 suites / 156, 0 failures/errors/skipped; установлено на 192.168.1.106:44537. Живая проверка: `credits song` (синяя обложка) — лайк, скачивание и Play в одном синем акценте, подложки с лёгким синим тоном; после выключения тумблера — серые подложки и зелёный акцент темы; тумблер возвращён в исходное состояние, лайк восстановлен.

## Пропадание текста при смене строки — сессия 51
- Симптом: при переключении строки весь текст на мгновение «исчезал» и возвращался, анимация выглядела рваной. Воспроизведено на устройстве записью экрана (30 с, `screenrecord`): 7 просадок «чернил» текста до 14% медианы, ровно в моменты смены активной строки; просадка держалась ~0.2 с и восстанавливалась ~250 мс.
- Причина: `Modifier.animateItem()` у строк `LazyColumn`. Смена активной строки вызывает `animateScrollToItem`, и аниматор появления (`fadeIn` spring `StiffnessMediumLow`) гасил в ноль уже видимые строки, а потом проявлял их заново. Список не добавляет и не удаляет строки, поэтому эта анимация не нужна (известные баги Google: 352584409, 455884472).
- Фикс 1: `animateItem()` убран; в коде оставлен комментарий-предупреждение, чтобы не вернули.
- Фикс 2: высота строки больше не зависит от активности. `AnimatedVisibility` с `expandVertically`/`shrinkVertically` заменён на постоянный слот `LyricBarSlot = 12.dp`, внутри которого полоска проявляется альфой (200 мс вход, 140 мс выход). Раньше появление/исчезновение полоски меняло высоту элемента на 10dp, и список прыгал на каждой смене строки.
- Побочно: вертикальный padding строки 6dp → `top = 2.dp` (слот сам даёт воздух); импорты `AnimatedVisibility`, `expandVertically`, `shrinkVertically` удалены.
- Проверка после фикса той же методикой (30 с записи): ни одной просадки ниже 50% медианы после стартового позиционирования списка.
- Проверки: `assembleDebug` — BUILD SUCCESSFUL; тесты 23 suites / 156, 0 failures/errors/skipped; установлено на 192.168.1.106:44537.

## Кинематографичная анимация текстов — сессия 50
- `LyricLineItem`: размер анимируется `animateFloatAsState` (spring 0.9 / MediumLow): активная строка плавно вырастает до 22sp, соседние — 17sp, дальние — 15sp; `lineHeight` считается от текущего размера.
- Цвет строки — `animateColorAsState` 280 мс (onSurfaceVariant → onSurface), вес переключается на SemiBold у активной.
- Прозрачность — `tween` 300 мс; значение читается внутри `graphicsLayer`, поэтому анимация не вызывает рекомпозицию на каждый кадр.
- Полоска прогресса: `AnimatedVisibility` с fade + `expandVertically`/`shrinkVertically` (220/160 мс). При смене строки старая полоска уезжает, новая выезжает; заливка внутри анимируется 240 мс.
- `LazyColumn`: к каждой строке добавлен `Modifier.animateItem()` — сдвиги строк при росте активной анимируются, а не перескакивают.
- Синхронизация не тронута: `activeIndex` по таймкоду и автоцентрирование остались прежними.
- Проверки: `assembleDebug` — BUILD SUCCESSFUL, APK 27 568 986 байт; тесты 23 suites / 156, 0 failures/errors/skipped; установлено на 192.168.1.106:44537.

## Динамические цвета обложки и анимация смены трека — сессия 49
- Динамическая тонировка усилена: при включённом тумблере фон плеера — вертикальный градиент 28% → 14% цвета обложки, панель управления подкрашена на 14%; слайдер и кнопка Play используют цвет обложки (`coverAccent`), контраст иконки Play считается по яркости. Акцент анимируется 420 мс, поэтому при смене трека фон и кнопки перетекают плавно. При выключенном тумблере — строго `colorScheme.background`, слайдер и Play — акцентом темы.
- Смена трека: обложка + название + артист обёрнуты в `AnimatedContent` по `TrackDisplay` (ключ трека + тексты + artwork). Старый блок уходит fade + slide вверх, новый приходит fade + slide снизу (340 мс / 220 мс, `SizeTransform(clip = false)`). Shared-element морф вешается только на актуальный трек, поэтому сворачивание плеера не ломается.
- Проверки: `assembleDebug` — BUILD SUCCESSFUL, APK 27 568 967 байт; тесты 23 suites / 156, 0 failures/errors/skipped; установлено на 192.168.1.106:44537.

## Синхронные тексты, акцент, настройки и свайп — сессия 48
- Тексты: убран горизонтальный градиент по буквам — на перенесённой строке он закрашивал и вторую строку целиком. Прогресс теперь показывается тонкой полосой 2dp под активной строкой; текст центрирован, активная строка 22sp SemiBold, соседние 17sp Medium, дальние 15sp с alpha-иерархией, свечение убрано.
- «Динамические цвета обложки» теперь работают: при включении фон плеера получает мягкую тонировку цветом обложки (10% в верхней точке), при выключении — чистый `background`. Палитра считается только при включённой опции.
- Акцентный цвет: новый флаг `accentEnabled` (`AppSettings`, `SettingsRepositoryImpl`, SharedPreferences `accent_enabled`, по умолчанию false). Выключен — `MainActivity` передаёт null в `customAccentColor`, и тема берёт Dynamic Color с обоев; включён — свой цвет. Тап по кружку палитры автоматически включает акцент, сброс цвета выключает его.
- Настройки: секции переставлены (Оформление → Моя волна → Разрешения), карточки без обводок, заголовки `titleSmall`, переключатель акцента с пояснением, палитра при выключенном акценте приглушается (alpha 0.38), геопозиция переехала в «Разрешения», в шапке стандартная стрелка `ArrowBack`.
- Свайп вниз: порог закрытия снижен с 45% до 30% высоты экрана.
- Морф обложки: с экрана плеера и мини-плеера сняты `scaleIn`/`scaleOut` — остаются только fade и сам shared-element морф, поэтому обложка не «едет» дважды; мини-плеер появляется мягким fade + scale 0.96.
- Проверки: `assembleDebug` — BUILD SUCCESSFUL, APK 27 568 968 байт; тесты 23 suites / 156, 0 failures/errors/skipped; установлено на SM-A266B (mDNS adb; адрес 192.168.0.102:39883 был недоступен).

## Чистый Material 3 — сессия 47
- Палитра переведена в строго нейтральную: светлая `#FAFAFA`, тёмная `#121212`, контейнеры — чистые серые без тёплого/зелёного уклона (`Color.kt`). Акцент на Android 12+ берётся из Dynamic Color (обои), fallback — Google Blue; нейтральные роли прибиты в `withAiryNeutrals`, чтобы обои не перекрашивали фон.
- Типографика — каноничная шкала M3: display 57/45/36, headline 32/28/24, title 22/16/14, body 16/14/12, label 14/12/11; SemiBold в display/headline, Medium в title/label, Regular в body. Ручных `fontWeight` в экранах больше нет.
- Формы: строго 12/16/24 (`AppShapes`) плюс `PillShape` для кнопок. Убраны асимметрия острова 44/22, кастомные радиусы и squircle.
- Навигация: вместо кастомного дока — стандартный `NavigationBar` + `NavigationBarItem` (`ExpressiveBottomDock.kt`); `ExpressiveNavigationBar.kt` удалён.
- Home: hero-карточка `surfaceContainerHigh` (обложка 88, текст, `FilledIconButton` 48); секции — `titleLarge` без надзаголовков; «Воспоминания» и «Любимое» — нейтральные `surfaceContainerLow` без гигантских цифр и разделителей.
- Плеер: убраны градиент от обложки, стекло, свечение и текстовые тени; фон — `colorScheme.background`, обложка 24dp, название `headlineSmall`, артист `bodyLarge`, панель управления — простая карточка 24dp на `surfaceContainerLow`, Play 64dp, остальные кнопки 48dp. Тексты песен — без свечения, активная строка акцентом.
- Отступы: сетка 16/24, между секциями 32dp; карточки `surfaceContainerLow` без теней и обводок.
- Логика свайпа, холодного старта, навигации, скачивания и рекомендаций не менялась: `PlayerSwipeState`, `AppNavGraph`, `PlayerController`, `SettingsRepositoryImpl` не в diff.
- Проверки: `assembleDebug` — BUILD SUCCESSFUL, APK 27 624 863 байт; тесты 23 suites / 156, 0 failures/errors/skipped; `adb install -r` на 192.168.0.102:39883 — Success.

## Редизайн «Light Airy MD3» — сессия 46
- `Type.kt`: убран вес 800 из семейства, display/headline переведены с ExtraBold на SemiBold, title* — на Medium, labelSmall — Medium с трекингом 1.2; bodyLarge трекинг 0.5 → 0.25. Снято 30 ручных `fontWeight`-переопределений в экранах, теперь вес задаёт шкала.
- `Color.kt`: нейтральная гамма — светлый фон `#FAF9F6`, тёмный `#1A1918`, контейнеры surfaceContainer* переведены с розовых на тёплые нейтральные; смягчены primaryContainer/secondaryContainer/tertiaryContainer.
- `Theme.kt`: `surfaceTint = Color.Transparent` (elevation не подмешивает акцент); новая `withAiryNeutrals()` прибивает нейтральные роли поверх любой схемы — Dynamic Color оставляет акцент с обоев, но не перекрашивает фон. `values/colors.xml` и `values-night/colors.xml` синхронизированы.
- `Shape.kt`: единая шкала M3 4/8/12/16/24 (`AppShapes`) + `PillShape`; `SquircleShape`, `HeroShape`, `BentoCardShape` удалены. 63 места с радиусами переведены на `MaterialTheme.shapes.*`; асимметрия осталась только у острова управления (44/22 и язычок), у стеклянной карточки плеера — симметричные 24dp.
- Тени: удалены `Modifier.shadow` у обложки (22dp), стеклянной карточки (20dp), острова управления (26dp), мини-плеера (8dp), поля поиска (3→10dp) и `shadowElevation` меню (12→4dp). Иерархия теперь на `surfaceContainer*` + hairline 1dp. Убраны текстовые `Shadow` у артиста и активной строки текста.
- «Воздух»: края экранов 20 → 24dp, блоки Home 32 → 40dp, внутренние паддинги карточек 12/14/22 → 18/18/26dp, списки 10–12 → 14–16dp, низ под док унифицирован на 24dp.
- Плеер: заголовок трека с 40sp ExtraBold на 30sp SemiBold, свечение обложки 0.45 → 0.20, остров получил тональный `containerColor` через `compositeOver`.
- Логика свайпов и холодного старта не тронута: `PlayerSwipeState.kt`, `AppNavGraph.kt`, `PlayerController`, `SettingsRepositoryImpl` не менялись; все `graphicsLayer`-блоки (translationY/alpha/scale) на месте.
- Сборка: `assembleDebug` — BUILD SUCCESSFUL (2m 25s), APK 27 461 047 байт. Тесты: 23 suites / 156 тестов, 0 failures/errors/skipped. `adb install -r` на 192.168.1.106:45609 — Success.


## Удаление motion blur — сессия 45
- Фича motion blur удалена из проекта полностью по решению пользователя: `core/ui/MotionBlur.kt` стёрт (`git rm`), других потребителей `Modifier.motionBlur` не было.
- `PlayerSwipeState.kt`: убраны свойство `blurRadius`, константа `MAX_BLUR_RADIUS = 15f` и упоминание блюра в KDoc. Логика свайпа (`offsetPx` через `mutableFloatStateOf`, `progress`, пружина `settle()` в `settleJob`, `beginDrag/dragTo/dismiss/reset`) сохранена без изменений — на ней держится плавность 120 Гц.
- `AppNavGraph.kt`: с `SharedTransitionLayout` снят `.motionBlur { maxOf(playerSwipe.blurRadius, playerBlur.value) }` (остался чистый `Modifier.fillMaxSize()`); удалены `playerVisibility`/`playerTransitionBlur`/`playerBlur` и импорты `animateFloat`, `rememberTransition`, `motionBlur`. `MutableTransitionState` + `SideEffect` сохранены — `playerVisibleState` по-прежнему передаётся в `AnimatedVisibility(visibleState = ...)` плеера.
- `PlayerScreen.kt` не менялся: `blurRadius = 8f/18f` там — параметры `Shadow` (свечение имени артиста и активной строки текста), к motion blur отношения не имеют.
- Оптимизации не тронуты: отложенное подключение `PlayerController` (`delay(600)` + `ensureConnected()`), асинхронный `SettingsRepositoryImpl` (lazy prefs, IO, `loaded.await()`), свайп только через `graphicsLayer` (translationY/scale/alpha), морф обложки через `SharedTransitionLayout`/`sharedElement` — всё работает без слоя размытия.
- Тесты: 23 suites / 156 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL (3m 23s), APK 27 611 487 байт. `adb install -r` на 192.168.1.106:45609 — Success. Живой прогон не выполнялся по просьбе пользователя.

## Cold start, 120 Гц и починка motion blur — сессия 44
- Motion blur (`core/ui/MotionBlur.kt`): в `graphicsLayer` добавлен `CompositingStrategy.Offscreen`, пока эффект активен (без собственного offscreen-слоя `RenderEffect` на части устройств игнорируется), и сброс в `Auto` при радиусе ≤ 0.1f. Появилась лямбда-перегрузка `motionBlur(() -> Float)`: радиус читается в layer-блоке, поэтому анимация не вызывает рекомпозицию; старая `motionBlur(Float)` осталась тонкой обёрткой.
- Блюр переехал из `PlayerScreen` в `AppNavGraph` — на сам `SharedTransitionLayout` (лишний `Box`-контейнер не понадобился: `modifier` у layout-функции уже есть). Слой layout захватывает и контент, и оверлей shared-элементов, где во время морфа живут панели. Итоговый радиус — `maxOf(playerSwipe.blurRadius, playerBlur)`.
- Новый `feature/player/PlayerSwipeState.kt`: состояние свайпа поднято из `PlayerScreen` в `AppNavGraph`, чтобы и блюр, и смещение читались в `graphicsLayer` без рекомпозиции тяжёлых панелей. `offsetPx` — `mutableFloatStateOf`, а не `Animatable`: `snapTo` — suspend-функция, а из `awaitEachGesture` (restricted suspend scope) её вызывать нельзя, компилятор падал с «Restricted suspending functions…». Пружина после отпускания — `animate()` в `settleJob`, новое касание её отменяет. Есть `progress`, `blurRadius` (`MAX_BLUR_RADIUS = 15f`), `screenWidthPx/HeightPx`, `isDragging`, `isDismissed`, `reset()`.
- 120 Гц: смещение корня и панелей — только `graphicsLayer.translationY`/`alpha`; обложка — `graphicsLayer` (scale/translationX/translationY), `Modifier.offset` в жесте не используется. На время drag `sharedElement` обложки отключается (`if (swipeState.isDragging) Modifier else coverModifier`) и включается после отпускания, чтобы `SharedTransitionLayout` не пересчитывал морф каждый кадр.
- Enter/exit блюр: `MutableTransitionState` + `rememberTransition` объявлены снаружи `AnimatedVisibility` (плееру передан `visibleState = playerVisibleState`), радиус `0f → 15f` только на закрытии; `derivedStateOf { if (playerVisibleState.isIdle) 0f else transitionBlur }` гасит эффект в покое, иначе размытым оставался мини-плеер.
- Cold start: `PlayerController` больше не подключается к `MusicService` в `init` — `scope.launch { delay(COLD_START_CONNECT_DELAY_MS = 600); ensureConnected() }`; `ensureConnected()` вызывается из `playTrackAt`, `resolveAndPlay`, `play`, `pauseOrResume`. Ранний тап по треку не теряется: `playStream` ждёт `controllerReady`, а `ensureConnected()` запускает соединение сразу.
- `SettingsRepositoryImpl`: `getSharedPreferences` — `by lazy`, чтение в `Dispatchers.IO` из `init`, `settings = _settings.filterNotNull()` (до загрузки UI живёт на `initial = AppSettings()` в `MainActivity`). Сеттеры ждут `loaded.await()` и пишут состояние атомарно через `update`/`updateAndGet` — иначе параллельная смена темы и акцента терялась (read-modify-write по снимку).
- `MainActivity`, `HomeViewModel`, `ServiceStatusManager` уже работали асинхронно (Room-флоу в `viewModelScope`, проверка сервисов в `Dispatchers.IO`) — правок не потребовали.
- Тесты: 23 suites / 156 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 611 495 байт. `adb install -r` на 192.168.1.106:34187 — Success. Живой прогон не выполнялся по просьбе пользователя, визуальная проверка блюра и 120 Гц — на нём.

## Морф обложки во время свайпа и motion blur — сессия 43
- `PlayerScreen.kt`: добавлен `screenWidthPx` (из `onSizeChanged`). Прогресс свайпа считается от анимированного `playerOffsetPx`, а не от `dragOffsetPx`: пока палец на экране значения совпадают (`snap`), а после отмены жеста пружина плавно возвращает и плеер, и обложку.
- Обложка получила `coverSwipeModifier`: `scaleX/scaleY = 1f - 0.75f * progress`, `translationX = -screenWidthPx * 0.35f * progress`, `translationY = screenHeightPx * 0.4f * progress`, `clip = false`. Модификатор применён к контейнеру обложки (вместе со свечением), а не к `Surface` с `sharedElement` — так морф не начинается из двойной трансформации.
- Важно: `translationY` содержит только дополнительный сдвиг, потому что корневой `Box` уже едет на `playerOffsetPx`. Формула из задачи (`dragOffsetPx + ...`) дала бы двойное смещение и увела обложку за экран раньше порога.
- При `isDismissed` свайп-прогресс обложки сбрасывается в 0: дальнейший полёт в мини-плеер выполняет shared-переход.
- Новый `core/ui/MotionBlur.kt`: `Modifier.motionBlur(blurRadius)` для Android 12+ (`RenderEffect.createBlurEffect` с `Shader.TileMode.CLAMP`); при радиусе ≤ 0.1f `renderEffect = null`, на API ниже 31 модификатор ничего не делает.
- Motion blur применён к корневому `Box` плеера: при свайпе `swipeProgress * 15f`, при enter/exit — `transitionBlur` из `AnimatedVisibilityScope.transition.animateFloat` (`EnterExitState.Visible → 0`, остальные → 15), итог — `maxOf`. В покое ровно 0.
- Тесты: 23 suites / 156 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 835 081 байт. APK установлен на устройство через `adb install -r` — Success; живой прогон не выполнялся по просьбе пользователя.

## Ровная обложка и z-order панелей при морфе — сессия 42
- `PlayerScreen.kt`: убран наклон обложки полностью — поле `HeroStyle.rotationZ` удалено, `graphicsLayer` больше не задаёт `rotationZ` (варианты 2/3 тоже без наклона). Форма обложки — единый `RoundedCornerShape(24.dp)` вместо асимметричных 56/24/56/24.
- В hero-`Box` добавлены явные слои: обложка `Modifier.zIndex(0f)`, `AnimatedContent` с карточкой трека/текстами — `Modifier.zIndex(1f)`. Панель объявлена после обложки, поэтому в обычном состоянии всегда рисуется поверх.
- Причина наложения при морфе: оверлей `SharedTransitionLayout` рисуется выше всего контента, и `zIndex` внутри `PlayerScreen` на него не влияет. Решение: панели (карточка трека и остров управления) на время enter/exit поднимаются в оверлей через `renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)` и получают `animateEnterExit(enter = playerEnter, exit = playerExit)`.
- `renderInOverlay = { !isPlayerOpen || animatedScope.transition.isRunning }`: в оверлее панели только во время перехода, в покое они остаются на своих местах и не перекрывают снекбар/мини-плеер.
- Оверлей не наследует `graphicsLayer` свайпа, поэтому `panelModifier` добавляет `graphicsLayer` с `translationY = playerOffsetPx` и той же alpha при `isClosing` — панели не прыгают при закрытии жестом.
- В оверлей поднимаются только панели, а не вся колонка: иначе свечение обложки оказалось бы над ней.
- `PlayerScreen` получил параметры `foregroundModifier: Modifier = Modifier` и `isClosing: Boolean = false`; `ExpressiveHeroZone` — `panelModifier`. В `AppNavGraph` enter/exit плеера вынесены в локальные `playerEnter`/`playerExit` и переиспользуются в `animateEnterExit`.
- Тесты: 23 suites / 156 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 835 081 байт (порядок импорта не изменил байткод). Живой тест на устройстве не выполнялся по просьбе пользователя; визуальный результат морфа не проверялся.

## Свайп-переключение треков и морф обложки — сессия 41
- `MiniPlayer.kt`: горизонтальный свайп переведён с перемотки на переключение треков. Влево — следующий (`onNext` → `PlayerViewModel.playNext()`), вправо — предыдущий (`onPrevious` → `playPrevious()`); `onSeekToFraction` удалён.
- Порог вынесен в чистую функцию `swipeTrackDirection(offsetPx, widthPx, velocityX)`: 30% ширины островка или скорость больше 1000 px/s (`VelocityTracker`); +1 — следующий, -1 — предыдущий, 0 — возврат.
- Обложка и текст едут за пальцем через `Animatable` + `graphicsLayer.translationX` с лёгким fade (до 25%), кнопка Play остаётся на месте; после отпускания или отмены — `spring(DampingRatioMediumBouncy, StiffnessMediumLow)`. Новый жест прерывает пружину через `offsetX.stop()`.
- `AppNavGraph`: контейнер мини-плеера и плеера обёрнут в `SharedTransitionLayout`; обе обложки получают `Modifier.sharedElement(rememberSharedContentState(PlayerCoverSharedKey), animatedVisibilityScope)` с общим ключом `"player-cover"`.
- Вход полноэкранного плеера заменён с `slideInVertically` на `fadeIn + scaleIn(0.96f)`, чтобы обложка летела в уже видимый экран; выход остался `scaleOut(0.9f) + fadeOut`.
- `PlayerScreen` и `FloatingMiniPlayer` получили параметр `coverModifier`; в режиме текстов shared-element к скрытой обложке не применяется, чтобы она не «оживала».
- `sharedBounds` для названия трека не делали: разные масштабы типографики дают заметный reflow при перелёте.
- Тесты: 23 suites / 156 тестов, 0 failures/errors (добавлен `MiniPlayerSwipeTest` — 8 тестов порогов и скорости). `assembleDebug` — BUILD SUCCESSFUL, APK 27 835 081 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Навигация и мини-плеер разделены, свайп-перемотка — сессия 40
- `ExpressiveBottomDock` теперь только навигационный бар: `Surface` во всю ширину, фон `surfaceContainerHigh` (alpha 0.97) доходит до нижней кромки, `navigationBarsPadding()` внутри, высота контента `DockNavHeight = 72dp`, обводка 1dp. Иконки выросли с 22dp до 28dp, подписи — с 11sp до 12sp, индикатор совмещён с квадратом иконки (`indicatorTop = 13dp`).
- `MiniPlayer.kt` переписан в `FloatingMiniPlayer`: отдельный островок высотой 64dp, радиус 30dp, тень 8dp, обложка 48dp, название/артист, кнопка Play/Pause 40dp (зона 48dp), полоска прогресса 2dp внизу с inset 24dp. Боковые отступы 16dp и позиция над баром задаются в `AppNavGraph`.
- Перемотка свайпом: `detectHorizontalDragGestures` на островке, прогресс следует за пальцем через `size.width`, при отпускании вызывается `PlayerViewModel.seekTo(fraction)`, при отмене жеста полоска возвращается к стартовой доле. Опрос позиции останавливается на время жеста и возобновляется через 120 мс после seek, чтобы полоска не прыгала назад.
- `AppNavGraph`: бар и мини-плеер — два независимых `AnimatedVisibility`. Высота бара измеряется через `onSizeChanged`; островок висит на `navBarHeight + 12dp`, виден при `showBottomBar && track != null && navBarHeightPx > 0`. `lastTrack` удерживает контент на время exit-анимации. `LocalBottomDockHeight = navBarHeight + анимированный резерв (64 + 12dp)`, поэтому контент вкладок не прыгает при появлении островка.
- Сворачивание плеера: exit заменён с полного `slideOutVertically` на `scaleOut(0.9f, TransformOrigin(0.5f, 1f)) + fadeOut`; мини-плеер входит `slideInVertically + scaleIn(0.85f, bottom origin) + fadeIn`. Визуально экран сжимается к низу и тает, а островок выезжает из-под бара.
- Тесты: 22 suites / 148 тестов, 0 failures/errors. `assembleDebug` — BUILD SUCCESSFUL, APK 27 635 291 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Плавающий нижний док — сессия 39
- `ExpressiveBottomDock`: боковые отступы увеличены с 16dp до 24dp, снизу — `navigationBarsPadding()` + 12dp (было 8dp), сверху осталось 8dp. Фон `surfaceContainerHigh` больше не тянется до краёв экрана и не прилипает к системной панели — док читается как отдельный островок.
- Радиус 30dp, обводка 1dp и тень 8dp не менялись; `DockNavHeight = 64dp`, `DockMiniHeight = 56dp`, обложка 44dp и кнопка Play 48dp сохранены — на ширине 320–360dp контент помещается, навигация по-прежнему делится через `weight(1f)`.
- Анимация мини-плеера переведена с `expandVertically`/`shrinkVertically` на `slideInVertically`/`slideOutVertically` + fade: строка выезжает из-под ряда навигации. Добавлено отдельное окно высотой 56dp над навигацией с `clipToBounds()`, чтобы во время выезда строка не рисовалась поверх иконок; фон дока растёт через `reveal` (spring NoBouncy/Medium), поэтому высота и slide синхронизированы.
- `LocalBottomDockHeight` автоматически вырос на 4dp (нижний padding), экраны уже используют его в `contentPadding` — правок не потребовалось.
- Тесты: 22 suites / 148 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 635 309 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Свайп плеера: перехват первого касания вместо nestedScroll — сессия 38
- `NestedScrollConnection` удалён полностью: `dismissNestedScrollConnection`, `Modifier.nestedScroll()`, корневой `detectVerticalDragGestures` и импорты `NestedScrollConnection`/`NestedScrollSource`/`nestedScroll`/`Velocity`/`Offset`. Причина: на тексте и пустых зонах жест не срабатывал.
- Новый жест — `Modifier.pointerInput(Unit) { awaitEachGesture { ... } }` на корневом `Box`, который оборачивает `Scaffold` всего плеера. `awaitFirstDown(pass = PointerEventPass.Initial)` получает касание раньше `LazyColumn`/`verticalScroll`; при своём жесте изменения потребляются в Initial-проходе, поэтому drag списка отменяется (`awaitPointerSlopOrCancellation` проверяет `isConsumed`).
- Решение о жесте принимается по накопленному `totalDy`: вверх (`totalDy <= 0`) всегда отдаётся списку/обложке; вниз двигает плеер, кроме касания внутри `LazyColumn` при уже проскролленном тексте (`isLyricsMode && inLyricsList && !lyricsListState.canScrollBackward`). Шапка, обложка и пустые зоны двигают плеер даже при проскролленном тексте.
- Области определяются реальными границами: `controlIslandBounds` и `lyricsListBounds` через `onGloballyPositioned { it.boundsInRoot() }`; касание из острова управления игнорируется. `LazyListState` поднят из `LyricsListView` в `PlayerScreen` и проброшен через `ExpressiveHeroZone` → `LyricsForeground`; границы списка передаются callback'ом `onListBoundsChanged` и сбрасываются в `null` через `DisposableEffect`, когда список уходит из композиции.
- Остров управления сохраняет функциональный guard `detectVerticalDragGestures { change, _ -> change.consume() }` (пустой `pointerInput` ничего не блокирует): кнопки и слайдер не запускают закрытие.
- Порог `DISMISS_THRESHOLD = 0.45f` сохранён: `finishPlayerDrag()` закрывает при `dragOffsetPx > screenHeightPx * 0.45f`, иначе `settlePlayer()` возвращает пружиной; отмена жеста в `finally` также возвращает плеер.
- Тесты: 22 suites / 148 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 635 309 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Свайп плеера: порог 45% и закрытие поверх текстов — сессия 37
- Порог закрытия снижен с 85% до 45% высоты экрана (`DISMISS_THRESHOLD = 0.45f`). Логика отпускания вынесена в `finishPlayerDrag()` и используется обоими путями: жестом на свободной зоне и nested-scroll'ом списка текста.
- Причина «свайп работает только за шапку»: жест с сессии 36 уже жил на корневом `Scaffold`, но `LazyColumn` текстов и `verticalScroll` обложки потребляли вертикальные изменения указателя раньше родителя, поэтому корневой `detectVerticalDragGestures` отменялся.
- На корневой `Scaffold` добавлен `Modifier.nestedScroll(dismissNestedScrollConnection)`:
  - `onPreScroll`: пока плеер смещён, сначала двигает плеер, а не список — контент не прокручивается под возвращающейся шторкой;
  - `onPostScroll`: забирает остаток тяги вниз, когда список уже в начале (`available.y < 0`), — свайп работает поверх текста песни;
  - `onPreFling`: выше порога закрывает, иначе возвращает пружиной, и гасит скорость списка, чтобы текст не улетал отдельной анимацией;
  - программные скроллы (`SideEffect`, автоцентрирование активной строки) игнорируются.
- Остров управления по-прежнему гасит вертикальные драги своим `pointerInput` (`controlIslandSwipeGuard`): его кнопки и слайдер не запускают закрытие. Шапка и пустые зоны обрабатываются корневым `detectVerticalDragGestures`.
- Тесты: 22 suites / 148 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 635 282 байта. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Мелкие UI-фиксы, свайп вниз по всему плееру и дизлайки — сессия 36
- Плеер, слот скачивания: у локального трека (`Source.LOCAL`) кнопка скачивания не показывается, но её место резервируется `Spacer(Modifier.size(46.dp))` — ряд «скачать/лайк/дизлайк» больше не прыгает между треками. Проброшено через `canDownload` в `ExpressiveHeroZone` → `CoverForeground` и `TrackGlassCard` (оба варианта обложки).
- `TrackCard`: название и автор объединены в одну `Column` внутри `headlineContent` (без `supportingContent`), поэтому у коротких названий исчезла пустая дыра между строками; в `HomeScreen.ContinueListeningCard` колонка центрируется по высоте обложки (`Arrangement.Center`).
- `TrackCard`: новый параметр `isPaused` — на паузе вместо бегущего эквалайзера рисуются три статичные точки `PausedEqualizerDots` того же размера 16dp. `LibraryViewModel.isPlaying` берётся из `PlayerController.state`, `LibraryScreen` передаёт `isPaused` для текущего трека.
- Плеер вынесен из `NavHost` в оверлей `AppNavGraph` (`isPlayerOpen`, `AnimatedVisibility` со slide): вкладка остаётся скомпонованной под плеером и реально проглядывает при свайпе. Системный «назад» закрывает оверлей через `BackHandler`, не трогая back stack; переход «Найти на YouTube» сначала закрывает оверлей.
- Свайп вниз: жест `detectVerticalDragGestures` живёт на всём `Scaffold` плеера, остров управления гасит вертикальные драги (`controlIslandSwipeGuard`), поэтому кнопки и слайдер не закрывают экран. Порог — `DISMISS_THRESHOLD = 0.85f` высоты экрана: ниже — возврат пружиной, на пороге и выше — `onBack()`. Смещение следует за пальцем без анимации (`snap()`), возврат — `animateFloatAsState` + `spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)`; `graphicsLayer` двигает весь плеер и плавно гасит альфу `1f - offsetY / screenHeight`, открывая вкладку под ним.
- Room v10: новая `disliked_tracks` (`DislikedTrackEntity`: `trackId` PK, `dislikedAt`), `DislikedTrackDao` (`insert` REPLACE, `delete`, `getAll`, `isDisliked` Flow), `MIGRATION_9_10`, DAO в `DatabaseModule`, схема `10.json` сгенерирована.
- Рекомендации: `RecommendationPlanner.filterDisliked` убирает дизлайкнутые треки; `HomeViewModel` загружает ID из DAO и фильтрует related-, fallback- и popular-кандидатов до отбора, чтобы дизлайки не занимали слоты.
- Плеер: `PlayerViewModel.isDisliked` (Flow из DAO по текущему треку) и `toggleDislike()`; дизлайк снимает лайк и taste-фидбек, лайк снимает дизлайк. В обоих layouts добавлен `DislikeSquircle` с `Icons.Filled.ThumbDown`, красная подсветка (`errorContainer`/`error`) при активном дизлайке.
- Тесты: 22 suites / 148 тестов, 0 failures/errors/skipped. `DatabaseMigrationTest` — новый кейс 9→10 и сквозной 1→10 с проверкой записи в `disliked_tracks`; `HomeViewModelRecommendationsTest` — фильтрация дизлайкнутого трека из выдачи. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 611 476 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Редизайн Пакет 4: Library/Settings + свайп вниз в плеере — сессия 35
- `LibraryScreen`: шапка с eyebrow «КОЛЛЕКЦИЯ» (11sp Bold primary, трекинг 1.4), заголовок «Медиатека» 34sp ExtraBold (трекинг −0.5), подпись с русской плюрализацией «X трек/трека/треков · Y ч Z мин»; для пустой коллекции длительность не показывается. Быстрые действия «Слушать всё» и «Перемешать» высотой 52dp в пропорции 1.6:1, `CircleShape`. Список — `TrackCard` с обложкой 60dp и радиусом 16dp, подсветкой играющего трека (`primaryContainer` 35%) и анимированным эквалайзером. Empty state: squircle-плашка 96dp с сердечком, заголовок 22sp, пояснение и кнопка «Найти музыку» (новый `onOpenSearch` в `AppNavGraph`, переход как у вкладки Search с сохранением состояния).
- `TrackCard`: новые параметры `isPlaying`, `artworkSize`, `artworkCornerDp` (дефолты 56/12 сохраняют вид Home/Search/Wave); `PlayingEqualizerIcon` на Canvas с `rememberInfiniteTransition` (три полоски, sin-волна, без рекомпозиций).
- `LibraryViewModel`: `currentTrackId` из `PlayerController.state` через `map` + `distinctUntilChanged` + `stateIn(WhileSubscribed(5000))` — строка играющего трека подсвечивается без лишних эмиссий.
- `SettingsScreen`: вертикальные отступы между секциями 32dp, заголовки секций 12sp Bold primary, карточки 30dp с тонким border 1dp (`onSurface` 4%), кнопка проверки разрешений 44dp, футер «MyMusic · версия X» из `BuildConfig.VERSION_NAME`. Горизонтальные отступы 20dp сохранены — как у Home и карточек.
- `PlayerScreen`: свайп вниз для закрытия. Жест живёт только в верхней зоне — `TopAppBar` и hero-зона в режиме обложки; в режиме текстов список сохраняет свой скролл, при открытой шторке очереди жест отключён. `detectVerticalDragGestures`, порог 20% высоты экрана или скорость >1200 px/s (`VelocityTracker` по последним событиям, а не по одному кадру). При перетаскивании контент (шапка + hero/контролы) уезжает вниз через `graphicsLayer.translationY` и теряет до 30% альфы; градиентный фон остаётся на месте, чтобы не проглядывало пустое окно. Ниже порога — возврат пружиной `NoBouncy`/`MediumLow` (незавершённый возврат отменяется новым касанием), выше порога — `onBack`, дальше экран уезжает штатным `popExit`-слайдом NavHost, без второй анимации и пустой паузы. `BackHandler` и guard `isDismissing` защищают от двойного pop.
- Тесты: 22 suites / 146 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 633 153 байта. Живой тест на устройстве не выполнялся по просьбе пользователя.
- Архитектурные фиксы сессий 31–34 (Room v9 и миграции, `PlayerController`, загрузки, тексты) не тронуты.

## Добивки аудита: скачанное, индексы Room, тексты, ошибки лайков, тесты миграций — сессия 34
- B4 (скачанное): `observeDownloadedIds`/`observeDownloadedTracks` строят список из `getAll()` и проверяют файл на диске (`isPlayable`): мусорные записи (удалённый файл, наследие HLS) вычищаются из БД через `pruneUnplayable` и больше не светятся как «скачано»; удаление перезапускает Flow Room, следующая эмиссия приходит уже чистой.
- C (Room): индексы `liked_tracks.season`, `liked_tracks.likedAt`, `play_history.playedAt`, `genre_weights.weight` (`Index` в сущностях); версия БД 9, `MIGRATION_8_9` создаёт четыре индекса с именами, которые Room генерирует сам; схема `9.json` сгенерирована, имена сверены с миграцией.
- C (тексты): загрузка текстов в `PlayerViewModel` вынесена из `collect` в отдельный поток с `flatMapLatest` (`flow<Unit> { loadLyrics(track) }`): старый запрос LRCLIB отменяется при смене трека и больше не блокирует обработку следующей смены; сброс позиции и ошибок остался в параллельном collect.
- C (ошибки лайков): `toggleLike` (плеер) и `removeLike` (медиатека) обёрнуты в `runCatching` + `rethrowCancellation`: сбой БД не крашит приложение, а показывает сообщение «Не удалось обновить лайк…» — в плеере через `uiState.error`, в медиатеке через новый `likeError` + Snackbar; taste-фидбек (`onLike`/`onUnlike`) обёрнут отдельно и только логируется.
- B7 (тесты миграций): в `DatabaseMigrationTest` шесть изолированных тестов на raw-SQL-заглушках схем v1–v3 (в `app/schemas` они не экспортировались): 2→3 (координаты), 3→4 (сезон), 4→5 (таблицы вкусов), 5→6 (downloaded_tracks), 7→8 (recently_shown + индекс), 8→9 (четыре индекса); сквозной тест теперь 1→9 и тоже проверяет индексы.
- Тесты: 22 suites / 146 тестов, 0 failures/errors/skipped (DatabaseMigrationTest — 8 кейсов). `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 611 487 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Монохромный ахроматический акцент, HEX-ввод и недавние цвета — сессия 33
- Баг «бордовый из чёрного»: у чёрного/белого/серого оттенок в HSL равен 0 (красный), а `accentColorScheme` подставлял в роли жёстко заданную насыщенность — ахроматический seed красился в бордовый. Добавлен порог `ACHROMATIC_SATURATION_THRESHOLD = 0.05f`: ниже него насыщенность ролей обнуляется (строго монохромная палитра), а светлота ролей тянется к светлоте seed на ±0.08, поэтому чёрный и белый дают различимые оттенки серого при сохранном контрасте пар. Та же защита в `PlayerPalette.computePlayerColors`: серые обложки больше не тонируются бордовым (раньше кламп принудительно поднимал насыщенность до 0.20).
- HEX-ввод в RGB-пикере: `OutlinedTextField` рядом с превью, двусторонняя связь — корректный код (#RRGGBB или RRGGBB, регистр не важен) двигает слайдеры и превью, слайдеры переписывают код; некорректный ввод цвет не применяет, поле переходит в `isError` с подсказкой. Ввод ограничен 7 символами («#» + 6 цифр), разбор — `parseHexColor`.
- Недавние цвета: `AppSettings.recentAccentColors` (ключ SharedPreferences `recent_accent_colors`, строка через запятую), `SettingsRepository.addRecentAccentColor` — LIFO, дедупликация, обрезка до 5 (`MAX_RECENT_ACCENTS`); вызывается из `setAccentColor` (пресеты) и `setCustomAccentColor` (не null). В секции «Оформление» над основной палитрой появился ряд «Недавние» с маленькими кружками (зона 36dp, кружок 22→26dp с галочкой на выбранном), тап применяет цвет мгновенно.
- Тесты: новые `AccentSchemeTest` (6 кейсов: монохромность чёрного/белого/серого в обеих темах, различимость серого, сохранение hue насыщенным seed, читаемость primary) и `SettingsRepositoryImplTest` (5 кейсов: LIFO, дедупликация, обрезка до 5, персистентность). Итого 22 suites / 140 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 611 486 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Верстка «Акцентного цвета»: скроллируемая палитра и круг с палитрой — сессия 32
- Баг: 7 кружков фиксированного размера (6 пресетов + «Свой цвет») лежали в обычном `Row` внутри карточки «Оформление» — на узких экранах ряд выходил за доступную ширину, последний элемент сплющивало в вертикальную полоску, зона нажатия съезжала.
- `SettingsScreen.kt`: ряд акцентов переведён на `Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()))` — кружки сохраняют размер и скроллятся горизонтально; крайние `Spacer(8.dp)` вместе с `spacedBy(10.dp)` дают 18dp от края карточки, при скролле кружки не прилипают к границам экрана (карточка сама отстоит на 20dp). Ортогональный скролл внутри вертикального `verticalScroll` экрана работает штатно.
- Старый `CustomAccentDot` удалён. В конце списка новый `CustomColorDot`: зона нажатия 44dp и кружок 34→40dp как у пресетов, заливка — замкнутый радужный `Brush.sweepGradient` с белой иконкой палитры; после применения своего цвета круг заливается этим цветом и получает галочку с кольцом `onSurface`. Вместо `indication = null` — штатная рябь (`clickable` без переопределения), круг читается как кнопка; тап открывает существующий RGB-диалог со слайдерами.
- Подсказка под заголовком обновлена: «Выберите цвет из палитры или нажмите на круг с палитрой в конце списка, чтобы задать свой RGB».
- Тесты: 20 suites / 129 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 645 526 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Комплексное ревью: аудит, регрессии UI и архитектурные правки — сессия 31
- ЭТАП 1 (аудит, отчёт выдан до правок): критично — синглтон `PlayerController` не переподключался к пересозданному `MusicService` (мёртвый binder до перезапуска процесса, `release()` — мёртвый код); провал `buildAsync()` завершал Deferred навсегда; `LocalContentColor` во всём приложении = `Color.Black` после удаления корневого Scaffold (сессия 29) — первопричина «чёрных заголовков»; `Theme.MyMusic` без values-night → белое окно; интерсептор OkHttp перезаписывал User-Agent всех запросов NewPipe; в загрузках не было `Result.retry()`/backoff. Чисто: GlobalScope/runBlocking/main-thread IO, Activity-контекст в Hilt, циклы в DI-графе, N+1, fallbackToDestructiveMigration, кэш протухших stream-URL, утечки потоков.
- Белая вспышка: непрозрачный `background` на корневом Box в `AppNavGraph`; новый `values-night/themes.xml` и явный `android:windowBackground` (#FFF8F5 / #171210, совпадает с Color.kt) — окно больше не белое ни на холодном старте, ни в переходах вкладок.
- Режим «Текст»: убран второй `Spacer(weight(1f))` — в режиме текстов два равных веса делили остаток пополам, остров управления уезжал в середину, снизу оставалась дыра. Теперь список занимает `weight(1f)`, остров прижат к низу (`wrapContentHeight`); в режиме обложки поведение сессии 30 сохранено.
- Чёрные заголовки: `MyMusicTheme` провайдит `LocalContentColor = onBackground` (то, что давал удалённый Scaffold); явный `onSurface` на «Настройки» и названия секций; `contentColor = onSurface` в Scaffold плеера, иначе `contentColorFor(Color.Transparent)` = Unspecified.
- Акцентный цвет: кружки палитры были физически выключены на Android 12+ (`enabled = !supportsDynamicColors`), тап не доходил до ViewModel. Теперь включены всегда и пишут в `customAccentColor` — он имеет высший приоритет в теме и перекрывает Dynamic Color; `accentColorArgb` остался legacy для <12. Кольцо «выбран» одно: пресет или «Свой цвет».
- Палитра плеера: убран буст насыщенности ×1.4 (кислотные цвета на серых обложках) — ×1.15 с клампом 0.20–0.58, светлота 0.38–0.62; `onPrimaryContainer`/`onSecondaryContainer` больше не равны самому тинту (текст сливался с контейнером) — контрастная пара от светлоты; рампа фона сглажена (0.58/0.26 вместо 0.88/0.40) и получила явные colorStops 0/0.55/1; `blendPaletteWithAccent` пересчитывает контрастные роли после смешивания; `CancellationException` в `computePlayerColors` реброузится.
- Архитектура 1 (`PlayerController`): `MediaController.Builder.setListener` + `onDisconnected` → снять слушателей, освободить мёртвый контроллер, пересоздать Deferred и переподключиться; провал подключения — ретрай с экспоненциальной задержкой (до 5 попыток) и ошибкой в state вместо вечного молчания; `release()` реально снимает слушателей. `MusicService.onTaskRemoved`: stopSelf только если ничего не играет.
- Архитектура 2 (загрузки): транзиентные сбои (IOException, 408/425/429/5xx, несовпавший размер) → `Result.retry()` + `setBackoffCriteria(EXPONENTIAL, 30s)`, до 3 попыток; постоянные («только онлайн», нет каталога) → сразу failure. Текст из `outputData[KEY_ERROR]` доезжает до UI: `DownloadState.Error.message` → `DownloadMessages.friendly` → Snackbar плеера. `lastTapAt` → `ConcurrentHashMap` с TTL; повторный тап по качающемуся треку не обрывает RUNNING-воркер (проверка `InProgress`).
- Архитектура 3 (сеть): заголовки подставляются только если их нет — NewPipe сохраняет браузерный User-Agent, дубли UA исключены.
- Тесты: 20 suites / 129 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 526 739 байт. APK установлен на SM-A266B по WiFi (`adb install -r` — Success), живой прогон не выполнен: телефон на экране блокировки, разблокировать удалённо нельзя; `svc power stayon` возвращён в false.
- Осталось (не критично): миграции 2→3…7→8 без изолированных тестов и неэкспортированные схемы v1–v3; индексы на `liked_tracks.season/likedAt` и `play_history.playedAt`; `loadLyrics` inline в collect (нужен `flatMapLatest`); Snackbar с причиной провала только в плеере, в медиатеке — крестик без текста; гонка `resetReplayCache` в NavigationEvents.

## Фиксы после рефакторинга навигации + кастомизация тем — сессия 30
- Белые экраны: после удаления корневого Scaffold (сессия 29) экраны остались без фона. В корень Home, Wave, Settings, Library и Search добавлен явный `Modifier.background(MaterialTheme.colorScheme.background)` (для Search/Library — на сам Scaffold).
- Плеер, чёрный заголовок: название трека в стеклянной карточке стало чёрным — `contentColorFor` полупрозрачного контейнера (палитра светлой обложки) давал чёрный текст на тёмном фоне. Цвет задан явно: `onSurface` в `TrackGlassCard` и в варианте 3 (название на обложке).
- Плеер, «дыра»: hero-зона больше не растягивается на весь свободный экран в режиме обложки — `weight(1f)` оставлен только для режима текстов (списку нужна ограниченная высота), `AnimatedContent` и `CoverForeground` оборачивают контент по высоте, а остаток высоты уходит в `Spacer(weight(1f))` под остров. Карточка трека и остров управления идут плотно, без пустого зазора между ними.
- Темы: в `AppSettings` добавлены `themeMode` (LIGHT/DARK/SYSTEM, по умолчанию SYSTEM) и `customAccentColor` (Int?, null по умолчанию); в `SettingsRepository` — `setThemeMode`/`setCustomAccentColor`, хранение в SharedPreferences (`theme_mode`, `custom_accent_color`).
- `MyMusicTheme`: darkTheme выбирается по `ThemeMode` (isSystemInDarkTheme только для SYSTEM); приоритет цвета — собственный акцент (все версии Android) → Dynamic Color (12+) → ручная палитра (<12) → fallback.
- Настройки: в «Оформлении» — `SingleChoiceSegmentedButtonRow` «Светлая/Тёмная/Система» и кружок «Свой цвет», открывающий диалог с тремя RGB-слайдерами, живым превью + HEX, кнопками «Применить»/«Отмена»/«Сбросить». Собственный цвет работает на всех версиях Android и перекрывает системные цвета.
- Палитра плеера: при заданном `customAccentColor` палитра обложки смешивается с ним на 40% (`blendPaletteWithAccent`), полупрозрачные роли сохраняют альфу — градиенты и подсветки не конфликтуют с темой.
- Тесты: 20 suites / 129 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 517 791 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Архитектурный фикс перехода в плеер: док-оверлей + вертикальный слайд (от Claude) — сессия 29
- Первопричина «25-го кадра» (а не анимация): док жил в слоте `bottomBar` корневого `Scaffold`, поэтому при входе/выходе из плеера `showBottomBar` переключался, `innerPadding` прыгал, и `NavHost` переизмерял оба экрана в один кадр — отсюда «развал» (кнопки и текст исчезали, фон/обложка дёргались). Поэтому ни `ExitTransition.None` (сессия 27), ни fade (сессия 28) кадр не убирали: fade к тому же добавлял джанк, оборачивая весь тяжёлый сабтри в offscreen alpha-слой.
- `AppNavGraph`: корневой `Scaffold` заменён на `Box`. `NavHost` теперь всегда во весь экран (`fillMaxSize`) и НЕ меняет размер при показе/скрытии дока. Док вынесен в оверлей (`AnimatedVisibility`, slide снизу + fade, выравнивание по низу). Высота дока меряется `onSizeChanged` и раздаётся вкладкам через `LocalBottomDockHeight` (новый `navigation/LocalBottomDock.kt`).
- Переход плеера: вместо fade — вертикальный slide (`slideInVertically`/`slideOutVertically`, 300 мс, `FastOutSlowInEasing`) — дешёвая трансляция без offscreen-слоя. Нижний экран держится на месте и видимым (`fadeIn/fadeOut(alpha = 1f)` в `tabEnter/tabExitTransition` для маршрута Player) — плеер наезжает «шторкой», за ним не мелькает пустой фон.
- `PlayerPalette`: добавлен `prewarmPlayerPalette(context, url)` — прогрев палитры обложки в кэш при тапе по треку (вызов в `openPlayer`). К первому кадру PlayerScreen цвет уже в кэше, `rememberPlayerDynamicColors` берёт его синхронно — без `isLoading` и без 400 мс анимации фона прямо во время перехода. Экстракция вынесена в общий `computePlayerColors` без дублирования логики.
- Инсеты: корневого Scaffold больше нет, поэтому статус-бар ставят сами экраны — Home/Wave через `statusBarsPadding`/`WindowInsets.statusBars`, Search/Library через `contentWindowInsets = WindowInsets.statusBars`, Settings через `systemBarsPadding`. Нижний отступ вкладок = `LocalBottomDockHeight` (высота плавающего дока, уже включает `navigationBarsPadding`).
- Затронуто: `AppNavGraph.kt`, `LocalBottomDock.kt` (новый), `PlayerPalette.kt`, `HomeScreen.kt`, `WaveScreen.kt`, `SearchScreen.kt`, `LibraryScreen.kt`, `SettingsScreen.kt`.
- Сборка: `assembleDebug` — BUILD SUCCESSFUL; `testDebugUnitTest` — BUILD SUCCESSFUL (под JDK 17 из Eclipse Adoptium; JAVA_HOME задавался только для команды, т.к. AGP требует Java 17). Живой тест на устройстве — на пользователе.

## Фейд вместо слайда и убран blur фона — сессия 28
- Проблема: фикс сессии 27 дал наложение экранов — полупрозрачный верх градиента фона плеера просвечивал главную при переходах; плюс при открытии плеера были просадки FPS от `Modifier.blur`.
- `AppNavGraph`: для маршрута Player убраны None-переходы и slide — теперь быстрый кросс-фейд `fadeIn`/`fadeOut(tween(150))` в обе стороны: плеер с непрозрачным фоном перекрывает главную, а не слипается с ней. `tabEnterTransition`/`tabExitTransition` при полноэкранном маршруте тоже возвращены к fade (150 мс) вместо None. Слайд между вкладками (300 мс) и `launchSingleTop` у `openPlayer` сохранены.
- `PlayerScreen`: полностью убран `Modifier.blur` — и с полноэкранного фона режима текста (44dp), и с hero-обложки (26dp × recede). Фон текстов теперь градиент `primary 45% → primaryContainer 22% → surface` с прежним scrim 0.65. Recede-анимация обложки сохранена (смещение, поворот, масштаб, альфа) — только без размытия.
- Непрозрачность: тонировка фона композитится поверх surface (`compositeOver`), а градиент рисуется на самом Scaffold (включая полосу под прозрачной TopAppBar) — корневой фон плеера полностью непрозрачный, главная больше не просвечивает.
- Тесты: 20 suites / 129 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 621 184 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Атомарный выход из плеера — сессия 27
- Баг «разваливающегося» плеера (25-й кадр): при pop из PlayerScreen вкладка под ним въезжала с fade (180/120 мс), а док появлялся мгновенно и накрывал низ экрана — на промежуточных кадрах висел «полуплеер» без контролов и слайдера. Пользователь зафиксировал скриншот.
- `AppNavGraph`: `tabEnterTransition`/`tabExitTransition` при `direction == 0` (полноэкранный маршрут с любой стороны) отдают `EnterTransition.None`/`ExitTransition.None` вместо fade — pop из плеера теперь атомарный: плеер исчезает и Home с доком появляются в одном кадре, без промежуточных состояний. Слайд между вкладками (300 мс) сохранён, вход плеера (slide снизу + fade) не тронут.
- `openPlayer`: добавлен `launchSingleTop = true` — защита от дублей Player в стеке, где повторный pop проигрывал бы slide-вход плеера поверх самого себя.
- `PlayerScreen`: проверены все внутренние анимации — exit-анимаций, реагирующих на закрытие экрана, нет: `AnimatedContent` героя работает только по `isLyricsMode`, `Crossfade` — по состоянию загрузки, `animate*AsState` — по контентным состояниям. Шторка очереди закрывается только мгновенным удалением из композиции (snap, без hide-анимации) — зафиксировано комментарием, чтобы анимация скрытия не вернулась.
- Проверено по байткоду navigation-compose 2.8.4: `ExitTransition.None` у плеера применяется честно (нет дефолтного fadeOut поверх), а pop-переход при полноэкранном маршруте собирается из popEnter/popExit.
- Тесты: 20 suites / 129 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 621 184 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Плавные переходы вкладок и фиксы дока — сессия 26
- `AppNavGraph`: переходы между вкладками — горизонтальный слайд по индексу вкладки (выезд с нужной стороны на треть ширины, 300 мс, `FastOutSlowInEasing`) + fade; направление вычисляется через `tabIndex()` по `bottomNavItems`. Для полноэкранных маршрутов и возвратов с них — только мягкий fade (вход 180 мс, выход 120 мс) без слайда.
- `PlayerScreen`: `exitTransition` и `popExitTransition` заменены на `ExitTransition.None` — тяжёлый экран (blur, большая обложка) больше не анимируется при сворачивании, просадка кадров убрана (в сессии 25 возврат из плеера давал 27.5% janky). Вход остался слайдом снизу + fade.
- `ExpressiveBottomDock`: контент дока обёрнут в `Box` с `matchParentSize()` и `clip(shape)` — полоска прогресса мини-плеера обрезается по скруглённому контуру и не выпирает за края дока.
- Тесты: 20 suites / 129 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 621 184 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Полировка анимаций нижнего дока — сессия 25
- `ExpressiveBottomDock`: ряд навигации закреплён у нижней кромки и больше не участвует в анимации мини-плеера. Фоновая поверхность `DockBackground` растёт отдельно (64 → 120dp), читая `State<Float>` только внутри себя; мини-плеер раскрывается через `AnimatedVisibility` (`expandVertically` + `fadeIn`, выход — `shrinkVertically` + `fadeOut`). Высота дока вместе с детьми не анимируется, навигация не смещается.
- Пружины: индикатор — `Spring.DampingRatioNoBouncy` / `StiffnessMediumLow` (как просил пользователь), иконка — NoBouncy / Medium, раскрытие дока и мини-плеера — NoBouncy / Medium. Убраны отскок 0.58 и рассинхрон фаз.
- `ExpressiveNavigationBar`: смещение индикатора читается в `graphicsLayer` (фаза отрисовки), поэтому ряд не рекомпозируется на каждом кадре; масштаб иконки и альфа подписи — тоже `graphicsLayer`. Градиент индикатора запоминается, колбэки вкладок стабильны (`remember` + `rememberUpdatedState`), индекс — `remember(items, selectedRoute)`.
- `AppNavGraph`: `selectedRoute` и `showBottomBar` вычисляются через `derivedStateOf`; `onTabSelect` и `onDockExpand` стабильны (`remember(navController)`).
- `MiniPlayer`: прогресс больше не опрашивается на паузе (`while (isPlaying)`), первый замер выполняется сразу.
- Тень дока перенесена с `Modifier.shadow(..., clip = false)` на `Surface(shadowElevation = 8.dp)` — один слой вместо двух.
- Тесты: 20 suites / 129 тестов, 0 failures/errors/skipped. `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 515 714 байт.
- Live SM-A266B: APK установлен, приложение запущено. Появление мини-плеера через «Мою волну» (без перехода в плеер): 599 кадров, 1.5% janky, 95-й перцентиль 14 мс. Переключения вкладок с открытым мини-плеером: 5.9% janky, 90-й 9 мс; серия из 5 быстрых тапов: 3.4% janky, 95-й 12 мс. Крашей и `FATAL EXCEPTION` нет. Возврат из полноэкранного плеера даёт 27.5% janky — это тяжёлый slide/fade всего `PlayerScreen`, а не док; изолированный док даёт 1.5%.
- Скрытие мини-плеера отдельно не воспроизведено: в приложении нет действия «стоп/очистить» (только пауза), а очистка очереди доступна лишь на экране плеера, где док скрыт. Выход использует симметричный `shrinkVertically` + `fadeOut`.

## Редизайн Пакет 1 (Home) + Пакет 3 (единый нижний док) — сессия 24
- Home получил трёхуровневую иерархию: `HomeSectionHeader` с eyebrow 11sp Bold в `primary` (трекинг 1.4) и заголовком 22sp Bold. Тексты: «РЕКОМЕНДАЦИИ / Для вас», «ИСТОРИЯ / Недавнее», «АРХИВ / Воспоминания», «КОЛЛЕКЦИЯ / Любимое». `BlockGap` увеличен с 26 до 32dp.
- Hero «Продолжить»: радиус 30dp, градиент `primaryContainer` 45% → `surfaceContainerHigh`, обложка 96dp squircle, заголовок `headlineSmall` (26sp) максимум в 2 строки, Play 48dp с иконкой 24dp. Eyebrow сокращён до «ПРОДОЛЖИТЬ», иначе обрезался на узких экранах.
- «Недавнее»: убрана чёрная плашка с белым текстом; обложка 132dp с радиусом 22dp, подписи под обложкой 13sp SemiBold / 11sp.
- «Воспоминания»: радиус 30dp, превью 3 трека (`MEMORY_PREVIEW_LIMIT = 3`), сезон 26sp, счётчик `displaySmall` 40sp, вертикальные отступы между блоками 32dp. `LikedCard` приведён к тому же стилю: радиус 22dp, внешний заголовок, внутренняя подпись убрана.
- Новый `ExpressiveBottomDock.kt`: один объект с отступами 16dp, радиусом 30dp, тенью 8dp, фоном `surfaceContainerHigh` 96% и бордером 1dp `onSurface` 6%. Внутри — mini-player и ряд навигации; появление трека анимируется ростом на 56dp (пружина 0.8 / MediumLow), навигация остаётся на месте.
- `MiniPlayer.kt` переписан на `internal MiniPlayerRow` высотой 56dp: обложка 44dp (радиус 14dp), название 14sp SemiBold, артист 11sp, Play 40dp с зоной касания 48dp, прогресс 2dp внутри клипа, разделитель 1dp с inset 16dp. Собственной панели и тени больше нет.
- `ExpressiveNavigationBar.kt` стал рядом 64dp без фона и тени: пружинный индикатор damping 0.58 / `StiffnessMedium`, иконка выбранного 1.18, подпись 11sp с `lineHeight = 16.sp` для точной укладки в 64dp.
- `AppNavGraph.kt` подключает `ExpressiveBottomDock` вместо пары `MiniPlayer` + `ExpressiveNavigationBar`. `PlayerScreen`, `LibraryScreen`, `SettingsScreen` не тронуты.
- Тесты: 20 suites / 129 тестов, 0 failures/errors/skipped (UI-тестов нет, изменения проверены сборкой). `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 616 075 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## SoundCloud progressive + умный fallback YouTube — сессия 23
- `SoundCloudRepositoryImpl.downloadCapability` переведён с `HLS_ONLY` на `PROGRESSIVE`. `SoundCloudDownloadSource` уже вызывает `resolveStreamUrl(requireProgressive = true)`, поэтому SC-трек с progressive MP3 качается напрямую, без обращения к YouTube. Если у трека только HLS, resolve возвращает ошибку, и резолвер переключается на YouTube.
- `DownloadSourceResolver`: после строгого матча для YouTube выполняется нестрогий поиск. Запрос `Артист Название` очищается от `official audio`, `remaster`, `feat.` и других служебных пометок; берутся топ-10 результатов; кандидат принимается, если `abs(durationMs - seed.durationMs) <= 15_000` (при неизвестной длительности seed фильтр не применяется, кандидат без длительности отбрасывается) и имя артиста встречается в названии видео или имени канала без учёта регистра. Резолвится первый подходящий кандидат.
- Если очищенный запрос совпадает со строгим, второй сетевой запрос не делается — используется уже полученная выдача. `RecentlyFailedSources.markFailed` вызывается только после неудачи и строгого, и нестрогого матчей.
- Единый текст ошибки: `DownloadMessages.SOURCE_NOT_FOUND` = «Не удалось найти этот трек на YouTube для скачивания. Возможно, он удален или доступен только в SoundCloud». Он же используется как дефолт `DownloadSourceUnavailableException` и в Snackbar SoundCloud в `PlayerScreen`; действие «Найти» сохранено для ручного поиска.
- Тесты: `DownloadSourceResolverTest` — 16 кейсов (SC progressive без YouTube, SC HLS-only → YouTube, строгие матчи, нестрогий матч с разницей 5 с, отказ при разнице >15 с, артист в названии/канале, HLS-результат, TTL, точный текст ошибки); новый `SoundCloudRepositoryCapabilityTest` — 1 кейс. Всего 20 suites / 129 тестов, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 508 441 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Фиксы скачивания: свежий резолв, атомарная запись, отмена — сессия 22
- `DownloadManager` больше не резолвит источник до постановки задачи: в WorkData передаются только идентичность трека и ссылки для резолва; ключи `KEY_DIRECT_STREAM_URL`, `KEY_DIRECT_MIME_TYPE`, `KEY_RESOLVED_SOURCE` удалены. Ошибки источника теперь приходят через FAILED WorkInfo.
- `DownloadTrackWorker` стал тонким: получает `DownloadSourceResolver` через `DownloadWorkerEntryPoint` и делегирует скачивание в новый `DownloadEngine`.
- `DownloadEngine`: перед каждой попыткой заново резолвит URL; при HTTP 403/410 делает ровно один повторный резолв и одну повторную загрузку, затем возвращает `HttpStatusException` с кодом. HLS, пустой файл и недоступный каталог дают понятную `DownloadException`.
- Запись: `.part` с уникальным `workId` → проверка `Content-Length` и ненулевого размера → `Files.move(REPLACE_EXISTING, ATOMIC_MOVE)` поверх целевого файла. Room обновляется только после rename; при любой ошибке удаляется только `.part`, старый рабочий файл не трогается. При ошибке сохранения в Room готовый файл не удаляется.
- Отмена: `DownloadEngine.downloadToFile` вешает `Job.invokeOnCompletion` → `Call.cancel()`; `NewPipeDownloader.withCancellation` через ThreadLocal отменяет активные NewPipe `Call`. Все вызовы `SearchInfo`/`StreamInfo`/`KioskInfo.getInfo` в SoundCloud, YouTube и чартах обёрнуты в него; отмена не логируется как сетевая ошибка.
- `NetworkModule`: `HttpLoggingInterceptor` (BASIC) подключается только при `BuildConfig.DEBUG`; в release токены и подписанные URL не попадают в логи.
- Тесты: новый `DownloadEngineTest` — 4 кейса: 403 → повторный резолв и успех; повторный 403 → failure после одной попытки; HTTP 500 → старый файл цел, Room не обновлён, `.part` удалён; успешная атомарная замена существующего файла. 19 suites / 121 тест, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 503 796 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Полный откат DSP и визуализатора — сессия 21
- Удалены `AudioDsp.kt`, `AudioEnergy.kt`, `AudioDspTest.kt` и `PlaybackAudioSession.kt`; из `MusicService` убрана публикация аудиосессии.
- `ExpressivePlayerSlider`: волна Phase B с постоянной амплитудой 9dp, длина 44dp, сужение к краям, круглый бегунок на гребне. Амплитуда больше не зависит от звука; на паузе волна плавно выпрямляется за 500 мс, фаза замирает. Обёртка Material3 Slider с drag-cancel, тапами и свайпами сохранена.
- `PlayerScreen`: удалены запрос RECORD_AUDIO, `AudioPermissionGate`, `rememberAudioEnergy` и параметры `canVisualize`/`visualizerDelayMs`.
- `SettingsRepository`/`AppSettings`, `SettingsRepositoryImpl`, `SettingsViewModel`, `SettingsScreen`: удалена настройка задержки визуализации и секция «Визуализация».
- `AndroidManifest.xml`: удалено разрешение RECORD_AUDIO.
- Тесты: `AudioDspTest` удалён; 18 suites / 117 тестов, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 406 945 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Калибровка задержки волны — сессия 20
- `AppSettings.visualizerDelayMs` (по умолчанию 0, диапазон -200..+200) хранится в SharedPreferences `mymusic_settings` под ключом `visualizer_delay_ms`; при загрузке значение ограничивается диапазоном, `SettingsRepository.setVisualizerDelayMs` пишет его в IO-диспетчере.
- `SettingsScreen`: новая секция «ВИЗУАЛИЗАЦИЯ» со слайдером «Задержка визуализации» -200..+200 мс, живой подписью и локальным draft — в настройки значение пишется по отпусканию ползунка.
- `AudioDsp.EnergyDelayBuffer`: кольцевой буфер на 500 мс (64 слота) с монотонными таймстампами; `push` вытесняет старые кадры, `valueAt(now, delayMs)` отдаёт самый свежий кадр не позже `now - delay`; пустой буфер и задержка больше окна — 0; отрицательная задержка читает самый свежий кадр.
- `AudioEnergy`: каждый FFT-кадр пишется в буфер с `SystemClock.elapsedRealtimeNanos`; при отрисовке читается `sampler.energyAt(now, visualizerDelayMs)`. Если FFT не приходил дольше 120 мс — 0. Буфер очищается в `start()`/`release()` (пауза, смена трека, потеря разрешения, lifecycle).
- `PlayerScreen`: `visualizerDelayMs` из `appSettings` прокинут в `PlayerProgressSection` → `rememberAudioEnergy`; `ExpressivePlayerSlider` получает уже задержанное `State<Float>`, его визуальная формула не менялась.
- Тесты: 6 новых в `AudioDspTest` — чтение значения 100 мс назад, вытеснение за окно 500 мс, сохранение кадров внутри окна, пустой буфер/reset, отрицательная задержка и санитизация. 19 suites / 144 теста, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 406 941 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Адаптивный порог и музыкальные полосы — сессия 19
- `AudioDsp`: полосы переделаны под ритм — Kick 30–120 Гц (бочка/бас), Snare_Vocal 150–600 Гц (снейр/клэп/тело вокала), Presence 1500–6000 Гц (щелчок снейра и согласные). Промежутки 120–150 и 600–1500 Гц не учитываются.
- Веса spectral flux: 1.2 / 1.5 / 1.0 — небольшой акцент на снейре и вокале.
- `TransientDetector` хранит скользящее среднее энергии за 12 кадров (~600 мс при 20 Гц FFT). Ударом считается только рост, где текущая энергия выше среднего в 1.5 раза и выше нижнего порога 0.04; слабый фон и мелкий шум волну не двигают.
- `AudioEnvelope`: атака 8 мс, спад 200 мс — волна резко подпрыгивает на удар и плавно опускается, без мелкой вибрации.
- Визуальная формула `ExpressivePlayerSlider` не менялась: `0.55 + 1.30 * sqrt(live)`, ограничение 1.65. Сильный удар даёт ~1.5x амплитуды, presence добавляет мягкий акцент.
- Тесты: 21 в `AudioDspTest` — новые границы полос, веса flux, игнорирование слабого шума, отсечение слабого роста адаптивным порогом, сильный удар и плавное затухание. 19 suites / 138 тестов, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 502 039 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Транзиенты вместо общей громкости — сессия 18
- `AudioDsp`: вместо Full Spectrum RMS — три полосы: Low 40–150 Гц (бочка/бас), Mid 150–1000 Гц (снейр/клэп/вокал), High 3000–10000 Гц (хэты/тарелки). Для каждой считается средний RMS комплексных бинов; 150 Гц отнесено к Low.
- `spectralFlux` суммирует только положительные приросты полос с весами 1.0/1.3/2.0 (компенсация числа бинов и спада высоких частот), результат ограничен 0..1. Падение и неизменная энергия вклада не дают — без нового удара значение сразу возвращается к нулю.
- `TransientDetector` хранит энергию предыдущего FFT-кадра; первый кадр задаёт базу, `reset()` очищает историю при смене трека, сессии и освобождении Visualizer.
- `AudioEnvelope`: атака 8 мс, спад 100 мс — волна резко подпрыгивает на удар и быстро опадает. `AudioSample` живёт 120 мс (запас на два пропущенных FFT-кадра при 20 Гц).
- `AudioEnergy`: `AudioSampler` использует `TransientDetector`, сброс детектора в `start()`/`release()`; логика Visualizer, разрешения, lifecycle и ambient-фолбэка не менялась.
- `ExpressivePlayerSlider`: амплитуда `9dp * waveHeight * (0.55 + 1.30 * sqrt(live))` с ограничением 1.65; корневая кривая поднимает тихие хэты, на паузе `waveHeight` за 500 мс уходит в 0.
- Тесты: 18 в `AudioDspTest`, включая разбиение по полосам, границы 40/150/1000/3000/10000 Гц, веса flux, детектор «удар → тишина» и сценарий «пик → быстрое затухание». 19 suites / 135 тестов, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 502 039 байт. Живой тест на устройстве не выполнялся по просьбе пользователя.

## Полный спектр вместо баса в пульсации волны — сессия 17
- `BassDsp.kt` → `AudioDsp.kt`, `BassEnergy.kt` → `AudioEnergy.kt`, `BassDspTest.kt` → `AudioDspTest.kt`; старые имена удалены.
- `audioRms`: средний RMS комплексных signed-бинов в полосе 40 Гц–16 кГц (корень из суммы квадратов, делённый на число бинов), DC и Nyquist исключены, нормализация на 128 и clamp 0..1. Теперь вклад дают синты и хэты, а не только низы.
- `AudioEnvelope` сохранил атаку 15 мс и спад 140 мс — волна дышит плавно, без судорожных скачков.
- `rememberAudioEnergy`/`AudioSampler` заменили bass-версии; тег логов `AudioEnergy`, логика Visualizer, ambient-фолбэка, разрешения и жизненного цикла не менялась.
- `ExpressivePlayerSlider`: параметр `energy` → `audioEnergy`; амплитуда `9dp * waveHeight * (0.65 + 1.20 * sqrt(live))` с ограничением 1.65. Корневая кривая компенсирует то, что средний RMS широкой полосы ниже узкополосного басового; на паузе `waveHeight` по-прежнему за 500 мс уходит в 0.
- Тесты: `AudioDspTest` проверяет высокочастотные бины (5 и 10 кГц), равенство вклада низов и верхов, границы 40 Гц/16 кГц, среднее по 371 бину, насыщение, DC/Nyquist и envelope. 19 suites / 134 теста, 0 failures/errors/skipped.
- `testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, APK 27 502 061 байт. Живой тест на устройстве не выполнялся по просьбе пользователя — проверка на телефоне за ним.

## Возврат волны Phase B внутрь Material3 Slider — сессия 16
- Пользователь отклонил прямую линию сессии 15: визуал таймлайна снова волна Phase B, но техническая обёртка Material3 `Slider` сохранена без изменений.
- `ExpressivePlayerSlider`: Canvas в слоте `track` высотой 38dp рисует синусоиду с длиной волны 44dp и сужением `sin(PI*u)` к краям, stroke 3dp с круглыми концами. Непропетанная часть — серая волна, буфер — та же волна плотнее, пропетанная — волна в акценте; клипы по `startX`/`progressX`/`bufferX` учитывают RTL через `trackX`.
- Thumb Material3 заменён прозрачным `Spacer(20.dp)` — он держит отступ дорожки и зону касания; видимый круглый бегунок 5dp рисуется в `track` на гребне волны по той же формуле синуса. При захвате работает пружина `spring(0.35f, StiffnessLow)` и ореол, как в Phase B.
- Басовый импульс (`BassDsp`, 40–180 Гц, envelope) меняет только амплитуду: `9.dp * waveHeight * (0.72f + 0.88f * live)`, форма синусоиды не искажается. На паузе `waveHeight` за 500 мс уходит в 0 (волна выпрямляется), фаза `rememberPlaybackPhase` замирает на текущем значении.
- `PlayerScreen`: позиция и буфер по-прежнему сбрасываются на смену `trackId` — морф `LinearProgressIndicator` ↔ `Slider` работает.
- Проверка: `gradlew testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, 19 suites / 133 теста, 0 failures/errors/skipped; APK 27 502 061 байт.
- Live SM-A266B: волна плавная и равномерная, бегунок круглый на гребне, амплитуда дышит 22.5→37 px при басе и 5 px на паузе, drag следует за пальцем с ореолом, tap-перемотка работает, `BassEnergy: Visualizer: сессия 4001/4033`.

## Откат визуала таймлайна к Phase B — сессия 15
- Пользователь отклонил волновой Canvas сессии 14: синусоида на всю ширину дорожки и вертикальный thumb-«палка» вернулись к спокойному виду Phase B.
- `ExpressivePlayerSlider` сохранил обёртку Material3 1.3.1 `Slider` (interactionSource, semantics, клавиатура, RTL, drag-cancel, минимальная зона 48dp) и слот `track`: непропетанная часть — серая линия 2.5dp, буфер — та же линия плотнее, пропетанная — сплошная заливка `primary`. Форма линии больше не искажается.
- Басовый импульс (`BassDsp`, 40–180 Гц, одна временная envelope) привязан к толщине 2.5→3dp и мягкому двухслойному свечению (alpha 0.10/0.14 от энергии). На паузе `calm`-анимация 500 мс и спад envelope возвращают линию к покою: свечение и утолщение исчезают без скачка.
- Thumb — собственный круглый бегунок 11dp с пружинным ростом 30% и ореолом при захвате (как в Phase B), вместо вертикального handle Material3.
- Морф `LinearProgressIndicator` ↔ `Slider` восстановлен: на смене трека позиция и буфер обнуляются (`remember(trackId) { 0 }`), иначе старая позиция переезжала в новый трек и `hasStarted` защёлкивался сразу, не давая показать индикатор.
- `SinePulseBar.kt` переименован в `BassEnergy.kt` (отдельной полосы над слайдером больше нет); добавлены логи `BassEnergy` для проверки источника энергии на устройстве.
- Проверка: `gradlew testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL, 133 JVM-теста, 0 failures/errors/skipped; APK 27 502 042 байт.
- Live SM-A266B: линия ровная, thumb круглый, свечение дышит (яркость ореола 37→66→104 при басе, 0 на паузе), drag и tap-перемотка работают, индикатор загрузки появляется на старте трека и морфит в слайдер, `BassEnergy: Visualizer: сессия 3961`.

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
- 129 реально выполненных JVM-тестов (20 suites), 0 failures/errors/skipped; `assembleDebug` подтверждён, APK 27 508 441 байт. Lint в этой сессии не запускался.
- Room v8, схемы `4.json`–`8.json`; ONNX/YAMNet и `core/audio` удалены revert-коммитом `9e74fb6`.
- «Для вас»: related-треки YouTube/SoundCloud через NewPipe, 4–6 треков-сидов, кап 2/3, fallback только при <10 и только по строгому артисту; глобальный популярный запрос — только на пустой истории.
- Скачивание: `DownloadSourceResolver` выбирает только progressive-источник; SoundCloud — PROGRESSIVE (HLS-only трек падает и уходит на YouTube), YouTube — PROGRESSIVE. Сначала строгий матч артиста и названия; для YouTube — нестрогий fallback: запрос без служебных пометок, топ-10, разница длительности ≤15 с, артист в названии или канале. TTL 30 мин для неудачных источников. Резолв свежего URL выполняется в `DownloadTrackWorker` перед каждой попыткой; при 403/410 — один повторный резолв. Запись `.part` → атомарный rename, Room после rename; отмена отменяет OkHttp/NewPipe `Call`; HTTP BASIC-логи только в debug. Кнопка скачивания есть в медиатеке и полноэкранном плеере.
- Плеер: режим текста отполирован в Phase A — спокойный фон (scrim `surface` 0.65 поверх размытой обложки), иерархия строк 24/18/16sp, горизонтальная караоке-заливка, подъём активной строки на 24dp. Таймлайн — волна Phase B на Material3 Slider: серая синусоида с длиной 44dp и сужением к краям, пропетанная часть в `primary`, круглый бегунок на гребне; амплитуда постоянная 9dp, на паузе волна плавно выпрямляется. DSP/Visualizer удалены в сессии 21, `RECORD_AUDIO` в манифесте нет.

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
