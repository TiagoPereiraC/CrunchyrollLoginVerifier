package es.srshiroh;

public class AuthenticationResult {
    private final String email;
    private final boolean success;
    private final String errorMessage;
    private final String accessToken;
    private final String refreshToken;
    private final String accountId;
    private final String responseBody;

    public AuthenticationResult(String email, boolean success, String errorMessage) {
        this.email = email;
        this.success = success;
        this.errorMessage = errorMessage;
        this.accessToken = null;
        this.refreshToken = null;
        this.accountId = null;
        this.responseBody = null;
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
    }

    public String getEmail() { return email; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getAccountId() { return accountId; }
    public String getResponseBody() { return responseBody; }
}
