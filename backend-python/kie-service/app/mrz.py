"""Parseur MRZ TD1 déterministe (carte d'identité ICAO 9303 partie 5).

Format TD1 : 3 lignes de 30 caractères.

Ligne 1 :
  pos 0-1   : type ("I", "IA", "IC", etc.)
  pos 2-4   : pays émetteur (ex "MAR")
  pos 5-13  : numéro document (CIN)  + filler "<"
  pos 14    : checksum
  pos 15-29 : optionnel (ignoré ici)

Ligne 2 :
  pos 0-5   : date de naissance YYMMDD
  pos 6     : checksum
  pos 7     : sexe (M/F/<)
  pos 8-13  : date d'expiration YYMMDD
  pos 14    : checksum
  pos 15-17 : nationalité (ex "MAR")
  pos 18-28 : optionnel
  pos 29    : checksum composite

Ligne 3 :
  Nom et prénoms : "NOM<<PRENOMS" — filler "<" remplace les espaces, padding
  jusqu'à 30 chars.

AUCUNE dépendance externe. Robuste si MRZ absente : retourne dict vide +
warning. Conversion YYMMDD -> "DD.MM.YYYY" avec règle siècle (<50 => 20xx,
sinon 19xx).
"""

from __future__ import annotations

import re
from typing import Any

_NON_MRZ_RE = re.compile(r"[^A-Z0-9<]")


def _yymmdd_to_ddmmyyyy(raw: str) -> str | None:
    if len(raw) != 6 or not raw.isdigit():
        return None
    yy = int(raw[0:2])
    mm = int(raw[2:4])
    dd = int(raw[4:6])
    if not (1 <= mm <= 12 and 1 <= dd <= 31):
        return None
    year = 2000 + yy if yy < 50 else 1900 + yy
    return f"{dd:02d}.{mm:02d}.{year}"


def _strip_fillers(s: str) -> str:
    return s.replace("<", " ").strip()


def _split_lines(text: str) -> list[str]:
    """Sépare en lignes brutes, garde les caractères MRZ uniquement par ligne."""
    raw_lines = [ln.strip() for ln in text.replace("\r\n", "\n").split("\n") if ln.strip()]
    cleaned: list[str] = []
    for ln in raw_lines:
        up = ln.upper()
        # Conserve uniquement les caractères MRZ valides.
        compact = _NON_MRZ_RE.sub("", up)
        if len(compact) >= 28:  # tolère 28-30 (OCR mange parfois 1-2 chars)
            cleaned.append(compact[:30].ljust(30, "<"))
    return cleaned


def parse_mrz_td1(text: str) -> tuple[dict[str, Any], list[str]]:
    """Parse un bloc texte contenant potentiellement une MRZ TD1.

    Retourne (fields, warnings). Si pas de MRZ détectable :
    fields == {}, warnings == ["mrz_not_found"].
    """
    if not text:
        return {}, ["mrz_not_found"]

    lines = _split_lines(text)
    if len(lines) < 3:
        return {}, ["mrz_not_found"]

    # Cherche 3 lignes consécutives plausibles.
    triplet: tuple[str, str, str] | None = None
    for i in range(len(lines) - 2):
        l1, l2, l3 = lines[i], lines[i + 1], lines[i + 2]
        # Ligne 1 commence souvent par "I" (carte) ou "C" pour TD1.
        if l1[0] in ("I", "C", "A") and any(c.isdigit() for c in l2[:6]):
            triplet = (l1, l2, l3)
            break

    if triplet is None:
        return {}, ["mrz_not_found"]

    l1, l2, l3 = triplet
    warnings: list[str] = []
    fields: dict[str, Any] = {}

    # CIN (ligne 1 pos 5-13)
    raw_cin = l1[5:14].replace("<", "").strip()
    if raw_cin:
        fields["cin"] = raw_cin

    # Date de naissance (ligne 2 pos 0-5)
    dn = _yymmdd_to_ddmmyyyy(l2[0:6])
    if dn:
        fields["date_naissance"] = dn
    else:
        warnings.append("mrz_date_naissance")

    # Sexe (ligne 2 pos 7)
    sexe = l2[7]
    if sexe in ("M", "F"):
        fields["sexe"] = sexe

    # Date validité (ligne 2 pos 8-13)
    dv = _yymmdd_to_ddmmyyyy(l2[8:14])
    if dv:
        fields["date_validite"] = dv
    else:
        warnings.append("mrz_date_validite")

    # Nationalité (ligne 2 pos 15-17)
    nat = l2[15:18].replace("<", "").strip()
    if nat:
        fields["nationalite"] = nat

    # Nom / prénoms (ligne 3) : "NOM<<PRENOMS<..."
    name_part = l3
    if "<<" in name_part:
        nom_raw, prenoms_raw = name_part.split("<<", 1)
        nom = _strip_fillers(nom_raw)
        # Coupe les fillers de queue éventuels.
        prenoms = _strip_fillers(prenoms_raw)
        if nom:
            fields["nom"] = nom
        if prenoms:
            # Si plusieurs prénoms séparés par "<", joindre avec espace.
            fields["prenom"] = " ".join(p for p in prenoms.split(" ") if p)
    else:
        # Ligne 3 dégénérée : warning.
        warnings.append("mrz_names")

    return fields, warnings
