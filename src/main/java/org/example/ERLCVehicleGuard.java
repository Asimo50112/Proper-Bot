package org.example;

import net.dv8tion.jda.api.Permission;
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
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final String RESTRICTION_KEY = "_restricted_cars";

    public static CommandData getCommandData() {
        return Commands.slash("vehicle-restrictions", "Manage car restrictions and scans")
                .addSubcommands(
                    new SubcommandData("add", "Add a car to the restricted list").addOption(OptionType.STRING, "carname", "Exact name of the car", true),
                    new SubcommandData("remove", "Remove a car from the list").addOption(OptionType.STRING, "carname", "Exact name of the car", true),
                    new SubcommandData("list", "List all restricted cars"),
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
                String current = getProperty(guildId + RESTRICTION_KEY, "");
                saveProperty(guildId + RESTRICTION_KEY, current + car + ",");
                event.reply("Added **" + car + "** to restrictions.").setEphemeral(true).queue();
            }
            case "remove" -> {
                String car = event.getOption("carname").getAsString();
                String current = getProperty(guildId + RESTRICTION_KEY, "");
                saveProperty(guildId + RESTRICTION_KEY, current.replace(car + ",", ""));
                event.reply("Removed **" + car + "** from restrictions.").setEphemeral(true).queue();
            }
            case "list" -> {
                String list = getProperty(guildId + RESTRICTION_KEY, "None");
                event.reply("### Restricted Cars:\n" + list.replace(",", "\n")).setEphemeral(true).queue();
            }
            case "scan" -> handleScan(event, guildId);
        }
    }

    private void handleScan(SlashCommandInteractionEvent event, String guildId) {
        event.deferReply().queue();
        String apiKey = getProperty(guildId, null);
        String restrictedRaw = getProperty(guildId + RESTRICTION_KEY, "");

        if (apiKey == null || restrictedRaw.isEmpty()) {
            event.getHook().sendMessage("API Key not set or no cars restricted.").queue();
            return;
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.policeroleplay.community/v1/server/vehicles"))
                .header("server-key", apiKey).GET().build();

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
            JSONArray vehicles = new JSONArray(res.body());
            int violations = 0;

            for (int i = 0; i < vehicles.length(); i++) {
                JSONObject v = vehicles.getJSONObject(i);
                String carName = v.getString("Name");
                String driverInfo = v.getString("Owner"); // Format "Username:ID"

                if (restrictedRaw.contains(carName)) {
                    String username = driverInfo.split(":")[0];
                    processViolation(apiKey, username, carName);
                    violations++;
                }
            }
            event.getHook().sendMessage("Scan complete. Processed **" + violations + "** violations.").queue();
        });
    }

    private void processViolation(String apiKey, String username, String carName) {
        // 1. Immediate Load
        sendPrcCommand(apiKey, ":load " + username);

        // 2. Scheduled PM (10 Seconds Later)
        scheduler.schedule(() -> {
            String message = ":pm " + username + " You were loaded for using a restricted vehicle (" + carName + "). Please switch cars.";
            sendPrcCommand(apiKey, message);
        }, 10, TimeUnit.SECONDS);
    }

    private void sendPrcCommand(String apiKey, String commandText) {
        JSONObject body = new JSONObject().put("command", commandText);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.policeroleplay.community/v1/server/command"))
                .header("server-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString());
    }

    // --- Data Persistence ---
    private void saveProperty(String key, String value) {
        Properties p = new Properties();
        File f = new File("guild-keys.properties");
        try {
            if (f.exists()) { try (InputStream i = new FileInputStream(f)) { p.load(i); } }
            p.setProperty(key, value);
            try (OutputStream o = new FileOutputStream(f)) { p.store(o, null); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private String getProperty(String key, String def) {
        Properties p = new Properties();
        File f = new File("guild-keys.properties");
        if (!f.exists()) return def;
        try (InputStream i = new FileInputStream(f)) {
            p.load(i); return p.getProperty(key, def);
        } catch (IOException e) { return def; }
    }
}
