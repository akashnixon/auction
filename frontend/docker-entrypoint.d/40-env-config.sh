#!/bin/sh
set -eu

cat <<EOF >/usr/share/nginx/html/config.js
window.__APP_CONFIG__ = {
  USER_API_URL: "${FRONTEND_USER_API_URL:-http://localhost:3001}",
  AUTH_API_URL: "${FRONTEND_AUTH_API_URL:-http://localhost:3002}",
  AUCTION_API_URL: "${FRONTEND_AUCTION_API_URL:-http://localhost:3003}",
  BID_API_URL: "${FRONTEND_BID_API_URL:-http://localhost:3004}",
  NOTIFICATION_API_URL: "${FRONTEND_NOTIFICATION_API_URL:-http://localhost:3005}"
};
EOF
