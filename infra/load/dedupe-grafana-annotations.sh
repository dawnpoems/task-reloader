#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

RESULT_ROOT="${RESULT_ROOT:-infra/load/results}"
TARGET_DIR="${1:-${TARGET_DIR:-}}"
GRAFANA_URL="${GRAFANA_URL:-http://127.0.0.1:3001}"
DRY_RUN="${DRY_RUN:-true}"
KEEP_MODE="${KEEP_MODE:-oldest}" # oldest | newest
QUERY_LIMIT="${QUERY_LIMIT:-2000}"
CAPTURE_PAD_BEFORE_SEC="${CAPTURE_PAD_BEFORE_SEC:-120}"
CAPTURE_PAD_AFTER_SEC="${CAPTURE_PAD_AFTER_SEC:-120}"

if [[ "${TARGET_DIR}" == "--help" || "${TARGET_DIR}" == "-h" ]]; then
  cat <<'EOF'
Usage:
  infra/load/dedupe-grafana-annotations.sh [RESULT_DIR]

Description:
  - 주어진 mixed 결과 디렉터리 기준으로 Grafana annotation 중복을 탐지합니다.
  - 중복 기준: (time, timeEnd, text, tags-set) 완전 동일
  - DRY_RUN=true(기본): 삭제 없이 리포트만 출력
  - DRY_RUN=false: 중복 중 keep 기준 제외하고 실제 삭제

Env:
  - GRAFANA_URL (default: http://127.0.0.1:3001)
  - GRAFANA_TOKEN or GRAFANA_USER/GRAFANA_PASSWORD
  - ANNOTATION_TAGS (csv, default: loadtest,mixed,<run-dir>)
  - KEEP_MODE=oldest|newest (default: oldest)
  - QUERY_LIMIT (default: 2000)
  - DRY_RUN=true|false (default: true)
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
  echo "Unsupported duration format: ${value}" >&2
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

urlencode() {
  printf '%s' "$1" | jq -sRr @uri
}

mode="$(read_kv "${CASE_ENV}" "MODE")"
mode="${mode:-mixed}"
if [[ "${mode}" != "mixed" ]]; then
  echo "This helper currently supports MODE=mixed only. MODE=${mode}" >&2
  exit 1
fi

started_epoch="$(read_kv "${CASE_ENV}" "STARTED_AT_EPOCH")"
if [[ -z "${started_epoch}" ]]; then
  started_iso="$(read_kv "${CASE_ENV}" "STARTED_AT")"
  if [[ -z "${started_iso}" ]]; then
    echo "STARTED_AT/STARTED_AT_EPOCH not found in ${CASE_ENV}" >&2
    exit 1
  fi
  started_epoch="$(iso_to_epoch "${started_iso}")"
fi
if [[ ! "${started_epoch}" =~ ^[0-9]+$ ]]; then
  echo "Invalid STARTED_AT_EPOCH: ${started_epoch}" >&2
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

t0="${started_epoch}"
t4="$((started_epoch + warmup_sec + ramp_sec + hold_sec + down_sec))"
from_ms="$(((t0 - CAPTURE_PAD_BEFORE_SEC) * 1000))"
to_ms="$(((t4 + CAPTURE_PAD_AFTER_SEC) * 1000))"

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

if [[ "${KEEP_MODE}" != "oldest" && "${KEEP_MODE}" != "newest" ]]; then
  echo "Invalid KEEP_MODE=${KEEP_MODE} (allowed: oldest|newest)" >&2
  exit 1
fi

curl_auth=()
if [[ -n "${GRAFANA_TOKEN:-}" ]]; then
  curl_auth=(-H "Authorization: Bearer ${GRAFANA_TOKEN}")
elif [[ -n "${GRAFANA_USER:-}" && -n "${GRAFANA_PASSWORD:-}" ]]; then
  curl_auth=(-u "${GRAFANA_USER}:${GRAFANA_PASSWORD}")
else
  echo "Authentication required: set GRAFANA_TOKEN or GRAFANA_USER/GRAFANA_PASSWORD" >&2
  exit 1
fi

api_base="${GRAFANA_URL%/}/api/annotations"
query_url="${api_base}?from=${from_ms}&to=${to_ms}&type=annotation&limit=${QUERY_LIMIT}"
for tag in "${clean_tags[@]}"; do
  query_url="${query_url}&tags=$(urlencode "${tag}")"
done

json="$(
  curl -sS \
    "${curl_auth[@]}" \
    -H "Accept: application/json" \
    "${query_url}"
)"

