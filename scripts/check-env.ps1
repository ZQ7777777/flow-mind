$ErrorActionPreference = "Continue"

Write-Host "Checking Flow Mind platform toolchain..."
Write-Host ""

Write-Host "[Java]"
java -version
Write-Host ""

Write-Host "[Maven]"
mvn -version
Write-Host ""

Write-Host "[SQLite CLI]"
$sqlite = Get-Command sqlite3 -ErrorAction SilentlyContinue
if ($sqlite) {
    sqlite3 --version
} else {
    Write-Host "sqlite3 not found in PATH. Install SQLite tools manually if you need CLI database inspection."
}

Write-Host ""
Write-Host "[Node.js for Agent Web]"
$node = Get-Command node -ErrorAction SilentlyContinue
if ($node) {
    node --version
    Write-Host "Agent Web requires Node.js >= 22.19.0."
} else {
    Write-Host "node not found in PATH. Agent Web requires Node.js >= 22.19.0."
}
