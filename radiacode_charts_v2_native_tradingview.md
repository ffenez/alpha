# RadiaCode Charts V2 — нативный интерактивный графический движок уровня TradingView

## Цель

Перепроектировать текущий полноэкранный график (`ui/screens/LiveChartScreen.kt`, отрисовка — `ui/components/DoseChart.kt`) в единый **Charts V2 engine** для:

- мощности дозы;
- скорости счёта;
- жёсткости;
- поиска;
- сессий истории;
- маршрутов;
- будущих временных рядов.

Главная цель — не внешнее копирование TradingView, а такое же ощущение управления:

- график двигается непосредственно под пальцем;
- pinch масштабирует относительно точки между пальцами;
- pan имеет инерцию;
- live-график естественно следует за правым краем;
- crosshair появляется и перемещается без рывков;
- масштаб осей не прыгает;
- никаких лагов при десятках/сотнях тысяч измерений;
- все переходы плавные, но данные не «анимируются» так, чтобы искажать измерение;
- карточки Главной и полноэкранный режим используют один data/viewport engine.

Не использовать WebView/TradingView Charting Library. Для Android TradingView Advanced Charts официально ориентирован на WebView и не предоставляет нативной Android-обёртки. Нам нужен собственный native renderer, полностью контролирующий научную семантику данных.

---

# 1. Текущую реализацию не наращивать — выделить новый движок

Текущая схема:

```text
LiveChartScreen
    ↓
DoseChart
    ↓
detectTransformGestures
detectDragGesturesAfterLongPress
ChartViewport.PinchAccumulator
ChartWindows
```

уже содержит полезную логику, но V2 должен разделить:

```text
DATA
VIEWPORT
GESTURES
RENDERER
OVERLAYS
UI CONTROLS
```

Целевая структура:

```text
charts/v2/
├── ChartEngine.kt
├── ChartViewport.kt
├── ChartInteractionState.kt
├── ChartDataSource.kt
├── ChartSeries.kt
├── ChartTransform.kt
├── ChartDownsampler.kt
├── ChartYAxis.kt
├── ChartXAxis.kt
├── ChartRenderer.kt
├── ChartGestureController.kt
├── ChartCrosshair.kt
├── ChartAnimationController.kt
├── ChartEventOverlay.kt
├── ChartPerformanceStats.kt
└── ui/
    ├── NativeChartSurface.kt
    ├── ChartTopBar.kt
    ├── ChartBottomControls.kt
    └── ChartDetailsSheet.kt
```

`DoseChart.kt` не должен продолжать превращаться в монолит, который одновременно хранит состояние окна, распознаёт жесты, выбирает статистику и рисует всё.

---

# 2. Renderer: настоящий native, без recomposition на каждый пиксель drag

## Предпочтительная архитектура

Сначала реализовать Charts V2 на нативном Compose drawing stack:

- `Canvas` / `DrawScope`;
- `drawWithCache` для сетки, текста, Path и объектов, которые не нужно пересоздавать на каждом кадре;
- pointer input отделён от тяжёлой подготовки данных;
- изменение viewport инвалидирует только chart surface, а не весь `LiveChartScreen`.

Jetpack Compose официально поддерживает низкоуровневые pointer input/multitouch APIs и `drawWithCache`, который предназначен для кэширования объектов между draw calls.

### Важный fallback

Если Macrobenchmark/Perfetto покажут, что Compose Canvas не держит требуемый frame budget на реальном целевом устройстве, renderer должен иметь возможность перейти на:

```text
custom android.view.View
+ Canvas
+ ScaleGestureDetector
+ GestureDetector
+ OverScroller
```

через `AndroidView`, сохранив тот же `ChartEngine` и `ChartViewport`.

**Не выбирать WebView как способ «получить TradingView».**

---

# 3. Модель viewport — центральная часть V2

Не хранить «выбранную ступень 5м/15м» как настоящий масштаб.

