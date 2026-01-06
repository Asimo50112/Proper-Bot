package org.example;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;

public class CommandHandler extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        // 1. Identify which command was typed
        String commandName = event.getName();

        switch (commandName) {
            case "echo":
                handleEcho(event);
                break;
                
            case "ping":
                event.reply("Pong! 🏓").queue();
                break;

            default:
                event.reply("Unknown command!").setEphemeral(true).queue();
                break;
        }
    }

    /**
     * Logic for the /echo command
     */
    private void handleEcho(SlashCommandInteractionEvent event) {
        // Retrieve the "message" option we defined in Main.java
        OptionMapping messageOption = event.getOption("message");

        if (messageOption == null) {
            event.reply("You didn't provide a message!").setEphemeral(true).queue();
            return;
        }

        String userMessage = messageOption.getAsString();
        
        // Reply back to the user
        event.reply("You said: **" + userMessage + "**").queue();
    }
}
