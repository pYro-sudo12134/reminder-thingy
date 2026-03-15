package by.losik.composition.root;

import by.losik.config.EmailConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class MailModule extends AbstractModule {
    @Override
    public void configure() {
        bind(String.class).annotatedWith(Names.named("secret.key"))
                .toInstance(System.getenv().getOrDefault("SECRET_KEY", "default-secret-key"));
        bind(Long.class).annotatedWith(Names.named("token.expiration"))
                .toInstance(Long.parseLong(
                        System.getenv().getOrDefault("TOKEN_EXPIRATION", "86400000")));
        bind(String.class).annotatedWith(Names.named("issuer"))
                .toInstance(System.getenv().getOrDefault("ISSUER", "voice-reminder-app"));
        bind(String.class).annotatedWith(Names.named("base.url"))
                .toInstance(System.getenv().getOrDefault("APP_BASE_URL", "http://localhost:8090"));
        bind(String.class).annotatedWith(Names.named("smtp.host"))
                .toInstance(System.getenv().getOrDefault("SMTP_HOST", "smtp.gmail.com"));
        bind(Integer.class).annotatedWith(Names.named("smtp.port"))
                .toInstance(Integer.parseInt(
                        System.getenv().getOrDefault("SMTP_PORT", "587")));
        bind(String.class).annotatedWith(Names.named("smtp.username"))
                .toInstance(System.getenv().getOrDefault("SMTP_USERNAME", "losik2006@gmail.com"));
        bind(String.class).annotatedWith(Names.named("smtp.password"))
                .toInstance(System.getenv().getOrDefault("SMTP_PASSWORD", "your-app-password"));
        bind(String.class).annotatedWith(Names.named("smtp.from.email"))
                .toInstance(System.getenv().getOrDefault("FROM_EMAIL",
                        System.getenv().getOrDefault("SMTP_USERNAME", "losik2006@gmail.com")));
        bind(Boolean.class).annotatedWith(Names.named("smtp.ssl"))
                .toInstance(Boolean.parseBoolean(System.getenv().getOrDefault("SMTP_SSL", "false")));
        bind(Boolean.class).annotatedWith(Names.named("smtp.tls"))
                .toInstance(Boolean.parseBoolean(System.getenv().getOrDefault("SMTP_TLS", "true")));
    }

    @Provides
    @Singleton
    public EmailConfig provideEmailConfig(
            @Named("smtp.host") String smtpHost,
            @Named("smtp.port") int smtpPort,
            @Named("smtp.username") String smtpUsername,
            @Named("smtp.password") String smtpPassword,
            @Named("smtp.from.email") String fromEmail,
            @Named("smtp.ssl") boolean useSsl,
            @Named("smtp.tls") boolean useTls) {

        return new EmailConfig(smtpHost, smtpPort, smtpUsername,
                smtpPassword, fromEmail, useSsl, useTls);
    }
}