package ma.jurika.common.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.common.security.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

import java.util.UUID;

@Aspect
@Order(0)
public class RlsAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) && execution(* ma.jurika..*(..))")
    public Object setTenantOnTransaction(ProceedingJoinPoint pjp) throws Throwable {
        UUID workspaceId = TenantContext.get();
        if (workspaceId != null) {
            entityManager
                    .createNativeQuery("SELECT set_config('app.current_workspace_id', :wid, true)")
                    .setParameter("wid", workspaceId.toString())
                    .getSingleResult();
        }
        return pjp.proceed();
    }
}
