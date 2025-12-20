<# scripts/git-safety.ps1
   PowerShell variant for Windows users.
   Usage: Called by .githooks\pre-push.ps1 or run manually.
#>
$ErrorActionPreference = 'Stop'

$root = git rev-parse --show-toplevel
Set-Location $root

# This script no longer stages or commits changes.
# It is now a dedicated scanner that should be called from a pre-push hook
# that has already determined a scan is necessary.

# Secrets scan
# Secret-scanning has been disabled per request — only running CodeQL now.
Write-Host "[git-safety] Secret-scan disabled; proceeding to CodeQL scan."

# CodeQL scan
if ($env:SKIP_CODEQL_SCAN -eq '1') {
    Write-Host "[git-safety] SKIP_CODEQL_SCAN=1 -> skipping CodeQL scan"
} else {
    if (Get-Command codeql -ErrorAction SilentlyContinue) {
        Write-Host "[git-safety] Running CodeQL (can be slow)..."
        if (-not (Test-Path codeql-db\codeql-database.yml)) {
            codeql database create codeql-db --language=python --source-root=. --overwrite
        }
        
        # We use -ErrorAction Continue because codeql.exe exits with a non-zero status code (2)
        # when it finds results, which would otherwise be a terminating error.
        codeql database analyze codeql-db --format=sarif-latest --output=codeql-results.sarif codeql/python-queries -j 0 -ErrorAction Continue
        
        $sarif = $null
        try {
            $sarif = Get-Content .\codeql-results.sarif -Raw | ConvertFrom-Json
        } catch [System.IO.FileNotFoundException] {
            Write-Host "[git-safety] Error: codeql-results.sarif not found. The analysis may have failed." -ForegroundColor Red
            exit 1
        } catch { # Catches other errors like JSON parsing
            Write-Host "[git-safety] Error: Could not parse codeql-results.sarif. It may be empty or corrupt." -ForegroundColor Red
            exit 1
        }

        $count = $sarif.runs[0].results.Count
        if ($count -gt 0) {
            Write-Host "[git-safety] CodeQL found $count issue(s) — see codeql-results.sarif" -ForegroundColor Red
            exit 1
        } else {
            Write-Host "[git-safety] CodeQL scan passed (0 results)."
        }
    } else {
        Write-Host "[git-safety] CodeQL CLI not found. Install and ensure 'codeql' is on PATH" -ForegroundColor Yellow
        if (-not ($env:SKIP_CODEQL_SCAN -eq '1')) { exit 1 }
    }
}

Write-Host "[git-safety] All checks passed."
exit 0
