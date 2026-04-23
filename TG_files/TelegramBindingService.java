package by.losik.service;

import by.losik.entity.User;
import by.losik.repository.UserRepository;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Singleton
public class TelegramBindingService {
    private static final Logger log = LoggerFactory.getLogger(TelegramBindingService.class);
    private static final String CODE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    @Inject
    public TelegramBindingService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Map<String, Object> generateBindingCode(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return Map.of("success", false, "error", "User not found");
        }

        User user = userOpt.get();
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }

        String bindingCode = code.toString();
        user.setTelegramBindingCode(bindingCode);
        user.setTelegramCodeExpiry(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES));
        userRepository.update(user);

        log.info("Generated Telegram binding code for user: {}", userId);
        return Map.of(
                "success", true,
                "code", bindingCode,
                "expiresIn", CODE_EXPIRATION_MINUTES
        );
    }

    public Map<String, Object> validateAndBind(Long userId, String bindingCode, Long telegramChatId) {
        if (userId != null) {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return Map.of("success", false, "error", "User not found");
            }

            User user = userOpt.get();

            if (user.getTelegramBindingCode() == null || !user.getTelegramBindingCode().equals(bindingCode)) {
                return Map.of("success", false, "error", "Invalid code");
            }

            if (user.getTelegramCodeExpiry() == null || LocalDateTime.now().isAfter(user.getTelegramCodeExpiry())) {
                return Map.of("success", false, "error", "Code expired");
            }

            user.setTelegramChatId(telegramChatId);
            user.setTelegramBindingCode(null);
            user.setTelegramCodeExpiry(null);
            userRepository.update(user);

            log.info("Telegram bound to user: {} with chatId: {}", userId, telegramChatId);
            return Map.of("success", true, "message", "Telegram linked successfully");
        } else {
            return validateAndBindByChatId(bindingCode, telegramChatId);
        }
    }

    public Map<String, Object> getBindingStatus(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return Map.of("success", false, "error", "User not found");
        }

        User user = userOpt.get();
        boolean linked = user.getTelegramChatId() != null;

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", true);
        result.put("linked", linked);
        if (linked) {
            result.put("chatId", user.getTelegramChatId().toString());
        }
        return result;
    }

    public Map<String, Object> unbindTelegram(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return Map.of("success", false, "error", "User not found");
        }

        User user = userOpt.get();
        user.setTelegramChatId(null);
        userRepository.update(user);

        log.info("Telegram unbound from user: {}", userId);
        return Map.of("success", true, "message", "Telegram unlinked successfully");
    }

    public Map<String, Object> validateAndBindByChatId(String bindingCode, Long telegramChatId) {
        Optional<User> userOpt = userRepository.findByTelegramBindingCode(bindingCode);
        if (userOpt.isEmpty()) {
            return Map.of("success", false, "error", "Invalid code");
        }

        User user = userOpt.get();
        if (user.getTelegramCodeExpiry() == null || LocalDateTime.now().isAfter(user.getTelegramCodeExpiry())) {
            return Map.of("success", false, "error", "Code expired");
        }

        user.setTelegramChatId(telegramChatId);
        user.setTelegramBindingCode(null);
        user.setTelegramCodeExpiry(null);
        userRepository.update(user);

        log.info("Telegram bound to user {} with chatId: {}", user.getId(), telegramChatId);
        return Map.of("success", true, "message", "Telegram linked successfully");
    }

    public Map<String, Object> getBindingStatusByChatId(Long chatId) {
        Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) {
            return Map.of("success", true, "linked", false);
        }

        User user = userOpt.get();
        return Map.of(
                "success", true,
                "linked", true,
                "userId", user.getId(),
                "chatId", chatId.toString()
        );
    }

    public Map<String, Object> unbindTelegramByChatId(Long chatId) {
        Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) {
            return Map.of("success", false, "error", "User not found");
        }

        User user = userOpt.get();
        user.setTelegramChatId(null);
        userRepository.update(user);

        log.info("Telegram unbound from user {} by chatId", user.getId());
        return Map.of("success", true, "message", "Telegram unlinked successfully");
    }
}