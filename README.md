# STO Booking Telegram Bot

A Telegram bot for online booking at a single automotive service station (STO).
Built with Java 21 + Spring Boot 3.3.6. Guides clients through registering their car, choosing a service, picking a time slot, and confirming a booking — all inside Telegram.

Live bot: [@STO1709_bot](https://t.me/STO1709_bot)

---

## Features

### Client side
- Register with phone number and car (brand, model, year, engine)
- Book a service two ways:
  - **Menu path** — pick a category → service → date → time
  - **Problem path** — describe the issue in free text, admin reviews it
- View active bookings (`/mybookings`) with inline cancel button
- Cancel a booking (up to 2 hours before the appointment)
- Receive notifications when admin confirms, rejects, or completes a booking
- Reminder message sent 24 hours before a confirmed appointment

### Admin side
| Command | Description |
|---|---|
| `/today` | Bookings scheduled for today |
| `/tomorrow` | Bookings scheduled for tomorrow |
| `/week` | Bookings for the next 7 days |
| `/pending` | Bookings waiting for confirmation |
| `/confirm <id>` | Confirm a booking → notifies client |
| `/reject <id> [reason]` | Reject a booking → notifies client |
| `/complete <id>` | Mark a booking as completed → notifies client |
| `/stats` | Today's count, pending count, weekly completed/cancelled |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.6 |
| Telegram | TelegramBots 6.9.7.1 (`TelegramLongPollingBot`) |
| Database (dev) | H2 file mode |
| Database (prod) | PostgreSQL |
| Migrations | Flyway |
| ORM | Spring Data JPA / Hibernate |
| Build | Gradle |
| Scheduling | Spring `@Scheduled` |

---

## Project Structure

```
src/
├── main/
│   ├── java/com/umd/stobooking/
│   │   ├── StoBookingApplication.java
│   │   ├── bot/
│   │   │   ├── StoBookingBot.java           # Long-polling bot entry point
│   │   │   ├── handler/
│   │   │   │   ├── CommandHandler.java      # /start, /book, /mybookings, admin cmds
│   │   │   │   ├── TextMessageHandler.java  # Phone, year, engine, problem text input
│   │   │   │   └── CallbackHandler.java     # Inline keyboard callbacks
│   │   │   ├── keyboard/
│   │   │   │   └── KeyboardFactory.java     # All InlineKeyboardMarkup builders
│   │   │   └── state/
│   │   │       ├── BotStateEnum.java        # State machine states
│   │   │       └── StateContext.java        # Temporary booking data
│   │   ├── config/
│   │   │   ├── BotConfig.java              # Registers bot on startup
│   │   │   └── AdminConfig.java            # Loads admin Telegram IDs
│   │   ├── exception/
│   │   │   ├── SlotUnavailableException.java
│   │   │   └── CancellationTooLateException.java
│   │   ├── model/                          # JPA entities
│   │   ├── repository/                     # Spring Data repositories
│   │   ├── service/
│   │   │   ├── AdminService.java           # Admin booking operations
│   │   │   ├── BookingService.java         # Create / cancel bookings
│   │   │   ├── CarService.java
│   │   │   ├── CatalogService.java         # Service categories & items
│   │   │   ├── ClientService.java
│   │   │   ├── ReminderService.java        # @Scheduled reminder & state expiry jobs
│   │   │   ├── ScheduleService.java        # Available dates & time slots
│   │   │   └── StateService.java           # Bot state machine persistence
│   │   └── util/
│   │       ├── DateTimeUtil.java           # Tashkent timezone helpers
│   │       ├── MessageFormatter.java       # All message/button text formatting
│   │       └── PhoneValidator.java         # Normalizes +998 phone numbers
│   └── resources/
│       ├── application.properties          # (gitignored — contains token)
│       ├── application.properties.example  # Template to copy from
│       └── db/migration/
│           ├── V1__schema.sql              # All 9 tables
│           ├── V2__seed_data.sql           # 5 categories, 20 services, 15 brands, 50 models
│           └── V3__indexes.sql             # Performance indexes
```

---

## Getting Started

### Prerequisites
- Java 21+
- Telegram bot token from [@BotFather](https://t.me/BotFather)
- Your Telegram user ID (get it from [@userinfobot](https://t.me/userinfobot))

### 1. Clone the repository

```bash
git clone https://github.com/UMD0905/TG_BOT.git
cd TG_BOT
```

### 2. Configure the bot

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Edit `application.properties`:

```properties
telegram.bot.username=your_bot_username
telegram.bot.token=YOUR_BOT_TOKEN_HERE

# Your Telegram user ID — you become the admin
admin.telegram-ids=YOUR_TELEGRAM_USER_ID
```

### 3. Run

```bash
./gradlew bootRun
```

The bot starts, Flyway creates all tables and seeds the data automatically.

### 4. View the database (dev)

While the bot is running, open:

```
http://localhost:8080/h2-console
```

| Field | Value |
|---|---|
| JDBC URL | `jdbc:h2:file:./data/stobookingdb` |
| User Name | `SA` |
| Password | *(leave empty)* |

---

## Database Schema

```
service_category  ←── service (ServiceItem)
car_brand         ←── car_model ←── car ←── client
client ←── booking ──→ car
booking ──→ service (nullable — null means free-text path)
working_hours     (Mon–Fri 09:00–18:00, Sat 09:00–16:00, Sun closed)
bot_state         (persisted state machine, survives restarts)
```

---

## Booking State Machine

```
IDLE
 └─ /start ──────────────────────────► AWAITING_PHONE
                                              │
                                     phone validated
                                              │
                                    ┌─────────▼──────────┐
                                    │  AWAITING_CAR_BRAND │
                                    └─────────┬───────────┘
                                              │ brand selected
                                    ┌─────────▼──────────┐
                                    │  AWAITING_CAR_MODEL │
                                    └─────────┬───────────┘
                                              │ model selected
                                    ┌─────────▼──────────┐
                                    │  AWAITING_CAR_YEAR  │
                                    └─────────┬───────────┘
                                              │ year typed
                                   ┌──────────▼────────────┐
                                   │  AWAITING_CAR_ENGINE   │
                                   └──────────┬─────────────┘
                                              │ engine typed → car saved
 /book ──────────────────────────► AWAITING_BOOK_PATH
          ┌───────────────────────────────────┤
     "Menu" path                        "Problem" path
          │                                   │
 AWAITING_CATEGORY              AWAITING_PROBLEM_DESCRIPTION
          │                                   │
 AWAITING_SERVICE               AWAITING_BOOK_CAR
          │                                   │
 AWAITING_BOOK_CAR              AWAITING_BOOK_DATE
          │                                   │
 AWAITING_BOOK_DATE             AWAITING_BOOK_TIME
          │                                   │
 AWAITING_BOOK_TIME             AWAITING_BOOK_CONFIRM
          │                                   │
 AWAITING_BOOK_CONFIRM ─────────────────────► booking saved ──► IDLE
```

---

## Scheduled Jobs

| Job | Schedule | What it does |
|---|---|---|
| Reminders | Every hour (`0 0 * * * *`) | Sends reminder to clients with CONFIRMED bookings 24–25 h away |
| State expiry | Daily at 03:00 (`0 0 3 * * *`) | Resets bot states stuck for more than 1 hour back to IDLE |

---

## Production Deployment (PostgreSQL)

1. Provision a PostgreSQL database.
2. In `application.properties`, comment out the H2 block and uncomment the PostgreSQL block:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/stobookingdb
spring.datasource.username=sto_user
spring.datasource.password=secret
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

3. Build the fat JAR:

```bash
./gradlew bootJar
```

4. Run:

```bash
java -jar build/libs/sto-booking-bot-0.0.1-SNAPSHOT.jar
```

Free hosting options: [Render.com](https://render.com), [Koyeb](https://koyeb.com), [Fly.io](https://fly.io)

---

## Security Notes

- `application.properties` is in `.gitignore` — your bot token is never committed.
- Admin access is controlled by a hardcoded list of Telegram user IDs (`admin.telegram-ids`).
- Slot double-booking is prevented by a transactional overlap check on every booking creation.
- Clients cannot cancel bookings less than 2 hours before the appointment.

---

## Author

Built by **Ulugbek** as a portfolio project and reusable template for small STOs in Uzbekistan.
