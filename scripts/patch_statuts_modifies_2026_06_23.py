# -*- coding: utf-8 -*-
"""Sprint 2026-06-23 — STATUTS_MODIFIES_SARL.docx + STATUTS_MODIFIES_SARL_AU.docx.

Rend les statuts modifiés DÉTERMINISTES par typeId :
  * chaque section est encadrée par un bloc ◇ TYPE_ID … ◆ TYPE_ID
    (sections non choisies → pruning automatique par DocxTemplateEngine) ;
  * pour les 18 types COUVERTS (cf. ResolutionTextGenerator), la clause est
    réécrite avec les variables {{snake_var}} (ANCIEN + NOUVEAU quand pertinent) ;
  * pour les types fallback (TRANSFORMATION, cessions, nantissement, fusion,
    pacte, DESIGNATION_CAC) la clause reste en l'état (« [à compléter — … ] »).

Le script est IDEMPOTENT : si la marque ◇ TYPE_ID est déjà présente sur le titre,
on saute la section.

Usage :
    PYTHONIOENCODING=utf-8 py -3 scripts/patch_statuts_modifies_2026_06_23.py
"""
from __future__ import annotations

import copy
import io
import re
import sys
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

from docx import Document  # type: ignore

ROOT = Path(__file__).resolve().parents[1]
TPL_DIR = ROOT / "backend-java" / "ai-service" / "src" / "main" / "resources" \
    / "templates" / "docx"

# ─────────────────────────────────────────────────────────────────────────────
# Mapping section title (regex sur le titre) → (TYPE_ID, clause_text_or_None)
# - Si clause_text fourni → on remplace le texte de la table de clause par
#   ce nouveau texte (avec {{variables}} et ANCIEN + NOUVEAU).
# - Si None → on laisse la clause existante (fallback annexe à fournir).
#
# Note SARL_AU : les clauses commencent par « L'associé unique décide » au lieu
# de « L'assemblée générale extraordinaire ». On adapte automatiquement.
# ─────────────────────────────────────────────────────────────────────────────

CLAUSE_AGE = "L'assemblée générale extraordinaire"
CLAUSE_AU = "L'associé unique"


def _clause(prefix: str, body: str) -> str:
    return f"{prefix} {body}"


