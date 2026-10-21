package com.nexo.ui.components;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;

public class RoundedPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final int radius;
    private Color fill;
    private Color stroke;

    public RoundedPanel() {
        this(14, Color.WHITE, new Color(228, 230, 236));
    }

    public RoundedPanel(int radius, Color fill, Color stroke) {
        this.radius = radius;
        this.fill = fill;
        this.stroke = stroke;
        setOpaque(false);
    }

    public void setFill(Color fill) {
        this.fill = fill;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float width = getWidth() - 1f;
        float height = getHeight() - 1f;
        RoundRectangle2D shape = new RoundRectangle2D.Float(.5f, .5f, width, height, radius, radius);
        g.setColor(fill);
        g.fill(shape);
        if (stroke != null) {
            g.setColor(stroke);
            g.draw(shape);
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
