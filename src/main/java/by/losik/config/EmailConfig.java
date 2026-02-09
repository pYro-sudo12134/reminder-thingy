package by.losik.config;

import com.google.inject.Singleton;

@Singleton
public class EmailConfig {
    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String fromEmail;
    private final boolean useSsl;
    private final boolean useTls;

    public EmailConfig() {
        this.smtpHost = System.getenv("SMTP_HOST") != null ?
                System.getenv("SMTP_HOST") : "smtp.gmail.com";

        this.smtpPort = System.getenv("SMTP_PORT") != null ?
                Integer.parseInt(System.getenv("SMTP_PORT")) : 587;

        this.smtpUsername = System.getenv("SMTP_USERNAME") != null ?
                System.getenv("SMTP_USERNAME") : "losik2006@gmail.com";

        this.smtpPassword = System.getenv("SMTP_PASSWORD") != null ?
                System.getenv("SMTP_PASSWORD") : "your-app-password";

        this.fromEmail = System.getenv("FROM_EMAIL") != null ?
                System.getenv("FROM_EMAIL") : this.smtpUsername;

        this.useSsl = System.getenv("SMTP_SSL") != null && Boolean.parseBoolean(System.getenv("SMTP_SSL"));

        this.useTls = System.getenv("SMTP_TLS") == null || Boolean.parseBoolean(System.getenv("SMTP_TLS"));
    }

    public String getSmtpHost() { return smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public String getSmtpUsername() { return smtpUsername; }
    public String getSmtpPassword() { return smtpPassword; }
    public String getFromEmail() { return fromEmail; }
    public boolean isUseSsl() { return useSsl; }
    public boolean isUseTls() { return useTls; }
}