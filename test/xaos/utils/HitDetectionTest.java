package xaos.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HitDetectionTest {

    @ParameterizedTest(name = "[{index}] ({0},{1}) in rect({2},{3},{4},{5}) -> {6}")
    @CsvSource({
        "15, 25, 10, 20, 10, 10, true",    // strictly inside
        "10, 20, 10, 20, 10, 10, true",    // top-left corner (inclusive)
        "20, 30, 10, 20, 10, 10, false",   // bottom-right corner (exclusive)
        "20, 25, 10, 20, 10, 10, false",   // just past right edge
        "15, 30, 10, 20, 10, 10, false",   // just past bottom edge
        "19, 25, 10, 20, 10, 10, true",    // one pixel inside right edge
        "15, 29, 10, 20, 10, 10, true",    // one pixel inside bottom edge
        "15, 19, 10, 20, 10, 10, false",   // above rect
        "9, 25, 10, 20, 10, 10, false",    // left of rect
        "10, 25, 10, 20, 0, 10, false",    // zero-width rect
        "15, 20, 10, 20, 10, 0, false",    // zero-height rect
        "-5, -5, -10, -10, 10, 10, true"   // negative coords (inside)
    })
    void isPointInRect_boundaryCases(int x, int y, int rectX, int rectY, int rectW, int rectH, boolean expected) {
        assertEquals(expected, HitDetection.isPointInRect(x, y, rectX, rectY, rectW, rectH));
    }
}
