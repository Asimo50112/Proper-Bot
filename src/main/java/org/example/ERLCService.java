package org.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class ERLCService {
    private final HttpClient client = HttpClient.newHttpClient();
    private static final String BASE_URL = "https://api.policeroleplay.community/v1";

    /**
     * Checks if the API key is "real" by attempting to fetch server info.
     */
    public CompletableFuture<Boolean> isValidKey(String apiKey) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/server"))
                .header("server-key", apiKey)
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(res -> res.statusCode() == 200);
    }

    /**
     * Handles the heavy lifting of sending command data to PRC
     */
    public CompletableFuture<String> executeInGameCommand(String apiKey, String command) {
        String json = "{\"command\": \"" + command + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/server/command"))
                .header("server-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    return switch (res.statusCode()) {
                        case 200 -> "✅ Success: Command sent to game.";
                        case 403 -> "❌ Error: API Key is invalid or expired.";
                        case 422 -> "⚠️ Error: Private server must have players in it to run commands.";
                        case 429 -> "⏳ Error: You are being rate limited by PRC.";
                        default -> "❌ PRC API Error: " + res.statusCode();
                    };
                });
    }
}
