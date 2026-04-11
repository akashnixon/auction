#!/usr/bin/env bash
set -u

# Prototype smoke test for ENCS 691K auction system.
# This script checks required initial endpoints and core happy-path behavior.

USER_BASE_URL="${USER_BASE_URL:-http://localhost:3001}"
AUTH_BASE_URL="${AUTH_BASE_URL:-http://localhost:3002}"
AUCTION_BASE_URL="${AUCTION_BASE_URL:-http://localhost:3003}"
BID_BASE_URL="${BID_BASE_URL:-http://localhost:3004}"
NOTIFICATION_BASE_URL="${NOTIFICATION_BASE_URL:-http://localhost:3005}"

PASS_COUNT=0
FAIL_COUNT=0
FAILURES=()

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is required"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "ERROR: jq is required"
  exit 1
fi

TMP_BODY="$(mktemp)"
trap 'rm -f "$TMP_BODY"' EXIT

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  cat <<USAGE
Usage:
  $(basename "$0")

Environment overrides:
  USER_BASE_URL
  AUTH_BASE_URL
  AUCTION_BASE_URL
  BID_BASE_URL
  NOTIFICATION_BASE_URL
USAGE
  exit 0
fi

pass() {
  local msg="$1"
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "PASS | $msg"
}

fail() {
  local msg="$1"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  FAILURES+=("$msg")
  echo "FAIL | $msg"
}

http_json() {
  local method="$1"
  local url="$2"
  local data="${3:-}"
  local code=""

  if [ -n "$data" ]; then
    code="$(curl -sS -o "$TMP_BODY" -w "%{http_code}" \
      -X "$method" \
      -H "Content-Type: application/json" \
      -d "$data" \
      "$url" 2>/dev/null || true)"
  else
    code="$(curl -sS -o "$TMP_BODY" -w "%{http_code}" \
      -X "$method" \
      "$url" 2>/dev/null || true)"
  fi

  if [[ ! "$code" =~ ^[0-9]{3}$ ]]; then
    code="000"
  fi
  echo "$code"
}

assert_status() {
  local got="$1"
  local expected_csv="$2"
  local got_norm=""
  local candidate=""
  got_norm="$(echo "$got" | tr -cd '0-9' | sed -E 's/^.*([0-9]{3})$/\1/')"
  if [[ ! "$got_norm" =~ ^[0-9]{3}$ ]]; then
    got_norm="000"
  fi

  IFS=',' read -r -a codes <<< "$expected_csv"
  for candidate in "${codes[@]}"; do
    if [ "$got_norm" = "$candidate" ]; then
      return 0
    fi
  done
  return 1
}

json_field() {
  local key="$1"
  jq -r "$key // empty" "$TMP_BODY" 2>/dev/null || true
}

banner() {
  echo
  echo "== $1 =="
}

RANDOM_SUFFIX="$(date +%s)"
SELLER_USERNAME="seller_${RANDOM_SUFFIX}"
BIDDER1_USERNAME="bidder1_${RANDOM_SUFFIX}"
BIDDER2_USERNAME="bidder2_${RANDOM_SUFFIX}"
EMAIL_DOMAIN="example.com"

SELLER_ID=""
BIDDER1_ID=""
BIDDER2_ID=""
AUCTION_ID=""
TOKEN=""
FIRST_BID_ID=""
SECOND_BID_ID=""

banner "Health Endpoints"
for entry in \
  "User|$USER_BASE_URL/health|200" \
  "Auth|$AUTH_BASE_URL/health|200" \
  "Auction|$AUCTION_BASE_URL/health|200" \
  "Bid|$BID_BASE_URL/health|200" \
  "Notification|$NOTIFICATION_BASE_URL/health|200"
do
  name="${entry%%|*}"
  rest="${entry#*|}"
  url="${rest%%|*}"
  expected="${rest##*|}"
  code="$(http_json GET "$url")"
  if assert_status "$code" "$expected"; then
    pass "$name health endpoint reachable ($code)"
  else
    fail "$name health endpoint expected $expected got $code"
  fi
done

banner "User Registration + Uniqueness"
code="$(http_json POST "$USER_BASE_URL/users/register" "{\"username\":\"$SELLER_USERNAME\",\"email\":\"$SELLER_USERNAME@$EMAIL_DOMAIN\"}")"
if assert_status "$code" "201"; then
  SELLER_ID="$(json_field '.id')"
  if [ -n "$SELLER_ID" ]; then
    pass "Seller registration"
  else
    fail "Seller registration returned 201 but missing id"
  fi
else
  fail "Seller registration expected 201 got $code"
fi

