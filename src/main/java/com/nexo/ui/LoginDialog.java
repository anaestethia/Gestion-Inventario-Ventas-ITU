package com.nexo.ui;

import com.nexo.db.StoreRepository;
import com.nexo.model.UserProfile;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.border.EmptyBorder;

public final class LoginDialog {
    private LoginDialog() {
    }

    public static UserProfile showDialog(java.awt.Window owner, StoreRepository repository) {
        List<UserProfile> users;
        try {
            users = repository.listActiveUsers();
        } catch (SQLException exception) {
            showDatabaseError(owner, exception);
            return null;
        }
        if (users.isEmpty()) {
            JOptionPane.showMessageDialog(owner,
                    "La base de datos no contiene perfiles activos. Revisá la inicialización del esquema.",
                    "No hay usuarios", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        JComboBox<UserProfile> profiles = new JComboBox<>(users.toArray(UserProfile[]::new));
        profiles.setRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(value == null ? "" : value.displayName() + " — "
                    + ("DUEÑA".equals(value.role()) ? "Dueña" : "Empleada"));
            label.setOpaque(selected);
            label.setBackground(selected ? Theme.PRIMARY_SOFT : Theme.SURFACE);
            label.setForeground(Theme.TEXT);
            label.setBorder(new EmptyBorder(7, 9, 7, 9));
            return label;
        });
        profiles.setPreferredSize(new Dimension(300, 38));
        JPasswordField password = new JPasswordField();
        password.setFont(Theme.FONT);
        password.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                new EmptyBorder(8, 9, 8, 9)));

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 8));
        form.setOpaque(false);
        form.add(Theme.label("Perfil", Theme.LABEL, Theme.TEXT_MUTED));
        form.add(profiles);
        form.add(Theme.label("Contraseña", Theme.LABEL, Theme.TEXT_MUTED));
        form.add(password);

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(Theme.BACKGROUND);
        content.setBorder(new EmptyBorder(22, 24, 20, 24));
        JPanel title = new JPanel();
        title.setOpaque(false);
        title.setLayout(new javax.swing.BoxLayout(title, javax.swing.BoxLayout.Y_AXIS));
        title.add(Theme.label("Ingresar a Nexo", Theme.TITLE, Theme.TEXT));
        title.add(javax.swing.Box.createVerticalStrut(5));
        title.add(Theme.label("Seleccioná tu perfil e ingresá tu contraseña.",
                Theme.SMALL, Theme.TEXT_MUTED));
        content.add(title, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);

        while (true) {
            int result = JOptionPane.showConfirmDialog(
                    owner, content, "Nexo — Gestión comercial",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return null;
            }
            UserProfile selected = (UserProfile) profiles.getSelectedItem();
            if (selected == null) {
                JOptionPane.showMessageDialog(owner, "Seleccioná un perfil.",
                        "Inicio de sesión", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            char[] enteredPassword = password.getPassword();
            try {
                Optional<UserProfile> authenticated =
                        repository.authenticate(selected.username(), enteredPassword);
                if (authenticated.isPresent()) {
                    return authenticated.get();
                }
                JOptionPane.showMessageDialog(owner, "La contraseña no es correcta.",
                        "No se pudo iniciar sesión", JOptionPane.WARNING_MESSAGE);
            } catch (SQLException exception) {
                showDatabaseError(owner, exception);
                return null;
            } finally {
                Arrays.fill(enteredPassword, '\0');
                password.setText("");
            }
        }
    }

    private static void showDatabaseError(java.awt.Component owner, SQLException exception) {
        JOptionPane.showMessageDialog(owner,
                "No se pudo consultar la base de datos MySQL.\n" + exception.getMessage(),
                "Error de base de datos", JOptionPane.ERROR_MESSAGE);
    }
}
