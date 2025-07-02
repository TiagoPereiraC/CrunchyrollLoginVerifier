package es.srshiroh;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Proxy;
import java.time.Duration;
import java.util.Set;
import java.util.logging.Logger;

public class SeleniumLoginService implements AutoCloseable {

    private WebDriver driver;
    private static final String LOGIN_URL = "https://sso.crunchyroll.com/es/login";
    private static final Logger logger = Logger.getLogger(SeleniumLoginService.class.getName());
    private ProxyManager.ProxyInfo currentProxy;

    public SeleniumLoginService(boolean headless) {
        this(headless, null);
    }

    public SeleniumLoginService(boolean headless, ProxyManager.ProxyInfo proxy) {
        this.currentProxy = proxy;
        try {
            WebDriverManager.edgedriver().setup();
            EdgeOptions options = new EdgeOptions();

            if (headless) {
                options.addArguments("--headless");
                options.addArguments("--disable-gpu");
                options.addArguments("--window-size=1920,1080");
                logger.info("EdgeDriver OPTIMIZADO iniciado correctamente.");
            } else {
                logger.info("EdgeDriver iniciado correctamente en modo headless.");
            }

            // Configurar proxy si está disponible
            if (proxy != null) {
                Proxy seleniumProxy = new Proxy();
                seleniumProxy.setHttpProxy(proxy.getHost() + ":" + proxy.getPort());
                seleniumProxy.setSslProxy(proxy.getHost() + ":" + proxy.getPort());
                options.setProxy(seleniumProxy);
                logger.info("Usando proxy: " + proxy.toString());
            }

            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0");
            options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
            options.setExperimentalOption("useAutomationExtension", false);
            options.addArguments("--disable-blink-features=AutomationControlled");

            this.driver = new EdgeDriver(options);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo inicializar WebDriver", e);
        }
    }

    public AuthenticationResult performLogin(String email, String password) {
        try {
            logger.info("Iniciando proceso de login OPTIMIZADO para: " + email);

            driver.get(LOGIN_URL);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            // Verificar si hay límite de solicitudes antes de continuar
            if (hasRateLimitError()) {
                String rateLimitMsg = getRateLimitMessage();
                logger.warning("Límite de solicitudes detectado para " + email + ": " + rateLimitMsg);
                return new AuthenticationResult(email, false, "RATE_LIMIT: " + rateLimitMsg);
            }

            WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("email")));
            emailField.clear();
            Thread.sleep(200);
            emailField.sendKeys(email);
            Thread.sleep(500);

