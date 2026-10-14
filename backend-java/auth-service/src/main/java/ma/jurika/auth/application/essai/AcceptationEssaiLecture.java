package ma.jurika.auth.application.essai;

import ma.jurika.auth.infrastructure.essai.EssaiJdbc;
import ma.jurika.common.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lot L7 (E13) : acceptation des conditions d'utilisation de l'essai gratuit (piece D,
 * RG-CTR-10) du cabinet, lue par billing-service a l'acceptation du contrat d'abonnement pour
 * la mention de l'exemplaire. Lecture la plus etroite possible : version et date d'acceptation,
 * rien d'autre (ni texte, ni IP, ni appareil).
 *
 * <p>Le cabinet n'a pas eu d'essai : reponse explicite {@code essai = false} (jamais un corps
 * vide, qu'un intermediaire pourrait produire). Une donnee incoherente (cabinet inscrit au
 * registre des essais sans acceptation des conditions, ou plusieurs acceptations) est un refus
 * nomme (409), jamais une mention omise ou choisie au hasard.
 */
@Service
public class AcceptationEssaiLecture {

    /** {@code essai = false} : version et date nulles. */
    public record Resultat(boolean essai, String version, Instant accepteesLe) {
        static Resultat aucun() { return new Resultat(false, null, null); }
    }

    private final EssaiJdbc essais;

    public AcceptationEssaiLecture(EssaiJdbc essais) {
        this.essais = essais;
    }

    @Transactional(readOnly = true)
    public Resultat lire(UUID workspaceId) {
        List<EssaiJdbc.AcceptationConditions> acceptations = essais.acceptationsConditions(workspaceId);
        if (acceptations.size() > 1) {
            throw new ConflictException("Plusieurs acceptations des conditions de l'essai sont enregistr\u00e9es "
                    + "pour ce cabinet : la mention du contrat ne peut pas \u00eatre \u00e9tablie.");
        }
        if (acceptations.isEmpty()) {
            if (essais.auRegistre(workspaceId)) {
                throw new ConflictException("Le cabinet a eu un essai gratuit, mais l'acceptation de ses conditions "
                        + "est introuvable : la mention du contrat ne peut pas \u00eatre \u00e9tablie.");
            }
            return Resultat.aucun();
        }
        EssaiJdbc.AcceptationConditions a = acceptations.get(0);
        return new Resultat(true, a.version(), a.accepteesLe());
    }
}
