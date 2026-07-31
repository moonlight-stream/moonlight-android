ВСЕГДА СВЕРЯЕМСЯ С CONTEXT7 ПЕРЕД ПРАВКАМИ ИЛИ НАПИСАНИЕМ КОДА!!!!

# Суть проекта

Это форк официального Android-клиента Moonlight:
`https://github.com/moonlight-stream/moonlight-android`.

Цель форка — превратить телефон в тонкий клиент для недорогого VR/Cardboard-шлема
без превращения Moonlight в VR-приложение. Один обычный видеопоток Sunshine
декодируется один раз аппаратным `MediaCodec`, после чего локальный OpenGL ES
композитор дублирует плоский рабочий стол в два side-by-side viewport для глаз.

Целевое устройство первого MVP — OnePlus 11 в SHINECON SC-G12. Основной сценарий
— удалённая работа с KDE/терминалом/кодом и обычные плоские игры лёжа. Sunshine,
Moonlight protocol, стереоскопическая камера, OpenXR, виртуальная комната и
обязательный head tracking в scope не входят.

## Ключевые границы

- `Game` владеет Android Activity, Surface lifecycle, подключением и связыванием
  компонентов. Новую рендер-логику в этот большой класс не складывать.
- `MediaCodecDecoderRenderer` владеет аппаратным декодером, pacing и codec
  recovery. Он принимает output `Surface`, но не владеет ресурсами SBS.
- `SbsRenderer` владеет EGL context, OES texture, `SurfaceTexture`, decoder
  `Surface`, GL-потоком и SBS-композицией.
- `PreferenceConfiguration` и `preferences.xml` владеют локальными настройками
  клиента. SBS не является частью `StreamConfiguration` или wire protocol.
- `NvConnection` и `moonlight-common-c` владеют запуском/остановкой соединения и
  протоколом. Изменения здесь требуют особенно строгой проверки normal mode.
- Обычный direct-to-Surface режим upstream должен оставаться поведением по
  умолчанию и не получать дополнительный GL pass.

Критические инварианты: один network stream, один hardware decoder, никаких CPU
копий кадра; GL-вызовы выполняются только на render thread; decoder останавливается
до освобождения его `SurfaceTexture`; stale Surface generation не может начать
стрим; normal mode не регрессирует. HDR пока отключён в SBS. Lens distortion,
Cardboard SDK, chromatic correction и head tracking — отдельные будущие решения.

# Обязательный протокол



Мат и неформальное общение приветствуются, но инженерные выводы должны быть
прямыми и проверяемыми.

## Перед изменением кода

1. Вызови Serena `initial_instructions`, если это ещё не сделано в сессии.
2. Активируй проект `moonlight-android-sbs`.
3. Прочитай `mem:core` и релевантные memories.
4. При работе с Android SDK, MediaCodec, EGL/OpenGL ES, Gradle/AGP, NDK/JNI или
   другой внешней библиотекой/API сверяйся с Context7 до написания кода.
5. До первой правки кратко сообщи, какие компоненты затрагиваются, кто владеет
   изменяемым состоянием, какие lifecycle-инварианты и проверки важны.

Если Serena onboarding для проекта не выполнен, выполни его до первой серьёзной
задачи и создай базовые memories. Если memory явно устарела относительно кода
или `AGENTS.md`, обнови её до правок.

## Решения пользователя

Пользователь не пишет код сам, но принимает важные архитектурные решения и
учится в процессе. Объясняй варианты простым языком: что получим, чем заплатим
и каков риск технического долга.

Остановись и спроси пользователя до реализации, если требуется:

- изменить границу Activity / decoder / GL compositor / connection core;
- изменить Moonlight protocol, Sunshine или bundled `moonlight-common-c`;
- добавить Cardboard/OpenXR, lens distortion или head tracking;
- принять существенный компромисс latency, качества изображения или lifecycle;
- расширить согласованный scope или применить временный workaround.

Локальные однозначные решения внутри согласованной границы принимай
самостоятельно и доводи задачу до проверки.

## Реализация

- Ищи и подтверждай первопричину, а не маскируй симптом.
- Делай минимальный корректный diff и не трогай чужие изменения.
- Не смешивай фичу, косметический рефакторинг и изменение границ.
- Не игнорируй ошибки и не подменяй конфигурацию хардкодом.
- Не блокируй UI thread ожиданием EGL, codec или network teardown.
- Не освобождай `Surface`, `SurfaceTexture` или EGL раньше остановки producer-а.
- Соблюдай существующий стиль upstream. Комментарии в production source пиши
  по-английски; по-русски ведутся harness docs и Serena memories.
- Комментируй только важные неочевидные инварианты и причины решений.

## Сборка и проверка

Требуются JDK 17, Android SDK 34, Build Tools 34.0.0, NDK 27.0.12077973 и
инициализированные Git submodules.

Основные команды:

```bash
git submodule update --init --recursive
./gradlew assembleNonRootDebug
./gradlew test lint
```

Debug APK: `app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk`.

Изменения видеорендеринга не считаются полностью проверенными без device smoke
test: запуск normal и SBS mode, connect/disconnect, background/foreground,
Surface recreation, 1440p60 HEVC, отсутствие переворота/растяжения кадра и
наблюдаемого лишнего кадра latency.

## Завершение

1. Выполни self-review и исправь найденные проблемы в согласованном scope.
2. Запусти релевантные Gradle-проверки; установку APK и потенциально опасные
   команды выполняй только с разрешения пользователя.
3. Проверь итоговый diff и отсутствие случайных generated/cache файлов.
4. Оцени, изменились ли boundaries, protocol/API, lifecycle, workflow, команды
   проверки, known limitations или расположение ключевых тестов.
5. Если durable project knowledge изменилось, обнови Serena memories. Если нет,
   не засоряй memories и укажи это в финальном ответе.
6. В финале кратко сообщи: что изменено, что проверено и обновлялись ли memories.
