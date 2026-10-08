#!/usr/bin/env bash
# Fills a fresh dev API (H2, emails logged) with demo customers and history, for the
# Android screenshot tour. Usage: seed-demo.sh <path to the API's log file>
# Prints RECEIVER=<Dara's USD account number> for the tour's transfer screen.
set -euo pipefail

API=http://localhost:8080
LOG=$1
JSON='Content-Type: application/json'

register() { # name email
  curl -sf -X POST "$API/api/auth/register" -H "$JSON" \
    -d "{\"fullName\":\"$1\",\"email\":\"$2\",\"password\":\"password123\"}" > /dev/null
  sleep 1
  local code
  code=$(grep -oE "to=$2 subject=\"Your verification code: [0-9]{6}" "$LOG" | tail -1 | grep -oE '[0-9]{6}$')
  curl -sf -X POST "$API/api/auth/verify-email" -H "$JSON" -d "{\"email\":\"$2\",\"code\":\"$code\"}"
}

login() { # email -> access token
  curl -sf -X POST "$API/api/auth/login" -H "$JSON" -d "{\"email\":\"$1\",\"password\":\"password123\"}" | jq -r .accessToken
}

open_account() { # token currency -> account JSON
  curl -sf -X POST "$API/api/accounts" -H "$JSON" -H "Authorization: Bearer $1" -d "{\"currency\":\"$2\"}"
}

post() { # token path body (money movements get a fresh Idempotency-Key)
  curl -sf -X POST "$API$2" -H "$JSON" -H "Authorization: Bearer $1" \
    -H "Idempotency-Key: $(cat /proc/sys/kernel/random/uuid)" -d "$3" > /dev/null
}

register "Sopheak Sor" sopheak@example.com
register "Dara Chan" dara@example.com
register "Sokunthea Lim" sokunthea@example.com

S=$(login sopheak@example.com)
D=$(login dara@example.com)
M=$(login sokunthea@example.com)

S_USD=$(open_account "$S" USD | jq -r .id)
S_KHR=$(open_account "$S" KHR | jq -r .id)
D_USD_JSON=$(open_account "$D" USD)
D_USD=$(echo "$D_USD_JSON" | jq -r .id)
D_NUM=$(echo "$D_USD_JSON" | jq -r .accountNumber)
M_NUM=$(open_account "$M" USD | jq -r .accountNumber)

post "$S" "/api/accounts/$S_USD/deposit" '{"amount":"2683.25","description":"Cash deposit"}'
post "$S" "/api/accounts/$S_KHR/deposit" '{"amount":"841000","description":"Cash deposit"}'
post "$D" "/api/accounts/$D_USD/deposit" '{"amount":"3000.00"}'
S_NUM=$(curl -sf "$API/api/accounts/$S_USD" -H "Authorization: Bearer $S" | jq -r .accountNumber)
post "$D" "/api/transfers" "{\"fromAccountId\":$D_USD,\"toAccountNumber\":\"$S_NUM\",\"amount\":\"1800.00\",\"description\":\"Salary\"}"
post "$S" "/api/transfers" "{\"fromAccountId\":$S_USD,\"toAccountNumber\":\"$D_NUM\",\"amount\":\"30.25\",\"description\":\"Dinner\"}"
post "$S" "/api/exchanges" "{\"fromAccountId\":$S_USD,\"toAccountId\":$S_KHR,\"amount\":\"100\"}"
post "$S" "/api/accounts/$S_USD/withdraw" '{"amount":"60.00","description":"ATM"}'
post "$S" "/api/transfers" "{\"fromAccountId\":$S_USD,\"toAccountNumber\":\"$M_NUM\",\"amount\":\"12.50\",\"description\":\"Coffee\"}"

curl -sf -X POST "$API/api/payees" -H "$JSON" -H "Authorization: Bearer $S" \
  -d "{\"accountNumber\":\"$D_NUM\",\"nickname\":\"Dara\"}" > /dev/null
curl -sf -X POST "$API/api/payees" -H "$JSON" -H "Authorization: Bearer $S" \
  -d "{\"accountNumber\":\"$M_NUM\",\"nickname\":\"Mom\"}" > /dev/null

echo "Seeded: Sopheak USD $(curl -sf "$API/api/accounts/$S_USD" -H "Authorization: Bearer $S" | jq -r .balance)" >&2
echo "RECEIVER=$D_NUM"
