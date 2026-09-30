# AlgoPrep — персональный тренер по алгоритмическим интервью

Нативное Android-приложение (Kotlin, Jetpack Compose, Material 3). Local-first: всё работает без регистрации и без интернета.

Документ = шаги 1–8 из плана (требования → UX → навигация → Room → доменные модели → планировщик/SRS → структура проекта → зависимости). Код начинается с PHASE 1 только после вашего подтверждения этого документа.

---

## 1. Product requirements

### 1.1 Цель
Пользователь за 30 дней готовится к алгоритмическому интервью. Приложение каждый день отвечает на 7 вопросов:
1. Что делать сейчас? 2. Почему именно это? 3. Сколько времени займёт? 4. Что получается плохо? 5. Улучшаюсь ли я? 6. Что повторить? 7. Насколько я соблюдаю план?

### 1.2 Принципы
- **Local-first.** Нет аккаунтов, нет обязательной сети. Единственный источник правды — Room.
- **Не выдумывать данные.** Неизвестные метаданные = `null` / `Unknown`. Автоматический парсинг — только предложение, финал за пользователем.
- **Прозрачность.** Любое решение планировщика объяснимо («Почему мне дали эту задачу?»).
- **Только пользовательские материалы.** Приложение не скачивает закрытые данные. Импорт — файлы/текст/ссылки, которые пользователь дал сам. Ссылки хранятся как ссылки (без скрапинга).
- **Честная аналитика.** Частоты — только по импортированному набору, без прогнозов вероятности.
- **Без «магии» без backend.** Всё, что в идеале делал бы LLM (извлечение задач, темы, подсказки), — за интерфейсом `AiAssistant` с рабочим локальным эвристическим fallback.

### 1.3 Функциональные модули
| # | Модуль | Суть |
|---|---|---|
| F1 | Onboarding | Цель, уровень, время в день, дата старта, оценка слабых тем, время напоминаний |
| F2 | Today | Главный экран: день N/30, тема, прогресс, блоки Theory/Warm-up/Main/Review/Error review, кнопка START NEXT TASK |
| F3 | 30-day Plan | Базовый roadmap + пересчёт каждый вечер (70/20/10) |
| F4 | Task Session | Условие, таймер, заметки, постепенные подсказки, результат, уверенность, тип ошибок |
| F5 | Spaced repetition | Интервалы повторения по результату и уверенности |
| F6 | Error Log | Журнал ошибок по типам/темам, разбор ошибок как задачи на завтра |
| F7 | Stats | Прогресс, слабые/сильные темы, динамика, соблюдение плана |
| F8 | Interview Task Bank | Import → Parse → Review → Save, дедупликация, упоминания |
| F9 | Interview Data | Аналитика по импортированному набору |
| F10 | Notifications | Утро / вечер / повторение / streak; время задаёт пользователь; всё отключаемо |
| F11 | Adaptive scheduling | Пересчёт плана + приоритизация задач банка + «Почему мне дали эту задачу?» |
| F12 | Mock Interview | Симуляция: 1–2 задачи, общий таймер, без подсказок, разбор |
| F13 | Profile/Settings | Время уведомлений, тема, экспорт/импорт бэкапа JSON, сброс |

### 1.4 Нефункциональные требования
- **minSdk 26 (Android 8.0)**: `NotificationChannel` (обязательны с 26) без ветвлений, `java.time` без desugaring, покрывает ~95%+ активных устройств. targetSdk/compileSdk 35. На 33+ запрашиваем `POST_NOTIFICATIONS`, на 31+ точные будильники не нужны — WorkManager (неточность ±несколько минут приемлема; это объясняем в UI).
- Офлайн; холодный старт Today < 1 c на среднем устройстве; тяжёлые операции (парсинг) — на `Dispatchers.Default`, с прогрессом.
- Приватность: никакой телеметрии; `allowBackup=false` по умолчанию + собственный JSON-экспорт.
- Тесты: unit — планировщик, SRS, парсер, дедупликатор (чистый Kotlin, без Android); Room — instrumented/Robolectric; UI — smoke на Today.
- Локализация: RU + EN строки в ресурсах с первого дня (UI-строки не хардкодим). Контент сида (roadmap) — на EN-терминах, заголовки локализуются.

### 1.5 Вне scope MVP
Облачная синхронизация, аккаунты, реальный LLM, скрапинг сайтов, исполнение кода пользователя, публикация в стор.

