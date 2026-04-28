package p1.config.runtime;

public record RuntimeModelSettings(String aiBaseUrl,
                                   String aiApiKey,
                                   String aiModelName,
                                   String embeddingBaseUrl,
                                   String embeddingApiKey,
                                   String embeddingModelName) {
}
