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

# Regles d'inclusion (identifiants et pieces delivres par un organisme).
INCLURE = [
    r'(^|_)RC_NUMERO(_NOUVEAU)?$', r'^NUMERO_RC$',
    r'(^|_)ICE$',
    r'(^|_)IDENTIFIANT_FISCAL(_NOUVEAU)?$',
    r'(^|_)IDENTIFIANT_TP$',
    r'(^|_)CNSS_NUMERO$', r'^SALARIE_CNSS_IMMATRICULATION$',
    r'^DATE_IMMATRICULATION(_NOUVELLE)?$', r'_IMMATRICULATION_DATE$', r'_DATE_IMMATRICULATION$',
    r'_IMMATRICULATION_NUMERO$', r'_IMMATRICULATION_REGISTRE$',
    r'^CERTIFICAT_NEGATIF_(NUMERO|DATE|CADUCITE_DATE)$',
    r'^BULLETIN_OFFICIEL_', r'_BO_DATE_PARUTION$',
    r'^DEPOT_LEGAL_NUMERO$', r'^DATE_DEPOT_LEGAL$', r'(^|_)DEPOT_GREFFE_(NUMERO|DATE)$',
    r'_ENREGISTREMENT_(DATE|REFERENCE)$', r'^ENREGISTREMENT_ACTE_(DATE|REFERENCE)$', r'^ENREGISTREMENT_LIEU$',
    r'^QUITUS_FISCAL_(DATE|REFERENCE|AUTORITE)$',
    r'^CNDP_RECEPISSE_NUMERO$',
    r'^SUCCURSALE_TP_CERTIFICAT_(NUMERO|DATE)$', r'^SUCCURSALE_TP_SERVICE_EMETTEUR$',
    r'^TAXE_SERVICES_COMMUNAUX_NUMERO$',
]
# Exclusions explicites : delais calcules, drapeaux et decisions de la societe.
EXCLURE = [r'_DATE_LIMITE$', r'^LIQUIDATION_CLOTURE_BO_DEMANDE_DATE$']

# Noms des gabarits du classpath servis hors corpus (ancienne liste fill_later, partie externe
# seulement) : controles par un test contre le texte de ces gabarits.
# DATE_DEPOT_AU_TC, DEPOT_NUMERO, NUMERO_DEPOT, NUMERO_DEPOT_AU_TC et NUMERO_RC n'apparaissent
# dans aucun gabarit : ecartes (nom jamais ecrit).
HORS_CORPUS = ['ASSOCIE_PRINCIPAL_IF', 'DEPOT_ACTES_REFERENCE']


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
    retenues = []
    for v in variables(racine):
        nom = v.lstrip('$')
        if any(re.search(r, nom) for r in INCLURE) and not any(re.search(r, nom) for r in EXCLURE):
            retenues.append(nom)
    with open(SORTIE, 'w', encoding='ascii') as f:
        f.write('# Lot L3 -- variables EXTERNES (regle des variables, CLAUDE.md).\n')
        f.write('# Genere par scripts/l3/variables_externes.py depuis %s.\n' % os.path.basename(os.path.normpath(racine)))
        f.write('# Proposition a valider par le directeur. Une variable absente de cette liste est INTERNE :\n')
        f.write('# manquante, elle bloque la generation et la donnee est nommee.\n')
        f.write('# Section [corpus] : noms du dictionnaire unique (controles au chargement).\n')
        f.write('[corpus]\n')
        for n in sorted(set(retenues)):
            f.write(n + '\n')
        f.write('# Section [hors_corpus] : noms des gabarits du classpath servis hors corpus.\n')
        f.write('[hors_corpus]\n')
        for n in sorted(HORS_CORPUS):
            f.write(n + '\n')
    print(len(set(retenues)), 'variables externes ecrites dans', os.path.normpath(SORTIE))


if __name__ == '__main__':
    main()
