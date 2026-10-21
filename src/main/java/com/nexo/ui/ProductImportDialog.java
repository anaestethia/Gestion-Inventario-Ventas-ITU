package com.nexo.ui;

import com.nexo.db.StoreRepository;
import com.nexo.importer.ProductFileImporter;
import com.nexo.model.ProductImportRow;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Window;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

public final class ProductImportDialog {
    private ProductImportDialog() {
    }

    public static void chooseAndImport(
            Window owner, StoreRepository repository, int userId, Runnable onSuccess) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Importar productos desde archivo");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Inventario (CSV, Excel .xlsx/.xls)", "csv", "xlsx", "xls"));
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<ProductFileImporter.Preview, Void>() {
            @Override
            protected ProductFileImporter.Preview doInBackground() throws Exception {
                return ProductFileImporter.parse(file);
            }

            @Override
            protected void done() {
                owner.setCursor(Cursor.getDefaultCursor());
                try {
                    ProductFileImporter.Preview preview = get();
                    confirmAndImport(owner, repository, userId, file, preview, onSuccess);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(owner, "Se interrumpió la lectura del archivo.", exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    showError(owner, "No se pudo leer el archivo.", exception.getCause());
                }
            }
        }.execute();
    }

    private static void confirmAndImport(
            Window owner,
            StoreRepository repository,
            int userId,
            Path file,
            ProductFileImporter.Preview preview,
            Runnable onSuccess) {
        if (preview.products().isEmpty()) {
            String message = preview.errors().isEmpty()
                    ? "No se encontraron filas de productos después de los encabezados."
                    : errorSummary(preview.errors());
            JOptionPane.showMessageDialog(owner,
                    message,
                    "No hay productos válidos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String[] columns = {"FILA", "PRODUCTO", "TALLE", "COLOR", "MARCA", "STOCK", "CATEGORÍA", "CÓDIGO"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        preview.products().stream().limit(100).forEach(product -> model.addRow(new Object[]{
            product.sourceRow(), product.description(), product.size(), product.color(),
            product.brand(), product.quantity(), product.category(), product.barcode()
        }));
        JTable table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setPreferredScrollableViewportSize(new Dimension(780, 300));
        JPanel content = new JPanel(new BorderLayout(0, 10));
        String summary = preview.products().size() + " filas válidas; "
                + preview.errors().size() + " filas con errores. ";
        if (!preview.errors().isEmpty()) {
            summary += "Las filas con errores se omitirán. ";
        }
        summary += "Si un producto coincide por código de barras, o por descripción, talle, color, marca y categoría "
                + "cuando no tenga código, se sumará su stock.";
        content.add(new JLabel("<html><div style='width:760px'>" + escape(summary) + "</div></html>"),
                BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        if (!preview.errors().isEmpty()) {
            content.add(new JLabel("<html><div style='width:760px;color:#a33'>"
                    + escape(errorSummary(preview.errors())) + "</div></html>"), BorderLayout.SOUTH);
        }
        String confirmText = preview.products().size() > 100
                ? "Importar " + preview.products().size() + " filas (vista previa de las primeras 100)"
                : "Importar " + preview.products().size() + " filas";
        Object[] options = {confirmText, "Cancelar"};
        if (JOptionPane.showOptionDialog(owner, content,
                "Revisar importación — " + file.getFileName(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]) != 0) {
            return;
        }
        importRows(owner, repository, userId, preview.products(), onSuccess);
    }

    private static void importRows(
            Window owner, StoreRepository repository, int userId,
            List<ProductImportRow> rows, Runnable onSuccess) {
        owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<StoreRepository.ImportResult, Void>() {
            @Override
            protected StoreRepository.ImportResult doInBackground() throws Exception {
                return repository.importProducts(rows, userId);
            }

            @Override
            protected void done() {
                owner.setCursor(Cursor.getDefaultCursor());
                try {
                    StoreRepository.ImportResult result = get();
                    JOptionPane.showMessageDialog(owner,
                            "Importación terminada.\nProductos nuevos: " + result.newProducts()
                            + "\nProductos existentes con stock sumado: " + result.stockUpdatedProducts()
                            + "\nFilas procesadas: " + result.importedRows(),
                            "Importación completada", JOptionPane.INFORMATION_MESSAGE);
                    onSuccess.run();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(owner, "Se interrumpió la importación.", exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    showError(owner, "No se pudo importar el archivo.", exception.getCause());
                }
            }
        }.execute();
    }

    private static String errorSummary(List<String> errors) {
        StringBuilder summary = new StringBuilder();
        errors.stream().limit(10).forEach(error -> summary.append(error).append('\n'));
        if (errors.size() > 10) {
            summary.append("… y ").append(errors.size() - 10).append(" errores más.");
        }
        return summary.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void showError(Component owner, String message, Throwable exception) {
        JOptionPane.showMessageDialog(owner, message + "\n" + exception.getMessage(),
                "Error de importación", JOptionPane.ERROR_MESSAGE);
    }
}
