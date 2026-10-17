#!/usr/bin/env python3
"""Lot L6b (decision du 2026-10-17) : tests de la copie de volume (sauvegarde-volume-par-exec.py).
Tout .minio.sys est hors de la comparaison d'integrite ; les autres fichiers restent compares un a un.
Lancement : python3 -m unittest scripts/test_sauvegarde_volume_par_exec.py
"""
import importlib.util
import os
import subprocess
import tarfile
import tempfile
import unittest

ICI = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("svol", os.path.join(ICI, "sauvegarde-volume-par-exec.py"))
svol = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(svol)

CACHE = "/data/.minio.sys/buckets/jurika-dataroom/.usage-cache.bin/xl.meta"
DOC = "/data/jurika-dataroom/ws/a/doc1.pdf/xl.meta"


def archive_temp():
    fd, p = tempfile.mkstemp(suffix=".tar.gz")
    os.close(fd)
    return p


class CopieVolume(unittest.TestCase):

    def test_un_fichier_interne_qui_change_pendant_la_copie_n_est_pas_un_echec(self):
        # Vu au Z440 le 2026-10-16 : "4025 octets lus, 4020 attendus" sur le cache d'usage.
        lus = {CACHE: b"x" * 4025, DOC: b"abc"}
        p = archive_temp()
        total, copies, internes = svol.copier([(4020, 0o600, CACHE), (3, 0o600, DOC)], "/data", p, lus.__getitem__)
        self.assertEqual((copies, internes), (2, 1))
        with tarfile.open(p, "r:gz") as t:
            self.assertEqual(sorted(m.name for m in t.getmembers()),
                             ["./.minio.sys/buckets/jurika-dataroom/.usage-cache.bin/xl.meta",
                              "./jurika-dataroom/ws/a/doc1.pdf/xl.meta"])

    def test_un_fichier_interne_disparu_est_omis(self):
        def lire(c):
            if c == CACHE:
                raise subprocess.CalledProcessError(1, "cat")
            return b"abc"
        total, copies, internes = svol.copier([(4020, 0o600, CACHE), (3, 0o600, DOC)], "/data", archive_temp(), lire)
        self.assertEqual((copies, internes, total), (1, 0, 3))

    def test_un_document_qui_change_reste_un_echec(self):
        lus = {DOC: b"abcd"}
        with self.assertRaises(SystemExit) as e:
            svol.copier([(3, 0o600, DOC)], "/data", archive_temp(), lus.__getitem__)
        self.assertIn("ECHEC", str(e.exception))

    def test_un_document_disparu_reste_un_echec(self):
        def lire(c):
            raise subprocess.CalledProcessError(1, "cat")
        with self.assertRaises(subprocess.CalledProcessError):
            svol.copier([(3, 0o600, DOC)], "/data", archive_temp(), lire)


if __name__ == "__main__":
    unittest.main()
