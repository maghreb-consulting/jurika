# -*- coding: utf-8 -*-
"""
Patch des gabarits statuts SARL — Sprint 2026-06-19.

Bug Cowork : le paragraphe 6.1 "Apports en numeraire" est monolithique avec
le marqueur ``▶ APPORTS_NUMERAIRE`` inline. Comme le DocxTemplateEngine
clone le PARAGRAPHE entier par associé, tout se duplique : sous-titre 6.1,
phrase d'apport, total des apports, clause de dépôt bancaire + mandataire.

Patch : scinder ce paragraphe unique en 4 paragraphes distincts pour qu'un
seul ``▶ APPORTS_NUMERAIRE`` cloné par associé contienne uniquement la phrase
d'apport (et que le sous-titre, le total et la clause de dépôt restent hors
boucle, en occurrence unique).

Le SARL_AU n'est pas touché par la duplication (associé unique, pas de
boucle dans le 6.1).

Tournes ce script via ``py -3 scripts/patch-statuts-templates-2026-06-19.py``.
Les .docx originaux sont sauvegardés en ``.preconv-2026-06-19.docx`` à côté.
"""
from __future__ import annotations

import copy
import io
import shutil
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
ET.register_namespace("w", NS["w"])

REPO = Path(__file__).resolve().parents[1]
TEMPLATES_DIR = REPO / "backend-java" / "ai-service" / "src" / "main" / "resources" / "templates" / "docx"
SARL = TEMPLATES_DIR / "STATUTS_CONSTITUTIFS_SARL.docx"
SARL_AU = TEMPLATES_DIR / "STATUTS_CONSTITUTIFS_SARL_AU.docx"


def paragraph_text(p):
    return "".join((t.text or "") for t in p.iter(W + "t"))


def clear_paragraph_runs(p):
    """Supprime tous les runs (<w:r>) du paragraphe sans toucher au <w:pPr>."""
    for r in list(p.findall(W + "r")):
        p.remove(r)


def first_run_template(p):
    """Renvoie une copie du 1er run pour preserver la mise en forme runProperties."""
    for r in p.findall(W + "r"):
        return copy.deepcopy(r)
    # Fallback : run vide.
    r = ET.SubElement(p, W + "r")
    return r


def replace_text_in_run(run, new_text):
    """Remplace tout le contenu textuel d'un run par new_text (1 seul <w:t>)."""
    for child in list(run):
        if child.tag == W + "t":
            run.remove(child)
    t = ET.SubElement(run, W + "t")
    t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
    t.text = new_text


def build_paragraph_like(template_p, text):
    """Clone le paragraphe template (pPr inclus), vide ses runs, met le texte."""
    new_p = copy.deepcopy(template_p)
    clear_paragraph_runs(new_p)
    run_tpl = first_run_template(template_p)
    # Nettoyer texte du run cloné puis y placer le nouveau texte.
    for t in list(run_tpl.iter(W + "t")):
        run_tpl.remove(t)
    new_p.append(run_tpl)
    replace_text_in_run(run_tpl, text)
    return new_p


def patch_sarl_document_xml(xml_bytes: bytes) -> tuple[bytes, bool]:
    """
    Scinde le paragraphe ``6.1 Apports en numeraire — ▶ APPORTS_NUMERAIRE ...``
    en 4 paragraphes distincts. Retourne (xml_modifie, changed).
    """
    tree = ET.ElementTree(ET.fromstring(xml_bytes))
    root = tree.getroot()
    body = root.find(W + "body")
    if body is None:
        return xml_bytes, False

    target_idx = None
    children = list(body)
    for idx, el in enumerate(children):
        if el.tag != W + "p":
            continue
        text = paragraph_text(el)
        if "▶ APPORTS_NUMERAIRE" in text and "Apports en num" in text:
            target_idx = idx
            break
    if target_idx is None:
        return xml_bytes, False

    original = body[target_idx]
    body.remove(original)

    # 4 paragraphes nouveaux, dans l'ordre. On utilise comme template
    # `original` pour preserver le style (pPr) — chaque nouveau paragraphe
    # est un clone du original avec le texte voulu.
    paragraphs_text = [
        "6.1 Apports en numéraire —",
        "▶ APPORTS_NUMERAIRE {{associe_pp_nom_prenom}} apporte à la société une somme de "
        "{{associe_pp_apport_numeraire}} dirhams.",
        "Le total des apports en numéraire s'élève à {{total_apports_numeraire}} dirhams.",
        "Cette somme a été déposée, préalablement à la signature des présents statuts, "
        "sur un compte bloqué ouvert au nom de la société en formation auprès de "
        "{{depot_banque_nom}}, sous le numéro {{depot_numero}}. Le retrait des fonds "
        "sera effectué par la gérance sur présentation de l'attestation d'immatriculation "
        "délivrée par le greffe.",
    ]

    for offset, txt in enumerate(paragraphs_text):
        new_p = build_paragraph_like(original, txt)
        body.insert(target_idx + offset, new_p)

    buf = io.BytesIO()
    tree.write(buf, xml_declaration=True, encoding="UTF-8", default_namespace=None)
    return buf.getvalue(), True


def patch_docx(path: Path, patcher) -> bool:
    """Applique `patcher` au document.xml du .docx. Backup -> .preconv-2026-06-19.docx."""
    if not path.is_file():
        print(f"  SKIP {path} (introuvable)")
        return False

    backup = path.with_suffix(".preconv-2026-06-19.docx")
    if not backup.exists():
        shutil.copy2(path, backup)
        print(f"  backup -> {backup.name}")

    tmp = path.with_suffix(".tmp.docx")
    changed = False
    with zipfile.ZipFile(path, "r") as src, zipfile.ZipFile(
        tmp, "w", zipfile.ZIP_DEFLATED
    ) as dst:
        for item in src.infolist():
            data = src.read(item.filename)
            if item.filename == "word/document.xml":
                new_data, ch = patcher(data)
                if ch:
                    changed = True
                    data = new_data
            dst.writestr(item, data)

    if changed:
        tmp.replace(path)
        print(f"  PATCHED {path.name}")
    else:
        tmp.unlink()
        print(f"  UNCHANGED {path.name}")
    return changed


def main():
    print(f"Templates dir : {TEMPLATES_DIR}")
    any_changed = False
    any_changed |= patch_docx(SARL, patch_sarl_document_xml)
    # SARL_AU : pas de boucle ▶ APPORTS_NUMERAIRE dans le 6.1, rien a scinder.
    # On le laisse intact pour ne pas casser la mise en page directrice.
    print()
    print("OK." if any_changed else "Aucun changement applique (deja patche ?).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
