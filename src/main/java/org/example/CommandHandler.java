package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Properties;

public class CommandHandler extends ListenerAdapter {
    private static final String KEYS_FILE = "guild-keys.properties";
    private final ERLCService erlcService = new ERLCService();

    public static void registerCommands(JDA jda) {
        jda.updateCommands().addCommands(
            // Global Command 1: API Key Setup (Restricted to Server Owners)
            Commands.slash("erlc-apikey", "Set the PRC API key for this server")
                .addOption(OptionType.STRING, "key", "The Server-Key from ER:LC settings", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),

            // Global Command 2: Execute In-Game Commands
            Commands.slash("run-command", "Execute an ER:LC server command")
                .addOption(OptionType.STRING, "command", "Example: :h Hello Server!", true)
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        if (event.getName().equals("erlc-apikey")) {
            // Check if the user is the Server Owner
            if (!event.getMember().isOwner()) {
                event.reply("Only the Server Owner can use this command.").setEphemeral(true).queue();
                return;
            }

            String key = event.getOption("key").getAsString();
            saveKey(guildId, key);
            event.reply("Key saved for this server!").setEphemeral(true).queue();
        } 
        
        else if (event.getName().equals("run-command")) {
            String apiKey = getKey(guildId);
            if (apiKey == null) {
                event.reply("No API key found. Use `/erlc-apikey` first.").setEphemeral(true).queue();
                return;
            }

            String command = event.getOption("command").getAsString();
            event.deferReply().queue(); // Inform Discord we are working on it

            erlcService.sendCommand(apiKey, command).thenAccept(response -> {
                event.getHook().sendMessage("PRC Response: " + response).queue();
            });
        }
    }

    private void saveKey(String guildId, String key) {
        Properties props = new Properties();
        File file = new File(KEYS_FILE);
        try {
            if (file.exists()) {
                try (InputStream in = new FileInputStream(file)) { props.load(in); }
            }
            props.setProperty(guildId, key);
            try (OutputStream out = new FileOutputStream(file)) { props.store(out, null); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private String getKey(String guildId) {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(KEYS_FILE)) {
            props.load(in);
            return props.getProperty(guildId);
        } catch (IOException e) { return null; }
    }
}
