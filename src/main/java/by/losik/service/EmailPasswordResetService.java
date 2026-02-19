package by.losik.service;

import by.losik.config.EmailConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

@Singleton
public class EmailPasswordResetService extends EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailPasswordResetService.class);
    private final PasswordResetTokenService tokenService;
    private final String baseUrl;

    @Inject
    public EmailPasswordResetService(EmailConfig emailConfig,
                                     PasswordResetTokenService tokenService,
                                     @Named("base.url")String baseUrl) {
        super(emailConfig);
        this.tokenService = tokenService;
        this.baseUrl = baseUrl;
    }

    public CompletableFuture<String> sendPasswordResetEmail(String toEmail, String username) {
        String resetToken = tokenService.generateToken(username);
        String resetLink = baseUrl + "/reset-password?token=" + resetToken;

        String subject = "Сброс пароля для Voice Reminder";

        String htmlBody = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }
                    .content { padding: 30px; background-color: #f9f9f9; border: 1px solid #ddd; }
                    .button { display: inline-block; padding: 12px 24px; background-color: #4CAF50; color: white;
                             text-decoration: none; border-radius: 5px; margin: 20px 0; }
                    .footer { text-align: center; padding: 20px; color: #777; font-size: 12px; }
                    .warning { background-color: #fff3cd; border: 1px solid #ffeeba; color: #856404; padding: 15px;
                              border-radius: 5px; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Сброс пароля</h1>
                    </div>
                    <div class="content">
                        <p>Здравствуйте, <strong>%s</strong>!</p>
                        <p>Мы получили запрос на сброс пароля для вашей учетной записи в Voice Reminder.</p>
                        
                        <div style="text-align: center;">
                            <a href="%s" class="button">Сбросить пароль</a>
                        </div>
                        
                        <p>Или скопируйте эту ссылку в браузер:</p>
                        <p style="word-break: break-all; background-color: #eee; padding: 10px; border-radius: 3px;">
                            %s
                        </p>
                        
                        <div class="warning">
                            <strong>Важно:</strong>
                            <ul style="margin-top: 10px;">
                                <li>Ссылка действительна в течение 24 часов</li>
                                <li>Если вы не запрашивали сброс пароля, проигнорируйте это письмо</li>
                                <li>Никому не сообщайте эту ссылку</li>
                            </ul>
                        </div>
                        
                        <p>Ссылка была создана: %s</p>
                    </div>
                    <div class="footer">
                        <p>Это письмо отправлено автоматически, пожалуйста, не отвечайте на него.</p>
                        <p>&copy; 2024 Voice Reminder. Все права защищены.</p>
                    </div>
                </div>
            </body>
            </html>
            """, username, resetLink, resetLink,
                new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date()));

        log.info("Sending password reset email to: {}", toEmail);

        log.debug("Reset token for {}: {}", username, resetToken);

        return sendEmail(toEmail, subject, htmlBody, true);
    }

    public String validateResetToken(String token) {
        return tokenService.validateToken(token);
    }

    public CompletableFuture<String> sendPasswordChangedConfirmation(String toEmail, String username) {
        String subject = "Пароль успешно изменен - Voice Reminder";

        String htmlBody = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }
                    .content { padding: 30px; background-color: #f9f9f9; }
                    .success { background-color: #d4edda; border: 1px solid #c3e6cb; color: #155724;
                              padding: 20px; border-radius: 5px; margin: 20px 0; }
                    .footer { text-align: center; padding: 20px; color: #777; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Пароль изменен</h1>
                    </div>
                    <div class="content">
                        <div class="success">
                            <h2>Пароль успешно изменен</h2>
                            <p>Здравствуйте, <strong>%s</strong>!</p>
                            <p>Ваш пароль был успешно изменен.</p>
                        </div>
                        
                        <p>Если вы не совершали это действие, немедленно свяжитесь с поддержкой.</p>
                        <p>Время изменения: %s</p>
                    </div>
                    <div class="footer">
                        <p>Это письмо отправлено автоматически, пожалуйста, не отвечайте на него.</p>
                    </div>
                </div>
            </body>
            </html>
            """, username,
                new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date()));

        return sendEmail(toEmail, subject, htmlBody, true);
    }
}