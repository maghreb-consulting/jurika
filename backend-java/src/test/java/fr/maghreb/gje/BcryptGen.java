package fr.maghreb.gje;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptGen {
    public static void main(String[] args) {
        System.out.println("HASH_START:" + new BCryptPasswordEncoder().encode("Test1234!") + ":HASH_END");
    }
}
