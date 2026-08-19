# History: redesign of anomaly/event logging

## Problem

The current History screen is overloaded with dozens of nearly identical `Отклонение` entries. Many of them correspond to ordinary fluctuations around the local baseline, for example approximately `0.14–0.17 µSv/h` when the usual value is around `0.16 µSv/h`.

This makes History function like a raw detector/debug log instead of a useful measurement journal.

The main architectural problem is that a user-visible history item appears to be created from each detector trigger or short-lived deviation instead of from a confirmed semantic event.

## Goal

History must represent meaningful measurement entities and confirmed events, not every individual detector trigger.

Use this principle:

> History is a semantic event log, not a detector/debug log.

A raw detector trigger may update internal state, but it must not automatically create a new user-visible History item.

---

## Top-level History structure

Keep the existing semantic categories, but make them consistent:

- All
- Sessions
- Routes
- Spectra
- Products
- Events

The `All` tab should contain only meaningful records.

Do not flood `All` with repeated low-value deviations.

---

## Replace the single generic "Отклонение" concept

Do not use `Отклонение` as a universal user-facing event name.

It currently mixes three different concepts:

1. numerical difference from baseline;
2. statistically confirmed change;
3. threshold exceedance.

These are not equivalent.

Use separate user-facing event types.

### Normal variation

Ordinary statistical fluctuation around baseline.

Example:

- baseline: `0.16 µSv/h`
- observed values: `0.14, 0.15, 0.16, 0.17`

This is not automatically a user event.

Default behavior:

- do not create a History item;
- do not show a warning icon;
- keep the data available in the graph and internal analysis.

If needed, this may be described inside detailed analytics as:

`Обычные колебания`

### Confirmed level change

Create only when the detector has enough evidence that the level changed and the change persisted according to the detector logic.

User-facing name:

`Изменение уровня`

Example:

**Изменение уровня**  
14:31–14:47 · 16 мин  
обычно 0,16 → 0,23 мкЗв/ч  
↑ примерно 1,4×

The exact statistical method must come from the detector implementation. Do not invent arbitrary percentage-only thresholds merely for UI behavior.

### Threshold exceedance

This is a separate event type.

User-facing name:

`Превышение порога`

Use the warning symbol only here, or for another explicitly high-priority safety condition.

Example:

**⚠ Превышение порога**  
15:42 · максимум 0,37 мкЗв/ч  
порог L1: 0,30 мкЗв/ч  
`На графике ›`

A difference from baseline is not automatically a threshold exceedance.

---

## Event lifecycle

Implement an event lifecycle so repeated detector hits become one continuous episode.

Suggested conceptual state machine:

```text
NORMAL
  ↓ candidate detected
CANDIDATE
  ↓ change confirmed
ACTIVE_EVENT
  ↓ readings return toward normal
RECOVERY
  ↓ recovery confirmed
CLOSED_EVENT
```

This is a conceptual model. Adapt names to the existing architecture rather than duplicating state unnecessarily.

### Behavior while an event is active

When the detector remains in the same event:

Do not create another History item.

Update the existing event instead.

The event should accumulate at least:

- start time;
- last update time;
- end time when closed;
- duration;
- minimum;
- maximum;
- representative central value;
- baseline/reference value;
- ratio or difference versus baseline;
- detector confidence/statistical status where available;
- threshold information if applicable.

### Hysteresis / recovery

The start and end conditions should not cause rapid event reopening near a boundary.

The detector/event layer needs recovery logic or hysteresis so that one physical episode does not become:

```text
event started
event closed
event started
event closed
...
```

Do not add arbitrary constants just to make the UI look cleaner. Use the detector's validated statistical logic, or explicitly introduce well-documented engineering parameters where necessary.

---

## History aggregation

Repeated detections belonging to the same episode must collapse into a single record.

Current undesirable result:

```text
14:03 Отклонение
14:08 Отклонение
14:12 Отклонение
14:15 Отклонение
14:19 Отклонение
...
```

Desired result:

```text
Изменение уровня
14:03–14:21 · 18 мин
0,14–0,17 мкЗв/ч
обычно 0,16 мкЗв/ч
```

If the detector concludes that this interval is just normal statistical fluctuation, there should be no event item at all.

---

## Recommended History visual hierarchy

### Sessions

Example:

**Дом** ● идёт  
с 11:26 · 3 ч 42 мин  
обычно 0,16 мкЗв/ч

### Routes

Example:

**Маршрут · 10:06**  
2 ч 02 мин · 4,9 км  
ср 0,11 · макс 0,26 мкЗв/ч

### Spectra

Keep the spectrum entry compact:

**Спектр**  
19 авг 12:40 · 6 ч 14 мин  
Дом

### Product measurements

Use the product name as the main title where available:

**Калийная соль · 100 г**  
19 авг 13:20 · 18 мин  
result summary

### Confirmed event

**Изменение уровня**  
14:31–14:47 · 16 мин  
0,16 → 0,23 мкЗв/ч  
`На графике ›`

### Threshold event

**⚠ Превышение порога**  
15:42 · максимум 0,37 мкЗв/ч  
L1 0,30  
`На графике ›`

---

## Warning color and icon rules

