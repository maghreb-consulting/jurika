"""Pré-traitement images avant inférence OCR.

Étapes appliquées :
 1. Décodage (image bitmap OU PDF -> rasterisation 1ère page 220 DPI).
 2. Conversion RGB (les modèles attendent 3 canaux).
 3. Downscale si l'image est plus grande que ``OCR_MAX_WIDTH`` (ratio conservé).

Volontairement minimaliste — les modèles PP-OCRv4 / docTR sont robustes au flou modéré
et aux légères rotations. Un deskew global pourrait être ajouté si on observe en prod
des CIN très rotatées (rare cas).
"""

from __future__ import annotations

import io
import logging
from typing import Tuple

import numpy as np
from PIL import Image

log = logging.getLogger(__name__)


def decode_to_rgb(file_bytes: bytes, filename: str | None) -> Image.Image:
    """Décode des bytes (image OU PDF) en PIL.Image RGB.

    Retourne UNIQUEMENT la 1ère page d'un PDF (suffisant pour CIN / CN / RC scan).
    Lève ValueError si rien d'exploitable.
    """
    if not file_bytes:
        raise ValueError("Empty file")

    looks_pdf = (
        len(file_bytes) >= 4 and file_bytes[:4] == b"%PDF"
    ) or (filename or "").lower().endswith(".pdf")

    if looks_pdf:
        return _decode_pdf_first_page(file_bytes)

    return _decode_image(file_bytes)


def _decode_image(data: bytes) -> Image.Image:
    try:
        img = Image.open(io.BytesIO(data))
        img.load()
    except Exception as exc:  # noqa: BLE001
        raise ValueError(f"Image decoding failed: {exc}") from exc
    if img.mode != "RGB":
        img = img.convert("RGB")
    return img


def _decode_pdf_first_page(data: bytes, dpi: int = 220) -> Image.Image:
    """Rasterise la 1ère page d'un PDF via pypdfium2 (sans dep système)."""
    try:
        import pypdfium2 as pdfium  # local import: évite coût démarrage si pas utilisé
    except ImportError as exc:
        raise ValueError("pypdfium2 indisponible — PDF non supporté") from exc

    pdf = pdfium.PdfDocument(data)
    try:
        if len(pdf) == 0:
            raise ValueError("PDF sans page")
        page = pdf[0]
        # dpi / 72 = scale
        bitmap = page.render(scale=dpi / 72.0)
        pil = bitmap.to_pil()
        if pil.mode != "RGB":
            pil = pil.convert("RGB")
        return pil
    finally:
        pdf.close()


def downscale_if_needed(img: Image.Image, max_width: int) -> Tuple[Image.Image, bool]:
    """Redimensionne ``img`` si sa largeur dépasse ``max_width``. Conserve le ratio.

    Retourne (image, downscaled?).
    """
    if max_width <= 0 or img.width <= max_width:
        return img, False
    ratio = max_width / img.width
    new_h = int(round(img.height * ratio))
    resized = img.resize((max_width, new_h), Image.LANCZOS)
    log.debug("downscale %sx%s -> %sx%s", img.width, img.height, max_width, new_h)
    return resized, True


def to_numpy_rgb(img: Image.Image) -> np.ndarray:
    """Convertit en np.ndarray HxWx3 uint8 RGB (format attendu par docTR et Paddle)."""
    arr = np.asarray(img, dtype=np.uint8)
    if arr.ndim == 2:  # safety: grayscale
        arr = np.stack([arr, arr, arr], axis=-1)
    return arr
