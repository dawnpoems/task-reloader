#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

RESULT_ROOT="${RESULT_ROOT:-infra/load/results}"
TARGET_DIR="${1:-${TARGET_DIR:-}}"
GRAFANA_URL="${GRAFANA_URL:-http://127.0.0.1:3001}"
DRY_RUN="${DRY_RUN:-false}"
ROUND_TO_MINUTE="${ROUND_TO_MINUTE:-true}"
CAPTURE_PAD_BEFORE_SEC="${CAPTURE_PAD_BEFORE_SEC:-120}"
CAPTURE_PAD_AFTER_SEC="${CAPTURE_PAD_AFTER_SEC:-120}"

if [[ "${TARGET_DIR}" == "--help" || "${TARGET_DIR}" == "-h" ]]; then
  cat <<'EOF'
Usage:
  infra/load/create-grafana-annotations.sh [RESULT_DIR]

Description:
  - mixed 실행 결과(case-env/test-env)를 읽어 Grafana org annotation 9개를 생성합니다.
    * 시각선 5개: 시작/20도달/50도달/유지종료/종료
    * 구간 4개: 0->20 / 20->50 / 50유지 / 50->0
  - 같은 태그를 조회하는 모든 대시보드에서 재사용할 수 있습니다.

Auth:
  - GRAFANA_TOKEN=...  (권장)
  - 또는 GRAFANA_USER=... GRAFANA_PASSWORD=...

