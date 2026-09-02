"""Tests du module clean — couvre les défauts réels observés sur le modèle Donut."""

from __future__ import annotations

import pytest

from app.clean import ALLOWED_FIELDS, _flatten_value, _strip_tokens, clean_fields


# ---------------------------------------------------------------------------
# Strip tokens
# ---------------------------------------------------------------------------


def test_strip_tokens_removes_unk():
    assert _strip_tokens("RUE <unk> X") == "RUE X"


def test_strip_tokens_removes_any_html_like_token():
    assert _strip_tokens("12 RUE <pad><unk> X<eos>") == "12 RUE X"


def test_strip_tokens_collapses_spaces_and_trims():
    assert _strip_tokens("  12 RUE\tDES   FLEURS  ") == "12 RUE DES FLEURS"


def test_strip_tokens_empty():
    assert _strip_tokens("") == ""
    assert _strip_tokens("<unk>") == ""


# ---------------------------------------------------------------------------
# Flatten value
# ---------------------------------------------------------------------------


def test_flatten_scalar_string_passes_through():
    assert _flatten_value("AB123456", "cin") == "AB123456"


def test_flatten_scalar_number_converts_to_str():
    assert _flatten_value(2027, "annee") == "2027"


def test_flatten_dict_with_homonyme_key_extracts_value():
    # Cas réel : {"lieu_naissance": {"lieu_naissance": "CASABLANCA"}}
    raw = {"lieu_naissance": "CASABLANCA"}
    assert _flatten_value(raw, "lieu_naissance") == "CASABLANCA"


def test_flatten_dict_without_homonyme_picks_first_scalar():
    # Cas réel : {"date_expiration": {"tribunal": "TINGHIR"}}
    # On prend "TINGHIR" comme 1re valeur scalaire utile.
    raw = {"tribunal": "TINGHIR"}
    assert _flatten_value(raw, "date_expiration") == "TINGHIR"


def test_flatten_nested_dict_recurses_via_homonyme():
    # {"lieu_naissance": {"lieu_naissance": {"lieu_naissance": "X"}}}
    raw = {"lieu_naissance": {"lieu_naissance": "X"}}
    assert _flatten_value(raw, "lieu_naissance") == "X"


def test_flatten_empty_dict_returns_none():
    assert _flatten_value({}, "cin") is None


def test_flatten_none_returns_none():
    assert _flatten_value(None, "cin") is None


def test_flatten_list_picks_first_non_empty():
    assert _flatten_value(["", "AB123456", "CD000"], "cin") == "AB123456"


def test_flatten_bool_is_dropped():
    assert _flatten_value(True, "valid") is None


# ---------------------------------------------------------------------------
# clean_fields — bout en bout
# ---------------------------------------------------------------------------


def test_clean_unwraps_jurika_wrapper():
    raw = {"jurika": {"nom": "BENATIK", "cin": "AB123456"}}
    out = clean_fields(raw, "cin_anc_recto")
    assert out["nom"] == "BENATIK"
    assert out["cin"] == "AB123456"
    assert out["doc_type"] == "cin_anc_recto"


def test_clean_flattens_nested_dict_parasite():
    # Défaut RÉEL : {"lieu_naissance": {"date_naissance": "15.05.1999"}}
    # → aplati : pas de clé interne homonyme, on prend la 1re valeur scalaire
    #   = "15.05.1999". (Le post-traitement validation.py se chargera de
    #   warner si la valeur ne ressemble pas à un lieu de naissance.)
    raw = {"lieu_naissance": {"date_naissance": "15.05.1999"}}
    out = clean_fields(raw, "cin_anc_recto")
    assert out["lieu_naissance"] == "15.05.1999"


def test_clean_strips_unk_tokens_from_address():
    raw = {"adresse": "12 RUE <unk> DES FLEURS", "cin": "AB123456", "sexe": "M"}
    out = clean_fields(raw, "cin_nouv_verso")
    assert out["adresse"] == "12 RUE DES FLEURS"
    assert out["cin"] == "AB123456"
    assert out["sexe"] == "M"


def test_clean_removes_keys_outside_whitelist():
    # Donut peut halluciner des clés hors doc_type : on filtre strictement.
    raw = {
        "cin": "AB123456",
        "sexe": "M",
        "adresse": "RUE X",
        "ice": "001234567000077",   # PAS dans cin_nouv_verso
        "nom": "BENATIK",            # PAS dans cin_nouv_verso non plus
    }
    out = clean_fields(raw, "cin_nouv_verso")
    assert set(out.keys()) - {"doc_type"} == {"cin", "sexe", "adresse"}
    assert "ice" not in out
    assert "nom" not in out


def test_clean_drops_empty_strings():
    raw = {"nom": "BENATIK", "prenom": "  ", "cin": ""}
    out = clean_fields(raw, "cin_anc_recto")
    assert out["nom"] == "BENATIK"
    assert "prenom" not in out
    assert "cin" not in out


