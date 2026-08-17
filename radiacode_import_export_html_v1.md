# RadiaCode — Import / Export / Backup / HTML Reports

## Цель

Добавить полноценную систему переноса и экспорта данных, не превращая настройки в техническую панель.

Нужно решить **три разные задачи**, и их нельзя смешивать:

1. **Резервная копия приложения**  
   Для переноса/восстановления всей истории и настроек на другом устройстве или после переустановки.

2. **Экспорт исходных данных**  
   Для анализа в других программах: CSV / JSON / GeoJSON / N42 / XML и т. п.

3. **Красивый HTML-отчёт**  
   Для человека: открыть файл в браузере и удобно посмотреть результаты измерения без приложения.

Эти три режима должны иметь разные UX и разные форматы.

---

# 1. Где это находится в настройках

В Settings V2 добавить:

```text
СИСТЕМА

Данные и резервные копии                         >
Диагностика                                      >
О приложении                              0.6.5
```

Экран:

```text
Данные и резервные копии

РЕЗЕРВНАЯ КОПИЯ
Создать резервную копию                          >
Восстановить из копии                            >

ЭКСПОРТ
Экспортировать данные                            >
HTML-отчёты                                      >

ХРАНЕНИЕ
История измерений             Без ограничения   >
Размер данных                         284 МБ
```

Не использовать формулировки:

```text
dump
database backup
сырые таблицы
Room export
```

Это инженерные детали.

---

# 2. Полная резервная копия

## Пользовательская модель

Пользователь нажимает:

```text
Создать резервную копию
```

и получает один файл, например:

```text
RadiaCode-backup-2026-08-17.radbackup
```

Это **внутренний переносимый формат приложения**, не предназначенный для ручного анализа.

### В копию входят

По умолчанию:

```text
✓ настройки приложения
✓ настройки прибора, которые хранит приложение
✓ профили мест
✓ обученный обычный фон
✓ история измерений
✓ сессии
✓ маршруты + GPS-точки + метки
✓ сохранённые спектры
✓ спектрограммы, если они хранятся
✓ эксперименты A/B
✓ пользовательские названия и заметки
✓ спектральные диапазоны
✓ локальные калибровочные/оценочные данные
```

Не включать автоматически:

```text
× кэш карт
× временные файлы
× debug logs
× crash dumps
× системные разрешения Android
× Bluetooth pairing Android
```

Последние два невозможно корректно переносить как обычные настройки приложения.

---

# 3. Формат `.radbackup`

Не делать основной backup простым копированием файла Room DB.

Причина: бинарная копия базы слишком жёстко связана с конкретной версией schema. Room поддерживает миграции схемы, но переносимый пользовательский backup должен иметь собственную версию формата, независимо от версии внутренней БД.

Рекомендуемая структура:

```text
RadiaCode-backup-2026-08-17.radbackup
└── ZIP container
    ├── manifest.json
    ├── settings.json
    ├── profiles.json
    ├── sessions.ndjson
    ├── measurements/
    │   ├── 2026-08.ndjson.zst
    │   └── ...
    ├── routes/
    │   ├── routes.json
    │   └── points.ndjson.zst
    ├── spectra/
    │   ├── index.json
    │   └── binary/...
    ├── spectrogram/
    │   └── slices...
    ├── experiments/
    │   └── experiments.json
    └── checksums.json
```

Расширение может быть `.radbackup`, но внутри — обычный архив с явным manifest.

## `manifest.json`

Минимум:

```json
{
  "format": "radiacode-backup",
  "formatVersion": 1,
  "createdAt": "2026-08-17T02:25:00+03:00",
  "appVersion": "0.6.5",
  "databaseSchemaVersion": 11,
  "deviceModel": "RadiaCode 110",
  "content": {
    "measurements": true,
    "spectra": true,
    "routes": true,
    "experiments": true,
    "settings": true
  }
}
```

`formatVersion` — главное поле для долгосрочной совместимости.

---

# 4. Почему NDJSON / chunked data, а не один гигантский JSON

История может стать большой.

Не строить:

```text
allMeasurements.toList()
→ Gson
→ огромная String
→ ZIP
```

Это даст лишний расход RAM и риск OOM.

Экспортировать потоково:

```text
Room cursor / paging
→ streaming serializer
→ compressed archive entry
```

