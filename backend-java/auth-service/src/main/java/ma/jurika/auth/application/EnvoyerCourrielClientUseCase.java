package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.common.courriel.CourrielClientEvenement;
import ma.jurika.common.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L5 (E9, RG-COM-09) : avertit le CLIENT par courriel d'une requete du
 * cabinet, d'un document mis a sa disposition ou de la reponse a sa demande.
 *
 * <p>Le courriel part de l'adresse unique de JURIKA ({@code jurika.email.from})
 * et nomme le cabinet a l'origine du message (sujet et corps). Le destinataire
 * est verifie : compte CLIENT, actif, DU cabinet de l'evenement ; sinon refus
 * nomme (exception), jamais d'envoi a une autre personne.
 *
 * <p>Le workspace courant ({@code TenantContext}) doit etre pose par l'appelant
 * avant d'entrer dans la transaction.
 */
@Service
public class EnvoyerCourrielClientUseCase {

    private static final Logger log = LoggerFactory.getLogger(EnvoyerCourrielClientUseCase.class);

    static final String GABARIT = "client-notification";

    private final UserRepository users;
    private final WorkspaceRepository workspaces;
    private final EmailSender emailSender;
    private final String baseUrl;

    public EnvoyerCourrielClientUseCase(UserRepository users, WorkspaceRepository workspaces,
                                        EmailSender emailSender,
                                        @Value("${jurika.frontend.login-url:http://localhost:5173/login}") String loginUrl) {
        this.users = users;
        this.workspaces = workspaces;
        this.emailSender = emailSender;
        this.baseUrl = racine(loginUrl);
    }

    /** Refus d'un evenement inexploitable (destinataire, cabinet, type) : jamais rejoue. */
    public static class CourrielClientRefuse extends RuntimeException {
        public CourrielClientRefuse(String message) {
            super(message);
        }
    }

    /** Textes par type d'evenement : objet du courriel, titre, phrase d'annonce, bouton. */
    record Textes(String sujet, String titre, String annonce, String bouton) {}

    @Transactional(readOnly = true)
    public void envoyer(CourrielClientEvenement e) {
        if (e == null || e.workspaceId() == null || e.clientId() == null || e.type() == null) {
            throw new CourrielClientRefuse("Evenement incomplet : " + e);
        }
        User client = users.findById(e.clientId())
                .filter(u -> e.workspaceId().equals(u.workspaceId()))
                .orElseThrow(() -> new CourrielClientRefuse("Client " + e.clientId()
                        + " introuvable dans le cabinet " + e.workspaceId()));
        if (client.role() != Role.CLIENT) {
            throw new CourrielClientRefuse("Le destinataire " + e.clientId() + " n'est pas un client");
        }
        if (!client.active()) {
            throw new CourrielClientRefuse("Le compte client " + e.clientId() + " est desactive");
        }
        String adresse = client.contactEmail();
        if (adresse == null || adresse.isBlank()) {
            throw new CourrielClientRefuse("Le client " + e.clientId() + " n'a pas d'adresse enregistree");
        }
        Workspace cabinet = workspaces.findById(e.workspaceId())
                .orElseThrow(() -> new CourrielClientRefuse("Cabinet " + e.workspaceId() + " introuvable"));
        String nomCabinet = cabinet.name();

        Textes t = textes(e, nomCabinet);
        Map<String, Object> vars = new HashMap<>();
        vars.put("cabinet", nomCabinet);
        vars.put("firstName", client.firstName() == null ? "" : client.firstName());
        vars.put("titre", t.titre());
        vars.put("annonce", t.annonce());
        vars.put("raisonSociale", e.raisonSociale());
        vars.put("objet", e.objet());
        vars.put("bouton", t.bouton());
        vars.put("lienUrl", baseUrl + (e.lien() == null ? "/" : e.lien()));
        vars.put("loginEmail", client.loginEmail());
        vars.put("contactEmail", adresse);
        emailSender.sendTemplated(adresse, t.sujet(), GABARIT, vars);
        log.info("Courriel client {} envoye (cabinet {}, client {})", e.type(), e.workspaceId(), e.clientId());
    }

    static Textes textes(CourrielClientEvenement e, String cabinet) {
        String dossier = e.raisonSociale() == null || e.raisonSociale().isBlank()
                ? "votre dossier" : "le dossier " + e.raisonSociale();
        return switch (e.type()) {
            case CourrielClientEvenement.TYPE_REQUETE_CABINET -> new Textes(
                    cabinet + " : nouvelle requête sur " + dossier,
                    "Une requête de votre cabinet",
                    "Votre cabinet " + cabinet + " vous adresse une requête sur " + dossier
                            + ". Merci d’y répondre depuis votre espace JURIKA.",
                    "Répondre à la requête");
            case CourrielClientEvenement.TYPE_COMPLEMENT_REQUETE -> new Textes(
                    cabinet + " : complément demandé sur " + dossier,
                    "Un complément vous est demandé",
                    "Votre cabinet " + cabinet + " a examiné votre réponse et vous demande un complément "
                            + "sur " + dossier + ".",
                    "Compléter ma réponse");
            case CourrielClientEvenement.TYPE_DOCUMENT_DISPONIBLE -> new Textes(
                    cabinet + " : nouveau document sur " + dossier,
                    "Un document est à votre disposition",
                    "Votre cabinet " + cabinet + " a mis à votre disposition un document sur " + dossier
                            + ". Vous pouvez le consulter dans votre Data Room.",
                    "Consulter ma Data Room");
            case CourrielClientEvenement.TYPE_REPONSE_DEMANDE -> new Textes(
                    cabinet + " : votre demande a été traitée",
                    "Votre demande a été traitée",
                    "Votre cabinet " + cabinet + " a traité votre demande sur " + dossier + ".",
                    "Voir mes demandes");
            default -> throw new CourrielClientRefuse("Type de courriel client inconnu : " + e.type());
        };
    }

    /** {@code https://hote/login} devient {@code https://hote}. */
    static String racine(String loginUrl) {
        if (loginUrl == null || loginUrl.isBlank()) return "";
        String u = loginUrl.trim();
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        if (u.endsWith("/login")) u = u.substring(0, u.length() - "/login".length());
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    /** Pour les journaux : identite minimale de l'evenement (jamais l'adresse). */
    public static String resume(CourrielClientEvenement e) {
        if (e == null) return "null";
        UUID ws = e.workspaceId();
        return e.type() + " cabinet=" + ws + " client=" + e.clientId() + " dossier=" + e.dossierId();
    }
}
