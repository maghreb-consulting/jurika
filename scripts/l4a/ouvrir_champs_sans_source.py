#!/usr/bin/env python3
"""Lot L4a (E1b) : ouvre a l'etape 7 de la creation les champs des variables "sans source".

Le catalogue `creation-champs.json` (lot B, 2026-09-11) laissait 55 variables sans champ,
dans l'attente d'un arbitrage du cabinet ("a saisir", "derivable", "rubrique vide"). Le
dictionnaire unique du corpus du 2026-10-03 propose pour chacune la provenance "Saisie"
(a valider par le directeur). Ce script :
  - deplace dans `champs` les variables saisies a l'etape 7 (libelle, type, aide) ;
  - laisse dans `sans_source` celles qui se saisissent ailleurs, avec leur destination :
    apport en nature (etape 6, par associe) et annulation (ecran d'annulation) ;
  - reecrit le tableau CHAMPS_CREATION et VARIABLES_SANS_SOURCE du fichier TS genere.

Usage : python3 scripts/l4a/ouvrir_champs_sans_source.py [racine du depot]
Le script est idempotent : une variable deja ouverte n'est pas dupliquee.
"""
import json
import re
import sys
from pathlib import Path

RACINE = Path(sys.argv[1] if len(sys.argv) > 1 else '.')
CATALOGUE = RACINE / 'backend-java/ai-service/src/main/resources/templates/v2/creation-champs.json'
TS = RACINE / 'frontend-react/src/pages/workflow/steps/documents-creation.generated.ts'

