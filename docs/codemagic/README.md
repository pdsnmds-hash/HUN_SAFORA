# Safora: подпись и AAB в Codemagic

Проверено по официальной документации 26.10.2026. Подготовлены примеры конфигурации; облачный запуск в аккаунте пользователя не выполнялся.

## 1. Что создаётся

| Объект | Смысл |
|---|---|
| `*.jks` / keystore | Файл-контейнер с приватным ключом и сертификатом |
| Upload key | Ключ внутри keystore, которым подписывается AAB перед загрузкой в Play Console |
| `upload` / alias | Имя записи внутри контейнера, не имя приложения |
| `upload_certificate.pem` | Публичный сертификат upload key, не приватный ключ и не замена JKS |
| App signing key | Ключ, которым Google Play подписывает APK для пользователей при Play App Signing |
| `safora_upload` / Reference name | Внутреннее имя загруженного keystore в Codemagic, на которое ссылается YAML |

Создание keystore с keytool одновременно создаёт ключ. Отдельно «после keystore генерировать upload key» не требуется. Роль upload key закрепляется при настройке Play App Signing/загрузке подписанного AAB. Для новых приложений можно оставить создание App signing key Google Play.

Источник: https://developer.android.com/studio/publish/app-signing

## 2. Выберите исходную ситуацию

### Safora с уже подготовленным ключом

Нужные файлы уже есть:

- `release/signing/upload-keystore.jks` — загружается в Codemagic.
- `release/signing/upload-key.properties` — содержит storePassword, keyAlias, keyPassword. Открывать только локально; содержимое не коммитить и не отправлять в чат.
- alias: `upload`.
- `release/upload_certificate.pem` — публичный сертификат.
- package: `hu.safora.documentstudio`.

Новый ключ не нужен для переноса сборки в CI. Загрузите существующий в Code signing identities по разделу 5. Это сохранит сертификат уже подготовленных AAB.

Если AAB уже зарегистрирован в Play Console, заменять upload key произвольным новым нельзя: новый ключ требует процедуры сброса/регистрации upload key в Play Console. Сначала определите текущую зарегистрированную подпись. Сам переход на Codemagic её не меняет.

### Новое приложение без upload key

Один раз выполните раздел 4 на машине Codemagic, скачайте JKS и сертификат, сохраните пароли. Затем разделы 5–8. Для каждого приложения используйте свой файл и Reference name; для следующих версий этого приложения используйте тот же ключ.

## 3. Подключите исходники

Codemagic собирает исходники из Git-репозитория, а не из локальной папки Mac. Сейчас этот каталог не является Git-репозиторием.

1. Создайте приватный репозиторий GitHub, GitLab или Bitbucket.
2. Для Safora сохраните в корне репозитория build.gradle.kts, settings.gradle.kts, gradle.properties, gradlew, gradlew.bat, gradle/, app/.
3. Добавьте каталог `docs/codemagic/` из этого проекта.
4. Для обычной сборки скопируйте `docs/codemagic/codemagic.yaml` в корневой `codemagic.yaml`. Для одноразовой генерации нового ключа используйте вместо него `generate-upload-key.yaml` из раздела 4.
5. Не добавляйте local.properties, release/signing, JKS, keystore, каталоги build, готовые APK/AAB, skill-evals и архив всего проекта с приватными файлами. Для сборки они в Git не нужны. Текущий .gitignore уже исключает основные приватные материалы.

Пример первого коммита из корня проекта (не выполнялся автоматически):

```sh
git init
git branch -M main
git add .gitignore build.gradle.kts settings.gradle.kts gradle.properties gradlew gradlew.bat gradle app/build.gradle.kts app/proguard-rules.pro app/src docs/codemagic codemagic.yaml
git diff --cached --name-only
git commit -m "Configure Android build for Codemagic"
git remote add origin git@github.com:YOUR_ACCOUNT/HUN_SAFORA.git
git push -u origin main
```

