package ma.jurika.dataroom.infrastructure.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L6b (E13) : une photo de piece d'identite dans un format que PDFBox ne lit pas (WebP, HEIC,
 * AVIF, accepte par le selecteur "image/*" du front) faisait repondre 400 avec le texte de PDFBox
 * en anglais ("Image type ... not supported"), et le front affichait "Requete invalide.".
 * Le refus doit dire en francais quelle face corriger et quels formats envoyer.
 */
class IdentityPdfBuilderTest {

    @Test
    @DisplayName("Format d'image non pris en charge : message francais qui nomme la face et les formats")
    void formatNonPrisEnCharge() {
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 fausse image".getBytes(StandardCharsets.ISO_8859_1);
        assertThatThrownBy(() -> IdentityPdfBuilder.build(webp, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recto")
                .hasMessageContaining("JPG")
                .hasMessageContaining("PNG")
                .hasMessageNotContaining("not supported");
    }
}
