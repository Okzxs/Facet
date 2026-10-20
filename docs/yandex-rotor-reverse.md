# Яндекс Музыка: реверс-инжиниринг Rotor / «Моей волны»

Статус: **в работе**  
Дата начала: 21.10.2026  
Источник: официальный APK Яндекс Музыки `2026.09.5 #167.2rur`  
Цель: статически извлечь контракт Rotor API и описать адаптацию нашего `WaveRepository`.

> Документ ведётся по мере анализа. Все выводы ниже получены статическим анализом APK, без запуска приложения, без обхода SSL Pinning и без перехвата трафика.

---

## 1. Артефакты

| Артефакт | Путь / значение |
|---|---|
| APK | `D:\MyMusic\reverse\yandex\yandex-music-2026.09.5.apk` |
| Пакет | `ru.yandex.music` |
| Версия | `2026.09.5 #167.2rur` |
| versionCode | `24026503` |
| minSdk / targetSdk | `24 / 36` |
| Размер APK | `46 804 627` байт |
| SHA-256 | `840b1653df3c329ca70f2315e4cb1048af3e369f73ba5ee23052820e3e1fdfe5` |
| Распакованные dex | `D:\MyMusic\reverse\yandex\unpacked\classes*.dex` |
| Строки dex | `D:\MyMusic\reverse\yandex\dex-strings.txt` |
| JADX single-class | `D:\MyMusic\reverse\yandex\single\*.java` |

APK получен через RuStore API, потому что APKPure, APKMirror и Uptodown заблокировали прямое скачивание:

- `GET https://backapi.rustore.ru/applicationData/overallInfo/ru.yandex.music` (заголовок `ruStoreVerCode: 110802`)
- `POST https://backapi.rustore.ru/applicationData/download-link` с телом `{"appId":303463871,"firstInstall":true}`

---

## 2. Найденные интерфейсы

Пакеты с Rotor/Wave-кодом не обфусцированы на уровне имён классов, но методы интерфейсов переименованы в `a`, `b`, `c`, `d`, `e`.

### 2.1 `TrackRotorApi`

Класс: `com.yandex.music.shared.wave.data.network.TrackRotorApi`

| Метод | HTTP (предположительно) | Путь | Параметры | Ответ |
|---|---|---|---|---|
| `a` | POST | `rotor/session/{radioSessionId}/tracks` | `X-Yandex-Music-AI-Content-Rate` (header), `radioSessionId` (path), `SessionTracksRequestDto` (body) | `MusicBackendResponse<SessionTracksResponseDto>` |
| `b` | POST | `rotor/sessions/feedbacks` | `SessionFeedbacksRequestDto` (body) | `Unit` |
| `c` | POST | `rotor/session/new` | `X-Yandex-Music-AI-Content-Rate` (header), `SessionNewRequestDto` (body) | `MusicBackendResponse<SessionNewResponseDto>` |
| `d` | POST | `rotor/session/{sessionId}/clone` | `X-Yandex-Music-AI-Content-Rate` (header), `sessionId` (path), `SessionCloneRequestDto` (body) | `MusicBackendResponse<SessionNewResponseDto>` |

Аннотации JADX:

- `@v2l("rotor/session/{radioSessionId}/tracks")`
- `@v2l("rotor/sessions/feedbacks")`
- `@v2l("rotor/session/new")`
- `@v2l("rotor/session/{sessionId}/clone")`
- `@ql3` — тело запроса
- `@gdl` — path-параметр
- `@kje` — header

`v2l` используется на всех методах с телом, поэтому с высокой вероятностью это `@POST`. Точное подтверждение аннотации — в разделе «Открытые вопросы».

### 2.2 `RotorApi`

Класс: `com.yandex.music.shared.radio.recommendation.RotorApi`

| Метод | HTTP (предположительно) | Путь | Параметры | Ответ |
|---|---|---|---|---|
| `a` | GET | `rotor/wave/last` | — | `MusicBackendResponse<WaveLastDto>` |
| `b` | GET | `rotor/session/{sessionId}?includeWaveModel=true` | `sessionId` (path) | `MusicBackendResponse<SessionInfoResponseDto>` |
| `c` | POST | `rotor/wave/last/reset` | — | `MusicBackendResponse<Unit>` |
| `d` | GET | `rotor/station/{stationId}/info` | `stationId` (path, объект `StationIdDtoRequest`) | `MusicBackendResponse<List<StationWithSettingsDto>>` |
| `e` | GET | `rotor/wave/settings` | `seeds` (query, `@efo`) | `MusicBackendResponse<WaveSettingsResponseDto>` |

Аннотации JADX:

- `@imd(...)` — GET-подобные методы без тела
- `@v2l("rotor/wave/last/reset")` — метод сброса
- `@efo("seeds")` — query-параметр
- `@gdl` — path-параметр

---

## 3. Схемы запросов

### 3.1 `SessionNewRequestDto`

Класс: `com.yandex.music.shared.wave.data.network.request.SessionNewRequestDto`