---

## 2. UX / экраны

Bottom navigation: **Home (Today) · Plan · Tasks · Stats · Profile**.

### Onboarding (один раз)
Welcome → Цель/уровень (Beginner/Intermediate/Advanced) → Часы в день (1/2/3/свободно) → Дата старта → Самооценка тем (чипы 1–5) → Время напоминаний + разрешение → Генерация плана.

### Home / Today
```
DAY 12 / 30 · Trees + DFS          ≈ 2ч 15мин
██████░░░░ 72%
Сегодня: Theory ✓  Warm-up ✓
MAIN PRACTICE   ✓ P1 18м  ✓ P2 27м  □ P3
REVIEW          □ Day 4  □ Day 7
ERROR REVIEW    □ binary search off-by-one
[ START NEXT TASK ]
```
Каждый элемент задачи имеет ⓘ «Почему мне это дали?» (bottom sheet). Баннер «отстаёшь от плана на N дня» → предложение сжать/сдвинуть.

### Plan
Лента 30 дней (пройдено / сегодня / будущие с темой и пометкой «скорректировано» + причина). Тап по дню — детали. Кнопка «Пересчитать сейчас».

### Tasks (Interview Task Bank)
Вкладки: **Bank** (список + фильтры: тема, паттерн, статус, компания, сложность, поиск) · **Import** · **Interview Data**. Карточка задачи: описание, метаданные (Unknown показываются как «—»), упоминания, заметки, история решений.

### Import flow (Import → Parse → Review → Save)
1. **Import**: SAF file picker (`.md .txt .json .csv`) или вставка текста / список ссылок.
2. **Parse**: прогресс, отчёт «Найдено N потенциальных задач».
3. **Review**: список кандидатов; у каждого чекбокс, название, условие, тема, сложность, источник, компания — всё редактируемо; бейдж «POSSIBLE DUPLICATE» с [Объединить] / [Оставить отдельно]; уверенность парсера (low/med/high) показывается явно; «выбрать все с high».
4. **Save**: транзакция в Room, итоговый экран (сохранено / объединено / пропущено). Import batch сохраняется, поэтому его можно откатить целиком.

### Task Session
Problem · Timer · Notes · [START] → таймер идёт; [Need a hint] раскрывает подсказку уровня 1→2→3 (последний — идея решения); [Finish] → **Result**: время, «как решил» (самостоятельно / маленькая подсказка / существенная подсказка / посмотрел решение / не решил), Confidence 1–5, чекбоксы ошибок (Pattern recognition, Implementation, Edge cases, Complexity, Other + текст), [COMPLETE]. Таймер переживает поворот/сворачивание (хранится `startedAt` + накопленные паузы, а не тикающее число).

### Stats
Обзор (streak, решено, среднее время, % самостоятельно) · Темы (сила/слабость, тренд) · Ошибки (по типам) · План (факт vs план) · График 30 дней.

### Interview Data
Всего записей / уникальных задач; Topics и Patterns (гистограммы); MOST FREQUENTLY MENTIONED; источники; постоянный дисклеймер: «Частота рассчитана только по импортированному пользователем набору и не означает вероятность появления задачи на будущем интервью».

### Profile
Уведомления (тумблеры + TimePicker), тема, цель дня, экспорт/импорт бэкапа, сброс плана, о приложении.

### Mock Interview
Выбор формата (45 мин / 1 задача · 90 мин / 2 задачи) → задачи из банка (не решённые или давно не решаемые) → без подсказок → результат/разбор → запись в историю как сессия типа `MOCK`.

---

## 3. Navigation graph (Navigation Compose, type-safe routes)

```
Root NavHost
├─ onboarding/                       (start, если профиль не создан)
│   └─ welcome → goals → topics → reminders → generating
└─ main/  (Scaffold + BottomBar)
    ├─ today                          (start, иначе)
    │   ├─ why/{plannedItemId}        (bottom sheet)
    │   └─ session/{taskId}?plannedItemId=
    │        └─ result/{sessionId}
    ├─ plan
    │   └─ day/{dayIndex}
    ├─ tasks
    │   ├─ bank ─ task/{taskId}
    │   ├─ import
    │   │    └─ review/{batchId} → done/{batchId}
    │   └─ data                       (Interview Data)
    ├─ stats
    │   └─ errors
    └─ profile
        └─ notifications
mock/setup → mock/session/{mockId} → mock/result/{mockId}   (открывается из Today/Tasks, вне BottomBar)
```
Правила: session/result — full-screen без bottom bar; после COMPLETE `popUpTo(today)`; deep link из уведомлений → `today` или `review-queue`. Состояние: один `ViewModel` на экран (Hilt `hiltViewModel()` + `SavedStateHandle` для аргументов), UI-state — `StateFlow<UiState>` + одноразовые события через `Channel`.

