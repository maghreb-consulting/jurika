"""Tests validation + normalisation."""

from __future__ import annotations

from app.validation import normalize_cin, validate_and_normalize


def test_normalize_cin_upper_and_strip():
    assert normalize_cin("ab 123 456") == "AB123456"


def test_cin_valid_no_warning():
    out, warnings = validate_and_normalize({"cin": "ab123456"})
    assert out["cin"] == "AB123456"
    assert "cin_format" not in warnings


def test_cin_invalid_warning():
    out, warnings = validate_and_normalize({"cin": "12"})
    assert out["cin"] == "12"
    assert "cin_format" in warnings


def test_date_naissance_normalized_french():
    out, warnings = validate_and_normalize({"date_naissance": "12/05/1990"})
    assert out["date_naissance"] == "12.05.1990"
    assert "date_date_naissance" not in warnings


def test_date_iso_normalized():
    out, _ = validate_and_normalize({"date_validite": "2031-04-01"})
    assert out["date_validite"] == "01.04.2031"


def test_date_invalid_warning():
    """2026-06-19 : valeur non parsable -> champ VIDE + warning low_confidence
    (avant on conservait la valeur brute, ce qui propageait "blabla" en aval).
    """
    out, warnings = validate_and_normalize({"date_naissance": "blabla"})
    assert out["date_naissance"] == ""
    assert "date_date_naissance_low_confidence" in warnings


# ---------------------------------------------------------------------------
# Sprint 2026-06-19 (Cowork) — Dates DDMMYYYY brut + cas non fiables
# ---------------------------------------------------------------------------


def test_date_eight_digits_ddmmyyyy_formatted():
    """Cas Cowork : Donut rend `16072025` -> doit devenir 16.07.2025."""
    out, warnings = validate_and_normalize({"date_validite": "16072025"})
    assert out["date_validite"] == "16.07.2025"
    assert not any("date_validite" in w for w in warnings)


def test_date_eight_digits_yyyymmdd_formatted():
    out, _ = validate_and_normalize({"date_delivrance": "20230416"})
    assert out["date_delivrance"] == "16.04.2023"


def test_date_nine_digits_marked_low_confidence_and_empty():
    """Cas Cowork : `160420223` (9 chiffres) — sortie modele erronee.
    Le champ doit ressortir VIDE pour ne pas afficher une date fausse."""
    out, warnings = validate_and_normalize({"date_delivrance": "160420223"})
    assert out["date_delivrance"] == ""
    assert "date_date_delivrance_low_confidence" in warnings


def test_date_seven_digits_marked_low_confidence():
    out, warnings = validate_and_normalize({"date_naissance": "1051990"})
    assert out["date_naissance"] == ""
    assert "date_date_naissance_low_confidence" in warnings


def test_date_impossible_month_marked_low_confidence():
    # Mois 13 -> strptime echoue, on flag.
    out, warnings = validate_and_normalize({"date_naissance": "32132025"})
    assert out["date_naissance"] == ""
    assert "date_date_naissance_low_confidence" in warnings


def test_date_validite_before_delivrance_warning():
    out, warnings = validate_and_normalize({
        "date_delivrance": "01.01.2025",
        "date_validite": "01.01.2024",
    })
    assert out["date_delivrance"] == "01.01.2025"
    assert out["date_validite"] == "01.01.2024"
    assert "date_validite_before_delivrance" in warnings


def test_date_validite_after_delivrance_no_warning():
    out, warnings = validate_and_normalize({
        "date_delivrance": "01.01.2020",
        "date_validite": "01.01.2030",
    })
    assert "date_validite_before_delivrance" not in warnings


def test_date_equal_delivrance_validite_warns():
    # Egalite => warning aussi (validite doit etre STRICTEMENT posterieure)
    out, warnings = validate_and_normalize({
        "date_delivrance": "01.01.2025",
        "date_validite": "01.01.2025",
    })
    assert "date_validite_before_delivrance" in warnings


def test_sexe_normalize_and_warn():
    out, w1 = validate_and_normalize({"sexe": "f"})
    assert out["sexe"] == "F"
    assert "sexe_format" not in w1

    out2, w2 = validate_and_normalize({"sexe": "X"})
    assert out2["sexe"] == "X"
    assert "sexe_format" in w2


def test_trim_and_collapse_spaces_text_fields():
    out, _ = validate_and_normalize({
        "nom": "  BEN  ATIK  ",
        "adresse": "12,  RUE\tDES   FLEURS",
    })
    assert out["nom"] == "BEN ATIK"
    assert out["adresse"] == "12, RUE DES FLEURS"


def test_unknown_field_is_preserved_and_collapsed():
    out, _ = validate_and_normalize({"libre": "  foo   bar  "})
    assert out["libre"] == "foo bar"


def test_none_value_is_preserved():
    out, warnings = validate_and_normalize({"nom": None})
    assert out["nom"] is None
    assert warnings == []


def test_non_string_value_passes_through():
    out, _ = validate_and_normalize({"numero": 12345})
    assert out["numero"] == 12345
