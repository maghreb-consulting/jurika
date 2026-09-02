package ma.jurika.dataroom.application.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sprint 7 / TASK 5 -- Marqueur pour les methodes a tracer dans le
 * dataroom_client_access_log. Pose sur methodes use case (preview, download, ...)
 *
 * L'extraction du dossier/document est faite par ClientAccessLogAspect.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ClientAccess {

    /** Doit etre VIEW_DOSSIER / PREVIEW_DOC / DOWNLOAD_DOC / PRINT_DOC. */
    String action();
}
