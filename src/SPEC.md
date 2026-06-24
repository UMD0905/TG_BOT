  # STO Booking Bot — Technical Specification

> A Telegram bot for online booking at a single automotive service station (STO).
> Built with Spring Boot 3 and the TelegramBots library. This document is the
> single source of truth for the project — it captures the requirements,
> architectural decisions, data model, development plan, and known pitfalls.

**Author:** Ulugbek
**Target:** MVP in 7 days
**Stack:** Java 21, Spring Boot 3.3, H2 (dev) / PostgreSQL (prod), Flyway

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Goals and Non-Goals](#2-goals-and-non-goals)
3. [Architectural Decisions](#3-architectural-decisions)
4. [Tech Stack](#4-tech-stack)
5. [Database Schema](#5-database-schema)
6. [Bot Commands](#6-bot-commands)
7. [User Flows](#7-user-flows)
8. [Booking State Machine](#8-booking-state-machine)
9. [Project Structure](#9-project-structure)
10. [Configuration](#10-configuration)
11. [7-Day Development Plan](#11-7-day-development-plan)
12. [Technical Pitfalls](#12-technical-pitfalls)
13. [MVP Acceptance Checklist](#13-mvp-acceptance-checklist)
14. [Future Enhancements](#14-future-enhancements)

---

## 1. Project Overview

A Telegram bot that lets car owners book services at a single automotive
service station (STO) without phone calls or trips to the shop. The bot
guides the user through:

1. Registering their car (brand, model, year, engine)
2. Choosing what kind of work they need — either through a menu or by
   describing their problem in free text
3. Picking a date and time from available slots
4. Confirming the booking

The bot also gives the STO administrator a simple control panel through
Telegram commands: view today's/tomorrow's bookings, confirm or reject
requests, see basic stats.

**Why this project?**
This bot is being built as both a portfolio project and a reusable template
for freelance work. Small STOs in Uzbekistan typically take bookings by
phone or WhatsApp; almost none use a dedicated booking system. A configurable
Telegram bot is a realistic product to sell to local STOs for $200–400 per
deployment.

---

## 2. Goals and Non-Goals

### Goals

- A working MVP bot deployed and accessible via Telegram in 7 days
- Single-STO deployment (one bot instance = one STO)
- Reliable booking with no double-booking of the same time slot
- A reusable template that can be cloned and customized per client
- Code structured so each subsystem (booking, scheduling, reminders, admin)
  is independently testable
- A `SPEC.md` (this document) that explains every nontrivial decision

### Non-Goals (explicitly out of scope for MVP)

- Multi-STO directory / aggregator features
- Master/mechanic assignment (a booking is to the STO, not to a person)
- Variable-duration services (e.g. "this might take 1–2 hours")
- Online payments
- Customer feedback / ratings
- Photos uploaded by customer
- Service history beyond the current booking
- Web admin panel
- Notifications via SMS or email — Telegram only
- Multi-language support (Russian only for V1)

These are deferred to "Phase 2" and noted in [Future Enhancements](#14-future-enhancements).

---

## 3. Architectural Decisions

Each decision below was made consciously. When a reviewer or interviewer asks
"why did you do X this way?", the answers are here.

### Decision 1: Single-STO Bot, Not Aggregator

**Choice:** One bot instance serves one STO. Customizing for another STO
means cloning the repository and editing `application.properties` plus the
seed data.

**Why:** An aggregator (multiple STOs choosable inside one bot) is a
competitor to Yclients-style systems and would take months to build. A
single-STO template is realistic in a week, is what small STOs actually need,
and is what can be sold per-deployment as freelance work.

### Decision 2: No Master/Mechanic Assignment

**Choice:** A booking is for a time slot at the STO as a whole, not for a
specific mechanic.

**Why:** Small STOs (10–30 cars/day) distribute work dynamically when the
car arrives. Pre-assigning a mechanic at booking time doesn't reflect how
they operate. Adds a lot of UI complexity (separate mechanic selection step,
mechanic-specific schedules) for no real benefit.

**Future:** If selling to larger STOs, add a `master` entity and per-master
schedules — this is Phase 2.

### Decision 3: Hybrid Service Selection — Menu + Free-Text Problem Description

**Choice:** When booking, the user picks one of two paths:
1. Browse a categorized menu of services and pick one (for users who know
   what they need)
2. Describe their problem in free text — the bot routes it to "Diagnostics"
   as a safe default and admin reviews it (for users who don't know what
   they need)

**Why:** Roughly half of STO customers know the service name ("oil change"),
half describe symptoms ("something is knocking when I turn"). A menu-only
bot loses the second group; a free-text-only bot is harder to navigate for
the first group. Hybrid covers both with minimal extra complexity.

### Decision 4: Fixed Duration Per Service

**Choice:** Every service has a fixed duration in minutes stored in the
database. The bot blocks exactly that duration in the schedule.

**Why:** Variable duration ("anywhere from 30 to 90 minutes") complicates
slot calculation significantly. For MVP we use realistic average durations
("oil change: 45 minutes"). If actual work goes over, the admin handles it
manually.

**Future:** Variable-duration with admin manual adjustment — Phase 2.

### Decision 5: Hardcoded Telegram User IDs for Admin Auth

**Choice:** Admin Telegram user IDs are listed in `application.properties`
under `admin.telegram-ids`. Anyone whose ID is on the list sees admin
commands; everyone else doesn't.

**Why:** Telegram user IDs are not guessable, not crackable, and don't
require a password infrastructure. For a single-STO bot operated by 1–3
admins this is the simplest and most secure option.

**Future:** Role table in DB with admin self-service — Phase 2.

---

## 4. Tech Stack

### Runtime

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 3.3.x |
| Build | Maven | 3.9+ |
| Telegram client | telegrambots-spring-boot-starter | 6.9.7.1 |
| Database (dev) | H2 (file mode) | 2.2.x |
| Database (prod) | PostgreSQL | 16 |
| Migrations | Flyway | 10.x (Spring Boot managed) |
| ORM | Spring Data JPA + Hibernate | Spring Boot managed |
| Validation | Bean Validation (Jakarta) | Spring Boot managed |
| Utility | Lombok | 1.18.x |
| Logging | SLF4J + Logback | Spring Boot managed |

### Test

| Layer | Technology |
|---|---|
| Unit tests | JUnit 5 |
| Mocking | Mockito 5 |
| Integration tests | Spring Boot Test + Testcontainers (optional) |

### Why these choices

- **Spring Boot Starter for Telegram** (not the bare library) — gives
  auto-registration of the bot, integration with Spring's DI and scheduler,
  cleaner code.
- **H2 in file mode for dev** (not in-memory) — survives bot restarts during
  development, no need to reseed every run.
- **Flyway over Liquibase** — simpler SQL-based migrations, same as Nexus.
- **JPA over JOOQ** — simpler for a small project; JOOQ is overkill here.

---

## 5. Database Schema

All tables use `BIGSERIAL` / `BIGINT IDENTITY` primary keys. All timestamps
are stored in UTC; conversion to local time happens at the display layer.

### Tables

#### `service_category`

A high-level grouping of services (e.g. "Maintenance", "Diagnostics",
"Tire service").

| Column | Type | Notes |
|---|---|---|
| id | BIGINT, PK | |
| name | VARCHAR(100), NOT NULL | "Maintenance", "Diagnostics", etc. |
| description | VARCHAR(500) | Shown when user browses category |
| sort_order | INT, DEFAULT 0 | Display order in menus |

#### `service`

A specific service offered (e.g. "Oil change", "Brake pad replacement").

| Column | Type | Notes |
|---|---|---|
| id | BIGINT, PK | |
| category_id | BIGINT, FK → service_category | |
| name | VARCHAR(150), NOT NULL | |
| description | VARCHAR(1000) | Shown when user views service |
| duration_minutes | INT, NOT NULL | Used for slot blocking |
| price_from | INT | In UZS, nullable |
| price_to | INT | In UZS, nullable, ≥ price_from |
| active | BOOLEAN, DEFAULT TRUE | Soft-disable without deleting |

#### `car_brand` and `car_model`

Reference data so users pick from a list, not free-text.

| `car_brand` columns | Type | Notes |
|---|---|---|
| id | BIGINT, PK | |
| name | VARCHAR(50), UNIQUE | "Toyota", "Hyundai", "Chevrolet" |

| `car_model` columns | Type | Notes |
|---|---|---|
| id | BIGINT, PK | |
| brand_id | BIGINT, FK → car_brand | |
| name | VARCHAR(80) | "Camry", "Sonata", "Lacetti" |

#### `client`

A Telegram user who has interacted with the bot.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT, PK | |
| telegram_user_id | BIGINT, UNIQUE, NOT NULL | From Telegram API |
| telegram_username | VARCHAR(100) | Nullable (some users have no @username) |
| first_name | VARCHAR(100) | From Telegram |
| phone | VARCHAR(20) | Asked during registration |
| created_at | TIMESTAMP, NOT NULL | UTC |

#### `car`

A car owned by a client. A client may have multiple cars.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT, PK | |
| client_id | BIGINT, FK → client | |
| brand_id | BIGINT, FK → car_brand | |
| model_id | BIGINT, FK → car_model | |
| year | INT | E.g. 2018 |
| engine_info | VARCHAR(100) | Free text, e.g. "1.6 petrol" |
| created_at | TIMESTAMP, NOT NULL | UTC |

#### `booking`

The main record. Status transitions are: `PENDING → CONFIRMED → COMPLETED`,
or `PENDING → REJECTED` / `CONFIRMED → CANCELLED`.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT, PK | |
| client_id | BIGINT, FK → client, NOT NULL | |
| car_id | BIGINT, FK → car, NOT NULL | |
| service_id | BIGINT, FK → service, NULL | NULL when booking from free-text problem path; admin fills in later |
| problem_description | VARCHAR(1000) | NOT NULL when service_id IS NULL |
| scheduled_at | TIMESTAMP, NOT NULL | UTC start time |
| end_at | TIMESTAMP, NOT NULL | UTC, computed as scheduled_at + service.duration_minutes (or default 60 min for problem path) |
| status | VARCHAR(20), NOT NULL | PENDING / CONFIRMED / REJECTED / CANCELLED / COMPLETED |
| created_at | TIMESTAMP, NOT NULL | UTC |
| confirmed_at | TIMESTAMP | UTC, set when admin confirms |
| notes | VARCHAR(1000) | Optional notes from client |

**Index:** `(scheduled_at, end_at, status)` for slot-conflict queries.

**Constraint to prevent double-booking:** an `EXCLUDE` constraint or
application-level pessimistic lock. See [Pitfall 1](#pitfall-1-race-condition-on-booking).

#### `working_hours`

When the STO is open. One row per day of week.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT, PK | |
| day_of_week | INT | 1 = Mon, 7 = Sun (ISO 8601) |
| open_time | TIME | E.g. 09:00 |
| close_time | TIME | E.g. 18:00 |
| is_working | BOOLEAN, DEFAULT TRUE | False for closed days |

#### `bot_state`

Tracks where each user is in a multi-step conversation. Without this, a
bot restart would lose users mid-booking.

| Column | Type | Notes |
|---|---|---|
| telegram_user_id | BIGINT, PK | |
| current_state | VARCHAR(50) | Enum name, see [State Machine](#8-booking-state-machine) |
| state_data | JSONB / TEXT | Serialized context (selected category id, etc.) |
| updated_at | TIMESTAMP | UTC, used to expire stale states |

### Seed Data (V2 migration)

- **Categories** (5): Maintenance, Diagnostics, Repair, Tire Service, Bodywork
- **Services** (~20): plausible defaults per category with realistic durations
- **Car brands** (10–15): Toyota, Hyundai, Chevrolet, Lada, Kia, Nissan, BMW,
  Mercedes-Benz, Volkswagen, Ravon, Daewoo, Honda, Mitsubishi, Mazda, Renault
- **Car models** (~50): 3–5 popular models per brand
- **Working hours**: Mon–Sat 09:00–18:00, Sun closed

---

## 6. Bot Commands

### Client Commands

| Command | Description |
|---|---|
| `/start` | Welcome message, initiates registration if new user |
| `/book` | Start a new booking flow |
| `/mybookings` | List user's active bookings |
| `/mycars` | List/manage user's registered cars |
| `/help` | Show available commands |
| `/cancel` | Cancel the current dialogue (resets state to IDLE) |

### Admin Commands

| Command | Description |
|---|---|
| `/today` | Show today's bookings |
| `/tomorrow` | Show tomorrow's bookings |
| `/week` | Show this week's bookings |
| `/pending` | Show pending (not-yet-confirmed) bookings |
| `/confirm <id>` | Confirm a pending booking |
| `/reject <id> [reason]` | Reject a pending booking |
| `/complete <id>` | Mark a booking as completed |
| `/stats` | Show counts: today, this week, this month |

### Inline Keyboard Callbacks

Used throughout, encoded as `prefix:value` strings:

- `cat:<id>` — user picked a category
- `svc:<id>` — user picked a service
- `brand:<id>` — user picked a car brand
- `model:<id>` — user picked a car model
- `car:<id>` — user picked one of their cars
- `date:<YYYY-MM-DD>` — user picked a date
- `time:<HH:mm>` — user picked a time
- `confirm:yes` / `confirm:no` — user confirmed or declined booking
- `cancel_booking:<id>` — user cancels one of their bookings

---

## 7. User Flows

### Flow 1: First-Time User Registration

```
User: /start
Bot:  Welcome! I'll help you book a service at <STO_NAME>.
      Let's start by registering you. What's your phone number?
      (Format: +998XXXXXXXXX)
User: +998901234567
Bot:  Thanks! Now let's add your car.
      Pick a brand: [Toyota] [Hyundai] [Chevrolet] [more...]
User: [Toyota]
Bot:  Pick a model: [Camry] [Corolla] [RAV4] [more...]
User: [Camry]
Bot:  What year?
User: 2018
Bot:  Engine? (e.g. "2.5 petrol")
User: 2.5 petrol
Bot:  All set! You can now use /book to make an appointment.
```

### Flow 2: Booking (Menu Path)

```
User: /book
Bot:  How would you like to choose a service?
      [Browse menu] [Describe my problem]
User: [Browse menu]
Bot:  Pick a category:
      [Maintenance] [Diagnostics] [Repair] [Tire Service] [Bodywork]
User: [Maintenance]
Bot:  Pick a service:
      [Oil change — 45 min — 200,000–350,000 UZS]
      [Brake pad replacement — 60 min — ...]
      ...
User: [Oil change]
Bot:  Which car? [Toyota Camry 2018]
User: [Toyota Camry 2018]
Bot:  Pick a date:
      [Mon Jun 11] [Tue Jun 12] [Wed Jun 13] ...
User: [Tue Jun 12]
Bot:  Available times on Tuesday:
      [09:00] [10:00] [11:00] [14:00] [16:00]
User: [10:00]
Bot:  Confirm booking?
      Car: Toyota Camry 2018, 2.5 petrol
      Service: Oil change
      Date: Tuesday, June 12, 10:00–10:45
      Estimated price: 200,000–350,000 UZS
      [✓ Confirm] [✗ Cancel]
User: [✓ Confirm]
Bot:  Booked! Your booking #142 is awaiting admin confirmation.
      We'll notify you when confirmed. You can cancel via /mybookings.
```

### Flow 3: Booking (Free-Text Path)

```
User: /book
Bot:  How would you like to choose a service?
      [Browse menu] [Describe my problem]
User: [Describe my problem]
Bot:  Describe what's wrong with your car in your own words.
      Our master will look at it during diagnostics.
User: When I turn the wheel left at slow speed, something clunks
      under the front of the car. Started about a week ago.
Bot:  Got it. We've categorized this as diagnostics.
      Which car? [Toyota Camry 2018]
User: [Toyota Camry 2018]
Bot:  Pick a date... [continues as menu path]
```

### Flow 4: Admin Sees New Booking

When a new booking is created, all admin user IDs get a message:

```
New booking #142 (PENDING)
Client: Ulugbek (@ulugbek_dev, +998901234567)
Car: Toyota Camry 2018, 2.5 petrol
Service: Oil change (45 min)
When: Tuesday, June 12, 10:00–10:45
[✓ Confirm] [✗ Reject]
```

### Flow 5: Reminder (1 day before)

Spring Scheduler runs every hour, finds confirmed bookings whose
`scheduled_at` falls within the next 24–25 hours, and sends:

```
Reminder: your booking is tomorrow.
Service: Oil change
Time: 10:00
Car: Toyota Camry 2018
Address: <STO_ADDRESS>

If you can't make it, please /cancel.
```

---

## 8. Booking State Machine

Each user's conversation state is tracked in the `bot_state` table. The
state machine handles multi-step booking flows.

### States

```
IDLE
├── /start (new user)   → AWAITING_PHONE
├── /start (returning)  → IDLE  (just shows menu)
├── /book               → AWAITING_BOOK_PATH

AWAITING_PHONE         → AWAITING_CAR_BRAND
AWAITING_CAR_BRAND     → AWAITING_CAR_MODEL
AWAITING_CAR_MODEL     → AWAITING_CAR_YEAR
AWAITING_CAR_YEAR      → AWAITING_CAR_ENGINE
AWAITING_CAR_ENGINE    → IDLE (registration done)

AWAITING_BOOK_PATH     → (menu) AWAITING_CATEGORY
                       → (free text) AWAITING_PROBLEM_DESCRIPTION

AWAITING_CATEGORY      → AWAITING_SERVICE
AWAITING_SERVICE       → AWAITING_BOOK_CAR
AWAITING_PROBLEM_DESCRIPTION → AWAITING_BOOK_CAR

AWAITING_BOOK_CAR      → AWAITING_BOOK_DATE
AWAITING_BOOK_DATE     → AWAITING_BOOK_TIME
AWAITING_BOOK_TIME     → AWAITING_BOOK_CONFIRM
AWAITING_BOOK_CONFIRM  → (yes) IDLE (booking created)
                       → (no)  IDLE (booking discarded)
```

### `state_data` JSON Schema

The `state_data` field carries the partial booking context:

```json
{
  "categoryId": 2,
  "serviceId": 7,
  "problemDescription": null,
  "carId": 15,
  "scheduledDate": "2026-06-12",
  "scheduledTime": "10:00"
}
```

### `/cancel` Behavior

At any state, `/cancel` resets `current_state` to `IDLE` and clears
`state_data`. Confirms with: "Cancelled. Use /book to start again."

### State Expiration

States older than 1 hour are reset to `IDLE` by a daily Spring Scheduler
job. Prevents users from being stuck in a stale state forever.

---

## 9. Project Structure

```
src/main/java/com/umd/stobooking/
├── StoBookingApplication.java        — Spring Boot main class
├── config/
│   ├── BotConfig.java                — Registers bot with TelegramBots API
│   ├── AdminConfig.java              — Loads admin IDs from properties
│   └── SchedulerConfig.java          — Enables @Scheduled
├── bot/
│   ├── StoBookingBot.java            — Main bot class (extends TelegramLongPollingBot)
│   ├── handler/
│   │   ├── CommandHandler.java       — Dispatches /start, /book, etc.
│   │   ├── CallbackHandler.java      — Dispatches inline-keyboard callbacks
│   │   └── TextMessageHandler.java   — Handles plain text per state
│   ├── state/
│   │   ├── BotStateEnum.java         — All states
│   │   └── StateContext.java         — DTO mapping to state_data JSON
│   ├── keyboard/
│   │   └── KeyboardFactory.java      — Builds all InlineKeyboardMarkup objects
│   └── reply/
│       └── ReplyFactory.java         — Builds all SendMessage objects (strings & layout)
├── model/
│   ├── Client.java                   — JPA entity
│   ├── Car.java
│   ├── CarBrand.java
│   ├── CarModel.java
│   ├── ServiceCategory.java
│   ├── Service.java                  — Renamed to ServiceItem to avoid clash with Spring's @Service
│   ├── Booking.java
│   ├── BookingStatus.java            — Enum
│   ├── WorkingHours.java
│   └── BotStateEntity.java
├── repository/
│   ├── ClientRepository.java
│   ├── CarRepository.java
│   ├── CarBrandRepository.java
│   ├── CarModelRepository.java
│   ├── ServiceCategoryRepository.java
│   ├── ServiceItemRepository.java
│   ├── BookingRepository.java
│   ├── WorkingHoursRepository.java
│   └── BotStateRepository.java
├── service/
│   ├── ClientService.java            — Register, lookup by Telegram ID
│   ├── CarService.java               — Add car, list cars
│   ├── CatalogService.java           — List categories, services
│   ├── ScheduleService.java          — Compute available slots
│   ├── BookingService.java           — Create, cancel, list, transitions
│   ├── ReminderService.java          — Spring Scheduler @Scheduled job
│   ├── AdminService.java             — Admin-only operations
│   └── StateService.java             — Get/set/clear bot state
└── util/
    ├── DateTimeUtil.java             — UTC ↔ Tashkent conversions
    ├── PhoneValidator.java           — +998XXXXXXXXX validation
    └── MessageFormatter.java         — Format prices, durations, etc.

src/main/resources/
├── application.properties.example    — Committed template
├── application.properties            — .gitignored, with real token
├── db/migration/
│   ├── V1__schema.sql                — All tables
│   ├── V2__seed_data.sql             — Categories, services, brands, models, working hours
│   └── V3__indexes.sql               — Performance indexes
└── logback-spring.xml                — Logging config

src/test/java/com/umd/stobooking/
├── service/
│   ├── BookingServiceTest.java
│   ├── ScheduleServiceTest.java
│   ├── StateServiceTest.java
│   └── ...
└── util/
    ├── DateTimeUtilTest.java
    └── PhoneValidatorTest.java
```

---

## 10. Configuration

### `application.properties.example`

```properties
# Telegram
telegram.bot.username=your_bot_username
telegram.bot.token=PLACEHOLDER_PUT_REAL_TOKEN_IN_APPLICATION_PROPERTIES

# Admin Telegram user IDs (comma-separated)
admin.telegram-ids=123456789

# STO info (used in messages)
sto.name=Auto Service Tashkent
sto.address=Tashkent, Yunusabad, Street 5
sto.phone=+998901234567
sto.timezone=Asia/Tashkent

# Database (H2 file mode for dev)
spring.datasource.url=jdbc:h2:file:./data/stobookingdb
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect

# For production, switch to PostgreSQL:
# spring.datasource.url=jdbc:postgresql://localhost:5432/stobookingdb
# spring.datasource.username=sto_user
# spring.datasource.password=...
# spring.datasource.driver-class-name=org.postgresql.Driver
# spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true

# Logging
logging.level.com.umd.stobooking=DEBUG
logging.level.org.telegram=INFO
```

### `.gitignore` additions

```
application.properties
data/
*.mv.db
*.trace.db
.idea/
.claude/
target/
```

---

## 11. 7-Day Development Plan

Each day is 2–4 hours of focused work. By end of day 7 you have a deployed,
working bot.

### Day 1 — Project Setup & Echo Bot

**Goal:** Bot exists and replies to `/start`.

- Create Spring Boot project via Spring Initializr (web, jpa, validation,
  h2, postgresql, devtools, lombok)
- Add `telegrambots-spring-boot-starter` to `pom.xml`
- Create `application.properties.example`, copy to `application.properties`,
  fill in real bot token (from BotFather)
- Add token, username, admin IDs to `.gitignore`
- Create `StoBookingApplication.java`
- Create `BotConfig.java` that registers the bot with `TelegramBotsApi`
- Create `StoBookingBot.java` extending `TelegramLongPollingBot`
- In `onUpdateReceived()`, just echo "Hello, you said: ..."
- Run, send `/start` from Telegram, verify reply
- First commit, push to GitHub

**Deliverable:** Echo bot responds in Telegram.

### Day 2 — Database Schema & Seed Data

**Goal:** All tables exist, services and car brands are seeded, queryable
via H2 console.

- Create all JPA entities (`Client`, `Car`, `CarBrand`, `CarModel`,
  `ServiceCategory`, `ServiceItem`, `Booking`, `WorkingHours`,
  `BotStateEntity`)
- Create all repositories (`extends JpaRepository`)
- Write `V1__schema.sql` with all `CREATE TABLE` statements
- Write `V2__seed_data.sql` with categories, services, brands, models,
  working hours
- Write `V3__indexes.sql` with performance indexes
- Enable H2 console (`spring.h2.console.enabled=true`)
- Run app, visit `http://localhost:8080/h2-console`, verify schema and seed
- Write a quick integration test: load services from repository, assert
  count > 0

**Deliverable:** Schema + seed in place, browsable.

### Day 3 — State Machine & Registration Flow

**Goal:** New user can complete registration through Telegram.

- Implement `BotStateEnum`, `StateContext` (Lombok DTO + Jackson
  serialization)
- Implement `StateService` (get/set/clear state per Telegram user ID)
- Split `onUpdateReceived` into `CommandHandler`, `CallbackHandler`,
  `TextMessageHandler`
- Implement `/start`:
  - If new user → ask phone, state `AWAITING_PHONE`
  - If returning → show greeting + menu
- Handle phone input (validate `+998XXXXXXXXX`), save client, transition to
  `AWAITING_CAR_BRAND`
- Show car brand inline keyboard, handle callback, transition states
- Continue through model, year, engine
- Save car, set state to `IDLE`, show confirmation

**Deliverable:** Walking through `/start` registers user and one car.

### Day 4 — Service Selection (Menu Path)

**Goal:** User can `/book` via menu and reach the "pick date" step.

- Implement `/book` command → state `AWAITING_BOOK_PATH`
- Show "[Browse menu] [Describe problem]" inline keyboard
- On "Browse menu" callback → `AWAITING_CATEGORY`
- `CatalogService.listCategories()` → build keyboard
- On category callback → `AWAITING_SERVICE`, show services in category
  with name + duration + price
- On service callback → store `serviceId` in `state_data`, transition to
  `AWAITING_BOOK_CAR`
- Show user's cars as inline keyboard
- On car callback → store `carId`, transition to `AWAITING_BOOK_DATE`
- For now, just reply "Date selection coming tomorrow" — full impl on day 5

**Deliverable:** Can browse menu, pick service, pick car.

### Day 5 — Date/Time Selection & Booking Creation

**Goal:** A booking can be created end-to-end.

- Implement `ScheduleService.availableDates(serviceId)` — returns next 7–14
  days that have any free slot, considering `working_hours`
- Implement `ScheduleService.availableSlots(date, serviceId)` — returns
  `LocalTime` slots based on `working_hours`, `service.duration_minutes`,
  existing bookings (`scheduled_at`, `end_at`)
- Build date keyboard, transition to `AWAITING_BOOK_TIME`
- Build time keyboard, transition to `AWAITING_BOOK_CONFIRM`
- Build confirmation message with all details
- On confirm: `BookingService.createBooking(...)` — must be `@Transactional`
  and re-check slot availability inside the transaction (see
  [Pitfall 1](#pitfall-1-race-condition-on-booking))
- Reset state to `IDLE`
- Notify all admins about the new booking

**Deliverable:** End-to-end booking works. Two simultaneous bookings on the
same slot — only one succeeds.

### Day 6 — Free-Text Problem Path, My Bookings, Cancel

**Goal:** All client-side features work.

- Implement "Describe my problem" branch:
  - State `AWAITING_PROBLEM_DESCRIPTION`
  - User sends free text → save to `state_data.problemDescription`
  - Booking is created with `service_id = NULL`, `problem_description = ...`,
    and default duration (60 min)
- Implement `/mybookings`:
  - List active bookings (status PENDING or CONFIRMED, scheduled_at in future)
  - Each booking has a [Cancel] inline button
- Implement booking cancellation via callback
- Add boundary checks: can't cancel within 2 hours of booking time

**Deliverable:** Full client experience works.

### Day 7 — Admin Features, Reminders, Polish

**Goal:** Admin can manage bookings; reminders work; bot is deployable.

- Implement admin authorization helper: `AdminService.isAdmin(telegramUserId)`
- Implement `/today`, `/tomorrow`, `/week`, `/pending`
- Implement `/confirm <id>`, `/reject <id>`, `/complete <id>`
- Notify the client when admin confirms/rejects/completes their booking
- Implement `ReminderService`:
  - `@Scheduled(cron = "0 0 * * * *")` — every hour
  - Find confirmed bookings 24–25 hours away → send reminder
- Implement state expiration:
  - `@Scheduled(cron = "0 0 3 * * *")` — daily at 3 AM
  - Reset states older than 1 hour to IDLE
- Polish: error handling, logging, README with deploy instructions
- (Optional) Deploy to Render.com / Koyeb / Fly.io

**Deliverable:** MVP complete. Can be demoed to a real STO.

---

## 12. Technical Pitfalls

Common mistakes and how to avoid them.

### Pitfall 1: Race Condition on Booking

**Problem:** Two users pick the same time slot within milliseconds of each
other. Both transactions check "is slot free?" simultaneously, both see
"yes", both insert a booking. Result: double-booked slot.

**Solution:** Inside the `@Transactional` booking creation method:

1. Re-query for any booking that overlaps `[scheduled_at, end_at]` with
   status IN (PENDING, CONFIRMED) — using pessimistic write lock if
   possible (`SELECT ... FOR UPDATE` in PostgreSQL).
2. If any overlap found, throw `SlotUnavailableException` — the user gets
   "sorry, just taken, please pick another time".
3. Otherwise, insert the new booking.

In PostgreSQL prod, an additional safety net is a GiST `EXCLUDE` constraint
with `tstzrange(scheduled_at, end_at, '[)')`. This is bulletproof but H2
doesn't support it, so for MVP rely on app-level locking.

### Pitfall 2: Time Zones

**Problem:** Storing local times leads to bugs on daylight saving, server
reboots in different zones, etc.

**Solution:**
- DB stores everything in **UTC** as `TIMESTAMP WITHOUT TIME ZONE`
- App converts to `Asia/Tashkent` (UTC+5, no DST) when displaying or parsing
- `DateTimeUtil.toLocal(Instant)` and `DateTimeUtil.toUtc(LocalDateTime)`
- Don't use `LocalDateTime.now()` — always `Instant.now()` or
  `ZonedDateTime.now(ZoneId.of("Asia/Tashkent"))`

### Pitfall 3: Bot Token Leakage

**Problem:** Committing `application.properties` with a real token. The token
is essentially a password and gets posted in your public repo.

**Solution:**
- `.gitignore` includes `application.properties`
- Commit `application.properties.example` instead, with placeholder values
- README explains how to copy the example and fill in the token
- If you ever leak a token, immediately revoke via BotFather (`/revoke`)

### Pitfall 4: Long Polling Goes Stale on Restart

**Problem:** After bot restart, sometimes updates are missed.

**Solution:** TelegramBots library handles this if you correctly persist
`bot_state` to DB (not in-memory). Use the library's default settings; don't
override `getLastReceivedUpdate`.

### Pitfall 5: Inline Keyboard Callback Size Limit

**Problem:** Telegram limits callback_data to 64 bytes. Encoding long IDs
or multiple fields breaks silently.

**Solution:**
- Use short prefixes: `svc:` not `service:`
- Use numeric IDs, not UUIDs
- If you need more data, look it up server-side from the ID, don't pack
  everything into callback_data

### Pitfall 6: Booking Across Closing Time

**Problem:** STO closes at 18:00, service is 60 min. Naive slot generator
offers 17:30 — but service would end at 18:30, after closing.

**Solution:** When generating available slots, ensure
`slotStart + service.duration ≤ closeTime`.

### Pitfall 7: Phone Number Formats

**Problem:** Users enter `8 90 123 45 67`, `+998 90 123-45-67`,
`998901234567`, etc.

**Solution:** `PhoneValidator.normalize(input)`:
1. Strip all non-digit characters
2. If starts with `8` and length is 12, replace with `998`
3. Validate: must be exactly 12 digits starting with `998`
4. Return as `+998XXXXXXXXX`
5. Reject anything else with a friendly error message

### Pitfall 8: User Sends `/cancel` During Booking

**Problem:** User is deep in the booking flow, sends `/cancel`. If you only
handle `/cancel` as a regular command, it doesn't reset state.

**Solution:** In `CommandHandler`, `/cancel` is special-cased: it always
clears state to IDLE regardless of where the user was.

### Pitfall 9: Admin Notifications Overwhelm

**Problem:** During testing or busy periods, admins get flooded with
"new booking" messages.

**Solution:** For MVP, accept this. In Phase 2, add a digest mode (one
message every 15 minutes summarizing).

### Pitfall 10: Forgetting to Test the Sad Paths

**Problem:** Booking works, but what if the user sends garbage during
phone input, or picks a date in the past, or types `/book` while already
in a booking flow?

**Solution:** For each state, write down: "what if user sends text?", "what
if user sends another command?", "what if user sends a sticker?". Handle
each with a polite "I expected a phone number, please try again" message.

---

## 13. MVP Acceptance Checklist

Before considering the bot "done":

### Client Side

- [ ] `/start` registers a new user with phone validation
- [ ] User can add at least one car (brand, model, year, engine)
- [ ] User can `/book` via the menu path and complete a booking
- [ ] User can `/book` via the free-text problem path and complete a booking
- [ ] User can `/mybookings` and see active bookings
- [ ] User can cancel a booking via inline button
- [ ] User can't cancel a booking less than 2 hours before
- [ ] User can `/cancel` at any point during a multi-step flow and reset
- [ ] User gets a reminder ~24 hours before their booking

### Admin Side

- [ ] Admin gets a notification when a new booking is created
- [ ] `/today`, `/tomorrow`, `/week`, `/pending` show correct bookings
- [ ] `/confirm <id>` confirms a booking and notifies the client
- [ ] `/reject <id>` rejects a booking and notifies the client
- [ ] `/complete <id>` marks a booking complete
- [ ] `/stats` shows accurate counts
- [ ] Non-admin users do not see admin commands

### Reliability

- [ ] Two users booking the same slot simultaneously — only one succeeds
- [ ] Bot restart mid-conversation — user's state is preserved
- [ ] Bot handles garbage input at every state gracefully
- [ ] All timestamps display in Tashkent time, not UTC
- [ ] No bot token in git history
- [ ] All migrations apply cleanly on a fresh DB
- [ ] H2 → PostgreSQL switch documented and tested

### Code Quality

- [ ] At least 5–10 unit tests covering core services
- [ ] No `System.out.println` — uses SLF4J logger
- [ ] No hardcoded magic strings — use constants or properties
- [ ] README explains setup, build, run, and BotFather steps
- [ ] `application.properties.example` is up to date

---

## 14. Future Enhancements

Documented here so they're not forgotten but consciously deferred.

### Phase 2 (next 2–4 weeks)

- **Master/mechanic assignment** — book with a specific mechanic
- **Variable-duration services** — admin marks "actual duration" on
  completion, frees up the remaining slot
- **Photo upload** — client sends photos of damage during free-text path
- **Service history per car** — show past visits
- **Multi-language** — Uzbek + Russian + English
- **Customer feedback** — 1–5 stars + comment after `COMPLETED`
- **Recurring maintenance reminders** — "your last oil change was 6 months
  ago, time for the next one?"

### Phase 3 (longer term)

- **Web admin panel** — REST API + React frontend
- **Multi-STO support** — aggregator mode
- **Payment integration** — Click, Payme, Uzum
- **CRM features** — customer loyalty, discounts
- **SMS fallback** — for clients without Telegram
- **Analytics dashboard** — visit frequency, revenue, popular services

---

## Appendix: Useful Commands

### BotFather Commands (reference)

| Command | Use |
|---|---|
| `/newbot` | Create a new bot |
| `/setname` | Change display name |
| `/setdescription` | Set bot description |
| `/setabouttext` | Set "about" text |
| `/setcommands` | Set the slash-command menu |
| `/setuserpic` | Set avatar |
| `/revoke` | Revoke current token (use if leaked) |
| `/mybots` | List your bots |

### How to Find Your Telegram User ID

Send any message to `@userinfobot` and it replies with your numeric ID.
Use this ID in `admin.telegram-ids` to grant yourself admin access.

### Useful References

- [Telegram Bot API docs](https://core.telegram.org/bots/api)
- [TelegramBots Java library](https://github.com/rubenlagus/TelegramBots)
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
- [Flyway docs](https://documentation.red-gate.com/fd/)

---

**End of Specification.**
