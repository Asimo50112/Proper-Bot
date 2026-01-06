package org.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.json.JSONObject;

import java.awt.Color;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class ERLCRemoteCommand extends ListenerAdapter {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    public static CommandData getCommandData() {
        return Commands.slash("c", "Remote command management")
                .addSubcommands(
                    new SubcommandData("run", "Run an in-game command")
                        .addOption(OptionType.STRING, "cmd", "Command (e.g. :m Hello)", true),
                    new SubcommandData("setrole", "Set the staff role allowed to use /c run")
                        .addOption(OptionType.ROLE, "role", "The role to authorize", true)
                );
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("c") || event.getGuild() == null) return;

        String subcommand = event.getSubcommandName();
        String guildId = event.getGuild().getId();

        if (subcommand.equals("setrole")) {
            // Check for Admin permission to set the role
            if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply("Only Administrators can set the authorized role.").setEphemeral(true).queue();
                return;
            }
            
            String roleId = event.getOption("role").getAsRole().getId();
            saveProperty(guildId + "_role", roleId);
            event.reply("Authorized role updated to: " + event.getOption("role").getAsRole().getName()).setEphemeral(true).queue();
            return;
        }

        if (subcommand.equals("run")) {
            // --- Permission Check Logic ---
            String requiredRoleId = getSavedProperty(guildId + "_role");
            boolean isAllowed = event.getMember().hasPermission(Permission.ADMINISTRATOR);

            if (!isAllowed && requiredRoleId != null) {
                isAllowed = event.getMember().getRoles().stream()
                        .anyMatch(role -> role.getId().equals(requiredRoleId));
            }

            if (!isAllowed) {
                event.reply("You do not have the authorized role to run in-game commands.").setEphemeral(true).queue();
                return;
            }

            // --- Execution Logic ---
            event.deferReply().queue();
            String apiKey = getSavedProperty(guildId);
            if (apiKey == null) {
                event.getHook().sendMessage("API key not set. Use /erlc-apikey first.").queue();
                return;
            }

            String cmd = event.getOption("cmd").getAsString();
            executePrcCommand(event, apiKey, cmd);
        }
    }

    private void executePrcCommand(SlashCommandInteractionEvent event, String apiKey, String cmd) {
        JSONObject body = new JSONObject().put("command", cmd);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.policeroleplay.community/v1/server/command"))
                .header("server-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .orTimeout(12, TimeUnit.SECONDS)
            .thenAccept(res -> {
                String responseMsg = "Command sent.";
                if (!res.body().isEmpty() && res.body().startsWith("{")) {
                    JSONObject json = new JSONObject(res.body());
                    if (json.has("message")) responseMsg = json.getString("message");
                }
                event.getHook().sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("Remote Execution")
                        .setDescription("`" + cmd + "`\n**Result:** " + responseMsg)
                        .setColor(Color.GREEN).build()).queue();
            })
            .exceptionally(ex -> {
                event.getHook().sendMessage("Request timed out or failed.").queue();
                return null;
            });
    }

    private void saveProperty(String key, String value) {
        Properties p = new Properties();
        try {
            File f = new File("guild-keys.properties");
            if (f.exists()) { try (InputStream i = new FileInputStream(f)) { p.load(i); } }
            p.setProperty(key, value);
            try (OutputStream o = new FileOutputStream(f)) { p.store(o, null); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private String getSavedProperty(String key) {
        Properties p = new Properties();
        try (InputStream i = new FileInputStream("guild-keys.properties")) {
            p.load(i); return p.getProperty(key);
        } catch (IOException e) { return null; }
    }
}
