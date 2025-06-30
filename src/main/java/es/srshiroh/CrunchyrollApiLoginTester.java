package es.srshiroh;

import java.util.ArrayList;
import java.util.List;
// Ya no necesitamos importar Map aquí si solo lo usa AuthenticationResult internamente
// import java.util.Map;

public class CrunchyrollApiLoginTester {
    // Constructor. Ya no necesita inicializar HttpClient, ObjectMapper, etc.
    public CrunchyrollApiLoginTester() {
        System.out.println("CrunchyrollApiLoginTester inicializado para usar SeleniumLoginService.");
    }

    public static void main(String[] args) {
        System.out.println("=== PROBADOR DE LOGIN CRUNCHYROLL CON SELENIUM ===\n");

        // No necesitamos una instancia de CrunchyrollApiLoginTester si los métodos son estáticos
        // o si main hace todo el trabajo con SeleniumLoginService.
        // CrunchyrollApiLoginTester tester = new CrunchyrollApiLoginTester();

        List<CrunchyrollCredentialExtractor.UserCredential> credentials =
                CrunchyrollCredentialExtractor.extractCredentials(CREDENTIALS_FILE_PATH);

        if (credentials.isEmpty()) {
            System.out.println("❌ No se encontraron credenciales válidas en el archivo de credenciales: " + CREDENTIALS_FILE_PATH);
            return;
        }

        System.out.println("📧 Probando " + credentials.size() + " credencial(es) usando Selenium WebDriver.\n");

        // Configurar headless: true para modo oculto, false para ver el navegador.
        boolean useHeadlessMode = true;
        // Podrías hacer esto configurable mediante un argumento de línea de comandos si quisieras:
        // boolean useHeadlessMode = args.length > 0 && args[0].equalsIgnoreCase("headless");

        try (SeleniumLoginService seleniumLoginService = new SeleniumLoginService(useHeadlessMode)) {
            List<AuthenticationResult> results = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < credentials.size(); i++) {
                CrunchyrollCredentialExtractor.UserCredential credential = credentials.get(i);
                System.out.println("\n🔍 Probando credencial " + (i + 1) + "/" + credentials.size());
                System.out.println("📧 Email: " + credential.getEmail());
                // La contraseña no se imprime por seguridad

                AuthenticationResult result = seleniumLoginService.performLogin(credential.getEmail(), credential.getPassword());
                results.add(result);

                if (result.isSuccess()) {
                    successCount++;
                    System.out.println("✅ ÉXITO - Login con Selenium parece exitoso!");
                    if (result.getCookies() != null && !result.getCookies().isEmpty()) {
                        System.out.println("🍪 Cookies extraídas: " + result.getCookies().size());
                        // Opcional: imprimir algunas cookies clave para depuración
                        /*
                        result.getCookies().forEach((key, value) -> {
                            if (key.startsWith("etp_") || key.startsWith("c_") || key.equals("session_id")) { // Ejemplo de cookies
                                System.out.println("   " + key + ": " +
                                    (value.length() > 60 ? value.substring(0, 57) + "..." : value));
                            }
                        });
                        */
                    } else if (result.getAccessToken() != null) {
                         System.out.println("🔑 Access Token extraído (parcial): " + result.getAccessToken().substring(0, Math.min(result.getAccessToken().length(),15)) + "...");
                    }
                    else {
                        System.out.println("ℹ️ Login exitoso, pero no se extrajeron cookies ni tokens explícitamente (o el método no lo implementó).");
                    }
                } else {
                    failCount++;
                    System.out.println("❌ FALLÓ con Selenium: " + result.getErrorMessage());
                }
                System.out.println("-".repeat(40));

                // Pausa entre intentos para no sobrecargar y permitir que las cosas se asienten si es necesario.
                if (i < credentials.size() - 1) {
                    safeSleep(2000); // Aumentado a 2 segundos
                }
            }
            printSummary(results, successCount, failCount);

        } catch (Exception e) {
            System.err.println("❌ Error crítico durante la ejecución con Selenium: " + e.getMessage());
            // Imprimir stack trace para más detalles en caso de error inesperado en la inicialización de Selenium
            e.printStackTrace(System.err);
        }
    }

    private static void safeSleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restablecer el estado de interrupción
            System.err.println("💤 Pausa interrumpida: " + e.getMessage());
        }
    }

    // printSummary ya no necesita el parámetro clientId
    private static void printSummary(List<AuthenticationResult> results, int successCount, int failCount) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🎯 RESUMEN FINAL (Login con Selenium)");
        System.out.println("=".repeat(60));
        System.out.println("📊 Total probadas: " + results.size());
        System.out.println("✅ Exitosas: " + successCount);
        System.out.println("❌ Fallidas: " + failCount);

        if (!results.isEmpty()) { // Evitar división por cero
            System.out.printf("📈 Tasa de éxito: %.2f%%%n", (successCount * 100.0 / results.size()));
        } else {
            System.out.println("📈 Tasa de éxito: N/A (no se procesaron credenciales)");
        }
        System.out.println("=".repeat(60));
    }

    // Ya no se necesita printManualInstructions para client_id
}
