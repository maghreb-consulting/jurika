"""Tests HTTP via TestClient — endpoint /ocr avec moteur stub.

On remplace l'engine global par un stub déterministe pour valider :
 - /health
 - /info
 - /ocr (succès, fichier vide, mauvais format, engine down)

Les tests d'intégration avec les vrais modèles tournent à part.
"""

from __future__ import annotations

import io

import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app import main as app_main
from app.ocr_engine import OcrEngine, OcrLine, OcrResult


class _StubEngine(OcrEngine):
    name = "stub"

    def warmup(self) -> None:  # pragma: no cover - trivial
        return None

    def run(self, image_rgb, lang="latin"):
        assert image_rgb.dtype == np.uint8
        return OcrResult(
            text="BENATIK\nOUSSAMA",
            lines=[OcrLine(text="BENATIK", confidence=0.95)],
            confidence=0.95,
            ms=42,
        )


@pytest.fixture()
def client(monkeypatch):
    # Remplace l'état global par un moteur stub — pas de chargement réel.
    monkeypatch.setattr(app_main.state, "engine", _StubEngine())
    monkeypatch.setattr(app_main.state, "engine_name_actual", "stub")
    monkeypatch.setattr(app_main.state, "engine_name_requested", "stub")
    monkeypatch.setattr(app_main.state, "ready", True)
    monkeypatch.setattr(app_main.state, "default_lang", "latin")
    monkeypatch.setattr(app_main.state, "max_width", 1600)
    return TestClient(app_main.app)


def _png_bytes(w=200, h=120) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (w, h), color=(255, 255, 255)).save(buf, format="PNG")
    return buf.getvalue()


def test_health_up_when_engine_loaded(client):
    r = client.get("/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "UP"
    assert body["ready"] is True


def test_info_shape(client):
    r = client.get("/info")
    assert r.status_code == 200
    body = r.json()
    assert body["service"] == "ocr-service"
    assert body["engine_actual"] == "stub"
    assert body["max_width"] == 1600


def test_ocr_endpoint_success(client):
    files = {"file": ("scan.png", _png_bytes(), "image/png")}
    r = client.post("/ocr", files=files)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["text"].startswith("BENATIK")
    assert body["engine"] == "stub"
    assert body["lines"][0]["text"] == "BENATIK"
    assert body["lang"] == "latin"
    assert body["totalMs"] >= 0
    assert body["ms"] == 42  # inference ms du stub
    assert body["downscaled"] is False


def test_ocr_endpoint_explicit_lang(client):
    files = {"file": ("scan.png", _png_bytes(), "image/png")}
    r = client.post("/ocr", files=files, data={"lang": "fr"})
    assert r.status_code == 200
    assert r.json()["lang"] == "fr"


def test_ocr_endpoint_downscale(client):
    files = {"file": ("scan.png", _png_bytes(w=3200, h=2400), "image/png")}
    r = client.post("/ocr", files=files)
    assert r.status_code == 200
    assert r.json()["downscaled"] is True


def test_ocr_endpoint_empty_file(client):
    files = {"file": ("scan.png", b"", "image/png")}
    r = client.post("/ocr", files=files)
    assert r.status_code == 400


def test_ocr_endpoint_invalid_file(client):
    files = {"file": ("scan.png", b"not-an-image", "image/png")}
    r = client.post("/ocr", files=files)
    assert r.status_code == 400


def test_ocr_endpoint_503_when_engine_missing(monkeypatch):
    monkeypatch.setattr(app_main.state, "engine", None)
    monkeypatch.setattr(app_main.state, "ready", False)
    client = TestClient(app_main.app)
    files = {"file": ("scan.png", _png_bytes(), "image/png")}
    r = client.post("/ocr", files=files)
    assert r.status_code == 503

    h = client.get("/health")
    assert h.json()["status"] == "DEGRADED"
