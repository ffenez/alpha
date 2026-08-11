# CLAUDE.md — Математическая спецификация графика мощности дозы

> Этот документ является обязательной реализационной спецификацией для экрана `Мощность дозы`.
> Научная корректность, воспроизводимость и честная интерпретация важнее визуальной гладкости.

## Цель

Экран должен одновременно:

- показывать временную динамику измеренной мощности дозы;
- сохранять видимость реального разброса измерений;
- показывать historical baseline активного профиля;
- позволять заметить короткие значимые всплески;
- не смешивать наблюдаемый разброс с measurement uncertainty;
- не использовать min–max как псевдо-доверительный интервал;
- не превращать исторический диапазон места в норматив безопасности;
- одинаково корректно работать от минут до месяцев;
- не менять научный вывод только из-за zoom / bin width.

Ключевое правило:

> **Raw observations, aggregation spread, historical baseline, extrema/events и measurement uncertainty — разные статистические объекты и должны рассчитываться/визуализироваться отдельно.**

---

## 1. Первичные данные

Использовать только реальные значения мощности дозы, поступившие от RC-110 / protocol layer.

Минимальная модель:

```text
timestamp
doseRate
deviceReportedUncertainty?   // только если реально доступна
countRate?
freshness
sessionId
profileId?
baselineEligible
```

Никогда не вычислять мощность дозы как:

```text
doseRate = CPS / 77
```

или через иной постоянный коэффициент.

Характеристика Radiacode `77 cps per 1 µSv/h` относится к Cs-137 и не является универсальным преобразованием CPS→dose rate для неизвестного спектра.

---

## 2. Raw data — источник истины

Хранить раздельно:

```text
RawSample
AggregatedBin
HistoricalBaseline
QuantileSketch
DerivedUncertainty
DetectedEvent
```

Сглаживание, resampling, binning и любые derived analytics никогда не перезаписывают raw data.

Любой derived result должен быть пересчитываем из raw data или из явно версионированной предагрегации.

---

## 3. Временное binning

Для длинных временных окон агрегировать данные по временным bins.

UX-цель:

```text
~100–250 bins на ширину графика
```

Ориентировочно:

```text
binWidth ≈ visibleDuration / targetBinCount
```

После этого округлять к удобным интервалам:

```text
1 s
2 s
5 s
10 s
15 s
30 s
1 min
2 min
5 min
10 min
15 min
30 min
1 h
...
```

Binning — только способ визуального представления.

**Изменение bin width / zoom не должно само по себе менять event classification или научный вывод.**

Если научная логика зависит от времени, она должна работать на собственном фиксированном/валидированном временном представлении, а не на текущем screen binning.

---

## 4. Центральная линия

Для каждого bin:

\[
m_i = \operatorname{median}(x_{i1},x_{i2},...,x_{in})
\]

Основная линия:

```text
median of bin
```

Подпись:

```text
линия — медиана окна
```

Не называть её:

```text
истинное значение
ожидаемое значение
filtered truth
```

---

## 5. Raw observations

На коротких масштабах показывать реальные samples как слабые точки.

Рекомендация:

```text
15 min / 1 h   raw points visible
6 h            raw points optional/downsampled
24 h+          hidden by default
```

Если используется downsampling, он должен сохранять:

- форму сигнала;
- временное положение событий;
- экстремальные точки.

Нельзя случайно «усреднить прочь» короткий всплеск.

---

## 6. Quantile envelopes вместо mean ± SD

Не использовать полосу `mean ± SD` вокруг медианной линии.

Это внутренне противоречиво:

- линия центрирована на median;
- полоса центрирована на mean;
- при асимметричном распределении они описывают разные статистические центры.

Рекомендуемая модель:

```text
inner band = Q25–Q75
outer band = Q10–Q90
center line = Q50 (median)
```

Где:

\[
Q_p = p\text{-й квантиль наблюдений bin}
\]

Это **наблюдаемый robust spread**, а не measurement uncertainty.

