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

# Serie extra (loonex, archive.org, ecc.): file separati, unione senza duplicati di slug.
# NB: niente hashtable per gli slug (in Windows PowerShell 5.1 ConvertFrom-Json
# emette l'array come singolo oggetto non enumerato e il lookup hashtable salta
# le collisioni: run 2026-09-11 produsse un duplicato 't-u-f-f-puppy').
# Confronto stringa esplicito; a parita' di slug VINCE l'extra (fonte diretta,
# piu' fresca: es. T.U.F.F. Puppy loonex 2026/57ep vs toonitalia 2024/54ep).
$srcList = New-Object System.Collections.Generic.List[object]
foreach ($t in $src) {
    if ($t -is [array]) { foreach ($x in $t) { $srcList.Add($x) } }
    else { $srcList.Add($t) }
}
foreach ($extraPath in @((Join-Path $dataDir "loonex_links.json"), (Join-Path $dataDir "archive_links.json"), (Join-Path $dataDir "animetop_links.json"))) {
if (Test-Path $extraPath) {
    try {
        $extraRaw = @(Get-Content -Raw -Encoding UTF8 $extraPath | ConvertFrom-Json)
        # Appiattisci: se un elemento e' a sua volta un array (non enumerato), srotolalo
        $extra = New-Object System.Collections.Generic.List[object]
        foreach ($er in $extraRaw) {
            if ($er -is [array]) { foreach ($x in $er) { $extra.Add($x) } }
            else { $extra.Add($er) }
        }
        $added = 0; $replaced = 0
        if ($extra.Count -gt 0) {
            foreach ($e in $extra) {
                $eslug = [string]$e.slug
                $idx = -1
                for ($i = 0; $i -lt $srcList.Count; $i++) {
                    if ([string]$srcList[$i].slug -eq $eslug) { $idx = $i; break }
                }
                if ($idx -ge 0) { $srcList[$idx] = $e; $replaced++ }
                else { $srcList.Add($e); $added++ }
            }
            Write-Host "Serie extra integrate da $(Split-Path $extraPath -Leaf): $($extra.Count) (nuove: $added, sostituite: $replaced)"
        }
    } catch {
        Write-Host "[WARN] $(Split-Path $extraPath -Leaf) non valido, ignorato: $($_.Exception.Message)"
    }
}
}
$src = $srcList

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
$newCount = $srcList.Count
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