Настоящее состояние:

```kotlin
data class ChartViewport(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val yMin: Double,
    val yMax: Double,
    val followLiveEdge: Boolean,
    val yMode: YMode,
)
```

Периоды `1м / 5м / 15м / 1ч / ...` — только **presets**.

После pinch пользователь может получить любое окно:

```text
3 мин 42 с
8 мин 17 с
47 мин
```

и график должен оставаться в нём.

---

# 4. Поведение X-оси как в хорошем финансовом графике

## Live edge

Последняя актуальная точка находится у правой границы с небольшим right padding.

Пока:

```text
followLiveEdge = true
```

новые данные мягко приходят справа, а окно едет вместе со временем.

## Пользователь потянул график в прошлое

Сразу:

```text
followLiveEdge = false
```

Новые данные продолжают записываться, но viewport пользователя **не прыгает обратно**.

Появляется компактная кнопка:

```text
⌖ сейчас
```

Tap → плавный возврат к live edge.

## Возвращение

Не teleport.

Короткая физически понятная анимация viewport к текущему времени, ориентировочно 180–250 мс.

Если расстояние очень большое, допустим fade/fast jump вместо многосекундного «полёта».

---

# 5. Жесты V2

TradingView на mobile использует single-touch drag для горизонтального перемещения и long-press для crosshair. Мы должны сохранить знакомую мобильную модель, но адаптировать её под наши данные.

## 5.1 Один палец — pan

Обычный drag сразу двигает график.

```text
finger Δx → time Δ
```

Без задержки.

График должен «держаться за палец».

Не ждать threshold после очевидного horizontal drag дольше системного touch slop.

---

## 5.2 Fling / inertia

После быстрого отпускания pan продолжается с естественным замедлением.

Использовать velocity tracking + decay/OverScroller.

Ограничения:

- не перелетать дальше доступной истории;
- мягко останавливаться у boundaries;
- при касании во время fling — сразу захватить управление;
- при достижении live edge можно snap в `followLiveEdge = true`, если пользователь действительно дошёл до края.

Это одно из главных отличий «живого» графика от текущего механического pan.

---

## 5.3 Pinch — непрерывный zoom

Два пальца:

- горизонтальное расстояние → X zoom;
- focal point остаётся под тем же местом пальцев;
- zoom не привязан к лестнице periods;
- после release окно не округляется к preset.

Формула концептуально:

```text
timeUnderFocus before zoom
=
timeUnderFocus after zoom
```

То есть pinch около правой части увеличивает именно правую часть, а не центр экрана.

---

## 5.4 Одновременный pan + pinch

Не разделять искусственно.

Во время двухпальцевого жеста:

- scale меняется;
- centroid может двигаться;
- viewport одновременно pan + zoom.

Это уже заложено концептуально в текущем `onTransform`, но V2 должен сделать это без PinchAccumulator-эффекта ступенчатости.

---

## 5.5 Long press — crosshair

Long press:

- включает crosshair;
- лёгкий haptic один раз;
- вертикальная линия;
- горизонтальная линия опциональна;
- value label на Y;
- time label на X.

Дальнейший drag одним пальцем двигает crosshair.

Crosshair привязывается не к пикселям, а к ближайшему доступному временному sample/bin.

---

## 5.6 Tap

Если crosshair активен:

```text
tap вне HUD → закрыть crosshair
```

Если crosshair не активен:

- обычный tap не должен случайно менять viewport;
- tap около event marker может выбрать событие;
- tap по пустой области ничего не делает.

---

## 5.7 Double tap

Сделать поведение более естественным:

### double tap по полю

Вернуть **Y autoscale** и сохранить текущее X-window.

Это полезнее, чем всегда полностью сбрасывать временной масштаб.

### отдельная команда Reset

В `⋯`:

```text
Сбросить масштаб
```

возвращает X + Y к preset/live state.

Опционально проверить на пользователях вариант:

