#!/usr/bin/env python3
"""Lot L6 (E2) : tests du controle de la sauvegarde MinIO (documents reels, rapprochement base).
Lancement : python3 -m unittest scripts/test_controle_sauvegarde_minio.py
"""
import importlib.util
import io
import os
import tarfile
import tempfile
import unittest

ICI = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("ctrl", os.path.join(ICI, "controle-sauvegarde-minio.py"))
ctrl = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ctrl)


def archive(chemins):
    fd, p = tempfile.mkstemp(suffix=".tar.gz")
    os.close(fd)
    with tarfile.open(p, "w:gz") as t:
        for c in chemins:
            info = tarfile.TarInfo(c)
            info.size = 3
            t.addfile(info, io.BytesIO(b"abc"))
    return p


class ControleSauvegardeMinio(unittest.TestCase):

    def test_les_fichiers_internes_ne_sont_pas_des_documents(self):
        p = archive([
            "./jurika-dataroom/ws/a/doc1.pdf/xl.meta",
            "./jurika-dataroom/ws/a/gros.pdf/xl.meta",
            "./jurika-dataroom/ws/a/gros.pdf/5f2c/part.1",
            "./.minio.sys/tmp/.trash/0316/xl.meta.bkp",
            "./.minio.sys/format.json",
        ])
        r = ctrl.analyser(p, "jurika-dataroom", ["ws/a/doc1.pdf", "ws/a/gros.pdf"])
        self.assertEqual(r["documents"], 2)
        self.assertEqual(r["internes"], 2)
        self.assertEqual(r["manquants"], [])
        self.assertEqual(r["orphelins"], [])
        self.assertTrue(r["ok"])

    def test_un_document_en_base_absent_de_la_sauvegarde_est_un_echec(self):
        p = archive(["./jurika-dataroom/ws/a/doc1.pdf/xl.meta"])
        r = ctrl.analyser(p, "jurika-dataroom", ["ws/a/doc1.pdf", "ws/a/perdu.pdf"])
        self.assertEqual(r["manquants"], ["ws/a/perdu.pdf"])
        self.assertFalse(r["ok"])

    def test_un_objet_sans_ligne_en_base_est_signale_sans_echec(self):
        p = archive(["./jurika-dataroom/ws/a/doc1.pdf/xl.meta", "./jurika-dataroom/ws/a/orphelin.pdf/xl.meta"])
        r = ctrl.analyser(p, "jurika-dataroom", ["ws/a/doc1.pdf"])
        self.assertEqual(r["orphelins"], ["ws/a/orphelin.pdf"])
        self.assertTrue(r["ok"])

    def test_les_autres_seaux_sont_comptes_a_part(self):
        p = archive(["./jurika-dataroom/ws/a/doc1.pdf/xl.meta", "./autre-seau/x.bin/xl.meta"])
        r = ctrl.analyser(p, "jurika-dataroom", ["ws/a/doc1.pdf"])
        self.assertEqual(r["documents"], 1)
        self.assertEqual(r["autres_seaux"], {"autre-seau": 1})


if __name__ == "__main__":
    unittest.main()
