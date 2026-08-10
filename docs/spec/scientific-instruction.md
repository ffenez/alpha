# CLAUDE.md --- Radiacode 110 Companion App

> This file is the implementation specification for Claude Code. Treat
> scientific correctness, traceability, conservative interpretation,
> local-first privacy, and preservation of raw measurements as hard
> requirements.

## How to use this specification

-   Inspect the existing repository before changing architecture.
-   Preserve working functionality unless this specification explicitly
    requires a change.
-   Implement features incrementally and keep raw device data separate
    from derived analytics.
-   Do not invent detector capabilities, conversion factors, safety
    thresholds, isotope confidence values, or statistical meanings.
-   Any research feature must satisfy the **Scientific release gate** in
    this document before being presented as a stable factual conclusion.
-   When a formula or algorithm has assumptions, encode those
    assumptions in documentation/tests and expose relevant limitations
    in the UI.
-   Prefer explainable outputs over opaque scores.
-   If the current RC-110 protocol/API does not expose data required by
    a feature, do not simulate it; mark the feature as unsupported until
    the required data are available.

------------------------------------------------------------------------

Итоговая продуктовая и научно-техническая инструкция Автоматические
профили • baseline • аналитика • спектрометрия • Research Mode • научная
валидация

Цель: превратить поток данных Radiacode 110 в понятный повседневный
мониторинг и воспроизводимый исследовательский инструмент, не выдавая
статистические или спектральные гипотезы за установленные факты.

## 1. Проверенная аппаратная основа

Radiacode 110 использует сцинтиллятор CsI(Tl) 14×14×14 мм (\~2,74 см³),
имеет заявленный энергетический диапазон 0,02--3 MeV, энергетическое
разрешение 8,4 ± 0,3% FWHM для Cs-137 и чувствительность 77 cps на 1
µSv/h для Cs-137. Эти характеристики подтверждают, что устройство
пригодно не только для отображения мощности дозы и скорости счёта, но и
для энергетического спектрального анализа. \[R1\]

Важно: 77 cps = 1 µSv/h --- характеристика, указанная для Cs-137, а не
универсальный коэффициент перевода CPS→µSv/h для неизвестного спектра.
Приложение не должно выполнять собственный фиксированный перевод CPS в
дозу для произвольного излучения. \[R1\]

## 2. Архитектурный принцип достоверности

-   MEASURED --- величины, полученные непосредственно от прибора/потока
    измерений.

-   CALCULATED --- детерминированные расчёты из измеренных данных.

-   STATISTICALLY DETECTED --- вывод, зависящий от статистической
    модели.

-   INTERPRETATION / CANDIDATE --- физическая интерпретация, например
    возможный радионуклид.

UI не должен объединять эти четыре уровня в одно категоричное сообщение.
Например, «пик статистически значим» не равнозначно «изотоп обнаружен»,
а «спектр изменился» не равнозначно «радиация опасна».

## 3. Автоматические профили и контекст

Основная сущность --- Profile / Measurement Context. `Дом` не является
специальным GPS-режимом. Это именуемый пользователем профиль с
собственной историей, baseline и, при желании, правилами автоматической
активации.

### 3.1. Настройки профилей

-   Settings → Profiles: создать, переименовать, выбрать иконку,
    архивировать профиль.

-   Привязать к профилю одну или несколько известных Wi‑Fi сетей.

-   Включить/выключить Auto activate и Baseline learning.

-   Поддержать профили: Дом, Офис, Дача, Родители, В пути, Без места и
    произвольные пользовательские.

-   Поддержать вложенные профили: Дом / Общий, Спальня, Кухня, Гостиная
    и т. п.

### 3.2. Wi‑Fi --- основной автоматический сигнал

Нормальный автоматический сценарий: известный домашний Wi‑Fi → `Дом`;
сеть исчезла и не вернулась после grace period → `В пути`; появилась
сеть офиса → `Офис`. Постоянный GPS для этого не нужен.

Ручной выбор всегда имеет приоритет. При `Дом / Спальня · вручную` Wi‑Fi
не должен немедленно переключать профиль обратно на `Дом`. Нужна команда
`Вернуться к авто`.

### 3.3. GPS

GPS используется только когда пользователь явно запускает Map Recording
или функцию, которой нужны координаты. После остановки записи
высокочастотная геолокация прекращается.

### 3.4. State machine

Рекомендуемые состояния:

-   AUTO_KNOWN(profile) --- известный Wi‑Fi уверенно соответствует
    профилю.

-   AUTO_UNCERTAIN(previousProfile) --- Wi‑Fi исчез недавно; baseline
    временно заморожен.

