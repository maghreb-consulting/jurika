package ma.jurika.dataroom.application;

import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.TicketViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.TicketViewJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Lot DIVERS §A (2026-08-13) — SOURCE UNIQUE de la regle « Data Room en lecture
 * seule quand la societe n'est plus vivante ».
 *
 * <p>Jusqu'ici la regle etait dupliquee a l'identique dans plusieurs services
 * de depot — chacun avec sa propre constante
 * {@code ARCHIVED_DOSSIER_STATUS} — et <b>absente</b> du Dossier Juridique et des
 * demandes / requetes client. Concretement : a la finalisation d'une DISSOLUTION
 * (dossier -> {@code DISSOUTE}) on pouvait encore televerser, remplacer, restaurer
 * et supprimer des documents juridiques, et ouvrir de nouvelles demandes.
 *
 * <p>Ce garde centralise la regle et l'applique a TOUS les chemins d'ecriture :
 * <ul>
 *   <li><b>Interdit</b> : upload, nouvelle version, restauration, suppression,
 *       depot de pieces, nouvelle demande / requete client ;</li>
 *   <li><b>Autorise</b> : consultation, apercu, telechargement, export ZIP
 *       (archives juridiques : elles doivent rester accessibles) ;</li>
 *   <li><b>Derogation</b> : les documents produits par le workflow
 *       <b>LIQUIDATION</b> (PV de cloture, rapport du liquidateur, annonce legale
 *       de cloture) doivent pouvoir etre deposes sur le dossier <i>deja dissous</i> —
 *       c'est meme leur seul dossier de destination possible. La derogation est
 *       adossee au <b>ticket</b> porteur du depot ({@code ticket.type =
 *       LIQUIDATION}), verifiee cote serveur : le front ne peut pas se l'octroyer.</li>
 * </ul>
 *
 * <p><b>Reversibilite.</b> La regle est purement derivee de
 * {@code entreprise_dossiers.statut} : aucun drapeau n'est persiste. Si le dossier
 * repasse ACTIVE, la Data Room redevient ecrivable sans action corrective.
 *
 * <p><b>Defense-in-depth multi-tenant.</b> Toutes les lectures passent par
 * {@code workspace_id} explicite : la RLS n'est pas fiable (le role applicatif a
 * BYPASSRLS sous le conteneur Postgres officiel).
 */
@Component
public class DossierArchiveGuard {

    /**
     * Statuts de societe qui figent la Data Room. {@code DISSOUTE} est le cas
     * nominal du §A ; les trois autres etaient deja traites par les services
     * comptable / fiscal / depots et sont conserves a l'identique.
     */
    public static final Set<String> ARCHIVED_STATUS = Set.of(
            "DISSOUTE", "EN_LIQUIDATION", "LIQUIDEE", "RADIE");

    /**
     * Types de ticket autorises a ECRIRE sur un dossier archive. Seule la
     * LIQUIDATION est concernee : elle depose ses actes de cloture sur la societe
     * dissoute. Tout autre cas legitime devra etre ajoute ici explicitement.
     */
    private static final Set<String> WRITE_ALLOWED_TICKET_TYPES = Set.of("LIQUIDATION");

    /**
     * Statuts de ticket consideres « en cours » pour la derogation. Les trois
     * statuts du parcours anterieurs a la cloture (guide cabinet v2, onglet 2).
     */
    private static final Set<String> OPEN_TICKET_STATUS =
            Set.of("CREATION_TICKET", "GENERATION_DOCUMENTS", "DEROULEMENT_DEMARCHE");

    private final DossierViewJpaRepository dossiers;
    private final TicketViewJpaRepository tickets;

    public DossierArchiveGuard(DossierViewJpaRepository dossiers,
                               TicketViewJpaRepository tickets) {
        this.dossiers = dossiers;
        this.tickets = tickets;
    }

    /**
     * @return le statut archivant du dossier ({@code DISSOUTE}, {@code LIQUIDEE}…)
     *         ou {@code null} si la Data Room est ecrivable. Renvoie {@code null}
     *         quand le dossier est inconnu : l'appelant a ses propres 404 / scoping,
     *         ce garde ne doit pas les prendre de vitesse.
     */
    public String archivedStatusOf(UUID dossierId) {
        if (dossierId == null) return null;
        String statut = dossiers.findById(dossierId)
                .map(DossierViewEntity::getStatut).orElse(null);
        return statut != null && ARCHIVED_STATUS.contains(statut) ? statut : null;
    }

