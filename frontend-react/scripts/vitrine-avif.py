#!/usr/bin/env python3
# Lot L6c : encode une image RGBA brute (largeur x hauteur x 4 octets) en AVIF, par libheif
# (paquets Ubuntu libheif1 et libheif-plugin-aomenc, deja presents sur le Z440 ; aucun
# telechargement). Appele par vitrine-images.mjs.
#
#   python3 vitrine-avif.py <brut.rgba> <largeur> <hauteur> <sortie.avif> [qualite 0-100]
import ctypes
import sys


class Erreur(ctypes.Structure):
    _fields_ = [("code", ctypes.c_int), ("subcode", ctypes.c_int), ("message", ctypes.c_char_p)]


def verifier(err, etape):
    if err.code != 0:
        raise SystemExit(f"libheif, {etape} : {err.message.decode(errors='replace')}")


def main():
    brut, largeur, hauteur, sortie = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
    qualite = int(sys.argv[5]) if len(sys.argv) > 5 else 60
    donnees = open(brut, "rb").read()
    if len(donnees) != largeur * hauteur * 4:
        raise SystemExit("taille du fichier brut incoherente")

    h = ctypes.CDLL("libheif.so.1")
    h.heif_context_alloc.restype = ctypes.c_void_p
    h.heif_image_create.restype = Erreur
    h.heif_image_create.argtypes = [ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.POINTER(ctypes.c_void_p)]
    h.heif_image_add_plane.restype = Erreur
    h.heif_image_add_plane.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int]
    h.heif_image_get_plane.restype = ctypes.POINTER(ctypes.c_uint8)
    h.heif_image_get_plane.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.POINTER(ctypes.c_int)]
    h.heif_context_get_encoder_for_format.restype = Erreur
    h.heif_context_get_encoder_for_format.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.POINTER(ctypes.c_void_p)]
    h.heif_encoder_set_lossy_quality.restype = Erreur
    h.heif_encoder_set_lossy_quality.argtypes = [ctypes.c_void_p, ctypes.c_int]
    h.heif_context_encode_image.restype = Erreur
    h.heif_context_encode_image.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p]
    h.heif_context_write_to_file.restype = Erreur
    h.heif_context_write_to_file.argtypes = [ctypes.c_void_p, ctypes.c_char_p]

    HEIF_COLORSPACE_RGB, HEIF_CHROMA_INTERLEAVED_RGBA, HEIF_CHANNEL_INTERLEAVED = 1, 11, 10
    HEIF_COMPRESSION_AV1 = 4

    ctx = h.heif_context_alloc()
    img = ctypes.c_void_p()
    verifier(h.heif_image_create(largeur, hauteur, HEIF_COLORSPACE_RGB, HEIF_CHROMA_INTERLEAVED_RGBA, ctypes.byref(img)), "image")
    verifier(h.heif_image_add_plane(img, HEIF_CHANNEL_INTERLEAVED, largeur, hauteur, 8), "plan")
    pas = ctypes.c_int()
    plan = h.heif_image_get_plane(img, HEIF_CHANNEL_INTERLEAVED, ctypes.byref(pas))
    ligne = largeur * 4
    for y in range(hauteur):
        ctypes.memmove(ctypes.addressof(plan.contents) + y * pas.value, donnees[y * ligne:(y + 1) * ligne], ligne)
    enc = ctypes.c_void_p()
    verifier(h.heif_context_get_encoder_for_format(ctx, HEIF_COMPRESSION_AV1, ctypes.byref(enc)), "encodeur AV1")
    verifier(h.heif_encoder_set_lossy_quality(enc, qualite), "qualite")
    verifier(h.heif_context_encode_image(ctx, img, enc, None, None), "encodage")
    verifier(h.heif_context_write_to_file(ctx, sortie.encode()), "ecriture")


if __name__ == "__main__":
    main()
