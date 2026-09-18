package team.cinenetwork.processor.impl.artifact;

import java.io.IOException;

/**
 * Генератор артефакта: независимый самодостаточный этап пайплайна
 * (постер, HLS-видео, аудио-рендишены, субтитры, мастер-плейлист).
 *
 * <p>Паттерн Strategy + «саморегистрация»: процессор не знает, что такое артефакт, —
 * он спрашивает у каждого генератора {@link #isEnabled()} и вызывает {@link #generate()}.
 * Новый артефакт = новый класс без правки {@code VideoProcessor} (OCP).
 */
public interface ArtifactGenerator {

    /** Генерирует артефакт; ошибки IO бросает как {@code IOException}. */
    void generate() throws IOException;

    /** Нужен ли артефакт при текущих опциях и исходнике. */
    boolean isEnabled();

    /** Человекочитаемое имя для логов ("Subtitles (WebVTT)"). */
    String name();

}
