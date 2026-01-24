package by.losik;

import by.losik.composition.root.CompositionRoot;
import by.losik.service.VoiceReminderService;
import by.losik.service.LambdaHandler;
import by.losik.service.AudioRecorderService;
import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.File;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private static final ExecutorService asyncExecutor = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");

        try {
            Injector injector = Guice.createInjector(new CompositionRoot());
            VoiceReminderService reminderService = injector.getInstance(VoiceReminderService.class);
            AudioRecorderService audioRecorder = injector.getInstance(AudioRecorderService.class);
            LambdaHandler lambdaHandler = injector.getInstance(LambdaHandler.class);

            runConsoleApp(reminderService, audioRecorder, lambdaHandler);

        } catch (Exception e) {
            System.err.println("Ошибка при запуске приложения: " + e.getMessage());
            e.printStackTrace();
        } finally {
            asyncExecutor.shutdown();
        }
    }

    private static void runConsoleApp(VoiceReminderService reminderService,
                                      AudioRecorderService audioRecorder,
                                      LambdaHandler lambdaHandler) {

        Scanner scanner = new Scanner(System.in);

        while (true) {
            try {
                System.out.println("\n=== Голосовые напоминания ===");
                System.out.println("1. Записать голосовое напоминание");
                System.out.println("2. Загрузить аудиофайл с напоминанием");
                System.out.println("3. Посмотреть мои напоминания");
                System.out.println("4. Удалить напоминание");
                System.out.println("5. Деплой лямбда-функции");
                System.out.println("6. Выход");
                System.out.print("\nВыберите действие (1-6): ");

                if (!scanner.hasNext()) {
                    System.out.println("\nКонец ввода. Завершение программы.");
                    break;
                }

                String choice = scanner.next();
                scanner.nextLine();

                System.out.println();

                switch (choice) {
                    case "1" -> recordVoiceReminder(scanner, audioRecorder);
                    case "2" -> uploadAudioFile(scanner, reminderService);
                    case "3" -> listReminders(scanner, reminderService);
                    case "4" -> deleteReminder(scanner, reminderService);
                    case "5" -> deployLambda(scanner, lambdaHandler);
                    case "6" -> {
                        System.out.println("Выход...");
                        scanner.close();
                        return;
                    }
                    default -> System.out.println("Неверный выбор. Введите число от 1 до 6.");
                }

            } catch (Exception e) {
                System.out.println("\nОшибка: " + e.getMessage());
                if (scanner.hasNextLine()) {
                    scanner.nextLine();
                }
            }
        }

        scanner.close();
    }

    private static void recordVoiceReminder(Scanner scanner, AudioRecorderService audioRecorder) {
        System.out.println("=== Запись голосового напоминания ===");

        try {
            System.out.print("Введите ваш ID: ");
            String userId = scanner.nextLine();

            System.out.print("Введите ваш email: ");
            String userEmail = scanner.nextLine();

            System.out.print("Длительность записи (секунды, 5-30): ");
            String durationStr = scanner.nextLine();
            int duration = Integer.parseInt(durationStr);

            if (duration < 5 || duration > 30) {
                System.out.println("Длительность должна быть от 5 до 30 секунд");
                return;
            }

            System.out.println("\nНачинаю запись через 3 секунды...");
            Thread.sleep(1000);
            System.out.println("2...");
            Thread.sleep(1000);
            System.out.println("1...");
            Thread.sleep(1000);
            System.out.println("Записываю!");

            asyncExecutor.submit(() -> {
                try {
                    System.out.println("Обрабатываю запись...");
                    CompletableFuture<String> future = audioRecorder.recordAndProcessReminder(
                            userId, userEmail, duration);

                    String reminderId = future.join();
                    System.out.println("\nНапоминание создано! ID: " + reminderId);

                } catch (Exception e) {
                    System.out.println("\nОшибка при создании напоминания: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    private static void uploadAudioFile(Scanner scanner, VoiceReminderService service) {
        System.out.println("=== Загрузка аудиофайла ===");

        try {
            System.out.print("Введите ваш ID: ");
            String userId = scanner.nextLine();

            System.out.print("Введите ваш email: ");
            String userEmail = scanner.nextLine();

            System.out.print("Введите путь к аудиофайлу (.wav): ");
            String audioPath = scanner.nextLine();

            File audioFile = new File(audioPath);
            if (!audioFile.exists()) {
                System.out.println("Файл не найден!");
                return;
            }

            asyncExecutor.submit(() -> {
                try {
                    System.out.println("Обработка аудио...");
                    CompletableFuture<String> future = service.processVoiceReminder(
                            userId, audioFile, userEmail);

                    String reminderId = future.join();
                    System.out.println("\nНапоминание создано! ID: " + reminderId);

                } catch (Exception e) {
                    System.out.println("\nОшибка при создании напоминания: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    private static void listReminders(Scanner scanner, VoiceReminderService service) {
        System.out.println("=== Мои напоминания ===");

        try {
            System.out.print("Введите ваш ID: ");
            String userId = scanner.nextLine();

            System.out.print("Сколько напоминаний показать (по умолчанию 10): ");
            String limitStr = scanner.nextLine();
            int limit = 10;

            if (!limitStr.isEmpty()) {
                try {
                    limit = Integer.parseInt(limitStr);
                } catch (NumberFormatException e) {
                    System.out.println("Некорректное число. Буду показывать 10 напоминаний.");
                }
            }

            int finalLimit = limit;
            asyncExecutor.submit(() -> {
                try {
                    System.out.println("⏳ Загружаю ваши напоминания...");
                    CompletableFuture<List<by.losik.dto.ReminderRecord>> future =
                            service.getUserReminders(userId, finalLimit);

                    List<by.losik.dto.ReminderRecord> reminders = future.join();

                    if (reminders.isEmpty()) {
                        System.out.println("\nНапоминаний нет");
                    } else {
                        System.out.println("\n=== Ваши напоминания ===");
                        for (by.losik.dto.ReminderRecord reminder : reminders) {
                            System.out.printf("\n ID: %s\n", reminder.reminderId());
                            System.out.printf("   Действие: %s\n", reminder.extractedAction());
                            System.out.printf("   Время: %s\n", reminder.reminderTime());
                            System.out.printf("   Статус: %s\n", reminder.status());
                        }
                        System.out.println("========================");
                    }

                } catch (Exception e) {
                    System.out.println("\nОшибка при загрузке напоминаний: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    private static void deleteReminder(Scanner scanner, VoiceReminderService service) {
        System.out.println("=== Удаление напоминания ===");

        try {
            System.out.print("Введите ID напоминания для удаления: ");
            String reminderId = scanner.nextLine();

            System.out.print("Вы уверены? (да/нет): ");
            String confirm = scanner.nextLine();

            if (confirm.equalsIgnoreCase("да")) {
                asyncExecutor.submit(() -> {
                    try {
                        System.out.println("Удаляю напоминание...");
                        CompletableFuture<Boolean> future = service.deleteReminder(reminderId);

                        boolean success = future.join();
                        if (success) {
                            System.out.println("\nНапоминание удалено");
                        } else {
                            System.out.println("\nНе удалось удалить напоминание");
                        }

                    } catch (Exception e) {
                        System.out.println("\nОшибка при удалении: " + e.getMessage());
                    }
                });
            } else {
                System.out.println("Отмена удаления");
            }

        } catch (Exception e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    private static void deployLambda(Scanner scanner, LambdaHandler lambdaHandler) {
        System.out.println("=== Деплой лямбда-функции ===");

        try {
            System.out.println("Убедитесь, что JAR файл лямбды собран:");
            System.out.println("1. Выполните: ./gradlew buildLambdaJar");
            System.out.println("2. Проверьте наличие файла: build/libs/send-reminder-lambda.jar");
            System.out.print("Продолжить? (да/нет): ");

            String confirm = scanner.nextLine();
            if (!confirm.equalsIgnoreCase("да")) {
                return;
            }

            asyncExecutor.submit(() -> {
                try {
                    System.out.println("⏳ Выполняю деплой...");
                    CompletableFuture<String> future = lambdaHandler.deployLambdaFunction();

                    String functionArn = future.join();
                    if (functionArn != null) {
                        System.out.println("\nЛямбда успешно задеплоена!");
                        System.out.println("   ARN функции: " + functionArn);
                    } else {
                        System.out.println("\nНе удалось деплоить лямбду");
                        System.out.println("   Возможно, лямбда уже существует");
                    }

                } catch (Exception e) {
                    System.out.println("\nОшибка при деплое лямбды: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }
}