package xaos.panels;

import java.awt.Point;

import org.lwjgl.opengl.GL11;
import static org.lwjgl.glfw.GLFW.*;

import xaos.main.Game;
import xaos.tiles.Tile;
import xaos.utils.ColorGL;
import xaos.utils.Messages;
import xaos.utils.UtilFont;
import xaos.utils.Utils;
import xaos.utils.UtilsGL;

public class TypingPanel {

    public static final int MAX_CHARS = 48;

    public static final int TYPE_NONE = 0;
    public static final int TYPE_RENAME_GROUP = 1;
    public static final int TYPE_REDEFINE_KEYS = 2;
    public static final int TYPE_SAVEGAME_NAME = 3;
    public static final int TYPE_RENAME_JOB_GROUP = 4;
    public static final int TYPE_ADD_SERVER = 5;
    public static final int TYPE_ADD_TEXT_TO_ITEM = 6;

    public static int TYPING_TYPE;
    public static int TYPING_PARAMETER;

    public static int WIDTH = 1024;
    public static int HEIGHT = 600;
    public static int WIDTH_SUBPANEL = 512;
    public static int HEIGHT_SUBPANEL = 300;

    // Text and data
    private static String title;
    private static String oldText;
    private static String newText;

    private static String subTitle;

    // Points
    private static Point panelPoint = new Point(0, 0);
    private static Point panelNewTextPoint = new Point(0, 0);
    private static Point closeButtonPoint = new Point(0, 0);
    private static Point titlePoint = new Point(0, 0);
    private static Point oldTextPoint = new Point(0, 0);
    private static Point newTextPoint = new Point(0, 0);
    private static Point subTitlePoint = new Point(0, 0);
    private static Point confirmPoint = new Point(0, 0);

    // Tiles
    private static Tile[] tilePanel;
    private static Tile[] tilePanelNewText;
    private static Tile tileConfirm;
    private static Tile tileConfirmON;
    private static boolean[][] tileConfirmAlpha;

    public TypingPanel(int renderWidth, int renderHeight, String title, String oldText, int type, int parameter) {
        setTitle(title);
        setOldText(oldText);
        setNewText(new String());
        setSubTitle(null);

        TYPING_TYPE = type;
        TYPING_PARAMETER = parameter;

        createPanel(renderWidth, renderHeight);
    }

