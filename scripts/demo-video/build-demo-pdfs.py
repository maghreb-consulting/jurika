# -*- coding: utf-8 -*-
"""
Fabrique les PDF de DEMONSTRATION deposes dans la Data Room de JUR-DEMO2.

Tout le contenu est FICTIF (societes inventees, personnes inventees, numeros
inventes). Aucun document ne provient d'un dossier client reel.

Sortie : scripts/demo-video/assets/*.pdf
Usage  : python scripts/demo-video/build-demo-pdfs.py
"""
import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.lib.enums import TA_JUSTIFY, TA_CENTER
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'assets')
os.makedirs(OUT, exist_ok=True)

ss = getSampleStyleSheet()
TITRE = ParagraphStyle('T', parent=ss['Title'], fontName='Helvetica-Bold',
                       fontSize=15, leading=19, spaceAfter=4, alignment=TA_CENTER)
SOUS = ParagraphStyle('S', parent=ss['Normal'], fontName='Helvetica',
                      fontSize=10, leading=14, alignment=TA_CENTER,
                      textColor='#555555', spaceAfter=14)
H = ParagraphStyle('H', parent=ss['Heading2'], fontName='Helvetica-Bold',
                   fontSize=11, leading=15, spaceBefore=10, spaceAfter=4)
P = ParagraphStyle('P', parent=ss['Normal'], fontName='Helvetica',
                   fontSize=9.5, leading=14, alignment=TA_JUSTIFY, spaceAfter=5)


def build(nom, titre, sous_titre, blocs):
    chemin = os.path.join(OUT, nom)
    doc = SimpleDocTemplate(chemin, pagesize=A4,
                            leftMargin=22 * mm, rightMargin=22 * mm,
                            topMargin=20 * mm, bottomMargin=18 * mm,
                            title=titre, author='JURIKA - donnees de demonstration')
    flow = [Paragraph(titre, TITRE), Paragraph(sous_titre, SOUS)]
    for kind, texte in blocs:
        flow.append(Paragraph(texte, H if kind == 'h' else P))
    flow.append(Spacer(1, 8 * mm))
    flow.append(Paragraph(
        "<i>Document de DEMONSTRATION - societe fictive, donnees inventees. "
        "Genere pour la video de presentation de la plateforme JURIKA.</i>",
        ParagraphStyle('F', parent=P, fontSize=7.5, textColor='#888888')))
    doc.build(flow)
    print('  +', nom)


# ---------------------------------------------------------------------
# 1. STATUTS - version 1
# ---------------------------------------------------------------------
STATUTS_COMMUNS = [
    ('h', 'ARTICLE 1 - FORME'),
    ('p', "Il est forme entre les proprietaires des parts sociales ci-apres creees et de celles "
          "qui pourraient l&rsquo;etre ulterieurement, une societe a responsabilite limitee regie par "
          "la loi n&deg; 5-96 du 13 fevrier 1997 sur la societe en nom collectif, la societe en "
          "commandite simple, la societe en commandite par actions, la societe a responsabilite "
          "limitee et la societe en participation, telle que modifiee et completee."),
    ('h', 'ARTICLE 2 - DENOMINATION'),
    ('p', "La societe prend la denomination sociale de &laquo; SARL ATLAS CONSEIL &raquo;."),
    ('h', 'ARTICLE 3 - OBJET'),
    ('p', "La societe a pour objet, tant au Maroc qu&rsquo;a l&rsquo;etranger : le conseil en organisation et "
          "en gestion d&rsquo;entreprise ; l&rsquo;assistance administrative et juridique aux societes ; la "
          "formation professionnelle continue ; et plus generalement toutes operations "
          "commerciales, industrielles, financieres, mobilieres ou immobilieres se rattachant "
          "directement ou indirectement a l&rsquo;objet social."),
    ('h', 'ARTICLE 4 - SIEGE SOCIAL'),
    ('p', "Le siege social est fixe au 45, boulevard Zerktouni, 6e etage, Casablanca. Il peut etre "
          "transfere en tout autre lieu de la meme prefecture par simple decision de la gerance, et "
          "en tout autre lieu du Royaume par decision collective extraordinaire des associes."),
    ('h', 'ARTICLE 5 - DUREE'),
    ('p', "La duree de la societe est fixee a quatre-vingt-dix-neuf (99) annees a compter de son "
          "immatriculation au registre du commerce, sauf dissolution anticipee ou prorogation."),
]

