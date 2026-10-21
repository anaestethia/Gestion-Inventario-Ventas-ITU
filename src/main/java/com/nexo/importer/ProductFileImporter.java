package com.nexo.importer;

import com.nexo.model.ProductImportRow;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public final class ProductFileImporter {
    private static final Map<String, List<String>> HEADER_ALIASES = Map.ofEntries(
            Map.entry("description", List.of(
                    "producto", "descripcion", "nombre", "product", "item",
                    "nombre producto", "nombre del producto", "descripcion producto", "descripcion del producto")),
            Map.entry("barcode", List.of(
                    "codigo", "codigo barras", "codigo de barras", "codigo producto", "barcode", "ean", "sku")),
            Map.entry("category", List.of("categoria", "category", "rubro")),
            Map.entry("brand", List.of("marca", "brand")),
            Map.entry("size", List.of("talle", "talla", "size")),
            Map.entry("color", List.of("color", "colour")),
            Map.entry("quantity", List.of(
                    "stock", "cantidad", "unidades", "quantity", "qty", "existencias", "stock disponible",
                    "cantidad en stock", "cantidad disponible")),
            Map.entry("minimumStock", List.of("stock minimo", "minimo", "minimum stock")),
            Map.entry("season", List.of("temporada", "estacion", "season")),
            Map.entry("purchaseCost", List.of(
                    "costo", "costo compra", "costo unitario", "costo de compra", "purchase cost", "cost")),
            Map.entry("cashPrice", List.of(
                    "precio", "precio contado", "precio venta", "precio de venta", "cash price", "price")),
            Map.entry("financedPrice", List.of("precio financiado", "financed price")),
            Map.entry("installments", List.of("cuotas", "installments")));

    private ProductFileImporter() {
    }

    public static Preview parse(Path file) throws IOException {
        List<List<String>> rows = file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")
                ? parseCsv(file) : parseWorkbook(file);
        if (rows.isEmpty()) {
            throw new IOException("El archivo no contiene filas para importar.");
        }
        int headerIndex = findHeaderRow(rows);
        if (headerIndex < 0) {
            throw new IOException("No se encontró una fila de encabezados con una columna de producto.");
        }
        Map<String, Integer> columns = mapHeaders(rows.get(headerIndex));
        if (!columns.containsKey("description")) {
            throw new IOException("Falta una columna de producto o descripción.");
        }
        if (!columns.containsKey("quantity")) {
            throw new IOException("Falta una columna de stock o cantidad.");
        }

        List<ProductImportRow> products = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int index = headerIndex + 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            int line = index + 1;
            try {
                String description = value(row, columns, "description").trim();
                if (description.isBlank()) {
                    throw new IllegalArgumentException("falta la descripción");
                }
                int quantity = parseInteger(value(row, columns, "quantity"), "stock", 0);
                int minimum = parseInteger(value(row, columns, "minimumStock"), "stock mínimo", 5);
                int installments = parseInteger(value(row, columns, "installments"), "cuotas", 0);
                if (quantity < 0 || minimum < 0 || installments < 0 || installments > 120) {
                    throw new IllegalArgumentException("el stock mínimo o las cuotas no son válidos");
                }
                BigDecimal cost = parseMoney(value(row, columns, "purchaseCost"), "costo", BigDecimal.ZERO);
                BigDecimal cash = parseMoney(value(row, columns, "cashPrice"), "precio contado", null);
                BigDecimal financed = parseMoney(value(row, columns, "financedPrice"), "precio financiado", null);
                String season = normalizeSeason(value(row, columns, "season"));
                products.add(new ProductImportRow(
                        line,
                        description,
                        value(row, columns, "size").trim(),
                        value(row, columns, "color").trim(),
                        value(row, columns, "brand").trim(),
                        quantity,
                        minimum,
                        season,
                        cost,
                        cash,
                        financed,
                        installments,
                        value(row, columns, "barcode").trim(),
                        defaultValue(value(row, columns, "category").trim(), "Otros")));
            } catch (IllegalArgumentException exception) {
                errors.add("Fila " + line + ": " + exception.getMessage());
            }
        }
        return new Preview(List.copyOf(products), List.copyOf(errors));
    }

    private static List<List<String>> parseWorkbook(Path file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("es-AR"));
            List<List<String>> rows = new ArrayList<>();
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                int lastCell = Math.max(0, row.getLastCellNum());
                for (int column = 0; column < lastCell; column++) {
                    values.add(formatter.formatCellValue(row.getCell(column)));
                }
                rows.add(values);
            }
            return rows;
        }
    }

    private static List<List<String>> parseCsv(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
            text = text.substring(1);
        }
        char delimiter = detectDelimiter(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && character == delimiter) {
                row.add(field.toString());
                field.setLength(0);
            } else if (!quoted && (character == '\n' || character == '\r')) {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw new IOException("El CSV contiene una comilla sin cerrar.");
        }
        if (!row.isEmpty() || field.length() > 0) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static char detectDelimiter(String text) {
        int[] counts = new int[3];
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && (character == '\n' || character == '\r')) {
                break;
            } else if (!quoted) {
                if (character == ',') {
                    counts[0]++;
                } else if (character == ';') {
                    counts[1]++;
                } else if (character == '\t') {
                    counts[2]++;
                }
            }
        }
        return counts[1] > counts[0] && counts[1] >= counts[2] ? ';'
                : counts[2] > counts[0] ? '\t' : ',';
    }

    private static int findHeaderRow(List<List<String>> rows) {
        for (int index = 0; index < Math.min(rows.size(), 15); index++) {
            if (mapHeaders(rows.get(index)).containsKey("description")) {
                return index;
            }
        }
        return -1;
    }

    private static Map<String, Integer> mapHeaders(List<String> headers) {
        Map<String, Integer> result = new HashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String header = normalize(headers.get(index));
            for (Map.Entry<String, List<String>> alias : HEADER_ALIASES.entrySet()) {
                if (alias.getValue().stream().map(ProductFileImporter::normalize).anyMatch(header::equals)) {
                    result.putIfAbsent(alias.getKey(), index);
                }
            }
        }
        return result;
    }

    private static String value(List<String> row, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        return index == null || index >= row.size() ? "" : row.get(index);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT)
                .replaceAll("[_\\-]+", " ").replaceAll("\\s+", " ");
    }

    private static int parseInteger(String value, String field, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ".")).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(field + " debe ser un número entero");
        }
    }

    private static BigDecimal parseMoney(String value, String field, BigDecimal defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String amount = value.trim().replaceAll("[^0-9,.-]", "");
        if (amount.contains(",") && amount.contains(".")) {
            if (amount.lastIndexOf(',') > amount.lastIndexOf('.')) {
                amount = amount.replace(".", "").replace(',', '.');
            } else {
                amount = amount.replace(",", "");
            }
        } else if (amount.contains(",")) {
            amount = amount.replace(',', '.');
        }
        try {
            BigDecimal parsed = new BigDecimal(amount);
            if (parsed.signum() < 0) {
                throw new IllegalArgumentException(field + " no puede ser negativo");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " debe ser un importe válido");
        }
    }

    private static String normalizeSeason(String season) {
        String normalized = normalize(season);
        return switch (normalized) {
            case "verano" -> "VERANO";
            case "otono" -> "OTONO";
            case "invierno" -> "INVIERNO";
            case "primavera" -> "PRIMAVERA";
            default -> "NO_ESPECIFICADO";
        };
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Preview(List<ProductImportRow> products, List<String> errors) {
    }
}