---

## 4. Room schema (v1, `exportSchema = true`, миграции обязательны с v2)

Хранение: enum → `TEXT`, списки → отдельные таблицы (для запросов/аналитики) либо JSON-`TEXT` там, где запросы по элементам не нужны (`hints`, `examples`, `constraints`). Время: `epochMillis` (UTC) + `localDate` (`TEXT` ISO `yyyy-MM-dd`) там, где важен «календарный день» пользователя.

### Задачи и банк
```
task                       -- каноническая задача
  id PK, title, originalText, canonicalKey (нормализованный title, индекс),
  difficulty? (EASY|MEDIUM|HARD), estimatedSolveMin?,
  constraintsJson?, examplesJson?, hintsJson? (List<String>, 3 уровня),
  solutionIdea?, timeComplexity?, spaceComplexity?,
  roleLevel?, personalNotes,
  status (NEW|LEARNING|REVIEW|MASTERED|FAILED_RECENTLY),
  timesSolved, lastSolvedAt?, nextReviewAt?, confidence? (1..5),
  origin (SEED|IMPORTED|MANUAL), createdAt, updatedAt

task_mention               -- упоминание (источник/отчёт); основа дедупликации и частот
  id PK, taskId FK→task CASCADE, batchId FK→import_batch (nullable, SET NULL),
  rawTitle, rawText, source (Unknown допустим → NULL), sourceUrl?,
  companyTag?, interviewStage?, roleLevel?, reportedDate?, createdAt
  INDEX(taskId), INDEX(companyTag)

task_topic   (taskId, topicId, isUserEdited)       PK(taskId, topicId)
task_pattern (taskId, patternId, isUserEdited)     PK(taskId, patternId)
topic   (id PK slug, title, parentId?, orderIndex)   -- Arrays, Graphs, Trees, DP, Binary Search…
pattern (id PK slug, title, topicHint?)              -- DFS, BFS, Two Pointers, Sliding Window…

import_batch
  id PK, createdAt, sourceName (файл/«вставка»), format, rawSize,
  candidatesFound, saved, merged, skipped, status (PARSED|SAVED|ROLLED_BACK)

duplicate_candidate        -- отложенные решения «оставить отдельно» (чтобы не спрашивать повторно)
  id PK, taskAId, taskBId, similarity REAL, decision (PENDING|MERGED|KEPT_SEPARATE)
  UNIQUE(taskAId, taskBId)
```
`mentions` для UI = `COUNT(task_mention)`; `Unknown` = `NULL` в БД.

### План и тренировка
```
user_profile (id=1)  level, goal, dailyMinutes, startDate, targetDate?, onboardedAt

topic_skill          -- оценка навыка по теме (обновляется после каждой сессии)
  topicId PK, selfRating (1..5 из онбординга), score REAL (0..1, EWMA), attempts, failures,
  lastPracticedAt?, updatedAt

plan_day
  dayIndex PK (1..30), date (localDate), focusTopicIds (через plan_day_topic), title,
  targetMinutes, isAdjusted, adjustReason?, generatedVersion, status (UPCOMING|TODAY|DONE|MISSED)

planned_item
  id PK, dayIndex FK, kind (THEORY|WARMUP|MAIN|REVIEW|ERROR_REVIEW|MOCK),
  taskId? FK, orderIndex, estimatedMin, status (TODO|DONE|SKIPPED),
  bucket (ROADMAP|WEAK|SPACED), reasonJson   -- список структурированных причин (см. §6.4)
  completedSessionId?

roadmap_template     -- базовый 30-дневный roadmap (из seed, read-only)
  dayIndex PK, title, topicIds, theoryRef, notes
```

