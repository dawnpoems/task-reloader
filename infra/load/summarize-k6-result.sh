#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

RESULT_ROOT="${RESULT_ROOT:-infra/load/results}"
TARGET_DIR="${1:-${TARGET_DIR:-}}"

if ! command -v jq >/dev/null 2>&1; then
  echo "Required command not found: jq" >&2
  exit 1
fi

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
  echo "Result directory not found: ${TARGET_DIR}" >&2
  exit 1
fi

mapfile -t summary_files < <(find "${TARGET_DIR}" -type f -name "summary.json" | sort)
if [[ ${#summary_files[@]} -eq 0 ]]; then
  echo "No summary.json found under ${TARGET_DIR}" >&2
  exit 1
fi

tmp_tsv="$(mktemp)"
for summary in "${summary_files[@]}"; do
  case_dir="$(dirname "${summary}")"
  case_name="$(basename "${case_dir}")"
  case_env="${case_dir}/case-env.txt"

  mode=""
  if [[ -f "${case_env}" ]]; then
    mode="$(awk -F= '/^MODE=/{print $2; exit}' "${case_env}")"
  fi
  if [[ -z "${mode}" ]]; then
    mode="${case_name%%-*}"
  fi

  started_at=""
  finished_at=""
  started_epoch=""
  finished_epoch=""
  if [[ -f "${case_env}" ]]; then
    started_at="$(awk -F= '/^STARTED_AT=/{print $2; exit}' "${case_env}")"
    finished_at="$(awk -F= '/^FINISHED_AT=/{print $2; exit}' "${case_env}")"
    started_epoch="$(awk -F= '/^STARTED_AT_EPOCH=/{print $2; exit}' "${case_env}")"
    finished_epoch="$(awk -F= '/^FINISHED_AT_EPOCH=/{print $2; exit}' "${case_env}")"
  fi
  if [[ -z "${started_at}" ]]; then started_at="-"; fi
  if [[ -z "${finished_at}" ]]; then finished_at="-"; fi
  duration_sec="-"
  if [[ "${started_epoch}" =~ ^[0-9]+$ ]] && [[ "${finished_epoch}" =~ ^[0-9]+$ ]]; then
    duration_sec="$((finished_epoch - started_epoch))"
  fi

  jq -r \
    --arg case "${case_name}" \
    --arg mode "${mode}" \
    --arg started_at "${started_at}" \
    --arg finished_at "${finished_at}" \
    --arg duration_sec "${duration_sec}" \
    '
    [
      $mode,
      $case,
      $started_at,
      $finished_at,
      $duration_sec,
      (.metrics.http_reqs.rate // 0),
      (.metrics.http_reqs.count // 0),
      (.metrics.http_req_duration["p(95)"] // 0),
      (.metrics.http_req_duration["p(99)"] // 0),
      (.metrics.http_req_duration.avg // 0),
      (.metrics.http_req_duration.max // 0),
      ((.metrics.http_req_failed.value // 0) * 100),
      (
        (.metrics.checks.passes // 0) as $p
        | (.metrics.checks.fails // 0) as $f
        | if (($p + $f) > 0) then ($p * 100 / ($p + $f)) else 0 end
      )
    ] | @tsv
    ' "${summary}" \
    | awk -F'\t' '{
        printf "%s\t%s\t%s\t%s\t%s\t%.2f\t%.0f\t%.2f\t%.2f\t%.2f\t%.2f\t%.4f\t%.4f\n",
          $1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13
      }' >> "${tmp_tsv}"
done

summary_tsv="${TARGET_DIR}/k6-summary.tsv"
summary_txt="${TARGET_DIR}/k6-summary.txt"

{
  echo -e "MODE\tCASE\tSTARTED_AT\tFINISHED_AT\tDURATION_SEC\tRPS\tREQS\tP95_MS\tP99_MS\tAVG_MS\tMAX_MS\tFAIL_PCT\tCHECK_PCT"
  sort "${tmp_tsv}"
} > "${summary_tsv}"

column -t -s $'\t' "${summary_tsv}" | tee "${summary_txt}"
rm -f "${tmp_tsv}"

run_window_txt="${TARGET_DIR}/k6-run-window.txt"
if [[ -f "${TARGET_DIR}/test-env.txt" ]]; then
  matrix_started_at="$(awk -F= '/^STARTED_AT=/{print $2; exit}' "${TARGET_DIR}/test-env.txt")"
  matrix_finished_at="$(awk -F= '/^FINISHED_AT=/{print $2; exit}' "${TARGET_DIR}/test-env.txt")"
  matrix_started_epoch="$(awk -F= '/^STARTED_AT_EPOCH=/{print $2; exit}' "${TARGET_DIR}/test-env.txt")"
  matrix_finished_epoch="$(awk -F= '/^FINISHED_AT_EPOCH=/{print $2; exit}' "${TARGET_DIR}/test-env.txt")"
  matrix_duration_sec="-"
  if [[ "${matrix_started_epoch}" =~ ^[0-9]+$ ]] && [[ "${matrix_finished_epoch}" =~ ^[0-9]+$ ]]; then
    matrix_duration_sec="$((matrix_finished_epoch - matrix_started_epoch))"
  fi

  {
    echo "RUN_DIR=${TARGET_DIR}"
    echo "STARTED_AT=${matrix_started_at:-"-"}"
    echo "FINISHED_AT=${matrix_finished_at:-"-"}"
    echo "DURATION_SEC=${matrix_duration_sec}"
  } | tee "${run_window_txt}"
fi

echo ""
echo "Summary files generated:"
echo "- ${summary_tsv}"
echo "- ${summary_txt}"
if [[ -f "${run_window_txt}" ]]; then
  echo "- ${run_window_txt}"
fi
