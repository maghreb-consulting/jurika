#!/usr/bin/env python3
"""Controle d'une sauvegarde du volume MinIO : documents reels, rapproches de la base.

Usage :
  python3 scripts/controle-sauvegarde-minio.py <minio_data.tar.gz> <cles_en_base.txt> [seau]
    <cles_en_base.txt> : une cle d'objet par ligne (colonne object_key des documents en base),
                         produite par la requete de CLES_EN_BASE ci-dessous (docker exec psql).
    [seau]             : jurika-dataroom par defaut.

Lot L6 (E2). Le controle precedent comptait TOUS les fichiers du volume, y compris les
fichiers internes de MinIO (.minio.sys : corbeille, fichiers temporaires) que MinIO cree et vide
lui-meme : le nombre variait (37 a L5, 32 a L5b) sans qu'aucun document ne change (controle
trompeur). Ici :
  - un document = un objet du seau, c'est-a-dire un fichier xl.meta hors de .minio.sys
    (un gros objet a aussi des fichiers part.N : ils ne comptent pas en plus) ;
  - les fichiers internes sont comptes a part et ne jugent rien ;
  - chaque cle en base doit avoir son objet dans la sauvegarde : un manquant est une PERTE
    (code de sortie 2) ; un objet sans ligne en base (orphelin) est signale sans echec.
"""
import sys
import tarfile

CLES_EN_BASE = (
    "SELECT object_key FROM dataroom_documents WHERE object_key IS NOT NULL "
    "UNION SELECT object_key FROM dataroom_depots WHERE object_key IS NOT NULL ORDER BY 1"
)


def analyser(archive, seau, cles_en_base):
    objets, internes, autres = set(), 0, {}
    with tarfile.open(archive, "r:gz") as tar:
        for m in tar.getmembers():
            if not m.isfile():
                continue
            chemin = m.name[2:] if m.name.startswith("./") else m.name
            if chemin.startswith(".minio.sys/"):
                internes += 1
                continue
            if not chemin.endswith("/xl.meta"):
                continue  # morceaux de donnees d'un objet (part.N)
            racine, _, cle = chemin[: -len("/xl.meta")].partition("/")
            if racine == seau:
                objets.add(cle)
            else:
                autres[racine] = autres.get(racine, 0) + 1
    base = {c.strip() for c in cles_en_base if c.strip()}
    manquants = sorted(base - objets)
    orphelins = sorted(objets - base)
    return {
        "documents": len(objets),
        "internes": internes,
        "en_base": len(base),
        "manquants": manquants,
        "orphelins": orphelins,
        "autres_seaux": autres,
        "ok": not manquants,
    }


def main():
    if len(sys.argv) not in (3, 4):
        sys.exit(__doc__)
    archive, fichier_cles = sys.argv[1], sys.argv[2]
    seau = sys.argv[3] if len(sys.argv) == 4 else "jurika-dataroom"
    with open(fichier_cles, encoding="utf-8") as f:
        r = analyser(archive, seau, f.readlines())
    print(f"Documents sauvegardes (seau {seau}) : {r['documents']} ; documents en base : {r['en_base']}")
    print(f"Fichiers internes de MinIO (.minio.sys, non comptes) : {r['internes']}")
    for s, n in sorted(r["autres_seaux"].items()):
        print(f"Autre seau {s} : {n} objet(s)")
    for c in r["orphelins"]:
        print(f"AVERTISSEMENT : objet sans document en base : {c}")
    for c in r["manquants"]:
        print(f"PERTE : document en base absent de la sauvegarde : {c}")
    if not r["ok"]:
        print(f"ECHEC : {len(r['manquants'])} document(s) en base absent(s) de la sauvegarde", file=sys.stderr)
        sys.exit(2)
    print("CONTROLE MINIO : OK")


if __name__ == "__main__":
    main()
