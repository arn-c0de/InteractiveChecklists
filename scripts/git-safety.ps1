<# scripts/git-safety.ps1
   PowerShell variant for Windows users.
   Usage: Called by .githooks\pre-push.ps1 or run manually.
#>
param([string[]]$CommitArgs)
$ErrorActionPreference = 'Stop'

$root = git rev-parse --show-toplevel
Set-Location $root

Write-Host "[git-safety] Staging all changes..."
git add .
if (-not (git diff --cached --quiet)) {
    $msg = if ($CommitArgs) { $CommitArgs -join ' ' } else { 'Automated commit by git-safety' }
    Write-Host "[git-safety] Committing: $msg"
    git commit -m $msg
} else {
    Write-Host "[git-safety] No staged changes to commit."
}

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
        codeql database analyze codeql-db --format=sarif-latest --output=codeql-results.sarif codeql/python-queries -j 0
        $sarif = Get-Content .\codeql-results.sarif | ConvertFrom-Json
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