Замените URL своим. Команды предполагают, что GitHub-аутентификация на Mac уже настроена. Перед commit проверьте перечень файлов: никаких JKS и upload-key.properties.

В Codemagic:

1. Applications → Add application.
2. Выберите свою оплачиваемую Team, где у вас роль Admin.
3. Подключите Git-провайдера и разрешите доступ к нужному репозиторию.
4. Выберите репозиторий и тип проекта Android / Android native.
5. Нажмите Finish: Add application.
6. Выберите ветку с корневым codemagic.yaml; при необходимости нажмите Check for configuration file.

Для Kotlin/Compose используется YAML, не Flutter workflow editor.

Источники:
- https://docs.codemagic.io/getting-started/adding-apps/
- https://docs.codemagic.io/yaml-basic-configuration/yaml-getting-started/

## 4. Создать новый ключ именно на Codemagic — один раз

Это наш отдельный workflow с keytool, а не кнопка автоматического создания Android-ключей в UI. Документированный Codemagic путь управления Android keystores — загрузка существующего файла; генерацию ниже выполняет Java keytool на build-машине.

Для существующего Safora-ключа этот раздел пропустите.

### 4.1. Создайте секреты

1. Откройте добавленное приложение в Codemagic.
2. Перейдите в Environment variables.
3. Создайте группу `safora_key_generation`.
4. Добавьте `NEW_KEYSTORE_PASSWORD`: пароль контейнера; включите Secret.
5. Добавьте `NEW_KEY_PASSWORD`: пароль ключа; включите Secret.
6. Сохраните оба пароля в своём менеджере паролей. Можно задать одинаковые значения обоим полям.

Пароли не записываются в YAML. Обычные сборки потом получают пароли из Code signing identities автоматически.

### 4.2. Установите одноразовый YAML

Из корня проекта:

```sh
cp docs/codemagic/generate-upload-key.yaml codemagic.yaml
git add codemagic.yaml
git commit -m "Add one-time upload key generation workflow"
git push
```

Для нового приложения замените в примере:

- workflow id/name;
- группу `safora_key_generation`;
- `KEYSTORE_NAME: safora-upload.jks`;
- KEY_SUBJECT — свои данные разработчика/организации.

Alias `upload` можно оставить одинаковым в разных независимых keystore.

Workflow использует Linux X2, Java 17 и выполняет keytool -genkeypair: JKS, RSA 4096, SHA256withRSA, validity 10000 дней. Затем экспортирует публичный сертификат. Приватные пароли передаются keytool через переменные окружения с `-storepass:env` / `-keypass:env` и не выводятся скриптом.

### 4.3. Запустите и скачайте

1. Start new build.
2. Выберите ветку с одноразовым YAML.
3. Выберите `safora-create-upload-key-once` / Safora - ONE-TIME create upload key.
4. Нажмите Start new build и дождитесь успеха.
5. В артефактах завершённого билда скачайте `safora-upload.jks` и `upload_certificate.pem`.
6. Сохраните JKS с резервной копией; пароли — в менеджере паролей.

В этом единственном workflow JKS специально возвращается приватным артефактом, чтобы вы могли забрать созданный ключ. Он секретный: не публикуйте build/artifact links и не добавляйте файл в репозиторий. PEM публичный. Обычный AAB-workflow JKS в артефакты не включает.

После успешной генерации замените корневой YAML обычным workflow из раздела 6. Не запускайте генератор для каждой версии: это создаст другой ключ, даже если имя файла и alias прежние.

Источники:
- https://docs.codemagic.io/yaml-code-signing/signing-android/
- https://docs.codemagic.io/yaml-basic-configuration/configuring-environment-variables/

## 5. Загрузите JKS в Code signing identities

Этот шаг одинаков для старого локального ключа и нового, созданного в разделе 4.

1. Откройте Team settings той же Team, к которой добавлено приложение.
2. codemagic.yaml settings → Code signing identities.
3. Android keystores → Choose a file.
4. Загрузите JKS.
5. Заполните поля:

