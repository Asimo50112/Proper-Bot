package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
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

public class ERLCVehicleGuard extends ListenerAdapter {
    private final JDA jda;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
    private static final String VEHICLE_URL = "https://api.policeroleplay.community/v1/server/vehicles";
    private static final String CMD_URL = "https://api.policeroleplay.community/v1/server/command";
    private static final String KEY_FILE = "guild-keys.properties";
    private static final String LINK_FILE = "linked_accounts.properties";

    public ERLCVehicleGuard(JDA jda) {
        this.jda = jda;
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkVehicles, 10, 20, TimeUnit.SECONDS);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("vehicle-restrictions") || event.getGuild() == null) return;

        if ("add".equals(event.getSubcommandName())) {
            String car = event.getOption("carname").getAsString();
            String roleId = event.getOption("role").getAsRole().getId();
            saveProperty(event.getGuild().getId() + "_v_role_" + car, roleId);
            event.reply("Restriction set for **" + car + "**.").setEphemeral(true).queue();
        } else if ("scan".equals(event.getSubcommandName())) {
            event.reply("Manual scan triggered.").setEphemeral(true).queue();
            checkVehicles();
        }
    }

    private void checkVehicles() {
        Properties config = loadProperties(KEY_FILE);
        Properties linkedUsers = loadProperties(LINK_FILE);

        for (Object keyObj : config.keySet()) {
            String key = (String) keyObj;
            if (key.matches("\\d+")) { // Only Guild IDs
                String apiKey = config.getProperty(key);
                scanServer(apiKey, key, config, linkedUsers);
            }
        }
    }

    private void scanServer(String apiKey, String guildId, Properties config, Properties linkedUsers) {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(VEHICLE_URL)).header("server-key", apiKey).GET().build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
            if (res.statusCode() != 200) return;
            JSONArray vehicles = new JSONArray(res.body());

            for (int i = 0; i < vehicles.length(); i++) {
                JSONObject v = vehicles.getJSONObject(i);
                String carName = v.getString("Name");
                String robloxOwner = v.getString("Owner");

                String roleId = config.getProperty(guildId + "_v_role_" + carName);
                if (roleId != null) {
                    System.out.println("[Guard] Restricted car spotted: " + carName + " owned by " + robloxOwner);
                    processViolation(guildId, apiKey, robloxOwner, carName, roleId, linkedUsers);
                }
            }
        });
    }

    private void processViolation(String guildId, String apiKey, String robloxOwner, String carName, String roleId, Properties linkedUsers) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;

        String discordId = linkedUsers.getProperty(robloxOwner);
        if (discordId == null) {
            System.out.println("[Guard] VIOLATION: " + robloxOwner + " is unlinked. Actioning...");
            executeChain(apiKey, robloxOwner, carName, "Verification Required");
            return;
        }

        guild.retrieveMemberById(discordId).queue(member -> {
            boolean hasRole = member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId));
            if (!hasRole) {
                System.out.println("[Guard] VIOLATION: " + robloxOwner + " lacks the role. Actioning...");
                executeChain(apiKey, robloxOwner, carName, guild.getRoleById(roleId).getName());
            } else {
                System.out.println("[Guard] AUTHORIZED: " + robloxOwner + " is allowed to drive " + carName);
            }
        }, err -> {
            System.out.println("[Guard] ERROR: Linked user " + robloxOwner + " not found in Discord.");
            executeChain(apiKey, robloxOwner, carName, "Discord Member Not Found");
        });
    }

    private void executeChain(String apiKey, String username, String carName, String reason) {
        sendCommand(apiKey, ":load " + username);
        CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> {
            sendCommand(apiKey, ":pm " + username + " Vehicle restricted. Role required: [" + reason + "]");
        });
    }

    private void sendCommand(String apiKey, String cmd) {
        JSONObject body = new JSONObject().put("command", cmd);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(CMD_URL)).header("server-key", apiKey)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        client.sendAsync(req, HttpResponse.BodyHandlers.discarding());
    }

    private void saveProperty(String key, String val) {
        Properties p = loadProperties(KEY_FILE);
        p.setProperty(key, val);
        try (OutputStream o = new FileOutputStream(KEY_FILE)) { p.store(o, null); } catch (IOException ignored) {}
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