Подпись:

```text
разброс измерений
```

Не:

```text
погрешность
неопределённость
доверительный интервал
```

если это просто quantile envelope.

---

## 7. Min–max: не удалять данные, но убрать псевдо-интервал

Не использовать min–max как постоянную закрашенную полосу.

Причина:

- экстремумы зависят от N;
- при длинном окне вероятность экстремума выше;
- envelope расширяется даже при неизменной физической среде.

Но экстремумы нельзя полностью скрывать, иначе короткий значимый всплеск может исчезнуть на длинном масштабе.

Правильная модель:

```text
Q25–Q75  → основной конверт
Q10–Q90  → внешний robust конверт
MIN/MAX  → отдельные markers / event extrema
```

На длинных масштабах:

- не рисовать непрерывный min/max envelope;
- помечать bins, внутри которых были существенные экстремумы;
- при тапе показывать exact min/max и время extrema;
- если событие классифицировано как event — рисовать event marker/episode.

Допустим тонкий экстремальный контур только если он визуально явно отличён от quantile band и не выглядит как uncertainty interval.

---

## 8. Historical baseline профиля

Baseline строится только по валидным измерениям:

```text
baselineEligible == true
context trusted
stream fresh
not Search
not Research
not anomaly quarantine
```

Основные показатели:

\[
B_{10}=Q_{0.10}(B)
\]

\[
B_{50}=\operatorname{median}(B)
\]

\[
B_{90}=Q_{0.90}(B)
\]

Опционально:

\[
MAD=\operatorname{median}(|B_i-B_{50}|)
\]

Отображать:

```text
Historical baseline P10–P90
baseline median B50
```

Подпись:

```text
Исторический диапазон профиля · P10–P90
```

Не использовать:

```text
норма
безопасный диапазон
допустимый диапазон
```

P10–P90 означает только:

> около 80% валидных исторических измерений этого профиля находились внутри диапазона.

---

## 9. Baseline median

Внутри historical band можно показывать:

```text
baseline median B50
```

как тонкую серую/нейтральную пунктирную линию.

Это reference, а не safety threshold.

---

## 10. Alarm threshold

Alarm — отдельный объект.

Например:

```text
L1 0.30 µSv/h
```

рисуется отдельной красной пунктирной линией.

Не связывать Alarm автоматически с:

```text
P90
P95
median
MAD
```

локального baseline.

Пересечение P90 профиля и пересечение Alarm — разные события.

---

## 11. Measurement uncertainty

Различать минимум три объекта:

```text
1. observed spread of dose-rate samples
2. counting-statistical uncertainty
3. device-reported / instrument uncertainty
```

Они не эквивалентны.

### 11.1 Если RC-110 передаёт uncertainty

Хранить и показывать отдельно как:

```text
device uncertainty
```

Не превращать её в SD временного ряда.

### 11.2 Пуассоновская статистика для count rate

Для независимого counting process:

\[
N\sim Poisson(\lambda)
\]

\[
\sigma_N\approx\sqrt N
\]

\[
R=N/t
\]

\[
\sigma_R\approx\frac{\sqrt N}{t}
\]

Это допустимая основа для uncertainty **count rate**, если выполняются предпосылки модели.

Не переносить автоматически эту uncertainty на dose rate в µSv/h.

### 11.3 Ограничения

Poisson approximation может требовать поправок при:

- dead time;
- pile-up;
- correlated events;
- detector processing effects;
- non-stationary environment.

Любая собственная uncertainty metric требует validation.

---

## 12. Голое `σ` запрещено

Никогда не показывать:

```text
σ 0.014
```

без определения.

Использовать явные названия:

```text
SD 0.014 µSv/h
MAD 0.010 µSv/h
device uncertainty ±15%
CPS statistical uncertainty ±4.9%
```

Если показывается SD:

```text
SD — наблюдаемый разброс значений
```

Если MAD:

```text
MAD — робастный разброс
```

