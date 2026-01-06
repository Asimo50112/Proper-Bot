package org.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.json.JSONObject;
import java.awt.Color;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class JoinCommand extends ListenerAdapter {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10)) // Prevent hanging connections
            .build();

    public static CommandData getCommandData() {
        return Commands.slash("join", "Get a direct link to join the server");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("join")) return;
        
        event.deferReply().queue();

        String apiKey = getSavedKey(event.getGuild().getId());
        if (apiKey == null) {
            event.getHook().sendMessage("Use /erlc-apikey first.").queue();
            return;
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.policeroleplay.community/v1/server"))
                .header("server-key", apiKey)
                .GET().build();

        // Use orTimeout to ensure the bot doesn't think forever if the PRC API is slow
        client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .orTimeout(10, TimeUnit.SECONDS) 
            .thenAccept(res -> {
                try {
                    if (res.statusCode() != 200) {
                        event.getHook().sendMessage("API Error: Received status code " + res.statusCode()).queue();
                        return;
                    }

                    JSONObject json = new JSONObject(res.body());
                    String code = json.getString("JoinKey");
                    String serverName = json.getString("Name");
                    String link = "roblox://placeId=2534724415&launchData=%7B%22psCode%22%3A%22" + code + "%22%7D";

                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Join " + serverName)
                            .setDescription("Click the button below to launch Roblox.")
                            .addField("Manual Code", "`" + code + "`", true)
                            .setColor(Color.GREEN);

                    event.getHook().sendMessageEmbeds(eb.build())
                            .addActionRow(Button.link(link, "Join Server"))
                            .queue();

                } catch (Exception e) {
                    event.getHook().sendMessage("Error: Failed to parse server data.").queue();
                }
            })
            .exceptionally(ex -> {
                // This is the most important part to stop the "thinking" loop
                event.getHook().sendMessage("Error: The request timed out or failed to connect.").queue();
                return null;
            });
    }

    private String getSavedKey(String id) {
        Properties p = new Properties();
        File f = new File("guild-keys.properties");
        if (!f.exists()) return null;
        try (InputStream i = new FileInputStream(f)) {
            p.load(i);
            return p.getProperty(id);
        } catch (IOException e) {
            return null;
        }
    }
}
