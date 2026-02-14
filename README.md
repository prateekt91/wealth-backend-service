# Wealth Manager Backend Service

Core backend service for the Wealth Manager application. Receives SMS messages forwarded from an Android device, stores them for AI-based parsing, and exposes transaction data via REST APIs with real-time WebSocket notifications.

## Tech Stack

| Technology      | Version | Purpose                          |
|-----------------|---------|----------------------------------|
| Java            | 25      | Runtime                          |
| Spring Boot     | 4.0.2   | Application framework            |
| Spring Security | 6.x     | API key authentication           |
| Spring Data JPA | 4.x     | Database access (Hibernate)      |
| PostgreSQL      | 16      | Primary database                 |
| Flyway          | 10.x    | Database migrations              |
| WebSocket/STOMP | -       | Real-time push notifications     |
| Lombok          | -       | Boilerplate reduction            |
| Maven           | 3.9+    | Build tool                       |
| Docker          | -       | Containerization                 |

## Prerequisites

- **Java 21** (Eclipse Temurin or similar)
- **Maven 3.9+**
- **Docker & Docker Compose** (for local infrastructure)
- **PostgreSQL 16** (or use Docker Compose)

## Quick Start

### 1. Start Infrastructure (PostgreSQL + Redis)

```bash
docker-compose up -d postgres redis
```

### 2. Run the Application

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. Or Run Everything with Docker Compose

```bash
docker-compose up --build
```

The service will be available at `http://localhost:8080`.

## API Endpoints

### Bridge (SMS Ingestion)

| Method | Endpoint                  | Description           | Auth         |
|--------|---------------------------|-----------------------|--------------|
| POST   | `/api/v1/bridge/ingest`   | Ingest an SMS message | API Key      |

**Headers Required:**
- `X-API-KEY: <your-api-key>`
- `Content-Type: application/json`

**Request Body:**
```json
{
  "sender": "+919876543210",
  "body": "Rs.500 debited from A/c XX1234 to AMAZON on 14-02-2026",
  "receivedAt": "2026-02-14T10:30:00",
  "deviceId": "pixel-7a"
}
```

**Response (202 Accepted):**
```json
{
  "status": "accepted",
  "ingestionId": 1,
  "message": "SMS queued for processing"
}
```

### Transactions

| Method | Endpoint                       | Description                   | Auth |
|--------|--------------------------------|-------------------------------|------|
| GET    | `/api/v1/transactions`         | List transactions (paginated) | Open |
| GET    | `/api/v1/transactions/{id}`    | Get single transaction        | Open |
| GET    | `/api/v1/transactions/summary` | Get summary statistics        | Open |

**Pagination Parameters:** `?page=0&size=20`

### WebSocket (STOMP)

| Endpoint    | Topic                  | Description             |
|-------------|------------------------|-------------------------|
| `/ws`       | `/topic/ingestion`     | New SMS ingestion events|
| `/ws`       | `/topic/transactions`  | New transaction events  |

## Environment Variables

| Variable         | Default                              | Description                     |
|------------------|--------------------------------------|---------------------------------|
| `DB_USERNAME`    | `postgres`                           | PostgreSQL username             |
| `DB_PASSWORD`    | `postgres`                           | PostgreSQL password             |
| `BRIDGE_API_KEY` | `dev-api-key-change-in-production`   | API key for bridge endpoints    |
| `SPRING_PROFILES_ACTIVE` | (none)                       | Active Spring profile (`dev`)   |

## Architecture

```
Android Phone (SMS Forwarder)
        │
        ▼  POST /api/v1/bridge/ingest (X-API-KEY)
┌───────────────────────────────────┐
│   Wealth Backend Service          │
│                                   │
│  ┌──────────┐   ┌──────────────┐  │
│  │ Bridge   │──▶│ Ingestion    │  │
│  │ Controller│   │ Service      │  │
│  └──────────┘   └──────┬───────┘  │
│                         │          │
│              ┌──────────▼───────┐  │
│              │ Raw Ingestion    │  │
│              │ Table (staging)  │  │
│              └──────────┬───────┘  │
│                         │          │
│              ┌──────────▼───────┐  │
│              │ AI/MCP Parser    │  │
│              │ (future)         │  │
│              └──────────┬───────┘  │
│                         │          │
│              ┌──────────▼───────┐  │
│              │ Transaction      │  │
│              │ Table            │  │
│              └──────────────────┘  │
│                                   │
│  ┌──────────┐   ┌──────────────┐  │
│  │ Txn      │──▶│ Transaction  │  │
│  │ Controller│   │ Service      │  │
│  └──────────┘   └──────────────┘  │
│                                   │
│  ┌──────────────────────────────┐ │
│  │ WebSocket (STOMP /topic/*)   │ │
│  └──────────────────────────────┘ │
└───────────────────────────────────┘
        │
        ▼
  React Dashboard (localhost:5173)
```

## Database Migrations

Migrations are managed by Flyway and located in `src/main/resources/db/migration/`:

- `V1__create_raw_ingestion_table.sql` — Raw SMS staging table
- `V2__create_transaction_table.sql` — Parsed transaction table

## Development

### Running Tests

```bash
mvn test
```

Tests use an in-memory H2 database and do not require PostgreSQL.

### Code Structure

```
src/main/java/com/wealthmanager/backend/
├── WealthBackendApplication.java    # Entry point
├── config/
│   ├── SecurityConfig.java          # Spring Security configuration
│   └── WebSocketConfig.java         # STOMP WebSocket configuration
├── controller/
│   ├── BridgeIngestController.java  # SMS ingestion endpoint
│   └── TransactionController.java   # Transaction CRUD endpoints
├── exception/
│   └── GlobalExceptionHandler.java  # Centralized error handling
├── model/
│   ├── RawIngestion.java            # Raw SMS entity
│   ├── Transaction.java             # Parsed transaction entity
│   └── dto/
│       ├── SmsPayload.java          # Ingestion request DTO
│       └── TransactionResponse.java # Transaction response DTO
├── repository/
│   ├── RawIngestionRepository.java  # Raw ingestion data access
│   └── TransactionRepository.java   # Transaction data access
├── security/
│   └── ApiKeyAuthFilter.java        # API key authentication filter
└── service/
    ├── IngestionService.java        # SMS ingestion logic
    ├── NotificationService.java     # WebSocket notifications
    └── TransactionService.java      # Transaction business logic
```

## License

Private — All rights reserved.
