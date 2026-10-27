# Проверка реализации 0.1.0/code1

25 октября 2026. Package `org.wayfarer.atlas`, minSDK26, target/compileSDK36. Перечислены фактически исполненные проверки; публикация Google Play не выполнена.

## Сборка

Изолированная среда JDK17/Gradle8.13/SDK36. Выполнено через SOCKS fallback wrapper с локальным окружением подписи:

```sh
python3 scripts/with-signing.py bash scripts/gradle.sh testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease bundleRelease
```

**BUILD SUCCESSFUL**, код0. Полная сборка и повтор после отключения языковых splits успешны. RU/EN остаются вместе; предупреждение AppBundleLocaleChanges устранено.

| Проверка | Фактический результат |
| --- | --- |
| Домен: категории, сравнение, покрытие, строгие даты, пересечения | 16 PASS |
| Изменения напоминаний | 5 PASS |
| Файловый журнал: roundtrip, изменения, повреждения, пределы | 12 PASS |
| Парсер справочника и настоящих assets | 14 PASS |
| Политика обновления: полнота, старые/неизвестные даты | 6 PASS |
| Выбор cache/bundle и fallback | 7 PASS |
| Native community adapter | 9 PASS; полная Python/native parity38 612 строк |
| Python-нормализатор | 9 PASS |
| Gradle JVM | 7 suites, 7 wrapper tests, 0 failures/errors/skips |
| Android UI, Pixel7 / Android36 ARM64 AOSP ATD | 9 сценариев PASS |
| lint debug/release | Каждый: 0 ошибок, 16 предупреждений |

Итого **78 поведенческих проверок**:69 Java+9 Python. Семь JVM wrapper tests вызывают Java-сценарии выше и повторно в сумму не включаются. Отдельный screenshot AndroidTest запускается явно с captureScreenshots=true; это инструмент экспорта, а не десятый функциональный сценарий.

UI-проверки охватывают навигацию/справочник, UNKNOWN, доступные гражданства, общий знаменатель рейтинга, журнал, восстановление черновика после пересоздания Activity, подпись переключателя напоминания и реальный отказ Android в уведомлениях с сохранением reminder=false. Найденные дефекты воспроизведены до исправления и проверены повторно.

Lint предупреждает о предлагаемых обновлениях зависимостей/Gradle, KTX/apply и localeConfig для API33+. Commit метки загрузки исполняется в IO. Старый Android игнорирует localeConfig, собственный RU/EN переключатель работает. Общий baseline не использован. Полные локальные отчёты: `app/build/reports/lint-results-{debug,release}.html`, `app/build/test-results/testDebugUnitTest/`.

## Runtime и данные

- Холодный запуск без сети показывает реальные встроенные правила и карту. US:150 доступных, покрытие196/249; RU:109, покрытие196/249. Это показатели конкретного среза.
- Явное онлайн-обновление на Android успешно загрузило общий JSON GitHub, сохранило38 612 правил и метку загрузки; дата исходного среза осталась2026-08-10.
- Сверены значения набора: US→FR без визы90, RU→TH без визы60, RU→JP виза требуется. Это не самостоятельная актуальная визовая консультация.
- Python/native категории, сроки, даты, URL и примечания совпадают для всех38 612 строк. Дополнительно desktop Java full normalize проходит с heap128MiB.
- Визовые origin/destination codes197; общий справочник250кодов. Отсутствующие пары UNKNOWN. Только38 индивидуальных дат проверки,177 официальных ссылок.
- Атомарность журнала, сохранение повреждённого оригинала, общий mutex загрузки/очистки и завершение записи при уничтожении Activity проверены доменными/файловыми сценариями и review. Изменение заметки не ставит доставленное напоминание повторно.

## Проверка подписанной release-версии