# Libelles affiches (contenu utilisateur : francais accentue, echappe ici en \\u pour l'ASCII du script).
D = 'date'
T = 'text'
X = 'textarea'
S = 'select'
OUVERTS = {
    'BAILLEUR_TYPE': ('Le bailleur est', S, ['personne physique', 'personne morale']),
    'BAILLEUR_CIVILITE': ('Civilit\u00e9 du bailleur', S, ['M.', 'Mme']),
    'BAILLEUR_PRENOM': ('Pr\u00e9nom du bailleur', T, None),
    'BAILLEUR_NOM': ('Nom du bailleur', T, None),
    'BAILLEUR_NATIONALITE': ('Nationalit\u00e9 du bailleur', T, None),
    'BAILLEUR_ADRESSE': ('Adresse du bailleur', T, None),
    'BAILLEUR_PIECE_TYPE': ("Type de pi\u00e8ce d'identit\u00e9 du bailleur", T, None),
    'BAILLEUR_PIECE_NUMERO': ("Num\u00e9ro de la pi\u00e8ce d'identit\u00e9 du bailleur", T, None),
    'BAILLEUR_DENOMINATION': ('D\u00e9nomination de la soci\u00e9t\u00e9 bailleresse', T, None),
    'BAILLEUR_FORME': ('Forme juridique de la soci\u00e9t\u00e9 bailleresse', T, None),
    'BAILLEUR_CAPITAL': ('Capital de la soci\u00e9t\u00e9 bailleresse (en dirhams)', T, None),
    'BAILLEUR_SIEGE': ('Si\u00e8ge social de la soci\u00e9t\u00e9 bailleresse', T, None),
    'BAILLEUR_RC_VILLE': ('Ville du registre du commerce du bailleur', T, None),
    'BAILLEUR_RC_NUMERO': ('Num\u00e9ro au registre du commerce du bailleur', T, None),
    'BAILLEUR_REPRESENTANT_NOM': ('Repr\u00e9sentant de la soci\u00e9t\u00e9 bailleresse', T, None),
    'BAILLEUR_REPRESENTANT_QUALITE': ('Qualit\u00e9 du repr\u00e9sentant du bailleur', T, None),
    'BAIL_TITRE_FONCIER': ('Num\u00e9ro du titre foncier des locaux (facultatif)', T, None),
    'BAIL_TITRE_FONCIER_NOM': ('Nom de la propri\u00e9t\u00e9 au titre foncier', T, None),
    'DOMICILIATAIRE_DENOMINATION': ('D\u00e9nomination du domiciliataire', T, None),
    'DOMICILIATAIRE_FORME': ('Forme juridique du domiciliataire', T, None),
    'DOMICILIATAIRE_CAPITAL': ('Capital du domiciliataire (en dirhams)', T, None),
    'DOMICILIATAIRE_SIEGE': ('Si\u00e8ge social du domiciliataire', T, None),
    'DOMICILIATAIRE_RC_VILLE': ('Ville du registre du commerce du domiciliataire', T, None),
    'DOMICILIATAIRE_RC_NUMERO': ('Num\u00e9ro au registre du commerce du domiciliataire', T, None),
    'DOMICILIATAIRE_ICE': ('ICE du domiciliataire', T, None),
    'DOMICILIATAIRE_REPRESENTANT_NOM': ('Repr\u00e9sentant du domiciliataire', T, None),
    'DOMICILIATAIRE_REPRESENTANT_QUALITE': ('Qualit\u00e9 du repr\u00e9sentant du domiciliataire', T, None),
    'COMMISSAIRE_APPORTS_MODE_DESIGNATION': ('D\u00e9signation du commissaire aux apports', S,
                                             ['unanimit\u00e9', 'ordonnance du pr\u00e9sident du tribunal']),
    'COMMISSAIRE_APPORTS_DATE_DESIGNATION': ('Date de d\u00e9signation du commissaire aux apports', D, None),
    'COMMISSAIRE_APPORTS_QUALITE': ('Qualit\u00e9 professionnelle du commissaire aux apports', T, None),
    'COMMISSAIRE_APPORTS_ADRESSE': ('Adresse professionnelle du commissaire aux apports', T, None),
    'COMMISSAIRE_APPORTS_DILIGENCES_COMPLEMENTAIRES': ('Diligences propres au dossier', X, None),
    'COMMISSAIRE_APPORTS_OBSERVATIONS': ('Observations du commissaire aux apports (facultatif)', X, None),
    'COMMISSAIRE_APPORTS_LIEU': ('Lieu de signature du rapport du commissaire', T, None),
    'COMMISSAIRE_APPORTS_DATE_RAPPORT': ('Date du rapport du commissaire aux apports', D, None),
    'COMMISSAIRE_COMPTES_ADRESSE': ('Adresse professionnelle du commissaire aux comptes', T, None),
    'BE_CHAINE_DETENTION': ('Cha\u00eene de d\u00e9tention (d\u00e9tention indirecte)', X, None),
    'BE_INTERMEDIAIRE_DENOMINATION': ('Soci\u00e9t\u00e9 interm\u00e9diaire : d\u00e9nomination', T, None),
    'BE_INTERMEDIAIRE_RC_NUMERO': ('Soci\u00e9t\u00e9 interm\u00e9diaire : num\u00e9ro au registre du commerce', T, None),
    'BE_INTERMEDIAIRE_RC_VILLE': ('Soci\u00e9t\u00e9 interm\u00e9diaire : ville du registre du commerce', T, None),
    'RBE_PIECES_COMPLEMENT': ('Pi\u00e8ces compl\u00e9mentaires (b\u00e9n\u00e9ficiaires effectifs)', X, None),
    'CNSS_PIECES_COMPLEMENT': ("Pi\u00e8ces compl\u00e9mentaires exig\u00e9es par l'agence CNSS", X, None),
    'SIMPL_PIECES_COMPLEMENT': ('Pi\u00e8ces compl\u00e9mentaires exig\u00e9es par le service (SIMPL)', X, None),
    'DEBLOCAGE_PIECES_COMPLEMENT': ('Pi\u00e8ces compl\u00e9mentaires exig\u00e9es par la banque', X, None),
    'POUVOIR_ETENDUE_COMPLEMENT': ('Diligence suppl\u00e9mentaire confi\u00e9e au mandataire', X, None),
    'CNDP_RECEPISSE_NUMERO': ('Num\u00e9ro du r\u00e9c\u00e9piss\u00e9 de la CNDP', T, None),
}
# Variables qui restent sans champ a l'etape 7 : elles se saisissent ailleurs.
AILLEURS = {
    'APPORT_NATURE_METHODE': "\u00e9tape 6, apport en nature de l'associ\u00e9",
    'APPORT_NATURE_ORIGINE_PROPRIETE': "\u00e9tape 6, apport en nature de l'associ\u00e9",
    'APPORT_NATURE_CHARGES': "\u00e9tape 6, apport en nature de l'associ\u00e9",
    'ANNULATION_ADMINISTRATION': "\u00e9cran d'annulation du ticket",
    'ANNULATION_DECISION_REFERENCE': "\u00e9cran d'annulation du ticket",
    'RETRAIT_ADMINISTRATION_DESIGNATION': "d\u00e9marches de d\u00e9p\u00f4t du ticket (annulation)",
    'RETRAIT_ADMINISTRATION_ADRESSE': "\u00e9cran d'annulation du ticket",
    'RETRAIT_DEPOT_DATE': "d\u00e9marches de d\u00e9p\u00f4t du ticket (annulation)",
    'RETRAIT_DEPOT_REFERENCE': "d\u00e9marches de d\u00e9p\u00f4t du ticket (annulation)",
}


