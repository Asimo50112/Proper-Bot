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
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build();

    public static CommandData getCommandData() {
        return Commands.slash("join", "Get a direct link to join the server");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("join")) return;
        
        System.out.println("[DEBUG] /join command received.");
        event.deferReply().queue();

        try {
            String apiKey = getSavedKey(event.getGuild().getId());
            if (apiKey == null) {
                System.out.println("[DEBUG] API Key not found for guild: " + event.getGuild().getId());
                event.getHook().sendMessage("Use /erlc-apikey first.").queue();
                return;
            }

            System.out.println("[DEBUG] Fetching PRC API data...");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.policeroleplay.community/v1/server"))
                    .header("server-key", apiKey)
                    .GET().build();

            client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .orTimeout(10, TimeUnit.SECONDS) 
                .thenAccept(res -> {
                    System.out.println("[DEBUG] PRC API responded with status: " + res.statusCode());
                    
                    if (res.statusCode() != 200) {
                        event.getHook().sendMessage("API Error: " + res.statusCode()).queue();
                        return;
                    }

                    JSONObject json = new JSONObject(res.body());
                    String code = json.getString("JoinKey");
                    String link = "roblox://placeId=2534724415&launchData=%7B%22psCode%22%3A%22" + code + "%22%7D";

                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Join " + json.getString("Name"))
                            .setDescription("Click the button below to launch Roblox.")
                            .addField("Manual Code", "`" + code + "`", true)
                            .setColor(Color.GREEN);

                    event.getHook().sendMessageEmbeds(eb.build())
                            .addActionRow(Button.link(link, "Join Server"))
                            .queue();
                    System.out.println("[DEBUG] Embed sent successfully.");
                })
                .exceptionally(ex -> {
                    System.out.println("[DEBUG] Async exception: " + ex.getMessage());
                    event.getHook().sendMessage("Error: The request failed or timed out.").queue();
                    return null;
                });

        } catch (Exception e) {
            System.out.println("[DEBUG] Immediate crash: " + e.getMessage());
            e.printStackTrace();
            event.getHook().sendMessage("A critical error occurred.").queue();
        }
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
