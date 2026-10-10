#!/usr/bin/env python3
"""Lot L3 : propose la liste des variables EXTERNES (regle des variables, CLAUDE.md).

Externe : donnee attendue d'un organisme (numero RC, ICE, IF, TP, CNSS, date
d'immatriculation, certificat negatif, depot au greffe, Bulletin officiel,
enregistrement, quitus fiscal, recepisse...). Tout le reste est interne.

Lit le dictionnaire unique du corpus date (onglet "Variables", colonne "Variable")
et ecrit backend-java/ai-service/src/main/resources/templates/v2/variables-externes.txt.
La liste est une PROPOSITION a valider par le directeur (rapport L3, Decisions a revoir) :
elle est versionnee et relue, jamais deduite a l'execution.
Usage : python3 scripts/l3/variables_externes.py <racine du corpus date>
"""
import html
import os
import re
import sys
import zipfile

SORTIE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'backend-java', 'ai-service',
                      'src', 'main', 'resources', 'templates', 'v2', 'variables-externes.txt')

# Decision du 2026-10-10 (D2 revue) : seules les donnees de la SOCIETE TRAITEE (et de ses propres
# succursales et etablissements) attendues d'un organisme sont externes : RC, ICE, IF, TP, CNSS,
# dates et numeros d'immatriculation, de depot, de recepisse. Les identifiants des tiers (bailleur,
# domiciliataire, associe personne morale, acquereur, creancier, liquidateur...) sont internes.
CATEGORIES = [
    ('Registre du commerce', [r'^RC_NUMERO(_NOUVEAU)?$', r'^SUCCURSALE_RC_NUMERO$', r'^ETAB_RC_NUMERO$',
                              r'^SUCCURSALE_REGROUPEMENT_ETABLISSEMENT_RC_NUMERO$']),
    ('ICE', [r'^ICE$', r'^SUCCURSALE_ICE$']),
    ('Identifiant fiscal', [r'^IDENTIFIANT_FISCAL(_NOUVEAU)?$', r'^SUCCURSALE_IDENTIFIANT_FISCAL$']),
    ('Taxe professionnelle', [r'^IDENTIFIANT_TP$', r'^SUCCURSALE_IDENTIFIANT_TP$', r'^ETAB_IDENTIFIANT_TP$',
                              r'^SUCCURSALE_ETABLISSEMENT_ORIGINE_IDENTIFIANT_TP$',
                              r'^SUCCURSALE_TP_CERTIFICAT_(NUMERO|DATE)$']),
    ('CNSS', [r'^CNSS_NUMERO$', r'^SUCCURSALE_CNSS_NUMERO$']),
    ('Immatriculation', [r'^DATE_IMMATRICULATION(_NOUVELLE)?$', r'^SUCCURSALE_DATE_IMMATRICULATION$',
                         r'^SUCCURSALE_IMMATRICULATION_DATE$']),
    ('Depot au greffe', [r'^DATE_DEPOT_LEGAL$', r'^DEPOT_LEGAL_NUMERO$', r'^DEPOT_GREFFE_DATE$',
                         r'^DISSOLUTION_DEPOT_GREFFE_(DATE|NUMERO)$', r'^LIQUIDATION_CLOTURE_DEPOT_GREFFE_DATE$']),
    ('Recepisse', [r'^CNDP_RECEPISSE_NUMERO$']),
]

# Candidats de l'ancienne regle (identifiants, pieces d'un organisme) ecartes, avec leur raison :
# ils sont listes dans le rapport pour relecture.
CANDIDATS = [
    r'(^|_)RC_NUMERO(_NOUVEAU)?$', r'(^|_)ICE$', r'(^|_)IDENTIFIANT_FISCAL(_NOUVEAU)?$', r'(^|_)IDENTIFIANT_TP$',
    r'(^|_)CNSS_NUMERO$', r'_CNSS_IMMATRICULATION$', r'IMMATRICULATION_(DATE|NUMERO|REGISTRE)$',
    r'^CERTIFICAT_NEGATIF_', r'^BULLETIN_OFFICIEL_', r'_BO_DATE_PARUTION$', r'DEPOT_LEGAL', r'DEPOT_GREFFE_(DATE|NUMERO)$',
    r'_ENREGISTREMENT_(DATE|REFERENCE)$', r'^ENREGISTREMENT_', r'^QUITUS_FISCAL_', r'RECEPISSE',
    r'^SUCCURSALE_TP_', r'^TAXE_SERVICES_COMMUNAUX_NUMERO$',
]
EXCLURE = [r'_DATE_LIMITE$', r'^LIQUIDATION_CLOTURE_BO_DEMANDE_DATE$']

