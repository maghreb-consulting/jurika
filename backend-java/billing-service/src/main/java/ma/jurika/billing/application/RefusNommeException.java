package ma.jurika.billing.application;

import ma.jurika.common.exception.BusinessException;

import java.util.List;

/**
 * Lot L7 (E5) : refus qui NOMME ce qui manque ou ce qui depasse (P3 du CDC, adapte a la
 * facturation) : mentions obligatoires absentes d'une facture, usage au-dela du forfait vise
 * (RG-TAR-05), parametre de paiement absent. Rendu en 422 avec la liste ({@code elements}).
 */
public class RefusNommeException extends BusinessException {

    private final List<String> elements;

    public RefusNommeException(String code, String message, List<String> elements) {
        super(code, message);
        this.elements = List.copyOf(elements);
    }

    public List<String> elements() {
        return elements;
    }
}
