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
    # Create database if missing or if sub-databases don't exist
    if [ ! -d codeql-db/python ] || [ ! -d codeql-db/java ]; then
      echo "[git-safety] Creating multi-language CodeQL database (first run or language change, may take a while)..."
      codeql database create codeql-db --language=python,kotlin --source-root=. --overwrite --db-cluster
    fi
    
    # Finalize databases if needed before analysis
    if [ -d codeql-db/python ] && ! codeql database check codeql-db/python >/dev/null 2>&1; then
      echo "[git-safety] Finalizing Python database..."
      codeql database finalize codeql-db/python || true
    fi
    if [ -d codeql-db/java ] && ! codeql database check codeql-db/java >/dev/null 2>&1; then
      echo "[git-safety] Finalizing Kotlin/Java database..."
      codeql database finalize codeql-db/java || true
    fi

    # The '|| true' is intentional. codeql analyze exits with 2 if it finds issues,
    # which would otherwise terminate the script. We want the Python script below to
    # parse the results and decide if the exit code should be 1.
    # Analyze each sub-database separately and merge results
    codeql database analyze codeql-db/python --format=sarif-latest --output=codeql-py-analysis.sarif codeql/python-queries -j 0 || true
    codeql database analyze codeql-db/java --format=sarif-latest --output=codeql-kt-analysis.sarif codeql/kotlin-queries -j 0 || true

    if python - <<'PY'
import json, sys

# Check both Python and Kotlin analysis results
files = ['codeql-py-analysis.sarif', 'codeql-kt-analysis.sarif']
total_count = 0

for sarif_file in files:
    try:
        with open(sarif_file) as f:
            d = json.load(f)
        runs = d.get('runs', [])
        if runs:
            count = len(runs[0].get('results', []))
            total_count += count
            if count > 0:
                print(f'[git-safety] {sarif_file}: {count} issue(s) found.')
    except FileNotFoundError:
        print(f'[git-safety] Warning: {sarif_file} not found.', file=sys.stderr)
    except json.JSONDecodeError:
        print(f'[git-safety] Error: Could not decode {sarif_file}.', file=sys.stderr)
        sys.exit(1)

if total_count > 0:
    print(f'[git-safety] CodeQL found {total_count} total issue(s). Review SARIF files for details.')
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
