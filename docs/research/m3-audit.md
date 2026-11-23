# Аудит UI Facet против Material 3 / M3 Expressive

**Дата:** 24.11.2026 · **Коммит:** `950415d` (`main`) · **Версия:** 1.1.0 (versionCode 6)
**Стек:** Compose BOM 2024.11.00, `androidx.compose.material3:material3:1.4.0-alpha07`, minSdk 26, compileSdk 35.
**Область:** `app/src/main/java/com/facet/music/feature/**`, `core/ui/**`, `navigation/**` — 53 файла, ~19 000 строк.
**Источник правил:** страницы m3.material.io (контент-снапшот сайта от 2026-11-19), прочитанные целиком, включая вкладки Overview / Specs / Guidelines / Accessibility. Ссылки — в каждом пункте.

Аудит только читает код. Ни один файл приложения не изменён; отчёт — единственный созданный файл.

> Снимок рабочего дерева — 24.11.2026, 00:15. В `PlayerLyrics.kt`, `PlayerScreen.kt`, `PlayerHero.kt` и `ImportScreen.kt` в это время шли параллельные правки: номера строк для них сверены на момент снимка, перед работой ориентируйтесь также на имена функций/якоря.

---

## 1. Инвентаризация экранов и компонентов

| Экран / оверлей | Файл (строк) | Ключевые M3-компоненты (строки) |
|---|---|---|
| Home | `feature/home/HomeScreen.kt` (1175) | `PullToRefreshBox`:127, кастомная шапка без app bar, `LazyColumn`:135, `Surface`-карточки:573/1049, `LazyRow`:672/765, `FilledIconButton` 48dp:625 |
| Search | `feature/search/SearchScreen.kt` (1704) | `Scaffold`:220, кастомное поле поиска (`BasicTextField`):937, `FilterChip`:444/463, `SuggestionChip`:976, `AssistChip`:1036, `DropdownMenu`:629, `AddToPlaylistSheet`, `SnackbarHost`:225 |
| Library | `feature/library/LibraryScreen.kt` (848) | `Scaffold`:239, `LargeTopAppBar`:247, `ExtendedFloatingActionButton`:270, `AssistChip`:321, `PlaylistTile`, 5×`AlertDialog` (689–791), `SnackbarHost`:245 |
| Liked tracks | `feature/library/LikedTracksScreen.kt` (334) | `Scaffold`:130, `TopAppBar`:138, `Button` 52dp:178, `FilledTonalIconButton` 52dp:196, `TrackCard` |
| Wave | `feature/wave/WaveScreen.kt` (1024) | `LazyColumn`:143, кастомная градиентная кнопка 76dp:517, `SingleChoiceSegmentedButtonRow`:601, `FilterChip`:878, `AlertDialog`:856/910 |
| Artist | `feature/artist/ArtistScreen.kt` (856) | `LargeTopAppBar`:359 + `exitUntilCollapsedScrollBehavior`, `ModalBottomSheet`:810, кастомный `SnackbarHost`:404 |
| Playlist | `feature/playlist/PlaylistScreen.kt` (360) | `Scaffold`:142, `TopAppBar`:150, `DropdownMenu`:173, `AlertDialog`:259, `SnackbarHost`:148 |
| Import | `feature/imports/ImportScreen.kt` (643) | `Scaffold`:105, `TopAppBar`:107, `TabRow`:127, `OutlinedTextField`:222/478, `FilterChip`:300, `SnackbarHost`:119 |
| Settings | `feature/settings/SettingsScreen.kt` (1330) | кастомный `Box`+`Column`.verticalScroll:180, кастомная шапка:700, `SegmentedButton`:212, `FilterChip`:465, `Switch`:653, `Slider` (RGB):947, `OutlinedTextField`:837/1142, 3×`AlertDialog`, `SnackbarHost`:576 |
| Onboarding | `feature/onboarding/OnboardingScreen.kt` (169) | `Surface`:50, `Column` + insets:55, `Button` |
| Player | `feature/player/PlayerScreen.kt` (940) + `PlayerHero.kt` (783), `PlayerLyrics.kt` (542), `PlayerActions.kt` (296), `PlayerSheets.kt` (254) | `Scaffold`:491, `TopAppBar`:498, `ExpressivePlayerSlider`:52/100, `ModalBottomSheet`:206, `AlertDialog`:154, `PredictiveBackHandler` в `AppNavGraph`:513 |
| Нижний док | `core/ui/components/ExpressiveBottomDock.kt` (58) | `NavigationBar`:24, `NavigationBarItem`:31, 4 destinations (`navigation/Screen.kt`:78), `LocalBottomDockHeight` |
| Mini-player | `core/ui/components/MiniPlayer.kt` (368) | плавающий `Surface` 64dp:130, `clickable` 48dp:265/310, `stateDescription` + `customActions`:174–179 |
| Sheets | `PlayerQueueSheet.kt` (409), `AddToPlaylistSheet.kt` (253), `SleepTimerSheet.kt` (147) | `ModalBottomSheet`:69/75/57, `SwipeToDismissBox`:277 |
| Списки | `core/ui/components/TrackCard.kt` (444), `CollectionComponents.kt` (333) | `ListItem`:153, `Surface`-обёртка, `SectionHeader`, `EmptyState` |

