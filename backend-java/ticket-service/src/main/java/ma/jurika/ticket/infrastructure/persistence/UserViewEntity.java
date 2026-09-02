package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Vue read-only sur la table users (creee par auth-service). Sert a resoudre le
 * nom de l'employe responsable (colonne assigne_id d'un ticket, un UUID brut) en
 * « Prenom Nom » pour l'affichage de la liste des tickets — sans cross-call HTTP.
 * Lecture seule uniquement.
 *
 * <p>Aligne sur le pattern des autres vues read-only du ticket-service
 * ({@link WorkspaceViewEntity}) et du dataroom-service (UserViewEntity). Toute
 * lecture est scopee workspace explicitement.
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
}
