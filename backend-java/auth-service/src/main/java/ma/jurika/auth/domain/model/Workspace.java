package ma.jurika.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public final class Workspace {

    private final UUID id;
    private final String code;
    private final String name;
    private final String contactEmail;
    private final UUID subscriptionId;
    private final WorkspaceStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    /** Lot L7 (E8) : motif d'une suspension (auth V46) ; IMPAYE ou null (decision du super-admin). */
    private final String suspensionMotif;

    /** Lot L7 (E8) : motif d'une suspension pour impaye (article 4.4 du contrat). */
    public static final String SUSPENSION_IMPAYE = "IMPAYE";

    public Workspace(UUID id, String code, String name, String contactEmail,
                     UUID subscriptionId, WorkspaceStatus status,
                     Instant createdAt, Instant updatedAt) {
        this(id, code, name, contactEmail, subscriptionId, status, createdAt, updatedAt, null);
    }

    public Workspace(UUID id, String code, String name, String contactEmail,
                     UUID subscriptionId, WorkspaceStatus status,
                     Instant createdAt, Instant updatedAt, String suspensionMotif) {
        this.suspensionMotif = suspensionMotif;
        this.id = id;
        this.code = code;
        this.name = name;
        this.contactEmail = contactEmail;
        this.subscriptionId = subscriptionId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID id() { return id; }
    public String code() { return code; }
    public String name() { return name; }
    public String contactEmail() { return contactEmail; }
    public UUID subscriptionId() { return subscriptionId; }
    public WorkspaceStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public boolean isActive() {
        return status == WorkspaceStatus.ACTIVE;
    }

    /** Lot L7 (E5), RG-TAR-07 : workspace archive (forfait arrete, rien n'est supprime). */
    public boolean isArchived() {
        return status == WorkspaceStatus.ARCHIVED;
    }

    /**
     * Lot L7 (E5), RG-TAR-07 et RG-TAR-09 : le compte de ce role peut-il ouvrir une session ? Workspace
     * actif : oui. Workspace archive : le seul superviseur (export, demande de suppression, nouvelle
     * souscription). Autre statut : non.
     */
    public boolean accepteConnexion(ma.jurika.common.security.Role role) {
        if (isActive()) return true;
        return (isArchived() || isSuspenduImpaye()) && role == ma.jurika.common.security.Role.SUPERVISEUR;
    }

    /**
     * Lot L7 (E8), article 4.4 du contrat : acces suspendu pour une echeance impayee (15 jours apres
     * l'echeance et apres un rappel). Rien n'est supprime ; le superviseur se connecte pour payer et
     * l'acces est retabli des le paiement constate. Une suspension decidee par le super-admin
     * (motif null) ne s'ouvre a personne.
     */
    public boolean isSuspenduImpaye() {
        return status == WorkspaceStatus.SUSPENDED && SUSPENSION_IMPAYE.equals(suspensionMotif);
    }

    public String suspensionMotif() { return suspensionMotif; }

    /**
     * Lot L7 : le workspace ouvre-t-il une session a l'un de ses comptes au moins (actif ; archive ou
     * suspendu pour impaye : son superviseur) ? Controle prealable au mot de passe.
     */
    public boolean ouvertALaConnexion() {
        return isActive() || isArchived() || isSuspenduImpaye();
    }
}
