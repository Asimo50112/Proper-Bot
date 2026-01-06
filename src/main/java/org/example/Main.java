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
        if (!loadConfig()) return;

        try {
            // Initialize JDA with proper caching for roles/members
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .build()
                    .awaitReady();

            // Initialize the Guard (Starts the 20s background loop automatically)
            ERLCVehicleGuard vehicleGuard = new ERLCVehicleGuard(jda);

            // REGISTER ALL LISTENERS
            jda.addEventListener(
                    new ERLCSetupCommand(),
                    new ERLCRemoteCommand(),
                    new JoinCommand(),
                    new ERLCStatusCommand(),
                    new ERLCPlayersCommand(),
                    new PurgeCommand(),
                    vehicleGuard
            );

            // SYNC ALL SLASH COMMANDS TO DISCORD
            jda.updateCommands().addCommands(
                    ERLCSetupCommand.getCommandData(),
                    ERLCRemoteCommand.getCommandData(),
                    JoinCommand.getCommandData(),
                    ERLCStatusCommand.getCommandData(),
                    ERLCPlayersCommand.getCommandData(),
                    PurgeCommand.getCommandData(),
                    ERLCVehicleGuard.getCommandData() // Added back
            ).queue(success -> System.out.println("Successfully synced all 7 commands."));

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
                System.out.println("Set token in bot-token.properties and restart.");
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
