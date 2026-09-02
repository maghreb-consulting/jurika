#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Patch les Statuts + Acte pour rendre les marqueurs neutres "(e)" sensibles au sexe.

Modifications appliquees :
- Statuts SARL/SARL_AU : "né(e)" -> "{{associe_pp_ne}}" (block ASSOCIES_PP) ou
  "{{au_pp_ne}}" (block ASSOCIE_UNIQUE_PP). Les autres "(e)" libres deviennent
  "{{associe_pp_e}}" / "{{au_pp_e}}".
- Acte SARL/SARL_AU : "Monsieur / Madame ${ASSOCIE_NOM}" -> "${ASSOCIE_TITRE} ${ASSOCIE_NOM}".
  Idem PRESIDENT_NOM, GERANT_NOM. "Est nomme(e)" -> "Est ${GERANT_NOMME}".
  "frappe(e)" -> "${GERANT_FRAPPE}". "ne(e)" -> "${GERANT_NE}".

Note : on opere sur le texte recompose (paragraphe.text) puis on ecrit le run[0]
unique en respectant la limitation L1 du moteur (formatage du 1er run conserve).
"""
import io, os, re, sys
from docx import Document

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

D = 'backend-java/ai-service/src/main/resources/templates/docx'


def set_text(p, new_text):
    """Re-ecrit un paragraphe avec un seul run portant new_text."""
    runs = list(p.runs)
    for r in runs:
        r._element.getparent().remove(r._element)
    p.add_run(new_text)


def patch_statuts(path: str, prefix: str):
    """prefix = 'associe_pp_' pour SARL ou 'au_pp_' pour SARL_AU.

    Strategie : on regarde le texte recompose du paragraphe. Si on detecte
    'né(e)' a l'interieur d'un block (▶ ASSOCIES_PP ou ▶ ASSOCIE_UNIQUE_PP),
    on remplace par le placeholder par-personne. Sinon on remplace par le
    placeholder global gerant_ne.
    """
    doc = Document(path)
    patches = 0
    in_block = False
    for p in doc.paragraphs:
        t = p.text
        if not t:
            continue
        marker_pp = '▶ ASSOCIES_PP' if prefix == 'associe_pp_' else '▶ ASSOCIE_UNIQUE_PP'
        marker_pm = '▶ ASSOCIES_PM' if prefix == 'associe_pp_' else '▶ ASSOCIE_UNIQUE_PM'
        on_pp = marker_pp in t
        if on_pp:
            new = t
            # Inside the block paragraph (PP) : per-item ne/née
            new = new.replace('né(e)', '{{%sne}}' % prefix)
            new = new.replace('demeurant', 'demeurant')  # noop
            # Don't touch other (e) — they may be redondants.
            if new != t:
                set_text(p, new)
                patches += 1
        else:
            # Gerant block : "(selon non_statutaire) : M Oussama BENATIK, ... né(e) le ..."
            # The gerant line is rendered with gerant_civilite which is just M./Mme
            # We add a gerant_ne placeholder. The mapper emits it.
            new = t
            if 'né(e)' in new and 'gerant' in new.lower() or 'gérant' in t.lower():
                new = new.replace('né(e)', '{{gerant_ne}}')
            if new != t:
                set_text(p, new)
                patches += 1
    doc.save(path)
    print(f'{path}: {patches} paragraphe(s) patche(s)')


def patch_acte(path: str):
    """Acte SARL/SARL_AU : remplace civilites doublees + (e) pour gerant nomme."""
    doc = Document(path)
    patches = 0
    for p in doc.paragraphs:
        t = p.text
        if not t:
            continue
        new = t
        # Monsieur / Madame ${X} -> ${X_TITRE} ${X}
        new = re.sub(r'Monsieur\s*/\s*Madame\s+\$\{ASSOCIE_NOM\}',
                     '${ASSOCIE_TITRE} ${ASSOCIE_NOM}', new)
        new = re.sub(r'Monsieur\s*/\s*Madame\s+\$\{PRESIDENT_NOM\}',
                     '${PRESIDENT_TITRE} ${PRESIDENT_NOM}', new)
        new = re.sub(r'Monsieur\s*/\s*Madame\s+\$\{GERANT_NOM\}',
                     '${GERANT_TITRE} ${GERANT_NOM}', new)
        # Est nommé(e) gérant -> Est ${GERANT_NOMME} gérant
        new = re.sub(r'Est nommé\(e\)\s+gérant', 'Est ${GERANT_NOMME} gérant', new)
        # né(e) le ${GERANT_NAISSANCE} -> ${GERANT_NE} le ${GERANT_NAISSANCE}
        new = re.sub(r'\bné\(e\)\s+le\s+\$\{GERANT_NAISSANCE\}',
                     '${GERANT_NE} le ${GERANT_NAISSANCE}', new)
        # déclare accepter ... frappé(e) -> frappé(e) - on emet GERANT_FRAPPE
        new = re.sub(r"n'être frappé\(e\)", "n'être ${GERANT_FRAPPE}", new)
        # nommé(e) declares -> garde si pas dans contexte resolution
        if new != t:
            set_text(p, new)
            patches += 1
    doc.save(path)
    print(f'{path}: {patches} paragraphe(s) patche(s)')


def main():
    patch_statuts(os.path.join(D, 'STATUTS_CONSTITUTIFS_SARL.docx'), 'associe_pp_')
    patch_statuts(os.path.join(D, 'STATUTS_CONSTITUTIFS_SARL_AU.docx'), 'au_pp_')
    patch_acte(os.path.join(D, 'ACTE_NOMINATION_GERANT_SARL.docx'))
    patch_acte(os.path.join(D, 'ACTE_NOMINATION_GERANT_SARL_AU.docx'))


if __name__ == '__main__':
    main()
