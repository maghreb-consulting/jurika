# -*- coding: utf-8 -*-
"""
Étape 7 §1bis (2026-08) — Recoloration sobre des 8 modèles déterministes directeur.

Objectif visuel du rendu généré :
  * Corps de texte = 100 % NOIR (#000000). Toute couleur de run résiduelle
    (ambre #B45309, vert #2E7D32 / #1E7A46, rouge #9C1B1B, navy des $VAR de corps)
    est neutralisée en noir.
  * On GARDE une couleur (navy unique #1F4E79) UNIQUEMENT sur :
      (a) le paragraphe d'en-tête portant $DENOMINATION (1re occurrence),
      (b) les paragraphes en STYLE TITRE (JurikaTitreArticle, JurikaSous(-)Titre,
          Titre1..6 / Titre, Title, Heading1..6) — titres de division
          (« TITRE PREMIER … ») et d'articles (« ARTICLE 1 — … »).

CONTENU INTOUCHABLE : on ne modifie QUE la couleur des runs (présentation).
Aucun texte, aucune balise $ / ◇ / ▼, aucun gras/italique n'est altéré. Le test
DirecteurTemplateContentUnchangedTest (qui compare le TEXTE) reste vert.

Surgical : lxml édite word/document.xml en place ; toutes les autres entrées du
.docx sont recopiées à l'octet près (compression préservée).
"""
import sys
import zipfile
import shutil
from lxml import etree

W = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
def w(tag): return f'{{{W}}}{tag}'

NAVY = '1F4E79'
BLACK = '000000'

# Ordre des enfants de <w:rPr> (schéma OOXML) — pour insérer <w:color> au bon rang.
RPR_ORDER = [
    'rStyle', 'rFonts', 'b', 'bCs', 'i', 'iCs', 'caps', 'smallCaps', 'strike',
    'dstrike', 'outline', 'shadow', 'emboss', 'imprint', 'noProof', 'snapToGrid',
    'vanish', 'webHidden', 'color', 'spacing', 'w', 'kern', 'position', 'sz',
    'szCs', 'highlight', 'u', 'effect', 'bdr', 'shd', 'fitText', 'vertAlign',
    'rtl', 'cs', 'em', 'lang', 'eastAsianLayout', 'specVanish', 'oMath',
]
ORDER_INDEX = {name: i for i, name in enumerate(RPR_ORDER)}


def local(tag):
    return tag.split('}', 1)[1] if '}' in tag else tag


def title_style(style_id):
    if not style_id:
        return False
    s = style_id.lower()
    if s.startswith('titre') or s.startswith('heading') or s == 'title':
        return True
    # Styles maison directeur : JurikaTitreArticle, JurikaSous-Titre / JurikaSousTitre
    if s.startswith('jurikatitre') or s.startswith('jurikasous'):
        return True
    return False


def get_pstyle(p):
    ppr = p.find(w('pPr'))
    if ppr is None:
        return None
    st = ppr.find(w('pStyle'))
    return st.get(w('val')) if st is not None else None


def para_text(p):
    return ''.join(t.text or '' for t in p.iter(w('t')))


def set_run_color(r, hexval, only_if_present):
    """Force la couleur d'un run. Si only_if_present=True, ne touche QUE les runs
    qui ont déjà une <w:color> (cas corps → noir : on ne colore pas ce qui est
    déjà en noir par héritage). Sinon, crée la couleur au bon rang (cas titre)."""
    rpr = r.find(w('rPr'))
    color = rpr.find(w('color')) if rpr is not None else None
    if color is not None:
        if color.get(w('val')) != hexval:
            color.set(w('val'), hexval)
            return True
        return False
    if only_if_present:
        return False
    # Créer rPr si absent (doit être le 1er enfant de w:r).
    if rpr is None:
        rpr = etree.SubElement(r, w('rPr'))
        r.remove(rpr)
        r.insert(0, rpr)
    color = etree.Element(w('color'))
    color.set(w('val'), hexval)
    # Insérer au bon rang dans rPr.
    ci = ORDER_INDEX['color']
    pos = len(rpr)
    for idx, child in enumerate(rpr):
        oi = ORDER_INDEX.get(local(child.tag), 999)
        if oi > ci:
            pos = idx
            break
    rpr.insert(pos, color)
    return True


def recolor_document(xml_bytes):
    parser = etree.XMLParser(remove_blank_text=False)
    root = etree.fromstring(xml_bytes, parser)
    body = root.find(w('body'))
    header_seen = False
    stats = {'body_black': 0, 'title_navy': 0, 'header_navy': 0}
    for p in body.iter(w('p')):
        style = get_pstyle(p)
        is_title = title_style(style)
        is_header = False
        if not header_seen and '$DENOMINATION' in para_text(p):
            is_header = True
            header_seen = True
        runs = p.findall('.//' + w('r'))
        if is_title or is_header:
            for r in runs:
                if set_run_color(r, NAVY, only_if_present=False):
                    stats['header_navy' if is_header else 'title_navy'] += 1
        else:
            for r in runs:
                if set_run_color(r, BLACK, only_if_present=True):
                    stats['body_black'] += 1
    out = etree.tostring(root, xml_declaration=True, encoding='UTF-8', standalone=True)
    return out, stats


def process(path):
    zin = zipfile.ZipFile(path, 'r')
    doc_xml = zin.read('word/document.xml')
    new_xml, stats = recolor_document(doc_xml)
    tmp = path + '.tmp'
    zout = zipfile.ZipFile(tmp, 'w')
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename == 'word/document.xml':
            data = new_xml
        # Préserver le mode de compression d'origine par entrée.
        zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
        zi.compress_type = item.compress_type
        zi.external_attr = item.external_attr
        zi.internal_attr = item.internal_attr
        zi.create_system = item.create_system
        zout.writestr(zi, data)
    zout.close()
    zin.close()
    shutil.move(tmp, path)
    return stats


if __name__ == '__main__':
    DIR = 'backend-java/ai-service/src/main/resources/templates/docx/'
    files = [
        'STATUTS_SARL_modele_deterministe.docx',
        'STATUTS_SARL_AU_modele_deterministe.docx',
        'ACTE_NOMINATION_GERANT_modele_deterministe.docx',
        'ANNONCE_LEGALE_modele_deterministe.docx',
        'PV_DEFAUT_QUORUM_SARL.docx',
        'PV_DEFAUT_QUORUM_SARL_AU.docx',
        'PV_IRREGULARITE_CONVOCATION_SARL.docx',
        'PV_IRREGULARITE_CONVOCATION_SARL_AU.docx',
    ]
    for fn in files:
        st = process(DIR + fn)
        print(f'{fn}: body->black={st["body_black"]} title->navy={st["title_navy"]} header->navy={st["header_navy"]}')
