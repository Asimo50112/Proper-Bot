package org.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
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

public class ERLCRemoteCommand extends ListenerAdapter {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    public static CommandData getCommandData() {
        return Commands.slash("c", "Run an in-game command")
                .addOption(OptionType.STRING, "cmd", "The command to run (e.g. :m Hello)", true);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("c")) return;

        event.deferReply().queue();

        String apiKey = getSavedKey(event.getGuild().getId());
        if (apiKey == null) {
            event.getHook().sendMessage("No API key configured. Use /erlc-apikey first.").queue();
            return;
        }

        String commandInput = event.getOption("cmd").getAsString();

        // Create the JSON body
        JSONObject body = new JSONObject();
        body.put("command", commandInput);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.policeroleplay.community/v1/server/command"))
                .header("server-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .orTimeout(12, TimeUnit.SECONDS)
            .thenAccept(res -> {
                if (res.statusCode() == 200 || res.statusCode() == 201) {
                    // Success! PRC sometimes returns an empty body on success
                    String responseMsg = "Command successfully sent to the server.";
                    
                    if (!res.body().isEmpty() && res.body().startsWith("{")) {
                        JSONObject json = new JSONObject(res.body());
                        if (json.has("message")) responseMsg = json.getString("message");
                    }

                    event.getHook().sendMessageEmbeds(new EmbedBuilder()
                            .setTitle("Command Executed")
                            .setDescription("`" + commandInput + "`\n\n**Result:** " + responseMsg)
                            .setColor(Color.GREEN)
                            .build()).queue();
                } else {
                    event.getHook().sendMessage("API Error: Received status " + res.statusCode() + "\nBody: " + res.body()).queue();
                }
            })
            .exceptionally(ex -> {
                event.getHook().sendMessage("Error: The request timed out or the API is unreachable.").queue();
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
