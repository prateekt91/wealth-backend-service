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
| Spring AI       | 1.0.0-M7| LLM integration (Ollama local)   |
| Maven           | 3.9+    | Build tool                       |
| Docker          | -       | Containerization                 |

## Prerequisites

- **Java 25** (Eclipse Temurin or similar)
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

## Spring AI / Local LLM (Ollama)

Transaction parsing uses **Spring AI** with a **local LLM** (Ollama) by default. Raw SMS/email text is sent to the model to extract amount, type (DEBIT/CREDIT), merchant, date, etc., and the result is stored as a `Transaction`.

### Setup Ollama (local)

1. Install [Ollama](https://ollama.com/download) and start it (default: `http://localhost:11434`).
2. Pull a model: `ollama pull llama3.2` (or set `OLLAMA_CHAT_MODEL` to another model).
3. Set `AI_ENABLED=true` (default) and optionally:
   - `OLLAMA_BASE_URL` – Ollama server URL (default `http://localhost:11434`)
   - `OLLAMA_CHAT_MODEL` – model name (default `llama3.2`)
   - `AI_BACKLOG_INTERVAL_MS` – how often to process unprocessed ingestions (default 300000 = 5 min)

When `AI_ENABLED=false`, a no-op parser is used (no LLM calls). The design allows adding **public LLM** support (e.g. OpenAI, Azure) later via the same `TransactionParser` interface and `app.ai.provider` configuration.

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
  "sender": "+9198xxxxxxxx",
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
| `GMAIL_ENABLED`  | `false`                              | Set `true` to enable Gmail polling |
| `GMAIL_CLIENT_ID` | (none)                              | Google OAuth 2.0 Client ID      |
| `GMAIL_CLIENT_SECRET` | (none)                            | Google OAuth 2.0 Client Secret  |
| `GMAIL_REFRESH_TOKEN` | (none)                            | Refresh token from OAuth flow   |
| `GMAIL_REDIRECT_URI` | `http://localhost:8080/api/v1/bridge/gmail/callback` | OAuth redirect URI |
| `GMAIL_POLL_INTERVAL_MS` | `60000`                         | How often to poll Gmail (ms)     |
| `GMAIL_INITIAL_LOOKBACK_MINUTES` | `1440`                    | How far back to scan on first run (minutes) |
| `AI_ENABLED` | `true` | Use LLM for transaction parsing |
| `AI_PROVIDER` | `ollama` | LLM provider (future: openai, azure) |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_CHAT_MODEL` | `llama3.2` | Ollama model name |
| `AI_BACKLOG_INTERVAL_MS` | `300000` | Backlog parsing interval (ms) |

## Gmail API – Fetching transaction emails

The backend can poll Gmail (read-only) for transaction-related emails (debits, credits, UPI, etc.) and ingest them like SMS. Setup is a one-time OAuth2 flow plus configuration.

### 1. Create Google Cloud project and enable Gmail API

1. Go to [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project (or select one) → **APIs & Services** → **Library**.
3. Search for **Gmail API** and **Enable** it.

### 2. Create OAuth 2.0 credentials

1. **APIs & Services** → **Credentials** → **Create Credentials** → **OAuth client ID**.
2. If prompted, configure the **OAuth consent screen**:
   - User type: **External** (or Internal for Workspace).
   - App name: e.g. `Wealth Manager`.
   - Add your email under **Developer contact**.
   - Scopes: add **`https://www.googleapis.com/auth/gmail.readonly`**.
   - Save.
3. Create the OAuth client:
   - Application type: **Web application**.
   - Name: e.g. `Wealth Manager Backend`.
   - **Authorized redirect URIs**: add:
     - `http://localhost:8080/api/v1/bridge/gmail/callback` (local)
     - If you deploy, add your production URL, e.g. `https://your-domain.com/api/v1/bridge/gmail/callback`.
4. Click **Create**. Copy the **Client ID** and **Client Secret**.

### 3. Configure the backend

Set the client ID and secret (env vars or `application.yml` / `application-dev.yml`):

```bash
# Windows (PowerShell)
$env:GMAIL_CLIENT_ID="your-client-id.apps.googleusercontent.com"
$env:GMAIL_CLIENT_SECRET="your-client-secret"

# Or in application-dev.yml (do not commit secrets; use env vars in production)
# app.gmail.client-id: ${GMAIL_CLIENT_ID}
# app.gmail.client-secret: ${GMAIL_CLIENT_SECRET}
```

Do **not** set `GMAIL_ENABLED=true` or `GMAIL_REFRESH_TOKEN` yet.

### 4. Get the refresh token (one-time OAuth flow)

1. Start the backend (e.g. `mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`).
2. Open in browser:
   ```text
   http://localhost:8080/api/v1/bridge/gmail/auth-url
   ```
   Or call it with curl and open the returned `authUrl` in the browser.
3. Sign in with the Google account that receives transaction emails and **Allow** access.
4. You will be redirected to a URL like:
   ```text
   http://localhost:8080/api/v1/bridge/gmail/callback?code=4/0A...
   ```
   The page will show a JSON response containing **`refreshToken`**.
5. Copy the **refresh token** and set it:
   ```bash
   $env:GMAIL_REFRESH_TOKEN="the-refresh-token-from-callback"
   ```

### 5. Enable Gmail polling and restart

```bash
$env:GMAIL_ENABLED="true"
# Restart the application
```

Optional tuning (env or config):

- **GMAIL_POLL_INTERVAL_MS** – interval between Gmail polls (default `60000` = 1 minute).
- **GMAIL_INITIAL_LOOKBACK_MINUTES** – how far back to look on first run (default `1440` = 24 hours).
- **app.gmail.search-keywords** – comma-separated keywords used to find transaction emails (default includes: debit, credit, debited, credited, UPI, NEFT, etc.).

After restart, the backend will:

- Poll Gmail on the configured interval.
- Search INBOX for messages matching the keywords and date range.
- Ingest matching emails into the same pipeline as SMS (raw ingestion → parsing later).

### Gmail endpoints (no API key required for these)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/bridge/gmail/auth-url` | Returns Google OAuth consent URL |
| GET | `/api/v1/bridge/gmail/callback?code=...` | OAuth callback; returns refresh token |
| GET | `/api/v1/bridge/gmail/status` | Status (enabled, has token, poll interval, keywords) |

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
