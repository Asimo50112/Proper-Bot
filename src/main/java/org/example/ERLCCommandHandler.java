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
            Commands.slash("erlc-apikey", "Set API key")
                .addOption(OptionType.STRING, "key", "Server Key", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
            Commands.slash("c", "Run command").addOption(OptionType.STRING, "cmd", "Content", true),
            Commands.slash("erlc", "Tools")
                .addSubcommands(new SubcommandData("status", "View status"))
                .addSubcommands(new SubcommandData("players", "View players with ranks"))
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;
        event.deferReply(event.getName().equals("erlc-apikey")).queue();

        String apiKey = getSavedKey(event.getGuild().getId());
        if (apiKey == null && !event.getName().equals("erlc-apikey")) {
            event.getHook().sendMessage("Use /erlc-apikey first.").queue();
            return;
        }

        switch (event.getName()) {
            case "erlc-apikey" -> handleApiKeySetup(event, event.getGuild().getId());
            case "c" -> handleInGameCommand(event, apiKey);
            case "erlc" -> {
                String sub = event.getSubcommandName();
                if ("status".equals(sub)) handleStatus(event, apiKey);
                else if ("players".equals(sub)) handlePlayersWithRanks(event, apiKey);
            }
        }
    }

    private void handleStatus(SlashCommandInteractionEvent event, String key) {
        erlcService.getStatus(key).thenAccept(res -> {
            if (res.startsWith("ERROR")) { event.getHook().sendMessage(res).queue(); return; }
            JSONObject json = new JSONObject(res);
            
            erlcService.getRobloxProfileLink(json.getLong("OwnerId")).thenAccept(ownerLink -> {
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("Server Status: " + json.getString("Name"))
                        .addField("Owner", ownerLink, true)
                        .addField("Join Key", json.getString("JoinKey"), true)
                        .addField("Players", json.getInt("CurrentPlayers") + "/" + json.getInt("MaxPlayers"), true)
                        .setColor(Color.BLUE);
                event.getHook().sendMessageEmbeds(eb.build()).queue();
            });
        });
    }

    private void handlePlayersWithRanks(SlashCommandInteractionEvent event, String key) {
        erlcService.getStatus(key).thenAccept(statusRes -> {
            JSONObject statusJson = new JSONObject(statusRes);
            long ownerId = statusJson.getLong("OwnerId");
            JSONArray coOwners = statusJson.getJSONArray("CoOwnerIds");

            erlcService.getPlayers(key).thenAccept(playerRes -> {
                if (playerRes.startsWith("ERROR")) { event.getHook().sendMessage(playerRes).queue(); return; }
                JSONArray players = new JSONArray(playerRes);
                
                List<CompletableFuture<String>> futures = new ArrayList<>();
                List<String> meta = new ArrayList<>();

                for (int i = 0; i < players.length(); i++) {
                    JSONObject p = players.getJSONObject(i);
                    long userId = Long.parseLong(p.getString("Player").split(":")[1]);
                    
                    String rank = "Player";
                    if (userId == ownerId) rank = "Server Owner";
                    else for (int j = 0; j < coOwners.length(); j++) if (coOwners.getLong(j) == userId) rank = "Co-Owner";

                    meta.add(String.format("— *%s* (%s)", p.getString("Team"), rank));
                    futures.add(erlcService.getRobloxProfileLink(userId));
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(10, TimeUnit.SECONDS).handle((v, t) -> {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < futures.size(); i++) sb.append("• ").append(futures.get(i).join()).append(" ").append(meta.get(i)).append("\n");
                        event.getHook().sendMessageEmbeds(new EmbedBuilder().setTitle("Online Players").setDescription(sb.length() > 0 ? sb.toString() : "Empty").setColor(Color.CYAN).build()).queue();
                        return null;
                    });
            });
        });
    }

    private void handleInGameCommand(SlashCommandInteractionEvent event, String apiKey) {
        erlcService.postCommand(apiKey, event.getOption("cmd").getAsString()).thenAccept(res -> {
            if (res.startsWith("ERROR")) event.getHook().sendMessage(res).queue();
            else event.getHook().sendMessageEmbeds(new EmbedBuilder().setTitle("Success").setDescription(new JSONObject(res).getString("message")).setColor(Color.GREEN).build()).queue();
        });
    }

    private void handleApiKeySetup(SlashCommandInteractionEvent event, String guildId) {
        String key = event.getOption("key").getAsString();
        erlcService.verifyKey(key).thenAccept(valid -> {
            if (valid) { saveKey(guildId, key); event.getHook().sendMessage("Key saved.").queue(); }
            else event.getHook().sendMessage("Invalid key.").queue();
        });
    }

    private void saveKey(String id, String key) {
        Properties p = new Properties();
        try {
            File f = new File(KEYS_FILE);
            if (f.exists()) try (InputStream i = new FileInputStream(f)) { p.load(i); }
            p.setProperty(id, key);
            try (OutputStream o = new FileOutputStream(f)) { p.store(o, null); }
        } catch (IOException e) {}
    }

    private String getSavedKey(String id) {
        Properties p = new Properties();
        File f = new File(KEYS_FILE);
        if (!f.exists()) return null;
        try (InputStream i = new FileInputStream(f)) { p.load(i); return p.getProperty(id); }
        catch (IOException e) { return null; }
    }
}
