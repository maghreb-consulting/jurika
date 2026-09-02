"""Post-traitement déterministe de la sortie Donut (token2json).

Le modèle Donut entraîné produit, sur de vrais scans, plusieurs défauts :

1. **Wrapper "jurika"** : la sortie peut être `{"jurika": {...}}` → on déballe.
2. **Structure imbriquée parasite** : ex.
   `{"lieu_naissance": {"date_naissance": "15.05.1999"}}` ou
   `{"date_expiration": {"tribunal": "TINGHIR"}}` →
   il faut extraire une valeur scalaire utile, sinon retirer le champ.
3. **Tokens spéciaux résiduels** : "<unk>" et autres "<...>" dans les chaînes
   (souvent dans les adresses) → à supprimer.
4. **Champs hors whitelist** : Donut peut halluciner un champ non attendu
   pour le doc_type donné → on filtre strictement.

Ce module est entièrement déterministe, sans dépendance externe, et applicable
côté CPU avant {@link validation.py} (regex CIN/dates/sexe) et avant la fusion
MRZ orchestrée par {@link main.py}.

Source de vérité : whitelist par doc_type décidée côté produit. Ne JAMAIS
inférer le doc_type — il est toujours imposé par l'appelant.
"""

from __future__ import annotations

import re
from typing import Any

# ---------------------------------------------------------------------------
# Whitelist canonique par doc_type
# ---------------------------------------------------------------------------

ALLOWED_FIELDS: dict[str, frozenset[str]] = {
    "cn": frozenset({
        "numero_cn", "denomination", "ice", "beneficiaire", "activite",
        "tribunal", "date_expiration", "date_delivrance",
    }),
    "cin_anc_recto": frozenset({
        "nom", "prenom", "date_naissance", "lieu_naissance",
        "date_validite", "cin",
    }),
    "cin_anc_verso": frozenset({
        "cin", "date_validite", "adresse", "sexe",
    }),
    "cin_nouv_recto": frozenset({
        "nom", "prenom", "date_naissance", "lieu_naissance",
        "cin", "date_validite",
    }),
    "cin_nouv_verso": frozenset({
        "cin", "sexe", "adresse",
    }),
}

# Tout caractère MRZ brut transitant en aval (cas verso nouvelle CIN) est
# autorisé via un champ dédié : main.py l'extrait avant la fusion. Pour le
# post-traitement standard ce champ n'est pas dans la whitelist (Donut ne
# le produit pas en sortie token2json structurée — c'est du texte libre).

_TOKEN_PATTERN = re.compile(r"<[^>]*>")
_SPACE_PATTERN = re.compile(r"\s+")


def _strip_tokens(s: str) -> str:
    """Retire <unk> et tout token <...> résiduel, puis trim + collapse spaces."""
    cleaned = _TOKEN_PATTERN.sub("", s)
    return _SPACE_PATTERN.sub(" ", cleaned).strip()


def _flatten_value(value: Any, key: str) -> str | None:
    """Convertit la valeur d'un champ en str scalaire utile, ou None.

    Règles :
      - Si v est un dict :
        * si une clé interne == key existe, prendre v[key] récursivement.
        * sinon, prendre la 1re valeur scalaire (str/int/float/bool) trouvée.
        * si rien d'exploitable → None.
      - Si v est une list/tuple : récursion sur le 1er élément non-None.
      - Si v est None : retourne None.
      - Sinon : str(v).
    """
    if value is None:
        return None
    if isinstance(value, dict):
        # Priorité : clé interne homonyme (cas Donut "lieu_naissance":
        # {"lieu_naissance": "X"} qu'on aplatit).
        if key in value:
            return _flatten_value(value[key], key)
        # Sinon, 1re valeur scalaire trouvée.
        for v in value.values():
            if isinstance(v, (str, int, float, bool)) and not isinstance(v, bool):
                return str(v)
            # Tolérer un bool peut être trompeur ; on saute.
            if isinstance(v, bool):
                continue
        return None
    if isinstance(value, (list, tuple)):
        for v in value:
            out = _flatten_value(v, key)
            if out:
                return out
        return None
    if isinstance(value, bool):
        return None  # Donut renvoie parfois bool ; pas utile ici.
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, str):
        return value
    return None


def _unwrap_jurika(raw: Any) -> dict[str, Any]:
    """Déballe `{"jurika": {...}}` si présent. Retourne {} si entrée invalide."""
    if not isinstance(raw, dict):
        return {}
    if list(raw.keys()) == ["jurika"] and isinstance(raw["jurika"], dict):
        return dict(raw["jurika"])
    # Cas plus permissif : jurika présent parmi d'autres clés -> idem
    if "jurika" in raw and isinstance(raw["jurika"], dict):
        # On fusionne : les clés frères restent valides s'ils matchent la whitelist.
        merged = dict(raw)
        wrapped = merged.pop("jurika")
        for k, v in wrapped.items():
            merged.setdefault(k, v)
        return merged
    return dict(raw)


def clean_fields(raw: Any, doc_type: str) -> dict[str, str]:
    """Nettoie la sortie token2json pour la rendre exploitable.

    Étapes :
      1. Unwrap éventuel du wrapper "jurika".
      2. Whitelist stricte selon `doc_type` (clés hors whitelist supprimées).
      3. Pour chaque valeur :
         a. Si dict → aplatir (cf. _flatten_value).
         b. Convertir en str, retirer <unk>/<...>, trim + collapse spaces.
         c. Si chaîne vide → ne pas garder.
      4. Réinjecter "doc_type": doc_type (toujours imposé par l'appelant).
      5. Retourner un dict[str, str] uniquement.

    Args:
        raw: sortie brute de `DonutProcessor.token2json`, ou tout dict-like.
        doc_type: doc_type fourni par l'appelant (cn, cin_anc_recto, ...).

    Returns:
        Dict de champs canoniques nettoyés, valeurs str non-vides.
    """
    if doc_type not in ALLOWED_FIELDS:
        # On ne renvoie que doc_type pour un doc_type inconnu (sécurité).
        return {"doc_type": doc_type}

    unwrapped = _unwrap_jurika(raw)
    allowed = ALLOWED_FIELDS[doc_type]
    out: dict[str, str] = {}

    for key, value in unwrapped.items():
        if not isinstance(key, str):
            continue
        if key not in allowed:
            continue
        flat = _flatten_value(value, key)
        if flat is None:
            continue
        cleaned = _strip_tokens(flat)
        if not cleaned:
            continue
        out[key] = cleaned

    # doc_type est imposé par l'appelant — il vit en dehors de la whitelist.
    out["doc_type"] = doc_type
    return out