    public static void createPanel(int renderWidth, int renderHeight) {
        if (tilePanel == null) {
            tilePanel = new Tile[9]; // Background/N/S/E/W/NE,NW,SE,SW
            tilePanel[0] = new Tile("typing_panel"); //$NON-NLS-1$
            tilePanel[1] = new Tile("typing_panel_N"); //$NON-NLS-1$
            tilePanel[2] = new Tile("typing_panel_S"); //$NON-NLS-1$
            tilePanel[3] = new Tile("typing_panel_E"); //$NON-NLS-1$
            tilePanel[4] = new Tile("typing_panel_W"); //$NON-NLS-1$
            tilePanel[5] = new Tile("typing_panel_NE"); //$NON-NLS-1$
            tilePanel[6] = new Tile("typing_panel_NW"); //$NON-NLS-1$
            tilePanel[7] = new Tile("typing_panel_SE"); //$NON-NLS-1$
            tilePanel[8] = new Tile("typing_panel_SW"); //$NON-NLS-1$
            tilePanelNewText = new Tile[9]; // Background/N/S/E/W/NE,NW,SE,SW
            tilePanelNewText[0] = new Tile("typing_subpanel"); //$NON-NLS-1$
            tilePanelNewText[1] = new Tile("typing_subpanel_N"); //$NON-NLS-1$
            tilePanelNewText[2] = new Tile("typing_subpanel_S"); //$NON-NLS-1$
            tilePanelNewText[3] = new Tile("typing_subpanel_E"); //$NON-NLS-1$
            tilePanelNewText[4] = new Tile("typing_subpanel_W"); //$NON-NLS-1$
            tilePanelNewText[5] = new Tile("typing_subpanel_NE"); //$NON-NLS-1$
            tilePanelNewText[6] = new Tile("typing_subpanel_NW"); //$NON-NLS-1$
            tilePanelNewText[7] = new Tile("typing_subpanel_SE"); //$NON-NLS-1$
            tilePanelNewText[8] = new Tile("typing_subpanel_SW"); //$NON-NLS-1$

            tileConfirm = new Tile("typing_confirm"); //$NON-NLS-1$
            tileConfirmON = new Tile("typing_confirmON"); //$NON-NLS-1$
            tileConfirmAlpha = UtilsGL.generateAlpha(tileConfirm);
        }

        // Width / height
        WIDTH = UtilFont.MAX_WIDTH * MAX_CHARS + (UtilFont.MAX_WIDTH * MAX_CHARS) / 8 + 2 * tilePanel[3].getTileWidth();
        HEIGHT = 2 * tilePanel[1].getTileWidth() + UtilFont.MAX_HEIGHT * 7 + tileConfirm.getTileHeight();
        WIDTH_SUBPANEL = (UtilFont.MAX_WIDTH * MAX_CHARS) + 2 * tilePanelNewText[3].getTileWidth();
        HEIGHT_SUBPANEL = UtilFont.MAX_HEIGHT * 2 + 2 * tilePanel[1].getTileHeight();

        panelPoint.setLocation(renderWidth / 2 - WIDTH / 2, renderHeight / 2 - HEIGHT / 2);
        closeButtonPoint.setLocation(panelPoint.x + WIDTH - UIPanel.tileButtonClose.getTileWidth(), panelPoint.y);
        titlePoint.setLocation(panelPoint.x + WIDTH / 2 - UtilFont.getWidth(getTitle()) / 2, panelPoint.y + tilePanel[1].getTileHeight());
        oldTextPoint.setLocation(panelPoint.x + WIDTH / 2 - UtilFont.getWidth(getOldText()) / 2, titlePoint.y + 2 * UtilFont.MAX_HEIGHT);

        panelNewTextPoint.setLocation(panelPoint.x + WIDTH / 2 - WIDTH_SUBPANEL / 2, oldTextPoint.y + 2 * UtilFont.MAX_HEIGHT);
        updateNewTextPosition();

        confirmPoint.setLocation(panelPoint.x + WIDTH / 2 - tileConfirm.getTileWidth() / 2, panelNewTextPoint.y + HEIGHT_SUBPANEL + UtilFont.MAX_HEIGHT);
    }

    private static void updateNewTextPosition() {
        newTextPoint.setLocation(panelNewTextPoint.x + WIDTH_SUBPANEL / 2 - UtilFont.getWidth(getNewText()) / 2, panelNewTextPoint.y + HEIGHT_SUBPANEL / 2 - UtilFont.MAX_HEIGHT / 2);
    }

    private static void updateSubTitlePosition() {
        if (getSubTitle() != null) {
            subTitlePoint.setLocation(panelPoint.x + WIDTH / 2 - UtilFont.getWidth(getSubTitle()) / 2, titlePoint.y + 2 * UtilFont.MAX_HEIGHT);
        }
    }

    public static int whereIsMouse(int mouseX, int mouseY) {
        if (UIPanel.isMouseOnAnIcon(mouseX, mouseY, closeButtonPoint, UIPanel.tileButtonClose, UIPanel.tileButtonCloseAlpha)) {
            return UIPanel.MOUSE_TYPING_PANEL_CLOSE;
        } else if (UIPanel.isMouseOnAnIcon(mouseX, mouseY, confirmPoint, tileConfirm, tileConfirmAlpha)) {
            return UIPanel.MOUSE_TYPING_PANEL_CONFIRM;
        }

        return UIPanel.MOUSE_NONE;
    }

    /**
     * Determina donde esta el mouse y llama al render (mousePanel) Se usa, de
     * momento, desde el main menu, para teclear el nombre de la partida grabada
     */
    public static void render(int mouseX, int mouseY) {
        render(whereIsMouse(mouseX, mouseY));
    }

