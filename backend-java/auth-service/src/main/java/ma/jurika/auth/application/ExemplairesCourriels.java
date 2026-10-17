package ma.jurika.auth.application;

import ma.jurika.common.courriel.CourrielClientEvenement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recette R3 : un exemplaire de chaque courriel d'auth-service soumis a la recette (bienvenue,
 * identifiants d'un employe, identifiants d'un client, reinitialisation du mot de passe,
 * notification au client), rendu avec des donnees FICTIVES explicites : cabinet "Cabinet
 * Exemple (fictif)", identifiants et liens inoperants (domaine reserve exemple.invalid).
 * L'objet de chaque exemplaire commence par "[Exemplaire fictif]".
 *
 * <p>Sert la route de test {@code POST /api/v1/test/courriels/auth} (hors production) et les tests
 * de structure des courriels. Aucune mention de forfait (planLabel absent) : les textes de forfait
 * sont traites a part.
 */
public final class ExemplairesCourriels {

    public record Exemplaire(String code, String objet, String gabarit, Map<String, Object> variables) {}

    static final String PREFIXE = "[Exemplaire fictif] ";
    static final String SITE = "https://exemple.invalid";
    static final String CABINET = "Cabinet Exemple (fictif)";
    static final String CODE = "JUR-EXEMPLE";

    private ExemplairesCourriels() {}

    public static List<Exemplaire> tous() {
        return List.of(bienvenue(), identifiantsEmploye(), identifiantsClient(), reinitialisation(), notificationClient());
    }

    private static Map<String, Object> communes(String prenom, String identifiant, String adresse) {
        Map<String, Object> v = new HashMap<>();
        v.put("brand", "JURIKA");
        v.put("firstName", prenom);
        v.put("workspaceCode", CODE);
        v.put("email", identifiant);
        v.put("loginEmail", identifiant);
        v.put("contactEmail", adresse);
        return v;
    }

    static Exemplaire bienvenue() {
        Map<String, Object> v = communes("Karim", "karim.exemple@exemple.invalid", "karim.perso@exemple.invalid");
        v.put("temporaryPassword", "Exemple-Fictif-1");
        v.put("verificationUrl", SITE + "/verify-email?token=EXEMPLE-FICTIF");
        v.put("ttlHours", "24");
        v.put("subject", "Activez votre compte JURIKA");
        v.put("planLabel", null);
        return new Exemplaire("bienvenue", PREFIXE + "Bienvenue sur JURIKA - activez votre compte", "welcome", v);
    }

    static Exemplaire identifiantsEmploye() {
        Map<String, Object> v = communes("Salma", "salma.exemple@exemple.invalid", "salma.perso@exemple.invalid");
        v.put("workspaceName", CABINET);
        v.put("roleLabel", "Employ\u00e9");
        v.put("temporaryPassword", "Exemple-Fictif-2");
        v.put("loginUrl", SITE + "/login?workspace=" + CODE);
        v.put("subject", "Invitation \u00e0 rejoindre " + CABINET + " sur JURIKA");
        return new Exemplaire("identifiants-employe", PREFIXE + "Invitation \u00e0 rejoindre " + CABINET + " sur JURIKA",
                "employe-invite", v);
    }

    static Exemplaire identifiantsClient() {
        Map<String, Object> v = communes("Youssef", "youssef.exemple@exemple.invalid", "youssef.perso@exemple.invalid");
        v.put("cabinet", CABINET);
        v.put("raisonSociale", "Atlas Exemple SARL (fictive)");
        v.put("temporaryPassword", "Exemple-Fictif-3");
        v.put("loginUrl", SITE + "/login?workspace=" + CODE);
        String sujet = InviteClientUseCase.sujetInvitation(CABINET);
        v.put("subject", sujet);
        return new Exemplaire("identifiants-client", PREFIXE + sujet, "client-invite", v);
    }

    static Exemplaire reinitialisation() {
        Map<String, Object> v = communes("Karim", "karim.exemple@exemple.invalid", "karim.perso@exemple.invalid");
        v.put("resetUrl", SITE + "/reset-password?token=EXEMPLE-FICTIF");
        v.put("ttlMinutes", "30");
        v.put("ipAddress", "192.0.2.10");
        v.put("userAgent", "Navigateur d'exemple (fictif)");
        v.put("subject", "R\u00e9initialisez votre mot de passe JURIKA");
        return new Exemplaire("reinitialisation", PREFIXE + "R\u00e9initialisez votre mot de passe JURIKA",
                "password-reset-link", v);
    }

    static Exemplaire notificationClient() {
        CourrielClientEvenement e = new CourrielClientEvenement(CourrielClientEvenement.TYPE_DOCUMENT_DISPONIBLE,
                null, null, null, "Atlas Exemple SARL (fictive)", "Statuts mis \u00e0 jour (exemple)", "/client/dataroom");
        EnvoyerCourrielClientUseCase.Textes t = EnvoyerCourrielClientUseCase.textes(e, CABINET);
        Map<String, Object> v = communes("Youssef", "youssef.exemple@exemple.invalid", "youssef.perso@exemple.invalid");
        v.put("cabinet", CABINET);
        v.put("titre", t.titre());
        v.put("annonce", t.annonce());
        v.put("raisonSociale", e.raisonSociale());
        v.put("objet", e.objet());
        v.put("bouton", t.bouton());
        v.put("lienUrl", SITE + e.lien());
        return new Exemplaire("notification-client", PREFIXE + t.sujet(), EnvoyerCourrielClientUseCase.GABARIT, v);
    }
}
