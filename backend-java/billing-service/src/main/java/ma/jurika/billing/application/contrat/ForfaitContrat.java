package ma.jurika.billing.application.contrat;

import java.util.Locale;

/**
 * Lot L7 (E6) : forfaits tels qu'ils sont imprimes aux conditions particulieres. Les quotas
 * reprennent la grille tarifaire du cahier des charges (paragraphe 4.3) ; le forfait Sur Devis
 * renvoie au devis (aucun chiffre invente).
 */
public enum ForfaitContrat {

    ESSENTIEL("Essentiel", "jusqu\u2019\u00e0 25 par mois", "1 superviseur et 1 acc\u00e8s employ\u00e9", "5 Go"),
    PROFESSIONNEL("Professionnel", "jusqu\u2019\u00e0 50 par mois", "1 superviseur et 2 acc\u00e8s employ\u00e9s", "10 Go"),
    MAX("Max", "jusqu\u2019\u00e0 100 par mois", "1 superviseur et 4 acc\u00e8s employ\u00e9s", "25 Go"),
    SUR_DEVIS("Sur Devis", "plus de 100 par mois", "selon devis", "selon devis");

    private final String libelle;
    private final String dossiers;
    private final String utilisateurs;
    private final String stockage;

    ForfaitContrat(String libelle, String dossiers, String utilisateurs, String stockage) {
        this.libelle = libelle;
        this.dossiers = dossiers;
        this.utilisateurs = utilisateurs;
        this.stockage = stockage;
    }

    public String libelle() { return libelle; }
    public String dossiers() { return dossiers; }
    public String utilisateurs() { return utilisateurs; }
    public String stockage() { return stockage; }

    public static ForfaitContrat depuis(String code) {
        if (code == null || code.isBlank()) {
            throw ContratException.invalide("FORFAIT_ABSENT", "Le forfait est obligatoire.");
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ContratException.invalide("FORFAIT_INCONNU",
                    "Forfait inconnu : " + code + " (Essentiel, Professionnel, Max ou Sur Devis).");
        }
    }
}