### Сессии и ошибки
```
solve_session
  id PK, taskId FK, plannedItemId?, type (PRACTICE|REVIEW|MOCK|ERROR_REVIEW),
  startedAt, finishedAt?, durationSec (без пауз), hintsUsed (0..3),
  outcome (INDEPENDENT|SMALL_HINT|BIG_HINT|SAW_SOLUTION|NOT_SOLVED),
  confidence (1..5), notes, localDate

error_entry
  id PK, sessionId FK, taskId FK, type (PATTERN|IMPLEMENTATION|EDGE_CASES|COMPLEXITY|OTHER),
  note?, resolved BOOL, createdAt

review_state         -- SRS-состояние задачи (1:1 с task)
  taskId PK, intervalDays, ease REAL, repetitions, lapses, dueAt, lastOutcome
```

### Настройки
DataStore (Preferences), не Room: `morningEnabled/time`, `eveningEnabled/time`, `reviewEnabled/time`, `streakEnabled/time`, `themeMode`, `onboardingDone`, `planVersion`, `lastReplanDate`.

Индексы для горячих запросов: `review_state(dueAt)`, `solve_session(localDate)`, `planned_item(dayIndex)`, `task(status)`, `task(canonicalKey)`.

**Миграции.** v1 без миграций; `exportSchema=true`, `schemas/` в git; `fallbackToDestructiveMigration` **не** используется. С v2 — `Migration` + `MigrationTestHelper`-тест на каждую.

---

## 5. Domain models (чистый Kotlin, без Android-зависимостей)

```kotlin
enum class Difficulty { EASY, MEDIUM, HARD }
enum class TaskStatus { NEW, LEARNING, REVIEW, MASTERED, FAILED_RECENTLY }
enum class SolveOutcome { INDEPENDENT, SMALL_HINT, BIG_HINT, SAW_SOLUTION, NOT_SOLVED }
enum class ErrorType { PATTERN, IMPLEMENTATION, EDGE_CASES, COMPLEXITY, OTHER }
enum class PlannedKind { THEORY, WARMUP, MAIN, REVIEW, ERROR_REVIEW, MOCK }
enum class Bucket { ROADMAP, WEAK, SPACED }

data class Task(
  val id: Long, val title: String, val originalText: String,
  val difficulty: Difficulty?, val estimatedSolveMin: Int?,
  val topics: Set<TopicId>, val patterns: Set<PatternId>,
  val constraints: List<String>, val examples: List<Example>, val hints: List<String>,
  val solutionIdea: String?, val complexity: Complexity?, val personalNotes: String,
  val status: TaskStatus, val timesSolved: Int, val lastSolvedAt: Instant?,
  val nextReviewAt: Instant?, val confidence: Int?, val mentions: List<Mention>,
)
data class Mention(val source: String?, val sourceUrl: String?, val companyTag: String?,
  val interviewStage: String?, val roleLevel: String?, val reportedDate: LocalDate?)

data class ImportCandidate(     // результат Parse, ещё НЕ в БД
  val tempId: String, val title: String, val text: String, val topics: Set<TopicId>,
  val difficulty: Difficulty?, val source: String?, val sourceUrl: String?, val companyTag: String?,
  val parserConfidence: Confidence, val possibleDuplicateOf: DuplicateMatch?, val selected: Boolean)

data class SolveResult(val taskId: Long, val duration: Duration, val hintsUsed: Int,
  val outcome: SolveOutcome, val confidence: Int, val errors: List<ErrorEntry>)

data class PlanReason(val code: ReasonCode, val text: String)   // для «Почему?»
enum class ReasonCode { WEAK_TOPIC, NOT_SOLVED_YET, REVIEW_DUE, RECENT_FAILURE, DIFFICULTY_FIT,
  FREQUENT_IN_DATASET, ROADMAP_TOPIC, MIXED }
```
**Слои:** `data` (Room entities/DAO, DataStore, мапперы, реализации репозиториев) → `domain` (модели, интерфейсы репозиториев, use cases, алгоритмы) → `ui` (Compose, ViewModel). Use case создаётся только там, где есть логика (`CompleteSession`, `ReplanRemainingDays`, `BuildTodayPlan`, `ParseImport`, `SaveImport`, `MergeTasks`); тривиальные чтения ViewModel берёт напрямую из репозитория. Clean Architecture «по канону» (mapper на каждый слой, use case на каждый чих) — сознательно не делаем.

