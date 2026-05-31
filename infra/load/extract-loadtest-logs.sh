#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

RESULT_ROOT="${RESULT_ROOT:-infra/load/results}"
TARGET_DIR="${1:-${TARGET_DIR:-}}"
API_CONTAINER="${API_CONTAINER:-task-reloader-api}"
DB_CONTAINER="${DB_CONTAINER:-task-reloader-db}"
LOG_EXTRACT_PAD_BEFORE_SEC="${LOG_EXTRACT_PAD_BEFORE_SEC:-180}"
LOG_EXTRACT_PAD_AFTER_SEC="${LOG_EXTRACT_PAD_AFTER_SEC:-180}"
LOG_EXTRACT_TRACE_LINES="${LOG_EXTRACT_TRACE_LINES:-160}"
LOG_EXTRACT_MAX_REPORT_LINES="${LOG_EXTRACT_MAX_REPORT_LINES:-300}"
COPY_TO_CLIPBOARD="${COPY_TO_CLIPBOARD:-false}"

if [[ "${TARGET_DIR}" == "--help" || "${TARGET_DIR}" == "-h" ]]; then
  cat <<'EOF'
Usage:
  infra/load/extract-loadtest-logs.sh [RESULT_DIR_OR_CASE_DIR]

Description:
  - k6 결과 디렉터리의 case-env.txt 시간을 기준으로 API/DB Docker 로그를 추출합니다.
  - 5xx/500/401/429 access log, requestId별 stack trace, exception 요약을 저장합니다.
  - RESULT_DIR를 넘기면 하위 case-env.txt를 모두 처리하고, CASE_DIR를 넘기면 해당 case만 처리합니다.

Env:
  - API_CONTAINER (default: task-reloader-api)
  - DB_CONTAINER (default: task-reloader-db)
  - LOG_EXTRACT_PAD_BEFORE_SEC (default: 180)
  - LOG_EXTRACT_PAD_AFTER_SEC (default: 180)
  - LOG_EXTRACT_TRACE_LINES (default: 160)
  - LOG_EXTRACT_MAX_REPORT_LINES (default: 300)
  - COPY_TO_CLIPBOARD=true|false (default: false)
EOF
  exit 0
fi

for cmd in docker grep sed awk sort uniq wc date; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Required command not found: ${cmd}" >&2
    exit 1
  fi
done