            WebElement passwordField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("password")));
            passwordField.clear();
            Thread.sleep(200);
            passwordField.sendKeys(password);
            Thread.sleep(1000);

            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button[data-t='login-button']:not([aria-disabled='true'])")));

            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", loginButton);
            Thread.sleep(300);

            try {
                loginButton.click();
                logger.info("Botón de login presionado para el email: " + email);
            } catch (Exception e) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginButton);
                logger.info("Botón de login presionado para el email: " + email);
            }

            Thread.sleep(4000);

            String currentUrl = driver.getCurrentUrl();

            // Verificar límite de solicitudes después del login
            if (hasRateLimitError()) {
                String rateLimitMsg = getRateLimitMessage();
                logger.warning("Límite de solicitudes detectado después del login para " + email + ": " + rateLimitMsg);
                return new AuthenticationResult(email, false, "RATE_LIMIT: " + rateLimitMsg);
            }

            if (hasLoginError()) {
                String errorMsg = getLoginErrorMessage();
                if (errorMsg.toLowerCase().contains("incorrect") || errorMsg.toLowerCase().contains("wrong") ||
                    errorMsg.toLowerCase().contains("invalid") || errorMsg.toLowerCase().contains("incorrecto")) {
                    return new AuthenticationResult(email, false, "contraseña incorrecta");
                }
                return new AuthenticationResult(email, false, errorMsg);
            }

            if (currentUrl.contains("/login") && currentUrl.contains("sso.crunchyroll.com")) {
                logger.warning("Aún en página de login, el login probablemente falló");
                return new AuthenticationResult(email, false, "permanece en página de login");
            }

            if (isSuccessfulRedirect(currentUrl)) {
                logger.info("Login exitoso detectado - redirigido a: " + currentUrl);
                Set<Cookie> cookies = driver.manage().getCookies();
                logger.info("Cookies capturadas: " + cookies.size());

                // Extraer tokens si están disponibles
                extractTokens();

                return new AuthenticationResult(email, true, cookies);
            }

            Thread.sleep(3000);
            currentUrl = driver.getCurrentUrl();

            if (isSuccessfulRedirect(currentUrl)) {
                logger.info("Login exitoso detectado - redirigido a: " + currentUrl);
                Set<Cookie> cookies = driver.manage().getCookies();
                logger.info("Cookies capturadas: " + cookies.size());

                // Extraer tokens si están disponibles
                extractTokens();

                return new AuthenticationResult(email, true, cookies);
            }

            logger.warning("Aún en página de login, el login probablemente falló");
            return new AuthenticationResult(email, false, "permanece en página de login");

        } catch (org.openqa.selenium.TimeoutException e) {
            return new AuthenticationResult(email, false, "Timeout - botón no se habilitó o campos no se llenaron");
        } catch (Exception e) {
            logger.severe("Error durante el proceso de login con Selenium para " + email + ": " + e.getMessage());
            return new AuthenticationResult(email, false, "Error: " + e.getMessage());
        }
    }

    private boolean hasRateLimitError() {
        try {
            // Verificar mensajes de límite de solicitudes en la página
            String pageSource = driver.getPageSource().toLowerCase();

            // Patrones comunes de límite de solicitudes
            if (pageSource.contains("too many requests") ||
                pageSource.contains("rate limit") ||
                pageSource.contains("limite de solicitudes") ||
                pageSource.contains("demasiadas solicitudes") ||
                pageSource.contains("temporarily blocked") ||
                pageSource.contains("temporalmente bloqueado") ||
                pageSource.contains("429") ||
                pageSource.contains("please wait") ||
                pageSource.contains("por favor espera") ||
                pageSource.contains("try again later") ||
                pageSource.contains("intenta más tarde")) {
                return true;
            }

            // Verificar elementos específicos de error de límite
            try {
                WebElement rateLimitElement = driver.findElement(By.xpath(
                    "//*[contains(text(), 'too many') or contains(text(), 'rate limit') or " +
                    "contains(text(), 'demasiadas') or contains(text(), 'limite') or " +
                    "contains(text(), 'blocked') or contains(text(), 'bloqueado')]"));
                return rateLimitElement.isDisplayed();
            } catch (Exception ignored) {}

            // Verificar si el título contiene indicaciones de límite
            String title = driver.getTitle().toLowerCase();
            if (title.contains("rate limit") || title.contains("blocked") || title.contains("429")) {
                return true;
            }

        } catch (Exception ignored) {}

        return false;
    }

    private String getRateLimitMessage() {
        try {
            // Intentar obtener el mensaje específico del límite de solicitudes
            String pageSource = driver.getPageSource().toLowerCase();

            if (pageSource.contains("too many requests")) {
                return "Demasiadas solicitudes - límite excedido";
            }
            if (pageSource.contains("rate limit")) {
                return "Límite de velocidad excedido";
            }
            if (pageSource.contains("temporarily blocked")) {
                return "Temporalmente bloqueado";
            }
            if (pageSource.contains("429")) {
                return "Error 429 - Demasiadas solicitudes";
            }

            // Intentar obtener mensaje específico de elementos
            try {
                WebElement rateLimitElement = driver.findElement(By.xpath(
                    "//*[contains(text(), 'too many') or contains(text(), 'rate limit') or " +
                    "contains(text(), 'demasiadas') or contains(text(), 'limite') or " +
                    "contains(text(), 'blocked') or contains(text(), 'bloqueado')]"));
                return rateLimitElement.getText().trim();
            } catch (Exception ignored) {}

        } catch (Exception ignored) {}

        return "Límite de solicitudes detectado";
    }

    private boolean hasLoginError() {
        try {
            WebElement errorElement = driver.findElement(By.cssSelector(
                "[data-t='error-message'], .error, .alert-danger, [class*='error'], .message--error"));
            return errorElement.isDisplayed() && !errorElement.getText().trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private String getLoginErrorMessage() {
        try {
            WebElement errorElement = driver.findElement(By.cssSelector(
                "[data-t='error-message'], .error, .alert-danger, [class*='error'], .message--error"));
            if (errorElement.isDisplayed()) {
                return errorElement.getText().trim();
            }
        } catch (Exception ignored) {}

        try {
            String pageSource = driver.getPageSource().toLowerCase();
            if (pageSource.contains("incorrect") || pageSource.contains("invalid") ||
                pageSource.contains("wrong") || pageSource.contains("incorrecto")) {
                return "contraseña incorrecta";
            }
        } catch (Exception ignored) {}

        return "Error de login desconocido";
    }

    private boolean isSuccessfulRedirect(String url) {
        if (url.contains("crunchyroll.com") && !url.contains("/login") && !url.contains("sso")) {
            return true;
        }

        if (url.contains("cloudflare") || url.contains("cf-challenge") ||
            url.contains("captcha") || url.contains("challenge")) {
            return true;
        }

        try {
            String pageSource = driver.getPageSource().toLowerCase();
            return pageSource.contains("welcome") || pageSource.contains("dashboard") ||
                   pageSource.contains("profile") || pageSource.contains("settings") ||
                   pageSource.contains("bienvenido") || pageSource.contains("perfil");
        } catch (Exception e) {
            return false;
        }
    }

    private void extractTokens() {
        try {
            // Intentar extraer tokens del localStorage o sessionStorage
            JavascriptExecutor js = (JavascriptExecutor) driver;

            String accessToken = (String) js.executeScript("return localStorage.getItem('access_token') || sessionStorage.getItem('access_token');");
            String refreshToken = (String) js.executeScript("return localStorage.getItem('refresh_token') || sessionStorage.getItem('refresh_token');");
            String accountId = (String) js.executeScript("return localStorage.getItem('account_id') || sessionStorage.getItem('account_id');");

            logger.info("Tokens extraídos - Access Token: " + (accessToken != null ? "✓" : "✗") +
                       ", Refresh Token: " + (refreshToken != null ? "✓" : "✗") +
                       ", Account ID: " + (accountId != null ? "✓" : "✗"));

        } catch (Exception e) {
            logger.warning("Error extrayendo tokens: " + e.getMessage());
        }
    }

    public ProxyManager.ProxyInfo getCurrentProxy() {
        return currentProxy;
    }

    @Override
    public void close() {
        if (driver != null) {
            try {
                logger.info("Cerrando WebDriver...");
                driver.quit();
                logger.info("WebDriver cerrado.");
            } catch (Exception e) {
                logger.warning("Error cerrando WebDriver: " + e.getMessage());
            }
        }
    }
}
