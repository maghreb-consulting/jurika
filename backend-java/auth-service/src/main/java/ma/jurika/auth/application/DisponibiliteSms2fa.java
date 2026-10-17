package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Recette R2 (2026-10-17) : la double authentification par SMS est-elle ouverte ?
 *
 * <ul>
 *   <li>fournisseur reel configure (SmsSender non simule) : oui, partout ;</li>
 *   <li>envoi simule (LoggerSmsSender, code ecrit dans le journal d'auth avec la marque
 *       SMS-DEV-STUB) : seulement si {@code jurika.sms.simulation=true} (JURIKA_SMS_SIMULATION,
 *       faux par defaut) ET hors profil {@code prod}. Double verrou, comme
 *       RouteDonneesDeTestConfig : en production, la simulation n'est jamais active, meme si la
 *       propriete est levee par erreur ;</li>
 *   <li>sinon : non. L'option SMS n'est ni proposee (setup-options) ni acceptee (choix, envoi,
 *       verification : refus SMS_2FA_INDISPONIBLE).</li>
 * </ul>
 * Le fournisseur reel de production reste A_DECIDER (decision du 2026-10-14 : Brevo SMS a la
 * mise en production) : aucun n'est branche ici.
 */
@Component
public class DisponibiliteSms2fa {

    public static final String CODE_INDISPONIBLE = "SMS_2FA_INDISPONIBLE";

    private static final Logger log = LoggerFactory.getLogger(DisponibiliteSms2fa.class);

    private final boolean disponible;
    private final boolean simule;

    @Autowired
    public DisponibiliteSms2fa(SmsSender smsSender, Environment environnement,
                               @Value("${jurika.sms.simulation:false}") boolean simulationDemandee) {
        boolean envoiSimule = smsSender.simule();
        boolean production = environnement.acceptsProfiles(Profiles.of("prod"));
        boolean simulationPermise = simulationDemandee && !production;
        this.disponible = !envoiSimule || simulationPermise;
        this.simule = envoiSimule && simulationPermise;
        if (envoiSimule && simulationDemandee && production) {
            log.error("jurika.sms.simulation=true ignore en profil prod : double authentification par SMS desactivee");
        }
        log.info("Double authentification par SMS : {}", disponible
                ? (simule ? "ouverte, SIMULEE (environnement de test, code dans le journal SMS-DEV-STUB)" : "ouverte (fournisseur configure)")
                : "desactivee (aucun fournisseur de SMS configure)");
    }

    /** Vrai si l'option SMS peut etre proposee et acceptee. */
    public boolean disponible() {
        return disponible;
    }

    /** Vrai si l'option est ouverte ET simulee (environnement de test). */
    public boolean simule() {
        return simule;
    }

    /** Refus explicite quand l'option SMS n'est pas disponible. */
    public void exiger() {
        if (!disponible) {
            throw new BusinessException(CODE_INDISPONIBLE,
                    "La double authentification par SMS n\u2019est pas disponible : aucun fournisseur de SMS "
                            + "n\u2019est configur\u00e9 sur la plateforme. Utilisez l\u2019application d\u2019authentification, "
                            + "ou un code de r\u00e9cup\u00e9ration pour vous connecter.");
        }
    }
}
