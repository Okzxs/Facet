# Facet

Кастомный музыкальный агрегатор в стиле Material 3 для Android.  
Воспроизводит треки с разных стриминговых платформ в одном приложении.

> **Распространение:** только APK (GitHub Releases / Telegram). Не для Google Play.

---

## Требования

- VS Code или любой редактор; тестирование на реальном устройстве через adb
- **JDK 17** (задаётся через `JAVA_HOME`)
- **Android SDK 35** (устанавливается автоматически при сборке)
- Реальное устройство **Android 8.0+ (API 26+)**, установка через adb

---

## Сборка

```powershell
# Debug APK
.\gradlew.bat assembleDebug

# Release APK (подписанный, с R8)
.\gradlew.bat assembleRelease
```

### Релизная подпись

Для подписи нужны `keystore.jks` и `keystore.properties` в корне проекта. Оба файла в `.gitignore` — в репозиторий они не попадают, сделайте резервную копию: без ключа обновления установленного приложения невозможны.

```properties
# keystore.properties
storeFile=keystore.jks
storePassword=…
keyAlias=facet
keyPassword=…
```

Если файла нет, `assembleRelease` соберёт неподписанный APK, но не упадёт. Правила R8 — в `app/proguard-rules.pro`.

### Ключ Last.fm для «Моей волны»

Похожие артисты подтягиваются через Last.fm. Ключ не хранится в репозитории; без него волна продолжит работать на лайках, истории и чарте.

```powershell
# Вариант 1: переменная окружения
$env:LASTFM_API_KEY = "ваш_ключ"
.\gradlew.bat assembleDebug

# Вариант 2: в %USERPROFILE%\.gradle\gradle.properties
# LASTFM_API_KEY=ваш_ключ
```

### Путь к APK после сборки
```
app\build\outputs\apk\debug\app-debug.apk
```

### Установка на устройство через ADB
```powershell
adb install app\build\outputs\apk\debug\app-debug.apk
```

---

## Структура проекта

```
app/src/main/java/com/facet/music/
├── core/            # Сеть, DI, UI-компоненты, тема
├── data/            # Модели, репозитории, источники данных
├── feature/         # Экраны: home, search, player, library, wave
├── navigation/      # NavGraph, Screen
├── MainActivity.kt
└── FacetApp.kt
```

Подробнее — см. [AGENTS.md](AGENTS.md) и [PROGRESS.md](PROGRESS.md).

---

## Источники

| Источник | Статус |
|----------|--------|
| SoundCloud | ✅ Поиск и воспроизведение |
| LRCLIB (тексты) | ✅ Синхронные тексты |
| Last.fm (похожие артисты) | ✅ «Моя волна» |
| Spotify | 📋 v2 |
| Яндекс Музыка | 📋 v2 |
| ВК Музыка | 📋 v2 |

> Spotify-вход (OAuth PKCE) требует Redirect URI `facet://spotify-auth` в настройках приложения на [Spotify Dashboard](https://developer.spotify.com/dashboard).

---

## Технологии

Kotlin 2.0.21 · Jetpack Compose · Material 3 · Hilt · Media3 · Room · NewPipe Extractor · Last.fm · Coil 3

---

## Лицензия

Проект распространяется по [GNU GPL v3.0](LICENSE): этого требует лицензия NewPipe Extractor, через который работает SoundCloud. Исходники открыты, форки обязаны оставаться под GPLv3.
