# .githooks/pre-push.ps1
# PowerShell variant of pre-push hook for Git on Windows.
param($remoteName, $remoteUrl)
$root = (git rev-parse --show-toplevel) -replace '\\','\\'
Set-Location $root
& "$root\.githooks\scripts\git-safety.ps1" @args
exit $LASTEXITCODE
