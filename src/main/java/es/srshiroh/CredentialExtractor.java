package es.srshiroh;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CredentialExtractor {

    // Patrones para diferentes formatos de credenciales
    private static final Pattern EMAIL_PASSWORD_COLON = Pattern.compile(
            "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\s*:\\s*(.+)"
    );

    private static final Pattern EMAIL_PASSWORD_SPACE = Pattern.compile(
            "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\s+(.+)"
    );

    private static final Pattern EMAIL_PASSWORD_PIPE = Pattern.compile(
            "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\s*\\|\\s*(.+)"
    );

    private static final Pattern EMAIL_PASSWORD_SEMICOLON = Pattern.compile(
            "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\s*;\\s*(.+)"
    );

    public static class UserCredential {
        private final String email;
        private final String password;
        private final String originalFormat;

        public UserCredential(String email, String password, String originalFormat) {
            this.email = email.trim();
            this.password = password.trim();
            this.originalFormat = originalFormat;
        }

        public String getEmail() { return email; }
        public String getPassword() { return password; }
        public String getOriginalFormat() { return originalFormat; }

        @Override
        public String toString() {
            return String.format("Email: %s, Password: %s, Format: %s",
                    email, "*".repeat(password.length()), originalFormat);
        }
    }

    public static List<UserCredential> extractCredentials(String filePath) {
        List<UserCredential> credentials = new ArrayList<>();
        int lineNumber = 0;
        int validCredentials = 0;
        int invalidLines = 0;

        System.out.println("📁 Leyendo archivo: " + filePath);
        System.out.println("🔍 Buscando credenciales en múltiples formatos...\n");

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Saltar líneas vacías o comentarios
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }

                UserCredential credential = parseLine(line, lineNumber);
                if (credential != null) {
                    credentials.add(credential);
                    validCredentials++;
                    System.out.println("✅ Línea " + lineNumber + ": " + credential.getOriginalFormat() +
                            " - " + credential.getEmail());
                } else {
                    invalidLines++;
                    System.out.println("❌ Línea " + lineNumber + ": Formato no reconocido - " +
                            (line.length() > 50 ? line.substring(0, 50) + "..." : line));
                }
            }

        } catch (IOException e) {
            System.err.println("❌ Error leyendo el archivo: " + e.getMessage());
            return credentials;
        }

        // Mostrar resumen de extracción
        System.out.println("\n" + "=".repeat(50));
        System.out.println("RESUMEN DE EXTRACCIÓN DE CREDENCIALES");
        System.out.println("=".repeat(50));
        System.out.println("Total de líneas procesadas: " + lineNumber);
        System.out.println("Credenciales válidas encontradas: " + validCredentials);
        System.out.println("Líneas con formato inválido: " + invalidLines);
        System.out.println("Líneas vacías/comentarios omitidas: " + (lineNumber - validCredentials - invalidLines));

        if (validCredentials > 0) {
            System.out.println("\nFORMATOS DETECTADOS:");
            countFormats(credentials);
        }

        System.out.println("=".repeat(50) + "\n");

        return credentials;
    }

    private static UserCredential parseLine(String line, int lineNumber) {
        // Formato 1: email:password (MÁS COMÚN)
        Matcher matcher = EMAIL_PASSWORD_COLON.matcher(line);
        if (matcher.find()) {
            String email = matcher.group(1);
            String password = matcher.group(2);
            return new UserCredential(email, password, "email:password");
        }

        // Formato 2: email|password
        matcher = EMAIL_PASSWORD_PIPE.matcher(line);
        if (matcher.find()) {
            String email = matcher.group(1);
            String password = matcher.group(2);
            return new UserCredential(email, password, "email|password");
        }

        // Formato 3: email;password
        matcher = EMAIL_PASSWORD_SEMICOLON.matcher(line);
        if (matcher.find()) {
            String email = matcher.group(1);
            String password = matcher.group(2);
            return new UserCredential(email, password, "email;password");
        }

        // Formato 4: email password (separado por espacios)
        matcher = EMAIL_PASSWORD_SPACE.matcher(line);
        if (matcher.find()) {
            String email = matcher.group(1);
            String password = matcher.group(2);
            // Validar que la contraseña no contenga espacios (evitar falsos positivos)
            if (!password.contains(" ") && password.length() >= 3) {
                return new UserCredential(email, password, "email password");
            }
        }

        return null; // No se pudo parsear
    }

    private static void countFormats(List<UserCredential> credentials) {
        java.util.Map<String, Integer> formatCount = new java.util.HashMap<>();

        for (UserCredential credential : credentials) {
            String format = credential.getOriginalFormat();
            formatCount.put(format, formatCount.getOrDefault(format, 0) + 1);
        }

        for (java.util.Map.Entry<String, Integer> entry : formatCount.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " credenciales");
        }
    }
}
