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
        return Commands.slash("vehicle-restrictions", "Vehicle restriction system")
                .addSubcommands(
                    new SubcommandData("add", "Restrict a car to a role")
                        .addOption(OptionType.STRING, "carname", "Exact PRC Car Name", true)
                        .addOption(OptionType.ROLE, "role", "Authorized Role", true),
                    new SubcommandData("scan", "Manually force a scan")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("vehicle-restrictions") || event.getGuild() == null) return;
        
        if ("add".equals(event.getSubcommandName())) {
            String car = event.getOption("carname").getAsString();
            String roleId = event.getOption("role").getAsRole().getId();
            // Save as lowercase to make matching easier
            saveProperty(event.getGuild().getId() + "_v_role_" + car.toLowerCase(), roleId);
            event.reply("Restriction set for **" + car + "**.").setEphemeral(true).queue();
        } else if ("scan".equals(event.getSubcommandName())) {
            event.reply("Manual scan triggered. Check console for output.").setEphemeral(true).queue();
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
            if (res.statusCode() != 200) return;
            JSONArray vehicles = new JSONArray(res.body());

            // Load all our saved restrictions for this guild
            Properties props = getAllProperties();

            for (int i = 0; i < vehicles.length(); i++) {
                JSONObject v = vehicles.getJSONObject(i);
                String carName = v.getString("Name").toLowerCase();
                String robloxOwner = v.getString("Owner");

                // DEBUG: This will show you exactly what the API sees
                System.out.println("[Guard] Spotted: " + carName + " owned by " + robloxOwner);

                // Look for any restriction that matches this car
                String requiredRoleId = null;
                for (String key : props.stringPropertyNames()) {
                    if (key.startsWith(guild.getId() + "_v_role_")) {
                        String restrictedName = key.replace(guild.getId() + "_v_role_", "");
                        if (carName.contains(restrictedName)) {
                            requiredRoleId = props.getProperty(key);
                            break;
                        }
                    }
                }

                if (requiredRoleId != null) {
                    final String finalRoleId = requiredRoleId;
                    // Check Discord for the member
                    List<Member> members = guild.getMembersByEffectiveName(robloxOwner, true);
                    
                    boolean isAuthorized = false;
                    if (!members.isEmpty()) {
                        Member m = members.get(0);
                        isAuthorized = m.getRoles().stream().anyMatch(r -> r.getId().equals(finalRoleId));
                    }

                    if (!isAuthorized) {
                        System.out.println("[Guard] !!! VIOLATION: " + robloxOwner + " is unauthorized in " + carName);
                        executePenalty(apiKey, robloxOwner, v.getString("Name"));
                    }
                }
            }
        });
    }

    private void executePenalty(String apiKey, String username, String carName) {
        sendPrcCommand(apiKey, ":load " + username);
        // Delay PM by 10 seconds
        scheduler.schedule(() -> {
            sendPrcCommand(apiKey, ":pm " + username + " You were loaded. The " + carName + " is a restricted vehicle.");
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
        try (OutputStream o = new FileOutputStream(FILE)) { p.store(o, null); }
        catch (IOException ignored) {}
    }

    private String getProperty(String key) {
        return getAllProperties().getProperty(key);
    }

    private Properties getAllProperties() {
        Properties p = new Properties();
        File f = new File(FILE);
        if (f.exists()) {
            try (InputStream i = new FileInputStream(f)) { p.load(i); }
            catch (IOException ignored) {}
        }
        return p;
    }
}
