package ma.jurika.workflow.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L4a (E5, RG-TKT-04) : la charge utile des DOCUMENTS D'ANNULATION, construite par le
 * serveur pour tous les parcours (note d'annulation, lettre de retrait par administration,
 * bordereau de restitution).
 *
 * <p>Ce qu'elle porte :
 * <ul>
 *   <li>les faits du ticket : reference, ouverture, employe en charge, date et motif de
 *       l'annulation, statut atteint (libelle des matrices) ;</li>
 *   <li>les donnees DERIVEES du parcours : famille de l'operation, succursale de societe
 *       etrangere, societe en liquidation, depots en cours, objet du bordereau ;</li>
 *   <li>les demarches interrompues : depots coches sans retrait coche (referentiel du
 *       parcours), completees par les saisies de l'ecran de meme designation ;</li>
 *   <li>les saisies de l'ecran d'annulation, au magasin du ticket, sous leur nom de
 *       variable ;</li>
 *   <li>le dossier, dont ai-service relit l'identite de la societe (fiche).</li>
 * </ul>
 * Aucune valeur n'y est supposee : une donnee absente reste absente et la generation la
 * nomme.
 */
@Service
public class ChargeUtileAnnulation {

    /** Libelles des statuts, tels que les matrices du cabinet les ecrivent (colonne Statut). */
    static final Map<String, String> LIBELLES_STATUT = Map.of(
            "CREATION_TICKET", "Cr\u00e9ation du ticket et collecte d'information",
            "GENERATION_DOCUMENTS", "G\u00e9n\u00e9ration des documents",
            "DEROULEMENT_DEMARCHE", "D\u00e9roulement de la d\u00e9marche",
            "CLOTURE_DOSSIER", "Cl\u00f4ture de dossier");

    /** Familles testees par les modeles ; la modification est leur branche "SINON". */
    static final Map<String, String> FAMILLES = Map.of(
            "DISSOLUTION", "Dissolution et liquidation",
            "LIQUIDATION", "Dissolution et liquidation",
            "PV_AGO", "Comptes et distributions",
            "SUCCURSALE_MA", "Succursale",
            "SUCCURSALE_ETR", "Succursale",
            "FERMETURE_SUCCURSALE", "Succursale",
            "MODIFICATION", "Modification statutaire ou vie sociale");

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");

    private final MagasinVariables magasin;
    private final FaitsDuTicket faitsDuTicket;
    private final EmployeEnCharge employeEnCharge;

    @PersistenceContext
    private EntityManager em;

