package org.example;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
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

    public ERLCVehicleGuard() {
        // --- THE AUTO-SCANNER ---
        // Initial delay 10s, then runs every 20 seconds
        scheduler.scheduleAtFixedRate(this::runGlobalScan, 10, 20, TimeUnit.SECONDS);
    }

    private void runGlobalScan() {
        // This will be triggered by JDA once the bot is ready. 
        // We use a helper in Main to pass the JDA instance if needed, 
        // or we can rely on the event guild context.
        System.out.println("[VehicleGuard] Starting automated 20-second scan...");
    }

    public static CommandData getCommandData() {
        return Commands.slash("vehicle-restrictions", "Manage car restrictions")
                .addSubcommands(
                    new SubcommandData("add", "Restrict a car to a role")
                        .addOption(OptionType.STRING, "carname", "Exact name from PRC", true)
                        .addOption(OptionType.ROLE, "role", "Authorized role", true),
                    new SubcommandData("list", "List restrictions"),
                    new SubcommandData("scan", "Manual trigger")
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
            event.reply("Restricted **" + car + "** to <@&" + roleId + ">.").setEphemeral(true).queue();
        } else if ("scan".equals(sub)) {
            performScan(event.getGuild(), event);
        }
    }

    public void performScan(Guild guild, SlashCommandInteractionEvent event) {
        String guildId = guild.getId();
        String apiKey = getProperty(guildId);
        if (apiKey == null) return;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.policeroleplay.community/v1/server/vehicles"))
                .header("server-key", apiKey).GET().build();

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
            if (res.statusCode() != 200) return;
            
            JSONArray vehicles = new JSONArray(res.body());
            for (int i = 0; i < vehicles.length(); i++) {
                JSONObject v = vehicles.getJSONObject(i);
                String carName = v.getString("Name");
                String robloxOwner = v.getString("Owner");

                String requiredRoleId = getProperty(guildId + "_v_role_" + carName);
                if (requiredRoleId != null) {
                    // Role Check: Does a member with this Roblox name have the Discord Role?
                    boolean authorized = guild.getMembers().stream()
                            .filter(m -> m.getEffectiveName().equalsIgnoreCase(robloxOwner))
                            .anyMatch(m -> m.getRoles().stream().anyMatch(r -> r.getId().equals(requiredRoleId)));

                    if (!authorized) {
                        executePenalty(apiKey, robloxOwner, carName);
                    }
                }
            }
            if (event != null) event.getHook().sendMessage("Scan complete.").queue();
        });
    }

    private void executePenalty(String apiKey, String username, String carName) {
        // 1. Immediate Load
        sendPrcCommand(apiKey, ":load " + username);
        // 2. PM after 10 seconds
        scheduler.schedule(() -> {
            sendPrcCommand(apiKey, ":pm " + username + " You were loaded. The " + carName + " is restricted.");
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

    private void saveProperty(String key, String val) {
        Properties p = new Properties();
        try {
            File f = new File(FILE);
            if (f.exists()) try (InputStream i = new FileInputStream(f)) { p.load(i); }
            p.setProperty(key, val);
            try (OutputStream o = new FileOutputStream(f)) { p.store(o, null); }
        } catch (IOException ignored) {}
    }

    private String getProperty(String key) {
        Properties p = new Properties();
        try (InputStream i = new FileInputStream(FILE)) {
            p.load(i); return p.getProperty(key);
        } catch (IOException e) { return null; }
    }
}