На главном compact view предпочтительнее quantiles, а SD/MAD — в расширенной статистике.

---

## 13. Window statistics

Compact default:

```text
P10
MEDIAN
P90
N
WINDOW
```

Expanded analysis:

```text
MIN
P10
Q25
MEDIAN
Q75
P90
MAX
MAD
SD
N
duration
```

---

## 14. Гистограмма

Гистограмма показывает распределение dose-rate samples в текущем видимом окне.

```text
x-axis = dose rate
y-axis = frequency or density
```

### Основное правило

Использовать Freedman–Diaconis как **базовую эвристику**, а не как абсолютный физический закон:

\[
h=2\cdot IQR(x)\cdot n^{-1/3}
\]

где:

\[
IQR=Q_{75}-Q_{25}
\]

Важно:

- FD не следует описывать как правило, которое «требует нормального распределения»;
- оно использует IQR и относительно устойчиво к выбросам;
- при реальных сильно дискретных/скошенных данных UI всё равно требует инженерных ограничений.

Рекомендуемая реализация:

```text
1. compute FD width
2. convert to candidate bin count
3. clamp to a product-defined readable range
4. snap displayed boundaries to readable values
```

Например:

```text
minBins = 8
maxBins = 40
```

Но это **UI-эвристика**, а не научная константа.

Fallback обязателен при:

```text
IQR == 0
very small N
degenerate distribution
```

---

## 15. Histogram vs baseline

Опционально сравнивать:

```text
current-window distribution
historical baseline distribution
```

Это позволяет увидеть:

- shifted median;
- increased spread;
- possible bimodality.

Не интерпретировать bimodality автоматически как конкретную физическую причину.

---

## 16. Cursor / touch

При касании показывать статистику bin.

Пример:

```text
14:02:00–14:03:00

median          0.154 µSv/h
Q25–Q75         0.148–0.160
Q10–Q90         0.143–0.168
min             0.139 @ 14:02:07
max             0.182 @ 14:02:43
samples         60

Home baseline
median          0.112
P10–P90         0.097–0.138
```

Если доступны count statistics:

```text
counts
CPS
CPS statistical uncertainty
```

---

## 17. Ratios должны иметь точный знаменатель

Если:

\[
R=x/B_{50}
\]

подписывать:

```text
4.8× baseline median
```

Если:

\[
R=x/B_{90}
\]

подписывать:

```text
4.8× baseline P90
```

Не писать:

```text
4.8× обычного
4.8× привычного
```

без определения знаменателя.

Для пользовательского русского UI предпочтительно:

```text
4,8× к P90 профиля
```

и в `Почему?` объяснять P90.

---

## 18. Главный статус профиля

Не использовать краткое `Обычный фон`, если оно может восприниматься как нормативное заключение.

Рекомендуемый compact status:

```text
В обычном диапазоне этого профиля
```

или, если нужен ещё короче:

```text
Обычный для этого места
```

Предпочтительный научно точный вариант:

```text
В обычном диапазоне этого профиля
```

Ниже всегда показывать reference:

```text
P10–P90: 0.14–0.17 µSv/h
baseline: 18 h
```

В `Почему?`:

```text
Текущая мощность дозы находится внутри исторического P10–P90 профиля.
```

Не использовать:

```text
норма
безопасно
допустимо
```

---

## 19. Status logic

Не определять status простым условием:

```text
current < baseline P90
```

Минимально учитывать:

- profile/context confidence;
- baseline maturity;
- persistence;
- stream freshness;
- current interval duration.

Пока formal anomaly engine не валидирован, использовать descriptive status, а не probabilistic claim.

---

## 20. Episode highlighting

Если обнаружен устойчивый эпизод:

- выделить временной диапазон вертикальной полупрозрачной областью;
- сохранить duration;
- сохранить extrema;
- показать reference.

Например:

```text
13:49–13:52
above historical P90
```

или:

```text
13:49–13:52
above Alarm L1
```

