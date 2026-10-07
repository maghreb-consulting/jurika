package ma.jurika.auth.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Lot L0 (E13a) : retrouve le workspace d'une requete PUBLIQUE avant toute
 * transaction, par les fonctions SECURITY DEFINER d'auth V34. Sous le role
 * d'execution jurika_app, la RLS cache tout ce qui n'est pas du workspace
 * courant : ces quelques recherches doivent donc se faire hors RLS, et
 * seulement elles.
 */
public interface ResolveurWorkspaceHorsContexte {

    Optional<UUID> parCode(String codeWorkspace);

    Optional<UUID> parEmpreinteJetonReset(String empreinte);

    Optional<UUID> parEmpreinteJetonVerification(String empreinte);

    Optional<UUID> parEmpreinteJetonRefresh(String empreinte);
}
