# JURIKA — kie-service

Microservice CPU (Python 3.11 + FastAPI) qui charge un modèle Donut
(VisionEncoderDecoderModel, OCR-free) déjà entraîné et expose une
extraction de champs sur cartes d'identité marocaines (CIN ancienne et
nouvelle) et carte/registre (CN).

Le type de document est **toujours fourni par l'appelant** — pas de
classification implicite côté serveur.

## Endpoints

| Méthode | URL | Description |
|---------|-----|-------------|
| GET | `/health` | `{status: "UP", model_loaded: bool}` |
| POST | `/api/v1/kie/extract` | multipart : `file` (image ou PDF, 15 Mo max), `doc_type` |

`doc_type` ∈ `cn`, `cin_anc_recto`, `cin_anc_verso`, `cin_nouv_recto`, `cin_nouv_verso`.

### Réponse

```json
{
  "doc_type": "cin_nouv_verso",
  "fields": {
    "nom": "...",
    "prenom": "...",
    "cin": "AB123456",
    "date_naissance": "12.05.1990",
    "date_validite": "01.04.2031",
    "sexe": "M",
    "nationalite": "MAR",
    "adresse": "...",
    "doc_type": "cin_nouv_verso"
  },
  "source": "kie | merged",
  "warnings": []
}
```

## Logique de fusion `cin_nouv_verso`

1. Donut extrait tous les champs (dont `adresse`).
2. Si une MRZ TD1 est lisible dans l'output Donut, elle est parsée
   localement (déterministe, sans dépendance externe).
3. Pour les champs communs (`nom`, `prenom`, `cin`, `date_naissance`,
   `date_validite`, `sexe`, `nationalite`) la **MRZ est prioritaire**.
4. L'`adresse` vient toujours de Donut.
5. `source = "merged"` si MRZ exploitée, sinon `source = "kie"`.

## Validation / post-traitement

`app/validation.py` normalise sans bloquer :

- CIN : `^[A-Z]{1,2}[0-9]{5,7}$` après upper + suppression espaces.
- dates : normalisées en `DD.MM.YYYY`.
- sexe : forcé dans `{M, F}`.
- tous les champs texte : trim + collapse spaces.

Les anomalies sont remontées dans `warnings` (ex : `cin_format`, `date_date_naissance`, `mrz_not_found`).

## Modèle Donut

Les poids ne sont **jamais** committés dans le repo. Ils sont montés en
volume au runtime via `DONUT_MODEL_DIR`.

## Lancer en local (dev)

```bash
cd backend-python/kie-service
python -m venv .venv && source .venv/bin/activate  # ou .venv\Scripts\activate (Windows)
pip install --extra-index-url https://download.pytorch.org/whl/cpu -r requirements.txt
export DONUT_MODEL_DIR=/chemin/local/donut-jurika-final
uvicorn app.main:app --host 0.0.0.0 --port 8088
```

## Lancer en container

```bash
docker build -t jurika/kie-service:dev \
  -f backend-python/kie-service/Dockerfile backend-python/kie-service

docker run --rm -p 8088:8088 \
  -e DONUT_MODEL_DIR=/models/donut-jurika-final \
  -v /chemin/host/donut-jurika-final:/models/donut-jurika-final:ro \
  jurika/kie-service:dev
```

## Tests

```bash
python -m pytest backend-python/kie-service/tests -q
```

Les tests **ne chargent jamais** de vrais poids : `donut_engine.infer`
est monkeypatché pour renvoyer un payload déterministe.

## Smoke test avec le vrai modèle

Le script `scripts/smoke_real.py` charge le vrai modèle Donut et exécute
une extraction complète (Donut → `clean_fields` → `validate_and_normalize`).
Pratique pour valider une vraie face de CIN ou un scan de CN avant
intégration end-to-end.

```bash
cd backend-python/kie-service

# Linux / macOS
DONUT_MODEL_DIR=/chemin/local/donut-jurika-final \
  .venv/bin/python scripts/smoke_real.py \
    --image /chemin/vers/cin_recto.jpg \
    --doc-type cin_nouv_recto

# Windows PowerShell
$env:DONUT_MODEL_DIR = "C:\chemin\donut-jurika-final"
.\.venv\Scripts\python.exe scripts\smoke_real.py `
    --image "C:\chemin\cin_recto.jpg" `
    --doc-type cin_nouv_recto
```

Doc types acceptés : `cn`, `cin_anc_recto`, `cin_anc_verso`,
`cin_nouv_recto`, `cin_nouv_verso`. Ajouter `--raw` pour voir la sortie
de `clean_fields` avant validation. **Ne JAMAIS committer d'images réelles**
— elles contiennent des données personnelles.

## Post-traitement Donut — `app/clean.py`

`clean_fields(raw, doc_type)` est exécuté juste après `token2json` et
corrige les défauts réels observés sur le modèle :

1. Wrapper `{"jurika": {...}}` → déballé.
2. Whitelist STRICTE par `doc_type` — toute clé hors-liste est supprimée.
3. Valeur dict imbriquée (ex. `"lieu_naissance": {"date_naissance": "..."}`)
   → si une clé interne homonyme existe, on prend `v[key]` ; sinon la 1re
   valeur scalaire utile ; sinon le champ est supprimé.
4. Tokens parasites `<unk>`, `<pad>`, etc. retirés des valeurs.
5. Trim + collapse spaces ; chaînes vides supprimées.
6. `doc_type` réinjecté (toujours = ce que l'appelant a demandé).

Whitelists canoniques (source de vérité — alignée Java + React) :

| `doc_type` | Champs |
|------------|--------|
| `cn` | `numero_cn`, `denomination`, `ice`, `beneficiaire`, `activite`, `tribunal`, `date_expiration`, `date_delivrance` |
| `cin_anc_recto` | `nom`, `prenom`, `date_naissance`, `lieu_naissance`, `date_validite`, `cin` |
| `cin_anc_verso` | `cin`, `date_validite`, `adresse`, `sexe` |
| `cin_nouv_recto` | `nom`, `prenom`, `date_naissance`, `lieu_naissance`, `cin`, `date_validite` |
| `cin_nouv_verso` | `cin`, `sexe`, `adresse` |

## Variables d'environnement

| Var | Défaut | Description |
|-----|--------|-------------|
| `DONUT_MODEL_DIR` | `/models/donut-jurika-final` | Répertoire des poids Donut. |
| `MAX_FILE_MB` | `15` | Limite stricte sur l'upload. |
| `KIE_PRELOAD` | `true` | Si `true`, charge Donut au boot (1re requête plus rapide). |
| `KIE_LOG_LEVEL` | `INFO` | Niveau de log uvicorn / app. |