Это разные классы событий.

---

## 21. Короткие всплески

Короткий всплеск не должен исчезать только потому, что он короче текущего bin.

Поэтому кроме quantile envelope хранить/показывать:

```text
binMin
binMax
extremeTimestamp
eventFlag
```

На длинном масштабе:

- marker в соответствующем bin;
- tap раскрывает точное событие;
- event history сохраняет оригинальный raw interval.

Не использовать один лишь P10–P90 для детектируемости transient spikes.

---

## 22. Временные масштабы

### 15 min

```text
raw points
median
Q25–Q75
baseline P10–P90
alarm
extrema/event markers
```

### 1 h

```text
raw points
median
Q25–Q75
Q10–Q90
baseline
extrema/event markers
```

### 6 h

```text
aggregated bins
raw points optional
robust envelopes
event markers
```

### 24 h

```text
aggregated bins
robust envelopes
event markers
no dense raw points by default
```

### 7 d

```text
larger bins
median
quantile envelopes
baseline
events
```

### 30 d+

```text
trend-oriented aggregation
robust bands
events/extrema markers
no dense point cloud
```

---

## 23. Trend: Theil–Sen вместо OLS по умолчанию

Если показывается:

```text
Тренд/ч
```

он должен иметь явное математическое определение.

Preferred estimator:

```text
Theil–Sen slope
```

Идея:

\[
\hat\beta = \operatorname{median}_{i<j}\frac{x_j-x_i}{t_j-t_i}
\]

Преимущество:

- значительно робастнее OLS к единичным выбросам;
- согласуется с median/quantile философией UI.

OLS можно оставить как Research comparison, но не как основной тренд по умолчанию.

Показывать trend только при достаточном N и time span.

Иначе:

```text
trend unavailable
```

Тренд — descriptive quantity, не доказательство физической причины.

---

## 24. Data freshness

Каждый current value имеет:

```text
sampleTimestamp
age = now - sampleTimestamp
```

При stale stream:

```text
Данные не обновляются
Последнее измерение: 38 с назад
```

Не продолжать линию старым значением как live data.

---

## 25. Missing data

Пропуски — реальные gaps.

Не интерполировать через длительные BLE outages.

График:

```text
line break
```

Короткая визуальная интерполяция допустима только если:

- визуально отличена;
- не участвует в statistics/baseline/events.

---

## 26. Smoothing

Default scientific chart не должен требовать smoothing сверх bin aggregation.

Если smoothing предлагается:

- optional;
- visual only;
- labeled;
- raw/aggregated values remain accessible.

Не использовать smoothing для alarms/baseline/anomaly logic без отдельной validation.

---

## 27. Baseline maturity

Не показывать P10–P90 как устойчивый reference сразу после создания профиля.

UI:

```text
Baseline собирается
2 h 14 min valid data
```

Позже:

```text
Baseline готов
```

Не выдумывать универсальную научную длительность.

Maturity criteria должны быть определены через validation.

---

# Архитектура квантилей и производительности

## 28. Нельзя строить long-window quantiles как «квантили квантилей»

Запрещённая архитектура:

```text
minute Q10/Q25/Q50/Q75/Q90
→ median/quantile of these minute quantiles
→ long-window quantile
```

В общем случае:

\[
median(median_1, median_2, ...)\neq median(all\ raw\ samples)
\]

и аналогично для Q10/Q25/Q75/Q90.

Поминутные квантили сами по себе не содержат достаточно информации, чтобы восстановить точные квантили объединённого массива.

---

## 29. Short windows: exact quantiles

Для коротких интервалов, где raw data доступны без проблем производительности:

```text
compute exact quantiles from raw samples
```

Это source of truth для визуального/тестового сравнения.

---

## 30. Long windows: mergeable quantile sketch

Для длинных периодов использовать mergeable approximate quantile structure.

Допустимые кандидаты:

```text
KLL sketch
t-digest
```

