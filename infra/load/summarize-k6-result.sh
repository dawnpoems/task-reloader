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

  jq -r \
    --arg case "${case_name}" \
    --arg mode "${mode}" \
    '
    [
      $mode,
      $case,
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
        printf "%s\t%s\t%.2f\t%.0f\t%.2f\t%.2f\t%.2f\t%.2f\t%.4f\t%.4f\n",
          $1,$2,$3,$4,$5,$6,$7,$8,$9,$10
      }' >> "${tmp_tsv}"
done

summary_tsv="${TARGET_DIR}/k6-summary.tsv"
summary_txt="${TARGET_DIR}/k6-summary.txt"

{
  echo -e "MODE\tCASE\tRPS\tREQS\tP95_MS\tP99_MS\tAVG_MS\tMAX_MS\tFAIL_PCT\tCHECK_PCT"
  sort "${tmp_tsv}"
} > "${summary_tsv}"

column -t -s $'\t' "${summary_tsv}" | tee "${summary_txt}"
rm -f "${tmp_tsv}"

echo ""
echo "Summary files generated:"
echo "- ${summary_tsv}"
echo "- ${summary_txt}"

