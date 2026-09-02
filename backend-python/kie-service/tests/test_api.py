"""Tests d'API — moteur Donut entièrement mocké (pas de poids chargés)."""

from __future__ import annotations

import io
from typing import Any

import pytest
from fastapi.testclient import TestClient
from PIL import Image


def _make_png_bytes(size: tuple[int, int] = (200, 120)) -> bytes:
    img = Image.new("RGB", size, color=(220, 220, 220))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


class FakeEngine:
    """Faux moteur : on contrôle la sortie sans charger transformers."""

    def __init__(self) -> None:
        self.loaded = True
        self.calls: list[tuple[str, Any]] = []
        self._next_payload: dict[str, Any] | None = None

    def set_next(self, payload: dict[str, Any]) -> None:
        self._next_payload = payload

    def load(self) -> None:  # noqa: D401 — interface
        self.loaded = True

    def infer(self, image: Any, doc_type: str) -> dict[str, Any]:
        self.calls.append((doc_type, image))
        payload = dict(self._next_payload or {})
        payload.setdefault("doc_type", doc_type)
        return payload


@pytest.fixture()
def client_and_engine():
    """Client FastAPI avec FakeEngine injecté à la place du vrai Donut."""
    from app import main

    fake = FakeEngine()
    main._State.engine = fake
    with TestClient(main.app) as client:
        # TestClient déclenche le startup ; il peut écraser _State.engine.
        # On le réinjecte après le startup pour neutraliser ça.
        main._State.engine = fake
        yield client, fake


def test_health_returns_up(client_and_engine):
    client, _ = client_and_engine
    resp = client.get("/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "UP"
    assert body["model_loaded"] is True


def test_extract_rejects_unknown_doc_type(client_and_engine):
    client, _ = client_and_engine
    resp = client.post(
        "/api/v1/kie/extract",
        files={"file": ("a.png", _make_png_bytes(), "image/png")},
        data={"doc_type": "passport"},
    )
    assert resp.status_code == 422
    assert "doc_type" in resp.json()["detail"].lower()


def test_extract_rejects_oversize(client_and_engine, monkeypatch):
    client, _ = client_and_engine
    from app import main

    # Settings est un frozen dataclass : on remplace l'instance entière.
    from app.config import Settings

    small = Settings(
        donut_model_dir=main.settings.donut_model_dir,
        max_file_mb=main.settings.max_file_mb,
        log_level=main.settings.log_level,
        preload=False,
    )
    # On force max_file_bytes via une sous-classe pour rester immuable côté config.
    class _TinySettings(Settings):
        @property
        def max_file_bytes(self) -> int:  # type: ignore[override]
            return 100

    monkeypatch.setattr(main, "settings", _TinySettings(**small.__dict__), raising=True)

    big = b"x" * 5000
    resp = client.post(
        "/api/v1/kie/extract",
        files={"file": ("a.png", big, "image/png")},
        data={"doc_type": "cn"},
    )
    assert resp.status_code == 413


def test_extract_cin_recto_returns_kie_source(client_and_engine):
    client, fake = client_and_engine
    fake.set_next({
        "nom": "  benatik  ",
        "prenom": "Oussama",
        "cin": "ab123456",
        "date_naissance": "12/05/1990",
        "sexe": "m",
        "adresse": "12, RUE  X",
    })
    resp = client.post(
        "/api/v1/kie/extract",
        files={"file": ("a.png", _make_png_bytes(), "image/png")},
        data={"doc_type": "cin_anc_recto"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["doc_type"] == "cin_anc_recto"
    assert body["source"] == "kie"
    f = body["fields"]
    # Normalisation appliquée :
    assert f["nom"] == "benatik"
    assert f["cin"] == "AB123456"
    assert f["date_naissance"] == "12.05.1990"
    assert f["sexe"] == "M"
    assert f["adresse"] == "12, RUE X"
    assert f["doc_type"] == "cin_anc_recto"


def test_extract_cin_nouv_verso_merges_mrz(client_and_engine):
    client, fake = client_and_engine
    # Donut renvoie une adresse + le bloc MRZ brut dans _mrz_text.
    mrz_text = "\n".join([
        "I<MARAB1234567<0<<<<<<<<<<<<<<",
        "9005120M3104015MAR<<<<<<<<<<<6",
        "BENATIK<<OUSSAMA<<<<<<<<<<<<<<",
    ])
    fake.set_next({
        "adresse": "12, RUE DES FLEURS, CASABLANCA",
        # Donut peut halluciner : le nom devra être ÉCRASÉ par la MRZ.
        "nom": "MAUVAIS",
        "cin": "ZZ000000",
        "_mrz_text": mrz_text,
    })
    resp = client.post(
        "/api/v1/kie/extract",
        files={"file": ("a.png", _make_png_bytes(), "image/png")},
        data={"doc_type": "cin_nouv_verso"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["source"] == "merged"
    f = body["fields"]
    # MRZ prioritaire sur les champs communs :
    assert f["nom"] == "BENATIK"
    assert f["prenom"] == "OUSSAMA"
    assert f["cin"] == "AB1234567"
    assert f["date_naissance"] == "12.05.1990"
    assert f["date_validite"] == "01.04.2031"
    assert f["sexe"] == "M"
    assert f["nationalite"] == "MAR"
    # Adresse vient toujours de Donut :
    assert f["adresse"] == "12, RUE DES FLEURS, CASABLANCA"


def test_extract_cin_nouv_verso_without_mrz_keeps_source_kie(client_and_engine):
    """2026-06-15 : le modèle Donut entraîné gère la MRZ via ses propres
    champs structurés. Si aucune MRZ brute n'est exposée, source reste "kie"
    SANS warning parasite — c'est le comportement nominal sur prod."""
    client, fake = client_and_engine
    fake.set_next({"adresse": "ailleurs"})  # pas de _mrz_text
    resp = client.post(
        "/api/v1/kie/extract",
        files={"file": ("a.png", _make_png_bytes(), "image/png")},
        data={"doc_type": "cin_nouv_verso"},
    )
    assert resp.status_code == 200
    body = resp.json()
    # Source reste "kie" si aucune MRZ n'a été utilisée.
    assert body["source"] == "kie"
    # Plus de warning "mrz_not_found" : ce n'était pas une erreur.
    assert "mrz_not_found" not in body["warnings"]


def test_extract_rejects_empty_file(client_and_engine):
    client, _ = client_and_engine
    resp = client.post(
        "/api/v1/kie/extract",
        files={"file": ("a.png", b"", "image/png")},
        data={"doc_type": "cn"},
    )
    assert resp.status_code == 400
