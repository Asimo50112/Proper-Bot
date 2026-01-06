package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import java.io.*;
import java.util.Properties;

public class Main {
    private static String token;

    public static void main(String[] args) throws Exception {
        if (!loadConfig()) return;

        try {
            // Initialize JDA
            JDA jda = JDABuilder.createDefault(token)
                    // Added GUILD_PRESENCES as it often helps role-caching stay updated
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_PRESENCES)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    // CRITICAL: This forces the bot to download the member list so it can check roles
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .build()
                    .awaitReady();

            // Initialize the Guard
            ERLCVehicleGuard vehicleGuard = new ERLCVehicleGuard(jda);

            // REGISTER ALL LISTENERS
            jda.addEventListener(
                    new ERLCSetupCommand(),
                    new ERLCRemoteCommand(),
                    new JoinCommand(),
                    new ERLCStatusCommand(),
                    new ERLCPlayersCommand(),
                    new PurgeCommand(),
                    new PromotionCommand()
                    vehicleGuard
            );

            // SYNC ALL SLASH COMMANDS
            jda.updateCommands().addCommands(
                    ERLCSetupCommand.getCommandData(),
                    ERLCRemoteCommand.getCommandData(),
                    JoinCommand.getCommandData(),
                    ERLCStatusCommand.getCommandData(),
                    ERLCPlayersCommand.getCommandData(),
                    PurgeCommand.getCommandData(),
                    ERLCVehicleGuard.getCommandData(), 
                    PromotionCommand.getCommandData()
            ).queue(success -> System.out.println("Successfully synced all 7 commands."));

            System.out.println("Bot is online as: " + jda.getSelfUser().getName());

        } catch (Exception e) {
            System.err.println("Error during JDA initialization:");
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
                    prop.store(o, "Discord Bot Token");
                }
                System.out.println("Set token in bot-token.properties and restart.");
                return false;
            }
            try (InputStream i = new FileInputStream(f)) {
                prop.load(i);
                token = prop.getProperty("bot_token");
                return token != null && !token.equals("INSERT_TOKEN_HERE");
            }
        } catch (IOException e) { 
            e.printStackTrace();
            return false; 
        }
    }
}
