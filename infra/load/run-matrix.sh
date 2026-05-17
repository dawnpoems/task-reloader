#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

BASE_URL="${BASE_URL:-http://127.0.0.1:3000}"
AUTH_EMAIL="${AUTH_EMAIL:-demo@dawnpoem.kr}"
AUTH_PASSWORD="${AUTH_PASSWORD:-demo1234!}"
RELOGIN_ON_401="${RELOGIN_ON_401:-true}"
SCRIPT_PATH="${SCRIPT_PATH:-infra/load/k6-auth-read-local.js}" # read matrix
MIXED_SCRIPT_PATH="${MIXED_SCRIPT_PATH:-infra/load/k6-auth-mixed-peak-local.js}"
SOAK_SCRIPT_PATH="${SOAK_SCRIPT_PATH:-infra/load/k6-auth-soak-local.js}"

RESULT_ROOT="${RESULT_ROOT:-infra/load/results}"
SUITE_MODE="${SUITE_MODE:-read}" # read | mixed | soak | all | read,mixed,soak
COOLDOWN_SECONDS="${COOLDOWN_SECONDS:-60}"
MIXED_WRITE_RATIO_PERCENT="${MIXED_WRITE_RATIO_PERCENT:-30}"
MIXED_WARMUP_VUS="${MIXED_WARMUP_VUS:-20}"
MIXED_PEAK_VUS="${MIXED_PEAK_VUS:-50}"
MIXED_WARMUP_DURATION="${MIXED_WARMUP_DURATION:-5m}"
MIXED_RAMP_TO_PEAK_DURATION="${MIXED_RAMP_TO_PEAK_DURATION:-5m}"
MIXED_PEAK_HOLD_DURATION="${MIXED_PEAK_HOLD_DURATION:-20m}"
MIXED_RAMP_DOWN_DURATION="${MIXED_RAMP_DOWN_DURATION:-5m}"
SOAK_VUS="${SOAK_VUS:-60}"
SOAK_DURATION="${SOAK_DURATION:-2h}"
SOAK_WRITE_RATIO_PERCENT="${SOAK_WRITE_RATIO_PERCENT:-15}"
SUMMARY_AFTER_RUN="${SUMMARY_AFTER_RUN:-true}"

if [[ -z "${MATRIX_NAME:-}" ]]; then
  case "${SUITE_MODE}" in
    read) MATRIX_NAME="local-read-matrix-$(date +%Y%m%d-%H%M%S)" ;;
    mixed) MATRIX_NAME="local-mixed-peak-$(date +%Y%m%d-%H%M%S)" ;;
    soak) MATRIX_NAME="local-soak-$(date +%Y%m%d-%H%M%S)" ;;
    *) MATRIX_NAME="local-k6-suite-$(date +%Y%m%d-%H%M%S)" ;;
  esac
fi

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

for cmd in git k6 docker uname df; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Required command not found: ${cmd}" >&2
    exit 1
  fi
done

ensure_running_container() {
  local name="$1"
  if ! docker ps --format '{{.Names}}' | grep -qx "${name}"; then
    echo "Required running container not found: ${name}" >&2
    echo "Run: cd infra && docker compose up -d" >&2
    exit 1
  fi
}

for c in "${APP_CONTAINERS[@]}" "${OBS_CONTAINERS[@]}"; do
  ensure_running_container "${c}"
done

git rev-parse HEAD | tee "${MATRIX_DIR}/git-sha.txt"
git status --short | tee "${MATRIX_DIR}/git-status.txt"

