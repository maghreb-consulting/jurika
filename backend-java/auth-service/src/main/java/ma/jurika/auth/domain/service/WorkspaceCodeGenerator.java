package ma.jurika.auth.domain.service;

import ma.jurika.auth.domain.port.WorkspaceRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class WorkspaceCodeGenerator {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 5;
    private static final int MAX_ATTEMPTS = 25;

    private final WorkspaceRepository workspaceRepository;
    private final SecureRandom random = new SecureRandom();

    public WorkspaceCodeGenerator(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    public String generateUnique() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = "JUR-" + randomSegment();
            if (!workspaceRepository.codeExists(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Impossible de generer un code workspace unique");
    }

    private String randomSegment() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
