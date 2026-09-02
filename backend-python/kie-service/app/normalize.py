"""Décodage entrée fichier -> image PIL RGB.

Sprint 2026-06-19 (Cowork) — Le modèle Donut attend une **image**. Les
uploads PDF (cas réel : CIN scannée en PDF) doivent être rasterisés AVANT
inférence. Ce module fournit :

  * :func:`is_pdf` — sniff magique sur les 5 premiers octets ;
  * :func:`pdf_first_page_to_image` — rendu 200 DPI de la page 0 ;
  * :func:`pdf_page_to_image` — rendu d'une page arbitraire (recto/verso
    dans un même PDF, p.ex.) ;
  * :func:`pdf_page_count` — nombre de pages (utile pour la dispatch
    recto/verso) ;
  * :func:`decode_to_rgb` — entrée unique pour le pipeline kie-service :
    PDF → page 0 image ; image → PIL.Image RGB direct.

PyMuPDF est en import paresseux pour ne pas alourdir les tests purs
(validation/clean/mrz) qui n'en ont pas besoin.
"""

from __future__ import annotations

import io
from typing import TYPE_CHECKING

from PIL import Image

if TYPE_CHECKING:  # pragma: no cover - typing only
    pass


class PdfDecodeError(Exception):
    """Levée quand un PDF est illisible (corruption, 0 page, etc.).

    Distincte d'une `ValueError` générique pour que :mod:`main` puisse
    renvoyer un message d'erreur explicite à l'employé au lieu du fourre-tout
    « Image illisible ».
    """


def is_pdf(data: bytes) -> bool:
    return len(data) >= 5 and data[:5] == b"%PDF-"


def pdf_page_count(data: bytes) -> int:
    """Nombre de pages d'un PDF. 0 si non-PDF ou corrompu."""
    if not is_pdf(data):
        return 0
    try:
        import fitz  # type: ignore[import-untyped]
    except ImportError:
        raise PdfDecodeError(
            "PyMuPDF (pymupdf) requis pour les uploads PDF mais introuvable."
        )
    try:
        doc = fitz.open(stream=data, filetype="pdf")
    except Exception as exc:  # noqa: BLE001
        raise PdfDecodeError(f"PDF illisible : {exc}") from exc
    try:
        return int(doc.page_count)
    finally:
        doc.close()


def pdf_page_to_image(data: bytes, page_index: int = 0, dpi: int = 200) -> Image.Image:
    """Rasterise UNE page d'un PDF en image RGB via PyMuPDF.

    :param data: contenu binaire du PDF.
    :param page_index: index 0-based de la page à rendre.
    :param dpi: résolution cible (>= 200 conseillé pour Donut OCR).
    :raises PdfDecodeError: si le PDF est illisible, vide, ou si la page
        demandée n'existe pas.
    """
    try:
        import fitz  # type: ignore[import-untyped]
    except ImportError:
        raise PdfDecodeError(
            "PyMuPDF (pymupdf) requis pour les uploads PDF mais introuvable. "
            "Installer `pymupdf==1.24.10` dans l'environnement kie-service."
        )

    try:
        doc = fitz.open(stream=data, filetype="pdf")
    except Exception as exc:  # noqa: BLE001
        raise PdfDecodeError(f"PDF illisible : {exc}") from exc

    try:
        if doc.page_count < 1:
            raise PdfDecodeError("PDF sans page.")
        if page_index < 0 or page_index >= doc.page_count:
            raise PdfDecodeError(
                f"Page {page_index} demandée mais le PDF n'en contient que {doc.page_count}."
            )
        page = doc.load_page(page_index)
        scale = max(72, dpi) / 72.0
        matrix = fitz.Matrix(scale, scale)
        pix = page.get_pixmap(matrix=matrix, alpha=False)
        return Image.frombytes("RGB", (pix.width, pix.height), pix.samples).copy()
    finally:
        doc.close()


def pdf_first_page_to_image(data: bytes) -> Image.Image:
    """Compat : rasterise la 1re page d'un PDF en image RGB (200 DPI)."""
    return pdf_page_to_image(data, page_index=0, dpi=200)


def decode_to_rgb(data: bytes) -> Image.Image:
    """Décode un upload (PDF ou image) en PIL.Image RGB.

    Pour un PDF, la 1re page est rendue à 200 DPI. Les PDFs multipages sont
    acceptés ; les pages suivantes restent accessibles via
    :func:`pdf_page_to_image` (le pipeline kie-service ne consomme à ce jour
    qu'UNE seule image par upload — recto et verso arrivent en 2 fichiers
    séparés depuis le front).
    """
    if is_pdf(data):
        return pdf_first_page_to_image(data)
    img = Image.open(io.BytesIO(data))
    if img.mode != "RGB":
        img = img.convert("RGB")
    return img