### AI-абстракция
```kotlin
interface AiAssistant {
  suspend fun extractTasks(text: String): List<RawCandidate>?   // null = «не умею»
  suspend fun classify(task: TaskDraft): Classification?         // topics/patterns/difficulty
  suspend fun hint(task: Task, level: Int): String?
}
class LocalHeuristicAssistant : AiAssistant   // словарь ключевых слов, regex — работает офлайн, единственная реализация в MVP
```
Вызывающий код всегда имеет путь при `null`. Подсказки без AI: (1) `hints` из импорта/сида, (2) шаблонные по паттерну (например, «Подумай, какая структура даёт O(1) поиск?»), (3) `solutionIdea`. Если ничего нет — UI честно пишет «для этой задачи подсказок нет, добавь свои в заметках».

---

## 6. Scheduling и Spaced Repetition

### 6.1 Roadmap (базовый 30-дневный, из seed)
Дни 1–3 Arrays/Hashing/Two Pointers · 4–5 Sliding Window/Stack · 6–7 Binary Search + **Review week 1** · 8–10 Linked List/Trees · 11–13 Trees/DFS/BFS · 14 **Review** · 15–17 Graphs · 18–20 Heap/Intervals/Greedy · 21–24 DP · 25–26 Backtracking/Tries · 27–28 Mixed · 29 Mock · 30 Final review. Это данные (`roadmap_template`), а не код — можно править без релиза.

### 6.2 Формирование дня (`BuildTodayPlan`)
Бюджет = `dailyMinutes`. Слоты делятся **70 / 20 / 10** по времени:
- **70% ROADMAP** — задачи по темам дня roadmap; Theory + Warm-up + MAIN.
- **20% WEAK** — слабые темы (см. 6.3), даже если их нет в сегодняшнем roadmap.
- **10% SPACED/MIXED** — просроченные повторения (`dueAt ≤ now`), при их отсутствии — одна mixed-задача.
Просроченные повторения важнее «10%»: если их больше слота, лишние переносятся (макс. 30% времени дня на review, чтобы не убить curriculum). Если банк пуст по теме — используются seed-задачи; если и их нет — слот честно пишется как «Theory/чтение».

### 6.3 Слабые темы
`topic_skill.score` — экспоненциально сглаженная оценка (0..1):
`score ← 0.7·score + 0.3·quality`, где quality по исходу: INDEPENDENT 1.0 · SMALL_HINT 0.75 · BIG_HINT 0.4 · SAW_SOLUTION 0.15 · NOT_SOLVED 0.0, минус штраф до −0.15 за низкий confidence и время > 1.5× оценки. Начальное значение — самооценка из онбординга (`(selfRating−1)/4`), затем данные перекрывают её. Тема **слабая**, если `attempts ≥ 3` и `score < 0.55` (либо самооценка ≤ 2 до накопления данных). Топ-3 слабые темы получают долю 20%.

### 6.4 Приоритет задачи (прозрачная эвристика, `TaskScorer`)
Не одна магическая формула, а **сумма именованных слагаемых, каждое ограничено 0..N и имеет текстовую причину**:

| Слагаемое | Баллы | Условие → причина |
|---|---|---|
| notSolved | +30 | `timesSolved=0` → «задача ещё не решалась» |
| recentFailure | +25 | последняя сессия за 7 дней NOT_SOLVED/SAW_SOLUTION → «недавно не получилось» |
| reviewDue | +25 (+ растёт с просрочкой до +35) | `dueAt ≤ now` → «пора повторить» |
| weakTopic | +20 · (1−score темы) | тема в топ-3 слабых → «<тема> — одна из слабых» |
| difficultyFit | +0..15 | сложность близка к «целевой» (по score: слабо → Easy/Medium, сильно → Medium/Hard) → «подходит по сложности» |
| datasetFrequency | +0..10 (лог-шкала по числу упоминаний) | ≥2 упоминаний → «встречается N раз в твоём импортированном наборе». **Вес намеренно мал**: частота ≠ вероятность |
| roadmapMatch | обязательный фильтр слота ROADMAP | тема/паттерн сегодняшнего дня |

Задачи берутся кандидатами слота (фильтр по теме/бакету), сортируются по сумме, лёгкая рандомизация среди топ-3 (чтобы не было одного и того же), исключаются решённые за последние 2 дня, если нет reviewDue. `reasonJson` хранит выбранные причины → экран «Почему мне дали эту задачу?» показывает их пунктами, ровно как в ТЗ. Эвристика — чистая функция, покрыта unit-тестами на фикстурах.

