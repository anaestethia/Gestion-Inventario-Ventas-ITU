package com.nexo.db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Database {
    private static final ExecutorService NETWORK_TIMEOUT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "nexo-mysql-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private static final List<String> CATEGORIES = List.of(
            "Remeras", "Pantalones", "Buzos", "Abrigos", "Calzado", "Accesorios", "Otros");
    private static final Map<String, String> DEFAULT_SETTINGS = Map.ofEntries(
            Map.entry("store.name", "Nexo Indumentaria"),
            Map.entry("store.cuit", ""),
            Map.entry("store.address", ""),
            Map.entry("store.currency", "ARS"),
            Map.entry("inventory.low_stock_threshold", "5"),
            Map.entry("inventory.allow_negative", "false"),
            Map.entry("sales.max_installments", "12"),
            Map.entry("sales.default_surcharge_percent", "0"),
            Map.entry("appearance.theme", "Claro"),
            Map.entry("appearance.scale", "100 %"),
            Map.entry("security.auto_lock", "true"),
            Map.entry("receipts.paper_size", "80 mm"),
            Map.entry("receipts.footer", "¡Gracias por tu compra!"),
            Map.entry("backup.retention_days", "30"));

    private final DatabaseConfig config;

    public Database(DatabaseConfig config) {
        this.config = config;
    }

    public String databaseName() {
        return config.database();
    }

    public Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(
                config.jdbcUrl(true), config.username(), config.password());
        try {
            connection.setNetworkTimeout(NETWORK_TIMEOUT_EXECUTOR, 10000);
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    public void initialize() throws SQLException {
        try (Connection server = DriverManager.getConnection(
                config.jdbcUrl(false), config.username(), config.password());
                Statement statement = server.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `" + config.database()
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                createBaseTables(connection);
                migrateExistingTables(connection);
                seedCategories(connection);
                seedUsers(connection);
                seedSettings(connection);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void createBaseTables(Connection connection) throws SQLException {
        List<String> statements = List.of(
                """
                CREATE TABLE IF NOT EXISTS categoria (
                    id_categoria INT UNSIGNED NOT NULL AUTO_INCREMENT,
                    nombre VARCHAR(50) NOT NULL UNIQUE,
                    PRIMARY KEY (id_categoria)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS producto (
                    id_producto INT UNSIGNED NOT NULL AUTO_INCREMENT,
                    descripcion VARCHAR(50) NOT NULL,
                    talle VARCHAR(30) NOT NULL,
                    color VARCHAR(50) NOT NULL,
                    marca VARCHAR(50) NOT NULL,
                    cantidad_inventario INT UNSIGNED NOT NULL,
                    estacion_anio ENUM('VERANO','OTONO','INVIERNO','PRIMAVERA','NO_ESPECIFICADO')
                        NOT NULL DEFAULT 'NO_ESPECIFICADO',
                    costo_compra_unitario DECIMAL(10,2) NOT NULL CHECK (costo_compra_unitario >= 0),
                    precio_venta_contado DECIMAL(10,2) NULL CHECK (precio_venta_contado >= 0),
                    id_categoria INT UNSIGNED NOT NULL,
                    codigo_barras VARCHAR(100) NULL,
                    precio_venta_financiado DECIMAL(10,2) NULL,
                    cuotas INT UNSIGNED NOT NULL DEFAULT 0,
                    stock_minimo INT UNSIGNED NOT NULL DEFAULT 5,
                    PRIMARY KEY (id_producto),
                    CONSTRAINT fk_categoria_producto FOREIGN KEY (id_categoria)
                        REFERENCES categoria(id_categoria) ON DELETE RESTRICT ON UPDATE RESTRICT,
                    INDEX idx_producto_marca_talle_color (marca,talle,color),
                    UNIQUE INDEX uq_producto_codigo_barras (codigo_barras)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS usuario (
                    id_usuario INT UNSIGNED NOT NULL AUTO_INCREMENT,
                    nombre_usuario VARCHAR(80) NOT NULL UNIQUE,
                    nombre_completo VARCHAR(120) NOT NULL,
                    rol ENUM('DUEÑA','EMPLEADA') NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    activo BOOLEAN NOT NULL DEFAULT TRUE,
                    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id_usuario)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS venta (
                    id_venta INT UNSIGNED NOT NULL AUTO_INCREMENT,
                    fecha DATE NOT NULL,
                    monto_total DECIMAL(10,2) NOT NULL CHECK (monto_total >= 0),
                    cliente VARCHAR(120) NOT NULL DEFAULT 'Venta mostrador',
                    medio_pago VARCHAR(50) NOT NULL DEFAULT 'Efectivo',
                    estado VARCHAR(30) NOT NULL DEFAULT 'Completada',
                    id_usuario INT UNSIGNED NULL,
                    PRIMARY KEY (id_venta),
                    INDEX idx_venta_fecha (fecha DESC),
                    CONSTRAINT fk_venta_usuario FOREIGN KEY (id_usuario)
                        REFERENCES usuario(id_usuario) ON DELETE SET NULL ON UPDATE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS detalle_venta (
                    id_venta INT UNSIGNED NOT NULL,
                    id_producto INT UNSIGNED NOT NULL,
                    precio_unitario DECIMAL(10,2) NOT NULL CHECK (precio_unitario >= 0),
                    cantidad INT NOT NULL CHECK (cantidad > 0),
                    PRIMARY KEY (id_venta,id_producto),
                    CONSTRAINT fk_venta_detalle_venta FOREIGN KEY (id_venta)
                        REFERENCES venta(id_venta) ON DELETE RESTRICT ON UPDATE RESTRICT,
                    CONSTRAINT fk_producto_detalle_venta FOREIGN KEY (id_producto)
                        REFERENCES producto(id_producto) ON DELETE RESTRICT ON UPDATE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS movimiento_stock (
                    id_movimiento BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    id_producto INT UNSIGNED NOT NULL,
                    tipo ENUM('INGRESO','VENTA','AJUSTE') NOT NULL,
                    cantidad INT NOT NULL,
                    fecha_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    nota VARCHAR(500) NOT NULL DEFAULT '',
                    id_usuario INT UNSIGNED NULL,
                    PRIMARY KEY (id_movimiento),
                    INDEX idx_movimiento_fecha (fecha_hora DESC),
                    CONSTRAINT fk_movimiento_producto FOREIGN KEY (id_producto)
                        REFERENCES producto(id_producto) ON DELETE RESTRICT ON UPDATE RESTRICT,
                    CONSTRAINT fk_movimiento_usuario FOREIGN KEY (id_usuario)
                        REFERENCES usuario(id_usuario) ON DELETE SET NULL ON UPDATE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS configuracion (
                    clave VARCHAR(100) NOT NULL,
                    valor TEXT NOT NULL,
                    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (clave)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private void migrateExistingTables(Connection connection) throws SQLException {
        ensureColumn(connection, "producto", "codigo_barras", "VARCHAR(100) NULL");
        ensureColumn(connection, "producto", "precio_venta_financiado", "DECIMAL(10,2) NULL");
        ensureColumn(connection, "producto", "cuotas", "INT UNSIGNED NOT NULL DEFAULT 0");
        ensureColumn(connection, "producto", "stock_minimo", "INT UNSIGNED NOT NULL DEFAULT 5");
        ensureIndex(connection, "producto", "uq_producto_codigo_barras", "UNIQUE");
        ensureColumn(connection, "venta", "cliente", "VARCHAR(120) NOT NULL DEFAULT 'Venta mostrador'");
        ensureColumn(connection, "venta", "medio_pago", "VARCHAR(50) NOT NULL DEFAULT 'Efectivo'");
        ensureColumn(connection, "venta", "estado", "VARCHAR(30) NOT NULL DEFAULT 'Completada'");
        ensureColumn(connection, "venta", "id_usuario", "INT UNSIGNED NULL");
    }

    private void ensureIndex(Connection connection, String table, String index, String definition)
            throws SQLException {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(config.database(), null, table, false, false)) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE " + definition + " INDEX `" + index + "` ON `" + table + "` (`codigo_barras`)");
        }
    }

    private void ensureColumn(Connection connection, String table, String column, String definition)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(config.database(), null, table, column)) {
            if (columns.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
        }
    }

    private void seedCategories(Connection connection) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT IGNORE INTO categoria (nombre) VALUES (?)")) {
            for (String category : CATEGORIES) {
                insert.setString(1, category);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void seedUsers(Connection connection) throws SQLException {
        seedUser(connection, "lourdes", "Lourdes Torassi", "DUEÑA");
        seedUser(connection, "lola", "Lola", "EMPLEADA");
    }

    private void seedUser(Connection connection, String username, String name, String role) throws SQLException {
        try (PreparedStatement exists = connection.prepareStatement(
                "SELECT id_usuario FROM usuario WHERE nombre_usuario = ?")) {
            exists.setString(1, username);
            try (ResultSet result = exists.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
        }
        String hash = PasswordHasher.hash("1234".toCharArray());
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO usuario (nombre_usuario, nombre_completo, rol, password_hash) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, username);
            insert.setString(2, name);
            insert.setString(3, role);
            insert.setString(4, hash);
            insert.executeUpdate();
        }
    }

    private void seedSettings(Connection connection) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT IGNORE INTO configuracion (clave, valor) VALUES (?, ?)")) {
            for (Map.Entry<String, String> setting : DEFAULT_SETTINGS.entrySet()) {
                insert.setString(1, setting.getKey());
                insert.setString(2, setting.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
