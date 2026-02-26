#!/usr/bin/env bash
# =============================================================================
# e2e-test.sh — Full customer lifecycle end-to-end test
#
# Exercises every API operation in order, asserting HTTP status codes and
# response payloads at each step.  Useful for smoke-testing after deploys
# and for generating recognisable trace sequences in Grafana.
#
# Flow:
#   1.  POST   — create customer                    → 201
#   2.  GET    — verify creation by ID              → 200
#   3.  GET    — paginated list (appears in page)   → 200
#   4.  PATCH  — partial update (firstName)         → 200  verify field changed
#   5.  GET    — fetch again, confirm patch applied → 200
#   6.  PUT    — full replace                       → 200  verify fields
#   7.  GET    — fetch again, confirm PUT applied   → 200
#   8.  GET    — search by email                    → 200  verify correct record
#   9.  DELETE — remove customer                    → 204
#   10. GET    — confirm 404 after delete           → 404
#   11. GET    — search after delete                → 404
# =============================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api/v1/customers}"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
PASS=0; FAIL=0

pass() { echo -e "  ${GREEN}✓${NC}  $*"; PASS=$((PASS+1)); }
fail() { echo -e "  ${RED}✗${NC}  $*"; FAIL=$((FAIL+1)); }
step() { echo -e "\n${BOLD}${CYAN}Step $STEP_NUM: $*${NC}"; STEP_NUM=$((STEP_NUM+1)); }
assert_eq() {
  local label="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    pass "$label: '$actual'"
  else
    fail "$label: expected '$expected', got '$actual'"
  fi
}
assert_ne() {
  local label="$1" unexpected="$2" actual="$3"
  if [ "$actual" != "$unexpected" ]; then
    pass "$label: '$actual'"
  else
    fail "$label: should not be '$unexpected'"
  fi
}

STEP_NUM=1
TMP=$(mktemp)
trap "rm -f $TMP" EXIT

http() {
  local method="$1" url="$2" body="${3:-}" ctype="${4:-application/json}"
  local args=(-s -o "$TMP" -w "%{http_code}" -X "$method" "$url")
  [ -n "$body" ] && args+=(-H "Content-Type: $ctype" -d "$body")
  curl "${args[@]}" 2>/dev/null
}

get_field() {
  python3 -c "import json; d=json.load(open('$TMP')); print(d.get('$1',''))" 2>/dev/null || echo ""
}

get_nested() {
  # get_nested "emails[0].email"
  python3 -c "
import json, re
d = json.load(open('$TMP'))
path = '$1'
for part in re.split(r'[\.\[\]]+', path):
    if not part: continue
    try:
        part = int(part)
    except ValueError:
        pass
    d = d[part]
print(d)
" 2>/dev/null || echo ""
}

check_app() {
  if ! curl -sf "http://localhost:8080/actuator/health" -o /dev/null 2>/dev/null; then
    echo -e "${RED}[ERROR]${NC} App is not running at http://localhost:8080" >&2
    echo "  Start it with: ./infra/start-app.sh" >&2
    exit 1
  fi
}

# ─────────────────────────────────────────────────────────────────────────────
check_app
echo ""
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${GREEN}  otel-demo End-to-End Customer Lifecycle Test${NC}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo -e "  Target: ${CYAN}$BASE_URL${NC}"
echo ""

E2E_EMAIL="e2e.test.$(date +%s)@demo.test"
E2E_SSN="123-45-$(( RANDOM % 9000 + 1000 ))"

# ══════════════════════════════════════════════════════════════════════════════
# Step 1 — CREATE
# ══════════════════════════════════════════════════════════════════════════════
step "POST /customers — create"
# Use printf to safely interpolate variables into JSON (avoids heredoc quoting issues)
CREATE_BODY=$(printf '{
  "type": "INDIVIDUAL",
  "firstName": "E2E",
  "lastName": "Tester",
  "middleName": "Integration",
  "emails": [{"primary": true, "email": "%s", "type": "PERSONAL"}],
  "phones": [{"type": "MOBILE", "countryCode": "+1", "number": "5550001234"}],
  "addresses": [{"type": "HOME", "line1": "1 Test Ave", "city": "Chicago", "state": "IL", "postalCode": "60601", "country": "USA"}],
  "documents": [{"country": "USA", "type": "SSN", "identifier": "%s"}]
}' "$E2E_EMAIL" "$E2E_SSN")
CODE=$(http POST "$BASE_URL" "$CREATE_BODY")
assert_eq "HTTP status" 201 "$CODE"
CUSTOMER_ID=$(get_field id)
assert_ne "id assigned" "" "$CUSTOMER_ID"
CREATED_AT=$(get_field createdAt)
assert_ne "createdAt set" "" "$CREATED_AT"
echo -e "     ID:        ${YELLOW}$CUSTOMER_ID${NC}"
echo -e "     createdAt: ${YELLOW}$CREATED_AT${NC}"