{
  echo "SUITE_MODE=${SUITE_MODE}"
  echo "BASE_URL=${BASE_URL}"
  echo "READ_SCRIPT_PATH=${SCRIPT_PATH}"
  echo "MIXED_SCRIPT_PATH=${MIXED_SCRIPT_PATH}"
  echo "SOAK_SCRIPT_PATH=${SOAK_SCRIPT_PATH}"
  echo "AUTH_EMAIL=${AUTH_EMAIL}"
  echo "RELOGIN_ON_401=${RELOGIN_ON_401}"
  echo "MIXED_WRITE_RATIO_PERCENT=${MIXED_WRITE_RATIO_PERCENT}"
  echo "MIXED_WARMUP_VUS=${MIXED_WARMUP_VUS}"
  echo "MIXED_PEAK_VUS=${MIXED_PEAK_VUS}"
  echo "MIXED_WARMUP_DURATION=${MIXED_WARMUP_DURATION}"
  echo "MIXED_RAMP_TO_PEAK_DURATION=${MIXED_RAMP_TO_PEAK_DURATION}"
  echo "MIXED_PEAK_HOLD_DURATION=${MIXED_PEAK_HOLD_DURATION}"
  echo "MIXED_RAMP_DOWN_DURATION=${MIXED_RAMP_DOWN_DURATION}"
  echo "SOAK_VUS=${SOAK_VUS}"
  echo "SOAK_DURATION=${SOAK_DURATION}"
  echo "SOAK_WRITE_RATIO_PERCENT=${SOAK_WRITE_RATIO_PERCENT}"
  echo "COOLDOWN_SECONDS=${COOLDOWN_SECONDS}"
  echo "SUMMARY_AFTER_RUN=${SUMMARY_AFTER_RUN}"
  echo "STARTED_AT=$(date -Iseconds)"
  echo "STARTED_AT_EPOCH=$(date +%s)"
} | tee "${MATRIX_DIR}/test-env.txt"

uname -a | tee "${MATRIX_DIR}/system.txt"
if command -v free >/dev/null 2>&1; then
  free -h | tee "${MATRIX_DIR}/memory.txt"
else
  {
    echo "free command unavailable on this host"
    if command -v vm_stat >/dev/null 2>&1; then
      vm_stat
    fi
  } | tee "${MATRIX_DIR}/memory.txt"
fi
df -h | tee "${MATRIX_DIR}/disk.txt"
docker ps | tee "${MATRIX_DIR}/docker-ps.txt"

echo "Checking app container stats before all tests..."
docker stats --no-stream "${APP_CONTAINERS[@]}" | tee "${MATRIX_DIR}/docker-stats-app-before-all.txt"

echo "Checking observability container stats before all tests..."
docker stats --no-stream "${OBS_CONTAINERS[@]}" | tee "${MATRIX_DIR}/docker-stats-observability-before-all.txt"

if [[ "${SUITE_MODE}" == "all" ]]; then
  MODE_LIST=(read mixed soak)
else
  IFS=',' read -r -a MODE_LIST <<< "${SUITE_MODE}"
fi

for mode in "${MODE_LIST[@]}"; do
  trimmed_mode="$(echo "${mode}" | tr -d '[:space:]')"
  case "${trimmed_mode}" in
    read|mixed|soak) ;;
    *)
      echo "Invalid SUITE_MODE value: '${trimmed_mode}' (allowed: read,mixed,soak,all)" >&2
      exit 1
      ;;
  esac
done

should_run() {
  local target="$1"
  local m
  for m in "${MODE_LIST[@]}"; do
    if [[ "$(echo "${m}" | tr -d '[:space:]')" == "${target}" ]]; then
      return 0
    fi
  done
  return 1
}

if should_run "read" && [[ ! -f "${SCRIPT_PATH}" ]]; then
  echo "Read script not found: ${SCRIPT_PATH}" >&2
  exit 1
fi
if should_run "mixed" && [[ ! -f "${MIXED_SCRIPT_PATH}" ]]; then
  echo "Mixed script not found: ${MIXED_SCRIPT_PATH}" >&2
  exit 1
fi
if should_run "soak" && [[ ! -f "${SOAK_SCRIPT_PATH}" ]]; then
  echo "Soak script not found: ${SOAK_SCRIPT_PATH}" >&2
  exit 1
fi

run_read_case() {
  local name="$1"
  local vus="$2"
  local duration="$3"

  local case_dir="${MATRIX_DIR}/read-${name}-vus${vus}-${duration}"
  mkdir -p "${case_dir}"

  echo ""
  echo "========================================"
  echo "Running case: ${name}"
  echo "VUS=${vus}, DURATION=${duration}"
  echo "========================================"

  {
    echo "MODE=read"
    echo "CASE=${name}"
    echo "BASE_URL=${BASE_URL}"
    echo "SCRIPT_PATH=${SCRIPT_PATH}"
    echo "VUS=${vus}"
    echo "DURATION=${duration}"
    echo "STARTED_AT=$(date -Iseconds)"
    echo "STARTED_AT_EPOCH=$(date +%s)"
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
    echo "FINISHED_AT_EPOCH=$(date +%s)"
  } | tee -a "${case_dir}/case-env.txt"

  echo "Cooling down for ${COOLDOWN_SECONDS} seconds..."
  sleep "${COOLDOWN_SECONDS}"
}

