# -*- coding: utf-8 -*-
"""
Lot DIVERS §C (2026-08-13) — VARIANTE ÉTRANGÈRE de l'annonce d'ouverture de succursale.

  ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL      (dérivé du 05_ SARL)
  ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL_AU   (dérivé du 05_ SARL AU)

⚠️  CES DEUX MODÈLES NE VIENNENT PAS DU DIRECTEUR.
Le directeur n'a livré que la variante « société mère MAROCAINE » (dossier
05_SUCCURSALE_OUVERTURE), dont le chapeau est explicitement marocain
(« $DENOMINATION SARL au capital de $CAPITAL_CHIFFRES **DH** — RC N° $RC_NUMERO »)
et qui ne comporte AUCUNE variable `$SOCIETE_MERE_*`. Une succursale marocaine
d'une société étrangère ne peut donc pas utiliser ce modèle tel quel.

RÈGLE DE DÉRIVATION (décision utilisateur, spec §C) :
  - on part du modèle 05_ et on CONSERVE sa structure et sa formulation ;
  - on remplace UNIQUEMENT le chapeau d'identification par les variables
    `$SOCIETE_MERE_*` (dénomination, forme, pays, capital + devise, siège,
    registre + numéro, loi applicable) ;
  - le corps (enseigne, adresse, activité, date d'ouverture, blocs conditionnels
    dotation / responsable, immatriculation au RC de `$SUCCURSALE_VILLE_GREFFE`)
    reste STRICTEMENT INCHANGÉ, mot pour mot ;
  - AUCUNE mention nouvelle : seules des variables déjà définies (dictionnaire des
    annonces + `$SOCIETE_MERE_*` / `$ORGANE_COMPETENT` du dictionnaire des
    assemblées, déjà consommées par PV_CREATION_SUCCURSALE_ETRANGERE_*).

TROIS PARAGRAPHES SEULEMENT sont modifiés (indices dans le modèle 05_ dérivé) :

  [2]  chapeau d'identification de la société  → `$SOCIETE_MERE_*`
  [4]  « Aux termes de … les associés … ont décidé » → « Aux termes de la décision
       de `$ORGANE_COMPETENT` … la société … a décidé » (l'organe compétent porte
       la distinction pluripersonnel / unipersonnel, comme dans le PV étranger)
  [20] dépôt légal : `$VILLE_GREFFE` (greffe du siège MAROCAIN, inexistant ici)
       → `$SUCCURSALE_VILLE_GREFFE` ; la queue « RC N° $RC_NUMERO » est retirée
       (la société mère n'a pas de RC marocain ; celui de la succursale est
       attribué APRÈS ce dépôt).

Les modèles produits portent `origin: "derive-jurika"` dans le manifest —
distinct de `directeur-2026-08` — et **doivent être validés par le directeur**.

Le script est réexécutable : il repart de la sortie de
`integrate-succursale-annonces-templates-2026-08-13.py` (elle-même issue de la
source directeur), afin qu'une correction du directeur se propage ici.

Usage :
  python scripts/integrate-succursale-annonces-templates-2026-08-13.py
  python scripts/derive-succursale-etrangere-annonce-2026-08-13.py
"""
import copy
import io
import json
import os
import shutil
import sys

from docx import Document
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
DOCX_DIR = os.path.join(ROOT, "backend-java", "ai-service", "src", "main",
                        "resources", "templates", "docx")
BASELINE_DIR = os.path.join(ROOT, "backend-java", "ai-service", "src", "test",
                            "resources", "directeur-baseline")

TITRE_SZ = "32"
CORPS_SZ = "22"
NAVY = "1F4E79"
NOIR = "000000"

# Chapeau d'identification — seul bloc réécrit (paragraphe [2]).
CHAPEAU = (
    "$SOCIETE_MERE_DENOMINATION",             # rendu en gras (comme $DENOMINATION)
    " $SOCIETE_MERE_FORME au capital de $SOCIETE_MERE_CAPITAL"
    " — Siège social : $SOCIETE_MERE_SIEGE, $SOCIETE_MERE_PAYS"
    " — $SOCIETE_MERE_REGISTRE N° $SOCIETE_MERE_REGISTRE_NUMERO"
    " — société régie par $SOCIETE_MERE_LOI_APPLICABLE.",
)

# Attendu du paragraphe [4] source, par variante (garde-fou : si le directeur
# reformule sa phrase, le script s'arrête au lieu de dériver un texte périmé).
ATTENDU_4 = {
    "SARL": "Aux termes de l'assemblée générale extraordinaire en date du $ASSEMBLEE_DATE,",
    "SARL_AU": "Aux termes de la décision de l'associé unique en date du $ASSEMBLEE_DATE,",
}

