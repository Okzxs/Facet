# Журнал реверс-инжиниринга Яндекс Музыки

Документ фиксирует ход статического анализа APK. Технические выводы — в `docs/yandex-rotor-reverse.md`.

---

## 21.10.2026

### 1. Постановка задачи

- Извлечь логику Rotor / «Моей волны» из официального Android APK.
- Не обходить SSL Pinning и не перехватывать трафик.
- Не менять код приложения до завершения анализа.
- Результат оформить в `docs/yandex-rotor-reverse.md`.

### 2. Инструменты

- REA skill: `C:\Users\souler\.agents\skills\reverse-engineer-anything\SKILL.md`
- REA CLI: `npx -y rea-agents@6.7.0`
- Android-провайдер REA: `C:\Users\souler\.local\share\rea\android\jadx-headless-mcp-0.7.1-all.jar`
- SHA-256 JAR: `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`
- JADX CLI 1.5.6: `D:\MyMusic\reverse\yandex\jadx\bin\jadx.bat`
- JDK 17: `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
- `apktool` установить не удалось: `choco install apktool` упал из-за non-elevated shell и lock-файла `jre8`. Для анализа хватает JADX.

Переменные окружения для REA:

```powershell
$env:REA_JADX_MCP_JAR="$HOME\.local\share\rea\android\jadx-headless-mcp-0.7.1-all.jar"
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
```

### 3. Получение APK

APKPure, APKMirror и Uptodown заблокировали скачивание (403 / Cloudflare / Turnstile). Использован RuStore API:

```text
GET https://backapi.rustore.ru/applicationData/overallInfo/ru.yandex.music
Header: ruStoreVerCode: 110802

POST https://backapi.rustore.ru/applicationData/download-link
Body: {"appId":303463871,"firstInstall":true}
```

Скачан zip `D:\MyMusic\reverse\yandex\rustore-yandex-music.zip`, внутри — APK. APK переименован в `yandex-music-2026.09.5.apk`.

Проверка:

- версия: `2026.09.5 #167.2rur`
- versionCode: `24026503`
- minSdk: 24
- targetSdk: 36
- размер: `46 804 627` байт
- SHA-256: `840b1653df3c329ca70f2315e4cb1048af3e369f73ba5ee23052820e3e1fdfe5`

### 4. Распаковка и инвентаризация

- APK распакован в `D:\MyMusic\reverse\yandex\unpacked`.
- 6 dex-файлов: `classes.dex` … `classes6.dex`.
- `inspect-android-package` показал `50 743` класса.
- Сохранён отчёт `D:\MyMusic\reverse\yandex\rea-inspect.json`.

### 5. Поиск классов

Выполнены REA-поиски по `Rotor`, `Wave`, `Feedback`, `Session`, `Station`.

Найдены ключевые классы:

- `com.yandex.music.shared.wave.data.network.TrackRotorApi`
- `com.yandex.music.shared.radio.recommendation.RotorApi`
- `com.yandex.music.shared.wave.data.network.request.SessionTracksRequestDto`
- `SessionNewRequestDto`, `SessionCloneRequestDto`
- `SessionFeedbackRequestDto`, `SessionFeedbacksRequestDto`
- `FeedbackRequestDto`, `FeedbackEventDto`
- `SessionTracksResponseDto`, `SessionNewResponseDto`, `SequenceItemDto`
- `WaveDto`, `WaveLastDto`, `SessionInfoResponseDto`

Поиски `WaveFeedback`, `WaveRepository`, `FeedbackReporter` упали с `provider_timeout` (120 000 мс) и будут повторены.

### 6. Декомпиляция

Через JADX `--single-class` декомпилированы и сохранены в `D:\MyMusic\reverse\yandex\single`:

- `TrackRotorApi.java`
- `RotorApi.java`
- `SessionNewRequestDto.java`
- `SessionCloneRequestDto.java`
- `SessionTracksRequestDto.java`
- `SessionFeedbackRequestDto.java`
- `SessionFeedbacksRequestDto.java`
- `FeedbackRequestDto.java`
- `FeedbackEventDto.java`
- `SessionNewResponseDto.java`
- `SessionTracksResponseDto.java`
- `SequenceItemDto.java`
- `WaveDto.java`
- `su7.java`, `uu7.java`, `imd.java`

Из `TrackRotorApi` и `RotorApi` извлечены точные пути:

- `rotor/session/{radioSessionId}/tracks`
- `rotor/sessions/feedbacks`
- `rotor/session/new`
- `rotor/session/{sessionId}/clone`
- `rotor/wave/last`
- `rotor/session/{sessionId}?includeWaveModel=true`
- `rotor/wave/last/reset`
- `rotor/station/{stationId}/info`
- `rotor/wave/settings`

### 7. Строки dex

Скриптом извлечены печатные строки из всех dex в `D:\MyMusic\reverse\yandex\dex-strings.txt` (`8 020 677` байт).

Найдены:

- `session_started` — строка 91260
- `track_finished` — строка 91840
- `track_started` — строка 91850
- `skip` — строка 55168
- `sessionCloneRequest`, `sessionNewRequest`, `sessionTracksRequest`

Также найдены следы Room-кэша feedback:

- таблица `wave_feedback`
- SQL `DELETE FROM wave_feedback WHERE id IN (`

### 8. Текущий шаг

Попытка запустить `dexdump -d classes.dex` для поиска класса, содержащего `mobile.api.music.yandex.net`, была прервана пользователем.

Дальнейший план:

1. Определить, в каком dex лежит строка базового URL.
2. Прогнать `dexdump` только по этому dex и найти класс-владелец.
3. Извлечь базовый URL, заголовки и авторизацию.
4. Найти классы, формирующие `track_started`, `track_finished`, `skip`.
5. Дополнить `docs/yandex-rotor-reverse.md` и перейти к плану адаптации.
