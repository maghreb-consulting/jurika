# DICTIONNAIRE DES VARIABLES — STATUTS_SARL & STATUTS_SARL_AU (mis à jour)

Base commune du projet, complétée par les variables issues des modèles réels analysés.
Les entrées marquées **[NOUVEAU]** ont été ajoutées lors de cette consolidation.
Convention : `$NOM_EN_MAJUSCULES`. Une même donnée porte le même nom dans TOUS les modèles JURIKA.

> **Portée SARL / SARL AU :** ce dictionnaire couvre les deux modèles. Le modèle **STATUTS_SARL_AU** (associé unique) réutilise strictement les mêmes variables, **sans variable nouvelle**. Seule différence d'emploi : l'associé unique est décrit avec les variables `$ASSOCIE_*` mais **hors boucle** (un seul associé), et il n'y a pas de boucle `ASSOCIES` ni `APPORTS_PAR_ASSOCIE` — l'apport unique est piloté par le même `$APPORT_TYPE`. Les boucles `GERANTS` et `SIGNATAIRES` et l'article de signature sociale (`$MODE_SIGNATURE`, plafond, mandataire) sont identiques.

## Société
- `$DENOMINATION` — Dénomination sociale complète.
- `$OBJET_SOCIAL` — Énoncé de l'objet social (liste des activités).
- `$SIEGE_SOCIAL` — Adresse complète du siège.
- `$DUREE_SOCIETE` — Durée de la société, en années (ex. 99).
- `$VILLE_GREFFE` — **[NOUVEAU]** Ville du tribunal de commerce où les statuts sont déposés (ex. Casablanca).

## Capital et parts
- `$CAPITAL_CHIFFRES` / `$CAPITAL_LETTRES` — Montant du capital (chiffres / lettres).
- `$NOMBRE_PARTS` — Nombre total de parts sociales.
- `$VALEUR_NOMINALE_PART` — Valeur nominale d'une part.
- `$MODE_LIBERATION` — **[NOUVEAU]** « intégrale » ou « partielle » (libération des apports en numéraire ; partielle = 1/4 minimum, solde ≤ 5 ans).
- `$DEPOT_FONDS_BLOQUE` — **[NOUVEAU]** « oui » / « non ». Dépôt en compte bloqué (obligatoire si apports en numéraire > 100 000 DH).
- `$COMPTE_BANCAIRE_NUMERO` / `$BANQUE_DEPOSITAIRE` — Coordonnées du dépôt des fonds (conditionnel, si `$DEPOT_FONDS_BLOQUE` = oui).

## Associés — boucle `ASSOCIES`
- `$ASSOCIE_TYPE` — Condition : « personne physique » / « personne morale ».
- `$ASSOCIE_NOM` — Nom (PP) ou dénomination (PM) — libellé court réutilisé dans la répartition des parts et les signatures.
- **Personne physique :** `$ASSOCIE_CIVILITE`, `$ASSOCIE_PRENOM`, `$ASSOCIE_DATE_NAISSANCE`, `$ASSOCIE_LIEU_NAISSANCE`, `$ASSOCIE_NATIONALITE`, `$ASSOCIE_ADRESSE`, `$ASSOCIE_PIECE_TYPE` (CIN, passeport…), `$ASSOCIE_PIECE_NUMERO`.
- **Personne morale :** `$ASSOCIE_DENOMINATION`, `$ASSOCIE_FORME` (SARL, SA…), `$ASSOCIE_CAPITAL`, `$ASSOCIE_SIEGE`, `$ASSOCIE_RC_VILLE`, `$ASSOCIE_RC_NUMERO`, `$ASSOCIE_REPRESENTANT_NOM`, `$ASSOCIE_REPRESENTANT_QUALITE`.
- **Parts détenues :** `$ASSOCIE_NOMBRE_PARTS`, `$ASSOCIE_PARTS_DE`, `$ASSOCIE_PARTS_A` (numérotation des parts attribuées).
- `$ASSOCIE_EST_GERANT` — **[NOUVEAU]** « oui » / « non ». Indique si l'associé est aussi gérant. Utilisé au bloc de signature : les associés « non » signent « Pour acceptation de la nomination de la gérance » lorsque le gérant est désigné dans les statuts.

