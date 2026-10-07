package ma.jurika.common.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import ma.jurika.common.security.TenantContext;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Gestionnaire de transactions JPA qui pose le workspace courant sur la
 * connexion de CHAQUE transaction physique, des son ouverture (lot L0, G1).
 *
 * <p>Les politiques RLS lisent {@code current_setting('app.current_workspace_id')}.
 * Le reglage est pose en {@code set_config(..., true)}, donc local a la
 * transaction : il disparait au commit ou au rollback et ne fuit jamais sur une
 * connexion rendue au pool.
 *
 * <p>Pourquoi ici et pas dans un aspect : l'ancien {@link RlsAspect} s'executait
 * AVANT l'intercepteur de transaction (ordre AOP), le reglage etait donc perdu.
 * Le gestionnaire couvre en outre {@code REQUIRES_NEW}, les auto-invocations,
 * les transactions ouvertes par Spring Data lui-meme et le {@code JdbcTemplate}
 * qui s'execute dans une transaction.
 *
 * <p>Regle d'usage : {@link TenantContext} doit etre pose AVANT d'entrer dans la
 * methode {@code @Transactional} ; une affectation dans le corps de la methode
 * arrive trop tard pour la transaction deja ouverte.
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    static final String POSER_WORKSPACE = "SELECT set_config('app.current_workspace_id', ?1, true)";

    public TenantAwareJpaTransactionManager() {
        super();
    }

    public TenantAwareJpaTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        UUID workspaceId = TenantContext.get();
        if (workspaceId == null) {
            return;
        }
        EntityManagerHolder holder = (EntityManagerHolder)
                TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        if (holder == null) {
            throw new IllegalStateException("Transaction ouverte sans EntityManager lie : "
                    + "impossible de poser le workspace courant.");
        }
        EntityManager em = holder.getEntityManager();
        em.createNativeQuery(POSER_WORKSPACE)
                .setParameter(1, workspaceId.toString())
                .getSingleResult();
    }
}
