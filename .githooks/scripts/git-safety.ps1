<# .githooks\scripts\git-safety.ps1
   PowerShell variant for Windows users.
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
if (Get-Command detect-secrets -ErrorAction SilentlyContinue) {
    Write-Host "[git-safety] Running detect-secrets scan..."
    detect-secrets scan --all-files --json > .detect_secrets_scan.json
    $json = Get-Content .detect_secrets_scan.json | ConvertFrom-Json
    if ($json.results.Keys.Count -gt 0) {
        Write-Host "[git-safety] Potential secrets found! Review .detect_secrets_scan.json" -ForegroundColor Red
        exit 1
    } else {
        Write-Host "[git-safety] No potential secrets found."
    }
} else {
    Write-Host "[git-safety] detect-secrets not found. Install with: pip install detect-secrets" -ForegroundColor Yellow
    if (-not ($env:SKIP_SECRET_SCAN -eq '1')) { exit 1 }
}

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
