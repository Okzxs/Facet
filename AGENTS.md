# AGENTS.md — Руководство для AI-агентов

## Инструкция для начала каждой сессии
> Прочитай этот файл и `PROGRESS.md`, назови последнюю выполненную задачу и предложи следующую.

---

## Описание проекта

**MyMusic** — кастомный музыкальный агрегатор в стиле Material 3.  
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
com.my.music
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
└── MyMusicApp.kt        # @HiltAndroidApp, NewPipe.init()
```

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

- `ThemeMode` (LIGHT/DARK/SYSTEM, по умолчанию SYSTEM) и `customAccentColor` (ARGB или null) живут в `AppSettings` (SharedPreferences `mymusic_settings`, ключи `theme_mode` и `custom_accent_color`).
- Приоритет цвета в `MyMusicTheme`: собственный акцент (работает на **всех** версиях Android, перекрывает Dynamic Color) → Dynamic Color (Android 12+) → ручная палитра кружочков (только ниже 12) → fallback-палитра. Кружки палитры и RGB-пикер пишут в `customAccentColor`, поэтому работают и поверх Dynamic Color; `accentColorArgb` — legacy-фолбэк для Android ниже 12.
- `MainActivity` читает `SettingsRepository.settings` и передаёт в `MyMusicTheme` параметры `themeMode`, `accentColor`, `customAccentColor`.
- Палитра плеера при заданном `customAccentColor` смешивается с ним на 40% (`blendPaletteWithAccent` в PlayerScreen), чтобы цвета обложки не конфликтовали с темой. Контрастные роли (`onPrimary`, `on*Container`) после смешивания пересчитываются от светлоты, а не лерпаются.
- Экраны-вкладки сами ставят себе фон (`Modifier.background(MaterialTheme.colorScheme.background)`) и инсеты: корневого Scaffold в `AppNavGraph` больше нет (док — оверлей, высота через `LocalBottomDockHeight`). Корневой Box навграфа тоже непрозрачный — иначе в переходах вкладок проглядывает окно.
- `MyMusicTheme` провайдит `LocalContentColor = colorScheme.onBackground`: MaterialTheme его не ставит, дефолт — `Color.Black`, и после удаления корневого Scaffold любой Text без явного цвета чернел на тёмном фоне. Scaffold плеера с `containerColor = Transparent` обязан задавать `contentColor` явно (`contentColorFor(Transparent)` = Unspecified).
- `windowBackground` задан в `values/themes.xml` и `values-night/themes.xml` (#FFF8F5 / #171210, совпадает с Color.kt) — без ночного варианта окно оставалось белым и давало вспышку на холодном старте.

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
