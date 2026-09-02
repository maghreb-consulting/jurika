package ma.jurika.workflow.domain.port;

import java.util.Map;

public interface AiClient {

    Map<String, Object> extractCnFromPdf(byte[] pdfBytes);

    Map<String, Object> extractCinFromImage(byte[] imageBytes);

    Map<String, Object> generateStatuts(Map<String, Object> data);
}
