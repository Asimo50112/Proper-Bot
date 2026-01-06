package org.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import java.awt.Color;
import java.time.Instant;

public class PromotionCommand extends ListenerAdapter {

    public static CommandData getCommandData() {
        return Commands.slash("promote", "Promote a member to a new role")
                .addOption(OptionType.USER, "user", "The member to promote", true)
                .addOption(OptionType.ROLE, "role", "The role to give them", true)
                .addOption(OptionType.STRING, "reason", "Reason for this promotion", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("promote")) return;

        // 1. Defer the reply so Discord knows we are processing
        event.deferReply().queue();

        try {
            Member target = event.getOption("user").getAsMember();
            Role role = event.getOption("role").getAsRole();
            String reason = event.getOption("reason").getAsString();

            if (target == null) {
                event.getHook().sendMessage("❌ User not found.").setEphemeral(true).queue();
                return;
            }

            // 2. Hierarchy Check
            if (!event.getGuild().getSelfMember().canInteract(role)) {
                event.getHook().sendMessage("❌ **Hierarchy Error:** My role must be **higher** than " + role.getAsMention() + " in Server Settings.")
                        .setEphemeral(true).queue();
                return;
            }

            // 3. Add the role and send the result
            event.getGuild().addRoleToMember(target, role).reason("Promoted by " + event.getUser().getName() + ": " + reason).queue(
                success -> {
                    // Create the Embed
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("🎊 New Promotion! 🎊");
                    eb.setDescription("Congratulations to " + target.getAsMention() + " on their new rank!");
                    eb.addField("New Role", role.getAsMention(), true);
                    eb.addField("Promoted By", event.getUser().getAsMention(), true);
                    eb.addField("Reason", reason, false);
                    eb.setColor(Color.GREEN);
                    eb.setThumbnail(target.getEffectiveAvatarUrl());
                    eb.setTimestamp(Instant.now());
                    eb.setFooter("Staff Management System");

                    // 4. Send the Ping as a separate message
                    // We delete the initial 'thinking' reply and send new ones to 'hide' the command trace
                    event.getHook().deleteOriginal().queue();
                    
                    // Send Ping outside the embed
                    event.getChannel().sendMessage("Congratulations " + target.getAsMention() + "!").queue();
                    
                    // Send the Embed
                    event.getChannel().sendMessageEmbeds(eb.build()).queue();
                },
                error -> {
                    event.getHook().sendMessage("❌ Failed to add role. Ensure I have permissions.").setEphemeral(true).queue();
                }
            );
        } catch (Exception e) {
            event.getHook().sendMessage("❌ An internal error occurred.").setEphemeral(true).queue();
            e.printStackTrace();
        }
    }
}
