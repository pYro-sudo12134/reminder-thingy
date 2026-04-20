package by.losik.composition.root;

import by.losik.config.TelegramConfig;
import by.losik.service.TelegramBotService;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class TelegramModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(TelegramConfig.class).in(Singleton.class);
        bind(TelegramBotService.class).in(Singleton.class);
    }
}