-   AUTO_TRANSIT --- известной сети нет после grace period; активен
    `В пути`.

-   NO_CONTEXT --- контекст определить нельзя; активен `Без места`.

-   MANUAL(profile) --- профиль явно выбран пользователем.

## 4. Baseline и защита данных

Baseline --- статистический профиль обычной радиационной среды
конкретного контекста. Он должен формироваться только из пригодных
измерений и никогда не поглощать аномалию автоматически.

### 4.1. Базовые статистики

Для профиля хранить median, P10/P25/P75/P90, длительность валидных
измерений, число выборок и при необходимости MAD.

MAD = median(\|xᵢ − median(x)\|).

Квантили и медиана устойчивее к редким выбросам, чем min/max и среднее;
приложение может показывать mean/σ дополнительно, но не должно
предполагать нормальность любого долговременного фона.

### 4.2. Admission pipeline

1.  Профиль разрешает baseline learning.

2.  Контекст достаточно надёжен.

3.  Поток данных свежий.

4.  Не запущен Search / source experiment / A-B experiment.

5.  Интервал не находится в quarantine после обнаруженной аномалии.

6.  Статистика измерения пригодна.

7.  Пользователь не заморозил baseline вручную.

Если условие не выполнено: raw data сохраняются, но интервал получает
baselineExcluded(reason). Это предотвращает circular learning, когда
аномалия постепенно становится «новой нормой».

## 5. Научная основа счётных данных

Для низких и умеренных скоростей счёта независимые радиоактивные события
стандартно моделируются пуассоновской статистикой: N \~ Poisson(λ).
Тогда E\[N\]=λ, Var(N)=λ и для достаточно большого N стандартная
неопределённость счёта приближённо σ_N≈√N. NIST подтверждает
применимость этой основы для nuclear counting, одновременно подчёркивая
отклонения при dead time/pile-up. \[R2\]\[R3\]

Скорость счёта: R = N / t.

При пуассоновском приближении: σ_R ≈ √N / t.

При больших скоростях счёта dead time и pile-up изменяют как наблюдаемую
скорость, так и статистику; поэтому приложение не должно без проверки
применять √N во всех режимах. \[R2\]

## 6. Доза и мощность дозы

Накопленная доза: D = ∫ Ḋ(t) dt.

При практически постоянной мощности дозы: D ≈ Ḋ × t.

Dose projection допустим только как математическая экстраполяция:
`если средняя измеренная внешняя фотонная мощность дозы останется такой же`.
Не называть это полной годовой эффективной дозой человека.

## 7. Энергетические окна

Для энергетического диапазона \[E₁,E₂\]: C = Σ Nᵢ, где суммируются
каналы внутри выбранного окна.

Скорость в окне: R_window = C / t.

Можно показывать изменение отдельных окон, например 100--300, 300--700,
700--1500 keV, но сами границы являются параметрами анализа, а не
универсальными физическими категориями.

Спектральное отношение, например R_low/R_high, допустимо как
описательный индекс состава спектра, но не как мера опасности.

## 8. Сравнение спектров

Спектры разной длительности сначала приводятся к сопоставимой форме,
например counts/s/channel: rᵢ=Nᵢ/t. Допустимы также area-normalized
представления для анализа формы, если метод нормализации явно указан.

Для статистического сравнения предпочтительна модель, соответствующая
пуассоновским count data. При больших counts возможны χ²-подобные
методы; при малых counts лучше использовать Poisson likelihood /
likelihood-ratio подход. Конкретный метод должен быть валидирован на
RC110 и документирован.

Не вводить произвольное `98.4% spectrum similarity`, пока не определены
метрика, её статистический смысл и пороги. До валидации безопаснее UI:
`consistent`, `changed`, `strong evidence of change`.

## 9. A/B анализ и background subtraction

Для независимых измерений сигнал после вычитания фона строится с учётом
времени накопления. В простом случае net counts = gross counts − scaled
background.

IAEA приводит стандартную логику net peak area N = G − B и расчёт
неопределённости с учётом статистики gross/background. Это должно быть
основой peak/background анализа, а не простое визуальное вычитание
кривых. \[R4\]

Research A/B должен сравнивать: dose rate, total CPS, energy windows,
полный спектр и статистическую значимость различий, сохраняя параметры и
длительности A и B.

## 10. Peak analytics

Для подходящего изолированного пика можно оценивать centroid, net area,
FWHM, resolution и uncertainty. Локальная модель может включать пик +
continuum/background; модель пика не должна предполагаться идеально
гауссовой для всех энергий и всех перекрывающихся линий.

Для гауссовой аппроксимации: FWHM = 2√(2 ln 2)·σ_E ≈ 2.355·σ_E.