## Apports — boucle `APPORTS_PAR_ASSOCIE`
- `$APPORT_ASSOCIE_LIBELLE` — **[NOUVEAU]** Libellé de l'associé apporteur pour la ligne d'apport (identique au libellé de l'associé concerné).
- `$APPORT_TYPE` — Condition : « numéraire » / « nature » / « industrie ».
- `$APPORT_NUMERAIRE_CHIFFRES`, `$APPORT_NUMERAIRE_LETTRES` — Montant de l'apport en numéraire.
- `$APPORT_NATURE_DESCRIPTION`, `$APPORT_NATURE_VALEUR_CHIFFRES`, `$APPORT_NATURE_VALEUR_LETTRES` — Apport en nature.
- `$APPORT_INDUSTRIE_DESCRIPTION` — Apport en industrie (art. 51 loi 5-96 ; ne concourt pas au capital).
- `$COMMISSAIRE_APPORTS_NOM` — Commissaire aux apports (conditionnel, si au moins un apport en nature).

## Gérance
- `$GERANT_MODE_DESIGNATION` — **[NOUVEAU]** Condition : « statutaire » (gérant nommé dans les statuts) / « non statutaire » (gérant désigné par acte séparé).
- `$DUREE_GERANCE` — Durée du mandat de gérance (ex. illimitée, ou nombre d'années).
- Boucle `GERANTS` (gérant obligatoirement personne physique — art. 62) : `$GERANT_CIVILITE`, `$GERANT_PRENOM`, `$GERANT_NOM`, `$GERANT_NATIONALITE`, `$GERANT_ADRESSE`, `$GERANT_PIECE_TYPE`, `$GERANT_PIECE_NUMERO`, `$GERANT_DATE_NAISSANCE` **[NOUVEAU]** (date de naissance du gérant, utilisée dans l'acte de nomination).
- `$LIMITATION_POUVOIRS` — Clause facultative de limitation des pouvoirs de la gérance.

## Signature sociale et pouvoir d'engagement (art. 15)
- `$MODE_SIGNATURE` — **[NOUVEAU]** Condition, valeurs : « séparée » (signature seule sans limitation), « séparée avec plafond » (signature seule jusqu'à un plafond, signature conjointe de deux au-delà), « conjointe » (signature conjointe de deux au moins quel que soit le montant).
- Boucle `SIGNATAIRES` — **[NOUVEAU]** Personnes titulaires de la signature sociale : `$SIGNATAIRE_NOM`, `$SIGNATAIRE_QUALITE` (ex. « associé », « gérant »).
- `$SIGNATURE_PLAFOND_CHIFFRES` / `$SIGNATURE_PLAFOND_LETTRES` — **[NOUVEAU]** Plafond par opération (conditionnel, si `$MODE_SIGNATURE` = « séparée avec plafond »).
- `$SIGNATURE_MANDATAIRE` — **[NOUVEAU]** « oui » / « non ». Faculté d'engagement par un mandataire disposant d'une délégation formelle.
- `$MANDATAIRE_NOM` — **[NOUVEAU]** Identité du mandataire habilité (conditionnel, si `$SIGNATURE_MANDATAIRE` = « oui »).
- `$MANDATAIRE_ACTE_DELEGATION` — **[NOUVEAU]** Référence de l'acte de pouvoir ou de délégation de signature (conditionnel).

*Remplace `$GERANT_SIGNATAIRE` (précédente version), désormais couvert par la boucle `SIGNATAIRES` et `$MODE_SIGNATURE`.*

## Contrôle et comptes
- `$COMMISSAIRE_COMPTES_NOM` — Commissaire aux comptes (conditionnel ; obligatoire si CA HT > 50 000 000 DH — art. 80).
- `$DUREE_MANDAT_CAC` — Durée du mandat du commissaire aux comptes (en exercices ; par défaut 3).
- `$EXERCICE_DEBUT`, `$EXERCICE_FIN` — Dates de début et de fin de l'exercice social (ex. 1er janvier / 31 décembre).
- `$PREMIER_EXERCICE_CLOTURE` — Date de clôture du premier exercice.

## Constitution et signature
- `$ENGAGEMENTS_MANDAT` — Engagements repris pour le compte de la société avant immatriculation (conditionnel).
- `$LIEU_SIGNATURE` — Lieu de signature de l'acte.
- `$DATE_SIGNATURE` — Date de signature.
- `$NOMBRE_ORIGINAUX` — Nombre d'exemplaires originaux.

## Publication (annonce légale) et acte de nomination du gérant
- `$ASSOCIE_UNIQUE` — **[NOUVEAU]** « oui » / « non ». Pilote SARL AU (associé unique) vs SARL (pluralité). Sert dans l'annonce légale et l'acte de nomination.
- `$DATE_ACTE` — **[NOUVEAU]** Date de l'acte constitutif (sous seing privé) — annonce légale.
- `$DATE_DEPOT_LEGAL` — **[NOUVEAU]** Date du dépôt légal au greffe du tribunal de commerce — annonce légale.
- `$RC_NUMERO` — **[NOUVEAU]** Numéro d'immatriculation de la société au registre du commerce — annonce légale.
- `$ARTICLE_DESIGNATION_STATUTS` — **[NOUVEAU]** Numéro de l'article des statuts relatif à la désignation de la gérance, cité dans l'acte de nomination.
- `$HEURE_ACTE` — **[NOUVEAU]** Heure de tenue de l'acte de nomination du gérant.
