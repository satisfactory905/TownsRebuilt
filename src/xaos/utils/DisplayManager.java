package xaos.utils;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public final class DisplayManager {

    private static long window = NULL;
    private static int width;
    private static int height;
    private static int fbWidth;
    private static int fbHeight;
    private static boolean fullscreen;
    private static int lastWindowedWidth;
    private static int lastWindowedHeight;
    private static boolean resized;
    private static long lastSyncTime = System.nanoTime();
    private static boolean glfwReady = false;

    /** Initializes GLFW without creating a window. Safe to call multiple times. */
    private static void ensureGlfw() {
        if (!glfwReady) {
            if (!glfwInit()) throw new RuntimeException("Unable to initialize GLFW");
            glfwReady = true;
        }
    }

    public static void init(int w, int h, boolean fs) {
        ensureGlfw();
        fullscreen = fs;
        // Preserve the caller's requested size as the "last windowed" dimensions so toggling
        // fullscreen off later restores a sensible windowed size.
        lastWindowedWidth = w; lastWindowedHeight = h;

        // When going fullscreen, ignore the caller-supplied w/h and use the primary monitor's
        // current video mode — matches the pre-phase-1 LWJGL 2 behavior (Display.getDesktopDisplayMode)
        // and mirrors what toggleFullscreen() already does below.
        int createW = w, createH = h, refresh = GLFW_DONT_CARE;
        long monitor = NULL;
        if (fs) {
            monitor = glfwGetPrimaryMonitor();
            GLFWVidMode vm = glfwGetVideoMode(monitor);
            if (vm != null) {
                createW = vm.width();
                createH = vm.height();
                refresh = vm.refreshRate();
                Log.log(Log.LEVEL_DEBUG, "init(): fullscreen=true, using monitor vidmode " + createW + "x" + createH + "@" + refresh + "Hz (caller requested " + w + "x" + h + ")", "DisplayManager");
            } else {
                Log.log(Log.LEVEL_ERROR, "init(): fullscreen=true but glfwGetVideoMode returned null; falling back to requested " + w + "x" + h, "DisplayManager");
            }
        }
        width = createW; height = createH;

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        if (refresh != GLFW_DONT_CARE) {
            glfwWindowHint(GLFW_REFRESH_RATE, refresh);
        }

        window = glfwCreateWindow(createW, createH, "Towns", monitor, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        // Enforce a minimum window size so the user cannot drag the window smaller than the UI supports.
        // With this in place, handleResize() no longer needs to re-init the window when it goes undersized.
        glfwSetWindowSizeLimits(window, xaos.main.Game.MIN_DISPLAY_WIDTH, xaos.main.Game.MIN_DISPLAY_HEIGHT, GLFW_DONT_CARE, GLFW_DONT_CARE);

        // Center window on screen when windowed
        if (!fs) {
            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidMode != null) {
                glfwSetWindowPos(window, (vidMode.width() - createW) / 2, (vidMode.height() - createH) / 2);
            }
        }

        glfwSetWindowSizeCallback(window, (win, nw, nh) -> {
            width = nw; height = nh; resized = true;
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // VSync
        GL.createCapabilities();

        // Track physical framebuffer size (may differ from logical window size on HiDPI).
        // glViewport uses framebuffer size; glOrtho uses logical window size.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer fw = stack.mallocInt(1);
            java.nio.IntBuffer fh = stack.mallocInt(1);
            glfwGetFramebufferSize(window, fw, fh);
            fbWidth = fw.get(0);
            fbHeight = fh.get(0);
        }

        glfwSetFramebufferSizeCallback(window, (win, nw, nh) -> {
            fbWidth = nw; fbHeight = nh;
        });

        glfwShowWindow(window);

    }

    public static void destroy() {
        if (window != NULL) { glfwDestroyWindow(window); window = NULL; }
        glfwTerminate();
    }

    public static boolean isCloseRequested() { return glfwWindowShouldClose(window); }

    public static void swapAndPoll() {
        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    public static void sync(int fps) {
        long targetNanos = 1_000_000_000L / fps;
        long sleepNanos = targetNanos - (System.nanoTime() - lastSyncTime);
        if (sleepNanos > 1_000_000L) {
            try { Thread.sleep(sleepNanos / 1_000_000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        lastSyncTime = System.nanoTime();
    }

    public static int getWidth()  { return width; }
    public static int getHeight() { return height; }
    public static int getFramebufferWidth()  { return fbWidth > 0 ? fbWidth : width; }
    public static int getFramebufferHeight() { return fbHeight > 0 ? fbHeight : height; }
    public static boolean isFullscreen() { return fullscreen; }
    public static long getWindowHandle() { return window; }

    public static int getDesktopWidth() {
        ensureGlfw();
        GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
        return vm != null ? vm.width() : 1920;
    }
    public static int getDesktopHeight() {
        ensureGlfw();
        GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
        return vm != null ? vm.height() : 1080;
    }

    public static boolean wasResized() {
        if (resized) { resized = false; return true; }
        return false;
    }

    public static void toggleFullscreen() {
        fullscreen = !fullscreen;
        if (fullscreen) {
            lastWindowedWidth = width; lastWindowedHeight = height;
            GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vm != null) glfwSetWindowMonitor(window, glfwGetPrimaryMonitor(), 0, 0, vm.width(), vm.height(), vm.refreshRate());
        } else {
            glfwSetWindowMonitor(window, NULL, 0, 0, lastWindowedWidth, lastWindowedHeight, GLFW_DONT_CARE);
            GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vm != null) glfwSetWindowPos(window, (vm.width() - lastWindowedWidth) / 2, (vm.height() - lastWindowedHeight) / 2);
        }
    }

    public static void setTitle(String title) { if (window != NULL) glfwSetWindowTitle(window, title); }
}
