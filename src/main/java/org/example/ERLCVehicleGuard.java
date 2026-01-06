package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
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
    private static final String KEY_FILE = "guild-keys.properties";
    private static final String VEHICLE_URL = "https://api.policeroleplay.community/v1/server/vehicles";
    private static final String CMD_URL = "https://api.policeroleplay.community/v1/server/command";

    public ERLCVehicleGuard(JDA jda) {
        this.jda = jda;
        // Ensure the config file exists on startup
        File f = new File(KEY_FILE);
        try { if (f.createNewFile()) System.out.println("Created " + KEY_FILE); } catch (IOException ignored) {}

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // Run every 20 seconds
        scheduler.scheduleAtFixedRate(this::checkVehicles, 10, 20, TimeUnit.SECONDS);
    }

    // FIX: Static method for Main.java to call
    public static CommandData getCommandData() {
        return Commands.slash("vehicle-restrictions", "Manage car permissions")
                .addSubcommands(
                        new SubcommandData("add", "Restrict car to a role")
                                .addOption(OptionType.STRING, "carname", "Exact PRC Car Name", true)
                                .addOption(OptionType.ROLE, "role", "Authorized Role", true),
                        new SubcommandData("scan", "Manual scan trigger")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("vehicle-restrictions") || event.getGuild() == null) return;

        if ("add".equals(event.getSubcommandName())) {
            String car = event.getOption("carname").getAsString();
            String roleId = event.getOption("role").getAsRole().getId();
            saveProperty(event.getGuild().getId() + "_v_role_" + car, roleId);
            event.reply("Restriction set: **" + car + "** now requires role <@&" + roleId + ">").setEphemeral(true).queue();
        } else if ("scan".equals(event.getSubcommandName())) {
            event.reply("Manual scan started.").setEphemeral(true).queue();
            checkVehicles();
        }
    }

    private void checkVehicles() {
        Properties config = loadProperties(KEY_FILE);
        for (Object keyObj : config.keySet()) {
            String guildId = (String) keyObj;
            if (guildId.matches("\\d+")) { // If key is a Guild ID
                String apiKey = config.getProperty(guildId);
                scanServer(apiKey, guildId, config);
            }
        }
    }

    private void scanServer(String apiKey, String guildId, Properties config) {
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
                    processViolation(guildId, apiKey, robloxOwner, carName, roleId);
                }
            }
        });
    }

    private void processViolation(String guildId, String apiKey, String robloxOwner, String carName, String roleId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;

        // Try to find the member by matching Discord Nickname to Roblox Username
        guild.retrieveMembersByPrefix(robloxOwner, 10).onSuccess(members -> {
            boolean isAuthorized = false;
            for (Member m : members) {
                if (m.getEffectiveName().equalsIgnoreCase(robloxOwner)) {
                    isAuthorized = m.getRoles().stream().anyMatch(r -> r.getId().equals(roleId));
                    break;
                }
            }

            if (!isAuthorized) {
                System.out.println("[Guard] VIOLATION: " + robloxOwner + " in " + carName);
                executeChain(apiKey, robloxOwner, carName);
            }
        });
    }

    private void executeChain(String apiKey, String username, String carName) {
        // Immediate Load
        sendCommand(apiKey, ":load " + username);
        // 10 Second delayed PM
        CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> {
            sendCommand(apiKey, ":pm " + username + " The " + carName + " is a restricted vehicle. You have been loaded.");
        });
    }

    private void sendCommand(String apiKey, String cmd) {
        JSONObject body = new JSONObject().put("command", cmd);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(CMD_URL))
                .header("server-key", apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
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
        if (f.exists()) { try (InputStream i = new FileInputStream(f)) { p.load(i); } catch (IOException ignored) {} }
        return p;
    }
}
