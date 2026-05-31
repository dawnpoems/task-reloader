# Work Unit: p4-load-testing-summary-doc

## 작업 내용

- 전체 k6/Grafana 부하테스트 흐름을 하나의 종합 문서로 정리
- Read Matrix, Mixed Peak, 초기 Soak, Fixed Token Mixed, 수정 후 Mixed 재검증, Fixed Token Soak 결과를 연결해 해석
- 부하테스트를 통해 확인한 병목, 코드 수정, 재검증 결과, 남은 후속 과제를 정리
- 메인 README의 부하테스트 섹션을 최신 결과와 종합 문서 링크 중심으로 갱신

## 테스트 방법

- 각 결과 폴더의 `summary.json`, `k6-summary.txt`, 결과 README를 기준으로 주요 수치 확인
- README와 종합 문서의 링크 대상 파일 존재 여부 확인

## 관련 테스트

- `infra/load/results/local-read-matrix-20260512-102647/*/summary.json`
- `infra/load/results/local-mixed-peak-20260517-043551/mixed-peak/summary.json`
- `infra/load/results/local-soak-20260517-055109/soak-steady/summary.json`
- `infra/load/results/local-mixed-fixed-token-20260530-121407/mixed-peak/summary.json`
- `infra/load/results/local-mixed-fixed-token-20260531-005152/mixed-peak/summary.json`
- `infra/load/results/local-soak-fixed-token-20260531-022322/soak-steady/summary.json`
