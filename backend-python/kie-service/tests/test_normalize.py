"""Tests du module normalize — sniff PDF + rasterisation multi-page.

Sprint 2026-06-19 (Cowork) — Bug "Extraction impossible" sur upload PDF.

PyMuPDF (fitz) est en dépendance optionnelle pour la suite de tests :
- Si dispo : on crée un PDF en mémoire et on vérifie la rasterisation.
- Si absent : on s'assure que `PdfDecodeError` est levé proprement.
"""
from __future__ import annotations

import io

import pytest

from app.normalize import (
    PdfDecodeError,
    decode_to_rgb,
    is_pdf,
    pdf_first_page_to_image,
    pdf_page_count,
    pdf_page_to_image,
)


# ---------------------------------------------------------------------------
# Helpers : construction d'un PDF en mémoire (lazy fitz import)
# ---------------------------------------------------------------------------


def _make_pdf(n_pages: int = 1) -> bytes:
    fitz = pytest.importorskip(
        "fitz", reason="pymupdf requis pour ces tests live de rasterisation"
    )
    doc = fitz.open()
    for i in range(n_pages):
        page = doc.new_page(width=300, height=400)
        page.insert_text((50, 50), f"page {i}", fontsize=24)
    data = doc.tobytes()
    doc.close()
    return data


def _make_png_bytes() -> bytes:
    from PIL import Image as PILImage

    img = PILImage.new("RGB", (32, 32), color=(123, 200, 32))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


# ---------------------------------------------------------------------------
# is_pdf
# ---------------------------------------------------------------------------


def test_is_pdf_true_on_magic_header():
    assert is_pdf(b"%PDF-1.4\n%fake content") is True


def test_is_pdf_false_on_png():
    assert is_pdf(b"\x89PNG\r\n\x1a\n") is False


def test_is_pdf_false_on_empty():
    assert is_pdf(b"") is False


def test_is_pdf_false_on_short_buffer():
    assert is_pdf(b"%PDF") is False  # 4 bytes only


# ---------------------------------------------------------------------------
# pdf_page_count
# ---------------------------------------------------------------------------


def test_pdf_page_count_on_non_pdf_returns_zero():
    assert pdf_page_count(b"\x89PNG\r\n") == 0
    assert pdf_page_count(b"") == 0


def test_pdf_page_count_single_page():
    data = _make_pdf(1)
    assert pdf_page_count(data) == 1


def test_pdf_page_count_two_pages_recto_verso():
    data = _make_pdf(2)
    assert pdf_page_count(data) == 2


def test_pdf_page_count_corrupted_raises():
    # En-tête %PDF mais contenu invalide.
    bad = b"%PDF-1.4\nthis is not a real pdf"
    with pytest.raises(PdfDecodeError):
        pdf_page_count(bad)


# ---------------------------------------------------------------------------
# pdf_page_to_image
# ---------------------------------------------------------------------------


def test_pdf_page_to_image_first_page_rgb():
    data = _make_pdf(1)
    img = pdf_page_to_image(data, page_index=0, dpi=200)
    assert img.mode == "RGB"
    # 300 pts * 200/72 dpi ≈ 833 px
    assert img.width > 500
    assert img.height > 500


def test_pdf_page_to_image_second_page_of_two():
    data = _make_pdf(2)
    img = pdf_page_to_image(data, page_index=1, dpi=150)
    assert img.mode == "RGB"


def test_pdf_page_to_image_out_of_range_raises():
    data = _make_pdf(1)
    with pytest.raises(PdfDecodeError):
        pdf_page_to_image(data, page_index=5)


def test_pdf_page_to_image_negative_index_raises():
    data = _make_pdf(1)
    with pytest.raises(PdfDecodeError):
        pdf_page_to_image(data, page_index=-1)


def test_pdf_first_page_to_image_is_page_zero():
    data = _make_pdf(2)
    a = pdf_first_page_to_image(data)
    b = pdf_page_to_image(data, page_index=0, dpi=200)
    assert a.size == b.size


# ---------------------------------------------------------------------------
# decode_to_rgb : dispatch PDF vs image
# ---------------------------------------------------------------------------


def test_decode_to_rgb_dispatches_to_pdf():
    data = _make_pdf(1)
    img = decode_to_rgb(data)
    assert img.mode == "RGB"


def test_decode_to_rgb_passes_through_image():
    data = _make_png_bytes()
    img = decode_to_rgb(data)
    assert img.mode == "RGB"
    assert img.size == (32, 32)


def test_decode_to_rgb_pdf_corrupted_raises_pdf_error():
    bad = b"%PDF-1.4\nbroken"
    with pytest.raises(PdfDecodeError):
        decode_to_rgb(bad)
