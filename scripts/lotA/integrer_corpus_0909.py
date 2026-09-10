#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Lot A, phases 3 et 4 - bascule du corpus CREATION vers la livraison du 9 septembre.

RETRAIT (phase 3)
  Les 7 entrees de manifeste du workflow CREATION_SARL partent.
  Cinq fichiers seulement sont supprimes : STATUTS_SARL_modele_deterministe.docx
  et STATUTS_SARL_AU_modele_deterministe.docx RESTENT, parce que les entrees
  STATUTS_REFONDUS_SARL / _AU du workflow MODIFICATION les consomment aussi
  (RefonteStatutsVarsBuilder). Les supprimer casserait la refonte des statuts a
  l'execution, pas a la compilation.

INTEGRATION (phase 4)
  Les 23 gabarits du cabinet, copies sans la moindre retouche, et declares au
  manifeste avec leur champ `workflow` - qui est fonctionnel : il pilote le
  routage et le listage.

Les variables et les boucles declarees sont RELEVEES DANS LE GABARIT, jamais
saisies a la main : une declaration ecrite a cote du fichier finit toujours par
mentir.

Archive locale non versionnee : projet/.tmp/lotA-archive-creation/
"""
import io
import json
import os
import re
import shutil
import sys
import zipfile
from collections import Counter

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

PROJET = r'C:\dev\JURIKA\projet'
LIVRAISON = r'C:\dev\JURIKA\specs\creation-2026-09-09\gabarits_word'
DOCX = os.path.join(PROJET, r'backend-java\ai-service\src\main\resources\templates\docx')
MANIFEST = os.path.join(PROJET, r'backend-java\ai-service\src\main\resources\templates\v2\manifest.json')
ARCHIVE = os.path.join(PROJET, r'.tmp\lotA-archive-creation')

WORKFLOW = 'CREATION_SARL'
ORIGINE = 'cabinet-2026-09-09'
STYLE = 'dollar_nu_directeur'

# Fichiers a supprimer : ceux que SEULE la creation consomme.
A_SUPPRIMER = [
    'ACTE_NOMINATION_GERANT_modele_deterministe.docx',
    'ANNONCE_LEGALE_modele_deterministe.docx',
    'DECLARATION_EXISTENCE.docx',
    'DECLARATION_IMMATRICULATION_RC.docx',
    'DEMANDE_TAXE_PROFESSIONNELLE.docx',
]
# Fichiers de creation a CONSERVER : la MODIFICATION s'en sert.
A_CONSERVER = [
    'STATUTS_SARL_modele_deterministe.docx',
    'STATUTS_SARL_AU_modele_deterministe.docx',
]

LIBELLES = {
    'ACTE_NOMINATION_GERANT': "Acte de nomination du gerant",
    'ANNONCE_LEGALE_CONSTITUTION': "Annonce legale de constitution",
    'ATTESTATION_SOUSCRIPTION_LIBERATION': "Declaration de souscription et de versement",
    'BORDEREAU_REMISE_DOSSIER': "Bordereau de remise, de restitution ou de recapitulatif des pieces",
    'CONTRAT_BAIL': "Contrat de bail commercial (loi n° 49-16)",
    'CONTRAT_DOMICILIATION': "Contrat de domiciliation (loi n° 89-17)",
    'DECLARATION_BENEFICIAIRES_EFFECTIFS': "Declaration des beneficiaires effectifs",
    'DECLARATION_CNDP': "Declaration de traitement de donnees personnelles (CNDP)",
    'DECLARATION_EXISTENCE': "Declaration d'existence - modele DGI ADP050B, art. 148 CGI",
    'DECLARATION_IMMATRICULATION_RC': "Declaration d'immatriculation au registre du commerce - modele n° 2",
    'DEMANDE_ADHESION_SIMPL': "Demande d'adhesion aux teleservices fiscaux SIMPL",
    'DEMANDE_AFFILIATION_CNSS': "Demande d'affiliation a la Caisse nationale de securite sociale",
    'DEMANDE_DEBLOCAGE_CAPITAL': "Demande de deblocage du capital social",
    'DEMANDE_TAXE_PROFESSIONNELLE': "Demande d'inscription a la taxe professionnelle - modele DGI AAC050B",
    'ETAT_ACTES_SOCIETE_EN_FORMATION': "Etat des actes accomplis pour le compte de la societe en formation",
    'FICHE_RENSEIGNEMENTS_CREATION': "Fiche de renseignements du dossier de creation",
    'LETTRE_RETRAIT_DEPOT': "Lettre de retrait, de regularisation ou de restitution d'un depot",
    'NOTE_ANNULATION_DOSSIER': "Note d'annulation du dossier",
    'NOTE_CONFORMITE_MENTIONS_LEGALES': "Note de conformite des mentions legales",
    'POUVOIR_FORMALITES_CREATION': "Pouvoir pour l'accomplissement des formalites de creation",
    'RAPPORT_COMMISSAIRE_APPORTS': "Rapport du commissaire aux apports",
    'STATUTS_SARL': "Statuts constitutifs SARL",
    'STATUTS_SARL_AU': "Statuts constitutifs SARL a associe unique",
}

RE_VAR = re.compile(r'\$([A-Z][A-Z0-9_]*)')
RE_DEB = re.compile('\u25bc' + r'\s*D[\u00c9E]BUT\s+BOUCLE\s*[\u2014\u2013-]\s*([A-Z0-9_]+)')
RE_FIN = re.compile('\u25b2' + r'\s*FIN\s+BOUCLE\s*[\u2014\u2013-]\s*([A-Z0-9_]+)')


def paragraphes(chemin):
    z = zipfile.ZipFile(chemin)
    out = []
    for n in z.namelist():
        if not (n.startswith('word/') and n.endswith('.xml')):
            continue
        xml = re.sub(r'<w:tab\b[^>]*/>', ' ', z.read(n).decode('utf-8', 'replace'))
        for b in re.split(r'</w:p\s*>', xml):
            t = re.sub(r'<[^>]+>', '', b)
            out.append((t.replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>')
                         .replace('&quot;', '"').replace('&apos;', "'")).strip())
    return out


def releve(chemin):
    """(variables hors boucle, {boucle: [variables]}) tels que le gabarit les porte."""
    hors, boucles, pile = set(), {}, []
    for t in paragraphes(chemin):
        deb, fin = RE_DEB.search(t), RE_FIN.search(t)
        if deb:
            pile.append(deb.group(1))
            boucles.setdefault(deb.group(1), set())
            continue
        if fin:
            if pile:
                pile.pop()
            continue
        noms = RE_VAR.findall(t)
        if pile:
            for b in pile:
                boucles[b] |= set(noms)
        else:
            hors |= set(noms)
    return sorted(hors), {k: sorted(v) for k, v in sorted(boucles.items())}


def decompte(templates):
    return Counter(e.get('workflow') for e in templates)


def main():
    manifest = json.load(open(MANIFEST, encoding='utf-8'))
    avant = decompte(manifest['templates'])

    os.makedirs(ARCHIVE, exist_ok=True)
    shutil.copy2(MANIFEST, os.path.join(ARCHIVE, 'manifest.avant.json'))

    # ── Phase 3 : retrait ────────────────────────────────────────────
    for f in A_SUPPRIMER:
        src = os.path.join(DOCX, f)
        if not os.path.exists(src):
            print('  deja absent : ' + f)
            continue
        shutil.copy2(src, os.path.join(ARCHIVE, f))
        os.remove(src)
        print('  supprime (archive) : ' + f)
    for f in A_CONSERVER:
        assert os.path.exists(os.path.join(DOCX, f)), 'CONSERVE MANQUANT : ' + f
        print('  conserve (MODIFICATION s en sert) : ' + f)

    retirees = [e for e in manifest['templates'] if e.get('workflow') == WORKFLOW]
    manifest['templates'] = [e for e in manifest['templates'] if e.get('workflow') != WORKFLOW]
    print('  entrees de manifeste retirees : %d (%s)'
          % (len(retirees), ', '.join(e['code'] for e in retirees)))

    # ── Phase 4 : integration ────────────────────────────────────────
    nouvelles = []
    for f in sorted(os.listdir(LIVRAISON)):
        if not f.endswith('.docx'):
            continue
        code = f[:-5]
        shutil.copy2(os.path.join(LIVRAISON, f), os.path.join(DOCX, f))
        hors, boucles = releve(os.path.join(LIVRAISON, f))
        nouvelles.append({
            'code': code,
            'file': f,
            'origin': ORIGINE,
            'document_kind': LIBELLES[code],
            'workflow': WORKFLOW,
            'placeholder_style': STYLE,
            'variables': hors,
            'blocks': [{'name': b, 'variables': v} for b, v in boucles.items()],
        })
    manifest['templates'].extend(nouvelles)
    manifest['generated_at'] = '2026-09-10'

    with open(MANIFEST, 'w', encoding='utf-8') as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)
        fh.write('\n')

    apres = decompte(manifest['templates'])
    print()
    print('  %-24s %6s %6s' % ('workflow', 'avant', 'apres'))
    for w in sorted(set(avant) | set(apres)):
        marque = '' if avant[w] == apres[w] else '   <-- change'
        print('  %-24s %6d %6d%s' % (w, avant[w], apres[w], marque))
    print()
    print('  entrees totales : %d -> %d' % (sum(avant.values()), sum(apres.values())))
    fichiers = [f for f in os.listdir(DOCX) if f.endswith('.docx')]
    print('  fichiers .docx  : %d' % len(fichiers))
    manquants = [e['file'] for e in manifest['templates']
                 if not os.path.exists(os.path.join(DOCX, e['file']))]
    print('  entrees sans fichier : %s' % (manquants or 'aucune'))
    orphelins = sorted(set(fichiers) - {e['file'] for e in manifest['templates']})
    print('  fichiers sans entree : %s' % (orphelins or 'aucun'))


if __name__ == '__main__':
    main()
