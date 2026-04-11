#!/usr/bin/env bash
set -euo pipefail

# Wrapper for Akash-owned functionality checks.
# Assumes services are already running.

export AUCTION_WAIT_SECONDS="${AUCTION_WAIT_SECONDS:-7}"
export NO_BID_WAIT_SECONDS="${NO_BID_WAIT_SECONDS:-7}"

exec "$(dirname "$0")/prototype-smoke-test.sh"
