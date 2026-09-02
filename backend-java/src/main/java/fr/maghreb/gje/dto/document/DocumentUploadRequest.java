package fr.maghreb.gje.dto.document;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class DocumentUploadRequest {
    @NotBlank private String title;
    @NotBlank private String dossierId;
    private String fileContent;
    private String fileName;
}
