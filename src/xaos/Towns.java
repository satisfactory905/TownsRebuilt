package xaos;

import xaos.property.PropertyFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Properties;

import xaos.main.Game;
import xaos.utils.Log;
import xaos.utils.Messages;

import xaos.property.Property;

public final class Towns {

    // Properties in ini files
    public static Properties propertiesMain;
    public static Properties propertiesGraphics;

    // Install root: directory containing towns.ini, audio.ini, graphics.ini, data/.
    // Resolved lazily on first access (or eagerly from main()).
    private static File installRoot;

    /**
     * Computes the install root.
     * - If the code source is a directory (mvn exec:exec from target/classes/), use process CWD.
     * - If it is a JAR (jpackage build), use that JAR's parent directory.
     * - On any exception, fall back to process CWD.
     */
    private static File computeInstallRoot() {
        java.net.URL codeSourceLocation = null;
        try {
            codeSourceLocation = Towns.class.getProtectionDomain().getCodeSource().getLocation();
            File codeSourceFile = new File(codeSourceLocation.toURI());
            File resolved;
            if (codeSourceFile.isDirectory()) {
                resolved = new File(System.getProperty("user.dir"));
            } else {
                // Treat as JAR file: install root is the JAR's parent directory.
                File parent = codeSourceFile.getParentFile();
                resolved = (parent != null) ? parent : new File(System.getProperty("user.dir"));
            }
            Log.log(Log.LEVEL_DEBUG, "Install root: " + resolved.getAbsolutePath()
                    + " (code source: " + codeSourceLocation + "; user.dir: " + System.getProperty("user.dir") + ")", "Towns");
            return resolved;
        } catch (Exception e) {
            Log.log(Log.LEVEL_ERROR, "Failed to resolve install root from code source (" + codeSourceLocation + "): " + e.toString(), "Towns");
            File fallback = new File(System.getProperty("user.dir"));
            Log.log(Log.LEVEL_DEBUG, "Install root (fallback): " + fallback.getAbsolutePath()
                    + " (code source: " + codeSourceLocation + "; user.dir: " + System.getProperty("user.dir") + ")", "Towns");
            return fallback;
        }
    }

    /**
     * Absolute path to the directory containing towns.ini, audio.ini, graphics.ini, and data/.
     * Never returns null.
     */
    public static File getInstallRoot() {
        if (installRoot == null) {
            installRoot = computeInstallRoot();
        }
        return installRoot;
    }

    /**
     * Resolve a path relative to the install root.
     * Equivalent to new File(getInstallRoot(), rel). Returns an absolute File.
     */
    public static File resolveFile(String rel) {
        File f = new File(getInstallRoot(), rel);
        return f.isAbsolute() ? f : f.getAbsoluteFile();
    }

    public static void main(String[] args) {
//		if (true) System.exit (0);
        // Resolve install root eagerly so the log line appears at startup.
        getInstallRoot();
        // Launch the main window
        try {
            new Game();
        } catch (Throwable t) {
            try {
                Writer writer = new StringWriter();
                PrintWriter pw = new PrintWriter(writer);
                t.printStackTrace(pw);
                pw.close();
                writer.close();

                Log.log(Log.LEVEL_ERROR, "Error Code [" + Game.iError + "]", "Towns"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                Log.log(Log.LEVEL_ERROR, writer.toString(), "Towns"); //$NON-NLS-1$
            } catch (Exception e) {
            }

            Game.exit();
        }
    }

    public native boolean SteamAPI_Init();

    /**
     * Loads town.ini
     */
    private static void loadPropertiesMain() {
        // Cargamos el .ini
        propertiesMain = new Properties();

        String sFile = "towns.ini"; //$NON-NLS-1$
        try {
            propertiesMain.load(new FileInputStream(resolveFile(sFile)));
            try {
                propertiesMain.load(new FileInputStream(Game.getUserFolder() + Game.getFileSeparator() + sFile));
            } catch (Exception e) {
            }

            // Absolutize any *_FOLDER value that is currently a relative path so
            // downstream CWD-relative reads still resolve after jpackage.
            absolutizeFolderProperties(propertiesMain);
        } catch (FileNotFoundException e) {
            Log.log(Log.LEVEL_ERROR, "Could not load " + sFile + " from " + resolveFile(sFile).getAbsolutePath(), "Towns"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            Game.exit();
        } catch (IOException e) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("Towns.7"), "Towns"); //$NON-NLS-1$ //$NON-NLS-2$
            Log.log(Log.LEVEL_ERROR, e.toString(), "Towns"); //$NON-NLS-1$
            Game.exit();
        }
    }

