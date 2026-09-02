# -*- coding: utf-8 -*-
"""
Sprint Cowork 2026-06-21 (C2) — wrap le paragraphe "15.3 Gérance par personne
morale (variante)" dans un bloc conditionnel ◇ GERANT_EST_MORALE ... ◆
GERANT_EST_MORALE pour qu'il SOIT SUPPRIME du document quand le gérant est
une personne physique (cas le plus fréquent).

Effet : plus aucune mention "personne morale gérante" / GERANT_PM_* visible
quand non applicable, donc plus de rouge "VALEUR MANQUANTE" pour ces variables
quand la politique stricte (C3) sera activée.

Cible : STATUTS_CONSTITUTIFS_SARL.docx uniquement. SARL_AU ne reference pas
gerant_pm_* dans son template (l'associé unique PM est dans ASSOCIE_UNIQUE_PM
deja conditionnel via le bloc).

Sauvegarde : STATUTS_CONSTITUTIFS_SARL.preconv-2026-06-21.docx.
"""
from __future__ import annotations
import copy, io, shutil, sys, zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
ET.register_namespace("w", NS["w"])

REPO = Path(__file__).resolve().parents[1]
TARGET = REPO / "backend-java" / "ai-service" / "src" / "main" / "resources" / "templates" / "docx" / "STATUTS_CONSTITUTIFS_SARL.docx"


def paragraph_text(p):
    return "".join((t.text or "") for t in p.iter(W + "t"))


def first_run_clone(p):
    for r in p.findall(W + "r"):
        return copy.deepcopy(r)
    return ET.SubElement(p, W + "r")


def patch_document_xml(xml_bytes: bytes) -> tuple[bytes, bool]:
    tree = ET.ElementTree(ET.fromstring(xml_bytes))
    body = tree.getroot().find(W + "body")
    if body is None:
        return xml_bytes, False

    children = list(body)
    target_idx = None
    for idx, el in enumerate(children):
        if el.tag != W + "p":
            continue
        text = paragraph_text(el)
        if "gerant_pm_denomination" in text and "15.3" in text:
            target_idx = idx
            break
    if target_idx is None:
        return xml_bytes, False

    original = body[target_idx]
    original_text = paragraph_text(original)

    # Idempotence : si on a deja les marqueurs ◇/◆ GERANT_EST_MORALE on n'y touche pas.
    if "◇ GERANT_EST_MORALE" in original_text and "◆ GERANT_EST_MORALE" in original_text:
        return xml_bytes, False

    # Strategie : prefixer le 1er <w:t> du paragraphe avec "◇ GERANT_EST_MORALE "
    # et suffixer le DERNIER <w:t> avec " ◆ GERANT_EST_MORALE". Le moteur
    # DocxTemplateEngine COND_START/END_PATTERN supporte les marqueurs inline
    # dans le meme paragraphe et supprime le paragraphe entier si le flag est falsy.
    t_elems = list(original.iter(W + "t"))
    if not t_elems:
        # Defensif : creer un run avec les deux marqueurs.
        new_p = copy.deepcopy(original)
        for r in list(new_p.findall(W + "r")):
            new_p.remove(r)
        r = first_run_clone(original)
        for t in list(r.iter(W + "t")):
            r.remove(t)
        t = ET.SubElement(r, W + "t")
        t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
        t.text = "◇ GERANT_EST_MORALE " + original_text + " ◆ GERANT_EST_MORALE"
        new_p.append(r)
        body[target_idx] = new_p
    else:
        first_t = t_elems[0]
        last_t = t_elems[-1]
        first_t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
        last_t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
        first_t.text = "◇ GERANT_EST_MORALE " + (first_t.text or "")
        last_t.text = (last_t.text or "") + " ◆ GERANT_EST_MORALE"

    buf = io.BytesIO()
    tree.write(buf, xml_declaration=True, encoding="UTF-8", default_namespace=None)
    return buf.getvalue(), True


def patch_docx(path: Path) -> bool:
    if not path.is_file():
        print(f"  SKIP {path} (introuvable)")
        return False
    backup = path.with_suffix(".preconv-2026-06-21.docx")
    if not backup.exists():
        shutil.copy2(path, backup)
        print(f"  backup -> {backup.name}")
    tmp = path.with_suffix(".tmp.docx")
    changed = False
    with zipfile.ZipFile(path, "r") as src, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data = src.read(item.filename)
            if item.filename == "word/document.xml":
                new_data, ch = patch_document_xml(data)
                if ch:
                    changed = True
                    data = new_data
            dst.writestr(item, data)
    if changed:
        tmp.replace(path)
        print(f"  PATCHED {path.name}")
    else:
        tmp.unlink()
        print(f"  UNCHANGED {path.name} (deja patche ou cible introuvable)")
    return changed


def main():
    print(f"Cible : {TARGET}")
    patch_docx(TARGET)
    return 0


if __name__ == "__main__":
    sys.exit(main())