    public ChargeUtileAnnulation(MagasinVariables magasin, FaitsDuTicket faitsDuTicket, EmployeEnCharge employeEnCharge) {
        this.magasin = magasin;
        this.faitsDuTicket = faitsDuTicket;
        this.employeEnCharge = employeEnCharge;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> construire(UUID workspaceId, UUID ticketId, UUID employeId) {
        employeEnCharge.exiger(workspaceId, ticketId, employeId);
        TenantContext.set(workspaceId);
        FaitsDuTicket.Faits faits = faitsDuTicket.lire(workspaceId, ticketId);
        Map<String, Object> magasinDuTicket = magasin.lirePourGeneration(workspaceId, ticketId);
        return assembler(faits, magasinDuTicket, demarchesSuivies(workspaceId, ticketId), dossierDuTicket(workspaceId, ticketId));
    }

    /** Assemblage pur (teste sans base). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> assembler(FaitsDuTicket.Faits faits, Map<String, Object> magasinDuTicket,
                                         List<Map<String, Object>> demarchesSuivies, UUID dossierId) {
        Map<String, Object> variables = new LinkedHashMap<>(magasinDuTicket == null ? Map.of() : magasinDuTicket);
        // Lot L4d (E6) : reference lisible du dossier (annee, numero, societe), plus celle du ticket.
        putSi(variables, "DOSSIER_NUMERO", faits.dossierReference());
        putSi(variables, "DOSSIER_DATE_OUVERTURE", faits.ouvertLe());
        putSi(variables, "DOSSIER_CHARGE", faits.chargeNom());
        putSi(variables, "DOSSIER_DATE_ANNULATION", faits.annuleLe());
        putSi(variables, "DOSSIER_MOTIF_ANNULATION", faits.motifAnnulation());
        if (faits.statutAnterieur() != null) {
            putSi(variables, "ANNULATION_STATUT_ATTEINT", LIBELLES_STATUT.get(faits.statutAnterieur()));
        }
        String type = faits.type() == null ? "" : faits.type();
        putSi(variables, "ANNULATION_FAMILLE", FAMILLES.get(type));
        if (!type.isEmpty() && !"CREATION".equals(type) && !"IMPORT".equals(type)) {
            variables.put("SUCCURSALE_SOCIETE_ETRANGERE", "SUCCURSALE_ETR".equals(type) ? "oui" : "non");
            variables.put("SOCIETE_EN_LIQUIDATION", "LIQUIDATION".equals(type) ? "oui" : "non");
        }
        // Le bordereau d'une annulation RESTITUE les pieces (emploi "restitution" du parcours).
        variables.put("BORDEREAU_OBJET", "restitution");

        // Demarches interrompues : celles que le ticket a suivies (marquees "_suivie" : l'ecran
        // ne les retire pas), completees par les saisies de l'ecran de meme designation
        // (reference, suite, adresse de l'administration...) ; les autres saisies sont des
        // demarches ajoutees a la main.
        List<Map<String, Object>> saisies = new ArrayList<>(variables.get("DEMARCHES_INTERROMPUES") instanceof List<?> l
                ? (List<Map<String, Object>>) l : List.of());
        List<Map<String, Object>> interrompues = new ArrayList<>();
        for (Map<String, Object> suivie : demarchesSuivies) {
            Map<String, Object> item = new LinkedHashMap<>(suivie);
            Object designation = suivie.get("DEMARCHE_DESIGNATION");
            for (int i = 0; i < saisies.size(); i++) {
                if (designation != null && designation.equals(saisies.get(i).get("DEMARCHE_DESIGNATION"))) {
                    saisies.remove(i).forEach(item::putIfAbsent);
                    break;
                }
            }
            item.put("_suivie", "oui");
            interrompues.add(item);
        }
        saisies.forEach(sa -> interrompues.add(new LinkedHashMap<>(sa)));
        variables.put("DEMARCHES_INTERROMPUES", interrompues);
        variables.put("ANNULATION_DEPOTS_EN_COURS", interrompues.isEmpty() ? "non" : "oui");

        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("variables", variables);
        charge.put("typeTicket", type);
        if (dossierId != null) charge.put("dossierId", dossierId.toString());
        charge.put("societe", Map.of());
        return charge;
    }

    /** Depots coches dont le retrait n'est pas coche, dans l'ordre du referentiel. */
    private List<Map<String, Object>> demarchesSuivies(UUID workspaceId, UUID ticketId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT r.libelle, r.organisme, td.coche_at
                FROM ticket_demarches td
                JOIN demarches_referentiel r ON r.id = td.demarche_id
                WHERE td.ticket_id = ?1 AND td.workspace_id = ?2
                  AND r.formalite_volet = 'DEPOT' AND td.etat = 'COCHEE'
                  AND NOT EXISTS (
                      SELECT 1 FROM ticket_demarches t2 JOIN demarches_referentiel r2 ON r2.id = t2.demarche_id
                      WHERE t2.ticket_id = td.ticket_id AND r2.formalite_volet = 'RETRAIT'
                        AND r2.formalite_code = r.formalite_code AND t2.etat = 'COCHEE')
                ORDER BY r.ordre
                """).setParameter(1, ticketId).setParameter(2, workspaceId).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> it = new LinkedHashMap<>();
            putSi(it, "DEMARCHE_DESIGNATION", r[0] == null ? null : String.valueOf(r[0]));
            putSi(it, "DEMARCHE_ADMINISTRATION", r[1] == null ? null : String.valueOf(r[1]));
            if (r[2] instanceof java.sql.Timestamp ts) {
                it.put("DEMARCHE_DATE_DEPOT", ts.toInstant().atZone(CASABLANCA).toLocalDate().toString());
            } else if (r[2] instanceof java.time.Instant i) {
                it.put("DEMARCHE_DATE_DEPOT", i.atZone(CASABLANCA).toLocalDate().toString());
            } else if (r[2] instanceof java.time.OffsetDateTime o) {
                it.put("DEMARCHE_DATE_DEPOT", o.atZoneSameInstant(CASABLANCA).toLocalDate().toString());
            }
            out.add(it);
        }
        return out;
    }

    private UUID dossierDuTicket(UUID workspaceId, UUID ticketId) {
        List<?> r = em.createNativeQuery("SELECT dossier_id FROM tickets WHERE id = ?1 AND workspace_id = ?2")
                .setParameter(1, ticketId).setParameter(2, workspaceId).getResultList();
        return r.isEmpty() || r.get(0) == null ? null : (UUID) r.get(0);
    }

    private static void putSi(Map<String, Object> m, String k, String v) {
        if (v != null && !v.isBlank()) m.putIfAbsent(k, v);
    }
}