Выбор алгоритма должен быть оформлен ADR/technical note.

Требования:

- mergeable;
- bounded memory;
- documented error behavior;
- deterministic enough for tests;
- algorithmVersion stored;
- raw data remain source of truth.

### Предпочтение

Если основная цель — общие quantiles по всему распределению:

```text
KLL
```

является сильным кандидатом.

Если особенно важна точность хвостовых quantiles:

```text
t-digest
```

может быть полезен.

Не выбирать алгоритм только по простоте реализации.

---

## 31. Предагрегация

Для каждого фиксированного базового периода (например, 1 минута) хранить не только scalar quantiles.

Рекомендуемая запись:

```text
minuteStart
count
min
max
sum?              // если нужен mean
sumSquares?       // если нужен SD
quantileSketchBlob
firstSampleTime
lastSampleTime
extreme timestamps
```

Тогда для длинного окна:

```text
merge sketches
→ approximate Q10/Q25/Q50/Q75/Q90
```

а не:

```text
quantile(quantile_i)
```

---

## 32. Пометка approximate quantiles

На длинных временных диапазонах, если используются sketches:

```text
quantiles = approximate
```

Не обязательно постоянно писать это крупно в UI.

Но:

- в Research details;
- export metadata;
- developer diagnostics

должно быть указано:

```text
method: KLL / t-digest
algorithm version
configured accuracy/compression
```

---

## 33. SQLite

Не исходить из предположения:

```text
SQLite никогда не умеет median()
```

В современных SQLite существует percentile extension, но его наличие зависит от конкретной сборки/платформы.

Для Android implementation:

- сначала проверить фактическую SQLite/API возможность проекта;
- не строить архитектуру на extension, которого может не быть;
- даже наличие median()/percentile() само по себе не решает проблему миллионов raw rows.

Предагрегированные mergeable sketches нужны прежде всего ради масштабируемости и повторного объединения длинных окон.

---

## 34. Performance target

Окно 30 дней не должно требовать чтения миллионов raw rows при каждом первом рендере.

Цель:

```text
short window  → raw exact path
long window   → pre-aggregated sketch path
```

При этом должен существовать diagnostic режим для сравнения:

```text
approximate long-window result
vs
exact raw result
```

на validation dataset.

---

# Formal anomaly testing

## 35. До формального теста

Разрешены descriptive statements:

```text
median shifted
spread wider
outside historical P90
short spike detected
```

Не выводить:

```text
+4.2σ
statistically significant
```

из простого quantile overlap.

---

## 36. Формальный current-vs-baseline test

Перед введением сильного statistical claim необходимо:

1. сформулировать null hypothesis;
2. выбрать statistic, соответствующий данным;
3. определить assumptions;
4. проверить autocorrelation;
5. измерить false-positive rate на стационарных RC110 recordings;
6. измерить detection power на контролируемых изменениях;
7. учитывать repeated/continuous testing.

Continuous scanning без correction/validation может генерировать случайные «аномалии».

---

# Полевое тестирование

## 37. Validation dataset

Перед Stable release проверить:

### A. Stationary background

RC110 неподвижен несколько часов/суток.

Проверить:

- baseline stability;
- false anomaly rate;
- quantile stability;
- отсутствие artificial events из-за binning.

### B. Controlled step

Повторяемо приблизить/удалить известный источник/объект.

Проверить:

- temporal response;
- episode boundaries;
- отсутствие скрытой smoothing delay.

### C. Short transient

Кратковременно приблизить источник.

Проверить:

- spike остаётся видимым на 15 min, 1 h, 24 h, 7 d;
- long-window quantile envelope не скрывает event marker.

### D. BLE interruption

Проверить:

- graph gap;
- stale indicator;
- old value not shown as live.

### E. Different zoom/bin widths

Один и тот же event должен сохранять один и тот же scientific classification.

### F. Outliers

Проверить:

- median/quantiles robust;
- raw spike visible;
- extrema preserved.

