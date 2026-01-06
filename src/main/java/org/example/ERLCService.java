package org.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class ERLCService {
    private final HttpClient client;
    private static final String BASE_URL = "https://api.policeroleplay.community/v1";

    public ERLCService() {
        this.client = HttpClient.newHttpClient();
    }

    /**
     * The core engine that processes all API requests.
     * It handles the authentication header and parses status codes.
     */
    private CompletableFuture<String> sendRequest(String apiKey, String endpoint, String method, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("server-key", apiKey)
                .header("Accept", "application/json");

        if (method.equals("POST")) {
            builder.header("Content-Type", "application/json")
                   .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
        } else {
            builder.GET();
        }

        return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    int status = res.statusCode();
                    return switch (status) {
                        case 200 -> res.body().isEmpty() ? "Request successful." : res.body();
                        case 400 -> "Bad Request: Check your command syntax.";
                        case 403 -> "Unauthorized: The API key provided is invalid.";
                        case 422 -> "Unprocessable Content: The server must have players in it to use this endpoint.";
                        case 429 -> "Rate Limited: You are sending requests too fast.";
                        case 500 -> "Internal Server Error: Problem communicating with Roblox.";
                        default -> "Unexpected Error: HTTP " + status;
                    };
                });
    }

    // --- Validation ---
    public CompletableFuture<Boolean> verifyKey(String apiKey) {
        // Attempts to fetch server status as a handshake test
        return sendRequest(apiKey, "/server", "GET", null)
                .thenApply(res -> !res.contains("Unauthorized"));
    }

    // --- Commands (The /c command logic) ---
    public CompletableFuture<String> postCommand(String key, String command) {
        String payload = "{\"command\": \"" + command + "\"}";
        return sendRequest(key, "/server/command", "POST", payload);
    }

    // --- Server Information ---
    public CompletableFuture<String> getStatus(String key) {
        return sendRequest(key, "/server", "GET", null);
    }

    public CompletableFuture<String> getPlayers(String key) {
        return sendRequest(key, "/server/players", "GET", null);
    }

    public CompletableFuture<String> getStaff(String key) {
        return sendRequest(key, "/server/staff", "GET", null);
    }

    public CompletableFuture<String> getVehicles(String key) {
        return sendRequest(key, "/server/vehicles", "GET", null);
    }

    public CompletableFuture<String> getQueue(String key) {
        return sendRequest(key, "/server/queue", "GET", null);
    }

    public CompletableFuture<String> getBans(String key) {
        return sendRequest(key, "/server/bans", "GET", null);
    }

    // --- Logs ---
    public CompletableFuture<String> getJoinLogs(String key) {
        return sendRequest(key, "/server/joinlogs", "GET", null);
    }

    public CompletableFuture<String> getKillLogs(String key) {
        return sendRequest(key, "/server/killlogs", "GET", null);
    }

    public CompletableFuture<String> getCommandLogs(String key) {
        return sendRequest(key, "/server/commandlogs", "GET", null);
    }

    public CompletableFuture<String> getModCalls(String key) {
        return sendRequest(key, "/server/modcalls", "GET", null);
    }
}
