package com.nexo.ui;

import com.nexo.db.StoreRepository;
import com.nexo.model.InventoryProduct;
import com.nexo.model.SaleLine;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public final class SaleDialog {
    private SaleDialog() {
    }

    public static boolean show(java.awt.Window owner, StoreRepository repository, int userId) {
        try {
            boolean allowNegative = Boolean.parseBoolean(
                    repository.setting("inventory.allow_negative", "false"));
            List<InventoryProduct> products = repository.listProducts()
                    .stream().filter(product -> allowNegative || product.quantity() > 0).toList();
            if (products.isEmpty()) {
                JOptionPane.showMessageDialog(owner,
                        "No hay productos con stock para vender. Agregá productos o registrá un ingreso.",
                        "No hay stock disponible", JOptionPane.INFORMATION_MESSAGE);
                return false;
            }
            JComboBox<InventoryProduct> product = new JComboBox<>(products.toArray(InventoryProduct[]::new));
            product.setRenderer((list, value, index, selected, focus) -> {
                JLabel label = new JLabel(value == null ? "" : value.description() + " · "
                        + value.size() + " · stock " + value.quantity());
                label.setOpaque(selected);
                label.setBackground(selected ? Theme.PRIMARY_SOFT : Theme.SURFACE);
                label.setForeground(Theme.TEXT);
                label.setBorder(new EmptyBorder(6, 7, 6, 7));
                return label;
            });
            product.setPreferredSize(new Dimension(320, 36));
            JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 99999, 1));
            JComboBox<String> payment = new JComboBox<>(new String[]{
                "Efectivo", "Tarjeta de débito", "Tarjeta de crédito", "Transferencia"
            });
            JComboBox<String> pricePlan = new JComboBox<>(new String[]{"Precio contado", "Precio financiado"});
            JTextField customer = new JTextField("Venta mostrador");
            JPanel entry = new JPanel(new GridLayout(0, 2, 10, 9));
            entry.setOpaque(false);
            entry.add(field("Producto", product));
            entry.add(field("Cantidad", quantity));
            entry.add(field("Medio de pago", payment));
            entry.add(field("Precio", pricePlan));
            entry.add(field("Cliente (opcional)", customer));
            JButton addLine = Theme.button("Agregar al detalle", false);
            entry.add(addLine);

            String[] columns = {"PRODUCTO", "CANTIDAD", "PRECIO", "SUBTOTAL"};
            DefaultTableModel model = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            JTable table = new JTable(model);
            table.setRowHeight(34);
            JLabel total = Theme.label("$ 0", Theme.BODY_BOLD, Theme.TEXT);
            List<SaleLine> cart = new ArrayList<>();

            JPanel center = new JPanel(new BorderLayout(0, 12));
            center.setOpaque(false);
            center.add(entry, BorderLayout.NORTH);
            center.add(new javax.swing.JScrollPane(table), BorderLayout.CENTER);
            JPanel cartFooter = new JPanel(new BorderLayout());
            cartFooter.setOpaque(false);
            JButton remove = Theme.button("Quitar seleccionado", false);
            remove.addActionListener(event -> {
                int selected = table.getSelectedRow();
                if (selected >= 0) {
                    cart.remove(selected);
                    model.removeRow(selected);
                    updateTotal(cart, total);
                }
            });
            cartFooter.add(remove, BorderLayout.WEST);
            cartFooter.add(total, BorderLayout.EAST);
            center.add(cartFooter, BorderLayout.SOUTH);

            addLine.addActionListener(event -> {
                InventoryProduct selected = (InventoryProduct) product.getSelectedItem();
                if (selected == null) {
                    return;
                }
                int amount = (Integer) quantity.getValue();
                boolean useFinanced = "Precio financiado".equals(pricePlan.getSelectedItem());
                BigDecimal unitPrice = useFinanced ? selected.financedPrice() : selected.cashPrice();
                if (unitPrice == null || unitPrice.signum() <= 0) {
                    JOptionPane.showMessageDialog(owner,
                            "El producto no tiene configurado ese precio.",
                            "Precio no disponible", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                int existingIndex = -1;
                for (int index = 0; index < cart.size(); index++) {
                    if (cart.get(index).product().id() == selected.id()
                            && cart.get(index).unitPrice().compareTo(unitPrice) == 0) {
                        existingIndex = index;
                        break;
                    }
                }
                int alreadyInCart = existingIndex < 0 ? 0 : cart.get(existingIndex).quantity();
                if (!allowNegative && amount + alreadyInCart > selected.quantity()) {
                    JOptionPane.showMessageDialog(owner, "La cantidad supera el stock disponible.",
                            "Stock insuficiente", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                SaleLine line = new SaleLine(selected, amount + alreadyInCart, unitPrice);
                if (existingIndex < 0) {
                    cart.add(line);
                    model.addRow(new Object[]{
                        selected.description() + " · " + selected.size(),
                        amount, money(unitPrice), money(line.subtotal())
                    });
                } else {
                    cart.set(existingIndex, line);
                    model.setValueAt(line.quantity(), existingIndex, 1);
                    model.setValueAt(money(line.subtotal()), existingIndex, 3);
                }
                updateTotal(cart, total);
            });

            JPanel content = new JPanel(new BorderLayout(0, 14));
            content.setBackground(Theme.BACKGROUND);
            content.setBorder(new EmptyBorder(16, 18, 12, 18));
            content.add(Theme.label("Seleccioná artículos y confirmá la operación.",
                    Theme.SMALL, Theme.TEXT_MUTED), BorderLayout.NORTH);
            content.add(center, BorderLayout.CENTER);

            JDialog dialog = new JDialog(
                    owner instanceof java.awt.Frame frame ? frame : null, "Registrar venta", true);
            final boolean[] saved = {false};
            JButton cancel = Theme.button("Cancelar", false);
            JButton save = Theme.button("Confirmar venta", true);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actions.setOpaque(false);
            actions.add(cancel);
            actions.add(save);
            content.add(actions, BorderLayout.SOUTH);
            cancel.addActionListener(event -> dialog.dispose());
            save.addActionListener(event -> {
                if (cart.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "Agregá al menos un producto.",
                            "Venta vacía", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                try {
                    String paymentMethod = (String) payment.getSelectedItem();
                    if ("Precio financiado".equals(pricePlan.getSelectedItem())) {
                        int installments = cart.get(0).product().installments();
                        paymentMethod = "Financiado (" + installments + " cuotas)";
                    }
                    int id = repository.createSale(cart,
                            customer.getText().isBlank() ? "Venta mostrador" : customer.getText().trim(),
                            paymentMethod, userId);
                    saved[0] = true;
                    JOptionPane.showMessageDialog(dialog, "Venta #" + id + " registrada correctamente.",
                            "Venta completada", JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();
                } catch (SQLException | IllegalArgumentException exception) {
                    JOptionPane.showMessageDialog(dialog,
                            "No se pudo registrar la venta.\n" + exception.getMessage(),
                            "Error de venta", JOptionPane.ERROR_MESSAGE);
                }
            });
            dialog.setContentPane(content);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setSize(780, 560);
            dialog.setMinimumSize(new Dimension(700, 500));
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
            return saved[0];
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(owner,
                    "No se pudieron cargar los productos.\n" + exception.getMessage(),
                    "Error de base de datos", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private static JPanel field(String label, java.awt.Component input) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.add(Theme.label(label, Theme.LABEL, Theme.TEXT_MUTED));
        panel.add(javax.swing.Box.createVerticalStrut(5));
        panel.add(input);
        return panel;
    }

    private static void updateTotal(List<SaleLine> cart, JLabel output) {
        BigDecimal total = cart.stream().map(SaleLine::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        output.setText("Total: " + money(total));
    }

    private static String money(BigDecimal amount) {
        return "$ " + NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR"))
                .format(amount.setScale(2, java.math.RoundingMode.HALF_UP));
    }
}
