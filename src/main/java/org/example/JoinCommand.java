package org.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.json.JSONObject;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class JoinCommand extends ListenerAdapter {
    private static final String KEYS_FILE = "guild-keys.properties";
    private final ERLCService erlcService = new ERLCService();

    // Command metadata for registration
    public static CommandData getCommandData() {
        return Commands.slash("join", "Get a direct link to join the ER:LC private server");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("join") || event.getGuild() == null) return;

        // 1. Defer immediately to stop the 3-second timeout loop
        event.deferReply().queue();

        // 2. Retrieve key directly within this class
        String apiKey = getSavedKey(event.getGuild().getId());
        if (apiKey == null) {
            event.getHook().sendMessage("No API key configured. Use /erlc-apikey first.").queue();
            return;
        }

        // 3. Fetch server status to get the JoinKey
        erlcService.getStatus(apiKey).thenAccept(res -> {
            if (res.startsWith("ERROR")) {
                event.getHook().sendMessage("PRC API Error: " + res).queue();
                return;
            }

            JSONObject json = new JSONObject(res);
            String joinKey = json.getString("JoinKey");
            String serverName = json.getString("Name");

            // Construct the deep link for ER:LC (PlaceID: 2534724415)
            // Encoding the JSON launch data so Roblox recognizes the psCode
            String deepLink = String.format(
                "roblox://placeId=2534724415&launchData=%%7B%%22psCode%%22%%3A%%22%s%%22%%7D", 
                joinKey
            );

            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("Join " + serverName)
                    .setDescription("Click the link below to launch Roblox and join the server.")
                    .addField("Direct Join Link", "[Click to Launch Roblox](" + deepLink + ")", false)
                    .addField("Manual Join Code", "`" + joinKey + "`", false)
                    .setColor(Color.GREEN)
                    .setFooter("Ensure Roblox is installed on your device.");

            event.getHook().sendMessageEmbeds(eb.build()).queue();
        }).exceptionally(ex -> {
            event.getHook().sendMessage("An error occurred while contacting the PRC API.").queue();
            return null;
        });
    }

    /**
     * Integrated property retrieval to keep this class independent.
     */
    private String getSavedKey(String id) {
        Properties props = new Properties();
        File file = new File(KEYS_FILE);
        if (!file.exists()) return null;
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
            return props.getProperty(id);
        } catch (IOException e) {
            return null;
        }
    }
}
