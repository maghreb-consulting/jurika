package ma.jurika.workflow.application;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Isole les effets de bord « best-effort » de la finalisation d'un workflow
 * dans une transaction SÉPARÉE ({@code REQUIRES_NEW}).
 *
 * <p><b>Pourquoi ce bean existe (fix 2026-07-19).</b> À la fin du workflow
 * CREATION, {@link WorkflowUseCases#executeStep} (annoté {@code @Transactional})
 * crée automatiquement l'{@code entreprise_dossier}. L'INSERT peut échouer sur
 * une violation d'unicité (index partiels {@code uq_dossier_workspace_ice_when_present}
 * ou {@code uq_dossier_workspace_raison_alive}, cf. ticket-service V6). Tant que
 * cet INSERT s'exécutait dans la transaction de {@code executeStep} (méthode
 * privée auto-invoquée = aucune sémantique transactionnelle propre), l'échec
 * marquait la transaction courante <i>rollback-only</i>. Le {@code try/catch}
 * best-effort avalait bien l'exception, mais au retour de {@code executeStep} le
 * commit de la transaction externe levait
 * {@link org.springframework.transaction.UnexpectedRollbackException} → 500.
 *
 * <p>En passant par le <b>proxy Spring</b> d'un bean distinct annoté
 * {@code REQUIRES_NEW}, l'INSERT s'exécute dans une transaction fille : s'il
 * échoue, seule cette transaction fille est annulée, la transaction de
 * finalisation ({@code progressRepository.save} → statut {@code TERMINE}) reste
 * committée, et le {@code try/catch} best-effort retrouve son sens.
 *
 * <p>Le lien {@code ticket.dossier_id} et le {@code applyPostCompletion} restent
 * dans la transaction principale : ils mutent la ligne {@code tickets} déjà
 * verrouillée par {@code autoTransitionTicket} (CLOTURE) — les rapatrier dans une
 * transaction fille provoquerait un <i>lock wait</i> sur cette même ligne. Seul
 * l'INSERT dans {@code entreprise_dossiers} (table jamais touchée par la
 * transaction principale) est isolé.
 */
@Service
public class WorkflowFinalizationService {

    /**
     * Référence {@code @Lazy} vers {@link WorkflowUseCases} pour rompre le cycle
     * de dépendances (WorkflowUseCases dépend de ce bean, et inversement). Le
     * proxy paresseux n'est résolu qu'au premier appel — postérieur au démarrage
     * du contexte —, donc aucune récursion d'instanciation.
     */
    private final WorkflowUseCases workflowUseCases;

    public WorkflowFinalizationService(@Lazy WorkflowUseCases workflowUseCases) {
        this.workflowUseCases = workflowUseCases;
    }

    /**
     * Crée l'{@code entreprise_dossier} de fin de workflow CREATION dans une
     * transaction NEUVE. En cas d'échec (violation d'unicité ICE / raison
     * sociale, quota atteint…), l'exception se propage et SEULE cette
     * transaction fille est annulée — la transaction appelante n'est jamais
     * marquée rollback-only. On ne <i>catch</i> pas ici volontairement : laisser
     * l'exception remonter garantit un rollback propre de la transaction fille
     * (et remonte la cause exacte à l'appelant, qui la journalise en WARN).
     *
     * @return l'id du dossier créé, {@code null} si aucune donnée exploitable
     *         (pas de dénomination), ou l'id existant si le ticket est déjà lié
     *         (idempotence).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID createEntrepriseDossierInNewTransaction(UUID workspaceId, UUID ticketId,
                                                        UUID initiatorUserId,
                                                        Map<String, Object> data) {
        return workflowUseCases.createEntrepriseDossier(workspaceId, ticketId, initiatorUserId, data);
    }

    /**
     * Lot DIVERS §C (2026-08-13) — cree (ou reutilise) le dossier de la <b>societe mere
     * ETRANGERE</b> du workflow SUCCURSALE_ETR, dans une transaction NEUVE.
     *
     * <p>Meme raison d'etre que la methode ci-dessus : l'INSERT porte sur
     * {@code entreprise_dossiers}, protegee par l'index unique partiel
     * {@code uq_dossier_workspace_raison_alive} (workspace + raison sociale, statuts
     * vivants). Une mere etrangere homonyme d'une societe marocaine deja au dossier
     * violerait cet index ; sans {@code REQUIRES_NEW}, la transaction de l'etape
     * entiere serait marquee <i>rollback-only</i> et le commit exploserait en 500 —
     * alors que le bon comportement est de REFUSER l'etape avec un message clair.
     *
     * <p>Appele des la <b>validation de l'etape 1</b> (et non a la finalisation) :
     * la Data Room de la mere doit exister avant l'etape « Pieces jointes », sinon les
     * depots n'ont aucun dossier de destination.
     *
     * @return l'id du dossier mere (existant ou cree), ou {@code null} si la
     *         denomination est absente (rien d'exploitable).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID createOrReuseDossierMereEtrangereInNewTransaction(UUID workspaceId,
                                                                  Map<String, Object> step1Data) {
        return workflowUseCases.resolveOrCreateDossierMereEtrangere(workspaceId, step1Data);
    }
}