То же при восстановлении:

```text
archive stream
→ parse chunk
→ transaction batch
→ next chunk
```

Никакой загрузки всей истории в память.

---

# 5. Сжатие

У измерений много повторяющейся структуры, поэтому архив должен хорошо сжиматься.

Минимальный вариант:

```text
ZIP + DEFLATE
```

Если в проекте уже допустима дополнительная зависимость и замеры показывают пользу:

```text
Zstandard
```

для больших потоков measurements/routes/spectrogram.

Не вводить Zstd только «потому что лучше» без измерения размера/скорости на реальном телефоне.

---

# 6. Проверка целостности

Каждый backup должен иметь checksums.

При импорте:

```text
1. открыть manifest
2. проверить formatVersion
3. проверить обязательные файлы
4. проверить checksums
5. проверить структуру данных
6. только после этого начинать восстановление
```

Если архив повреждён:

```text
Резервная копия повреждена
Не удалось проверить measurements/2026-08.ndjson
```

Не делать частичное молчаливое восстановление.

---

# 7. Восстановление: нельзя сразу уничтожать текущую историю

После выбора файла:

```text
Резервная копия
17 авг 2026 · RadiaCode 110
0.6.5
284 МБ

Содержит:
• 1 248 331 измерение
• 42 сессии
• 18 маршрутов
• 27 спектров
• 6 экспериментов
• настройки приложения
```

Дальше:

```text
Как восстановить?

● Объединить с текущими данными
○ Заменить данные приложения
```

### По умолчанию — `Объединить`

Это безопаснее.

---

# 8. Merge strategy

Каждая сущность должна иметь стабильный UUID, не только auto-increment Room ID.

Например:

```text
session.uuid
route.uuid
spectrum.uuid
experiment.uuid
profile.uuid
```

Иначе импорт одного backup дважды создаст дубликаты.

Для измерений нужен стабильный identity key, например комбинация:

```text
deviceInstanceId
sessionUuid / streamUuid
timestamp
sequence
```

или отдельный UUID/monotonic sequence, если это уже доступно.

Правило:

```text
одинаковый UUID → обновить/пропустить согласно revision
новый UUID → добавить
```

Не дедуплицировать измерения только по timestamp: два источника могут честно иметь одинаковое время.

---

# 9. Импорт более новой версии

Если:

```text
backup.formatVersion > app.supportedBackupVersion
```

не пытаться угадать.

Показать:

```text
Эта копия создана более новой версией приложения.
Обновите приложение, чтобы восстановить её.
```

Если версия старая — применить backup migration:

```text
BackupV1 → BackupV2 → BackupV3
```

Отдельно от Room migrations.

---

# 10. Импорт настроек отдельно

Иногда пользователь хочет перенести только UI/preferences.

В `Восстановить из копии` после чтения manifest:

```text
Что восстановить

[✓] Настройки
[✓] Профили
[✓] История измерений
[✓] Спектры
[✓] Маршруты
[✓] Эксперименты
```

Но не показывать этот advanced selection до чтения файла.

---

# 11. Android file UX

Использовать системный Storage Access Framework:

### Export

```text
ACTION_CREATE_DOCUMENT
```

Пользователь сам выбирает:

- Downloads;
- Google Drive;
- Dropbox/другой DocumentsProvider;
- SD-карту;
- любой доступный системному picker storage provider.

### Import

```text
ACTION_OPEN_DOCUMENT
```

Не просить `MANAGE_EXTERNAL_STORAGE`.

Для обычного backup/export приложению не нужен доступ «ко всем файлам».

---

# 12. Progress

Большой backup может идти минуты.

Экран:

```text
Создание резервной копии

История измерений
██████████████░░░ 78 %

920 451 / 1 181 203
```

И ниже текущая стадия:

```text
Сейчас: маршруты
```

Не блокировать UI.

Для длительной операции использовать подходящий background execution mechanism; прогресс должен наблюдаться UI.

Если пользователь уходит с экрана, операция не должна обрываться без причины.

---

# 13. Export Data — отдельная функция

Экран:

```text
Экспортировать данные

Что экспортировать?

Сессии                                      >
Маршруты                                    >
Спектры                                     >
Эксперименты                                >
История измерений                           >
```

После выбора объекта:

```text
Формат

CSV
JSON
HTML
```