Env:
  - GRAFANA_URL (default: http://127.0.0.1:3001)
  - ANNOTATION_TAGS (csv, default: loadtest,mixed,<run-dir>)
  - ROUND_TO_MINUTE=true|false (default: true)
  - CAPTURE_PAD_BEFORE_SEC (default: 120)
  - CAPTURE_PAD_AFTER_SEC  (default: 120)
  - DRY_RUN=true|false (default: false)
EOF
  exit 0
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "Required command not found: jq" >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "Required command not found: curl" >&2
  exit 1
fi

if [[ -z "${TARGET_DIR}" ]]; then
  shopt -s nullglob
  dirs=("${RESULT_ROOT}"/local-mixed-peak-*)
  shopt -u nullglob
  if [[ ${#dirs[@]} -eq 0 ]]; then
    echo "No local-mixed-peak-* result directory found under ${RESULT_ROOT}" >&2
    exit 1
  fi
  TARGET_DIR="$(ls -dt "${dirs[@]}" | head -1)"
fi

if [[ ! -d "${TARGET_DIR}" ]]; then
  echo "Result directory not found: ${TARGET_DIR}" >&2
  exit 1
fi

CASE_DIR=""
if [[ -f "${TARGET_DIR}/case-env.txt" ]]; then
  CASE_DIR="${TARGET_DIR}"
elif [[ -f "${TARGET_DIR}/mixed-peak/case-env.txt" ]]; then
  CASE_DIR="${TARGET_DIR}/mixed-peak"
else
  mapfile -t case_files < <(find "${TARGET_DIR}" -maxdepth 3 -type f -name "case-env.txt" | sort)
  if [[ ${#case_files[@]} -eq 0 ]]; then
    echo "case-env.txt not found under ${TARGET_DIR}" >&2
    exit 1
  fi
  CASE_DIR="$(dirname "${case_files[0]}")"
fi

CASE_ENV="${CASE_DIR}/case-env.txt"
MATRIX_DIR="$(cd -- "${CASE_DIR}/.." && pwd)"
TEST_ENV="${MATRIX_DIR}/test-env.txt"

read_kv() {
  local file="$1"
  local key="$2"
  awk -F= -v k="$key" '$1==k{print substr($0, index($0,$2)); exit}' "$file"
}

parse_duration_seconds() {
  local value="$1"
  if [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${value}"
    return 0
  fi
  if [[ "${value}" =~ ^([0-9]+)([smhd])$ ]]; then
    local num="${BASH_REMATCH[1]}"
    local unit="${BASH_REMATCH[2]}"
    case "${unit}" in
      s) echo "${num}" ;;
      m) echo "$((num * 60))" ;;
      h) echo "$((num * 3600))" ;;
      d) echo "$((num * 86400))" ;;
      *)
        echo "Unsupported duration unit: ${value}" >&2
        return 1
        ;;
    esac
    return 0
  fi
  echo "Unsupported duration format: ${value} (allowed: 300, 5m, 2h ...)" >&2
  return 1
}

iso_to_epoch() {
  local iso="$1"
  local epoch=""

  epoch="$(date -d "${iso}" +%s 2>/dev/null || true)"
  if [[ -n "${epoch}" ]]; then
    echo "${epoch}"
    return 0
  fi

  # BSD date fallback (macOS)
  epoch="$(date -j -f "%Y-%m-%dT%H:%M:%S%z" "${iso}" +%s 2>/dev/null || true)"
  if [[ -n "${epoch}" ]]; then
    echo "${epoch}"
    return 0
  fi

  echo "Failed to parse ISO datetime: ${iso}" >&2
  return 1
}

epoch_to_iso_local() {
  local epoch="$1"
  local out=""
  out="$(date -d "@${epoch}" +"%Y-%m-%d %H:%M:%S %z" 2>/dev/null || true)"
  if [[ -n "${out}" ]]; then
    echo "${out}"
    return 0
  fi
  out="$(date -r "${epoch}" +"%Y-%m-%d %H:%M:%S %z" 2>/dev/null || true)"
  if [[ -n "${out}" ]]; then
    echo "${out}"
    return 0
  fi
  echo "${epoch}"
}

round_to_minute_epoch() {
  local epoch="$1"
  if [[ "${ROUND_TO_MINUTE}" != "true" ]]; then
    echo "${epoch}"
    return 0
  fi
  echo "$((((epoch + 30) / 60) * 60))"
}

MODE="$(read_kv "${CASE_ENV}" "MODE")"
if [[ -z "${MODE}" ]]; then
  MODE="mixed"
fi

STARTED_AT_EPOCH="$(read_kv "${CASE_ENV}" "STARTED_AT_EPOCH")"
if [[ -z "${STARTED_AT_EPOCH}" ]]; then
  started_at_iso="$(read_kv "${CASE_ENV}" "STARTED_AT")"
  if [[ -z "${started_at_iso}" ]]; then
    echo "STARTED_AT/STARTED_AT_EPOCH not found in ${CASE_ENV}" >&2
    exit 1
  fi
  STARTED_AT_EPOCH="$(iso_to_epoch "${started_at_iso}")"
fi

if [[ ! "${STARTED_AT_EPOCH}" =~ ^[0-9]+$ ]]; then
  echo "Invalid STARTED_AT_EPOCH: ${STARTED_AT_EPOCH}" >&2
  exit 1
fi

if [[ "${MODE}" != "mixed" ]]; then
  echo "This helper currently supports MODE=mixed only. MODE=${MODE}" >&2
  exit 1
fi

warmup_raw="${MIXED_WARMUP_DURATION:-}"
ramp_raw="${MIXED_RAMP_TO_PEAK_DURATION:-}"
hold_raw="${MIXED_PEAK_HOLD_DURATION:-}"
down_raw="${MIXED_RAMP_DOWN_DURATION:-}"

if [[ -z "${warmup_raw}" && -f "${TEST_ENV}" ]]; then
  warmup_raw="$(read_kv "${TEST_ENV}" "MIXED_WARMUP_DURATION")"
fi
if [[ -z "${ramp_raw}" && -f "${TEST_ENV}" ]]; then
  ramp_raw="$(read_kv "${TEST_ENV}" "MIXED_RAMP_TO_PEAK_DURATION")"
fi
if [[ -z "${hold_raw}" && -f "${TEST_ENV}" ]]; then
  hold_raw="$(read_kv "${TEST_ENV}" "MIXED_PEAK_HOLD_DURATION")"
fi
if [[ -z "${down_raw}" && -f "${TEST_ENV}" ]]; then
  down_raw="$(read_kv "${TEST_ENV}" "MIXED_RAMP_DOWN_DURATION")"
fi

warmup_raw="${warmup_raw:-5m}"
ramp_raw="${ramp_raw:-5m}"
hold_raw="${hold_raw:-20m}"
down_raw="${down_raw:-5m}"

warmup_sec="$(parse_duration_seconds "${warmup_raw}")"
ramp_sec="$(parse_duration_seconds "${ramp_raw}")"
hold_sec="$(parse_duration_seconds "${hold_raw}")"
down_sec="$(parse_duration_seconds "${down_raw}")"

t0_exact="${STARTED_AT_EPOCH}"
t1_exact="$((t0_exact + warmup_sec))"
t2_exact="$((t1_exact + ramp_sec))"
t3_exact="$((t2_exact + hold_sec))"
t4_exact="$((t3_exact + down_sec))"

t0="$(round_to_minute_epoch "${t0_exact}")"
t1="$(round_to_minute_epoch "${t1_exact}")"
t2="$(round_to_minute_epoch "${t2_exact}")"
t3="$(round_to_minute_epoch "${t3_exact}")"
t4="$(round_to_minute_epoch "${t4_exact}")"

run_tag="$(basename "${MATRIX_DIR}")"
default_tags="loadtest,mixed,${run_tag}"
ANNOTATION_TAGS="${ANNOTATION_TAGS:-${default_tags}}"

IFS=',' read -r -a raw_tags <<< "${ANNOTATION_TAGS}"
clean_tags=()
for tag in "${raw_tags[@]}"; do
  trimmed="$(echo "${tag}" | xargs)"
  if [[ -n "${trimmed}" ]]; then
    clean_tags+=("${trimmed}")
  fi
done
if [[ ${#clean_tags[@]} -eq 0 ]]; then
  clean_tags=("loadtest" "mixed" "${run_tag}")
fi
tags_json="$(printf '%s\n' "${clean_tags[@]}" | jq -Rsc 'split("\n")[:-1]')"

api_url="${GRAFANA_URL%/}/api/annotations"

curl_auth=()
if [[ "${DRY_RUN}" != "true" ]]; then
  if [[ -n "${GRAFANA_TOKEN:-}" ]]; then
    curl_auth=(-H "Authorization: Bearer ${GRAFANA_TOKEN}")
  elif [[ -n "${GRAFANA_USER:-}" && -n "${GRAFANA_PASSWORD:-}" ]]; then
    curl_auth=(-u "${GRAFANA_USER}:${GRAFANA_PASSWORD}")
  else
    echo "Authentication required: set GRAFANA_TOKEN or GRAFANA_USER/GRAFANA_PASSWORD" >&2
    exit 1
  fi
else
  curl_auth=()
fi

post_annotation() {
  local time_ms="$1"
  local time_end_ms="$2"
  local text="$3"

  local payload
  if [[ -n "${time_end_ms}" ]]; then
    payload="$(jq -nc \
      --argjson time "${time_ms}" \
      --argjson timeEnd "${time_end_ms}" \
      --arg text "${text}" \
      --argjson tags "${tags_json}" \
      '{time: $time, timeEnd: $timeEnd, text: $text, tags: $tags}')"
  else
    payload="$(jq -nc \
      --argjson time "${time_ms}" \
      --arg text "${text}" \
      --argjson tags "${tags_json}" \
      '{time: $time, text: $text, tags: $tags}')"
  fi

  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "[DRY_RUN] POST ${api_url}"
    echo "${payload}"
    return 0
  fi

  response="$(
    curl -sS \
      -X POST \
      "${curl_auth[@]}" \
      -H "Content-Type: application/json" \
      -H "Accept: application/json" \
      --data "${payload}" \
      -w $'\n%{http_code}' \
      "${api_url}"
  )"

  body="${response%$'\n'*}"
  code="${response##*$'\n'}"

  if [[ ! "${code}" =~ ^2[0-9][0-9]$ ]]; then
    echo "Annotation API failed: HTTP ${code}" >&2
    echo "Response: ${body}" >&2
    exit 1
  fi

  id="$(echo "${body}" | jq -r '.id // "-"' 2>/dev/null || echo "-")"
  msg="$(echo "${body}" | jq -r '.message // "ok"' 2>/dev/null || echo "ok")"
  echo "Created annotation: id=${id}, message=${msg}, text=${text}"
}

ts0_ms="$((t0 * 1000))"
ts1_ms="$((t1 * 1000))"
ts2_ms="$((t2 * 1000))"
ts3_ms="$((t3 * 1000))"
ts4_ms="$((t4 * 1000))"

echo "Target result dir : ${TARGET_DIR}"
echo "Case dir          : ${CASE_DIR}"
echo "Mode              : ${MODE}"
echo "Grafana URL       : ${GRAFANA_URL}"
echo "DRY_RUN           : ${DRY_RUN}"
echo "ROUND_TO_MINUTE   : ${ROUND_TO_MINUTE}"
echo "Tags              : ${clean_tags[*]}"
echo ""
echo "Phase boundaries:"
echo "1) Start             $(epoch_to_iso_local "${t0}")"
echo "2) 20 VU reached     $(epoch_to_iso_local "${t1}")"
echo "3) 50 VU reached     $(epoch_to_iso_local "${t2}")"
echo "4) 50 VU hold ends   $(epoch_to_iso_local "${t3}")"
echo "5) Ramp-down ends    $(epoch_to_iso_local "${t4}")"
echo ""

post_annotation "${ts0_ms}" "" "mixed: start (0->20 ramp)"
post_annotation "${ts1_ms}" "" "mixed: 20 VU reached (20->50 ramp)"
post_annotation "${ts2_ms}" "" "mixed: 50 VU reached (hold start)"
post_annotation "${ts3_ms}" "" "mixed: 50 VU hold ends (ramp-down start)"
post_annotation "${ts4_ms}" "" "mixed: finished (0 VU)"

post_annotation "${ts0_ms}" "${ts1_ms}" "mixed phase 1: 0->20 VU"
post_annotation "${ts1_ms}" "${ts2_ms}" "mixed phase 2: 20->50 VU"
post_annotation "${ts2_ms}" "${ts3_ms}" "mixed phase 3: 50 VU hold"
post_annotation "${ts3_ms}" "${ts4_ms}" "mixed phase 4: 50->0 VU"

capture_from="$((t0 - CAPTURE_PAD_BEFORE_SEC))"
capture_to="$((t4 + CAPTURE_PAD_AFTER_SEC))"

echo ""
echo "Recommended Grafana capture window (with padding):"
echo "FROM: $(epoch_to_iso_local "${capture_from}")"
echo "TO  : $(epoch_to_iso_local "${capture_to}")"
echo ""
echo "Tip: dashboard annotation query must include the same tags (or all tags)."
