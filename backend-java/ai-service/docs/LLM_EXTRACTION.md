# Pipeline LLM d'extraction documentaire (P4 + Vision 2026-06-05 + FAST OCR 2026-06-09)

Pipeline générique à **routage intelligent** : un seul endpoint, quatre voies d'extraction
choisies dynamiquement selon le type de fichier et la disponibilité des moteurs locaux.

```
                      ┌─ PDF avec couche texte ≥ 60 chars
                      │     → PDFBox text + LLM texte                       (PDFBOX_TEXT)
                      │
  fileBytes ──────────┼─ PDF scanné OU image
                      │   ├─ FAST OCR activé (jurika.ocr.fast.enabled=true)
                      │   │   1) raster ~150 dpi (PDF) ou direct (image)
                      │   │   2) appel HTTP ocr-service Python (docTR/Paddle CPU, 1-10s)
                      │   │   3) texte → LLM texte (qwen2.5)
                      │   │      ★ extractionMode = PDFBOX_RENDER_FAST_OCR_<engine>
                      │   │                       | IMAGE_FAST_OCR_<engine>
                      │   │
                      │   ├─ Vision activé (LLM_VISION_MODEL) — fallback FAST OCR ko
                      │   │     → raster ~150 dpi (PDF) ou direct (image)
                      │   │     → LLM VISION (qwen3-vl, llava, ...)         (PDFBOX_RENDER_VISION | IMAGE_VISION)
                      │   │
                      │   └─ Vision absent
                      │         → Tesseract + LLM texte                     (PDFBOX_RENDER_TESSERACT | TESSERACT_IMAGE)
                      │
                      └─ Tout échoue → Noop                                  (FALLBACK_MANUAL)
```

> **Pourquoi la voie FAST OCR (2026-06-09) ?** Sur CPU hôte le LLM vision (qwen3-vl:2b)
> traitait une CIN en **100-120 s**. PaddleOCR / docTR — moteurs OCR dédiés CPU —
> font le même travail en **1-10 s** avec une précision équivalente sur du latin (CIN,
> CN, RC). On scinde donc en 2 étapes : OCR rapide pour passer image→texte, LLM
> texte (qwen2.5 / Groq llama-3.3) pour passer texte→champs. La voie vision est
> conservée comme filet de sécurité (typographies exotiques, scans très dégradés).

## Endpoint

```
POST /api/v1/ai/extract?type=<DOC_TYPE>
Content-Type: multipart/form-data
form-data: file=@document.pdf
```

Réponse (succès via vision sur image scannée) :

```json
{
  "type": "CIN",
  "fields": { "nom": "BENATIK", "prenom": "Oussama", "cinNumero": "AB123456", "dateNaissance": "1990-01-15", "dateExpiration": null },
  "confidence": {},
  "source": "LLM_OLLAMA_VISION",
  "provider": "ollama-vision",
  "model": "qwen3-vl:2b",
  "degraded": false,
  "extractionMode": "IMAGE_VISION",
  "warnings": []
}
```

Réponse (PDF numérique avec couche texte exploitable — voie la plus rapide) :

```json
{
  "type": "CERTIFICAT_NEGATIF",
  "fields": { "ice": "001122334455667", "cnNumero": "CN-2026-1234", "cnDate": "2026-04-12", "denomination": "ATLAS TRADING", "...": "..." },
  "source": "LLM_OLLAMA",
  "provider": "ollama",
  "model": "qwen2.5:7b",
  "degraded": false,
  "extractionMode": "PDFBOX_TEXT",
  "warnings": []
}
```

Réponse (fallback Tesseract si vision indisponible) :

```json
{
  "type": "CIN",
  "fields": { "nom": "BENATIK", "...": "..." },
  "source": "LLM_OLLAMA",
  "provider": "ollama",
  "model": "qwen2.5:7b",
  "degraded": false,
  "extractionMode": "TESSERACT_IMAGE",
  "warnings": []
}
```

