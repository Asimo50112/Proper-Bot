package org.example;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
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
        return Commands.slash("vehicle-restrictions", "Manage car permissions")
                .addSubcommands(
                    new SubcommandData("add", "Restrict car to a role").addOption(OptionType.STRING, "carname", "Exact Car Name", true).addOption(OptionType.ROLE, "role", "Authorized Role", true),
                    new SubcommandData("scan", "Force manual scan")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("vehicle-restrictions") || event.getGuild() == null) return;
        
        if ("add".equals(event.getSubcommandName())) {
            String car = event.getOption("carname").getAsString();
            String roleId = event.getOption("role").getAsRole().getId();
            saveProperty(event.getGuild().getId() + "_v_role_" + car.toLowerCase(), roleId);
            event.reply("Car **" + car + "** restricted to role successfully.").setEphemeral(true).queue();
        } else if ("scan".equals(event.getSubcommandName())) {
            event.reply("Scan started... Check console.").setEphemeral(true).queue();
            performScan(event.getGuild());
        }
    }

    public void performScan(Guild guild) {
        String apiKey = getProperty(guild.getId());
        if (apiKey == null) return;

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://api.policeroleplay.community/v1/server/vehicles")).header("server-key", apiKey).GET().build();

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
            if (res.statusCode() != 200) return;
            JSONArray vehicles = new JSONArray(res.body());
            Properties props = getAllProperties();

            for (int i = 0; i < vehicles.length(); i++) {
                JSONObject v = vehicles.getJSONObject(i);
                String carName = v.getString("Name");
                String robloxOwner = v.getString("Owner");

                // Check for saved restriction
                String requiredRoleId = props.getProperty(guild.getId() + "_v_role_" + carName.toLowerCase());
                
                if (requiredRoleId != null) {
                    System.out.println("[Guard] RESTRICTION FOUND: " + carName + " requires Role " + requiredRoleId);

                    // Find Member (Checking Nickname and Global Name)
                    Member driver = guild.getMembers().stream()
                            .filter(m -> m.getEffectiveName().equalsIgnoreCase(robloxOwner) || m.getUser().getName().equalsIgnoreCase(robloxOwner))
                            .findFirst().orElse(null);

                    if (driver == null) {
                        System.out.println("[Guard] VIOLATION: Member '" + robloxOwner + "' not found in Discord. Loading.");
                        executePenalty(apiKey, robloxOwner, carName);
                    } else {
                        boolean hasRole = driver.getRoles().stream().anyMatch(r -> r.getId().equals(requiredRoleId));
                        if (!hasRole) {
                            System.out.println("[Guard] VIOLATION: " + robloxOwner + " found but lacks required role. Loading.");
                            executePenalty(apiKey, robloxOwner, carName);
                        } else {
                            System.out.println("[Guard] AUTHORIZED: " + robloxOwner + " is cleared.");
                        }
                    }
                }
            }
        });
    }

    private void executePenalty(String apiKey, String username, String carName) {
        // Immediate :load
        sendPrcCommand(apiKey, ":load " + username);
        
        // :pm after 10 seconds
        scheduler.schedule(() -> {
            sendPrcCommand(apiKey, ":pm " + username + " The " + carName + " is restricted. You have been loaded.");
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
        Properties p = getAllProperties();
        p.setProperty(key, val);
        try (OutputStream o = new FileOutputStream(FILE)) { p.store(o, null); } catch (IOException ignored) {}
    }

    private String getProperty(String key) { return getAllProperties().getProperty(key); }

    private Properties getAllProperties() {
        Properties p = new Properties();
        File f = new File(FILE);
        if (f.exists()) { try (InputStream i = new FileInputStream(f)) { p.load(i); } catch (IOException ignored) {} }
        return p;
    }
}
