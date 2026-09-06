$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# Serie extra da AnimeTop: tabelle stagioni con link streaming uprot/MaxStream.
# (player embed con JS: l'app li apre in WebView e cattura il flusso.)
# RIMOSSO Mr. Bean 2026-09-06: captcha+popup uprot non fruibili in app.
$series = @(
)

$ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'
$dataDir = Join-Path $PSScriptRoot "data"
$outPath = Join-Path $dataDir "animetop_links.json"

function Get-Http([string]$url) {
    for ($i = 1; $i -le 3; $i++) {
        try {
            return (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 60 -Headers @{ 'User-Agent' = $ua }).Content
        } catch {
            Write-Host "[RETRY $i] $url"
            Start-Sleep -Seconds (2 * $i)
        }
    }
    return $null
}

$results = New-Object System.Collections.Generic.List[object]

foreach ($s in $series) {
    Write-Host "--- AnimeTop: $($s.titolo) ---"
    try {
        $page = Get-Http $s.pagina
        if (-not $page) { throw "pagina serie non raggiungibile" }

        $rx = "<td colspan='4'><b>(?<season>[^<]+)</b>|<tr><td>(?<label>[^<]+)</td><td><a href='(?<url>https://uprot\.net/msf/[^']+)'"
        $ms = [regex]::Matches($page, $rx)
        if ($ms.Count -eq 0) { throw "nessun episodio trovato nella pagina" }

        $seen = @{}
        $season = ""
        $episodes = New-Object System.Collections.Generic.List[object]
        foreach ($m in $ms) {
            if ($m.Groups['season'].Success -and $m.Groups['season'].Value) {
                $season = ([System.Net.WebUtility]::HtmlDecode($m.Groups['season'].Value)).Trim()
                continue
            }
            $url = $m.Groups['url'].Value
            if ($seen.ContainsKey($url)) { continue }
            $seen[$url] = $true
            $label = ([System.Net.WebUtility]::HtmlDecode($m.Groups['label'].Value) -replace [char]0x00d7, 'x').Trim()
            $label = ($label -replace '(?i)^mr\.?\s*bean(\s+serie\s+animata)?', '' -replace '(?i)\s*avi\s*$', '').Trim()
            if (-not $label) { $label = "Episodio $($episodes.Count + 1)" }
            if ($season -and $label -notmatch '(?i)stagione') { $label = "$label" }
            $episodes.Add([pscustomobject]@{
                episodio = $label
                player   = @([pscustomobject]@{ nome = 'MaxStream'; dominio = 'uprot.net'; url = $url })
            })
        }

        if ($episodes.Count -gt 0) {
            $results.Add([pscustomobject]@{
                id             = $s.id
                titolo         = $s.titolo
                slug           = $s.slug
                url_pagina     = $s.pagina
                immagine       = $s.copertina
                categorie      = @()
                categorie_nomi = @($s.categorie)
                modificato     = $s.modified
                episodi        = $episodes
            })
            Write-Host "[OK] $($episodes.Count) episodi estratti"
        } else {
            throw "nessun episodio valido"
        }
    } catch {
        Write-Host "[WARN] $($s.titolo): $($_.Exception.Message)"
    }
}

# Unione col file precedente (stessa guardia di extract_loonex.ps1)
$prev = @()
if (Test-Path $outPath) {
    try { $prev = @(Get-Content -Raw -Encoding UTF8 $outPath | ConvertFrom-Json) } catch { $prev = @() }
}
$bySlug = @{}
foreach ($p in $prev) { if ($p.slug) { $bySlug[$p.slug] = $p } }
foreach ($r in $results) { $bySlug[$r.slug] = $r }
$merged = @($bySlug.Values)

if ($merged.Count -gt 0) {
    $json = $merged | ConvertTo-Json -Depth 8
    [System.IO.File]::WriteAllText($outPath, $json, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "animetop_links.json aggiornato: $($merged.Count) serie (fresche: $($results.Count))"
} else {
    Write-Host "Nessuna serie animetop estratta: mantengo il file precedente"
}
