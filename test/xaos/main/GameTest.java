package xaos.main;

import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTest {

    @Test
    void collectTextureFileNames_nullProperties_returnsEmptySet() {
        assertTrue(Game.collectTextureFileNames(null).isEmpty());
    }

    @Test
    void collectTextureFileNames_emptyProperties_returnsEmptySet() {
        assertTrue(Game.collectTextureFileNames(new Properties()).isEmpty());
    }

    @Test
    void collectTextureFileNames_singleTextureFile_returnsOneValue() {
        Properties props = new Properties();
        props.setProperty("TEXTURE_FILE", "terrain.png");

        Set<String> result = Game.collectTextureFileNames(props);

        assertEquals(1, result.size());
        assertTrue(result.contains("terrain.png"));
    }

    @Test
    void collectTextureFileNames_duplicateValues_areDeduplicated() {
        Properties props = new Properties();
        props.setProperty("[grass]TEXTURE_FILE", "terrain.png");
        props.setProperty("[dirt]TEXTURE_FILE", "terrain.png");
        props.setProperty("[stone]TEXTURE_FILE", "rocks.png");

        Set<String> result = Game.collectTextureFileNames(props);

        assertEquals(2, result.size());
        assertTrue(result.contains("terrain.png"));
        assertTrue(result.contains("rocks.png"));
    }

    @Test
    void collectTextureFileNames_nonTextureFileKeys_excluded() {
        Properties props = new Properties();
        props.setProperty("TEXTURE_FILE", "terrain.png");
        props.setProperty("AUDIO_FILE", "sound.ogg");
        props.setProperty("WINDOW_WIDTH", "1024");
        props.setProperty("SOMETHING_ELSE", "value");

        Set<String> result = Game.collectTextureFileNames(props);

        assertEquals(1, result.size());
        assertTrue(result.contains("terrain.png"));
    }

    @Test
    void collectTextureFileNames_tilePrefixedKeys_included() {
        Properties props = new Properties();
        props.setProperty("[grass]TEXTURE_FILE", "terrain.png");
        props.setProperty("[grass]TILE_X", "6");
        props.setProperty("[grass]TILE_Y", "0");

        Set<String> result = Game.collectTextureFileNames(props);

        assertEquals(1, result.size());
        assertTrue(result.contains("terrain.png"));
    }
}
