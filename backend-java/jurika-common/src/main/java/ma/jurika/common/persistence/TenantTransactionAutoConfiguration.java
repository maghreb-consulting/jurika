package ma.jurika.common.persistence;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionManager;

import javax.sql.DataSource;

/**
 * Lot L0 (G1) : remplace le gestionnaire de transactions JPA de Spring Boot par
 * {@link TenantAwareJpaTransactionManager}, qui pose le workspace courant sur
 * chaque transaction. Ordonnee AVANT {@link HibernateJpaAutoConfiguration}, dont
 * le {@code transactionManager} est {@code @ConditionalOnMissingBean} : c'est
 * donc le notre qui est retenu. Un service qui declare son propre gestionnaire
 * garde le sien.
 *
 * <p>Garde « hors transaction » : chaque connexion du pool recoit a
 * l'ouverture une valeur de session INVALIDE pour {@code app.current_workspace_id}.
 * Une requete sur une table sous RLS executee sans workspace (hors transaction,
 * ou dans une transaction sans {@link ma.jurika.common.security.TenantContext})
 * echoue alors sur le cast en {@code uuid} au lieu de renvoyer zero ligne en
 * silence. Le reglage local d'une transaction la remplace ; un superutilisateur
 * n'evalue pas les politiques et n'est pas concerne.
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class, before = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({JpaTransactionManager.class, EntityManagerFactory.class})
@ConditionalOnSingleCandidate(DataSource.class)
public class TenantTransactionAutoConfiguration {

    /** Valeur de session posee hors transaction : non convertible en uuid, donc bruyante. */
    public static final String VALEUR_HORS_TRANSACTION = "hors-transaction";

    static final String GARDE_SQL = "SET app.current_workspace_id = '" + VALEUR_HORS_TRANSACTION + "'";

    @Bean(name = "transactionManager")
    @ConditionalOnMissingBean(TransactionManager.class)
    public JpaTransactionManager transactionManager(
            ObjectProvider<TransactionManagerCustomizers> customizers) {
        TenantAwareJpaTransactionManager tm = new TenantAwareJpaTransactionManager();
        customizers.ifAvailable(c -> c.customize((TransactionManager) tm));
        return tm;
    }

    @Bean
    @ConditionalOnClass(HikariDataSource.class)
    @ConditionalOnProperty(name = "jurika.rls.garde-hors-transaction", havingValue = "true", matchIfMissing = true)
    public static BeanPostProcessor gardeHorsTransactionHikari() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof HikariDataSource ds && ds.getConnectionInitSql() == null) {
                    ds.setConnectionInitSql(GARDE_SQL);
                }
                return bean;
            }
        };
    }
}
