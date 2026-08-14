#!/usr/bin/env bash
# ================================================================
# Event Ticket Booking Service - JMeter Bash Runner
# ================================================================

set -e

HOST="${1:-localhost}"
PORT="${2:-8080}"
BOOKING_RPM="${3:-500}"
DURATION="${4:-300}"
BROWSE_USERS="${5:-100}"
BOOKING_USERS="${6:-50}"
RAMPUP="${7:-30}"
PROTOCOL="${8:-http}"

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLAN_FILE="$BASE_DIR/plans/event_ticket_booking_load_test.jmx"
CSV_FILE="$BASE_DIR/data/users_tokens.csv"

echo "================================================================"
echo "   Event Ticket Booking Service - JMeter Load Test Runner"
echo "================================================================"

# Check CSV
if [ ! -f "$CSV_FILE" ]; then
    echo "[INFO] users_tokens.csv not found. Generating 50,000 tokens..."
    python3 "$BASE_DIR/data/generate_test_data.py" || python "$BASE_DIR/data/generate_test_data.py"
fi

JMETER_BIN="jmeter"
if [ -n "$JMETER_HOME" ]; then
    JMETER_BIN="$JMETER_HOME/bin/jmeter"
fi

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
REPORTS_DIR="$BASE_DIR/reports"
mkdir -p "$REPORTS_DIR"

REPORT_DIR="$REPORTS_DIR/report_$TIMESTAMP"
JTL_FILE="$REPORTS_DIR/results_$TIMESTAMP.jtl"

echo ""
echo "Test Configuration:"
echo "----------------------------------------------------"
echo "Target Host       : $PROTOCOL://$HOST:$PORT"
echo "Booking Rate      : $BOOKING_RPM requests/minute"
echo "Active Users Pool : 50,000 users ($CSV_FILE)"
echo "Catalog Users     : $BROWSE_USERS concurrent threads"
echo "Booking Users     : $BOOKING_USERS concurrent threads"
echo "Duration          : $DURATION seconds (Ramp-up: ${RAMPUP}s)"
echo "JMX Plan          : $PLAN_FILE"
echo "Report Directory  : $REPORT_DIR"
echo "----------------------------------------------------"
echo ""

echo "[INFO] Executing JMeter in non-GUI mode..."
"$JMETER_BIN" -n -t "$PLAN_FILE" \
  -l "$JTL_FILE" \
  -e -o "$REPORT_DIR" \
  -Jhost="$HOST" \
  -Jport="$PORT" \
  -Jprotocol="$PROTOCOL" \
  -Jbooking_rpm="$BOOKING_RPM" \
  -Jbrowse_users="$BROWSE_USERS" \
  -Jbooking_users="$BOOKING_USERS" \
  -Jduration="$DURATION" \
  -Jrampup="$RAMPUP" \
  -Jcsv_file="$CSV_FILE"

echo ""
echo "================================================================"
echo "[SUCCESS] Load test completed successfully!"
echo "HTML Dashboard Report: $REPORT_DIR/index.html"
echo "================================================================"
