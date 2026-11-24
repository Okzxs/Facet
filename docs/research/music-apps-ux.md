# UX/UI музыкальных плееров: матрица паттернов для Facet

**Дата:** 25.11.2026 · **Объект:** Facet (Android, Jetpack Compose, MD3 Expressive, GPLv3, APK ≤40 МБ).
**Метод:** shallow-клоны исходников в `/tmp/uxrepos`, чтение UI-кода, строк и настроек; закрытые приложения — только публичные сайты, страницы Google Play и Apple/Spotify Support. APK не распаковывались, ничего не устанавливалось, код Facet не менялся.
**Ограничения:** у Dotify не нашлось публичного репозитория (приложение закрытое, распространяется APK/Telegram); по Samsung Music публичных деталей о кастомизации не нашлось. Эти пробелы отмечены явно.

**Как читать:** «Берём» — делать в Facet; «Идея» — переносить принцип, не код; «Не берём» — осознанный отказ. Ссылки на код привязаны к коммитам, чтобы строки не уплывали.

**Что у Facet уже есть (чтобы не рекомендовать повторно):** пресеты акцента + RGB-пикер + недавние цвета, dynamic cover colors, тёмная тема (без отдельного AMOLED-режима), плавающий мини-плеер 64 dp, нижний док, шторки очереди и таймера сна, word-synced текст с подсветкой, офсет и перевод, настройки перевода текста (`docs/research/m3-audit.md`). Поэтому ниже — только дельта.

---

## 1. Структура экранов