Энергетическое разрешение: Resolution = FWHM / E_peak × 100%.

RC110 заявлен с 8,4 ± 0,3% FWHM для Cs-137; это характеристика при
конкретной эталонной линии, а не универсальная ширина каждого пика во
всём диапазоне. \[R1\]

## 11. Значимость пика

Показывать `x σ` можно только если используемый statistic действительно
интерпретируется как число стандартных отклонений. Нельзя
переименовывать произвольный anomaly score в σ.

Для слабых сигналов на фоне обычное propagation-of-errors может иметь
ограничения; NIST рассматривает специальные интервальные/likelihood
методы для weak Poisson signals in background. \[R5\]

## 12. Идентификация радионуклидов

Nearest-line lookup недостаточен. Candidate matching должен учитывать
несколько совместимых линий, остаток по энергии, uncertainty калибровки,
статистическую значимость, возможные интерференции и доступность
ожидаемых линий в диапазоне прибора.

Разрешённые формулировки: `possible match`,
`weak/possible/strong candidate`, `compatible lines`.

Запрещено автоматически писать `detected`, если вывод основан только на
одном близком пике.

Численный `93% confidence isotope` не вводить до построения и валидации
полноценной вероятностной модели с detector response, calibration
uncertainty и interfering lines.

## 13. Spectral anomaly и radiation fingerprint

Для зрелого профиля хранить нормированный baseline spectrum и
распределения dose/CPS. Приложение должно уметь обнаружить ситуацию
`Dose normal · Spectrum changed`.

Fingerprint используется как вторичный guard: сильное устойчивое отличие
может остановить обучение домашнего baseline, но похожий спектр не
доказывает, что пользователь физически дома.

## 14. Change-point и события

Change-point detection --- валидная аналитическая задача, но конкретный
алгоритм (CUSUM, Bayesian online change-point, likelihood test и т. п.)
должен быть выбран и проверен на реальных RC110 time series.

Событие хранит pre/event/post интервалы, профиль, dose/CPS, energy
windows, спектр и используемую статистику.

Событие `Environment changed` не является alarm `Danger`.

## 15. Measurement quality и «сколько ещё измерять»

Quality должен быть объяснимым: total counts, duration, uncertainty, fit
quality, stability, доступность baseline. Не использовать непрозрачный
балл 86/100 без опубликованной формулы.

Оценка оставшегося времени возможна только относительно конкретной
статистической цели и при допущении примерно постоянной скорости счёта.
Для detection limits/MDA существуют специальные формализованные методы;
универсальный таймер `ещё 12 минут` без постановки задачи недостоверен.
\[R6\]

## 16. Исследовательские сценарии

Background vs Object: A/B измерение с одинаково документированной
геометрией и нормализацией по времени.

Place vs Place: Сравнение распределений dose/CPS, energy windows и
нормированных спектров двух профилей.

Distance: Серия измерений на известных расстояниях; сравнение с
идеализированной зависимостью I∝1/r² только с предупреждением о
геометрии, рассеянии и фоне.

Shielding: Сравнение одной конфигурации без/с материалом; не выводить
универсальные коэффициенты ослабления из неконтролируемой домашней
геометрии.

Long-term: 24 h / 7 d / 30 d: median, quantiles, accumulated dose, CPS
distribution, spectrum evolution, events.

## 17. Главный экран

Главная должна оставаться простой и не превращаться в Research
dashboard. Рекомендуемый слой:

⌂ Дом · авто 0.15 µSv/h Обычный для этого baseline 24.6 CPS Сегодня:
накопленная доза Spectrum: без статистически значимых изменений

По нажатию `Почему?` показать данные, которые привели к выводу: диапазон
baseline, CPS, наличие/отсутствие spectral anomaly, длительность
baseline и исключённые интервалы.

## 18. Поиск

Search ориентирован на быстрое относительное обнаружение изменения:
крупный CPS, local background, изменение %, неопределённость, 30--60 s
график, звук/вибрация. Запуск Search автоматически исключает интервал из
обучения baseline.

## 19. Spectrum / Research

-   Raw/normalized spectrum; log/linear.

-   Background subtraction с сохранением метода.

-   Peak centroid, net area, FWHM, resolution, uncertainty.

-   Energy windows.

-   A/B comparison.

-   Spectrogram.

-   Candidate isotope matching с multi-line evidence.

-   Calibration-health diagnostics.

-   Экспорт raw + processing metadata.

## 20. История как научный журнал

Каждая сессия: имя, профиль, auto/manual context, время,
baselineIncluded yes/no, причина исключения, dose/CPS statistics,
спектр, события, optional GPS track. Пользователь может позже исправить
профиль и решить, включать ли сессию в baseline.

