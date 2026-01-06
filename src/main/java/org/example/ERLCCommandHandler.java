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
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
            Commands.slash("erlc", "Server tools")
                .addSubcommands(new SubcommandData("status", "View status with Roblox links"))
                .addSubcommands(new SubcommandData("players", "View online players"))
                .addSubcommands(new SubcommandData("killlogs", "View recent kill logs"))
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;
        boolean isPrivate = event.getName().equals("erlc-apikey");
        event.deferReply(isPrivate).queue();

        String apiKey = getSavedKey(event.getGuild().getId());
        if (apiKey == null && !event.getName().equals("erlc-apikey")) {
            event.getHook().sendMessage("No API key configured. Use /erlc-apikey first.").queue();
            return;
        }

        try {
            switch (event.getName()) {
                case "erlc-apikey" -> handleApiKeySetup(event, event.getGuild().getId());
                case "c" -> handleInGameCommand(event, apiKey);
                case "erlc" -> handleSubcommands(event, apiKey);
            }
        } catch (Exception e) {
            e.printStackTrace();
            event.getHook().sendMessage("An internal error occurred.").queue();
        }
    }

    private void handleSubcommands(SlashCommandInteractionEvent event, String key) {
        String sub = event.getSubcommandName();
        if (sub == null) return;
        
        switch (sub) {
            case "status" -> handleStatus(event, key);
            case "players" -> handlePlayers(event, key);
            case "killlogs" -> event.getHook().sendMessage("Kill logs not implemented yet.").queue();
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
            JSONArray coOwners = json.getJSONArray("CoOwnerIds");

            CompletableFuture<String> ownerFuture = erlcService.getRobloxProfileLink(ownerId);
            List<CompletableFuture<String>> coFutures = new ArrayList<>();
            for (int i = 0; i < coOwners.length(); i++) {
                coFutures.add(erlcService.getRobloxProfileLink(coOwners.getLong(i)));
            }

            CompletableFuture.allOf(coFutures.toArray(new CompletableFuture[0]))
                .orTimeout(10, TimeUnit.SECONDS)
                .handle((v, t) -> {
                    if (t != null) {
                        event.getHook().sendMessage("Roblox API timed out.").queue();
                        return null;
                    }

                    ownerFuture.thenAccept(ownerLink -> {
                        StringBuilder coList = new StringBuilder();
                        for (var f : coFutures) coList.append(f.join()).append("\n");

                        EmbedBuilder eb = new EmbedBuilder()
                                .setTitle("Server Status: " + json.getString("Name"))
                                .addField("Owner", ownerLink, true)
                                .addField("Co-Owners", coList.length() > 0 ? coList.toString() : "None", true)
                                .addField("Players", json.getInt("CurrentPlayers") + "/" + json.getInt("MaxPlayers"), true)
                                .addField("Join Key", json.getString("JoinKey"), true)
                                .setColor(Color.BLUE);
                        event.getHook().sendMessageEmbeds(eb.build()).queue();
                    });
                    return null;
                });
        });
    }

    private void handlePlayers(SlashCommandInteractionEvent event, String key) {
        erlcService.getPlayers(key).thenAccept(res -> {
            if (res.startsWith("ERROR")) {
                event.getHook().sendMessage(res).queue();
                return;
            }

            JSONArray array = new JSONArray(res);
            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("Online Players")
                    .setColor(Color.CYAN);

            if (array.isEmpty()) {
                eb.setDescription("The server is currently empty.");
                event.getHook().sendMessageEmbeds(eb.build()).queue();
            } else {
                List<CompletableFuture<String>> playerFutures = new ArrayList<>();
                List<String> teams = new ArrayList<>();

                for (int i = 0; i < array.length(); i++) {
                    JSONObject p = array.getJSONObject(i);
                    // Extract numerical ID from "PlayerName:Id"
                    String playerData = p.getString("Player");
                    long userId = Long.parseLong(playerData.split(":")[1]);
                    teams.add(p.getString("Team"));
                    playerFutures.add(erlcService.getRobloxProfileLink(userId));
                }

                CompletableFuture.allOf(playerFutures.toArray(new CompletableFuture[0]))
                    .orTimeout(10, TimeUnit.SECONDS)
                    .handle((v, t) -> {
                        if (t != null) {
                            event.getHook().sendMessage("Error or timeout fetching player profiles.").queue();
                            return null;
                        }

                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < playerFutures.size(); i++) {
                            String profileLink = playerFutures.get(i).join();
                            String team = teams.get(i);
                            sb.append("• ").append(profileLink).append(" — *").append(team).append("*\n");
                        }

                        eb.setDescription(sb.toString());
                        eb.setFooter("Total Players: " + array.length());
                        event.getHook().sendMessageEmbeds(eb.build()).queue();
                        return null;
                    });
            }
        });
    }

    private void handleInGameCommand(SlashCommandInteractionEvent event, String apiKey) {
        erlcService.postCommand(apiKey, event.getOption("cmd").getAsString()).thenAccept(res -> {
            if (res.startsWith("ERROR")) {
                event.getHook().sendMessage(res).queue();
            } else {
                event.getHook().sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("Command Sent")
                        .setDescription(new JSONObject(res).getString("message"))
                        .setColor(Color.GREEN).build()).queue();
            }
        });
    }

    private void handleApiKeySetup(SlashCommandInteractionEvent event, String guildId) {
        String newKey = event.getOption("key").getAsString();
        erlcService.verifyKey(newKey).thenAccept(valid -> {
            if (valid) {
                saveKey(guildId, newKey);
                event.getHook().sendMessage("Key saved and verified.").queue();
            } else {
                event.getHook().sendMessage("Invalid key. Please check your PRC Server Key.").queue();
            }
        });
    }

    private void saveKey(String id, String key) {
        Properties p = new Properties();
        File f = new File(KEYS_FILE);
        try {
            if (f.exists()) {
                try (InputStream i = new FileInputStream(f)) { p.load(i); }
            }
            p.setProperty(id, key);
            try (OutputStream o = new FileOutputStream(f)) { p.store(o, null); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private String getSavedKey(String id) {
        Properties p = new Properties();
        File f = new File(KEYS_FILE);
        if (!f.exists()) return null;
        try (InputStream i = new FileInputStream(f)) {
            p.load(i);
            return p.getProperty(id);
        } catch (IOException e) { return null; }
    }
}
