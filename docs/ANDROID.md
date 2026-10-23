# Android: сборка, запуск, публикация

## Требования

- Android Studio Ladybug (2024.2) или новее, в ней уже есть JDK 21 и Android SDK;
- телефон на Android 7.0+ или эмулятор (Device Manager → Create device → Pixel 7, образ API 34).

## Запуск из Android Studio

1. **File → Open** → папка `android` из репозитория. Открывать нужно именно её, а не корень.
2. Дождитесь окончания Gradle Sync внизу экрана. В первый раз он идёт 3–10 минут, пока качаются зависимости.
3. Выберите устройство вверху и нажмите ▶ **Run 'app'**.

Отладочная сборка ставится рядом с обычной (`ru.antonshkel.subly.debug`), поэтому на телефоне можно держать обе.

### Телефон по USB

Откройте Настройки → О телефоне и семь раз нажмите «Номер сборки». Появится раздел «Для разработчиков», в нём включите «Отладка по USB». Подключите кабель и разрешите отладку на телефоне, после этого устройство появится в списке Android Studio.

### Свой сервер

На экране входа внизу нажмите «Сервер: …»:

| Где сервер | Адрес |
|---|---|
| На этом же компьютере, приложение в эмуляторе | `http://10.0.2.2:8080` |
| На компьютере, приложение на телефоне в той же Wi-Fi сети | `http://192.168.x.x:8080` (IP компьютера) |
| На VPS | `https://subly.antonshkel.ru` |

Обычный `http://` разрешён только в отладочной сборке, release-сборка ходит только по HTTPS.

Адрес по умолчанию задаётся при сборке: `./gradlew assembleRelease -Psubly.apiUrl=https://мой-домен.ru`.

## Сборка из командной строки

```bash
cd android
./gradlew testDebugUnitTest        # unit-тесты и проверка, что все экраны рисуются
./gradlew assembleRelease          # APK → app/build/outputs/apk/release/app-release.apk
./gradlew recordRoborazziDebug     # перерисовать скриншоты в docs/screenshots
```

На Windows вместо `./gradlew` используйте `gradlew.bat`.

## Подпись

Release-сборка подписывается демо-ключом `keystore/subly-demo.jks` (в папке `android`). Пароль от него открыт и лежит в `app/build.gradle.kts`. Так сделано специально: APK из любой сборки CI ставятся поверх друг друга, и пользователи не теряют данные при обновлении.

Для публикации в магазине нужен свой ключ:

```bash
keytool -genkeypair -v -keystore subly-release.jks -alias subly -keyalg RSA -keysize 2048 -validity 10000
```

Храните файл и пароли вне репозитория: если потерять ключ, выпускать обновления будет нельзя. Сборка с ним:

```bash
export SUBLY_KEYSTORE=/путь/subly-release.jks
export SUBLY_KEYSTORE_PASSWORD=...
export SUBLY_KEY_ALIAS=subly
export SUBLY_KEY_PASSWORD=...
./gradlew assembleRelease
```

В GitHub Actions переменные можно задать как секреты репозитория (Settings → Secrets and variables → Actions), а файл ключа восстанавливать из base64 в шаге сборки.

## Публикация в RuStore

1. Зарегистрируйтесь в [консоли разработчика RuStore](https://console.rustore.ru) как физлицо или самозанятый.
2. Подпишите APK своим ключом (см. выше) и поднимите `versionCode` в `app/build.gradle.kts`.
3. Загрузите APK, скриншоты из `docs/screenshots`, иконку 512×512 и описание с лендинга.
4. Укажите ссылку на политику конфиденциальности: `https://subly.antonshkel.ru/privacy`.

## Как устроен код

```
app/src/main/kotlin/ru/antonshkel/subly/
├── SublyApp.kt            зависимости и канал уведомлений
├── MainActivity.kt        edge-to-edge, тема, ссылки, экспорт CSV, разрешения
├── data/                  модели API, клиент Ktor, DataStore, кеш на диске
├── domain/Schedule.kt     даты списаний, пересчёт в месяц, рубли, склонения
├── work/ReminderWorker.kt ежедневная проверка и уведомления (WorkManager)
└── ui/
    ├── AppViewModel.kt    состояние приложения и действия
    ├── SublyRoot.kt       вкладки, модальные экраны, нижняя панель
    ├── theme/             палитра, шрифтовая шкала
    ├── components/        кнопки, карточки, переключатели, диаграмма
    └── screens/           онбординг, вход, обзор, подписки, редактор, советы, Pro, профиль
```

Экраны написаны как функции без ViewModel внутри: они получают данные и колбэки. Поэтому в скриншот-тестах их легко показать с готовыми данными.
