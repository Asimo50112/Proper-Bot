package org.example;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

public class ERLCSetupCommand extends ListenerAdapter {
    private final HttpClient client = HttpClient.newHttpClient();

    public static CommandData getCommandData() {
        return Commands.slash("erlc-apikey", "Set and verify the PRC API key")
                .addOption(OptionType.STRING, "key", "Server-Key from ER:LC", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("erlc-apikey")) return;
        String key = event.getOption("key").getAsString();
        event.deferReply(true).queue();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.policeroleplay.community/v1/server"))
                .header("server-key", key)
                .GET().build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
            if (res.statusCode() == 200) {
                saveKey(event.getGuild().getId(), key);
                event.getHook().sendMessage("API key verified and saved.").queue();
            } else {
                event.getHook().sendMessage("Invalid API key (Status: " + res.statusCode() + ")").queue();
            }
        });
    }

    private void saveKey(String id, String key) {
        Properties p = new Properties();
        File f = new File("guild-keys.properties");
        try {
            if (f.exists()) { try (InputStream i = new FileInputStream(f)) { p.load(i); } }
            p.setProperty(id, key);
            try (OutputStream o = new FileOutputStream(f)) { p.store(o, null); }
        } catch (IOException e) { e.printStackTrace(); }
    }
}
