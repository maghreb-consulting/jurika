package ma.jurika.ticket.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 2026-06-30 — Port d'accès à l'annuaire des membres internes du workspace.
 *
 * <p>ticket-service ne possède pas la table {@code users} (elle vit dans
 * auth-service). Ce port abstrait la résolution du rôle d'un utilisateur pour
 * que la couche application reste testable (mock en unitaire) et indépendante
 * du transport (Feign vers auth-service en prod).
 *
 * <p>Sémantique de {@link #roleOf} : retourne le rôle ("EMPLOYE"/"SUPERVISEUR")
 * du membre interne, ou {@link Optional#empty()} si l'userId n'est pas un membre
 * interne connu (CLIENT/SUPER_ADMIN/inconnu). Une indisponibilité d'auth-service
 * propage l'exception (fail-closed : on ne valide pas un transfert sans pouvoir
 * vérifier le rôle de la cible).
 */
public interface MemberDirectory {

    Optional<String> roleOf(UUID workspaceId, UUID userId);
}
