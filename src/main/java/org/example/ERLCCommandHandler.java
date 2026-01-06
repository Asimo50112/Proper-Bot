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

        // 1. Immediately defer the reply to prevent "Application did not respond"
        // Ephemeral is set to false so everyone can see the server info, 
        // except for the API key setup which we handle separately.
        boolean isPrivate = event.getName().equals("erlc-apikey");
        event.deferReply(isPrivate).queue(); 

        if (event.getName().equals("erlc-apikey")) {
            handleApiKeySetup(event, guildId);
            return;
        }

        String apiKey = getSavedKey(guildId);
        if (apiKey == null) {
            event.getHook().sendMessage("No API key configured for this server. Use /erlc-apikey first.").queue();
            return;
        }

        // Handle commands via the Interaction Hook
        switch (event.getName()) {
            case "c" -> handleInGameCommand(event, apiKey);
            case "erlc" -> handleSubcommands(event, apiKey);
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
            case "status" -> handleStatus(event, key);
            case "players" -> erlcService.getPlayers(key).thenAccept(res -> sendListEmbed(event, res, "Online Players", Color.CYAN));
            case "killlogs" -> erlcService.getKillLogs(key).thenAccept(res -> sendListEmbed(event, res, "Recent Kill Logs", Color.RED));
            case "vehicles" -> erlcService.getVehicles(key).thenAccept(res -> sendListEmbed(event, res, "Spawned Vehicles", Color.ORANGE));
        }
    }

    private void handleStatus(SlashCommandInteractionEvent event, String key) {
        erlcService.getStatus(key).thenAccept(res -> {
            if (res.startsWith("ERROR")) {
                event.getHook().sendMessage(res).queue();
                return;
            }

            JSONObject json = new JSONObject(res);
            long ownerId = json.getLong("OwnerId");
            JSONArray coOwnersArray = json.getJSONArray("CoOwnerIds");

            // Start multiple async handshakes for Roblox profiles
            CompletableFuture<String> ownerFuture = erlcService.getRobloxProfileLink(ownerId);
            List<CompletableFuture<String>> coOwnerFutures = new ArrayList<>();
            for (int i = 0; i < coOwnersArray.length(); i++) {
                coOwnerFutures.add(erlcService.getRobloxProfileLink(coOwnersArray.getLong(i)));
            }

            // Coordinate all futures before sending embed
            CompletableFuture.allOf(coOwnerFutures.toArray(new CompletableFuture[0]))
                .thenCombine(ownerFuture, (voidUnused, ownerLink) -> {
                    StringBuilder coOwnerLinks = new StringBuilder();
                    for (var future : coOwnerFutures) {
                        coOwnerLinks.append(future.join()).append("\n");
                    }

                    return new EmbedBuilder()
                            .setTitle("Server Status: " + json.getString("Name"))
                            .addField("Owner", ownerLink, true)
                            .addField("Co-Owners", coOwnerLinks.length() > 0 ? coOwnerLinks.toString() : "None", true)
                            .addField("Players", json.getInt("CurrentPlayers") + "/" + json.getInt("MaxPlayers"), true)
                            .addField("Join Key", json.getString("JoinKey"), true)
                            .addField("Team Balance", json.getBoolean("TeamBalance") ? "Enabled" : "Disabled", true)
                            .setColor(Color.BLUE)
                            .build();
                }).thenAccept(embed -> event.getHook().sendMessageEmbeds(embed).queue());
        });
    }

    private void sendListEmbed(SlashCommandInteractionEvent event, String res, String title, Color color) {
        if (res.startsWith("ERROR")) {
            event.getHook().sendMessage(res).queue();
            return;
        }
        JSONArray array = new JSONArray(res);
        EmbedBuilder eb = new EmbedBuilder().setTitle(title).setColor(color);
        
        if (array.isEmpty()) {
            eb.setDescription("No data found.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(array.length(), 15); i++) {
                JSONObject item = array.getJSONObject(i);
                // Handle different JSON keys depending on the endpoint
                if (item.has("Player")) sb.append("• ").append(item.getString("Player").split(":")[0]).append("\n");
                else if (item.has("Name")) sb.append("• ").append(item.getString("Name")).append("\n");
            }
            eb.setDescription(sb.toString());
        }
        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }

    private void handleApiKeySetup(SlashCommandInteractionEvent event, String guildId) {
        String newKey = event.getOption("key").getAsString();
        erlcService.verifyKey(newKey).thenAccept(isValid -> {
            if (isValid) {
                saveKey(guildId, newKey);
                event.getHook().sendMessage("API key verified and saved.").queue();
            } else {
                event.getHook().sendMessage("Invalid API key.").queue();
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
            try (OutputStream out = new FileOutputStream(file)) { props.store(out, "ERLC Keys"); }
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
