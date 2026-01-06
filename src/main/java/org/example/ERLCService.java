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
     * Optimized request handler. 
     * Returns raw JSON on success (200) or a descriptive error string.
     */
    private CompletableFuture<String> sendRequest(String apiKey, String endpoint, String method, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("server-key", apiKey)
                .header("Accept", "application/json");

        if (method.equalsIgnoreCase("POST")) {
            builder.header("Content-Type", "application/json")
                   .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
        } else {
            builder.GET();
        }

        return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    int status = res.statusCode();
                    String body = res.body();

                    if (status == 200) {
                        // Return raw body so Handler can build Embeds from JSON
                        return (body == null || body.isEmpty()) ? "{\"message\":\"Success\"}" : body;
                    }

                    // Return formatted error messages for Discord
                    return switch (status) {
                        case 400 -> "ERROR: Bad Request. Check your command syntax.";
                        case 403 -> "ERROR: Unauthorized. The API key is invalid.";
                        case 422 -> "ERROR: Unprocessable Content. The server has no players online.";
                        case 429 -> "ERROR: Rate Limited. Slow down requests.";
                        case 500 -> "ERROR: Internal Server Error. Roblox communication failed.";
                        default -> "ERROR: Unexpected status " + status;
                    };
                });
    }

    // --- Validation ---
    public CompletableFuture<Boolean> verifyKey(String apiKey) {
        return sendRequest(apiKey, "/server", "GET", null)
                .thenApply(res -> !res.startsWith("ERROR"));
    }

    // --- Commands ---
    public CompletableFuture<String> postCommand(String key, String command) {
        String payload = "{\"command\": \"" + command + "\"}";
        return sendRequest(key, "/server/command", "POST", payload);
    }

    // --- Server Information & Logs (Returns JSON Strings) ---
    public CompletableFuture<String> getStatus(String key) { return sendRequest(key, "/server", "GET", null); }
    public CompletableFuture<String> getPlayers(String key) { return sendRequest(key, "/server/players", "GET", null); }
    public CompletableFuture<String> getStaff(String key) { return sendRequest(key, "/server/staff", "GET", null); }
    public CompletableFuture<String> getVehicles(String key) { return sendRequest(key, "/server/vehicles", "GET", null); }
    public CompletableFuture<String> getJoinLogs(String key) { return sendRequest(key, "/server/joinlogs", "GET", null); }
    public CompletableFuture<String> getKillLogs(String key) { return sendRequest(key, "/server/killlogs", "GET", null); }
}
