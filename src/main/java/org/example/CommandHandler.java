package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Properties;

public class CommandHandler extends ListenerAdapter {
    private static final String KEYS_FILE = "guild-keys.properties";
    private final ERLCService erlcService = new ERLCService();

    public static void registerCommands(JDA jda) {
        jda.updateCommands().addCommands(
            // Setup command restricted to Administrators
            Commands.slash("erlc-apikey", "Set and verify the PRC API key")
                .addOption(OptionType.STRING, "key", "Server-Key from ER:LC", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),

            // Fast command execution
            Commands.slash("c", "Run an in-game command")
                .addOption(OptionType.STRING, "cmd", "Command content", true),

            // Server information subcommands
            Commands.slash("erlc", "Server management tools")
                .addSubcommands(new SubcommandData("status", "View server status"))
                .addSubcommands(new SubcommandData("players", "View online players"))
                .addSubcommands(new SubcommandData("killlogs", "View recent kill logs"))
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;
        String guildId = event.getGuild().getId();
        String commandName = event.getName();

        // Special handling for API key setup
        if (commandName.equals("erlc-apikey")) {
            handleApiKeySetup(event, guildId);
            return;
        }

        // Retrieve key for all other commands
        String apiKey = getSavedKey(guildId);
        if (apiKey == null) {
            event.reply("No API key configured for this server. Use /erlc-apikey first.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        // Delegate to ERLCService based on command name
        if (commandName.equals("c")) {
            String input = event.getOption("cmd").getAsString();
            erlcService.postCommand(apiKey, input)
                    .thenAccept(response -> event.getHook().sendMessage(response).queue());
        } else if (commandName.equals("erlc")) {
            handleSubcommands(event, apiKey);
        }
    }

    private void handleApiKeySetup(SlashCommandInteractionEvent event, String guildId) {
        String newKey = event.getOption("key").getAsString();
        event.deferReply(true).queue();

        // Verification handshake via ERLCService
        erlcService.verifyKey(newKey).thenAccept(isValid -> {
            if (isValid) {
                saveKey(guildId, newKey);
                event.getHook().sendMessage("API key verified and saved successfully.").queue();
            } else {
                event.getHook().sendMessage("Invalid API key. Verification failed with PRC API.").queue();
            }
        });
    }

    private void handleSubcommands(SlashCommandInteractionEvent event, String key) {
        String sub = event.getSubcommandName();
        if (sub == null) return;

        switch (sub) {
            case "status" -> erlcService.getStatus(key).thenAccept(res -> event.getHook().sendMessage("```json\n" + res + "```").queue());
            case "players" -> erlcService.getPlayers(key).thenAccept(res -> event.getHook().sendMessage("```json\n" + res + "```").queue());
            case "killlogs" -> erlcService.getKillLogs(key).thenAccept(res -> event.getHook().sendMessage("```json\n" + res + "```").queue());
        }
    }

    private void saveKey(String id, String key) {
        Properties props = new Properties();
        File file = new File(KEYS_FILE);
        try {
            if (file.exists()) {
                try (InputStream in = new FileInputStream(file)) { props.load(in); }
            }
            props.setProperty(id, key);
            try (OutputStream out = new FileOutputStream(file)) { props.store(out, "ERLC Guild Keys"); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private String getSavedKey(String id) {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(KEYS_FILE)) {
            props.load(in);
            return props.getProperty(id);
        } catch (IOException e) { return null; }
    }
}
