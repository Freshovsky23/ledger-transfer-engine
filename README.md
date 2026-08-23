# High-Performance Double-Entry Ledger & Transfer Engine

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED.svg)](https://www.docker.com/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-Ready-326CE5.svg)](https://kubernetes.io/)
[![Spock Framework](https://img.shields.io/badge/Spock-2.4--Groovy4-brightgreen.svg)](https://spockframework.org/)

A production-grade, ACID-compliant **Double-Entry Ledger and Transfer Engine** developed in **Java 21**. Designed for fintech architectures requiring absolute consistency, high throughput, zero-balance drift, distributed idempotency, and deadlock-free concurrent fund transfers.

---

## Key Architecture & Features

- **Double-Entry Bookkeeping**: Every fund movement records matching immutable `DEBIT` and `CREDIT` ledger entries, ensuring zero-sum balance integrity and comprehensive audit trails.
- **Deadlock-Free Concurrency Control**: Implements deterministic resource locking (`SELECT ... FOR UPDATE` ordered alphabetically by Account ID) to eliminate database deadlocks during high-contention, bidirectional concurrent transfers.
- **Distributed Idempotency (Redis)**: Protects against double-spend and duplicate network retries via mandatory `Idempotency-Key` headers and cached transaction outcomes with configurable TTL.
- **ACID Transactional Guarantees**: Strict transactional boundaries managed via **HikariCP** connection pooling and PostgreSQL database constraints (`CHECK (balance >= 0.0000)`).
- **Domain-Driven & Modern Java**: Clean separation of concerns leveraging Java 21 immutable `record` components and domain modeling.
- **Lightweight REST API**: Minimal-overhead HTTP microservice built on **SparkJava** and **Jackson JSR-310**.
- **Rigorous Verification & Concurrency Testing**: Comprehensive unit and integration test suite using **Spock Framework (Groovy 4)**, including multi-threaded race-condition tests (100 simultaneous bidirectional transactions across 20 threads).
- **Container-Native & Orchestration**:
  - Multi-stage minimal **Dockerfile** based on `eclipse-temurin:21-jre-alpine` running as a non-privileged user.
  - **Docker Compose** orchestration with automated container healthchecks.
  - Production-ready **Kubernetes** manifests (`Deployment`, `Service`, `ConfigMap`, `Secret`) featuring Liveness and Readiness probes.
- **Automated CI/CD**: GitHub Actions workflow executing end-to-end migrations, Spock integration suites against ephemeral PostgreSQL and Redis service containers, and automated image builds.

---

## System Architecture

```
+-------------------------------------------------------------------+
|                          Client Requests                          |
|                (REST API / Idempotency-Key Header)                |
+------------------------------------+-------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------+
|                    TransferController (SparkJava)                 |
|            - Validates HTTP Headers & DTOs                        |
|            - Maps Errors (400, 404, 422, 500)                     |
+------------------------------------+-------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------+
|                     RedisIdempotencyService                       |
|         - Checks Redis cache (idempotency:<key>)                  |
|         - Returns cached response immediately if key exists       |
+------------------------------------+-------------------------------+
                       | (If new transaction)
                                     v
+-------------------------------------------------------------------+
|                      TransferService (Core)                       |
|  1. Opens JDBC Transaction (autoCommit = false)                   |
|  2. Sorts Account IDs deterministically: min(src, dst) -> max()   |
|  3. SELECT ... FOR UPDATE on source & destination (Pessimistic)   |
|  4. Validates balances, currency parity, and domain invariants    |
|  5. Updates source & destination balances in accounts table       |
|  6. Inserts immutable transfer record into transfers table        |
|  7. Inserts DEBIT and CREDIT records into ledger_entries table    |
|  8. Commits Transaction                                           |
|  9. Saves transaction outcome into Redis with TTL                 |
+---------------------+------------------------+--------------------+
                      |                        |
                      v                        v
+---------------------------------+   +-----------------------------+
|   PostgreSQL Database (ACID)    |   |     Redis Cache (TTL 1h)     |
|  - accounts (balance >= 0)      |   |  - idempotency:<key> ->      |
|  - transfers (idempotency_key)  |   |      transfer_id             |
|  - ledger_entries (DEBIT/CREDIT)|   +-----------------------------+
+---------------------------------+
```

---

## API Endpoints

### 1. Health Check

Checks service operational status.

- **URL**: `/health`
- **Method**: `GET`
- **Response**: `200 OK`

```json
{
  "status": "UP"
}
```

### 2. Get Account Balance

Retrieves account details and current balance.

- **URL**: `/accounts/:id/balance`
- **Method**: `GET`
- **Response**: `200 OK`

```json
{
  "id": "acc-pln-1",
  "userId": "user-100",
  "currency": "PLN",
  "balance": 10000.0000
}
```

### 3. Execute Money Transfer

Executes an atomic double-entry fund transfer between two accounts.

- **URL**: `/transfers`
- **Method**: `POST`
- **Headers**:
  - `Content-Type: application/json`
  - `Idempotency-Key: <unique-uuid-or-key>` (Mandatory)

**Request Body:**

```json
{
  "sourceAccountId": "acc-pln-1",
  "destinationAccountId": "acc-pln-2",
  "amount": 1500.00,
  "currency": "PLN"
}
```

**Response (New Transfer — 200 OK):**

```json
{
  "status": "COMPLETED",
  "message": "Transfer executed successfully",
  "transferId": "4fbb631a-e66a-4ae1-8d2b-63be3d489b43"
}
```

**Response (Duplicate Retry — 200 OK, Cached):**

```json
{
  "status": "COMPLETED",
  "message": "Transfer already processed (cached)",
  "transferId": "4fbb631a-e66a-4ae1-8d2b-63be3d489b43"
}
```

**Response (Insufficient Funds — 422 Unprocessable Entity):**

```json
{
  "error": "Insufficient funds in account: acc-pln-1"
}
```

---

## Technology Stack

| Layer | Technology | Description |
|---|---|---|
| Language & Runtime | Java 21 LTS | Amazon Corretto / Eclipse Temurin (Records, Pattern Matching) |
| Web Framework | SparkJava 2.9.4 | Embedded Jetty HTTP Web Server |
| Relational Database | PostgreSQL 16 | ACID transactions, strict schema constraints, indexing |
| Connection Pool | HikariCP 5.1.0 | High-performance JDBC connection management |
| Distributed Cache | Redis 7 & Jedis 5.1.2 | Low-latency idempotency lock & result cache |
| Serialization | Jackson 2.17.0 | High-speed JSON serialization with JavaTime support |
| Testing | Spock 2.4 & Groovy 4 | BDD testing, unit specs, multi-threaded concurrency specs |
| Containerization | Docker & Compose | Multi-stage build (alpine), orchestration with healthchecks |
| Orchestration | Kubernetes | Deployments, Services, ConfigMaps, Secrets, Health Probes |
| CI / CD | GitHub Actions | Automated build, testing with service containers, image packaging |

---

## Project Structure

```
ledger-transfer-engine/
├── .github/
│   └── workflows/
│       └── ci.yml                       # GitHub Actions CI/CD Pipeline
├── k8s/                                 # Kubernetes Manifests
│   ├── configmap.yaml                   # Application ConfigMap
│   ├── secret.yaml                      # Secret configuration
│   ├── postgres.yaml                    # PostgreSQL Deployment & Service
│   ├── redis.yaml                       # Redis Deployment & Service
│   ├── deployment.yaml                  # Ledger App Deployment (2 Replicas)
│   └── service.yaml                     # NodePort Service (Port 30080)
├── src/
│   ├── main/
│   │   ├── java/com/fintech/ledger/
│   │   │   ├── api/
│   │   │   │   ├── dto/                 # Request & Response Records
│   │   │   │   │   ├── CreateTransferRequest.java
│   │   │   │   │   └── TransferResponse.java
│   │   │   │   ├── JsonTransformer.java # Jackson Response Transformer
│   │   │   │   └── TransferController.java # REST API Endpoints & Handlers
│   │   │   ├── config/
│   │   │   │   └── DatabaseConfig.java  # HikariCP & Jedis Configuration
│   │   │   ├── domain/                  # Domain Entities & Invariants
│   │   │   │   ├── Account.java
│   │   │   │   ├── EntryDirection.java
│   │   │   │   ├── TransferCommand.java
│   │   │   │   └── TransferResult.java
│   │   │   ├── repository/              # Data Access Layer
│   │   │   │   ├── AccountRepository.java
│   │   │   │   ├── LedgerRepository.java
│   │   │   │   ├── PostgresAccountRepository.java
│   │   │   │   └── PostgresLedgerRepository.java
│   │   │   ├── service/                 # Business & Idempotency Services
│   │   │   │   ├── IdempotencyService.java
│   │   │   │   ├── RedisIdempotencyService.java
│   │   │   │   └── TransferService.java
│   │   │   └── Main.java                # Microservice Bootstrap
│   │   └── resources/
│   │       ├── db/migration/
│   │       │   └── V1__initial_schema.sql # Initial DB schema & seed accounts
│   │       └── application.properties   # Default application configuration
│   └── test/
│       └── groovy/com/fintech/ledger/   # Spock Specifications
│           ├── domain/
│           │   └── AccountSpec.groovy   # Domain invariants test suite
│           └── service/
│               ├── RedisIdempotencyServiceSpec.groovy
│               ├── TransferServiceSpec.groovy
│               └── TransferConcurrencySpec.groovy # Multi-threaded stress test
├── Dockerfile                            # Multi-stage container definition
├── docker-compose.yml                    # Local development stack
├── pom.xml                               # Maven build definition & plugins
└── README.md                             # Project Documentation
```

---

## Quickstart & Local Setup

### Prerequisites

- JDK 21 or higher
- Maven 3.9+
- Docker & Docker Compose
- PowerShell or Bash

### Option A: Run via Docker Compose (Recommended)

Clone repository:

```bash
git clone https://github.com/your-username/ledger-transfer-engine.git
cd ledger-transfer-engine
```

Spin up the entire environment (PostgreSQL, Redis, Java App):

```bash
docker compose up -d --build
```

Verify running containers:

```bash
docker ps
```

Check application logs:

```bash
docker logs -f fintech_ledger_app
```

### Option B: Run Locally (Dev Mode)

Start PostgreSQL and Redis infrastructure:

```bash
docker compose up -d postgres redis
```

Compile and run all tests:

```bash
mvn clean test
```

Start the application:

```bash
mvn exec:java -Dexec.mainClass="com.fintech.ledger.Main"
```

(Or run `Main.java` directly in IntelliJ IDEA.)

### Option C: Deploy on Kubernetes

Build local container image:

```bash
docker build -t fintech-ledger:latest .
```

Apply Kubernetes manifests:

```bash
kubectl apply -f k8s/
```

Initialize PostgreSQL schema inside K8s:

```powershell
# In PowerShell:
$PG_POD = (kubectl get pods -l app=postgres -o jsonpath="{.items[0].metadata.name}")
Get-Content ./src/main/resources/db/migration/V1__initial_schema.sql | kubectl exec -i $PG_POD -- psql -U ledger_user -d ledger_db
```

Forward ports for local access:

```bash
kubectl port-forward svc/ledger-app-service 8080:8080
```

---

## Testing & Concurrency Verification

Execute the complete test suite (Unit + Distributed Idempotency + Concurrency):

```bash
mvn test
```

**Concurrency Stress Test Details (`TransferConcurrencySpec`):**

- **Scenario**: 100 concurrent transfers dispatched across 20 threads simultaneously in bidirectional mode (A → B and B → A).
- **Checks Verified**:
  - Zero deadlocks detected (`SELECT ... FOR UPDATE` deterministic sorting).
  - Absolute financial invariant preserved: `Sum(A, B) = 10,000.00 PLN` at all times.
  - Exactly 200 double-entry audit records registered (DEBIT/CREDIT pairs).

### Testing via PowerShell / cURL

Execute a Transfer:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/transfers" `
  -Method Post `
  -Headers @{ "Idempotency-Key" = "tx-sample-001"; "Content-Type" = "application/json" } `
  -Body '{"sourceAccountId": "acc-pln-1", "destinationAccountId": "acc-pln-2", "amount": 250.00, "currency": "PLN"}'
```

Check Balances:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/accounts/acc-pln-1/balance" -Method Get
Invoke-RestMethod -Uri "http://localhost:8080/accounts/acc-pln-2/balance" -Method Get
```

---

## License

Distributed under the MIT License. See `LICENSE` for more information.
