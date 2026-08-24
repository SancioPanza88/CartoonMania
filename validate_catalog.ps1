$ErrorActionPreference = 'Stop'
$path = "app\src\main\assets\catalog.json.gz"

# Decomprime e carica come testo (validazione sintattica fatta da ConvertFrom-Json)
$tmp = Join-Path $env:TEMP "catalog_check.json"
$in = [System.IO.File]::OpenRead((Resolve-Path $path))
$gz = New-Object System.IO.Compression.GZipStream($in, [System.IO.Compression.CompressionMode]::Decompress)
$out = [System.IO.File]::Create($tmp)
$gz.CopyTo($out); $out.Dispose(); $gz.Dispose(); $in.Dispose()

$json = Get-Content -Raw -Encoding UTF8 $tmp | ConvertFrom-Json
"JSON valido. Root keys: $($json.PSObject.Properties.Name -join ', ')"
"Titoli: $($json.s.Count)"

# Replicazione ESATTA del contratto del parser Kotlin
$errors = New-Object System.Collections.Generic.List[string]
$idx = 0
foreach ($t in $json.s) {
    $props = $t.PSObject.Properties.Name
    foreach ($k in @('u','t','e')) { if ($props -notcontains $k) { $errors.Add("[$idx] manca '$k' in titolo $($t.u)") } }
    if ($t.e -isnot [array]) { $errors.Add("[$idx] $($t.u): 'e' non è array ($($t.e.GetType().Name))") }
    else {
        foreach ($ep in $t.e) {
            $eprops = $ep.PSObject.Properties.Name
            foreach ($k in @('l','p')) { if ($eprops -notcontains $k) { $errors.Add("[$idx] $($t.u): episodio manca '$k'") } }
            if (-not ($ep.p -is [array])) { $errors.Add("[$idx] $($t.u) [$($ep.l)]: 'p' non è array") }
            else {
                foreach ($pl in $ep.p) {
                    if ($pl.PSObject.Properties.Name -notcontains 'u') { $errors.Add("[$idx] $($t.u) [$($ep.l)]: player manca 'u'") }
                }
            }
        }
    }
    $idx++
}
"Violazioni trovate: $($errors.Count)"
$errors | Select-Object -First 10

# Statistiche categorie dai raw posts (per le righe stile Netflix)
$catCount = @{}
foreach ($f in (Get-ChildItem "data\raw" -Filter "posts_*.json")) {
    $posts = Get-Content -Raw -Encoding UTF8 $f.FullName | ConvertFrom-Json
    foreach ($p in $posts) {
        if ($p._embedded.'wp:term') {
            foreach ($term in ($p._embedded.'wp:term'[0])) {
                $n = $term.name
                if (-not $catCount.ContainsKey($n)) { $catCount[$n] = 0 }
                $catCount[$n]++
            }
        }
    }
}
"--- TOP 20 CATEGORIE ---"
$catCount.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 20 | ForEach-Object { "$($_.Value)x $($_.Key)" }
