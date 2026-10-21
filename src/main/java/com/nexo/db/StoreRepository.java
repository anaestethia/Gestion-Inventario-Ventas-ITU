package com.nexo.db;

import com.nexo.model.DashboardData;
import com.nexo.model.InventoryProduct;
import com.nexo.model.MovementRecord;
import com.nexo.model.ProductImportRow;
import com.nexo.model.SaleLine;
import com.nexo.model.SaleRecord;
import com.nexo.model.UserProfile;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class StoreRepository {
    private static final String PRODUCT_SELECT = """
            SELECT p.id_producto, p.descripcion, p.talle, p.color, p.marca,
                   p.cantidad_inventario, p.stock_minimo, p.estacion_anio,
                   p.costo_compra_unitario, p.precio_venta_contado,
                   p.precio_venta_financiado, p.cuotas, p.codigo_barras,
                   p.id_categoria, c.nombre
            FROM producto p JOIN categoria c ON c.id_categoria = p.id_categoria
            """;

    private final Database database;

    public StoreRepository(Database database) {
        this.database = database;
    }

    public String databaseName() {
        return database.databaseName();
    }

    public void testConnection() throws SQLException {
        try (Connection connection = database.open();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT 1")) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new SQLException("La consulta de diagnóstico devolvió un resultado inesperado.");
            }
        }
    }

    public Optional<UserProfile> authenticate(String username, char[] password) throws SQLException {
        String sql = """
                SELECT id_usuario, nombre_usuario, nombre_completo, rol, password_hash
                FROM usuario WHERE nombre_usuario = ? AND activo = TRUE
                """;
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !PasswordHasher.verify(password, result.getString("password_hash"))) {
                    return Optional.empty();
                }
                return Optional.of(new UserProfile(
                        result.getInt("id_usuario"),
                        result.getString("nombre_usuario"),
                        result.getString("nombre_completo"),
                        result.getString("rol")));
            }
        }
    }

    public List<UserProfile> listUsers() throws SQLException {
        List<UserProfile> users = new ArrayList<>();
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id_usuario, nombre_usuario, nombre_completo, rol, activo "
                        + "FROM usuario ORDER BY nombre_completo");
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                users.add(new UserProfile(
                        result.getInt("id_usuario"),
                        result.getString("nombre_usuario"),
                        result.getString("nombre_completo"),
                        result.getString("rol"),
                        result.getBoolean("activo")));
            }
        }
        return users;
    }

    public List<UserProfile> listActiveUsers() throws SQLException {
        return listUsers().stream().filter(UserProfile::active).toList();
    }

    public String setting(String key, String defaultValue) throws SQLException {
        return loadSettings().getOrDefault(key, defaultValue);
    }

    public void createUser(String username, String displayName, String role, char[] password) throws SQLException {
        if (username == null || !username.matches("[A-Za-z0-9._-]{3,80}")) {
            throw new IllegalArgumentException("El usuario debe tener entre 3 y 80 caracteres válidos.");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Ingresá el nombre de la persona.");
        }
        if (!"DUEÑA".equals(role) && !"EMPLEADA".equals(role)) {
            throw new IllegalArgumentException("El perfil seleccionado no es válido.");
        }
        if (password == null || password.length < 4) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 4 caracteres.");
        }
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO usuario (nombre_usuario,nombre_completo,rol,password_hash) VALUES (?,?,?,?)")) {
            statement.setString(1, username.trim().toLowerCase(Locale.ROOT));
            statement.setString(2, displayName.trim());
            statement.setString(3, role);
            statement.setString(4, PasswordHasher.hash(password));
            statement.executeUpdate();
        }
    }

    public void updateUser(int userId, String displayName, String role, boolean active) throws SQLException {
        if (!"DUEÑA".equals(role) && !"EMPLEADA".equals(role)) {
            throw new IllegalArgumentException("El perfil seleccionado no es válido.");
        }
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                if (!"DUEÑA".equals(role) || !active) {
                    try (PreparedStatement owners = connection.prepareStatement(
                            "SELECT COUNT(*) FROM usuario WHERE rol='DUEÑA' AND activo=TRUE AND id_usuario<>?")) {
                        owners.setInt(1, userId);
                        try (ResultSet result = owners.executeQuery()) {
                            result.next();
                            if (result.getInt(1) == 0) {
                                try (PreparedStatement target = connection.prepareStatement(
                                        "SELECT rol,activo FROM usuario WHERE id_usuario=? FOR UPDATE")) {
                                    target.setInt(1, userId);
                                    try (ResultSet user = target.executeQuery()) {
                                        if (user.next() && "DUEÑA".equals(user.getString(1))
                                                && user.getBoolean(2)) {
                                            throw new IllegalArgumentException(
                                                    "No se puede quitar o desactivar a la única dueña activa.");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE usuario SET nombre_completo=?,rol=?,activo=? WHERE id_usuario=?")) {
                    update.setString(1, displayName.trim());
                    update.setString(2, role);
                    update.setBoolean(3, active);
                    update.setInt(4, userId);
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("El usuario ya no existe.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void changePassword(int userId, char[] password) throws SQLException {
        if (password == null || password.length < 4) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 4 caracteres.");
        }
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE usuario SET password_hash=? WHERE id_usuario=?")) {
            statement.setString(1, PasswordHasher.hash(password));
            statement.setInt(2, userId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("El usuario ya no existe.");
            }
        }
    }

    public List<InventoryProduct> listProducts() throws SQLException {
        List<InventoryProduct> products = new ArrayList<>();
        try (Connection connection = database.open();
        PreparedStatement statement = prepareQuery(
                connection, PRODUCT_SELECT + " ORDER BY p.descripcion");
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                products.add(readProduct(result));
            }
        }
        return products;
    }

    public Optional<InventoryProduct> findProduct(int productId) throws SQLException {
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(
                        PRODUCT_SELECT + " WHERE p.id_producto = ?")) {
            statement.setInt(1, productId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readProduct(result)) : Optional.empty();
            }
        }
    }

    public List<Map.Entry<Integer, String>> listCategories() throws SQLException {
        List<Map.Entry<Integer, String>> categories = new ArrayList<>();
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id_categoria, nombre FROM categoria ORDER BY nombre");
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                categories.add(Map.entry(result.getInt(1), result.getString(2)));
            }
        }
        return categories;
    }

    public boolean barcodeExists(String barcode, int exceptProductId) throws SQLException {
        if (barcode == null || barcode.isBlank()) {
            return false;
        }
        String sql = "SELECT 1 FROM producto WHERE codigo_barras = ? AND id_producto <> ? LIMIT 1";
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, barcode.trim());
            statement.setInt(2, exceptProductId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public int saveProduct(InventoryProduct product, int userId) throws SQLException {
        String sql = """
                INSERT INTO producto
                    (descripcion,talle,color,marca,cantidad_inventario,estacion_anio,
                     costo_compra_unitario,precio_venta_contado,id_categoria,codigo_barras,
                     precio_venta_financiado,cuotas,stock_minimo)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    bindProduct(statement, product, 1);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("MySQL no devolvió el identificador del producto creado.");
                        }
                        id = keys.getInt(1);
                    }
                }
                if (product.quantity() > 0) {
                    insertMovement(connection, id, "INGRESO", product.quantity(), "Stock inicial", userId);
                }
                connection.commit();
                return id;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public ImportResult importProducts(List<ProductImportRow> importedRows, int userId) throws SQLException {
        if (importedRows.isEmpty()) {
            throw new IllegalArgumentException("El archivo no contiene filas válidas para importar.");
        }
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Integer> categoryIds = new LinkedHashMap<>();
                try (PreparedStatement categories = connection.prepareStatement(
                        "SELECT id_categoria,nombre FROM categoria");
                        ResultSet result = categories.executeQuery()) {
                    while (result.next()) {
                        categoryIds.put(normalizeProductKey(result.getString(2)), result.getInt(1));
                    }
                }
                Integer fallbackCategory = categoryIds.get(normalizeProductKey("Otros"));
                if (fallbackCategory == null) {
                    throw new SQLException("No existe la categoría predeterminada 'Otros'.");
                }
                List<InventoryProduct> existingProducts = new ArrayList<>();
                try (PreparedStatement products = connection.prepareStatement(
                        PRODUCT_SELECT + " ORDER BY p.id_producto FOR UPDATE");
                        ResultSet result = products.executeQuery()) {
                    while (result.next()) {
                        existingProducts.add(readProduct(result));
                    }
                }

                int added = 0;
                int updated = 0;
                for (ProductImportRow row : importedRows) {
                    int categoryId = categoryIds.getOrDefault(
                            normalizeProductKey(row.category()), fallbackCategory);
                    InventoryProduct existing = findImportMatch(existingProducts, row, categoryId);
                    if (existing != null) {
                        long nextQuantity = (long) existing.quantity() + row.quantity();
                        if (nextQuantity > Integer.MAX_VALUE) {
                            throw new IllegalArgumentException(
                                    "La cantidad acumulada supera el límite en la fila " + row.sourceRow() + ".");
                        }
                        try (PreparedStatement update = connection.prepareStatement(
                                "UPDATE producto SET cantidad_inventario=? WHERE id_producto=?")) {
                            update.setInt(1, (int) nextQuantity);
                            update.setInt(2, existing.id());
                            update.executeUpdate();
                        }
                        if (row.quantity() > 0) {
                            insertMovement(connection, existing.id(), "INGRESO", row.quantity(),
                                    "Importación de archivo · fila " + row.sourceRow(), userId);
                        }
                        existingProducts.remove(existing);
                        existingProducts.add(new InventoryProduct(
                                existing.id(), existing.description(), existing.size(), existing.color(),
                                existing.brand(), (int) nextQuantity, existing.minimumStock(), existing.season(),
                                existing.purchaseCost(), existing.cashPrice(), existing.financedPrice(),
                                existing.installments(), existing.barcode(), existing.categoryId(), existing.category()));
                        updated++;
                    } else {
                        InventoryProduct product = new InventoryProduct(
                                0, row.description(), row.size(), row.color(), row.brand(), row.quantity(),
                                row.minimumStock(), row.season(), row.purchaseCost(), row.cashPrice(),
                                row.financedPrice(), row.installments(), row.barcode(), categoryId,
                                categoryName(categoryIds, row.category()));
                        int productId = insertImportedProduct(connection, product);
                        if (row.quantity() > 0) {
                            insertMovement(connection, productId, "INGRESO", row.quantity(),
                                    "Stock inicial por importación · fila " + row.sourceRow(), userId);
                        }
                        existingProducts.add(new InventoryProduct(
                                productId, product.description(), product.size(), product.color(), product.brand(),
                                product.quantity(), product.minimumStock(), product.season(),
                                product.purchaseCost(), product.cashPrice(), product.financedPrice(),
                                product.installments(), product.barcode(), product.categoryId(), product.category()));
                        added++;
                    }
                }
                connection.commit();
                return new ImportResult(added, updated, importedRows.size());
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private int insertImportedProduct(Connection connection, InventoryProduct product) throws SQLException {
        String sql = """
                INSERT INTO producto
                    (descripcion,talle,color,marca,cantidad_inventario,estacion_anio,
                     costo_compra_unitario,precio_venta_contado,id_categoria,codigo_barras,
                     precio_venta_financiado,cuotas,stock_minimo)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindProduct(statement, product, 1);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("MySQL no devolvió el identificador de un producto importado.");
                }
                return keys.getInt(1);
            }
        }
    }

    private InventoryProduct findImportMatch(
            List<InventoryProduct> products, ProductImportRow row, int categoryId) {
        if (row.barcode() != null && !row.barcode().isBlank()) {
            return products.stream().filter(product -> product.barcode() != null
                    && product.barcode().equalsIgnoreCase(row.barcode().trim())).findFirst().orElse(null);
        }
        String key = String.join("|", normalizeProductKey(row.description()),
                normalizeProductKey(row.size()), normalizeProductKey(row.color()),
                normalizeProductKey(row.brand()), Integer.toString(categoryId));
        return products.stream().filter(product -> String.join("|",
                normalizeProductKey(product.description()), normalizeProductKey(product.size()),
                normalizeProductKey(product.color()), normalizeProductKey(product.brand()),
                Integer.toString(product.categoryId())).equals(key)).findFirst().orElse(null);
    }

    private String normalizeProductKey(String value) {
        return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private PreparedStatement prepareQuery(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(8);
        return statement;
    }

    private String categoryName(Map<String, Integer> categories, String category) {
        String requested = normalizeProductKey(category);
        if (categories.containsKey(requested)) {
            return category;
        }
        return "Otros";
    }

    public record ImportResult(int newProducts, int stockUpdatedProducts, int importedRows) {
    }

    public void updateProduct(InventoryProduct product, int userId) throws SQLException {
        String sql = """
                UPDATE producto SET descripcion=?,talle=?,color=?,marca=?,cantidad_inventario=?,
                    estacion_anio=?,costo_compra_unitario=?,precio_venta_contado=?,id_categoria=?,
                    codigo_barras=?,precio_venta_financiado=?,cuotas=?,stock_minimo=?
                WHERE id_producto=?
                """;
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                int previousQuantity;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT cantidad_inventario FROM producto WHERE id_producto=? FOR UPDATE")) {
                    query.setInt(1, product.id());
                    try (ResultSet result = query.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("El producto ya no existe en la base de datos.");
                        }
                        previousQuantity = result.getInt(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindProduct(statement, product, 1);
                    statement.setInt(14, product.id());
                    statement.executeUpdate();
                }
                int quantityChange = product.quantity() - previousQuantity;
                if (quantityChange != 0) {
                    insertMovement(connection, product.id(), "AJUSTE", quantityChange,
                            "Ajuste al editar el producto", userId);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void deleteProduct(int productId) throws SQLException {
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM producto WHERE id_producto = ?")) {
            statement.setInt(1, productId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("El producto ya no existe en la base de datos.");
            }
        } catch (SQLException exception) {
            if ("23000".equals(exception.getSQLState())) {
                throw new SQLException(
                        "No se puede eliminar un producto que tenga ventas o movimientos asociados. "
                        + "Podés dejar su stock en cero.", exception);
            }
            throw exception;
        }
    }

    public int recordMovement(int productId, String type, int quantityChange, String note, int userId)
            throws SQLException {
        if (quantityChange == 0) {
            throw new IllegalArgumentException("El cambio de stock no puede ser cero.");
        }
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                boolean allowNegative = Boolean.parseBoolean(setting("inventory.allow_negative", "false"));
                String sql = "UPDATE producto SET cantidad_inventario = cantidad_inventario + ? "
                        + "WHERE id_producto = ?" + (allowNegative ? "" : " AND cantidad_inventario + ? >= 0");
                try (PreparedStatement update = connection.prepareStatement(sql)) {
                    update.setInt(1, quantityChange);
                    update.setInt(2, productId);
                    if (!allowNegative) {
                        update.setInt(3, quantityChange);
                    }
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("El producto no existe o el movimiento dejaría stock negativo.");
                    }
                }
                int id = insertMovement(connection, productId, type, quantityChange, note, userId);
                connection.commit();
                return id;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public int createSale(
            List<SaleLine> requestedLines, String customer, String paymentMethod, int userId) throws SQLException {
        if (requestedLines.isEmpty()) {
            throw new IllegalArgumentException("Agregá al menos un producto a la venta.");
        }
        boolean allowNegative = Boolean.parseBoolean(setting("inventory.allow_negative", "false"));
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                BigDecimal total = BigDecimal.ZERO;
                for (SaleLine line : requestedLines) {
                    if (line.quantity() <= 0 || line.unitPrice().signum() < 0) {
                        throw new IllegalArgumentException("La cantidad y el precio deben ser válidos.");
                    }
                    try (PreparedStatement lock = connection.prepareStatement(
                            "SELECT cantidad_inventario FROM producto WHERE id_producto = ? FOR UPDATE")) {
                        lock.setInt(1, line.product().id());
                        try (ResultSet result = lock.executeQuery()) {
                            if (!result.next() || (!allowNegative && result.getInt(1) < line.quantity())) {
                                throw new SQLException("Stock insuficiente para " + line.product().description() + ".");
                            }
                        }
                    }
                    total = total.add(line.subtotal());
                }
                int saleId;
                try (PreparedStatement sale = connection.prepareStatement(
                        "INSERT INTO venta (fecha,monto_total,cliente,medio_pago,estado,id_usuario) "
                        + "VALUES (CURRENT_DATE,?,?,?,'Completada',?)", Statement.RETURN_GENERATED_KEYS)) {
                    sale.setBigDecimal(1, total);
                    sale.setString(2, customer.isBlank() ? "Venta mostrador" : customer.trim());
                    sale.setString(3, paymentMethod);
                    sale.setInt(4, userId);
                    sale.executeUpdate();
                    try (ResultSet keys = sale.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("MySQL no devolvió el identificador de la venta.");
                        }
                        saleId = keys.getInt(1);
                    }
                }
                for (SaleLine line : requestedLines) {
                    try (PreparedStatement detail = connection.prepareStatement(
                            "INSERT INTO detalle_venta (id_venta,id_producto,precio_unitario,cantidad) "
                            + "VALUES (?,?,?,?)");
                            PreparedStatement update = connection.prepareStatement(
                                    "UPDATE producto SET cantidad_inventario = cantidad_inventario - ? "
                                    + "WHERE id_producto = ?" + (allowNegative ? "" : " AND cantidad_inventario >= ?"));
                            ) {
                        detail.setInt(1, saleId);
                        detail.setInt(2, line.product().id());
                        detail.setBigDecimal(3, line.unitPrice());
                        detail.setInt(4, line.quantity());
                        detail.executeUpdate();
                        update.setInt(1, line.quantity());
                        update.setInt(2, line.product().id());
                        if (!allowNegative) {
                            update.setInt(3, line.quantity());
                        }
                        if (update.executeUpdate() != 1) {
                            throw new SQLException("El stock cambió mientras se registraba la venta.");
                        }
                    }
                    insertMovement(connection, line.product().id(), "VENTA", -line.quantity(),
                            "Venta #" + saleId, userId);
                }
                connection.commit();
                return saleId;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void cancelSale(int saleId, int userId) throws SQLException {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement sale = connection.prepareStatement(
                        "SELECT estado FROM venta WHERE id_venta = ? FOR UPDATE")) {
                    sale.setInt(1, saleId);
                    try (ResultSet result = sale.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("La venta seleccionada ya no existe.");
                        }
                        if ("Cancelada".equalsIgnoreCase(result.getString(1))) {
                            throw new SQLException("La venta ya está cancelada.");
                        }
                    }
                }
                try (PreparedStatement lines = connection.prepareStatement(
                        "SELECT id_producto,cantidad FROM detalle_venta WHERE id_venta = ?")) {
                    lines.setInt(1, saleId);
                    try (ResultSet result = lines.executeQuery()) {
                        while (result.next()) {
                            int productId = result.getInt(1);
                            int quantity = result.getInt(2);
                            try (PreparedStatement update = connection.prepareStatement(
                                    "UPDATE producto SET cantidad_inventario = cantidad_inventario + ? "
                                    + "WHERE id_producto = ?")) {
                                update.setInt(1, quantity);
                                update.setInt(2, productId);
                                update.executeUpdate();
                            }
                            insertMovement(connection, productId, "AJUSTE", quantity,
                                    "Reintegro por cancelación de venta #" + saleId, userId);
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE venta SET estado = 'Cancelada' WHERE id_venta = ?")) {
                    update.setInt(1, saleId);
                    update.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public List<SaleRecord> listSales() throws SQLException {
        return readSales(null);
    }

    public List<SaleRecord> listRecentSales(int limit) throws SQLException {
        if (limit < 1) {
            throw new IllegalArgumentException("El límite de ventas debe ser mayor que cero.");
        }
        return readSales(limit);
    }

    private List<SaleRecord> readSales(Integer limit) throws SQLException {
        List<SaleRecord> sales = new ArrayList<>();
        String sql = """
                SELECT v.id_venta,v.fecha,v.cliente,v.medio_pago,v.estado,v.monto_total,
                       COALESCE(u.nombre_completo,'Usuario eliminado') AS vendedor
                FROM venta v LEFT JOIN usuario u ON u.id_usuario=v.id_usuario
                ORDER BY v.fecha DESC,v.id_venta DESC
                """;
        if (limit != null) {
            sql += " LIMIT ?";
        }
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(15);
            if (limit != null) {
                statement.setInt(1, limit);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    sales.add(new SaleRecord(
                            result.getInt("id_venta"),
                            result.getDate("fecha").toLocalDate(),
                            result.getString("cliente"),
                            result.getString("medio_pago"),
                            result.getString("estado"),
                            result.getBigDecimal("monto_total"),
                            result.getString("vendedor")));
                }
            }
        }
        return sales;
    }

    public List<InventoryProduct> listLowStockProducts(int limit) throws SQLException {
        if (limit < 1) {
            throw new IllegalArgumentException("El límite de productos debe ser mayor que cero.");
        }
        List<InventoryProduct> products = new ArrayList<>();
        String sql = PRODUCT_SELECT + " WHERE p.cantidad_inventario <= p.stock_minimo "
                + "ORDER BY p.cantidad_inventario,p.descripcion LIMIT ?";
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(15);
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    products.add(readProduct(result));
                }
            }
        }
        return products;
    }

    public List<MovementRecord> listMovements() throws SQLException {
        List<MovementRecord> movements = new ArrayList<>();
        String sql = """
                SELECT m.id_movimiento,m.tipo,p.descripcion,m.cantidad,m.fecha_hora,m.nota,
                       COALESCE(u.nombre_completo,'Usuario eliminado') AS usuario
                FROM movimiento_stock m
                JOIN producto p ON p.id_producto=m.id_producto
                LEFT JOIN usuario u ON u.id_usuario=m.id_usuario
                ORDER BY m.fecha_hora DESC,m.id_movimiento DESC
                """;
        try (Connection connection = database.open();
                PreparedStatement statement = prepareQuery(connection, sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                movements.add(new MovementRecord(
                        result.getLong("id_movimiento"),
                        result.getString("tipo"),
                        result.getString("descripcion"),
                        result.getInt("cantidad"),
                        result.getTimestamp("fecha_hora").toLocalDateTime(),
                        result.getString("nota"),
                        result.getString("usuario")));
            }
        }
        return movements;
    }

    public Map<String, String> loadSettings() throws SQLException {
        Map<String, String> settings = new LinkedHashMap<>();
        try (Connection connection = database.open();
                PreparedStatement statement = prepareQuery(
                        connection, "SELECT clave,valor FROM configuracion");
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                settings.put(result.getString(1), result.getString(2));
            }
        }
        return settings;
    }

    public void saveSettings(Map<String, String> settings) throws SQLException {
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO configuracion (clave,valor) VALUES (?,?) "
                        + "ON DUPLICATE KEY UPDATE valor=VALUES(valor)")) {
            connection.setAutoCommit(false);
            try {
                for (Map.Entry<String, String> setting : settings.entrySet()) {
                    statement.setString(1, setting.getKey());
                    statement.setString(2, setting.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void exportSqlBackup(Path destination) throws SQLException, IOException {
        try (Connection connection = database.open();
                BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8);
                Statement tables = connection.createStatement();
                ResultSet tableNames = tables.executeQuery("SHOW TABLES")) {
            writer.write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n\n");
            while (tableNames.next()) {
                String table = tableNames.getString(1);
                try (Statement definition = connection.createStatement();
                        ResultSet create = definition.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
                    if (create.next()) {
                        writer.write("DROP TABLE IF EXISTS `" + table + "`;\n");
                        writer.write(create.getString(2) + ";\n\n");
                    }
                }
                try (Statement select = connection.createStatement();
                        ResultSet rows = select.executeQuery("SELECT * FROM `" + table + "`")) {
                    ResultSetMetaData metadata = rows.getMetaData();
                    int count = metadata.getColumnCount();
                    while (rows.next()) {
                        writer.write("INSERT INTO `" + table + "` (");
                        for (int column = 1; column <= count; column++) {
                            if (column > 1) {
                                writer.write(",");
                            }
                            writer.write("`" + metadata.getColumnName(column).replace("`", "``") + "`");
                        }
                        writer.write(") VALUES (");
                        for (int column = 1; column <= count; column++) {
                            if (column > 1) {
                                writer.write(",");
                            }
                            Object value = rows.getObject(column);
                            writer.write(sqlLiteral(value, metadata.getColumnType(column)));
                        }
                        writer.write(");\n");
                    }
                }
                writer.write("\n");
            }
            writer.write("SET FOREIGN_KEY_CHECKS=1;\n");
        }
    }

    private String sqlLiteral(Object value, int sqlType) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || sqlType == Types.BOOLEAN || sqlType == Types.BIT) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            return "X'" + java.util.HexFormat.of().formatHex(bytes) + "'";
        }
        String text = value.toString().replace("\\", "\\\\").replace("'", "''")
                .replace("\r", "\\r").replace("\n", "\\n");
        return "'" + text + "'";
    }

    public DashboardData loadDashboard() throws SQLException {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimal todayRevenue = BigDecimal.ZERO;
        BigDecimal monthRevenue = BigDecimal.ZERO;
        BigDecimal monthAverage = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        long todaySales = 0;
        long monthSales = 0;
        long todayUnits = 0;
        long lowStock = 0;
        long inStock = 0;
        long financedSales = 0;
        BigDecimal inventoryValue = BigDecimal.ZERO;
        List<DashboardData.DailyRevenue> dailyRevenue;
        List<DashboardData.CategoryUnits> categoryUnits;
        List<DashboardData.PaymentTotal> paymentTotals;
        try (Connection connection = database.open()) {
            try (PreparedStatement statement = prepareQuery(connection,
                    "SELECT COALESCE(SUM(monto_total),0),COUNT(*) FROM venta "
                    + "WHERE fecha=? AND estado <> 'Cancelada'")) {
                statement.setDate(1, Date.valueOf(today));
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    todayRevenue = result.getBigDecimal(1);
                    todaySales = result.getLong(2);
                }
            }
            try (PreparedStatement statement = prepareQuery(connection,
                    "SELECT COALESCE(SUM(monto_total),0),COUNT(*),COALESCE(AVG(monto_total),0) "
                    + "FROM venta WHERE fecha>=? AND estado <> 'Cancelada'")) {
                statement.setDate(1, Date.valueOf(monthStart));
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    monthRevenue = result.getBigDecimal(1);
                    monthSales = result.getLong(2);
                    monthAverage = result.getBigDecimal(3);
                }
            }
            try (PreparedStatement statement = prepareQuery(connection,
                    "SELECT COALESCE(SUM(d.cantidad),0) FROM detalle_venta d "
                    + "JOIN venta v ON v.id_venta=d.id_venta WHERE v.fecha=? AND v.estado <> 'Cancelada'")) {
                statement.setDate(1, Date.valueOf(today));
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    todayUnits = result.getLong(1);
                }
            }
            try (PreparedStatement statement = prepareQuery(connection,
                    "SELECT COALESCE(SUM(cantidad_inventario <= stock_minimo),0),"
                    + "COALESCE(SUM(cantidad_inventario > 0),0),"
                    + "COALESCE(SUM(cantidad_inventario*costo_compra_unitario),0) FROM producto")) {
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    lowStock = result.getLong(1);
                    inStock = result.getLong(2);
                    inventoryValue = result.getBigDecimal(3);
                }
            }
            try (PreparedStatement statement = prepareQuery(connection,
                    "SELECT COALESCE(SUM((d.precio_unitario-p.costo_compra_unitario)*d.cantidad),0) "
                    + "FROM detalle_venta d JOIN venta v ON v.id_venta=d.id_venta "
                    + "JOIN producto p ON p.id_producto=d.id_producto "
                    + "WHERE v.fecha>=? AND v.estado <> 'Cancelada'")) {
                statement.setDate(1, Date.valueOf(monthStart));
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    profit = result.getBigDecimal(1);
                }
            }
            try (PreparedStatement statement = prepareQuery(connection,
                    "SELECT COUNT(*) FROM venta WHERE medio_pago LIKE 'Financiado%' "
                    + "AND estado <> 'Cancelada'")) {
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    financedSales = result.getLong(1);
                }
            }
            dailyRevenue = loadDailyRevenue(connection, today.minusDays(11), today);
            categoryUnits = loadCategoryUnits(connection, monthStart);
            paymentTotals = loadPaymentTotals(connection, monthStart);
        }
        return new DashboardData(todayRevenue, todaySales, todayUnits, lowStock, monthRevenue,
                monthSales, monthAverage, profit, financedSales, inStock, inventoryValue,
                dailyRevenue, categoryUnits, paymentTotals);
    }

    private List<DashboardData.DailyRevenue> loadDailyRevenue(
            Connection connection, LocalDate start, LocalDate end) throws SQLException {
        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        try (PreparedStatement statement = prepareQuery(connection,
                "SELECT fecha,SUM(monto_total) AS total FROM venta "
                + "WHERE fecha BETWEEN ? AND ? AND estado <> 'Cancelada' GROUP BY fecha")) {
            statement.setDate(1, Date.valueOf(start));
            statement.setDate(2, Date.valueOf(end));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    totals.put(result.getDate("fecha").toLocalDate(), result.getBigDecimal("total"));
                }
            }
        }
        List<DashboardData.DailyRevenue> daily = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            daily.add(new DashboardData.DailyRevenue(date, totals.getOrDefault(date, BigDecimal.ZERO)));
        }
        return daily;
    }

    private List<DashboardData.CategoryUnits> loadCategoryUnits(Connection connection, LocalDate start)
            throws SQLException {
        List<DashboardData.CategoryUnits> units = new ArrayList<>();
        String sql = """
                SELECT c.nombre,SUM(d.cantidad) AS unidades
                FROM detalle_venta d JOIN venta v ON v.id_venta=d.id_venta
                JOIN producto p ON p.id_producto=d.id_producto
                JOIN categoria c ON c.id_categoria=p.id_categoria
                WHERE v.fecha>=? AND v.estado <> 'Cancelada'
                GROUP BY c.id_categoria,c.nombre ORDER BY unidades DESC
                """;
        try (PreparedStatement statement = prepareQuery(connection, sql)) {
            statement.setDate(1, Date.valueOf(start));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    units.add(new DashboardData.CategoryUnits(
                            result.getString(1), result.getLong(2)));
                }
            }
        }
        return units;
    }

    private List<DashboardData.PaymentTotal> loadPaymentTotals(Connection connection, LocalDate start)
            throws SQLException {
        List<DashboardData.PaymentTotal> totals = new ArrayList<>();
        try (PreparedStatement statement = prepareQuery(connection,
                "SELECT medio_pago,SUM(monto_total) FROM venta "
                + "WHERE fecha>=? AND estado <> 'Cancelada' GROUP BY medio_pago ORDER BY SUM(monto_total) DESC")) {
            statement.setDate(1, Date.valueOf(start));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    totals.add(new DashboardData.PaymentTotal(result.getString(1), result.getBigDecimal(2)));
                }
            }
        }
        return totals;
    }

    private int insertMovement(
            Connection connection, int productId, String type, int quantity, String note, int userId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO movimiento_stock (id_producto,tipo,cantidad,nota,id_usuario) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, productId);
            statement.setString(2, type);
            statement.setInt(3, quantity);
            statement.setString(4, note == null ? "" : note);
            statement.setInt(5, userId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("MySQL no devolvió el identificador del movimiento.");
                }
                return keys.getInt(1);
            }
        }
    }

    private void bindProduct(PreparedStatement statement, InventoryProduct product, int start)
            throws SQLException {
        statement.setString(start, product.description());
        statement.setString(start + 1, product.size());
        statement.setString(start + 2, product.color());
        statement.setString(start + 3, product.brand());
        statement.setInt(start + 4, product.quantity());
        statement.setString(start + 5, product.season());
        statement.setBigDecimal(start + 6, product.purchaseCost());
        if (product.cashPrice() == null) {
            statement.setNull(start + 7, java.sql.Types.DECIMAL);
        } else {
            statement.setBigDecimal(start + 7, product.cashPrice());
        }
        statement.setInt(start + 8, product.categoryId());
        if (product.barcode() == null || product.barcode().isBlank()) {
            statement.setNull(start + 9, java.sql.Types.VARCHAR);
        } else {
            statement.setString(start + 9, product.barcode().trim());
        }
        if (product.financedPrice() == null) {
            statement.setNull(start + 10, java.sql.Types.DECIMAL);
        } else {
            statement.setBigDecimal(start + 10, product.financedPrice());
        }
        statement.setInt(start + 11, product.installments());
        statement.setInt(start + 12, product.minimumStock());
    }

    private InventoryProduct readProduct(ResultSet result) throws SQLException {
        BigDecimal cashPrice = result.getBigDecimal("precio_venta_contado");
        BigDecimal financedPrice = result.getBigDecimal("precio_venta_financiado");
        return new InventoryProduct(
                result.getInt("id_producto"),
                result.getString("descripcion"),
                result.getString("talle"),
                result.getString("color"),
                result.getString("marca"),
                result.getInt("cantidad_inventario"),
                result.getInt("stock_minimo"),
                result.getString("estacion_anio"),
                result.getBigDecimal("costo_compra_unitario"),
                cashPrice,
                financedPrice,
                result.getInt("cuotas"),
                result.getString("codigo_barras"),
                result.getInt("id_categoria"),
                result.getString("nombre"));
    }
}
