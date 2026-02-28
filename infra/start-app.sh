#!/usr/bin/env bash
# =============================================================================
# start-app.sh — Build and start the otel-demo Spring Boot app with OTel agent
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR_GLOB="$PROJECT_DIR/target/otel-*-SNAPSHOT.jar"
AGENT_JAR="$PROJECT_DIR/opentelemetry-javaagent.jar"
LOG_FILE="$PROJECT_DIR/app.log"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
die()     { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

# ── Java 21 ───────────────────────────────────────────────────────────────────
export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
JAVA_BIN="$JAVA_HOME/bin/java"
[ -x "$JAVA_BIN" ] || die "Java 21 not found. Install via: brew install --cask temurin@21"
info "Java: $("$JAVA_BIN" -version 2>&1 | head -1)"

# ── Kill any existing app instance ───────────────────────────────────────────
EXISTING=$(pgrep -f "otel-.*SNAPSHOT.jar" 2>/dev/null || true)
if [ -n "$EXISTING" ]; then
  warn "Stopping existing app (PID $EXISTING)..."
  echo "$EXISTING" | xargs kill 2>/dev/null || true
  sleep 2
fi

# ── Build if JAR is missing or source is newer ───────────────────────────────
SKIP_BUILD="${SKIP_BUILD:-false}"
JAR_FILE=$(ls $JAR_GLOB 2>/dev/null | head -1 || true)

if [ "$SKIP_BUILD" = "true" ] && [ -n "$JAR_FILE" ]; then
  info "Skipping build (SKIP_BUILD=true), using: $(basename "$JAR_FILE")"
else
  info "Building application (skipping tests)..."
  cd "$PROJECT_DIR"
  mvn clean package -DskipTests -q
  JAR_FILE=$(ls $JAR_GLOB | head -1)
  success "Built: $(basename "$JAR_FILE")"
fi

# ── OTel agent ────────────────────────────────────────────────────────────────
AGENT_ARGS=()
if [ -f "$AGENT_JAR" ]; then
  info "OTel Java agent found: $AGENT_JAR"
  AGENT_ARGS=(-javaagent:"$AGENT_JAR")
else
  warn "opentelemetry-javaagent.jar not found at project root — running WITHOUT agent"
  warn "Traces and metrics from the app will NOT be exported"
  warn "Download from: https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases"
fi

# ── Start app ─────────────────────────────────────────────────────────────────
info "Starting app in background → log: $LOG_FILE"
"$JAVA_BIN" \
  "${AGENT_ARGS[@]}" \
  -Dspring.profiles.active=local \
  -Dotel.service.name=otel-demo \
  -Dotel.exporter.otlp.endpoint=http://localhost:4318 \
  -Dotel.exporter.otlp.protocol=http/protobuf \
  -Dotel.traces.sampler=always_on \
  -Dotel.metrics.exporter=otlp \
  -Dotel.logs.exporter=otlp \
  -jar "$JAR_FILE" \
  > "$LOG_FILE" 2>&1 &

APP_PID=$!
echo "$APP_PID" > "$PROJECT_DIR/.app.pid"
info "App PID $APP_PID — waiting for startup..."

# ── Wait for actuator health ──────────────────────────────────────────────────
elapsed=0
until curl -sf http://localhost:8080/actuator/health -o /dev/null 2>/dev/null; do
  sleep 2; elapsed=$((elapsed+2))
  printf "."
  if [ $elapsed -ge 60 ]; then
    echo ""
    die "App did not start within 60s. Check $LOG_FILE"
  fi
done
echo ""

success "App is UP (PID $APP_PID)"
echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  otel-demo App is running${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "  Health:    ${CYAN}http://localhost:8080/actuator/health${NC}"
echo -e "  API base:  ${CYAN}http://localhost:8080/api/v1/customers${NC}"
echo -e "  Logs:      ${CYAN}$LOG_FILE${NC}"
echo -e "  PID file:  ${CYAN}$PROJECT_DIR/.app.pid${NC}"
echo ""
echo -e "  Next steps:"
echo -e "    Generate load:  ${YELLOW}./infra/load-test.sh${NC}"
echo -e "    E2E test:       ${YELLOW}./infra/e2e-test.sh${NC}"
echo -e "    Grafana:        ${CYAN}http://localhost:3000${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