### G. Exact vs sketch

Для одного и того же большого dataset:

```text
exact raw quantiles
vs
KLL/t-digest quantiles
```

Измерить фактическую ошибку approximation.

---

## 38. Unit tests

Минимум:

```text
median
quantiles
MAD
IQR
FD histogram
FD IQR=0 fallback
histogram clamp
bin boundaries
empty bin
single-sample bin
missing intervals
out-of-order samples
duplicate timestamps
timezone changes
baseline exclusion
profile switch
manual profile lock
stale stream
extreme numeric values
quantile sketch merge
exact-vs-approx quantile tolerance
event preservation across zoom
```

Использовать deterministic reference datasets.

---

## 39. Terminology

Использовать:

```text
Мощность дозы
Медиана
Исторический диапазон P10–P90
Разброс измерений
SD
MAD
Статистическая неопределённость счёта
Неопределённость прибора
Baseline
Текущий интервал
В обычном диапазоне этого профиля
```

Не использовать без строгого определения:

```text
σ
норма
безопасно
допустимо
точность
достоверность 98%
×4.8 к привычному
```

---

## 40. Recommended visual encoding

```text
Alarm threshold
    red dashed line

Historical baseline P10–P90
    neutral gray band

Historical baseline median
    subtle gray dashed line

Current bin Q10–Q90
    light cyan envelope

Current bin Q25–Q75
    darker cyan envelope

Current bin median
    solid cyan line

Raw samples
    low-opacity dots

Transient/extreme event
    discrete marker or episode region
```

Не использовать цвет как единственный носитель смысла.

---

## 41. Scientific release gate

Перед Stable release:

```text
[ ] Every band has an exact mathematical definition.
[ ] Median line is paired with quantile envelopes, not mean±SD.
[ ] No observed spread is labeled as uncertainty.
[ ] No historical percentile is described as a safety threshold.
[ ] CPS is never converted to dose rate with a universal constant.
[ ] Min/max are not rendered as a pseudo-confidence band.
[ ] Short transient events remain discoverable on long time scales.
[ ] Long-window quantiles are not computed as quantiles-of-quantiles.
[ ] Long-window approximate quantiles use a documented mergeable sketch.
[ ] Exact vs approximate quantile error is validated.
[ ] Missing BLE data create gaps.
[ ] Raw data are preserved.
[ ] Binning does not change event classification.
[ ] Baseline excludes Search/Research/anomaly intervals.
[ ] Trend estimator is explicitly defined.
[ ] Any significance statement has a documented statistical test.
[ ] Continuous anomaly testing has false-positive validation.
[ ] UI can explain every derived metric via “Почему?”.
```

Если пункт не выполнен — функция остаётся Experimental или claim убирается из Stable UI.

---

## 42. Приоритет внедрения

### P0 — исправить сейчас

```text
median + Q25–Q75 + Q10–Q90
remove mean±SD visual band
remove min–max fill
rename σ → SD/MAD with units
rename ×4.8 к привычному → ×4.8 к P90 профиля
status → В обычном диапазоне этого профиля
```

### P1 — архитектура данных

```text
raw immutable
minute preaggregation
mergeable quantile sketch
exact short-window path
approximate long-window path
extrema/event preservation
```

### P2 — статистика

```text
Theil–Sen trend
FD histogram + clamp/fallback
formal anomaly test
false-positive validation
```

### P3 — полевая валидация

```text
stationary background
controlled source step
short transient
BLE outage
zoom invariance
exact vs sketch
```

---

## Итоговый принцип

График должен отвечать на четыре разных вопроса:

```text
Что реально измерял прибор?
Как распределялись измерения внутри окна?
Как это соотносится с историческим профилем?
Было ли отдельное событие, которое нельзя скрыть агрегацией?
```

Ни одна полоса на графике не должна существовать только потому, что она «красиво выглядит».
У каждой линии, заливки, маркера и числа должна быть точная математическая семантика.
