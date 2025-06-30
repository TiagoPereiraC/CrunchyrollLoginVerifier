package es.srshiroh;

import java.util.Collection;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;
import org.openqa.selenium.Cookie;

public class AuthenticationResult {
    private final String email;
    private final boolean success;
    private final String errorMessage;
    private final String accessToken;
    private final String refreshToken;
    private final String accountId;
    private final String responseBody;
    private final Map<String, String> cookies;

    public AuthenticationResult(String email, boolean success, String errorMessage) {
        this.email = email;
        this.success = success;
        this.errorMessage = errorMessage;
        this.accessToken = null;
        this.refreshToken = null;
        this.accountId = null;
        this.responseBody = null;
        this.cookies = new HashMap<>();
    }

    public AuthenticationResult(String email, boolean success, String accessToken,
                                String refreshToken, String accountId, String responseBody) {
        this.email = email;
        this.success = success;
        this.errorMessage = null;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accountId = accountId;
        this.responseBody = responseBody;
        this.cookies = new HashMap<>();
    }

    public AuthenticationResult(String email, boolean success, String accessToken,
                                String refreshToken, String accountId, String responseBody,
                                Collection<Cookie> seleniumCookies) {
        this.email = email;
        this.success = success;
        this.errorMessage = null;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accountId = accountId;
        this.responseBody = responseBody;
        this.cookies = new HashMap<>();

        if (seleniumCookies != null) {
            for (Cookie cookie : seleniumCookies) {
                this.cookies.put(cookie.getName(), cookie.getValue());
            }
        }
    }

    public String getEmail() { return email; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getAccountId() { return accountId; }
    public String getResponseBody() { return responseBody; }

    public Map<String, String> getCookies() {
        return Collections.unmodifiableMap(cookies);
    }
}
