# JURIKA — ocr-service (Python)

Microservice OCR CPU-only utilisé par l'`ai-service` (Java) pour la voie *image → texte*.
Le pipeline d'extraction final reste celui du backend Java : `texte → champs` via les règles +
LLM texte qwen2.5 (cf. `backend-java/ai-service/docs/LLM_EXTRACTION.md`).

## Pourquoi un microservice Python ?

- **PaddleOCR** et **docTR** sont des libs Python — pas d'équivalent JVM mature pour 2026.
- Isolation : le runtime OCR (poids des modèles, dépendances numpy/torch) ne pollue pas la JVM.
- Conformité **CNDP / loi 09-08** : tout reste localhost, aucun appel cloud (HuggingFace Inference, etc.).
- Scalabilité : on peut le démarrer en N replicas indépendamment du reste.

## Moteurs supportés

| Engine | Speed CPU CIN | Précision CIN | Install Windows | Choix par défaut |
|--------|---------------|---------------|-----------------|------------------|
| **docTR** (Mindee, torch CPU) | ~1.5-3 s | ≥ 90 % FR/latin | Pip simple `python-doctr[torch]` | ✅ |
| **PaddleOCR** (PP-OCRv4 mobile) | ~1-2 s | ≥ 92 % FR/latin | `paddlepaddle` parfois capricieux sur Windows | optionnel |

Le moteur est sélectionné par la variable d'environnement `OCR_ENGINE` (`doctr` par défaut, `paddle` en option).
Si `paddle` échoue au boot, le service tente `doctr` automatiquement et log un warning.

## API HTTP

```
POST /ocr
Content-Type: multipart/form-data
form-data: file=@scan.jpg   (image/* | application/pdf — première page seulement pour PDF)
form-data (optionnel): lang=fr|latin|en  (défaut: latin)

Réponse 200 :
{
  "text": "BENATIK\nOussama\nAB123456\n...",
  "lines": [
    {"text": "BENATIK", "confidence": 0.97, "bbox": [x1,y1,x2,y2]},
    ...
  ],
  "confidence": 0.93,
  "engine": "doctr",
  "ms": 1840
}

Réponse 503 si le moteur n'a pas pu être chargé (au boot ou pendant l'inférence non récupérable).

GET /health -> { "status": "UP", "engine": "doctr", "ready": true }
GET /info   -> { "engine": ..., "version": ..., "default_lang": ..., "loaded_at": ... }
```

## Démarrer le service

### Voie 1 — Docker (recommandé, reproductible)

```bash
docker compose -f infrastructure/docker-compose.yml \
               -f infrastructure/docker-compose.services.yml up -d ocr-service
# ou simplement la stack complète : start-all.ps1 lance ocr-service automatiquement.
```

Le port exposé par défaut est **8089**.

### Voie 2 — Local Python (dev, ≥ Python 3.10)

```powershell
cd backend-python\ocr-service
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install --upgrade pip
pip install -r requirements.txt
# Le 1er run télécharge les poids docTR (~200 MB) sous %USERPROFILE%\.cache\doctr\
$env:OCR_ENGINE = 'doctr'
$env:OCR_PORT = '8089'
uvicorn app.main:app --host 0.0.0.0 --port 8089
```

### Voie 3 — Paddle (optionnel, performance maximale)

Installer en plus :

```bash
pip install -r requirements-paddle.txt
# Sur Windows si l'install paddlepaddle échoue : utiliser la roue officielle CPU,
# https://www.paddlepaddle.org.cn/install/quick?docurl=/documentation/docs/en/install/pip/windows-pip_en.html
# Exemple PP 2.6 CPU :
#   pip install paddlepaddle==2.6.2 -i https://pypi.tuna.tsinghua.edu.cn/simple
```

Puis :

```powershell
$env:OCR_ENGINE = 'paddle'
uvicorn app.main:app --host 0.0.0.0 --port 8089
```

## Configuration

| Variable | Défaut | Description |
|----------|--------|-------------|
| `OCR_ENGINE` | `doctr` | Moteur OCR : `doctr` ou `paddle`. |
| `OCR_PORT` | `8089` | Port HTTP du service. |
| `OCR_DEFAULT_LANG` | `latin` | Langue par défaut quand aucune n'est précisée dans la requête. |
| `OCR_MAX_WIDTH` | `1600` | Largeur max de l'image (px) avant inférence. Le service redimensionne. |
| `OCR_USE_GPU` | `false` | Forcer GPU si dispo (non utilisé en mode hôte CPU). |
| `OCR_ENABLE_MKLDNN` | `true` | Optimisation CPU x86 pour Paddle (pas d'effet sur docTR). |
| `OCR_PRELOAD_MODELS` | `true` | Charge les modèles au boot (recommandé en prod). |

## Intégration côté Java (ai-service)

Une nouvelle voie `PADDLE_OCR_TEXT` / `DOCTR_TEXT` est insérée dans
`GenericExtractionService` AVANT la voie vision : le texte renvoyé par ce service est passé
directement au LLM texte qwen2.5 pour l'extraction structurée.

Variable d'environnement côté ai-service : `OCR_SERVICE_URL=http://localhost:8089`. Si le
service ne répond pas (down, timeout), le pipeline retombe gracieusement sur la voie vision
puis sur le fallback manuel — exactement comme avant.

Voir `backend-java/ai-service/docs/LLM_EXTRACTION.md` pour le tableau de routage à jour.

## Tests

```powershell
pip install -r requirements-dev.txt
pytest tests/
```

## Tableau bench (résultats live sur la machine du directeur)

Voir `backend-java/ai-service/docs/LLM_EXTRACTION.md` § "Bench OCR CPU 2026-06-09".
