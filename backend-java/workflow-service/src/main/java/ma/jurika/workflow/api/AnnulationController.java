package ma.jurika.workflow.api;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.TenantContext;
import ma.jurika.workflow.application.ChargeUtileAnnulation;
import ma.jurika.workflow.application.EmployeEnCharge;
import ma.jurika.workflow.application.MagasinVariables;
import ma.jurika.workflow.domain.model.VariableDuDossier.Origine;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lot L4a (E5, RG-TKT-04) : les documents d'annulation d'un ticket, tous parcours.
 *
 * <p>L'ecran lit ici ce que la plateforme sait deja (faits du ticket, statut atteint,
 * famille, demarches interrompues) et y enregistre les saisies propres a l'annulation
 * (origine, etat de la decision sociale, objet du retrait, adresse de chaque
 * administration...). Les saisies vont au magasin du ticket, sous leur nom de variable ;
 * elles ne s'ecrivent que sur un ticket ANNULE, par l'employe en charge.
 */
@RestController
@RequestMapping("/api/v1/workflows")
public class AnnulationController {

    private static final Pattern NOM_VARIABLE = Pattern.compile("[A-Z][A-Z0-9_]{1,79}");

    private final ChargeUtileAnnulation chargeAnnulation;
    private final MagasinVariables magasin;
    private final EmployeEnCharge employeEnCharge;

    @PersistenceContext
    private EntityManager em;

    public AnnulationController(ChargeUtileAnnulation chargeAnnulation, MagasinVariables magasin,
                                EmployeEnCharge employeEnCharge) {
        this.chargeAnnulation = chargeAnnulation;
        this.magasin = magasin;
        this.employeEnCharge = employeEnCharge;
    }

    /**
     * Saisies de l'ecran d'annulation : variables simples, demarches interrompues (rang par
     * rang) et pieces restituees (bordereau).
     */
    public record Saisies(Map<String, String> variables, List<Map<String, String>> demarches,
                          List<Map<String, String>> pieces) {}

    @GetMapping("/{ticketId}/annulation")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    public Map<String, Object> lire(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID ticketId) {
        return chargeAnnulation.construire(actor.workspaceId(), ticketId, actor.userId());
    }

    @PutMapping("/{ticketId}/annulation/saisies")
    @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
    @Transactional
    public Map<String, Object> enregistrer(@AuthenticationPrincipal AuthenticatedUser actor,
                                           @PathVariable UUID ticketId, @RequestBody Saisies saisies) {
        UUID ws = actor.workspaceId();
        employeEnCharge.exiger(ws, ticketId, actor.userId());
        TenantContext.set(ws);
        List<?> statut = em.createNativeQuery("SELECT statut FROM tickets WHERE id = ?1 AND workspace_id = ?2")
                .setParameter(1, ticketId).setParameter(2, ws).getResultList();
        if (statut.isEmpty() || !"ANNULE".equals(String.valueOf(statut.get(0)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Les documents d\u2019annulation se pr\u00e9parent sur un ticket annul\u00e9.");
        }
        if (saisies.variables() != null) {
            saisies.variables().forEach((nom, valeur) -> {
                exigerNom(nom);
                magasin.poser(ws, ticketId, nom, valeur, Origine.SAISIE, actor.userId(), "annulation");
            });
        }
        if (saisies.demarches() != null) {
            magasin.poserBoucle(ws, ticketId, "DEMARCHES_INTERROMPUES", occurrences(saisies.demarches()),
                    Origine.SAISIE, actor.userId(), "annulation");
        }
        if (saisies.pieces() != null) {
            magasin.poserBoucle(ws, ticketId, "PIECES_REMISES", occurrences(saisies.pieces()),
                    Origine.SAISIE, actor.userId(), "annulation");
        }
        return chargeAnnulation.construire(ws, ticketId, actor.userId());
    }

    private static List<Map<String, String>> occurrences(List<Map<String, String>> lignes) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> d : lignes) {
            Map<String, String> it = new LinkedHashMap<>();
            d.forEach((nom, valeur) -> {
                exigerNom(nom);
                it.put(nom, valeur);
            });
            out.add(it);
        }
        return out;
    }

    private static void exigerNom(String nom) {
        if (nom == null || !NOM_VARIABLE.matcher(nom).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nom de donn\u00e9e invalide : " + nom);
        }
    }
}
