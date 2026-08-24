# CartoonMania 🎬

App Android per sfogliare il catalogo di anime, cartoni e film con i relativi link di riproduzione estratti dal sito.

## Come funziona l'aggiornamento automatico

- Un **GitHub Action** (`android.yml`) gira ogni notte alle 04:00 UTC:
  1. ri-estrae tutti i link di riproduzione dal sito via WordPress REST API;
  2. rigenera `data/catalog.json.gz` (catalogo compresso ~3 MB);
  3. fa commit del nuovo catalogo → il push triggerisce la **compilazione automatica dell'APK**.
- L'app all'avvio controlla `data/catalog.version.txt` su GitHub: se c'è una versione più recente scarica il nuovo catalogo. La copia integrata nell'APK funge da fallback offline.

## Come ottenere l'APK

1. Fai push del repo su GitHub (branch `main`).
2. Apri la sezione **Actions** → workflow `CartoonMania CI` → ultima esecuzione riuscita.
3. In fondo alla pagina scarica l'artifact **CartoonMania-debug-apk**.

## Struttura dati

Formato compatto di `catalog.json.gz`:

```json
{
  "g": "cartoonmania",
  "v": 202608242227,
  "s": [
    {
      "u": "slug-titolo",
      "t": "Titolo",
      "i": "https://url-copertina.jpg",
      "e": [ { "l": "1x01 - Titolo episodio", "p": [ { "n": "PLAYER1", "u": "https://player-url" } ] } ]
    }
  ]
}
```

## Script locali (Windows PowerShell)

- `extract_links.ps1` — scarica tutti i post dall'API ed estrae i player in `data/streaming_links.json/csv`
- `generate_catalog.ps1` — genera `data/catalog.json.gz`, la copia in `app/src/main/assets/` e scrive `data/catalog.version.txt`

## Prima del primo push

Sostituisci `OWNER` in `app/src/main/java/com/cartoonmania/app/CatalogRepo.kt` con il tuo username GitHub.