    public static void render(int mousePanel) {
        // Fondo
        GL11.glColor4f(1, 1, 1, 1);
        int iCurrentTexture = tilePanel[0].getTextureID();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, iCurrentTexture);
        GL11.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        UtilsGL.glBegin(GL11.GL_QUADS);
        UIPanel.renderBackground(tilePanel, panelPoint, WIDTH, HEIGHT);

        // Close button
        if (mousePanel == UIPanel.MOUSE_TYPING_PANEL_CLOSE) {
            iCurrentTexture = UtilsGL.setTexture(UIPanel.tileButtonClose, iCurrentTexture);
            UIPanel.drawTile(UIPanel.tileButtonClose, closeButtonPoint);
        } else {
            iCurrentTexture = UtilsGL.setTexture(UIPanel.tileButtonCloseDisabled, iCurrentTexture);
            UIPanel.drawTile(UIPanel.tileButtonCloseDisabled, closeButtonPoint);
        }

        // Subpanel
        iCurrentTexture = UtilsGL.setTexture(tilePanelNewText[0], iCurrentTexture);
        UIPanel.renderBackground(tilePanelNewText, panelNewTextPoint, WIDTH_SUBPANEL, HEIGHT_SUBPANEL);

        // Confirm
        if (TYPING_TYPE != TYPE_REDEFINE_KEYS) {
            if (mousePanel == UIPanel.MOUSE_TYPING_PANEL_CONFIRM) {
                iCurrentTexture = UtilsGL.setTexture(tileConfirmON, iCurrentTexture);
                UIPanel.drawTile(tileConfirmON, confirmPoint);
            } else {
                iCurrentTexture = UtilsGL.setTexture(tileConfirm, iCurrentTexture);
                UIPanel.drawTile(tileConfirm, confirmPoint);
            }
        }

        UtilsGL.glEnd();

