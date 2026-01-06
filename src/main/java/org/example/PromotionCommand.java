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

        // 1. Tell Discord to wait (Fixes "Application didn't respond")
        event.deferReply().queue();

        Member target = event.getOption("user").getAsMember();
        Role role = event.getOption("role").getAsRole();

        if (target == null) {
            event.getHook().sendMessage("Could not find that user.").setEphemeral(true).queue();
            return;
        }

        // 2. Attempt to add role
        event.getGuild().addRoleToMember(target, role).queue(
            success -> {
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("🎊 New Promotion! 🎊");
                eb.setDescription("Congratulations to " + target.getAsMention() + " on their new rank!");
                eb.addField("New Role", role.getAsMention(), true);
                eb.addField("Promoted By", event.getUser().getAsMention(), true);
                eb.setColor(Color.GREEN);
                eb.setThumbnail(target.getEffectiveAvatarUrl());
                eb.setFooter("Promotion System", event.getGuild().getIconUrl());
                eb.setTimestamp(Instant.now());

                // Use getHook() because we deferred the reply
                event.getHook().sendMessageEmbeds(eb.build()).queue();
            },
            error -> {
                event.getHook().sendMessage("❌ Error: I cannot give that role. Make sure my role is HIGHER than the role you are trying to give in the server settings.")
                     .setEphemeral(true).queue();
            }
        );
    }
}
