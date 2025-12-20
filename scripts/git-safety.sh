#!/usr/bin/env bash
# scripts/git-safety.sh
# Usage: Called by .githooks/pre-push. Runs CodeQL analysis.
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

# This script no longer stages or commits changes.
# It is now a dedicated scanner that should be called from a pre-push hook
# that has already determined a scan is necessary.

# ---- Secrets scan ----
# Secret-scanning has been disabled per request — only running CodeQL now.
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

    # The '|| true' is intentional. codeql analyze exits with 2 if it finds issues,
    # which would otherwise terminate the script. We want the Python script below to
    # parse the results and decide if the exit code should be 1.
    codeql database analyze codeql-db --format=sarif-latest --output=codeql-results.sarif codeql/python-queries -j 0 || true

    if python - <<'PY'
import json, sys
try:
    with open('codeql-results.sarif') as f:
        d = json.load(f)
except FileNotFoundError:
    print('[git-safety] Error: codeql-results.sarif not found. The analysis may have failed.', file=sys.stderr)
    sys.exit(1)
except json.JSONDecodeError:
    print('[git-safety] Error: Could not decode codeql-results.sarif. It may be empty or corrupt.', file=sys.stderr)
    sys.exit(1)

runs = d.get('runs', [])
count = 0
if runs:
    count = len(runs[0].get('results', []))

if count > 0:
    print(f'[git-safety] CodeQL found {count} issue(s). See codeql-results.sarif for details.')
    sys.exit(1)

sys.exit(0)
PY
    then
      echo "[git-safety] CodeQL scan passed (0 results)."
    else
      # This block is now reached if the Python script exits with a non-zero status.
      # The Python script already printed a detailed error.
      exit 1
    fi
  else
    echo "[git-safety] CodeQL CLI not found on PATH. Install and ensure 'codeql' is available." >&2
    echo "[git-safety] Aborting push. To bypass locally, set SKIP_CODEQL_SCAN=1 (not recommended)." >&2
    if [ "${SKIP_CODEQL_SCAN:-0}" != "1" ]; then
      exit 1
    fi
  fi
fi

# All checks passed
echo "[git-safety] All checks passed."
exit 0
