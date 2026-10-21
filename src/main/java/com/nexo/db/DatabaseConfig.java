package com.nexo.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public record DatabaseConfig(String host, int port, String database, String username, String password) {
    private static final String EMPTY_PASSWORD = "DPAPI_EMPTY";

    public DatabaseConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("El servidor MySQL es obligatorio.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("El puerto MySQL debe estar entre 1 y 65535.");
        }
        if (database == null || !database.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("El nombre de la base solo puede contener letras, números y guion bajo.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("El usuario MySQL es obligatorio.");
        }
        password = password == null ? "" : password;
    }

    public String jdbcUrl(boolean includeDatabase) {
        String base = "jdbc:mysql://" + host + ":" + port + "/";
        return base + (includeDatabase ? database : "")
                + "?useUnicode=true&characterEncoding=utf8"
                + "&connectionTimeZone=America%2FArgentina%2FBuenos_Aires"
                + "&connectTimeout=3000&socketTimeout=10000"
                + "&enableQueryTimeouts=true";
    }

    public static Path configPath() {
        String appData = System.getenv("APPDATA");
        Path parent = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".nexo")
                : Path.of(appData, "Nexo");
        return parent.resolve("database.properties");
    }

    public static DatabaseConfig load() throws IOException {
        Path path = configPath();
        if (!Files.exists(path)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        String encodedPassword = properties.getProperty("password", EMPTY_PASSWORD);
        String password = EMPTY_PASSWORD.equals(encodedPassword) ? "" : unprotect(encodedPassword);
        try {
            return new DatabaseConfig(
                    properties.getProperty("host", "localhost"),
                    Integer.parseInt(properties.getProperty("port", "3306")),
                    properties.getProperty("database", "tienda_de_ropa"),
                    properties.getProperty("username", "root"),
                    password);
        } catch (IllegalArgumentException exception) {
            throw new IOException("La configuración local de MySQL no es válida.", exception);
        }
    }

    public void save() throws IOException {
        Path path = configPath();
        Files.createDirectories(path.getParent());
        Properties properties = new Properties();
        properties.setProperty("host", host);
        properties.setProperty("port", Integer.toString(port));
        properties.setProperty("database", database);
        properties.setProperty("username", username);
        properties.setProperty("password", password.isEmpty() ? EMPTY_PASSWORD : protect(password));
        Path temporary = Files.createTempFile(path.getParent(), "database-", ".properties");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Nexo local MySQL connection (password protected with Windows DPAPI)");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String protect(String value) throws IOException {
        return runPowerShell(
                "$value = [Console]::In.ReadLine(); "
                + "$secure = ConvertTo-SecureString -String $value -AsPlainText -Force; "
                + "ConvertFrom-SecureString -SecureString $secure",
                value);
    }

    private static String unprotect(String value) throws IOException {
        return runPowerShell(
                "$secure = ConvertTo-SecureString -String ([Console]::In.ReadLine()); "
                + "$credential = [System.Management.Automation.PSCredential]::new('Nexo', $secure); "
                + "$credential.GetNetworkCredential().Password",
                value);
    }

    private static String runPowerShell(String script, String input) throws IOException {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            throw new IOException("La protección local de credenciales requiere Windows.");
        }
        Process process = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true)
                .start();
        try (OutputStream standardInput = process.getOutputStream()) {
            standardInput.write(input.getBytes(StandardCharsets.UTF_8));
            standardInput.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isEmpty()) {
                throw new IOException("Windows no pudo proteger o recuperar la credencial MySQL.");
            }
            return output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Se interrumpió la protección de la credencial MySQL.", exception);
        }
    }
}
