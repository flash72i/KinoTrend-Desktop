# KinoTrend Desktop — план проекта

Десктопная версия KinoTrend 2.3.8 (Kotlin + Compose Desktop), визуально и
функционально повторяющая Android-приложение.

Отчёт по отзывчивости интерфейса и разбор узкого места «клик по Смотреть →
запуск плеера» — `../docs/AUDIT-DESKTOP-PERF.md`. Стенд для перепроверки —
`src/test/kotlin/app/kinotrend/desktop/bench/`.

## Структура (план)

```
desktop/
  build.gradle.kts, settings.gradle.kts, gradle wrapper
  src/main/kotlin/app/kinotrend/desktop/
    Main.kt
    core/        AppPaths, Settings, Cache
    data/        CatalogApi, Models, MirrorRepository, ImageCache
    playback/    PlayerController, StreamResolver
    ui/          App, AppModel, theme
    ui/screens/  Home, Details, Torrents, Player, Search,
                 Filters, Settings, About
  src/main/resources/  иконки, шрифты
```

## Этапы

1. Каркас Compose Desktop, тема, окно.
2. Каталог: загрузка `data.json`, кэш 2 часа, переключение зеркал.
3. Главный экран: постеры, ряды, поиск.
4. Карточка фильма и список раздач/качеств.
5. Плеер: встроенный (VLCJ/JavaFX) после динамического анализа APK.
6. Настройки, «О программе», обновления.
7. Установщик на базе C#-шаблона, отдельная тема.

## Источник данных

- `http://kino-trend.ru/kinotrend/data.json`
- зеркало: `http://nemiroff.insomnia247.nl/kinotrend/data.json`
- версия: `http://kino-trend.ru/version.json`

Схема и детали — в `docs/REPORT.md`.