```text
double tap → preset window + live edge
```

но не смешивать две функции без явной причины.

---

# 6. Дополнительное управление осями

## X-axis drag

Опционально V2.1:

drag непосредственно по нижней временной шкале → горизонтальный zoom вокруг выбранной точки.

## Y-axis drag

Опционально V2.1:

drag по правой Y-axis:

```text
up/down → vertical scale
```

После ручного изменения:

```text
yMode = MANUAL
```

Double tap по Y-axis:

```text
yMode = AUTO
```

Это знакомая модель аналитических графиков, но её не нужно включать в MVP, если она усложнит touch targets.

---

# 7. Y autoscale без прежних багов

Это должно стать частью общего `ChartYAxis`, а не логикой отдельных карточек.

Расчёт только по:

```text
visible X range
+
valid visible samples
```

Старые выбросы вне viewport не влияют на Y.

## Robust scaling

Не позволять одному bad sample уничтожать масштаб.

Использовать:

```text
robust visible range
+ controlled padding
+ minimumSpan
```

При этом истинный outlier не удаляется из данных — он может быть показан индикатором за пределами области.

## Во время pan/zoom

**Не пересчитывать Y bounds агрессивно каждый pointer frame.**

Иначе весь график будет «дышать».

Поведение:

1. interaction start → текущий Y-domain фиксируется;
2. пользователь pan/zoom;
3. после небольшого settle (~80–120 мс) вычислить новый autoscale;
4. плавно анимировать Y-domain к новому диапазону.

Если в viewport появляется огромный реальный скачок, разрешить более быстрый rescale.

---

# 8. Анимации: плавность без фальсификации данных

Не анимировать точки между измеренными значениями так, будто прибор измерил промежуточную физическую траекторию.

Анимировать только **представление**:

- viewport;
- axis bounds;
- opacity overlays;
- crosshair;
- появление/скрытие controls;
- возврат к live edge.

## Live updates

Новая sample point появляется без декоративного overshoot/bounce.

Можно плавно сдвигать viewport на величину прошедшего времени, но геометрия raw trace соответствует данным.

---

# 9. Производительность: не рисовать 100 000 точек на 800 пикселей

Это критично.

Если chart width = 800 px, отображение десятков тысяч отдельных line segments бесполезно.

## Multiresolution data pipeline

Для каждого масштаба выбирать уровень данных:

```text
raw
1 s
5 s
30 s
1 min
5 min
...
```

Но агрегат должен сохранять экстремумы.

Для временных радиационных данных предпочтительно хранить внутри display bucket минимум:

```text
first
min
max
last
median / representative value
```

Чтобы узкий настоящий всплеск не исчез после downsampling.

---

# 10. Pixel-aware downsampling

После выбора history level делать final reduction относительно фактической ширины Canvas.

Цель:

```text
~1–4 drawable primitives per horizontal pixel
```

а не `all samples`.

Нельзя использовать простое `everyNth`, потому что оно может пропустить короткий пик.

Исследовать:

- min/max envelope per pixel bucket;
- M4/min-max-first-last;
- LTTB для режимов, где важнее общая форма.

Для наших научных traces **peak-preserving min/max bucket** должен быть базовым кандидатом.

---

# 11. Не выполнять тяжёлые вычисления в draw()

В draw path запрещено:

- фильтровать Room entities;
- считать quantiles;
- искать события;
- делать allocations на каждую точку;
- строить formatter/Path заново без необходимости;
- пересортировывать данные.

Pipeline:

```text
Room / live stream
    ↓
ChartDataSource
    ↓
window query
    ↓
aggregation/downsampling
    ↓
PreparedChartFrame
    ↓
renderer
```

`PreparedChartFrame` должен быть максимально близок к готовым screen coordinates/series primitives.

---

# 12. Кэширование

Кэшировать отдельно:

## Static until layout/theme changes

- plot rect;
- grid;
- axis text layouts;
- Paint/TextMeasurer objects;
- clipping paths.