def cle(variable):
    return re.sub(r'_([a-z0-9])', lambda m: m.group(1).upper(), variable.lower())


def main():
    cat = json.loads(CATALOGUE.read_text(encoding='utf-8'))
    deja = {c['variable'] for c in cat['champs']}
    restants = []
    ouverts = 0
    for x in cat['sans_source']:
        v = x['variable']
        if v in OUVERTS:
            if v not in deja:
                label, typ, options = OUVERTS[v]
                cat['champs'].append({
                    'variable': v, 'cle': cle(v), 'label': label, 'type': typ, 'options': options,
                    'aide': x.get('aide'), 'section': x.get('section'), 'documents': x['documents'],
                    'boucle': None, 'ouvertL4a': True,
                })
                ouverts += 1
        elif v in AILLEURS:
            x['saisieAilleurs'] = AILLEURS[v]
            restants.append(x)
        else:
            raise SystemExit('variable sans destination : ' + v)
    cat['sans_source'] = restants
    # PIECE_DESIGNATION : classee "derivable" au lot B, jamais derivee ; c'est la designation
    # de chaque piece remise, saisie dans la boucle PIECES_REMISES du bordereau.
    if 'PIECE_DESIGNATION' not in deja:
        cat['derivables'] = [d for d in cat['derivables'] if d['variable'] != 'PIECE_DESIGNATION']
        cat['champs'].append({
            'variable': 'PIECE_DESIGNATION', 'cle': 'pieceDesignation', 'label': 'D\u00e9signation de la pi\u00e8ce',
            'type': 'text', 'options': None, 'aide': None, 'section': 'Bordereau de remise',
            'documents': ['BORDEREAU_REMISE_DOSSIER'], 'boucle': 'PIECES_REMISES', 'ouvertL4a': True,
        })
        for b in cat['boucles']:
            if b['nom'] == 'PIECES_REMISES' and 'pieceDesignation' not in b['champs']:
                b['champs'].insert(0, 'pieceDesignation')
    CATALOGUE.write_text(json.dumps(cat, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    ts = TS.read_text(encoding='utf-8')
    champs = json.dumps(cat['champs'], ensure_ascii=False, indent=2)
    ts, n1 = re.subn(r'(export const CHAMPS_CREATION: ChampCreation\[\] = )\[.*?\];\n\nexport const BOUCLES_CREATION',
                     lambda m: m.group(1) + champs + ';\n\nexport const BOUCLES_CREATION', ts, flags=re.S)
    boucles = json.dumps(cat['boucles'], ensure_ascii=False, indent=2)
    ts, n4 = re.subn(r'(export const BOUCLES_CREATION: BoucleCreation\[\] = )\[.*?\];',
                     lambda m: m.group(1) + boucles + ';', ts, flags=re.S)
    if n4 != 1:
        raise SystemExit('BOUCLES_CREATION introuvable')
    sans = json.dumps([x['variable'] for x in restants], ensure_ascii=False, indent=2)
    ts, n2 = re.subn(r'(export const VARIABLES_SANS_SOURCE: string\[\] = )\[.*?\];',
                     lambda m: m.group(1) + sans + ';', ts, flags=re.S)
    if n1 != 1 or n2 != 1:
        raise SystemExit('fichier TS : tableaux introuvables (%d, %d)' % (n1, n2))
    if 'ouvertL4a?: boolean;' not in ts:
        ts, n3 = re.subn(r'(  heritee\?: boolean;\n)', r'\1  /** Lot L4a (E1b) : champ ouvert pour une variable "sans source" du lot B. */\n  ouvertL4a?: boolean;\n', ts, count=1)
        if n3 != 1:
            raise SystemExit('interface ChampCreation introuvable')
    TS.write_text(ts, encoding='utf-8')
    print('%d champs ouverts, %d variables saisies ailleurs' % (ouverts, len(restants)))


if __name__ == '__main__':
    main()
