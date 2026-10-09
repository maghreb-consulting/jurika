#!/usr/bin/env python3
"""Archive (tar.gz, mode 600) d'un repertoire d'un conteneur EN MARCHE, par `docker exec`.

Usage : python3 scripts/sauvegarde-volume-par-exec.py <conteneur> <repertoire> <archive.tar.gz>

Pourquoi : l'image MinIO du Z440 (Chainguard) n'a ni tar, ni gzip, ni find ; la
sauvegarde de son volume passait par un `docker run` jetable. Ce script n'utilise que
`docker exec` (lecture seule cote conteneur : un shell qui liste, puis `cat` fichier par
fichier) et construit l'archive sur l'hote. Les consommateurs doivent etre arretes avant
(aucune ecriture pendant la copie).

Controle : nombre de fichiers et octets lus, compares a la liste du conteneur ; code de
sortie non nul au moindre ecart. Lot L2 (activation), 2026-10-09.
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
    total = 0
    ancien_umask = os.umask(0o077)
    try:
        with tarfile.open(archive, "w:gz") as tar:
            for taille, mode, chemin in fichiers:
                contenu = subprocess.run(["docker", "exec", conteneur, "cat", chemin],
                                         check=True, capture_output=True).stdout
                if len(contenu) != taille:
                    sys.exit(f"ECHEC : {chemin} : {len(contenu)} octets lus, {taille} attendus")
                info = tarfile.TarInfo("./" + os.path.relpath(chemin, racine))
                info.size, info.mode, info.mtime = taille, mode, int(time.time())
                tar.addfile(info, io.BytesIO(contenu))
                total += taille
    finally:
        os.umask(ancien_umask)
    os.chmod(archive, 0o600)
    with tarfile.open(archive, "r:gz") as tar:
        relus = [m for m in tar.getmembers() if m.isfile()]
    if len(relus) != len(fichiers) or sum(m.size for m in relus) != total:
        sys.exit("ECHEC : archive relue differente de la liste")
    print(f"OK {archive} : {len(fichiers)} fichiers, {total} octets, mode 600")


if __name__ == "__main__":
    main()