AGREMENT = (
    'p',
    "Les parts sociales sont librement cessibles entre associes. Elles ne peuvent etre cedees "
    "a des tiers etrangers a la societe qu&rsquo;avec le consentement de la majorite des associes "
    "representant au moins les trois quarts (3/4) des parts sociales, conformement a "
    "l&rsquo;article 56 de la loi n&deg; 5-96. Le projet de cession est notifie a la societe et a "
    "chacun des associes par lettre recommandee avec accuse de reception. Si la societe n&rsquo;a pas "
    "fait connaitre sa decision dans le delai de trente (30) jours a compter de la derniere des "
    "notifications, le consentement a la cession est repute acquis. Si la societe refuse de "
    "consentir a la cession, les associes sont tenus, dans le delai de trente (30) jours a compter "
    "de ce refus, d&rsquo;acquerir ou de faire acquerir les parts a un prix fixe a dire d&rsquo;expert."
)

build('Statuts_Atlas_Conseil_v1.pdf',
      'STATUTS',
      'SARL ATLAS CONSEIL - Societe a responsabilite limitee au capital de 100 000,00 MAD<br/>'
      'Siege social : 45, boulevard Zerktouni, Casablanca',
      STATUTS_COMMUNS + [
          ('h', 'ARTICLE 6 - APPORTS ET CAPITAL SOCIAL'),
          ('p', "Le capital social est fixe a la somme de CENT MILLE DIRHAMS (100 000,00 MAD). "
                "Il est divise en MILLE (1 000) parts sociales de CENT DIRHAMS (100,00 MAD) "
                "chacune, entierement souscrites et liberees, attribuees comme suit : "
                "Monsieur Youssef BENNANI, six cents (600) parts ; Madame Salma IDRISSI, "
                "quatre cents (400) parts. Total : mille (1 000) parts."),
          ('h', 'ARTICLE 7 - CESSION DE PARTS SOCIALES - AGREMENT'),
          AGREMENT,
          ('h', 'ARTICLE 8 - GERANCE'),
          ('p', "La societe est geree et administree par Monsieur Youssef BENNANI, nomme gerant "
                "pour une duree illimitee. Le gerant dispose des pouvoirs les plus etendus pour "
                "agir au nom de la societe en toutes circonstances."),
          ('h', 'ARTICLE 9 - EXERCICE SOCIAL'),
          ('p', "L&rsquo;exercice social commence le 1er janvier et se termine le 31 decembre de chaque "
                "annee. Par exception, le premier exercice social commence a la date "
                "d&rsquo;immatriculation et se termine le 31 decembre 2025."),
          ('h', 'ARTICLE 10 - AFFECTATION DES RESULTATS'),
          ('p', "Sur le benefice net de l&rsquo;exercice, diminue le cas echeant des pertes anterieures, "
                "il est preleve cinq pour cent (5 %) pour constituer le fonds de reserve legale. "
                "Ce prelevement cesse d&rsquo;etre obligatoire lorsque la reserve atteint le dixieme du "
                "capital social."),
      ])

# ---------------------------------------------------------------------
# 2. STATUTS - version 2 (refondus apres augmentation de capital)
# ---------------------------------------------------------------------
build('Statuts_Atlas_Conseil_v2.pdf',
      'STATUTS REFONDUS',
      'SARL ATLAS CONSEIL - Capital porte a 250 000,00 MAD<br/>'
      "Mis a jour suite a l&rsquo;assemblee generale extraordinaire du 12 mars 2026",
      STATUTS_COMMUNS + [
          ('h', 'ARTICLE 6 - APPORTS ET CAPITAL SOCIAL (MODIFIE)'),
          ('p', "Le capital social est fixe a la somme de DEUX CENT CINQUANTE MILLE DIRHAMS "
                "(250 000,00 MAD), suite a l&rsquo;augmentation de capital de 150 000,00 MAD decidee "
                "par l&rsquo;assemblee generale extraordinaire du 12 mars 2026. Il est divise en DEUX "
                "MILLE CINQ CENTS (2 500) parts sociales de CENT DIRHAMS (100,00 MAD) chacune, "
                "attribuees comme suit : Monsieur Youssef BENNANI, mille deux cents (1 200) parts ; "
                "Madame Salma IDRISSI, huit cents (800) parts ; Monsieur Karim TAZI, cinq cents "
                "(500) parts. Total : deux mille cinq cents (2 500) parts."),
          ('h', 'ARTICLE 7 - CESSION DE PARTS SOCIALES - AGREMENT'),
          AGREMENT,
          ('h', 'ARTICLE 8 - GERANCE (MODIFIE)'),
          ('p', "La societe est geree par deux cogerants : Monsieur Youssef BENNANI et Madame "
                "Salma IDRISSI, nommes pour une duree illimitee. Chacun dispose de la signature "
                "sociale ; toutefois, les actes de disposition portant sur un montant superieur a "
                "cent mille dirhams (100 000,00 MAD) requierent la signature conjointe."),
          ('h', 'ARTICLE 9 - EXERCICE SOCIAL'),
          ('p', "L&rsquo;exercice social commence le 1er janvier et se termine le 31 decembre de chaque annee."),
          ('h', 'ARTICLE 10 - AFFECTATION DES RESULTATS'),
          ('p', "Sur le benefice net de l&rsquo;exercice, il est preleve cinq pour cent (5 %) pour la "
                "reserve legale. Le solde est reparti entre les associes proportionnellement au "
                "nombre de parts detenues, ou reporte a nouveau sur decision collective."),
      ])

