package org.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ERLCCommandHandler extends ListenerAdapter {
    private final ERLCService erlcService = new ERLCService();

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        // 1. Defer immediately to extend the 3-second limit to 15 minutes
        boolean isPrivate = event.getName().equals("erlc-apikey");
        event.deferReply(isPrivate).queue();

        String apiKey = getSavedKey(event.getGuild().getId());
        if (apiKey == null && !event.getName().equals("erlc-apikey")) {
            event.getHook().sendMessage("No API key configured. Use /erlc-apikey first.").queue();
            return;
        }

        try {
            switch (event.getName()) {
                case "erlc" -> {
                    if ("status".equals(event.getSubcommandName())) {
                        handleStatus(event, apiKey);
                    } else {
                        // Handle other subcommands...
                        handleOtherSubcommands(event, apiKey);
                    }
                }
                case "c" -> handleInGameCommand(event, apiKey);
                case "erlc-apikey" -> handleApiKeySetup(event, event.getGuild().getId());
            }
        } catch (Exception e) {
            // Safety net: Always send a message if code crashes to stop the infinite loop
            event.getHook().sendMessage("An internal error occurred while processing the command.").queue();
            e.printStackTrace();
        }
    }

    private void handleStatus(SlashCommandInteractionEvent event, String key) {
        erlcService.getStatus(key).thenAccept(res -> {
            if (res.startsWith("ERROR")) {
                event.getHook().sendMessage(res).queue();
                return;
            }

            try {
                JSONObject json = new JSONObject(res);
                long ownerId = json.getLong("OwnerId");
                JSONArray coOwnersArray = json.getJSONArray("CoOwnerIds");

                // Fetch Owner profile link
                CompletableFuture<String> ownerFuture = erlcService.getRobloxProfileLink(ownerId);
                
                // Fetch Co-Owner profile links
                List<CompletableFuture<String>> coOwnerFutures = new ArrayList<>();
                for (int i = 0; i < coOwnersArray.length(); i++) {
                    coOwnerFutures.add(erlcService.getRobloxProfileLink(coOwnersArray.getLong(i)));
                }

                // Create a combined future that times out after 10 seconds to prevent infinite loop
                CompletableFuture<Void> allDone = CompletableFuture.allOf(coOwnerFutures.toArray(new CompletableFuture[0]));
                
                allDone.orTimeout(10, TimeUnit.SECONDS).handle((voidUnused, throwable) -> {
                    if (throwable != null) {
                        event.getHook().sendMessage("Error or timeout fetching Roblox usernames.").queue();
                        return null;
                    }

                    StringBuilder coOwnerLinks = new StringBuilder();
                    for (var future : coOwnerFutures) {
                        coOwnerLinks.append(future.join()).append("\n");
                    }

                    ownerFuture.thenAccept(ownerLink -> {
                        EmbedBuilder eb = new EmbedBuilder()
                                .setTitle("Server Status: " + json.getString("Name"))
                                .addField("Owner", ownerLink, true)
                                .addField("Co-Owners", coOwnerLinks.length() > 0 ? coOwnerLinks.toString() : "None", true)
                                .addField("Players", json.getInt("CurrentPlayers") + "/" + json.getInt("MaxPlayers"), true)
                                .addField("Join Key", json.getString("JoinKey"), true)
                                .setColor(Color.BLUE);
                        
                        event.getHook().sendMessageEmbeds(eb.build()).queue();
                    });
                    return null;
                });
            } catch (Exception e) {
                event.getHook().sendMessage("Failed to parse server data.").queue();
            }
        }).exceptionally(ex -> {
            event.getHook().sendMessage("The PRC API failed to respond.").queue();
            return null;
        });
    }

    // ... other helper methods (handleInGameCommand, handleOtherSubcommands, etc.) ...
}
