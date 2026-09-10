#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Lot A — releve des conditions et des blocs « case a cocher » des gabarits.

Applique les patrons REELS du moteur (ATOM_EXISTS, ATOM_EQUALS,
CHECKBOX_MARKER_PATTERN) pour distinguer :
  - les conditions que le moteur sait evaluer ($VAR existe / $VAR = « … ») ;
  - celles redigees en langage naturel, qui exigent une entree dans
    NL_CONDITION_FLAGS cote moteur, faute de quoi elles valent false en silence.
"""
import io, os, re, sys, json, unicodedata, zipfile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

ATOM_EXISTS = re.compile(r'\$([A-Z][A-Z0-9_]*)\s+existe', re.I)
ATOM_EQUALS = re.compile(r'\$([A-Z][A-Z0-9_]*)\s*=\s*(.+)', re.S)
CHECKBOX = re.compile(r'◈\s*CASE\s+[AÀ]\s+COCHER.*?\$([A-Z][A-Z0-9_]*)', re.I | re.S)

NL_CONNUES = {
    "au moins un apport est realise en nature",
    "le siege etait precedemment exploite par un tiers",
    "la societe comporte des succursales",
    "capital variable",
    "brevets ou marques deposes",
    "un dirigeant est une personne morale",
}


def norm(s):
    n = unicodedata.normalize('NFD', s)
    n = ''.join(c for c in n if not unicodedata.combining(c))
    n = re.sub(r"[‘’‛ʼ`´]", "'", n)
    return re.sub(r'[\s  ]+', ' ', n.lower()).strip()


def paras(chemin):
    with zipfile.ZipFile(chemin) as z:
        xml = z.read('word/document.xml').decode('utf-8', 'replace')
    xml = re.sub(r'<w:tab\b[^>]*/>', ' ', xml)
    out = []
    for bloc in re.split(r'</w:p\s*>', xml):
        t = re.sub(r'<[^>]+>', '', bloc)
        t = (t.replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>')
              .replace('&quot;', '"').replace('&apos;', "'"))
        out.append(t.strip())
    return out


def main(dossier):
    inconnues, cases, total_atomes = [], [], 0
    for f in sorted(os.listdir(dossier)):
        if not f.lower().endswith('.docx'):
            continue
        ps = paras(os.path.join(dossier, f))
        for i, p in enumerate(ps):
            if '◇' in p:
                rem = p.split('◇', 1)[1].strip()
                if re.match(r'(?iu)SINON\s+SI\s*:', rem) or re.match(r'(?iu)SI\s*:', rem):
                    expr = rem.split(':', 1)[1].strip()
                    parts = re.split(r'(?iu)\s+(?:ET|OU)\s+', expr)
                    for a in parts:
                        a = a.strip()
                        if not a:
                            continue
                        total_atomes += 1
                        if ATOM_EXISTS.fullmatch(a) or ATOM_EQUALS.fullmatch(a):
                            continue
                        inconnues.append((f, a, norm(a) in NL_CONNUES))
            m = CHECKBOX.search(p)
            if m:
                opts = []
                for q in ps[i + 1:]:
                    if not q:
                        continue
                    if q.startswith('☐'):
                        opts.append(q.lstrip('☐').strip())
                    else:
                        break
                cases.append((f, m.group(1), opts))

    print(f'ATOMES DE CONDITION analyses : {total_atomes}')
    print(f'  reconnus par le moteur      : {total_atomes - len(inconnues)}')
    print(f'  NON reconnus par les patrons: {len(inconnues)}')
    print()
    connues = [x for x in inconnues if x[2]]
    nouvelles = [x for x in inconnues if not x[2]]
    print(f'--- langage naturel DEJA cable dans NL_CONDITION_FLAGS ({len(connues)}) ---')
    for f, a, _ in connues:
        print(f'  {f[:42]:44s} {a[:90]}')
    print()
    print(f'--- langage naturel NON cable => evalue a FALSE en silence ({len(nouvelles)}) ---')
    for f, a, _ in nouvelles:
        print(f'  {f[:42]:44s} {a[:100]}')
    print()
    print(f'=== BLOCS « CASE A COCHER » : {len(cases)} ===')
    for f, v, opts in cases:
        print(f'  {f[:42]:44s} ${v}  ({len(opts)} options)')
        for o in opts:
            print(f'      ☐ {o[:95]}')


if __name__ == '__main__':
    main(sys.argv[1])