# ---------------------------------------------------------------------
# 3. PV AGO
# ---------------------------------------------------------------------
build('PV_AGO_Atlas_Conseil_2025.pdf',
      "PROCES-VERBAL DE L&rsquo;ASSEMBLEE GENERALE ORDINAIRE",
      'SARL ATLAS CONSEIL - Exercice clos le 31 decembre 2025<br/>Reunion du 20 juin 2026',
      [
          ('p', "L&rsquo;an deux mille vingt-six, le vingt juin a dix heures, les associes de la societe "
                "SARL ATLAS CONSEIL se sont reunis en assemblee generale ordinaire au siege social, "
                "sur convocation du gerant adressee le 4 juin 2026, soit plus de quinze (15) jours "
                "avant la date de reunion, conformement a l&rsquo;article 71 de la loi n&deg; 5-96."),
          ('h', 'FEUILLE DE PRESENCE'),
          ('p', "Monsieur Youssef BENNANI, titulaire de 600 parts, present. Madame Salma IDRISSI, "
                "titulaire de 400 parts, presente. Total : 1 000 parts sur 1 000, soit 100 % du "
                "capital social. L&rsquo;assemblee peut valablement deliberer."),
          ('h', 'ORDRE DU JOUR'),
          ('p', "1. Lecture du rapport de gestion du gerant. 2. Approbation des comptes de "
                "l&rsquo;exercice clos le 31 decembre 2025. 3. Affectation du resultat. 4. Quitus au "
                "gerant. 5. Questions diverses."),
          ('h', 'PREMIERE RESOLUTION - APPROBATION DES COMPTES'),
          ('p', "L&rsquo;assemblee generale, apres avoir entendu la lecture du rapport de gestion, "
                "approuve les comptes annuels de l&rsquo;exercice clos le 31 decembre 2025, tels qu&rsquo;ils "
                "lui ont ete presentes, se soldant par un benefice net de QUATRE-VINGT-DOUZE MILLE "
                "QUATRE CENTS DIRHAMS (92 400,00 MAD). Cette resolution est adoptee a l&rsquo;unanimite."),
          ('h', 'DEUXIEME RESOLUTION - AFFECTATION DU RESULTAT'),
          ('p', "L&rsquo;assemblee generale decide d&rsquo;affecter le benefice net de 92 400,00 MAD comme "
                "suit : reserve legale (5 %) : 4 620,00 MAD ; report a nouveau : 87 780,00 MAD. "
                "Cette resolution est adoptee a l&rsquo;unanimite."),
          ('h', 'TROISIEME RESOLUTION - QUITUS AU GERANT'),
          ('p', "L&rsquo;assemblee generale donne au gerant quitus entier et sans reserve de sa gestion "
                "pour l&rsquo;exercice ecoule. Cette resolution est adoptee a l&rsquo;unanimite."),
          ('p', "L&rsquo;ordre du jour etant epuise et personne ne demandant plus la parole, la seance "
                "est levee a onze heures trente."),
      ])

