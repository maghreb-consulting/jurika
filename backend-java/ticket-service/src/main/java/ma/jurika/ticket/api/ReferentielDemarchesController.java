package ma.jurika.ticket.api;

import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.ticket.application.referentiel.ImportReferentielDemarches;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lot L4b (E2, RG-DEM-01) : import du referentiel des demarches du corpus du cabinet.
 * Reserve au super-administrateur : le referentiel est commun a tous les cabinets.
 */
@RestController
@RequestMapping("/api/v1/tickets/referentiel-demarches")
public class ReferentielDemarchesController {

    private final ImportReferentielDemarches imports;

    public ReferentielDemarchesController(ImportReferentielDemarches imports) {
        this.imports = imports;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> etat() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("versionActive", imports.versionActive());
        out.put("imports", imports.journal());
        return out;
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ImportReferentielDemarches.Resultat importer(@AuthenticationPrincipal AuthenticatedUser actor) {
        return imports.importer(actor == null ? null : actor.userId());
    }
}
