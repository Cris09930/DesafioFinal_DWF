# Inicia Keycloak
# Uso (desde la carpeta ventas-api):
#   powershell -ExecutionPolicy Bypass -File keycloak\iniciar-keycloak.ps1 -KeycloakDir "C:\keycloak-26.4.0"
param(
    [Parameter(Mandatory = $true)][string]$KeycloakDir
)

$ErrorActionPreference = "Stop"
$proyecto = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $proyecto ".env"

if (-not (Test-Path "$KeycloakDir\bin\kc.bat")) { throw "No se encontró $KeycloakDir\bin\kc.bat. Revise la ruta de Keycloak." }
if (-not (Test-Path $envFile)) { throw "No existe $envFile. Copie .env.example como .env y complete los valores." }

# 1. Cargar variables del .env en esta sesión (Keycloak las usa en el realm: ${KC_DEMO_PASSWORD}, etc.)
Get-Content $envFile | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim(), "Process")
}
foreach ($req in "KC_ADMIN_PASSWORD", "KC_SERVICE_CLIENT_SECRET", "KC_DEMO_PASSWORD") {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($req))) { throw "Falta $req en .env" }
}
$env:KC_BOOTSTRAP_ADMIN_USERNAME = if ($env:KC_ADMIN_USER) { $env:KC_ADMIN_USER } else { "admin" }
$env:KC_BOOTSTRAP_ADMIN_PASSWORD = $env:KC_ADMIN_PASSWORD

# 2. Java 21: si JAVA_HOME no está definido, usar el JDK que descargó IntelliJ
if (-not $env:JAVA_HOME) {
    # Prefiere un JDK 21; si no hay, usa el más reciente que tenga IntelliJ
    $jdks = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path "$($_.FullName)\bin\java.exe" }
    $jdk = ($jdks | Where-Object { $_.Name -match '(^|[^0-9])21([^0-9]|$)' } | Select-Object -First 1)
    if (-not $jdk) { $jdk = $jdks | Sort-Object Name -Descending | Select-Object -First 1 }
    if (-not $jdk) { throw "No se encontró un JDK. Defina JAVA_HOME o descargue el JDK 21 desde IntelliJ." }
    $env:JAVA_HOME = $jdk.FullName
}
Write-Host "Usando Java: $env:JAVA_HOME"

# 3. Copiar el realm para que se importe al arrancar
New-Item -ItemType Directory -Force "$KeycloakDir\data\import" | Out-Null
Copy-Item (Join-Path $PSScriptRoot "dwf404-realm.json") "$KeycloakDir\data\import\" -Force

# 4. Arrancar en modo desarrollo en el puerto 8180
Write-Host "Iniciando Keycloak en http://localhost:8180 ... (Ctrl+C para detener)"
& "$KeycloakDir\bin\kc.bat" start-dev --http-port=8180 --import-realm
