package org.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
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
            .connectTimeout(java.time.Duration.ofSeconds(10))
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

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .orTimeout(10, TimeUnit.SECONDS)
            .thenAccept(res -> {
                try {
                    JSONObject json = new JSONObject(res.body());
                    String code = json.getString("JoinKey");
                    String serverName = json.getString("Name");
                    
                    // The Deep Link
                    String deepLink = "roblox://placeId=2534724415&launchData=%7B%22psCode%22%3A%22" + code + "%22%7D";

                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Join " + serverName)
                            .setColor(Color.GREEN)
                            .setDescription("### [Click Here to Join the Server](" + deepLink + ")\n" +
                                    "*If the link above doesn't work, ensure you have Roblox installed.*")
                            .addField("Manual Join Code", "`" + code + "`", false)
                            .setFooter("Direct launch links are only supported on some devices.");

                    // We send ONLY the embed, NO buttons (to avoid the 50035 error)
                    event.getHook().sendMessageEmbeds(eb.build()).queue();

                } catch (Exception e) {
                    event.getHook().sendMessage("Error: Failed to parse server data.").queue();
                }
            })
            .exceptionally(ex -> {
                event.getHook().sendMessage("Error: The request timed out or failed.").queue();
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
