package ma.jurika.auth.smoke;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/**
 * Smoke test SMTP standalone, lance via JavaMail (meme stack que Spring).
 *
 * <p>Utilisation :
 * <pre>
 * cd backend-java/auth-service
 * mvn -q test-compile exec:java -Dexec.mainClass=ma.jurika.auth.smoke.SmtpSmokeTest -Dexec.classpathScope=test
 * </pre>
 *
 * <p>Lit les variables SMTP_HOST / SMTP_PORT / SMTP_USER / SMTP_PASSWORD /
 * SMTP_FROM / SMTP_TO (defaut = SMTP_USER) depuis les ENV variables. Brevo / Gmail
 * / Mailtrap / Postmark / SES : tout passe via cette stack.
 *
 * <p>Sortie : message d'erreur SMTP exact du serveur (le vrai, pas un wrapping
 * .NET defectueux comme le smoke PowerShell renvoie sur Brevo).
 */
public class SmtpSmokeTest {

    public static void main(String[] args) throws Exception {
        String host = env("SMTP_HOST", "smtp-relay.brevo.com");
        String port = env("SMTP_PORT", "587");
        String user = env("SMTP_USER", null);
        String pass = env("SMTP_PASSWORD", null);
        String from = env("SMTP_FROM", user);
        String fromName = env("SMTP_FROM_NAME", "JURIKA");
        String to = env("SMTP_TO", user);
        boolean auth = Boolean.parseBoolean(env("SMTP_AUTH", "true"));
        boolean starttls = Boolean.parseBoolean(env("SMTP_STARTTLS", "true"));

        if (user == null || pass == null) {
            System.err.println("[FATAL] SMTP_USER ou SMTP_PASSWORD absent des env vars.");
            System.exit(2);
        }

        System.out.println();
        System.out.println("=== JURIKA SMTP smoke (JavaMail) ===");
        System.out.println("Host       : " + host + ":" + port);
        System.out.println("User       : " + user);
        System.out.println("Password   : " + pass.substring(0, Math.min(8, pass.length())) + "... (" + pass.length() + " chars)");
        System.out.println("From       : " + from + " <" + fromName + ">");
        System.out.println("To         : " + to);
        System.out.println("AUTH       : " + auth);
        System.out.println("STARTTLS   : " + starttls);
        System.out.println();

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.starttls.required", String.valueOf(starttls));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        // Activer le debug protocol — affiche EXACTEMENT le dialogue client/serveur
        props.put("mail.debug", "true");

        Session session = Session.getInstance(props, new jakarta.mail.Authenticator() {
            @Override
            protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                return new jakarta.mail.PasswordAuthentication(user, pass);
            }
        });
        session.setDebug(true);

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from, fromName));
        msg.setRecipients(jakarta.mail.Message.RecipientType.TO, to);
        msg.setSubject("JURIKA SMTP smoke test (JavaMail)");
        msg.setText("Test reussi. Si tu lis ceci, ta config SMTP marche.\n\nHost: "
                + host + ":" + port + "\nFrom: " + from);

        try {
            System.out.println("--- DIALOGUE SMTP (mail.debug=true) ---");
            Transport.send(msg);
            System.out.println();
            System.out.println("[OK] MAIL ENVOYE a " + to);
            System.out.println("Verifie ta boite (et le spam).");
            System.exit(0);
        } catch (Exception e) {
            System.out.println();
            System.err.println("[ERREUR JAVA MAIL] " + e.getClass().getSimpleName());
            System.err.println("Message : " + e.getMessage());
            Throwable cause = e.getCause();
            while (cause != null) {
                System.err.println("Cause   : " + cause.getClass().getSimpleName() + " " + cause.getMessage());
                cause = cause.getCause();
            }
            System.exit(1);
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
