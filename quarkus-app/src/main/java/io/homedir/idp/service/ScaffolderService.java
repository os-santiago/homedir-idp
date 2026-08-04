package io.homedir.idp.service;

import io.homedir.idp.model.Template;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class ScaffolderService {

    private static final Logger log = Logger.getLogger(ScaffolderService.class);

    @ConfigProperty(name = "idp.scaffold.temp.dir")
    String scaffoldTempDir;

    public Path scaffoldProject(Template template, String projectName, Map<String, String> placeholders) throws IOException {
        Path workDir = Paths.get(scaffoldTempDir, projectName);

        if (Files.exists(workDir)) {
            log.warn("Scaffold directory already exists, cleaning: " + workDir);
            deleteDirectory(workDir);
        }

        Files.createDirectories(workDir);
        log.info("Created scaffold directory: " + workDir);

        cloneTemplate(template.getSourceRepo(), template.getSourceBranch(), workDir);

        customizeFiles(workDir, template.filesToCustomize(), placeholders);

        log.info("Scaffolding completed: " + workDir);
        return workDir;
    }

    private void cloneTemplate(String repoUrl, String branch, Path targetDir) throws IOException {
        log.info("Cloning template: " + repoUrl + " (branch: " + branch + ")");

        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("clone");
        command.add("--depth");
        command.add("1");
        command.add("--branch");
        command.add(branch);
        command.add(repoUrl);
        command.add(targetDir.toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("git: " + line);
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Git clone failed with exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git clone interrupted", e);
        }

        Path gitDir = targetDir.resolve(".git");
        if (Files.exists(gitDir)) {
            deleteDirectory(gitDir);
            log.debug("Removed .git directory");
        }
    }

    private void customizeFiles(Path projectDir, List<String> filesToCustomize, Map<String, String> placeholders) throws IOException {
        if (filesToCustomize == null || filesToCustomize.isEmpty()) {
            log.warn("No files to customize");
            return;
        }

        log.info("Customizing " + filesToCustomize.size() + " files with " + placeholders.size() + " placeholders");

        for (String relativePath : filesToCustomize) {
            Path filePath = projectDir.resolve(relativePath);

            if (!Files.exists(filePath)) {
                log.warn("File to customize not found, skipping: " + relativePath);
                continue;
            }

            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            String originalContent = content;

            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String placeholder = entry.getKey();
                String value = entry.getValue();
                content = content.replace(placeholder, value);
            }

            if (!content.equals(originalContent)) {
                Files.writeString(filePath, content, StandardCharsets.UTF_8);
                log.debug("Customized: " + relativePath);
            } else {
                log.debug("No changes needed: " + relativePath);
            }
        }
    }

    public void initGitRepository(Path projectDir, String repoUrl) throws IOException {
        log.info("Initializing git repository: " + projectDir);

        execGit(projectDir, "init");
        execGit(projectDir, "config", "user.name", "Homedir IDP");
        execGit(projectDir, "config", "user.email", "idp@opensourcesantiago.io");
        execGit(projectDir, "remote", "add", "origin", repoUrl);
        execGit(projectDir, "add", ".");
        execGit(projectDir, "commit", "-m", "Initial commit from Homedir IDP template");
        log.info("Git repository initialized");
    }

    public void pushToGitHub(Path projectDir, String branch) throws IOException {
        log.info("Pushing to GitHub: branch=" + branch);
        execGit(projectDir, "push", "-u", "origin", branch);
        log.info("Push completed");
    }

    private void execGit(Path workDir, String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        for (String arg : args) {
            command.add(arg);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("git: " + line);
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Git command failed with exit code " + exitCode + ": " + String.join(" ", command) + "\nOutput: " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git command interrupted", e);
        }
    }

    public void cleanup(Path projectDir) {
        try {
            if (Files.exists(projectDir)) {
                deleteDirectory(projectDir);
                log.info("Cleaned up scaffold directory: " + projectDir);
            }
        } catch (IOException e) {
            log.error("Failed to cleanup scaffold directory: " + projectDir, e);
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walk(directory)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    log.warn("Failed to delete: " + path, e);
                }
            });
    }
}
