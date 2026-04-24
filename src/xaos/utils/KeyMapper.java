package xaos.utils;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

public final class KeyMapper {

    public static final int KEY_NONE = 0;

    private static final Map<String, Integer> nameToGlfw = new HashMap<>();
    private static final Map<Integer, String> glfwToName = new HashMap<>();

    static {
        // Letters
        reg("A", GLFW_KEY_A); reg("B", GLFW_KEY_B); reg("C", GLFW_KEY_C);
        reg("D", GLFW_KEY_D); reg("E", GLFW_KEY_E); reg("F", GLFW_KEY_F);
        reg("G", GLFW_KEY_G); reg("H", GLFW_KEY_H); reg("I", GLFW_KEY_I);
        reg("J", GLFW_KEY_J); reg("K", GLFW_KEY_K); reg("L", GLFW_KEY_L);
        reg("M", GLFW_KEY_M); reg("N", GLFW_KEY_N); reg("O", GLFW_KEY_O);
        reg("P", GLFW_KEY_P); reg("Q", GLFW_KEY_Q); reg("R", GLFW_KEY_R);
        reg("S", GLFW_KEY_S); reg("T", GLFW_KEY_T); reg("U", GLFW_KEY_U);
        reg("V", GLFW_KEY_V); reg("W", GLFW_KEY_W); reg("X", GLFW_KEY_X);
        reg("Y", GLFW_KEY_Y); reg("Z", GLFW_KEY_Z);
        // Digits
        reg("0", GLFW_KEY_0); reg("1", GLFW_KEY_1); reg("2", GLFW_KEY_2);
        reg("3", GLFW_KEY_3); reg("4", GLFW_KEY_4); reg("5", GLFW_KEY_5);
        reg("6", GLFW_KEY_6); reg("7", GLFW_KEY_7); reg("8", GLFW_KEY_8);
        reg("9", GLFW_KEY_9);
        // Function keys
        reg("F1", GLFW_KEY_F1);   reg("F2", GLFW_KEY_F2);   reg("F3", GLFW_KEY_F3);
        reg("F4", GLFW_KEY_F4);   reg("F5", GLFW_KEY_F5);   reg("F6", GLFW_KEY_F6);
        reg("F7", GLFW_KEY_F7);   reg("F8", GLFW_KEY_F8);   reg("F9", GLFW_KEY_F9);
        reg("F10", GLFW_KEY_F10); reg("F11", GLFW_KEY_F11); reg("F12", GLFW_KEY_F12);
        // Special
        reg("ESCAPE", GLFW_KEY_ESCAPE);
        reg("RETURN", GLFW_KEY_ENTER);
        reg("BACK", GLFW_KEY_BACKSPACE);
        reg("TAB", GLFW_KEY_TAB);
        reg("SPACE", GLFW_KEY_SPACE);
        reg("DELETE", GLFW_KEY_DELETE);
        reg("INSERT", GLFW_KEY_INSERT);
        reg("HOME", GLFW_KEY_HOME);
        reg("END", GLFW_KEY_END);
        reg("PRIOR", GLFW_KEY_PAGE_UP);
        reg("NEXT", GLFW_KEY_PAGE_DOWN);
        // Arrow keys
        reg("UP", GLFW_KEY_UP);
        reg("DOWN", GLFW_KEY_DOWN);
        reg("LEFT", GLFW_KEY_LEFT);
        reg("RIGHT", GLFW_KEY_RIGHT);
        // Modifiers
        reg("LSHIFT", GLFW_KEY_LEFT_SHIFT);
        reg("RSHIFT", GLFW_KEY_RIGHT_SHIFT);
        reg("LCONTROL", GLFW_KEY_LEFT_CONTROL);
        reg("RCONTROL", GLFW_KEY_RIGHT_CONTROL);
        reg("LALT", GLFW_KEY_LEFT_ALT);
        reg("RALT", GLFW_KEY_RIGHT_ALT);
        reg("LMETA", GLFW_KEY_LEFT_SUPER);
        reg("RMETA", GLFW_KEY_RIGHT_SUPER);
        // Numpad
        reg("NUMPAD0", GLFW_KEY_KP_0); reg("NUMPAD1", GLFW_KEY_KP_1);
        reg("NUMPAD2", GLFW_KEY_KP_2); reg("NUMPAD3", GLFW_KEY_KP_3);
        reg("NUMPAD4", GLFW_KEY_KP_4); reg("NUMPAD5", GLFW_KEY_KP_5);
        reg("NUMPAD6", GLFW_KEY_KP_6); reg("NUMPAD7", GLFW_KEY_KP_7);
        reg("NUMPAD8", GLFW_KEY_KP_8); reg("NUMPAD9", GLFW_KEY_KP_9);
        reg("NUMPADENTER", GLFW_KEY_KP_ENTER);
        reg("ADD", GLFW_KEY_KP_ADD);
        reg("SUBTRACT", GLFW_KEY_KP_SUBTRACT);
        reg("MULTIPLY", GLFW_KEY_KP_MULTIPLY);
        reg("DIVIDE", GLFW_KEY_KP_DIVIDE);
        reg("DECIMAL", GLFW_KEY_KP_DECIMAL);
        // Punctuation / symbols
        reg("MINUS", GLFW_KEY_MINUS);
        reg("EQUALS", GLFW_KEY_EQUAL);
        reg("LBRACKET", GLFW_KEY_LEFT_BRACKET);
        reg("RBRACKET", GLFW_KEY_RIGHT_BRACKET);
        reg("SEMICOLON", GLFW_KEY_SEMICOLON);
        reg("APOSTROPHE", GLFW_KEY_APOSTROPHE);
        reg("COMMA", GLFW_KEY_COMMA);
        reg("PERIOD", GLFW_KEY_PERIOD);
        reg("SLASH", GLFW_KEY_SLASH);
        reg("BACKSLASH", GLFW_KEY_BACKSLASH);
        reg("GRAVE", GLFW_KEY_GRAVE_ACCENT);
        // Lock / utility
        reg("CAPITAL", GLFW_KEY_CAPS_LOCK);
        reg("NUMLOCK", GLFW_KEY_NUM_LOCK);
        reg("SCROLL", GLFW_KEY_SCROLL_LOCK);
        reg("PAUSE", GLFW_KEY_PAUSE);
        reg("SYSRQ", GLFW_KEY_PRINT_SCREEN);
    }

    private static void reg(String name, int glfwKey) {
        nameToGlfw.put(name, glfwKey);
        glfwToName.put(glfwKey, name);
    }

    /** Maps an LWJGL2 key name (e.g. "ESCAPE", "F1", "LSHIFT") to a GLFW key code. Returns KEY_NONE if unknown. */
    public static int fromName(String name) {
        if (name == null || name.isEmpty()) return KEY_NONE;
        Integer k = nameToGlfw.get(name.toUpperCase());
        return k != null ? k : KEY_NONE;
    }

    /** Maps a GLFW key code to a name string (e.g. GLFW_KEY_ESCAPE -> "ESCAPE"). Returns empty string if unknown. */
    public static String toName(int glfwKey) {
        if (glfwKey == KEY_NONE) return "";
        String name = glfwToName.get(glfwKey);
        return name != null ? name : "";
    }
}
