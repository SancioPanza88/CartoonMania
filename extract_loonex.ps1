$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# Config: una voce qui per ogni serie extra da integrare
$series = @(
    @{
        slug      = 'le-nuove-avventure-di-scooby-doo'
        titolo    = 'Le nuove avventure di Scooby-Doo'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=le-nuove-avventure-di-scooby-doo-1782907602'
        copertina = 'https://loonex.eu/cartoni/uploads/covers/cover_lenuoveavventurediscooby-doo_1782907602.jpg'
        categorie = @('Bambini', 'ITA')
        modified  = '2026-08-25T12:00:00'
        id        = 9000001
    },
    @{
        slug      = 'geronimo-stilton'
        titolo    = 'Geronimo Stilton'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=geronimo-stilton-1778423136'
        copertina = 'https://loonex.eu/cartoni/covers/346-geronimo-stilton-1778423136-cover.jpg'
        categorie = @('Bambini', 'Avventura', 'ITA')
        modified  = '2026-09-05T12:00:00'
        id        = 9000002
    },
    @{
        slug      = 'cuccioli'
        titolo    = 'Cuccioli'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=cuccioli-1786102718'
        copertina = 'https://loonex.eu/cartoni/uploads/covers/cover_cuccioli_1786102718.png'
        categorie = @('Bambini', 'ITA')
        modified  = '2026-09-05T12:00:00'
        id        = 9000003
    },
    @{
        slug      = 'gli-antenati-i-flintstones'
        titolo    = 'Gli Antenati - I Flintstones'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=gli-antenati---i-flintstones-1787859582'
        copertina = 'https://loonex.eu/cartoni/uploads/covers/cover_gliantenati-iflintstones_1787859582.jpg'
        categorie = @('Bambini', 'Commedia', 'ITA')
        modified  = '2026-09-05T12:00:00'
        id        = 9000004
    },
    @{
        slug      = 'sabrina-amiche-per-sempre'
        titolo    = 'Sabrina - Amiche per sempre'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=sabrina---amiche-per-sempre-1775992920'
        copertina = 'https://loonex.eu/cartoni/covers/275-sabrina-amiche-per-sempre-1775992920-cover.jpg'
        categorie = @('Bambini', 'ITA')
        modified  = '2026-09-05T12:00:00'
        id        = 9000005
    },
    @{
        slug      = 'rekkit-rabbit'
        titolo    = 'Rekkit Rabbit'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=rekkit-rabbit-1772300435'
        copertina = 'https://loonex.eu/cartoni/covers/193-rekkit-rabbit-1772300435-cover.png'
        categorie = @('Bambini', 'Commedia', 'ITA')
        modified  = '2026-09-05T12:00:00'
        id        = 9000006
    },
    @{
        slug      = 'the-looney-tunes-show'
        titolo    = 'The Looney Tunes Show'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=the-looney-tunes-show-1769796646'
        copertina = 'https://loonex.eu/tls/ltstitle.jpg'
        categorie = @('Bambini', 'Commedia', 'ITA')
        modified  = '2026-09-05T12:00:00'
        id        = 9000007
    },
    @{
        slug      = 'titeuf'
        titolo    = 'Titeuf'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=titeuf-1775760641'
        copertina = 'https://loonex.eu/cartoni/covers/271-titeuf-1775760641-cover.jpg'
        categorie = @('Bambini', 'Commedia', 'ITA')
        modified  = '2026-09-05T12:00:00'
        id        = 9000008
    }
)

$ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'
$dataDir = Join-Path $PSScriptRoot "data"
$outPath = Join-Path $dataDir "loonex_links.json"

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

function ConvertFrom-LoonexUrl([string]$hex, [string]$key) {
    if (-not $hex -or -not $key) { return $null }
    $sb = New-Object System.Text.StringBuilder
    for ($i = 0; $i + 1 -lt $hex.Length; $i += 2) {
        $c = [Convert]::ToInt32($hex.Substring($i, 2), 16) -bxor [int][char]$key[[int](($i / 2) % $key.Length)]
        [void]$sb.Append([char]$c)
    }
    try { return [System.Uri]::UnescapeDataString($sb.ToString()) } catch { return $sb.ToString() }
}

function Resolve-GuardaUrl([string]$guardaUrl) {
    $html = Get-Http $guardaUrl
    if (-not $html) { return $null }
    $enc = [regex]::Match($html, 'encodedStr\s*=\s*"([0-9a-fA-F]+)"').Groups[1].Value
    $key = [regex]::Match($html, 'decryptionKey\s*=\s*"([^"]+)"').Groups[1].Value
    $url = ConvertFrom-LoonexUrl $enc $key
    if ($url -and $url.StartsWith('http') -and $url -match '\.(m3u8|mp4)(\?|$)') { return $url }
    return $null
}

$results = New-Object System.Collections.Generic.List[object]

foreach ($s in $series) {
    Write-Host "--- Loonex: $($s.titolo) ---"
    try {
        $page = Get-Http $s.pagina
        if (-not $page) { throw "pagina serie non raggiungibile" }

        $rows = [regex]::Matches($page, '<div class="episode-row[^"]*"\s+data-ep-label="(?<label>[^"]+)"[\s\S]*?href="(?<g>https://loonex\.eu/guarda/\?id=[^"]+)"')
        if ($rows.Count -eq 0) {
            # Fallback: pagine film/special con card qualità invece di righe episodio
            $rows = [regex]::Matches($page, 'data-ep-label="(?<label>[^"]+)"[\s\S]{0,4000}?href="(?<g>https://loonex\.eu/guarda/\?id=[^"]+)"')
        }
        if ($rows.Count -eq 0) { throw "nessun episodio trovato nella pagina" }

        $seen = @{}
        $episodes = New-Object System.Collections.Generic.List[object]
        foreach ($r in $rows) {
            $label = ([System.Net.WebUtility]::HtmlDecode($r.Groups['label'].Value) -replace [char]0x00d7, 'x').Trim()
            $guarda = $r.Groups['g'].Value
            if ($seen.ContainsKey($guarda)) { continue }
            $seen[$guarda] = $true

            $m3u8 = Resolve-GuardaUrl $guarda
            # Normalizza la codifica: alcune pagine danno URL gia' escaped
            # (%20) e riescaparli darebbe %2520 -> 404 sul videoserver
            $playerUrl = if ($m3u8) { [uri]::EscapeUriString([uri]::UnescapeDataString($m3u8)) } else { $guarda }
            if (-not $m3u8) { Write-Host "[WARN] fallback pagina guarda per: $label" }

            $episodes.Add([pscustomobject]@{
                episodio = $label
                player   = @([pscustomobject]@{ nome = 'Loonex'; dominio = 'loonex.eu'; url = $playerUrl })
            })
            Start-Sleep -Milliseconds 300
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
            throw "nessun episodio valido decodificato"
        }
    } catch {
        Write-Host "[WARN] $($s.titolo): $($_.Exception.Message)"
    }
}

if ($results.Count -gt 0) {
    $json = $results | ConvertTo-Json -Depth 8
    [System.IO.File]::WriteAllText($outPath, $json, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "loonex_links.json aggiornato: $($results.Count) serie"
} else {
    Write-Host "Nessuna serie loonex estratta: mantengo il file precedente"
}