Показывать только применимые форматы.

---

# 14. Матрица форматов

| Объект | HTML | CSV | JSON | GeoJSON | N42 | XML |
|---|---:|---:|---:|---:|---:|---:|
| Сессия | ✓ | ✓ | ✓ | — | — | — |
| Маршрут | ✓ | ✓ | ✓ | ✓ | — | — |
| Спектр | ✓ | ✓ | ✓ | — | ✓ | ✓ |
| Эксперимент | ✓ | ✓ | ✓ | — | ✓* | ✓* |
| История измерений | — / пакет | ✓ | ✓ | — | — | — |

`*` — только если в эксперименте сохранены соответствующие spectra и экспортируется их спектральная часть.

HTML — человекочитаемый отчёт.

CSV/JSON/GeoJSON/N42/XML — машинные данные.

Не заменять одно другим.

---

# 15. Главная идея HTML export

HTML должен быть **самодостаточным и открываться двойным тапом/кликом в любом современном браузере**.

По умолчанию:

```text
один .html файл
```

без:

```text
CDN
внешних JS библиотек
Google Fonts
сетевых API
analytics
```

Все CSS и JS inline.

Плюсы:

- работает offline;
- легко отправить;
- не ломается через годы из-за CDN;
- не отправляет данные наружу;
- выглядит одинаково.

---

# 16. Общий дизайн HTML-отчётов

Не делать «распечатку экрана приложения».

Нужен отдельный report design.

Header:

```text
RadiaCode measurement report
Дом · 17 августа 2026

RadiaCode 110
13:10–15:42 · 2 ч 32 мин
```

Hero metrics:

```text
0,154 мкЗв/ч
среднее

0,12–0,21
диапазон

0,39 мкЗв
накопленная доза
```

Далее:

```text
График
Ключевые события
Статистика
Спектр
Метаданные
Примечания
```

Внизу:

```text
Создано RadiaCode Companion 0.6.5
17.08.2026 02:25
```

---

# 17. HTML charts

Не экспортировать графики PNG, если их можно сохранить как вектор.

Предпочтительно:

```text
inline SVG
```

Плюсы:

- резкий текст;
- масштабируется;
- print-friendly;
- можно делать hover/crosshair небольшим встроенным JS.

## В HTML можно сохранить интерактивность

На desktop:

```text
mouse hover → crosshair
wheel / drag → zoom/pan
```

На mobile browser:

```text
tap/drag → crosshair
pinch → optional
```

Но export viewer должен быть лёгким.

Не переносить весь Android ChartEngine в JS.

Достаточно:

```text
hover
crosshair
tooltip
toggle series
reset zoom
```

---

# 18. HTML export сессии

Файл:

```text
Session-2026-08-17-Home.html
```

Структура:

```text
Дом
17 августа · 13:10–15:42

КЛЮЧЕВОЕ
Средняя мощность дозы    0,154 мкЗв/ч
Минимум                  0,12
Максимум                 0,21
Доза                     0,39 мкЗв
Измерений                9 104

МОЩНОСТЬ ДОЗЫ
[interactive SVG graph]

СКОРОСТЬ СЧЁТА
[interactive SVG graph]

ЖЁСТКОСТЬ
[interactive SVG graph]

СОБЫТИЯ
14:03  кратковременное повышение
...

СПЕКТР
[если есть snapshot / accumulated spectrum]

ДЕТАЛИ
профиль: Дом
прибор: RadiaCode 110
...
```

Не показывать пустые разделы.

Если жесткость отсутствует:

```text
не рисовать блок вообще
```

---

# 19. HTML export маршрута

Это один из самых полезных отчётов.

Файл:

```text
Route-2026-08-17-park.html
```

Header:

```text
Маршрут
3,8 км · 1 ч 45 мин · 4 654 измерения
```

Hero:

```text
средняя 0,12
максимум 0,21
доза 0,20 мкЗв
```

## Карта в self-contained HTML

Не полагаться на online OSM tiles как единственный способ отображения.

По умолчанию сделать встроенный **SVG route map**:

- географический bounding box;
- route polyline;
- цвет линии по выбранной radiation scale;
- start/end;
- пользовательские метки;
- high points;
- scale bar;
- north indicator.

Это работает полностью offline.

### Дополнительно

Кнопка:

```text
Открыть координаты на карте
```

может строить внешнюю ссылку только при явном действии пользователя.

Если в будущем появится опция `HTML с картой`, можно создавать ZIP-package с локальными map assets, но это не нужно для V1.

---

# 20. Связь карты и графика

HTML route report:

```text
[SVG карта]
[график мощности дозы по времени]
```

Hover/tap на графике:

```text
→ marker на карте перемещается к соответствующей GPS point
```

Hover/tap маршрута:

```text
→ crosshair графика перемещается к времени точки
```

Это даст реально полезный отчёт, а не статичную картинку.

---

# 21. Экспорт GeoJSON маршрута

Помимо HTML:

```text
Route-....geojson
```

FeatureCollection:

```text
LineString
Point markers
```

Каждая route point при необходимости может иметь:

```json
{
  "timestamp": "...",
  "doseRate": 0.153,
  "cps": 24.8,
  "hardness": 0.61
}
```

Но если точек десятки тысяч, не дублировать тяжёлые properties без нужды.

Можно:

```text
LineString → geometry
measurements → separate CSV/JSON
```

или сделать detailed GeoJSON отдельной опцией.

---

# 22. HTML export спектра

Файл:

```text
Spectrum-2026-08-17-1420.html
```

Самый важный принцип:

**HTML не заменяет N42/XML.**

Рядом с HTML пользователь может экспортировать машинный формат.

## HTML

```text
Спектр
Накопление 126:47:03
11 246 644 импульса

[большой interactive spectrum]

Найденные пики
E       площадь      значимость     возможное совпадение
86,8    ...
571,0   ...
1441,5  ...          K-40
```

Клик по строке:

```text
→ подсвечивает peak на графике
```

Клик по peak:

```text
→ подсвечивает строку
```

---

# 23. Режимы спектра в HTML

Дать переключатель:

```text
Y:
[Лин] [Лог]
```

Опционально:

```text
[Исходный] [Сглаженный]
```

Если есть background spectrum:

```text
[Спектр] [Спектр − фон]
```

При этом обязательно подписать, какой именно фон использован.

---

# 24. Спектр — экспорт исходных данных

Кроме HTML:

### N42

Оставить/развить существующий экспорт N42 как interoperable spectrum format.

### XML

Сохранять существующий XML, если он уже нужен совместимости.

### CSV

Простой аналитический формат:

```text
channel,energy_keV,counts
0,6.9,123
1,9.2,145
...
```

Если spectrum calibration nonlinear:

```text
energy_keV
```

должна быть вычислена тем же calibration model, который использовался при отображении.

---

# 25. HTML export эксперимента

Файл:

```text
Experiment-Coffee-2026-08-17.html
```

Структура:

```text
Эксперимент
Объект / фон

Геометрия
образец на столе, детектор сверху, 5 см

Результат
Различие подтверждено / не подтверждено / недостаточно данных

A
время
импульсы
CPS
спектр

B
время
импульсы
CPS
спектр

СРАВНЕНИЕ СЧЁТА
[chart]
отношение
95 % interval
significance

СПЕКТРАЛЬНОЕ СРАВНЕНИЕ
[overlay A/B spectrum]
[residual / ratio if scientifically valid]

ЭНЕРГЕТИЧЕСКИЕ ОКНА
...

ПРИМЕЧАНИЕ
Результат показывает различие измерений в этой геометрии,
а не автоматически наличие/опасность вещества.
```

HTML особенно полезен именно для экспериментов, потому что сохраняет **контекст геометрии**, который CSV теряет.

---

# 26. HTML export нескольких объектов

В Истории:

long press / selection:

```text
☑ Сессия 1
☑ Сессия 2
☑ Route
```

Действие:

```text
Экспорт
```

Если HTML:

```text
Один отчёт
Отдельные файлы
```

### Один отчёт

Например сравнение двух сессий:

```text
Comparison-2026-08-17.html
```

с overlay chart и таблицей.

---

# 27. HTML report bundle

Для больших отчётов, где single HTML станет очень тяжёлым, разрешить второй режим:

```text
HTML-пакет (.zip)
```

Структура:

```text
report.zip
├── index.html
├── data/
│   ├── measurements.json
│   └── spectrum.json
└── assets/
    └── ...
```

Но default:

```text
Один HTML
```

