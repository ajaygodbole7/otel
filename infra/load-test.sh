#!/usr/bin/env bash
# =============================================================================
# load-test.sh — Generate sustained load across all otel-demo API endpoints
#
# Hits every route repeatedly so Grafana panels populate with visible data:
#   POST   /api/v1/customers          (create)
#   GET    /api/v1/customers/:id      (fetch by ID)
#   GET    /api/v1/customers          (paginated list)
#   PATCH  /api/v1/customers/:id      (partial update)
#   GET    /api/v1/customers/search?email=
#   PUT    /api/v1/customers/:id      (full replace)
#   DELETE /api/v1/customers/:id      (delete)
#   GET    /api/v1/customers/9999999  (intentional 404 — drives error-rate panel)
# =============================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api/v1/customers}"
ROUNDS="${ROUNDS:-5}"          # how many full cycles to run
PAUSE="${PAUSE:-0.3}"          # seconds between requests (keeps rate visible in Grafana)

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()      { echo -e "${GREEN}[${1}]${NC} ${2}"; }
err()     { echo -e "${RED}[${1}]${NC} ${2}"; }
section() { echo -e "\n${BOLD}${YELLOW}── $* ──${NC}"; }

check_app() {
  if ! curl -sf "$BASE_URL/../actuator/health" -o /dev/null 2>/dev/null && \
     ! curl -sf "http://localhost:8080/actuator/health" -o /dev/null 2>/dev/null; then
    echo -e "${RED}[ERROR]${NC} App is not running at http://localhost:8080" >&2
    echo "  Start it with: ./infra/start-app.sh" >&2
    exit 1
  fi
}

http() {
  # http <method> <url> [body] [content-type]
  local method="$1" url="$2" body="${3:-}" ctype="${4:-application/json}"
  local args=(-s -o /tmp/http_resp -w "%{http_code}" -X "$method" "$url")
  [ -n "$body" ] && args+=(-H "Content-Type: $ctype" -d "$body")
  local code
  code=$(curl "${args[@]}" 2>/dev/null)
  echo "$code"
}

json_field() {
  # Extract a top-level JSON field from /tmp/http_resp using python3
  python3 -c "import sys,json; print(json.load(open('/tmp/http_resp')).get('$1',''))" 2>/dev/null || echo ""
}

TOTAL_OK=0; TOTAL_ERR=0

req() {
  local label="$1" expected="$2" code="$3"
  sleep "$PAUSE"
  if [ "$code" = "$expected" ]; then
    ok "$code" "$label"
    TOTAL_OK=$((TOTAL_OK+1))
  else
    err "$code" "$label  (expected $expected)"
    TOTAL_ERR=$((TOTAL_ERR+1))
  fi
}

# ── Seed data pool for realistic variety ─────────────────────────────────────
FIRST_NAMES=("Alice" "Bob" "Carol" "Dave" "Eve" "Frank" "Grace" "Hank" "Iris" "Jack")
LAST_NAMES=("Smith" "Jones" "Williams" "Taylor" "Brown" "Davis" "Miller" "Wilson" "Moore" "Lee")
CITIES=("Chicago" "New York" "San Francisco" "Austin" "Seattle" "Boston" "Denver" "Atlanta")
STATES=("IL" "NY" "CA" "TX" "WA" "MA" "CO" "GA")

rand_idx() { echo $(( RANDOM % $1 )); }

make_customer() {
  local n="$1"
  local fi="${FIRST_NAMES[$(rand_idx 10)]}"
  local la="${LAST_NAMES[$(rand_idx 10)]}"
  local ci="${CITIES[$(rand_idx 8)]}"
  local st="${STATES[$(rand_idx 8)]}"
  local seq=$(printf "%06d" $n)
  cat <<JSON
{
  "type": "INDIVIDUAL",
  "firstName": "${fi}",
  "lastName": "${la}",
  "emails": [{"primary": true, "email": "load${seq}@demo.test", "type": "PERSONAL"}],
  "phones": [{"type": "MOBILE", "countryCode": "+1", "number": "555${seq}"}],
  "addresses": [{"type": "HOME", "line1": "${n} Load St", "city": "${ci}", "state": "${st}", "postalCode": "60601", "country": "USA"}]
}
JSON
}