| JSON-поле | Тип | Обязательность |
|---|---|---|
| `seeds` | `List<String>` | да |
| `queue` | `List<String>?` | нет |
| `includeTracksInResponse` | `boolean` | да |
| `includeWaveModel` | `boolean` | да |
| `trackToStartFrom` | `String?` | нет |
| `clientRemoteType` | `String?` | нет |
| `incognito` | `Boolean?` | нет |
| `allowExplicit` | `Boolean?` | нет |
| `interactive` | `Boolean?` | нет |
| `sessions` | `List<SessionFeedbackRequestDto>?` | нет |

### 3.2 `SessionCloneRequestDto`

Класс: `com.yandex.music.shared.wave.data.network.request.SessionCloneRequestDto`

| JSON-поле | Тип |
|---|---|
| `queue` | `List<String>?` |
| `includeTracksInResponse` | `boolean` |
| `includeWaveModel` | `boolean` |
| `trackToStartFrom` | `String?` |
| `clientRemoteType` | `String?` |
| `incognito` | `Boolean?` |
| `allowExplicit` | `Boolean?` |
| `sessions` | `List<SessionFeedbackRequestDto>?` |

### 3.3 `SessionTracksRequestDto`

Класс: `com.yandex.music.shared.wave.data.network.request.SessionTracksRequestDto`

| JSON-поле | Тип |
|---|---|
| `queue` | `List<String>?` |
| `feedbacks` | `List<FeedbackRequestDto>?` |
| `sessions` | `List<SessionFeedbackRequestDto>?` |

### 3.4 `SessionFeedbacksRequestDto`

Класс: `com.yandex.music.shared.wave.data.network.request.SessionFeedbacksRequestDto`

| JSON-поле | Тип |
|---|---|
| `sessions` | `List<SessionFeedbackRequestDto>` |

Для класса есть кастомный Gson-сериализатор `SessionFeedbacksRequestDto$SessionFeedbacksRequestDtoJsonAdapter`.

### 3.5 `SessionFeedbackRequestDto`

Класс: `com.yandex.music.shared.wave.data.network.request.SessionFeedbackRequestDto`

| JSON-поле | Тип |
|---|---|
| `sessionId` | `String` |
| `seeds` | `List<String>?` |
| `feedbacks` | `List<FeedbackRequestDto>` |

### 3.6 `FeedbackRequestDto`

Класс: `com.yandex.music.shared.wave.data.network.request.FeedbackRequestDto`

| JSON-поле | Тип |
|---|---|
| `event` | `FeedbackEventDto` |
| `batchId` | `String?` |
| `from` | `String` |

### 3.7 `FeedbackEventDto`

Класс: `com.yandex.music.shared.wave.data.network.request.FeedbackEventDto`

| JSON-поле | Тип | Примечание |
|---|---|---|
| `type` | `String` | тип события |
| `timestamp` | `su7` | обёртка даты; сериализуется кастомно |
| `trackId` | `String?` | ID трека |
| `totalPlayedSeconds` | `Float?` | сколько прослушано |
| `trackLengthSeconds` | `Float?` | длина трека |
| `from` | `String?` | источник события |

`su7` наследует `uu7`:

```java
public abstract class uu7 {
    public final String a;
    public final Date b;

    public uu7(Date date, String str) {
        this.a = str;
        this.b = date;
    }

    public final String a() {
        return this.a;
    }
}
```

Точный формат сериализации `timestamp` ещё требует проверки: нужно понять, пишется ли ISO-строка, unix-время или объект.

---

## 4. Схемы ответов

### 4.1 `SessionNewResponseDto`

Класс: `com.yandex.music.shared.wave.data.network.response.SessionNewResponseDto`

| JSON-поле | Тип |
|---|---|
| `radioSessionId` | `String?` |
| `batchId` | `String?` |
| `sequence` | `List<SequenceItemDto>?` |
| `pumpkin` | `Boolean?` |
| `wave` | `WaveDto?` |
| `offlineRecommenderData` | `int[]?` |
| `terminated` | `Boolean?` |

### 4.2 `SessionTracksResponseDto`

Класс: `com.yandex.music.shared.wave.data.network.response.SessionTracksResponseDto`

| JSON-поле | Тип |
|---|---|
| `batchId` | `String?` |
| `sequence` | `List<SequenceItemDto>?` |
| `pumpkin` | `Boolean?` |
| `unknownSession` | `Boolean?` |
| `offlineRecommenderData` | `int[]?` |
| `terminated` | `Boolean?` |

### 4.3 `SequenceItemDto`

Класс: `com.yandex.music.shared.wave.data.network.response.SequenceItemDto`

| JSON-поле | Тип |
|---|---|
| `track` | `TrackDto?` |
| `trackParameters` | `TrackParametersDto?` |

---

## 5. Feedback-события

В `dex-strings.txt` найдены строковые литералы:

| Строка | Номер строки в файле |
|---|---|
| `skip` | 55168 |
| `session_started` | 91260 |
| `track_finished` | 91840 |
| `track_started` | 91850 |

Рядом со `session_started` находятся имена запросов:

