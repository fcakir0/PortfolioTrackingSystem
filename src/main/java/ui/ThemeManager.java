package ui;

import java.awt.Color;

/**
 * Tema yönetimi için renk paleti sınıfı
 */
public class ThemeManager {
    
    public enum Theme {
        LIGHT, DARK
    }
    
    private static Theme currentTheme = Theme.LIGHT;
    
    // Açık tema renkleri
    private static final Color LIGHT_BG = new Color(248, 249, 250);
    private static final Color LIGHT_PANEL_BG = Color.WHITE;
    private static final Color LIGHT_BORDER = new Color(220, 224, 230);
    private static final Color LIGHT_TEXT = new Color(33, 37, 41);
    private static final Color LIGHT_TEXT_MUTED = new Color(108, 117, 125);
    private static final Color LIGHT_TABLE_HEADER = new Color(240, 242, 245);
    private static final Color LIGHT_TABLE_ROW_ALT = new Color(248, 249, 250);
    
    // Karanlık tema renkleri
    private static final Color DARK_BG = new Color(30, 30, 30); // #1e1e1e
    private static final Color DARK_PANEL_BG = new Color(40, 40, 40);
    private static final Color DARK_BORDER = new Color(60, 60, 60);
    private static final Color DARK_TEXT = new Color(255, 255, 255);
    private static final Color DARK_TEXT_MUTED = new Color(180, 180, 180);
    private static final Color DARK_TABLE_HEADER = new Color(50, 50, 50);
    private static final Color DARK_TABLE_ROW_ALT = new Color(35, 35, 35);
    
    public static Theme getCurrentTheme() {
        return currentTheme;
    }
    
    public static void setTheme(Theme theme) {
        currentTheme = theme;
    }
    
    public static Color getBackground() {
        return currentTheme == Theme.LIGHT ? LIGHT_BG : DARK_BG;
    }
    
    public static Color getPanelBackground() {
        return currentTheme == Theme.LIGHT ? LIGHT_PANEL_BG : DARK_PANEL_BG;
    }
    
    public static Color getBorder() {
        return currentTheme == Theme.LIGHT ? LIGHT_BORDER : DARK_BORDER;
    }
    
    public static Color getText() {
        return currentTheme == Theme.LIGHT ? LIGHT_TEXT : DARK_TEXT;
    }
    
    public static Color getTextMuted() {
        return currentTheme == Theme.LIGHT ? LIGHT_TEXT_MUTED : DARK_TEXT_MUTED;
    }
    
    public static Color getTableHeader() {
        return currentTheme == Theme.LIGHT ? LIGHT_TABLE_HEADER : DARK_TABLE_HEADER;
    }
    
    public static Color getTableRowAlt() {
        return currentTheme == Theme.LIGHT ? LIGHT_TABLE_ROW_ALT : DARK_TABLE_ROW_ALT;
    }
    
    public static boolean isDarkTheme() {
        return currentTheme == Theme.DARK;
    }
    
    public static void toggleTheme() {
        currentTheme = currentTheme == Theme.LIGHT ? Theme.DARK : Theme.LIGHT;
    }
}

