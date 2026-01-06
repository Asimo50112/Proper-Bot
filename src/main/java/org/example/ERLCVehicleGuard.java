package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ERLCVehicleGuard {
    private final JDA jda;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    private static final String VEHICLE_URL = "https://api.policeroleplay.community/v1/server/vehicles";
    private static final String CMD_URL = "https://api.policeroleplay.community/v1/server/command";
    private static final String KEY_FILE = "guild-keys.properties";
    private static final String LINK_FILE = "linked_accounts.properties";

    public ERLCVehicleGuard(JDA jda) {
        this.jda = jda;
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // Fixed 20s interval as requested
        scheduler.scheduleAtFixedRate(this::checkVehicles, 10, 20, TimeUnit.SECONDS);
    }

    private void checkVehicles() {
        Properties config = loadProperties(FILE);
        Properties linkedUsers = loadProperties(LINK_FILE);

        for (String guildId : config.stringPropertyNames()) {
            if (!guildId.matches("\\d+")) continue;

            String apiKey = config.getProperty(guildId);
            // Restriction keys follow: guildId_v_role_CarName
            // We loop through to find relevant restrictions for this guild
            scanServer(apiKey, guildId, config, linkedUsers);
        }
    }

    private void scanServer(String apiKey, String guildId, Properties config, Properties linkedUsers) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(VEHICLE_URL))
                .header("server-key", apiKey).GET().build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
            if (res.statusCode() != 200) return;

            JSONArray vehicles = new JSONArray(res.body());
            for (int i = 0; i < vehicles.length(); i++) {
                JSONObject v = vehicles.getJSONObject(i);
                String carName = v.getString("Name");
                String robloxOwner = v.getString("Owner");

                // Check if this car is restricted in our properties
                String roleId = config.getProperty(guildId + "_v_role_" + carName);
                if (roleId != null) {
                    processViolation(guildId, apiKey, robloxOwner, carName, roleId, linkedUsers);
                }
            }
        });
    }

    private void processViolation(String guildId, String apiKey, String robloxOwner, String carName, String roleId, Properties linkedUsers) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;

        // Try to find linked Discord ID
        String discordId = linkedUsers.getProperty(robloxOwner);
        
        if (discordId == null) {
            // No link? Action immediately as unauthorized
            executeChain(apiKey, robloxOwner, carName, "Unlinked Account");
            return;
        }

        // Retrieve member asynchronously (Fixes 'cannot find symbol' error)
        guild.retrieveMemberById(discordId).queue(member -> {
            boolean hasRole = member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId));
            if (!hasRole) {
                executeChain(apiKey, robloxOwner, carName, guild.getRoleById(roleId).getName());
            }
        }, err -> executeChain(apiKey, robloxOwner, carName, "Missing Discord Data"));
    }

    private void executeChain(String apiKey, String username, String carName, String requiredRole) {
        // 1. Immediate Load
        sendCommand(apiKey, ":load " + username);

        // 2. Scheduled PM (Exactly 10 seconds later)
        CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> {
            String msg = ":pm " + username + " You were loaded for driving the " + carName + ". Role required: [" + requiredRole + "]";
            sendCommand(apiKey, msg);
        });
    }

    private void sendCommand(String apiKey, String cmd) {
        JSONObject body = new JSONObject().put("command", cmd);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(CMD_URL))
                .header("server-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        client.sendAsync(req, HttpResponse.BodyHandlers.discarding());
    }

    private Properties loadProperties(String name) {
        Properties p = new Properties();
        File f = new File(name);
        if (f.exists()) {
            try (InputStream i = new FileInputStream(f)) { p.load(i); } catch (IOException ignored) {}
        }
        return p;
    }
}
