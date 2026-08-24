$ErrorActionPreference = 'Stop'

$dataDir = Join-Path $PSScriptRoot "data"
$assetsDir = Join-Path $PSScriptRoot "app\src\main\assets"
New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null

$src = Get-Content -Raw (Join-Path $dataDir "streaming_links.json") | ConvertFrom-Json

$version = [long](Get-Date -Format yyyyMMddHHmm)
$titles = New-Object System.Collections.Generic.List[object]

foreach ($t in $src) {
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
$gzipPaths = @((Join-Path $dataDir "catalog.json.gz"), (Join-Path $assetsDir "catalog.json.gz"))
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