# ────────────────────────────────────────────────────────────────────────────
check_app
echo ""
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${GREEN}  otel-demo Load Test  (${ROUNDS} rounds)${NC}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo -e "  Target: ${CYAN}$BASE_URL${NC}"
echo -e "  Pause between requests: ${PAUSE}s"
echo ""

CREATED_IDS=()
SEQ=0

for round in $(seq 1 "$ROUNDS"); do
  section "Round $round / $ROUNDS"
  SEQ=$((SEQ+1))
  BODY="$(make_customer $SEQ)"

  # ── POST — create ──────────────────────────────────────────────────────────
  code=$(http POST "$BASE_URL" "$BODY")
  req "POST   /customers  (create ${FIRST_NAMES[$(rand_idx 10)]})" 201 "$code"
  ID=""
  if [ "$code" = "201" ]; then
    ID=$(json_field id)
    [ -n "$ID" ] && CREATED_IDS+=("$ID")
  fi

  # ── GET by ID ─────────────────────────────────────────────────────────────
  if [ -n "$ID" ]; then
    code=$(http GET "$BASE_URL/$ID")
    req "GET    /customers/$ID" 200 "$code"
  fi

  # ── Paginated list ────────────────────────────────────────────────────────
  code=$(http GET "$BASE_URL?limit=5")
  req "GET    /customers?limit=5" 200 "$code"

  # ── PATCH — partial update ────────────────────────────────────────────────
  if [ -n "$ID" ]; then
    PATCH='{"firstName":"Patched'$round'"}'
    code=$(http PATCH "$BASE_URL/$ID" "$PATCH" "application/merge-patch+json")
    req "PATCH  /customers/$ID" 200 "$code"
  fi

  # ── Search by email ───────────────────────────────────────────────────────
  EMAIL="load$(printf "%06d" $SEQ)@demo.test"
  code=$(http GET "$BASE_URL/search?email=${EMAIL}")
  req "GET    /customers/search?email=$EMAIL" 200 "$code"

  # ── PUT — full replace ────────────────────────────────────────────────────
  if [ -n "$ID" ]; then
    # Fetch current to get required fields
    code=$(http GET "$BASE_URL/$ID")
    if [ "$code" = "200" ]; then
      # Build a full replace body preserving phones/addresses from the created record
      PUT_BODY=$(python3 -c "
import sys, json
d = json.load(open('/tmp/http_resp'))
d['firstName'] = 'Updated${round}'
d['lastName']  = 'LoadTest'
# keep id and createdAt — required by PUT; remove only updatedAt
d.pop('updatedAt', None)
print(json.dumps(d))
" 2>/dev/null || echo "")
      if [ -n "$PUT_BODY" ]; then
        code=$(http PUT "$BASE_URL/$ID" "$PUT_BODY")
        req "PUT    /customers/$ID (full replace)" 200 "$code"
      fi
    fi
  fi

  # ── Cursor-based pagination (page 2) ──────────────────────────────────────
  if [ ${#CREATED_IDS[@]} -ge 2 ]; then
    CURSOR="${CREATED_IDS[0]}"
    code=$(http GET "$BASE_URL?limit=5&after=$CURSOR")
    req "GET    /customers?limit=5&after=$CURSOR" 200 "$code"
  fi

  # ── Intentional 404s — populate error-rate panel ─────────────────────────
  for bad_id in 9999999999 8888888888; do
    code=$(http GET "$BASE_URL/$bad_id")
    req "GET    /customers/$bad_id (expect 404)" 404 "$code"
  done

done

# ── Cleanup: delete all IDs we created ────────────────────────────────────────
section "Cleanup — deleting ${#CREATED_IDS[@]} created customers"
for id in "${CREATED_IDS[@]}"; do
  code=$(http DELETE "$BASE_URL/$id")
  req "DELETE /customers/$id" 204 "$code"
done

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  Load Test Complete${NC}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo -e "  ${GREEN}Passed:${NC} $TOTAL_OK"
echo -e "  ${RED}Failed:${NC} $TOTAL_ERR"
echo ""
echo -e "  View results in Grafana:"
echo -e "    App Overview:    ${CYAN}http://localhost:3000/d/app-overview${NC}"
echo -e "    HTTP Endpoints:  ${CYAN}http://localhost:3000/d/http-endpoints${NC}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
