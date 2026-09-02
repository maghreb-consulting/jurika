package ma.jurika.ticket.application;

import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.DossierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Fix 2026-06-07 (BUG 2) — Helper transactionnel pour
 * l'idempotence de creation de dossier.
 *
 * <p>Pourquoi un bean separe : lors d'un double-submit reel
 * (Promise.all cote front), 2 INSERT atterrissent presque
 * simultanement. Le 2nd peut prendre une
 * {@code DataIntegrityViolationException} (unique partial index
 * defini par Flyway V6). Si on tente le retry dans la MEME
 * transaction outer, Hibernate refuse car la transaction est
 * marquee rollback-only -- on ne peut plus rien y SELECT/INSERT
 * jusqu'au rollback du parent, ce qui condamne le ticket lui-meme.
 *
 * <p>Solution standard : tout le bloc {@code findAlive → save → retry}
 * tourne dans une transaction SEPAREE ({@code REQUIRES_NEW}) qui peut
 * rollback sans impacter la transaction du ticket. Le bean est
 * volontairement separe de {@code CreateTicketUseCase} car l'AOP
 * Spring n'intercepte PAS les appels intra-classe.
 */
@Service
public class DossierIdempotenceLookup {

    private static final Logger log = LoggerFactory.getLogger(DossierIdempotenceLookup.class);

    private final DossierRepository dossierRepository;

    public DossierIdempotenceLookup(DossierRepository dossierRepository) {
        this.dossierRepository = dossierRepository;
    }

    /**
     * Idempotence du dossier : retourne l'existant si trouve, sinon
     * cree ; tout dans une transaction NEUVE pour isoler les eventuelles
     * collisions d'index unique de la transaction outer (ticket).
     *
     * Strategie :
     *  1. findAlive (workspaceId, lower(raisonSociale)) : si present, return.
     *  2. Sinon try save. Si DataIntegrityViolationException (race
     *     concurrente), re-lire findAlive dans une TX encore separee.
     *  3. Si toujours rien, propager l'exception originale (cas
     *     impossible en pratique sauf bug).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EntrepriseDossier getOrCreateAlive(UUID workspaceId, String raisonSociale,
                                              FormeJuridique forme, TicketType type,
                                              UUID responsableId) {
        var existing = dossierRepository.findAliveByRaisonSociale(workspaceId, raisonSociale);
        if (existing.isPresent()) {
            // Dossier reutilise (idempotence / double-submit) : on NE touche PAS
            // a son responsable existant (sinon un IMPORT reprendrait un dossier
            // deja gere par un autre employe).
            log.info("Idempotence : reuse dossier {} ({}) deja existant workspace {}",
                    existing.get().id(), existing.get().formeJuridique(), workspaceId);
            return existing.get();
        }
        EntrepriseDossier stub = type == TicketType.CREATION
                ? EntrepriseDossier.creationStub(workspaceId, raisonSociale, forme, responsableId)
                : EntrepriseDossier.importStub(workspaceId, raisonSociale, forme, responsableId);
        try {
            EntrepriseDossier saved = dossierRepository.save(stub);
            log.info("Auto-create dossier {} ({}) workspace {}",
                    saved.id(), forme, workspaceId);
            return saved;
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Course concurrente : un autre thread/process a deja insere
            // entre notre findAlive et notre save. Le rollback automatique
            // se fait au sortir de cette transaction (REQUIRES_NEW). On
            // appelle findAliveInOtherTx pour re-lire APRES rollback.
            log.warn("Idempotence race detectee workspace {} raison {} -- on retombe sur dossier existant",
                    workspaceId, raisonSociale);
            throw ex;  // bubble vers le wrapper qui fera findAliveInOtherTx
        }
    }

    /**
     * Lecture stricte du dossier vivant dans une transaction NEUVE.
     * Utilise apres rollback de {@link #getOrCreateAlive(UUID, String, FormeJuridique, TicketType)}
     * pour re-trouver le dossier insere par le concurrent.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public java.util.Optional<EntrepriseDossier> findAliveInNewTx(UUID workspaceId, String raisonSociale) {
        return dossierRepository.findAliveByRaisonSociale(workspaceId, raisonSociale);
    }
}
