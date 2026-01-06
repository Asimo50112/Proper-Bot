package org.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class ERLCCommandHandler extends ListenerAdapter {
    private static final String KEYS_FILE = "guild-keys.properties";
    private final ERLCService erlcService = new ERLCService();

    public static void registerCommands(JDA jda) {
        jda.updateCommands().addCommands(
            Commands.slash("erlc-apikey", "Set and verify the PRC API key")
                .addOption(OptionType.STRING, "key", "Server-Key from ER:LC", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),

            Commands.slash("c", "Run an in-game command")
                .addOption(OptionType.STRING, "cmd", "Command content", true),

            Commands.slash("erlc", "Server management tools")
                .addSubcommands(new SubcommandData("status", "View server status with Roblox profile links"))
                .addSubcommands(new SubcommandData("players", "View online players"))
                .addSubcommands(new SubcommandData("killlogs", "View recent kill logs"))
                .addSubcommands(new SubcommandData("vehicles", "View spawned vehicles"))
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;
        String guildId = event.getGuild().getId();
        String commandName = event.getName();

        if (commandName.equals("erlc-apikey")) {
            handleApiKeySetup(event, guildId);
            return;
        }

        String apiKey = getSavedKey(guildId);
        if (apiKey == null) {
            event.reply("No API key configured for this server. Use /erlc-apikey first.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        if (commandName.equals("c")) {
            handleInGameCommand(event, apiKey);
        } else if (commandName.equals("erlc")) {
            handleSubcommands(event, apiKey);
        }
    }

    private void handleInGameCommand(SlashCommandInteractionEvent event, String apiKey) {
        String input = event.getOption("cmd").getAsString();
        erlcService.postCommand(apiKey, input).thenAccept(response -> {
            if (response.startsWith("ERROR")) {
                event.getHook().sendMessage(response).queue();
            } else {
                JSONObject json = new JSONObject(response);
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("Remote Command")
                        .setDescription(json.getString("message"))
                        .setColor(Color.GREEN)
                        .setFooter("PRC API System");
                event.getHook().sendMessageEmbeds(eb.build()).queue();
            }
        });
    }

    private void handleSubcommands(SlashCommandInteractionEvent event, String key) {
        String sub = event.getSubcommandName();
        if (sub == null) return;

        switch (sub) {
            case "status" -> erlcService.getStatus(key).thenAccept(res -> {
                if (res.startsWith("ERROR")) {
                    event.getHook().sendMessage(res).queue();
                } else {
                    JSONObject json = new JSONObject(res);
                    long ownerId = json.getLong("OwnerId");
                    JSONArray coOwnersArray = json.getJSONArray("CoOwnerIds");

                    // Step 1: Fetch Owner Profile Link
                    erlcService.getRobloxProfileLink(ownerId).thenAccept(ownerLink -> {
                        
                        // Step 2: Fetch Co-Owner Profile Links
                        List<CompletableFuture<String>> coOwnerFutures = new ArrayList<>();
                        for (int i = 0; i < coOwnersArray.length(); i++) {
                            coOwnerFutures.add(erlcService.getRobloxProfileLink(coOwnersArray.getLong(i)));
                        }

                        // Step 3: Combine all results
                        CompletableFuture.allOf(coOwnerFutures.toArray(new CompletableFuture[0])).thenRun(() -> {
                            StringBuilder coOwnerLinks = new StringBuilder();
                            for (var future : coOwnerFutures) {
                                coOwnerLinks.append(future.join()).append("\n");
                            }

                            EmbedBuilder eb = new EmbedBuilder()
                                    .setTitle("Server Status: " + json.getString("Name"))
                                    .addField("Owner", ownerLink, true)
                                    .addField("Co-Owners", coOwnerLinks.length() > 0 ? coOwnerLinks.toString() : "None", true)
                                    .addField("Players", json.getInt("CurrentPlayers") + "/" + json.getInt("MaxPlayers"), true)
                                    .addField("Join Key", json.getString("JoinKey"), true)
                                    .addField("Team Balance", json.getBoolean("TeamBalance") ? "Enabled" : "Disabled", true)
                                    .setColor(Color.BLUE);
                            
                            event.getHook().sendMessageEmbeds(eb.build()).queue();
                        });
                    });
                }
            });

            case "players" -> erlcService.getPlayers(key).thenAccept(res -> {
                if (res.startsWith("ERROR")) {
                    event.getHook().sendMessage(res).queue();
                } else {
                    JSONArray array = new JSONArray(res);
                    EmbedBuilder eb = new EmbedBuilder().setTitle("Online Players").setColor(Color.CYAN);
                    if (array.isEmpty()) {
                        eb.setDescription("The server is currently empty.");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject p = array.getJSONObject(i);
                            sb.append("• **").append(p.getString("Player").split(":")[0]).append("**")
                              .append(" (").append(p.getString("Team")).append(")\n");
                        }
                        eb.setDescription(sb.toString());
                    }
                    event.getHook().sendMessageEmbeds(eb.build()).queue();
                }
            });

            case "killlogs" -> erlcService.getKillLogs(key).thenAccept(res -> {
                if (res.startsWith("ERROR")) {
                    event.getHook().sendMessage(res).queue();
                } else {
                    JSONArray array = new JSONArray(res);
                    EmbedBuilder eb = new EmbedBuilder().setTitle("Recent Kill Logs").setColor(Color.RED);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(array.length(), 10); i++) {
                        JSONObject k = array.getJSONObject(i);
                        sb.append("**").append(k.getString("Killer").split(":")[0]).append("** killed **")
                          .append(k.getString("Killed").split(":")[0]).append("**\n");
                    }
                    eb.setDescription(sb.length() > 0 ? sb.toString() : "No recent activity found.");
                    event.getHook().sendMessageEmbeds(eb.build()).queue();
                }
            });

            case "vehicles" -> erlcService.getVehicles(key).thenAccept(res -> {
                if (res.startsWith("ERROR")) {
                    event.getHook().sendMessage(res).queue();
                } else {
                    JSONArray array = new JSONArray(res);
                    EmbedBuilder eb = new EmbedBuilder().setTitle("Spawned Vehicles").setColor(Color.ORANGE);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(array.length(), 15); i++) {
                        JSONObject v = array.getJSONObject(i);
                        sb.append("**").append(v.getString("Name")).append("** - Owner: ")
                          .append(v.getString("Owner")).append("\n");
                    }
                    eb.setDescription(sb.length() > 0 ? sb.toString() : "No vehicles currently spawned.");
                    event.getHook().sendMessageEmbeds(eb.build()).queue();
                }
            });
        }
    }

    private void handleApiKeySetup(SlashCommandInteractionEvent event, String guildId) {
        String newKey = event.getOption("key").getAsString();
        event.deferReply(true).queue();
        erlcService.verifyKey(newKey).thenAccept(isValid -> {
            if (isValid) {
                saveKey(guildId, newKey);
                event.getHook().sendMessage("API key verified and saved successfully.").queue();
            } else {
                event.getHook().sendMessage("Invalid API key. Handshake failed with PRC API.").queue();
            }
        });
    }

    private void saveKey(String id, String key) {
        Properties props = new Properties();
        File file = new File(KEYS_FILE);
        try {
            if (file.exists()) {
                try (InputStream in = new FileInputStream(file)) { props.load(in); }
            }
            props.setProperty(id, key);
            try (OutputStream out = new FileOutputStream(file)) { props.store(out, "ERLC Guild Keys"); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private String getSavedKey(String id) {
        Properties props = new Properties();
        File file = new File(KEYS_FILE);
        if (!file.exists()) return null;
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
            return props.getProperty(id);
        } catch (IOException e) { return null; }
    }
}
