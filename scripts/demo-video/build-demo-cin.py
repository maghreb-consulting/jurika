# -*- coding: utf-8 -*-
"""
Fabrique un SPECIMEN de carte nationale d'identite, pour la demonstration de
l'extraction OCR.

Il n'existe aucune image de CIN dans le depot, et il est hors de question d'en
utiliser une vraie : la video de demonstration ne doit contenir aucune donnee
personnelle reelle. On dessine donc une carte fictive, portant l'identite deja
utilisee par le workflow de demonstration (Rachid EL AMRANI), et marquee
« SPECIMEN - DOCUMENT FICTIF » en clair et en filigrane pour qu'elle ne puisse
etre confondue avec une piece authentique.

Sortie : scripts/demo-video/assets/CIN_specimen_recto.png (et _verso.png)
Usage  : python scripts/demo-video/build-demo-cin.py
"""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'assets')
os.makedirs(OUT, exist_ok=True)

W, H = 1012, 638          # ratio ID-1 (85,6 x 54 mm) a ~300 dpi
FOND = (238, 240, 233)
ENCRE = (24, 32, 48)
GRIS = (110, 120, 135)
ACCENT = (168, 133, 61)


def police(taille, gras=False):
    for nom in (('arialbd.ttf', 'ariblk.ttf') if gras else ('arial.ttf',)):
        for base in (r'C:\Windows\Fonts',):
            chemin = os.path.join(base, nom)
            if os.path.exists(chemin):
                return ImageFont.truetype(chemin, taille)
    return ImageFont.load_default()


def carte():
    img = Image.new('RGB', (W, H), FOND)
    d = ImageDraw.Draw(img)
    # Bandeau superieur
    d.rectangle([0, 0, W, 96], fill=(18, 28, 45))
    d.text((36, 22), "ROYAUME DU MAROC", font=police(30, True), fill=(255, 255, 255))
    d.text((36, 58), "CARTE NATIONALE D'IDENTITE", font=police(22), fill=(200, 205, 215))
    # Cadre photo
    # Aucun texte dans le cadre photo : le modele lisait « PHOTO » et le
    # ressortait comme prenom.
    d.rectangle([36, 130, 276, 452], outline=GRIS, width=3)
    d.rectangle([46, 140, 266, 442], fill=(214, 217, 210))
    return img, d


def champ(d, x, y, libelle, valeur, large=False):
    d.text((x, y), libelle, font=police(19), fill=GRIS)
    d.text((x, y + 26), valeur, font=police(31 if large else 27, True), fill=ENCRE)


def filigrane(img, texte="SPECIMEN"):
    """Filigrane diagonal, non ambigu, sur toute la carte."""
    calque = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(calque)
    # Assez lisible pour lever toute ambiguite, assez discret pour ne pas
    # corrompre la lecture OCR des valeurs (le numero de carte sortait faux).
    f = police(74, True)
    d.text((150, 250), texte, font=f, fill=(200, 60, 60, 46))
    calque = calque.rotate(18, resample=Image.BICUBIC, center=(W // 2, H // 2))
    return Image.alpha_composite(img.convert('RGBA'), calque).convert('RGB')


# ─── Recto ──────────────────────────────────────────────────────────────
img, d = carte()
champ(d, 320, 140, "NOM", "EL AMRANI", large=True)
champ(d, 320, 226, "PRENOM", "RACHID", large=True)
champ(d, 320, 312, "NE LE", "17.04.1981")
champ(d, 640, 312, "A", "RABAT")
champ(d, 320, 392, "VALABLE JUSQU'AU", "12.03.2032")
d.text((640, 400), "N°", font=police(19), fill=GRIS)
d.text((640, 418), "A741852", font=police(34, True), fill=ACCENT)
d.rectangle([36, 486, W - 36, 490], fill=ACCENT)
d.text((36, 512), "SPECIMEN - DOCUMENT FICTIF - AUCUNE VALEUR LEGALE",
       font=police(23, True), fill=(180, 40, 40))
d.text((36, 552), "Genere pour la demonstration de la plateforme JURIKA.",
       font=police(19), fill=GRIS)
recto = filigrane(img)
recto.save(os.path.join(OUT, 'CIN_specimen_recto.png'), dpi=(300, 300))
print('  + CIN_specimen_recto.png')

# ─── Verso ──────────────────────────────────────────────────────────────
img, d = carte()
champ(d, 320, 140, "ADRESSE", "8, RUE OUED ZIZ")
champ(d, 320, 220, "", "AGDAL - RABAT")
champ(d, 320, 300, "SEXE", "M")
champ(d, 640, 300, "NATIONALITE", "MAROCAINE")
champ(d, 320, 380, "N° CARTE", "A741852")
d.rectangle([36, 486, W - 36, 490], fill=ACCENT)
d.text((36, 512), "SPECIMEN - DOCUMENT FICTIF - AUCUNE VALEUR LEGALE",
       font=police(23, True), fill=(180, 40, 40))
verso = filigrane(img)
verso.save(os.path.join(OUT, 'CIN_specimen_verso.png'), dpi=(300, 300))
print('  + CIN_specimen_verso.png')

print('\nOK - specimens ecrits dans', OUT)