Именно release APK установлен в чистом тестовом эмуляторе. Offline cold start показывает встроенную матрицу. Синтетическая поездка сохраняется после force-stop и повторного запуска. Для поездки с началом сегодня через системный диалог разрешены уведомления; WorkManager фактически доставил NotificationRecord в канале trip-reminders, importance3, visibility PRIVATE. После отдельной повторной доставки поездка удалена через подтверждение UI без промежуточного force-stop: журнал пуст, активных уведомлений приложения0. Тестовые записи удалены. Онлайн-обновление именно release также PASS: source updated2026-08-10, downloadedAt2026-10-25T19:57:19.422598Z. Ссылка на первоисточник открыла системный выбор браузера. APK SHA-256 eab6088d6f97bda066fb31c876b87da4e80cefae3d35ebe17a843489f27b255a совпадает с передаваемым файлом. Это проверка реальной доставки/отмены без изменения часов или production-кода; тестовый эмулятор после проверки остановлен.

## Артефакты

Debug APK20 092 390байт; release APK2 250 977байт; AAB4 376 366байт.

- APK: apksigner PASS, v2 signature, RSA4096.
- AAB: jarsigner PASS; официальный bundletool1.18.1 validate PASS; LANGUAGE negate:true, PAGE_ALIGNMENT_16K. [Отчёт](release/bundle-validation.md).
- ZIP: zipalign -c -P16 4 PASS.
- Все4ABI libandroidx.graphics.path.so имеют LOAD alignment16384; RELRO безопасен: отдельные LOAD-сегменты не перекрывают последующие writable данные при округлении до16KB. Простая сумма RELRO address+size здесь не кратна16KB; применён отдельный LOAD случай из [документации Google](https://developer.android.com/guide/practices/page-sizes).
- Повтор: `python3 scripts/verify-artifacts.py`; [результаты и хеши](release/artifact-verification.json).
- Публичный пакет: `scripts/package-release.py`, исключает инструменты, build/dist, release-private, ключи, local.properties и symlinks.

Jarsigner предупреждает о самоподписанном upload certificate, отсутствии timestamp и порядке manifest в AGP ZIP. Проверка подписи через JarFile и официальный bundletool проходят. Play Console в эту проверку не входит.

## Приватность и визуальные материалы

Итоговый manifest содержит INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS и служебные WorkManager permissions. Нет камеры, location, общего хранилища, account permissions, рекламного ID или billing. Исходники/зависимости не содержат auth, analytics, рекламы или crash-reporting SDK. allowBackup=false; правила cloud backup/device transfer исключают данные приложения. Физическое/OEM устройство ещё требует проверки владельцем.

Обновление использует общий HTTPS URL: redirects запрещены, размер/хост ограничены, пользовательские параметры отсутствуют. Поездки, заметки и гражданство не включаются. Live refresh исполнен; отдельный packet-capture аудит трафика не проводился. GitHub видит IP/время и HTTP metadata: политика/Data safety должны это учитывать.

Восемь реальных native captures1080×1920 RU/EN сняты Compose captureToImage с MainActivity и настоящими данными. Журнал при съёмке пуст. Экспорт24-bit RGB удаляет только полностью непрозрачный alpha; RGB пиксели проверены неизменными. PNG, SHA-256 и metadata: `release/store-assets/screenshots/`. Иконка512RGBA, feature graphics1024×500RGB и5оригинальных схем Console просмотрены. Схемы подписаны и не выдаются за скриншоты Google.

## Границы и действия владельца

Runtime проверен на одном API36 ARM64 эмуляторе. Физическое устройство, API26, устройство с16KB страницами, полный TalkBack и все OEM-режимы энергосбережения не испытаны. Точное время напоминания не обещается; многодневное ожидание не выполнялось.

Нужны имя издателя, рабочий email и страна; HTTPS policy, завершение Data safety/Play Console, физическое устройство, требуемый тестовый трек и проверка Google. Community-данные не позволяют обещать официальную актуальность всех направлений. Подготовка файлов не означает публикацию.