if [[ -z "${TARGET_DIR}" ]]; then
  shopt -s nullglob
  dirs=("${RESULT_ROOT}"/local-*)
  shopt -u nullglob
  if [[ ${#dirs[@]} -eq 0 ]]; then
    echo "No local-* result directory found under ${RESULT_ROOT}" >&2
    exit 1
  fi
  TARGET_DIR="$(ls -dt "${dirs[@]}" | head -1)"
fi

if [[ ! -d "${TARGET_DIR}" ]]; then
  echo "Target directory not found: ${TARGET_DIR}" >&2
  exit 1
fi

read_kv() {
  local file="$1"
  local key="$2"
  awk -F= -v k="$key" '$1==k{print substr($0, index($0,$2)); exit}' "$file"
}

epoch_to_utc() {
  local epoch="$1"
  local out=""
  out="$(date -u -d "@${epoch}" "+%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || true)"
  if [[ -n "${out}" ]]; then
    echo "${out}"
    return 0
  fi
  out="$(date -u -r "${epoch}" "+%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || true)"
  if [[ -n "${out}" ]]; then
    echo "${out}"
    return 0
  fi
  echo "Failed to format epoch: ${epoch}" >&2
  return 1
}

epoch_to_local() {
  local epoch="$1"
  local out=""
  out="$(date -d "@${epoch}" "+%Y-%m-%d %H:%M:%S %z" 2>/dev/null || true)"
  if [[ -n "${out}" ]]; then
    echo "${out}"
    return 0
  fi
  out="$(date -r "${epoch}" "+%Y-%m-%d %H:%M:%S %z" 2>/dev/null || true)"
  if [[ -n "${out}" ]]; then
    echo "${out}"
    return 0
  fi
  echo "${epoch}"
}

copy_file_to_clipboard() {
  local file="$1"
  if [[ "${COPY_TO_CLIPBOARD}" != "true" ]]; then
    return 0
  fi

  if command -v pbcopy >/dev/null 2>&1; then
    pbcopy < "${file}"
  elif command -v wl-copy >/dev/null 2>&1; then
    wl-copy < "${file}"
  elif command -v xclip >/dev/null 2>&1; then
    xclip -selection clipboard < "${file}"
  elif command -v xsel >/dev/null 2>&1; then
    xsel --clipboard --input < "${file}"
  else
    local b64
    b64="$(base64 < "${file}" | tr -d '\n')"
    printf '\e]52;c;%s\a' "${b64}"
    echo "clipboard utility unavailable -> OSC52 clipboard transfer attempted"
  fi
}

extract_access_summary() {
  local input="$1"
  local output="$2"
  awk '
    {
      method="-"; uri="-"; status="-";
      for (i = 1; i <= NF; i++) {
        if ($i ~ /^method=/) { method=substr($i, 8); }
        if ($i ~ /^uri=/) { uri=substr($i, 5); }
        if ($i ~ /^status=/) { status=substr($i, 8); }
      }
      key=status " " method " " uri;
      count[key]++;
    }
    END {
      for (key in count) {
        print count[key] " " key;
      }
    }
  ' "${input}" | sort -nr > "${output}"
}

extract_request_traces() {
  local ids_file="$1"
  local api_log="$2"
  local output="$3"

  : > "${output}"
  if [[ ! -s "${ids_file}" ]]; then
    return 0
  fi

  while read -r request_id; do
    [[ -z "${request_id}" ]] && continue
    {
      echo "===== requestId=${request_id} ====="
      echo
      echo "[all lines with requestId]"
      grep -n "requestId=${request_id}" "${api_log}" || true
      echo
      echo "[stack trace around Unhandled exception]"
      awk -v id="${request_id}" -v max="${LOG_EXTRACT_TRACE_LINES}" '
        index($0, "Unhandled exception requestId=" id) {
          printing=1
          remaining=max
        }
        printing && remaining > 0 {
          print
          remaining--
        }
      ' "${api_log}" || true
      echo
    } >> "${output}"
  done < "${ids_file}"
}

extract_case_logs() {
  local case_dir="$1"
  local case_env="${case_dir}/case-env.txt"
  local out_dir="${case_dir}/log-extract"

  if [[ ! -f "${case_env}" ]]; then
    echo "case-env.txt not found: ${case_env}" >&2
    return 1
  fi

  local started_epoch
  local finished_epoch
  started_epoch="$(read_kv "${case_env}" "STARTED_AT_EPOCH")"
  finished_epoch="$(read_kv "${case_env}" "FINISHED_AT_EPOCH")"

  if [[ ! "${started_epoch}" =~ ^[0-9]+$ ]]; then
    echo "STARTED_AT_EPOCH missing or invalid in ${case_env}" >&2
    return 1
  fi
  if [[ ! "${finished_epoch}" =~ ^[0-9]+$ ]]; then
    finished_epoch="$(date +%s)"
  fi

  local from_epoch=$((started_epoch - LOG_EXTRACT_PAD_BEFORE_SEC))
  local to_epoch=$((finished_epoch + LOG_EXTRACT_PAD_AFTER_SEC))
  local from_utc
  local to_utc
  from_utc="$(epoch_to_utc "${from_epoch}")"
  to_utc="$(epoch_to_utc "${to_epoch}")"

  mkdir -p "${out_dir}"

  local api_log="${out_dir}/api.log"
  local db_log="${out_dir}/db.log"
  local access_5xx="${out_dir}/access-5xx.log"
  local access_500="${out_dir}/access-500.log"
  local access_401_429="${out_dir}/access-401-429.log"
  local request_ids="${out_dir}/5xx-request-ids.txt"
  local request_traces="${out_dir}/5xx-request-traces.log"
  local exception_summary="${out_dir}/exception-summary.txt"
  local access_5xx_summary="${out_dir}/access-5xx-summary.txt"
  local db_errors="${out_dir}/db-errors.log"
  local summary_md="${out_dir}/500-summary.md"

  echo "Extracting logs for ${case_dir}"
  echo "  window: ${from_utc} ~ ${to_utc}"

  docker logs "${API_CONTAINER}" --since "${from_utc}" --until "${to_utc}" > "${api_log}" 2>&1 || true
  docker logs "${DB_CONTAINER}" --since "${from_utc}" --until "${to_utc}" > "${db_log}" 2>&1 || true

  grep -E "access method=.*status=5[0-9][0-9]" "${api_log}" > "${access_5xx}" || true
  grep -E "access method=.*status=500" "${api_log}" > "${access_500}" || true
  grep -E "access method=.*status=(401|429)" "${api_log}" > "${access_401_429}" || true

  sed -nE 's/.*requestId=([a-zA-Z0-9-]+).*/\1/p' "${access_5xx}" | sort -u > "${request_ids}"
  extract_request_traces "${request_ids}" "${api_log}" "${request_traces}"

  grep -E "^[[:space:]]*(Caused by: )?[[:alnum:]_.$]+(Exception|Error):" "${api_log}" \
    | sed -E 's/^[[:space:]]*Caused by: //; s/:.*//' \
    | sort \
    | uniq -c \
    | sort -nr > "${exception_summary}" || true

  if [[ -s "${access_5xx}" ]]; then
    extract_access_summary "${access_5xx}" "${access_5xx_summary}"
  else
    : > "${access_5xx_summary}"
  fi

  grep -Ein "ERROR|FATAL|exception|deadlock|timeout|constraint" "${db_log}" > "${db_errors}" || true

  local total_5xx
  local total_500
  local total_401_429
  local total_request_ids
  local total_db_errors
  total_5xx="$(wc -l < "${access_5xx}" | tr -d ' ')"
  total_500="$(wc -l < "${access_500}" | tr -d ' ')"
  total_401_429="$(wc -l < "${access_401_429}" | tr -d ' ')"
  total_request_ids="$(wc -l < "${request_ids}" | tr -d ' ')"
  total_db_errors="$(wc -l < "${db_errors}" | tr -d ' ')"

  {
    echo "# Load Test 500 Summary"
    echo
    echo "- case_dir: \`${case_dir}\`"
    echo "- api_container: \`${API_CONTAINER}\`"
    echo "- db_container: \`${DB_CONTAINER}\`"
    echo "- window_utc: \`${from_utc} ~ ${to_utc}\`"
    echo "- window_local: \`$(epoch_to_local "${from_epoch}") ~ $(epoch_to_local "${to_epoch}")\`"
    echo "- total_5xx_access_lines: \`${total_5xx}\`"
    echo "- total_500_access_lines: \`${total_500}\`"
    echo "- total_401_429_access_lines: \`${total_401_429}\`"
    echo "- total_5xx_request_ids: \`${total_request_ids}\`"
    echo "- total_db_error_lines: \`${total_db_errors}\`"
    echo
    echo "## 5xx Endpoint Summary"
    if [[ -s "${access_5xx_summary}" ]]; then
      sed -n "1,${LOG_EXTRACT_MAX_REPORT_LINES}p" "${access_5xx_summary}"
    else
      echo "No 5xx access log lines."
    fi
    echo
    echo "## Exception Summary"
    if [[ -s "${exception_summary}" ]]; then
      sed -n "1,${LOG_EXTRACT_MAX_REPORT_LINES}p" "${exception_summary}"
    else
      echo "No exception class lines detected."
    fi
    echo
    echo "## 500 Access Lines"
    if [[ -s "${access_500}" ]]; then
      sed -n "1,${LOG_EXTRACT_MAX_REPORT_LINES}p" "${access_500}"
    else
      echo "No 500 access log lines."
    fi
    echo
    echo "## 5xx Request Traces"
    if [[ -s "${request_traces}" ]]; then
      sed -n "1,${LOG_EXTRACT_MAX_REPORT_LINES}p" "${request_traces}"
    else
      echo "No 5xx request traces."
    fi
    echo
    echo "## DB Errors"
    if [[ -s "${db_errors}" ]]; then
      sed -n "1,${LOG_EXTRACT_MAX_REPORT_LINES}p" "${db_errors}"
    else
      echo "No DB error lines."
    fi
    echo
    echo "## Generated Files"
    echo "- \`api.log\`"
    echo "- \`db.log\`"
    echo "- \`access-5xx.log\`"
    echo "- \`access-500.log\`"
    echo "- \`access-401-429.log\`"
    echo "- \`5xx-request-ids.txt\`"
    echo "- \`5xx-request-traces.log\`"
    echo "- \`exception-summary.txt\`"
    echo "- \`db-errors.log\`"
  } > "${summary_md}"

  copy_file_to_clipboard "${summary_md}"

  echo "  summary: ${summary_md}"
  echo "  5xx=${total_5xx}, 500=${total_500}, 401/429=${total_401_429}, requestIds=${total_request_ids}"
}

case_env_files=()
if [[ -f "${TARGET_DIR}/case-env.txt" ]]; then
  case_env_files=("${TARGET_DIR}/case-env.txt")
else
  while IFS= read -r file; do
    case_env_files+=("${file}")
  done < <(find "${TARGET_DIR}" -maxdepth 3 -type f -name "case-env.txt" | sort)
fi

if [[ ${#case_env_files[@]} -eq 0 ]]; then
  echo "No case-env.txt found under ${TARGET_DIR}" >&2
  exit 1
fi

for case_env in "${case_env_files[@]}"; do
  extract_case_logs "$(dirname "${case_env}")"
done
