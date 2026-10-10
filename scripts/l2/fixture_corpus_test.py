#!/usr/bin/env python3
"""Lot L2 : genere le corpus FICTIF de test (meme structure que le corpus du cabinet).

Ecrit dans backend-java/ai-service/src/test/resources/corpus-test/CORPUS_TEST :
  INDEX_CORPUS.xlsx (onglet "Modeles" avec les en-tetes du corpus reel),
  00_COMMUN/DICTIONNAIRE_UNIQUE_VARIABLES.xlsx (onglets "Variables" et "Alias"),
  01_TEST/GABARITS_WORD/*.docx (conventions de l'annexe technique, section 2).
Aucun contenu du cabinet : textes inventes pour les tests. Sortie deterministe
(dates fixes dans les archives) pour que les empreintes soient stables.
Usage : python3 scripts/l2/fixture_corpus_test.py
"""
import os
import zipfile
from xml.sax.saxutils import escape

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..',
                      'backend-java', 'ai-service', 'src', 'test', 'resources',
                      'corpus-test', 'CORPUS_TEST')
DATE = (2026, 10, 9, 0, 0, 0)


def ecrire_zip(chemin, fichiers):
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    with zipfile.ZipFile(chemin, 'w', zipfile.ZIP_DEFLATED) as z:
        for nom, contenu in fichiers:
            info = zipfile.ZipInfo(nom, DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, contenu.encode('utf-8'))


def colonne(i):
    s = ''
    i += 1
    while i:
        i, r = divmod(i - 1, 26)
        s = chr(65 + r) + s
    return s


def feuille(lignes):
    rows = []
    for n, ligne in enumerate(lignes, start=1):
        cells = ''.join(
            '<c r="%s%d" t="inlineStr"><is><t xml:space="preserve">%s</t></is></c>'
            % (colonne(i), n, escape(v)) for i, v in enumerate(ligne) if v is not None)
        rows.append('<row r="%d">%s</row>' % (n, cells))
    return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
            '<sheetData>%s</sheetData></worksheet>' % ''.join(rows))


def classeur(chemin, onglets):
    noms = list(onglets)
    ct = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
          '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
          '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
          '<Default Extension="xml" ContentType="application/xml"/>'
          '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
          '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
          + ''.join('<Override PartName="/xl/worksheets/sheet%d.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>' % (i + 1) for i in range(len(noms)))
          + '</Types>')
    rels = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
            '</Relationships>')
    wb = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
          '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
          'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>'
          + ''.join('<sheet name="%s" sheetId="%d" r:id="rId%d"/>' % (escape(n), i + 1, i + 1) for i, n in enumerate(noms))
          + '</sheets></workbook>')
    wbrels = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
              '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
              + ''.join('<Relationship Id="rId%d" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet%d.xml"/>' % (i + 1, i + 1) for i in range(len(noms)))
              + '<Relationship Id="rId%d" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>' % (len(noms) + 1)
              + '</Relationships>')
    styles = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
              '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
              '<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>'
              '<fills count="1"><fill><patternFill patternType="none"/></fill></fills>'
              '<borders count="1"><border/></borders>'
              '<cellStyleXfs count="1"><xf/></cellStyleXfs>'
              '<cellXfs count="1"><xf/></cellXfs></styleSheet>')
    fichiers = [('[Content_Types].xml', ct), ('_rels/.rels', rels), ('xl/workbook.xml', wb),
                ('xl/_rels/workbook.xml.rels', wbrels), ('xl/styles.xml', styles)]
    for i, n in enumerate(noms):
        fichiers.append(('xl/worksheets/sheet%d.xml' % (i + 1), feuille(onglets[n])))
    ecrire_zip(chemin, fichiers)


def gabarit(chemin, paragraphes):
    ct = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
          '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
          '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
          '<Default Extension="xml" ContentType="application/xml"/>'
          '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
          '</Types>')
    rels = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
            '</Relationships>')
    corps = ''.join('<w:p><w:r><w:t xml:space="preserve">%s</w:t></w:r></w:p>' % escape(p) for p in paragraphes)
    doc = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
           '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
           '<w:body>%s<w:sectPr/></w:body></w:document>' % corps)
    ecrire_zip(chemin, [('[Content_Types].xml', ct), ('_rels/.rels', rels), ('word/document.xml', doc)])


ENTETE_INDEX = ['Famille (dossier)', 'Famille', 'Mod\u00e8le', 'Forme', 'Mod\u00e8le de base',
                'Jumeau SARL / SARL AU', 'Source Markdown', 'Gabarit Word']