        // Text
        iCurrentTexture = Game.TEXTURE_FONT_ID;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, Game.TEXTURE_FONT_ID);
        GL11.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        UtilsGL.glBegin(GL11.GL_QUADS);

        // Title
        UtilsGL.drawStringWithBorder(getTitle(), titlePoint.x, titlePoint.y, ColorGL.WHITE, ColorGL.BLACK);

        // SubTitle
        if (getSubTitle() != null) {
            UtilsGL.drawStringWithBorder(getSubTitle(), subTitlePoint.x, subTitlePoint.y, ColorGL.RED, ColorGL.BLACK);
        }

        // OldText
        UtilsGL.drawStringWithBorder(getOldText(), oldTextPoint.x, oldTextPoint.y, ColorGL.YELLOW, ColorGL.BLACK);

        // New text
        UtilsGL.drawStringWithBorder(getNewText(), newTextPoint.x, newTextPoint.y, ColorGL.WHITE, ColorGL.BLACK);

        // Cursor
        UtilsGL.drawStringWithBorder("_", newTextPoint.x + UtilFont.getWidth(getNewText()), newTextPoint.y, ColorGL.WHITE, ColorGL.BLACK); //$NON-NLS-1$

        UtilsGL.glEnd();
    }

    /**
     * Anade un caracter al texto y retorna true en caso de que ya haya
     * terminado.
     *
     * @param key
     * @return
     */
    public static boolean keyPressed(int key) {
        return keyPressed(key, false);
    }

    /**
     * Anade un caracter al texto y retorna true en caso de que ya haya
     * terminado.
     *
     * @param key
     * @param shift
     * @return
     */
    public static boolean keyPressed(int key, boolean shift) {
        if (TYPING_TYPE == TYPE_REDEFINE_KEYS) {
            if (key == GLFW_KEY_ESCAPE) {
                return true;
            }
            if (key == GLFW_KEY_LEFT_SHIFT || key == GLFW_KEY_RIGHT_SHIFT) {
                return false;
            }

            setNewText(Integer.toString(key));
            return true;
        }

        if (key == GLFW_KEY_BACKSPACE || key == GLFW_KEY_DELETE) {
            if (getNewText().length() > 0) {
                setNewText(getNewText().substring(0, getNewText().length() - 1));
            }
        } else if (key == GLFW_KEY_ESCAPE) {
            setNewText(getOldText());
            return true;
        } else if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) {
            String sNewName = getNewText().trim();
            setNewText(sNewName);

            if (getNewText().length() == 0) {
                setNewText(getOldText());
            }
            return true;
        }

        return false;
    }

    /**
     * Anade un caracter al texto tecleado (llamado desde el char callback de GLFW).
     * Retorna false (escritura no completada).
     *
     * @param c
     * @return
     */
    public static boolean charInput(char c) {
        if (c >= 32 && c < 127) {  // printable ASCII
            if (getNewText().length() < MAX_CHARS) {
                if (c == ' ') {
                    // Si ya tenia un espacio al final pasamos de este
                    if (getNewText().length() > 0) {
                        if (getNewText().charAt(getNewText().length() - 1) != ' ') {
                            setNewText(getNewText() + c);
                        }
                    }
                } else {
                    setNewText(getNewText() + c);
                }
            }
        }
        return false;
    }

    public static String getTitle() {
        return title;
    }

    public static void setTitle(String title) {
        TypingPanel.title = title;
    }

    public static String getOldText() {
        return oldText;
    }

    public static void setOldText(String oldText) {
        TypingPanel.oldText = oldText;
    }

    public static String getNewText() {
        return newText;
    }

    public static void setNewText(String newText) {
        TypingPanel.newText = newText;

        // En el caso de que este poniendo el nombre de una partida, miraremos que no exista en disco
        if (TYPING_TYPE == TYPE_SAVEGAME_NAME) {
            if (Utils.existsSavegame(getNewText())) {
                setSubTitle(Messages.getString("TypingPanel.1")); //$NON-NLS-1$
            } else {
                setSubTitle(null);
            }
        }

        updateNewTextPosition();
    }

    public static void setSubTitle(String subTitle) {
        TypingPanel.subTitle = subTitle;
        updateSubTitlePosition();
    }

    public static String getSubTitle() {
        return subTitle;
    }

    public static Point getPanelPoint() {
        return panelPoint;
    }

    public static void setPanelPoint(Point panelPoint) {
        TypingPanel.panelPoint = panelPoint;
    }

    public static Point getPanelNewTextPoint() {
        return panelNewTextPoint;
    }

    public static void setPanelNewTextPoint(Point panelNewTextPoint) {
        TypingPanel.panelNewTextPoint = panelNewTextPoint;
    }

    public static Point getCloseButtonPoint() {
        return closeButtonPoint;
    }

    public static void setCloseButtonPoint(Point closeButtonPoint) {
        TypingPanel.closeButtonPoint = closeButtonPoint;
    }

    public static Point getTitlePoint() {
        return titlePoint;
    }

    public static void setTitlePoint(Point titlePoint) {
        TypingPanel.titlePoint = titlePoint;
    }

    public static Point getOldTextPoint() {
        return oldTextPoint;
    }

    public static void setOldTextPoint(Point oldTextPoint) {
        TypingPanel.oldTextPoint = oldTextPoint;
    }

    public static Point getNewTextPoint() {
        return newTextPoint;
    }

    public static void setNewTextPoint(Point newTextPoint) {
        TypingPanel.newTextPoint = newTextPoint;
    }

    public static Point getConfirmPoint() {
        return confirmPoint;
    }

    public static void setConfirmPoint(Point confirmPoint) {
        TypingPanel.confirmPoint = confirmPoint;
    }

    public static Tile getTileConfirm() {
        return tileConfirm;
    }

    public static void setTileConfirm(Tile tileConfirm) {
        TypingPanel.tileConfirm = tileConfirm;
    }

    public static boolean[][] getTileConfirmAlpha() {
        return tileConfirmAlpha;
    }

    public static void setTileConfirmAlpha(boolean[][] tileConfirmAlpha) {
        TypingPanel.tileConfirmAlpha = tileConfirmAlpha;
    }
}
