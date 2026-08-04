package io.homedir.idp.service;

import jakarta.enterprise.context.ApplicationScoped;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class VpsProvisioningService {

    private static final Logger log = Logger.getLogger(VpsProvisioningService.class);

    @ConfigProperty(name = "idp.vps.host")
    String vpsHost;

    @ConfigProperty(name = "idp.vps.user")
    String vpsUser;

    @ConfigProperty(name = "idp.vps.ssh.key.path")
    String sshKeyPath;

    @ConfigProperty(name = "idp.vps.nginx.sites.path")
    String nginxSitesPath;

    @ConfigProperty(name = "idp.vps.systemd.path")
    String systemdPath;

    public void provisionNginx(String subdomain, String domain, int containerPort) throws IOException {
        log.info("Provisioning nginx for: " + subdomain + "." + domain);

        String nginxConfig = generateNginxConfig(subdomain, domain, containerPort);
        String configPath = nginxSitesPath + "/" + subdomain;

        try (SSHClient ssh = connectSSH()) {
            execCommand(ssh, "mkdir -p " + nginxSitesPath);

            String uploadCommand = String.format(
                "cat > %s <<'NGINX_EOF'\n%s\nNGINX_EOF",
                configPath, nginxConfig
            );
            execCommand(ssh, uploadCommand);

            execCommand(ssh, "ln -sf " + configPath + " /etc/nginx/sites-enabled/" + subdomain);

            execCommand(ssh, "nginx -t");
            execCommand(ssh, "systemctl reload nginx");

            log.info("Nginx configured successfully");
        }
    }

    public void provisionSSL(String subdomain, String domain) throws IOException {
        log.info("Provisioning SSL certificate for: " + subdomain + "." + domain);

        String fullDomain = subdomain + "." + domain;

        try (SSHClient ssh = connectSSH()) {
            String certbotCommand = String.format(
                "certbot --nginx -d %s --non-interactive --agree-tos --email admin@%s",
                fullDomain, domain
            );

            execCommand(ssh, certbotCommand);
            log.info("SSL certificate provisioned successfully");
        }
    }

    public void provisionSystemdService(String serviceName, String containerImage, int port) throws IOException {
        log.info("Provisioning systemd service: " + serviceName);

        String serviceContent = generateSystemdService(serviceName, containerImage, port);
        String servicePath = systemdPath + "/" + serviceName + ".service";

        try (SSHClient ssh = connectSSH()) {
            String uploadCommand = String.format(
                "cat > %s <<'SERVICE_EOF'\n%s\nSERVICE_EOF",
                servicePath, serviceContent
            );
            execCommand(ssh, uploadCommand);

            execCommand(ssh, "systemctl daemon-reload");
            execCommand(ssh, "systemctl enable " + serviceName);
            execCommand(ssh, "systemctl start " + serviceName);

            log.info("Systemd service provisioned successfully");
        }
    }

    public void createDirectories(String projectName) throws IOException {
        log.info("Creating project directories for: " + projectName);

        try (SSHClient ssh = connectSSH()) {
            execCommand(ssh, "mkdir -p /opt/" + projectName + "/data");
            execCommand(ssh, "chmod 755 /opt/" + projectName);
            log.info("Directories created successfully");
        }
    }

    public boolean healthCheck(String url) {
        log.info("Performing health check: " + url);

        for (int i = 0; i < 30; i++) {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();

                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

                java.net.http.HttpResponse<String> response = client.send(
                    request,
                    java.net.http.HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() == 200) {
                    log.info("Health check passed on attempt " + (i + 1));
                    return true;
                }

                log.debug("Health check attempt " + (i + 1) + " failed with status: " + response.statusCode());
            } catch (Exception e) {
                log.debug("Health check attempt " + (i + 1) + " failed: " + e.getMessage());
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.error("Health check failed after 30 attempts");
        return false;
    }

    private SSHClient connectSSH() throws IOException {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(vpsHost);

        KeyProvider keys = ssh.loadKeys(sshKeyPath);
        ssh.authPublickey(vpsUser, keys);

        log.debug("SSH connected to " + vpsHost);
        return ssh;
    }

    private String execCommand(SSHClient ssh, String command) throws IOException {
        try (Session session = ssh.startSession()) {
            Session.Command cmd = session.exec(command);
            String output = IOUtils.readFully(cmd.getInputStream()).toString();
            String error = IOUtils.readFully(cmd.getErrorStream()).toString();

            cmd.join(30, TimeUnit.SECONDS);

            int exitStatus = cmd.getExitStatus();

            if (exitStatus != 0) {
                log.error("Command failed [" + exitStatus + "]: " + command);
                log.error("Error: " + error);
                throw new IOException("Command failed with exit code " + exitStatus + ": " + error);
            }

            log.debug("Command succeeded: " + command);
            if (!output.isBlank()) {
                log.debug("Output: " + output);
            }

            return output;
        }
    }

    private String generateNginxConfig(String subdomain, String domain, int containerPort) {
        String fullDomain = subdomain + "." + domain;

        return String.format("""
            server {
                listen 80;
                listen [::]:80;
                server_name %s;

                location / {
                    proxy_pass http://127.0.0.1:%d;
                    proxy_set_header Host $host;
                    proxy_set_header X-Real-IP $remote_addr;
                    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                    proxy_set_header X-Forwarded-Proto $scheme;
                }
            }
            """, fullDomain, containerPort);
    }

    private String generateSystemdService(String serviceName, String containerImage, int port) {
        return String.format("""
            [Unit]
            Description=%s Podman Container
            After=network-online.target
            Wants=network-online.target

            [Service]
            Type=simple
            ExecStartPre=-/usr/bin/podman stop %s
            ExecStartPre=-/usr/bin/podman rm %s
            ExecStart=/usr/bin/podman run --rm --name %s -p %d:%d %s
            ExecStop=/usr/bin/podman stop %s
            Restart=always
            RestartSec=10

            [Install]
            WantedBy=multi-user.target
            """, serviceName, serviceName, serviceName, serviceName, port, port, containerImage, serviceName);
    }
}
