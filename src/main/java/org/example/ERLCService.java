package org.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class ERLCService {
    private static final String BASE_URL = "https://api.policeroleplay.community/v1";
    private final HttpClient client = HttpClient.newHttpClient();

    // Sends a command to the ER:LC server
    public CompletableFuture<String> sendCommand(String apiKey, String command) {
        String jsonBody = "{\"command\": \"" + command + "\"}";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/server/command"))
                .header("server-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) return "Success!";
                    if (response.statusCode() == 403) return "Error: Invalid API Key.";
                    return "Error: " + response.statusCode();
                });
    }
}