    /** {@code true} si la Data Room du dossier est en lecture seule. */
    public boolean isReadOnly(UUID dossierId) {
        return archivedStatusOf(dossierId) != null;
    }

    /**
     * Refuse toute ecriture sur un dossier archive. Aucune derogation : a utiliser
     * pour les modifications / suppressions / demandes, qui restent interdites
     * meme pendant la liquidation.
     */
    public void assertWritable(UUID dossierId) {
        assertWritable(dossierId, null);
    }

    /**
     * Refuse l'ecriture sur un dossier archive, sauf si {@code ticketId} designe un
     * ticket de LIQUIDATION du meme workspace portant sur ce dossier (depot des
     * actes de cloture sur la societe dissoute).
     *
     * @param ticketId ticket porteur de l'ecriture, ou {@code null}
     */
    public void assertWritable(UUID dossierId, UUID ticketId) {
        String statut = archivedStatusOf(dossierId);
        if (statut == null) {
            return;
        }
        if (isLiquidationDeposit(dossierId, ticketId)) {
            return;
        }
        throw new ValidationException(
                "DOSSIER_ARCHIVED_READ_ONLY : la societe est " + statut
                        + ". La Data Room est en lecture seule (archive legale) : "
                        + "consultation et telechargement restent possibles, "
                        + "mais plus aucun ajout, remplacement ni suppression.");
    }

    /**
     * Derogation LIQUIDATION, verifiee cote serveur. Le ticket doit :
     * appartenir au workspace courant, etre de type {@code LIQUIDATION}, et cibler
     * le dossier ecrit (un ticket sans {@code dossier_id} est accepte : le lien
     * ticket -> dossier n'est pose qu'a la finalisation du workflow, or les
     * documents sont deposes AVANT).
     */
    private boolean isLiquidationDeposit(UUID dossierId, UUID ticketId) {
        if (ticketId == null) {
            return false;
        }
        UUID ws = TenantContext.get();
        if (ws == null) {
            return false;
        }
        TicketViewEntity t = tickets.findByWorkspaceIdAndId(ws, ticketId).orElse(null);
        if (t == null || !WRITE_ALLOWED_TICKET_TYPES.contains(t.getType())) {
            return false;
        }
        return t.getDossierId() == null || t.getDossierId().equals(dossierId);
    }

    /**
     * Variante de {@link #assertWritable(UUID, UUID)} pour les chemins d'ecriture
     * qui n'ont AUCUN ticket sous la main — typiquement l'archivage OCR d'une CIN
     * ({@code DataroomIdentityArchiver}), appele depuis un endpoint d'extraction
     * qui ne transporte pas de {@code ticketId}.
     *
     * <p>La derogation y est adossee au dossier : un ticket LIQUIDATION encore
     * ouvert (NOUVEAU / EN_COURS) sur cette societe autorise l'ecriture. C'est
     * volontairement un peu plus large que la derogation par ticket : c'est le
     * prix a payer pour ne pas faire transiter un ticketId a travers toute la
     * chaine d'extraction d'identite.
     */
    public void assertWritableForLiquidationCapableWrite(UUID dossierId) {
        String statut = archivedStatusOf(dossierId);
        if (statut == null || hasOpenLiquidationTicket(dossierId)) {
            return;
        }
        throw new ValidationException(
                "DOSSIER_ARCHIVED_READ_ONLY : la societe est " + statut
                        + ". La Data Room est en lecture seule (archive legale).");
    }

    /** {@code true} si un workflow LIQUIDATION est encore ouvert sur ce dossier. */
    private boolean hasOpenLiquidationTicket(UUID dossierId) {
        if (dossierId == null) return false;
        for (String open : OPEN_TICKET_STATUS) {
            boolean found = tickets
                    .findAllByDossierIdAndStatutOrderByClotureAtDesc(dossierId, open)
                    .stream()
                    .anyMatch(t -> WRITE_ALLOWED_TICKET_TYPES.contains(t.getType()));
            if (found) return true;
        }
        return false;
    }
}
