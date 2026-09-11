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
    },
    @{
        slug      = 'i-magicanti-e-i-tre-elementi-2003'
        titolo    = 'I Magicanti e i Tre Elementi (2003)'
        item      = 'i-magicanti-e-i-tre-elementi-2003'
        pagina    = 'https://archive.org/details/i-magicanti-e-i-tre-elementi-2003'
        copertina = 'https://archive.org/services/img/i-magicanti-e-i-tre-elementi-2003'
        categorie = @('Bambini', 'Fantasy', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9100002
    },
    @{
        slug      = 'gli-animotosi-nella-terra-di-non-dove-2005'
        titolo    = 'Gli Animotosi nella Terra di Nondove (2005)'
        item      = 'gli-animotosi-nella-terra-di-non-dove-2005'
        pagina    = 'https://archive.org/details/gli-animotosi-nella-terra-di-non-dove-2005'
        copertina = 'https://archive.org/services/img/gli-animotosi-nella-terra-di-non-dove-2005'
        categorie = @('Bambini', 'Avventura', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9100003
    },
    @{
        slug      = 'gli-skatenini-e-le-dune-dorate-2007'
        titolo    = 'Gli Skatenini e le Dune Dorate (2007)'
        item      = 'gli-skatenini-e-le-dune-dorate-07'
        pagina    = 'https://archive.org/details/gli-skatenini-e-le-dune-dorate-07'
        copertina = 'https://archive.org/services/img/gli-skatenini-e-le-dune-dorate-07'
        categorie = @('Bambini', 'Avventura', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9100004
    },
    @{
        slug      = 'gli-smile-and-go-e-il-braciere-di-fuoco-2007'
        titolo    = 'Gli Smile and Go e il Braciere di Fuoco (2007)'
        item      = 'gli-smile-and-go-e-il-braciere-di-fuoco-2007'
        pagina    = 'https://archive.org/details/gli-smile-and-go-e-il-braciere-di-fuoco-2007'
        copertina = 'https://archive.org/services/img/gli-smile-and-go-e-il-braciere-di-fuoco-2007'
        categorie = @('Bambini', 'Avventura', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9100005
    },
    @{
        slug      = 'i-lunes-e-la-sfera-di-lasifer-2002'
        titolo    = 'I Lunes e la Sfera di Lasifer (2002)'
        item      = 'i-lunes-e-la-sfera-di-lasifer_2002'
        pagina    = 'https://archive.org/details/i-lunes-e-la-sfera-di-lasifer_2002'
        copertina = 'https://archive.org/services/img/i-lunes-e-la-sfera-di-lasifer_2002'
        categorie = @('Bambini', 'Fantascienza', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9100006
    },
    @{
        slug      = 'i-lampaclima-e-l-isola-misteriosa-2006'
        titolo    = "I Lampaclima e l'Isola Misteriosa (2006)"
        item      = 'i-lampaclima-e-lisola-misteriosa-2006'
        pagina    = 'https://archive.org/details/i-lampaclima-e-lisola-misteriosa-2006'
        copertina = 'https://archive.org/services/img/i-lampaclima-e-lisola-misteriosa-2006'
        categorie = @('Bambini', 'Avventura', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9100007
    },
    @{
        slug      = 'i-magotti-e-la-pentola-magica-2001'
        titolo    = 'I Magotti e la Pentola Magica (2001)'
        item      = 'i-magotti-e-la-pentola-magica-2001'
        pagina    = 'https://archive.org/details/i-magotti-e-la-pentola-magica-2001'
        copertina = 'https://archive.org/services/img/i-magotti-e-la-pentola-magica-2001'
        categorie = @('Bambini', 'Fantasy', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9100008
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

        # Escludi i preview .ia.mp4 (presenti nei film: 1 file reale + 1 preview).
        # Senza questo filtro ogni film comparirebbe con 2 episodi duplicati.
        $vids = @($meta.files | Where-Object { $_.name -match '\.mp4$' -and $_.name -notmatch '\.ia\.mp4$' } | Sort-Object name)
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

# Unione col file precedente (stesso merge deterministico e anti-spuri
# di extract_loonex.ps1: solo voci con slug, array appiattiti)
$prev = @()
if (Test-Path $outPath) {
    try { $prev = @(Get-Content -Raw -Encoding UTF8 $outPath | ConvertFrom-Json) } catch { $prev = @() }
}
function Add-MergedItem($obj) {
    if ($obj -is [array]) { foreach ($x in $obj) { Add-MergedItem $x }; return }
    $k = [string]$obj.slug
    if (-not $k) { return }
    if (-not $have.ContainsKey($k)) { $merged.Add($obj); $have[$k] = $true }
}
$merged = New-Object System.Collections.Generic.List[object]
$have = @{}
foreach ($r in $results) { Add-MergedItem $r }
foreach ($p in $prev) { Add-MergedItem $p }

if ($merged.Count -gt 0) {
    if ($merged.Count -eq 1) {
        $json = '[' + ($merged[0] | ConvertTo-Json -Depth 8) + ']'
    } else {
        $json = $merged | ConvertTo-Json -Depth 8
    }
    [System.IO.File]::WriteAllText($outPath, $json, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "archive_links.json aggiornato: $($merged.Count) serie (fresche: $($results.Count))"
} else {
    Write-Host "Nessuna serie archive estratta: mantengo il file precedente"
}
