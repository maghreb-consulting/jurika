package ma.jurika.auth.domain.service;

import ma.jurika.common.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PasswordPolicy {

    private static final Pattern UPPER = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWER = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};:'\",.<>/?\\\\|`~].*");
    private static final int MIN_LENGTH = 12;

    public void check(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ValidationException("Le mot de passe doit comporter au moins " + MIN_LENGTH + " caracteres");
        }
        if (!UPPER.matcher(password).matches()) {
            throw new ValidationException("Le mot de passe doit contenir au moins une majuscule");
        }
        if (!LOWER.matcher(password).matches()) {
            throw new ValidationException("Le mot de passe doit contenir au moins une minuscule");
        }
        if (!DIGIT.matcher(password).matches()) {
            throw new ValidationException("Le mot de passe doit contenir au moins un chiffre");
        }
        if (!SPECIAL.matcher(password).matches()) {
            throw new ValidationException("Le mot de passe doit contenir au moins un caractere special");
        }
    }
}