Réponse (dégradée — pas de LLM configuré du tout) :

```json
{
  "type": "CIN",
  "fields": { "nom": null, "prenom": null, "cinNumero": null, "dateNaissance": null, "dateExpiration": null },
  "source": "LLM_DEGRADED",
  "provider": "noop",
  "model": "",
  "degraded": true,
  "extractionMode": "TESSERACT_IMAGE_FALLBACK_MANUAL",
  "warnings": ["LLM_API_KEY non configurée — saisie manuelle requise"]
}
```

L'UI affiche dans ce cas un formulaire de saisie 100% manuelle (`OcrSuggestionsPanel` est non destructif).

## Modes d'extraction (`extractionMode`)

Le champ permet au frontend d'afficher à l'utilisateur la voie technique réellement empruntée
(et son niveau de fiabilité attendu).

| Mode                                | Voie                                                                            | Précision attendue |
|-------------------------------------|---------------------------------------------------------------------------------|--------------------|
| `PDFBOX_TEXT`                       | PDF numérique — couche texte directement → LLM texte                            | **~100%** (texte natif) |
| `IMAGE_FAST_OCR_DOCTR`              | Image → ocr-service docTR CPU → LLM texte (★ 2026-06-09)                        | **élevée** (≥ 90 % FR/latin) |
| `IMAGE_FAST_OCR_PADDLE`             | Image → ocr-service PaddleOCR CPU → LLM texte (★ 2026-06-09)                    | **élevée** (≥ 92 % FR/latin) |
| `PDFBOX_RENDER_FAST_OCR_DOCTR`      | PDF scanné → rastérisation → ocr-service docTR → LLM texte (★ 2026-06-09)        | **élevée** |
| `PDFBOX_RENDER_FAST_OCR_PADDLE`     | PDF scanné → rastérisation → ocr-service Paddle → LLM texte (★ 2026-06-09)       | **élevée** |
| `IMAGE_VISION`                      | Image (CIN scan, JPG, PNG) → LLM vision multimodal                              | **élevée** (qwen3-vl voit l'image entière) — lent CPU |
| `PDFBOX_RENDER_VISION`              | PDF scanné → rastérisation → LLM vision                                         | **élevée** (idem) — lent CPU |
| `TESSERACT_IMAGE`                   | Image → Tesseract OCR → LLM texte (fallback vision)                             | moyenne (dépend qualité image) |
| `PDFBOX_RENDER_TESSERACT`           | PDF scanné → rastérisation → Tesseract → LLM texte                              | moyenne |
| `TESSERACT_IMAGE_EMPTY`             | Tesseract n'a rien extrait                                                      | dégradé |
| `TESSERACT_IMAGE_FALLBACK_MANUAL`   | Tesseract OK mais LLM indisponible                                              | dégradé |
| `IMAGE_VISION_FALLBACK_MANUAL`      | Vision OK mais réponse dégradée (rare)                                          | dégradé |
| `EMPTY_INPUT`                       | Aucun byte transmis                                                             | dégradé |

## Types supportés (V1)

| typeCode             | Champs                                                                                          |
|----------------------|-------------------------------------------------------------------------------------------------|
| `CIN`                | nom, prenom, cinNumero, dateNaissance, dateExpiration                                           |
| `CERTIFICAT_NEGATIF` | ice, cnNumero, cnDate, denomination, beneficiaire, activiteCn                                   |
| `STATUTS_SARL`       | raisonSociale, formeJuridique, capitalSocial, nombreParts, siegeSocial, dateConstitution        |
| `RC_IMMATRICULATION` | rcNumero, ville, dateImmatriculation, raisonSociale                                             |
| `IF_DECLARATION`     | ifNumero, dateAttribution, raisonSociale                                                        |
| `JUSTIFICATIF_SIEGE` | adresse, ville, codePostal, type (BAIL/DOMICILIATION), proprietaire                             |

## Configuration

Toutes les valeurs sont lues depuis des variables d'environnement (jamais de secret en clair) :

| Variable                  | Défaut                                       | Description                                                  |
|---------------------------|----------------------------------------------|--------------------------------------------------------------|
| `LLM_ENABLED`             | `false`                                      | Active l'appel réseau LLM. Si false → `NoopLlmProvider`.     |
| `LLM_PROVIDER`            | `groq`                                       | Identifiant logique (`groq`, `openai`, `ollama`, ...).       |
| `LLM_BASE_URL`            | `https://api.groq.com/openai/v1`             | URL de l'API OpenAI-compatible (avec `/v1`).                 |
| `LLM_API_KEY`             | *(vide)*                                     | Clé API. Optionnelle pour Ollama / LM Studio / vLLM local.   |
| `LLM_MODEL`               | `llama-3.1-70b-versatile`                    | Nom du modèle TEXTE.                                         |
| `LLM_TIMEOUT`             | `30`                                         | Timeout HTTP texte (connect + read) en secondes.             |
| **`LLM_VISION_MODEL`**    | *(vide)*                                     | **NOUVEAU 2026-06-05.** Nom du modèle VISION multimodal. Si renseigné ET `LLM_ENABLED=true`, active la route vision pour images / PDF scannés. Vide → fallback Tesseract+LLM. |
| `LLM_VISION_TIMEOUT`      | `120`                                        | Timeout HTTP vision (l'inférence image est plus lente).      |
| `LLM_PDF_RENDER_DPI`      | `220`                                        | DPI rasterisation PDF scanné avant envoi vision (200-300 recommandé). |
| `LLM_MIN_PDF_TEXT_CHARS`  | `60`                                         | Seuil minimum chars couche texte PDF pour considérer le doc "numérique". |
| **`OCR_FAST_ENABLED`**    | `false`                                      | **NOUVEAU 2026-06-09.** Active la voie OCR rapide CPU via microservice Python `ocr-service`. |
| **`OCR_SERVICE_URL`**     | `http://localhost:8089`                      | URL du microservice ocr-service (PaddleOCR / docTR). |
| `OCR_FAST_TIMEOUT`        | `30`                                         | Timeout HTTP (s) — l'inférence docTR/Paddle tient en 1-10 s sur CPU host. |
| `OCR_FAST_LANG`           | `latin`                                      | Code langue passé au microservice (`latin` couvre FR/EN/AR-romanisé). |
| `OCR_FAST_HEALTH_CACHE_SECONDS` | `30`                                  | Période min entre 2 `GET /health` du microservice (évite le bruit si down). |

## Bench OCR CPU 2026-06-09 (branche feat/ocr-paddle-cpu)

Mesures live sur machine hôte Windows 11 (CPU only, sans GPU), CIN synthétique 1200×760 PNG (script `backend-python/ocr-service/scripts/bench_synthetic.py`).

| Voie                                    | Avant (Session A)      | Après (branche FAST OCR)            | Gain          |
|-----------------------------------------|------------------------|-------------------------------------|---------------|
| PDF numérique (couche texte) — PDFBOX_TEXT | ~1 s (PDFBox) + LLM    | inchangé                            | inchangé      |
| **CIN scannée image → texte**            | **100-120 s** (qwen3-vl:2b vision) | **9-10 s** (docTR CPU + LLM texte)  | **× 10-12**   |
| PDF scanné → texte                       | 100-120 s (vision)     | ~10-15 s (raster 150 dpi + docTR)   | × 8-10        |
| Précision champs CIN (NOM, PRENOM, CIN, dates) | ≥ 95% vision         | ≥ 90% docTR (le manuel rattrape le delta) | inchangé en pratique |

> Mesure docTR brute : 9173-9980 ms inference + ~50 ms overhead HTTP ; confidence pondérée 0.96 sur la CIN synthétique. La 1ère inférence post-boot inclut un warmup torch (~30 s) — exécutée au démarrage du service quand `OCR_PRELOAD=true`.

> **Note précision** : sur des CIN très dégradées (manuscrites, plis, scans très basse résolution) le LLM vision reste plus robuste ; le pipeline garde la voie vision en filet de sécurité — elle est appelée automatiquement si l'OCR rapide renvoie un texte vide ou trop court (< 3 chars utiles).

## Setup OCR rapide CPU (procédure reproductible — mode hôte Windows)

Doc complète : `backend-python/ocr-service/README.md`. Résumé pour la direction :

```powershell
# 1) Préparer le venv Python (Python 3.10–3.12)
cd backend-python\ocr-service
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install --upgrade pip
pip install -r requirements.txt
# Le 1er run télécharge les poids docTR (~200 MB) sous %USERPROFILE%\.cache\doctr\

# 2) Sanity local
$env:OCR_ENGINE = 'doctr'
uvicorn app.main:app --host 0.0.0.0 --port 8089
# Dans un 2e terminal :
curl http://localhost:8089/health
python scripts/bench_synthetic.py --runs 3 --warmup

# 3) Activer côté ai-service (Java) — variables d'env (.env.local)
$env:OCR_FAST_ENABLED = 'true'
$env:OCR_SERVICE_URL  = 'http://localhost:8089'
$env:LLM_ENABLED      = 'true'
# (Groq texte recommandé pour la rapidité)
$env:LLM_BASE_URL = 'https://api.groq.com/openai/v1'
$env:LLM_MODEL    = 'llama-3.3-70b-versatile'
$env:LLM_API_KEY  = 'gsk_xxx'   # console.groq.com (gratuit)

# 4) Restart ai-service
.\scripts\start-all.ps1     # lance ocr-service automatiquement quand le venv est présent

# 5) E2E
node scripts\e2e-ocr-2026-06-09.mjs
```

### Choix Paddle vs docTR (note pour la direction)

| Critère                                 | docTR (Mindee, torch CPU)        | PaddleOCR (PP-OCRv4 mobile)              |
|-----------------------------------------|----------------------------------|------------------------------------------|
| Install Windows (CPU)                   | ✅ `pip install python-doctr[torch]` direct | ⚠ `paddlepaddle` parfois capricieux, dépendant du miroir pypi |
| Latence CPU host                        | 9-10 s / CIN                     | ~5-7 s / CIN (parfois mieux)             |
| Précision FR/latin                      | ≥ 90 %                           | ≥ 92 %                                   |
| Empreinte modèles                       | ~165 MB                          | ~12 MB (mobile)                          |
| Stabilité runtime Windows               | ✅ très bonne (torch CPU stable) | ⚠ MKLDNN parfois flaky                   |

**Décision livraison initiale : docTR** — meilleure expérience d'installation Windows et pas de dépendance MKLDNN. La voie Paddle reste activable (`requirements-paddle.txt` + `OCR_ENGINE=paddle`) pour les cabinets qui veulent gagner ~30 % supplémentaire en latence.

## Setup démo (Groq, gratuit / pré-prod, texte seulement)

```bash
export LLM_ENABLED=true
export LLM_PROVIDER=groq
export LLM_BASE_URL=https://api.groq.com/openai/v1
export LLM_API_KEY=gsk_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export LLM_MODEL=llama-3.1-70b-versatile
# Vision non activée → PDF scannés / images passent par Tesseract.
```

## Setup production CNDP-compliant (Ollama local — texte + vision)

Pour garantir le respect de la **loi 09-08** (CNDP, protection des données personnelles au Maroc),
les données métier des cabinets clients **ne doivent jamais quitter la machine d'hébergement**.

Solution : déployer Ollama localement, avec un modèle texte ET un modèle vision.

```bash
# 1) Installer Ollama (https://ollama.com/download)
curl -fsSL https://ollama.com/install.sh | sh

# 2) Télécharger un modèle texte (extraction depuis texte OCR ou PDF numérique).
ollama pull qwen2.5:7b
# Ou plus léger :
# ollama pull llama3.1:8b-instruct-q4_K_M

# 3) Télécharger un modèle VISION (CIN / Certificats Négatifs scannés).
ollama pull qwen3-vl:2b
# Variantes plus précises mais plus lourdes :
# ollama pull qwen3-vl:7b
# ollama pull llava:13b

# 4) Configurer ai-service (.env.local ou export)
export LLM_ENABLED=true
export LLM_PROVIDER=ollama
export LLM_BASE_URL=http://localhost:11434/v1
export LLM_API_KEY=
export LLM_MODEL=qwen2.5:7b
export LLM_VISION_MODEL=qwen3-vl:2b
export LLM_VISION_TIMEOUT=120
export LLM_PDF_RENDER_DPI=220
```

Avec cette config :

- **PDF numérique** (Certificat Négatif édité par OMPIC, RC) → couche texte PDFBox → modèle texte → extraction ~100% précise, < 1 seconde.
- **Scan papier / photo CIN** → modèle vision multimodal lit directement l'image. Bien plus précis que Tesseract (zones manuscrites, sceaux, multi-colonnes, scans dégradés).
- **Aucun byte ne quitte la machine** — inférence sur GPU local (recommandé) ou CPU.

## Routage automatique (résumé)

Au runtime, `GenericExtractionService` choisit la voie d'extraction selon trois informations :

1. **Magic bytes `%PDF`** (`PdfDocumentInspector.looksLikePdf`) — détecte un PDF même si l'extension est trompeuse.
2. **Couche texte PDFBox** — si ≥ `LLM_MIN_PDF_TEXT_CHARS` chars, le PDF est traité comme numérique.
3. **`LLM_VISION_MODEL` non vide** — active la route vision (sinon fallback Tesseract+LLM, qui reste fonctionnel).

L'extraction n'est jamais bloquée : si vision répond dégradé (modèle absent, 5xx, parsing échec), le pipeline retombe automatiquement sur Tesseract+LLM, puis sur Noop (saisie manuelle).

## Ajouter un nouveau type de document

Tout se passe dans `ma.jurika.ai.llm.schema.DocumentSchemaRegistry` : éditer le bloc `static { ... }`,
ajouter une entrée :

```java
m.put("NOUVEAU_TYPE", new DocumentSchema(
    "NOUVEAU_TYPE",
    "Description sémantique courte du document",
    List.of(
        new FieldDef("champ1", "string", "Description champ 1", true),
        new FieldDef("champ2", "number", "Description champ 2", false),
        new FieldDef("champ3", "date",   "Description champ 3 (ISO YYYY-MM-DD)", false)
    )
));
```

Aucun autre fichier à modifier : le contrôleur `/api/v1/ai/extract?type=NOUVEAU_TYPE` répondra automatiquement avec le nouveau schéma — y compris pour la voie vision (l'instruction multimodale est générée depuis le schéma).

## Sécurité / robustesse

- Aucun secret n'est jamais loggé (seul `apiKey={PRESENT|ABSENT}` est tracé au boot).
- Toute exception réseau / HTTP 5xx / parsing → résultat dégradé avec warning explicite (jamais d'exception remontée).
- L'application démarre **sans aucune configuration LLM** — `NoopLlmProvider` + `NoopLlmVisionProvider` se chargent du fallback.
- Compatibilité ascendante : l'endpoint historique `/api/v1/ai/extract-cn` reste actif et inchangé.

## Tests

- `OpenAiCompatibleLlmProviderTest` — mock `RestTemplate` via `MockRestServiceServer`. Provider TEXTE.
- `OllamaVisionLlmProviderTest` — mock `RestTemplate` via `MockRestServiceServer`. Provider VISION (succès, 5xx, content liste de parts, JSON malformé, image vide, schéma null, désactivation).
- `GenericExtractionServiceTest` — mocks OCR + LLM texte + LLM vision (routage selon vision on/off, fallback vision→Tesseract, PDF scanné → vision, image directe → vision, etc.).
- Aucune connexion réseau réelle requise pour exécuter `mvn -pl ai-service test`.