## Changes with viewport

- X ticks;
- Y ticks;
- transformed series geometry.

## Changes each live sample

- только правый хвост series;
- current value label;
- live edge.

Не пересобирать весь экран из-за нового `25.1 → 25.2`.

---

# 13. Frame scheduling

Во время активного gesture:

- обновления viewport coalesce до одного render на frame;
- не рисовать 3–5 раз между двумя VSYNC;
- background data update не должен конкурировать с pointer processing.

На Android использовать frame-synchronised invalidation/Compose frame scheduling.

---

# 14. Crosshair V2 — сильно упростить

Сейчас cursor пытается сразу показывать:

- интервал колонки;
- медиану;
- P25–P75;
- P10–P90;
- min/max и времена;
- n;
- метод квантилей;
- профиль;
- отношение;
- события.

Это полезная аналитика, но **не должна вываливаться при каждом long press**.

## Основной cursor HUD

Только:

```text
14:12:36
0,153 мкЗв/ч
```

Если точка агрегирована:

```text
14:12:30–14:12:35
медиана 0,153
```

Компактно.

## Расширение

Tap по HUD / `Подробнее`:

bottom sheet:

```text
Медиана
P25–P75
P10–P90
min
max
n
профиль
события
```

Метод квантилей — под `i`, а не в cursor UI.

---

# 15. Events — слой, а не основная часть графика

По умолчанию:

```text
events OFF
```

Когда включены:

- небольшие markers;
- collision grouping;
- `△3` только после tap/selection, не постоянно;
- при сильной плотности cluster marker.

Events overlay не должен влиять на Y-domain series.

---

# 16. Контролы V2 — меньше чипов

Сейчас одновременно:

```text
period
log
smoothing
events
details
i
edge
close
```

Это перегружает chart chrome.

## Top bar

```text
×   МОЩНОСТЬ ДОЗЫ          0,153       ⌖ сейчас
```

`i` — в overflow.

Для history:

```text
×   МОЩНОСТЬ ДОЗЫ          0,153       сессия
```

## Bottom toolbar

Основной уровень:

```text
[5м ▾]      [масштаб]      [⋯]
```

Где `масштаб`:

```text
Авто / Лин / Лог
```

только если данный metric действительно поддерживает эти варианты.

`⋯`:

- Сглаживание
- События
- Статистика
- Справка
- Сбросить масштаб

Не держать шесть равнозначных чипов постоянно.

---

# 17. Period selector

Не разворачивать 15 чипов горизонтальной лентой поверх графика.

Tap по `5м ▾` → compact bottom sheet / popup:

```text
1м    5м    15м
30м   1ч    2ч
6ч    12ч   24ч
3д    7д    30д
Вся история
```

Последние/частые варианты сверху.

После pinch label показывает фактическое окно:

```text
7м 42с
```

и небольшая точка/метка показывает, что это custom zoom, а не preset.

---

# 18. Масштаб Linear / Log / Power

Не делать один generic `лог` chip без контекста.

Если для конкретной величины power scale научно/визуально полезен:

```text
Лин
Лог
Степень
```

Power:

```text
1/n
n = 1 … 10
```

с slider в details.

Но это отдельная capability metric-а. Не показывать Power там, где он не даёт практической пользы.

---

# 19. Smoothing

Сглаживание — renderer-only.

Raw data не меняется.

В интерфейсе лучше:

```text
Вид:
● исходный
○ сглаженный
```

или просто toggle в `⋯`.

При переключении:

- короткий crossfade между rendering paths;
- не morph raw data;
- analytics/cursor всегда знает, какие реальные samples лежат под visual curve.

---

# 20. Карточка Главной и fullscreen — один engine

Сейчас `interactive=false` уже идёт в правильную сторону.

V2:

```text
Home Chart Card
= ChartEngine + compact renderer preset
```