# ---------------------------------------------------------------------
# 4. PV AGE augmentation de capital
# ---------------------------------------------------------------------
build('PV_AGE_Augmentation_Capital.pdf',
      "PROCES-VERBAL DE L&rsquo;ASSEMBLEE GENERALE EXTRAORDINAIRE",
      'SARL ATLAS CONSEIL - Augmentation de capital<br/>Reunion du 12 mars 2026',
      [
          ('p', "L&rsquo;an deux mille vingt-six, le douze mars a quinze heures, les associes de la "
                "societe SARL ATLAS CONSEIL se sont reunis en assemblee generale extraordinaire au "
                "siege social, sur convocation reguliere du gerant."),
          ('h', 'PREMIERE RESOLUTION - AUGMENTATION DE CAPITAL'),
          ('p', "L&rsquo;assemblee generale decide d&rsquo;augmenter le capital social d&rsquo;une somme de CENT "
                "CINQUANTE MILLE DIRHAMS (150 000,00 MAD) pour le porter de 100 000,00 MAD a "
                "250 000,00 MAD, par creation de mille cinq cents (1 500) parts sociales nouvelles "
                "de 100,00 MAD chacune, integralement liberees en numeraire. Cette resolution est "
                "adoptee a l&rsquo;unanimite."),
          ('h', 'DEUXIEME RESOLUTION - AGREMENT D&rsquo;UN NOUVEL ASSOCIE'),
          ('p', "L&rsquo;assemblee generale agree Monsieur Karim TAZI en qualite de nouvel associe, a "
                "hauteur de cinq cents (500) parts sociales nouvelles, conformement a la procedure "
                "d&rsquo;agrement prevue a l&rsquo;article 7 des statuts. Cette resolution est adoptee a "
                "l&rsquo;unanimite."),
          ('h', 'TROISIEME RESOLUTION - MODIFICATION CORRELATIVE DES STATUTS'),
          ('p', "En consequence des resolutions qui precedent, l&rsquo;assemblee generale decide de "
                "modifier l&rsquo;article 6 des statuts et adopte les statuts refondus dont un "
                "exemplaire demeure annexe au present proces-verbal. Cette resolution est adoptee "
                "a l&rsquo;unanimite."),
          ('h', 'QUATRIEME RESOLUTION - POUVOIRS'),
          ('p', "Tous pouvoirs sont donnes au porteur d&rsquo;un original, d&rsquo;une copie ou d&rsquo;un extrait "
                "des presentes a l&rsquo;effet d&rsquo;accomplir les formalites de publicite legale, de depot "
                "au greffe et de modification au registre du commerce."),
      ])

# ---------------------------------------------------------------------
# 5. Annonce legale
# ---------------------------------------------------------------------
build('Annonce_Legale_Augmentation_Capital.pdf',
      'ANNONCE LEGALE',
      "Journal d&rsquo;annonces legales - Parution du 25 mars 2026",
      [
          ('h', 'SARL ATLAS CONSEIL'),
          ('p', "Societe a responsabilite limitee au capital de 250 000,00 MAD. Siege social : "
                "45, boulevard Zerktouni, Casablanca. RC Casablanca n&deg; 512 447 - ICE "
                "001234567000089."),
          ('h', 'AUGMENTATION DE CAPITAL'),
          ('p', "Aux termes du proces-verbal de l&rsquo;assemblee generale extraordinaire du 12 mars 2026, "
                "les associes ont decide d&rsquo;augmenter le capital social de 150 000,00 MAD pour le "
                "porter de 100 000,00 MAD a 250 000,00 MAD, par creation de 1 500 parts sociales "
                "nouvelles de 100,00 MAD chacune, integralement liberees en numeraire."),
          ('p', "Les articles 6 et 8 des statuts ont ete modifies en consequence. Le depot legal a "
                "ete effectue au greffe du tribunal de commerce de Casablanca."),
          ('p', "Pour avis - La gerance."),
      ])

