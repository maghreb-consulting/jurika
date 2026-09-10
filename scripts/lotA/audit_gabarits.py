#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Lot A — controles independants sur les gabarits .docx du corpus CREATION.

Ne modifie rien. Lit chaque .docx, en extrait le texte paragraphe par paragraphe
(corps + en-tetes + pieds de page) et applique les memes patrons que le moteur
DocxTemplateEngine :

  - variables            $EN_MAJUSCULES
  - conditions           ◇ SI / ◇ SINON SI / ◇ SINON / ◆ FIN SI
  - boucles              ▼ DEBUT BOUCLE — NOM / ▲ FIN BOUCLE — NOM
  - cases a cocher       ◈ CASE A COCHER … pilotee par $VAR
  - annotations          lignes ouvertes par ↳
  - preambule            « Convention de balisage » et ses faux marqueurs

Usage : python audit_gabarits.py <dossier|fichier> [...]
Sortie : JSON sur stdout.
"""
import io, json, os, re, sys, zipfile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

PARTS_TEXTE = re.compile(r'^word/(document|header\d*|footer\d*|footnotes|endnotes)\.xml$')

RE_VAR = re.compile(r'\$([A-Z][A-Z0-9_]*)')
RE_SI = re.compile(r'^\s*◇\s*SI\s*(?:SI)?\b|^\s*◇\s*SI\b')
RE_BOUCLE_DEB = re.compile(r'▼\s*D[ÉE]BUT\s+BOUCLE\s*[—–-]\s*([A-Z0-9_]+)')
RE_BOUCLE_FIN = re.compile(r'▲\s*FIN\s+BOUCLE\s*[—–-]\s*([A-Z0-9_]+)')

MARQUEURS_PREAMBULE = ('Convention de balisage', '$VARIABLE', '$EN_MAJUSCULES')


def paragraphes(chemin):
    """Retourne [(partie, index, texte)] pour toutes les parties textuelles du docx."""
    out = []
    with zipfile.ZipFile(chemin) as z:
        for nom in sorted(z.namelist()):
            if not PARTS_TEXTE.match(nom):
                continue
            xml = z.read(nom).decode('utf-8', 'replace')
            xml = re.sub(r'<w:tab\b[^>]*/>', '\t', xml)
            xml = re.sub(r'<w:br\b[^>]*/>', '\n', xml)
            for i, bloc in enumerate(re.split(r'</w:p\s*>', xml)):
                txt = re.sub(r'<[^>]+>', '', bloc)
                txt = (txt.replace('&amp;', '&').replace('&lt;', '<')
                          .replace('&gt;', '>').replace('&quot;', '"').replace('&apos;', "'"))
                out.append((nom, i, txt))
    return out


def styles_directs(chemin):
    """Compte les runs porteurs d'un formatage direct de police / corps / gras."""
    with zipfile.ZipFile(chemin) as z:
        xml = z.read('word/document.xml').decode('utf-8', 'replace')
    runs = re.findall(r'<w:rPr>.*?</w:rPr>', xml, re.S)
    direct = 0
    for r in runs:
        if re.search(r'<w:(rFonts|sz|szCs|color)\b', r):
            direct += 1
    return {'runs_avec_rPr': len(runs), 'runs_formatage_direct': direct}


def audit(chemin):
    paras = paragraphes(chemin)
    texte_total = '\n'.join(t for _, _, t in paras)

    conds = {'SI': 0, 'SINON SI': 0, 'SINON': 0, 'FIN SI': 0, 'losange_ouvrant_autre': 0}
    boucles_deb, boucles_fin = [], []
    lignes_annotation, lignes_case = [], []
    marqueurs_orphelins = []

    for partie, idx, t in paras:
        s = t.strip()
        if not s:
            continue
        if s.startswith('↳'):
            lignes_annotation.append(s[:120])
        if '◈' in s:
            lignes_case.append(s[:160])
        if '◇' in s:
            corps = s.split('◇', 1)[1].strip()
            haut = corps.upper()
            if haut.startswith('SINON SI'):
                conds['SINON SI'] += 1
            elif haut.startswith('SINON'):
                conds['SINON'] += 1
            elif haut.startswith('SI'):
                conds['SI'] += 1
            else:
                conds['losange_ouvrant_autre'] += 1
                marqueurs_orphelins.append(('◇', partie, s[:120]))
        if '◆' in s:
            corps = s.split('◆', 1)[1].strip().upper()
            if corps.startswith('FIN SI'):
                conds['FIN SI'] += 1
            else:
                marqueurs_orphelins.append(('◆', partie, s[:120]))
        for m in RE_BOUCLE_DEB.finditer(t):
            boucles_deb.append((m.group(1), partie))
        for m in RE_BOUCLE_FIN.finditer(t):
            boucles_fin.append((m.group(1), partie))

    variables = sorted(set(RE_VAR.findall(texte_total)))

    # Sentinelles a l'interieur des boucles : variables citees entre ▼ et ▲.
    dans_boucle, pile, vars_en_boucle = [], [], set()
    for partie, idx, t in paras:
        if RE_BOUCLE_DEB.search(t):
            pile.append(RE_BOUCLE_DEB.search(t).group(1))
        elif RE_BOUCLE_FIN.search(t):
            if pile:
                pile.pop()
        elif pile:
            for v in RE_VAR.findall(t):
                vars_en_boucle.add(v)

    noms_deb = [n for n, _ in boucles_deb]
    noms_fin = [n for n, _ in boucles_fin]

    preambule = [m for m in MARQUEURS_PREAMBULE if m in texte_total]

    return {
        'fichier': os.path.basename(chemin),
        'paragraphes': len(paras),
        'variables_distinctes': len(variables),
        'variables': variables,
        'ICE': texte_total.count('$ICE') - texte_total.count('$ICE_NUMERO'),
        'ICE_NUMERO': texte_total.count('$ICE_NUMERO'),
        'DOMICILIATAIRE_ICE': texte_total.count('$DOMICILIATAIRE_ICE'),
        'conditions': conds,
        'conditions_appariees': conds['SI'] == conds['FIN SI'],
        'boucles_debut': sorted(noms_deb),
        'boucles_fin': sorted(noms_fin),
        'boucles_appariees': sorted(noms_deb) == sorted(noms_fin),
        'lignes_annotation': len(lignes_annotation),
        'exemples_annotation': lignes_annotation[:3],
        'lignes_case_a_cocher': len(lignes_case),
        'exemples_case': lignes_case[:3],
        'variables_citees_dans_boucle': sorted(vars_en_boucle),
        'marqueurs_orphelins': marqueurs_orphelins[:10],
        'preambule_detecte': preambule,
        'apostrophe_typographique': texte_total.count('’'),
        'apostrophe_droite': texte_total.count("'"),
        **styles_directs(chemin),
    }


def main():
    cibles = []
    for a in sys.argv[1:]:
        if os.path.isdir(a):
            cibles += [os.path.join(a, f) for f in sorted(os.listdir(a)) if f.lower().endswith('.docx')]
        else:
            cibles.append(a)
    print(json.dumps([audit(c) for c in cibles], ensure_ascii=False, indent=1))


if __name__ == '__main__':
    main()
