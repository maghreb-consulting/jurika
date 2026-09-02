package ma.jurika.ai.infrastructure;

import ma.jurika.ai.domain.factory.DocumentFactory;
import ma.jurika.common.audit.Auditable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Factory Pattern : produit des "documents" en mode template (stub).
 * Sera remplace en Phase 6 par une implementation Spring AI.
 */
@Component
public class TemplateDocumentFactory implements DocumentFactory {

    @Override
    @Auditable(action = "AI_DOCUMENT_GENERATED", resourceType = "document")
    public Map<String, Object> create(DocumentType type, Map<String, Object> data) {
        return switch (type) {
            case STATUTS_CONSTITUTIFS -> generateStatuts(data);
            case ACTE_NOMINATION -> generateActeNomination(data);
            case PV_AGE_DISSOLUTION -> generatePvDissolution(data);
            case PV_AGE_MODIFICATION -> generatePvModification(data);
            case PV_AGO -> generatePvAgo(data);
            case PV_LIQUIDATION -> generatePvLiquidation(data);
            case RAPPORT_LIQUIDATEUR -> generateRapportLiquidateur(data);
            case ANNONCE_LEGALE -> generateAnnonceLegale(data);
            case ETAT_DEBOURS -> generateEtatDebours(data);
        };
    }

    private Map<String, Object> generateStatuts(Map<String, Object> data) {
        Map<String, Object> out = base("STATUTS_CONSTITUTIFS", data);
        out.put("titre", "Statuts constitutifs - " + data.getOrDefault("denomination", "SARL"));
        out.put("contenuMarkdown", "# STATUTS CONSTITUTIFS\n\n" +
                "## Article 1 - Forme\n" + data.getOrDefault("formeJuridique", "SARL") + "\n\n" +
                "## Article 2 - Denomination\n" + data.getOrDefault("denomination", "<a remplir>") + "\n\n" +
                "## Article 3 - Siege social\n" + data.getOrDefault("siegeAdresse", "<adresse>") + "\n\n" +
                "## Article 4 - Capital social\n" + data.getOrDefault("capitalSocial", "0") + " MAD\n\n" +
                "[Generation IA complete en Phase 6 — Spring AI + GPT-4o]");
        return out;
    }

    private Map<String, Object> generateActeNomination(Map<String, Object> data) {
        Map<String, Object> out = base("ACTE_NOMINATION", data);
        out.put("titre", "Acte de nomination du gerant");
        return out;
    }

    private Map<String, Object> generatePvDissolution(Map<String, Object> data) {
        return base("PV_AGE_DISSOLUTION", data);
    }

    private Map<String, Object> generatePvModification(Map<String, Object> data) {
        return base("PV_AGE_MODIFICATION", data);
    }

    private Map<String, Object> generatePvAgo(Map<String, Object> data) {
        return base("PV_AGO", data);
    }

    private Map<String, Object> generatePvLiquidation(Map<String, Object> data) {
        return base("PV_LIQUIDATION", data);
    }

    private Map<String, Object> generateRapportLiquidateur(Map<String, Object> data) {
        return base("RAPPORT_LIQUIDATEUR", data);
    }

    private Map<String, Object> generateAnnonceLegale(Map<String, Object> data) {
        Map<String, Object> out = base("ANNONCE_LEGALE", data);
        out.put("titre", "Annonce legale - " + data.getOrDefault("denomination", ""));
        return out;
    }

    private Map<String, Object> generateEtatDebours(Map<String, Object> data) {
        return base("ETAT_DEBOURS", data);
    }

    private Map<String, Object> base(String type, Map<String, Object> data) {
        Map<String, Object> out = new HashMap<>();
        out.put("documentId", UUID.randomUUID().toString());
        out.put("type", type);
        out.put("generatedAt", LocalDate.now().toString());
        out.put("source", "TEMPLATE_STUB");
        out.put("editable", true);
        out.put("input", data);
        return out;
    }
}