# Noms des gabarits du classpath servis hors corpus (donnees de la societe traitee seulement).
# ASSOCIE_PRINCIPAL_IF (identifiant fiscal d'un associe) est un tiers : interne.
HORS_CORPUS = ['DEPOT_ACTES_REFERENCE']


# Libelles du rapport (francais accentue, ecrits en echappements pour garder le script en ASCII).
LIBELLES = {
    'Registre du commerce': 'Registre du commerce',
    'ICE': 'ICE',
    'Identifiant fiscal': 'Identifiant fiscal',
    'Taxe professionnelle': 'Taxe professionnelle',
    'CNSS': 'CNSS',
    'Immatriculation': 'Immatriculation',
    'Depot au greffe': 'D\u00e9p\u00f4t au greffe',
    'Recepisse': 'R\u00e9c\u00e9piss\u00e9',
    "identifiant ou piece d'un tiers": "Identifiant ou pi\u00e8ce d\u2019un tiers",
    'hors des categories retenues (immatriculation, depot, recepisse, RC, ICE, IF, TP, CNSS)':
        'Hors des cat\u00e9gories retenues (immatriculation, d\u00e9p\u00f4t, r\u00e9c\u00e9piss\u00e9, RC, ICE, IF, TP, CNSS)',
}


def categorie(nom):
    for cat, regles in CATEGORIES:
        if any(re.search(r, nom) for r in regles):
            return cat
    return None


def raison_ecart(nom):
    if re.search(r'^(ACQUEREUR|APPORTEUR|APPORT_BENEFICIAIRE|APPORT_FONDS|APPORT_ORIGINE|ASSOCIE|ATTESTATION_TITRES|BAILLEUR|BE_|BONI_|'
                 r'CESSION_|COMPTE_COURANT|CREANCIER|DELEGATAIRE|DIVIDENDE_|DOMICILIATAIRE|DPS_|FONDS_|FUSION_|GARANTIE_|'
                 r'LIQUIDATEUR_|LOCATION_GERANCE|NANTISSEMENT_|POUVOIR_MANDATAIRE|SCISSION_|SOUSCRIPTEUR_|SUCCURSALE_DOMICILIATAIRE|'
                 r'SALARIE_|INVESTISSEMENT_DETENU|SUCCURSALE_ETRANGERE_|SUCCURSALE_BAIL)', nom):
        return "identifiant ou piece d'un tiers"
    return 'hors des categories retenues (immatriculation, depot, recepisse, RC, ICE, IF, TP, CNSS)'


def variables(racine):
    z = zipfile.ZipFile(os.path.join(racine, '00_COMMUN', 'DICTIONNAIRE_UNIQUE_VARIABLES.xlsx'))
    feuille = z.read('xl/worksheets/sheet2.xml').decode('utf-8')
    for ligne in re.findall(r'<row[^>]*>(.*?)</row>', feuille, re.S)[1:]:
        m = re.search(r'<c r="A\d+"[^>]*>(.*?)</c>', ligne, re.S)
        if m:
            t = re.findall(r'<t[^>]*>(.*?)</t>', m.group(1), re.S)
            if t:
                yield html.unescape(''.join(t)).strip()


