package ma.jurika.workflow.domain.model;

public enum WorkflowType {
    CREATION(9),
    // 2026-06-25 — Refonte IMPORT : meme tronc de SAISIE que la CREATION
    // (1 Denomination, 2 Siege, 3 Capital, 4 Activite, 5 Dirigeants,
    // 6 Associes) — saisie FORCEE pour une fiche structuree COMPLETE — puis
    // des etapes d'UPLOAD au lieu de la Generation IA de la Creation.
    //
    // 2026-09-04 (lot 1) — les dossiers comptable et fiscal sortent du perimetre
    // produit. Les anciennes etapes 8 (Comptable), 9 (Fiscal) et 10 (Suivi :
    // regime TVA + ouverture d'exercices) sont remplacees par une SEULE etape 8
    // « Depot » : les fichiers de cette nature sont stockes tels quels dans
    // l'espace Depot, sans classement ni traitement. Total 11 -> 9. Le workflow
    // IMPORT sera refondu dans un lot ulterieur ; ce lot se borne a le laisser
    // coherent. Migration en vol : la position courante est bornee par
    // WorkflowProgressAdapter.
    IMPORT(9),
    // 2026-08-11 — Refonte MODIFICATION en 5 etapes (spec directeur consolidee) :
    // 1 Selection · 2 Saisie · 3 Generation · 4 Pieces jointes (optionnelle) ·
    // 5 Synthese/Finalisation. Migration en vol : un workflow demarre sous
    // l'ancien total (4) voit son total reporte a 5 — le nombre d'etapes est
    // toujours lu depuis cet enum (source de verite), jamais depuis le snapshot
    // persiste (cf. WorkflowProgressAdapter.toDomain).
    MODIFICATION(5),
    // 2026-08-12 — Refonte DISSOLUTION en 4 etapes (spec directeur) :
    // 1 Saisie (societe + motif + date AGE + liquidateur + siege de liquidation +
    // convocation optionnelle 16 j) · 2 Generation (PV + annonce legale) ·
    // 3 Pieces jointes (optionnelle) · 4 Synthese. Migration en vol : un workflow
    // demarre sous l'ancien total (3) voit son total reporte a 4 (le nombre d'etapes
    // est toujours lu depuis cet enum, jamais depuis le snapshot persiste).
    DISSOLUTION(4),
    // 2026-08-13 — Refonte LIQUIDATION en 4 etapes (spec directeur) :
    // 1 Saisie (societe DISSOUTE + date de l'AGE de cloture + comptes finaux +
    // liquidateur/date de dissolution repris de la BD + convocation optionnelle 16 j) ·
    // 2 Generation (PV de cloture + rapport de liquidation + annonce legale de cloture) ·
    // 3 Pieces jointes (optionnelle) · 4 Synthese. Migration en vol : un workflow demarre
    // sous l'ancien total (3) voit son total reporte a 4 (le nombre d'etapes est toujours
    // lu depuis cet enum, jamais depuis le snapshot persiste).
    LIQUIDATION(4),
    // 2026-08-13 — Refonte SUCCURSALE_MA en 5 etapes (spec directeur, lot DIVERS §B) :
    // 1 Societe mere BD + date/type d'assemblee + convocation optionnelle (16 j) ·
    // 2 Saisie de la succursale (localisation + greffe propre + dotation + responsable) ·
    // 3 Generation (PV d'ouverture + annonce legale d'ouverture) · 4 Pieces jointes
    // (optionnelle) · 5 Synthese. Les anciennes etapes « statuts modifies » et
    // « formulaire RC » sont retirees (aucun modele directeur), de meme que la saisie du
    // RC de la succursale (attribue par le greffe APRES depot -> identifiants du dossier).
    // Migration en vol : un workflow demarre sous l'ancien total (11) voit son total
    // reporte a 5 (le nombre d'etapes est toujours lu depuis cet enum, jamais depuis le
    // snapshot persiste — cf. WorkflowProgressAdapter.toDomain).
    SUCCURSALE_MA(5),
    // 2026-08-13 — Refonte SUCCURSALE_ETR en 5 etapes (spec directeur, lot DIVERS §C) :
    // 1 Societe mere etrangere (saisie ou selection d'une mere deja enregistree) +
    // date/type de decision de l'organe + convocation optionnelle (16 j) + ecran de
    // conformite BLOQUANT (6 controles, rapatrie ici) · 2 Saisie de la succursale
    // (identique au workflow marocain) · 3 Generation (PV + annonce legale, variante
    // ETRANGERE derivee) · 4 Pieces jointes (optionnelle) · 5 Synthese. Les etapes
    // « statuts apostilles », « RC d'origine », « decision traduite », « formulaire RC »
    // et « RC + ICE succursale » sont retirees (couvertes par la conformite / le depot
    // de pieces, ou attribuees par le greffe APRES depot). Migration en vol : le total
    // BAISSE (13 -> 5), la position courante est bornee par WorkflowProgressAdapter.
    SUCCURSALE_ETR(5),
    FERMETURE_SUCCURSALE(4),
    // 2026-08-13 — Refonte PV_AGO en 5 etapes (spec directeur, lot DIVERS §E) :
    // 1 Societe BD verrouillee + date d'AGO (assemblee ORDINAIRE par nature, aucun
    // selecteur de type) + convocation optionnelle (16 j) + exercice clos ·
    // 2 Donnees du PV (resultat, affectation, dividendes, quitus, conventions / CAC) ·
    // 3 Generation (PV d'approbation + rapport de gestion OPTIONNEL + optionnels de
    // seance ; AUCUNE annonce legale — l'approbation n'est pas opposable aux tiers) ·
    // 4 Pieces jointes (optionnelle) · 5 Synthese. Migration en vol : un workflow demarre
    // sous l'ancien total (4) voit son total reporte a 5.
    PV_AGO(5);

    private final int totalSteps;

    WorkflowType(int totalSteps) {
        this.totalSteps = totalSteps;
    }

    public int totalSteps() {
        return totalSteps;
    }
}
