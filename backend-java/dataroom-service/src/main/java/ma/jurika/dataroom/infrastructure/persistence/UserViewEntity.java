package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Vue read-only sur la table users (creee par auth-service). Sert a la Fiche
 * client pour resoudre l'auteur d'un document (colonne uploaded_by, un UUID
 * brut) en « Prenom Nom » sans cross-call HTTP. Lecture seule uniquement.
 *
 * <p>Decision 2026-07-14 : lecture directe de la table partagee, alignee sur le
 * pattern des autres vues read-only du dataroom-service (DossierViewEntity,
 * TicketViewEntity). Toute lecture est scopee workspace explicitement.
 */
@Entity
@Table(name = "users")
public class UserViewEntity {
    @Id
    private UUID id;
    @Column(name = "workspace_id", insertable = false, updatable = false)
    private UUID workspaceId;
    @Column(name = "first_name", insertable = false, updatable = false)
    private String firstName;
    @Column(name = "last_name", insertable = false, updatable = false)
    private String lastName;
    @Column(insertable = false, updatable = false)
    private String email;

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }

    /** « Prenom Nom » ; repli sur l'email puis un tiret si tout est vide. */
    public String displayName() {
        String fn = firstName == null ? "" : firstName.trim();
        String ln = lastName == null ? "" : lastName.trim();
        String full = (fn + " " + ln).trim();
        if (!full.isBlank()) return full;
        if (email != null && !email.isBlank()) return email;
        return "—";
    }
}
