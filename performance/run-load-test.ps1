$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$pcapPath = Join-Path $PSScriptRoot 'swiftpay-load-test.pcap'

Write-Host 'Starting SwiftPay services...'
docker compose up -d --build

Write-Host 'Waiting for app startup...'
Start-Sleep -Seconds 25

Write-Host 'Starting packet capture...'
$containerId = docker run -d --rm --privileged --network host -v "${repoRoot}:/work" nicolaka/netshoot:latest sh -c "tcpdump -i any -w /work/performance/swiftpay-load-test.pcap -s 0 'port 8081 or port 8082 or port 5432 or port 6379 or port 9092'"

Write-Host 'Running k6 load test against the payment API...'
docker run --rm -v "${repoRoot}:/work" --add-host=host.docker.internal:host-gateway -w /work grafana/k6:latest run /work/performance/load-test.js

Write-Host 'Stopping packet capture...'
docker stop $containerId | Out-Null

if (Test-Path $pcapPath) {
    Write-Host "PCAP saved at: $pcapPath"
} else {
    Write-Warning 'PCAP was not created. Check docker/network access and re-run the script.'
}