code="$(http_json POST "$USER_BASE_URL/users/register" "{\"username\":\"$BIDDER1_USERNAME\",\"email\":\"$BIDDER1_USERNAME@$EMAIL_DOMAIN\"}")"
if assert_status "$code" "201"; then
  BIDDER1_ID="$(json_field '.id')"
  [ -n "$BIDDER1_ID" ] && pass "Bidder1 registration" || fail "Bidder1 registration missing id"
else
  fail "Bidder1 registration expected 201 got $code"
fi

code="$(http_json POST "$USER_BASE_URL/users/register" "{\"username\":\"$BIDDER2_USERNAME\",\"email\":\"$BIDDER2_USERNAME@$EMAIL_DOMAIN\"}")"
if assert_status "$code" "201"; then
  BIDDER2_ID="$(json_field '.id')"
  [ -n "$BIDDER2_ID" ] && pass "Bidder2 registration" || fail "Bidder2 registration missing id"
else
  fail "Bidder2 registration expected 201 got $code"
fi

code="$(http_json POST "$USER_BASE_URL/users/register" "{\"username\":\"$SELLER_USERNAME\",\"email\":\"dupe_$SELLER_USERNAME@$EMAIL_DOMAIN\"}")"
if assert_status "$code" "400,409"; then
  pass "Duplicate username rejected"
else
  fail "Duplicate username expected 400/409 got $code"
fi

banner "Auth Endpoints"
code="$(http_json POST "$AUTH_BASE_URL/auth/login" '{"username":"john_doe","password":"password123"}')"
if assert_status "$code" "200"; then
  TOKEN="$(json_field '.token')"
  [ -n "$TOKEN" ] && pass "Auth login" || fail "Auth login missing token"
else
  fail "Auth login expected 200 got $code"
fi

if [ -n "$TOKEN" ]; then
  code="$(http_json POST "$AUTH_BASE_URL/auth/validate" "{\"token\":\"$TOKEN\"}")"
  if assert_status "$code" "200"; then
    valid="$(json_field '.valid')"
    [ "$valid" = "true" ] && pass "Auth validate" || fail "Auth validate expected valid=true"
  else
    fail "Auth validate expected 200 got $code"
  fi

  code="$(curl -sS -o "$TMP_BODY" -w "%{http_code}" -H "Authorization: Bearer $TOKEN" "$AUTH_BASE_URL/auth/verify")"
  if assert_status "$code" "200"; then
    pass "Auth verify"
  else
    fail "Auth verify expected 200 got $code"
  fi
fi

banner "Auction Endpoints"
if [ -n "$SELLER_ID" ]; then
  code="$(http_json POST "$AUCTION_BASE_URL/auctions" "{\"itemName\":\"Prototype Item $RANDOM_SUFFIX\",\"sellerId\":\"$SELLER_ID\"}")"
  if assert_status "$code" "201"; then
    AUCTION_ID="$(json_field '.auctionId')"
    [ -n "$AUCTION_ID" ] && pass "Auction creation" || fail "Auction creation missing auctionId"
  else
    fail "Auction creation expected 201 got $code"
  fi
else
  fail "Auction creation skipped (missing seller id)"
fi

if [ -n "$AUCTION_ID" ]; then
  code="$(http_json GET "$AUCTION_BASE_URL/auctions/$AUCTION_ID")"
  assert_status "$code" "200" && pass "Get auction by id" || fail "Get auction by id expected 200 got $code"

  code="$(http_json GET "$AUCTION_BASE_URL/auctions/$AUCTION_ID/state")"
  if assert_status "$code" "200"; then
    cycle="$(json_field '.cycleNumber')"
    status="$(json_field '.status')"
    if [ -n "$cycle" ] && [ "$status" = "ACTIVE" ]; then
      pass "Auction state endpoint"
    else
      fail "Auction state missing expected ACTIVE/cycle"
    fi
  else
    fail "Auction state expected 200 got $code"
  fi

  code="$(http_json GET "$AUCTION_BASE_URL/auctions/active")"
  assert_status "$code" "200" && pass "List active auctions" || fail "List active auctions expected 200 got $code"

  code="$(http_json GET "$AUCTION_BASE_URL/auctions/user/$SELLER_ID/active")"
  assert_status "$code" "200" && pass "List seller active auctions" || fail "Seller active auctions expected 200 got $code"
fi

