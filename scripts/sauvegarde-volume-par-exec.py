#!/usr/bin/env python3
"""Archive (tar.gz, mode 600) d'un repertoire d'un conteneur EN MARCHE, par `docker exec`.

Usage : python3 scripts/sauvegarde-volume-par-exec.py <conteneur> <repertoire> <archive.tar.gz>

Pourquoi : l'image MinIO du Z440 (Chainguard) n'a ni tar, ni gzip, ni find ; la
sauvegarde de son volume passait par un `docker run` jetable. Ce script n'utilise que
`docker exec` (lecture seule cote conteneur : un shell qui liste, puis `cat` fichier par
fichier) et construit l'archive sur l'hote. Les consommateurs doivent etre arretes avant
(aucune ecriture pendant la copie).

Controle : nombre de fichiers et octets lus, compares a la liste du conteneur ; code de
sortie non nul au moindre ecart. Lot L2 (activation), 2026-10-09. Depuis le 2026-10-17, les
fichiers internes de MinIO (.minio.sys) sont hors de cette comparaison (voir `copier`).
"""
import io
import os
import subprocess
import sys
import tarfile
import time

LISTER = r'''
parcourir() {
  for f in "$1"/* "$1"/.[!.]*; do
    if [ -L "$f" ]; then continue; fi
    if [ -d "$f" ]; then parcourir "$f";
    elif [ -f "$f" ]; then printf '%s\t%s\t%s\n' "$(stat -c %s "$f")" "$(stat -c %a "$f")" "$f"; fi
  done
}
parcourir "$1"
'''


def est_interne(chemin):
    """Fichier interne de MinIO (.minio.sys) : cache d'usage, corbeille, journaux qui varient
    d'eux-memes pendant la copie. Ce n'est pas un document."""
    return "/.minio.sys/" in chemin + "/"


def copier(fichiers, racine, archive, lire):
    """Copie `fichiers` [(taille, mode, chemin)] dans `archive` en lisant chacun par `lire`.

    Decision du 2026-10-17 : tout .minio.sys est exclu de la comparaison d'integrite (une taille
    changee ou un fichier disparu pendant la copie n'est pas un echec : il est copie tel que lu,
    ou omis) ; tout autre fichier reste compare un a un, et le moindre ecart est un ECHEC.
    Rend (total des octets copies, nombre de fichiers copies, nombre d'internes).
    """
    total = copies = internes = 0
    ancien_umask = os.umask(0o077)
    try:
        with tarfile.open(archive, "w:gz") as tar:
            for taille, mode, chemin in fichiers:
                interne = est_interne(chemin)
                try:
                    contenu = lire(chemin)
                except subprocess.CalledProcessError:
                    if interne:
                        continue
                    raise
                if len(contenu) != taille and not interne:
                    sys.exit(f"ECHEC : {chemin} : {len(contenu)} octets lus, {taille} attendus")
                info = tarfile.TarInfo("./" + os.path.relpath(chemin, racine))
                info.size, info.mode, info.mtime = len(contenu), mode, int(time.time())
                tar.addfile(info, io.BytesIO(contenu))
                total += len(contenu)
                copies += 1
                internes += 1 if interne else 0
    finally:
        os.umask(ancien_umask)
    os.chmod(archive, 0o600)
    with tarfile.open(archive, "r:gz") as tar:
        relus = [m for m in tar.getmembers() if m.isfile()]
    if len(relus) != copies or sum(m.size for m in relus) != total:
        sys.exit("ECHEC : archive relue differente de la copie")
    attendus = [f for f in fichiers if not est_interne(f[2])]
    relus_docs = [m for m in relus if not est_interne(m.name[1:])]
    if len(relus_docs) != len(attendus) or sum(m.size for m in relus_docs) != sum(f[0] for f in attendus):
        sys.exit("ECHEC : fichiers hors .minio.sys relus differents de la liste")
    return total, copies, internes


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    conteneur, racine, archive = sys.argv[1], sys.argv[2].rstrip("/"), sys.argv[3]
    liste = subprocess.run(["docker", "exec", conteneur, "sh", "-c", LISTER, "sh", racine],
                           check=True, capture_output=True, text=True).stdout.splitlines()
    fichiers = []
    for ligne in liste:
        taille, mode, chemin = ligne.split("\t", 2)
        fichiers.append((int(taille), int(mode, 8), chemin))
    if not fichiers:
        sys.exit("ECHEC : aucun fichier sous " + racine)

    def lire(chemin):
        return subprocess.run(["docker", "exec", conteneur, "cat", chemin],
                              check=True, capture_output=True).stdout

    total, copies, internes = copier(fichiers, racine, archive, lire)
    # Lot L6 (E2) : ce compte est celui des FICHIERS copies (integrite de la copie), pas des
    # documents : les documents se controlent avec controle-sauvegarde-minio.py.
    detail = f" (dont {internes} fichiers internes .minio.sys, hors comparaison)" if internes else ""
    print(f"OK {archive} : {copies} fichiers copies{detail}, {total} octets, mode 600")


if __name__ == "__main__":
    main()