### 6.5 Spaced repetition (`ReviewScheduler`, упрощённый SM-2)
Состояние: `intervalDays, ease (1.3..2.8, старт 2.3), repetitions, lapses`.
Оценка `q` из исхода + confidence:
| Исход | Действие |
|---|---|
| INDEPENDENT, conf ≥4 | `rep+1`; интервал: 1 → 3 → `prev·ease`; `ease += 0.1` |
| INDEPENDENT, conf ≤3 / SMALL_HINT | `rep+1`; интервал `prev·1.2` (min 2 д); ease без изменений |
| BIG_HINT | интервал 2 д; `ease −= 0.15`; `rep = 0` |
| SAW_SOLUTION / NOT_SOLVED | интервал 1 д; `lapses+1`; `ease −= 0.2`; status → FAILED_RECENTLY; ошибка попадает в ERROR REVIEW на завтра |
`nextReviewAt = localDate + interval`, время суток — утро пользователя. **MASTERED**: `repetitions ≥ 4` и последние 2 попытки INDEPENDENT с conf ≥4. `LEARNING`: есть попытки, но не выполнены условия REVIEW/MASTERED; `REVIEW`: `dueAt` наступил. Потолок интервала — 60 дней (в рамках 30-дневного плана длинные интервалы мало значимы, но данные накапливаются на будущее).

### 6.6 Вечерний пересчёт (`ReplanRemainingDays`, WorkManager + при открытии приложения)
1. Зафиксировать сегодняшний день: невыполненные items → SKIPPED/переносятся на завтра как «долг» (не больше 30% следующего дня).
2. Пересчитать `topic_skill` (уже обновлён сессиями) → топ слабых тем.
3. Для дней `today+1..30`: **roadmap-часть не трогается** (порядок и темы сохраняются), меняются только доли 20% и 10%: слабой теме +до 10 п.п. (итого максимум 30% WEAK, ROADMAP не ниже 60%), сильные темы (score > 0.85) получают −1 слот в своём roadmap-дне. Никаких перестановок дней.
4. Если пользователь отстаёт ≥2 дней: предложить (не применять молча) «сжать» — слить Review-дни, урезать WARMUP; либо сдвинуть дату цели.
5. Записать `isAdjusted=true` + `adjustReason` («Graphs: 3 из 4 попыток провалены → +1 задача в дни 16–18») — видно в Plan.
Идемпотентно (по `lastReplanDate` + `generatedVersion`), выполняется в транзакции.

### 6.7 Уведомления (WorkManager)
Используем `OneTimeWorkRequest` с `setInitialDelay` до ближайшего времени, worker сам ставит следующий запуск (надёжнее `PeriodicWorkRequest`, у которого нельзя задать точное время суток). 4 уникальных work'а (`unique name`, `REPLACE` при смене времени): morning / evening / review / streak. Текст формируется в момент показа из актуальных данных; **если нечего сообщать** (например, нет due-повторений, или тренировка уже была) — уведомление не показывается. Перепланирование после перезагрузки: WorkManager сохраняет работу сам; при старте приложения — `ensureScheduled()`. Каналы: `plan`, `review`, `streak` (пользователь может отключить каждый в системных настройках). Оговорка в UI: время может сдвигаться до нескольких минут (Doze).

### 6.8 Импорт и дедупликация
**Parse (`LocalHeuristicAssistant`, детерминированно):**
- Форматы: JSON (массив/объект с полями title/text/…, с гибким маппингом ключей), CSV (заголовки → колонки, выбор маппинга в UI при неоднозначности), MD/TXT (сегментация: заголовки `#`, нумерованные списки, разделители `---`/пустые блоки, маркеры «Задача/Problem/Question»).
- Отчёты об интервью: абзацы вида «дали задачу…» дают кандидата с `parserConfidence=LOW` — пользователь решает.
- Ссылки (leetcode.com/problems/slug, и т.п.) → `sourceUrl` + title из slug; **без загрузки страницы**.
- Тема/паттерн/сложность — по словарю ключевых слов (RU+EN: «граф», «BFS», «two pointers»…); нет совпадения → `null`. Сложность — только если указана явно в тексте.

