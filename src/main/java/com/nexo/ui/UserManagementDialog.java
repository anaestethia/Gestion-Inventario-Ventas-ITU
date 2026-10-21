package com.nexo.ui;

import com.nexo.db.StoreRepository;
import com.nexo.model.UserProfile;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import javax.swing.border.EmptyBorder;

public final class UserManagementDialog {
    private UserManagementDialog() {
    }

    public static void show(java.awt.Window owner, StoreRepository repository) {
        DefaultTableModel model = new DefaultTableModel(
                new String[]{"ID", "USUARIO", "NOMBRE", "ROL", "ACTIVO", "USER_DATA"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(38);
        table.removeColumn(table.getColumnModel().getColumn(5));
        Runnable load = () -> {
            model.setRowCount(0);
            try {
                for (UserProfile user : repository.listUsers()) {
                    model.addRow(new Object[]{
                        user.id(), user.username(), user.displayName(),
                        "DUEÑA".equals(user.role()) ? "Dueña" : "Empleada",
                        user.active() ? "Activo" : "Inactivo", user
                    });
                }
            } catch (SQLException exception) {
                showError(owner, exception);
            }
        };
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(Theme.BACKGROUND);
        content.setBorder(new EmptyBorder(18, 18, 12, 18));
        content.add(Theme.label("Los perfiles se autentican contra MySQL.",
                Theme.SMALL, Theme.TEXT_MUTED), BorderLayout.NORTH);
        content.add(new javax.swing.JScrollPane(table), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        JButton add = Theme.button("Nuevo usuario", true);
        JButton edit = Theme.button("Editar", false);
        JButton password = Theme.button("Cambiar contraseña", false);
        add.addActionListener(event -> {
            if (createUser(owner, repository)) {
                load.run();
            }
        });
        edit.addActionListener(event -> {
            UserProfile user = selectedUser(table, model);
            if (user != null && editUser(owner, repository, user)) {
                load.run();
            }
        });
        password.addActionListener(event -> {
            UserProfile user = selectedUser(table, model);
            if (user != null && changePassword(owner, repository, user)) {
                JOptionPane.showMessageDialog(owner, "La contraseña se actualizó.",
                        "Usuario actualizado", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        actions.add(add);
        actions.add(edit);
        actions.add(password);
        content.add(actions, BorderLayout.SOUTH);

        JDialog dialog = new JDialog(owner instanceof java.awt.Frame frame ? frame : null,
                "Administrar usuarios", true);
        dialog.setContentPane(content);
        dialog.setSize(720, 420);
        dialog.setLocationRelativeTo(owner);
        load.run();
        dialog.setVisible(true);
    }

    private static UserProfile selectedUser(JTable table, DefaultTableModel model) {
        int row = table.getSelectedRow();
        return row < 0 ? null
                : (UserProfile) model.getValueAt(table.convertRowIndexToModel(row), 5);
    }

    private static boolean createUser(java.awt.Component owner, StoreRepository repository) {
        JTextField username = new JTextField();
        JTextField displayName = new JTextField();
        JComboBox<String> role = new JComboBox<>(new String[]{"EMPLEADA", "DUEÑA"});
        JPasswordField password = new JPasswordField();
        JPanel fields = fields(
                "Usuario (sin espacios)", username,
                "Nombre visible", displayName,
                "Perfil", role,
                "Contraseña (mínimo 4 caracteres)", password);
        if (JOptionPane.showConfirmDialog(owner, fields, "Nuevo usuario",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return false;
        }
        char[] secret = password.getPassword();
        try {
            repository.createUser(username.getText(), displayName.getText(),
                    (String) role.getSelectedItem(), secret);
            return true;
        } catch (SQLException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(owner, exception.getMessage(),
                    "No se pudo crear el usuario", JOptionPane.ERROR_MESSAGE);
            return false;
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private static boolean editUser(
            java.awt.Component owner, StoreRepository repository, UserProfile user) {
        JTextField displayName = new JTextField(user.displayName());
        JComboBox<String> role = new JComboBox<>(new String[]{"DUEÑA", "EMPLEADA"});
        role.setSelectedItem(user.role());
        JComboBox<String> active = new JComboBox<>(new String[]{"Activo", "Inactivo"});
        active.setSelectedItem(user.active() ? "Activo" : "Inactivo");
        JPanel fields = fields("Nombre visible", displayName, "Perfil", role,
                "Estado", active);
        if (JOptionPane.showConfirmDialog(owner, fields, "Editar " + user.username(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return false;
        }
        try {
            repository.updateUser(user.id(), displayName.getText(),
                    (String) role.getSelectedItem(), "Activo".equals(active.getSelectedItem()));
            return true;
        } catch (SQLException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(owner, exception.getMessage(),
                    "No se pudo actualizar el usuario", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private static boolean changePassword(
            java.awt.Component owner, StoreRepository repository, UserProfile user) {
        JPasswordField password = new JPasswordField();
        JPanel fields = fields("Nueva contraseña (mínimo 4 caracteres)", password);
        if (JOptionPane.showConfirmDialog(owner, fields, "Cambiar contraseña de " + user.username(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return false;
        }
        char[] secret = password.getPassword();
        try {
            repository.changePassword(user.id(), secret);
            return true;
        } catch (SQLException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(owner, exception.getMessage(),
                    "No se pudo cambiar la contraseña", JOptionPane.ERROR_MESSAGE);
            return false;
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private static JPanel fields(Object... pairs) {
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 2, 9, 9));
        panel.setOpaque(false);
        for (int i = 0; i < pairs.length; i += 2) {
            panel.add(new JLabel(String.valueOf(pairs[i])));
            panel.add((java.awt.Component) pairs[i + 1]);
        }
        return panel;
    }

    private static void showError(java.awt.Component owner, SQLException exception) {
        JOptionPane.showMessageDialog(owner,
                "No se pudieron consultar los usuarios.\n" + exception.getMessage(),
                "Error de base de datos", JOptionPane.ERROR_MESSAGE);
    }
}