# ---------------------------------------------------------------------
# 6. Contrat de bail
# ---------------------------------------------------------------------
build('Contrat_Bail_Siege_Social.pdf',
      'CONTRAT DE BAIL COMMERCIAL',
      'Siege social - 45, boulevard Zerktouni, Casablanca',
      [
          ('h', 'ENTRE LES SOUSSIGNES'),
          ('p', "La societe IMMO ZERKTOUNI SARL, au capital de 500 000,00 MAD, dont le siege est "
                "sis 12, rue Ibn Batouta, Casablanca, ci-apres denommee &laquo; le bailleur &raquo;, "
                "d&rsquo;une part ; et la societe SARL ATLAS CONSEIL, ci-apres denommee &laquo; le "
                "preneur &raquo;, d&rsquo;autre part."),
          ('h', 'ARTICLE 1 - OBJET'),
          ('p', "Le bailleur donne a bail au preneur, qui accepte, un local a usage commercial et "
                "de bureaux d&rsquo;une superficie de cent vingt (120) metres carres, situe au 6e etage "
                "de l&rsquo;immeuble sis 45, boulevard Zerktouni a Casablanca."),
          ('h', 'ARTICLE 2 - DUREE'),
          ('p', "Le present bail est consenti pour une duree de trois (3) annees entieres et "
                "consecutives, ayant commence a courir le 1er janvier 2026 pour se terminer le "
                "31 decembre 2028."),
          ('h', 'ARTICLE 3 - LOYER'),
          ('p', "Le loyer mensuel est fixe a DOUZE MILLE DIRHAMS (12 000,00 MAD) hors taxes, "
                "payable d&rsquo;avance le premier de chaque mois. Il sera revise triennalement selon "
                "l&rsquo;indice officiel des loyers commerciaux."),
          ('h', 'ARTICLE 4 - DESTINATION DES LIEUX'),
          ('p', "Les lieux loues sont exclusivement destines a l&rsquo;exercice de l&rsquo;activite de conseil "
                "en organisation et gestion d&rsquo;entreprise du preneur."),
      ])

# ---------------------------------------------------------------------
# 7-9. Documents fiscaux et comptables
# ---------------------------------------------------------------------
build('Etat_Imposition_2025.pdf',
      "ETAT D&rsquo;IMPOSITION",
      'SARL ATLAS CONSEIL - Exercice 2025 - Direction Generale des Impots',
      [
          ('h', 'IDENTIFICATION DU CONTRIBUABLE'),
          ('p', "Raison sociale : SARL ATLAS CONSEIL. Identifiant fiscal : 40218773. "
                "ICE : 001234567000089. Taxe professionnelle : 30721104. "
                "Adresse : 45, boulevard Zerktouni, Casablanca."),
          ('h', 'IMPOT SUR LES SOCIETES - EXERCICE 2025'),
          ('p', "Resultat fiscal declare : 92 400,00 MAD. Taux applicable (bareme progressif "
                "art. 19 CGI) : 20 %. Impot sur les societes du : 18 480,00 MAD. "
                "Cotisation minimale (art. 144 CGI) : 4 830,00 MAD. Montant retenu : 18 480,00 MAD."),
          ('h', 'ACOMPTES VERSES'),
          ('p', "Acompte T1 : 3 210,00 MAD. Acompte T2 : 3 210,00 MAD. Acompte T3 : 3 210,00 MAD. "
                "Acompte T4 : 3 210,00 MAD. Total des acomptes : 12 840,00 MAD."),
          ('h', 'SOLDE'),
          ('p', "Solde de liquidation du a la date du depot : 5 640,00 MAD. Date limite de "
                "paiement : 31 mars 2026."),
      ])

build('Declaration_TVA_T4_2025.pdf',
      'DECLARATION DE TVA - 4e TRIMESTRE 2025',
      'SARL ATLAS CONSEIL - Regime trimestriel',
      [
          ('h', 'CHIFFRE D&rsquo;AFFAIRES'),
          ('p', "Chiffre d&rsquo;affaires taxable a 20 % : 486 000,00 MAD. TVA collectee : 97 200,00 MAD."),
          ('h', 'TVA DEDUCTIBLE'),
          ('p', "TVA sur charges : 31 400,00 MAD. TVA sur immobilisations : 8 200,00 MAD. "
                "Total deductible : 39 600,00 MAD."),
          ('h', 'TVA DUE'),
          ('p', "TVA due au titre du trimestre : 57 600,00 MAD. Date limite de depot et de "
                "paiement : 31 janvier 2026."),
      ])

