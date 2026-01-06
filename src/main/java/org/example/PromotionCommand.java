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
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("promote")) return;

        // 1. Tell Discord to wait (Stops the "thinking" timeout)
        event.deferReply().queue();

        try {
            Member target = event.getOption("user").getAsMember();
            Role role = event.getOption("role").getAsRole();

            if (target == null) {
                event.getHook().sendMessage("❌ User not found.").queue();
                return;
            }

            // 2. Add the role
            event.getGuild().addRoleToMember(target, role).queue(
                success -> {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("🎊 New Promotion! 🎊");
                    eb.setDescription("Congratulations to " + target.getAsMention() + " on their new rank!");
                    eb.addField("New Role", role.getAsMention(), true);
                    eb.addField("Promoted By", event.getUser().getAsMention(), true);
                    eb.setColor(Color.GREEN);
                    eb.setThumbnail(target.getEffectiveAvatarUrl());
                    eb.setTimestamp(Instant.now());

                    event.getHook().sendMessageEmbeds(eb.build()).queue();
                },
                error -> {
                    // This triggers if the Bot Role is lower than the target Role
                    event.getHook().sendMessage("❌ **Hierarchy Error:** My role must be HIGHER than " + role.getName() + " in Server Settings.")
                         .setEphemeral(true).queue();
                }
            );
        } catch (Exception e) {
            event.getHook().sendMessage("❌ An internal error occurred. Check console.").queue();
            e.printStackTrace();
        }
    }
}