- `sessionCloneRequest`
- `sessionNewRequest`
- `sessionTracksRequest`
- `session_started`

Предварительная модель:

1. Создать сессию через `rotor/session/new` с `seeds` и `includeTracksInResponse=true`.
2. Получить `radioSessionId`, `batchId` и `sequence`.
3. При старте трека отправить `track_started` с `trackId`, `batchId`, `from`.
4. При завершении/скипе отправить `track_finished` или `skip` с длительностью прослушивания.
5. Запросить следующую пачку через `rotor/session/{radioSessionId}/tracks`, передав `queue`, накопленные `feedbacks` и `sessions`.

Что ещё нужно подтвердить:

- точные значения `from` (`radio`, `wave`, `user`, `playlist` и т.д.);
- где формируются события `track_started` / `track_finished` / `skip`;
- как считается `totalPlayedSeconds`;
- что именно попадает в `batchId` и `sessions`;
- как обрабатываются `terminated`, `unknownSession`, `pumpkin`.

---

## 6. Базовый URL и авторизация

В dex найдены строки:

- `https://api.music.yandex.net/`
- `https://mobile.api.music.yandex.net/`
- `legacyBaseUrl=https://api.music.yandex.net/`
- `wss://ws.api.music.yandex.net/match/websocket`

Пока не установлено, какой именно host использует мобильный Retrofit-клиент для Rotor. Это одна из ближайших задач.

Также нужно извлечь:

- заголовок авторизации (`Authorization: OAuth ...` или иной);
- заголовки клиента (`X-Yandex-Music-Client`, версия, device id, language);
- обязательные query-параметры;
- формат ошибок `MusicBackendResponse`.

---

## 7. План адаптации `WaveRepository`

Текущий `WaveRepositoryImpl` работает только с SoundCloud и собирает волну локально:

- 10 лайков;
- 5 треков из истории;
- 5 треков по жанрам;
- fallback на чарт.

Rotor-подход отличается: это не «собрать список один раз», а **сессия с обратной связью**.

### 7.1 Что нужно изменить в модели

Ввести сущности:

- `WaveSession` — `sessionId`, `batchId`, `seeds`, `queue`, `isTerminated`;
- `WaveBatch` — список треков и флаг необходимости догрузки;
- `WaveFeedbackEvent` — `type`, `trackId`, `playedSeconds`, `trackLengthSeconds`, `from`, `timestamp`;
- `WaveFeedbackBatch` — `sessionId`, `batchId`, `events`.

### 7.2 Что должен уметь репозиторий

```kotlin
interface WaveRepository {
    suspend fun startSession(seeds: List<String>): Result<WaveSession>
    suspend fun nextBatch(session: WaveSession, queue: List<String>): Result<WaveBatch>
    suspend fun sendFeedback(session: WaveSession, events: List<WaveFeedbackEvent>): Result<Unit>
    suspend fun reset(): Result<Unit>
}
```

### 7.3 Два варианта реализации

**Вариант A. Реальный Rotor API Яндекс Музыки.**

Плюс: настоящее рекомендование.  
Минус: нужна авторизация Яндекс, токен, ID треков Яндекса и решение юридических/ToS-вопросов. Для нашего приложения это отдельный источник `Source.YANDEX`, а не замена SoundCloud.

**Вариант B. Локальная эмуляция Rotor-логики.**

Плюс: не зависит от чужого API, работает на существующих источниках.  
Минус: качество рекомендаций ниже.

Для варианта B:

1. `seeds` — ID лайкнутых треков и недавней истории.
2. `startSession()` создаёт локальную сессию и очередь-заготовку.
3. `nextBatch()` добирает треки из SoundCloud по жанрам/похожести, исключая уже показанные.
4. `sendFeedback()` сохраняет лайк/скип/прослушивание в Room.
5. Следующая пачка учитывает feedback: убирает скипнутые, поднимает похожие на дослушанные.
6. `reset()` очищает сессию и историю волны.

### 7.4 Минимальный первый шаг

Не переписывать UI. Сначала добавить в `WaveRepository` сессию и события, сохранив текущий `generateWave()` как совместимый метод. Затем перевести `WaveViewModel` на `startSession()` + `nextBatch()`.

---

## 8. Открытые вопросы

1. `v2l` — это точно `@POST`, а `imd` — `@GET`?
2. Какой host используется: `api.music.yandex.net` или `mobile.api.music.yandex.net`?
3. Какие заголовки обязательны для Rotor-запросов?
4. Как сериализуется `timestamp` в `FeedbackEventDto`?
5. Где именно формируются `track_started`, `track_finished`, `skip`?
6. Что передаётся в `from` и `sessions`?
7. Какой формат `MusicBackendResponse` и ошибок?
8. Нужен ли `X-Yandex-Music-AI-Content-Rate`, и что означают его значения?

---

## 9. Журнал изменений документа

| Дата | Изменение |
|---|---|
| 21.10.2026 | Создан документ; зафиксированы APK, интерфейсы, DTO и предварительная модель feedback |
