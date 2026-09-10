$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$outDir = Join-Path $PSScriptRoot "data\raw"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

# Header browser: senza User-Agent realistico Cloudflare/WAF risponde 403
# agli IP datacenter (runner GitHub Actions), mentre da IP residenziali passa.
$headers = @{
    'User-Agent'      = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'
    'Accept'          = 'application/json'
    'Accept-Language' = 'it-IT,it;q=0.9,en;q=0.8'
    'Referer'         = 'https://toonitalia.xyz/'
}

# Sessione persistente: riusa i cookie Cloudflare tra le richieste.
# Senza sessione ogni Invoke-WebRequest riparte senza cf_clearance e il WAF
# blocca le pagine successive (pagina 1 OK, poi 403 a raffica).
$wpSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# --- 1. Download/refresh pagine WP ---
# FIX "Aggiunti di recente" (2026-09-10): prima le pagine gia' scaricate
# venivano saltate (if Test-Path -> continue). Dato che l'API WP ordina
# per data DESC, un nuovo post sposta tutti gli altri di una posizione:
# saltando le pagine esistenti le nuove serie di ToonItalia non entravano
# MAI nel catalogo. Ora ogni run riscarica tutte le pagine (sovrascrittura)
# e in fase 2 i post vengono deduplicati per id, cosi' un refresh parziale
# (blocco 403 a meta' pagine) non crea doppioni e le novita' emergono
# ordinate per `modificato` nella riga "Aggiunti di recente" dell'app.
$totalPages = 32
$consecutiveBlocked = 0
$abortedByBlock = $false
$refreshedPages = 0
for ($p = 1; $p -le $totalPages; $p++) {
    $n = '{0:d3}' -f $p
    $file = Join-Path $outDir "posts_$n.json"
    $tmpFile = "$file.tmp"
    $done = $false
    for ($i = 1; $i -le 5 -and -not $done; $i++) {
        try {
            $r = Invoke-WebRequest -Uri "https://toonitalia.xyz/wp-json/wp/v2/posts?per_page=100&page=$p&_embed=wp:term,wp:featuredmedia" -UseBasicParsing -TimeoutSec 180 -Headers $headers -WebSession $wpSession
            # Aggiorna il totale pagine dall'header WP (fallback: 32 se assente)
            if ($p -eq 1 -and $r.Headers['X-WP-TotalPages']) {
                try { $totalPages = [int]($r.Headers['X-WP-TotalPages'] | Select-Object -First 1) } catch {}
            }
            $null = $r.Content | ConvertFrom-Json
            Set-Content -Path $tmpFile -Value $r.Content -Encoding UTF8
            Move-Item -Path $tmpFile -Destination $file -Force
            Write-Host "[OK] posts_$n.json (refresh)"
            $done = $true
            $consecutiveBlocked = 0
            $refreshedPages++
        } catch {
            $code = ''
            if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
            if ($code -eq 400) { Write-Host "[FINE] pagina $p oltre limite"; $done = $true }
            elseif ($code -eq 401 -or $code -eq 403 -or $code -eq 429) {
                $consecutiveBlocked++
                Write-Host "[RETRY $i] ($code) pagina $p (blocco $consecutiveBlocked di fila - probabile filtro Cloudflare su IP datacenter)"
                if ($consecutiveBlocked -ge 3) { $abortedByBlock = $true; break }
                # Backoff lungo: il rate-limit Cloudflare scatta con richieste ravvicinate
                Start-Sleep -Seconds (10 * $i)
            }
            else { Write-Host "[RETRY $i] ($code) pagina $p"; Start-Sleep -Seconds (3 * $i) }
        }
    }
    if (Test-Path $tmpFile) { Remove-Item -Force $tmpFile -ErrorAction SilentlyContinue }
    if ($abortedByBlock) { break }
    # Se la pagina ha fallito ma esiste una copia vecchia, la teniamo
    # (sara' deduplicata in fase 2) invece di cancellarla.
    # Pausa anti-rate-limit tra pagine (6-9s con jitter)
    Start-Sleep -Seconds (6 + (Get-Random -Minimum 0 -Maximum 4))
}
Write-Host "Pagine aggiornate in questo run: $refreshedPages su $totalPages"
if ($abortedByBlock) {
    Write-Host "BLOCCO RILEVATO: toonitalia.xyz risponde 403/401/429 in sequenza. Probabile blocco Cloudflare degli IP GitHub Actions. Interrompo i download, uso i dati in cache."
}