| Поле Codemagic | Для существующего Safora | Для только что созданного ключа |
|---|---|---|
| File | release/signing/upload-keystore.jks | скачанный safora-upload.jks |
| Keystore password | storePassword из локального properties | NEW_KEYSTORE_PASSWORD |
| Key alias | upload | upload |
| Key password | keyPassword из локального properties | NEW_KEY_PASSWORD |
| Reference name | safora_upload | safora_upload |

6. Add keystore.
7. Убедитесь, что видны сведения о сертификате и сроке действия.

Reference name должен точно совпасть с android_signing в YAML. Для управления identities нужны права Team admin. Codemagic предупреждает: загруженный в identities keystore нельзя потом скачать из этого раздела, поэтому резервная копия у вас обязательна.

Источник: https://docs.codemagic.io/yaml-code-signing/signing-android/

## 6. Подключите обычную сборку AAB

В корне репозитория:

```sh
cp docs/codemagic/codemagic.yaml codemagic.yaml
git add codemagic.yaml docs/codemagic
git commit -m "Configure signed AAB workflow"
git push
```

Проверить в YAML:

```yaml
environment:
  java: 17
  android_signing:
    - safora_upload
```

После выбора identity Codemagic предоставляет CM_KEYSTORE_PATH, CM_KEYSTORE_PASSWORD, CM_KEY_ALIAS и CM_KEY_PASSWORD. Их не нужно дублировать вручную, если используете Code signing identities.

Текущий Safora Gradle читает release/signing/upload-key.properties и требует его перед release. Пример prepare_signing.py создаёт эти временные приватные файлы в checkout из CM_* переменных; исходный app/build.gradle.kts менять не требуется. Значения экранируются в формате Java Properties, файлы создаются с правами 600, существующие приватные файлы не перезаписываются.

Далее workflow:

1. Выбирает Java 17 и Linux X2.
2. Настраивает local.properties для Android SDK на build-машине, устанавливает platforms;android-36 и build-tools;36.0.0.
3. Выполняет prepare_signing.py.
4. Запускает `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:bundleRelease`.
5. Выполняет verify_aab.py: подпись AAB, соответствие сертификату выбранного keystore, наличие четырёх ABI и отсутствие keystore в bundle.
6. Возвращает AAB, mapping R8 и отчёты проверок.

Сборка не публикует приложение в Google Play. Google Play service account для получения AAB не требуется.

Проверка verify_aab.py не заменяет Google Play validation и не запускает приложение на устройствах. Полная прежняя проверка ELF/ZIP alignment находится в tools/verify_release.py и требует также APK/bundletool; она не выдаётся за часть нового AAB-only workflow.

Источники:
- https://docs.codemagic.io/yaml-quick-start/building-a-native-android-app/
- https://docs.codemagic.io/yaml-basic-configuration/yaml-getting-started/

## 7. Запустите AAB-сборку

1. В Codemagic откройте приложение.
2. Start new build.
3. Выберите ветку, куда попали новый YAML и helper-файлы.
4. Выберите workflow `safora-aab` / Safora - Signed AAB.
5. Start new build.
6. Проверьте стадии Configure Android SDK, Prepare uploaded signing key, Build signed release AAB, Verify AAB signature and native ABI.
7. В успешной сборке скачайте `app-release.aab` из Artifacts / артефактов.
8. Сохраните mapping.txt именно этого билда, он нужен для расшифровки crash reports после R8.

Фактический путь результата: `app/build/outputs/bundle/release/app-release.aab`.

Текущий package: hu.safora.documentstudio; versionName 1.2, versionCode 2; minSdk 26, targetSdk 36, все четыре ABI. Эти параметры задаёт Gradle, не имя проекта/identity в Codemagic.

## 8. Перед загрузкой AAB в Play Console

