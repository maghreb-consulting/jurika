#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Lot A - jeu de donnees temoin pour les 23 gabarits du 9 septembre.

Produit quatre dossiers realistes (SARL pluripersonnelle, SARL AU, associe
personne morale, apport en nature) couvrant les 404 variables et les 15 boucles.

Les valeurs des egalites « SI : $VAR = "..." » et des blocs « CASE A COCHER »
sont recopiees TEXTUELLEMENT depuis le gabarit : le moteur compare sur le
libelle, une valeur approchante ferait taire une branche sans rien signaler.

Sortie : backend-java/ai-service/src/test/resources/lotA/temoins/*.json
"""
import io
import json
import os
import re
import sys
import zipfile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

GABARITS = r'C:\dev\JURIKA\specs\creation-2026-09-09\gabarits_word'
DEST = r'C:\dev\JURIKA\projet\backend-java\ai-service\src\test\resources\lotA\temoins'

CASE_VIDE = '\u2610'
LOSANGE_OUVRE = '\u25c7'
LOSANGE_FERME = '\u25c6'
TRIANGLE_BAS = '\u25bc'
TRIANGLE_HAUT = '\u25b2'
CASE_MARQUEUR = '\u25c8'

RE_BOUCLE_DEB = re.compile(TRIANGLE_BAS + r'\s*D[\u00c9E]BUT\s+BOUCLE\s*[\u2014\u2013-]\s*([A-Z0-9_]+)')
RE_BOUCLE_FIN = re.compile(TRIANGLE_HAUT + r'\s*FIN\s+BOUCLE\s*[\u2014\u2013-]\s*([A-Z0-9_]+)')
RE_EGALITE = re.compile(r'\$([A-Z][A-Z0-9_]*)\s*=\s*\u00ab\s*([^\u00bb]+)\u00bb')
RE_CASE = re.compile(CASE_MARQUEUR + r'\s*CASE\s+[A\u00c0]\s+COCHER.*?\$([A-Z][A-Z0-9_]*)', re.I)
RE_VAR = re.compile(r'\$([A-Z][A-Z0-9_]*)')


def paragraphes(chemin):
    """[(texte, style)] - le style porte la convention d'option du 9 septembre."""
    z = zipfile.ZipFile(chemin)
    out = []
    for n in z.namelist():
        if not (n.startswith('word/') and n.endswith('.xml')):
            continue
        xml = re.sub(r'<w:tab\b[^>]*/>', ' ', z.read(n).decode('utf-8', 'replace'))
        for b in re.split(r'</w:p\s*>', xml):
            st = re.search(r'<w:pStyle w:val="([^"]+)"', b)
            t = re.sub(r'<[^>]+>', '', b)
            t = (t.replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>')
                  .replace('&quot;', '"').replace('&apos;', "'"))
            out.append((t.strip(), st.group(1) if st else ''))
    return out


def releve():
    """(variables, boucles, litteraux d'egalite, options de case a cocher)."""
    variables, boucles, egalites, options = set(), {}, {}, {}
    for f in sorted(os.listdir(GABARITS)):
        if not f.endswith('.docx'):
            continue
        ps = paragraphes(os.path.join(GABARITS, f))
        pile = []
        for i, (t, style) in enumerate(ps):
            variables |= set(RE_VAR.findall(t))
            deb = RE_BOUCLE_DEB.search(t)
            fin = RE_BOUCLE_FIN.search(t)
            if deb:
                pile.append(deb.group(1))
                boucles.setdefault(deb.group(1), set())
            elif fin:
                if pile:
                    pile.pop()
            elif pile:
                for v in RE_VAR.findall(t):
                    for b in pile:
                        boucles[b].add(v)
            for m in RE_EGALITE.finditer(t):
                egalites.setdefault(m.group(1), []).append(m.group(2).strip())
            c = RE_CASE.search(t)
            if c:
                libelles = []
                suivants = ps[i + 1:]
                if suivants and CASE_VIDE in suivants[0][0]:
                    # Convention du 4 septembre : chaque option porte sa case.
                    for q, _ in suivants:
                        if CASE_VIDE not in q:
                            break
                        libelles.append(q.split(CASE_VIDE, 1)[1].strip())
                elif suivants:
                    # Convention du 9 septembre : l'option se reconnait au style.
                    style_option = suivants[0][1]
                    if style_option and style_option != style:
                        for q, st in suivants:
                            if st != style_option:
                                break
                            libelles.append(q.strip())
                if libelles or c.group(1) not in options:
                    options[c.group(1)] = libelles
    return variables, {k: sorted(v) for k, v in boucles.items()}, egalites, options