```text
Fullscreen
= тот же ChartEngine + interactive controller
```

Никакой отдельной математики scale/downsampling для карточки.

Tap по карточке передаёт в fullscreen **тот же visible time range**, а не всегда открывает дефолтные 5/30 минут.

Это создаёт ощущение seamless expansion.

---

# 21. Переход карточка → fullscreen

Сделать короткую shared-like transition:

1. карточка tap;
2. plot rect расширяется;
3. series остаётся визуально на месте;
4. появляются оси/controls.

Если полноценный shared-element сложен — достаточно 150–200 мс scale/fade.

Не делать тяжёлую декоративную анимацию.

---

# 22. История / маршрут / поиск

Один renderer, разные `ChartContext`.

```kotlin
sealed interface ChartContext {
    data object Live
    data class Session(val sessionId: Long)
    data class Route(val routeId: Long)
    data class Search(val searchId: Long?)
}
```

## Live

- `⌖ сейчас`;
- новые данные поступают;
- follow live edge.

## Session

- immutable range;
- `вся сессия`;
- никакого «сейчас».

## Route

- time window маршрута;
- crosshair синхронизируется с точкой карты;
- action `Показать на карте`.

## Search

- CPS;
- reference/local background overlay;
- crosshair показывает разницу с фоном.

---

# 23. Gap semantics

Никаких линий через:

- disconnect;
- reboot;
- process/data gap;
- отсутствующую телеметрию;
- incompatible segment.

Renderer получает segments:

```text
segment A
gap
segment B
```

и рисует их отдельно.

Gap никогда не интерполируется просто ради красивой линии.

---

# 24. Latest value label

Как в TradingView-подобных интерфейсах:

- текущая величина закреплена на правой Y-axis;
- label движется вместе с последним значением;
- пока пользователь в прошлом, можно показать faint offscreen-live marker отдельно;
- выбранный crosshair value и live value визуально различаются.

Не размещать value pill поверх последних данных, если можно вынести его на axis gutter.

---

# 25. Labels и collision

Сейчас подписи событий/порогов могут конфликтовать.

Нужен общий `ChartLabelLayout`.

Приоритет:

```text
crosshair value
current value
alarm threshold
selected event
ordinary event
axis tick
```

Если конфликт:

- lower-priority label скрывается/сдвигается;
- текст никогда не рисуется друг поверх друга.

---

# 26. Landscape

Не делать отдельный другой график.

В landscape:

- plot занимает максимум высоты/ширины;
- top bar компактнее;
- bottom controls overlay;
- details открываются сбоку или bottom sheet в зависимости от ширины.

Всю логику viewport/gesture оставить общей.

---

# 27. Accessibility

- touch targets controls не меньше стандартных Android target sizes;
- TalkBack получает текущее значение, окно и состояние;
- не кодировать alarm/event только цветом;
- haptic на crosshair activation и snap-to-event;
- Reduce Motion отключает лишние transitions, но не взаимодействие.

---

# 28. Performance targets

Не принимать «на глаз».

Проверять Macrobenchmark/Perfetto на реальном устройстве.

## Интерактивность

Цель:

- pan/pinch/crosshair без заметного jank;
- p95 frame time внутри бюджета частоты экрана;
- pointer-to-render latency минимальна;
- никакой Room/quantile computation на main thread во время gesture.

## Dataset tests

Минимально проверить:

```text
60 s @ 1 Hz
30 min
6 h
24 h
7 d
30 d
100k samples
1M historical samples
many gaps
many events
single huge outlier
live append while panning
```

Renderer никогда не должен пытаться нарисовать миллион raw vertices.

---

# 29. Benchmark regression gate

Добавить benchmark сценарии:

```text
continuous pan 5 s
pinch in/out 5 s
fling
activate + drag crosshair
return to live
switch 5m → 24h
toggle smoothing
open details
live append during gestures
```

Если Charts V2 ухудшает frame time после следующих изменений — perf run должен это обнаружить.

