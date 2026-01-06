package org.example;

import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class ERLCService {
    private final HttpClient client = HttpClient.newHttpClient();
    private static final String PRC_BASE_URL = "https://api.policeroleplay.community/v1";
    private static final String ROBLOX_USERS_URL = "https://users.roblox.com/v1/users/";

    private CompletableFuture<String> sendRequest(String apiKey, String endpoint, String method, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(PRC_BASE_URL + endpoint))
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
                    if (status == 200) {
                        return (res.body() == null || res.body().isEmpty()) ? "{\"message\":\"Success\"}" : res.body();
                    }
                    return switch (status) {
                        case 400 -> "ERROR: Bad Request.";
                        case 403 -> "ERROR: Unauthorized.";
                        case 422 -> "ERROR: Server is empty.";
                        case 500 -> "ERROR: Roblox communication failure.";
                        default -> "ERROR: Status " + status;
                    };
                });
    }

    public CompletableFuture<String> getRobloxProfileLink(long userId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ROBLOX_USERS_URL + userId))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() == 200) {
                        String name = new JSONObject(res.body()).getString("name");
                        return String.format("[%s](https://www.roblox.com/users/%d/profile)", name, userId);
                    }
                    return "Unknown (" + userId + ")";
                });
    }

    public CompletableFuture<Boolean> verifyKey(String apiKey) {
        return sendRequest(apiKey, "/server", "GET", null)
                .thenApply(res -> !res.startsWith("ERROR"));
    }

    public CompletableFuture<String> postCommand(String key, String command) {
        return sendRequest(key, "/server/command", "POST", "{\"command\": \"" + command + "\"}");
    }

    public CompletableFuture<String> getStatus(String key) { return sendRequest(key, "/server", "GET", null); }
    public CompletableFuture<String> getPlayers(String key) { return sendRequest(key, "/server/players", "GET", null); }
    public CompletableFuture<String> getKillLogs(String key) { return sendRequest(key, "/server/killlogs", "GET", null); }
    public CompletableFuture<String> getVehicles(String key) { return sendRequest(key, "/server/vehicles", "GET", null); }
}
