package ma.jurika.dataroom.application.identity;

import ma.jurika.common.audit.Auditable;
import ma.jurika.dataroom.api.dto.IdentityDtos.ExtractedIdentityDto;
import ma.jurika.dataroom.api.dto.IdentityDtos.IdentityType;
import ma.jurika.dataroom.domain.port.IdentityArchiver;
import ma.jurika.dataroom.domain.port.KieServiceClient;
import ma.jurika.dataroom.domain.port.KieServiceClient.KieExtractResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestre l'extraction d'identité : un appel kie-service par face,
 * fusion des champs, archivage Data Room optionnel.
 * <p>
 * Cette classe est testable en isolation : le client HTTP et l'archiver
 * sont injectés.
 */
@Service
public class IdentityExtractionService {

    private static final Logger log = LoggerFactory.getLogger(IdentityExtractionService.class);

    /** Champs où la face VERSO l'emporte sur le recto en cas de conflit (cohérent MRZ). */
    static final Set<String> VERSO_WINS = Set.of(
            "cin", "sexe", "adresse", "date_validite", "nationalite"
    );

    /** Champs où la face RECTO l'emporte (état civil principalement). */
    static final Set<String> RECTO_WINS = Set.of(
            "nom", "prenom", "date_naissance", "lieu_naissance"
    );

    private final KieServiceClient kie;
    private final IdentityArchiver archiver;

    public IdentityExtractionService(KieServiceClient kie, IdentityArchiver archiver) {
        this.kie = kie;
        this.archiver = archiver;
    }

    @Auditable(action = "IDENTITY_EXTRACTED", resourceType = "dossier", resourceIdExpr = "#dossierId")
    public ExtractedIdentityDto extract(IdentityType type,
                                        MultipartFile recto,
                                        MultipartFile verso,
                                        UUID dossierId,
                                        boolean archive,
                                        UUID uploaderId) {
        if (recto == null || recto.isEmpty()) {
            throw new IllegalArgumentException("Recto requis");
        }
        if (type == IdentityType.CN && verso != null && !verso.isEmpty()) {
            log.warn("Verso ignoré pour type=CN");
            verso = null;
        }

        byte[] rectoBytes = readBytes(recto);
        String rectoFilename = recto.getOriginalFilename();
        String rectoContentType = recto.getContentType();

        KieExtractResponse rectoOut = kie.extract(
                docTypeForRecto(type), rectoFilename, rectoContentType, rectoBytes);

        KieExtractResponse versoOut = null;
        byte[] versoBytes = null;
        String versoFilename = null;
        String versoContentType = null;
        if (verso != null && !verso.isEmpty() && type != IdentityType.CN) {
            versoBytes = readBytes(verso);
            versoFilename = verso.getOriginalFilename();
            versoContentType = verso.getContentType();
            versoOut = kie.extract(
                    docTypeForVerso(type), versoFilename, versoContentType, versoBytes);
        }

        Map<String, String> fused = fuseFields(rectoOut, versoOut);
        List<String> warnings = fuseWarnings(rectoOut, versoOut);
        String source = pickSource(rectoOut, versoOut);

        UUID archivedDocumentId = null;
        if (archive && dossierId != null) {
            archivedDocumentId = archiver.archive(
                    dossierId,
                    uploaderId,
                    archiveDocumentType(type),
                    buildArchiveTitle(type, fused, rectoFilename),
                    rectoBytes, rectoFilename, rectoContentType,
                    versoBytes, versoFilename, versoContentType);
        }

        return new ExtractedIdentityDto(type, fused, source, warnings, archivedDocumentId);
    }

    // ---------------------------------------------------------------------
    // Mapping IdentityType -> doc_type kie-service. Visible pour les tests.
    // ---------------------------------------------------------------------

    static String docTypeForRecto(IdentityType type) {
        return switch (type) {
            case NOUVELLE -> "cin_nouv_recto";
            case ANCIENNE -> "cin_anc_recto";
            case CN       -> "cn";
        };
    }

    static String docTypeForVerso(IdentityType type) {
        return switch (type) {
            case NOUVELLE -> "cin_nouv_verso";
            case ANCIENNE -> "cin_anc_verso";
            case CN       -> throw new IllegalArgumentException("CN n'a pas de verso");
        };
    }

