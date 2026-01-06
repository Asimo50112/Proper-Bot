package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import java.io.*;
import java.util.Properties;

public class Main {
    private static String token;

    public static void main(String[] args) throws Exception {
        if (!loadConfig()) return;

        try {
            // Pre-initialize JDA to pass it to the Guard
            JDABuilder builder = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                    .setMemberCachePolicy(MemberCachePolicy.ALL);

            JDA jda = builder.build().awaitReady();

            // Create Guard and Register it
            ERLCVehicleGuard vehicleGuard = new ERLCVehicleGuard(jda);
            jda.addEventListener(vehicleGuard);

            // Sync Commands
            jda.updateCommands().addCommands(
                    Commands.slash("vehicle-restrictions", "Vehicle restriction system")
                            .addSubcommands(
                                    new SubcommandData("add", "Restrict a car to a role")
                                            .addOption(OptionType.STRING, "carname", "Exact PRC Car Name", true)
                                            .addOption(OptionType.ROLE, "role", "Authorized Role", true),
                                    new SubcommandData("scan", "Manually trigger scan")
                            )
            ).queue();

            System.out.println("Bot Started. Vehicle Monitoring running every 20s.");

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
                System.out.println("Please set token in bot-token.properties");
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