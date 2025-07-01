package es.srshiroh;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver; // Cambio a EdgeDriver
import org.openqa.selenium.edge.EdgeOptions; // Cambio a EdgeOptions
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.util.Set;

public class SeleniumLoginService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SeleniumLoginService.class);
    private WebDriver driver;

    private static final String LOGIN_URL = "https://sso.crunchyroll.com/es/login";
    // Nuevos selectores basados en la inspección del usuario
    private static final String EMAIL_INPUT_NAME = "email";
    private static final String PASSWORD_INPUT_NAME = "password";
    // Usaremos data-t para el botón, es más robusto que el XPATH anterior que dependía de IDs inexistentes.
    private static final String LOGIN_BUTTON_SELECTOR = "button[data-t='login-button']"; // Selector CSS


    public SeleniumLoginService(boolean headless) {
        try {
            WebDriverManager.edgedriver().setup(); // Cambio a edgedriver
            EdgeOptions options = new EdgeOptions(); // Cambio a EdgeOptions
            if (headless) {
                options.addArguments("--headless");
                options.addArguments("--disable-gpu");
                options.addArguments("--window-size=1920,1080");
            }
            // User agent actualizado para Edge (aunque el anterior podría funcionar, es mejor ser específico)
            options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 Edg/90.0.818.66");

            // Opciones para evitar detección como bot (generalmente funcionan para todos los basados en Chromium)
            options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
            options.setExperimentalOption("useAutomationExtension", false);
            options.addArguments("--disable-blink-features=AutomationControlled");

            this.driver = new EdgeDriver(options); // Cambio a EdgeDriver
            logger.info("EdgeDriver iniciado correctamente en modo {}.", headless ? "headless" : "normal");

        } catch (Exception e) {
            logger.error("Error al inicializar Selenium WebDriver (Edge): {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo inicializar Selenium WebDriver", e);
        }
    }

    public WebDriver getDriver() {
        return driver;
    }

    public AuthenticationResult performLogin(String email, String password) {
        logger.info("Iniciando proceso de login para el email: {}", email);
        try {
            driver.get(LOGIN_URL);
            logger.debug("Navegado a la página de login: {}", LOGIN_URL);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            // Esperar y rellenar campo de email usando el atributo name
            logger.debug("Esperando por el campo de email (Name: {})...", EMAIL_INPUT_NAME);
            WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name(EMAIL_INPUT_NAME)));
            emailField.clear();
            emailField.sendKeys(email);
            logger.debug("Email ingresado.");

            // Esperar y rellenar campo de contraseña usando el atributo name
            logger.debug("Esperando por el campo de contraseña (Name: {})...", PASSWORD_INPUT_NAME);
            WebElement passwordField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name(PASSWORD_INPUT_NAME)));
            passwordField.clear();
            passwordField.sendKeys(password);
            logger.debug("Contraseña ingresada.");

            // Pequeña pausa antes de hacer clic
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            // Esperar y hacer clic en el botón de login usando el selector CSS para data-t
            logger.debug("Esperando por el botón de login (Selector: {})...", LOGIN_BUTTON_SELECTOR);
            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(LOGIN_BUTTON_SELECTOR)));

            loginButton.click();
            logger.info("Botón de login presionado para el email: {}", email);

            // Esperar un momento para que la página procese el login
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

            // Detectar si el login fue exitoso
            String currentUrl = driver.getCurrentUrl();
            logger.debug("URL actual después del login: {}", currentUrl);

            // Verificar si hay errores de login
            try {
                // Buscar mensajes de error comunes
                WebElement errorElement = driver.findElement(By.cssSelector("[data-t='error-message'], .error, .alert-danger, [class*='error']"));
                if (errorElement.isDisplayed()) {
                    String errorText = errorElement.getText();
                    logger.warn("Error de login detectado: {}", errorText);
                    return new AuthenticationResult(email, false, "Error de login: " + errorText);
                }
            } catch (Exception e) {
                // No se encontró mensaje de error, continuar
                logger.debug("No se encontraron mensajes de error visibles");
            }

            // Verificar si seguimos en la página de login (indicaría fallo)
            if (currentUrl.contains("/login") || currentUrl.contains("login")) {
                logger.warn("Aún en página de login, el login probablemente falló");
                return new AuthenticationResult(email, false, "Login falló - aún en página de login");
            }

            // Verificar redirección exitosa (indicaría éxito)
            if (currentUrl.contains("/home") || currentUrl.contains("/dashboard") ||
                currentUrl.contains("/profile") || currentUrl.contains("crunchyroll.com") && !currentUrl.contains("/login")) {

                logger.info("Login exitoso detectado - redirigido a: {}", currentUrl);

                // Extraer cookies
                Set<Cookie> seleniumCookies = driver.manage().getCookies();
                logger.info("Cookies capturadas: {}", seleniumCookies.size());

                // Intentar extraer tokens de localStorage o cookies
                String accessToken = null;
                String refreshToken = null;
                String accountId = null;

                try {
                    // Buscar tokens en localStorage
                    JavascriptExecutor js = (JavascriptExecutor) driver;
                    accessToken = (String) js.executeScript("return localStorage.getItem('access_token') || localStorage.getItem('accessToken') || localStorage.getItem('etp_rt');");
                    refreshToken = (String) js.executeScript("return localStorage.getItem('refresh_token') || localStorage.getItem('refreshToken');");
                    accountId = (String) js.executeScript("return localStorage.getItem('account_id') || localStorage.getItem('accountId') || localStorage.getItem('user_id');");

                    // Si no hay tokens en localStorage, buscar en cookies
                    if (accessToken == null) {
                        for (Cookie cookie : seleniumCookies) {
                            if (cookie.getName().contains("etp_rt") || cookie.getName().contains("access_token")) {
                                accessToken = cookie.getValue();
                            }
                            if (cookie.getName().contains("refresh_token")) {
                                refreshToken = cookie.getValue();
                            }
                            if (cookie.getName().contains("account_id") || cookie.getName().contains("user_id")) {
                                accountId = cookie.getValue();
                            }
                        }
                    }

                    logger.info("Tokens extraídos - Access Token: {}, Refresh Token: {}, Account ID: {}",
                               accessToken != null ? "✓" : "✗",
                               refreshToken != null ? "✓" : "✗",
                               accountId != null ? "✓" : "✗");

                } catch (Exception e) {
                    logger.warn("Error extrayendo tokens: {}", e.getMessage());
                }

                return new AuthenticationResult(email, true, accessToken, refreshToken, accountId, currentUrl, seleniumCookies);
            }

            // Si llegamos aquí, no pudimos determinar claramente el estado
            logger.warn("Estado de login incierto para {}, URL: {}", email, currentUrl);
            Set<Cookie> seleniumCookies = driver.manage().getCookies();
            return new AuthenticationResult(email, false, "Estado de login incierto - URL: " + currentUrl);

        } catch (org.openqa.selenium.TimeoutException e) {
            logger.error("Timeout esperando elementos en la página de login para {}: {}", email, e.getMessage());
            return new AuthenticationResult(email, false, "Timeout esperando elementos en la página de login.");
        } catch (Exception e) {
            logger.error("Error durante el proceso de login con Selenium para {}: {}", email, e.getMessage(), e);
            return new AuthenticationResult(email, false, "Error inesperado durante login con Selenium: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (driver != null) {
            try {
                logger.info("Cerrando WebDriver...");
                driver.quit();
                logger.info("WebDriver cerrado.");
            } catch (Exception e) {
                logger.error("Error al cerrar WebDriver: {}", e.getMessage(), e);
            }
        }
    }
}
