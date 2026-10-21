package com.nexo.ui.components;

import com.nexo.ui.Theme;
import com.nexo.model.DashboardData;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.JPanel;

public final class BarChartPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final List<DashboardData.DailyRevenue> values;

    public BarChartPanel() {
        this(List.of());
    }

    public BarChartPanel(List<DashboardData.DailyRevenue> values) {
        this.values = List.copyOf(values);
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int left = 44;
        int right = getWidth() - 12;
        int top = 12;
        int bottom = getHeight() - 25;
        g.setFont(Theme.SMALL.deriveFont(9f));
        g.setColor(Theme.TEXT_MUTED);
        BigDecimal maximum = values.stream()
                .map(DashboardData.DailyRevenue::amount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        if (maximum.signum() == 0) {
            maximum = BigDecimal.ONE;
        }
        BigDecimal scaleMaximum = maximum.multiply(BigDecimal.valueOf(1.15));
        NumberFormat formatter = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-AR"));

        for (int i = 0; i < 4; i++) {
            int y = top + (bottom - top) * i / 3;
            BigDecimal amount = scaleMaximum.multiply(BigDecimal.valueOf(3 - i)).divide(BigDecimal.valueOf(3));
            String label = "$" + formatter.format(amount.longValue());
            g.drawString(label, 0, y + 3);
            g.setColor(Theme.BORDER);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3f, 3f}, 0));
            g.drawLine(left, y, right, y);
            g.setColor(Theme.TEXT_MUTED);
        }

        int available = right - left;
        int count = values.size();
        if (count == 0) {
            g.setColor(Theme.TEXT_MUTED);
            g.drawString("Sin ventas en el período", left, (top + bottom) / 2);
            g.dispose();
            return;
        }
        int slot = Math.max(1, available / count);
        int barWidth = Math.max(12, Math.min(25, slot / 2));
        DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("d/M", Locale.forLanguageTag("es-AR"));
        for (int i = 0; i < count; i++) {
            int x = left + i * slot + (slot - barWidth) / 2;
            int height = scaleMaximum.signum() == 0 ? 0
                    : (int) Math.round((bottom - top)
                            * values.get(i).amount().doubleValue() / scaleMaximum.doubleValue());
            int y = bottom - height;
            g.setColor(i == count - 1 ? Theme.PRIMARY_DARK : new Color(132, 119, 226));
            g.fill(new RoundRectangle2D.Float(x, y, barWidth, height, 7, 7));
            g.setColor(Theme.TEXT_MUTED);
            String day = values.get(i).date().format(dayFormat);
            if (count > 8 && i % 2 == 1 && i != count - 1) {
                continue;
            }
            int textWidth = g.getFontMetrics().stringWidth(day);
            g.drawString(day, x + (barWidth - textWidth) / 2, getHeight() - 7);
        }
        g.dispose();
    }
}