    /**
     * Rewrites any property whose value is a relative path under data/ (or data\), making
     * it absolute against the install root. Targets data/ specifically because under the
     * jpackage launcher the process CWD is not the install root, so bare data/... paths
     * fail to resolve. URL-like values (e.g. SERVERS) are left alone because they do not
     * start with data/. USER_FOLDER is a user-home path handled elsewhere. The original
     * trailing-separator shape is preserved so downstream concatenation still works.
     */
    private static void absolutizeFolderProperties(Properties props) {
        if (props == null) {
            return;
        }
        for (String key : props.stringPropertyNames()) {
            if ("USER_FOLDER".equals(key)) {
                continue;
            }
            String value = props.getProperty(key);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (!value.startsWith("data/") && !value.startsWith("data\\")) {
                continue;
            }
            if (new File(value).isAbsolute()) {
                continue;
            }
            boolean hadTrailingSeparator = value.endsWith("/") || value.endsWith("\\");
            String trimmed = value;
            while (trimmed.endsWith("/") || trimmed.endsWith("\\")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            String absoluteValue = resolveFile(trimmed).getAbsolutePath();
            if (hadTrailingSeparator) {
                absoluteValue = absoluteValue + File.separator;
            }
            props.setProperty(key, absoluteValue);
            Log.log(Log.LEVEL_DEBUG, "Resolved " + key + " -> " + absoluteValue, "Towns");
        }
    }

    /**
     * Loads graphics.ini
     */
    private static void loadPropertiesGraphics() {
        // Cargamos el .ini
        propertiesGraphics = new Properties();

        String sFile = "graphics.ini"; //$NON-NLS-1$
        try {
            propertiesGraphics.load(new FileInputStream(resolveFile(sFile)));

            // Defensive: if a mod graphics.ini adds *_FOLDER keys, absolutize them.
            absolutizeFolderProperties(propertiesGraphics);

            // Mods
            File fUserFolder = new File(Game.getUserFolder());
            if (!fUserFolder.exists() || !fUserFolder.isDirectory()) {
                return;
            }

            ArrayList<String> alMods = Game.getModsLoaded();
            if (alMods != null && alMods.size() > 0) {
                for (int i = 0; i < alMods.size(); i++) {
                    String sModGraphicsIniPath = fUserFolder.getAbsolutePath() + System.getProperty("file.separator") + Game.MODS_FOLDER1 + System.getProperty("file.separator") + alMods.get(i) + System.getProperty("file.separator") + "graphics.ini";
                    File fIni = new File(sModGraphicsIniPath);
                    if (fIni.exists()) {
                        propertiesGraphics.load(new FileInputStream(fIni));
                    }
                }
            }
        } catch (FileNotFoundException e) {
            Log.log(Log.LEVEL_ERROR, "Could not load " + sFile + " from " + resolveFile(sFile).getAbsolutePath(), "Towns"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            Game.exit();
        } catch (IOException e) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("Towns.7"), "Towns"); //$NON-NLS-1$ //$NON-NLS-2$
            Log.log(Log.LEVEL_ERROR, e.toString(), "Towns"); //$NON-NLS-1$
            Game.exit();
        }
    }

    public static <T> T getProperty(Property<T> property, T defaultValue) {
        final String rawValue = getPropertiesString(property.getPropertyFile(), property.getKey());
        if (rawValue != null) {
            final T value = property.getPropertyWrapper().wrap(rawValue);
            if (value != null) {
                return value;
            }
        }
        return defaultValue;
    }

    /**
     * Returns a property from main .ini converted to int with default value 0
     *
     * @param sProperty Property name
     * @return a property converted to int with default value 0
     */
    public static int getPropertiesInt(String sProperty) {
        return getPropertiesInt(PropertyFile.PROPERTY_FILE_MAIN, sProperty, 0);
    }

    /**
     * Returns a main property converted to int with default value if error
     *
     * @param sProperty Propery name
     * @param iDefaultValue Default value in case of error
     * @return a property converted to int with default value if error
     */
    public static int getPropertiesInt(String sProperty, int iDefaultValue) {
        return getPropertiesInt(PropertyFile.PROPERTY_FILE_MAIN, sProperty, iDefaultValue);
    }

    /**
     * Returns a property converted to int with default value 0
     *
     * @param propertyFile
     * @param sProperty Property name
     * @return a property converted to int with default value 0
     */
    public static int getPropertiesInt(PropertyFile propertyFile, String sProperty) {
        return getPropertiesInt(propertyFile, sProperty, 0);
    }

    /**
     * Returns a property converted to int with default value if error
     *
     * @param propertyFile
     * @param sProperty Propery name
     * @param iDefaultValue Default value in case of error
     * @return a property converted to int with default value if error
     */
    public static int getPropertiesInt(PropertyFile propertyFile, String sProperty, int iDefaultValue) {
        String sValue = getPropertiesString(propertyFile, sProperty);

        try {
            if (sValue != null) {
                return Integer.parseInt(sValue);
            }
        } catch (NumberFormatException nfe) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("Towns.10") + sProperty + Messages.getString("Towns.11") + sValue + Messages.getString("Towns.12") + iDefaultValue + "]", "Towns"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        }

        return iDefaultValue;
    }

    /**
     * Returns a property from main .ini
     *
     * @param sProperty Property name
     * @return a property from .ini, null if something fails
     */
    public static String getPropertiesString(String sProperty) {
        return getPropertiesString(PropertyFile.PROPERTY_FILE_MAIN, sProperty);
    }

    /**
     *
     * @param propertyFile
     * @param sProperty Property name
     * @return a property from a .ini, null if something fails
     */
    public static String getPropertiesString(PropertyFile propertyFile, String sProperty) {
        if (propertyFile == PropertyFile.PROPERTY_FILE_MAIN && propertiesMain == null) {
            loadPropertiesMain();
        } else if (propertyFile == PropertyFile.PROPERTY_FILE_GRAPHICS && propertiesGraphics == null) {
            loadPropertiesGraphics();
        }

        if (sProperty == null || sProperty.length() == 0) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("Towns.15"), "Towns"); //$NON-NLS-1$ //$NON-NLS-2$
            Game.exit();
        }

        switch (propertyFile) {
            case PROPERTY_FILE_MAIN:
                return propertiesMain.getProperty(sProperty);
            case PROPERTY_FILE_GRAPHICS:
                return propertiesGraphics.getProperty(sProperty);
            default:
                return null;
        }
    }

    public static Properties getPropertiesGraphics() {
        if (propertiesGraphics == null) {
            loadPropertiesGraphics();
        }

        return propertiesGraphics;
    }

    public static void clearPropertiesGraphics() {
        if (propertiesGraphics != null) {
            propertiesGraphics.clear();
        }
        propertiesGraphics = null;
    }
}
