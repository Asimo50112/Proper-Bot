package org.example;

import net.dv8tion.jda.api.Permission;
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
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ERLCVehicleGuard extends ListenerAdapter {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static final String FILE = "guild-keys.properties";

    public static CommandData getCommandData() {
        return Commands.slash("vehicle-restrictions", "Manage car restrictions")
                .addSubcommands(
                    new SubcommandData("add", "Restrict a car to a specific role")
                        .addOption(OptionType.STRING, "carname", "Exact name from PRC (e.g. 2019 Falcon Interceptor Utility)", true)
                        .addOption(OptionType.ROLE, "role", "Role allowed to drive this car", true),
                    new SubcommandData("remove", "Remove restriction from a car")
                        .addOption(OptionType.STRING, "carname", "Car name to unrestrict", true),
                    new SubcommandData("list", "List all restricted cars and their roles"),
                    new SubcommandData("scan", "Check server for unauthorized drivers")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("vehicle-restrictions") || event.getGuild() == null) return;
        
        String sub = event.getSubcommandName();
        String guildId = event.getGuild().getId();

        switch (sub) {
            case "add" -> {
                String car = event.getOption("carname").getAsString();
                String roleId = event.getOption("role").getAsRole().getId();
                saveProperty(guildId + "_v_role_" + car, roleId);
                event.reply("Restriction saved: **" + car + "** now requires the specified role.").setEphemeral(true).queue();
            }
            case "remove" -> {
                String car = event.getOption("carname").getAsString();
                removeProperty(guildId + "_v_role_" + car);
                event.reply("Restriction removed for **" + car + "**.").setEphemeral(true).queue();
            }
            case "list" -> handleList(event, guildId);
            case "scan" -> handleScan(event, guildId);
        }
    }

    private void handleScan(SlashCommandInteractionEvent event, String guildId) {
        event.deferReply().queue();
        String apiKey = getProperty(guildId);
        if (apiKey == null) {
            event.getHook().sendMessage("Error: API Key not set. Use `/erlc-apikey`.").queue();
            return;
        }

        // 1. Fetch Spawned Vehicles (GET /v1/server/vehicles)
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.policeroleplay.community/v1/server/vehicles"))
                .header("server-key", apiKey).GET().build();

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
            try {
                JSONArray vehicles = new JSONArray(res.body());
                int violations = 0;

                for (int i = 0; i < vehicles.length(); i++) {
                    JSONObject v = vehicles.getJSONObject(i);
                    String carName = v.getString("Name");
                    String robloxOwner = v.getString("Owner"); // As per docs: "flat_bird"

                    String requiredRoleId = getProperty(guildId + "_v_role_" + carName);
                    
                    if (requiredRoleId != null) {
                        // 2. Check if the driver is authorized via Discord Role
                        if (!isAuthorized(event, robloxOwner, requiredRoleId)) {
                            executePenalty(apiKey, robloxOwner, carName);
                            violations++;
                        }
                    }
                }
                event.getHook().sendMessage("Scan complete. Processed **" + violations + "** unauthorized drivers.").queue();
            } catch (Exception e) {
                event.getHook().sendMessage("Error: Failed to parse PRC vehicle data.").queue();
            }
        });
    }

    private boolean isAuthorized(SlashCommandInteractionEvent event, String robloxName, String roleId) {
        // Logic: Find a Discord member whose nickname or username matches the Roblox owner
        return event.getGuild().getMembers().stream()
                .anyMatch(m -> (m.getEffectiveName().equalsIgnoreCase(robloxName)) && 
                               m.getRoles().stream().anyMatch(r -> r.getId().equals(roleId)));
    }

    private void executePenalty(String apiKey, String username, String carName) {
        // Immediate Load
        sendPrcCommand(apiKey, ":load " + username);

        // Delayed PM (10 seconds)
        scheduler.schedule(() -> {
            sendPrcCommand(apiKey, ":pm " + username + " You are not authorized to use the " + carName + ".");
        }, 10, TimeUnit.SECONDS);
    }

    private void sendPrcCommand(String apiKey, String cmd) {
        JSONObject body = new JSONObject().put("command", cmd);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.policeroleplay.community/v1/server/command"))
                .header("server-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        client.sendAsync(req, HttpResponse.BodyHandlers.ofString());
    }

    private void handleList(SlashCommandInteractionEvent event, String guildId) {
        Properties p = new Properties();
        StringBuilder sb = new StringBuilder("### Current Vehicle Restrictions:\n");
        try (InputStream i = new FileInputStream(FILE)) {
            p.load(i);
            p.forEach((k, v) -> {
                if (k.toString().startsWith(guildId + "_v_role_")) {
                    String car = k.toString().replace(guildId + "_v_role_", "");
                    sb.append("• **").append(car).append("**: <@&").append(v).append(">\n");
                }
            });
            event.reply(sb.toString()).setEphemeral(true).queue();
        } catch (IOException e) { event.reply("No restrictions found.").setEphemeral(true).queue(); }
    }

    // --- Persistence ---
    private void saveProperty(String key, String val) {
        Properties p = new Properties();
        try {
            File f = new File(FILE);
            if (f.exists()) try (InputStream i = new FileInputStream(f)) { p.load(i); }
            p.setProperty(key, val);
            try (OutputStream o = new FileOutputStream(f)) { p.store(o, null); }
        } catch (IOException ignored) {}
    }

    private void removeProperty(String key) {
        Properties p = new Properties();
        try {
            File f = new File(FILE);
            if (f.exists()) {
                try (InputStream i = new FileInputStream(f)) { p.load(i); }
                p.remove(key);
                try (OutputStream o = new FileOutputStream(f)) { p.store(o, null); }
            }
        } catch (IOException ignored) {}
    }

    private String getProperty(String key) {
        Properties p = new Properties();
        try (InputStream i = new FileInputStream(FILE)) {
            p.load(i); return p.getProperty(key);
        } catch (IOException e) { return null; }
    }
}
