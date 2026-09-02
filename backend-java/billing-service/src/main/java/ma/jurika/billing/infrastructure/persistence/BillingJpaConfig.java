package ma.jurika.billing.infrastructure.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Configuration JPA du billing-service.
 *
 * <p>{@code considerNestedRepositories = true} est indispensable : les 4 interfaces
 * {@code JpaRepository} sont imbriquees dans {@link BillingRepositories} et Spring
 * Data ne les scanne pas sans ce flag (fix Sprint 12).
 *
 * <p>Lot J3 (2026-06-27) — l'annotation a ete DEPLACEE depuis
 * {@code BillingApplication} vers cette {@code @Configuration} dediee : sur la
 * classe applicative elle etait honoree meme par les slices {@code @WebMvcTest}
 * (qui ne demarrent pas JPA), faisant echouer le chargement du contexte sur un
 * {@code entityManagerFactory} manquant. Une {@code @Configuration} dediee est
 * exclue par le filtre du slice web → la securite des controllers est testable.
 * Comportement runtime strictement identique.
 */
@Configuration
@EnableJpaRepositories(basePackages = "ma.jurika.billing", considerNestedRepositories = true)
public class BillingJpaConfig {
}