build('Grand_Livre_2025.pdf',
      'GRAND LIVRE - EXTRAIT',
      'SARL ATLAS CONSEIL - Exercice 2025',
      [
          ('h', 'CLASSE 6 - CHARGES'),
          ('p', "6111 Achats de marchandises : 128 400,00 MAD. 6131 Locations et charges "
                "locatives : 144 000,00 MAD. 6171 Remunerations du personnel : 312 000,00 MAD. "
                "6174 Charges sociales : 74 880,00 MAD."),
          ('h', 'CLASSE 7 - PRODUITS'),
          ('p', "7111 Ventes de marchandises : 0,00 MAD. 7121 Ventes de services produits : "
                "842 300,00 MAD. 7381 Interets et produits assimiles : 1 480,00 MAD."),
          ('h', 'RESULTAT'),
          ('p', "Total produits : 843 780,00 MAD. Total charges : 751 380,00 MAD. "
                "Resultat de l&rsquo;exercice : 92 400,00 MAD."),
      ])

# ---------------------------------------------------------------------
# 10. Version TEXTE des statuts refondus, pour le corpus du chatbot
# ---------------------------------------------------------------------
# Le chunker du RAG (TextChunker, ai-service) decoupe sur les LIGNES VIDES et
# coupe en dur a 1200 caracteres quand un paragraphe est plus long. Le texte
# extrait d'un PDF arrive en un seul bloc : l'article 7 (agrement) se retrouvait
# alors coupe au milieu d'une phrase, et le chatbot repondait en signalant un
# contexte tronque. On fournit donc au corpus la meme matiere, mais avec un
# article par paragraphe : chaque chunk s'arrete sur une frontiere d'article.
def _sans_entites(t):
    return (t.replace('&rsquo;', "'").replace('&laquo; ', '« ').replace(' &raquo;', ' »')
             .replace('&laquo;', '«').replace('&raquo;', '»').replace('&deg;', '°'))


STATUTS_V2_BLOCS = STATUTS_COMMUNS + [
    ('h', 'ARTICLE 6 - APPORTS ET CAPITAL SOCIAL (MODIFIE)'),
    ('p', "Le capital social est fixe a la somme de DEUX CENT CINQUANTE MILLE DIRHAMS "
          "(250 000,00 MAD), suite a l&rsquo;augmentation de capital de 150 000,00 MAD decidee par "
          "l&rsquo;assemblee generale extraordinaire du 12 mars 2026. Il est divise en DEUX MILLE "
          "CINQ CENTS (2 500) parts sociales de CENT DIRHAMS (100,00 MAD) chacune, attribuees "
          "comme suit : Monsieur Youssef BENNANI, mille deux cents (1 200) parts ; Madame Salma "
          "IDRISSI, huit cents (800) parts ; Monsieur Karim TAZI, cinq cents (500) parts."),
    ('h', 'ARTICLE 7 - CESSION DE PARTS SOCIALES - AGREMENT'),
    AGREMENT,
    ('h', 'ARTICLE 8 - GERANCE (MODIFIE)'),
    ('p', "La societe est geree par deux cogerants : Monsieur Youssef BENNANI et Madame Salma "
          "IDRISSI, nommes pour une duree illimitee. Chacun dispose de la signature sociale ; "
          "toutefois, les actes de disposition portant sur un montant superieur a cent mille "
          "dirhams (100 000,00 MAD) requierent la signature conjointe."),
    ('h', 'ARTICLE 9 - EXERCICE SOCIAL'),
    ('p', "L&rsquo;exercice social commence le 1er janvier et se termine le 31 decembre de chaque annee."),
    ('h', 'ARTICLE 10 - AFFECTATION DES RESULTATS'),
    ('p', "Sur le benefice net de l&rsquo;exercice, il est preleve cinq pour cent (5 %) pour la "
          "reserve legale. Le solde est reparti entre les associes proportionnellement au nombre "
          "de parts detenues, ou reporte a nouveau sur decision collective."),
]

lignes = ["STATUTS REFONDUS - SARL ATLAS CONSEIL",
          "Capital porte a 250 000,00 MAD - AGE du 12 mars 2026"]
courant = []
for kind, texte in STATUTS_V2_BLOCS:
    if kind == 'h':
        if courant:
            lignes.append('\n'.join(courant))
            courant = []
        courant.append(_sans_entites(texte))
    else:
        courant.append(_sans_entites(texte))
if courant:
    lignes.append('\n'.join(courant))

chemin_txt = os.path.join(OUT, 'Statuts_Atlas_Conseil_v2_corpus.txt')
with open(chemin_txt, 'w', encoding='utf-8') as f:
    f.write('\n\n'.join(lignes) + '\n')
print('  +', os.path.basename(chemin_txt), '(corpus chatbot, 1 article = 1 paragraphe)')

print('\nOK - PDF de demonstration ecrits dans', OUT)