| Паттерн | Где видел (ссылка/файл) | Берём/не берём | Почему |
|---|---|---|---|
| Настройки как отдельные страницы-разделы, а не один длинный скролл | MetroMusic, [SettingsPages.kt:70](https://github.com/Diffechento/MetroMusic/blob/68f939863d32ce450c64c303c3948b9782691990/app/src/main/java/com/metromusic/ui/screens/SettingsPages.kt#L70) | Идея | У Facet один длинный экран настроек (~1,5 тыс. строк); разбиение на 5–6 страниц уберёт «простыню», но потребует навигации внутри настроек |
| Поиск по настройкам с синонимами и ключевыми словами строк | RX Playa, [SettingsSheet.kt:504](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L504), пример синонимов [там же:1058](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L1058) | Берём | Самый дешёвый способ сделать «студию» обозримой: строка ищется по «glass/frost/blur/refract», а не по точному названию |
| Оглавление настроек с категориями и переходом на страницу | RX Playa, [SettingsSheet.kt:632](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L632) | Берём | Категории + поиск дают и обзор, и точечный доступ; у Facet сейчас только секции подряд |
| Библиотека-панорама с циклическим свайпом и настройками внутри | MetroMusic, [README](https://github.com/Diffechento/MetroMusic/blob/68f939863d32ce450c64c303c3948b9782691990/README.md) | Не берём | Сильный характер, но ломает привычную модель «док + экраны» и конфликтует с жестами Facet |
| Настраиваемый состав и порядок вкладок библиотеки | Auxio, [preferences_personalize.xml:8](https://github.com/OxygenCobalt/Auxio/blob/9e18f5fe660a76e0df91194a2b1bf7bba1952aee/app/src/main/res/xml/preferences_personalize.xml#L8); Retro, [pref_ui.xml:57](https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/41af88df479c9571592327d0ccc53901f6a67b6e/app/src/main/res/xml/pref_ui.xml#L57) | Берём | Дешёвая персонализация: скрыть «Жанры» или поднять «Плейлисты» без нового кода экранов |
| Стиль сетки Home: circular / card / tiny card / square | Retro, [pref_ui.xml:23](https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/41af88df479c9571592327d0ccc53901f6a67b6e/app/src/main/res/xml/pref_ui.xml#L23) | Берём | Одна настройка меняет облик Home без второй вёрстки; полезно на планшете и в landscape |
| Поиск внутри раздела из его шапки | MetroMusic, [README](https://github.com/Diffechento/MetroMusic/blob/68f939863d32ce450c64c303c3948b9782691990/README.md) | Идея | У Facet есть глобальный поиск; локальный фильтр в длинном плейлисте/альбоме снимает «прокрути и найди» |
| Поиск по тексту песен и с опечатками | Namida, [README](https://github.com/namidaco/namida/blob/436feae5be45d92bebcdf66cd9e04e613cd7782c/README.md) | Идея | Мощно, но требует индексации текстов в Room; для Facet — отдельная фаза, не в первый релиз студии |
| Очередь как отдельный экран и как шторка одновременно | Riff, [index.html:923](https://github.com/rootscripts/riff/blob/5031a840fb50dbb53a5830329ed69363fe09d824/src/renderer/index.html#L923); Auxio, [QueueFragment.kt:40](https://github.com/OxygenCobalt/Auxio/blob/9e18f5fe660a76e0df91194a2b1bf7bba1952aee/app/src/main/java/org/oxycblt/auxio/playback/queue/QueueFragment.kt#L40) | Берём | У Facet шторка есть; отдельный маршрут нужен для длинной очереди и восстановления после смерти процесса |

## 2. Кастомизация

| Паттерн | Где видел (ссылка/файл) | Берём/не берём | Почему |
|---|---|---|---|
| 17 схем, включая Dynamic, и отдельные pure-black варианты каждой | Auxio, [Accent.kt:25](https://github.com/OxygenCobalt/Auxio/blob/9e18f5fe660a76e0df91194a2b1bf7bba1952aee/app/src/main/java/org/oxycblt/auxio/ui/accent/Accent.kt#L25) | Идея | У Facet 6 пресетов + свой цвет; можно добавить 2–3 «тёмных» дубля, но не 34 темы — вес и поддержка |
| AMOLED как отдельный переключатель, а не тема | Auxio, [preferences_ui.xml:19](https://github.com/OxygenCobalt/Auxio/blob/9e18f5fe660a76e0df91194a2b1bf7bba1952aee/app/src/main/res/xml/preferences_ui.xml#L19); Gramophone, [settings_appearance.xml:37](https://github.com/FoedusProgramme/Gramophone/blob/f8eb02d9f92e98c4876dbfed7519948752765cd3/app/src/main/res/xml/settings_appearance.xml#L37) | Берём | Ортогонально любой палитре, дёшево в Compose: `surface = Color.Black` при тёмной теме |
| Радиус обложки слайдером 0–28 dp | Gramophone, [settings_player.xml:25](https://github.com/FoedusProgramme/Gramophone/blob/f8eb02d9f92e98c4876dbfed7519948752765cd3/app/src/main/res/xml/settings_player.xml#L25) | Берём | Один `Int` в настройках даёт «квадрат/сквиркл/круг» без трёх отдельных тем; хорошо ложится на `Shape.kt` Facet |
| «Cookie»-форма обложки (6-гранная MD3 Expressive) | Gramophone, [settings_player.xml:82](https://github.com/FoedusProgramme/Gramophone/blob/f8eb02d9f92e98c4876dbfed7519948752765cd3/app/src/main/res/xml/settings_player.xml#L82); Auxio, [ListUtil.kt:86](https://github.com/OxygenCobalt/Auxio/blob/9e18f5fe660a76e0df91194a2b1bf7bba1952aee/app/src/main/java/org/oxycblt/auxio/home/list/ListUtil.kt#L86) | Идея | Для обложек в списках это снижает узнаваемость; уместнее в пустых состояниях/иллюстрациях, а не на альбомах |
| Full-bleed обложка на весь экран + «Blended» край-в-край | RX Playa, [SettingsSheet.kt:1236](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L1236); Poweramp, [powerampapp.com](https://powerampapp.com/) | Берём | Даёт «вау» без нового экрана; у Facet уже есть морф обложки — остаётся режим края |
| Mesh-градиент из обложки как фон плеера | RX Playa, [SettingsSheet.kt:1252](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L1252); Riff, [theme.js:728](https://github.com/rootscripts/riff/blob/5031a840fb50dbb53a5830329ed69363fe09d824/src/renderer/modules/theme.js#L728) | Берём | У Facet тонировка 28→14 %; 2–3 радиальных пятна из палитры обложки дают глубину почти бесплатно |
| Регулировка силы размытия + отдельный «уменьшить blur» | Retro, [pref_now_playing_screen.xml:116](https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/41af88df479c9571592327d0ccc53901f6a67b6e/app/src/main/res/xml/pref_now_playing_screen.xml#L116); RX Playa, [SettingsSheet.kt:1216](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L1216) | Берём | Blur — главный источник jank на слабых GPU; пользователю нужен «выключить, если тормозит» |
| Тумблер «уменьшить движение» с честным tween=0 | RX Playa, [SettingsSheet.kt:1201](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L1201) | Берём | И доступность, и батарея; у Facet анимаций много, глобальный множитель длительности — малая правка |
| Переключатель гарнитуры и шкалы (SF Pro ↔ legacy) | RX Playa, [SettingsSheet.kt:1191](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L1191) | Идея | Montserrat приходит вместе с M3-стилем, а не отдельным выбором; вторая гарнитура — +150–400 КБ и вторая шкала, для 40 МБ только как тема-пресет |
| Множитель всех радиусов и глобальный fontScale | Namida, [customization_settings.dart:97](https://github.com/namidaco/namida/blob/436feae5be45d92bebcdf66cd9e04e613cd7782c/lib/ui/widgets/settings/customization_settings.dart#L97) | Идея | Мощно, но ломает M3-консистентность и вёрстку; достаточно шкалы «Компактно/Обычно/Крупно» |
| Анимированная смена акцента (theme reveal) | Namida, [ntheme.dart:47](https://github.com/namidaco/namida/blob/436feae5be45d92bebcdf66cd9e04e613cd7782c/lib/core/ntheme.dart#L47) | Берём | Смена палитры должна быть событием, а не мгновенным щелчком; 250–300 мс `Crossfade` цвета |
| Animated cover art / Canvas / видео-обложки | RX Playa, [SettingsSheet.kt:1267](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L1267) | Не берём | Видеодекод, трафик и кэш; для APK ≤40 МБ и парсинга источников — лишний риск |
| Снегопад, карусель обложек, «swipe anywhere» для смены трека | Retro, [pref_now_playing_screen.xml:20](https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/41af88df479c9571592327d0ccc53901f6a67b6e/app/src/main/res/xml/pref_now_playing_screen.xml#L20), [:47](https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/41af88df479c9571592327d0ccc53901f6a67b6e/app/src/main/res/xml/pref_now_playing_screen.xml#L47), [:74](https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/41af88df479c9571592327d0ccc53901f6a67b6e/app/src/main/res/xml/pref_now_playing_screen.xml#L74) | Не берём | Снег — китч; свайп «где угодно» конфликтует с шторкой очереди и системным back-жестом |
| Настраиваемая нижняя навигация: состав и порядок | Retro, [retromusic.app](https://retromusic.app/); Auxio, [preferences_personalize.xml:8](https://github.com/OxygenCobalt/Auxio/blob/9e18f5fe660a76e0df91194a2b1bf7bba1952aee/app/src/main/res/xml/preferences_personalize.xml#L8) | Берём | У Facet фиксированные 4 вкладки; настройка состава снимает «мне не нужна Волна» без потери экрана |
| Дополнительные элементы мини-плеера и свайп-вниз для закрытия | Retro, [pref_now_playing_screen.xml:86](https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/41af88df479c9571592327d0ccc53901f6a67b6e/app/src/main/res/xml/pref_now_playing_screen.xml#L86) | Берём | У Facet мини-плеер уже плавающий; не хватает «закрыть свайпом» и настраиваемого набора кнопок |

## 3. Настройки и «студия оформления»

| Паттерн | Где видел (ссылка/файл) | Берём/не берём | Почему |
|---|---|---|---|
| Группы-карточки с инлайн-контролами (свотчи, сегменты, слайдеры) | RX Playa, [SettingsSheet.kt:1053](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L1053) | Берём | Вместо «строка → диалог» настройка видна и меняется на месте; меньше тапов |
| Точечный сброс параметра (акцент, папка загрузок), а не только «сбросить всё» | RX Playa, [SettingsSheet.kt:1171](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L1171) | Берём | После экспериментов с акцентом нужен точечный откат; глобальный сброс страшен |
| «В этом режиме» бейджи (Wi-Fi / мобильные данные) | RX Playa, [SettingsSheet.kt:577](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L577) | Берём | Пользователь видит, какое качество реально применяется, без открытия диалога |
| Экспорт/импорт настроек скина в JSON с проверкой версии | Poweramp, [powerampapp.com](https://powerampapp.com/) | Берём | Перенос темы между устройствами и бэкап; в Facet — JSON в `SharedPreferences`-схему с `schemaVersion` |
| Скрытая диагностика («Nerd stats») под отдельным тумблером | RX Playa, [AppSettings.kt:511](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/data/settings/AppSettings.kt#L511) | Берём | Отладочная информация не должна пугать обычного пользователя, но нужна для баг-репортов |
| Плагины/скины как отдельные APK или JS-файлы | Riff, [index.html:1786](https://github.com/rootscripts/riff/blob/5031a840fb50dbb53a5830329ed69363fe09d824/src/renderer/index.html#L1786); Poweramp, [powerampapp.com](https://powerampapp.com/) | Не берём | Система плагинов — это API, безопасность и поддержка; для GPL-APK с парсингом — неподъёмно |
| Мгновенное применение темы без «Применить» | Riff, [theme.js:855](https://github.com/rootscripts/riff/blob/5031a840fb50dbb53a5830329ed69363fe09d824/src/renderer/modules/theme.js#L855) | Берём | Живой предпросмотр = меньше отмен; обязательно с undo-снеком «Вернуть» на 5 с |

## 4. Плеер

| Паттерн | Где видел (ссылка/файл) | Берём/не берём | Почему |
|---|---|---|---|
| Три раскладки плеера: Default / Alternative / Full cover | Poweramp, [powerampapp.com](https://powerampapp.com/) | Берём | Один экран, три компоновки — дешевле трёх экранов; Facet нужен минимум «Обложка» и «Инфо» |
| Прогресс-бар-хайрлайн, утолщающийся под пальцем | RX Playa, [ThinSlider.kt:5](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/sharedUi/src/jvmSharedMain/kotlin/com/rentalx/playa/ui/player/ThinSlider.kt#L5) | Идея | Точный seek без «прыжка» большим пальцем; у Facet волнистый Expressive-слайдер, хайрлайн уместен в раскладке «Инфо» |
| Стиль обложки + «album cover skip» (сдвиг при скролле) | Retro, [pref_now_playing_screen.xml:57](https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/41af88df479c9571592327d0ccc53901f6a67b6e/app/src/main/res/xml/pref_now_playing_screen.xml#L57), [:66](https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/41af88df479c9571592327d0ccc53901f6a67b6e/app/src/main/res/xml/pref_now_playing_screen.xml#L66) | Идея | Параллакс обложки — приятно, но легко превращается в «дёрганый» экран; только на сильных устройствах |
| История очереди: секция «уже проиграно» | RX Playa, [PlayerQueue.kt:250](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/sharedUi/src/jvmSharedMain/kotlin/com/rentalx/playa/ui/player/PlayerQueue.kt#L250) | Берём | Позволяет вернуться к треку, который только что проскочил; данные уже есть в истории |
| Контекст «Playing from …» с возможностью скрыть | RX Playa, [PlayerControls.kt:149](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/sharedUi/src/jvmSharedMain/kotlin/com/rentalx/playa/ui/player/PlayerControls.kt#L149) | Берём | Снимает вопрос «почему играет этот трек»; в Facet такого индикатора нет, но контекст уже известен при запуске из плейлиста/волны |
| Таймер сна: 15/30/45/60/90, «до конца трека», +5 мин, off | Riff, [index.html:3530](https://github.com/rootscripts/riff/blob/5031a840fb50dbb53a5830329ed69363fe09d824/src/renderer/index.html#L3530) | Берём | У Facet 15/30/60; «+5» и «до конца трека» закрывают реальные сценарии |
| Кроссфейд слайдером 0–12 с | RX Playa, [SettingsSheet.kt:892](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L892); Spotify, [tracks-transitions](https://support.spotify.com/us/article/tracks-transitions/) | Берём | Media3 умеет; ценность выше, чем у эквалайзера, при малом коде |
| Automix / beat-aligned переходы | RX Playa, [SettingsSheet.kt:908](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L908) | Не берём | Нужен анализ BPM/структуры и ML-модель; вес, батарея, непредсказуемость на стримах |
| Боковая панель текста/очереди на планшете и в landscape | RX Playa, [PlayerSidePanels.kt:25](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/sharedUi/src/jvmSharedMain/kotlin/com/rentalx/playa/ui/player/PlayerSidePanels.kt#L25) | Берём | У Facet есть только телефонная компоновка; `WindowSizeClass` + панель справа — сильный планшетный сценарий |
| Свайп вверх по обложке открывает очередь | RX Playa, [NowPlayingScreen.kt:239](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/sharedUi/src/jvmSharedMain/kotlin/com/rentalx/playa/ui/player/NowPlayingScreen.kt#L239) | Идея | Красиво, но у Facet уже есть свайп-вниз для закрытия и кнопка очереди; два свайпа на одном экране конфликтуют |
| Громкость и дополнительные кнопки прямо в плеере | Retro, [pref_now_playing_screen.xml:102](https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/41af88df479c9571592327d0ccc53901f6a67b6e/app/src/main/res/xml/pref_now_playing_screen.xml#L102) | Не берём | Дублирует системный слайдер и занимает место; на Android это плохая практика |
| Blur на локскрине и кастомные локскрин-контролы | Poweramp, [powerampapp.com](https://powerampapp.com/); Retro, [pref_now_playing_screen.xml](https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/41af88df479c9571592327d0ccc53901f6a67b6e/app/src/main/res/xml/pref_now_playing_screen.xml) | Не берём | На Android 13+ локскрин рисует система из MediaSession; кастомизация хрупкая и не окупается |

## 5. Текст песен

| Паттерн | Где видел (ссылка/файл) | Берём/не берём | Почему |
|---|---|---|---|
| Пословная заливка с бликом и «дыханием» строк | RX Playa, [PlayerLyrics.kt:769](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/sharedUi/src/jvmSharedMain/kotlin/com/rentalx/playa/ui/player/PlayerLyrics.kt#L769) | Идея | У Facet уже `WordSweptText`; брать целиком не нужно, но приём «blur после клипа» даёт мягкий край без ореола |
| Вторая дорожка для бэк-вокала под строкой | Oto, [Google Play](https://play.google.com/store/apps/details?id=com.piyush.music) | Берём | «Background vocals tucked under the line» — редкий и очень уместный паттерн; данные уже приходят в rich-sync |
| Перевод + транслитерация/произношение отдельным переключателем | Apple Music, [Support 105076](https://support.apple.com/en-us/105076); RX Playa, [PlayerLyrics.kt:289](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/sharedUi/src/jvmSharedMain/kotlin/com/rentalx/playa/ui/player/PlayerLyrics.kt#L289) | Берём | У Facet есть перевод; транслитерация для японского/корейского — дешёвая дельта на том же лейне |
| Выбор провайдера текста со статусом по каждому | RX Playa, [LyricsProviderSheet.kt:55](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/sharedUi/src/jvmSharedMain/kotlin/com/rentalx/playa/ui/player/LyricsProviderSheet.kt#L55) | Берём | «Не найдено у LRCLIB, найдено у Musixmatch» снимает ощущение «текста нет»; Facet уже знает `LyricsResult.provider`, но не показывает его и не даёт выбрать |
| Ручная подстройка офсета с шагом и сбросом | RX Playa, [LyricsOffsetSheet.kt:75](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/sharedUi/src/jvmSharedMain/kotlin/com/rentalx/playa/ui/player/LyricsOffsetSheet.kt#L75) | Берём | У Facet слайдер ±2 с шагом 100 мс и сброс; не хватает пары кнопок −/+100 мс, чтобы подстраивать на ходу, не открывая диалог |
| Размытие неактивных строк | RX Playa, [SettingsSheet.kt:1397](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L1397) | Берём | Фокус на текущей строке без жёсткого затемнения; дешёвый `graphicsLayer` blur |
| Размер текста слайдером (по умолчанию 34) | Gramophone, [settings_lyric.xml:42](https://github.com/FoedusProgramme/Gramophone/blob/f8eb02d9f92e98c4876dbfed7519948752765cd3/app/src/main/res/xml/settings_lyric.xml#L42) | Берём | Крупный текст — не только доступность, но и «караоке с дивана»; хранить в sp, не в px |
| Жирность и выравнивание строк отдельными тумблерами | Gramophone, [settings_player.xml:34](https://github.com/FoedusProgramme/Gramophone/blob/f8eb02d9f92e98c4876dbfed7519948752765cd3/app/src/main/res/xml/settings_player.xml#L34) | Берём | Два булева флага, а ощущение «под себя»; для RTL выравнивание уже учитывается |
| Текст в статус-баре и уведомлении | Gramophone, [settings_lyric.xml:51](https://github.com/FoedusProgramme/Gramophone/blob/f8eb02d9f92e98c4876dbfed7519948752765cd3/app/src/main/res/xml/settings_lyric.xml#L51); Namida, [README](https://github.com/namidaco/namida/blob/436feae5be45d92bebcdf66cd9e04e613cd7782c/README.md) | Не берём | Строка-статус в шторке Android выглядит как уведомление и конфликтует с Now Bar Samsung; низкая отдача |
| Виджет с текстом песни | Gramophone, [lyric_widget.xml](https://github.com/FoedusProgramme/Gramophone/blob/f8eb02d9f92e98c4876dbfed7519948752765cd3/app/src/main/res/xml/lyric_widget.xml) | Не берём | Ещё один `AppWidgetProvider` и обновления; при лимите 40 МБ лучше один универсальный виджет |
| Редактор синхронизации текста руками | Namida, [README](https://github.com/namidaco/namida/blob/436feae5be45d92bebcdf66cd9e04e613cd7782c/README.md) | Не берём | Отдельный редактор с таймкодами — продукт в продукте; для Facet достаточно офсета |
| Два режима подсветки: градиент по строке и по словам | Riff, [index.html:2518](https://github.com/rootscripts/riff/blob/5031a840fb50dbb53a5830329ed69363fe09d824/src/renderer/index.html#L2518) | Берём | «Gradient» дешевле и красивее на длинных строках, «Words» — для караоке; переключатель в настройках текста |
| Поделиться фрагментом текста | Spotify, [View lyrics](https://support.spotify.com/us/article/lyrics/) | Идея | Хороший вирусный жест, но требует рендера картинки и шаринга; после стабилизации плеера |

## 6. Эквалайзер и звук

| Паттерн | Где видел (ссылка/файл) | Берём/не берём | Почему |
|---|---|---|---|
| 7-полосный графический EQ + Tone-пад X/Y + пресеты | RX Playa, [EqualizerScreen.kt:80](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/EqualizerScreen.kt#L80), [TonePad:290](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/EqualizerScreen.kt#L290) | Берём | 7 полос и пресеты — понятная база, Tone-пад X/Y — редкий и дешёвый UX-дифференциатор; способ DSP в Facet (системный `Equalizer` или `AudioProcessor`) решается отдельно |
| 64-полосный параметрический EQ, AutoEq 20 500 пресетов, лимитер | Poweramp, [powerampapp.com](https://powerampapp.com/) | Не берём | Своя DSP-цепочка, база пресетов и UI на десятки тысяч строк — не для лёгкого APK |
| Skip silence | RX Playa, [AppSettings.kt:356](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/data/settings/AppSettings.kt#L356) | Берём | В Media3 есть `setSkipSilenceEnabled`; для подкастов/концертов полезно |
| ReplayGain / нормализация громкости | Auxio, [preferences_audio.xml](https://github.com/OxygenCobalt/Auxio/blob/9e18f5fe660a76e0df91194a2b1bf7bba1952aee/app/src/main/res/xml/preferences_audio.xml); Oto, [Google Play](https://play.google.com/store/apps/details?id=com.piyush.music) | Идея | Media3 ReplayGain требует анализа громкости; можно как «выравнивание» на уровне трека позже |

## 7. UX-детали, онбординг, доступность

| Паттерн | Где видел (ссылка/файл) | Берём/не берём | Почему |
|---|---|---|---|
| Пустые состояния с человеческой подсказкой, а не «Нет данных» | Auxio, [strings.xml:213](https://github.com/OxygenCobalt/Auxio/blob/9e18f5fe660a76e0df91194a2b1bf7bba1952aee/app/src/main/res/values/strings.xml#L213) | Берём | «Your songs will show up here» объясняет, что делать; у Facet `EmptyState` есть, но тексты надо переписать под каждый экран |
| Пустая очередь с приглашением, а не пустой список | Riff, [index.html:929](https://github.com/rootscripts/riff/blob/5031a840fb50dbb53a5830329ed69363fe09d824/src/renderer/index.html#L929) | Берём | Очередь — место, где чаще всего «ничего нет»; подсказка «включите трек» убирает тупик |
| Бейдж количества в навигации очереди | Riff, [index.html:561](https://github.com/rootscripts/riff/blob/5031a840fb50dbb53a5830329ed69363fe09d824/src/renderer/index.html#L561) | Берём | Даёт ощущение «там что-то есть»; в Compose — `BadgedBox` на иконке |
| Автоскролл к играющему треку в списке | Retro, [pref_ui.xml:79](https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/41af88df479c9571592327d0ccc53901f6a67b6e/app/src/main/res/xml/pref_ui.xml#L79) | Берём | У Facet частично есть; нужен как явная настройка, потому что ломает позицию чтения |
| Каждый жест — с тумблером | MetroMusic, [SettingsPages.kt:90](https://github.com/Diffechento/MetroMusic/blob/68f939863d32ce450c64c303c3948b9782691990/app/src/main/java/com/metromusic/ui/screens/SettingsPages.kt#L90) | Берём | «Жест, который сработал случайно, хуже отсутствия жеста» — точная формулировка из кода |
| Экран разрешения с ручной инструкцией, если диалог больше не показывается | MetroMusic, [PermissionGate.kt:99](https://github.com/Diffechento/MetroMusic/blob/68f939863d32ce450c64c303c3948b9782691990/app/src/main/java/com/metromusic/ui/PermissionGate.kt#L99) | Берём | Классическая ловушка «Отклонить навсегда»; текст «Settings → Apps → …» спасает поддержку |
| Онбординг из 2–3 шагов: имя, источники, качество | RX Playa, [OnboardingScreen.kt:169](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/onboarding/OnboardingScreen.kt#L169) | Берём | У Facet один экран с кнопкой «Начать»; выбор источников и качества на старте снимает первые ошибки |
| Крупный шрифт: пересчёт размеров, а не обрезка | MetroMusic, [NowPlayingScreen.kt](https://github.com/Diffechento/MetroMusic/blob/68f939863d32ce450c64c303c3948b9782691990/app/src/main/java/com/metromusic/ui/screens/NowPlayingScreen.kt); Namida, [customization_settings.dart:113](https://github.com/namidaco/namida/blob/436feae5be45d92bebcdf66cd9e04e613cd7782c/lib/ui/widgets/settings/customization_settings.dart#L113) | Берём | При `fontScale` 1.3 плеер Facet должен переносить строки, а не резать; нужен тест на 1.3/1.5 |
| `contentDescription` у иконок и кастомных кнопок | Auxio, [PlaybackPanelFragment.kt](https://github.com/OxygenCobalt/Auxio/blob/9e18f5fe660a76e0df91194a2b1bf7bba1952aee/app/src/main/java/org/oxycblt/auxio/playback/PlaybackPanelFragment.kt) | Берём | В `PlayerScreen` есть `contentDescription = null`; часть — законно декоративные, но кастомные кнопки надо проверить, иначе TalkBack читает «кнопка» без смысла |

## 8. Виджеты

| Паттерн | Где видел (ссылка/файл) | Берём/не берём | Почему |
|---|---|---|---|
| Семь типов виджетов под разные обои | Retro, [appwidgets](https://github.com/RetroMusicPlayer/RetroMusicPlayer/tree/41af88df479c9571592327d0ccc53901f6a67b6e/app/src/main/java/code/name/monkey/retromusic/appwidgets) | Не берём | Семь `AppWidgetProvider` + верстки = вес и поддержка; Facet хватит 2 (4×2 и 2×2) |
| Виджет-плитка «сейчас играет» для оболочки | MetroMusic, [NowPlayingTile.kt](https://github.com/Diffechento/MetroMusic/blob/68f939863d32ce450c64c303c3948b9782691990/app/src/main/java/com/metromusic/widget/NowPlayingTile.kt) | Идея | Интересно для Samsung/Quick Settings, но зависит от лончера; не приоритет |

---

## 10 лучших идей для Facet с приоритетом

1. **P0 — поиск по настройкам с синонимами** (RX Playa): 1 экран, 1 текстовое поле, словарь ключевых слов. Самый высокий эффект на строку кода.
2. **P0 — AMOLED-тумблер + «уменьшить blur/движение»** (Auxio, RX Playa): три `Boolean` в настройках, снимают главные жалобы на тёмную тему и jank.
3. **P0 — слайдер радиуса обложки 0–28 dp** (Gramophone): один `Int`, закрывает «квадрат/сквиркл/круг» без трёх тем.
4. **P0 — пустые состояния и пустая очередь с действием** (Auxio, Riff): текст + кнопка «Найти музыку»/«Включить трек»; сейчас это тупик.
5. **P0 — таймер сна: +5 мин и «до конца трека»** (Riff): два пункта в существующую шторку, реальный сценарий «ещё чуть-чуть».
6. **P1 — три раскладки плеера (Обложка / Full-bleed / Инфо)** (Poweramp, RX Playa): пресеты компоновки, а не новая навигация.
7. **P1 — боковая панель текста/очереди на планшете и в landscape** (RX Playa): адаптивность, которую конкуренты почти не делают.
8. **P1 — провайдеры текста со статусами + транслитерация** (RX Playa, Apple): у Facet три провайдера (LRCLIB, BetterLyrics, LyricsPlus) и поле `provider`, но в UI нет «кто ответил».
9. **P1 — экспорт/импорт темы в JSON + точечный сброс** (Poweramp, RX Playa): бэкап и перенос, без плагинов.
10. **P2 — два режима подсветки текста (градиент/слова) + бэк-вокал** (Riff, Oto): дифференциатор в тексте, когда база уже стабильна.

## 5 идей, которые не подходят для APK ≤40 МБ

1. **64-полосный параметрический EQ + 20 500 AutoEq-пресетов + лимитер** (Poweramp) — своя DSP-цепочка и база данных, это отдельный продукт.
2. **252 Milkdrop-визуализации с шейдерами до 120 fps** (Poweramp) — мегабайты ассетов, GPU-нагрузка и батарея.
3. **Три полноценные «перерождающие» темы + сторонние скины-APK** (Stellio, Poweramp) — несколько UI-словарей и API скинов; поддержка съест больше, чем даст.
4. **Animated cover art / Canvas / видео-обложки** (RX Playa) — видеодекод, трафик и кэш; при парсинге источников это ещё и хрупко.
5. **Мультибиблиотека WebDAV/SMB/Subsonic/Jellyfin с офлайн-кэшем** (Namida, RX Playa) — сетевые SDK, синхронизация и хранение; в 40 МБ не влезает без вырезания текущих фич.

## Что взять из RX Playa

RX Playa — форк Playa, GPL-3.0, Kotlin/Compose, самый близкий к Facet по стеку и лицензии. Брать стоит не экраны, а **механику настроек и плеера**:

- **Студия как набор групп с инлайн-контролами.** Liquid glass, акценты, переключатель стиля UI, Material You, кастомный акцент с reset, тема, шрифт, reduce motion, reduce blur, full-bleed, mesh gradient, animated covers — всё в одной секции, каждая строка с ключевыми словами для поиска ([SettingsSheet.kt:1053](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L1053)).
- **Поиск настроек с синонимами** — «glass/frost/blur/refract», «wallpaper/monet/dynamic/material you» ([там же:1058](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L1058)). Это дешёвый и очень заметный апгрейд.
- **Плеер: боковые панели на широком экране** — текст и очередь стоят колонкой рядом с плеером, «как сайдбар Apple Music» ([PlayerSidePanels.kt:25](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/sharedUi/src/jvmSharedMain/kotlin/com/rentalx/playa/ui/player/PlayerSidePanels.kt#L25)). Для Facet — `WindowWidthSizeClass.Expanded` и один и тот же composable.
- **Очередь: история проигранного + «Playing from»** ([PlayerQueue.kt:250](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/sharedUi/src/jvmSharedMain/kotlin/com/rentalx/playa/ui/player/PlayerQueue.kt#L250), [PlayerControls.kt:149](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/sharedUi/src/jvmSharedMain/kotlin/com/rentalx/playa/ui/player/PlayerControls.kt#L149)).
- **Текст: провайдеры со статусами, офсет, blur неактивных строк, приоритет syllable sync, порядок источников** ([LyricsProviderSheet.kt:55](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/sharedUi/src/jvmSharedMain/kotlin/com/rentalx/playa/ui/player/LyricsProviderSheet.kt#L55), [AppSettings.kt:742](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/data/settings/AppSettings.kt#L742)). У Facet уже есть база — брать именно прозрачность и настройки.
- **EQ: Tone-пад X/Y + 7 полос + пресеты** ([EqualizerScreen.kt:290](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/EqualizerScreen.kt#L290)) — самый дешёвый способ выглядеть «аудиофильским» без параметрического движка на 64 полосы.
- **Кроссфейд 0–12 с слайдером и sleep timer в меню трека** ([SettingsSheet.kt:892](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L892), [SongActionsMenu.kt:424](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/components/SongActionsMenu.kt#L424)).
- **Чего не брать:** bit-perfect/USB DAC/DSD, Automix, root-обход YouTube (zapret), TV/Wear-модули, Listen Together, плагины. Это либо не про Facet, либо резко увеличивает вес и риск.

## Темы/студия оформления: как сделать лучше конкурентов

Сейчас у Facet есть акцент, RGB-пикер, недавние цвета и dynamic cover colors. Конкуренты выигрывают не количеством цветов, а **предсказуемостью и переносимостью**. Предлагаю «Студию» из пяти блоков:

1. **Пресеты-персоны (4–5), а не 30 палитр.** «Тихая ночь» (AMOLED, монохром, тонкий шрифт), «Винил» (тёплый акцент, серифные заголовки, скругление 8), «Неон» (mesh из обложки, крупный радиус), «Бумага» (светлая, высокий контраст), «Система» (Material You). Пресет меняет 5–7 параметров сразу, но каждый остаётся редактируемым. У Retro — 3 темы и 15+ раскладок, у Stellio — «темы, которые перерождают интерфейс» ([retromusic.app](https://retromusic.app/), [stellio.ru](https://stellio.ru/)); Facet должен выиграть не числом, а связностью.
2. **Живой предпросмотр в самом экране.** Мини-карточка «плеер + мини-плеер + док» над настройками, обновляется мгновенно (как Riff, [theme.js:855](https://github.com/rootscripts/riff/blob/5031a840fb50dbb53a5830329ed69363fe09d824/src/renderer/modules/theme.js#L855)). Кнопка «Применить» не нужна; нужна «Вернуть» в snackbar.
3. **Ортогональные оси, а не темы-монолиты.** AMOLED (да/нет), контраст (обычный/высокий), радиус (0–28), обложка (квадрат/сквиркл/круг), движение (полное/уменьшенное), blur (полный/сниженный), фон (сплошной/mesh). Комбинации не конфликтуют; Poweramp подтверждает спрос на «Blended skin» и настройку силы Material You ([powerampapp.com](https://powerampapp.com/)).
4. **Экспорт/импорт темы в JSON + точечный сброс.** `schemaVersion`, миграции, предупреждение о незнакомых ключах — как у Poweramp. Это же даёт «поделиться темой» без плагинов и APK-скинов.
5. **Поиск и карта настроек.** Строка поиска с синонимами + счётчики изменённых параметров у секций («Оформление · 3») + «Сбросить раздел». RX Playa показывает, что поиск по настройкам реально работает ([SettingsSheet.kt:504](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/screens/SettingsSheet.kt#L504)).

Чего избегать: 17 схем + 17 pure-black дублей (Auxio) — это 34 темы и гарантированные рассинхроны; «перерождающих» интерфейс скинов (Stellio) — поддержка двух UI; снега и карусели (Retro) — характер ценой спокойствия. Сильная сторона Facet — MD3 Expressive, поэтому студия должна продавать **форму и движение**, а не косплей iOS.

## Serif vs Sans для крупных заголовков: аргументы и примеры

**Факты.** Facet использует Google Sans Flex (sans, вариативный) — [Type.kt](https://github.com/Okzxs/Facet/blob/main/app/src/main/java/com/facet/music/core/ui/theme/Type.kt). RX Playa держит две шкалы: SF Pro и Montserrat (геометрический sans) с трекингом −1.0 sp на display ([Theme.kt:170](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/app/src/main/java/com/rentalx/playa/ui/theme/Theme.kt#L170)). Apple держит серифный New York отдельно от SF Pro: шесть весов, кириллица и вариативные оптические размеры ([developer.apple.com/fonts](https://developer.apple.com/fonts/)). Второй пример — Noto Serif (OFL, кириллица, системный `serif` на Android, [fonts.google.com](https://fonts.google.com/noto/specimen/Noto+Serif)).

**Аргументы за sans в Facet:**
- кириллица: у вариативного Google Sans Flex предсказуемые «Д», «Ж», «Щ»; у серифов кириллица проработана неровно, и это видно на крупном кегле;
- плотность: sans позволяет 34–40 sp с line-height 1.1 без «частокола» засечек на тёмном фоне;
- вес: одна вариативная гарнитура вместо двух экономит 150–400 КБ и одну шкалу;
- динамический цвет: тонкие засечки теряют контраст на тонированных поверхностях и в AMOLED.

**Аргументы за сериф — точечно:**
- «альбомный» характер: крупный заголовок Home/плейлиста серифом читается как буклет, а не как дашборд;
- контраст с интерфейсом: если весь UI — Google Sans Flex, один серифный display-слот создаёт иерархию без новых цветов;
- примеры: New York (Apple) и Noto Serif (Android/OFL); в плеерах сериф почти не встречается — это свободная ниша, но и риск.

**Рекомендация.** Оставить sans глобально; добавить **один** серифный display-слот (Hero на Home и заголовок плейлиста) только в пресете «Винил», с проверкой на: «Сплин», «Мумий Тролль», «Дельфин», верхний регистр, 2 строки, 1.3× fontScale. Не использовать сериф ниже 24 sp, в кнопках, в тексте песни и в AMOLED-пресете. Перед релизом — замер высоты строки и обрезки на 360 dp.

## Лицензии: что можно переносить, а что только как идею

| Источник | Лицензия | Что это значит для Facet (GPLv3) |
|---|---|---|
| RX Playa | GPL-3.0 ([LICENSE](https://github.com/rx-playa-team/rx-playa/blob/bc01bda862cb4dcbb47dad64b32d7a5d2c5bb4d6/LICENSE)) | Код совместим: можно портировать фрагменты при сохранении GPLv3, copyright-заголовков и указании источника. Идеи — без ограничений. |
| Auxio | GPL-3.0 ([LICENSE](https://github.com/OxygenCobalt/Auxio/blob/9e18f5fe660a76e0df91194a2b1bf7bba1952aee/LICENSE)) | То же; файлы Auxio несут GPL-заголовки — при переносе их нельзя вырезать. |
| Gramophone | GPL-3.0 ([LICENSE](https://github.com/FoedusProgramme/Gramophone/blob/f8eb02d9f92e98c4876dbfed7519948752765cd3/LICENSE)) | То же. |
| Retro Music | GPL-3.0 ([LICENSE.md](https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/41af88df479c9571592327d0ccc53901f6a67b6e/LICENSE.md)) | То же. |
| MetroMusic | GPLv3 ([LICENSE](https://github.com/Diffechento/MetroMusic/blob/68f939863d32ce450c64c303c3948b9782691990/LICENSE)) | То же. |
| Riff | MIT ([LICENSE](https://github.com/rootscripts/riff/blob/5031a840fb50dbb53a5830329ed69363fe09d824/LICENSE)) | Можно брать код с сохранением copyright notice; GPL-совместимо. |
| Namida | EULA, «source available» ([LICENSE](https://github.com/namidaco/namida/blob/436feae5be45d92bebcdf66cd9e04e613cd7782c/LICENSE)) | Код переносить нельзя; только идеи и наблюдения. |
| Poweramp, Stellio, Oto, Samsung, Spotify, Apple Music, YouTube Music | Проприетарные | Только идеи/паттерны; декомпиляция и копирование ресурсов исключены. |

Отдельно: шрифты и иконки имеют свою лицензию. Montserrat — SIL OFL, Google Sans Flex — OFL (проверять при обновлении), Simple Icons — CC0. Серифную гарнитуру для пресета нужно выбирать с OFL/Apache и кириллицей (например, Noto Serif), иначе — только системный `serif`.

## Пробелы и «не подтверждено»

- **Dotify.** Публичного репозитория нет: поиск по GitHub (`dotify in:name language:dart`, `dotify flutter`) дал только нерелевантный `GeorgeIkeda3/Dotify_Flutter` (0 звёзд, без описания); приложение закрытое и распространяется APK/Telegram ([гайд](https://aurum2347.gitbook.io/info/tools/dotify-muzykalnyi-pleer)). Паттерны из внутренней заметки Facet (секции очереди, `player.sleep_timer`, «Playing from», кастомизация мини-плеера) помечены как «по реверсу», публично не подтверждены.
- **Samsung Music.** Публичной страницы с описанием кастомизации не нашлось; в отчёт не включён, чтобы не выдумывать функции.
- **YouTube Music.** Подтверждены только «Song lyrics so you can sing along» и mood-радио по [Google Play](https://play.google.com/store/apps/details?id=com.google.android.apps.youtube.music). Детали очереди/настроек плеера — не подтверждены.
- **Spotify.** Кроссфейд, Automix, Gapless и нормализация — [tracks-transitions](https://support.spotify.com/us/article/tracks-transitions/); очередь «Next in queue» — [now-playing](https://support.spotify.com/us/article/now-playing/); текст — [lyrics](https://support.spotify.com/us/article/lyrics/).
- **Apple Music.** Строчная синхронизация, перевод/произношение и «Larger Text» для текста — [Support 105076](https://support.apple.com/en-us/105076).
- **Oto Music.** «Lyrics light up word by word, with background vocals tucked under the line» и «Editorial Blur theme» — [Google Play](https://play.google.com/store/apps/details?id=com.piyush.music).
- **Stellio.** «Темы, которые не просто меняют цвет, а полностью перерождают интерфейс» — [stellio.ru](https://stellio.ru/).
- **Poweramp.** Три раскладки, скины 15–17 опций, экспорт/импорт JSON, Blended skin, 252 визуализации, 4 виджета + Flip — [powerampapp.com](https://powerampapp.com/).

## Источники (клоны в /tmp, не в проекте)

- RX Playa — `bc01bda` — https://github.com/rx-playa-team/rx-playa (форк Playa, GPL-3.0)
- Auxio — `9e18f5f` — https://github.com/OxygenCobalt/Auxio (GPL-3.0)
- Gramophone — `f8eb02d` — https://github.com/FoedusProgramme/Gramophone (GPL-3.0)
- Retro Music Player — `41af88d` — https://github.com/RetroMusicPlayer/RetroMusicPlayer (GPL-3.0)
- MetroMusic — `68f9398` — https://github.com/Diffechento/MetroMusic (GPLv3)
- Namida — `436feae` — https://github.com/namidaco/namida (EULA, source available)
- Riff — `5031a84` — https://github.com/rootscripts/riff (MIT)
- Dotify — публичного репозитория нет; закрытое приложение (APK/Telegram)
- Poweramp — https://powerampapp.com/ · Oto Music — https://play.google.com/store/apps/details?id=com.piyush.music · Stellio — https://stellio.ru/ · Spotify — https://support.spotify.com/ · Apple Music — https://support.apple.com/en-us/105076 · YouTube Music — https://play.google.com/store/apps/details?id=com.google.android.apps.youtube.music
