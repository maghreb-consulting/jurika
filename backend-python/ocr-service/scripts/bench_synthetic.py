"""Bench live — génère une CIN synthétique puis chronomètre l'OCR via /ocr.

Usage :
    py scripts/bench_synthetic.py --runs 3
    py scripts/bench_synthetic.py --runs 3 --warmup --url http://localhost:8089

La CIN synthétique imite la disposition typique d'une CIN marocaine (latin) :
nom / prénom / n° CIN / date de naissance / date d'expiration / adresse.
Couleur sombre sur fond clair, police bitmap par défaut PIL pour assurer la
reproductibilité sans dépendance à une font système. Ce n'est PAS une CIN réelle
— le but est uniquement de mesurer la latence OCR + texte produit.
"""

from __future__ import annotations

import argparse
import io
import statistics
import sys
import time
from pathlib import Path

import requests
from PIL import Image, ImageDraw, ImageFont


def build_synthetic_cin(w: int = 1200, h: int = 760) -> bytes:
    img = Image.new("RGB", (w, h), color=(244, 244, 240))
    draw = ImageDraw.Draw(img)

    # Plusieurs polices candidates Windows / Linux pour éviter le fallback bitmap
    # (qui produit un texte trop pixelisé pour un OCR honnête).
    candidates = [
        "C:/Windows/Fonts/arial.ttf",
        "C:/Windows/Fonts/segoeui.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    font_large = font_medium = None
    for c in candidates:
        if Path(c).exists():
            font_large = ImageFont.truetype(c, 42)
            font_medium = ImageFont.truetype(c, 30)
            break
    if font_large is None:
        font_large = ImageFont.load_default()
        font_medium = font_large

    # Header bandeau marron
    draw.rectangle([(0, 0), (w, 80)], fill=(60, 35, 20))
    draw.text((30, 18), "ROYAUME DU MAROC - CARTE D'IDENTITE NATIONALE",
              fill=(255, 240, 200), font=font_medium)

    # Champs
    lines = [
        ("NOM", "BENATIK"),
        ("PRENOM", "OUSSAMA"),
        ("N CIN", "AB123456"),
        ("DATE DE NAISSANCE", "15 / 01 / 1990"),
        ("LIEU DE NAISSANCE", "CASABLANCA"),
        ("DATE D EXPIRATION", "20 / 04 / 2030"),
        ("ADRESSE", "12 RUE IBN BATTUTA CASABLANCA"),
    ]
    y = 130
    for label, value in lines:
        draw.text((40, y), label, fill=(80, 80, 80), font=font_medium)
        draw.text((460, y), value, fill=(15, 15, 15), font=font_large)
        y += 75

    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def run_one(url: str, payload: bytes) -> dict:
    t0 = time.time()
    r = requests.post(
        f"{url.rstrip('/')}/ocr",
        files={"file": ("cin.png", payload, "image/png")},
        data={"lang": "latin"},
        timeout=120,
    )
    t1 = time.time()
    r.raise_for_status()
    body = r.json()
    body["_total_client_ms"] = int((t1 - t0) * 1000)
    return body


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://localhost:8089")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--warmup", action="store_true",
                        help="Compte une exécution préalable hors moyenne")
    parser.add_argument("--save", default="",
                        help="Chemin où sauvegarder le PNG synthétique (debug)")
    args = parser.parse_args()

    payload = build_synthetic_cin()
    print(f"Synthetic CIN PNG ready ({len(payload)/1024:.1f} kB)")
    if args.save:
        Path(args.save).write_bytes(payload)
        print(f"Saved to {args.save}")

    # Healthcheck
    try:
        h = requests.get(f"{args.url.rstrip('/')}/health", timeout=5).json()
        print(f"/health -> {h}")
    except Exception as exc:
        print(f"OCR service indisponible sur {args.url} : {exc}")
        return 2

    if args.warmup:
        print("warmup...")
        warm = run_one(args.url, payload)
        print(f"  warmup totalClientMs={warm['_total_client_ms']} engineMs={warm.get('ms')}")

    timings_total = []
    timings_engine = []
    text_excerpt = None
    confidences = []
    for i in range(args.runs):
        out = run_one(args.url, payload)
        timings_total.append(out["_total_client_ms"])
        timings_engine.append(int(out.get("ms", 0)))
        confidences.append(float(out.get("confidence", 0.0)))
        text_excerpt = out.get("text", "")
        print(f"  run {i+1}: totalClientMs={out['_total_client_ms']} "
              f"engineMs={out.get('ms')} chars={len(text_excerpt)} "
              f"conf={out.get('confidence'):.2f} engine={out.get('engine')}")

    print()
    print("=== résumé ===")
    print(f"  engine        : {out.get('engine')}")
    print(f"  totalClientMs : min {min(timings_total)} / "
          f"median {int(statistics.median(timings_total))} / "
          f"max {max(timings_total)}")
    print(f"  engineMs      : min {min(timings_engine)} / "
          f"median {int(statistics.median(timings_engine))} / "
          f"max {max(timings_engine)}")
    print(f"  confidence avg: {statistics.mean(confidences):.2f}")
    print()
    print("--- texte OCR (extrait) ---")
    print((text_excerpt or "")[:400])
    return 0


if __name__ == "__main__":
    sys.exit(main())