Переключаться на package автоматически только если single-file превысит разумный предел либо пользователь выбирает «полные исходные данные внутри отчёта».

---

# 28. Не встраивать миллионы raw samples в HTML

Отчёт — не backup.

Для графика экспортировать визуально достаточную multiresolution/downsampled series.

Например:

```text
overview → min/max buckets
```

А при необходимости приложить:

```text
CSV исходных данных
```

отдельным файлом/package.

HTML должен открываться быстро даже на телефоне.

---

# 29. Privacy при экспорте

Маршрут может содержать точные координаты дома.

Перед HTML/GeoJSON export маршрута:

```text
Координаты

● Полные
○ Скрыть первые/последние 200 м
○ Убрать координаты
```

Для обычного HTML можно по умолчанию предложить:

```text
Скрыть начало и конец маршрута
```

но **не менять данные молча**.

Пользователь должен видеть выбранный режим перед экспортом.

---

# 30. Metadata privacy

Перед экспортом:

```text
Включить в отчёт

[✓] модель прибора
[✓] версия прошивки
[ ] имя профиля места
[ ] точные координаты
[ ] заметки
```

Системные идентификаторы:

```text
Android ID
Bluetooth MAC
Wi‑Fi SSID
internal database ids
```

никогда не включать в публичный HTML по умолчанию.

---

# 31. HTML «Поделиться»

После генерации:

```text
Отчёт готов

Spectrum-2026-08-17.html
1,8 МБ

[Открыть]
[Поделиться]
[Сохранить ещё копию]
```

Использовать Android share sheet.

---

# 32. Export actions прямо из объектов

Не заставлять идти в Settings для каждого экспорта.

Settings — место глобального import/export.

Но entity screens:

### Spectrum

```text
⋯
Открыть
Сравнить
Продолжить накопление
Экспортировать >
```

### Session

```text
⋯
HTML-отчёт
CSV
JSON
```

### Route

```text
⋯
HTML-отчёт
GeoJSON
CSV
```

### Experiment

```text
⋯
HTML-отчёт
CSV
JSON
```

Это быстрее и естественнее.

---

# 33. История — multi-select export

В Истории добавить selection mode:

```text
долгое нажатие
→ чекбоксы
```

Top actions:

```text
Экспорт
Сравнить
Удалить
```

Фильтр типа остаётся:

```text
Все | Сессии | Маршруты | Спектры | Эксперименты
```

---

# 34. HTML renderer architecture

Не строить HTML строками внутри UI.

Создать независимый report layer:

```text
export/
├── BackupManager
├── BackupManifest
├── BackupImporter
├── BackupExporter
├── DataExporter
└── html/
    ├── HtmlReportRenderer
    ├── SessionReportModel
    ├── RouteReportModel
    ├── SpectrumReportModel
    ├── ExperimentReportModel
    ├── HtmlChartRenderer
    ├── HtmlSpectrumRenderer
    ├── HtmlRouteRenderer
    └── ReportTheme
```

Но, как и с Charts V2, **не создавать заранее 15 пустых файлов**.

Выращивать из первого реального отчёта:

```text
Spectrum HTML
→ общие report primitives
→ Session
→ Route
→ Experiment
```

---

# 35. ReportModel отделён от Room entities

Правильно:

```text
Room / repositories
       ↓
ReportModel
       ↓
HtmlRenderer
```

Неправильно:

```text
HtmlRenderer напрямую знает SessionEntity, RoutePointEntity,
все DAO и текущую Room schema
```

HTML export должен переживать внутренние изменения базы.

---

# 36. Версия HTML schema

В HTML добавить невидимые machine metadata:

```html
<meta name="radiacode-report-version" content="1">
<meta name="radiacode-report-type" content="spectrum">
```

И, при желании:

```html
<script type="application/json" id="report-metadata">
{...}
</script>
```

Это позволит будущей версии приложения **импортировать собственный HTML metadata**, если когда-нибудь это понадобится.

Но HTML не должен становиться основным backup format.

---

# 37. Print / PDF

HTML report сразу проектировать print-friendly:

```css
@media print
```

- белый фон;
- без sticky controls;
- charts не обрезаются;
- page breaks между крупными секциями;
- metadata сохраняется.

Тогда пользователь может:

```text
Открыть HTML → Печать → Сохранить PDF
```

