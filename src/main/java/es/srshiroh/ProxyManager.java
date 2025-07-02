package es.srshiroh;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Iterator;

public class ProxyManager {

    private final List<ProxyInfo> workingProxies = Collections.synchronizedList(new ArrayList<>());
    private int currentIndex = 0;
    private static final String PROXIES_FILE_PATH = "C:\\Users\\Shiroh\\Documents\\CrunchyChecker\\Proxies.txt";
    private static final int PROXY_TEST_TIMEOUT = 10000; // 10 segundos
    private static final String TEST_URL = "https://httpbin.org/ip"; // URL para probar conectividad

    public static class ProxyInfo {
        private final String host;
        private final int port;
        private boolean isValid;
        private int failureCount;

        public ProxyInfo(String host, int port) {
            this.host = host;
            this.port = port;
            this.isValid = true;
            this.failureCount = 0;
        }

        public String getHost() { return host; }
        public int getPort() { return port; }
        public boolean isValid() { return isValid; }
        public int getFailureCount() { return failureCount; }

        public void markAsInvalid() {
            this.isValid = false;
            this.failureCount++;
        }

        public void markAsValid() {
            this.isValid = true;
            this.failureCount = 0;
        }

        public void incrementFailure() {
            this.failureCount++;
            // Si falla más de 3 veces, marcarlo como inválido
            if (this.failureCount >= 3) {
                this.isValid = false;
            }
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    public ProxyManager() {
        loadProxiesFromFile();
    }

    private void loadProxiesFromFile() {
        List<String> proxyLines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(PROXIES_FILE_PATH))) {
            System.out.println("📂 Leyendo proxies desde: " + PROXIES_FILE_PATH);

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) { // Ignorar líneas vacías y comentarios
                    proxyLines.add(line);
                }
            }

