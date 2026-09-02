# -*- coding: utf-8 -*-
"""Sprint Cowork 2026-06-21 (F — police) — Force TIMES NEW ROMAN partout
sur les Statuts SARL & SARL_AU.

Strategie :
  1) word/styles.xml   : reecrit tous les <w:rFonts .../> en
                         <w:rFonts w:ascii="Times New Roman" w:hAnsi="..."
                                   w:eastAsia="..." w:cs="..."/>
                         (couvre docDefaults + tous styles : Normal,
                          JurikaTitreArticle, JurikaSousTitre, headings,
                          table styles).
  2) word/theme/theme1.xml : remplace le fontScheme (typeface des
                              majorFont/minorFont latin) par "Times New Roman"
                              -> les styles qui referencent asciiTheme="..."
                              heritent egalement.
  3) word/document.xml : strip les overrides inline <w:rFonts .../> dans les
                         runs (qui sinon ecrasent le style).
  4) word/fontTable.xml : ajoute une declaration <w:font w:name="Times New Roman">
                          si absente.

Usage :
    PYTHONIOENCODING=utf-8 python scripts/patch_statuts_sarl_fonts_2026_06_21.py
"""
import io
import re
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

ROOT = Path(__file__).resolve().parents[1]
TPL_DIR = ROOT / "backend-java" / "ai-service" / "src" / "main" / "resources" / "templates" / "docx"

FONT_NAME = "Times New Roman"
RFONTS_CANONICAL = (
    f'<w:rFonts w:ascii="{FONT_NAME}" w:hAnsi="{FONT_NAME}"'
    f' w:eastAsia="{FONT_NAME}" w:cs="{FONT_NAME}"/>'
)

# --- patch helpers ---

RFONTS_RE = re.compile(r'<w:rFonts\b[^/]*/>')


def patch_styles_xml(xml: str) -> str:
    """Replace every <w:rFonts .../> with the canonical Times New Roman one."""
    return RFONTS_RE.sub(RFONTS_CANONICAL, xml)


def patch_document_xml(xml: str) -> str:
    """Strip <w:rFonts .../> from inline runs (rPr) so the style takes over.
    Note : on garde les balises en debut de rPr en les remplaçant par la
    version Times New Roman -- cela force le rendu meme si Word ignore le
    style (Mammoth / docx-preview lisent souvent l'inline)."""
    return RFONTS_RE.sub(RFONTS_CANONICAL, xml)


def patch_theme_xml(xml: str) -> str:
    """Remplace les typeface des majorFont/minorFont latin par Times New Roman."""
    # <a:latin typeface="Cambria"/> ou similaire dans majorFont/minorFont
    new = re.sub(
        r'<a:latin typeface="[^"]*"/>',
        f'<a:latin typeface="{FONT_NAME}"/>',
        xml,
    )
    return new


def patch_font_table(xml: str) -> str:
    """Ajoute Times New Roman si absent."""
    if FONT_NAME in xml:
        return xml
    insert = (
        f'<w:font w:name="{FONT_NAME}">'
        f'<w:panose1 w:val="02020603050405020304"/>'
        f'<w:charset w:val="00"/>'
        f'<w:family w:val="roman"/>'
        f'<w:pitch w:val="variable"/>'
        f'<w:sig w:usb0="E0002AFF" w:usb1="C0007841" w:usb2="00000009" '
        f'w:usb3="00000000" w:csb0="000001FF" w:csb1="00000000"/>'
        f'</w:font>'
    )
    return xml.replace('</w:fonts>', insert + '</w:fonts>')


def patch_docx(path: Path) -> None:
    tmpdir = Path(tempfile.mkdtemp(prefix="patchfont_"))
    try:
        # Extract
        with zipfile.ZipFile(path, 'r') as z:
            z.extractall(tmpdir)
        # Patch styles
        styles = tmpdir / "word" / "styles.xml"
        if styles.exists():
            txt = styles.read_text(encoding='utf-8')
            patched = patch_styles_xml(txt)
            styles.write_text(patched, encoding='utf-8')
            print(f"  [OK] styles.xml ({len(RFONTS_RE.findall(txt))} rFonts -> {FONT_NAME})")
        # Patch document
        doc = tmpdir / "word" / "document.xml"
        if doc.exists():
            txt = doc.read_text(encoding='utf-8')
            patched = patch_document_xml(txt)
            doc.write_text(patched, encoding='utf-8')
            print(f"  [OK] document.xml ({len(RFONTS_RE.findall(txt))} inline rFonts -> {FONT_NAME})")
        # Patch theme
        theme = tmpdir / "word" / "theme" / "theme1.xml"
        if theme.exists():
            txt = theme.read_text(encoding='utf-8')
            patched = patch_theme_xml(txt)
            theme.write_text(patched, encoding='utf-8')
            cnt = len(re.findall(r'<a:latin typeface="[^"]*"/>', txt))
            print(f"  [OK] theme1.xml ({cnt} latin typeface -> {FONT_NAME})")
        # Patch fontTable
        ft = tmpdir / "word" / "fontTable.xml"
        if ft.exists():
            txt = ft.read_text(encoding='utf-8')
            patched = patch_font_table(txt)
            ft.write_text(patched, encoding='utf-8')
            print(f"  [OK] fontTable.xml ({FONT_NAME} declared)")

        # Re-zip
        new_zip = tmpdir.parent / (path.stem + "_repack.docx")
        with zipfile.ZipFile(new_zip, 'w', zipfile.ZIP_DEFLATED) as zo:
            for f in tmpdir.rglob('*'):
                if f.is_file():
                    rel = f.relative_to(tmpdir)
                    zo.write(f, str(rel).replace('\\', '/'))
        shutil.move(str(new_zip), str(path))
        print(f"  [OK] sauvegarde {path.name}")
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)


for fn in ["STATUTS_CONSTITUTIFS_SARL.docx", "STATUTS_CONSTITUTIFS_SARL_AU.docx"]:
    print(f"\n=== {fn} ===")
    patch_docx(TPL_DIR / fn)
