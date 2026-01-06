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

public class JoinCommand extends ListenerAdapter {
    private final HttpClient client = HttpClient.newHttpClient();

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
                .header("server-key", apiKey).GET().build();

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
            try {
                JSONObject json = new JSONObject(res.body());
                String code = json.getString("JoinKey");
                String serverName = json.getString("Name");

                // Updated link formatting with proper encoding
                String link = "roblox://placeId=2534724415&launchData=%7B%22psCode%22%3A%22" + code + "%22%7D";

                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("Join " + serverName)
                        .setDescription("Click the button below to launch Roblox and join the private server automatically.")
                        .addField("Manual Join Code", "`" + code + "`", true)
                        .setColor(Color.GREEN)
                        .setFooter("Note: This requires Roblox to be installed.");

                // Use an ActionRow with a Button for the best compatibility
                event.getHook().sendMessageEmbeds(eb.build())
                        .addActionRow(Button.link(link, "Join Server")) 
                        .queue();
                        
            } catch (Exception e) {
                event.getHook().sendMessage("Error parsing server data. Ensure the server is active.").queue();
            }
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
