package fr.maghreb.gje.dto.document;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class DocumentEditRequest {
    @NotBlank private String editedContent;
    private String editMention;
}
