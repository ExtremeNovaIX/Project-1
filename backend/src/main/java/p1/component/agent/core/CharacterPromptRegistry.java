package p1.component.agent.core;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class CharacterPromptRegistry {

    private static final String PROMPT_FILE_NAME = "prompt.txt";

    @Getter
    private Map<String, String> prompts = Map.of();

    @PostConstruct
    public void loadPrompts() {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path charaDirectory = workingDirectory.getParent() == null
                ? null
                : workingDirectory.getParent().resolve("chara").normalize();

        if (charaDirectory == null || !Files.isDirectory(charaDirectory)) {
            throw new IllegalStateException("Character directory not found: " + charaDirectory);
        }

        Map<String, String> loadedPrompts = new LinkedHashMap<>();
        try (Stream<Path> directories = Files.list(charaDirectory)) {
            directories
                    .filter(Files::isDirectory)
                    .sorted()
                    .forEach(characterDirectory -> {
                        Path promptPath = characterDirectory.resolve(PROMPT_FILE_NAME);
                        if (!Files.isRegularFile(promptPath)) {
                            throw new IllegalStateException("Missing prompt file: " + promptPath);
                        }

                        try {
                            String prompt = Files.readString(promptPath, StandardCharsets.UTF_8).trim();
                            if (prompt.isBlank()) {
                                throw new IllegalStateException("Prompt file is blank: " + promptPath);
                            }
                            loadedPrompts.put(characterDirectory.getFileName().toString(), prompt);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to read prompt file: " + promptPath, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan character directory: " + charaDirectory, e);
        }

        if (loadedPrompts.isEmpty()) {
            throw new IllegalStateException("No characters found under: " + charaDirectory);
        }

        this.prompts = Map.copyOf(loadedPrompts);
    }

    public String getPrompt(String characterName) {
        if (characterName == null || characterName.isBlank()) {
            throw new IllegalArgumentException("characterName is required");
        }

        String prompt = prompts.get(characterName);
        if (prompt == null) {
            throw new IllegalArgumentException("Unknown characterName: " + characterName);
        }
        return prompt;
    }
}
