# Автоматические сборки GitHub / GitHub builds

GitHub Actions запускает workflow [Android](../.github/workflows/android.yml) после каждого push в `main`, для pull request и вручную через **Actions → Android → Run workflow**. Workflow не требует пользовательских secrets или ключа подписи.

## Проверки

На `ubuntu-24.04` устанавливаются Temurin JDK 17, Android SDK 36 и Build Tools 36.0.0. Gradle Wrapper использует закреплённый Gradle 8.13 с проверкой SHA-256 дистрибутива. Actions закреплены полными commit SHA; `setup-gradle` также проверяет wrapper JAR.

Первое задание синхронизирует справочные assets и проверяет отсутствие расхождений с файлами в Git, запускает Python-тесты данных, JVM-тесты, `lintDebug`, `lintRelease` и собирает debug APK, release APK и release AAB. Тесты используют сохранённые в репозитории данные: доступность внешнего визового сервиса не влияет на их результат.

Второе задание запускает Android-тесты на эмуляторе API 35, Google APIs, x86_64 с аппаратным ускорением KVM. Оно проверяет интерфейс, первое предупреждение, официальный источник Гонконга, журнал и разрешение уведомлений. Отдельный тест создания магазинных скриншотов пропускается при обычном запуске; это не функциональная проверка.

Зелёный статус относится к конкретному commit и обоим заданиям. Локальная проверка workflow не заменяет успешный запуск в GitHub. Прохождение API 35 не доказывает совместимость со всеми устройствами Android или отдельную проверку устройств с размером страницы памяти 16 КБ.

## Скачать результат

Откройте **Actions → Android → нужный запуск → Artifacts**. GitHub может потребовать вход в аккаунт для скачивания CI artifacts. Бинарные файлы и контрольные суммы хранятся 30 дней, отчёты — 14 дней.

| Файл в `wayfarer-atlas-build-<commit>` | Назначение |
| --- | --- |
| `wayfarer-atlas-<version>-debug.apk` | Для тестов. Подписан временным debug-ключом runner; не обновляет опубликованный подписанный релиз. |
| `wayfarer-atlas-<version>-unsigned-release.apk` | Оптимизированная сборка без подписи. Перед установкой и распространением требуется подпись ключом издателя. |
| `wayfarer-atlas-<version>-unsigned-release.aab` | Bundle без подписи. Перед загрузкой в Play Console требуется подпись upload-ключом издателя. |
| `SHA256SUMS.txt`, `manifest.json` | Размеры, SHA-256, версия, commit и состояние подписи. |

Готовые для установки публичные APK находятся в разделе **Releases** репозитория. Они подписываются постоянным ключом издателя вне Actions. Автоматическая проверка не создаёт публичный GitHub Release и не публикует приложение в Google Play.

Результаты JVM-тестов и lint доступны в artifact `wayfarer-atlas-reports-<commit>`, Android-тестов — в `wayfarer-atlas-android-tests-<commit>`. При ошибке сборки отчёты сохраняются по возможности.

## Локально

Нужны JDK 17 и Android SDK с `platforms;android-36` и `build-tools;36.0.0`. Укажите `ANDROID_HOME` и выполните из корня приложения:

```sh
python3 scripts/sync-assets.py
python3 -m unittest discover -s data/tests -v
bash ./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease bundleRelease
```

Чтобы собрать unsigned-артефакты как в CI, выполняйте сборку без переменных `WAYFARER_KEYSTORE`, `WAYFARER_STORE_PASSWORD`, `WAYFARER_KEY_ALIAS`, `WAYFARER_KEY_PASSWORD`, затем запустите:

```sh
python3 scripts/ci-stage-artifacts.py
```

Для интерфейсных тестов запустите эмулятор API 35 или новее и выполните `bash ./gradlew connectedDebugAndroidTest`. Не передавайте upload-ключ, пароли и локальные файлы `release-private/` в pull request или artifacts.

## English

The **Android** workflow runs on pushes to `main`, pull requests and manual dispatch. It validates the Gradle wrapper, checks bundled reference assets, runs Python/JVM tests and Android lint, builds APK/AAB files, then runs functional Android tests on an API 35 emulator. It needs no publisher credentials.

Open **Actions → Android → a run → Artifacts** to download builds and reports. The debug APK uses the runner's temporary debug key. The release APK/AAB are explicitly **unsigned** and need the publisher's existing key before distribution or Play submission. Download installable, permanently signed builds from **Releases**. The workflow does not automatically publish releases or upload to Google Play.

Artifacts include checksums and a manifest with the source commit and signing status. Build artifacts expire after 30 days; reports after 14 days. A successful run verifies that commit on the configured runner and emulator, not every Android device.
