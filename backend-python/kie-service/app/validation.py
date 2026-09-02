"""Validation + normalisation post-extraction.

Ne BLOQUE jamais : remplit `warnings`. Les champs reconnus sont normalisés
(trim, collapse spaces, dates en DD.MM.YYYY, sexe en {M,F}).

Sprint 2026-06-19 (Cowork) — Dates :
  * Une chaîne ne contenant que des chiffres et de longueur 8 est interprétée
    comme `DDMMYYYY` (cas Donut qui rend "16072025" au lieu de "16/07/2025").
  * Une chaîne ne ressemblant à aucun format de date connu OU contenant un
    nombre de chiffres invalide (≠ 8 si on est dans un cas "raw digits") OU
    représentant une date impossible (mois>12, jour>31) est marquée
    NON FIABLE : le champ ressort à vide (`""`) + warning
    `date_<field>_low_confidence` — on ne propage JAMAIS une date erronée.
  * `date_validite` doit être postérieure à `date_delivrance` ; sinon
    warning `date_validite_before_delivrance` (on garde les deux valeurs,
    c'est l'employé qui tranche).
"""

from __future__ import annotations

import re
from datetime import datetime
from typing import Any

CIN_RE = re.compile(r"^[A-Z]{1,2}[0-9]{5,7}$")
SEXE_VALUES = {"M", "F"}

_SPACE_RE = re.compile(r"\s+")
_DIGITS_ONLY_RE = re.compile(r"^\d+$")

DATE_FIELDS = {
    "date_naissance",
    "date_validite",
    "date_expiration",
    "date_delivrance",
}

TEXT_FIELDS_TO_TRIM = {
    "nom",
    "prenom",
    "lieu_naissance",
    "adresse",
    "nationalite",
    "denomination",
    "beneficiaire",
    "activite",
    "tribunal",
    "ice",
    "numero_cn",
    "cin",
    "sexe",
}


def _collapse(s: str) -> str:
    return _SPACE_RE.sub(" ", s.strip())


def _try_parse_date(raw: str) -> str | None:
    """Tente plusieurs formats. Retourne 'DD.MM.YYYY' ou None.

    Sprint 2026-06-19 (Cowork) — accepte aussi :
      * `DDMMYYYY` (8 chiffres, sans séparateur) — cas typique du modèle Donut
        qui rend la date brute sans formatage (« 16072025 » → 16.07.2025).
      * `YYYYMMDD` (8 chiffres, format ISO compact).

    Si la chaîne ne contient QUE des chiffres mais que la longueur n'est pas
    8, ou si la date est impossible (mois > 12, jour > 31) → renvoie None
    pour que l'appelant marque le champ NON FIABLE.
    """
    if not raw:
        return None
    s = raw.strip().replace(" ", "").replace("-", "").replace(".", "").replace("/", "")
    # Cas "raw digits only" : on n'accepte QUE 8 chiffres exactement, sinon None.
    if _DIGITS_ONLY_RE.match(s):
        if len(s) != 8:
            return None
        for fmt in ("%d%m%Y", "%Y%m%d"):
            try:
                dt = datetime.strptime(s, fmt)
                return dt.strftime("%d.%m.%Y")
            except ValueError:
                continue
        return None
    # Sinon : on retente sur la chaine d'origine avec des séparateurs.
    s2 = raw.strip().replace(" ", "")
    candidates = [
        "%d.%m.%Y",
        "%d/%m/%Y",
        "%d-%m-%Y",
        "%Y-%m-%d",
        "%Y/%m/%d",
        "%d.%m.%y",
    ]
    for fmt in candidates:
        try:
            dt = datetime.strptime(s2, fmt)
            if dt.year < 100:
                # safety net (strptime %y gère déjà mais on garde explicite)
                dt = dt.replace(year=dt.year + (2000 if dt.year < 50 else 1900))
            return dt.strftime("%d.%m.%Y")
        except ValueError:
            continue
    return None


def _parse_date_obj(normalized: str) -> datetime | None:
    """Parse une date au format pivot 'DD.MM.YYYY'. Retourne None si invalide."""
    try:
        return datetime.strptime(normalized, "%d.%m.%Y")
    except (ValueError, TypeError):
        return None


def normalize_cin(value: str) -> str:
    return value.upper().replace(" ", "")


def validate_and_normalize(fields: dict[str, Any]) -> tuple[dict[str, Any], list[str]]:
    """Retourne (fields normalisés, warnings).

    Tolérant aux valeurs non-str (laisse passer tel quel) ; ne supprime pas
    les champs inconnus.
    """
    warnings: list[str] = []
    out: dict[str, Any] = {}

    for key, value in fields.items():
        if value is None:
            out[key] = value
            continue

        if not isinstance(value, str):
            out[key] = value
            continue

        cleaned = _collapse(value)

        if key in DATE_FIELDS:
            parsed = _try_parse_date(cleaned)
            if parsed is None:
                # Sprint 2026-06-19 (Cowork) — on n'affiche JAMAIS une date
                # erronee : on vide le champ et on remonte un warning
                # `low_confidence` pour que le front l'indique a l'employe
                # (champ a saisir manuellement).
                warnings.append(f"date_{key}_low_confidence")
                out[key] = ""
            else:
                out[key] = parsed
            continue

        if key == "cin":
            normalized = normalize_cin(cleaned)
            if not CIN_RE.match(normalized):
                warnings.append("cin_format")
            out[key] = normalized
            continue

        if key == "sexe":
            up = cleaned.upper()
            if up not in SEXE_VALUES:
                warnings.append("sexe_format")
                out[key] = up
            else:
                out[key] = up
            continue

        if key in TEXT_FIELDS_TO_TRIM:
            out[key] = cleaned
            continue

        out[key] = cleaned

    # Sprint 2026-06-19 (Cowork) — coherence date_validite > date_delivrance.
    dv = out.get("date_validite") if isinstance(out.get("date_validite"), str) else None
    dd = out.get("date_delivrance") if isinstance(out.get("date_delivrance"), str) else None
    if dv and dd:
        dv_obj = _parse_date_obj(dv)
        dd_obj = _parse_date_obj(dd)
        if dv_obj and dd_obj and dv_obj <= dd_obj:
            warnings.append("date_validite_before_delivrance")

    return out, warnings