**Частота компонентов (по всему UI):** `Surface` 71, `TextButton` 40, `Button` 32, `IconButton` 23, `FilledTonalButton` 20, `AlertDialog` 19, `FilledTonalIconButton` 16, `SnackbarHost` 15, `OutlinedTextField` 14, `HorizontalDivider` 14, `Scaffold` 11, `FilterChip` 9, `ModalBottomSheet` 8, `TopAppBar` 7, `ListItem` 7, `SegmentedButton` 5, `Slider` 4, `TabRow` 2, `NavigationBar` 1. `Badge/BadgedBox` и `Carousel` не используются.

---

## 2. Отклонения от гайдлайнов

Формат: **[ID] [приоритет]** что не так → где → правило M3 → как исправить в Compose.

### 2.1 Навигация и структура

- **[N1] [P2] Базовая `NavigationBar` вместо flexible.** `ExpressiveBottomDock.kt:24,31`. M3 Expressive прямо говорит: «The baseline navigation bar is no longer recommended… replaced by the flexible nav bar, which is shorter and supports horizontal navigation items in medium windows» (https://m3.material.io/components/navigation-bar/guidelines). Фикс: перейти на `ShortNavigationBar`/`ShortNavigationBarItem` (есть в material3 1.4.0-alpha07, новых зависимостей не нужно), активный лейбл — `secondary`, индикатор — `secondaryContainer`.
- **[N2] [P2] Лейблы дока обрезаются при крупном шрифте.** `ExpressiveBottomDock.kt:43` — `maxLines = 1` + `Ellipsis`; `NavigationBar` фиксированной высоты 80dp. M3 Accessibility: «the navigation bar should grow vertically… It’s okay for scaled text to wrap… ensure the full label is always visible on-screen at up to 2x text sizing» (тот же URL). Фикс: убрать `maxLines = 1` либо `maxLines = 2` без обрезки, `ShortNavigationBar` (сам считает высоту), проверить на fontScale 2.0; высота дока уже измеряется (`AppNavGraph.kt:388`), контент подстроится.
- **[N3] [P2] Нет переключения навигации на medium/expanded.** Док всегда снизу (`AppNavGraph.kt:376–390`), `WindowSizeClass` нигде не используется. M3 Breakpoints: compact <600dp, medium 600–839dp (планшет портрет, **телефон в landscape — expanded 840–1199dp**), для medium/expanded рекомендуется navigation rail (https://m3.material.io/foundations/layout/breakpoints); nav bar допускает horizontal items в medium (nav URL выше). Фикс: `BoxWithConstraints` или `calculateWindowSizeClass()` в `AppNavGraph`; при `width ≥ 600dp` — `NavigationRail` + контент в `Row`, иначе док.
- **[N4] [P3] Baseline `LargeTopAppBar` и отсутствие отделения при скролле.** `LibraryScreen.kt:247–254` (`scrolledContainerColor = background` = фон тела), `ArtistScreen.kt:359`. M3: medium/large baseline «no longer recommended… use medium flexible and large flexible»; «On scroll, apply a fill color to separate from body content» (https://m3.material.io/components/app-bars/overview). Фикс: `LargeFlexibleTopAppBar` + `scrolledContainerColor = surfaceContainer` (или `surfaceContainerLow`), заголовок в `title`/`subtitle`.
- **[N5] [P3] Шапка Settings уезжает вместе с контентом.** `SettingsScreen.kt:180–190` (весь экран — `verticalScroll`), шапка с back-кнопкой — `SettingsScreen.kt:700–733`. M3 app bar «displays labels and page navigation controls at the top of the page» и остаётся на экране; сейчас после скролла исчезает единственная визуальная back-аффорданс. Фикс: `Scaffold` + `TopAppBar`/`LargeFlexibleTopAppBar`, контент — `LazyColumn`/`verticalScroll` ниже.
- **[N6] [P3] Search без search app bar.** `SearchScreen.kt:220` + кастомное поле на `BasicTextField`:937. M3 Expressive ввёл search app bar и search view (app bars URL). Это не нарушение размеров, но паттерн поиска, фокуса и предсказуемого back-поведения приходится поддерживать вручную. Фикс (опционально): `SearchBar`/`DockedSearchBar` либо `TopAppBar` с полем, чтобы получить штатные семантику и инсеты.

### 2.2 Размеры и доступность

- **[A1] [P1] Кружки акцента меньше 48dp и без доступного имени.** `SettingsScreen.kt:763` (44dp), `:992` (36dp), `:1039` (44dp) — `Modifier.clickable` без `minimumInteractiveComponentSize()`; у `RecentColorDot` при невыбранном состоянии вообще нет `contentDescription` и нет `Role.RadioButton`. M3: интерактивная цель ≥48×48dp («Target size must be at least 48x48dp», https://m3.material.io/components/lists/guidelines; «24dp symbols should have a target size of 48dp», https://m3.material.io/styles/icons). Для TalkBack элемент без имени — тупик. Фикс: внешний `Box.size(48.dp)`, внутри визуальный кружок; `Modifier.selectable(selected, role = Role.RadioButton, onClick)` + `contentDescription` с названием/HEX цвета; группа — `Modifier.selectableGroup()`.
- **[A2] [P2] `Switch` и RGB-`Slider` не связаны со своими подписями.** `SettingsScreen.kt:627–656` (текст в `Column`, кликабелен только `Switch`:653), `:947` (подпись «R/G/B» отдельным `Text`). TalkBack объявит «Переключатель, выключен» без названия настройки. M3 Accessibility требует осмысленные labels (https://m3.material.io/foundations/accessible-design/overview). Фикс: `Row.toggleable(value, role = Role.Switch, onValueChange)` + `Switch(checked, onCheckedChange = null)`; слайдеру — `Modifier.semantics { contentDescription = label; stateDescription = "$value из 255" }`.
- **[A3] [P2] Контраст статуса «онлайн» 3.43:1.** `Color.kt:83` `StatusOnlineLight = #1E8E3E`, применяется к тексту `labelMedium` в `SearchScreen.kt:793` (источник — `:842–843`) на `surfaceContainerHigh` (#E8E8E8). M3 Typography: «3:1 for large text, 4.5:1 for small text» (https://m3.material.io/styles/typography). Расчёт: 3.43 (<4.5). Фикс: текст — `onSurface`/`onSurfaceVariant`, зелёным оставить только точку/иконку (для графики достаточно 3:1); либо зелёный `#137333` (4.86:1).
- **[A4] [P2] Плейсхолдер поиска 3.4:1.** `SearchScreen.kt:917` — `onSurfaceVariant.copy(alpha = 0.72f)` на `surface`. Тот же порог 4.5:1 (typography URL). Фикс: убрать alpha (`onSurfaceVariant` даёт 6.4:1) или использовать `onSurface` с alpha ≥0.9.
- **[A5] [P3] Самая дальняя строка текста песни 2.76:1.** `PlayerLyrics.kt:99` `LYRIC_ALPHA = [1f, 0.74f, 0.6f, 0.48f, 0.4f, 0.34f]`, `:413`. Строки 30–34sp — крупный текст, для него порог 3:1 (typography URL); 0.34 даёт ~2.76:1, 0.4 — 3.36:1, 0.48 — 4.3:1. Фикс: нижнюю границу поднять до 0.4 (мин. 3:1) или 0.55 для запаса; проверить на светлой обложке и при fontScale 1.5.
- **[A6] [P2] Фиксированные высоты ломаются при fontScale 2.0.** `LikedTracksScreen.kt:182` (`height(52.dp)`), `SearchScreen.kt:897` (поле 56dp), `WaveScreen.kt:516` (кнопка 76dp), `SettingsScreen.kt:399,435` (`height(48.dp)`). M3: нав-бар и компоненты должны расти под масштаб текста (nav URL), «Avoid changing the type size» (typography URL). Фикс: `heightIn(min = …)` вместо `height`, `maxLines` без жёсткого обрезания, проверить 1.3/1.5/2.0.
- **[A7] [P3] Размеры иконок 16–23dp вместо 24dp.** `SearchScreen.kt:548` (23dp), `:616` (21dp), `:744` (19dp), `:457/683` (16dp), `LibraryScreen.kt:328` (16dp). M3: «Standard icons are displayed as 24dp x 24dp»; 20dp — в первую очередь desktop/dense (icons URL). Для чипов допустимо 18dp (chips specs), для остальных — 24dp (или 20dp только в плотных строках).
- **[A8] [P3] Двухстрочный заголовок в 64dp `TopAppBar`.** `PlayerScreen.kt:503–520` (`labelMedium` + `titleMedium` в `Column` без subtitle-слота). На fontScale 2.0 не помещается. Фикс: `TopAppBar` с одной строкой названия трека и отдельной строкой артиста под app bar, либо `MediumFlexibleTopAppBar` с `subtitle`.
- **[A9] [P3] Дублирующиеся действия в карточках.** `HomeScreen.kt:562–638`: вся карточка `clickable` + внутренняя `FilledIconButton` с тем же `onClick`; TalkBack получит два одинаковых действия. Фикс: оставить одно действие (кнопку) либо `mergeDescendants = true` и убрать inner clickable.

### 2.3 Типографика

- **[T1] [P2] Произвольные `sp` вместо ролей.** `HomeScreen.kt:905–918` (`titleSmall.copy(13.sp)`, `bodySmall.copy(11.sp)`), `MiniPlayer.kt:250–252` (11sp), `PlayerLyrics.kt:436–445` (34sp/30sp), `WaveScreen.kt:580` (`letterSpacing = 0.2.sp`). M3: «Adjust properties like line height and letter spacing… Avoid changing the type size; this can affect how components render and reflow» (typography URL). Фикс: `titleSmall`/`labelMedium`/`bodyLarge` без `copy(fontSize=…)`; для «микро»-подписей — `labelSmall` (11sp) как роль, а не копия.
- **[T2] [P3] Не используются emphasized-стили.** `Type.kt:26–132` задаёт только baseline-роли; M3 Expressive: 15 baseline + 15 emphasized, «use emphasized styles for selection, actions, headlines» (typography URL). В material3 1.4.0-alpha07 доступны `typography.titleLargeEmphasized` и т.п. Фикс: заголовки секций/выбранные лейблы/hero — `*Emphasized`.
- **[T3] [P3] Брендовый шрифт применён ко всем ролям.** `Type.kt:22–132` — Google Sans Flex на display/headline/title/body/label. M3 рекомендует брендовый шрифт прежде всего для display/headline, body/label — для читаемости (typography URL). Не нарушение, но для body/label стоит оставить Roboto/Roboto Flex.
- **[T4] [P3] Ручной `letterSpacing` поверх роли.** `WaveScreen.kt:580`, `MiniPlayer.kt:252`. Фикс: удалить, использовать токен роли.

### 2.4 Цвет

- **[C1] [P2] Незамкнутая пара ролей на градиенте.** `WaveScreen.kt:484–485` — `Brush.linearGradient(primary, tertiary)`, текст/иконка — `onPrimary`: `onPrimary` не является парой для `tertiary`, при динамической палитре и кастомном акценте конец градиента может дать <4.5:1. M3: «apply colors only in the intended pairs… Combining colors improperly may break contrast» (https://m3.material.io/styles/color/roles). Фикс: градиент `primaryContainer → tertiaryContainer` + `onPrimaryContainer`/`onTertiaryContainer` (взять более контрастный), либо затемняющий слой под текстом.
- **[C2] [P3] Смысловые цвета через alpha вместо роли.** `CollectionComponents.kt:198` (`surface.copy(alpha=0.55f)`), `:222` (`content.copy(alpha=0.78f)`), `PlayerHero.kt:627,727` (`onSurface.copy(alpha=0.65f)`). Alpha-текст непредсказуем на разных поверхностях и в dynamic color. Фикс: `onSurfaceVariant`/`secondary` как роль или `contrastContentColor()` из `core/ui/theme/Contrast.kt`.
- **[C3] [P3] Белая иконка на произвольном цвете акцента.** `SettingsScreen.kt:797,1017,1068` `tint = Color.White`; контраст на `#F5A524` — 2.04:1, на `#43A047` — 3.30:1, на `#8B5CF6` — 4.23:1. Для графики нужно 3:1. Фикс: `contrastContentColor(dotColor)` (утилита уже есть) вместо `Color.White`.
- **[C4] [P3] Брендовые цвета вне темы.** `SearchScreen.kt:148–150` (`YoutubeRed`, `SoundCloudOrange`, `SpotifyGreen`). Это осознанное исключение для брендов, но значения стоит держать в `core/ui/theme` и проверять контраст при использовании как tint/фона.
- **[C5] Правильно:** dynamic color + кастомный акцент + ахроматический fallback (`Theme.kt:27–48,89–108`), ручные нейтралы для light/dark, `surfaceTint = Transparent`, `surfaceContainer*` вместо теней, `values-night` windowBackground.

### 2.5 Компоненты

- **[K1] [P1] Snackbar оказывается под доком и мини-плеером.** `SearchScreen.kt:225`, `LikedTracksScreen.kt:136`, `ImportScreen.kt:119`, `PlaylistScreen.kt:148`, `LibraryScreen.kt:245`, `PlayerScreen.kt:495` — обычный `Scaffold.snackbarHost` без нижнего отступа; док и мини-плеер рисуются **после** `NavHost` (`AppNavGraph.kt:376–412`) и перекрывают его. M3: snackbar можно «nudge upwards to avoid overlapping… docked toolbars» и нельзя ставить «in front of… navigation» (https://m3.material.io/components/snackbar/guidelines). Фикс: общий `DockAwareSnackbarHost` с `Modifier.padding(bottom = maxOf(LocalBottomDockHeight.current, navigationBarsInset) + 8.dp)` (или передавать `bottomInset` в экраны); не добавлять глобальный `Scaffold.bottomBar`, чтобы не сломать оверлейный док.
- **[K2] [P2] Шторка очереди открывается сразу на 78%.** `PlayerQueueSheet.kt:59–60` — `screenHeight * 0.78` + `skipPartiallyExpanded = true`. M3: «the initial vertical position of modal bottom sheets is capped at 50% of the screen height», дальше можно тянуть на весь экран (https://m3.material.io/components/bottom-sheets/guidelines). Фикс: `skipPartiallyExpanded = false`, частичное положение 50%, полное — по drag; высоту контента не фиксировать.
- **[K3] [P3] Радиус шторок 24dp вместо 28dp.** `PlayerQueueSheet.kt:72`, `SleepTimerSheet.kt:60`. M3: «28dp top corner radius» (sheets URL). Фикс: убрать кастомный `shape` или `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`.
- **[K4] [P3] Drag handle ниже 48dp.** `PlayerQueueSheet.kt:74–83` — визуально 32×4dp + 12dp вертикальных отступов = 28dp. M3: drag handle с «accessible 48dp hit target», верхние 48dp интерактивны (sheets URL). Фикс: дефолтный `dragHandle` Compose (он даёт нужную зону) или `Box.heightIn(min = 48.dp)` + semantics «Изменить высоту».
- **[K5] [P3] Чипсы источников скроллятся горизонтально.** `SearchScreen.kt:439` — `horizontalScroll` для `FilterChip`. M3 Chips: при переполнении «use a filter chip as a leading element to reflow… avoid scrolling»; 8dp между чипсами (https://m3.material.io/components/chips/guidelines). Фикс: `FlowRow` + чип «Все»/«Ещё» или меню; при 3–4 источниках чаще всего помещается без скролла.
- **[K6] [P3] Baseline `TabRow` в Import.** `ImportScreen.kt:127`. M3: «Two variants: primary and secondary… Primary tabs are placed at the top of the content pane under an app bar»; primary-индикатор — 3dp (https://m3.material.io/components/tabs/guidelines). `TabRow` даёт baseline-индикатор 2dp и не тянет primary-токены. Фикс: `PrimaryTabRow`.
- **[K7] [P3] Список превращён в карточки.** `TrackCard.kt:136–154` — каждая строка `ListItem` внутри `Surface` с `surfaceContainerLow` и 4dp зазорами. M3 Lists — непрерывная группа с 48dp целью (lists URL). Гибрид «list-item-as-card» допустим, но тогда стоит унифицировать: либо плоский список с `HorizontalDivider`, либо настоящие карточки с большим паддингом; сейчас на разных экранах и то, и другое.
- **[K8] [P3] 19 `AlertDialog` — перегруз диалогами.** `LibraryScreen.kt:689,709,742,775,791`, `SettingsScreen.kt:663,819,1237`, `WaveScreen.kt:856,910`, `PlayerSheets.kt:154` и др. M3: «Dialogs are purposefully interruptive… should be used sparingly» (https://m3.material.io/components/dialogs/guidelines). Не нарушение по отдельности, но часть сценариев (создание плейлиста, выбор частоты) уместнее в bottom sheet/menu.
- **[K9] [P3] Вложенные кликабельные `RadioButton` в Variations.** `PlayerSheets.kt:214–222` — `Row.clickable` + `RadioButton(onClick=…)`: два фокуса на одну опцию. Фикс: `Row.selectable(role = Role.RadioButton)` + `RadioButton(onClick = null)`.
- **[K10] [P3] Кастомный статус-попап как `DropdownMenu`.** `SearchScreen.kt:629–645` — ширина 304dp, `shape = extraLarge` (28dp), `shadowElevation = 12.dp`. M3 menu использует surface-container и меньшую форму/тень; для панели статусов лучше `ModalBottomSheet` или `Popup` с явной ролью. Фикс: дефолтные `MenuDefaults`/`surfaceContainer`, либо sheet.
- **[K11] Правильно:** FAB не перекрывает контент (`LibraryScreen.kt:281,292` — отступ `LocalBottomDockHeight + 12`, `contentPadding` 96dp), `AddToPlaylistSheet`/`SleepTimerSheet` используют scrim и модальную семантику, чипсы Compose сами дают 48dp target.

### 2.6 Motion

- **[M1] [P2] 54 `tween` против 24 `spring`; Expressive требует motion physics.** `grep`: 54 `tween(`, 24 `spring(` в `feature/`+`core/ui/`. M3 Expressive: «Products should migrate to the new system» (physics) и «ensure your custom components adopt… the motion specs available through `MaterialTheme.motionScheme`» (https://m3.material.io/styles/motion/easing-and-duration, https://m3.material.io/m3-expressive-motion-theming). Фикс: в кастомных компонентах (`RevealBlock`, скелетоны, chips, sheet-контент) использовать `Motion.spatialDefaultSpec()`/`effectsDefaultSpec()` (уже есть в `Motion.kt:60–70`); `tween` оставить для кроссфейдов, где overshoot мешает.
- **[M2] [P2] Длительности вне пар M3 (420/340/220/160/900/1200/7000 мс).** Примеры: `WaveScreen.kt:134` (420), `SearchScreen.kt` (140–240), `PlayerScreen.kt`/`AppNavGraph.kt:447` (450/200). M3 пары: Emphasized 500 — begin/end, 400 — enter, 200 — exit; Standard 300/250/200 (motion URL). Фикс: свести в `Motion` (`EnterMillis = 400`, `ExitMillis = 200`, `EmphasizedMillis = 500`) и использовать только их; 7000/1200/900 проверить — это, вероятно, служебные задержки, вынести в именованные константы.
- **[M3] [P3] `Motion.Emphasized` совпадает с `Motion.Standard`.** `Motion.kt:16–17` обе кривые `(0.2, 0, 0, 1)`; в таблице M3 Emphasized на Android задаётся отдельным `PathInterpolator`, а не Standard-кривой (motion URL). Практический эффект: «begin and end on screen» переходы ощущаются как стандартные. Фикс: либо использовать `MotionScheme`-спеки, либо задокументировать осознанную аппроксимацию.
- **[M4] [P3] Морф формы не используется.** M3 Expressive даёт shape library и shape-morph (https://m3.material.io/building-with-m3-expressive); `MaterialShapes` доступен в 1.4.0-alpha07. Shared element обложки mini→player уже есть (`AppNavGraph.kt:410–430`) — это хороший hero moment; морф — опция, не требование.
- **[M5] [P3] Слишком много «hero moments».** Бесконечное свечение кнопки волны (`WaveScreen.kt:490–516`), пульсирующий эквалайзер в карточках, `RevealBlock` в Home (`HomeScreen.kt:256–287`), градиентные кнопки, сквирклы плеера. M3 Expressive: «Stick to one or two hero moments in your product; too many moments can be overwhelming and distracting» (https://m3.material.io/building-with-m3-expressive). Фикс: оставить 1–2 (обложка mini→player и «Новая волна»), остальное — статичнее/короче.

### 2.7 Adaptive

- **[AD1] [P2] Приложение фактически phone-only.** Единственные обращения к конфигурации — локаль и высота шторки: `HomeScreen.kt:291`, `PlayerQueueSheet.kt:58`. Нет `WindowSizeClass`, rail, двухпанельных layouts, ограничения ширины контента. M3: продукты «should automatically adapt to any breakpoint»; phone landscape = expanded (breakpoints URL). Фикс: breakpoint в `AppNavGraph`; на ≥600dp — rail + max-width контента 840dp; на ≥840dp — две панели (список + плеер) хотя бы для Library/Artist.
- **[AD2] [P2] Плеер в landscape требует скролла до управления.** `PlayerHero.kt:606–615` — `fillMaxWidth(style.widthFraction)` + `aspectRatio(1f)` внутри `verticalScroll`; при 840×400 обложка ~720dp высотой, кнопки ниже. Фикс: `HeroStyle.widthFraction` от breakpoint (в landscape ≤0.42), либо side-by-side: обложка слева, контролы справа.
- **[AD3] [P3] Контент растягивается на всю ширину.** `HomeScreen.kt`/`LibraryScreen.kt` — `fillMaxWidth()` колонки с 16dp полями на любой ширине; на планшете строки и карточки чрезмерно широкие (M3: line length 40–60 символов, typography URL). Фикс: `widthIn(max = 840.dp)` + `Alignment.CenterHorizontally`.

### 2.8 Edge-to-edge и инсеты

- **[E1] [P2] Нет обработки IME.** `MainActivity.kt:71` `enableEdgeToEdge()`, `AndroidManifest.xml:28` `adjustResize`; `imePadding()`/`WindowInsets.ime` не используются нигде. При открытой клавиатуре `LazyColumn` поиска сохраняет полную высоту, нижние результаты и поля в диалогах уходят под IME. M3/Android: при edge-to-edge IME-инсеты нужно применять явно. Фикс: `contentWindowInsets = WindowInsets.statusBars` + `Modifier.imePadding()` на контейнере Search и диалогов, либо `WindowInsets.ime` в `contentWindowInsets`; для полей — `BringIntoViewRequester`.
- **[E2] [P3] `contentWindowInsets = WindowInsets.statusBars` вместо `systemBars`.** `SearchScreen.kt:225`, `LibraryScreen.kt:244`, `LikedTracksScreen.kt:135`, `PlaylistScreen.kt:147`: нижний nav-инсет компенсируется только высотой дока; на экранах без дока (Playlist — док скрыт) остаётся `navigationBarsPadding()` вручную (`PlaylistScreen.kt:202`), что легко забыть. Фикс: оставить `statusBars` + явный `navigationBarsPadding()` на контенте, либо `systemBars` и вычитать док.
- **[E3] Правильно:** `enableEdgeToEdge`, per-screen `statusBarsPadding`/`systemBarsPadding` (`HomeScreen.kt:133`, `SettingsScreen.kt:187`, `OnboardingScreen.kt:55`), `navigationBarsPadding` на списках (`LikedTracksScreen.kt:163`, `ArtistScreen.kt:158`), dock сам добавляет нижний инсет, mini-player учитывает `navigationBarInset` (`AppNavGraph.kt:211–219`).

---

## 3. Что уже сделано правильно (не сломать при рефакторинге)

1. **Dynamic color + кастомный акцент.** `Theme.kt:89–108`: приоритет custom → dynamic (Android 12+) → manual accent → fallback; ахроматический акцент даёт Monochrome-палитру (`ACHROMATIC_CHROMA_THRESHOLD`).
2. **Тёмная тема и нейтралы.** `withAiryNeutrals` (`Theme.kt:38–87`), `values-night/themes.xml`, `surfaceTint = Transparent` — поверхности не «зеленеют» от elevation.
3. **Типографика на токенах.** `Type.kt` повторяет baseline M3 размеры/lineHeight/letterSpacing; Google Sans Flex — variable font, что Expressive рекомендует.
4. **Правильная база навигации.** 4 destinations (`Screen.kt:78`), `alwaysShowLabel`, filled/outlined иконки, `surfaceContainer`, без тени (`ExpressiveBottomDock.kt:24–56`).
5. **MotionScheme.expressive()** в теме (`Theme.kt:112`) и корректные spring-константы spatial/effects (`Motion.kt:24–38`) — совпадают с expressive-токами.
6. **Hero-переход обложки** mini-player → player через `SharedTransitionLayout`/`sharedElement` (`AppNavGraph.kt:393–433`) и **predictive back** со свайпом плеера (`AppNavGraph.kt:540+`).
7. **Artist app bar**: `LargeTopAppBar` + `exitUntilCollapsedScrollBehavior` + `nestedScroll` (`ArtistScreen.kt:359`), прозрачная шапка с баннером.
8. **Семантика mini-player**: `stateDescription` «играет/пауза/загрузка» + custom actions next/previous (`MiniPlayer.kt:174–179`), цели 48dp (`:265`, `:310`).
9. **Контент не уходит под док**: `LocalBottomDockHeight` в `contentPadding` (`HomeScreen.kt:142`, `SearchScreen.kt:1273`, `WaveScreen.kt:147`, `LibraryScreen.kt:291`, `LikedTracksScreen.kt:164`).
10. **FAB не перекрывает список** (`LibraryScreen.kt:281,292`), кнопки/чипсы — настоящие M3-компоненты, поэтому 48dp minimumInteractiveComponentSize уже работает.
11. **Контраст-утилиты** `wcagContrast`/`contrastContentColor`/`bestContentColor` (`Contrast.kt`) — готовый инструмент для точечных фиксов, не нужно тащить новые библиотеки.
12. **Локализация и строки** — весь текст через `stringResource`, включая content descriptions; это упрощает аудит доступности.

---

## 4. Приоритеты и порядок работ

| Приоритет | Кол-во | Что входит | Экраны/файлы first |
|---|---|---|---|
| **P1** | 2 | K1 snackbar под доком; A1 недоступные кружки акцента | `AppNavGraph`, все Scaffold-экраны; `SettingsScreen` |
| **P2** | 15 | N1–N3, A2–A4, A6, T1, C1, K2, M1–M2, AD1–AD2, E1 | Док, Search, Player, Settings, Library/Artist |
| **P3** | 26 | N4–N6, A5, A7–A9, T2–T4, C2–C4, K3–K10, M3–M5, AD3, E2 | косметика и консистентность |

**Волна 1 — «ничего не теряется» (P1 + дешёвые P2), 1–2 дня.**
Общий dock-aware `SnackbarHost`; IME-инсеты в Search/диалогах; семантика `Switch`/`Slider`; 48dp и `Role.RadioButton` для кружков; контраст статуса/подсказки/текста песни. Файлы: `AppNavGraph.kt`, `SearchScreen.kt`, `SettingsScreen.kt`, `PlayerLyrics.kt`, `Color.kt`, `Contrast.kt`.

**Волна 2 — навигация и шторки, 2–3 дня.**
`ShortNavigationBar` + перенос лейблов на fontScale 2.0; breakpoint и rail для ≥600dp; `LargeFlexibleTopAppBar` в Library/Artist; `Scaffold`+app bar в Settings; очередь — 50% + 28dp + дефолтный handle; чипсы — `FlowRow`; `PrimaryTabRow`. Файлы: `ExpressiveBottomDock.kt`, `AppNavGraph.kt`, `LibraryScreen.kt`, `ArtistScreen.kt`, `SettingsScreen.kt`, `PlayerQueueSheet.kt`, `SleepTimerSheet.kt`, `SearchScreen.kt`, `ImportScreen.kt`.

**Волна 3 — токены, motion, adaptive, 3–5 дней.**
Убрать raw `sp` и alpha-текст; emphasized-стили; motion через `motionScheme`; landscape-плеер; max-width контента; опционально shape-morph. Файлы: `Type.kt`, `Type`-потребители, `PlayerHero.kt`, `HomeScreen.kt`, `Theme`/`Motion`.

**Порядок экранов:** Player → Search → Library/Artist → Settings → Home/Wave → Import/Playlist/Onboarding. Причина: Player и Search — ежедневные сценарии с наибольшим числом P2; Settings — единственный P1 по доступности; Home/Wave в основном косметика.

---

## 5. Нужен ли полный рефакторинг?

**Нет.** Архитектура UI здоровая: M3-компоненты используются по назначению, тема на токенах, dynamic color, Expressive motion scheme, shared element и predictive back уже есть. Проблемы локальные: оверлейный док (snackbar/insets), несколько кастомных кликабельных элементов, контраст и «сырые» размеры, отсутствие breakpoints. Это **точечный рефакторинг 15–20 файлов**, а не переписывание.

Ограничения по весу соблюдаются: все рекомендуемые API (`ShortNavigationBar`, `LargeFlexibleTopAppBar`, `MaterialShapes`, `WindowSizeClass` через `material3-window-size-class`) доступны в текущем material3 1.4.0-alpha07 или добавляются крошечным artifact'ом; новые тяжёлые зависимости не нужны, APK останется в пределах 40 МБ (текущий ~21.8 МБ). Нельзя тащить `material3-adaptive` целиком ради пары брейкпоинтов — достаточно `BoxWithConstraints` или `calculateWindowSizeClass()`.

Риски рефакторинга: не заменять оверлейный док на `Scaffold.bottomBar` (сломает `LocalBottomDockHeight`, shared element и мини-плеер); не менять `PlayerCoverSharedKey`; не трогать `MotionScheme.expressive()` и spring-константы; сохранять per-screen `background` (корневого `Scaffold` нет — иначе в переходах вкладок проглядывает окно).

---

## 6. Топ-10 правок

1. **Dock-aware snackbar.** Добавить общий `SnackbarHost` с `bottom = maxOf(LocalBottomDockHeight.current, navigationBarsInset) + 8.dp` и подключить в Search, LikedTracks, Import, Playlist, Library, Player. Проверка: сообщение «добавлено в плейлист» видно при играющем мини-плеере.
2. **Кружки акцента: 48dp + имя + роль.** `SettingsScreen.kt:763,992,1039`: внешний `Box.size(48.dp)`, `selectable(role = Role.RadioButton)`, `contentDescription` с HEX/названием; `selectableGroup()` на ряду. TalkBack должен называть цвет.
3. **Связка Switch/Slider с подписями.** `SettingsSwitchRow`: `Row.toggleable(role = Role.Switch)`, `Switch(onCheckedChange = null)`; RGB-слайдеру — `contentDescription = "R/G/B"` и `stateDescription`.
4. **Контраст:** `onSurfaceVariant` без alpha в поиске (917); текст статуса — `onSurface`, зелёный только точка; lyric idle alpha ≥0.4 (лучше 0.55); `contrastContentColor()` для белых иконок на кружках.
5. **Навигация под fontScale и Expressive:** `ShortNavigationBar`, лейблы без `maxLines = 1`, проверить 1.0/1.3/1.5/2.0; активный лейбл `secondary`.
6. **IME-инсеты:** `Modifier.imePadding()` в Search и диалогах, `BringIntoViewRequester` для полей; тест «клавиатура + последний результат».
7. **Шторка очереди:** `skipPartiallyExpanded = false`, частичное 50%, `shape` 28dp, дефолтный drag handle (48dp); max-width 640dp на широких экранах.
8. **Чипсы источников:** заменить `horizontalScroll` (`SearchScreen.kt:439`) на `FlowRow` с чипсом «Все», сохранив 8dp между чипсами.
9. **Убрать сырые `sp`:** Home 905/916, MiniPlayer 250, PlayerLyrics 400, Settings 558 → роли `titleSmall`/`labelSmall`/`bodyLarge`; добавить `*Emphasized` для заголовков и выбранных состояний.
10. **Motion и breakpoints:** кастомные анимации — через `MaterialTheme.motionScheme`/`Motion.*Spec()`, enter 400 / exit 200; `WindowSizeClass` в `AppNavGraph`: rail на ≥600dp, landscape-плеер с `widthFraction ≤0.42` или side-by-side.

---

## Приложение: использованные страницы M3

- Навигация: https://m3.material.io/components/navigation-bar/guidelines
- App bars: https://m3.material.io/components/app-bars/overview
- Bottom sheets: https://m3.material.io/components/bottom-sheets/guidelines
- Chips: https://m3.material.io/components/chips/guidelines
- Snackbar: https://m3.material.io/components/snackbar/guidelines
- Dialogs: https://m3.material.io/components/dialogs/guidelines
- Lists: https://m3.material.io/components/lists/guidelines
- FAB: https://m3.material.io/components/floating-action-button/guidelines
- Tabs: https://m3.material.io/components/tabs/guidelines
- Типографика: https://m3.material.io/styles/typography
- Цветовые роли: https://m3.material.io/styles/color/roles
- Иконки: https://m3.material.io/styles/icons
- Motion: https://m3.material.io/styles/motion/easing-and-duration
- Expressive motion: https://m3.material.io/m3-expressive-motion-theming
- Expressive: https://m3.material.io/building-with-m3-expressive
- Breakpoints: https://m3.material.io/foundations/layout/breakpoints
- Доступность: https://m3.material.io/foundations/accessible-design/overview
- Interaction states: https://m3.material.io/foundations/interaction/states
