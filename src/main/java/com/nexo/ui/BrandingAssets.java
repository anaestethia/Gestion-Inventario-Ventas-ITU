package com.nexo.ui;

import java.awt.Image;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

final class BrandingAssets {
    private BrandingAssets() {
    }

    static Path directory() throws IOException {
        String appData = System.getenv("APPDATA");
        Path directory = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".nexo")
                : Path.of(appData, "Nexo");
        Files.createDirectories(directory);
        return directory;
    }

    static Path copyLogo(Path source) throws IOException {
        String extension = extension(source);
        Path destination = directory().resolve("company-logo" + extension);
        try (var image = Files.newInputStream(source)) {
            if (ImageIO.read(image) == null) {
                throw new IOException("El archivo seleccionado no contiene una imagen compatible.");
            }
        }
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }

    static ImageIcon scaledIcon(Path imagePath, int width, int height) throws IOException {
        if (imagePath == null || !Files.isRegularFile(imagePath)) {
            return null;
        }
        Image image = ImageIO.read(imagePath.toFile());
        if (image == null) {
            throw new IOException("No se pudo leer la imagen del logotipo.");
        }
        return new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH));
    }

    private static String extension(Path source) throws IOException {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            throw new IOException("El logotipo debe ser PNG, JPEG, GIF o BMP.");
        }
        String extension = name.substring(dot).toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of(".png", ".jpg", ".jpeg", ".gif", ".bmp").contains(extension)) {
            throw new IOException("El logotipo debe ser PNG, JPEG, GIF o BMP.");
        }
        return extension;
    }
}
