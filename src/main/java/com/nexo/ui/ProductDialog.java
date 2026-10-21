package com.nexo.ui;

import com.nexo.model.InventoryProduct;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;

public final class ProductDialog {
    private static final String[] SEASONS = {"NO_ESPECIFICADO", "VERANO", "OTONO", "INVIERNO", "PRIMAVERA"};

    private ProductDialog() {
    }

    public static boolean show(
            java.awt.Window owner,
            com.nexo.db.StoreRepository repository,
            InventoryProduct existing,
            int userId) {
        try {
            List<Map.Entry<Integer, String>> categories = repository.listCategories();
            if (categories.isEmpty()) {
                throw new SQLException("No hay categorías de producto disponibles.");
            }
            int defaultMinimumStock = Integer.parseInt(repository.setting(
                    "inventory.low_stock_threshold", "5"));
            int maximumInstallments = Integer.parseInt(repository.setting(
                    "sales.max_installments", "12"));
            JComboBox<CategoryOption> category = new JComboBox<>(
                    categories.stream().map(item -> new CategoryOption(item.getKey(), item.getValue()))
                            .toArray(CategoryOption[]::new));
            category.setRenderer((list, value, index, selected, focus) -> {
                JLabel label = new JLabel(value == null ? "" : value.name());
                label.setOpaque(selected);
                label.setBackground(selected ? Theme.PRIMARY_SOFT : Theme.SURFACE);
                label.setForeground(Theme.TEXT);
                label.setBorder(new EmptyBorder(5, 8, 5, 8));
                return label;
            });
            if (existing != null) {
                categories.stream().filter(item -> item.getKey() == existing.categoryId())
                        .findFirst().ifPresent(item -> category.setSelectedItem(
                                new CategoryOption(item.getKey(), item.getValue())));
            }

            JTextField description = fieldValue(existing == null ? "" : existing.description());
            JTextField barcode = fieldValue(existing == null ? "" : value(existing.barcode()));
            JTextField brand = fieldValue(existing == null ? "" : existing.brand());
            JTextField size = fieldValue(existing == null ? "" : existing.size());
            JTextField color = fieldValue(existing == null ? "" : existing.color());
            JComboBox<String> season = new JComboBox<>(SEASONS);
            JSpinner quantity = new JSpinner(new SpinnerNumberModel(
                    existing == null ? 0 : existing.quantity(), 0, 999999, 1));
            JSpinner minimumStock = new JSpinner(new SpinnerNumberModel(
                    existing == null ? defaultMinimumStock : existing.minimumStock(), 0, 999999, 1));
            JTextField cost = fieldValue(existing == null ? "" : decimalText(existing.purchaseCost()));
            JTextField cashPrice = fieldValue(existing == null || existing.cashPrice() == null
                    ? "" : decimalText(existing.cashPrice()));
            JTextField financedPrice = fieldValue(existing == null || existing.financedPrice() == null
                    ? "" : decimalText(existing.financedPrice()));
            List<String> installmentOptions = new java.util.ArrayList<>();
            installmentOptions.add("Sin financiación");
            for (int count : new int[]{3, 6, 9, 12}) {
                if (count <= maximumInstallments || (existing != null && existing.installments() == count)) {
                    installmentOptions.add(count + " cuotas");
                }
            }
            JComboBox<String> installments = new JComboBox<>(installmentOptions.toArray(String[]::new));
            if (existing != null && existing.installments() > 0) {
                installments.setSelectedItem(existing.installments() + " cuotas");
            }
            season.setSelectedItem(existing == null ? "NO_ESPECIFICADO" : existing.season());

            JPanel form = new JPanel(new GridLayout(0, 2, 12, 10));
            form.setOpaque(false);
            form.add(field("Descripción *", description));
            form.add(field("Código de barras", barcode));
            form.add(field("Categoría *", category));
            form.add(field("Marca", brand));
            form.add(field("Talle", size));
            form.add(field("Color", color));
            form.add(field("Temporada", season));
            form.add(field(existing == null ? "Stock inicial" : "Stock actual", quantity));
            form.add(field("Stock mínimo", minimumStock));
            form.add(field("Costo unitario *", cost));
            form.add(field("Precio contado", cashPrice));
            form.add(field("Precio financiado", financedPrice));
            form.add(field("Cuotas", installments));

            JPanel card = new JPanel(new BorderLayout(0, 10));
            card.setBackground(Theme.BACKGROUND);
            card.setBorder(new EmptyBorder(14, 18, 12, 18));
            card.add(Theme.label(existing == null ? "Registrá un producto nuevo"
                    : "Actualizá los datos del producto", Theme.SMALL, Theme.TEXT_MUTED), BorderLayout.NORTH);
            JPanel surface = new JPanel(new BorderLayout());
            surface.setBackground(Theme.SURFACE);
            surface.setBorder(new EmptyBorder(15, 15, 15, 15));
            surface.add(form, BorderLayout.CENTER);
            card.add(surface, BorderLayout.CENTER);

            while (true) {
                int result = JOptionPane.showConfirmDialog(owner, card,
                        existing == null ? "Nuevo producto" : "Editar producto",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (result != JOptionPane.OK_OPTION) {
                    return false;
                }
                if (description.getText().isBlank()) {
                    showValidation(owner, "Ingresá la descripción del producto.", description);
                    continue;
                }
                try {
                    BigDecimal purchaseCost = parseMoney(cost.getText(), "El costo");
                    BigDecimal cash = optionalMoney(cashPrice.getText(), "El precio contado");
                    BigDecimal financed = optionalMoney(financedPrice.getText(), "El precio financiado");
                    String barcodeValue = barcode.getText().trim();
                    if (!barcodeValue.isEmpty() && repository.barcodeExists(barcodeValue,
                            existing == null ? 0 : existing.id())) {
                        showValidation(owner, "Ya existe un producto con ese código de barras.", barcode);
                        continue;
                    }
                    CategoryOption selectedCategory = (CategoryOption) category.getSelectedItem();
                    if (selectedCategory == null) {
                        showValidation(owner, "Seleccioná una categoría.", category);
                        continue;
                    }
                    int installmentCount = parseInstallments((String) installments.getSelectedItem());
                    InventoryProduct product = new InventoryProduct(
                            existing == null ? 0 : existing.id(),
                            description.getText().trim(),
                            size.getText().trim(),
                            color.getText().trim(),
                            brand.getText().trim(),
                            (Integer) quantity.getValue(),
                            (Integer) minimumStock.getValue(),
                            (String) season.getSelectedItem(),
                            purchaseCost,
                            cash,
                            financed,
                            installmentCount,
                            barcodeValue,
                            selectedCategory.id(),
                            selectedCategory.name());
                    if (existing == null) {
                        repository.saveProduct(product, userId);
                    } else {
                        repository.updateProduct(product, userId);
                    }
                    return true;
                } catch (IllegalArgumentException exception) {
                    JOptionPane.showMessageDialog(owner, exception.getMessage(),
                            "Revisá los datos", JOptionPane.WARNING_MESSAGE);
                } catch (SQLException exception) {
                    JOptionPane.showMessageDialog(owner,
                            "No se pudo guardar el producto.\n" + exception.getMessage(),
                            "Error de base de datos", JOptionPane.ERROR_MESSAGE);
                }
            }
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(owner,
                    "No se pudieron cargar las categorías.\n" + exception.getMessage(),
                    "Error de base de datos", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private static int parseInstallments(String value) {
        if (value == null || "Sin financiación".equals(value)) {
            return 0;
        }
        return Integer.parseInt(value.split(" ")[0]);
    }

    private static BigDecimal optionalMoney(String value, String label) {
        return value.isBlank() ? null : parseMoney(value, label);
    }

    private static BigDecimal parseMoney(String value, String label) {
        String normalized = value.trim().replace("$", "").replace(" ", "");
        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(",", ".");
        }
        if (!normalized.matches("\\d+(\\.\\d{1,2})?")) {
            throw new IllegalArgumentException(label + " debe ser un importe válido con hasta dos decimales.");
        }
        BigDecimal amount = new BigDecimal(normalized);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(label + " no puede ser negativo.");
        }
        return amount;
    }

    private static JTextField fieldValue(String value) {
        JTextField field = new JTextField(value);
        field.setFont(Theme.FONT);
        field.setBorder(Theme.compoundBorder(Theme.BORDER, 7, 9));
        return field;
    }

    private static JComponent field(String label, JComponent input) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new javax.swing.BoxLayout(row, javax.swing.BoxLayout.Y_AXIS));
        row.add(Theme.label(label, Theme.LABEL, Theme.TEXT_MUTED));
        row.add(javax.swing.Box.createVerticalStrut(5));
        row.add(input);
        return row;
    }

    private static String decimalText(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static void showValidation(java.awt.Component owner, String message, JComponent input) {
        JOptionPane.showMessageDialog(owner, message, "Revisá los datos", JOptionPane.WARNING_MESSAGE);
        input.requestFocusInWindow();
    }

    private record CategoryOption(int id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
