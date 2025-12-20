# .githooks/pre-push.ps1
# PowerShell variant of pre-push hook for Git on Windows.
param($remoteName, $remoteUrl)

$root = git rev-parse --show-toplevel
Set-Location $root

# --- Determine if a scan is needed ---
# This hook will check if any .py files are part of the changes being pushed.
# The heavy CodeQL scan will only run if Python code is affected.

$python_files_changed = $false
foreach ($line in $input) {
    $parts = $line.Split(' ')
    $local_ref = $parts[0]
    $local_sha = $parts[1]
    $remote_ref = $parts[2]
    $remote_sha = $parts[3]

    # If the remote ref is all zeroes, this is a new branch.
    $range = if ($remote_sha -match '^0+$') {
        # In a new branch, we check all commits that are not on any remote.
        # A simple approach is to find a merge base with a default branch.
        $default_branch_raw = git symbolic-ref "refs/remotes/origin/HEAD"
        $default_branch = if ($LASTEXITCODE -eq 0) { $default_branch_raw.Replace("refs/remotes/origin/", "") } else { "" }
        if (-not $default_branch) {
            # Fallback to main or master if origin/HEAD is not set
            if (git show-ref --verify --quiet refs/heads/main) {
                $default_branch = "main"
            } elseif (git show-ref --verify --quiet refs/heads/master) {
                $default_branch = "master"
            } else {
                Write-Host "[git-safety] Could not determine a default branch to compare against. Running full scan."
                $python_files_changed = $true
                break
            }
        }
        "$default_branch..$local_sha"
    } else {
        # This is an update to an existing branch.
        "$remote_sha..$local_sha"
    }

    # Handle branch deletion.
    if ($local_ref -eq '(delete)') {
        Write-Host "[git-safety] A branch is being deleted, no checks needed."
        continue
    }

    # Get the list of changed files in the commit range.
    $changed_files = git diff-tree --no-commit-id --name-only -r $range
    
    if ($changed_files | Select-String -Quiet -Pattern '\.py$') {
        $python_files_changed = $true
        break # Found a python file, no need to check other refs.
    }
}

if ($python_files_changed) {
    Write-Host "[git-safety] Python files were changed. Running security scan..."
    & "$root\scripts\git-safety.ps1"
    exit $LASTEXITCODE
} else {
    Write-Host "[git-safety] No Python files were changed. Skipping security scan."
    exit 0
}