без отдельного PDF renderer на первом этапе.

Позже можно добавить `Экспорт PDF`, используя тот же `ReportModel`.

---

# 38. Темы HTML

Отчёт должен быть нейтральным.

По умолчанию:

```text
светлая print-friendly тема
```

В HTML добавить:

```text
☀ / ☾
```

и учитывать `prefers-color-scheme`, но печать всегда корректная.

Не переносить 8-bit theme в научный отчёт по умолчанию.

---

# 39. Доступность HTML

- семантические headings;
- таблицы настоящими `<table>`;
- SVG с `aria-label`;
- значения не различаются только цветом;
- units в тексте;
- хорошая контрастность;
- responsive width.

Отчёт должен нормально открываться и на телефоне, и на desktop.

---

# 40. Что экспортировать из спектра после повторного подключения

Перед реализацией необходимо отдельно подтвердить текущую модель spectrum persistence:

```text
1. какие spectra действительно хранятся в Room;
2. что хранится только в приборе;
3. что импортировано вручную;
4. что является snapshot;
5. что является текущим accumulated spectrum устройства;
6. как определяется device identity;
7. продолжает ли «Продолжить накопление» тот же spectrum либо создаёт новый revision.
```

Нельзя строить backup/export на предположении, что прибор хранит временную историю спектров.

Backup должен сохранять **то, что приложение реально получило и сохранило**.

---

# 41. Future-proof spectrum identity

Для каждого spectrum сохранить:

```text
uuid
createdAt
capturedAt
accumulationDuration
totalCounts
channelCount
calibration coefficients
device model
device fingerprint if safely available
source:
  LIVE_DEVICE
  IMPORT_XML
  IMPORT_N42
  RESTORED_BACKUP
parentSpectrumUuid?
```

Тогда:

- backup;
- import;
- comparison;
- resume;
- HTML;
- N42

не будут зависеть от случайного title `Спектр 14.08.2026`.

---

# 42. Settings backup

Экспорт settings должен быть schema-aware.

Если используется DataStore:

```text
DataStore
↓
SettingsExportModel
↓
settings.json
```

Не копировать внутренний DataStore-файл как публичный формат.

Пример:

```json
{
  "schemaVersion": 2,
  "language": "ru",
  "theme": "system",
  "units": "uSv_h",
  "chart": {
    "smoothing": false,
    "events": false
  },
  "alerts": {
    "mode": "normal"
  }
}
```

Unknown future fields при импорте старой версией должны игнорироваться безопасно, если формат это позволяет.

---

# 43. Device settings

Разделить:

```text
app preferences
```

и:

```text
device commands / actual device configuration
```

Если приложение **не умеет прочитать** текущее состояние звука/вибрации прибора, backup не должен после restore утверждать, что прибор физически находится в сохранённом состоянии.

Можно восстановить:

```text
последняя желаемая настройка приложения
```

но после подключения состояние устройства нужно синхронизировать/уточнить согласно возможностям протокола.

---

# 44. Импорт внешних форматов

Отдельно от backup:

```text
Импортировать
```

Поддерживаемые исходные данные:

```text
N42 spectrum
XML spectrum
CSV — только если есть строгая схема
GeoJSON route — future/optional
```

Не принимать произвольный CSV эвристически.

Если CSV import будет нужен:

```text
wizard mapping columns
```

или собственный versioned CSV dialect.

---

# 45. Undo / transactional restore

Для `Заменить данные приложения`:

1. создать локальную временную safety snapshot текущего состояния;
2. импортировать в staging/new DB или транзакционно;
3. проверить counts/invariants;
4. переключить;
5. удалить temporary snapshot только после успеха.

Если import падает:

```text
Текущие данные остаются без изменений.
```

Это обязательное свойство.

---

# 46. Restore summary

После восстановления:

```text
Готово

Добавлено:
1 218 439 измерений
18 маршрутов
27 спектров
6 экспериментов

Пропущено как уже существующие:
4 218 измерений
2 спектра

Настройки восстановлены
```

Ошибки отдельных optional секций:

```text
1 устаревшая настройка не импортирована
```

с `Подробнее`.

---

# 47. Автоматические резервные копии — не в V1

Не добавлять cloud sync сразу.

Сначала сделать надёжный:

```text
manual export
manual restore
```