            System.out.println("📋 Proxies encontrados en archivo: " + proxyLines.size());

        } catch (Exception e) {
            System.err.println("❌ Error leyendo archivo de proxies: " + e.getMessage());
            System.out.println("⚠️ Usando proxies de respaldo...");
            loadFallbackProxies(proxyLines);
        }

        if (proxyLines.isEmpty()) {
            System.out.println("⚠️ No se encontraron proxies en el archivo, usando proxies de respaldo...");
            loadFallbackProxies(proxyLines);
        }

        System.out.println("🔄 Verificando proxies disponibles...");

        for (String proxyStr : proxyLines) {
            String[] parts = proxyStr.split(":");
            if (parts.length == 2) {
                try {
                    String host = parts[0].trim();
                    int port = Integer.parseInt(parts[1].trim());

                    ProxyInfo proxyInfo = new ProxyInfo(host, port);
                    if (testProxy(proxyInfo)) {
                        workingProxies.add(proxyInfo);
                        System.out.println("✅ Proxy válido: " + proxyStr);
                    } else {
                        System.out.println("❌ Proxy inválido: " + proxyStr);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("❌ Formato incorrecto: " + proxyStr);
                }
            } else {
                System.out.println("❌ Formato incorrecto (debe ser host:puerto): " + proxyStr);
            }
        }

        System.out.println("📊 Proxies válidos encontrados: " + workingProxies.size());

        if (workingProxies.isEmpty()) {
            System.out.println("⚠️ ADVERTENCIA: No hay proxies válidos disponibles. El programa funcionará sin proxy.");
        }
    }

    private void loadFallbackProxies(List<String> proxyLines) {
        // Lista de proxies de respaldo en caso de que el archivo no esté disponible
        String[] fallbackProxies = {
            "8.8.8.8:80",
            "1.1.1.1:80",
            "208.67.222.222:80"
        };

        for (String proxy : fallbackProxies) {
            proxyLines.add(proxy);
        }
        System.out.println("📋 Proxies de respaldo cargados: " + fallbackProxies.length);
    }

    private boolean testProxy(ProxyInfo proxyInfo) {
        return testProxy(proxyInfo.getHost(), proxyInfo.getPort());
    }

    private boolean testProxy(String host, int port) {
        try {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
            URL url = new URL(TEST_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);

            connection.setConnectTimeout(PROXY_TEST_TIMEOUT);
            connection.setReadTimeout(PROXY_TEST_TIMEOUT);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized ProxyInfo getNextProxy() {
        // Limpiar proxies inválidos antes de obtener el siguiente
        cleanInvalidProxies();

        if (workingProxies.isEmpty()) {
            return null;
        }

        // Rotar al siguiente proxy válido
        ProxyInfo proxy = null;
        int attempts = 0;

        while (attempts < workingProxies.size()) {
            currentIndex = (currentIndex + 1) % workingProxies.size();
            proxy = workingProxies.get(currentIndex);

            if (proxy.isValid()) {
                break;
            }
            attempts++;
        }

        // Si todos los proxies están marcados como inválidos, intentar validar algunos
        if (proxy != null && !proxy.isValid()) {
            revalidateProxies();
            return getNextProxy(); // Llamada recursiva después de revalidar
        }

        return proxy;
    }

    public synchronized void markProxyAsInvalid(ProxyInfo proxy) {
        if (proxy != null) {
            proxy.markAsInvalid();
            System.out.println("⚠️ Proxy marcado como inválido: " + proxy);

            // Si el proxy falla demasiadas veces, removerlo completamente
            if (proxy.getFailureCount() >= 5) {
                removeProxy(proxy);
            }
        }
    }

    public synchronized void markProxyAsFailure(ProxyInfo proxy) {
        if (proxy != null) {
            proxy.incrementFailure();
            System.out.println("⚠️ Fallo registrado para proxy: " + proxy + " (fallos: " + proxy.getFailureCount() + ")");

            if (!proxy.isValid()) {
                System.out.println("❌ Proxy marcado como inválido después de múltiples fallos: " + proxy);
            }
        }
    }

    public synchronized void markProxyAsValid(ProxyInfo proxy) {
        if (proxy != null) {
            proxy.markAsValid();
            System.out.println("✅ Proxy marcado como válido: " + proxy);
        }
    }

    private synchronized void removeProxy(ProxyInfo proxy) {
        if (workingProxies.remove(proxy)) {
            System.out.println("🗑️ Proxy eliminado permanentemente: " + proxy);
        }
    }

    private synchronized void cleanInvalidProxies() {
        Iterator<ProxyInfo> iterator = workingProxies.iterator();
        while (iterator.hasNext()) {
            ProxyInfo proxy = iterator.next();
            if (proxy.getFailureCount() >= 5) {
                iterator.remove();
                System.out.println("🗑️ Proxy eliminado por exceso de fallos: " + proxy);
            }
        }
    }

    private synchronized void revalidateProxies() {
        System.out.println("🔄 Revalidando proxies...");

        for (ProxyInfo proxy : workingProxies) {
            if (!proxy.isValid() && proxy.getFailureCount() < 5) {
                if (testProxy(proxy)) {
                    proxy.markAsValid();
                    System.out.println("✅ Proxy revalidado exitosamente: " + proxy);
                } else {
                    proxy.incrementFailure();
                    System.out.println("❌ Proxy sigue siendo inválido: " + proxy);
                }
            }
        }
    }

    public synchronized int getProxyCount() {
        return workingProxies.size();
    }

    public synchronized int getValidProxyCount() {
        return (int) workingProxies.stream().filter(ProxyInfo::isValid).count();
    }

    public synchronized List<ProxyInfo> getAllProxies() {
        return new ArrayList<>(workingProxies);
    }

    public synchronized void printProxyStatus() {
        System.out.println("📊 Estado de proxies:");
        System.out.println("   Total: " + getProxyCount());
        System.out.println("   Válidos: " + getValidProxyCount());
        System.out.println("   Inválidos: " + (getProxyCount() - getValidProxyCount()));
    }

    // Método para validar un proxy específico desde el exterior
    public boolean validateProxy(ProxyInfo proxy) {
        if (proxy == null) return false;

        boolean isValid = testProxy(proxy);
        if (isValid) {
            proxy.markAsValid();
        } else {
            proxy.incrementFailure();
        }

        return isValid;
    }
}
