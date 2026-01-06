package org.example;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.json.JSONObject;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

public class ERLCRemoteCommand extends ListenerAdapter {
    private final HttpClient client = HttpClient.newHttpClient();

    public static CommandData getCommandData() {
        return Commands.slash("c", "Run in-game command")
                .addOption(OptionType.STRING, "cmd", "Content", true);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("c")) return;
        event.deferReply().queue();

        String key = getSavedKey(event.getGuild().getId());
        String cmd = event.getOption("cmd").getAsString();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.policeroleplay.community/v1/server/command"))
                .header("server-key", key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"command\": \"" + cmd + "\"}"))
                .build();

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
            String msg = new JSONObject(res.body()).getString("message");
            event.getHook().sendMessage(msg).queue();
        });
    }

    private String getSavedKey(String id) {
        Properties p = new Properties();
        try (InputStream i = new FileInputStream("guild-keys.properties")) {
            p.load(i); return p.getProperty(id);
        } catch (IOException e) { return null; }
    }
}
