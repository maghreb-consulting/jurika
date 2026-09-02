"""FastAPI app — endpoint /ocr.

Lance avec:
    uvicorn app.main:app --host 0.0.0.0 --port 8089

ENV:
    OCR_ENGINE        doctr | paddle           (défaut: doctr)
    OCR_DEFAULT_LANG  latin | fr | en           (défaut: latin)
    OCR_MAX_WIDTH     int                       (défaut: 1600 ; 0 = pas de downscale)
    OCR_PRELOAD       true|false                (défaut: true ; charge modèles au boot)
"""

from __future__ import annotations

import logging
import os
import time
from datetime import datetime, timezone

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from . import __version__
from .ocr_engine import OcrEngine, create_engine
from .preprocess import decode_to_rgb, downscale_if_needed, to_numpy_rgb

logging.basicConfig(
    level=os.getenv("OCR_LOG_LEVEL", "INFO"),
    format="%(asctime)s %(levelname)s %(name)s — %(message)s",
)
log = logging.getLogger("ocr-service")


# ---------------------------------------------------------------------------
# Etat global (chargé au boot)
# ---------------------------------------------------------------------------


class _State:
    engine: OcrEngine | None = None
    engine_name_requested: str = "doctr"
    engine_name_actual: str = ""
    ready: bool = False
    loaded_at: str | None = None
    default_lang: str = "latin"
    max_width: int = 1600


state = _State()


def _bool_env(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in ("1", "true", "yes", "on")


def _create_app() -> FastAPI:
    app = FastAPI(
        title="JURIKA ocr-service",
        version=__version__,
        description="OCR CPU local — docTR / PaddleOCR. CNDP-compliant, aucun appel cloud.",
    )

    @app.on_event("startup")
    def _startup() -> None:
        state.engine_name_requested = os.getenv("OCR_ENGINE", "doctr").strip().lower() or "doctr"
        state.default_lang = os.getenv("OCR_DEFAULT_LANG", "latin").strip().lower() or "latin"
        state.max_width = int(os.getenv("OCR_MAX_WIDTH", "1600"))
        preload = _bool_env("OCR_PRELOAD", True)

        # Essai du moteur demandé, puis fallback vers l'autre si install échoue.
        order = [state.engine_name_requested]
        if state.engine_name_requested == "paddle":
            order.append("doctr")
        elif state.engine_name_requested == "doctr":
            order.append("paddle")

        seen = set()
        for name in order:
            if name in seen:
                continue
            seen.add(name)
            try:
                eng = create_engine(name)
                if preload:
                    eng.warmup()
                state.engine = eng
                state.engine_name_actual = eng.name
                state.ready = True
                state.loaded_at = datetime.now(timezone.utc).isoformat()
                log.info("OCR moteur actif: %s (preload=%s, max_width=%s)",
                         eng.name, preload, state.max_width)
                break
            except Exception as exc:  # noqa: BLE001
                log.warning("Init moteur %r échouée: %s — tentative suivante", name, exc)
                continue

        if state.engine is None:
            log.error("Aucun moteur OCR n'a pu être chargé. Le service répondra 503.")
            state.ready = False

    @app.get("/health")
    def health() -> dict:
        return {
            "status": "UP" if state.ready else "DEGRADED",
            "engine": state.engine_name_actual or state.engine_name_requested,
            "ready": state.ready,
        }

    @app.get("/info")
    def info() -> dict:
        return {
            "service": "ocr-service",
            "version": __version__,
            "engine_requested": state.engine_name_requested,
            "engine_actual": state.engine_name_actual,
            "default_lang": state.default_lang,
            "max_width": state.max_width,
            "loaded_at": state.loaded_at,
        }

    @app.post("/ocr")
    async def ocr(
        file: UploadFile = File(...),
        lang: str = Form(default=""),
    ) -> JSONResponse:
        if state.engine is None or not state.ready:
            raise HTTPException(status_code=503, detail="OCR engine unavailable")

        t_total = time.time()
        data = await file.read()
        if not data:
            raise HTTPException(status_code=400, detail="Empty file")

        try:
            img = decode_to_rgb(data, file.filename)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc

        img, downscaled = downscale_if_needed(img, state.max_width)
        arr = to_numpy_rgb(img)

        chosen_lang = (lang or state.default_lang).strip().lower() or state.default_lang
        try:
            result = state.engine.run(arr, lang=chosen_lang)
        except Exception as exc:  # noqa: BLE001
            log.exception("OCR run failure: %s", exc)
            raise HTTPException(status_code=500, detail=f"OCR run failure: {exc}") from exc

        total_ms = int((time.time() - t_total) * 1000)
        payload = result.to_dict()
        payload["engine"] = state.engine_name_actual
        payload["lang"] = chosen_lang
        payload["downscaled"] = downscaled
        payload["totalMs"] = total_ms
        log.info(
            "OCR ok file=%s engine=%s lang=%s lines=%d conf=%.2f inferMs=%d totalMs=%d downscaled=%s",
            file.filename, state.engine_name_actual, chosen_lang,
            len(result.lines), result.confidence, result.ms, total_ms, downscaled,
        )
        return JSONResponse(payload)

    return app


app = _create_app()
