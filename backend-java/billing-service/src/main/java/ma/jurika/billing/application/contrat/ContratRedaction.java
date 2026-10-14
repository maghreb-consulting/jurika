package ma.jurika.billing.application.contrat;

import ma.jurika.billing.infrastructure.persistence.EditeurParametresEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lot L7 (E6) : redaction du contrat a partir du modele d'une version, des conditions
 * particulieres, de l'identite de l'editeur et de celle du cabinet. Toute donnee absente est
 * imprimee "[non renseigne : ...]" et nommee dans {@link Redaction#manquants()} : rien n'est
 * invente, rien n'est laisse blanc.
 */
public final class ContratRedaction {

    public static final String NON_RENSEIGNE = "[non renseign\u00e9 : %s]";
    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    /** Donnees propres au signataire, saisies a l'acceptation. */
    public record Signataire(String civilite, String qualite, String formeCabinet, String rcVilleCabinet) {}

    public record Redaction(String texte, String empreinte, List<String> manquants) {}

    private ContratRedaction() {}

    /** Texte soumis a l'acceptation (paragraphe d'acceptation "a completer"). */
    public static Redaction rediger(String modele, String versionCode, ContratDepot.Conditions cp,
                                    EditeurParametresEntity editeur, IdentiteCabinetSource.Identite cabinet,
                                    Signataire signataire) {
        List<String> manquants = new ArrayList<>();
        Map<String, String> v = new HashMap<>();
        // Editeur : parametres de l'editeur (ecran /admin/editeur), colonne par colonne.
        //  - RC_VILLE = rc_tribunal, libelle "Ville du registre du commerce" (et non "ville", qui est la
        //    ville du siege) ;
        //  - SIEGE = adresse du siege, puis ville du siege, comme sur les factures (FactureService) :
        //    l'adresse saisie ne porte pas la ville.
        // Les modeles en vigueur (version 1) impriment toutes ces mentions.
        v.put("EDITEUR_DENOMINATION", ou(editeur.getRaisonSociale(), "raison sociale de l\u2019\u00c9diteur", manquants));
        v.put("EDITEUR_FORME", ou(editeur.getFormeJuridique(), "forme juridique de l\u2019\u00c9diteur", manquants));
        v.put("EDITEUR_CAPITAL", ou(editeur.getCapitalSocial(), "capital social de l\u2019\u00c9diteur", manquants));
        v.put("EDITEUR_SIEGE", ou(editeur.getSiegeSocial(), "adresse du si\u00e8ge de l\u2019\u00c9diteur", manquants)
                + ", " + ou(editeur.getVille(), "ville du si\u00e8ge de l\u2019\u00c9diteur", manquants));
        v.put("EDITEUR_RC_VILLE", ou(editeur.getRcTribunal(), "ville du registre du commerce de l\u2019\u00c9diteur", manquants));
        v.put("EDITEUR_RC_NUMERO", ou(editeur.getRcNumero(), "num\u00e9ro du registre du commerce de l\u2019\u00c9diteur", manquants));
        v.put("EDITEUR_ICE", ou(editeur.getIce(), "ICE de l\u2019\u00c9diteur", manquants));
        v.put("EDITEUR_IF", ou(editeur.getIdentifiantFiscal(), "identifiant fiscal de l\u2019\u00c9diteur", manquants));
        String repNom = ou(editeur.getRepresentantNom(), "nom du repr\u00e9sentant de l\u2019\u00c9diteur", manquants);
        String repQualite = ou(editeur.getRepresentantQualite(), "qualit\u00e9 du repr\u00e9sentant de l\u2019\u00c9diteur", manquants);
        v.put("EDITEUR_REPRESENTANT_NOM", repNom);
        v.put("EDITEUR_REPRESENTANT_QUALITE", repQualite);
        v.put("EDITEUR_REPRESENTANT", repNom + ", " + repQualite);
        // Cabinet.
        v.put("CABINET_DENOMINATION", ou(cabinet.denomination(), "d\u00e9nomination du Cabinet", manquants));
        v.put("CABINET_FORME", ou(signataire.formeCabinet(), "forme juridique du Cabinet", manquants));
        v.put("CABINET_SIEGE", ou(cabinet.adresse(), "adresse du si\u00e8ge du Cabinet", manquants));
        v.put("CABINET_RC_VILLE", ou(signataire.rcVilleCabinet(), "ville du registre du commerce du Cabinet", manquants));
        v.put("CABINET_RC_NUMERO", ou(cabinet.rcNumero(), "num\u00e9ro du registre du commerce du Cabinet", manquants));
        v.put("CABINET_ICE", ou(cabinet.ice(), "ICE du Cabinet", manquants));
        v.put("CABINET_EMAIL", ou(cabinet.email(), "adresse \u00e9lectronique de notification du Cabinet", manquants));
        // Signataire.
        v.put("SIGNATAIRE_CIVILITE", ou(signataire.civilite(), "civilit\u00e9 du signataire", manquants));
        v.put("SIGNATAIRE_NOM", ou(cabinet.signataireNom(), "nom du signataire", manquants));
        v.put("SIGNATAIRE_PRENOM", ou(cabinet.signatairePrenom(), "pr\u00e9nom du signataire", manquants));
        v.put("SIGNATAIRE_QUALITE", ou(signataire.qualite(), "qualit\u00e9 du signataire", manquants));
        v.put("SIGNATAIRE_COMPTE", ou(cabinet.signataireCompte(), "compte du signataire", manquants));
        // Conditions particulieres.
        ForfaitContrat forfait = ForfaitContrat.depuis(cp.forfait());
        boolean mensuel = "MENSUEL".equals(cp.rythmePaiement());
        v.put("ABONNEMENT_REFERENCE", cp.reference());
        v.put("FORFAIT_NOM", forfait.libelle());
        v.put("FORFAIT_DOSSIERS", forfait.dossiers());
        v.put("FORFAIT_UTILISATEURS", forfait.utilisateurs());
        v.put("FORFAIT_STOCKAGE", forfait.stockage());
        v.put("RYTHME_PAIEMENT", mensuel ? "Mensuel" : "Annuel");
        v.put("PRIX_HT", montant(cp.prixHt()));
        v.put("PRIX_PERIODE", mensuel ? "par mois" : "par an");
        v.put("PRIX_ANNUEL_HT", montant(mensuel ? cp.prixHt().multiply(BigDecimal.valueOf(12)) : cp.prixHt()));
        v.put("DATE_DEBUT", date(cp.dateDebut()));
        v.put("DATE_ECHEANCE", date(cp.dateFin()));
        v.put("MODE_PAIEMENT", ou(modePaiement(cp.modePaiement()), "mode de paiement", manquants));
        v.put("CG_VERSION", versionCode);
        for (String a : ContratTexte.VARIABLES_ACCEPTATION) {
            v.put(a, ContratTexte.A_COMPLETER);
        }
        String texte = ContratTexte.remplir(ContratTexte.normaliser(modele), v);
        return new Redaction(texte, ContratTexte.sha256(texte), List.copyOf(manquants));
    }

    /**
     * Exemplaire (RG-CTR-04) : le texte accepte, dont le paragraphe d'acceptation est rempli avec
     * les donnees de la preuve. Le paragraphe "a completer" doit figurer tel quel dans le texte
     * accepte ; sinon l'exemplaire est refuse (jamais un document approximatif).
     */
    public static String exemplaire(String modele, ContratDepot.Acceptation a) {
        String gabarit = ContratTexte.paragrapheAcceptation(ContratTexte.normaliser(modele));
        if (gabarit == null) {
            throw new IllegalStateException("Paragraphe d'acceptation absent du modele " + a.versionCode());
        }
        Map<String, String> avant = new HashMap<>();
        Map<String, String> apres = new HashMap<>();
        avant.put("SIGNATAIRE_NOM", a.signataireNom());
        avant.put("SIGNATAIRE_PRENOM", a.signatairePrenom());
        avant.put("SIGNATAIRE_COMPTE", a.signataireCompte());
        apres.putAll(avant);
        for (String var : ContratTexte.VARIABLES_ACCEPTATION) {
            avant.put(var, ContratTexte.A_COMPLETER);
        }
        String[] dateHeure = a.accepteLeCasablanca().split(" ");
        apres.put("ACCEPTATION_DATE", date(LocalDate.parse(dateHeure[0])));
        apres.put("ACCEPTATION_HEURE", dateHeure[1]);
        apres.put("ACCEPTATION_IP", a.adresseIp());
        apres.put("CONTRAT_EMPREINTE", a.empreinteContrat());
        String p0 = ContratTexte.remplir(gabarit, avant);
        String p1 = ContratTexte.remplir(gabarit, apres);
        int i = a.texteAccepte().indexOf(p0);
        if (i < 0 || a.texteAccepte().indexOf(p0, i + 1) >= 0) {
            throw new IllegalStateException("Paragraphe d'acceptation introuvable dans le texte accepte " + a.id());
        }
        return a.texteAccepte().replace(p0, p1);
    }

    public static String date(LocalDate d) {
        String jour = d.getDayOfMonth() == 1 ? "1er" : String.valueOf(d.getDayOfMonth());
        return jour + " " + d.format(MOIS);
    }

    public static String montant(BigDecimal m) {
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.FRENCH);
        s.setGroupingSeparator(' ');
        s.setDecimalSeparator(',');
        DecimalFormat f = new DecimalFormat("#,##0.00", s);
        return f.format(m.setScale(2, RoundingMode.HALF_UP));
    }

    static String modePaiement(String code) {
        if (code == null) return null;
        return switch (code) {
            case "CARTE" -> "carte bancaire en ligne";
            case "VIREMENT" -> "virement bancaire";
            case "CHEQUE" -> "ch\u00e8que";
            case "ESPECES" -> "esp\u00e8ces";
            default -> throw new IllegalStateException("Mode de paiement inconnu : " + code);
        };
    }

    private static String ou(String valeur, String libelle, List<String> manquants) {
        if (valeur == null || valeur.isBlank()) {
            if (!manquants.contains(libelle)) manquants.add(libelle);
            return String.format(NON_RENSEIGNE, libelle);
        }
        return valeur.trim();
    }
}