MODELES = [
    ('ACTE_TEST_SIMPLE', 'Sans variante de forme', 'ACTE_TEST_SIMPLE', '\u2014', [
        'ACTE DE TEST',
        'La soci\u00e9t\u00e9 $DENOMINATION, au capital de $CAPITAL_SOCIAL dirhams, a son si\u00e8ge \u00e0 $SIEGE_SOCIAL.',
        '\u25bc D\u00c9BUT BOUCLE \u2014 ASSOCIES',
        'Associ\u00e9 : $ASSOCIE_NOM, titulaire de $ASSOCIE_PARTS parts.',
        '\u25b2 FIN BOUCLE \u2014 ASSOCIES',
        '\u25c7 SI : $GERANT_UNIQUE = Oui',
        'Le g\u00e9rant unique est $GERANT_NOM.',
        '\u25c7 SINON',
        'La g\u00e9rance est coll\u00e9giale.',
        '\u25c6 FIN SI',
        'Fait \u00e0 $LIEU_SIGNATURE.',
    ]),
    ('PV_TEST_SARL', 'SARL', 'PV_TEST', 'PV_TEST_SARL_AU', [
        'PROC\u00c8S-VERBAL DE TEST (SARL)',
        'Les associ\u00e9s de $DENOMINATION se sont r\u00e9unis \u00e0 $LIEU_SIGNATURE.',
    ]),
    ('PV_TEST_SARL_AU', 'SARL AU', 'PV_TEST', 'PV_TEST_SARL', [
        'PROC\u00c8S-VERBAL DE TEST (SARL AU)',
        "L'associ\u00e9 unique de $DENOMINATION_SOCIALE a d\u00e9cid\u00e9 \u00e0 $LIEU_SIGNATURE.",
    ]),
]
VARIABLES = ['$DENOMINATION', '$CAPITAL_SOCIAL', '$SIEGE_SOCIAL', '$ASSOCIE_NOM',
             '$ASSOCIE_PARTS', '$GERANT_UNIQUE', '$GERANT_NOM', '$LIEU_SIGNATURE']
ALIAS = [('$DENOMINATION_SOCIALE', '$DENOMINATION', 'PV_TEST_SARL_AU')]
# Lot L3 : les variables externes de la liste versionnee doivent etre connues du
# dictionnaire charge (controle bloquant au demarrage d'ai-service) : le dictionnaire
# fictif les reprend, sans autre contenu.
EXTERNES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'backend-java', 'ai-service',
                        'src', 'main', 'resources', 'templates', 'v2', 'variables-externes.txt')


def variables_externes():
    noms, section = [], None
    with open(EXTERNES, encoding='ascii') as f:
        for ligne in f:
            l = ligne.strip()
            if not l or l.startswith('#'):
                continue
            if l.startswith('['):
                section = l
            elif section == '[corpus]':
                noms.append('$' + l)
    return noms


LIBELLES = {'$DENOMINATION': 'D\u00e9nomination de la soci\u00e9t\u00e9', '$LIEU_SIGNATURE': 'Lieu de signature'}


def main():
    lignes = [ENTETE_INDEX]
    for code, forme, base, jumeau, paras in MODELES:
        chemin = '01_TEST/GABARITS_WORD/%s.docx' % code
        lignes.append(['01_TEST', 'Famille de test', code, forme, base, jumeau,
                       '01_TEST/MODELES_MD/%s.md' % code, chemin])
        gabarit(os.path.join(RACINE, chemin), paras)
    classeur(os.path.join(RACINE, 'INDEX_CORPUS.xlsx'), {'Mod\u00e8les': lignes})
    # Lot L3 : colonne L, libelle du champ a l'ecran (nomme la donnee manquante).
    entete_var = (['Variable', 'Signification (texte du cabinet)'] + [None] * 9
                  + ["Libell\u00e9 du champ \u00e0 l'\u00e9cran (propos\u00e9, \u00e0 valider)",
                     'Nom canonique retenu par la plateforme'])
    var_lignes = [entete_var] + [[v, 'variable de test'] + [None] * 9 + [LIBELLES.get(v, v[1:]), v]
                                 for v in VARIABLES + [e for e in variables_externes() if e not in VARIABLES]]
    alias_lignes = [['Alias (nom employ\u00e9 par un mod\u00e8le)', 'Nom canonique retenu', "Mod\u00e8les employant l'alias"]] + [list(a) for a in ALIAS]
    classeur(os.path.join(RACINE, '00_COMMUN', 'DICTIONNAIRE_UNIQUE_VARIABLES.xlsx'),
             {'Variables': var_lignes, 'Alias': alias_lignes})
    print('corpus fictif ecrit dans', os.path.normpath(RACINE))


if __name__ == '__main__':
    main()