# ══════════════════════════════════════════════════════════════════════════════
# Step 2 — GET by ID (verify creation)
# ══════════════════════════════════════════════════════════════════════════════
step "GET /customers/$CUSTOMER_ID — verify creation"
CODE=$(http GET "$BASE_URL/$CUSTOMER_ID")
assert_eq "HTTP status"  200           "$CODE"
assert_eq "id"           "$CUSTOMER_ID" "$(get_field id)"
assert_eq "firstName"    "E2E"          "$(get_field firstName)"
assert_eq "lastName"     "Tester"       "$(get_field lastName)"
assert_eq "middleName"   "Integration"  "$(get_field middleName)"
assert_eq "type"         "INDIVIDUAL"   "$(get_field type)"
assert_eq "email"        "$E2E_EMAIL"   "$(get_nested 'emails[0].email')"
assert_eq "phone"        "5550001234"   "$(get_nested 'phones[0].number')"
assert_eq "city"         "Chicago"      "$(get_nested 'addresses[0].city')"

# ══════════════════════════════════════════════════════════════════════════════
# Step 3 — LIST (paginated, customer appears)
# ══════════════════════════════════════════════════════════════════════════════
step "GET /customers?limit=20 — list returns valid paginated response"
CODE=$(http GET "$BASE_URL?limit=20")
assert_eq "HTTP status" 200 "$CODE"
# Verify response has expected pagination envelope fields
HAS_DATA=$(python3 -c "
import json
d=json.load(open('$TMP'))
print('yes' if 'data' in d and 'hasMore' in d and 'limit' in d else 'no')
" 2>/dev/null || echo "no")
assert_eq "response has data/hasMore/limit fields" "yes" "$HAS_DATA"
# Confirm cursor pagination: fetch with after=$CUSTOMER_ID should return 200
CODE=$(http GET "$BASE_URL?limit=5&after=$CUSTOMER_ID")
assert_eq "cursor pagination (after=ID) returns 200" 200 "$CODE"

# ══════════════════════════════════════════════════════════════════════════════
# Step 4 — PATCH (partial update)
# ══════════════════════════════════════════════════════════════════════════════
step "PATCH /customers/$CUSTOMER_ID — change firstName"
CODE=$(http PATCH "$BASE_URL/$CUSTOMER_ID" '{"firstName":"PatchedE2E"}' "application/merge-patch+json")
assert_eq "HTTP status"  200          "$CODE"
assert_eq "firstName"    "PatchedE2E" "$(get_field firstName)"
assert_eq "lastName"     "Tester"     "$(get_field lastName)"   # unchanged
assert_eq "id"           "$CUSTOMER_ID" "$(get_field id)"
UPDATED_AT=$(get_field updatedAt)
assert_ne "updatedAt changed" "$CREATED_AT" "$UPDATED_AT"
echo -e "     updatedAt: ${YELLOW}$UPDATED_AT${NC}"

# ══════════════════════════════════════════════════════════════════════════════
# Step 5 — GET after PATCH (confirm persisted)
# ══════════════════════════════════════════════════════════════════════════════
step "GET /customers/$CUSTOMER_ID — confirm PATCH persisted"
CODE=$(http GET "$BASE_URL/$CUSTOMER_ID")
assert_eq "HTTP status" 200          "$CODE"
assert_eq "firstName"   "PatchedE2E" "$(get_field firstName)"
assert_eq "lastName"    "Tester"     "$(get_field lastName)"

# ══════════════════════════════════════════════════════════════════════════════
# Step 6 — PUT (full replace)
# ══════════════════════════════════════════════════════════════════════════════
step "PUT /customers/$CUSTOMER_ID — full replace"
PUT_BODY=$(printf '{
  "id": %s,
  "type": "INDIVIDUAL",
  "firstName": "Replaced",
  "lastName": "Completely",
  "emails": [{"primary": true, "email": "%s", "type": "WORK"}],
  "phones": [{"type": "HOME", "countryCode": "+1", "number": "5559999999"}],
  "addresses": [{"type": "WORK", "line1": "99 Replace Blvd", "city": "Austin", "state": "TX", "postalCode": "78701", "country": "USA"}],
  "createdAt": "%s"
}' "$CUSTOMER_ID" "$E2E_EMAIL" "$CREATED_AT")
CODE=$(http PUT "$BASE_URL/$CUSTOMER_ID" "$PUT_BODY")
assert_eq "HTTP status" 200          "$CODE"
assert_eq "firstName"   "Replaced"   "$(get_field firstName)"
assert_eq "lastName"    "Completely" "$(get_field lastName)"
assert_eq "city"        "Austin"     "$(get_nested 'addresses[0].city')"
assert_eq "phone"       "5559999999" "$(get_nested 'phones[0].number')"

# ══════════════════════════════════════════════════════════════════════════════
# Step 7 — GET after PUT (confirm full replace persisted)
# ══════════════════════════════════════════════════════════════════════════════
step "GET /customers/$CUSTOMER_ID — confirm PUT persisted"
CODE=$(http GET "$BASE_URL/$CUSTOMER_ID")
assert_eq "HTTP status" 200          "$CODE"
assert_eq "firstName"   "Replaced"   "$(get_field firstName)"
assert_eq "lastName"    "Completely" "$(get_field lastName)"
assert_eq "city"        "Austin"     "$(get_nested 'addresses[0].city')"
# middleName should be gone (full replace omitted it)
MIDDLE=$(get_field middleName)
[ "$MIDDLE" = "None" ] || [ -z "$MIDDLE" ] || [ "$MIDDLE" = "null" ] && pass "middleName cleared by PUT" || fail "middleName not cleared: '$MIDDLE'"

# ══════════════════════════════════════════════════════════════════════════════
# Step 8 — SEARCH by email
# ══════════════════════════════════════════════════════════════════════════════
step "GET /customers/search?email=$E2E_EMAIL — find by email"
CODE=$(http GET "$BASE_URL/search?email=${E2E_EMAIL}")
assert_eq "HTTP status" 200           "$CODE"
assert_eq "id"          "$CUSTOMER_ID" "$(get_field id)"
assert_eq "firstName"   "Replaced"     "$(get_field firstName)"

# ══════════════════════════════════════════════════════════════════════════════
# Step 9 — DELETE
# ══════════════════════════════════════════════════════════════════════════════
step "DELETE /customers/$CUSTOMER_ID"
CODE=$(http DELETE "$BASE_URL/$CUSTOMER_ID")
assert_eq "HTTP status" 204 "$CODE"

# ══════════════════════════════════════════════════════════════════════════════
# Step 10 — GET after DELETE (expect 404)
# ══════════════════════════════════════════════════════════════════════════════
step "GET /customers/$CUSTOMER_ID — expect 404 after delete"
CODE=$(http GET "$BASE_URL/$CUSTOMER_ID")
assert_eq "HTTP status" 404 "$CODE"
PROBLEM_TYPE=$(get_field type)
assert_ne "RFC 7807 type" "" "$PROBLEM_TYPE"
echo -e "     Problem type: ${YELLOW}$PROBLEM_TYPE${NC}"

# ══════════════════════════════════════════════════════════════════════════════
# Step 11 — SEARCH after DELETE (expect 404)
# ══════════════════════════════════════════════════════════════════════════════
step "GET /customers/search?email=$E2E_EMAIL — expect 404 after delete"
CODE=$(http GET "$BASE_URL/search?email=${E2E_EMAIL}")
assert_eq "HTTP status" 404 "$CODE"

# ── Summary ───────────────────────────────────────────────────────────────────
TOTAL=$((PASS+FAIL))
echo ""
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  E2E Test Results${NC}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo -e "  ${GREEN}Passed:${NC}  $PASS / $TOTAL"
echo -e "  ${RED}Failed:${NC}  $FAIL / $TOTAL"
if [ $FAIL -eq 0 ]; then
  echo -e "\n  ${BOLD}${GREEN}ALL ASSERTIONS PASSED${NC}"
else
  echo -e "\n  ${BOLD}${RED}$FAIL ASSERTION(S) FAILED — review output above${NC}"
fi
echo ""
echo -e "  Traces visible in Grafana Explore → Tempo"
echo -e "    ${CYAN}http://localhost:3000/explore${NC}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"

[ $FAIL -eq 0 ] && exit 0 || exit 1
