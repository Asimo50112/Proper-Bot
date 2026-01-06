package org.example;

import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class ERLCService {
    private final HttpClient client = HttpClient.newHttpClient();
    private static final String BASE_URL = "https://api.policeroleplay.community/v1";

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
                    if (res.statusCode() == 200) return res.body();
                    // Return error codes as plain text for the handler to catch
                    return "ERROR:" + res.statusCode();
                });
    }

    public CompletableFuture<String> postCommand(String key, String command) {
        return sendRequest(key, "/server/command", "POST", "{\"command\": \"" + command + "\"}");
    }

    public CompletableFuture<String> getStatus(String key) {
        return sendRequest(key, "/server", "GET", null);
    }
    
    // ... add your other get methods here following the same pattern
}

        if (method.equals("POST")) {
            builder.header("Content-Type", "application/json")
                   .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
        } else {
            builder.GET();
        }

        return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    int status = res.statusCode();
                    String body = res.body();

                    // If successful, try to parse out the "message" value
                    if (status == 200) {
                        if (body == null || body.isEmpty() || body.equals("[]")) return "Request successful.";
                        return formatResponse(body);
                    }

                    return switch (status) {
                        case 400 -> "Bad Request: Check your command syntax.";
                        case 403 -> "Unauthorized: The API key provided is invalid.";
                        case 422 -> "Unprocessable Content: The private server has no players in it.";
                        case 429 -> "Rate Limited: You are sending requests too fast.";
                        case 500 -> "Internal Server Error: Problem communicating with Roblox.";
                        default -> "Unexpected Error: HTTP " + status;
                    };
                });
    }

    /**
     * Cleans up JSON responses.
     * Extracts "message" content or keeps raw JSON for logs/lists.
     */
    private String formatResponse(String body) {
        // If it's a simple message response: {"message":"Success"}
        if (body.contains("\"message\":\"")) {
            return body.split("\"message\":\"")[1].split("\"")[0];
        }
        // Return raw body for complex data (lists/logs) to be handled by Discord code blocks
        return body;
    }

    // --- Validation ---
    public CompletableFuture<Boolean> verifyKey(String apiKey) {
        return sendRequest(apiKey, "/server", "GET", null)
                .thenApply(res -> !res.contains("Unauthorized"));
    }

    // --- Commands ---
    public CompletableFuture<String> postCommand(String key, String command) {
        String payload = "{\"command\": \"" + command + "\"}";
        return sendRequest(key, "/server/command", "POST", payload);
    }

    // --- Server Information & Logs ---
    public CompletableFuture<String> getStatus(String key) { return sendRequest(key, "/server", "GET", null); }
    public CompletableFuture<String> getPlayers(String key) { return sendRequest(key, "/server/players", "GET", null); }
    public CompletableFuture<String> getStaff(String key) { return sendRequest(key, "/server/staff", "GET", null); }
    public CompletableFuture<String> getVehicles(String key) { return sendRequest(key, "/server/vehicles", "GET", null); }
    public CompletableFuture<String> getQueue(String key) { return sendRequest(key, "/server/queue", "GET", null); }
    public CompletableFuture<String> getBans(String key) { return sendRequest(key, "/server/bans", "GET", null); }
    public CompletableFuture<String> getJoinLogs(String key) { return sendRequest(key, "/server/joinlogs", "GET", null); }
    public CompletableFuture<String> getKillLogs(String key) { return sendRequest(key, "/server/killlogs", "GET", null); }
    public CompletableFuture<String> getCommandLogs(String key) { return sendRequest(key, "/server/commandlogs", "GET", null); }
    public CompletableFuture<String> getModCalls(String key) { return sendRequest(key, "/server/modcalls", "GET", null); }
}
