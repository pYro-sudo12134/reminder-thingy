package by.losik.test;

import org.mindrot.jbcrypt.BCrypt;

public class GenerateHash {
    public static void main(String[] args) {
        String hash = BCrypt.hashpw("password", BCrypt.gensalt());
        System.out.println("Hash for 'password': " + hash);
    }
}
