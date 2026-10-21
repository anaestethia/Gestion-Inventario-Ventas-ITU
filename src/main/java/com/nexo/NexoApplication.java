package com.nexo;

import com.nexo.db.Database;
import com.nexo.db.DatabaseConfig;
import com.nexo.db.StoreRepository;
import com.nexo.ui.NexoFrame;
import com.nexo.ui.Theme;
import com.nexo.ui.DatabaseSetupDialog;
import com.nexo.ui.LoginDialog;
import java.awt.BorderLayout;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public final class NexoApplication {
    private NexoApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            UIManager.put("swing.boldMetal", Boolean.FALSE);
            DatabaseConfig config;
            try {
                config = DatabaseConfig.load();
            } catch (Exception exception) {
                showStartupError(exception);
                config = null;
            }
            prepareDatabase(config, null);
        });
    }

    private static void prepareDatabase(DatabaseConfig current, String previousError) {
        DatabaseConfig config = current;
        if (config == null || previousError != null) {
            config = DatabaseSetupDialog.showDialog(null, current, previousError);
        }
        if (config == null) {
            return;
        }

        JDialog progress = new JDialog((java.awt.Frame) null, "Iniciando Nexo", true);
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(22, 26, 22, 26));
        content.add(Theme.label("Conectando y preparando la base MySQL…", Theme.BODY_BOLD, Theme.TEXT));
        progress.setContentPane(content);
        progress.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progress.pack();
        progress.setLocationRelativeTo(null);

        DatabaseConfig selected = config;
        SwingWorker<StoreRepository, Void> worker = new SwingWorker<>() {
            @Override
            protected StoreRepository doInBackground() throws SQLException, java.io.IOException {
                Database database = new Database(selected);
                database.initialize();
                selected.save();
                return new StoreRepository(database);
            }

            @Override
            protected void done() {
                progress.dispose();
                try {
                    StoreRepository repository = get();
                    var user = LoginDialog.showDialog(null, repository);
                    if (user != null) {
                        new NexoFrame(repository, user).setVisible(true);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showStartupError(exception);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    showStartupError(cause);
                    prepareDatabase(selected, cause.getMessage());
                }
            }
        };
        worker.execute();
        progress.setVisible(true);
    }

    private static void showStartupError(Throwable exception) {
        JOptionPane.showMessageDialog(null,
                "No se pudo iniciar Nexo:\n" + exception.getMessage(),
                "Error de inicio", JOptionPane.ERROR_MESSAGE);
    }
}
