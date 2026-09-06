$ErrorActionPreference = 'Stop'

# Serie extra da Archive.org: una voce qui per ogni item (metadata API,
# niente scraping HTML). Stesso schema di loonex_links.json.
$series = @(
    @{
        slug      = 'jackie-chan-adventures'
        titolo    = 'Jackie Chan Adventures'
        item      = 'jackie-chan-adventures-1080p-ai-upscale'
        pagina    = 'https://archive.org/details/jackie-chan-adventures-1080p-ai-upscale'
        copertina = 'https://archive.org/services/img/jackie-chan-adventures-1080p-ai-upscale'
        categorie = @('Bambini', 'Azione', 'ITA')
        modified  = '2026-09-05T12:00:00'
        id        = 9100001
    }
)

$dataDir = Join-Path $PSScriptRoot "data"
$outPath = Join-Path $dataDir "archive_links.json"

$results = New-Object System.Collections.Generic.List[object]

foreach ($s in $series) {
    Write-Host "--- Archive.org: $($s.titolo) ---"
    try {
        $meta = Invoke-RestMethod -Uri "https://archive.org/metadata/$($s.item)" -TimeoutSec 120
        if (-not $meta.files) { throw "metadata senza files" }

        $vids = @($meta.files | Where-Object { $_.name -match '\.mp4$' } | Sort-Object name)
        if ($vids.Count -eq 0) { throw "nessun mp4 trovato" }

        $episodes = New-Object System.Collections.Generic.List[object]
        foreach ($f in $vids) {
            $base = [System.IO.Path]::GetFileNameWithoutExtension($f.name)
            $label = $base -replace '^Jackie Chan Adventures\s*-\s*', ''
            $label = ($label -replace '\s+', ' ').Trim()
            if (-not $label) { $label = $base }
            $url = [uri]::EscapeUriString("https://archive.org/download/$($s.item)/$($f.name)")
            $episodes.Add([pscustomobject]@{
                episodio = $label
                player   = @([pscustomobject]@{ nome = 'Archive.org'; dominio = 'archive.org'; url = $url })
            })
        }

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
    Write-Host "archive_links.json aggiornato: $($merged.Count) serie (fresche: $($results.Count))"
} else {
    Write-Host "Nessuna serie archive estratta: mantengo il file precedente"
}