# Catalogue de clauses (corps sans préfixe AGE/AU).
CLAUSE_BODY = {
    "CHANGEMENT_DENOMINATION":
        "décide de modifier la dénomination sociale de la société, précédemment "
        "« {{ancienne_denomination}} », pour devenir « {{nouvelle_denomination}} ». "
        "L'article 2 des statuts est modifié en conséquence.",

    "CHANGEMENT_OBJET":
        "décide de modifier l'objet social. L'ancien objet « {{ancien_objet}} » "
        "est remplacé par : « {{nouvel_objet}} ». L'article 3 des statuts "
        "est modifié en conséquence.",

    "TRANSFERT_SIEGE":
        "décide de transférer le siège social de « {{ancienne_adresse}} » à "
        "« {{nouvelle_adresse}}, {{nouvelle_ville}} », avec effet à compter du "
        "{{date_effet}}. L'article 4 des statuts est modifié en conséquence.",

    "PROROGATION_DUREE":
        "décide de proroger la durée de la société de {{annees_prorogation}} "
        "({{annees_prorogation_lettres}}) années à compter du {{date_effet}}. "
        "L'article 5 des statuts est modifié en conséquence.",

    "AUGMENTATION_CAPITAL":
        "décide d'augmenter le capital social de {{ancien_capital}} "
        "({{ancien_capital_lettres}}) MAD à {{nouveau_capital}} "
        "({{nouveau_capital_lettres}}) MAD, par création de {{nouvelles_parts}} "
        "parts sociales nouvelles libérées en numéraire (versement des fonds le "
        "{{date_versement}}). Les articles 6 et 7 des statuts sont modifiés "
        "en conséquence.",

    "AUGMENTATION_CAPITAL_RESERVES":
        "décide d'augmenter le capital social de {{ancien_capital}} "
        "({{ancien_capital_lettres}}) MAD à {{nouveau_capital}} "
        "({{nouveau_capital_lettres}}) MAD par incorporation d'un montant de "
        "{{montant_incorpore}} MAD prélevé sur les réserves disponibles. "
        "Les articles 6 et 7 des statuts sont modifiés en conséquence.",

    "REDUCTION_CAPITAL":
        "décide de réduire le capital social d'un montant de "
        "{{montant_reduction}} ({{montant_reduction_lettres}}) MAD, le ramenant "
        "de {{ancien_capital}} ({{ancien_capital_lettres}}) MAD à "
        "{{nouveau_capital}} ({{nouveau_capital_lettres}}) MAD. Motif : "
        "{{motif_reduction}}. Les articles 6 et 7 des statuts sont modifiés "
        "en conséquence.",

    "MODIF_VALEUR_NOMINALE":
        "décide de modifier la valeur nominale des parts sociales, qui est "
        "portée à {{nouvelle_valeur_nominale}} "
        "({{nouvelle_valeur_nominale_lettres}}) MAD par part. L'article 7 des "
        "statuts est modifié en conséquence.",

    "DESIGNATION_GERANT":
        "décide de nommer en qualité de gérant Monsieur/Madame "
        "{{gerant_nom_prenom}}, de nationalité {{gerant_nationalite}}, titulaire "
        "de la CIN n° {{gerant_cin}}, avec prise d'effet au {{date_effet}}. Le "
        "nouveau gérant accepte expressément ses fonctions et déclare ne tomber "
        "sous aucune incompatibilité légale ou réglementaire. L'article 12 des "
        "statuts est modifié en conséquence.",

    "REVOCATION_GERANT":
        "décide de révoquer {{gerant_revoque}} de ses fonctions de gérant, "
        "avec effet à compter du {{date_effet}}. Motif : {{motif_revocation}}. "
        "L'article 12 des statuts est modifié en conséquence.",

    "MODIF_NOMBRE_GERANTS":
        "décide de modifier les dispositions relatives au nombre et à la durée "
        "des fonctions des gérants. Modalités : {{modif_gerants_details}}. "
        "L'article 12 des statuts est modifié en conséquence.",

    "MODIF_POUVOIRS_GERANT":
        "décide de modifier les pouvoirs et/ou la rémunération du gérant. "
        "Modalités : {{modif_pouvoirs_details}}. L'article 12 des statuts est "
        "modifié en conséquence.",

    "MODALITES_DECISIONS":
        "décide de modifier les modalités de prise de décisions collectives. "
        "Stipulations : {{modalites_details}}. Les statuts sont modifiés "
        "en conséquence.",

    "CLAUSE_AGREMENT":
        "décide d'adopter ou de modifier la clause d'agrément des cessions de "
        "parts sociales. Stipulations : {{clause_agrement_texte}}. L'article 10 "
        "des statuts est modifié en conséquence.",

    "CLAUSE_PREEMPTION":
        "décide d'adopter ou de modifier le droit de préemption et/ou la clause "
        "d'inaliénabilité des parts sociales. Stipulations : "
        "{{clause_preemption_texte}}. Les statuts sont modifiés en conséquence.",

    "CONTINUATION_PERTES":
        ", conformément à l'article 86 de la loi 5-96, décide la continuation "
        "de l'activité sociale malgré les pertes constatées et le maintien des "
        "capitaux propres en deçà du quart du capital social. Précisions : "
        "{{continuation_details}}.",

    "CREATION_SUCCURSALE":
        "décide la création, le transfert ou la suppression d'une succursale. "
        "Modalités : {{succursale_details}}. L'article 17 des statuts est "
        "modifié en conséquence.",

    "POUVOIRS_FORMALITES":
        "confère tous pouvoirs à {{mandataire_formalites}} à l'effet d'accomplir "
        "toutes formalités d'enregistrement, de dépôt au greffe, d'inscription "
        "modificative au registre du commerce et de publicité légale.",
}