**Дедупликация (`DuplicateDetector`):** нормализация (lowercase, удаление пунктуации/стоп-слов/номеров) → точное совпадение `canonicalKey` = дубль (score 1.0); иначе токен-Jaccard + сравнение trigram по title ≥ 0.8, а для текста — Jaccard шинглов ≥ 0.6 → «POSSIBLE DUPLICATE». Сравнение внутри батча и с банком; O(n·k) через индекс по токенам (не n²). **Ничего не удаляется автоматически.** «Объединить» = кандидат превращается в `task_mention` существующей задачи (сохраняются source/company/date), заметки сливаются; «Оставить отдельно» пишет `duplicate_candidate(KEPT_SEPARATE)`, чтобы не спрашивать снова.

---

## 7. Структура проекта

Один Gradle-модуль `:app` (осознанно: 1 разработчик, MVP; границы — пакетами; разнесение в `:core/:feature` возможно позже без переписывания).

```
algoprepareapp/
├─ build.gradle.kts, settings.gradle.kts, gradle.properties
├─ gradle/libs.versions.toml            (version catalog)
├─ docs/SPEC.md
└─ app/
   ├─ build.gradle.kts, proguard-rules.pro, schemas/
   └─ src/
      ├─ main/
      │  ├─ AndroidManifest.xml
      │  ├─ assets/seed/                (topics.json, patterns.json, roadmap.json, tasks_seed.json)
      │  ├─ res/values{,-ru}/strings.xml, ...
      │  └─ java/com/algoprep/app/
      │     ├─ AlgoPrepApp.kt           (@HiltAndroidApp, WorkManager config)
      │     ├─ MainActivity.kt          (edge-to-edge, NavHost)
      │     ├─ di/                      (DatabaseModule, RepositoryModule, WorkModule, AiModule, ClockModule)
      │     ├─ core/                    (Result, DispatcherProvider, TimeUtils, Clock)
      │     ├─ domain/
      │     │  ├─ model/
      │     │  ├─ repository/           (interfaces)
      │     │  ├─ scheduling/           (BuildTodayPlan, TaskScorer, ReviewScheduler, ReplanRemainingDays, SkillTracker)
      │     │  ├─ importer/             (ImportParser, formats/*, DuplicateDetector, TextNormalizer)
      │     │  ├─ ai/                   (AiAssistant, LocalHeuristicAssistant, KeywordDictionary)
      │     │  └─ usecase/              (CompleteSession, SaveImport, MergeTasks, ...)
      │     ├─ data/
      │     │  ├─ db/                   (AppDatabase, entities/, dao/, converters, migrations/)
      │     │  ├─ repository/           (…Impl)
      │     │  ├─ prefs/                (SettingsDataStore)
      │     │  ├─ seed/                 (SeedLoader — первый запуск, идемпотентно)
      │     │  └─ backup/               (JSON export/import)
      │     ├─ notifications/           (NotificationChannels, Notifier, workers/*, ReminderScheduler)
      │     └─ ui/
      │        ├─ theme/                (Color, Type, Shape, Theme — Material 3, dynamic color + fallback)
      │        ├─ navigation/           (Routes, AppNavHost, BottomBar)
      │        ├─ components/           (ProgressBar, TimerText, TopicChip, WhySheet, …)
      │        └─ screens/{onboarding,today,plan,tasks,importer,session,result,stats,data,profile,mock}/
      ├─ test/                          (JUnit5/JUnit4 + Turbine + MockK: scheduling, importer, SRS, ViewModels)
      └─ androidTest/                   (Room DAO, migrations, Compose smoke)
```
Пакет `com.algoprep.app` — рабочий; при желании заменим на ваш applicationId до PHASE 1.

**State management:** `ViewModel` → `StateFlow<UiState>` (`stateIn(WhileSubscribed(5_000))`) из `combine` репозиториев; действия — функции VM; события (navigate/snackbar) — `Channel`. Compose собирает через `collectAsStateWithLifecycle()`. Timer — в `SessionViewModel` через `startedAt`/`pausedTotal` и тик-`Flow`, состояние сессии восстанавливается из `SavedStateHandle`/БД («активная сессия»).

---

## 8. Dependencies (version catalog; конкретные версии зафиксируем при PHASE 1 по актуальным стабильным)

