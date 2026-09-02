package ma.jurika.ai.domain.factory;

import java.util.Map;

public interface DocumentFactory {

    enum DocumentType {
        STATUTS_CONSTITUTIFS,
        ACTE_NOMINATION,
        PV_AGE_DISSOLUTION,
        PV_AGE_MODIFICATION,
        PV_AGO,
        PV_LIQUIDATION,
        RAPPORT_LIQUIDATEUR,
        ANNONCE_LEGALE,
        ETAT_DEBOURS
    }

    Map<String, Object> create(DocumentType type, Map<String, Object> data);
}
