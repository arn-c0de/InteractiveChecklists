#!/usr/bin/env bash
# .githooks/scripts/git-safety.sh
# Same behavior as scripts/git-safety.sh but living inside .githooks/scripts
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

echo "[git-safety] Staging all changes..."
git add .

if git diff --cached --quiet; then
  echo "[git-safety] No staged changes to commit."
else
  MSG="${1:-Automated commit by git-safety}"
  echo "[git-safety] Committing: $MSG"
  git commit -m "$MSG"
fi

# ---- Secrets scan ----
# Secret-scanning has been disabled per request — only running CodeQL now.
# If you want to re-enable secrets scanning, restore detect-secrets usage here or set SKIP_SECRET_SCAN to 0 and install detect-secrets.
echo "[git-safety] Secret-scan disabled; proceeding to CodeQL scan."

# ---- CodeQL scan ----
if [ "${SKIP_CODEQL_SCAN:-0}" = "1" ]; then
  echo "[git-safety] SKIP_CODEQL_SCAN=1 -> skipping CodeQL scan"
else
  if command -v codeql >/dev/null 2>&1; then
    echo "[git-safety] Running CodeQL (this can be slow)..."
    # Create database if missing
    if [ ! -d codeql-db ] || [ ! -f codeql-db/codeql-database.yml ]; then
      echo "[git-safety] Creating CodeQL database (first run, may take a while)..."
      codeql database create codeql-db --language=python --source-root=. --overwrite
    fi

    codeql database analyze codeql-db --format=sarif-latest --output=codeql-results.sarif codeql/python-queries -j 0 || true

    if python - <<'PY'
import json,sys
try:
    d=json.load(open('codeql-results.sarif'))
except Exception:
    print('No codeql-results.sarif produced')
    sys.exit(1)
runs=d.get('runs',[])
count=0
if runs:
    count=len(runs[0].get('results',[]))
if count>0:
    print(f'CodeQL found {count} issue(s)')
    sys.exit(1)
sys.exit(0)
PY
    then
      echo "[git-safety] CodeQL scan passed (0 results)."
    else
      echo "[git-safety] CodeQL found issues. See codeql-results.sarif"
      exit 1
    fi
  else
    echo "[git-safety] CodeQL CLI not found on PATH. Install and ensure 'codeql' is available."
    echo "[git-safety] Aborting push. To bypass locally, set SKIP_CODEQL_SCAN=1 (not recommended)."
    if [ "${SKIP_CODEQL_SCAN:-0}" != "1" ]; then
      exit 1
    fi
  fi
fi

# All checks passed
echo "[git-safety] All checks passed — proceeding to push."
exit 0