run_single_case() {
  local mode="$1"
  local case_name="$2"
  local script_path="$3"
  shift 3
  local case_dir="${MATRIX_DIR}/${mode}-${case_name}"
  mkdir -p "${case_dir}"

  echo ""
  echo "========================================"
  echo "Running ${mode} case: ${case_name}"
  echo "SCRIPT=${script_path}"
  echo "========================================"

  {
    echo "MODE=${mode}"
    echo "CASE=${case_name}"
    echo "BASE_URL=${BASE_URL}"
    echo "SCRIPT_PATH=${script_path}"
    echo "STARTED_AT=$(date -Iseconds)"
    echo "STARTED_AT_EPOCH=$(date +%s)"
  } | tee "${case_dir}/case-env.txt"

  echo "Container stats before case..."
  docker stats --no-stream "${APP_CONTAINERS[@]}" | tee "${case_dir}/docker-stats-app-before.txt"
  docker stats --no-stream "${OBS_CONTAINERS[@]}" | tee "${case_dir}/docker-stats-observability-before.txt"

  env \
    BASE_URL="${BASE_URL}" \
    AUTH_EMAIL="${AUTH_EMAIL}" \
    AUTH_PASSWORD="${AUTH_PASSWORD}" \
    RELOGIN_ON_401="${RELOGIN_ON_401}" \
    "$@" \
    k6 run "${script_path}" \
    --summary-mode=full \
    --summary-trend-stats="avg,min,med,max,p(90),p(95),p(99),count" \
    --summary-export "${case_dir}/summary.json" \
    | tee "${case_dir}/k6.log"

  echo "Container stats after case..."
  docker stats --no-stream "${APP_CONTAINERS[@]}" | tee "${case_dir}/docker-stats-app-after.txt"
  docker stats --no-stream "${OBS_CONTAINERS[@]}" | tee "${case_dir}/docker-stats-observability-after.txt"

  {
    echo "FINISHED_AT=$(date -Iseconds)"
    echo "FINISHED_AT_EPOCH=$(date +%s)"
  } | tee -a "${case_dir}/case-env.txt"

  echo "Cooling down for ${COOLDOWN_SECONDS} seconds..."
  sleep "${COOLDOWN_SECONDS}"
}

if should_run "read"; then
  run_read_case "smoke" 5 "1m"
  run_read_case "baseline" 20 "10m"
  run_read_case "step" 40 "5m"
  run_read_case "step" 60 "5m"
  run_read_case "step" 80 "5m"
fi

if should_run "mixed"; then
  run_single_case "mixed" "peak" "${MIXED_SCRIPT_PATH}" \
    "WARMUP_VUS=${MIXED_WARMUP_VUS}" \
    "PEAK_VUS=${MIXED_PEAK_VUS}" \
    "WARMUP_DURATION=${MIXED_WARMUP_DURATION}" \
    "RAMP_TO_PEAK_DURATION=${MIXED_RAMP_TO_PEAK_DURATION}" \
    "PEAK_HOLD_DURATION=${MIXED_PEAK_HOLD_DURATION}" \
    "RAMP_DOWN_DURATION=${MIXED_RAMP_DOWN_DURATION}" \
    "WRITE_RATIO_PERCENT=${MIXED_WRITE_RATIO_PERCENT}"
fi

if should_run "soak"; then
  run_single_case "soak" "steady" "${SOAK_SCRIPT_PATH}" \
    "SOAK_VUS=${SOAK_VUS}" \
    "SOAK_DURATION=${SOAK_DURATION}" \
    "WRITE_RATIO_PERCENT=${SOAK_WRITE_RATIO_PERCENT}"
fi

{
  echo "FINISHED_AT=$(date -Iseconds)"
  echo "FINISHED_AT_EPOCH=$(date +%s)"
} | tee -a "${MATRIX_DIR}/test-env.txt"

echo ""
echo "Done."
echo "Result directory: ${MATRIX_DIR}"

if [[ "${SUMMARY_AFTER_RUN}" == "true" ]]; then
  summary_script="${PROJECT_ROOT}/infra/load/summarize-k6-result.sh"
  if [[ -x "${summary_script}" ]]; then
    echo ""
    echo "Generating summary table..."
    "${summary_script}" "${MATRIX_DIR}" || true
  else
    echo "Summary script not executable: ${summary_script}"
  fi
fi
