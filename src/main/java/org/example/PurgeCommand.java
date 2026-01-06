package org.example;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class PurgeCommand extends ListenerAdapter {

    public static CommandData getCommandData() {
        return Commands.slash("purge", "Delete a specific amount of messages")
                .addOption(OptionType.INTEGER, "amount", "Number of messages to delete (1-100)", true)
                // Restrict command to members with MANAGE_MESSAGES permission
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("purge")) return;

        int amount = event.getOption("amount").getAsInt();

        // Validate amount (Discord limit for bulk delete is 100)
        if (amount < 1 || amount > 100) {
            event.reply("You can only delete between 1 and 100 messages at a time.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // Defer reply so we can process the deletion first
        event.deferReply(true).queue();

        event.getChannel().getIterableHistory()
                .takeAsync(amount)
                .thenAccept(messages -> {
                    // Filter out messages older than 2 weeks (Discord restriction)
                    long twoWeeksAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14);
                    List<Message> targetMessages = messages.stream()
                            .filter(m -> m.getTimeCreated().toInstant().toEpochMilli() > twoWeeksAgo)
                            .toList();

                    if (targetMessages.isEmpty()) {
                        event.getHook().sendMessage("No messages found that are younger than 14 days.").queue();
                        return;
                    }

                    // Bulk delete
                    event.getGuildChannel().deleteMessages(targetMessages).queue(
                        success -> event.getHook().sendMessage("Successfully deleted " + targetMessages.size() + " messages.").queue(),
                        error -> event.getHook().sendMessage("Failed to delete messages. Ensure I have the Correct Permissions.").queue()
                    );
                });
    }
}