Do not use an orange warning triangle for ordinary fluctuations.

Suggested semantic usage:

- neutral/black: normal records;
- green: active/normal operating state;
- amber: attention-worthy but non-critical confirmed change;
- red or stronger alarm semantics: only where current alert policy actually requires it;
- warning triangle: threshold exceedance or another explicit alarm-class event.

Color must represent semantics, not simply `current != baseline`.

---

## Event details screen

Tapping a confirmed event should open a compact event detail screen.

Show:

- event type;
- start/end;
- duration;
- baseline;
- observed range;
- representative value;
- maximum;
- threshold if relevant;
- graph positioned on the event;
- spectrum comparison if data exist;
- profile/location context if relevant.

Avoid long explanatory paragraphs when `screen explanations` are disabled.

Move methodology and educational text into Help / explanation mode.

---

## "На графике"

Every event should have a direct action to open the shared full-screen chart focused on that event interval.

The chart must receive:

- start timestamp;
- end timestamp;
- relevant metric;
- event marker/range.

Do not create a separate chart implementation for History events.

Reuse the shared chart engine.

---

## Internal detector log

If raw detector triggers are useful for diagnostics, keep them separately from user History.

Possible internal/debug fields:

- trigger timestamp;
- detector rule;
- input statistics;
- candidate score;
- baseline snapshot;
- raw reason;
- state transition.

This data may be exported in a debug archive, but should not be rendered as normal History entries.

---

## Notification behavior

One physical/semantic event should produce at most one active user notification.

If the event continues:

- update the existing notification;
- do not create repeated notifications for every detector refresh.

If the event closes:

- update/finalize the existing record;
- optionally provide the final duration or maximum.

History and notifications should refer to the same semantic event ID.

---

## Data model

Prefer a semantic event entity rather than storing only detector triggers.

Example conceptual model:

```kotlin
MeasurementEvent(
    id,
    type,
    profileId,
    sessionId,
    startAt,
    endAt,
    status,
    baselineValue,
    representativeValue,
    minValue,
    maxValue,
    ratioToBaseline,
    thresholdLevel,
    confidence,
    sampleCount
)
```

Do not copy this structure blindly if equivalent entities already exist.

First inspect the current data model and extend the existing model minimally.

---

## Event identity

An active event must have a stable ID.

New measurements belonging to the same episode must update that same event.

Do not key events by display timestamp alone.

---

## Migration / existing noisy History

Existing stored `Отклонение` records may already contain a large number of low-value entries.

Do not silently reinterpret scientific data.

Recommended migration approach:

1. preserve raw measurements;
2. preserve old event records if needed for compatibility;
3. stop displaying obsolete raw trigger records in the default semantic History view;
4. where reliable grouping is possible, expose a migration path or reconstructed semantic view;
5. do not delete user measurement history solely to clean the UI.

If old records cannot be grouped reliably, keep them available only in a legacy/debug view rather than pretending reconstructed events are scientifically exact.

---

## Acceptance criteria

### Normal baseline fluctuation

Given a stable profile around `0.16 µSv/h` and ordinary values such as `0.14–0.17 µSv/h`:

- History must not create dozens of warning events;
- no warning triangle should appear merely because a value differs from `0.16`;
- raw data remain visible in the chart.

### One continuous change

If a confirmed change persists for 20 minutes:

- exactly one event appears;
- its duration updates while active;
- its min/max/representative values update;
- no duplicate event is created every analysis cycle.

### Recovery

When the signal returns to baseline and recovery is confirmed:

- the active event closes;
- end time is saved;
- one final event remains in History.

### Threshold crossing

If L1 is actually exceeded:

- create/update a separate threshold event;
- show the configured threshold;
- link to the graph;
- warning semantics are allowed.

### UI

The `All` History feed must remain readable after many hours of continuous measurement.

A day of stable background measurement must not result in tens or hundreds of near-identical `Отклонение` rows.

---

## Important scientific constraint

Do not solve this only by adding an arbitrary rule such as:

```text
ignore anything below ±10%
```

or

```text
only show changes longer than 5 minutes
```

unless such values are intentionally introduced, documented, and scientifically/engineering-wise justified.

The correct separation is:

```text
raw measurement
→ detector result
→ candidate
→ confirmed semantic event
→ History
```

not:

```text
raw measurement
→ if different from baseline
→ History row
```

The detector should account for the measurement statistics already available in the application: count-rate data, integration time, baseline distribution and the existing event/change-detection model.

---

## Implementation instruction for Claude

Before editing:

1. inspect the current detector, anomaly/event model, History repository and notification flow;
2. identify exactly where every `Отклонение` History item is created;
3. determine whether the current code saves raw detector triggers, candidate changes, or confirmed events;
4. reuse existing statistical logic instead of inventing a second detector;
5. introduce one semantic event lifecycle shared by History and notifications;
6. keep raw measurement storage unchanged;
7. do not alter parts that are already more correct than this specification.

After implementation:

- run unit tests;
- add state-transition tests;
- add a test with hours of stable synthetic background;
- verify that stable background produces zero or near-zero semantic events;
- add a sustained-change test;
- add a recovery test;
- add an L1 threshold test;
- verify that one event never becomes dozens of History rows.
