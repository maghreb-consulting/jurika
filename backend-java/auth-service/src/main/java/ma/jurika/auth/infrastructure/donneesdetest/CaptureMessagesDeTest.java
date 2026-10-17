package ma.jurika.auth.infrastructure.donneesdetest;

import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.SmsSender;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recette R1 : capture des messages envoyes (SMS, courriels) pour les parcours de bout en bout,
 * qui ne peuvent lire ni le code SMS (journal d'auth) ni le courriel (fournisseur reel).
 *
 * <p>Meme double verrou que la route de donnees de test (TestSeedController de dataroom,
 * RouteDonneesDeTestConfig de la passerelle) : absente en profil {@code prod}, et seulement si
 * {@code jurika.test.seed.enabled=true} (JURIKA_TEST_SEED_ENABLED, faux par defaut).
 *
 * <p>Les envois ne sont pas modifies : chaque {@link SmsSender} et {@link EmailSender} est
 * enveloppe : le message est retenu en memoire (les 200 derniers), puis il part comme avant (un
 * relais injoignable n'empeche pas le parcours de lire le message). Seuls
 * les courriels adresses au domaine reserve aux tests ({@value #DOMAINE_DE_TEST}) sont retenus ;
 * la route de lecture (MessagesDeTestController) ne rend un SMS que pour un telephone saisi dans
 * une demande d'essai faite avec une adresse de ce domaine.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "jurika.test.seed.enabled", havingValue = "true", matchIfMissing = false)
public class CaptureMessagesDeTest implements BeanPostProcessor {

    /** Domaine reserve aux tests (aucune boite reelle). */
    public static final String DOMAINE_DE_TEST = "@jurika.test";
    private static final int CAPACITE = 200;

    public record Sms(String telephone, String texte, Instant envoyeLe) {}

    public record Courriel(String adresse, String sujet, String gabarit, Map<String, String> variables,
                           Instant envoyeLe) {}

    private final Deque<Sms> sms = new ArrayDeque<>();
    private final Deque<Courriel> courriels = new ArrayDeque<>();
    private final Clock horloge = Clock.systemUTC();

    public static boolean adresseDeTest(String adresse) {
        return adresse != null && adresse.trim().toLowerCase(Locale.ROOT).endsWith(DOMAINE_DE_TEST);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof SmsSender cible && !(bean instanceof SmsCapture)) {
            return new SmsCapture(cible);
        }
        if (bean instanceof EmailSender cible && !(bean instanceof CourrielCapture)) {
            return new CourrielCapture(cible);
        }
        return bean;
    }

    void retenirSms(String telephone, String texte) {
        synchronized (sms) {
            sms.addLast(new Sms(telephone, texte, horloge.instant()));
            while (sms.size() > CAPACITE) sms.removeFirst();
        }
    }

    void retenirCourriel(String adresse, String sujet, String gabarit, Map<String, Object> variables) {
        if (!adresseDeTest(adresse)) return;
        Map<String, String> texte = new LinkedHashMap<>();
        if (variables != null) {
            variables.forEach((k, v) -> {
                if (v instanceof CharSequence || v instanceof Number || v instanceof Boolean) {
                    texte.put(k, String.valueOf(v));
                }
            });
        }
        synchronized (courriels) {
            courriels.addLast(new Courriel(adresse.trim().toLowerCase(Locale.ROOT), sujet, gabarit,
                    Map.copyOf(texte), horloge.instant()));
            while (courriels.size() > CAPACITE) courriels.removeFirst();
        }
    }

    /** SMS envoyes a ce numero, du plus ancien au plus recent. */
    public List<Sms> smsPour(String telephone) {
        List<Sms> r = new ArrayList<>();
        synchronized (sms) {
            for (Sms s : sms) if (s.telephone().equals(telephone)) r.add(s);
        }
        return r;
    }

    /** Courriels envoyes a cette adresse (domaine de test seulement), du plus ancien au plus recent. */
    public List<Courriel> courrielsPour(String adresse) {
        List<Courriel> r = new ArrayList<>();
        if (!adresseDeTest(adresse)) return r;
        String cle = adresse.trim().toLowerCase(Locale.ROOT);
        synchronized (courriels) {
            for (Courriel c : courriels) if (c.adresse().equals(cle)) r.add(c);
        }
        return r;
    }

    private final class SmsCapture implements SmsSender {
        private final SmsSender cible;

        SmsCapture(SmsSender cible) { this.cible = cible; }

        @Override
        public void send(String phoneE164, String body) {
            retenirSms(phoneE164, body);
            cible.send(phoneE164, body);
        }
    }

    private final class CourrielCapture implements EmailSender {
        private final EmailSender cible;

        CourrielCapture(EmailSender cible) { this.cible = cible; }

        @Override
        public void sendTemplated(String to, String subject, String templateName, Map<String, Object> variables) {
            retenirCourriel(to, subject, templateName, variables);
            cible.sendTemplated(to, subject, templateName, variables);
        }
    }
}
