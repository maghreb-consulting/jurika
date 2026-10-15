package ma.jurika.auth.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Lot L6b (E2) : preferences d'affichage d'un COMPTE (colonnes {@code users.theme} et
 * {@code users.juri_masque}, auth V47). Le workspace courant (RLS) doit etre {@code workspaceId} ;
 * chaque requete filtre aussi explicitement sur {@code id} et {@code workspace_id}.
 */
public interface PreferencesCompteRegistre {

    /** Theme ('SYSTEME', 'CLAIR', 'SOMBRE') et assistant Juri masque. */
    record Preferences(String theme, boolean juriMasque) {}

    /** Preferences du compte ; vide si le compte n'est pas visible dans ce workspace. */
    Optional<Preferences> lire(UUID userId, UUID workspaceId);

    /**
     * Modifie les preferences donnees (null = inchangee) et rend l'etat enregistre ; vide si le
     * compte n'est pas visible dans ce workspace (rien n'est ecrit).
     */
    Optional<Preferences> modifier(UUID userId, UUID workspaceId, String theme, Boolean juriMasque);
}