# Dossier temoin : une SARL casablancaise, valeurs coherentes entre elles.
SOCIETE = {
    'DENOMINATION': 'ATLAS NEGOCE',
    'SIGLE': 'ATN',
    'ENSEIGNE': 'Atlas Negoce',
    'OBJET_SOCIAL': "l'import, l'export et la distribution de materiel informatique",
    'SIEGE_SOCIAL': '12, rue Ibn Batouta, quartier Maarif, Casablanca',
    'SIEGE_VILLE': 'Casablanca',
    'VILLE': 'Casablanca',
    'VILLE_GREFFE': 'Casablanca',
    'TRIBUNAL_VILLE': 'Casablanca',
    'TRIBUNAL_TYPE': 'Tribunal de commerce',
    'DUREE_SOCIETE': '99',
    'DATE_FIN_SOCIETE': '15 janvier 2125',
    'SOCIETE_NATIONALITE': 'marocaine',
    'TELEPHONE': '05 22 44 55 66',
    'FAX': '05 22 44 55 67',
    'EMAIL': 'contact@atlasnegoce.ma',
    'SITE_WEB': 'www.atlasnegoce.ma',
    'CAPITAL_CHIFFRES': '100 000',
    'CAPITAL_LETTRES': 'cent mille',
    'NOMBRE_PARTS': '1 000',
    'VALEUR_NOMINALE_PART': '100',
    'ICE': '002145879000045',
    'IDENTIFIANT_FISCAL': '45218796',
    'IDENTIFIANT_TP': '31245789',
    'RC_NUMERO': '512345',
    'RC_VILLE': 'Casablanca',
    'DIRECTION_REGIONALE': 'Direction regionale des impots de Casablanca',
    'SUBDIVISION': 'Subdivision de Maarif',
    'ACTIVITE_PRINCIPALE': 'commerce de gros de materiel informatique',
    'LIEU_ACTIVITE': '12, rue Ibn Batouta, Casablanca',
    'DOMICILE_FISCAL': '12, rue Ibn Batouta, Casablanca',
}

LISTES = {
    'ASSOCIES': 3, 'GERANTS': 2, 'SIGNATAIRES': 2, 'APPORTS_PAR_ASSOCIE': 3,
    'APPORTS_NATURE': 2, 'SOUSCRIPTIONS': 3, 'ACTES_EN_FORMATION': 2,
    'BENEFICIAIRES_EFFECTIFS': 2, 'ETABLISSEMENTS': 2, 'PIECES_REMISES': 4,
    'BAIL_LOCAUX': 2, 'DIRIGEANTS_PM': 1, 'DOCUMENTS_COMMERCIAUX': 3,
    'TRAITEMENTS_DONNEES': 2, 'DEMARCHES_INTERROMPUES': 2,
}

NOMS = ['BENNANI', 'EL FASSI', 'ALAOUI', 'TAZI']
PRENOMS = ['Youssef', 'Salma', 'Karim', 'Nadia']
MONTANTS = ['50 000', '30 000', '20 000', '10 000']
LETTRES = ['cinquante mille', 'trente mille', 'vingt mille', 'dix mille']
DATES = ['15 janvier 2026', '3 fevrier 2026', '28 fevrier 2026', '10 mars 2026']


def valeur(nom, rang=0):
    """Valeur plausible deduite du nom de la variable."""
    if nom in SOCIETE:
        return SOCIETE[nom]
    i = rang % 4
    if nom.endswith('_LETTRES'):
        return LETTRES[i]
    if nom.endswith('_CHIFFRES') or 'MONTANT' in nom or nom.endswith('_TOTAL'):
        return MONTANTS[i]
    if 'DATE' in nom:
        return DATES[i]
    if nom.endswith('_PRENOM'):
        return PRENOMS[i]
    if nom.endswith(('_NOM', '_NOM_QUALITE', '_AUTEUR', '_REPRESENTANT', '_CHARGE')):
        return NOMS[i] + ' ' + PRENOMS[i]
    if nom.endswith('_CIVILITE'):
        return ['Monsieur', 'Madame'][i % 2]
    if nom.endswith('_GENRE'):
        return ['Masculin', 'Feminin'][i % 2]
    if nom.endswith('_NATIONALITE'):
        return 'marocaine'
    if nom.endswith('_PIECE_TYPE'):
        return "Carte nationale d'identite"
    if nom.endswith(('_PIECE_NUMERO', '_NUMERO', '_REFERENCE')):
        return ['BE812345', 'BK447790', 'AB119023', 'CD556677'][i]
    if nom.endswith(('_VILLE', '_COMMUNE', '_PAYS_RESIDENCE')):
        return 'Casablanca'
    if nom.endswith(('_ADRESSE', '_SIEGE', '_LIEU', '_LIEU_NAISSANCE')):
        return str(4 + i) + ', avenue Hassan II, Casablanca'
    if nom.endswith('_EMAIL'):
        return 'contact@atlasnegoce.ma'
    if nom.endswith('_TELEPHONE'):
        return '05 22 44 55 66'
    if nom.endswith(('_POURCENTAGE', '_POURCENTAGE_VOTE', '_QUOTITE')):
        return ['40 %', '35 %', '25 %', '10 %'][i]
    if nom.endswith(('_PARTS', '_NOMBRE_PARTS', '_PARTS_SOUSCRITES', '_PARTS_TOTAL',
                     '_PARTS_ATTRIBUEES', '_NOMBRE', '_EFFECTIF')):
        return ['400', '350', '250', '100'][i]
    if nom.endswith(('_EXISTE', '_SENSIBLES', '_ETRANGER', '_REGLEMENTEE',
                     '_EN_COURS', '_SOUS_LOCATION', '_PERSONNELLES')):
        return 'oui'
    if nom.endswith('_QUALITE'):
        return 'Gerant'
    return nom.replace('_', ' ').capitalize() + ' du dossier temoin'


