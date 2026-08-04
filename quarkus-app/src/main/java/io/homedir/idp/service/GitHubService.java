package io.homedir.idp.service;

import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import com.goterl.lazysodium.interfaces.Box;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.kohsuke.github.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

@ApplicationScoped
public class GitHubService {

    private static final Logger log = Logger.getLogger(GitHubService.class);

    @ConfigProperty(name = "idp.github.token", defaultValue = "")
    String githubToken;

    @ConfigProperty(name = "idp.github.token.file", defaultValue = "/etc/homedir-idp-secrets/github-token")
    String githubTokenFile;

    @ConfigProperty(name = "idp.github.default.org")
    String defaultOrg;

    private GitHub github;
    private final LazySodiumJava sodium = new LazySodiumJava(new SodiumJava());

    private GitHub getGitHub() throws IOException {
        if (github == null) {
            String token = getGitHubToken();
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("GitHub token not configured");
            }
            github = new GitHubBuilder().withOAuthToken(token).build();
            log.info("GitHub client initialized for token: " + token.substring(0, Math.min(10, token.length())) + "...");
        }
        return github;
    }

    private String getGitHubToken() {
        if (githubToken != null && !githubToken.isBlank()) {
            return githubToken;
        }

        Path tokenPath = Paths.get(githubTokenFile);
        if (Files.exists(tokenPath)) {
            try {
                String token = Files.readString(tokenPath).trim();
                log.debug("Loaded GitHub token from file: " + githubTokenFile);
                return token;
            } catch (IOException e) {
                log.error("Failed to read GitHub token from file: " + githubTokenFile, e);
            }
        }

        return null;
    }

    public GHRepository createRepository(String orgName, String repoName, String description) throws IOException {
        GitHub gh = getGitHub();
        GHOrganization org = gh.getOrganization(orgName);

        if (org == null) {
            throw new IllegalArgumentException("Organization not found: " + orgName);
        }

        GHRepository existingRepo = org.getRepository(repoName);
        if (existingRepo != null) {
            log.warn("Repository already exists: " + orgName + "/" + repoName);
            return existingRepo;
        }

        log.info("Creating repository: " + orgName + "/" + repoName);
        return org.createRepository(repoName)
            .description(description)
            .private_(false)
            .autoInit(false)
            .create();
    }

    public void configureRepositorySecrets(String orgName, String repoName, Map<String, String> secrets) throws IOException {
        GitHub gh = getGitHub();
        GHRepository repo = gh.getRepository(orgName + "/" + repoName);

        GHRepositoryPublicKey publicKey = repo.getPublicKey();
        String keyId = publicKey.getKeyId();
        String keyValue = publicKey.getKey();

        log.info("Configuring " + secrets.size() + " secrets for " + orgName + "/" + repoName);

        for (Map.Entry<String, String> entry : secrets.entrySet()) {
            String secretName = entry.getKey();
            String secretValue = entry.getValue();

            String encryptedValue = encryptSecret(secretValue, keyValue);

            repo.createSecret(secretName)
                .withKeyId(keyId)
                .withEncryptedValue(encryptedValue)
                .create();

            log.debug("Created secret: " + secretName);
        }
    }

    private String encryptSecret(String plaintext, String publicKeyBase64) {
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = new byte[Box.SEALBYTES + plaintextBytes.length];

        boolean success = sodium.cryptoBoxSealEasy(ciphertext, plaintextBytes, plaintextBytes.length, publicKeyBytes);

        if (!success) {
            throw new RuntimeException("Failed to encrypt secret with libsodium");
        }

        return Base64.getEncoder().encodeToString(ciphertext);
    }

    public void enableActions(String orgName, String repoName) throws IOException {
        GitHub gh = getGitHub();
        GHRepository repo = gh.getRepository(orgName + "/" + repoName);

        log.info("GitHub Actions are enabled by default for new repos: " + orgName + "/" + repoName);
    }

    public boolean verifyOrganizationMembership(String username, String orgName) throws IOException {
        GitHub gh = getGitHub();
        GHOrganization org = gh.getOrganization(orgName);

        if (org == null) {
            return false;
        }

        return org.hasMember(gh.getUser(username));
    }

    public GHRepository getRepository(String orgName, String repoName) throws IOException {
        GitHub gh = getGitHub();
        return gh.getRepository(orgName + "/" + repoName);
    }
}
