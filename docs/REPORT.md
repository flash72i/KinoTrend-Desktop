# KinoTrend 2.3.8 — отчёт по реверс-инжинирингу

Дата: 2026-11-18. APK: `apk/KinoTrend 2.3.8.apk`.

## 1. Общие данные

| Параметр | Значение |
|---|---|
| Пакет | `ru.tiardev.kinotrend` |
| Версия | 2.3.8 (versionCode 238) |
| minSdk / targetSdk | 21 / 33 |
| Точка входа | `ru.tiardev.kinotrend.ui.FirstLaunch` |
| Размер APK | 3,76 МБ, 1522 записи, один `classes.dex` |
| Нативные библиотеки | отсутствуют |
| Обфускация | есть (jadx: 4652 файла, 30 ошибок декомпиляции) |

Разрешённые permissions (кратко): INTERNET, POST_NOTIFICATIONS, RECORD_AUDIO,
WRITE/READ_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE, RECEIVE_BOOT_COMPLETED,
REQUEST_INSTALL_PACKAGES, FOREGROUND_SERVICE, ACCESS_NETWORK_STATE, WAKE_LOCK,
AD_ID, TV EPG.

## 2. Технологии

- Android Views + AppCompat, Leanback для TV; XML-разметки (`res/layout*`).
- Media3 ExoPlayer (`androidx.media3.ui.PlayerView`) — встроенный плеер.
- Room, WorkManager, Coil (изображения), Firebase Analytics/Crashlytics.
- Сетевой слой: собственный HTTP-клиент (OkHttp-подобный `q9`, `z7`),
  ответы разбираются через `org.json`.
- UI: мобильные Activity + TV-фрагменты; Compose не используется.

## 3. Экраны и компоненты

Мобильные:

- `FirstLaunch` — стартовый экран, проверка обновления/данных;
- `ui.mobile.MainActivity` — главный экран каталога;
- `ui.mobile.DetailActivity` — карточка фильма;
- `ui.mobile.SearchMobileActivity` — поиск;
- `TorrentDetailsActivity` — раздачи/качества;
- `PlayerActivity` — встроенный плеер (Media3);
- `SettingsActivity`, `AboutActivity`.

TV (Leanback):

- `ui.tv.VideoDetailsActivity` / `VideoDetailsFragment`;
- `ui.tv.VerticalGridActivity` / `VerticalGridFragment`;
- `ui.tv.SearchActivity` / `SearchFragment`;
- `ui.tv.FilterActivity` — фильтры;
- `SettingsActivity` с TV-темой.

Сервисы: `UpdateRecommendationsService`, `BootCompletedReceiver`, `KTProvider`
(content provider для рекомендаций).

## 4. Данные и API

Адреса были закодированы Base64 в `wb/a.java`:

| Назначение | URL |
|---|---|
| Каталог (основной) | `http://kino-trend.ru/kinotrend/data.json` |
| Каталог (зеркало) | `http://nemiroff.insomnia247.nl/kinotrend/data.json` |
| Проверка версии | `http://kino-trend.ru/version.json` |

Логика (`wb.c.N()`):

- в SharedPreferences хранятся `KTMovieList`, `KTNextUpdate`, `KTUseMirror`;
- каталог кэшируется на 2 часа; при истечении — загрузка с сервера;
- переключатель зеркала: основной или зеркальный URL;
- JSON: `{ "count": N, "movies": [ ... ] }`, 144 фильма в текущей выдаче.

Поля фильма: `filmID`, `nameRU`, `nameOriginal`, `description`, `year`,
`country`, `genre`, `rating*`, `posterURL`, `bigPosterURL`, `trailerURL`,
`filmLength`, `torrents[]`.

Поля раздачи: `name`, `link`, `magnet`, `date`, `type`, `audio`, `size`,
`seeders`, `leechers`, `quality`, `dv`, `license`; в `KTorrent` также
`fileLink`, `resolution`, `hdr`, `HEVC`, `dolbyVision`, `peers`.

Обновление: `version.json` (`version`, `short_version`, `download_url`,
`release_notes`, `forced_install`), скачивание APK и установка через
FileProvider.

## 5. Воспроизведение

- `PlayerActivity` принимает URL через Intent (`getIntent().getDataString()`)
  и играет через Media3 ExoPlayer + `PlayerView`.
- Встроенного торрент-движка в APK нет (нет libtorrent/webtorrent).
- Раздачи содержат `magnet` и `fileLink`; фактический источник
  воспроизведения нужно подтвердить динамическим анализом (какой именно URL
  передаётся в PlayerActivity: прямой HTTP-поток или локальный торрент-сервер).
- Есть поддержка аудиодорожек/качеств, seek, полноэкранный режим.

## 6. Чувствительные данные

В `res/values/strings.xml` лежат Firebase-ключи (`google_api_key`,
`google_crash_reporting_api_key`) и bucket `kinotrend-crash`. Публиковать их
не следует.

## 7. Что дальше для десктопной версии

1. Динамический анализ: запустить APK в эмуляторе, снять трафик, подтвердить
   источник видеопотока и формат (`fileLink`/magnet/промежуточный сервис).
2. Снять экранные состояния: главная, карточка, раздачи, плеер, настройки,
   поиск, фильтры — как эталон для 1:1 вёрстки.
3. Десктоп на Kotlin + Compose Desktop: каталог из `data.json` с кэшем 2 ч,
   зеркала, поиск/фильтры, карточка, раздачи.
4. Плеер: Media3 недоступен на десктопе — варианты VLCJ (libVLC) или JavaFX
   Media; решение принять после п.1.
5. Установщик — отдельный, на базе нашего C#-шаблона, с другой темой.

## 8. Структура папки

```
KinoTrend 2.3.8/
  apk/                исходный APK
  re/jadx/            Java-декомпиляция jadx
  re/apktool/         ресурсы, манифест, smali
  re/data/            скачанные data.json и version.json
  docs/REPORT.md      этот отчёт
  desktop/            будущий десктопный проект (план в README)
```
