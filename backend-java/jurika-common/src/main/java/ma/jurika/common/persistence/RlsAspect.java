package ma.jurika.common.persistence;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

/**
 * NEUTRALISE depuis le lot L0 (G1) : ne fait plus rien.
 *
 * <p>Cet aspect posait {@code set_config('app.current_workspace_id', ..., true)}
 * AVANT l'ouverture de la transaction ({@code @Order(0)}, exterieur a
 * l'intercepteur de transaction) : le reglage, local a une transaction qui
 * n'existait pas encore, etait aussitot perdu, et la RLS ne recevait jamais le
 * workspace (prouve par TenantTransactionIT). Le workspace est desormais pose
 * par {@link TenantAwareJpaTransactionManager} a l'ouverture de chaque
 * transaction.
 *
 * <p>La classe est conservee le temps que les services retirent leur
 * declaration de bean ({@code JpaConfig} / {@code SecurityConfig}) ; sa
 * suppression est un point d'arret du lot (suppression de fichier).
 */
@Aspect
@Order(0)
@Deprecated(since = "lot L0")
public class RlsAspect {

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) && execution(* ma.jurika..*(..))")
    public Object setTenantOnTransaction(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }
}
