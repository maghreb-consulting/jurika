"""Tests contrat OcrEngine — sans charger les vrais modèles.

On vérifie ici la structure du contrat (factory, OcrResult sérialisation, weighted
confidence). Les tests d'intégration avec les modèles réels (docTR, Paddle) tournent
en local quand les libs sont installées — voir tests/integration/.
"""

from __future__ import annotations

import pytest

from app.ocr_engine import (
    OcrLine,
    OcrResult,
    _weighted_confidence,
    create_engine,
)


def test_factory_doctr():
    eng = create_engine("doctr")
    assert eng.name == "doctr"


def test_factory_paddle():
    eng = create_engine("paddle")
    assert eng.name == "paddle"


def test_factory_aliases():
    assert create_engine("Doctr").name == "doctr"
    assert create_engine("paddleocr").name == "paddle"
    assert create_engine("MINDEE").name == "doctr"
    assert create_engine("PP").name == "paddle"


def test_factory_unknown_raises():
    with pytest.raises(ValueError):
        create_engine("tesseract")


def test_weighted_confidence_empty():
    assert _weighted_confidence([]) == 0.0


def test_weighted_confidence_weighted_by_length():
    lines = [
        OcrLine(text="X", confidence=1.0),         # weight=1
        OcrLine(text="HELLO WORLD", confidence=0.5),  # weight=11
    ]
    c = _weighted_confidence(lines)
    # Plus pondéré par HELLO WORLD que par X
    assert 0.5 < c < 0.6


def test_result_to_dict_shape():
    r = OcrResult(
        text="BENATIK\nOUSSAMA",
        lines=[OcrLine(text="BENATIK", confidence=0.97, bbox=[10, 20, 100, 50])],
        confidence=0.97,
        ms=1234,
    )
    d = r.to_dict()
    assert d["text"] == "BENATIK\nOUSSAMA"
    assert d["lines"][0]["text"] == "BENATIK"
    assert d["lines"][0]["confidence"] == 0.97
    assert d["lines"][0]["bbox"] == [10, 20, 100, 50]
    assert d["ms"] == 1234


def test_line_to_dict_rounds_confidence():
    ln = OcrLine(text="X", confidence=0.123456789)
    d = ln.to_dict()
    assert d["confidence"] == 0.1235
