package es.srshiroh;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration; // Asegurarse de que java.time.Duration está importado

public class SeleniumLoginService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SeleniumLoginService.class);
    private WebDriver driver;

    private static final String LOGIN_URL = "https://www.crunchyroll.com/login";
    // IDs hipotéticos de los elementos del formulario. ¡VERIFICAR ESTOS EN LA PÁGINA REAL!
    private static final String EMAIL_INPUT_ID = "etp-email-input";
    private static final String PASSWORD_INPUT_ID = "etp-password-input";
    // Selector para el botón de login. Puede ser más específico.
    // Intentaremos con un selector de botón de tipo submit dentro de un formulario que contenga los campos.
    // O un selector más directo si se conoce, por ejemplo, por data-testid.
    private static final String LOGIN_BUTTON_XPATH = "//form[.//input[@id='" + EMAIL_INPUT_ID + "']]//button[@type='submit']";
    // Alternativa si el botón tiene un data-testid (más robusto si existe)
    // private static final String LOGIN_BUTTON_DATA_TESTID = "login-button";


    public SeleniumLoginService(boolean headless) {
        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            if (headless) {
                options.addArguments("--headless");
                options.addArguments("--disable-gpu"); // Opcional, a veces necesario para headless en Windows
                options.addArguments("--window-size=1920,1080"); // Opcional
            }
            // Añadir user-agent para parecer un navegador normal, podría ayudar a evitar detección
            options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36");

            // Deshabilitar "Chrome is being controlled by automated test software"
            options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
            options.setExperimentalOption("useAutomationExtension", false);

            // Otras opciones para intentar parecer menos un bot
            options.addArguments("--disable-blink-features=AutomationControlled"); // Para versiones más nuevas de Chrome

            this.driver = new ChromeDriver(options);
            logger.info("ChromeDriver iniciado correctamente en modo {}.", headless ? "headless" : "normal");

        } catch (Exception e) {
            logger.error("Error al inicializar Selenium WebDriver: {}", e.getMessage(), e);
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

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20)); // Aumentado a 20s

            // Esperar y rellenar campo de email
            logger.debug("Esperando por el campo de email (ID: {})...", EMAIL_INPUT_ID);
            WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(EMAIL_INPUT_ID)));
            emailField.clear(); // Limpiar el campo por si acaso
            emailField.sendKeys(email);
            logger.debug("Email ingresado.");

            // Esperar y rellenar campo de contraseña
            logger.debug("Esperando por el campo de contraseña (ID: {})...", PASSWORD_INPUT_ID);
            WebElement passwordField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(PASSWORD_INPUT_ID)));
            passwordField.clear();
            passwordField.sendKeys(password);
            logger.debug("Contraseña ingresada.");

            // Pequeña pausa antes de hacer clic, a veces ayuda
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            // Esperar y hacer clic en el botón de login
            logger.debug("Esperando por el botón de login (XPath: {})...", LOGIN_BUTTON_XPATH);
            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(LOGIN_BUTTON_XPATH)));
            // Alternativa con data-testid:
            // WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[@data-testid='" + LOGIN_BUTTON_DATA_TESTID + "']")));

            loginButton.click();
            logger.info("Botón de login presionado para el email: {}", email);

            // Aquí iría la lógica para detectar éxito/fallo y extraer tokens/cookies (Paso 3 del plan)
            // Por ahora, devolvemos un resultado provisional.
            return new AuthenticationResult(email, false, "Lógica de detección de éxito/fallo y extracción de tokens pendiente.");

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
