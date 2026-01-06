package org.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class ERLCPlayersCommand extends ListenerAdapter {
    private final HttpClient client = HttpClient.newBuilder().build();

    public static CommandData getCommandData() {
        return Commands.slash("players", "View online players with ranks and profile links");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("players")) return;

        event.deferReply().queue();
        String apiKey = getSavedKey(event.getGuild().getId());

        if (apiKey == null) {
            event.getHook().sendMessage("Use /erlc-apikey first.").queue();
            return;
        }

        // 1. Get Status (to identify Owners/Co-Owners)
        fetchPRC(apiKey, "/server").thenAccept(statusRes -> {
            JSONObject statusJson = new JSONObject(statusRes);
            long ownerId = statusJson.getLong("OwnerId");
            JSONArray coOwners = statusJson.getJSONArray("CoOwnerIds");

            // 2. Get Players
            fetchPRC(apiKey, "/server/players").thenAccept(playerRes -> {
                JSONArray playerArray = new JSONArray(playerRes);
                if (playerArray.isEmpty()) {
                    event.getHook().sendMessage("The server is currently empty.").queue();
                    return;
                }

                List<CompletableFuture<String>> futures = new ArrayList<>();
                List<String> rankMeta = new ArrayList<>();

                for (int i = 0; i < playerArray.length(); i++) {
                    JSONObject p = playerArray.getJSONObject(i);
                    long uId = Long.parseLong(p.getString("Player").split(":")[1]);
                    
                    String rank = "Player";
                    if (uId == ownerId) rank = "Owner";
                    else {
                        for (int j = 0; j < coOwners.length(); j++) {
                            if (coOwners.getLong(j) == uId) { rank = "Co-Owner"; break; }
                        }
                    }

                    rankMeta.add("— *" + p.getString("Team") + "* (**" + rank + "**)");
                    futures.add(fetchRoblox(uId));
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < futures.size(); i++) {
                        sb.append("• ").append(futures.get(i).join()).append(" ").append(rankMeta.get(i)).append("\n");
                    }

                    event.getHook().sendMessageEmbeds(new EmbedBuilder()
                            .setTitle("Online Players")
                            .setDescription(sb.toString())
                            .setColor(Color.CYAN)
                            .setFooter("Total: " + playerArray.length())
                            .build()).queue();
                });
            });
        });
    }

    private CompletableFuture<String> fetchPRC(String key, String end) {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://api.policeroleplay.community/v1" + end)).header("server-key", key).GET().build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);
    }

    private CompletableFuture<String> fetchRoblox(long id) {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://users.roblox.com/v1/users/" + id)).GET().build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
            if (res.statusCode() == 200) return "[" + new JSONObject(res.body()).getString("name") + "](https://www.roblox.com/users/" + id + "/profile)";
            return "Unknown (" + id + ")";
        });
    }

    private String getSavedKey(String id) {
        Properties p = new Properties();
        try (InputStream i = new FileInputStream("guild-keys.properties")) { p.load(i); return p.getProperty(id); }
        catch (IOException e) { return null; }
    }
}