def test_clean_returns_only_strings():
    raw = {"cin": 123456, "sexe": "M"}
    out = clean_fields(raw, "cin_nouv_verso")
    for k, v in out.items():
        assert isinstance(v, str), f"{k}={v!r}"


def test_clean_always_reinjects_doc_type():
    raw = {}
    out = clean_fields(raw, "cin_anc_recto")
    assert out == {"doc_type": "cin_anc_recto"}


def test_clean_doc_type_imposed_even_if_donut_emits_other():
    # Donut pourrait à tort réémettre doc_type — on l'écrase.
    raw = {"doc_type": "cn", "cin": "AB123456"}
    out = clean_fields(raw, "cin_anc_recto")
    assert out["doc_type"] == "cin_anc_recto"


def test_clean_unknown_doc_type_returns_only_doc_type():
    out = clean_fields({"nom": "X"}, "passport")
    assert out == {"doc_type": "passport"}


def test_clean_invalid_raw_returns_only_doc_type():
    # Donut a échoué et renvoyé un str / list / None : on dégrade proprement.
    assert clean_fields("not a dict", "cin_anc_recto") == {"doc_type": "cin_anc_recto"}
    assert clean_fields(None, "cin_anc_recto") == {"doc_type": "cin_anc_recto"}
    assert clean_fields([1, 2, 3], "cin_anc_recto") == {"doc_type": "cin_anc_recto"}


def test_clean_full_cn_canonical_passthrough():
    # Cas nominal CN : tous les champs canoniques sont préservés.
    raw = {
        "numero_cn": "987654",
        "denomination": "ATLAS HOLDING SA",
        "ice": "001234567000077",
        "beneficiaire": "Karim ATLAS",
        "activite": "Négoce général",
        "tribunal": "Casablanca",
        "date_expiration": "31.12.2030",
        "date_delivrance": "01.01.2025",
    }
    out = clean_fields(raw, "cn")
    for k, v in raw.items():
        assert out[k] == v
    assert out["doc_type"] == "cn"


def test_clean_cin_anc_verso_canonical_whitelist():
    # cin_anc_verso : whitelist stricte {cin, date_validite, adresse, sexe}.
    raw = {
        "cin": "AB123456",
        "date_validite": "01.04.2031",
        "adresse": "12 RUE X",
        "sexe": "M",
        # Hors-whitelist :
        "nom": "BENATIK",
        "prenom": "OUSSAMA",
    }
    out = clean_fields(raw, "cin_anc_verso")
    assert "nom" not in out
    assert "prenom" not in out
    assert out["cin"] == "AB123456"
    assert out["date_validite"] == "01.04.2031"
    assert out["adresse"] == "12 RUE X"
    assert out["sexe"] == "M"


def test_clean_dict_value_then_strip_tokens():
    # Combine flatten + strip : {"adresse": {"adresse": "RUE <unk> X"}}
    raw = {"adresse": {"adresse": "RUE <unk> X"}, "cin": "AB123456", "sexe": "M"}
    out = clean_fields(raw, "cin_nouv_verso")
    assert out["adresse"] == "RUE X"


def test_clean_nested_date_expiration_tribunal_real_case():
    # Cas RÉEL CN : {"date_expiration": {"tribunal": "TINGHIR"}}
    # On extrait "TINGHIR" même si c'est sémantiquement un tribunal —
    # la validation aval warnera (date_format) ; meilleur que de jeter
    # silencieusement.
    raw = {
        "numero_cn": "987654",
        "denomination": "ATLAS",
        "date_expiration": {"tribunal": "TINGHIR"},
    }
    out = clean_fields(raw, "cn")
    assert out["date_expiration"] == "TINGHIR"
    assert out["numero_cn"] == "987654"


def test_clean_allowed_fields_snapshot():
    """Snapshot anti-régression : les whitelists doivent rester strictes."""
    assert ALLOWED_FIELDS["cn"] == frozenset({
        "numero_cn", "denomination", "ice", "beneficiaire", "activite",
        "tribunal", "date_expiration", "date_delivrance",
    })
    assert ALLOWED_FIELDS["cin_anc_recto"] == frozenset({
        "nom", "prenom", "date_naissance", "lieu_naissance",
        "date_validite", "cin",
    })
    assert ALLOWED_FIELDS["cin_anc_verso"] == frozenset({
        "cin", "date_validite", "adresse", "sexe",
    })
    assert ALLOWED_FIELDS["cin_nouv_recto"] == frozenset({
        "nom", "prenom", "date_naissance", "lieu_naissance",
        "cin", "date_validite",
    })
    assert ALLOWED_FIELDS["cin_nouv_verso"] == frozenset({
        "cin", "sexe", "adresse",
    })


def test_clean_jurika_merged_with_siblings():
    # Cas pervers : {"jurika": {"nom": "X"}, "cin": "Y"} -> on fusionne.
    raw = {"jurika": {"nom": "BENATIK"}, "cin": "AB123456"}
    out = clean_fields(raw, "cin_anc_recto")
    assert out["nom"] == "BENATIK"
    assert out["cin"] == "AB123456"
