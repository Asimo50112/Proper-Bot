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
import java.util.concurrent.TimeUnit;

public class ERLCStatusCommand extends ListenerAdapter {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    public static CommandData getCommandData() {
        return Commands.slash("status", "View detailed server status and ownership");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("status")) return;

        event.deferReply().queue();

        String apiKey = getSavedKey(event.getGuild().getId());
        if (apiKey == null) {
            event.getHook().sendMessage("Use /erlc-apikey first.").queue();
            return;
        }

        // Step 1: Fetch PRC Server Data
        HttpRequest prcRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.policeroleplay.community/v1/server"))
                .header("server-key", apiKey)
                .GET().build();

        client.sendAsync(prcRequest, HttpResponse.BodyHandlers.ofString())
            .orTimeout(10, TimeUnit.SECONDS)
            .thenAccept(res -> {
                if (res.statusCode() != 200) {
                    event.getHook().sendMessage("PRC API Error: " + res.statusCode()).queue();
                    return;
                }

                JSONObject json = new JSONObject(res.body());
                long ownerId = json.getLong("OwnerId");
                JSONArray coOwnersArray = json.getJSONArray("CoOwnerIds");

                // Step 2: Prepare Roblox Username lookups
                CompletableFuture<String> ownerFuture = fetchRobloxLink(ownerId);
                List<CompletableFuture<String>> coOwnerFutures = new ArrayList<>();
                for (int i = 0; i < coOwnersArray.length(); i++) {
                    coOwnerFutures.add(fetchRobloxLink(coOwnersArray.getLong(i)));
                }

                // Step 3: Wait for all Roblox lookups to finish
                CompletableFuture.allOf(coOwnerFutures.toArray(new CompletableFuture[0]))
                    .thenCombine(ownerFuture, (voidUnused, ownerLink) -> {
                        StringBuilder coOwnerLinks = new StringBuilder();
                        for (var future : coOwnerFutures) {
                            coOwnerLinks.append(future.join()).append("\n");
                        }

                        EmbedBuilder eb = new EmbedBuilder()
                                .setTitle("Server Status: " + json.getString("Name"))
                                .addField("Owner", ownerLink, true)
                                .addField("Co-Owners", coOwnerLinks.length() > 0 ? coOwnerLinks.toString() : "None", true)
                                .addField("Players", json.getInt("CurrentPlayers") + "/" + json.getInt("MaxPlayers"), true)
                                .addField("Join Key", "`" + json.getString("JoinKey") + "`", true)
                                .addField("Team Balance", json.getBoolean("TeamBalance") ? "Enabled" : "Disabled", true)
                                .setColor(Color.BLUE)
                                .setFooter("PRC API System • Data updated live");

                        event.getHook().sendMessageEmbeds(eb.build()).queue();
                        return null;
                    }).exceptionally(ex -> {
                        event.getHook().sendMessage("Error fetching Roblox profiles.").queue();
                        return null;
                    });

            }).exceptionally(ex -> {
                event.getHook().sendMessage("PRC API request timed out.").queue();
                return null;
            });
    }

    /**
     * Helper to fetch Roblox username and format as Markdown link
     */
    private CompletableFuture<String> fetchRobloxLink(long userId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://users.roblox.com/v1/users/" + userId))
                .GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() == 200) {
                        String name = new JSONObject(res.body()).getString("name");
                        return String.format("[%s](https://www.roblox.com/users/%d/profile)", name, userId);
                    }
                    return "Unknown (" + userId + ")";
                });
    }

    private String getSavedKey(String id) {
        Properties p = new Properties();
        File f = new File("guild-keys.properties");
        if (!f.exists()) return null;
        try (InputStream i = new FileInputStream(f)) {
            p.load(i);
            return p.getProperty(id);
        } catch (IOException e) { return null; }
    }
}
