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
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.Color;
import java.time.Instant;

public class RoleCommand extends ListenerAdapter {

    public static CommandData getCommandData() {
        return Commands.slash("role", "Give or remove a role from a member")
                .addOptions(
                    new OptionData(OptionType.STRING, "action", "Add or remove the role", true)
                        .addChoice("Give", "add")
                        .addChoice("Remove", "remove"),
                    new OptionData(OptionType.USER, "user", "The member to manage", true),
                    new OptionData(OptionType.ROLE, "role", "The role to toggle", true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("role")) return;

        event.deferReply().queue();

        String action = event.getOption("action").getAsString();
        Member target = event.getOption("user").getAsMember();
        Role role = event.getOption("role").getAsRole();

        if (target == null) {
            event.getHook().sendMessage("❌ User not found.").setEphemeral(true).queue();
            return;
        }

        // Hierarchy Safety Check
        if (!event.getGuild().getSelfMember().canInteract(role)) {
            event.getHook().sendMessage("❌ **Hierarchy Error:** My role must be higher than " + role.getAsMention() + " to manage it.")
                    .setEphemeral(true).queue();
            return;
        }

        if (action.equals("add")) {
            event.getGuild().addRoleToMember(target, role).queue(
                success -> sendSuccessEmbed(event, target, role, "Given"),
                error -> event.getHook().sendMessage("❌ Failed to add role.").setEphemeral(true).queue()
            );
        } else {
            event.getGuild().removeRoleFromMember(target, role).queue(
                success -> sendSuccessEmbed(event, target, role, "Removed"),
                error -> event.getHook().sendMessage("❌ Failed to remove role.").setEphemeral(true).queue()
            );
        }
    }

    private void sendSuccessEmbed(SlashCommandInteractionEvent event, Member target, Role role, String actionType) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Role Updated");
        eb.setDescription("Successfully **" + actionType + "** the role " + role.getAsMention() + " to " + target.getAsMention());
        eb.setColor(actionType.equals("Given") ? Color.GREEN : Color.ORANGE);
        eb.setThumbnail(target.getEffectiveAvatarUrl());
        eb.setFooter("Actioned by " + event.getUser().getName());
        eb.setTimestamp(Instant.now());

        // Hides the "thinking" command trace and sends clean messages
        event.getHook().deleteOriginal().queue();
        event.getChannel().sendMessage(target.getAsMention() + ", your roles have been updated.").queue();
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }
}
