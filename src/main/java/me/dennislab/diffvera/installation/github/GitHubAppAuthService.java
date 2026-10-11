package me.dennislab.diffvera.installation.github;

import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.installation.github.dto.GitHubInstallationTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * Service managing GitHub App authentication via RS256 signed JWTs
 * and minting installation access tokens.
 */
@Service
@Slf4j
public class GitHubAppAuthService {

    private final String appId;
    private final String privateKeyRaw;
    private final RestClient restClient;
    private PrivateKey cachedPrivateKey;

    @org.springframework.beans.factory.annotation.Autowired
    public GitHubAppAuthService(
        @Value("${diffvera.github.app-id:}") String appId,
        @Value("${diffvera.github.app-private-key:}") String privateKeyRaw
    ) {
        this.appId = appId.trim();
        this.privateKeyRaw = privateKeyRaw.trim();
        this.restClient = RestClient.builder().build();
    }

    public GitHubAppAuthService(
        String appId,
        String privateKeyRaw,
        RestClient restClient
    ) {
        this.appId = appId.trim();
        this.privateKeyRaw = privateKeyRaw.trim();
        this.restClient = restClient != null ? restClient : RestClient.builder().build();
    }

    /**
     * Checks if GitHub App credentials (App ID and Private Key) are configured.
     */
    public boolean isConfigured() {
        return !appId.isBlank() && !privateKeyRaw.isBlank();
    }

    /**
     * Generates an RS256 JWT authenticating Diffvera as a registered GitHub App.
     * Valid for 9 minutes (GitHub maximum is 10 minutes).
     */
    public Optional<String> createGitHubAppJwt() {
        if (!isConfigured()) {
            log.debug("GitHub App is not fully configured (missing App ID or Private Key).");
            return Optional.empty();
        }

        try {
            PrivateKey key = getPrivateKey();
            long now = System.currentTimeMillis();
            Date iat = new Date(now - 60_000); // 60 seconds clock skew tolerance
            Date exp = new Date(now + 540_000); // 9 minutes expiration

            String jwt = Jwts.builder()
                .issuer(appId)
                .issuedAt(iat)
                .expiration(exp)
                .signWith(key, Jwts.SIG.RS256)
                .compact();

            return Optional.of(jwt);
        } catch (Exception e) {
            log.error("Failed to generate GitHub App JWT: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Obtains a short-lived GitHub App Installation Access Token for a specific installation ID.
     */
    public Optional<String> getInstallationToken(Long installationId) {
        Optional<String> appJwt = createGitHubAppJwt();
        if (appJwt.isEmpty()) {
            return Optional.empty();
        }

        try {
            GitHubInstallationTokenResponse response = restClient.post()
                .uri("https://api.github.com/app/installations/{id}/access_tokens", installationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt.get())
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("User-Agent", "Diffvera-Backend")
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GitHubInstallationTokenResponse.class);

            if (response != null && response.getToken() != null && !response.getToken().isBlank()) {
                return Optional.of(response.getToken());
            }
        } catch (Exception e) {
            log.error("Failed to fetch installation access token for installation ID {}: {}", installationId, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Obtains installation details (account login, account type) from GitHub API using the App JWT.
     */
    public Optional<me.dennislab.diffvera.installation.github.dto.GitHubInstallationDetailDto> getInstallationDetail(Long installationId) {
        Optional<String> appJwt = createGitHubAppJwt();
        if (appJwt.isEmpty()) {
            return Optional.empty();
        }

        try {
            me.dennislab.diffvera.installation.github.dto.GitHubInstallationDetailDto response = restClient.get()
                .uri("https://api.github.com/app/installations/{id}", installationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt.get())
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("User-Agent", "Diffvera-Backend")
                .retrieve()
                .body(me.dennislab.diffvera.installation.github.dto.GitHubInstallationDetailDto.class);

            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.error("Failed to fetch installation details for ID {}: {}", installationId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lists all installations of the Diffvera GitHub App directly from GitHub API.
     */
    public java.util.List<me.dennislab.diffvera.installation.github.dto.GitHubInstallationDetailDto> fetchAppInstallations() {
        Optional<String> appJwt = createGitHubAppJwt();
        if (appJwt.isEmpty()) {
            return java.util.List.of();
        }

        try {
            me.dennislab.diffvera.installation.github.dto.GitHubInstallationDetailDto[] response = restClient.get()
                .uri("https://api.github.com/app/installations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt.get())
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("User-Agent", "Diffvera-Backend")
                .retrieve()
                .body(me.dennislab.diffvera.installation.github.dto.GitHubInstallationDetailDto[].class);

            return response != null ? java.util.List.of(response) : java.util.List.of();
        } catch (Exception e) {
            log.error("Failed to fetch app installations from GitHub: {}", e.getMessage());
            return java.util.List.of();
        }
    }

    private synchronized PrivateKey getPrivateKey() throws Exception {
        if (cachedPrivateKey != null) {
            return cachedPrivateKey;
        }

        String pemContent = privateKeyRaw;
        // Check if the property points to an existing file path
        if (Files.exists(Path.of(privateKeyRaw))) {
            pemContent = Files.readString(Path.of(privateKeyRaw));
        } else if (Files.exists(Path.of("backend/diffvera", privateKeyRaw))) {
            pemContent = Files.readString(Path.of("backend/diffvera", privateKeyRaw));
        }

        boolean isPkcs1 = pemContent.contains("BEGIN RSA PRIVATE KEY");

        // Clean PEM markers and line breaks
        String cleaned = pemContent
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(cleaned);

        if (isPkcs1) {
            keyBytes = wrapPkcs1ToPkcs8(keyBytes);
        }

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        this.cachedPrivateKey = keyFactory.generatePrivate(keySpec);
        return this.cachedPrivateKey;
    }

    private byte[] wrapPkcs1ToPkcs8(byte[] pkcs1Bytes) {
        int pkcs1Length = pkcs1Bytes.length;
        int totalLength = pkcs1Length + 22;
        byte[] pkcs8Header = new byte[] {
            0x30, (byte) 0x82, (byte) ((totalLength >> 8) & 0xff), (byte) (totalLength & 0xff),
            0x02, 0x01, 0x00,
            0x30, 0x0d,
            0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
            0x05, 0x00,
            0x04, (byte) 0x82, (byte) ((pkcs1Length >> 8) & 0xff), (byte) (pkcs1Length & 0xff)
        };
        byte[] pkcs8Bytes = new byte[pkcs8Header.length + pkcs1Bytes.length];
        System.arraycopy(pkcs8Header, 0, pkcs8Bytes, 0, pkcs8Header.length);
        System.arraycopy(pkcs1Bytes, 0, pkcs8Bytes, pkcs8Header.length, pkcs1Bytes.length);
        return pkcs8Bytes;
    }
}
