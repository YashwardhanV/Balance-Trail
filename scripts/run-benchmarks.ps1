param(
    [int[]]$Sizes = @(1000, 10000, 100000),
    [ValidateRange(1, 10)][int]$Runs = 3,
    [string]$Username = "analyst",
    [string]$Password = "change-me-now",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$DatabaseUser = "balancetrail",
    [string]$DatabaseName = "balancetrail"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectRoot "benchmark-data"
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

$health = curl.exe --silent --show-error --fail "$BaseUrl/actuator/health"
if (-not $health) { throw "Backend health endpoint is unavailable at $BaseUrl" }

$databaseContainer = docker compose -f (Join-Path $projectRoot "docker-compose.yml") ps -q db
if (-not $databaseContainer) { throw "The Compose PostgreSQL service is not running" }

$results = @()
foreach ($size in $Sizes) {
    foreach ($runNumber in 1..$Runs) {
        $prefix = "BENCH-$size-R$runNumber-$(Get-Date -Format 'yyyyMMddHHmmssfff')"
        $manifestJson = docker run --rm -v "${projectRoot}:/workspace" -w /workspace python:3.13-alpine python scripts/generate_benchmark_data.py --records $size --prefix $prefix --output-dir benchmark-data
        $manifest = $manifestJson | ConvertFrom-Json
        $gatewayPath = if ([System.IO.Path]::IsPathRooted($manifest.gateway)) { $manifest.gateway } else { Join-Path $projectRoot $manifest.gateway }
        $ledgerPath = if ([System.IO.Path]::IsPathRooted($manifest.ledger)) { $manifest.ledger } else { Join-Path $projectRoot $manifest.ledger }

        $containerCsv = "/tmp/ledger-$prefix.csv"
        docker cp $ledgerPath "${databaseContainer}:$containerCsv" | Out-Null
        $copySql = "COPY ledger_transaction (transaction_ref, account_number, amount, transaction_date, description) FROM '$containerCsv' WITH (FORMAT csv, HEADER true)"
        docker compose -f (Join-Path $projectRoot "docker-compose.yml") exec -T db psql -v ON_ERROR_STOP=1 -U $DatabaseUser -d $DatabaseName -c $copySql | Out-Null

        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        $response = curl.exe --silent --show-error --fail -u "${Username}:$Password" -F "file=@$gatewayPath" "$BaseUrl/reconciliations" | ConvertFrom-Json
        $runId = $response.reconciliation.id

        do {
            Start-Sleep -Milliseconds 200
            $run = curl.exe --silent --show-error --fail -u "${Username}:$Password" "$BaseUrl/reconciliations/$runId" | ConvertFrom-Json
        } while ($run.status -in @("PENDING", "RUNNING"))
        $stopwatch.Stop()

        $replayWatch = [System.Diagnostics.Stopwatch]::StartNew()
        $replay = curl.exe --silent --show-error --fail -u "${Username}:$Password" -F "file=@$gatewayPath" "$BaseUrl/reconciliations" | ConvertFrom-Json
        $replayWatch.Stop()

        $elapsedSeconds = [Math]::Round($stopwatch.Elapsed.TotalSeconds, 3)
        $batchSeconds = [Math]::Round((([DateTimeOffset]::Parse($run.finishedAt)) - ([DateTimeOffset]::Parse($run.startedAt))).TotalSeconds, 3)
        $results += [PSCustomObject]@{
            size = $size
            runNumber = $runNumber
            runId = $runId
            status = $run.status
            processingSeconds = $batchSeconds
            endToEndSeconds = $elapsedSeconds
            recordsPerSecond = if ($batchSeconds -gt 0) { [Math]::Round($run.totalCount / $batchSeconds, 2) } else { 0 }
            matched = $run.matchedCount
            amountMismatch = $run.amountMismatchCount
            missing = $run.missingCount
            invalid = $run.invalidCount
            duplicate = $run.duplicateCount
            replayMilliseconds = [Math]::Round($replayWatch.Elapsed.TotalMilliseconds, 2)
            replayReturnedSameRun = $replay.idempotentReplay -and $replay.reconciliation.id -eq $runId
        }
    }
}

$resultPath = Join-Path $outputDirectory "results-$(Get-Date -Format 'yyyyMMdd-HHmmss').json"
$results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $resultPath -Encoding utf8
Write-Output "Benchmark results written to $resultPath"
$results | Format-Table -AutoSize
