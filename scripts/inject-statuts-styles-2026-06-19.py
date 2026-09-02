#!/usr/bin/env python3
"""Injection deterministe des styles Word JurikaTitreArticle + JurikaSousTitre
dans word/styles.xml des 4 gabarits Statuts (SARL + SARL_AU, constitutifs +
modifies). Idempotent : ne fait rien si les styles sont deja presents.

Police imposee : Calibri (alignee sur le style Normal du gabarit).
- JurikaTitreArticle : 13 pt (sz=26), gras, gauche, espacement avant/apres
                      pour aerer la mise en page.
- JurikaSousTitre   : 11,5 pt (sz=23), gras, gauche, espacement modere.

Le style Normal existant (corps justifie, Calibri) reste inchange.
"""

from pathlib import Path
import shutil
import zipfile
import sys


REPO = Path(__file__).resolve().parents[1]
TEMPLATES_DIR = REPO / "backend-java" / "ai-service" / "src" / "main" / \
    "resources" / "templates" / "docx"

TARGETS = [
    "STATUTS_CONSTITUTIFS_SARL.docx",
    "STATUTS_CONSTITUTIFS_SARL_AU.docx",
    "STATUTS_MODIFIES_SARL.docx",
    "STATUTS_MODIFIES_SARL_AU.docx",
]

STYLE_TITRE_ARTICLE = (
    '<w:style w:type="paragraph" w:customStyle="1" w:styleId="JurikaTitreArticle">'
    '<w:name w:val="Jurika Titre Article"/>'
    '<w:basedOn w:val="Normal"/>'
    '<w:next w:val="Normal"/>'
    '<w:qFormat/>'
    '<w:pPr>'
    '<w:keepNext/>'
    '<w:spacing w:before="360" w:after="180" w:line="312" w:lineRule="auto"/>'
    '<w:jc w:val="left"/>'
    '</w:pPr>'
    '<w:rPr>'
    '<w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>'
    '<w:b/>'
    '<w:bCs/>'
    '<w:sz w:val="26"/>'
    '<w:szCs w:val="26"/>'
    '</w:rPr>'
    '</w:style>'
)

STYLE_SOUS_TITRE = (
    '<w:style w:type="paragraph" w:customStyle="1" w:styleId="JurikaSousTitre">'
    '<w:name w:val="Jurika Sous-Titre"/>'
    '<w:basedOn w:val="Normal"/>'
    '<w:next w:val="Normal"/>'
    '<w:qFormat/>'
    '<w:pPr>'
    '<w:keepNext/>'
    '<w:spacing w:before="180" w:after="60" w:line="312" w:lineRule="auto"/>'
    '<w:jc w:val="left"/>'
    '</w:pPr>'
    '<w:rPr>'
    '<w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>'
    '<w:b/>'
    '<w:bCs/>'
    '<w:sz w:val="23"/>'
    '<w:szCs w:val="23"/>'
    '</w:rPr>'
    '</w:style>'
)

CLOSING = "</w:styles>"
PAYLOAD = STYLE_TITRE_ARTICLE + STYLE_SOUS_TITRE + CLOSING


def patch_docx(path: Path) -> bool:
    """Retourne True si une modification a ete appliquee, False si deja a jour."""
    if not path.exists():
        print(f"  ! introuvable : {path}", file=sys.stderr)
        return False

    # Lecture de styles.xml.
    with zipfile.ZipFile(path, "r") as zf:
        if "word/styles.xml" not in zf.namelist():
            print(f"  ! word/styles.xml absent : {path.name}", file=sys.stderr)
            return False
        styles_xml = zf.read("word/styles.xml").decode("utf-8")

    if "JurikaTitreArticle" in styles_xml and "JurikaSousTitre" in styles_xml:
        print(f"  = deja a jour : {path.name}")
        return False
    if CLOSING not in styles_xml:
        print(f"  ! balise </w:styles> absente : {path.name}", file=sys.stderr)
        return False

    patched = styles_xml.replace(CLOSING, PAYLOAD, 1)

    # Re-ecriture du zip en preservant tous les autres entries.
    tmp = path.with_suffix(path.suffix + ".tmp")
    with zipfile.ZipFile(path, "r") as src, \
            zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data = src.read(item.filename)
            if item.filename == "word/styles.xml":
                data = patched.encode("utf-8")
            dst.writestr(item, data)
    shutil.move(str(tmp), str(path))
    print(f"  + patche : {path.name}")
    return True


def main() -> int:
    if not TEMPLATES_DIR.is_dir():
        print(f"Dossier introuvable : {TEMPLATES_DIR}", file=sys.stderr)
        return 1
    print(f"Patch styles Statuts dans {TEMPLATES_DIR}")
    changed = 0
    for name in TARGETS:
        if patch_docx(TEMPLATES_DIR / name):
            changed += 1
    print(f"Termine : {changed}/{len(TARGETS)} gabarit(s) modifie(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
