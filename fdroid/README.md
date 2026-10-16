# Сборка Sirin Music для F-Droid

В версии 2.0.2 APK обновлён под требования F-Droid. Отключён зашифрованный блок Google Play со сведениями о зависимостях, убрана автоматическая загрузка JDK через Foojay. Версия записана в исходниках и одинакова для локальной сборки и GitHub Actions.

Автор в метаданных — **RaineDev**, пакет приложения — `ru.rainedev.sirinmusic`.

## Файлы

| Путь | Содержимое |
| --- | --- |
| `fastlane/metadata/android/ru-RU/` | Русское описание, история изменений, значок и скриншоты |
| `fastlane/metadata/android/en-US/` | Английское описание и история изменений |
| `fdroid/ru.rainedev.sirinmusic.yml` | Рецепт сборки для F-Droid |

В `fastlane` лежат обычные текстовые файлы и изображения; устанавливать Fastlane для их чтения не нужно. Файл `changelogs/5.txt` относится к коду версии `5`.

## Инструменты сборки

Нужны JDK 21 для Gradle, JDK 17 для компиляции, Android SDK Platform 37.0 и Build Tools 37.0.0. Версии Gradle, Android Gradle Plugin и библиотек закреплены в исходниках.

Без переменных ключа подписи команда создаёт неподписанный APK:

```sh
./gradlew :app:assembleRelease
```

Официальные APK собирает и публикует workflow **Android Release** в GitHub Actions. Он сверяет тег с `versionName` в `app/build.gradle.kts`. Код версии не зависит от номера запуска workflow.

## Подпись и воспроизводимость

Рецепт использует ту же подпись, что и GitHub Releases. Поле `Binaries` указывает на официальный APK, `AllowedAPKSigningKeys` — на SHA-256 его сертификата. Приватный ключ в рецепт не входит.

Воспроизводимость означает, что независимая сборка тех же исходников даёт одинаковый APK. Для проверки берётся официальный APK и неподписанная сборка из того же тега:

```sh
apksigcopier compare --unsigned SirinMusic-2.0.2.apk app-release-unsigned.apk
```

Команда переносит подпись официального APK на пересобранный и проверяет её. Так проверяется совпадение всего APK, включая структуру ZIP. Сравнение только распакованных файлов не даёт такой проверки.

Для сборки используется обычный Git-клон. В проверенном окружении Android Gradle Plugin не прочитал Git revision из worktree и записал другое содержимое `META-INF/version-control-info.textproto`, из-за чего APK отличался от CI.

## Проверка рецепта

В репозитории [fdroiddata](https://gitlab.com/fdroid/fdroiddata) файл рецепта размещается в `metadata/ru.rainedev.sirinmusic.yml`. Описание и изображения берутся из исходников приложения.

```sh
fdroid rewritemeta ru.rainedev.sirinmusic
fdroid lint ru.rainedev.sirinmusic
fdroid checkupdates --allow-dirty ru.rainedev.sirinmusic
fdroid build --server ru.rainedev.sirinmusic:5
```

`fdroid lint` проверяет метаданные. Сборка в окружении F-Droid отдельно проверяет рецепт, доступность инструментов и совпадение с официальным APK.

## Сеть и обновления

Для прослушивания используется сервер [musik](https://github.com/torwin-job/musik), адрес которого задаётся в настройках. Обязательного публичного сервера нет. Google Play Services и Firebase приложению не нужны.

Встроенная проверка обновлений обращается к GitHub API и загружает APK из GitHub Releases. Автоматические проверки по умолчанию выключены. Их можно оставить выключенными и получать обновления через другой источник с той же подписью.

## Документация F-Droid

- [Формат рецепта сборки](https://f-droid.org/docs/Build_Metadata_Reference/)
- [Воспроизводимые сборки и подпись разработчика](https://f-droid.org/docs/Reproducible_Builds/)