# Mapping titre → TYPE_ID (les regex matchent le DÉBUT du titre nettoyé).
# IDs entre crochets pour les sections sans match front (jamais activées par
# MODIFICATION mais wrap pour pouvoir être supprimées du rendu).
SARL_SECTIONS = [
    (r"^1\.\s*Changement de la dénomination",         "CHANGEMENT_DENOMINATION"),
    (r"^2\.\s*Changement de l'objet",                 "CHANGEMENT_OBJET"),
    (r"^3\.\s*Transfert du siège",                    "TRANSFERT_SIEGE"),
    (r"^4\.\s*Prorogation de la durée",               "PROROGATION_DUREE"),
    (r"^5\.\s*Transformation",                        "TRANSFORMATION"),
    (r"^6\.\s*Augmentation de capital en numéraire",  "AUGMENTATION_CAPITAL"),
    (r"^7\.\s*Augmentation de capital par apport en nature", "AUGMENTATION_CAPITAL_NATURE"),
    (r"^8\.\s*Augmentation de capital par incorporation", "AUGMENTATION_CAPITAL_RESERVES"),
    (r"^9\.\s*Réduction de capital",                  "REDUCTION_CAPITAL"),
    (r"^10\.\s*Modification de la valeur nominale",   "MODIF_VALEUR_NOMINALE"),
    (r"^11\.\s*Cession de parts entre associés",      "CESSION_PARTIELLE"),
    (r"^12\.\s*Cession de parts à un tiers",          "CESSION_TOTALE"),
    (r"^13\.\s*Transmission de parts",                "TRANSMISSION_PARTS"),
    (r"^14\.\s*Nantissement",                         "NANTISSEMENT"),
    (r"^15\.\s*Nomination d'un nouveau gérant",       "DESIGNATION_GERANT"),
    (r"^16\.\s*Révocation d'un gérant",               "REVOCATION_GERANT"),
    (r"^17\.\s*Modification du nombre de gérants",    "MODIF_NOMBRE_GERANTS"),
    (r"^18\.\s*Modification des pouvoirs",            "MODIF_POUVOIRS_GERANT"),
    (r"^19\.\s*Modification des modalités de convocation", "MODALITES_DECISIONS"),
    (r"^20\.\s*Modification des règles de quorum",    "MODALITES_DECISIONS"),
    (r"^21\.\s*Recours à la consultation écrite",     "MODALITES_DECISIONS"),
    (r"^22\.\s*Insertion ou modification d'une clause d'agrément", "CLAUSE_AGREMENT"),
    (r"^23\.\s*Insertion ou modification d'un droit de préemption", "CLAUSE_PREEMPTION"),
    (r"^24\.\s*Clauses d'inaliénabilité",             "CLAUSE_PREEMPTION"),
    (r"^25\.\s*Clause d'exclusion",                   "PACTE_ASSOCIES"),
    (r"^26\.\s*Annexion ou modification d'un pacte",  "PACTE_ASSOCIES"),
    (r"^27\.\s*Continuation de la société",           "CONTINUATION_PERTES"),
    (r"^28\.\s*Dissolution anticipée",                "DISSOLUTION_LIQUIDATION"),
    (r"^29\.\s*Fusion, scission",                     "FUSION_SCISSION"),
    (r"^30\.\s*Création, transfert ou suppression de succursales", "CREATION_SUCCURSALE"),
    (r"^31\.\s*Pouvoirs pour les formalités",         "POUVOIRS_FORMALITES"),
]

