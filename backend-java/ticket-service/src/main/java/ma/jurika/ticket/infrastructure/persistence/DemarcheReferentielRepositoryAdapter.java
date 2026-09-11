package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.model.DelaiUnite;
import ma.jurika.ticket.domain.model.FormaliteVolet;
import ma.jurika.ticket.domain.model.Demarche;
import ma.jurika.ticket.domain.model.JustificatifAttendu;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.DemarcheReferentielRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class DemarcheReferentielRepositoryAdapter implements DemarcheReferentielRepository {

    private final DemarcheReferentielJpaRepository referentiel;
    private final DemarcheJustificatifJpaRepository justificatifs;

    public DemarcheReferentielRepositoryAdapter(DemarcheReferentielJpaRepository referentiel,
                                                 DemarcheJustificatifJpaRepository justificatifs) {
        this.referentiel = referentiel;
        this.justificatifs = justificatifs;
    }

    @Override
    public List<Demarche> findByWorkflow(String workflowType) {
        return hydrate(referentiel.findByWorkflowTypeAndActifIsTrueOrderByOrdreAsc(workflowType));
    }

    @Override
    public List<Demarche> findByWorkflowAndStatut(String workflowType, TicketStatut statut) {
        return hydrate(referentiel.findByWorkflowTypeAndStatutTicketAndActifIsTrueOrderByOrdreAsc(
                workflowType, statut.name()));
    }

    @Override
    public Optional<Demarche> findByWorkflowAndOrdre(String workflowType, int ordre) {
        return referentiel.findByWorkflowTypeAndOrdreAndActifIsTrue(workflowType, (short) ordre)
                .map(e -> hydrate(List.of(e)).get(0));
    }

    /**
     * Charge les justificatifs attendus en UNE requete pour tout le lot, plutot
     * qu'une par demarche : la liste complete du parcours fait 51 lignes et se
     * lit a chaque affichage de l'onglet.
     */
    private List<Demarche> hydrate(List<DemarcheReferentielEntity> entities) {
        if (entities.isEmpty()) return List.of();
        List<UUID> ids = entities.stream().map(DemarcheReferentielEntity::getId).toList();
        Map<UUID, List<JustificatifAttendu>> parDemarche =
                justificatifs.findByDemarcheIdInOrderByAlternativeGroupeAsc(ids).stream()
                        .collect(Collectors.groupingBy(
                                DemarcheJustificatifEntity::getDemarcheId,
                                Collectors.mapping(j -> new JustificatifAttendu(
                                        j.getAlternativeGroupe(), j.getDocumentType(), j.getLibelle()),
                                        Collectors.toList())));

        List<Demarche> out = new ArrayList<>(entities.size());
        for (DemarcheReferentielEntity e : entities) {
            out.add(new Demarche(
                    e.getId(),
                    e.getWorkflowType(),
                    e.getOrdre(),
                    e.getPhaseCode(),
                    e.getPhaseLibelle(),
                    e.getLibelle(),
                    TicketStatut.valueOf(e.getStatutTicket()),
                    e.getActeur(),
                    e.getOrganisme(),
                    "O".equals(e.getObligatoire()),
                    e.getConditionApplication(),
                    e.getPiecesEntrantes(),
                    e.getDocumentProduit(),
                    e.getJustificatifsTexte(),
                    e.getModeleJurika(),
                    e.getDelai(),
                    e.getCoutIndicatif(),
                    e.getVariablesAlimentees(),
                    e.getDelaiValeur() == null ? null : e.getDelaiValeur().intValue(),
                    e.getDelaiUnite() == null ? null : DelaiUnite.valueOf(e.getDelaiUnite()),
                    e.getDelaiReferenceOrdre() == null ? null : e.getDelaiReferenceOrdre().intValue(),
                    e.getDelaiReferenceDonnee(),
                    e.getFormaliteCode(),
                    e.getFormaliteVolet() == null ? null
                            : FormaliteVolet.valueOf(e.getFormaliteVolet()),
                    parDemarche.getOrDefault(e.getId(), List.of())));
        }
        return out;
    }
}
