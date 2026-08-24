$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$outDir = Join-Path $PSScriptRoot "data\raw"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

# --- 1. Completa download post mancanti ---
$totalPages = 32
for ($p = 1; $p -le $totalPages; $p++) {
    $n = '{0:d3}' -f $p
    $file = Join-Path $outDir "posts_$n.json"
    if (Test-Path $file) { continue }
    $done = $false
    for ($i = 1; $i -le 5 -and -not $done; $i++) {
        try {
            $r = Invoke-WebRequest -Uri "https://toonitalia.xyz/wp-json/wp/v2/posts?per_page=100&page=$p&_embed=wp:term,wp:featuredmedia" -UseBasicParsing -TimeoutSec 180
            $null = $r.Content | ConvertFrom-Json
            Set-Content -Path $file -Value $r.Content -Encoding UTF8
            Write-Host "[OK] posts_$n.json"
            $done = $true
        } catch {
            $code = ''
            if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
            if ($code -eq 400) { Write-Host "[FINE] pagina $p oltre limite"; $done = $true }
            else { Write-Host "[RETRY $i] ($code) pagina $p"; Start-Sleep -Seconds (3 * $i) }
        }
    }
    Start-Sleep -Milliseconds 800
}

# --- 2. Estrazione link di riproduzione ---
$excluded = '^(https?://([^/]+\.)?(toonitalia\.(xyz|org)|wikipedia\.org|animeclick\.it|mymovies\.it|filmtv\.it))'

$results = New-Object System.Collections.Generic.List[object]
$files = Get-ChildItem $outDir -Filter "posts_*.json" | Sort-Object Name
$postCount = 0
$linkCount = 0

foreach ($f in $files) {
    $posts = Get-Content -Raw $f.FullName | ConvertFrom-Json
    foreach ($post in $posts) {
        $postCount++
        $content = $post.content.rendered
        $episodes = New-Object System.Collections.Generic.List[object]
        $epIndex = @{}

        $buf = ''
        $ms = [regex]::Matches($content, '<a\s[^>]*?href=["''](?<url>[^"'']+)["''][^>]*>(?<txt>.*?)</a>', [System.Text.RegularExpressions.RegexOptions]::Singleline)
        $cursor = 0
        foreach ($m in $ms) {
            $between = $content.Substring($cursor, $m.Index - $cursor)
            $segs = [regex]::Split($between, '<br\s*/?>|</p\s*>|<p[\s>]|<h[1-6][^>]*>')
            if ($segs.Count -gt 1) { $buf = '' }
            $vis = [regex]::Replace($segs[$segs.Count - 1], '<[^>]+>', ' ')
            $vis = [System.Net.WebUtility]::HtmlDecode($vis)
            $buf = ($buf + ' ' + $vis).Trim()
            $cursor = $m.Index + $m.Length

            $url = $m.Groups['url'].Value.Trim()
            $anchorTxt = ([regex]::Replace($m.Groups['txt'].Value, '<[^>]+>', '')).Trim()
            if ($url -match $excluded) { continue }
            if ($url -notmatch '^https?://') { continue }

            $label = $buf
            $label = $label.Replace([char]0x00d7, 'x')
            $label = ($label -replace '\s+', ' ').Trim(' ', '-', [char]0x2013, [char]0x2014, ':', '.')
            if ($label.Length -gt 120) { $label = $label.Substring($label.Length - 120) }

            $dom = ([regex]::Match($url, 'https?://([^/]+)')).Groups[1].Value
            $serverName = if ($anchorTxt) { $anchorTxt } else { $dom }

            $entry = $null
            foreach ($e in $episodes) { if ($e.episodio -eq $label) { $entry = $e; break } }
            if (-not $entry) {
                $entry = [pscustomobject]@{ episodio = $label; player = @() }
                $episodes.Add($entry); $epIndex[$label] = $entry
            }
            if (-not ($entry.player | Where-Object { $_.url -eq $url })) {
                $entry.player += [pscustomobject]@{ nome = $serverName; dominio = $dom; url = $url }
                $script:linkCount++
            }
        }

        $results.Add([pscustomobject]@{
            id = $post.id
            titolo = [System.Net.WebUtility]::HtmlDecode($post.title.rendered)
            slug = $post.slug
            url_pagina = $post.link
            immagine = $(if ($post._embedded.'wp:featuredmedia') { $post._embedded.'wp:featuredmedia'[0].source_url } else { $null })
            categorie = $post.categories
            episodi = $episodes
        })
    }
}

# --- 3. Salvataggio output ---
$dataDir = Join-Path $PSScriptRoot "data"
$jsonPath = Join-Path $dataDir "streaming_links.json"
$csvPath  = Join-Path $dataDir "streaming_links.csv"

$json = $results | ConvertTo-Json -Depth 8
[System.IO.File]::WriteAllText($jsonPath, $json, (New-Object System.Text.UTF8Encoding($false)))

$flat = New-Object System.Collections.Generic.List[object]
foreach ($r in $results) {
    foreach ($ep in $r.episodi) {
        foreach ($pl in $ep.player) {
            $flat.Add([pscustomobject]@{
                slug = $r.slug; titolo = $r.titolo; episodio = $ep.episodio
                player = $pl.nome; dominio = $pl.dominio; url_pagina = $r.url_pagina; link_player = $pl.url
            })
        }
    }
}
$flat | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8

Write-Host ""
Write-Host "=== RIEPILOGO ==="
Write-Host "Post elaborati: $postCount"
Write-Host "Voci episodio+player: $($flat.Count)"
Write-Host "JSON: $jsonPath"
Write-Host "CSV: $csvPath"