# Phrase d'ouverture dérivée (paragraphe [4]) — même structure, organe générique.
#
# ⚠ « prise PAR » et non « de » : $ORGANE_COMPETENT est une valeur saisie qui commence
# par son article (« le conseil d'administration », « l'associé unique », « les
# actionnaires »). « de » aurait produit « de le conseil d'administration ». « par »
# se combine avec n'importe quel article sans élision.
OUVERTURE_4 = (
    "Aux termes de la décision prise par $ORGANE_COMPETENT en date du $ASSEMBLEE_DATE,"
    " la société ",
    "$SOCIETE_MERE_DENOMINATION",             # gras
    " $SOCIETE_MERE_FORME au capital de $SOCIETE_MERE_CAPITAL, a décidé :",
)

DEPOT_SRC = "Le dépôt légal a été effectué au Greffier du tribunal de Commerce de $VILLE_GREFFE,"
DEPOT_DERIVE = (
    "Le dépôt légal a été effectué au Greffier du tribunal de Commerce de"
    " $SUCCURSALE_VILLE_GREFFE, le $DATE_DEPOT_LEGAL sous le numéro"
    " $DEPOT_LEGAL_NUMERO."
)

JOBS = [
    ("ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL",
     "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL", "SARL"),
    ("ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL_AU",
     "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL_AU", "SARL_AU"),
]

# Variables qui ne doivent PLUS apparaître (elles désignent une mère marocaine).
INTERDITES = ("$DENOMINATION", "$CAPITAL_CHIFFRES", "$SIEGE_SOCIAL",
              "$RC_NUMERO", "$VILLE_GREFFE ")
REQUISES = ("$SOCIETE_MERE_DENOMINATION", "$SOCIETE_MERE_FORME", "$SOCIETE_MERE_PAYS",
            "$SOCIETE_MERE_CAPITAL", "$SOCIETE_MERE_SIEGE", "$SOCIETE_MERE_REGISTRE",
            "$SOCIETE_MERE_REGISTRE_NUMERO", "$SOCIETE_MERE_LOI_APPLICABLE",
            "$ORGANE_COMPETENT", "$ASSEMBLEE_DATE",
            "$SUCCURSALE_ENSEIGNE", "$SUCCURSALE_ADRESSE", "$SUCCURSALE_VILLE",
            "$SUCCURSALE_ACTIVITE", "$SUCCURSALE_DATE_OUVERTURE",
            "$SUCCURSALE_VILLE_GREFFE",
            "$SUCCURSALE_DOTATION_PRESENTE", "$SUCCURSALE_DOTATION_CHIFFRES",
            "$SUCCURSALE_DOTATION_LETTRES",
            "$SUCCURSALE_RESPONSABLE_PRESENT", "$SUCCURSALE_RESPONSABLE_CIVILITE",
            "$SUCCURSALE_RESPONSABLE_PRENOM", "$SUCCURSALE_RESPONSABLE_NOM",
            "$SUCCURSALE_RESPONSABLE_POUVOIRS",
            "$DATE_DEPOT_LEGAL", "$DEPOT_LEGAL_NUMERO")


def para_runs(p_el):
    out = []
    for r in p_el.iter(qn("w:r")):
        text = "".join(t.text or "" for t in r.iter(qn("w:t")))
        if not text:
            continue
        rpr = r.find(qn("w:rPr"))
        bold = False
        if rpr is not None:
            b = rpr.find(qn("w:b"))
            bold = b is not None and b.get(qn("w:val")) not in ("0", "false")
        out.append((bold, text))
    return out


def para_text(p_el):
    return "".join(t.text or "" for t in p_el.iter(qn("w:t")))


def make_run(text, bold, size, color):
    r = OxmlElement("w:r")
    rpr = OxmlElement("w:rPr")
    rfonts = OxmlElement("w:rFonts")
    rfonts.set(qn("w:ascii"), "Times New Roman")
    rfonts.set(qn("w:hAnsi"), "Times New Roman")
    rpr.append(rfonts)
    b = OxmlElement("w:b")
    if not bold:
        b.set(qn("w:val"), "0")
    rpr.append(b)
    col = OxmlElement("w:color")
    col.set(qn("w:val"), color)
    rpr.append(col)
    sz = OxmlElement("w:sz")
    sz.set(qn("w:val"), size)
    rpr.append(sz)
    r.append(rpr)
    t = OxmlElement("w:t")
    t.set(qn("xml:space"), "preserve")
    t.text = text
    r.append(t)
    return r


