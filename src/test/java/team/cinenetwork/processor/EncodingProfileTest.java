package team.cinenetwork.processor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Тесты неизменяемого плана кодирования.
 */
class EncodingProfileTest {

    @Test
    void exposesAllPlanFields() {

        EncodingProfile profile = profile();

        assertEquals(2, profile.variants().size());
        assertEquals(1920, profile.posterWidth());
        assertEquals(1280, profile.mp4Width());
        assertEquals(9000, profile.mp4BitrateKbps());

    }

    @Test
    void bitrateForWidthFindsRungOrZero() {

        EncodingProfile profile = profile();

        assertEquals(8000, profile.bitrateForWidth(1920));
        assertEquals(5000, profile.bitrateForWidth(1280));
        assertEquals(0, profile.bitrateForWidth(640));  // нет такой ступени

    }

    @Test
    void variantsListIsImmutable() {

        List<EncodingProfile.Variant> source = new ArrayList<>(List.of(
                new EncodingProfile.Variant(1920, 8000, "libx264", "high@5.2", "slow", "1080p")));

        EncodingProfile profile = new EncodingProfile(source, 1920, 1920, 12000);
        source.add(new EncodingProfile.Variant(640, 1000, "libx264", "high@4.0", "slow", "240p"));

        // внешняя мутация списка не проникает в профиль
        assertEquals(1, profile.variants().size());
        assertThrows(UnsupportedOperationException.class, () -> profile.variants().add(
                new EncodingProfile.Variant(1, 1, "x", "x", "x", "x")));

    }

    private EncodingProfile profile() {

        return new EncodingProfile(List.of(
                new EncodingProfile.Variant(1920, 8000, "libx264", "high@5.2", "slow", "1080p"),
                new EncodingProfile.Variant(1280, 5000, "libx264", "high@5.1", "slow", "720p")
        ), 1920, 1280, 9000);

    }
}
