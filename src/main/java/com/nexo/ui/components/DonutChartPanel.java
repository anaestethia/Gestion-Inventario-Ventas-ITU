package com.nexo.ui.components;

import com.nexo.ui.Theme;
import com.nexo.model.DashboardData;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Font;
import java.util.List;
import javax.swing.JPanel;

public final class DonutChartPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final Color[] COLORS = {
        Theme.PRIMARY, new Color(78, 145, 215), new Color(57, 173, 130),
        new Color(225, 169, 77), new Color(202, 91, 91), new Color(128, 139, 160)
    };

    private final List<DashboardData.CategoryUnits> values;

    public DonutChartPanel() {
        this(List.of());
    }

    public DonutChartPanel(List<DashboardData.CategoryUnits> values) {
        this.values = List.copyOf(values);
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int size = Math.min(getWidth(), getHeight()) - 20;
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;
        g.setStroke(new BasicStroke(Math.max(17, size / 6f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        int start = 90;
        long totalUnits = values.stream().mapToLong(DashboardData.CategoryUnits::units).sum();
        if (totalUnits == 0) {
            g.setColor(Theme.BORDER);
            g.drawArc(x + 10, y + 10, size - 20, size - 20, 0, 360);
        }
        for (int i = 0; i < values.size(); i++) {
            int angle = -(int) Math.round(values.get(i).units() * 360d / Math.max(1, totalUnits));
            g.setColor(COLORS[i % COLORS.length]);
            g.drawArc(x + 10, y + 10, size - 20, size - 20, start, angle);
            start += angle;
        }
        g.setColor(Theme.TEXT);
        g.setFont(Theme.FONT.deriveFont(Font.BOLD, 20f));
        String total = Long.toString(totalUnits);
        int totalWidth = g.getFontMetrics().stringWidth(total);
        g.drawString(total, (getWidth() - totalWidth) / 2, getHeight() / 2);
        g.setColor(Theme.TEXT_MUTED);
        g.setFont(Theme.SMALL.deriveFont(9f));
        String subtitle = "unidades vendidas";
        int subtitleWidth = g.getFontMetrics().stringWidth(subtitle);
        g.drawString(subtitle, (getWidth() - subtitleWidth) / 2, getHeight() / 2 + 16);
        g.dispose();
    }
}