SARL_AU_SECTIONS = [
    (r"^1\.\s*Changement de la dénomination",         "CHANGEMENT_DENOMINATION"),
    (r"^2\.\s*Changement de l'objet",                 "CHANGEMENT_OBJET"),
    (r"^3\.\s*Transfert du siège",                    "TRANSFERT_SIEGE"),
    (r"^4\.\s*Prorogation de la durée",               "PROROGATION_DUREE"),
    (r"^5\.\s*Transformation",                        "TRANSFORMATION"),
    (r"^6\.\s*Augmentation de capital en numéraire",  "AUGMENTATION_CAPITAL"),
    (r"^7\.\s*Augmentation de capital par apport en nature", "AUGMENTATION_CAPITAL_NATURE"),
    (r"^8\.\s*Augmentation de capital par incorporation", "AUGMENTATION_CAPITAL_RESERVES"),
    (r"^9\.\s*Réduction de capital",                  "REDUCTION_CAPITAL"),
    (r"^10\.\s*Modification de la valeur nominale",   "MODIF_VALEUR_NOMINALE"),
    (r"^11\.\s*Cession partielle des parts",          "CESSION_PARTIELLE"),
    (r"^12\.\s*Cession totale des parts",             "CESSION_TOTALE"),
    (r"^13\.\s*Transmission des parts",               "TRANSMISSION_PARTS"),
    (r"^14\.\s*Nantissement",                         "NANTISSEMENT"),
    (r"^15\.\s*Nomination d'un nouveau gérant",       "DESIGNATION_GERANT"),
    (r"^16\.\s*Révocation d'un gérant",               "REVOCATION_GERANT"),
    (r"^17\.\s*Modification du nombre de gérants",    "MODIF_NOMBRE_GERANTS"),
    (r"^18\.\s*Modification des pouvoirs",            "MODIF_POUVOIRS_GERANT"),
    (r"^19\.\s*Modification des modalités de prise des décisions", "MODALITES_DECISIONS"),
    (r"^20\.\s*Désignation d'un commissaire aux comptes", "DESIGNATION_CAC"),
    (r"^21\.\s*Insertion d'une clause d'agrément",    "CLAUSE_AGREMENT"),
    (r"^22\.\s*Insertion d'un droit de préemption",   "CLAUSE_PREEMPTION"),
    (r"^23\.\s*Préparation et formalisation d'un pacte", "PACTE_ASSOCIES"),
    (r"^24\.\s*Continuation de la société",           "CONTINUATION_PERTES"),
    (r"^25\.\s*Dissolution anticipée",                "DISSOLUTION_LIQUIDATION"),
    (r"^26\.\s*Fusion, scission",                     "FUSION_SCISSION"),
    (r"^27\.\s*Création, transfert ou suppression de succursales", "CREATION_SUCCURSALE"),
    (r"^28\.\s*Pouvoirs pour les formalités",         "POUVOIRS_FORMALITES"),
]


def set_paragraph_text(par, new_text: str) -> None:
    """Réécrit le texte du paragraphe en conservant le style du 1er run."""
    if not par.runs:
        par.add_run(new_text)
        return
    first = par.runs[0]
    for r in par.runs[1:]:
        r._r.getparent().remove(r._r)
    first.text = new_text


def replace_clause_table_text(table, new_text: str) -> None:
    """Vide la 1re cellule d'une clause-table (1×1) et y injecte new_text.

    Les clause-tables (T1, T3, T5, …) du modèle ont 1 ligne / 1 colonne.
    On conserve le style du 1er paragraphe.
    """
    cell = table.rows[0].cells[0]
    paragraphs = cell.paragraphs
    if not paragraphs:
        cell.add_paragraph(new_text)
        return
    # Garder le 1er paragraphe (avec son style), remplacer son texte ; supprimer les autres.
    first_par = paragraphs[0]
    set_paragraph_text(first_par, new_text)
    for extra_par in paragraphs[1:]:
        extra_par._p.getparent().remove(extra_par._p)


def find_section_titles(doc, sections):
    """Retourne [(title_para_idx, type_id, section_index_in_sections), ...] dans l'ordre."""
    out = []
    used = set()
    for i, p in enumerate(doc.paragraphs):
        t = (p.text or "").strip()
        if not t:
            continue
        for s_idx, (pattern, type_id) in enumerate(sections):
            if s_idx in used:
                continue
            if re.match(pattern, t):
                out.append((i, type_id, s_idx))
                used.add(s_idx)
                break
    return out


