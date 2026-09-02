package ma.jurika.ai.llm.schema;

import ma.jurika.ai.llm.schema.DocumentSchema.FieldDef;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registre central des schémas de documents extractibles via LLM.
 * <p>
 * P4 V1 — 6 types couvrant le workflow CREATION SARL :
 * CIN, CERTIFICAT_NEGATIF, STATUTS_SARL, RC_IMMATRICULATION, IF_DECLARATION, JUSTIFICATIF_SIEGE.
 * <p>
 * Pour ajouter un type : enrichir le bloc static d'initialisation ci-dessous.
 * Aucun mécanisme YAML pour rester déterministe et auditable cote Java.
 */
@Component
public class DocumentSchemaRegistry {

    private static final Map<String, DocumentSchema> SCHEMAS;

    static {
        Map<String, DocumentSchema> m = new LinkedHashMap<>();

        m.put("CIN", new DocumentSchema(
                "CIN",
                "Carte d'identité nationale marocaine (CIN). Champs civils du titulaire.",
                List.of(
                        new FieldDef("nom", "string", "Nom de famille (en majuscules)", true),
                        new FieldDef("prenom", "string", "Prénom(s) du titulaire", true),
                        new FieldDef("cinNumero", "string", "Numéro de CIN (1-2 lettres + 4-7 chiffres, ex AB123456)", true),
                        new FieldDef("dateNaissance", "date", "Date de naissance (format ISO YYYY-MM-DD)", false),
                        new FieldDef("dateExpiration", "date", "Date d'expiration de la CIN (format ISO YYYY-MM-DD)", false)
                )
        ));

        m.put("CERTIFICAT_NEGATIF", new DocumentSchema(
                "CERTIFICAT_NEGATIF",
                "Certificat Négatif délivré par l'OMPIC (vérification disponibilité de dénomination commerciale).",
                List.of(
                        new FieldDef("ice", "string", "ICE de l'entreprise si présent (15 chiffres)", false),
                        new FieldDef("cnNumero", "string", "Numéro du Certificat Négatif (ex CN-2026-12345)", true),
                        new FieldDef("cnDate", "date", "Date de délivrance du certificat (format ISO YYYY-MM-DD)", true),
                        new FieldDef("denomination", "string", "Dénomination / raison sociale réservée", true),
                        new FieldDef("beneficiaire", "string", "Nom du bénéficiaire (personne physique demandeuse)", false),
                        new FieldDef("activiteCn", "string", "Activité(s) déclarée(s) au certificat", false)
                )
        ));

        m.put("STATUTS_SARL", new DocumentSchema(
                "STATUTS_SARL",
                "Statuts constitutifs d'une SARL (ou SARL AU) de droit marocain.",
                List.of(
                        new FieldDef("raisonSociale", "string", "Raison sociale exacte de la société", true),
                        new FieldDef("formeJuridique", "string", "Forme juridique : SARL ou SARL AU", true),
                        new FieldDef("capitalSocial", "number", "Capital social total en MAD (entier)", true),
                        new FieldDef("nombreParts", "number", "Nombre total de parts sociales", false),
                        new FieldDef("siegeSocial", "string", "Adresse complète du siège social", true),
                        new FieldDef("dateConstitution", "date", "Date de signature des statuts (format ISO YYYY-MM-DD)", false)
                )
        ));

        m.put("RC_IMMATRICULATION", new DocumentSchema(
                "RC_IMMATRICULATION",
                "Récépissé / extrait du Registre du Commerce attestant l'immatriculation.",
                List.of(
                        new FieldDef("rcNumero", "string", "Numéro RC attribué (ex 123456)", true),
                        new FieldDef("ville", "string", "Ville du tribunal de commerce (Casablanca, Rabat, ...)", true),
                        new FieldDef("dateImmatriculation", "date", "Date d'immatriculation au RC (format ISO YYYY-MM-DD)", true),
                        new FieldDef("raisonSociale", "string", "Raison sociale immatriculée", false)
                )
        ));

        m.put("IF_DECLARATION", new DocumentSchema(
                "IF_DECLARATION",
                "Attestation / déclaration d'attribution de l'Identifiant Fiscal (IF) par la DGI.",
                List.of(
                        new FieldDef("ifNumero", "string", "Numéro d'Identifiant Fiscal (IF) attribué", true),
                        new FieldDef("dateAttribution", "date", "Date d'attribution de l'IF (format ISO YYYY-MM-DD)", false),
                        new FieldDef("raisonSociale", "string", "Raison sociale du contribuable", false)
                )
        ));

        m.put("JUSTIFICATIF_SIEGE", new DocumentSchema(
                "JUSTIFICATIF_SIEGE",
                "Justificatif de siège social : contrat de bail OU contrat de domiciliation.",
                List.of(
                        new FieldDef("adresse", "string", "Adresse complète (numéro + rue + quartier)", true),
                        new FieldDef("ville", "string", "Ville du siège", true),
                        new FieldDef("codePostal", "string", "Code postal", false),
                        new FieldDef("type", "string", "Type de justificatif : BAIL ou DOMICILIATION", true),
                        new FieldDef("proprietaire", "string", "Nom du bailleur ou de la société de domiciliation", false)
                )
        ));

        // Phase A item 10 (Audit Workflows QA 2026-06-05) — schema de synthese pour
        // un dossier importe (RG-IA07). Consomme par workflow IMPORT step 5 lors de
        // la consolidation FicheJuridique. Tous les champs alignes sur l'output de
        // ImportWorkflow.handleSynthese cote workflow-service.
        m.put("FICHE_JURIDIQUE_IMPORT", new DocumentSchema(
                "FICHE_JURIDIQUE_IMPORT",
                "Fiche juridique synthese pour un dossier importe (RG-IA07).",
                List.of(
                        new FieldDef("raisonSociale", "string", "Raison sociale officielle", true),
                        new FieldDef("ice", "string", "Numero ICE 15 chiffres", true),
                        new FieldDef("rcNumero", "string", "Numero RC", true),
                        new FieldDef("ifNumero", "string", "Numero IF", true),
                        new FieldDef("formeJuridique", "string", "SARL ou SARL_AU", true),
                        new FieldDef("capitalSocial", "number", "Capital social en MAD", true),
                        new FieldDef("capitalLibere", "number", "Capital libere en MAD", true),
                        new FieldDef("siegeAdresse", "string", "Adresse complete du siege", true),
                        new FieldDef("siegeVille", "string", "Ville du siege", true),
                        new FieldDef("gerants", "string", "Liste des gerants (separes par virgules)", false)
                )
        ));

        SCHEMAS = Collections.unmodifiableMap(m);
    }

    /**
     * Récupère le schéma associé au code donné (case-sensitive).
     *
     * @param typeCode Code du type de document (ex "CIN", "CERTIFICAT_NEGATIF").
     * @return Schéma optionnel.
     */
    public Optional<DocumentSchema> find(String typeCode) {
        if (typeCode == null) return Optional.empty();
        return Optional.ofNullable(SCHEMAS.get(typeCode));
    }

    /**
     * Liste des codes supportés (immuable, ordre d'insertion).
     */
    public Set<String> supportedTypes() {
        return SCHEMAS.keySet();
    }

    public Map<String, DocumentSchema> all() {
        return SCHEMAS;
    }
}
