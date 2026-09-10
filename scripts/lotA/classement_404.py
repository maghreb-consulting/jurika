#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Lot A - classement des 404 variables du corpus CREATION en cinq categories.

La frontiere entre « a saisir » et « sans source » est une decision, pas un
calcul. Elle est posee ici explicitement, une fois, pour que le lot B parte
d'un perimetre lisible :

  DEJA RESOLUE  la chaine de generation l'alimente aujourd'hui (manifeste v2,
                entrees de workflow CREATION_SARL : 151 variables).
  DERIVABLE     calculable a partir d'une donnee que la plateforme detient deja
                (total d'une boucle, rang d'une ligne, montant en lettres,
                echeance = date d'effet + duree, donnee du ticket).
  A SAISIR      donnee du dossier, reellement nouvelle : c'est la liste des
                champs a ouvrir au lot B.
  SANS SOURCE   donnee d'un TIERS ou produite par une ADMINISTRATION : aucune
                piece du dossier ne la porte, et aucun ecran ne la produira
                sans que le cabinet la releve ailleurs. A signaler au cabinet.
  RENOMMEE      la plateforme connait la donnee sous un autre nom : l'alignement
                se fait dans la resolution, jamais dans le .docx.

Sortie : output/2026-09-10_lotA_ecart_404_variables.md
"""
import io
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecart_variables as ev  # noqa: E402

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

SORTIE = r'C:\dev\JURIKA\projet\output\2026-09-10_lotA_ecart_404_variables.md'

DERIVABLES = {
    # Totaux et decomptes : les lignes sont deja au dossier, la somme se calcule.
    'SOUSCRIPTIONS_TOTAL_SOUSCRIT', 'SOUSCRIPTIONS_TOTAL_VERSE',
    'SOUSCRIPTIONS_TOTAL_VERSE_LETTRES', 'SOUSCRIPTIONS_SOLDE_TOTAL',
    'SOUSCRIPTEUR_SOLDE_A_LIBERER', 'ACTES_EN_FORMATION_TOTAL',
    'APPORTS_NATURE_TOTAL_CHIFFRES', 'APPORTS_NATURE_TOTAL_LETTRES',
    'APPORTS_NATURE_PARTS_TOTAL', 'RBE_NOMBRE_BENEFICIAIRES', 'PIECES_NOMBRE_TOTAL',
    # Rangs de boucle.
    'ACTE_FORMATION_NUMERO', 'BE_NUMERO', 'PIECE_NUMERO', 'TRAITEMENT_NUMERO',
    # Recopies de la boucle ASSOCIES / APPORTS, deja resolue.
    'SOUSCRIPTEUR_LIBELLE', 'SOUSCRIPTEUR_PARTS_SOUSCRITES',
    'SOUSCRIPTEUR_MONTANT_SOUSCRIT', 'SOUSCRIPTEUR_MONTANT_VERSE',
    'APPORT_NATURE_APPORTEUR', 'APPORT_NATURE_PARTS_ATTRIBUEES',
    'BE_NOMBRE_PARTS', 'BE_POURCENTAGE',
    # Montants en lettres : FrenchNumberToLetters existe deja au moteur.
    'BAIL_LOYER_LETTRES', 'DOMICILIATION_REDEVANCE_LETTRES',
    # Echeance = date d'effet + duree.
    'BAIL_DATE_FIN',
    # Donnees du ticket, deja en base (V20 referentiel des demarches).
    'DOSSIER_NUMERO', 'DOSSIER_DATE_OUVERTURE', 'DOSSIER_CHARGE',
    'DOSSIER_DATE_ANNULATION', 'ANNULATION_STATUT_ATTEINT',
    'DEMARCHE_DESIGNATION', 'DEMARCHE_ADMINISTRATION', 'DEMARCHE_DATE_DEPOT',
    'DEMARCHE_REFERENCE', 'PIECES_MANQUANTES', 'PIECE_DESIGNATION',
}

SANS_SOURCE = {
    # Le domiciliataire est un tiers : sa fiche societe n'est pas au dossier.
    'DOMICILIATAIRE_CAPITAL', 'DOMICILIATAIRE_DENOMINATION', 'DOMICILIATAIRE_FORME',
    'DOMICILIATAIRE_ICE', 'DOMICILIATAIRE_RC_NUMERO', 'DOMICILIATAIRE_RC_VILLE',
    'DOMICILIATAIRE_REPRESENTANT_NOM', 'DOMICILIATAIRE_REPRESENTANT_QUALITE',
    'DOMICILIATAIRE_SIEGE',
    # Le bailleur aussi, et le titre foncier releve de la conservation fonciere.
    'BAILLEUR_ADRESSE', 'BAILLEUR_CAPITAL', 'BAILLEUR_CIVILITE', 'BAILLEUR_DENOMINATION',
    'BAILLEUR_FORME', 'BAILLEUR_NATIONALITE', 'BAILLEUR_NOM', 'BAILLEUR_PIECE_NUMERO',
    'BAILLEUR_PIECE_TYPE', 'BAILLEUR_PRENOM', 'BAILLEUR_RC_NUMERO', 'BAILLEUR_RC_VILLE',
    'BAILLEUR_REPRESENTANT_NOM', 'BAILLEUR_REPRESENTANT_QUALITE', 'BAILLEUR_SIEGE',
    'BAILLEUR_TYPE', 'BAIL_TITRE_FONCIER', 'BAIL_TITRE_FONCIER_NOM',
    # Le commissaire aux apports est un professionnel exterieur ; sa designation,
    # ses diligences et son rapport sont son oeuvre, pas celle du dossier.
    'COMMISSAIRE_APPORTS_ADRESSE', 'COMMISSAIRE_APPORTS_QUALITE',
    'COMMISSAIRE_APPORTS_DATE_DESIGNATION', 'COMMISSAIRE_APPORTS_DATE_RAPPORT',
    'COMMISSAIRE_APPORTS_LIEU', 'COMMISSAIRE_APPORTS_MODE_DESIGNATION',
    'COMMISSAIRE_APPORTS_DILIGENCES_COMPLEMENTAIRES', 'COMMISSAIRE_APPORTS_OBSERVATIONS',
    'COMMISSAIRE_COMPTES_ADRESSE',
    'APPORT_NATURE_METHODE', 'APPORT_NATURE_ORIGINE_PROPRIETE', 'APPORT_NATURE_CHARGES',
    # Delivres par une administration APRES le depot : n'existent pas a la generation.
    'CNDP_RECEPISSE_NUMERO', 'ANNULATION_ADMINISTRATION', 'ANNULATION_DECISION_REFERENCE',
    'RETRAIT_ADMINISTRATION_DESIGNATION', 'RETRAIT_ADMINISTRATION_ADRESSE',
    'RETRAIT_DEPOT_DATE', 'RETRAIT_DEPOT_REFERENCE',
    # « Pieces complementaires exigees par … » : la liste varie d'un guichet a l'autre.
    'DEBLOCAGE_PIECES_COMPLEMENT', 'CNSS_PIECES_COMPLEMENT', 'SIMPL_PIECES_COMPLEMENT',
    'RBE_PIECES_COMPLEMENT', 'POUVOIR_ETENDUE_COMPLEMENT',
    # Chaine de detention indirecte : structure d'un tiers.
    'BE_INTERMEDIAIRE_DENOMINATION', 'BE_INTERMEDIAIRE_RC_NUMERO',
    'BE_INTERMEDIAIRE_RC_VILLE', 'BE_CHAINE_DETENTION',
}

RENOMMEES = {
    'ICE': ('ICE_NUMERO', 'TRANCHE le 9 septembre. Cote creation, la plateforme '
            'ecrit deja $ICE : rien a aligner. $ICE_NUMERO ne survit qu\'a une '
            'entree de manifeste du workflow PV_AGO, hors perimetre.'),
    'SIEGE_VILLE': ('VILLE', 'OUVERT. Les deux noms coexistent DANS LE MEME '
                    'document (declaration d\'existence, demande de taxe '
                    'professionnelle). Non tranche ici.'),
    'SIGNATAIRE_NOM_QUALITE': ('FORMULAIRE_SIGNATAIRE', 'OUVERT. Coexiste avec la '
                               'boucle SIGNATAIRES ($SIGNATAIRE_NOM, '
                               '$SIGNATAIRE_QUALITE) dans la declaration '
                               'd\'immatriculation. Non tranche ici.'),
}


def classe(v, deja):
    if v in RENOMMEES:
        return 'RENOMMEE'
    if v in deja:
        return 'DEJA'
    if v in DERIVABLES:
        return 'DERIVABLE'
    if v in SANS_SOURCE:
        return 'SANS_SOURCE'
    return 'A_SAISIR'


ORDRE = ['DEJA', 'DERIVABLE', 'A_SAISIR', 'SANS_SOURCE', 'RENOMMEE']
TITRES = {
    'DEJA': 'Deja resolues',
    'DERIVABLE': 'Derivables',
    'A_SAISIR': 'A saisir',
    'SANS_SOURCE': 'Sans source',
    'RENOMMEE': 'Renommees',
}


def main():
    gab, man, dic = ev.vars_gabarits(), ev.vars_manifest(), ev.dictionnaire()
    groupes = {k: [] for k in ORDRE}
    for v in sorted(gab):
        groupes[classe(v, man)].append(v)

    lignes = []
    lignes.append("# Lot A - analyse d'ecart des 404 variables du corpus CREATION\n")
    lignes.append("Corpus : les 23 gabarits livres le 9 septembre 2026.  \n"
                  "Reference « deja resolue » : les variables declarees au manifeste v2 "
                  "pour le workflow `CREATION_SARL` - c'est le contrat que "
                  "`CreationDirecteurVarsBuilder` et `CreationFormulairesVarsBuilder` "
                  "honorent aujourd'hui.\n")
    lignes.append('| Categorie | Nombre | Ce que le lot B doit en faire |')
    lignes.append('|---|---:|---|')
    quoi = {
        'DEJA': 'Rien. Le mapper les produit deja.',
        'DERIVABLE': 'Les calculer dans le builder. Ne pas ouvrir de champ.',
        'A_SAISIR': 'Ouvrir le champ au parcours. **C\'est le perimetre de saisie du lot B.**',
        'SANS_SOURCE': 'Aucune donnee du dossier ne les porte : arbitrage cabinet.',
        'RENOMMEE': 'Aligner dans la resolution, jamais dans le `.docx`.',
    }
    for k in ORDRE:
        lignes.append('| %s | %d | %s |' % (TITRES[k], len(groupes[k]), quoi[k]))
    lignes.append('| **Total** | **%d** | |\n' % sum(len(groupes[k]) for k in ORDRE))

    for k in ORDRE:
        lignes.append('\n## %s (%d)\n' % (TITRES[k], len(groupes[k])))
        if k == 'RENOMMEE':
            lignes.append('| Nom du corpus | Nom cote plateforme | Etat |')
            lignes.append('|---|---|---|')
            for v in groupes[k]:
                autre, note = RENOMMEES[v]
                lignes.append('| `$%s` | `$%s` | %s |' % (v, autre, note))
            continue
        if k == 'DEJA':
            lignes.append('Les 151 variables du manifeste `CREATION_SARL`. '
                          'Le corpus du 9 septembre les reprend **toutes** : '
                          'aucune variable resolue aujourd\'hui n\'a disparu, '
                          'donc aucune regression de resolution.\n')
            lignes.append('```')
            lignes.append(', '.join('$' + v for v in groupes[k]))
            lignes.append('```')
            continue
        lignes.append('| Variable | Section du dictionnaire | Libelle |')
        lignes.append('|---|---|---|')
        for v in groupes[k]:
            sec, lib = dic.get(v, ('(non documentee)', ''))
            sec = sec.replace('**', '').replace('|', '/')
            lib = lib.replace('|', '/')[:110]
            lignes.append('| `$%s` | %s | %s |' % (v, sec, lib))

    os.makedirs(os.path.dirname(SORTIE), exist_ok=True)
    with open(SORTIE, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(lignes) + '\n')
    for k in ORDRE:
        print('%-14s %3d' % (TITRES[k], len(groupes[k])))
    print('%-14s %3d' % ('TOTAL', sum(len(groupes[k]) for k in ORDRE)))
    print('ecrit : ' + SORTIE)


if __name__ == '__main__':
    main()