def get_clause_table_for_section(doc, title_para_idx, sections_so_far):
    """Trouve la table de clause associée au titre = la 2e table apparaissant
    AVANT le titre suivant. On utilise le compteur global pour s'aligner :
    section k → table odd-index 2k+1 si toutes sections suivent le motif
    title/info-grid/clause-table."""
    # Comptage : on a 1 table d'info + 1 table de clause par section.
    # La k-ième section a sa clause à table-index = 2*k + 1.
    return doc.tables[2 * sections_so_far + 1]


def patch_template(docx_path: Path, sections, decision_prefix: str, label: str):
    print(f"\n=== {label} : {docx_path.name} ===")
    doc = Document(str(docx_path))
    located = find_section_titles(doc, sections)
    print(f"  Sections detectees : {len(located)}/{len(sections)}")
    if len(located) != len(sections):
        missing = [(i, s) for i, s in enumerate(sections) if i not in {l[2] for l in located}]
        for s_idx, (pat, tid) in missing:
            print(f"  [MISS] section {s_idx + 1} (regex {pat!r} typeId {tid})")
    # Trier par index de paragraphe pour pouvoir wrap titre + trailing para.
    located.sort(key=lambda x: x[0])

    # Pour chaque section : prepend ◇ TYPE_ID a titre + set trailing empty para a ◆ TYPE_ID.
    # Identifier le trailing para = title_para_idx + 4 (title, info-header, empty, clause-header, empty).
    # Vérifier que c'est bien un paragraphe vide ; sinon, créer un nouveau paragraphe vide.
    wrapped, skipped, clause_rewritten = 0, 0, 0
    for k, (title_idx, type_id, s_idx) in enumerate(located):
        title_par = doc.paragraphs[title_idx]
        title_text = title_par.text or ""

        if f"◇ {type_id}" in title_text:
            skipped += 1
        else:
            set_paragraph_text(title_par, f"◇ {type_id} {title_text}")
            wrapped += 1

        # Trailing close marker : on cible doc.paragraphs[title_idx + 4] (empty para).
        trail_idx = title_idx + 4
        if trail_idx < len(doc.paragraphs):
            trail_par = doc.paragraphs[trail_idx]
            trail_text = (trail_par.text or "").strip()
            if f"◆ {type_id}" not in trail_text:
                # Si le paragraphe est vide on le réutilise ; sinon on insère un nouveau.
                if not trail_text:
                    set_paragraph_text(trail_par, f"◆ {type_id}")
                else:
                    set_paragraph_text(trail_par, f"{trail_par.text}  ◆ {type_id}")
        else:
            # Fin de document : append un paragraphe.
            doc.add_paragraph(f"◆ {type_id}")

        # Réécriture de la clause si type couvert.
        if type_id in CLAUSE_BODY:
            body = CLAUSE_BODY[type_id]
            full = _clause(decision_prefix, body)
            clause_table = doc.tables[2 * k + 1]  # k = position d'ordre des located triés
            # Garde idempotente : si la table contient déjà des {{ on suppose déjà patchée.
            cell0_text = clause_table.rows[0].cells[0].text
            if "{{" not in cell0_text:
                replace_clause_table_text(clause_table, full)
                clause_rewritten += 1

    print(f"  ◇ titres wrappes : {wrapped} (skip idempotent : {skipped})")
    print(f"  Clauses reecrites : {clause_rewritten}/{len([s for s in sections if s[1] in CLAUSE_BODY])}")
    doc.save(str(docx_path))
    print(f"  [OK] sauvegarde {docx_path}")


def main():
    sarl = TPL_DIR / "STATUTS_MODIFIES_SARL.docx"
    sarl_au = TPL_DIR / "STATUTS_MODIFIES_SARL_AU.docx"
    assert sarl.exists(), sarl
    assert sarl_au.exists(), sarl_au

    patch_template(sarl, SARL_SECTIONS, CLAUSE_AGE, "SARL")
    patch_template(sarl_au, SARL_AU_SECTIONS, CLAUSE_AU, "SARL_AU")
    print("\n[DONE]")


if __name__ == "__main__":
    main()
