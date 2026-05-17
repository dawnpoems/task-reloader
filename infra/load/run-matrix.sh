#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:3000}"
AUTH_EMAIL="${AUTH_EMAIL:-demo@dawnpoem.kr}"
AUTH_PASSWORD="${AUTH_PASSWORD:-demo1234!}"
RELOGIN_ON_401="${RELOGIN_ON_401:-true}"
SCRIPT_PATH="${SCRIPT_PATH:-infra/load/k6-auth-read-local.js}"

RESULT_ROOT="${RESULT_ROOT:-infra/load/results}"
MATRIX_NAME="${MATRIX_NAME:-local-read-matrix-$(date +%Y%m%d-%H%M%S)}"
MATRIX_DIR="${RESULT_ROOT}/${MATRIX_NAME}"

APP_CONTAINERS=(
  "task-reloader-web"
  "task-reloader-api"
  "task-reloader-db"
)

OBS_CONTAINERS=(
  "task-reloader-prometheus"
  "task-reloader-grafana"
)

mkdir -p "${MATRIX_DIR}"

echo "Matrix directory: ${MATRIX_DIR}"

git rev-parse HEAD | tee "${MATRIX_DIR}/git-sha.txt"
git status --short | tee "${MATRIX_DIR}/git-status.txt"

{
  echo "BASE_URL=${BASE_URL}"
  echo "SCRIPT_PATH=${SCRIPT_PATH}"
  echo "AUTH_EMAIL=${AUTH_EMAIL}"
  echo "RELOGIN_ON_401=${RELOGIN_ON_401}"
  echo "STARTED_AT=$(date -Iseconds)"
} | tee "${MATRIX_DIR}/test-env.txt"

uname -a | tee "${MATRIX_DIR}/system.txt"
free -h | tee "${MATRIX_DIR}/memory.txt"
df -h | tee "${MATRIX_DIR}/disk.txt"
docker ps | tee "${MATRIX_DIR}/docker-ps.txt"

echo "Checking app container stats before all tests..."
docker stats --no-stream "${APP_CONTAINERS[@]}" | tee "${MATRIX_DIR}/docker-stats-app-before-all.txt"

echo "Checking observability container stats before all tests..."
docker stats --no-stream "${OBS_CONTAINERS[@]}" | tee "${MATRIX_DIR}/docker-stats-observability-before-all.txt"

run_case() {
  local name="$1"
  local vus="$2"
  local duration="$3"

  local case_dir="${MATRIX_DIR}/${name}-vus${vus}-${duration}"
  mkdir -p "${case_dir}"

  echo ""
  echo "========================================"
  echo "Running case: ${name}"
  echo "VUS=${vus}, DURATION=${duration}"
  echo "========================================"

  {
    echo "CASE=${name}"
    echo "BASE_URL=${BASE_URL}"
    echo "VUS=${vus}"
    echo "DURATION=${duration}"
    echo "STARTED_AT=$(date -Iseconds)"
  } | tee "${case_dir}/case-env.txt"

  echo "Container stats before case..."
  docker stats --no-stream "${APP_CONTAINERS[@]}" | tee "${case_dir}/docker-stats-app-before.txt"
  docker stats --no-stream "${OBS_CONTAINERS[@]}" | tee "${case_dir}/docker-stats-observability-before.txt"

  BASE_URL="${BASE_URL}" \
  AUTH_EMAIL="${AUTH_EMAIL}" \
  AUTH_PASSWORD="${AUTH_PASSWORD}" \
  RELOGIN_ON_401="${RELOGIN_ON_401}" \
  VUS="${vus}" \
  DURATION="${duration}" \
  k6 run "${SCRIPT_PATH}" \
    --summary-mode=full \
    --summary-trend-stats="avg,min,med,max,p(90),p(95),p(99),count" \
    --summary-export "${case_dir}/summary.json" \
    | tee "${case_dir}/k6.log"

  echo "Container stats after case..."
  docker stats --no-stream "${APP_CONTAINERS[@]}" | tee "${case_dir}/docker-stats-app-after.txt"
  docker stats --no-stream "${OBS_CONTAINERS[@]}" | tee "${case_dir}/docker-stats-observability-after.txt"

  {
    echo "FINISHED_AT=$(date -Iseconds)"
  } | tee -a "${case_dir}/case-env.txt"

  echo "Cooling down for 60 seconds..."
  sleep 60
}

run_case "smoke" 5 "1m"
run_case "baseline" 20 "10m"
run_case "step" 40 "5m"
run_case "step" 60 "5m"
run_case "step" 80 "5m"

echo "FINISHED_AT=$(date -Iseconds)" | tee -a "${MATRIX_DIR}/test-env.txt"

echo ""
echo "Done."
echo "Result directory: ${MATRIX_DIR}"
