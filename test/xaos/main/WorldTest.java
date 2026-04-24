package xaos.main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTest {

    // -----------------------------------------------------------------------
    // forEachSnapshotReversed — snapshot iteration used in World.nextTurn
    //
    // Protects against ConcurrentModificationException when the action
    // mutates the map during iteration (e.g., Item.delete() removing itself
    // from World.items, LivingEntity.delete() removing from livingsDiscovered).
    // -----------------------------------------------------------------------

    @Test
    void forEachSnapshotReversed_visitsAllEntriesInPopulatedMap() {
        Map<Integer, String> map = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            map.put(i, "value" + i);
        }
        List<String> visited = new ArrayList<>();

        World.forEachSnapshotReversed(map, new Integer[0], (id, value) -> visited.add(value));

        assertEquals(5, visited.size());
        assertTrue(visited.contains("value1"));
        assertTrue(visited.contains("value2"));
        assertTrue(visited.contains("value3"));
        assertTrue(visited.contains("value4"));
        assertTrue(visited.contains("value5"));
    }

    @Test
    void forEachSnapshotReversed_actionSeesMatchingKeyAndValue() {
        Map<Integer, String> map = new HashMap<>();
        map.put(10, "ten");
        map.put(20, "twenty");
        map.put(30, "thirty");
        Map<Integer, String> seen = new HashMap<>();

        World.forEachSnapshotReversed(map, new Integer[0], (id, value) -> seen.put(id, value));

        assertEquals(3, seen.size());
        assertEquals("ten", seen.get(10));
        assertEquals("twenty", seen.get(20));
        assertEquals("thirty", seen.get(30));
    }

    @Test
    void forEachSnapshotReversed_emptyMapNeverCallsAction() {
        Map<Integer, String> empty = new HashMap<>();
        List<String> visited = new ArrayList<>();

        World.forEachSnapshotReversed(empty, new Integer[0], (id, value) -> visited.add(value));

        assertTrue(visited.isEmpty());
    }

    @Test
    void forEachSnapshotReversed_actionRemovingCurrentEntryIsSafe() {
        Map<Integer, String> map = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            map.put(i, "value" + i);
        }
        List<String> visited = new ArrayList<>();

        // Action removes each entry as it sees it. No CME should occur
        // because the snapshot was captured before iteration began.
        World.forEachSnapshotReversed(map, new Integer[0], (id, value) -> {
            visited.add(value);
            map.remove(id);
        });

        assertEquals(5, visited.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void forEachSnapshotReversed_actionRemovingOtherEntriesSkipsNulls() {
        Map<Integer, String> map = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            map.put(i, "value" + i);
        }
        List<String> visited = new ArrayList<>();

        // The first time action fires, it clears the whole map. Subsequent
        // iterations should see null from map.get(id) and be skipped. Only
        // ONE call to action should occur (for the entry visited first).
        World.forEachSnapshotReversed(map, new Integer[0], (id, value) -> {
            visited.add(value);
            map.clear();
        });

        assertEquals(1, visited.size(), "only the first visited entry should trigger action; remaining lookups return null and skip");
    }

    @Test
    void forEachSnapshotReversed_entriesAddedDuringIterationAreNotVisited() {
        Map<Integer, String> map = new HashMap<>();
        map.put(1, "original-1");
        map.put(2, "original-2");
        List<String> visited = new ArrayList<>();

        // Action adds new entries; they must not be visited this call.
        // (They'd be visited on the NEXT invocation.)
        World.forEachSnapshotReversed(map, new Integer[0], (id, value) -> {
            visited.add(value);
            map.put(id + 100, "added-" + id);
        });

        assertEquals(2, visited.size());
        assertTrue(visited.contains("original-1"));
        assertTrue(visited.contains("original-2"));
        for (String v : visited) {
            assertTrue(v.startsWith("original-"), "added entries should not have been visited: " + v);
        }
    }

    @Test
    void forEachSnapshotReversed_bufferGrowsWhenTooSmall() {
        Map<Integer, String> map = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            map.put(i, "v" + i);
        }

        Integer[] tinyBuffer = new Integer[0];
        Integer[] returned = World.forEachSnapshotReversed(map, tinyBuffer, (id, value) -> {});

        assertNotSame(tinyBuffer, returned, "a too-small buffer should be replaced with a new, larger one");
        assertTrue(returned.length >= map.size(),
                "returned buffer must fit all keys: length=" + returned.length + " size=" + map.size());
    }
}
