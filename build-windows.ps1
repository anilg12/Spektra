# ============================================================
#  SPEKTRA - Windows paketleme betigi
#  Gereksinim: JDK 21 (jpackage dahil) + Maven, ilk derlemede internet.
#  Kullanim (PowerShell):  .\build-windows.ps1
# ============================================================
$ErrorActionPreference = "Stop"

Write-Host "==> Ozel calisma zamani olusturuluyor (jlink)..." -ForegroundColor Cyan
mvn -q clean javafx:jlink
if (!(Test-Path target/spektra-runtime)) { throw "jlink imaji bulunamadi (target/spektra-runtime)" }

Write-Host "==> Bagimsiz uygulama imaji paketleniyor (jpackage)..." -ForegroundColor Cyan
if (Test-Path dist) { Remove-Item dist -Recurse -Force }

jpackage `
  --type app-image `
  --name SPEKTRA `
  --app-version 2.0.0 `
  --runtime-image target/spektra-runtime `
  --module com.anilgul.spektra/com.anilgul.spektra.App `
  --dest dist `
  --vendor "Anil Gul" `
  --description "SPEKTRA - Elektronik Harp Spektrum Analiz Platformu"

Write-Host ""
Write-Host "TAMAM." -ForegroundColor Green
Write-Host "Bagimsiz uygulama:  dist\SPEKTRA\SPEKTRA.exe" -ForegroundColor Green
Write-Host "dist\SPEKTRA klasorunu ziplemen yeterli; Java kurulu olmayan her Windows 10/11'de calisir." -ForegroundColor Green
Write-Host ""
Write-Host "Cift tiklamali .msi kurulum istersen (istege bagli) WiX Toolset kurup su komutu calistir:" -ForegroundColor DarkGray
Write-Host '  jpackage --type msi --name SPEKTRA --app-version 2.0.0 --runtime-image target/spektra-runtime --module com.anilgul.spektra/com.anilgul.spektra.App --dest dist --win-shortcut --win-menu --vendor "Anil Gul"' -ForegroundColor DarkGray
