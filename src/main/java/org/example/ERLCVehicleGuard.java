package org.example;

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
                    new SubcommandData("add", "Restrict a car to a role")
                        .addOption(OptionType.STRING, "carname", "Exact name (use /status to check)", true)
                        .addOption(OptionType.ROLE, "role", "Role allowed to drive this", true),
                    new SubcommandData("list", "List all current restrictions"),
                    new SubcommandData("scan", "Manually trigger a scan")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("vehicle-restrictions") || event.getGuild() == null) return;
        String sub = event.getSubcommandName();

        if ("add".equals(sub)) {
            String car = event.getOption("carname").getAsString();
            String roleId = event.getOption("role").getAsRole().getId();
            saveProperty(event.getGuild().getId() + "_v_role_" + car, roleId);
            event.reply("Restriction set: **" + car + "** now requires <@&" + roleId + ">").setEphemeral(true).queue();
        } else if ("scan".equals(sub)) {
            event.reply("Scan triggered. Check console for debug logs.").setEphemeral(true).queue();
            performScan(event.getGuild());
        }
    }

    public void performScan(Guild guild) {
        String apiKey = getProperty(guild.getId());
        if (apiKey == null) return;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.policeroleplay.community/v1/server/vehicles"))
                .header("server-key", apiKey).GET().build();

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
            if (res.statusCode() != 200) {
                System.out.println("[Guard] API Error: " + res.statusCode());
                return;
            }

            JSONArray vehicles = new JSONArray(res.body());
            for (int i = 0; i < vehicles.length(); i++) {
                JSONObject v = vehicles.getJSONObject(i);
                String carName = v.getString("Name");
                String robloxOwner = v.getString("Owner");

                // Check if this specific car has a restriction
                String requiredRoleId = getProperty(guild.getId() + "_v_role_" + carName);
                
                if (requiredRoleId != null) {
                    System.out.println("[Guard] FOUND RESTRICTED CAR: " + carName + " driven by " + robloxOwner);
                    
                    // Match Roblox Name to Discord Member
                    Member target = guild.getMembers().stream()
                            .filter(m -> m.getEffectiveName().equalsIgnoreCase(robloxOwner))
                            .findFirst().orElse(null);

                    if (target == null) {
                        System.out.println("[Guard] VIOLATION: No Discord member found matching nickname '" + robloxOwner + "'");
                        executePenalty(apiKey, robloxOwner, carName);
                    } else if (target.getRoles().stream().noneMatch(r -> r.getId().equals(requiredRoleId))) {
                        System.out.println("[Guard] VIOLATION: " + robloxOwner + " found in Discord but lacks the role.");
                        executePenalty(apiKey, robloxOwner, carName);
                    } else {
                        System.out.println("[Guard] PASS: " + robloxOwner + " is authorized.");
                    }
                }
            }
        }).exceptionally(ex -> {
            System.out.println("[Guard] Network Error: " + ex.getMessage());
            return null;
        });
    }

    private void executePenalty(String apiKey, String username, String carName) {
        // Immediate Load
        sendPrcCommand(apiKey, ":load " + username);
        
        // 10-second delay for PM
        scheduler.schedule(() -> {
            sendPrcCommand(apiKey, ":pm " + username + " You were loaded for driving a restricted car: " + carName);
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

    // --- Property Management ---
    private synchronized void saveProperty(String key, String val) {
        Properties p = new Properties();
        File f = new File(FILE);
        try {
            if (f.exists()) {
                try (InputStream i = new FileInputStream(f)) { p.load(i); }
            }
            p.setProperty(key, val);
            try (OutputStream o = new FileOutputStream(f)) { p.store(o, null); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private synchronized String getProperty(String key) {
        Properties p = new Properties();
        try (InputStream i = new FileInputStream(FILE)) {
            p.load(i); return p.getProperty(key);
        } catch (Exception e) { return null; }
    }
}