banner "Bid Endpoints"
if [ -n "$AUCTION_ID" ] && [ -n "$BIDDER1_ID" ] && [ -n "$BIDDER2_ID" ]; then
  IDEM_KEY="idem-$RANDOM_SUFFIX"
  code="$(http_json POST "$BID_BASE_URL/bids" "{\"auctionId\":\"$AUCTION_ID\",\"bidderId\":\"$BIDDER1_ID\",\"amount\":100,\"idempotencyKey\":\"$IDEM_KEY\"}")"
  if assert_status "$code" "201"; then
    FIRST_BID_ID="$(json_field '.bidId')"
    [ -n "$FIRST_BID_ID" ] && pass "Place first bid" || fail "First bid missing bidId"
  else
    fail "First bid expected 201 got $code"
  fi

  code="$(http_json POST "$BID_BASE_URL/bids" "{\"auctionId\":\"$AUCTION_ID\",\"bidderId\":\"$BIDDER1_ID\",\"amount\":100,\"idempotencyKey\":\"$IDEM_KEY\"}")"
  if assert_status "$code" "200,201"; then
    replay_bid="$(json_field '.bidId')"
    if [ -n "$FIRST_BID_ID" ] && [ "$replay_bid" = "$FIRST_BID_ID" ]; then
      pass "Idempotent bid replay"
    else
      fail "Idempotent replay returned different bidId"
    fi
  else
    fail "Idempotent replay expected 200/201 got $code"
  fi

  code="$(http_json POST "$BID_BASE_URL/bids" "{\"auctionId\":\"$AUCTION_ID\",\"bidderId\":\"$BIDDER2_ID\",\"amount\":120}")"
  if assert_status "$code" "201"; then
    SECOND_BID_ID="$(json_field '.bidId')"
    [ -n "$SECOND_BID_ID" ] && pass "Place second higher bid" || fail "Second bid missing bidId"
  else
    fail "Second bid expected 201 got $code"
  fi

  code="$(http_json GET "$BID_BASE_URL/bids/auction/$AUCTION_ID")"
  if assert_status "$code" "200"; then
    total="$(json_field '.total')"
    if [ -n "$total" ] && [ "$total" -ge 2 ] 2>/dev/null; then
      pass "List bids by auction"
    else
      fail "List bids expected total >= 2"
    fi
  else
    fail "List bids expected 200 got $code"
  fi

  code="$(http_json GET "$BID_BASE_URL/internal/auctions/$AUCTION_ID/cycles/1/winner")"
  if assert_status "$code" "200"; then
    wbidder="$(json_field '.bidderId')"
    if [ "$wbidder" = "$BIDDER2_ID" ]; then
      pass "Internal winner lookup (highest bid)"
    else
      fail "Winner lookup expected bidder2"
    fi
  else
    fail "Winner lookup expected 200 got $code"
  fi
fi

banner "Deregistration Constraints"
if [ -n "$SELLER_ID" ]; then
  code="$(http_json POST "$USER_BASE_URL/users/deregister" "{\"userId\":\"$SELLER_ID\"}")"
  if assert_status "$code" "409"; then
    pass "Deregister denied for active seller"
  else
    fail "Deregister active seller expected 409 got $code"
  fi
fi

if [ -n "$AUCTION_ID" ]; then
  code="$(http_json POST "$BID_BASE_URL/internal/auctions/$AUCTION_ID/cycles/1/close")"
  assert_status "$code" "200" && pass "Internal cycle close" || fail "Internal cycle close expected 200 got $code"
fi

if [ -n "$BIDDER2_ID" ]; then
  code="$(http_json POST "$USER_BASE_URL/users/deregister" "{\"userId\":\"$BIDDER2_ID\"}")"
  if assert_status "$code" "200,409"; then
    # 200 is expected if bidder-status was cleared on close, 409 means bug remains.
    if [ "$code" = "200" ]; then
      pass "Deregister previous highest bidder after cycle close"
    else
      fail "Deregister bidder still blocked after cycle close (expected cleared state)"
    fi
  else
    fail "Deregister bidder after close expected 200/409 got $code"
  fi
fi

banner "Notification Endpoints"
code="$(http_json GET "$NOTIFICATION_BASE_URL/events?limit=5")"
assert_status "$code" "200" && pass "Notification events list" || fail "Notification events list expected 200 got $code"

code="$(http_json POST "$NOTIFICATION_BASE_URL/events" '{"type":"SMOKE_TEST_EVENT","audience":"ALL_REGISTERED","payload":{"source":"smoke-test"}}')"
if assert_status "$code" "201"; then
  pass "Notification publish event"
else
  fail "Notification publish expected 201 got $code"
fi

echo
echo "========================================"
echo "Smoke Test Summary: PASS=$PASS_COUNT FAIL=$FAIL_COUNT"
if [ "$FAIL_COUNT" -gt 0 ]; then
  echo "Failed checks:"
  for item in "${FAILURES[@]}"; do
    echo "- $item"
  done
  exit 1
fi

echo "All prototype smoke checks passed."
exit 0
