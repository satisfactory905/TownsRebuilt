package xaos.utils;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.lwjgl.glfw.GLFW.*;

public final class InputState {

    // Mouse state
    private static int mouseX, mouseY;
    private static boolean mouseInside;
    private static final Queue<long[]> mouseEventQueue = new ArrayDeque<>();
    // mouse event fields: [x, y, button (-1=scroll), buttonState (1=press,0=release), dwheel]

    // Keyboard state
    private static final boolean[] keyDown = new boolean[GLFW_KEY_LAST + 1];
    private static final Queue<int[]> keyEventQueue = new ArrayDeque<>();
    // key event fields: [glfwKey, state (1=press/repeat, 0=release)]

    // Char input
    private static final Queue<Character> charQueue = new ArrayDeque<>();

    // Current event values
    private static long[] currentMouseEvent;
    private static int[] currentKeyEvent;
    private static char currentChar;

    public static void installCallbacks(long window) {
        glfwSetCursorPosCallback(window, (win, x, y) -> {
            // GLFW reports cursor position in logical window coordinates — the same space
            // everything downstream uses (glOrtho is set to DisplayManager.getWidth()/getHeight(),
            // UI hit-tests are in logical coords). Do NOT scale to framebuffer space here: only
            // glViewport uses framebuffer size, and that is an internal GL detail.
            mouseX = (int) x;
            mouseY = (int) y; // 0=top, increases DOWN
        });
        glfwSetCursorEnterCallback(window, (win, entered) -> mouseInside = entered);
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (action == GLFW_PRESS || action == GLFW_RELEASE) {
                mouseEventQueue.add(new long[]{ mouseX, mouseY, button, action == GLFW_PRESS ? 1 : 0, 0 });
            }
        });
        glfwSetScrollCallback(window, (win, dx, dy) -> {
            mouseEventQueue.add(new long[]{ mouseX, mouseY, -1, 0, dy > 0 ? 1 : -1 });
        });
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key >= 0 && key <= GLFW_KEY_LAST) {
                keyDown[key] = (action != GLFW_RELEASE);
            }
            if (action == GLFW_PRESS || action == GLFW_RELEASE) {
                keyEventQueue.add(new int[]{ key, action == GLFW_PRESS ? 1 : 0 });
            }
        });
        glfwSetCharCallback(window, (win, codepoint) -> charQueue.add((char) codepoint));
    }

    // Mouse event queue
    public static boolean nextMouseEvent() {
        currentMouseEvent = mouseEventQueue.poll();
        return currentMouseEvent != null;
    }
    public static int getEventX()            { return (int) currentMouseEvent[0]; }
    public static int getEventY()            { return (int) currentMouseEvent[1]; } // GLFW: 0=top
    public static int getEventButton()       { return (int) currentMouseEvent[2]; } // -1=scroll
    public static boolean getEventButtonState() { return currentMouseEvent[3] == 1; }
    public static int getEventDWheel()       { return (int) currentMouseEvent[4]; } // +1=up,-1=down

    public static boolean isInsideWindow()   { return mouseInside; }
    // Poll the cursor position directly instead of relying on the callback-cached value so the
    // UI sees up-to-date coordinates every frame even when GLFW has not dispatched a new event.
    // Coordinates are in logical window space (same space as glOrtho and all UI hit-tests).
    public static int getMouseX() {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.DoubleBuffer px = stack.mallocDouble(1);
            java.nio.DoubleBuffer py = stack.mallocDouble(1);
            org.lwjgl.glfw.GLFW.glfwGetCursorPos(xaos.utils.DisplayManager.getWindowHandle(), px, py);
            return (int) px.get(0);
        }
    }
    public static int getMouseY() {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.DoubleBuffer px = stack.mallocDouble(1);
            java.nio.DoubleBuffer py = stack.mallocDouble(1);
            org.lwjgl.glfw.GLFW.glfwGetCursorPos(xaos.utils.DisplayManager.getWindowHandle(), px, py);
            return (int) py.get(0);
        }
    } // GLFW: 0=top, increases DOWN

    // Keyboard event queue
    public static boolean nextKeyEvent() {
        currentKeyEvent = keyEventQueue.poll();
        return currentKeyEvent != null;
    }
    public static int getEventKey()          { return currentKeyEvent[0]; }
    public static boolean getEventKeyState() { return currentKeyEvent[1] == 1; }

    // Char input queue
    public static boolean nextCharEvent() {
        Character c = charQueue.poll();
        if (c == null) return false;
        currentChar = c;
        return true;
    }
    public static char getEventChar()        { return currentChar; }

    // Polling
    public static boolean isKeyDown(int glfwKey) {
        return glfwKey > 0 && glfwKey <= GLFW_KEY_LAST && keyDown[glfwKey];
    }
}
