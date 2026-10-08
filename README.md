# Core Banking Backend

[![CI](https://github.com/Sor-Channorakpitou/Banking/actions/workflows/ci.yml/badge.svg?branch=development)](https://github.com/Sor-Channorakpitou/Banking/actions/workflows/ci.yml)

A learning project: an enterprise-style core banking API with Java 21 and Spring Boot 3.

- Double-entry ledger: balances are always derived from ledger entries, never stored or edited
- JWT access tokens + rotating refresh tokens, BCrypt passwords, role-based access
- Deposits, withdrawals and transfers with pessimistic locking (ascending ID order) and idempotency keys
- Consistent JSON errors (RFC 9457 problem details with a stable `code`)
- Audit log, Prometheus metrics, structured logging with request IDs, login rate limiting
- Email verification, password reset, two-step login (TOTP), daily outgoing limits
- Saved payees, recipient name check, EMV/KHQR-style payment QR codes
- Android app "Lime" (Kotlin, Jetpack Compose) in `android/`, APK built by GitHub Actions
- KHR/USD currency exchange with published buy/sell rates (four-entry, per-currency balanced postings)
- Monthly statements
- Flyway migrations, H2 for local dev, PostgreSQL in Docker

## Run

```bash
# Local, with H2 (no setup needed). Admin: admin@bank.local / Admin123!
./mvnw spring-boot:run

# Full stack in Docker (PostgreSQL + app)
cp .env.example .env        # set JWT_SECRET and DATA_ENCRYPTION_KEY: openssl rand -base64 32
docker compose up --build
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- H2 console (dev): http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/bankdb`, user `sa`)

## Test

```bash
./mvnw verify
```

The PostgreSQL tests (migrations and the concurrency test) use Testcontainers and are skipped when Docker isn't running. CI (GitHub Actions) runs everything, including those tests and the Docker image build, on every push.

## API overview

| Method | Path | Who |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` | public |
| POST | `/api/auth/verify-email`, `/api/auth/forgot-password`, `/api/auth/reset-password` | public |
| GET | `/api/users/me` | authenticated |
| POST | `/api/users/me/password`, `/api/users/me/verify-email/resend`, `/api/users/me/two-step/{setup,enable,disable}` | authenticated |
| GET | `/api/accounts/lookup?number=`, `/api/accounts/{id}/payment-qr`, `/api/accounts/{id}/limits` | authenticated / owner |
| GET, POST, DELETE | `/api/payees`, `/api/payees/{id}` | authenticated (own payees) |
| POST | `/api/payment-qr/decode` | authenticated |
| POST, GET | `/api/accounts` | customer (own accounts) |
| GET | `/api/accounts/{id}`, `/{id}/transactions`, `/{id}/statements/{yyyy-MM}` | owner or admin |
| POST | `/api/accounts/{id}/deposit`, `/{id}/withdraw` (Idempotency-Key header) | owner (deposit: also admin) |
| POST | `/api/transfers` (Idempotency-Key header) | owner of source account |
| GET | `/api/exchange-rates`, `/api/exchange-rates/quote?from=USD&to=KHR&amount=10` | authenticated |
| POST | `/api/exchanges` (Idempotency-Key header) | owner of both accounts |
| POST | `/api/admin/exchange-rates` | admin |
| GET | `/api/admin/users`, `/api/admin/accounts`, `/api/admin/audit-logs` | admin |
| POST | `/api/admin/accounts/{id}/freeze`, `/unfreeze`, `/close` | admin |
| GET | `/actuator/metrics`, `/actuator/prometheus` | admin |

## Example

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@bank.local","password":"Admin123!"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s -X POST localhost:8080/api/accounts -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"currency":"USD"}'

curl -s -X POST localhost:8080/api/accounts/1/deposit -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" -d '{"amount":"100.00"}'
```

## Project layout

```
com.example.bank
├── config       Spring configuration, startup bootstrap, request ID filter
├── controller   thin REST controllers
├── dto          request/response records
├── service      business rules (accounts, money movement, statements, audit, metrics)
├── repository   Spring Data JPA repositories
├── domain       JPA entities and enums
├── security     JWT, refresh tokens, rate limiting, security error responses
└── exception    error codes and the global exception handler
```

## Android app

`android/` holds the customer app (Kotlin + Jetpack Compose, English and Khmer). Every push that changes it
builds a debug APK: open the repository's **Actions** tab, the latest **Android** run, and download
`lime-debug-apk`. To build locally, open `android/` in Android Studio.

The emulator reaches your PC's API at `http://10.0.2.2:8080`. On a real phone, tap **Change server** on the
sign-in screen and enter your PC's Wi-Fi address, e.g. `http://192.168.1.20:8080`.

## Branches

- `development`: default branch, where work lands
- `main`: production
