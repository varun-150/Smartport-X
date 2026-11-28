<#
.SYNOPSIS
    Fails a production deployment when well-known development defaults are still in use.

.DESCRIPTION
    Application-level fail-fast already covers the JWT keystore and demo seeders when the
    'prod' Spring profile is active. This script is the deployment-time companion: it
    checks the environment that will be handed to `docker compose` / the runtime and
    refuses to continue if insecure development defaults remain.

    SECURITY: only VARIABLE NAMES are ever printed. Values are never echoed or logged.

.PARAMETER Profile
    Effective Spring profile. Defaults to $env:SPRING_PROFILES_ACTIVE.

.PARAMETER Environment
    Optional hashtable of environment values (used for self-tests). Defaults to the
    real process environment.

.PARAMETER ConfigJson
    Optional JSON object of environment values. Convenient for CI / `-File` invocation
    where a hashtable cannot be passed directly. Merged over the real environment.

.EXAMPLE
    ./scripts/validate-production-env.ps1
    ./scripts/validate-production-env.ps1 -Environment @{ DB_PASSWORD = 'weak' } -Profile prod
#>
[CmdletBinding()]
param(
    [string]$Profile = $env:SPRING_PROFILES_ACTIVE,
    [hashtable]$Environment,
    [string]$ConfigJson
)

$ErrorActionPreference = 'Stop'

if ($ConfigJson) {
    if (-not $Environment) { $Environment = @{} }
    $fromJson = $ConfigJson | ConvertFrom-Json
    foreach ($prop in $fromJson.PSObject.Properties) {
        $Environment[$prop.Name] = [string]$prop.Value
    }
}

if (-not $Environment) {
    $Environment = @{}
    foreach ($entry in [System.Environment]::GetEnvironmentVariables().GetEnumerator()) {
        $Environment[[string]$entry.Key] = [string]$entry.Value
    }
}

function Get-Env([string]$name) {
    if ($Environment.ContainsKey($name)) { return [string]$Environment[$name] }
    return ''
}

# Known development-only default values. Matching any of these in prod is a hard failure.
$devDefaults = @{
    'DB_PASSWORD'                            = @('smartportx_local', 'smartportx', 'postgres', 'password', 'change_me', '')
    'RABBITMQ_PASSWORD'                      = @('guest', 'password', 'change_me', '')
    'JWT_SECRET'                             = @('change_me_to_a_long_random_string_at_least_32_chars', 'change_me', 'secret', '')
    'SMARTPORTX_EUREKA_SECURITY_PASSWORD'    = @('admin', 'password', 'change_me', '')
    'GRAFANA_PASSWORD'                       = @('admin', 'password', 'change_me', '')
}

$failures = [System.Collections.Generic.List[string]]::new()

if ([string]::IsNullOrWhiteSpace($Profile)) {
    Write-Host 'PROD_ENV_VALIDATION=SKIPPED (SPRING_PROFILES_ACTIVE is not set; assuming non-prod)'
    exit 0
}

$isProd = ($Profile -split ',' | ForEach-Object { $_.Trim() }) -contains 'prod'
if (-not $isProd) {
    Write-Host "PROD_ENV_VALIDATION=SKIPPED (profile='$Profile' is not prod)"
    exit 0
}

foreach ($name in $devDefaults.Keys | Sort-Object) {
    $value = Get-Env $name
    if ($devDefaults[$name] -contains $value) {
        $failures.Add("INSECURE_DEFAULT: $name is set to a known development default")
    }
}

# JWT signing key must be long enough even when it is not a known default.
$jwt = Get-Env 'JWT_SECRET'
if ($jwt.Length -gt 0 -and $jwt.Length -lt 32) {
    $failures.Add('WEAK_VALUE: JWT_SECRET is shorter than 32 characters')
}

# Demo/testimonial seed data must never be enabled in production.
foreach ($flag in @('SEED_DEMO_USERS', 'SEED_TESTIMONIALS')) {
    $v = (Get-Env $flag).Trim().ToLowerInvariant()
    if ($v -eq 'true' -or $v -eq '1' -or $v -eq 'yes') {
        $failures.Add("INSECURE_DEFAULT: $flag must be false in prod (app seeder also blocks this)")
    }
}

# Trusted-proxy network must be explicitly narrowed for production ingress.
$trusted = Get-Env 'SMARTPORTX_TRUSTED_PROXY_NETWORKS'
if ([string]::IsNullOrWhiteSpace($trusted) -or $trusted -eq '172.16.0.0/12') {
    $failures.Add('INSECURE_DEFAULT: SMARTPORTX_TRUSTED_PROXY_NETWORKS must be set to your real ingress CIDRs')
}

if ($failures.Count -gt 0) {
    Write-Host 'PROD_ENV_VALIDATION=FAILED'
    foreach ($f in $failures) { Write-Host "  - $f" }
    Write-Host 'Remediate the named variables (never commit values) and re-run before deploying.'
    exit 1
}

Write-Host 'PROD_ENV_VALIDATION=PASSED (no insecure development defaults detected)'
exit 0
