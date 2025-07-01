package com.example;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

public class BraveWebDriverInitializer { // TODO: Rename class to EdgeWebDriverInitializer

    public static void main(String[] args) {
        // System.setProperty("webdriver.edge.driver", "RUTA_A_TU_MSEDGEDRIVER_EXE");
        // Selenium Manager (incluido en Selenium 4+) debería intentar descargar
        // msedgedriver.exe automáticamente si no se especifica esta propiedad.

        // Configurar las opciones de Edge
        EdgeOptions options = new EdgeOptions();
        // No especificamos options.setBinary() para que Selenium intente encontrar Edge automáticamente

        // Agregar argumentos comunes para la automatización (opcional, pero recomendado)
        // options.addArguments("--headless"); // Desactivado para ver el navegador
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 Edg/90.0.818.66"); // User agent para Edge
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        WebDriver driver = null;
        try {
            System.out.println("INFO: Intentando inicializar WebDriver con Microsoft Edge...");
            driver = new EdgeDriver(options);
            System.out.println("SUCCESS: WebDriver inicializado correctamente con Microsoft Edge.");

            // Navegar a una página web
            System.out.println("INFO: Navegando a https://www.google.com...");
            driver.get("https://www.google.com");
            System.out.println("SUCCESS: Navegación a google.com solicitada.");

            // Pequeña espera para asegurar que la página cargue
            try {
                Thread.sleep(3000); // Espera 3 segundos
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            System.out.println("INFO: URL actual: " + driver.getCurrentUrl());
            System.out.println("INFO: Título de la página: " + driver.getTitle());

        } catch (Exception e) {
            System.err.println("ERROR: Error durante la ejecución de Selenium con Microsoft Edge:");
            e.printStackTrace();
        } finally {
            if (driver != null) {
                System.out.println("INFO: Cerrando el navegador...");
                driver.quit();
                System.out.println("INFO: Navegador cerrado.");
            }
        }
    }
}
