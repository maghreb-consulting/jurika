package ma.jurika.workflow.application;

import ma.jurika.workflow.domain.model.VariableDuDossier.Origine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'ENTRÉE DU MAGASIN — ce que les écrans du parcours y déposent.
 *
 * <p>C'est le <b>seul</b> chemin par lequel une saisie devient une variable de
 * document. Les neuf étapes écrivent leur état de formulaire dans
 * {@code workflow_progress.data} comme avant — c'est ce que l'écran réaffiche —
 * et ce projecteur en tire les variables, une fois, au même endroit.
 *
 * <h2>Pourquoi ici, et pas dans chaque étape</h2>
 *
 * <p>Le lot C corrige un défaut dont la cause était la dispersion : chaque écran
 * construisait sa part de charge utile à sa façon, et ce que l'un oubliait,
 * personne ne le réclamait. Répartir la projection dans les neuf étapes
 * rouvrirait exactement cette porte. Une seule classe, lisible d'un bout à
 * l'autre, se relit ; neuf fragments ne se relisent pas.
 *
 * <h2>La règle de nommage de l'étape 7 n'est pas devinée</h2>
 *
 * <p>Le catalogue des 180 champs est <b>généré</b> par
 * {@code scripts/lotB/derive-champs-creation.mjs}, qui dérive la clé du nom de
 * la variable par une règle explicite :
 *
 * <pre>const cleDe = (nom) =&gt; nom.toLowerCase().replace(/_([a-z0-9])/g, (_, c) =&gt; c.toUpperCase());</pre>
 *
 * <p>{@code BAILLEUR_NOM} donne {@code bailleurNom}. {@link #variableDeLaCle}
 * applique l'inverse de cette règle — ce n'est pas un rapprochement par
 * ressemblance, c'est la réciproque d'une fonction connue.
 *
 * <h2>Ce qui n'est pas projeté</h2>
 *
 * <p>Les valeurs <b>dérivables</b> ne passent pas par le magasin : date de fin de
 * société, type de tribunal, accord grammatical, montants calculés. Elles
 * restent au résolveur, qui les produit à partir des variables. Les poser ici en
 * ferait des valeurs figées qu'une correction ne rattraperait plus.
 */
@Service
public class ProjecteurVariablesCreation {

    private static final Logger log = LoggerFactory.getLogger(ProjecteurVariablesCreation.class);

    private final MagasinVariables magasin;

    public ProjecteurVariablesCreation(MagasinVariables magasin) {
        this.magasin = magasin;
    }

    /**
     * Projette l'état complet d'un parcours de création dans le magasin.
     *
     * <p>Idempotent : reposer les mêmes valeurs ne crée pas de doublon — les deux
     * index uniques partiels de {@code V13} l'interdisent, et
     * {@link MagasinVariables#poser} est un <i>upsert</i>.
     *
     * @return le nombre de variables simples posées (les boucles comptent pour 1
     *         par occurrence renseignée).
     */
    public int projeter(UUID workspaceId, UUID ticketId, Map<String, Object> data, UUID auteur) {
        if (data == null || data.isEmpty()) return 0;
        Compteur n = new Compteur();

        Map<String, Object> s1 = etape(data, "step1", "denomination");
        Map<String, Object> s2 = etape(data, "step2", "siege");
        Map<String, Object> s3 = etape(data, "step3", "capital");
        Map<String, Object> s4 = etape(data, "step4", "activite");
        Map<String, Object> s7 = etape(data, "step7", null);
        Map<String, Object> s9 = etape(data, "step9", null);
        Map<String, Object> acte = sousCarte(s9, "acteParams");
        Map<String, Object> postImmat = sousCarte(s9, "postImmat");

        // ---- étape 1 — dénomination et identifiants -------------------------
        poser(workspaceId, ticketId, auteur, "etape-1", n, "DENOMINATION", s1.get("denomination"));
        poser(workspaceId, ticketId, auteur, "etape-1", n, "SIGLE", s1.get("sigle"));
        poser(workspaceId, ticketId, auteur, "etape-1", n, "FORME_JURIDIQUE", s1.get("formeJuridique"));
        poser(workspaceId, ticketId, auteur, "etape-1", n, "ICE", s1.get("ice"));
        poser(workspaceId, ticketId, auteur, "etape-1", n, "IDENTIFIANT_FISCAL", s1.get("ifFiscal"));
        poser(workspaceId, ticketId, auteur, "etape-1", n, "RC_NUMERO", s1.get("rcNumero"));
        poser(workspaceId, ticketId, auteur, "etape-1", n, "CERTIFICAT_NEGATIF_NUMERO", s1.get("cnNumero"));
        poser(workspaceId, ticketId, auteur, "etape-1", n, "CERTIFICAT_NEGATIF_DATE", s1.get("cnDate"));

        // ---- étape 2 — siège -----------------------------------------------
        poser(workspaceId, ticketId, auteur, "etape-2", n, "SIEGE_SOCIAL", s2.get("adresse"));
        // La ville du SIÈGE — distincte de celle du greffe et de celle de la DGI.
        poser(workspaceId, ticketId, auteur, "etape-2", n, "SIEGE_VILLE",
                premier(s2.get("commune"), s2.get("ville")));
        // La ville d'IMMATRICULATION, où siège le tribunal : une seule donnée,
        // trois noms au corpus (§ 4.2 du relevé de rattachement).
        Object villeGreffe = premier(s2.get("villeGreffe"), s2.get("province"));
        poser(workspaceId, ticketId, auteur, "etape-2", n, "VILLE_GREFFE", villeGreffe);
        poser(workspaceId, ticketId, auteur, "etape-2", n, "RC_VILLE", villeGreffe);
        poser(workspaceId, ticketId, auteur, "etape-2", n, "TRIBUNAL_VILLE", villeGreffe);
        poser(workspaceId, ticketId, auteur, "etape-2", n, "SIEGE_TITRE_OCCUPATION",
                titreOccupation(s2.get("justificatifType")));

        // ---- étape 3 — capital ---------------------------------------------
        poser(workspaceId, ticketId, auteur, "etape-3", n, "CAPITAL_CHIFFRES", s3.get("capitalSocialMad"));
        poser(workspaceId, ticketId, auteur, "etape-3", n, "NOMBRE_PARTS", s3.get("nombreParts"));
        poser(workspaceId, ticketId, auteur, "etape-3", n, "VALEUR_NOMINALE_PART", s3.get("valeurNominale"));
        poser(workspaceId, ticketId, auteur, "etape-3", n, "DUREE_SOCIETE", s3.get("dureeAnnees"));
        // Lot L3 : l'ecran ecrit `depotBanqueNom` ; `banqueDepot` n'est ecrit par personne.
        poser(workspaceId, ticketId, auteur, "etape-3", n, "BANQUE_DEPOSITAIRE",
                premier(s3.get("banqueDepot"), s3.get("depotBanqueNom")));

        // ---- étape 4 — activité --------------------------------------------
        poser(workspaceId, ticketId, auteur, "etape-4", n, "OBJET_SOCIAL", objetSocial(s4));
        // Saisie à l'étape 4 et NULLE PART AILLEURS : le champ que l'étape 7
        // rouvrait était le doublon (§ 1 du rapport de phase 0).
        poser(workspaceId, ticketId, auteur, "etape-4", n, "ACTIVITE_REGLEMENTEE",
                ouiNon(s4.get("activiteReglementee")));
        poser(workspaceId, ticketId, auteur, "etape-4", n, "ACTIVITE_REGLEMENTEE_PRECISION",
                s4.get("categorieOna"));

        // ---- étape 5 — gérance et signataires -------------------------------
        projeterGerants(workspaceId, ticketId, auteur, liste(data, "step5", "dirigeants"), n);
        projeterSignataires(workspaceId, ticketId, auteur, liste(data, "step5", "signataires"), n);
        Map<String, Object> gerance = sousCarte(etape(data, "step5", null), "gerance");
        poser(workspaceId, ticketId, auteur, "etape-5", n, "DUREE_GERANCE",
                premier(gerance.get("dureeGerance"), gerance.get("dureeMandat")));

        // ---- étape 6 — associés ---------------------------------------------
        projeterAssocies(workspaceId, ticketId, auteur, liste(data, "step6", "associes"), n);

        // ---- étape 7 — les saisies du corpus --------------------------------
        projeterCatalogue(workspaceId, ticketId, auteur, sousCarte(s7, "complements"), n);
        projeterBoucles(workspaceId, ticketId, auteur, sousCarte(s7, "boucles"), n);

        // ---- étape 9 — les dix champs qui n'atteignaient aucun document ------
        // Lot L3 : LIEU_SIGNATURE se saisit a l'etape 7 (« Signature des actes »), une seule
        // fois ; l'etape 9 le posait aussi, avec « Casablanca » par defaut, apres la generation.
        poser(workspaceId, ticketId, auteur, "etape-9", n, "NOMBRE_ORIGINAUX", acte.get("nombreOriginaux"));
        poser(workspaceId, ticketId, auteur, "etape-9", n, "HEURE_ACTE", acte.get("heureActe"));
        poser(workspaceId, ticketId, auteur, "etape-9", n, "EXERCICE_DEBUT", acte.get("exerciceDebut"));
        poser(workspaceId, ticketId, auteur, "etape-9", n, "EXERCICE_FIN", acte.get("exerciceFin"));
        poser(workspaceId, ticketId, auteur, "etape-9", n, "PREMIER_EXERCICE_CLOTURE",
                acte.get("premierExerciceCloture"));
        poser(workspaceId, ticketId, auteur, "etape-9", n, "COMMISSAIRE_COMPTES_NOM",
                acte.get("commissaireComptesNom"));
        poser(workspaceId, ticketId, auteur, "etape-9", n, "DUREE_MANDAT_CAC", acte.get("dureeMandatCac"));
        poser(workspaceId, ticketId, auteur, "etape-9", n, "ENGAGEMENTS_MANDAT", acte.get("engagementsMandat"));
        poser(workspaceId, ticketId, auteur, "etape-9", n, "ARTICLE_DESIGNATION_STATUTS",
                acte.get("articleDesignationStatuts"));
        poser(workspaceId, ticketId, auteur, "etape-9", n, "DATE_DEPOT_LEGAL", postImmat.get("dateDepotLegal"));

        // ---- Lot L3 (RG-VAR-09, D14 de L1) : provenance EXTRAITE -------------
        // L'ecran d'extraction liste, sur l'objet saisi, les champs remplis par la lecture
        // d'une piece et confirmes par l'employe (`_extraits`) ; il retire un champ de la
        // liste des que l'employe le modifie. Ces valeurs sont EXTRAITES, pas SAISIES.
        marquerExtraits(workspaceId, ticketId, auteur, s1, null, null, CHAMPS_ETAPE_1);
        List<Map<String, Object>> dirigeants = liste(data, "step5", "dirigeants");
        for (int i = 0; i < dirigeants.size(); i++) {
            marquerExtraits(workspaceId, ticketId, auteur, dirigeants.get(i), "GERANTS", (short) i, CHAMPS_GERANT);
        }
        List<Map<String, Object>> associes = liste(data, "step6", "associes");
        for (int i = 0; i < associes.size(); i++) {
            marquerExtraits(workspaceId, ticketId, auteur, associes.get(i), "ASSOCIES", (short) i, CHAMPS_ASSOCIE);
        }

        log.debug("magasin.projection ticket={} variables={}", ticketId, n.valeur);
        return n.valeur;
    }

    /** Champ d'ecran -> variable, pour les champs qu'une extraction de piece peut remplir. */
    static final Map<String, String> CHAMPS_ETAPE_1 = Map.of(
            "denomination", "DENOMINATION",
            "cnNumero", "CERTIFICAT_NEGATIF_NUMERO",
            "cnDate", "CERTIFICAT_NEGATIF_DATE");
    static final Map<String, String> CHAMPS_GERANT = Map.of(
            "civilite", "GERANT_CIVILITE", "nom", "GERANT_NOM", "prenom", "GERANT_PRENOM",
            "adresse", "GERANT_ADRESSE", "nationalite", "GERANT_NATIONALITE",
            "dateNaissance", "GERANT_DATE_NAISSANCE", "lieuNaissance", "GERANT_LIEU_NAISSANCE",
            "pieceNumero", "GERANT_PIECE_NUMERO", "cinNumero", "GERANT_PIECE_NUMERO");
    static final Map<String, String> CHAMPS_ASSOCIE = Map.of(
            "civilite", "ASSOCIE_CIVILITE", "nom", "ASSOCIE_NOM", "prenom", "ASSOCIE_PRENOM",
            "adresse", "ASSOCIE_ADRESSE", "nationalite", "ASSOCIE_NATIONALITE",
            "dateNaissance", "ASSOCIE_DATE_NAISSANCE", "lieuNaissance", "ASSOCIE_LIEU_NAISSANCE",
            "pieceNumero", "ASSOCIE_PIECE_NUMERO", "cinNumero", "ASSOCIE_PIECE_NUMERO", "cin", "ASSOCIE_PIECE_NUMERO");

    private void marquerExtraits(UUID ws, UUID ticket, UUID auteur, Map<String, Object> objet,
                                  String boucle, Short rang, Map<String, String> champs) {
        if (auteur == null || objet == null || !(objet.get("_extraits") instanceof List<?> extraits)) return;
        for (Object champ : extraits) {
            String variable = champs.get(String.valueOf(champ));
            if (variable != null) {
                magasin.marquerOrigine(ws, ticket, boucle, rang, variable, Origine.EXTRAITE, auteur);
            }
        }
    }

    // ------------------------------------------------------------------
    // Boucles
    // ------------------------------------------------------------------

    private void projeterGerants(UUID ws, UUID ticket, UUID auteur,
                                  List<Map<String, Object>> dirigeants, Compteur n) {
        List<Map<String, String>> gerants = new ArrayList<>();
        List<Map<String, String>> pm = new ArrayList<>();
        for (Map<String, Object> d : dirigeants) {
            Map<String, String> g = new LinkedHashMap<>();
            g.put("GERANT_CIVILITE", txt(d.get("civilite")));
            g.put("GERANT_NOM", txt(d.get("nom")));
            g.put("GERANT_PRENOM", txt(d.get("prenom")));
            g.put("GERANT_ADRESSE", txt(d.get("adresse")));
            g.put("GERANT_NATIONALITE", txt(d.get("nationalite")));
            g.put("GERANT_DATE_NAISSANCE", txt(d.get("dateNaissance")));
            g.put("GERANT_LIEU_NAISSANCE", txt(d.get("lieuNaissance")));
            g.put("GERANT_PIECE_TYPE", txt(premier(d.get("pieceType"), "CIN")));
            g.put("GERANT_PIECE_NUMERO", txt(premier(d.get("pieceNumero"), d.get("cinNumero"))));
            gerants.add(g);

            Map<String, String> e = new LinkedHashMap<>();
            if ("MORALE".equalsIgnoreCase(txt(d.get("typePersonne")))) {
                e.put("DIRIGEANT_PM_DENOMINATION", txt(d.get("denomination")));
                e.put("DIRIGEANT_PM_FORME", txt(d.get("formeJuridiqueEntite")));
                e.put("DIRIGEANT_PM_SIEGE", txt(d.get("siege")));
                e.put("DIRIGEANT_PM_RC", txt(d.get("rc")));
                e.put("DIRIGEANT_PM_REPRESENTANT", txt(premier(d.get("representantLegal"), nomComplet(d))));
            }
            pm.add(e);
        }
        magasin.poserBoucle(ws, ticket, "GERANTS", gerants, Origine.SAISIE, auteur, "etape-5");
        magasin.poserBoucle(ws, ticket, "DIRIGEANTS_PM", pm, Origine.SAISIE, auteur, "etape-5");
        n.valeur += gerants.size();
    }

    /**
     * LES SIGNATAIRES DÉSIGNÉS À L'ÉTAPE 5.
     *
     * <p>C'est la clé que le constructeur vivant ne transmettait jamais : le
     * résolveur trouvait une liste vide et faisait signer les gérants, en qualité
     * de « gérant », en basculant l'accord grammatical au pluriel. Un acte
     * complet, cohérent et faux.
     *
     * <p>Une liste vide reste vide : le repli « à défaut, les gérants » appartient
     * au résolveur et il est juste. On ne le contourne pas, on cesse de le
     * déclencher à tort.
     */
    private void projeterSignataires(UUID ws, UUID ticket, UUID auteur,
                                      List<Map<String, Object>> designes, Compteur n) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, Object> s : designes) {
            String nom = txt(s.get("nom"));
            if (nom.isBlank()) continue;
            Map<String, String> it = new LinkedHashMap<>();
            it.put("SIGNATAIRE_NOM", nom);
            it.put("SIGNATAIRE_QUALITE", txt(s.get("qualite")));
            out.add(it);
        }
        magasin.poserBoucle(ws, ticket, "SIGNATAIRES", out, Origine.SAISIE, auteur, "etape-5");
        n.valeur += out.size();
    }

    private void projeterAssocies(UUID ws, UUID ticket, UUID auteur,
                                   List<Map<String, Object>> associes, Compteur n) {
        List<Map<String, String>> base = new ArrayList<>();
        List<Map<String, String>> apports = new ArrayList<>();
        for (Map<String, Object> a : associes) {
            boolean morale = "MORALE".equalsIgnoreCase(txt(a.get("typePersonne")));
            Map<String, String> it = new LinkedHashMap<>();
            it.put("ASSOCIE_TYPE", morale ? "personne morale" : "personne physique");
            it.put("ASSOCIE_CIVILITE", txt(a.get("civilite")));
            it.put("ASSOCIE_NOM", txt(morale ? a.get("denomination") : a.get("nom")));
            it.put("ASSOCIE_PRENOM", morale ? "" : txt(a.get("prenom")));
            it.put("ASSOCIE_ADRESSE", txt(a.get("adresse")));
            it.put("ASSOCIE_NATIONALITE", txt(a.get("nationalite")));
            it.put("ASSOCIE_DATE_NAISSANCE", txt(a.get("dateNaissance")));
            it.put("ASSOCIE_LIEU_NAISSANCE", txt(a.get("lieuNaissance")));
            it.put("ASSOCIE_PIECE_TYPE", txt(premier(a.get("pieceType"), "CIN")));
            // Lot L3 : l'ecran des associes ecrit `cin` (le projecteur ne lisait que pieceNumero/cinNumero).
            it.put("ASSOCIE_PIECE_NUMERO", txt(premier(a.get("pieceNumero"), a.get("cinNumero"), a.get("cin"))));
            it.put("ASSOCIE_NOMBRE_PARTS", txt(a.get("nombreParts")));
            it.put("ASSOCIE_EST_GERANT", ouiNon(a.get("estGerant")));
            if (morale) {
                it.put("ASSOCIE_DENOMINATION", txt(a.get("denomination")));
                it.put("ASSOCIE_FORME", txt(premier(a.get("formeJuridique"), a.get("formeJuridiqueEntite"))));
                it.put("ASSOCIE_CAPITAL", txt(a.get("capitalEntite")));
                it.put("ASSOCIE_SIEGE", txt(a.get("siege")));
                it.put("ASSOCIE_RC_NUMERO", txt(a.get("rc")));
                it.put("ASSOCIE_RC_VILLE", txt(a.get("rcVille")));
                it.put("ASSOCIE_REPRESENTANT_NOM", txt(premier(a.get("representantLegal"), nomComplet(a))));
                it.put("ASSOCIE_REPRESENTANT_QUALITE", txt(a.get("repQualite")));
            }
            base.add(it);

            Map<String, String> ap = new LinkedHashMap<>();
            ap.put("APPORT_TYPE", txt(premier(a.get("apportType"), a.get("typeApport"))));
            apports.add(ap);
        }
        magasin.poserBoucle(ws, ticket, "ASSOCIES", base, Origine.SAISIE, auteur, "etape-6");
        magasin.poserBoucle(ws, ticket, "APPORTS_PAR_ASSOCIE", apports, Origine.SAISIE, auteur, "etape-6");
        n.valeur += base.size();

        magasin.poser(ws, ticket, "ASSOCIE_UNIQUE", base.size() == 1 ? "oui" : "non",
                Origine.DERIVEE, null, "etape-6");
    }

    /** Les champs du catalogue de l'étape 7 : {@code bailleurNom} → {@code $BAILLEUR_NOM}. */
    private void projeterCatalogue(UUID ws, UUID ticket, UUID auteur,
                                    Map<String, Object> complements, Compteur n) {
        complements.forEach((cle, valeur) ->
                poser(ws, ticket, auteur, "etape-7", n, variableDeLaCle(cle), valeur));
    }

    @SuppressWarnings("unchecked")
    private void projeterBoucles(UUID ws, UUID ticket, UUID auteur,
                                  Map<String, Object> boucles, Compteur n) {
        boucles.forEach((nomBoucle, brut) -> {
            if (!(brut instanceof List<?> l)) return;
            List<Map<String, String>> out = new ArrayList<>();
            for (Object o : l) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Map<String, String> it = new LinkedHashMap<>();
                ((Map<String, Object>) m).forEach((cle, v) -> it.put(variableDeLaCle(cle), txt(v)));
                out.add(it);
            }
            magasin.poserBoucle(ws, ticket, variableDeLaCle(nomBoucle), out,
                    Origine.SAISIE, auteur, "etape-7");
            n.valeur += out.size();
        });
    }

    // ------------------------------------------------------------------

    /**
     * Réciproque de la règle de génération du catalogue :
     * {@code bailleurNom} → {@code BAILLEUR_NOM}.
     */
    static String variableDeLaCle(String cle) {
        if (cle == null || cle.isBlank()) return "";
        StringBuilder sb = new StringBuilder(cle.length() + 8);
        for (char c : cle.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append('_').append(c);
            else sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    private void poser(UUID ws, UUID ticket, UUID auteur, String occasion, Compteur n,
                        String variable, Object valeur) {
        String v = txt(valeur);
        if (v.isBlank()) return;
        magasin.poser(ws, ticket, variable, v, Origine.SAISIE, auteur, occasion);
        n.valeur++;
    }

    /** « bail » / « domiciliation » — les libellés EXACTS du corpus, pas le code écran. */
    private static String titreOccupation(Object justificatifType) {
        String s = txt(justificatifType).toUpperCase();
        if (s.startsWith("BAIL")) return "bail";
        if (s.startsWith("DOMICILIATION")) return "domiciliation";
        if (s.startsWith("PROPRIETE") || s.startsWith("PROPRIÉTÉ")) return "propriété";
        return "";
    }

    private static String ouiNon(Object valeur) {
        if (valeur == null) return "";
        if (valeur instanceof Boolean b) return b ? "oui" : "non";
        String s = txt(valeur).trim().toLowerCase();
        if (s.isEmpty()) return "";
        return (s.equals("true") || s.equals("oui")) ? "oui" : "non";
    }

    private static String objetSocial(Map<String, Object> s4) {
        Object activites = s4.get("activites");
        if (activites instanceof List<?> l && !l.isEmpty()) {
            if (l.size() == 1) return txt(l.get(0));
            StringBuilder sb = new StringBuilder();
            for (Object o : l) sb.append(sb.isEmpty() ? "" : "\n").append("- ").append(txt(o));
            return sb.toString();
        }
        return txt(s4.get("description"));
    }

    /** Lot L3 : representant d'une personne morale saisi en prenom / nom (repPrenom, repNom). */
    private static Object nomComplet(Map<String, Object> o) {
        String n = (txt(o.get("repPrenom")) + " " + txt(o.get("repNom"))).trim();
        return n.isEmpty() ? null : n;
    }

    private static Object premier(Object... candidats) {
        for (Object c : candidats) {
            if (c != null && !String.valueOf(c).isBlank()) return c;
        }
        return null;
    }

    private static String txt(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** L'étape est stockée à plat OU nichée : on lit les deux, comme les écrans. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> etape(Map<String, Object> data, String cle, String niche) {
        Object brut = data.get(cle);
        if (!(brut instanceof Map<?, ?> m)) return Map.of();
        Map<String, Object> plat = (Map<String, Object>) m;
        if (niche == null) return plat;
        Object dedans = plat.get(niche);
        if (!(dedans instanceof Map<?, ?> inner)) return plat;
        Map<String, Object> fusion = new LinkedHashMap<>(plat);
        fusion.putAll((Map<String, Object>) inner);
        return fusion;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sousCarte(Map<String, Object> parent, String cle) {
        Object o = parent.get(cle);
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> liste(Map<String, Object> data, String etape, String cle) {
        Object brut = data.get(etape);
        if (!(brut instanceof Map<?, ?> m)) return List.of();
        Object l = ((Map<String, Object>) m).get(cle);
        return l instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    /** Compteur mutable — les lambdas de projection ne peuvent pas incrémenter un int local. */
    private static final class Compteur {
        int valeur;
    }
}
