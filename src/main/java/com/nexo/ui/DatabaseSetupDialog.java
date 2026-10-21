package com.nexo.ui;

import com.nexo.db.DatabaseConfig;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

public final class DatabaseSetupDialog {
    private DatabaseSetupDialog() {
    }

    public static DatabaseConfig showDialog(java.awt.Window owner, DatabaseConfig current, String message) {
        JTextField host = new JTextField(current == null ? "localhost" : current.host());
        JTextField port = new JTextField(current == null ? "3306" : Integer.toString(current.port()));
        JTextField database = new JTextField(current == null ? "tienda_de_ropa" : current.database());
        JTextField username = new JTextField(current == null ? "root" : current.username());
        JPasswordField password = new JPasswordField(current == null ? "" : current.password());
        style(host);
        style(port);
        style(database);
        style(username);
        style(password);

        JPanel form = new JPanel(new GridLayout(0, 2, 12, 11));
        form.setOpaque(false);
        form.add(Theme.label("Servidor MySQL", Theme.BODY_BOLD, Theme.TEXT));
        form.add(host);
        form.add(Theme.label("Puerto", Theme.BODY_BOLD, Theme.TEXT));
        form.add(port);
        form.add(Theme.label("Base de datos", Theme.BODY_BOLD, Theme.TEXT));
        form.add(database);
        form.add(Theme.label("Usuario administrador MySQL", Theme.BODY_BOLD, Theme.TEXT));
        form.add(username);
        form.add(Theme.label("Contraseña MySQL", Theme.BODY_BOLD, Theme.TEXT));
        form.add(password);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(Theme.BACKGROUND);
        content.setBorder(new EmptyBorder(22, 24, 20, 24));
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new javax.swing.BoxLayout(heading, javax.swing.BoxLayout.Y_AXIS));
        heading.add(Theme.label("Conectar Nexo a MySQL", Theme.TITLE, Theme.TEXT));
        heading.add(javax.swing.Box.createVerticalStrut(6));
        heading.add(Theme.label(
                "Nexo creará tienda_de_ropa si no existe y conservará las tablas existentes.",
                Theme.SMALL, Theme.TEXT_MUTED));
        content.add(heading, BorderLayout.NORTH);

        JPanel fields = new JPanel(new BorderLayout());
        fields.setBackground(Theme.SURFACE);
        fields.setBorder(new EmptyBorder(17, 17, 17, 17));
        fields.add(form, BorderLayout.CENTER);
        content.add(fields, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);
        footer.add(Theme.label(
                "La contraseña se guarda cifrada con la protección local de Windows, fuera del proyecto.",
                Theme.SMALL.deriveFont(10f), Theme.TEXT_MUTED), BorderLayout.NORTH);
        if (message != null && !message.isBlank()) {
            JLabel status = Theme.label(message, Theme.SMALL, Theme.RED);
            status.setBorder(new EmptyBorder(5, 0, 0, 0));
            footer.add(status, BorderLayout.CENTER);
        }
        content.add(footer, BorderLayout.SOUTH);

        Object[] options = {"Conectar y preparar base", "Cancelar"};
        while (true) {
            int result = JOptionPane.showOptionDialog(
                    owner, content, "Configuración inicial de Nexo",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, options, options[0]);
            if (result != 0) {
                return null;
            }
            try {
                int mysqlPort = Integer.parseInt(port.getText().trim());
                char[] passwordChars = password.getPassword();
                try {
                    return new DatabaseConfig(
                            host.getText().trim(),
                            mysqlPort,
                            database.getText().trim(),
                            username.getText().trim(),
                            new String(passwordChars));
                } finally {
                    java.util.Arrays.fill(passwordChars, '\0');
                }
            } catch (IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(owner, exception.getMessage(),
                        "Configuración incompleta", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private static void style(JTextField input) {
        input.setFont(Theme.FONT);
        input.setPreferredSize(new Dimension(220, 36));
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                new EmptyBorder(8, 9, 8, 9)));
    }
}