1. Убедитесь, что используется нужное приложение с package hu.safora.documentstudio.
2. Если versionCode 2 уже загружен, перед новой сборкой измените его в app/build.gradle.kts на большее ещё не использованное значение, например 3; закоммитьте и соберите заново. Здесь автоматическое versioning намеренно не подключено.
3. Создайте выпуск во внутреннем тестировании и загрузите AAB, скачанный из Codemagic.
4. Для нового приложения оставьте Play App Signing с созданием App signing key Google.
5. Наш/ваш upload key подтверждает право загрузки, а Google App signing key подписывает доставляемые пользователям APK.

Для дальнейших обновлений того же приложения: меняется код и versionCode, используется прежний upload key. Подключение автоматической публикации через сервисный аккаунт — отдельная настройка, для этого руководства не требуется.

Источник: https://developer.android.com/studio/publish/app-signing

## 9. Что повторять для каждого нового приложения

| Значение | Пример Safora | Другое приложение |
|---|---|---|
| package в Gradle | hu.safora.documentstudio | Собственный уникальный package |
| файл ключа | safora-upload.jks | myapp-upload.jks |
| группа генерации | safora_key_generation | myapp_key_generation |
| Reference name | safora_upload | myapp_upload |
| android_signing | safora_upload | Точно тот же myapp_upload |
| alias | upload | Можно оставить upload |
| KEY_SUBJECT | Данные Safora | Данные нужного разработчика |

Структура каталогов/Gradle у другого проекта может отличаться. prepare_signing.py специально соответствует существующему Safora signingConfig; он не универсальная замена Flutter/Groovy signingConfig. В другом приложении нужно связать Gradle с CM_* либо создать тот properties-файл, который оно фактически читает.

Новый ключ создаётся один раз на приложение, не один раз на сборку.

## 10. Диагностика

| Симптом | Проверить |
|---|---|
| YAML не найден | Корневое имя codemagic.yaml, нужная ветка, файл отправлен в Git |
| Не найдена identity safora_upload | Та же Team, точный Reference name, android_signing |
| Missing private upload signing properties | Запускается ли prepare_signing.py; закоммичен ли helper |
| Signing destination already contains files | В CI попали приватные signing-файлы или prepare_signing.py запущен дважды; убрать их из Git, использовать чистый checkout |
| Keystore was tampered with / password incorrect | Пароль контейнера, правильный JKS |
| No key with alias / alias does not exist | Alias внутри JKS, а не Reference name Codemagic |
| Cannot recover key | Key password, а не только пароль контейнера |
| Permission denied: ./gradlew | chmod +x gradlew; пример workflow уже делает это |
| SDK location not found | Стадия настройки local.properties для CM машины |
| SDK 36 не найден | Установка platforms;android-36 / build-tools;36.0.0 |
| Version code already used | Увеличить versionCode и собрать заново |
| Google Play ожидает другой сертификат | Выбран другой upload key; сравнить с Upload key certificate в Play Console, не генерировать новый вслепую |

Не исправляйте эти проблемы печатью CM_KEYSTORE_PASSWORD/CM_KEY_PASSWORD, cat приватного properties, printenv или включением shell tracing set -x.

## Файлы и фактическая проверка

- codemagic.yaml — обычный шаблон для AAB.
- generate-upload-key.yaml — отдельный одноразовый шаблон keytool.
- prepare_signing.py — мост существующего Gradle Safora и Codemagic identities.
- verify_aab.py — проверка готового подписанного AAB.
- local-validation.json — результаты локальных проверок, не отчёт об облачной сборке.
- local-aab-verification.txt — проверка существующего подписанного AAB.

Локально проверены YAML/shell syntax, Java Properties с Unicode/спецсимволами, отказ перезаписывать существующие ключи, обязательные CM_* переменные, подпись существующего Safora AAB и все ABI, отказ принимать unsigned AAB, генерация/экспорт временного тестового ключа. Временный ключ удалён, основной ключ Safora не заменён. Build-машина Codemagic не запускалась.
