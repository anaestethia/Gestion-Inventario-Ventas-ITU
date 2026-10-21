package com.nexo.ui;

import com.nexo.db.StoreRepository;
import com.nexo.model.DashboardData;
import com.nexo.model.InventoryProduct;
import com.nexo.model.MovementRecord;
import com.nexo.model.SaleLine;
import com.nexo.model.SaleRecord;
import com.nexo.model.UserProfile;
import com.nexo.ui.components.BarChartPanel;
import com.nexo.ui.components.DonutChartPanel;
import com.nexo.ui.components.MetricCard;
import com.nexo.ui.components.RoundedPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

public final class NexoFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private static final String[] SECTIONS = {
        "Resumen", "Inventario", "Ventas", "Movimientos", "Configuración"
    };

    private static final Map<String, String> SUBTITLES = Map.of(
            "Resumen", "Una mirada rápida al rendimiento de tu negocio.",
            "Inventario", "Controlá tus productos, existencias y precios.",
            "Ventas", "Consultá operaciones, facturación y medios de pago.",
            "Movimientos", "Revisá cada ingreso, venta y ajuste de stock.",
            "Configuración", "Personalizá el funcionamiento de Nexo.");

    private final CardLayout viewLayout = new CardLayout();
    private final JPanel viewContainer = new JPanel(viewLayout);
    private final JLabel title = Theme.label("Resumen", Theme.TITLE, Theme.TEXT);
    private final JLabel subtitle = Theme.label(SUBTITLES.get("Resumen"), Theme.SMALL, Theme.TEXT_MUTED);
    private final Map<String, NavButton> navButtons = new LinkedHashMap<>();
    private final Map<String, JComponent> sectionCards = new LinkedHashMap<>();
    private final Map<String, JComponent> settingInputs = new LinkedHashMap<>();
    private final Map<String, String> loadedSettings = new LinkedHashMap<>();
    private final StoreRepository repository;
    private JLabel brandNameLabel;
    private JLabel brandLogoLabel;
    private RoundedPanel brandMark;
    private UserProfile currentUser;
    private String currentSection;
    private String currencyCode = "ARS";
    private int sectionLoadGeneration;

    public NexoFrame(StoreRepository repository, UserProfile user) {
        super("Nexo — Gestión comercial");
        this.repository = repository;
        this.currentUser = user;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 720));
        setSize(1440, 900);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.BACKGROUND);

        add(createSidebar(), BorderLayout.WEST);
        add(createWorkspace(), BorderLayout.CENTER);
        showSection("Resumen");
        applyInterfaceScale();
    }

    private JComponent createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(Theme.SURFACE);
        sidebar.setPreferredSize(new Dimension(230, 0));
        sidebar.setLayout(new BorderLayout());
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 13, 19));
        brand.setBackground(Theme.SURFACE);
        brand.setPreferredSize(new Dimension(230, 79));
        brand.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        brandMark = new RoundedPanel(11, Theme.PRIMARY, null);
        brandMark.setPreferredSize(new Dimension(39, 39));
        brandMark.setLayout(new BorderLayout());
        brandLogoLabel = new JLabel();
        brandLogoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        brandMark.add(brandLogoLabel, BorderLayout.CENTER);
        setDefaultBrandIcon();
        brandMark.setToolTipText("Elegir el logotipo del comercio");
        brandMark.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        brandMark.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                chooseCompanyLogo();
            }
        });

        JPanel brandCopy = verticalPanel(0);
        brandNameLabel = Theme.label("Nexo", Theme.FONT.deriveFont(Font.BOLD, 18f), Theme.TEXT);
        brandCopy.add(brandNameLabel);
        brandCopy.add(Box.createVerticalStrut(2));
        brandCopy.add(Theme.label("GESTIÓN COMERCIAL", Theme.LABEL.deriveFont(9f), Theme.TEXT_MUTED));
        brand.add(brandMark);
        brand.add(brandCopy);

        JPanel navigation = new JPanel();
        navigation.setBackground(Theme.SURFACE);
        navigation.setBorder(new EmptyBorder(24, 14, 14, 14));
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        JLabel management = Theme.label("GESTIÓN", Theme.LABEL, new Color(160, 165, 178));
        management.setBorder(new EmptyBorder(0, 10, 8, 0));
        management.setAlignmentX(Component.LEFT_ALIGNMENT);
        navigation.add(management);

        String[] symbols = {"R", "I", "$", "↕", "C"};
        for (int i = 0; i < SECTIONS.length; i++) {
            if (i == 4) {
                navigation.add(Box.createVerticalStrut(24));
                JLabel system = Theme.label("SISTEMA", Theme.LABEL, new Color(160, 165, 178));
                system.setBorder(new EmptyBorder(0, 10, 8, 0));
                system.setAlignmentX(Component.LEFT_ALIGNMENT);
                navigation.add(system);
            }
            NavButton button = new NavButton(symbols[i], SECTIONS[i]);
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(event -> showSection(button.section));
            navButtons.put(SECTIONS[i], button);
            navigation.add(button);
            navigation.add(Box.createVerticalStrut(3));
        }
        navigation.add(Box.createVerticalGlue());

        JPanel profile = new JPanel(new BorderLayout(9, 0));
        profile.setBackground(Theme.SURFACE);
        profile.setPreferredSize(new Dimension(230, 74));
        profile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(15, 16, 15, 15)));
        RoundedPanel avatar = new RoundedPanel(35, Theme.PRIMARY_SOFT, null);
        avatar.setPreferredSize(new Dimension(36, 36));
        avatar.setLayout(new BorderLayout());
        JLabel initials = Theme.label("LT", Theme.LABEL.deriveFont(11f), Theme.PRIMARY_DARK);
        initials.setHorizontalAlignment(SwingConstants.CENTER);
        avatar.add(initials);
        JPanel profileCopy = verticalPanel(1);
        JLabel profileName = Theme.label(currentUser.displayName(), Theme.BODY_BOLD.deriveFont(11f), Theme.TEXT);
        JLabel profileRole = Theme.label(
                currentUser.isOwner() ? "Dueña" : "Empleada", Theme.SMALL.deriveFont(9f), Theme.TEXT_MUTED);
        profileCopy.add(profileName);
        profileCopy.add(profileRole);
        JButton logout = iconButton("↪");
        logout.setToolTipText("Cerrar sesión");
        logout.addActionListener(event -> {
            UserProfile nextUser = LoginDialog.showDialog(this, repository);
            if (nextUser != null) {
                currentUser = nextUser;
                profileName.setText(nextUser.displayName());
                profileRole.setText(nextUser.isOwner() ? "Dueña" : "Empleada");
                showSection("Resumen");
            }
        });
        profile.add(avatar, BorderLayout.WEST);
        profile.add(profileCopy, BorderLayout.CENTER);
        profile.add(logout, BorderLayout.EAST);

        sidebar.add(brand, BorderLayout.NORTH);
        sidebar.add(navigation, BorderLayout.CENTER);
        sidebar.add(profile, BorderLayout.SOUTH);
        return sidebar;
    }

    private JComponent createWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(Theme.BACKGROUND);
        workspace.add(createHeader(), BorderLayout.NORTH);

        viewContainer.setOpaque(false);
        for (String section : SECTIONS) {
            addSectionCard(section, createSectionPlaceholder(section));
        }
        workspace.add(viewContainer, BorderLayout.CENTER);
        return workspace;
    }

    private JComponent createSectionPlaceholder(String section) {
        JPanel placeholder = new JPanel(new BorderLayout());
        placeholder.setBackground(Theme.BACKGROUND);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel loading = Theme.label("Cargando " + section.toLowerCase(Locale.ROOT) + "…",
                Theme.BODY_BOLD, Theme.TEXT_MUTED);
        loading.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton retry = Theme.button("Reintentar", false);
        retry.setAlignmentX(Component.CENTER_ALIGNMENT);
        retry.addActionListener(event -> loadSection(section));
        content.add(loading);
        content.add(Box.createVerticalStrut(10));
        content.add(retry);
        placeholder.add(content, BorderLayout.CENTER);
        return placeholder;
    }

    private void addSectionCard(String section, JComponent card) {
        sectionCards.put(section, card);
        viewContainer.add(card, section);
    }

    private JComponent createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(255, 255, 255));
        header.setPreferredSize(new Dimension(0, 79));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(14, 29, 12, 29)));

        JPanel heading = verticalPanel(3);
        heading.add(title);
        heading.add(subtitle);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 5));
        actions.setOpaque(false);
        String date = LocalDate.now().format(
                DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", Locale.forLanguageTag("es-AR")));
        JButton dateButton = Theme.button("▣  " + date, false);
        dateButton.setFocusable(false);
        JButton notifications = iconButton("•");
        notifications.setForeground(Theme.RED);
        notifications.setToolTipText("Notificaciones");
        notifications.addActionListener(event -> showLowStockAlerts());
        actions.add(dateButton);
        actions.add(notifications);

        header.add(heading, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JComponent createSummaryView(
            DashboardData data, List<SaleRecord> recentSales, List<InventoryProduct> products) {
        JPanel content = pageColumn();
        content.add(metricGrid(List.of(
                metric("V", "Ventas de hoy", formatMoney(data.todayRevenue()),
                        data.todaySales() + " operaciones", Theme.PRIMARY, Theme.PRIMARY_SOFT),
                metric("#", "Operaciones", Long.toString(data.todaySales()),
                        "Ticket promedio " + formatMoney(data.todaySales() == 0
                                ? BigDecimal.ZERO
                                : data.todayRevenue().divide(BigDecimal.valueOf(data.todaySales()), 2,
                                        java.math.RoundingMode.HALF_UP)),
                        Theme.BLUE, Theme.BLUE_SOFT),
                metric("U", "Unidades vendidas", Long.toString(data.todayUnits()),
                        "Durante el día de hoy", Theme.GREEN, Theme.GREEN_SOFT),
                metric("!", "Alertas de stock", Long.toString(data.lowStockProducts()),
                        "Requieren atención", Theme.AMBER, Theme.AMBER_SOFT))));
        content.add(Box.createVerticalStrut(17));

        JPanel charts = new JPanel(new GridBagLayout());
        charts.setOpaque(false);
        GridBagConstraints constraints = fillConstraints();
        constraints.weightx = .68;
        RoundedPanel performance = sectionCard("Rendimiento de ventas", "Ingresos de los últimos 12 días");
        performance.setPreferredSize(new Dimension(700, 335));
        JPanel chartHeader = new JPanel(new BorderLayout());
        chartHeader.setOpaque(false);
        chartHeader.setBorder(new EmptyBorder(0, 19, 4, 19));
        chartHeader.add(Theme.label(formatMoney(data.monthRevenue()),
                Theme.FONT.deriveFont(Font.BOLD, 23f), Theme.TEXT), BorderLayout.WEST);
        chartHeader.add(Theme.label("Mes actual · " + data.monthSales() + " ventas",
                Theme.SMALL, Theme.TEXT_MUTED), BorderLayout.EAST);
        JPanel chartBody = new JPanel(new BorderLayout());
        chartBody.setOpaque(false);
        chartBody.add(chartHeader, BorderLayout.NORTH);
        chartBody.add(new BarChartPanel(data.dailyRevenue()), BorderLayout.CENTER);
        performance.add(chartBody, BorderLayout.CENTER);
        charts.add(performance, constraints);

        constraints.gridx = 1;
        constraints.weightx = .32;
        constraints.insets = new Insets(0, 17, 0, 0);
        RoundedPanel categories = sectionCard("Ventas por categoría", "Distribución del mes actual");
        categories.setPreferredSize(new Dimension(330, 335));
        categories.add(new DonutChartPanel(data.categoryUnits()), BorderLayout.CENTER);
        JPanel legend = new JPanel(new GridLayout(0, 1, 8, 6));
        legend.setOpaque(false);
        legend.setBorder(new EmptyBorder(5, 22, 19, 22));
        long totalUnits = data.categoryUnits().stream().mapToLong(DashboardData.CategoryUnits::units).sum();
        Color[] colors = {Theme.PRIMARY, Theme.BLUE, Theme.GREEN, Theme.AMBER};
        for (int i = 0; i < Math.min(4, data.categoryUnits().size()); i++) {
            DashboardData.CategoryUnits item = data.categoryUnits().get(i);
            String share = totalUnits == 0 ? "0%" : Math.round(item.units() * 100d / totalUnits) + "%";
            legend.add(legend("●  " + item.category(), share, colors[i % colors.length]));
        }
        if (data.categoryUnits().isEmpty()) {
            legend.add(Theme.label("Todavía no hay ventas en el período.",
                    Theme.SMALL, Theme.TEXT_MUTED));
        }
        categories.add(legend, BorderLayout.SOUTH);
        charts.add(categories, constraints);
        content.add(charts);
        content.add(Box.createVerticalStrut(17));

        JPanel lower = new JPanel(new GridLayout(1, 2, 17, 0));
        lower.setOpaque(false);
        lower.add(recentSalesCard(recentSales));
        lower.add(lowStockCard(products));
        content.add(lower);
        return content;
    }

    private JComponent createInventoryView(List<InventoryProduct> products) {
        JPanel content = pageColumn();
        long lowStock = products.stream().filter(product -> product.quantity() <= product.minimumStock()).count();
        BigDecimal inventoryValue = products.stream()
                .map(product -> product.purchaseCost().multiply(BigDecimal.valueOf(product.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        content.add(metricGrid(List.of(
                metric("P", "Productos registrados", Integer.toString(products.size()),
                        "En el inventario", Theme.PRIMARY, Theme.PRIMARY_SOFT),
                metric("$", "Valor del inventario", formatMoney(inventoryValue),
                        "Al costo de compra", Theme.BLUE, Theme.BLUE_SOFT),
                metric("!", "Stock bajo", lowStock + " productos",
                        "Requieren atención", Theme.AMBER, Theme.AMBER_SOFT),
                metric("V", "Unidades disponibles",
                        Long.toString(products.stream().mapToLong(InventoryProduct::quantity).sum()),
                        "En productos activos", Theme.GREEN, Theme.GREEN_SOFT))));
        content.add(Box.createVerticalStrut(17));

        JPanel layout = new JPanel(new BorderLayout(17, 0));
        layout.setOpaque(false);
        layout.add(inventoryTableCard(products), BorderLayout.CENTER);
        JComponent movementForm = movementFormCard(products);
        movementForm.setPreferredSize(new Dimension(310, 590));
        layout.add(movementForm, BorderLayout.EAST);
        content.add(layout);
        return content;
    }

    private JComponent inventoryTableCard(List<InventoryProduct> products) {
        RoundedPanel card = sectionCard("Detalle de stock", "Productos registrados en MySQL");
        String[] columns = {"ID", "PRODUCTO", "DETALLE", "STOCK", "COSTO", "CONTADO", "FINANCIADO", "PRODUCT_DATA"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        products.forEach(product -> addProductRow(model, product));
        JTable table = styledTable(model, new int[]{55, 180, 140, 65, 85, 85, 115, 0});
        table.removeColumn(table.getColumnModel().getColumn(7));
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        JTextField search = textField("");
        search.setToolTipText("Buscar por descripción, código, marca o talle");
        search.putClientProperty("JTextField.placeholderText", "Buscar descripción, código, marca...");
        JComboBox<String> categoryFilter = combo(categoryOptions(products));
        JComboBox<String> stockFilter = combo(new String[]{"Todos los estados", "Con stock", "Stock bajo", "Sin stock"});
        JLabel count = Theme.label("", Theme.SMALL.deriveFont(9f), Theme.TEXT_MUTED);
        Runnable filter = () -> {
            List<RowFilter<DefaultTableModel, Integer>> filters = new ArrayList<>();
            if (!search.getText().isBlank()) {
                filters.add(RowFilter.regexFilter(
                        "(?i)" + java.util.regex.Pattern.quote(search.getText().trim()), 0, 1, 2, 3, 4, 5, 6));
            }
            String category = (String) categoryFilter.getSelectedItem();
            if (category != null && !"Todas las categorías".equals(category)) {
                filters.add(new RowFilter<>() {
                    @Override
                    public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                        return category.equals(((InventoryProduct) entry.getValue(7)).category());
                    }
                });
            }
            String status = (String) stockFilter.getSelectedItem();
            if ("Con stock".equals(status) || "Sin stock".equals(status) || "Stock bajo".equals(status)) {
                filters.add(new RowFilter<>() {
                    @Override
                    public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                        InventoryProduct product = (InventoryProduct) entry.getValue(7);
                        return switch (status) {
                            case "Con stock" -> product.quantity() > 0;
                            case "Sin stock" -> product.quantity() == 0;
                            default -> product.quantity() <= product.minimumStock();
                        };
                    }
                });
            }
            sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
            count.setText("Mostrando " + sorter.getViewRowCount() + " de " + model.getRowCount() + " productos");
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                filter.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                filter.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                filter.run();
            }
        });
        categoryFilter.addActionListener(event -> filter.run());
        stockFilter.addActionListener(event -> filter.run());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 8));
        toolbar.setBackground(Theme.SURFACE_SUBTLE);
        toolbar.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.BORDER));
        search.setPreferredSize(new Dimension(240, 35));
        toolbar.add(search);
        toolbar.add(categoryFilter);
        toolbar.add(stockFilter);
        JButton importFile = Theme.button("Importar archivo", false);
        importFile.addActionListener(event -> ProductImportDialog.chooseAndImport(
                this, repository, currentUser.id(), () -> refreshAfterChange(null)));
        toolbar.add(importFile);
        JButton add = Theme.button("+ Producto", true);
        add.addActionListener(event -> {
            if (ProductDialog.show(this, repository, null, currentUser.id())) {
                refreshAfterChange("Producto agregado.");
            }
        });
        JButton edit = Theme.button("Editar", false);
        JButton delete = Theme.button("Eliminar", false);
        edit.setEnabled(false);
        delete.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(event -> {
            boolean selected = table.getSelectedRow() >= 0;
            edit.setEnabled(selected);
            delete.setEnabled(selected);
        });
        edit.addActionListener(event -> {
            InventoryProduct selected = selectedProduct(table, model);
            if (selected != null && ProductDialog.show(this, repository, selected, currentUser.id())) {
                refreshAfterChange("Producto actualizado.");
            }
        });
        delete.addActionListener(event -> deleteSelectedProduct(table, model));
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    InventoryProduct selected = selectedProduct(table, model);
                    if (selected != null && ProductDialog.show(NexoFrame.this, repository, selected, currentUser.id())) {
                        refreshAfterChange("Producto actualizado.");
                    }
                }
            }
        });
        toolbar.add(add);
        toolbar.add(edit);
        toolbar.add(delete);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(toolbar, BorderLayout.NORTH);
        center.add(tableScroll(table), BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);
        card.add(inventoryTableFooter(count), BorderLayout.SOUTH);
        filter.run();
        return card;
    }

    private String[] categoryOptions(List<InventoryProduct> products) {
        List<String> categories = new ArrayList<>();
        categories.add("Todas las categorías");
        products.stream().map(InventoryProduct::category).distinct().sorted().forEach(categories::add);
        return categories.toArray(String[]::new);
    }

    private void addProductRow(DefaultTableModel model, InventoryProduct product) {
        model.addRow(new Object[]{
            product.id(),
            product.description() + (product.brand().isBlank() ? "" : " · " + product.brand()),
            product.category() + " · " + product.size() + " · " + product.color(),
            product.quantity() + " u.",
            formatMoney(product.purchaseCost()),
            formatMoney(product.cashPrice()),
            product.installments() == 0 || product.financedPrice() == null
                    ? "—" : formatMoney(product.financedPrice()) + " · " + product.installments() + " cuotas",
            product
        });
    }

    private InventoryProduct selectedProduct(JTable table, DefaultTableModel model) {
        int row = table.getSelectedRow();
        return row < 0 ? null : (InventoryProduct) model.getValueAt(table.convertRowIndexToModel(row), 7);
    }

    private void deleteSelectedProduct(JTable table, DefaultTableModel model) {
        InventoryProduct product = selectedProduct(table, model);
        if (product == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "¿Eliminar “" + product.description() + "”? Esta acción no se puede deshacer.",
                "Eliminar producto", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            repository.deleteProduct(product.id());
            refreshAfterChange("Producto eliminado.");
        } catch (SQLException exception) {
            showDatabaseError("No se pudo eliminar el producto.", exception);
        }
    }

    private JPanel inventoryTableFooter(JLabel countLabel) {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(12, 19, 12, 19)));
        footer.add(countLabel, BorderLayout.WEST);
        return footer;
    }

    private JComponent movementFormCard(List<InventoryProduct> products) {
        RoundedPanel card = sectionCard("Nuevo movimiento", "Actualizá el stock rápidamente");
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(0, 16, 16, 16));
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        if (products.isEmpty()) {
            form.add(Theme.label("Agregá un producto antes de registrar movimientos.",
                    Theme.SMALL, Theme.TEXT_MUTED));
            card.add(form, BorderLayout.CENTER);
            return card;
        }
        JComboBox<InventoryProduct> product = new JComboBox<>(products.toArray(InventoryProduct[]::new));
        product.setRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(value == null ? "" : value.description() + " · "
                    + value.size() + " · " + value.color() + " (" + value.quantity() + ")");
            label.setOpaque(selected);
            label.setBackground(selected ? Theme.PRIMARY_SOFT : Theme.SURFACE);
            label.setForeground(Theme.TEXT);
            label.setBorder(new EmptyBorder(6, 7, 6, 7));
            return label;
        });
        JComboBox<String> type = combo(new String[]{"Ingreso", "Salida", "Ajuste positivo", "Ajuste negativo"});
        JSpinner quantity = spinner(1, 1, 999999);
        JTextArea note = new JTextArea(3, 12);
        note.setLineWrap(true);
        note.setBorder(Theme.compoundBorder(Theme.BORDER, 8, 8));
        JLabel resultingStock = Theme.label("", Theme.BODY_BOLD.deriveFont(11f), Theme.TEXT);
        Runnable updatePreview = () -> {
            InventoryProduct selected = (InventoryProduct) product.getSelectedItem();
            int amount = (Integer) quantity.getValue();
            boolean incoming = "Ingreso".equals(type.getSelectedItem())
                    || "Ajuste positivo".equals(type.getSelectedItem());
            resultingStock.setText(selected == null ? "—"
                    : (selected.quantity() + (incoming ? amount : -amount)) + " unidades");
        };
        product.addActionListener(event -> updatePreview.run());
        type.addActionListener(event -> updatePreview.run());
        quantity.addChangeListener(event -> updatePreview.run());
        form.add(field("Producto", product));
        form.add(Box.createVerticalStrut(12));
        form.add(field("Tipo de movimiento", type));
        form.add(Box.createVerticalStrut(12));
        form.add(field("Cantidad", quantity));
        form.add(Box.createVerticalStrut(12));
        form.add(field("Nota", note));
        form.add(Box.createVerticalStrut(14));
        JPanel total = new JPanel(new BorderLayout());
        total.setOpaque(false);
        total.add(Theme.label("Stock resultante", Theme.SMALL, Theme.TEXT_MUTED), BorderLayout.WEST);
        total.add(resultingStock, BorderLayout.EAST);
        form.add(total);
        form.add(Box.createVerticalStrut(14));
        JButton submit = Theme.button("Registrar movimiento", true);
        submit.setAlignmentX(Component.LEFT_ALIGNMENT);
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        submit.addActionListener(event -> {
            InventoryProduct selected = (InventoryProduct) product.getSelectedItem();
            if (selected == null) {
                return;
            }
            int amount = (Integer) quantity.getValue();
            boolean incoming = "Ingreso".equals(type.getSelectedItem())
                    || "Ajuste positivo".equals(type.getSelectedItem());
            String movementType = "Ingreso".equals(type.getSelectedItem()) ? "INGRESO"
                    : "Salida".equals(type.getSelectedItem()) ? "VENTA" : "AJUSTE";
            try {
                repository.recordMovement(selected.id(), movementType, incoming ? amount : -amount,
                        note.getText().trim(), currentUser.id());
                refreshAfterChange("Movimiento de stock registrado.");
            } catch (SQLException | IllegalArgumentException exception) {
                showDatabaseError("No se pudo registrar el movimiento.", exception);
            }
        });
        form.add(submit);
        card.add(form, BorderLayout.CENTER);
        updatePreview.run();
        return card;
    }

    private JComponent createSalesView(DashboardData data, List<SaleRecord> sales) {
        JPanel content = pageColumn();
        content.add(metricGrid(List.of(
                metric("V", "Ventas del mes", formatMoney(data.monthRevenue()),
                        data.monthSales() + " operaciones", Theme.PRIMARY, Theme.PRIMARY_SOFT),
                metric("$", "Ticket promedio", formatMoney(data.averageTicket()),
                        "Ventas completadas", Theme.BLUE, Theme.BLUE_SOFT),
                metric("↑", "Ganancia estimada", formatMoney(data.estimatedProfit()),
                        "Venta menos costo registrado", Theme.GREEN, Theme.GREEN_SOFT),
                metric("%", "Ventas financiadas",
                        Long.toString(data.financedSales()),
                        "Operaciones del historial", Theme.AMBER, Theme.AMBER_SOFT))));
        content.add(Box.createVerticalStrut(17));

        JPanel upper = new JPanel(new GridBagLayout());
        upper.setOpaque(false);
        GridBagConstraints constraints = fillConstraints();
        constraints.weightx = .7;
        RoundedPanel evolution = sectionCard("Evolución de ventas", "Facturación diaria del mes actual");
        evolution.setPreferredSize(new Dimension(700, 330));
        JPanel value = new JPanel(new BorderLayout());
        value.setOpaque(false);
        value.setBorder(new EmptyBorder(0, 19, 4, 19));
        value.add(Theme.label(formatMoney(data.monthRevenue()),
                Theme.FONT.deriveFont(Font.BOLD, 23f), Theme.TEXT), BorderLayout.WEST);
        JButton createSale = Theme.button("+  Nueva venta", true);
        createSale.addActionListener(event -> {
            if (SaleDialog.show(this, repository, currentUser.id())) {
                refreshAfterChange("Venta registrada.");
            }
        });
        value.add(createSale, BorderLayout.EAST);
        JPanel evolutionBody = new JPanel(new BorderLayout());
        evolutionBody.setOpaque(false);
        evolutionBody.add(value, BorderLayout.NORTH);
        evolutionBody.add(new BarChartPanel(data.dailyRevenue()), BorderLayout.CENTER);
        evolution.add(evolutionBody, BorderLayout.CENTER);
        upper.add(evolution, constraints);
        constraints.gridx = 1;
        constraints.weightx = .3;
        constraints.insets = new Insets(0, 17, 0, 0);
        upper.add(paymentCard(data), constraints);
        content.add(upper);
        content.add(Box.createVerticalStrut(17));
        content.add(salesTableCard(sales));
        return content;
    }

    private JComponent paymentCard(DashboardData data) {
        RoundedPanel card = sectionCard("Medios de pago", "Distribución del mes");
        card.setPreferredSize(new Dimension(330, 330));
        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setBorder(new EmptyBorder(2, 19, 18, 19));
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        RoundedPanel total = new RoundedPanel(9, new Color(247, 246, 253), null);
        total.setLayout(new BorderLayout());
        total.setBorder(new EmptyBorder(12, 12, 12, 12));
        total.add(Theme.label("Total procesado", Theme.SMALL.deriveFont(9f), Theme.TEXT_MUTED), BorderLayout.NORTH);
        total.add(Theme.label(formatMoney(data.monthRevenue()),
                Theme.FONT.deriveFont(Font.BOLD, 16f), Theme.TEXT), BorderLayout.SOUTH);
        rows.add(total);
        rows.add(Box.createVerticalStrut(12));
        Color[] colors = {Theme.PRIMARY, Theme.BLUE, Theme.GREEN, Theme.AMBER};
        for (int i = 0; i < data.paymentTotals().size(); i++) {
            DashboardData.PaymentTotal payment = data.paymentTotals().get(i);
            String share = data.monthRevenue().signum() == 0 ? "0%"
                    : Math.round(payment.amount().multiply(BigDecimal.valueOf(100))
                            .divide(data.monthRevenue(), 2, java.math.RoundingMode.HALF_UP)
                            .doubleValue()) + "%";
            rows.add(paymentRow(payment.method(), share, formatMoney(payment.amount()), colors[i % colors.length]));
        }
        if (data.paymentTotals().isEmpty()) {
            rows.add(Theme.label("Todavía no hay pagos en el período.",
                    Theme.SMALL, Theme.TEXT_MUTED));
        }
        card.add(rows, BorderLayout.CENTER);
        return card;
    }

    private JComponent salesTableCard(List<SaleRecord> sales) {
        RoundedPanel card = sectionCard("Historial de ventas", "Operaciones guardadas en MySQL");
        String[] columns = {"VENTA", "FECHA", "CLIENTE", "MEDIO DE PAGO", "ESTADO", "TOTAL", "SALE_DATA"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        sales.forEach(sale -> model.addRow(new Object[]{
            "#" + sale.id(), sale.date().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            sale.customer(), sale.paymentMethod(), sale.status(), formatMoney(sale.total()), sale
        }));
        JTable table = styledTable(model, new int[]{75, 95, 170, 145, 90, 100, 0});
        table.removeColumn(table.getColumnModel().getColumn(6));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        toolbar.setOpaque(false);
        JButton cancel = Theme.button("Cancelar venta", false);
        cancel.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(event ->
                cancel.setEnabled(table.getSelectedRow() >= 0));
        cancel.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                return;
            }
            SaleRecord sale = (SaleRecord) model.getValueAt(table.convertRowIndexToModel(row), 6);
            if ("Cancelada".equalsIgnoreCase(sale.status())) {
                return;
            }
            if (JOptionPane.showConfirmDialog(this,
                    "¿Cancelar la venta #" + sale.id() + " y reintegrar sus productos al stock?",
                    "Cancelar venta", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                repository.cancelSale(sale.id(), currentUser.id());
                refreshAfterChange("Venta cancelada y stock reintegrado.");
            } catch (SQLException exception) {
                showDatabaseError("No se pudo cancelar la venta.", exception);
            }
        });
        JButton export = Theme.button("Exportar CSV", false);
        export.addActionListener(event -> exportSales());
        toolbar.add(export);
        toolbar.add(cancel);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(toolbar, BorderLayout.NORTH);
        center.add(tableScroll(table), BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);
        card.add(tableFooter("Mostrando " + model.getRowCount() + " ventas"), BorderLayout.SOUTH);
        return card;
    }

    private JComponent createMovementsView(List<MovementRecord> movements) {
        LocalDate today = LocalDate.now();
        List<MovementRecord> todayMovements = movements.stream()
                .filter(item -> item.dateTime().toLocalDate().equals(today)).toList();
        long incoming = todayMovements.stream().filter(item -> item.quantityChange() > 0).count();
        long outgoing = todayMovements.stream().filter(item -> item.quantityChange() < 0).count();
        long adjustments = todayMovements.stream().filter(item -> "AJUSTE".equals(item.type())).count();
        MovementRecord latest = movements.isEmpty() ? null : movements.get(0);
        JPanel content = pageColumn();
        content.add(metricGrid(List.of(
                metric("↓", "Ingresos de hoy", Long.toString(incoming),
                        todayMovements.stream().filter(item -> item.quantityChange() > 0)
                                .mapToInt(MovementRecord::quantityChange).sum() + " unidades",
                        Theme.GREEN, Theme.GREEN_SOFT),
                metric("↑", "Salidas de hoy", Long.toString(outgoing),
                        todayMovements.stream().filter(item -> item.quantityChange() < 0)
                                .mapToInt(item -> Math.abs(item.quantityChange())).sum() + " unidades",
                        Theme.PRIMARY, Theme.PRIMARY_SOFT),
                metric("↕", "Ajustes de hoy", Long.toString(adjustments),
                        "Recuentos y correcciones", Theme.BLUE, Theme.BLUE_SOFT),
                metric("T", "Último movimiento", latest == null ? "Sin actividad"
                                : latest.dateTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                        latest == null ? "Aún no hay movimientos" : latest.user(),
                        Theme.AMBER, Theme.AMBER_SOFT))));
        content.add(Box.createVerticalStrut(17));

        JPanel layout = new JPanel(new BorderLayout(17, 0));
        layout.setOpaque(false);
        layout.add(movementsTableCard(movements), BorderLayout.CENTER);
        JComponent activity = activityCard(todayMovements);
        activity.setPreferredSize(new Dimension(285, 520));
        layout.add(activity, BorderLayout.EAST);
        content.add(layout);
        return content;
    }

    private JComponent movementsTableCard(List<MovementRecord> movements) {
        RoundedPanel card = sectionCard("Historial de movimientos", "Ingresos, ventas y ajustes registrados");
        String[] columns = {"ID", "TIPO", "PRODUCTO", "CAMBIO", "FECHA", "USUARIO", "NOTA", "MOVEMENT_DATA"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        movements.forEach(item -> model.addRow(new Object[]{
            item.id(), item.type(), item.product(), (item.quantityChange() > 0 ? "+" : "")
                    + item.quantityChange() + " u.",
            item.dateTime().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")),
            item.user(), item.note(), item
        }));
        JTable table = styledTable(model, new int[]{70, 90, 170, 85, 115, 140, 180, 0});
        table.removeColumn(table.getColumnModel().getColumn(7));
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(Theme.SURFACE_SUBTLE);
        toolbar.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.BORDER));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        filters.setOpaque(false);
        JTextField search = textField("");
        search.setToolTipText("Buscar producto, usuario o nota");
        search.setPreferredSize(new Dimension(280, 35));
        filters.add(search);
        JComboBox<String> type = combo(new String[]{"Todos los tipos", "INGRESO", "VENTA", "AJUSTE"});
        JComboBox<String> date = combo(new String[]{"Todas las fechas", "Hoy", "Últimos 7 días", "Últimos 30 días"});
        filters.add(type);
        filters.add(date);
        JButton export = Theme.button("Exportar CSV", false);
        export.addActionListener(event -> exportMovements());
        filters.add(export);
        Runnable applyFilter = () -> {
            List<RowFilter<DefaultTableModel, Integer>> rowFilters = new ArrayList<>();
            if (!search.getText().isBlank()) {
                rowFilters.add(RowFilter.regexFilter(
                        "(?i)" + java.util.regex.Pattern.quote(search.getText().trim()), 0, 1, 2, 4, 5, 6));
            }
            String selectedType = (String) type.getSelectedItem();
            if (selectedType != null && !"Todos los tipos".equals(selectedType)) {
                rowFilters.add(RowFilter.regexFilter(selectedType, 1));
            }
            String selectedDate = (String) date.getSelectedItem();
            if (!"Todas las fechas".equals(selectedDate)) {
                LocalDate from = switch (selectedDate) {
                    case "Hoy" -> LocalDate.now();
                    case "Últimos 7 días" -> LocalDate.now().minusDays(6);
                    default -> LocalDate.now().minusDays(29);
                };
                rowFilters.add(new RowFilter<>() {
                    @Override
                    public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                        MovementRecord movement = (MovementRecord) entry.getValue(7);
                        LocalDate movementDate = movement.dateTime().toLocalDate();
                        return !movementDate.isBefore(from) && !movementDate.isAfter(LocalDate.now());
                    }
                });
            }
            sorter.setRowFilter(rowFilters.isEmpty() ? null : RowFilter.andFilter(rowFilters));
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { applyFilter.run(); }
            @Override public void removeUpdate(DocumentEvent event) { applyFilter.run(); }
            @Override public void changedUpdate(DocumentEvent event) { applyFilter.run(); }
        });
        type.addActionListener(event -> applyFilter.run());
        date.addActionListener(event -> applyFilter.run());
        toolbar.add(filters);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(toolbar, BorderLayout.NORTH);
        center.add(tableScroll(table), BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);
        card.add(tableFooter("Mostrando " + model.getRowCount() + " movimientos"), BorderLayout.SOUTH);
        return card;
    }

    private JComponent activityCard(List<MovementRecord> movements) {
        RoundedPanel card = sectionCard("Actividad de hoy", "Resumen en tiempo real");
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(0, 19, 18, 19));
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(Theme.label(Integer.toString(movements.size()), Theme.FONT.deriveFont(Font.BOLD, 28f), Theme.TEXT));
        body.add(Theme.label("movimientos hoy", Theme.SMALL, Theme.TEXT_MUTED));
        body.add(Box.createVerticalStrut(15));

        RoundedPanel split = new RoundedPanel(9, Theme.SURFACE, Theme.BORDER);
        split.setLayout(new GridLayout(1, 2));
        split.add(statBlock(Long.toString(movements.stream().filter(item -> item.quantityChange() > 0).count()),
                "Ingresos", Theme.GREEN));
        split.add(statBlock(Long.toString(movements.stream().filter(item -> item.quantityChange() < 0).count()),
                "Salidas", Theme.PRIMARY));
        body.add(split);
        body.add(Box.createVerticalStrut(17));
        for (MovementRecord movement : movements.stream().limit(4).toList()) {
            JPanel item = new JPanel(new BorderLayout(9, 0));
            item.setOpaque(false);
            JLabel dot = Theme.label("●", Theme.SMALL, movement.quantityChange() > 0 ? Theme.GREEN : Theme.PRIMARY);
            JPanel itemCopy = verticalPanel(2);
            itemCopy.add(Theme.label(movement.type() + " de stock", Theme.BODY_BOLD.deriveFont(10f), Theme.TEXT));
            itemCopy.add(Theme.label(movement.product(), Theme.SMALL.deriveFont(9f), Theme.TEXT_MUTED));
            itemCopy.add(Theme.label(movement.dateTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                    Theme.SMALL.deriveFont(8f), new Color(170, 174, 183)));
            item.add(dot, BorderLayout.WEST);
            item.add(itemCopy, BorderLayout.CENTER);
            body.add(item);
            body.add(Box.createVerticalStrut(13));
        }
        JButton report = Theme.button("Ver reporte del día", false);
        report.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        report.setAlignmentX(Component.LEFT_ALIGNMENT);
        report.addActionListener(event -> exportMovements());
        body.add(report);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent createSettingsView(Map<String, String> settings) {
        settingInputs.clear();
        loadedSettings.clear();
        loadedSettings.putAll(settings);
        currencyCode = loadedSettings.getOrDefault("store.currency", "ARS");
        RoundedPanel shell = new RoundedPanel();
        shell.setLayout(new BorderLayout());
        shell.setPreferredSize(new Dimension(1000, 650));

        List<String> categories = new ArrayList<>(List.of(
                "General", "Inventario", "Ventas y pagos"));
        if (currentUser.isOwner()) {
            categories.add("Usuarios");
        }
        categories.addAll(List.of("Comprobantes", "Copias de seguridad", "Apariencia", "Sistema"));
        JPanel menu = new JPanel();
        menu.setBackground(Theme.SURFACE_SUBTLE);
        menu.setPreferredSize(new Dimension(215, 0));
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER),
                new EmptyBorder(19, 12, 19, 12)));
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        JLabel label = Theme.label("PREFERENCIAS", Theme.LABEL, Theme.TEXT_MUTED);
        label.setBorder(new EmptyBorder(0, 10, 10, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        menu.add(label);

        CardLayout settingsLayout = new CardLayout();
        JPanel settingsCards = new JPanel(settingsLayout);
        settingsCards.setOpaque(false);
        List<NavButton> settingsButtons = new ArrayList<>();
        for (String category : categories) {
            NavButton button = new NavButton("·", category);
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(event -> {
                settingsLayout.show(settingsCards, category);
                settingsButtons.forEach(item -> item.setActive(item == button));
            });
            settingsButtons.add(button);
            menu.add(button);
            menu.add(Box.createVerticalStrut(3));
        }

        settingsCards.add(settingsGeneral(), "General");
        settingsCards.add(settingsInventory(), "Inventario");
        settingsCards.add(settingsSales(), "Ventas y pagos");
        if (currentUser.isOwner()) {
            settingsCards.add(settingsUsers(), "Usuarios");
        }
        settingsCards.add(settingsReceipts(), "Comprobantes");
        settingsCards.add(settingsBackup(), "Copias de seguridad");
        settingsCards.add(settingsAppearance(), "Apariencia");
        settingsCards.add(settingsSystem(), "Sistema");
        settingsButtons.get(0).setActive(true);

        shell.add(menu, BorderLayout.WEST);
        shell.add(settingsCards, BorderLayout.CENTER);
        return shell;
    }

    private JComponent settingsGeneral() {
        return settingPage(
                "General",
                "Datos principales del comercio y preferencias regionales.",
                settingSection("Información del comercio",
                        settingFieldRow("store.name", "Nombre comercial", "Se mostrará en comprobantes y reportes.", textField("")),
                        settingFieldRow("store.cuit", "CUIT", "Identificación fiscal del negocio.", textField("")),
                        settingFieldRow("store.address", "Dirección", "Domicilio comercial.", textField("")),
                        actionRow("Logotipo", "Elegí la imagen que aparece en la barra lateral.",
                                "Seleccionar imagen", this::chooseCompanyLogo)),
                settingSection("Región",
                        settingFieldRow("store.currency", "Moneda", "Formato utilizado para importes.",
                                combo(new String[]{"ARS", "USD"}))));
    }

    private JComponent settingsInventory() {
        return settingPage(
                "Inventario",
                "Definí alertas, códigos y criterios de control de stock.",
                settingSection("Control de existencias",
                        settingFieldRow("inventory.low_stock_threshold", "Stock mínimo predeterminado",
                                "Se aplica al crear productos.", spinner(5, 0, 999)),
                        settingToggleRow("inventory.allow_negative", "Permitir stock negativo",
                                "Autoriza salidas aunque no haya unidades disponibles.", false)));
    }

    private JComponent settingsSales() {
        return settingPage(
                "Ventas y pagos",
                "Configurá condiciones comerciales y medios de pago.",
                settingSection("Condiciones de venta",
                        settingFieldRow("sales.max_installments", "Cantidad máxima de cuotas",
                                "Límite disponible al configurar productos.", combo(new String[]{"3", "6", "9", "12"})),
                        infoRow("Precios financiados", "Se configuran por producto en el inventario.")),
                settingSection("Medios de pago",
                        infoRow("Medios de pago disponibles",
                                "Efectivo, débito, crédito, transferencia y financiación")));
    }

    private JComponent settingsUsers() {
        JButton manage = Theme.button("Administrar perfiles", true);
        manage.addActionListener(event -> UserManagementDialog.show(this, repository));
        JButton password = Theme.button("Cambiar mi contraseña", false);
        password.addActionListener(event -> changeOwnPassword());
        return settingPage("Usuarios",
                "Administrá los perfiles que pueden ingresar a Nexo.",
                settingSection("Cuenta activa",
                        infoRow("Sesión iniciada", currentUser.displayName() + " · " + currentUser.role()),
                        actionRow("Perfiles de acceso", "Crear usuarios, asignar roles y desactivar perfiles.",
                                manage),
                        actionRow("Seguridad", "Actualizá la contraseña del perfil actual.", password)));
    }

    private JComponent settingsReceipts() {
        return settingPage(
                "Comprobantes",
                "Personalizá la información impresa después de cada venta.",
                settingSection("Formato",
                        settingFieldRow("receipts.paper_size", "Tamaño de papel",
                                "Formato para impresión térmica.", combo(new String[]{"80 mm", "58 mm", "A4"})),
                        settingToggleRow("receipts.show_barcode", "Mostrar código de barras",
                                "Incluye el identificador de cada producto.", true),
                        settingToggleRow("receipts.show_installments", "Mostrar detalle de cuotas",
                                "Incluye el plan elegido y el importe por cuota.", true)),
                settingSection("Pie del comprobante",
                        settingFieldRow("receipts.footer", "Mensaje", "Texto final para el cliente.",
                                textField(""))));
    }

    private JComponent settingsBackup() {
        return settingPage(
                "Copias de seguridad",
                "Exportá una copia de la información guardada en MySQL.",
                settingSection("Respaldo automático",
                        settingFieldRow("backup.retention_days", "Conservar copias",
                                "Período de conservación recomendado.", combo(new String[]{"7", "15", "30", "90"}))),
                settingSection("Acciones manuales",
                        actionRow("Respaldo de base", "Genera un archivo SQL con los datos actuales.",
                                "Exportar copia SQL", this::exportBackup)));
    }

    private JComponent settingsAppearance() {
        return settingPage(
                "Apariencia",
                "Elegí cómo se presenta la interfaz en este equipo.",
                settingSection("Escala de interfaz",
                        settingFieldRow("appearance.scale", "Tamaño de controles",
                                "Ajusta el tamaño de textos y controles.", combo(new String[]{"100 %", "110 %", "125 %"})),
                        infoRow("Tema", "Claro")));
    }

    private JComponent settingsSystem() {
        return settingPage(
                "Sistema",
                "Información técnica y mantenimiento de la aplicación.",
                settingSection("Aplicación",
                        infoRow("Versión", "Nexo 1.0.0"),
                        infoRow("Tecnología", "Java 25 · Swing"),
                        infoRow("Base de datos", "MySQL · " + repository.databaseName())),
                settingSection("Mantenimiento",
                        actionRow("Diagnóstico de conexión", "Comprueba lectura y escritura en MySQL.",
                                "Ejecutar diagnóstico", this::runDatabaseDiagnostic)));
    }

    private JComponent settingPage(String heading, String description, JComponent... sections) {
        JPanel content = pageColumn();
        content.setBorder(new EmptyBorder(25, 28, 28, 28));
        content.add(Theme.label(heading, Theme.TITLE, Theme.TEXT));
        content.add(Box.createVerticalStrut(5));
        content.add(Theme.label(description, Theme.SMALL, Theme.TEXT_MUTED));
        content.add(Box.createVerticalStrut(22));
        for (JComponent section : sections) {
            content.add(section);
            content.add(Box.createVerticalStrut(15));
        }
        JButton save = Theme.button("Guardar cambios", true);
        save.setAlignmentX(Component.LEFT_ALIGNMENT);
        save.addActionListener(event -> saveSettings());
        content.add(save);
        return wrapPage(content);
    }

    private JComponent settingSection(String heading, JComponent... rows) {
        RoundedPanel section = new RoundedPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        JLabel titleLabel = Theme.label(heading, Theme.BODY_BOLD, Theme.TEXT);
        titleLabel.setBorder(new EmptyBorder(16, 18, 14, 18));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(titleLabel);
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                JSeparator separator = separator();
                separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                section.add(separator);
            }
            rows[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(rows[i]);
        }
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height));
        return section;
    }

    private JComponent settingFieldRow(String key, String heading, String description, JComponent input) {
        String value = loadedSettings.get(key);
        if (value != null) {
            if (input instanceof JTextField text) {
                text.setText(value);
            } else if (input instanceof JComboBox<?> select) {
                select.setSelectedItem(value);
            } else if (input instanceof JSpinner number) {
                try {
                    number.setValue(Integer.parseInt(value));
                } catch (NumberFormatException exception) {
                    throw new IllegalStateException("La configuración " + key + " no es un número válido.", exception);
                }
            }
        }
        settingInputs.put(key, input);
        JPanel row = settingRowBase(heading, description);
        input.setPreferredSize(new Dimension(245, 36));
        input.setMinimumSize(new Dimension(210, 36));
        row.add(input, BorderLayout.EAST);
        return row;
    }

    private JComponent settingToggleRow(String key, String heading, String description, boolean selected) {
        selected = Boolean.parseBoolean(loadedSettings.getOrDefault(key, Boolean.toString(selected)));
        JPanel row = settingRowBase(heading, description);
        JCheckBox toggle = new JCheckBox(selected ? "Activado" : "Desactivado", selected);
        toggle.setOpaque(false);
        toggle.setForeground(selected ? Theme.GREEN : Theme.TEXT_MUTED);
        toggle.setFont(Theme.BODY_BOLD.deriveFont(10f));
        toggle.setFocusPainted(false);
        toggle.addActionListener(event -> {
            toggle.setText(toggle.isSelected() ? "Activado" : "Desactivado");
            toggle.setForeground(toggle.isSelected() ? Theme.GREEN : Theme.TEXT_MUTED);
        });
        settingInputs.put(key, toggle);
        row.add(toggle, BorderLayout.EAST);
        return row;
    }

    private JComponent infoRow(String heading, String value) {
        JPanel row = settingRowBase(heading, "");
        row.add(Theme.label(value, Theme.BODY_BOLD.deriveFont(11f), Theme.TEXT_MUTED), BorderLayout.EAST);
        return row;
    }

    private JComponent actionRow(String heading, String description, String action) {
        JPanel row = settingRowBase(heading, description);
        row.add(Theme.button(action, false), BorderLayout.EAST);
        return row;
    }

    private JComponent actionRow(String heading, String description, JButton action) {
        JPanel row = settingRowBase(heading, description);
        row.add(action, BorderLayout.EAST);
        return row;
    }

    private JComponent actionRow(String heading, String description, String action, Runnable operation) {
        JPanel row = settingRowBase(heading, description);
        JButton button = Theme.button(action, false);
        button.addActionListener(event -> operation.run());
        row.add(button, BorderLayout.EAST);
        return row;
    }

    private JPanel settingRowBase(String heading, String description) {
        JPanel row = new JPanel(new BorderLayout(20, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(13, 18, 13, 18));
        JPanel copy = verticalPanel(3);
        copy.add(Theme.label(heading, Theme.BODY_BOLD.deriveFont(11f), Theme.TEXT));
        if (!description.isBlank()) {
            copy.add(Theme.label(description, Theme.SMALL.deriveFont(10f), Theme.TEXT_MUTED));
        }
        row.add(copy, BorderLayout.CENTER);
        return row;
    }

    private RoundedPanel recentSalesCard(List<SaleRecord> sales) {
        RoundedPanel card = sectionCard("Ventas recientes", "Últimas operaciones registradas");
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        List<SaleRecord> recent = sales.stream().limit(4).toList();
        for (int i = 0; i < recent.size(); i++) {
            SaleRecord sale = recent.get(i);
            JPanel row = listRow(sale.customer(), "#" + sale.id() + " · "
                    + sale.date().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    formatMoney(sale.total()));
            list.add(row);
            if (i < recent.size() - 1) {
                list.add(separator());
            }
        }
        if (recent.isEmpty()) {
            list.add(listRow("Todavía no hay ventas", "Las operaciones aparecerán aquí.", "—"));
        }
        card.add(list, BorderLayout.CENTER);
        return card;
    }

    private RoundedPanel lowStockCard(List<InventoryProduct> products) {
        RoundedPanel card = sectionCard("Productos con stock bajo", "Próximos a agotarse");
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        List<InventoryProduct> lowStock = products.stream()
                .filter(product -> product.quantity() <= product.minimumStock())
                .limit(4).toList();
        for (int i = 0; i < lowStock.size(); i++) {
            InventoryProduct product = lowStock.get(i);
            JPanel row = listRow(product.description(),
                    product.category() + " · " + product.size(),
                    product.quantity() + " u.");
            list.add(row);
            if (i < lowStock.size() - 1) {
                list.add(separator());
            }
        }
        if (lowStock.isEmpty()) {
            list.add(listRow("Todo en orden", "No hay productos por debajo del mínimo.", "—"));
        }
        card.add(list, BorderLayout.CENTER);
        return card;
    }

    private JPanel listRow(String heading, String detail, String amount) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(10, 18, 10, 18));
        JPanel copy = verticalPanel(3);
        copy.add(Theme.label(heading, Theme.BODY_BOLD.deriveFont(10f), Theme.TEXT));
        copy.add(Theme.label(detail, Theme.SMALL.deriveFont(9f), Theme.TEXT_MUTED));
        row.add(copy, BorderLayout.CENTER);
        row.add(Theme.label(amount, Theme.BODY_BOLD.deriveFont(10f), Theme.TEXT), BorderLayout.EAST);
        return row;
    }

    private RoundedPanel sectionCard(String heading, String description) {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new BorderLayout());
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(17, 19, 15, 19));
        JPanel copy = verticalPanel(3);
        copy.add(Theme.label(heading, Theme.FONT.deriveFont(Font.BOLD, 14f), Theme.TEXT));
        copy.add(Theme.label(description, Theme.SMALL.deriveFont(10f), Theme.TEXT_MUTED));
        header.add(copy, BorderLayout.WEST);
        card.add(header, BorderLayout.PAGE_START);
        return card;
    }

    private JPanel metricGrid(List<MetricCard> metrics) {
        JPanel grid = new JPanel(new GridLayout(1, 4, 14, 0));
        grid.setOpaque(false);
        metrics.forEach(grid::add);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 106));
        return grid;
    }

    private MetricCard metric(
            String marker, String label, String value, String note, Color tone, Color softTone) {
        return new MetricCard(marker, label, value, note, tone, softTone);
    }

    private JPanel paymentRow(String label, String percent, String amount, Color color) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(7, 0, 7, 0));
        JLabel name = Theme.label("●  " + label, Theme.SMALL.deriveFont(9f), color);
        name.setPreferredSize(new Dimension(120, 18));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(Theme.label(percent, Theme.LABEL.deriveFont(9f), Theme.TEXT_MUTED));
        right.add(Theme.label(amount, Theme.LABEL.deriveFont(9f), Theme.TEXT));
        row.add(name, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private JPanel legend(String name, String value, Color color) {
        JPanel item = new JPanel(new BorderLayout());
        item.setOpaque(false);
        item.add(Theme.label(name, Theme.SMALL.deriveFont(9f), color), BorderLayout.WEST);
        item.add(Theme.label(value, Theme.LABEL.deriveFont(9f), Theme.TEXT), BorderLayout.EAST);
        return item;
    }

    private JPanel statBlock(String value, String caption, Color color) {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setBorder(new EmptyBorder(10, 12, 10, 12));
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.add(Theme.label("●  " + value, Theme.BODY_BOLD.deriveFont(12f), color));
        block.add(Box.createVerticalStrut(2));
        block.add(Theme.label(caption, Theme.SMALL.deriveFont(9f), Theme.TEXT_MUTED));
        return block;
    }

    private JTable styledTable(DefaultTableModel model, int[] widths) {
        JTable table = new JTable(model);
        table.setRowHeight(57);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(new Color(238, 240, 243));
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(new Color(248, 247, 255));
        table.setSelectionForeground(Theme.TEXT);
        table.setForeground(new Color(80, 87, 100));
        table.setBackground(Theme.SURFACE);
        table.getTableHeader().setPreferredSize(new Dimension(0, 37));
        table.getTableHeader().setBackground(new Color(252, 252, 253));
        table.getTableHeader().setForeground(new Color(156, 162, 174));
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);
        for (int index = 0; index < widths.length; index++) {
            table.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
        }
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setBorder(new EmptyBorder(0, 11, 0, 11));
        table.setDefaultRenderer(Object.class, renderer);
        return table;
    }

    private JScrollPane tableScroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.setPreferredSize(new Dimension(700, 350));
        return scroll;
    }

    private JPanel tableFooter(String text) {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(12, 19, 12, 19)));
        footer.add(Theme.label(text, Theme.SMALL.deriveFont(9f), Theme.TEXT_MUTED), BorderLayout.WEST);
        return footer;
    }

    private JPanel field(String label, JComponent input) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel fieldLabel = Theme.label(label, Theme.LABEL.deriveFont(9f), new Color(85, 92, 105));
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        input.setAlignmentX(Component.LEFT_ALIGNMENT);
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, input instanceof JTextArea ? 70 : 37));
        panel.add(fieldLabel);
        panel.add(Box.createVerticalStrut(7));
        panel.add(input);
        return panel;
    }

    private JTextField textField(String value) {
        JTextField input = new JTextField(value);
        input.setForeground(value.startsWith("Buscar") ? Theme.TEXT_MUTED : Theme.TEXT);
        input.setBackground(Theme.SURFACE);
        input.setBorder(Theme.compoundBorder(Theme.BORDER, 8, 10));
        input.setPreferredSize(new Dimension(220, 36));
        return input;
    }

    private JPasswordField passwordField() {
        JPasswordField input = new JPasswordField("nexo2025");
        input.setBorder(Theme.compoundBorder(Theme.BORDER, 8, 10));
        return input;
    }

    private JSpinner spinner(int value, int minimum, int maximum) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, minimum, maximum, 1));
        styleInput(spinner);
        return spinner;
    }

    private void styleInput(JComponent input) {
        input.setBorder(Theme.compoundBorder(Theme.BORDER, 6, 8));
        input.setBackground(Theme.SURFACE);
    }

    private JComboBox<String> combo(String[] options) {
        JComboBox<String> combo = new JComboBox<>(options);
        combo.setBackground(Theme.SURFACE);
        combo.setForeground(new Color(95, 102, 116));
        combo.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        combo.setPreferredSize(new Dimension(180, 35));
        combo.setFocusable(false);
        return combo;
    }

    private JRadioButton radio(String text, boolean selected) {
        JRadioButton button = new JRadioButton(text, selected);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setFont(Theme.BODY_BOLD.deriveFont(10f));
        button.setForeground(selected ? Theme.GREEN : Theme.TEXT_MUTED);
        button.setBackground(selected ? Theme.SURFACE : new Color(241, 242, 245));
        button.setBorder(Theme.compoundBorder(Theme.BORDER, 8, 9));
        button.setFocusPainted(false);
        button.addActionListener(event -> button.setForeground(button.isSelected() ? Theme.GREEN : Theme.TEXT_MUTED));
        return button;
    }

    private JButton iconButton(String text) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(36, 36));
        button.setForeground(new Color(111, 117, 130));
        button.setBackground(Theme.SURFACE);
        button.setBorder(Theme.compoundBorder(Theme.BORDER, 0, 0));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JSeparator separator() {
        JSeparator separator = new JSeparator();
        separator.setForeground(Theme.BORDER);
        separator.setBackground(Theme.BORDER);
        return separator;
    }

    private JPanel verticalPanel(int gap) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        if (gap > 0) {
            panel.setBorder(new EmptyBorder(gap, 0, gap, 0));
        }
        return panel;
    }

    private JPanel pageColumn() {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        return content;
    }

    private JScrollPane wrapPage(JComponent content) {
        JPanel margin = new JPanel(new BorderLayout());
        margin.setBackground(Theme.BACKGROUND);
        margin.setBorder(new EmptyBorder(22, 28, 30, 28));
        margin.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(margin);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private void refreshAfterChange(String message) {
        loadSection(currentSection);
        if (message != null) {
            JOptionPane.showMessageDialog(this, message, "Nexo", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showDatabaseError(String message, Throwable exception) {
        JOptionPane.showMessageDialog(this, message + "\n" + exception.getMessage(),
                "Error de base de datos", JOptionPane.ERROR_MESSAGE);
    }

    private void showLowStockAlerts() {
        try {
            List<InventoryProduct> lowStock = repository.listProducts().stream()
                    .filter(product -> product.quantity() <= product.minimumStock())
                    .toList();
            if (lowStock.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No hay productos con stock bajo.",
                        "Alertas de inventario", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String details = lowStock.stream()
                    .map(product -> product.description() + " — " + product.quantity()
                            + " disponibles (mínimo " + product.minimumStock() + ")")
                    .collect(java.util.stream.Collectors.joining("\n"));
            JOptionPane.showMessageDialog(this, details, "Productos con stock bajo",
                    JOptionPane.WARNING_MESSAGE);
        } catch (SQLException exception) {
            showDatabaseError("No se pudieron consultar las alertas.", exception);
        }
    }

    private void exportSales() {
        try {
            List<SaleRecord> sales = repository.listSales();
            exportCsv("Ventas", "ventas.csv", writer -> {
                writer.write("Venta,Fecha,Cliente,Medio de pago,Estado,Total,Vendedor\n");
                for (SaleRecord sale : sales) {
                    writeCsvRow(writer, "#" + sale.id(), sale.date().toString(), sale.customer(),
                            sale.paymentMethod(), sale.status(), sale.total().toPlainString(), sale.seller());
                }
            });
        } catch (SQLException | IOException exception) {
            showDatabaseError("No se pudo exportar el historial de ventas.", exception);
        }
    }

    private void exportMovements() {
        try {
            List<MovementRecord> movements = repository.listMovements();
            exportCsv("Movimientos", "movimientos.csv", writer -> {
                writer.write("ID,Tipo,Producto,Cambio de stock,Fecha y hora,Nota,Usuario\n");
                for (MovementRecord movement : movements) {
                    writeCsvRow(writer, Long.toString(movement.id()), movement.type(), movement.product(),
                            Integer.toString(movement.quantityChange()), movement.dateTime().toString(),
                            movement.note(), movement.user());
                }
            });
        } catch (SQLException | IOException exception) {
            showDatabaseError("No se pudo exportar el historial de movimientos.", exception);
        }
    }

    private void exportCsv(String title, String suggestedName, CsvWriter export) throws IOException {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setSelectedFile(new java.io.File(suggestedName));
        chooser.setFileFilter(new FileNameExtensionFilter("Archivo CSV (*.csv)", "csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path destination = withExtension(chooser.getSelectedFile().toPath(), ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            export.write(writer);
        }
        JOptionPane.showMessageDialog(this, "Archivo guardado en:\n" + destination.toAbsolutePath(),
                "Exportación completada", JOptionPane.INFORMATION_MESSAGE);
    }

    private void writeCsvRow(BufferedWriter writer, String... values) throws IOException {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                writer.write(',');
            }
            String value = values[index] == null ? "" : values[index];
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write('\n');
    }

    private void exportBackup() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exportar copia SQL");
        chooser.setSelectedFile(new java.io.File("nexo-backup.sql"));
        chooser.setFileFilter(new FileNameExtensionFilter("Copia SQL (*.sql)", "sql"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path destination = withExtension(chooser.getSelectedFile().toPath(), ".sql");
        try {
            repository.exportSqlBackup(destination);
            JOptionPane.showMessageDialog(this, "Copia guardada en:\n" + destination.toAbsolutePath(),
                    "Copia completada", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException | IOException exception) {
            showDatabaseError("No se pudo crear la copia de seguridad.", exception);
        }
    }

    private Path withExtension(Path selected, String extension) {
        return selected.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension)
                ? selected : selected.resolveSibling(selected.getFileName() + extension);
    }

    private void changeOwnPassword() {
        JPasswordField password = new JPasswordField();
        JPasswordField confirmation = new JPasswordField();
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
        fields.add(new JLabel("Nueva contraseña"));
        fields.add(password);
        fields.add(new JLabel("Confirmar contraseña"));
        fields.add(confirmation);
        if (JOptionPane.showConfirmDialog(this, fields, "Cambiar contraseña",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        char[] secret = password.getPassword();
        char[] repeated = confirmation.getPassword();
        try {
            if (!java.util.Arrays.equals(secret, repeated)) {
                JOptionPane.showMessageDialog(this, "Las contraseñas no coinciden.",
                        "No se pudo cambiar la contraseña", JOptionPane.WARNING_MESSAGE);
                return;
            }
            repository.changePassword(currentUser.id(), secret);
            JOptionPane.showMessageDialog(this, "La contraseña se actualizó.",
                    "Usuario actualizado", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException | IllegalArgumentException exception) {
            showDatabaseError("No se pudo cambiar la contraseña.", exception);
        } finally {
            java.util.Arrays.fill(secret, '\0');
            java.util.Arrays.fill(repeated, '\0');
        }
    }

    private void runDatabaseDiagnostic() {
        try {
            repository.testConnection();
            JOptionPane.showMessageDialog(this, "La conexión con MySQL funciona correctamente.",
                    "Diagnóstico completado", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException exception) {
            showDatabaseError("No se pudo conectar con MySQL.", exception);
        }
    }

    private void chooseCompanyLogo() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Elegir logotipo del comercio");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Imágenes (PNG, JPEG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();
        new SwingWorker<ImageIcon, Void>() {
            private Path copied;

            @Override
            protected ImageIcon doInBackground() throws Exception {
                copied = BrandingAssets.copyLogo(source);
                repository.saveSettings(Map.of("store.logo_path", copied.toString()));
                return BrandingAssets.scaledIcon(copied, 30, 30);
            }

            @Override
            protected void done() {
                try {
                    brandLogoLabel.setIcon(get());
                    brandLogoLabel.setText("");
                    brandMark.setBackground(Theme.SURFACE);
                    loadedSettings.put("store.logo_path", copied.toString());
                    JOptionPane.showMessageDialog(NexoFrame.this,
                            "El logotipo del comercio se actualizó.",
                            "Imagen guardada", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showDatabaseError("Se interrumpió la carga del logotipo.", exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    showDatabaseError("No se pudo guardar el logotipo.", cause);
                }
            }
        }.execute();
    }

    private void updateBranding(Map<String, String> settings, ImageIcon logo) {
        String storeName = settings.getOrDefault("store.name", "Nexo").trim();
        brandNameLabel.setText(storeName.isEmpty() ? "Nexo" : storeName);
        if (logo == null) {
            setDefaultBrandIcon();
        } else {
            brandLogoLabel.setIcon(logo);
            brandLogoLabel.setText("");
            brandMark.setBackground(Theme.SURFACE);
        }
    }

    private void setDefaultBrandIcon() {
        if (brandLogoLabel == null) {
            return;
        }
        brandLogoLabel.setIcon(null);
        brandLogoLabel.setText("N");
        brandLogoLabel.setFont(Theme.FONT.deriveFont(Font.BOLD, 18f));
        brandLogoLabel.setForeground(Color.WHITE);
        brandMark.setBackground(Theme.PRIMARY);
    }

    private void saveSettings() {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, JComponent> entry : settingInputs.entrySet()) {
                JComponent input = entry.getValue();
                String value;
                if (input instanceof JTextField text) {
                    value = text.getText().trim();
                } else if (input instanceof JComboBox<?> select) {
                    value = String.valueOf(select.getSelectedItem());
                } else if (input instanceof JSpinner number) {
                    value = number.getValue().toString();
                } else if (input instanceof JCheckBox toggle) {
                    value = Boolean.toString(toggle.isSelected());
                } else {
                    throw new IllegalStateException("No se reconoce un campo de configuración.");
                }
                values.put(entry.getKey(), value);
            }
            if (values.getOrDefault("store.name", "").isBlank()) {
                throw new IllegalArgumentException("El nombre comercial no puede quedar vacío.");
            }
            repository.saveSettings(values);
            loadedSettings.putAll(values);
            currencyCode = values.getOrDefault("store.currency", "ARS");
            refreshAfterChange("La configuración se guardó correctamente.");
        } catch (SQLException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "No se pudo guardar la configuración", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyInterfaceScale() {
        float scale = switch (loadedSettings.getOrDefault("appearance.scale", "100 %")) {
            case "110 %" -> 1.1f;
            case "125 %" -> 1.25f;
            default -> 1f;
        };
        applyInterfaceScale(getContentPane(), scale);
    }

    private void applyInterfaceScale(Component component, float scale) {
        if (component.getFont() != null) {
            Object original = component instanceof JComponent swing
                    ? swing.getClientProperty("nexo.originalFont") : null;
            if (original == null) {
                original = component.getFont();
                if (component instanceof JComponent swing) {
                    swing.putClientProperty("nexo.originalFont", original);
                }
            }
            Font base = (Font) original;
            component.setFont(base.deriveFont(base.getSize2D() * scale));
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                applyInterfaceScale(child, scale);
            }
        }
    }

    private String formatMoney(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(
                "es-" + ("USD".equals(currencyCode) ? "US" : "AR")));
        format.setCurrency(java.util.Currency.getInstance(currencyCode));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private GridBagConstraints fillConstraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        return constraints;
    }

    private void showSection(String section) {
        if (section.equals(currentSection)) {
            return;
        }
        currentSection = section;
        viewLayout.show(viewContainer, section);
        title.setText(section);
        subtitle.setText(SUBTITLES.get(section));
        navButtons.forEach((name, button) -> button.setActive(name.equals(section)));
        loadSection(section);
    }

    private void loadSection(String section) {
        int generation = ++sectionLoadGeneration;
        JComponent previous = sectionCards.get(section);
        if (previous != null) {
            viewContainer.remove(previous);
        }
        addSectionCard(section, createSectionPlaceholder(section));
        viewLayout.show(viewContainer, section);
        viewContainer.revalidate();
        viewContainer.repaint();

        Timer timeout = new Timer(20000, event -> {
            if (generation == sectionLoadGeneration && section.equals(currentSection)) {
                sectionLoadGeneration++;
                showSectionError(section, "La consulta superó el tiempo límite.");
            }
        });
        timeout.setRepeats(false);
        timeout.start();
        SwingWorker<SectionData, Void> worker = new SwingWorker<>() {
            @Override
            protected SectionData doInBackground() throws Exception {
                Map<String, String> settings = repository.loadSettings();
                ImageIcon logo = BrandingAssets.scaledIcon(
                        Path.of(settings.getOrDefault("store.logo_path", "")), 30, 30);
                return switch (section) {
                case "Resumen" -> new SectionData(repository.loadDashboard(),
                        repository.listRecentSales(4), repository.listLowStockProducts(4), null, settings, logo);
                case "Inventario" -> new SectionData(null, null,
                        repository.listProducts(), null, settings, logo);
                case "Ventas" -> new SectionData(repository.loadDashboard(),
                        repository.listRecentSales(500), null, null, settings, logo);
                case "Movimientos" -> new SectionData(null, null, null,
                        repository.listMovements(), settings, logo);
                case "Configuración" -> new SectionData(null, null, null, null, settings, logo);
                    default -> throw new IllegalArgumentException("Sección desconocida: " + section);
                };
            }

            @Override
            protected void done() {
                timeout.stop();
                if (generation != sectionLoadGeneration || !section.equals(currentSection)) {
                    return;
                }
                try {
                    SectionData data = get();
                    loadedSettings.clear();
                    loadedSettings.putAll(data.settings());
                    currencyCode = loadedSettings.getOrDefault("store.currency", "ARS");
                    updateBranding(data.settings(), data.logo());
                    JComponent content = switch (section) {
                        case "Resumen" -> wrapPage(createSummaryView(
                                data.dashboard(), data.sales(), data.products()));
                        case "Inventario" -> wrapPage(createInventoryView(data.products()));
                        case "Ventas" -> wrapPage(createSalesView(data.dashboard(), data.sales()));
                        case "Movimientos" -> wrapPage(createMovementsView(data.movements()));
                        case "Configuración" -> wrapPage(createSettingsView(data.settings()));
                        default -> throw new IllegalArgumentException("Sección desconocida: " + section);
                    };
                    JComponent loading = sectionCards.get(section);
                    if (loading != null) {
                        viewContainer.remove(loading);
                    }
                    addSectionCard(section, content);
                    viewLayout.show(viewContainer, section);
                    applyInterfaceScale();
                    viewContainer.revalidate();
                    viewContainer.repaint();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showSectionError(section, "Se interrumpió la carga: " + exception.getMessage());
                } catch (java.util.concurrent.ExecutionException | RuntimeException exception) {
                    Throwable cause = exception instanceof java.util.concurrent.ExecutionException
                            && exception.getCause() != null ? exception.getCause() : exception;
                    showSectionError(section, "No se pudo cargar: " + cause.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void showSectionError(String section, String error) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BACKGROUND);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel message = Theme.label(error, Theme.BODY_BOLD, Theme.RED);
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel hint = Theme.label("Comprobá MySQL y la conexión, y volvé a intentar.",
                Theme.SMALL, Theme.TEXT_MUTED);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton retry = Theme.button("Reintentar", true);
        retry.setAlignmentX(Component.CENTER_ALIGNMENT);
        retry.addActionListener(event -> loadSection(section));
        content.add(message);
        content.add(Box.createVerticalStrut(7));
        content.add(hint);
        content.add(Box.createVerticalStrut(12));
        content.add(retry);
        panel.add(content, BorderLayout.CENTER);
        JComponent previous = sectionCards.get(section);
        if (previous != null) {
            viewContainer.remove(previous);
        }
        addSectionCard(section, panel);
        viewLayout.show(viewContainer, section);
        viewContainer.revalidate();
        viewContainer.repaint();
    }

    private record SectionData(
            DashboardData dashboard,
            List<SaleRecord> sales,
            List<InventoryProduct> products,
            List<MovementRecord> movements,
            Map<String, String> settings,
            ImageIcon logo) {
    }

    @FunctionalInterface
    private interface CsvWriter {
        void write(BufferedWriter writer) throws IOException;
    }

    private static final class NavButton extends JButton {
        private static final long serialVersionUID = 1L;

        private final String section;
        private final String symbol;
        private boolean active;

        private NavButton(String symbol, String section) {
            super("      " + section);
            this.symbol = symbol;
            this.section = section;
            setHorizontalAlignment(SwingConstants.LEFT);
            setPreferredSize(new Dimension(202, 42));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            setBorder(new EmptyBorder(0, 11, 0, 11));
            setFont(Theme.FONT.deriveFont(Font.PLAIN, 13f));
            setForeground(new Color(112, 119, 135));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    if (!active) {
                        setForeground(Theme.PRIMARY_DARK);
                    }
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    if (!active) {
                        setForeground(new Color(112, 119, 135));
                    }
                }
            });
        }

        private void setActive(boolean active) {
            this.active = active;
            setForeground(active ? Theme.PRIMARY_DARK : new Color(112, 119, 135));
            setFont(Theme.FONT.deriveFont(active ? Font.BOLD : Font.PLAIN, 13f));
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (active) {
                g.setColor(Theme.PRIMARY_SOFT);
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
            }
            g.setColor(active ? Theme.PRIMARY : getForeground());
            g.setFont(Theme.FONT.deriveFont(Font.BOLD, 15f));
            g.drawString(symbol, 13, 27);
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
