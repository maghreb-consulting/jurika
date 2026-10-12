#!/usr/bin/env python3
# -*- coding: ascii -*-
"""
Lot L5 (E7, RG-FIC-01, RG-FIC-05 a 07) : construit les deux gabarits Word des fiches
(fiche client, fiche dossier), ranges dans les ressources de dataroom-service.

Le corpus du cabinet (CORPUS_2026-10-11) ne contient aucun modele de fiche client ni de
fiche dossier (seule FICHE_RENSEIGNEMENTS_CREATION existe, pour l'ouverture d'une
creation) : ces gabarits sont donc construits ici, reproductibles a l'octet pres.

Balisage : $NOM (une variable par segment de texte). Une ligne de tableau qui porte des
variables $VTP_* ou $OP_* est une ligne modele, repetee pour chaque element.
Texte accentue : references de caracteres XML (le script reste en ASCII pur).

Usage : python3 scripts/l5/gabarits_fiches.py
"""
import os
import zipfile

SORTIE = os.path.join(os.path.dirname(__file__), "..", "..", "backend-java", "dataroom-service",
                      "src", "main", "resources", "gabarits", "fiches")

CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

DOC_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults>
<w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/><w:sz w:val="21"/><w:lang w:val="fr-FR"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:after="80" w:line="264" w:lineRule="auto"/></w:pPr></w:pPrDefault>
</w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
<w:style w:type="paragraph" w:styleId="Titre"><w:name w:val="Title"/><w:basedOn w:val="Normal"/>
<w:pPr><w:jc w:val="center"/><w:spacing w:before="120" w:after="60"/></w:pPr>
<w:rPr><w:b/><w:caps/><w:color w:val="1F3864"/><w:sz w:val="32"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Titre1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/>
<w:pPr><w:keepNext/><w:spacing w:before="280" w:after="100"/><w:pBdr><w:bottom w:val="single" w:sz="6" w:space="1" w:color="1F3864"/></w:pBdr></w:pPr>
<w:rPr><w:b/><w:color w:val="1F3864"/><w:sz w:val="24"/></w:rPr></w:style>
<w:style w:type="table" w:styleId="Grille"><w:name w:val="Table Grid"/>
<w:tblPr><w:tblBorders>
<w:top w:val="single" w:sz="4" w:space="0" w:color="8EA9C1"/><w:left w:val="single" w:sz="4" w:space="0" w:color="8EA9C1"/>
<w:bottom w:val="single" w:sz="4" w:space="0" w:color="8EA9C1"/><w:right w:val="single" w:sz="4" w:space="0" w:color="8EA9C1"/>
<w:insideH w:val="single" w:sz="4" w:space="0" w:color="8EA9C1"/><w:insideV w:val="single" w:sz="4" w:space="0" w:color="8EA9C1"/>
</w:tblBorders><w:tblCellMar><w:left w:w="90" w:type="dxa"/><w:right w:w="90" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style>
</w:styles>"""

W = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"'


def run(texte, gras=False, taille=None, couleur=None, italique=False):
    props = ""
    if gras:
        props += "<w:b/>"
    if italique:
        props += "<w:i/>"
    if couleur:
        props += '<w:color w:val="%s"/>' % couleur
    if taille:
        props += '<w:sz w:val="%d"/>' % taille
    rpr = "<w:rPr>%s</w:rPr>" % props if props else ""
    return '<w:r>%s<w:t xml:space="preserve">%s</w:t></w:r>' % (rpr, texte)


def para(*runs, style=None, jc=None):
    ppr = ""
    if style or jc:
        ppr = "<w:pPr>%s%s</w:pPr>" % ('<w:pStyle w:val="%s"/>' % style if style else "",
                                     '<w:jc w:val="%s"/>' % jc if jc else "")
    return "<w:p>%s%s</w:p>" % (ppr, "".join(runs))


def cellule(contenu, largeur, entete=False):
    shd = '<w:shd w:val="clear" w:color="auto" w:fill="DCE6F0"/>' if entete else ""
    return ('<w:tc><w:tcPr><w:tcW w:w="%d" w:type="dxa"/>%s</w:tcPr>%s</w:tc>'
            % (largeur, shd, para(run(contenu, gras=entete))))


def tableau(entetes, lignes, largeurs):
    grille = "".join('<w:gridCol w:w="%d"/>' % l for l in largeurs)
    xml = ('<w:tbl><w:tblPr><w:tblStyle w:val="Grille"/><w:tblW w:w="%d" w:type="dxa"/>'
           '<w:tblLayout w:type="fixed"/></w:tblPr><w:tblGrid>%s</w:tblGrid>' % (sum(largeurs), grille))
    xml += ('<w:tr><w:trPr><w:tblHeader/></w:trPr>%s</w:tr>'
            % "".join(cellule(e, l, True) for e, l in zip(entetes, largeurs)))
    for ligne in lignes:
        xml += "<w:tr><w:trPr><w:cantSplit/></w:trPr>%s</w:tr>" % "".join(
            cellule(c, l) for c, l in zip(ligne, largeurs))
    return xml + "</w:tbl>"


IDENTIFIANTS = [
    ("D&#233;nomination", "DENOMINATION"),
    ("Forme juridique", "FORME"),
    ("Capital social", "CAPITAL"),
    ("Si&#232;ge social", "SIEGE"),
    ("Registre du commerce", "RC"),
    ("ICE", "ICE"),
    ("Identifiant fiscal", "IF"),
    ("Taxe professionnelle (patente) en vigueur", "TP"),
    ("CNSS", "CNSS"),
]


def corps_commun(titre):
    xml = para(run("$CABINET_NOM", gras=True, couleur="595959", taille=18), jc="right")
    xml += para(run(titre), style="Titre")
    xml += para(run("$DENOMINATION_TITRE", gras=True, taille=26), jc="center")
    xml += para(run("Identifiants de la soci&#233;t&#233;"), style="Titre1")
    xml += tableau(["Information", "Valeur en vigueur", "Provenance"],
                   [[lib, "$%s_VALEUR" % cle, "$%s_PROVENANCE" % cle] for lib, cle in IDENTIFIANTS],
                   [2700, 3300, 3400])
    xml += para(run("Taxe professionnelle (patente) : versions successives"), style="Titre1")
    xml += tableau(["Num&#233;ro", "Date de prise d&#8217;effet", "&#201;tat"],
                   [["$VTP_NUMERO", "$VTP_DATE_EFFET", "$VTP_ETAT"]],
                   [3600, 3300, 2500])
    return xml


def fiche_client():
    xml = corps_commun("Fiche client")
    xml += para(run("La fiche client pr&#233;sente uniquement les identifiants de la soci&#233;t&#233;, "
                    "&#224; leur valeur en vigueur. Une donn&#233;e absente du dossier est indiqu&#233;e "
                    "&#171; non renseign&#233;e &#187;.", italique=True, couleur="595959", taille=18))
    return xml


def fiche_dossier():
    xml = corps_commun("Fiche dossier")
    xml += para(run("Op&#233;rations juridiques du dossier"), style="Titre1")
    xml += para(run("$A_JOUR", italique=True, couleur="595959", taille=18))
    xml += tableau(["Op&#233;ration", "Ticket", "Ouverture", "Cl&#244;ture ou annulation", "Statut",
                    "Employ&#233; responsable"],
                   [["$OP_TYPE", "$OP_REFERENCE", "$OP_OUVERTURE", "$OP_FIN", "$OP_STATUT", "$OP_RESPONSABLE"]],
                   [2300, 1300, 1200, 1700, 1400, 1500])
    xml += para(run("La fiche dossier reprend les identifiants de la soci&#233;t&#233; et toutes les "
                    "op&#233;rations juridiques du dossier, y compris celles annul&#233;es.",
                    italique=True, couleur="595959", taille=18))
    return xml


def document(corps):
    sect = ('<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>'
            '<w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="567" w:footer="567" w:gutter="0"/>'
            '</w:sectPr>')
    return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<w:document %s><w:body>%s%s</w:body></w:document>'
            % (W, corps, sect))


def ecrire(nom, corps):
    os.makedirs(SORTIE, exist_ok=True)
    chemin = os.path.join(SORTIE, nom)
    parties = [("[Content_Types].xml", CONTENT_TYPES), ("_rels/.rels", RELS),
               ("word/_rels/document.xml.rels", DOC_RELS), ("word/styles.xml", STYLES),
               ("word/document.xml", document(corps))]
    with zipfile.ZipFile(chemin, "w", zipfile.ZIP_DEFLATED) as z:
        for nom_partie, contenu in parties:
            info = zipfile.ZipInfo(nom_partie, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, contenu.encode("ascii"))
    print(chemin)


if __name__ == "__main__":
    ecrire("FICHE_CLIENT.docx", fiche_client())
    ecrire("FICHE_DOSSIER.docx", fiche_dossier())
