# CodeQL Multi-Language Analysis Guide

This project uses CodeQL to analyze code in multiple languages:
- **Java/Kotlin** (Android app)
- **Python** (DCS scripts)

## GitHub Online Scan

The GitHub workflow (`.github/workflows/codeql.yml`) automatically creates **separate databases** for each language using a **matrix strategy**:

```yaml
matrix:
  include:
  - language: actions
    build-mode: none
  - language: java-kotlin
    build-mode: manual
  - language: python
    build-mode: none
```

This runs **3 parallel jobs** on GitHub Actions, each creating its own database and uploading results separately.

### How it works:
1. Each language gets its own job (runner)
2. CodeQL creates a database for that specific language
3. Analysis runs independently
4. Results are uploaded to GitHub Security tab

## Local CodeQL Analysis

### Option 1: Automated Script (Recommended)

Run the provided batch script:

```bash
run_codeql_analysis.bat
```

This will:
1. Clean previous databases
2. Create Java/Kotlin database (compiles Android app)
3. Create Python database (scans scripts folder)
4. Download CodeQL query packs (first run only)
5. Analyze both databases
6. Generate SARIF reports

### Option 2: Manual Commands

**Java/Kotlin (Android):**
```bash
# Use Debug build (Release build may have compilation errors)
codeql database create codeql-db-java --language=java-kotlin --command="gradlew.bat assembleDebug --no-daemon"

# Download query pack (first time only)
codeql pack download codeql/java-queries

# Analyze with explicit query pack
codeql database analyze codeql-db-java codeql/java-queries --format=sarif-latest --output=java-analysis.sarif
```

**Python (Scripts):**
```bash
codeql database create codeql-db-python --language=python --source-root=scripts

# Download query pack (first time only)
codeql pack download codeql/python-queries

# Analyze with explicit query pack
codeql database analyze codeql-db-python codeql/python-queries --format=sarif-latest --output=python-analysis.sarif
```

## IDE Integration

### VS Code CodeQL Extension

1. Install the CodeQL extension
2. Run `run_codeql_analysis.bat` to create databases
3. In VS Code, select **CodeQL: Choose Database**
4. Select either:
   - `codeql-db-java` for Android analysis
   - `codeql-db-python` for Python scripts

You can only analyze **one language at a time** in the IDE, but you can switch between databases.

## Why Separate Databases?

CodeQL requires separate databases for each language because:
- Different languages have different AST (Abstract Syntax Tree) structures
- Each language needs specific extractors and libraries
- Analysis queries are language-specific

The **GitHub online scan handles this automatically** with matrix jobs.
For **local/IDE analysis**, you must create separate databases.

## Output Files

After running analysis:
- `java-analysis.sarif` - Java/Kotlin security findings
- `python-analysis.sarif` - Python security findings
- `codeql-db-java/` - Java/Kotlin database (can be reused)
- `codeql-db-python/` - Python database (can be reused)

All output files are git-ignored (see `.gitignore` lines 57-66).

## Troubleshooting

### "CodeQL not found"
Install CodeQL CLI: https://github.com/github/codeql-cli-binaries/releases

### "Query pack cannot be found"
The query packs are downloaded automatically by the script. If manual download is needed:
```bash
codeql pack download codeql/java-queries
codeql pack download codeql/python-queries
```

### Java/Kotlin build fails
Make sure you can build the project with:
```bash
gradlew.bat assembleDebug
```

If Release build fails, CodeQL analysis uses Debug build by default (which is sufficient for security analysis).

### Python analysis empty
Ensure Python files exist in `scripts/` directory.

## Resources

- [CodeQL Documentation](https://codeql.github.com/docs/)
- [Multi-language analysis](https://docs.github.com/en/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/customizing-your-advanced-setup-for-code-scanning)
- [CodeQL CLI](https://codeql.github.com/docs/codeql-cli/)
