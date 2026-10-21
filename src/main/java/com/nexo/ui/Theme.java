package com.nexo.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.Border;

public final class Theme {
    public static final Color BACKGROUND = new Color(244, 245, 248);
    public static final Color SURFACE = Color.WHITE;
    public static final Color SURFACE_SUBTLE = new Color(250, 251, 252);
    public static final Color BORDER = new Color(228, 230, 236);
    public static final Color TEXT = new Color(32, 37, 49);
    public static final Color TEXT_MUTED = new Color(145, 151, 164);
    public static final Color PRIMARY = new Color(102, 86, 217);
    public static final Color PRIMARY_DARK = new Color(80, 67, 185);
    public static final Color PRIMARY_SOFT = new Color(240, 238, 254);
    public static final Color GREEN = new Color(41, 154, 115);
    public static final Color GREEN_SOFT = new Color(229, 248, 241);
    public static final Color BLUE = new Color(63, 122, 203);
    public static final Color BLUE_SOFT = new Color(232, 242, 255);
    public static final Color AMBER = new Color(197, 125, 28);
    public static final Color AMBER_SOFT = new Color(255, 242, 220);
    public static final Color RED = new Color(202, 91, 91);
    public static final Color RED_SOFT = new Color(255, 235, 235);

    public static final Font FONT = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font SMALL = FONT.deriveFont(11f);
    public static final Font LABEL = FONT.deriveFont(Font.BOLD, 10f);
    public static final Font BODY_BOLD = FONT.deriveFont(Font.BOLD, 13f);
    public static final Font TITLE = FONT.deriveFont(Font.BOLD, 22f);

    private Theme() {
    }

    public static void install() {
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.font", FONT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Button.font", FONT);
        UIManager.put("TextField.font", FONT);
        UIManager.put("TextArea.font", FONT);
        UIManager.put("ComboBox.font", FONT);
        UIManager.put("Table.font", FONT.deriveFont(12f));
        UIManager.put("TableHeader.font", LABEL);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("OptionPane.messageFont", FONT);
        UIManager.put("OptionPane.buttonFont", FONT);
    }

    public static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    public static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(BODY_BOLD.deriveFont(11f));
        button.setForeground(primary ? Color.WHITE : new Color(95, 102, 116));
        button.setBackground(primary ? PRIMARY : Color.WHITE);
        button.setBorder(primary
                ? BorderFactory.createEmptyBorder(10, 16, 10, 16)
                : compoundBorder(BORDER, 8, 13));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(0, 0, 0, 0));
        return button;
    }

    public static Border compoundBorder(Color color, int vertical, int horizontal) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color),
                BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal));
    }

    public static void transparent(JComponent component) {
        component.setOpaque(false);
    }
}