def make_paragraph(runs, is_title):
    p = OxmlElement("w:p")
    ppr = OxmlElement("w:pPr")
    spacing = OxmlElement("w:spacing")
    spacing.set(qn("w:after"), "60")
    ppr.append(spacing)
    p.append(ppr)
    for bold, text in runs:
        if is_title:
            p.append(make_run(text, True, TITRE_SZ, NAVY))
        else:
            p.append(make_run(text, bold, CORPS_SZ, NOIR))
    return p


def derive(src_code, dest_code, variante):
    src = os.path.join(DOCX_DIR, src_code + ".docx")
    if not os.path.exists(src):
        raise RuntimeError(
            "Source introuvable : %s\n"
            "Lancez d'abord scripts/integrate-succursale-annonces-templates-2026-08-13.py"
            % src)

    doc = Document(src)
    children = [c for c in doc.element.body.iterchildren() if c.tag == qn("w:p")]
    paragraphs = [para_runs(c) for c in children]
    textes = [para_text(c) for c in children]

    # ---- Garde-fous de position : on refuse de dériver un modèle inattendu ----
    if not textes[0].strip().startswith("AVIS D"):
        raise RuntimeError("%s : titre inattendu %r" % (src_code, textes[0]))
    if "$DENOMINATION" not in textes[2] or "$RC_NUMERO" not in textes[2]:
        raise RuntimeError("%s : chapeau d'identification introuvable en [2] : %r"
                           % (src_code, textes[2]))
    if not textes[4].startswith(ATTENDU_4[variante]):
        raise RuntimeError(
            "%s : la phrase d'ouverture du directeur a changé en [4] : %r\n"
            "La dérivation doit être revue avec le directeur avant regénération."
            % (src_code, textes[4]))
    idx_depot = next((i for i, t in enumerate(textes) if t.startswith("Le dépôt légal")), None)
    if idx_depot is None or DEPOT_SRC not in textes[idx_depot]:
        raise RuntimeError("%s : paragraphe de dépôt légal introuvable / modifié" % src_code)

    # ---- Substitutions (3 paragraphes) ----
    paragraphs[2] = [(True, CHAPEAU[0]), (False, CHAPEAU[1])]
    paragraphs[4] = [(False, OUVERTURE_4[0]), (True, OUVERTURE_4[1]), (False, OUVERTURE_4[2])]
    paragraphs[idx_depot] = [(False, DEPOT_DERIVE)]

    # ---- Écriture ----
    dest = os.path.join(DOCX_DIR, dest_code + ".docx")
    shutil.copyfile(src, dest)
    out_doc = Document(dest)
    body = out_doc.element.body
    sectpr = body.find(qn("w:sectPr"))
    sectpr = copy.deepcopy(sectpr) if sectpr is not None else None
    for ch in list(body.iterchildren()):
        body.remove(ch)
    for idx, runs in enumerate(paragraphs):
        body.append(make_paragraph(runs, is_title=(idx == 0)))
    if sectpr is not None:
        body.append(sectpr)
    out_doc.save(dest)

    # ---- Contrôles ----
    check = Document(dest)
    lines = [p.text for p in check.paragraphs]
    full = "\n".join(lines)
    for banned in INTERDITES:
        assert banned not in full, \
            "%s : variable de mère MAROCAINE résiduelle (%s)" % (dest_code, banned.strip())
    for var in REQUISES:
        assert var in full, "%s : variable %s absente" % (dest_code, var)
    for marker in ("◇ SI :", "◆ FIN SI"):
        assert marker in full, "%s : marqueur %s perdu" % (dest_code, marker)

    # Le CORPS doit être identique au modèle source, mot pour mot.
    src_lines = [p.text for p in Document(src).paragraphs]
    for i, (a, b) in enumerate(zip(src_lines, lines)):
        if i in (2, 4, idx_depot):
            continue
        assert a == b, "%s : le corps a bougé au paragraphe %d\n  %r\n  %r" % (dest_code, i, a, b)

    os.makedirs(BASELINE_DIR, exist_ok=True)
    with open(os.path.join(BASELINE_DIR, dest_code + ".docx.json"), "w",
              encoding="utf-8") as fh:
        json.dump(lines, fh, ensure_ascii=False, indent=2)

    print("  OK -> %s (%d paragraphes, 3 modifiés) + baseline" % (dest_code, len(lines)))


def main():
    print("Dérivation de la variante ÉTRANGÈRE (origin=derive-jurika) dans :", DOCX_DIR)
    print("⚠️  Ces modèles NE sont PAS du directeur — validation directeur requise.")
    for src_code, dest_code, variante in JOBS:
        derive(src_code, dest_code, variante)
    print("Terminé.")


if __name__ == "__main__":
    main()