| Назначение | Артефакты |
|---|---|
| Build | Android Gradle Plugin, Kotlin, KSP (Room/Hilt), Compose compiler plugin (Kotlin 2.x) |
| Compose | `androidx.compose:compose-bom`, `ui`, `ui-tooling-preview`, `material3`, `material-icons-extended`, `activity-compose` |
| Nav | `androidx.navigation:navigation-compose` (type-safe routes, `kotlinx-serialization`) |
| Lifecycle | `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` |
| DI | `hilt-android`, `hilt-compiler`, `androidx.hilt:hilt-navigation-compose`, `androidx.hilt:hilt-work` |
| DB | `room-runtime`, `room-ktx`, `room-compiler` (KSP), `room-testing` |
| Settings | `androidx.datastore:datastore-preferences` |
| Background | `androidx.work:work-runtime-ktx` |
| Async | `kotlinx-coroutines-android`, `kotlinx-coroutines-test` |
| Serialization | `kotlinx-serialization-json` (seed, backup, JSON-импорт) |
| CSV/MD | свой парсер (небольшой, без зависимостей); CSV — минимальный RFC 4180-совместимый |
| Test | JUnit, MockK, Turbine, `androidx.test.ext:junit`, `compose-ui-test-junit4`, Robolectric (опц.) |

Минимум внешних библиотек — сознательно: нет Retrofit/OkHttp/Coil (сети в MVP нет).

---

## 9. План реализации (фазы) и Definition of Done для каждой

PHASE 1 skeleton+nav+theme → 2 Room+repos+seed → 3 Onboarding → 4 Today+plan → 5 Session+timer → 6 Results+Error Log → 7 SRS → 8 Stats → 9 Notifications → 10 Task Bank+import → 11 Adaptive scheduling → 12 Mock Interview.

После каждой фазы: список файлов, полный код, проверка imports/Gradle/navigation/state/Room-схемы, инструкция запуска и проверки; сборка `./gradlew assembleDebug` + unit-тесты должны проходить.

### Открытые решения (значения по умолчанию, если не возразите)
1. applicationId/package: `com.algoprep.app`.
2. minSdk 26, target/compile 35.
3. Один модуль `:app`.
4. Язык UI: RU + EN, дефолт — по системе.
5. Сид-банк: небольшой набор (~60–80) **собственных кратких формулировок** классических задач (название + идея + темы; без копирования чужих текстов условий) — чтобы приложение работало до импорта.
6. Ссылки на публичные задачи хранятся как `sourceUrl`; контент по ним не скачивается.


---

## 10. As built: отличия от этой спецификации

Документ выше — исходный замысел. Что изменилось при реализации (и почему):

* **Причины плана** хранятся как `code + arg`, а не как готовый текст (`PlanReason`), чтобы UI локализовал их на русском и английском. Добавлены коды `CARRIED_OVER` и `MOCK_DAY`.
* **Жизненный цикл сессии** выводится без новых колонок: `RUNNING` (durationSec = 0), `AWAITING_RESULT` (durationSec > 0, finishedAt = null), `FINISHED`.
* **Дни будущего** создаются «скелетом» без задач; задачи выбираются в сам день (по актуальным навыкам). Вечерний пересчёт (`ReplanRemainingDays`) хранит в `plan_day.adjustReason` короткий код причины корректировки (`MORE_WEAK|30|graphs`, `STRONG_DAY|trees`, `SHIFTED|2`, `COMPRESSED|2`), UI его локализует.
* **Случайность** среди топ-3 кандидатов планировщика не используется (детерминированный выбор, проще тестировать); в мок-интервью она есть, но только среди близких по баллам задач.
* **Доли дня**: слабые темы 20% → до 30% (по 5 п.п. за каждую явно слабую тему), spaced 10% → 15% в день сильных тем; roadmap не ниже 60%.
* **Повторения**: `dueAt` — начало локального дня; REVIEW выставляется при открытии Today/в напоминаниях (`markDueTasksForReview`).
* **Импорт**: `task_mention.rawTitle/rawText` хранят исходный текст записи; `duplicate_candidate` пишется только для решения «оставить отдельно» (аудит, при повторном импорте не подавляет вопрос).
* **Отставание**: «Продолжить с того места» (сдвиг) и «Пропустить дни повторения» (сжатие) — дни помечены в roadmap заметкой `skippable`; день мока — заметкой `mock`.
* **Не реализовано из §1.3**: F13 экспорт/импорт бэкапа JSON и тема (светлая/тёмная вручную; работает системная).