Через SAF пользователь уже сможет сохранять файл непосредственно в поддерживаемый облачный DocumentsProvider.

После стабилизации формата можно рассмотреть:

```text
авторезервирование
```

как отдельную функцию.

---

# 48. UX export wizard

Не делать огромный экран с 20 checkbox.

Пример для route:

```text
Экспорт маршрута

Формат
● HTML-отчёт
○ GeoJSON
○ CSV

Координаты
Скрыть начало и конец                  [switch]

[Экспортировать]
```

Spectrum:

```text
Экспорт спектра

● HTML-отчёт
○ N42
○ XML
○ CSV

[Экспортировать]
```

Session:

```text
● HTML-отчёт
○ CSV
○ JSON
```

---

# 49. Лучший default

Для обычного пользователя:

```text
HTML-отчёт
```

первый.

Для Spectrum:

```text
HTML-отчёт
N42
```

оба должны быть легко доступны.

Для Route:

```text
HTML-отчёт
GeoJSON
```

Для полного архива:

```text
.radbackup
```

---

# 50. Acceptance criteria

## Backup

- один файл;
- потоковый export/import;
- вся пользовательская история восстанавливается;
- настройки восстанавливаются;
- formatVersion независим от Room schema;
- checksum validation;
- импорт той же копии повторно не плодит дубликаты;
- newer unsupported backup не импортируется «на удачу»;
- merge является default;
- replace не уничтожает текущие данные до успешной проверки;
- операция переживает уход пользователя с экрана.

## HTML

- один self-contained HTML по умолчанию;
- offline;
- нет CDN;
- нет analytics/network requests;
- responsive;
- print-friendly;
- интерактивный crosshair;
- spectrum peaks связаны с таблицей;
- route map связана с timeline;
- пустые блоки скрываются;
- отчёт читаем без знания приложения;
- technical metadata не захламляет основной экран.

## Export

- Spectrum: HTML + N42 + XML + CSV;
- Route: HTML + GeoJSON + CSV;
- Session: HTML + CSV + JSON;
- Experiment: HTML + CSV + JSON;
- история измерений: streaming CSV/JSON;
- export доступен и из Settings, и контекстно из самой сущности.

---

# 51. Порядок реализации

## P0 — основа backup

1. описать `BackupManifest V1`;
2. ввести stable UUID для импортируемых сущностей;
3. streaming export;
4. checksum;
5. SAF create/open;
6. merge restore;
7. round-trip tests.

## P1 — Spectrum export

Уже есть предметная модель и существующие XML/N42 действия, поэтому начать HTML именно со спектра:

1. `SpectrumReportModel`;
2. self-contained HTML;
3. SVG spectrum;
4. peak table + link peak ↔ row;
5. Lin/Log;
6. N42/XML/CSV рядом.

## P2 — Session HTML

1. summary;
2. Dose/CPS/Hardness charts;
3. events;
4. spectrum if available.

## P3 — Route HTML

1. offline SVG map;
2. radiation-colored track;
3. chart;
4. map ↔ timeline interaction;
5. privacy options;
6. GeoJSON.

## P4 — Experiment HTML

1. geometry;
2. A/B summary;
3. count comparison;
4. spectral comparison;
5. result wording;
6. attached spectra exports.

## P5 — History multi-select + report comparison

Только после надёжных single-entity exporters.

---

# 52. Тестирование

Нужны round-trip tests:

```text
DB state A
→ backup
→ clean app state
→ restore
→ state B
```

Проверить:

```text
A == B
```

по всем смысловым сущностям, а не Room primary keys.

Отдельные fixtures:

```text
empty database
1M measurements
routes with gaps
route without GPS
spectrum with nonlinear calibration
imported N42 spectrum
experiment without spectrum
old backupVersion
corrupted archive
duplicate import
partial optional metadata
```

HTML snapshot tests:

```text
valid HTML
no external resource URLs
contains expected values
correct escaping of user notes
works with Cyrillic
works with decimal localization
```

Обязательно экранировать пользовательские названия/заметки, чтобы они не могли вставить HTML/JS в отчёт.

---

# 53. Важное правило

**Backup сохраняет состояние приложения.  
Data export сохраняет данные.  
HTML объясняет результат человеку.**

Никогда не пытаться сделать один универсальный формат для всех трёх задач.