## 21. Data-stream health

Всегда отслеживать freshness. Если BLE/поток перестал обновляться,
старое число нельзя продолжать показывать как текущее. UI:
`Data stream interrupted · last valid sample 38 s ago`; baseline
learning stops.

## 22. Raw data и воспроизводимость

-   Raw measurement data не перезаписываются сглаженными.

-   Derived analyses хранят algorithmVersion и параметры.

-   При обновлении алгоритма старые raw data можно переанализировать.

-   Экспорт указывает нормализацию, background method, calibration
    metadata и версии алгоритмов.

## 23. Что НЕ реализовывать как установленный факт

-   Универсальный CPS→µSv/h коэффициент.

-   Radon concentration в Bq/m³ из RC110 gamma spectrum.

-   Isotope detected по одному nearest peak.

-   Процент isotope confidence без валидированной вероятностной модели.

-   Процент spectrum similarity без определённой и валидированной
    метрики.

-   Любую статистическую аномалию как `опасность`.

-   Полную индивидуальную годовую дозу из внешней мощности дозы RC110.

-   Постоянный GPS ради автоматического профиля.

## 24. Научный release gate

Каждая новая исследовательская функция до выхода в Stable должна иметь:

1.  Formula / statistical model.

2.  Assumptions.

3.  Units and dimensional checks.

4.  Reference implementation or authoritative literature basis.

5.  Validation dataset: synthetic + recorded RC110 data where
    applicable.

6.  Known limitations.

7.  Unit tests and edge cases.

8.  Algorithm version.

9.  User-facing explanation of what the result does and does not mean.

## 25. Приоритет реализации

## 26. Итоговая продуктовая логика

Автоматизация места: Known Wi‑Fi / manual profile → Active context →
Correct baseline → Analysis.

Научная интерпретация: Measured → Calculated → Statistically detected →
Candidate interpretation.

Главный принцип: приложение должно быть максимально автоматическим в
быту, но максимально консервативным в научных выводах.

## Источники и проверка

\[R1\] Radiacode, Technical Specification --- RC110: CsI(Tl), 14×14×14
mm, 0.02--3 MeV, 8.4±0.3% FWHM for Cs-137, 77 cps = 1 µSv/h for Cs-137.
https://radiacode.com/docs/en/100-series/devices/100-series-introduction/technical-specification

\[R2\] Pommé S., Keightley J., Fitzgerald R.P. Uncertainty of nuclear
counting. Metrologia 52 (2015) S3--S17. NIST publication page; discusses
Poisson counting, dead time and pile-up.
https://www.nist.gov/publications/uncertainty-nuclear-counting

\[R3\] Klouda G.A., Currie L.A., Eijgenhuijsen E.M. On the Validity of
the Poisson Hypothesis for Low-Level Counting. NIST publication page.
https://www.nist.gov/publications/validity-poisson-hypothesis-low-level-counting-investigation-distributional

\[R4\] IAEA, Investigation of Uncertainty Sources in the Determination
of Gamma --- net peak area/background and uncertainty expressions.
https://www-ns.iaea.org/downloads/rw/ppss/quality-management/uncertainty-gamma-measurement.pdf

\[R5\] Coakley K.J., Splett J.D., Simons D.S. Frequentist coverage
properties of uncertainty intervals for weak Poisson signals in the
presence of background. NIST.
https://www.nist.gov/publications/frequentist-coverage-properties-uncertainty-intervals-weak-poisson-signals-presence

\[R6\] INIS/IAEA record: Minimum detectable activity, systematic
uncertainties, and the ISO 11929 standard --- discusses Currie MDA and
ISO 11929 characteristic limits.
https://inis-temp.iaea.org/search/search.aspx?orig_q=author%3A%22Kirkpatrick%2C+J.M.%22

| Этап \| Функции \| Статус \|

| --- \| --- \| --- \|

| 1 \| Profiles + Wi‑Fi auto context + baseline admission + stale stream
  protection \| Можно реализовывать сразу \|

| 2 \| Median/quantiles/MAD, dose integration, energy windows,
  normalized spectra, A/B \| Научная основа стандартна \|

| 3 \| Peak centroid/net area/FWHM/resolution + background uncertainty
  \| Реализовать и проверить на RC110 \|

| 4 \| Spectral anomaly + change-point + event capture \| Нужна
  выбранная и валидированная модель \|

| 5 \| Multi-line isotope candidates + calibration health \|
  Консервативно, с validation dataset \|

| 6 \| Measurement-time estimate / detection limits / numerical
  confidence \| Только после строгой валидации \|
