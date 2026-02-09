package by.losik.composition.root;

import by.losik.config.EmailConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class MailModule extends AbstractModule {
    @Provides
    @Singleton
    public EmailConfig provideEmailConfig() {
        return new EmailConfig();
    }
}
