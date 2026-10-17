# MyMusic

Кастомный музыкальный агрегатор в стиле Material 3 для Android.  
Воспроизводит треки с разных стриминговых платформ в одном приложении.

> **Распространение:** только APK (GitHub Releases / Telegram). Не для Google Play.

---

## Требования

- VS Code или любой редактор; тестирование на реальном устройстве через adb
- **JDK 17** (задаётся через `JAVA_HOME` или Android Studio)
- **Android SDK 35** (устанавливается автоматически при сборке)
- Устройство или эмулятор **Android 8.0+ (API 26+)**

---

## Сборка

```powershell
# Debug APK
.\gradlew.bat assembleDebug

# Release APK (требует настройки подписи)
.\gradlew.bat assembleRelease
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
app/src/main/java/com/my/music/
├── core/            # Сеть, DI, UI-компоненты, тема
├── data/            # Модели, репозитории, источники данных
├── feature/         # Экраны: home, search, player, library, wave
├── navigation/      # NavGraph, Screen
├── MainActivity.kt
└── MyMusicApp.kt
```

Подробнее — см. [AGENTS.md](AGENTS.md) и [PROGRESS.md](PROGRESS.md).

---

## Планируемые источники

| Источник | Статус |
|----------|--------|
| SoundCloud | ✅ Поиск работает |
| LRCLIB (тексты) | 🔜 В разработке |
| Spotify | 📋 v2 |
| Яндекс Музыка | 📋 v2 |
| ВК Музыка | 📋 v2 |

---

## Технологии

Kotlin 2.0.21 · Jetpack Compose · Material 3 · Hilt · Media3 · Room · NewPipe Extractor · Coil 3