def construit(cas, variables, boucles, egalites, options):
    v = {}
    for nom in sorted(variables):
        v[nom] = valeur(nom)
    for nom, libelles in egalites.items():
        v[nom] = libelles[0]
    for nom, libelles in options.items():
        if libelles:
            v[nom] = libelles[0]
    v.update(SOCIETE)
    v.update(cas.get('scalaires', {}))
    for b, champs in boucles.items():
        n = cas.get('listes', {}).get(b, LISTES.get(b, 2))
        surcharges = cas.get('lignes', {}).get(b, [])
        lignes = []
        for r in range(n):
            ligne = {}
            for c in champs:
                if c in egalites:
                    ligne[c] = egalites[c][0]
                elif options.get(c):
                    ligne[c] = options[c][0]
                else:
                    ligne[c] = valeur(c, r)
            if r < len(surcharges):
                ligne.update(surcharges[r])
            lignes.append(ligne)
        v[b] = lignes
    drapeaux = cas.get('drapeaux', {})
    for f in ('HAS_APPORT_NATURE', 'HAS_SIEGE_PRECEDENT', 'HAS_SUCCURSALES',
              'HAS_CAPITAL_VARIABLE', 'HAS_BREVETS_MARQUES', 'HAS_DIRIGEANT_PM'):
        v[f] = drapeaux.get(f, 'oui')
    return v


CAS = {
    'sarl_pluripersonnelle': {
        'scalaires': {'FORME_JURIDIQUE': 'Societe a responsabilite limitee'},
    },
    'sarl_au': {
        'scalaires': {
            'FORME_JURIDIQUE': 'Societe a responsabilite limitee a associe unique',
        },
        'listes': {'ASSOCIES': 1, 'APPORTS_PAR_ASSOCIE': 1, 'SOUSCRIPTIONS': 1,
                   'GERANTS': 1, 'SIGNATAIRES': 1},
        'drapeaux': {'HAS_APPORT_NATURE': 'non', 'HAS_DIRIGEANT_PM': 'non'},
    },
    'associe_personne_morale': {
        'scalaires': {'FORME_JURIDIQUE': 'Societe a responsabilite limitee'},
        'listes': {'ASSOCIES': 2, 'DIRIGEANTS_PM': 2},
        'lignes': {'ASSOCIES': [
            {'ASSOCIE_TYPE': 'personne morale',
             'ASSOCIE_DENOMINATION': 'HOLDING SAADA',
             'ASSOCIE_FORME': 'Societe anonyme',
             'ASSOCIE_SIEGE': '9, boulevard Zerktouni, Casablanca',
             'ASSOCIE_REPRESENTANT_NOM': 'ALAOUI Karim',
             'ASSOCIE_REPRESENTANT_QUALITE': 'President'},
            {'ASSOCIE_TYPE': 'personne physique'},
        ]},
        'drapeaux': {'HAS_DIRIGEANT_PM': 'oui'},
    },
    'apport_en_nature': {
        'scalaires': {'FORME_JURIDIQUE': 'Societe a responsabilite limitee'},
        'listes': {'APPORTS_NATURE': 2, 'APPORTS_PAR_ASSOCIE': 3},
        'lignes': {'APPORTS_PAR_ASSOCIE': [
            {'APPORT_TYPE': 'nature'},
            {'APPORT_TYPE': 'numeraire'},
            {'APPORT_TYPE': 'numeraire'},
        ]},
        'drapeaux': {'HAS_APPORT_NATURE': 'oui'},
    },
}


def main():
    variables, boucles, egalites, options = releve()
    os.makedirs(DEST, exist_ok=True)
    for nom, cas in CAS.items():
        v = construit(cas, variables, boucles, egalites, options)
        with open(os.path.join(DEST, nom + '.json'), 'w', encoding='utf-8') as fh:
            json.dump(v, fh, ensure_ascii=False, indent=1, sort_keys=True)
        scal = sum(1 for k in v if not isinstance(v[k], list))
        print('%-26s %d scalaires, %d boucles' % (nom, scal, len(v) - scal))
    print()
    print('variables couvertes : %d' % len(variables))
    print('egalites imposees   : %d' % len(egalites))
    print('cases a cocher      : %d (dont %d avec options lisibles)'
          % (len(options), sum(1 for o in options.values() if o)))


if __name__ == '__main__':
    main()
