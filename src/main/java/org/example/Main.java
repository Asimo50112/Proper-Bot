package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import java.io.*;
import java.util.Properties;

public class Main {
    private static String token;

    public static void main(String[] args) throws Exception {
        if (!loadConfig()) {
            System.out.println("CONFIG CREATED: Please put your token in bot-token.properties");
            return;
        }

        try {
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                    .setMemberCachePolicy(MemberCachePolicy.ALL) // Critical for role checks
                    .build()
                    .awaitReady();

            // FIX 1: Pass 'jda' into the constructor
            // This starts the 20-second monitor automatically
            new ERLCVehicleGuard(jda);

            // FIX 2: Manually add the command data since the static method was removed
            // Note: If you have other command classes, add them here too
            jda.updateCommands().addCommands(
                    net.dv8tion.jda.api.interactions.commands.build.Commands.slash("vehicle-restrictions", "Vehicle restriction system")
                            .addSubcommands(
                                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("add", "Restrict a car to a role")
                                            .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "carname", "Exact PRC Car Name", true)
                                            .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.ROLE, "role", "Authorized Role", true),
                                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("scan", "Manually trigger scan")
                            )
            ).queue();

            System.out.println("Bot is online as: " + jda.getSelfUser().getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean loadConfig() {
        Properties prop = new Properties();
        File f = new File("bot-token.properties");
        try {
            if (!f.exists()) {
                try (OutputStream o = new FileOutputStream(f)) {
                    prop.setProperty("bot_token", "INSERT_TOKEN_HERE");
                    prop.store(o, null);
                }
                return false;
            }
            try (InputStream i = new FileInputStream(f)) {
                prop.load(i);
                token = prop.getProperty("bot_token");
                return token != null && !token.equals("INSERT_TOKEN_HERE");
            }
        } catch (IOException e) { return false; }
    }
}