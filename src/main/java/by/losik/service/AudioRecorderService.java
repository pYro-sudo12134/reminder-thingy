package by.losik.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class AudioRecorderService {
    private static final Logger log = LoggerFactory.getLogger(AudioRecorderService.class);
    private final VoiceReminderService voiceReminderService;
    private TargetDataLine line;
    private AudioFormat format;
    private final AtomicBoolean recording;
    private ByteArrayOutputStream audioStream;

    @Inject
    public AudioRecorderService(VoiceReminderService voiceReminderService) {
        this.voiceReminderService = voiceReminderService;
        this.recording = new AtomicBoolean(false);
        initAudioFormat();
    }

    private void initAudioFormat() {
        float sampleRate = 16000;
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;

        format = new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    public CompletableFuture<File> startRecording(String userId, int durationSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

                if (!AudioSystem.isLineSupported(info)) {
                    throw new RuntimeException("Микрофон не поддерживается");
                }

                line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format);
                audioStream = new ByteArrayOutputStream();

                log.info("Начинаю запись... Говорите!");
                System.out.println("🎤 Запись началась. Говорите ваше напоминание...");

                recording.set(true);
                line.start();

                byte[] buffer = new byte[4096];
                int bytesRead;
                long startTime = System.currentTimeMillis();

                while (recording.get() &&
                        (System.currentTimeMillis() - startTime) < durationSeconds * 1000L) {
                    bytesRead = line.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        audioStream.write(buffer, 0, bytesRead);
                    }
                }

                stopRecording();

                System.out.println("✅ Запись завершена");
                log.info("Запись завершена, размер: {} байт", audioStream.size());

                try {
                    return saveAudioToFile(userId);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            } catch (LineUnavailableException | SecurityException e) {
                log.error("Ошибка при записи аудио", e);
                throw new RuntimeException("Не удалось начать запись", e);
            }
        });
    }

    public void stopRecording() {
        if (recording.compareAndSet(true, false)) {
            if (line != null) {
                line.stop();
                line.close();
                line = null;
            }

            if (audioStream != null) {
                try {
                    audioStream.close();
                } catch (IOException e) {
                    log.warn("Ошибка при закрытии потока аудио", e);
                }
            }
        }
    }

    private File saveAudioToFile(String userId) throws IOException {
        byte[] audioData = audioStream.toByteArray();

        if (audioData.length == 0) {
            throw new RuntimeException("Аудио данные отсутствуют");
        }

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String fileName = String.format("recording_%s_%s.wav", userId, timestamp);
        File audioFile = new File(System.getProperty("java.io.tmpdir"), fileName);

        AudioInputStream rawStream = new AudioInputStream(
                new ByteArrayInputStream(audioData),
                format,
                audioData.length / format.getFrameSize()
        );

        AudioSystem.write(rawStream, AudioFileFormat.Type.WAVE, audioFile);
        rawStream.close();

        log.info("Аудио сохранено в файл: {}", audioFile.getAbsolutePath());
        return audioFile;
    }

    public CompletableFuture<String> recordAndProcessReminder(String userId, String userEmail, int durationSeconds) {
        return startRecording(userId, durationSeconds)
                .thenCompose(audioFile -> {
                    System.out.println("📡 Обрабатываю аудио...");
                    return voiceReminderService.processVoiceReminder(userId, audioFile, userEmail)
                            .thenApply(reminderId -> {
                                if (audioFile.exists()) {
                                    audioFile.delete();
                                }
                                return reminderId;
                            });
                })
                .exceptionally(ex -> {
                    log.error("Ошибка при обработке записи", ex);
                    throw new RuntimeException("Не удалось обработать запись", ex);
                });
    }

    public boolean isRecording() {
        return recording.get();
    }

    public int getAudioFormatSampleRate() {
        return (int) format.getSampleRate();
    }

    public int getAudioFormatChannels() {
        return format.getChannels();
    }
}