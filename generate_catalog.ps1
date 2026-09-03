$ErrorActionPreference = 'Stop'

$dataDir = Join-Path $PSScriptRoot "data"
$assetsDir = Join-Path $PSScriptRoot "app\src\main\assets"
New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null

$streamingPath = Join-Path $dataDir "streaming_links.json"
if (-not (Test-Path $streamingPath)) {
    Write-Host "WARN: $streamingPath assente (scrape precedente bloccato?). Niente da generare, esco senza fallire."
    exit 0
}
$src = Get-Content -Raw -Encoding UTF8 $streamingPath | ConvertFrom-Json

# Serie extra (loonex ecc.): file separato, unione senza duplicati di slug
$extraPath = Join-Path $dataDir "loonex_links.json"
if (Test-Path $extraPath) {
    try {
        $extra = @(Get-Content -Raw -Encoding UTF8 $extraPath | ConvertFrom-Json)
        if ($extra.Count -gt 0) {
            $slugs = @{}
            foreach ($t in $src) { $slugs[$t.slug] = $true }
            foreach ($e in $extra) {
                if (-not $slugs.ContainsKey($e.slug)) { $src = @($src) + $e }
            }
            Write-Host "Serie extra integrate: $($extra.Count)"
        }
    } catch {
        Write-Host "[WARN] loonex_links.json non valido, ignorato: $($_.Exception.Message)"
    }
}

# Guardia: non pubblicare catalogi vuoti o drasticamente ridotti
$prevCount = 0
$assetPath = Join-Path $assetsDir "catalog.cm"
if (Test-Path $assetPath) {
    try {
        $in = [System.IO.File]::OpenRead($assetPath)
        $gz = New-Object System.IO.Compression.GZipStream($in, [System.IO.Compression.CompressionMode]::Decompress)
        $sr = New-Object System.IO.StreamReader($gz)
        $prev = $sr.ReadToEnd() | ConvertFrom-Json
        $sr.Close()
        $prevCount = @($prev.s).Count
    } catch { $prevCount = 0 }
}
$newCount = @($src).Count
if ($newCount -eq 0 -or ($prevCount -gt 100 -and $newCount -lt [int]($prevCount * 0.7))) {
    # Protezione attiva per scelta: con scrape parziale (es. blocco Cloudflare
    # 403 dopo pagina 1) non sovrascrivere mai il catalogo buono. Esco 0
    # cosi' il workflow resta verde: niente da aggiornare in questo run.
    Write-Host "WARN: catalogo parziale ($newCount titoli contro $prevCount precedenti, probabile blocco 403). Mantengo il precedente, nessun file scritto."
    exit 0
}

$version = [long](Get-Date -Format yyyyMMddHHmm)
$titles = New-Object System.Collections.Generic.List[object]

foreach ($t in $src) {
    # NB: niente $(...) qui: svuoterebbe gli array con un solo elemento
    $cats = @()
    if ($t.categorie_nomi) { $cats = @($t.categorie_nomi) }
    $eps = New-Object System.Collections.Generic.List[object]
    foreach ($ep in $t.episodi) {
        $players = New-Object System.Collections.Generic.List[object]
        foreach ($p in $ep.player) {
            if ($p.url) {
                $players.Add([pscustomobject]@{ n = $p.nome; u = $p.url })
            }
        }
        if ($players.Count -gt 0) {
            $eps.Add([pscustomobject]@{ l = $ep.episodio; p = $players })
        }
    }
    $titles.Add([pscustomobject]@{
        u = $t.slug
        t = $t.titolo
        i = $t.immagine
        c = $cats
        m = $t.modificato
        e = $eps
    })
}

$catalog = [pscustomobject]@{ g = 'cartoonmania'; v = $version; s = $titles }
$jsonPath = Join-Path $dataDir "catalog.json"
$json = $catalog | ConvertTo-Json -Depth 8 -Compress
[System.IO.File]::WriteAllText($jsonPath, $json, (New-Object System.Text.UTF8Encoding($false)))

# Gzip
$bytes = [System.IO.File]::ReadAllBytes($jsonPath)
$msIn = New-Object System.IO.MemoryStream(,$bytes)
$gzipPaths = @((Join-Path $dataDir "catalog.json.gz"), (Join-Path $assetsDir "catalog.cm"))
foreach ($gp in $gzipPaths) {
    $fs = [System.IO.File]::Create($gp)
    $gz = New-Object System.IO.Compression.GZipStream($fs, [System.IO.Compression.CompressionLevel]::Optimal)
    $msIn.CopyTo($gz)
    $msIn.Position = 0
    $gz.Dispose(); $fs.Dispose()
}
$msIn.Dispose()

Set-Content -Path (Join-Path $dataDir "catalog.version.txt") -Value "$version" -Encoding ASCII
$gzSize = [math]::Round((Get-Item (Join-Path $dataDir "catalog.json.gz")).Length / 1MB, 1)
Write-Host "Catalogo generato: $($titles.Count) titoli, versione $version, gz: $gzSize MB"
