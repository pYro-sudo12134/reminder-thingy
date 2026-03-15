package by.losik.config;

public class EmailConfig {
    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String fromEmail;
    private final boolean useSsl;
    private final boolean useTls;

    public EmailConfig(String smtpHost, int smtpPort, String smtpUsername,
                       String smtpPassword, String fromEmail, boolean useSsl, boolean useTls) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.fromEmail = fromEmail;
        this.useSsl = useSsl;
        this.useTls = useTls;
    }
    public String getSmtpHost() { return smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public String getSmtpUsername() { return smtpUsername; }
    public String getSmtpPassword() { return smtpPassword; }
    public String getFromEmail() { return fromEmail; }
    public boolean isUseSsl() { return useSsl; }
    public boolean isUseTls() { return useTls; }
}