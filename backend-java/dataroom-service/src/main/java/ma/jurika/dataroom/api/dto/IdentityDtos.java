package ma.jurika.dataroom.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs orchestration extraction d'identité (CIN / CN) via kie-service.
 */
public final class IdentityDtos {

    private IdentityDtos() {}

    /**
     * Type métier exposé à l'API.
     * <ul>
     *   <li>{@code NOUVELLE} — CIN nouvelle génération (avec MRZ TD1).</li>
     *   <li>{@code ANCIENNE} — CIN ancienne génération.</li>
     *   <li>{@code CN}       — Carte / registre (pas de verso).</li>
     * </ul>
     */
    public enum IdentityType { ANCIENNE, NOUVELLE, CN }

    /**
     * Résultat de l'orchestration.
     *
     * @param type                  type tel que demandé par l'appelant.
     * @param fields                champs fusionnés (recto + verso si applicable).
     * @param source                "kie" si Donut seul, "merged" si MRZ TD1 exploitée.
     * @param warnings              anomalies non-bloquantes remontées par kie-service ou la fusion.
     * @param archivedDocumentId    id du document Data Room créé si archivage demandé, sinon {@code null}.
     */
    public record ExtractedIdentityDto(IdentityType type,
                                       Map<String, String> fields,
                                       String source,
                                       List<String> warnings,
                                       UUID archivedDocumentId) {}
}
