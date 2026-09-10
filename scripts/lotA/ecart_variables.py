#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Lot A — analyse d'ecart des 404 variables du corpus CREATION du 9 septembre.

Croise trois sources :
  1. les variables reellement presentes dans les 23 gabarits .docx livres ;
  2. les variables que la plateforme sait deja alimenter (manifest v2, entrees
     de workflow CREATION_SARL — c'est le contrat que les builders honorent) ;
  3. le dictionnaire du cabinet, pour la section et le libelle de chaque variable.

Produit le tableau par section, avec la marque « deja resolue » / « nouvelle ».
"""
import io, json, os, re, sys, zipfile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

RACINE = r'C:\dev\JURIKA'
GABARITS = os.path.join(RACINE, r'specs\creation-2026-09-09\gabarits_word')
DICO = os.path.join(RACINE, r'specs\creation-2026-09-09\modeles_markdown\dictionnaire_variables_creation.md')
MANIFEST = os.path.join(RACINE, r'projet\backend-java\ai-service\src\main\resources\templates\v2\manifest.json')


def vars_gabarits():
    """Variable -> liste des gabarits qui l'emploient."""
    idx = {}
    for f in sorted(os.listdir(GABARITS)):
        if not f.lower().endswith('.docx'):
            continue
        z = zipfile.ZipFile(os.path.join(GABARITS, f))
        # Un paragraphe = une unite de texte : les runs d'un meme paragraphe se
        # recollent (une variable peut y etre coupee), mais JAMAIS deux paragraphes
        # ni deux parties, sous peine de fabriquer des noms fantomes (« $DENOMINATION »
        # suivi de « Constituee… » donnerait DENOMINATIONC).
        morceaux = []
        for n in z.namelist():
            if not (n.startswith('word/') and n.endswith('.xml')):
                continue
            xml = z.read(n).decode('utf-8', 'replace')
            for bloc in re.split(r'</w:p\s*>', xml):
                morceaux.append(re.sub(r'<[^>]+>', '', bloc))
        txt = chr(10).join(morceaux)
        for v in set(re.findall(r'\$([A-Z][A-Z0-9_]*)', txt)):
            idx.setdefault(v, []).append(f[:-5])
    return idx


def vars_manifest():
    m = json.load(open(MANIFEST, encoding='utf-8'))
    out = set()
    for e in m['templates']:
        if e.get('workflow') != 'CREATION_SARL':
            continue
        out |= set(e.get('variables', []))
        for b in e.get('blocks', []):
            out |= set(b.get('variables', []))
            for sb in b.get('blocks', []) or []:
                out |= set(sb.get('variables', []))
    return out


def dictionnaire():
    """Variable -> (section, libelle).

    Le dictionnaire ne documente pas tout en puces : certaines variables ne
    paraissent que dans une phrase (les totaux du § 4) ou dans un tableau
    d'arbitrage (§ 16). On indexe donc TOUTE occurrence `$VAR` et on retient la
    premiere ligne qui la cite ; sans cela, sept variables du corpus passeraient
    pour non documentees alors qu'elles le sont.
    """
    section, out = '(hors section)', {}
    for ligne in open(DICO, encoding='utf-8'):
        h = re.match(r'^#{2,3}\s+(.*)', ligne)
        if h:
            section = h.group(1).strip()
            continue
        noms = re.findall(r'`\$([A-Z][A-Z0-9_]*)`', ligne)
        if not noms:
            continue
        corps = re.sub(r'^\s*[-*]\s+', '', ligne).strip()
        libelle = corps.split('—', 1)[1].strip() if '—' in corps else corps
        libelle = re.sub(r'\*\*|`', '', libelle).strip()
        for n in noms:
            out.setdefault(n, (section, libelle))
    return out


def main():
    gab, man, dic = vars_gabarits(), vars_manifest(), dictionnaire()
    corpus = set(gab)
    print(f'gabarits  : {len(corpus)} variables employees')
    print(f'manifest  : {len(man)} variables deja resolues (CREATION_SARL)')
    print(f'dico      : {len(dic)} variables documentees')
    print(f'employees non documentees : {sorted(corpus - set(dic))}')
    print(f'documentees non employees : {len(set(dic) - corpus)}')
    print()
    par_section = {}
    for v in sorted(corpus):
        sec, lib = dic.get(v, ('(non documentee)', ''))
        par_section.setdefault(sec, []).append((v, v in man, lib, gab[v]))
    for sec in par_section:
        neuves = [x for x in par_section[sec] if not x[1]]
        print(f'### {sec}  — {len(par_section[sec])} variables, {len(neuves)} nouvelles')
        for v, connue, lib, fichiers in par_section[sec]:
            marque = 'OK ' if connue else 'NEW'
            print(f'  {marque} ${v:38s} {lib[:96]}')
        print()


if __name__ == '__main__':
    main()
