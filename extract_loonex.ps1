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
    },
    @{
        slug      = 'tom-and-jerry-cortometraggi-1940-2005'
        titolo    = 'Tom and Jerry - Cortometraggi (1940-2005)'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=tom-and-jerry---cortometraggi-1940-2005--1788256604'
        copertina = 'https://loonex.eu/cartoni/uploads/covers/cover_tomandjerry-cortometraggi1940-2005_1788256604.webp'
        categorie = @('Bambini', 'Commedia', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000009
    },
    @{
        slug      = 'the-boondocks'
        titolo    = 'The Boondocks'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=the-boondocks-1774264609'
        copertina = 'https://loonex.eu/cartoni/covers/238-the-boondocks-1774264609-cover.png'
        categorie = @('Commedia', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000010
    },
    @{
        slug      = 'tom-e-jerry-tales'
        titolo    = 'Tom e Jerry Tales'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=tom-e-jerry-tales-1776085035'
        copertina = 'https://loonex.eu/cartoni/covers/282-tom-e-jerry-tales-1776085035-cover.png'
        categorie = @('Bambini', 'Commedia', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000011
    },
    @{
        slug      = 'topolino-e-il-cervello-in-fuga'
        titolo    = 'Topolino e il Cervello in Fuga'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=topolino-e-il-cervello-in-fuga-1784930810'
        copertina = 'https://loonex.eu/cartoni/uploads/covers/cover_topolinoeilcervelloinfuga_1784930810.png'
        categorie = @('Bambini', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000012
    },
    @{
        slug      = 'spider-man-the-new-animated-series'
        titolo    = 'Spider-Man: The New Animated Series'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=spider-man-the-new-animated-series-1771272470'
        copertina = 'https://loonex.eu/cartoni/covers/136-spider-man-the-new-animated-series-1771272470-cover.jpg'
        categorie = @('Bambini', 'Azione', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000013
    },
    @{
        slug      = 'squitto-lo-scoiattolo'
        titolo    = 'Squitto lo Scoiattolo'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=squitto-lo-scoiattolo-1770388478'
        copertina = 'https://loonex.eu/cartoni/covers/119-squitto-lo-scoiattolo-1770388478-cover.jpg'
        categorie = @('Bambini', 'Commedia', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000014
    },
    @{
        slug      = 't-u-f-f-puppy'
        titolo    = 'T.U.F.F. Puppy'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=t-u-f-f-puppy-1772141790'
        copertina = 'https://loonex.eu/cartoni/covers/190-t-u-f-f-puppy-1772141790-cover.png'
        categorie = @('Bambini', 'Azione', 'Commedia', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000015
    },
    @{
        slug      = 'sabrina-la-mia-vita-segreta'
        titolo    = 'Sabrina, la mia vita segreta'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=sabrina-la-mia-vita-segreta-1775908844'
        copertina = 'https://loonex.eu/cartoni/covers/272-sabrina-la-mia-vita-segreta-1775908844-cover.jpg'
        categorie = @('Bambini', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000016
    },
    @{
        slug      = 'ren-and-stimpy'
        titolo    = 'Ren and Stimpy'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=ren-and-stimpy-1771462295'
        copertina = 'https://loonex.eu/cartoni/covers/145-ren-and-stimpy-1771462295-cover.jpg'
        categorie = @('Commedia', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000017
    },
    @{
        slug      = 'polli-kung-fu'
        titolo    = 'Polli Kung Fu'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=polli-kung-fu-1785742232'
        copertina = 'https://loonex.eu/cartoni/uploads/covers/cover_pollikungfu_1785742232.webp'
        categorie = @('Bambini', 'Azione', 'Commedia', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000018
    },
    @{
        slug      = 'phineas-e-ferb'
        titolo    = 'Phineas e Ferb'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=phineas-e-ferb-1773439185'
        copertina = 'https://loonex.eu/cartoni/covers/216-phineas-e-ferb-1773439185-cover.png'
        categorie = @('Bambini', 'Commedia', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000019
    },
    @{
        slug      = 'nome-in-codice-kommando-nuovi-diavoli'
        titolo    = 'Nome in Codice: Kommando Nuovi Diavoli'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=nome-in-codice-kommando-nuovi-diavoli-1781618224'
        copertina = 'https://loonex.eu/cartoni/uploads/covers/cover_nomeincodicekommandonuovidiavoli_1781618224.jpg'
        categorie = @('Bambini', 'Azione', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000020
    },
    @{
        slug      = 'justice-league-serie-animata-2001'
        titolo    = 'Justice League (Serie Animata 2001)'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=justice-league-serie-animata-2001-1774988207'
        copertina = 'https://loonex.eu/cartoni/covers/249-justice-league-serie-animata-2001-1774988207-cover.jpg'
        categorie = @('Azione', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000021
    },
    @{
        slug      = 'i-fantaeroi'
        titolo    = 'I Fantaeroi'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=i-fantaeroi-1783666846'
        copertina = 'https://loonex.eu/cartoni/uploads/covers/cover_i-fantaeroi-1783666846_1783667035.webp'
        categorie = @('Bambini', 'Commedia', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000022
    },
    @{
        slug      = 'i-pronipoti-the-jetsons'
        titolo    = 'I Pronipoti - The Jetsons'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=i-pronipoti---the-jetsons-1783832695'
        copertina = 'https://loonex.eu/cartoni/uploads/covers/cover_ipronipoti-thejetsons_1783832695.jpg'
        categorie = @('Bambini', 'Commedia', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000023
    },
    @{
        slug      = 'fantomette'
        titolo    = 'Fantomette'
        pagina    = 'https://loonex.eu/cartoni/index.php?cartone=fantomette-1788950244'
        copertina = 'https://loonex.eu/cartoni/uploads/covers/cover_fantomette_1788950244.jpg'
        categorie = @('Bambini', 'Commedia', 'ITA')
        modified  = '2026-09-11T12:00:00'
        id        = 9000024
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

# Nuovo schema player (2026-09): l'URL reale viene da
# guardaUnpackSrc(packed, decryptionKey) — base64url + doppio XOR.
# Il vecchio encodedStr e' rimasto come esca e punta a path /episodi/_x/
# inesistenti (404). Replicazione esatta del JS del sito.
function ConvertFrom-LoonexPacked([string]$packed, [string]$key) {
    if (-not $packed -or -not $key) { return $null }
    $b64 = $packed.Replace('-', '+').Replace('_', '/')
    while ($b64.Length % 4) { $b64 += '=' }
    try { $bytes = [Convert]::FromBase64String($b64) } catch { return $null }
    if ($bytes.Length -lt 2) { return $null }
    $sb = New-Object System.Text.StringBuilder
    for ($i = 1; $i -lt $bytes.Length; $i++) {
        $j = $i - 1
        $c = $bytes[$i] -bxor [int][char]$key[$j % $key.Length] -bxor ((($j * 13 + 7) -band 255))
        [void]$sb.Append([char]$c)
    }
    try { return [System.Uri]::UnescapeDataString($sb.ToString()) } catch { return $sb.ToString() }
}

function Resolve-GuardaUrl([string]$guardaUrl) {
    $html = Get-Http $guardaUrl
    if (-not $html) { return $null }
    $key = [regex]::Match($html, 'decryptionKey\s*=\s*"([^"]+)"').Groups[1].Value
    # 1) schema nuovo (packed)
    $packed = [regex]::Match($html, 'guardaUnpackSrc\("([^"]+)",\s*decryptionKey\)').Groups[1].Value
    $url = ConvertFrom-LoonexPacked $packed $key
    # Episodi hostati su OK.ru (isOkru): URL con expires+srcIp legati a
    # sessione/IP, muoiono in ore. Non immagazzinarli: meglio la pagina
    # guarda (il player del sito li risolve freschi a ogni visita).
    if ($url -match 'okcdn\.ru|ok\.ru') { return $null }
    if ($url -and $url.StartsWith('http') -and $url -match '\.(m3u8|mp4)(\?|$)') { return $url }
    # 2) fallback schema vecchio (XOR): alcune pagine potrebbero usarlo ancora
    $enc = [regex]::Match($html, 'encodedStr\s*=\s*"([0-9a-fA-F]+)"').Groups[1].Value
    $url = ConvertFrom-LoonexUrl $enc $key
    if ($url -and $url.StartsWith('http') -and $url -match '\.(m3u8|mp4)(\?|$)') { return $url }
    return $null
}

# Verifica l'URL risolto: 'ok' | 'dead' (404: path inesistente per chiunque)
# | 'unknown' (403/timeout: possibile filtro anti-datacenter, tengo il link).
# Senza questo controllo un cambio schema del sito spedisce in catalogo
# centinaia di link morti senza che nessuno se ne accorga (run 2026-09-11).
function Test-VideoUrl([string]$url) {
    try {
        $req = [System.Net.HttpWebRequest]::Create($url)
        $req.Method = 'HEAD'
        $req.Timeout = 15000
        $req.UserAgent = $ua
        $req.Referer = 'https://loonex.eu/guarda/'
        $resp = $req.GetResponse()
        $code = [int]$resp.StatusCode
        $resp.Close()
        if ($code -ge 200 -and $code -lt 400) { return 'ok' }
        if ($code -eq 404) { return 'dead' }
        return 'unknown'
    } catch [System.Net.WebException] {
        $r = $_.Exception.Response
        if ($r) {
            try { $c = [int]$r.StatusCode } catch { $c = 0 }
            try { $r.Close() } catch { }
            if ($c -eq 404) { return 'dead' }
        }
        return 'unknown'
    } catch { return 'unknown' }
}

$results = New-Object System.Collections.Generic.List[object]

foreach ($s in $series) {
    Write-Host "--- Loonex: $($s.titolo) ---"
    try {
        $page = Get-Http $s.pagina
        if (-not $page) { throw "pagina serie non raggiungibile" }

        $rows = [regex]::Matches($page, '<div class="episode-row[^"]*"\s+data-ep-label="(?<label>[^"]+)"[\s\S]*?href="(?<g>https://loonex\.eu/guarda/\?[^"]+)"')
        if ($rows.Count -eq 0) {
            # Fallback: pagine film/special con card qualità invece di righe episodio
            $rows = [regex]::Matches($page, 'data-ep-label="(?<label>[^"]+)"[\s\S]{0,4000}?href="(?<g>https://loonex\.eu/guarda/\?[^"]+)"')
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
            if (-not $m3u8) {
                Write-Host "[WARN] fallback pagina guarda per: $label"
            } else {
                # Link morto (404)? Meglio la pagina guarda (il player del sito
                # funziona sempre) che un m3u8 inesistente.
                $check = Test-VideoUrl $playerUrl
                if ($check -eq 'dead') {
                    Write-Host "[WARN] link morto (404), uso pagina guarda per: $label"
                    $playerUrl = $guarda
                }
            }

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

# Unione col file precedente: le serie fallite in questo run (es. WAF/403
# dagli IP datacenter GitHub) mantengono i dati vecchi invece di sparire
# dal catalogo. Senza questa guardia un run parziale clobberava tutto.
# Merge deterministico: prima i dati freschi, poi i vecchi solo per gli
# slug mancanti. Solo voci CON slug (scarta eventuali oggetti spuri) e
# appiattimento esplicito: in PS 5.1 ConvertFrom-Json puo' emettere l'array
# come singolo oggetto non enumerato (visto garbage {"value","Count"} nel
# run 2026-09-11 contro i 404 di Loonex).
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
$expected = @($series | ForEach-Object { $_.slug })
$missing = @($expected | Where-Object { -not $have.ContainsKey([string]$_) })
if ($missing.Count -gt 0) { Write-Host "[WARN] serie senza dati (mai estratte): $($missing -join ', ')" }

if ($merged.Count -gt 0) {
    if ($merged.Count -eq 1) {
        $json = '[' + ($merged[0] | ConvertTo-Json -Depth 8) + ']'
    } else {
        $json = $merged | ConvertTo-Json -Depth 8
    }
    [System.IO.File]::WriteAllText($outPath, $json, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "loonex_links.json aggiornato: $($merged.Count) serie (fresche: $($results.Count))"
} else {
    Write-Host "Nessuna serie loonex estratta: mantengo il file precedente"
}
