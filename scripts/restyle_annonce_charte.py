#!/usr/bin/env python
"""
Restyle « charte JURIKA / Maghreb Consulting » des modèles d'annonce légale directeur.

Réutilisable : applique UNIQUEMENT la forme (police, tailles de titres, couleurs),
le CONTENU (texte + marqueurs $VAR / ◇◆▼▲) reste strictement inchangé — le test
DirecteurTemplateContentUnchangedTest (comparaison texte) reste vert.

Charte (alignée sur ANNONCE_LEGALE_modele_deterministe.docx et PV_MODIFICATION_*) :
  - Police : Times New Roman (partout) — au lieu du Calibri par défaut hérité.
  - Titre « $DENOMINATION »        : 16 pt, gras, bleu 1F4E79.
  - Titre « AVIS DE MODIFICATION » : 14 pt, gras, noir.
  - Corps                          : noir (retrait de la couleur or 9A6A00 issue du
                                     générateur source).

Usage : python scripts/restyle_annonce_charte.py [fichier1.docx fichier2.docx ...]
        (sans argument : restyle les 2 annonces de modification par défaut)
"""
import sys
from docx import Document
from docx.shared import Pt, RGBColor

FONT = "Times New Roman"
BLEU = RGBColor(0x1F, 0x4E, 0x79)
NOIR = RGBColor(0x00, 0x00, 0x00)

TEMPLATES_DIR = "backend-java/ai-service/src/main/resources/templates/docx/"
DEFAULTS = [
    TEMPLATES_DIR + "ANNONCE_LEGALE_MODIFICATION_SARL.docx",
    TEMPLATES_DIR + "ANNONCE_LEGALE_MODIFICATION_SARL_AU.docx",
]


def set_font_name(run, name):
    run.font.name = name
    # Force ascii + hAnsi + cs pour couvrir tous les scripts.
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(
        "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}rFonts"
    )
    if rfonts is None:
        from docx.oxml.ns import qn
        rfonts = rpr.makeelement(qn("w:rFonts"), {})
        rpr.insert(0, rfonts)
    from docx.oxml.ns import qn
    for attr in ("w:ascii", "w:hAnsi", "w:cs"):
        rfonts.set(qn(attr), name)


def restyle(path):
    doc = Document(path)
    for p in doc.paragraphs:
        text = p.text.strip()
        is_denom = text == "$DENOMINATION"
        is_avis = text == "AVIS DE MODIFICATION"
        for r in p.runs:
            set_font_name(r, FONT)
            if is_denom:
                r.font.size = Pt(16)
                r.font.bold = True
                r.font.color.rgb = BLEU
            elif is_avis:
                r.font.size = Pt(14)
                r.font.bold = True
                r.font.color.rgb = NOIR
            else:
                # Corps : noir (retrait de la couleur or du générateur).
                r.font.color.rgb = NOIR
    doc.save(path)
    print("restyle OK :", path)


if __name__ == "__main__":
    targets = sys.argv[1:] or DEFAULTS
    for t in targets:
        restyle(t)
