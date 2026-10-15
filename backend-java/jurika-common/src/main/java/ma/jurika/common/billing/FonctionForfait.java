package ma.jurika.common.billing;

/**
 * Lot L7 (E5) : fonctions qui distinguent les forfaits (CDC 4.5, "Differenciation fonctionnelle").
 *
 * <p>Les fonctions communes a tous les forfaits (workflows et generation des actes, Data Room et
 * acces client, chatbot, extraction des pieces) ne figurent pas ici : elles ne se refusent jamais.
 * Chaque fonction ci-dessous est controlee cote serveur (P6) par le service qui la porte, et masquee
 * a l'ecran avec son explication.
 */
public enum FonctionForfait {

    /** Alertes de delais legaux : Professionnel, Max, Sur Devis. */
    ALERTES_DELAIS("Alertes de d\u00e9lais l\u00e9gaux"),
    /** Messagerie interne : Professionnel, Max, Sur Devis. */
    MESSAGERIE_INTERNE("Messagerie interne"),
    /** Tableaux de bord de productivite : Max, Sur Devis. */
    TABLEAUX_DE_BORD_PRODUCTIVITE("Tableaux de bord de productivit\u00e9"),
    /** Edition des actes integree a la plateforme : Max, Sur Devis. */
    EDITION_INTEGREE_ACTES("\u00c9dition des actes int\u00e9gr\u00e9e \u00e0 la plateforme"),
    /** Rapports d'activite : Max, Sur Devis. */
    RAPPORTS_ACTIVITE("Rapports d\u2019activit\u00e9"),
    /** Engagement de service contractuel et support prioritaire : Sur Devis. */
    ENGAGEMENT_SERVICE("Engagement de service contractuel et support prioritaire");

    private final String libelle;

    FonctionForfait(String libelle) {
        this.libelle = libelle;
    }

    /** Libelle affiche (francais accentue). */
    public String libelle() {
        return libelle;
    }
}