    static String archiveDocumentType(IdentityType type) {
        return switch (type) {
            case NOUVELLE -> "CIN_NOUVELLE";
            case ANCIENNE -> "CIN_ANCIENNE";
            case CN       -> "CN";
        };
    }

    /**
     * Fix A5 (2026-08-16) — un titre d'archive doit IDENTIFIER SA PERSONNE.
     *
     * <p>Le titre n'était nominatif que si l'OCR avait rendu un nom. Dès qu'il
     * échouait (photo floue, CIN étrangère, MRZ illisible), toutes les pièces
     * retombaient sur le même libellé générique « CIN nouvelle » : impossible de
     * savoir à qui elles appartenaient, et à l'export elles s'écrasaient.
     *
     * <p>Depuis le fix DR1, le couple (type + titre) désigne un DOCUMENT LOGIQUE :
     * deux CIN au titre identique deviendraient deux VERSIONS l'une de l'autre —
     * la seconde masquerait la première. Un titre nominatif n'est donc plus un
     * confort d'affichage mais une condition de non-perte.
     *
     * <p>À défaut de nom exploitable, on retient le n° de pièce, puis le nom du
     * fichier d'origine : le repli reste distinctif ET vérifiable par un humain.
     */
    private static String buildArchiveTitle(IdentityType type, Map<String, String> fused,
                                            String rectoFilename) {
        String base = switch (type) {
            case NOUVELLE -> "CIN nouvelle";
            case ANCIENNE -> "CIN ancienne";
            case CN       -> "CN";
        };
        String who = (fused.getOrDefault("prenom", "") + " "
                + fused.getOrDefault("nom", "")).trim();
        if (who.isBlank()) {
            who = firstNonBlank(fused.get("numero"), fused.get("cin"),
                    fused.get("numeroPiece"), stripExtension(rectoFilename));
        }
        return who.isBlank() ? base : base + " - " + who;
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private static String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) return "";
        String n = filename.trim();
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    // ---------------------------------------------------------------------
    // Fusion. Visible pour les tests.
    // ---------------------------------------------------------------------

    static Map<String, String> fuseFields(KieExtractResponse rectoOut, KieExtractResponse versoOut) {
        Map<String, String> out = new LinkedHashMap<>();
        if (rectoOut != null) {
            out.putAll(filterMeaningful(rectoOut.fields()));
        }
        if (versoOut != null) {
            Map<String, String> versoFields = filterMeaningful(versoOut.fields());
            for (Map.Entry<String, String> e : versoFields.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                if (!out.containsKey(k)) {
                    out.put(k, v);
                    continue;
                }
                // Conflit : appliquer la priorité.
                if (VERSO_WINS.contains(k)) {
                    out.put(k, v);
                } else if (RECTO_WINS.contains(k)) {
                    // Ne pas écraser la valeur du recto.
                    continue;
                } else {
                    // Champ non listé : on garde la 1re valeur (recto) tant qu'elle est non vide.
                    if (out.get(k) == null || out.get(k).isBlank()) {
                        out.put(k, v);
                    }
                }
            }
        }
        return out;
    }

    private static Map<String, String> filterMeaningful(Map<String, String> raw) {
        if (raw == null) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (e.getKey() == null) continue;
            String v = e.getValue();
            if (v == null || v.isBlank()) continue;
            out.put(e.getKey(), v);
        }
        return out;
    }

    static List<String> fuseWarnings(KieExtractResponse rectoOut, KieExtractResponse versoOut) {
        Set<String> seen = new LinkedHashSet<>();
        if (rectoOut != null && rectoOut.warnings() != null) {
            seen.addAll(rectoOut.warnings());
        }
        if (versoOut != null && versoOut.warnings() != null) {
            seen.addAll(versoOut.warnings());
        }
        return new ArrayList<>(seen);
    }

    private static String pickSource(KieExtractResponse rectoOut, KieExtractResponse versoOut) {
        // Si la MRZ TD1 a été exploitée côté verso, kie-service renvoie source=merged.
        if (versoOut != null && "merged".equals(versoOut.source())) {
            return "merged";
        }
        return "kie";
    }

    private static byte[] readBytes(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException ex) {
            throw new RuntimeException("Lecture fichier échouée : " + ex.getMessage(), ex);
        }
    }
}