def main():
    racine = sys.argv[1]
    retenues, ecartees = {}, {}
    for v in variables(racine):
        nom = v.lstrip('$')
        if any(re.search(r, nom) for r in EXCLURE):
            continue
        cat = categorie(nom)
        if cat:
            retenues[nom] = cat
        elif any(re.search(r, nom) for r in CANDIDATS):
            ecartees[nom] = raison_ecart(nom)
    with open(SORTIE, 'w', encoding='ascii') as f:
        f.write('# Lot L3 -- variables EXTERNES (regle des variables, CLAUDE.md ; D2 revue le 2026-10-10).\n')
        f.write('# Genere par scripts/l3/variables_externes.py depuis %s.\n' % os.path.basename(os.path.normpath(racine)))
        f.write('# Donnees de la societe traitee attendues d\'un organisme : RC, ICE, IF, TP, CNSS, immatriculation,\n')
        f.write('# depot, recepisse. Les identifiants des tiers sont internes. Toute variable absente est INTERNE :\n')
        f.write('# manquante, elle bloque la generation et la donnee est nommee.\n')
        f.write('# Section [corpus] : noms du dictionnaire unique (controles au chargement).\n')
        f.write('[corpus]\n')
        for n in sorted(retenues):
            f.write(n + '\n')
        f.write('# Section [hors_corpus] : noms des gabarits du classpath servis hors corpus.\n')
        f.write('[hors_corpus]\n')
        for n in sorted(HORS_CORPUS):
            f.write(n + '\n')
    if len(sys.argv) > 2:
        with open(sys.argv[2], 'w', encoding='utf-8') as f:
            f.write(rapport(racine, retenues, ecartees))
    print(len(retenues), 'variables externes ;', len(ecartees), 'candidats ecartes')


def rapport(racine, retenues, ecartees):
    l = ['# Lot L3 \u2014 liste finale des variables externes', '',
         'Corpus : `%s`. Fichier versionn\u00e9 : `backend-java/ai-service/src/main/resources/templates/v2/variables-externes.txt`, '
         'produit par `scripts/l3/variables_externes.py`.' % os.path.basename(os.path.normpath(racine)), '',
         'R\u00e8gle (d\u00e9cision du 2026-10-10) : seules les donn\u00e9es de la **soci\u00e9t\u00e9 trait\u00e9e** (et de ses propres succursales et '
         '\u00e9tablissements) attendues d\u2019un organisme sont externes \u2014 RC, ICE, IF, TP, CNSS, dates et num\u00e9ros d\u2019immatriculation, '
         'de d\u00e9p\u00f4t, de r\u00e9c\u00e9piss\u00e9. Manquantes, elles sortent marqu\u00e9es \u00ab \u00c0 OBTENIR \u00bb, sont r\u00e9clam\u00e9es, et l\u2019acte se r\u00e9g\u00e9n\u00e8re '
         'quand elles arrivent. Toute autre variable est interne : manquante, elle bloque la g\u00e9n\u00e9ration et elle est nomm\u00e9e.', '']
    total = len(retenues) + len(HORS_CORPUS)
    l.append('## Externes (%d)' % total)
    l.append('')
    for cat, _ in CATEGORIES:
        noms = sorted(n for n, c in retenues.items() if c == cat)
        if noms:
            l.append('### %s (%d)' % (LIBELLES[cat], len(noms)))
            l += ['- `$%s`' % n for n in noms]
            l.append('')
    l.append('### Gabarits du classpath servis hors corpus (%d)' % len(HORS_CORPUS))
    l += ['- `%s` (d\u00e9p\u00f4t au greffe)' % n for n in HORS_CORPUS]
    l.append('')
    l.append('## Candidats \u00e9cart\u00e9s, donc internes (%d)' % len(ecartees))
    l.append('')
    l.append('Identifiants ou pi\u00e8ces d\u2019un organisme qui ne sont pas des donn\u00e9es de la soci\u00e9t\u00e9 trait\u00e9e, ou qui sortent des '
             'cat\u00e9gories retenues. \u00c0 relire : un nom \u00e0 reclasser se corrige dans le script, puis la liste est r\u00e9g\u00e9n\u00e9r\u00e9e.')
    l.append('')
    for raison in sorted(set(ecartees.values())):
        noms = sorted(n for n, r in ecartees.items() if r == raison)
        l.append('### %s (%d)' % (LIBELLES[raison], len(noms)))
        l.append(', '.join('`$%s`' % n for n in noms))
        l.append('')
    l.append('Hors corpus : `ASSOCIE_PRINCIPAL_IF` (identifiant fiscal d\u2019un associ\u00e9) est un tiers, interne.')
    l.append('')
    return '\n'.join(l)


if __name__ == '__main__':
    main()
