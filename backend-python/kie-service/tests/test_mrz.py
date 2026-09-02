"""Tests parseur MRZ TD1 — déterministe, sans dépendance externe."""

from __future__ import annotations

from app.mrz import parse_mrz_td1, _yymmdd_to_ddmmyyyy


def test_yymmdd_convert_century_pre_50():
    # 26 -> 2026 (règle <50 => 20xx)
    assert _yymmdd_to_ddmmyyyy("261225") == "25.12.2026"


def test_yymmdd_convert_century_50_plus():
    # 80 -> 1980 (>=50 => 19xx)
    assert _yymmdd_to_ddmmyyyy("800101") == "01.01.1980"


def test_yymmdd_invalid_returns_none():
    assert _yymmdd_to_ddmmyyyy("abc123") is None
    assert _yymmdd_to_ddmmyyyy("261325") is None  # mois 13
    assert _yymmdd_to_ddmmyyyy("12345") is None  # trop court


def test_parse_full_td1_returns_all_common_fields():
    # Ligne 1 : type "I" + pays "MAR" + CIN "AB1234567" + filler.
    l1 = "I<MARAB1234567<0<<<<<<<<<<<<<<"
    # Ligne 2 : naissance 900512 + check + sexe M + validité 310401 + check + nat MAR.
    l2 = "9005120M3104015MAR<<<<<<<<<<<6"
    # Ligne 3 : "BENATIK<<OUSSAMA"
    l3 = "BENATIK<<OUSSAMA<<<<<<<<<<<<<<"
    text = "\n".join([l1, l2, l3])

    fields, warnings = parse_mrz_td1(text)

    assert fields["cin"] == "AB1234567"
    assert fields["date_naissance"] == "12.05.1990"
    assert fields["sexe"] == "M"
    assert fields["date_validite"] == "01.04.2031"
    assert fields["nationalite"] == "MAR"
    assert fields["nom"] == "BENATIK"
    assert fields["prenom"] == "OUSSAMA"
    assert "mrz_not_found" not in warnings


def test_parse_multiple_given_names_join_with_space():
    l1 = "I<MARCD9876543<0<<<<<<<<<<<<<<"
    l2 = "8801200F2812315MAR<<<<<<<<<<<2"
    l3 = "ALAOUI<<SALMA<FATIMA<<<<<<<<<<"
    fields, _ = parse_mrz_td1("\n".join([l1, l2, l3]))
    assert fields["nom"] == "ALAOUI"
    assert fields["prenom"] == "SALMA FATIMA"
    assert fields["sexe"] == "F"


def test_parse_empty_returns_warning():
    fields, warnings = parse_mrz_td1("")
    assert fields == {}
    assert "mrz_not_found" in warnings


def test_parse_garbage_returns_warning():
    fields, warnings = parse_mrz_td1("ceci n'est pas une MRZ")
    assert fields == {}
    assert "mrz_not_found" in warnings


def test_parse_handles_short_lines_with_padding():
    # Ligne 1 légèrement amputée (29 chars) → padding ljust("<").
    l1 = "I<MARZZ1111111<0<<<<<<<<<<<<<"  # 29 chars
    l2 = "9501010M2501015MAR<<<<<<<<<<<0"
    l3 = "DOE<<JOHN<<<<<<<<<<<<<<<<<<<<<"
    fields, _ = parse_mrz_td1("\n".join([l1, l2, l3]))
    assert fields["cin"] == "ZZ1111111"
    assert fields["nom"] == "DOE"
    assert fields["prenom"] == "JOHN"
