#!/usr/bin/env python3
"""Smoke test bout-en-bout avec le VRAI modèle Donut.

Charge le modèle depuis ``DONUT_MODEL_DIR`` (ou ``--model-dir``), inférence sur
une image / PDF réel(le), passe la sortie par le post-traitement déterministe
(``clean_fields``) puis par la validation, et imprime le JSON final.

NE PAS committer d'images réelles (CIN, CN) — informations personnelles.

Usage :

    cd backend-python/kie-service
    .venv\\Scripts\\python.exe scripts/smoke_real.py \\
        --image "C:/path/to/cin_recto.jpg" \\
        --doc-type cin_nouv_recto \\
        --model-dir "C:/Users/HP ELITEBOOK 830 G5/Downloads/donut-jurika-final"

Sortie : JSON pretty-printed sur stdout. Code retour 0 si OK.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

# Force la stack torch AVANT l'import transformers (cf. donut_engine).
os.environ.setdefault("USE_TF", "0")
os.environ.setdefault("USE_JAX", "0")
os.environ.setdefault("USE_TORCH", "1")

# Ajoute le répertoire kie-service au PYTHONPATH pour pouvoir importer `app`.
_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_ROOT))

from app.clean import ALLOWED_FIELDS  # noqa: E402
from app.donut_engine import DonutEngine  # noqa: E402
from app.normalize import decode_to_rgb  # noqa: E402
from app.validation import validate_and_normalize  # noqa: E402


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--image", required=True, help="Chemin vers une image (JPG/PNG/TIFF) ou un PDF.")
    p.add_argument(
        "--doc-type",
        required=True,
        choices=sorted(ALLOWED_FIELDS.keys()),
        help="Type de pièce imposé (jamais inféré).",
    )
    p.add_argument(
        "--model-dir",
        default=os.environ.get("DONUT_MODEL_DIR"),
        help="Répertoire des poids Donut. Défaut : env DONUT_MODEL_DIR.",
    )
    p.add_argument(
        "--raw",
        action="store_true",
        help="Affiche aussi la sortie brute clean_fields (avant validation).",
    )
    args = p.parse_args()

    if not args.model_dir:
        print(
            "ERREUR : --model-dir absent et DONUT_MODEL_DIR non défini.",
            file=sys.stderr,
        )
        return 2
    if not Path(args.model_dir).exists():
        print(f"ERREUR : modèle introuvable à {args.model_dir}", file=sys.stderr)
        return 2

    path = Path(args.image)
    if not path.exists():
        print(f"ERREUR : image introuvable à {path}", file=sys.stderr)
        return 2

    print(f"[1/4] Chargement modèle depuis {args.model_dir} …", file=sys.stderr)
    engine = DonutEngine(model_dir=args.model_dir)
    engine.load()
    print(f"      OK — device={engine.device}", file=sys.stderr)

    print(f"[2/4] Lecture image {path.name} ({path.stat().st_size} bytes)…", file=sys.stderr)
    data = path.read_bytes()
    image = decode_to_rgb(data)

    print(f"[3/4] Inférence doc_type={args.doc_type} …", file=sys.stderr)
    cleaned = engine.infer(image, args.doc_type)

    if args.raw:
        print(
            "      Brut (clean_fields) : "
            + json.dumps(cleaned, ensure_ascii=False, indent=2),
            file=sys.stderr,
        )

    print("[4/4] Validation finale (regex CIN/dates/sexe) …", file=sys.stderr)
    validated, warnings = validate_and_normalize(cleaned)
    validated["doc_type"] = args.doc_type  # invariant

    result = {
        "doc_type": args.doc_type,
        "fields": validated,
        "warnings": warnings,
        "source": "kie",
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
