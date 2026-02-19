package by.losik.composition.root;

import by.losik.config.EmailConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;

public class MailModule extends AbstractModule {
    @Override
    public void configure() {
        bind(String.class).annotatedWith(Names.named("secret.key"))
                .toInstance(System.getenv().getOrDefault("SECRET_KEY", ""));
        bind(Long.class).annotatedWith(Names.named("token.expiration"))
                .toInstance(Long.parseLong(
                        System.getenv().getOrDefault("TOKEN_EXPIRATION", "86400000")));
        bind(String.class).annotatedWith(Names.named("issuer"))
                .toInstance(System.getenv().getOrDefault("ISSUER", "voice-reminder-app"));
        bind(String.class).annotatedWith(Names.named("base.url"))
                .toInstance(System.getenv().getOrDefault("APP_BASE_URL", "http://localhost:8090"));
    }

    @Provides
    @Singleton
    public EmailConfig provideEmailConfig() {
        return new EmailConfig();
    }
}
