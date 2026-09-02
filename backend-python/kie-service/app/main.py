"""FastAPI app — endpoints /health et /api/v1/kie/extract.

Lancer en local :
    uvicorn app.main:app --host 0.0.0.0 --port 8088
"""

from __future__ import annotations

import logging

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from .config import SUPPORTED_DOC_TYPES, load_settings
from .donut_engine import DonutEngine
from .mrz import parse_mrz_td1
from .normalize import PdfDecodeError, decode_to_rgb, is_pdf, pdf_page_count
from .schemas import ExtractResponse, HealthResponse
from .validation import validate_and_normalize

settings = load_settings()

logging.basicConfig(
    level=settings.log_level,
    format="%(asctime)s %(levelname)s %(name)s — %(message)s",
)
log = logging.getLogger("kie-service")


class _State:
    engine: DonutEngine | None = None


app = FastAPI(title="JURIKA kie-service", version="0.1.0")


@app.on_event("startup")
def _startup() -> None:
    _State.engine = DonutEngine(model_dir=settings.donut_model_dir)
    if settings.preload:
        try:
            _State.engine.load()
        except Exception as exc:  # pragma: no cover — défensif
            log.error("Échec du chargement Donut : %s", exc)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="UP",
        model_loaded=bool(_State.engine and _State.engine.loaded),
    )


@app.post("/api/v1/kie/extract", response_model=ExtractResponse)
async def extract(
    file: UploadFile = File(..., description="Image (JPG/PNG/TIFF) ou PDF (1re page utilisée)."),
    doc_type: str = Form(..., description="Type imposé par l'appelant."),
) -> ExtractResponse:
    if doc_type not in SUPPORTED_DOC_TYPES:
        raise HTTPException(
            status_code=422,
            detail=f"doc_type inconnu : {doc_type}. Valeurs autorisées : {sorted(SUPPORTED_DOC_TYPES)}",
        )

    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Fichier vide.")
    if len(data) > settings.max_file_bytes:
        raise HTTPException(
            status_code=413,
            detail=f"Fichier trop volumineux (> {settings.max_file_mb} Mo).",
        )

    # 2026-06-19 (Cowork) — Sprint kie-service : on rasterise les PDFs en image
    # AVANT inference (Donut attend une image). Si le PDF est illisible on
    # remonte un message dedie (avant on disait juste "Image illisible").
    try:
        if is_pdf(data):
            try:
                n_pages = pdf_page_count(data)
                log.info("Upload PDF detecte (%d page(s)) — rendu de la page 0", n_pages)
            except PdfDecodeError:
                # On laisse decode_to_rgb lever proprement ci-dessous.
                pass
        image = decode_to_rgb(data)
    except PdfDecodeError as exc:
        raise HTTPException(
            status_code=400,
            detail=(
                f"PDF illisible : {exc}. Vérifiez que le fichier n'est pas corrompu "
                "et qu'il contient au moins une page. La saisie manuelle reste disponible."
            ),
        ) from exc
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Image illisible : {exc}") from exc

    if _State.engine is None:
        raise HTTPException(status_code=503, detail="Moteur Donut non initialisé.")

    try:
        fields = _State.engine.infer(image, doc_type)
    except Exception as exc:  # pragma: no cover — défensif
        log.exception("Inférence Donut échouée")
        raise HTTPException(status_code=500, detail=f"Inférence Donut échouée : {exc}") from exc

    warnings: list[str] = []
    source = "kie"

    # 2026-06-15 — La fusion MRZ TD1 reste possible si Donut produit
    # explicitement un texte MRZ brut (clé "_mrz_text", "mrz" ou "text").
    # Avec le modèle entraîné, ces clés sont normalement filtrées par
    # clean_fields ; la fusion devient un no-op silencieux et "source"
    # reste "kie". On ne lève PAS de warning "mrz_not_found" : c'est le
    # comportement attendu quand le modèle structuré gère lui-même la MRZ.
    if doc_type == "cin_nouv_verso":
        raw_text = fields.get("_mrz_text") or fields.get("mrz") or fields.get("text") or ""
        if isinstance(raw_text, str) and raw_text:
            mrz_fields, mrz_warnings = parse_mrz_td1(raw_text)
            warnings.extend(mrz_warnings)
            if mrz_fields:
                # MRZ PRIORITAIRE sur les champs communs.
                for key, value in mrz_fields.items():
                    fields[key] = value
                source = "merged"

    # Réinjecte doc_type côté serveur (sécurité, déjà fait par clean_fields).
    fields["doc_type"] = doc_type

    normalized, post_warnings = validate_and_normalize(fields)
    warnings.extend(post_warnings)

    # On garantit que doc_type renvoyé == doc_type appelant, même si Donut
    # avait halluciné autre chose.
    normalized["doc_type"] = doc_type

    return ExtractResponse(
        doc_type=doc_type,
        fields=normalized,
        source=source,
        warnings=warnings,
    )
