package fr.maghreb.gje.dto.document;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class DocumentGenerateRequest {
    @NotBlank private String dossierId;
    @NotBlank private String documentType;
    private String templateName;
    private String additionalInstructions;
}
