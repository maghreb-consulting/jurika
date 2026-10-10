package ma.jurika.workflow.application;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Lot L3 (RG-VAR-02/03, RG-FIC-03) : le magasin de chaque ticket est initialise depuis la
 * fiche societe, pour tous les parcours -- provenance FICHE. Une donnee deja connue de la
 * societe n'est jamais redemandee : elle s'affiche avec sa provenance, et une correction
 * de la fiche se repercute (RG-VAR-05). Une donnee saisie au ticket n'est jamais recouverte.
 */
@Component
public class ProjecteurFicheSociete {

    /** Variable canonique (dictionnaire unique) -> cle des faits du dossier (WorkflowUseCases#toDossierFacts). */
    static final Map<String, String> VARIABLES = Map.ofEntries(
            Map.entry("DENOMINATION", "denomination"),
            Map.entry("FORME_JURIDIQUE", "formeJuridique"),
            Map.entry("ICE", "ice"),
            Map.entry("IDENTIFIANT_FISCAL", "identifiantFiscal"),
            Map.entry("IDENTIFIANT_TP", "identifiantTp"),
            Map.entry("CNSS_NUMERO", "cnssNumero"),
            Map.entry("RC_NUMERO", "rcNumero"),
            Map.entry("RC_VILLE", "rcTribunal"),
            Map.entry("TRIBUNAL_VILLE", "rcTribunal"),
            Map.entry("SIEGE_SOCIAL", "adresseSiege"),
            Map.entry("SIEGE_VILLE", "ville"),
            Map.entry("CAPITAL_CHIFFRES", "capitalSocial"));

    private final MagasinVariables magasin;

    public ProjecteurFicheSociete(MagasinVariables magasin) {
        this.magasin = magasin;
    }

    /** @return le nombre de variables posees ou mises a jour. */
    public int projeter(UUID workspaceId, UUID ticketId, Map<String, Object> faits) {
        if (faits == null || faits.isEmpty()) return 0;
        int n = 0;
        for (Map.Entry<String, String> e : VARIABLES.entrySet()) {
            Object v = faits.get(e.getValue());
            if (v == null) continue;
            String valeur = v instanceof java.math.BigDecimal b ? b.stripTrailingZeros().toPlainString() : String.valueOf(v);
            if (magasin.poserFiche(workspaceId, ticketId, e.getKey(), valeur)) n++;
        }
        return n;
    }
}