if ! echo "${json}" | jq -e 'type == "array"' >/dev/null 2>&1; then
  echo "Unexpected response from Grafana annotations API" >&2
  echo "${json}" >&2
  exit 1
fi

dups_json="$(
  echo "${json}" \
    | jq -c --arg keep_mode "${KEEP_MODE}" '
      map({
        id,
        time,
        timeEnd: (.timeEnd // .time),
        text: (.text // ""),
        tags: ((.tags // []) | sort)
      })
      | group_by([.time, .timeEnd, .text, (.tags | join(","))])
      | map(select(length > 1))
      | map(
          sort_by(.id) as $g
          | if $keep_mode == "newest" then
              {
                key: {time: $g[0].time, timeEnd: $g[0].timeEnd, text: $g[0].text, tags: $g[0].tags},
                keepId: $g[-1].id,
                deleteIds: ($g[0:-1] | map(.id))
              }
            else
              {
                key: {time: $g[0].time, timeEnd: $g[0].timeEnd, text: $g[0].text, tags: $g[0].tags},
                keepId: $g[0].id,
                deleteIds: ($g[1:] | map(.id))
              }
            end
        )
    '
)"

group_count="$(echo "${dups_json}" | jq 'length')"
delete_count="$(echo "${dups_json}" | jq '[.[].deleteIds[]] | length')"

echo "Target result dir : ${TARGET_DIR}"
echo "Case dir          : ${CASE_DIR}"
echo "Grafana URL       : ${GRAFANA_URL}"
echo "Tags              : ${clean_tags[*]}"
echo "Query range       : $(epoch_to_iso_local "$((from_ms / 1000))") ~ $(epoch_to_iso_local "$((to_ms / 1000))")"
echo "KEEP_MODE         : ${KEEP_MODE}"
echo "DRY_RUN           : ${DRY_RUN}"
echo ""
echo "Fetched annotations: $(echo "${json}" | jq 'length')"
echo "Duplicate groups  : ${group_count}"
echo "Delete candidates : ${delete_count}"
echo ""

if [[ "${group_count}" -gt 0 ]]; then
  echo "Duplicate groups detail:"
  echo "${dups_json}" \
    | jq -r '
      to_entries[]
      | "  - group #\(.key + 1)\n" +
        "    keepId=\(.value.keepId), deleteIds=\(.value.deleteIds | join(","))\n" +
        "    time=\(.value.key.time), timeEnd=\(.value.key.timeEnd)\n" +
        "    text=\(.value.key.text)\n" +
        "    tags=\(.value.key.tags | join(","))"
    '
fi

if [[ "${DRY_RUN}" == "true" || "${delete_count}" -eq 0 ]]; then
  exit 0
fi

mapfile -t delete_ids < <(echo "${dups_json}" | jq -r '.[].deleteIds[]')
for id in "${delete_ids[@]}"; do
  response="$(
    curl -sS \
      -X DELETE \
      "${curl_auth[@]}" \
      -H "Accept: application/json" \
      -w $'\n%{http_code}' \
      "${api_base}/${id}"
  )"
  body="${response%$'\n'*}"
  code="${response##*$'\n'}"

  if [[ ! "${code}" =~ ^2[0-9][0-9]$ ]]; then
    echo "Delete failed for id=${id}: HTTP ${code}" >&2
    echo "Response: ${body}" >&2
    exit 1
  fi
  message="$(echo "${body}" | jq -r '.message // "deleted"' 2>/dev/null || echo "deleted")"
  echo "Deleted annotation id=${id} (${message})"
done