# --- 2. Estrazione link di riproduzione ---
$excluded = '^(https?://([^/]+\.)?(toonitalia\.(xyz|org)|wikipedia\.org|animeclick\.it|mymovies\.it|filmtv\.it))'

$results = New-Object System.Collections.Generic.List[object]
$files = Get-ChildItem $outDir -Filter "posts_*.json" | Sort-Object Name
$postCount = 0
$linkCount = 0
$seenIds = @{}
$dupCount = 0

foreach ($f in $files) {
    $posts = Get-Content -Raw -Encoding UTF8 $f.FullName | ConvertFrom-Json
    foreach ($post in $posts) {
        # Deduplica per id: con il refresh di tutte le pagine, un post
        # spostato dalla pagina N alla N+1 (nuova uscita) comparirebbe in
        # due file se il run si interrompe a meta' (blocco 403). Senza
        # questa guardia il catalogo avrebbe doppioni con stesso slug.
        $postId = [string]$post.id
        if ($seenIds.ContainsKey($postId)) { $dupCount++; continue }
        $seenIds[$postId] = $true
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

        $catNames = @()
        if ($post._embedded.'wp:term') {
            $catNames = @($post._embedded.'wp:term'[0] | ForEach-Object { [string]$_.name })
        }

        # Immagine: featuredmedia oppure primo <img> uploads nel contenuto
        $img = $null
        if ($post._embedded.'wp:featuredmedia') {
            $img = $post._embedded.'wp:featuredmedia'[0].source_url
        }
        if (-not $img) {
            $mImg = [regex]::Match($content, '<img[^>]+src=["'']([^"'']+/wp-content/uploads/[^"'']+)["'']', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
            if ($mImg.Success) { $img = $mImg.Groups[1].Value }
        }
        if ($img) { $img = $img -replace '^http://toonitalia\.xyz', 'https://toonitalia.xyz' }

        $results.Add([pscustomobject]@{
            id = $post.id
            titolo = [System.Net.WebUtility]::HtmlDecode($post.title.rendered)
            slug = $post.slug
            url_pagina = $post.link
            immagine = $img
            categorie = $post.categories
            categorie_nomi = $catNames
            modificato = $post.modified_gmt
            episodi = $episodes
        })
    }
}

# --- 3. Guardia anti-catalogo-vuoto ---
# Se il sito ci blocca (403 da runner GitHub) non far fallire il workflow:
# mantieni i dati precedenti ed esci 0 (verde) invece di 1 (rosso).
if ($results.Count -eq 0) {
    $prevJson = Join-Path (Join-Path $PSScriptRoot "data") "streaming_links.json"
    if (Test-Path $prevJson) {
        Write-Host "WARN: nessun nuovo post estratto (probabile blocco 403 Cloudflare). Mantengo streaming_links.json precedente."
    } else {
        Write-Host "WARN: nessun post estratto da toonitalia (probabile blocco 403 Cloudflare su IP GitHub). Nessun dato precedente, niente da aggiornare."
    }
    exit 0
}

# --- 4. Salvataggio output ---
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
Write-Host "Post elaborati: $postCount (doppioni da shift pagine ignorati: $dupCount)"
Write-Host "Voci episodio+player: $($flat.Count)"
Write-Host "JSON: $jsonPath"
Write-Host "CSV: $csvPath"