---

# 30. Миграция без big-bang rewrite

## Phase 1

Выделить:

```text
ChartViewportV2
ChartDataSourceV2
ChartTransformV2
```

Старый renderer временно может использовать новый viewport.

## Phase 2

Новый renderer + downsampling.

## Phase 3

GestureController + inertia + live edge.

## Phase 4

Crosshair V2 + controls V2.

## Phase 5

Перевести:

- Главную;
- History;
- Route;
- Search.

После parity удалить старый gesture/render path.

---

# 31. Acceptance criteria Charts V2

Charts V2 готов, если:

### Gestures
- pan следует пальцу без заметной задержки;
- есть естественный fling;
- pinch плавный и focal-point preserving;
- pinch не прыгает между preset periods;
- long press crosshair стабилен;
- касание во время fling немедленно останавливает fling;
- pan в прошлое отключает live-follow;
- `сейчас` возвращает к live edge.

### Rendering
- нет графических прыжков Y-scale;
- старые outliers вне viewport не влияют на ось;
- gap не соединяется линией;
- latest value находится на правом краю;
- labels не накладываются друг на друга;
- event markers не захламляют график.

### Performance
- график остаётся плавным на длинной истории;
- число draw primitives связано с pixel width, а не с raw sample count;
- никаких тяжёлых allocations/computation внутри draw;
- live append не вызывает полную тяжёлую recomposition дерева экрана.

### UX
- main card → fullscreen ощущается как продолжение того же графика;
- controls занимают минимум места;
- cursor сначала показывает только значение и время;
- продвинутая статистика доступна по запросу;
- Live/Session/Route/Search используют одну систему управления.

---

# 32. Что не делать

Не делать:

- WebView ради TradingView;
- отдельную библиотеку графика для каждой величины;
- 15 видимых period chips;
- snap pinch к ближайшему preset;
- autoscale каждый pointer frame;
- миллион raw points на Canvas;
- сглаживание, меняющее данные;
- соединение gaps;
- огромный cursor-инспектор поверх графика;
- анимацию самих физических измерений;
- декоративный bounce/overshoot у новых radiation samples;
- разные gesture semantics на Dose/CPS/Hardness.

---

# 33. Важный принцип

Charts V2 должен ощущаться как **инструмент**, а не как экран с графиком.

Пользователь должен иметь возможность:

```text
положить палец
→ сдвинуть время
→ отпустить
→ получить естественную инерцию
→ приблизить интересующий участок
→ удержать
→ сразу увидеть точное значение и время
```

без размышлений о том, какой сейчас «режим графика».

UI должен исчезать из внимания, а данные — оставаться главным объектом.

---

# Официальные ориентиры

## TradingView mobile interaction

TradingView Advanced Charts mobile documentation описывает touch navigation, pinch scaling и long-press crosshair как часть мобильной модели взаимодействия:

- https://www.tradingview.com/charting-library-docs/latest/mobile_specifics/
- https://www.tradingview.com/charting-library-docs/latest/customization/Featuresets/

Важно: их Android-интеграция основана на WebView; native wrapper не предоставляется. Поэтому использовать это как UX reference, а не как implementation dependency.

## Android / Jetpack Compose

Официальная документация Android:

- Pointer input:
  https://developer.android.com/develop/ui/compose/touch-input/pointer-input
- Multi-touch:
  https://developer.android.com/develop/ui/compose/touch-input/pointer-input/multi-touch
- Drag / swipe / fling:
  https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling
- Compose graphics:
  https://developer.android.com/develop/ui/compose/graphics/draw/overview
- Graphics modifiers / drawWithCache:
  https://developer.android.com/develop/ui/compose/graphics/draw/modifiers
- Compose performance:
  https://developer.android.com/develop/ui/compose/performance

При реализации опираться на актуальные версии AndroidX в репозитории, а не копировать устаревшие gesture APIs из старых примеров.
