#!/usr/bin/env bash
# Chạy toàn bộ UAT NC/CAPA: API chức năng + E2E Playwright.
#   ./run-uat.sh                 # dùng mặc định local API + public E2E
#   UAT_BASE=... ./run-uat.sh
set -uo pipefail
cd "$(dirname "$0")"

RUN_TAG="$(date +%Y%m%d-%H%M%S)"
export UAT_RUN="${UAT_RUN:-$RUN_TAG}"
mkdir -p results

echo "=============================================================="
echo " UAT NC/CAPA — run $UAT_RUN"
echo " API : ${UAT_BASE:-http://127.0.0.1:3110/nc-capa}"
echo " E2E : ${UAT_ORIGIN:-https://tienhv.io.vn}${UAT_ROOT:-/nc-capa}"
echo "=============================================================="

echo
echo ">>> [1/2] Kiểm thử chức năng qua API"
node api/uat-api.mjs
API_RC=$?

echo
echo ">>> [2/2] E2E Playwright"
if [ ! -d e2e/node_modules ]; then
  ( cd e2e && npm install --silent && npx playwright install chromium )
fi
( cd e2e && npx playwright test --reporter=list )
E2E_RC=$?

echo
echo "=============================================================="
echo " KẾT QUẢ: API=$([ $API_RC -eq 0 ] && echo PASS || echo FAIL)  E2E=$([ $E2E_RC -eq 0 ] && echo PASS || echo FAIL)"
echo " Chi tiết: uat/results/  ·  Kế hoạch & lỗi: uat/UAT-NC-CAPA.md"
echo "=============================================================="

[ $API_RC -eq 0 ] && [ $E2E_RC -eq 0 ]
