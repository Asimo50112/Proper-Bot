package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;

public class CommandHandler extends ListenerAdapter {

    // This method is called from Main to set up the commands
    public static void registerCommands(JDA jda) {
        jda.updateCommands().addCommands(
            // The API Key command (Owner Only)
            Commands.slash("erlc-apikey", "Set the PRC API key for this server")
                .addOption(OptionType.STRING, "key", "Your Server-Key from ER:LC", true),

            // The Command execution command
            Commands.slash("run-command", "Run a command in your ER:LC server")
                .addOption(OptionType.STRING, "command", "The command (e.g. :h Hello)", true),

            // The Global echo command
            Commands.slash("echo", "Repeat a message")
                .addOption(OptionType.STRING, "message", "The words to repeat", true)
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        // ... (Same logic as before to handle the commands)
    }
}
