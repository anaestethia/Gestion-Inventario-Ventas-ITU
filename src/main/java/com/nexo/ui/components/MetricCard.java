package com.nexo.ui.components;

import com.nexo.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class MetricCard extends RoundedPanel {
    private static final long serialVersionUID = 1L;
    private final JLabel valueLabel;

    public MetricCard(String marker, String label, String value, String note, Color tone, Color softTone) {
        setLayout(new BorderLayout(13, 0));
        setBorder(BorderFactory.createEmptyBorder(17, 17, 14, 17));
        setPreferredSize(new Dimension(220, 106));

        RoundedPanel icon = new RoundedPanel(11, softTone, null);
        icon.setPreferredSize(new Dimension(42, 42));
        icon.setLayout(new BorderLayout());
        JLabel markerLabel = Theme.label(marker, Theme.BODY_BOLD.deriveFont(Font.BOLD, 15f), tone);
        markerLabel.setHorizontalAlignment(JLabel.CENTER);
        icon.add(markerLabel);

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.add(Theme.label(label, Theme.SMALL, Theme.TEXT_MUTED));
        copy.add(Box.createVerticalStrut(5));
        valueLabel = Theme.label(value, Theme.FONT.deriveFont(Font.BOLD, 18f), Theme.TEXT);
        copy.add(valueLabel);
        copy.add(Box.createVerticalStrut(7));
        copy.add(Theme.label(note, Theme.SMALL.deriveFont(10f), tone));

        JPanel iconWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        iconWrap.setOpaque(false);
        iconWrap.add(icon);
        add(iconWrap, BorderLayout.WEST);
        add(copy, BorderLayout.CENTER);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }
}
