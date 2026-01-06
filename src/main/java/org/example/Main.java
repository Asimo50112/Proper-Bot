package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import java.io.*;
import java.util.Properties;

public class Main {
    private static String token;

    public static void main(String[] args) throws Exception {
        // Step 1: Load config from bot-token.properties
        if (!loadConfig()) {
            System.out.println("-------------------------------------------------------");
            System.out.println("CONFIG CREATED: Please put your token in bot-token.properties");
            System.out.println("-------------------------------------------------------");
            return;
        }

        // Step 2: Initialize JDA
        try {
            JDA jda = JDABuilder.createDefault(token)
                    // CRITICAL: Required to see members and check roles for Vehicle Guard /c
                    .enableIntents(GatewayIntent.GUILD_MEMBERS) 
                    .addEventListeners(
                            new ERLCSetupCommand(),   // /erlc-apikey
                            new ERLCRemoteCommand(),  // /c
                            new JoinCommand(),        // /join
                            new ERLCStatusCommand(),  // /status
                            new ERLCPlayersCommand(), // /players
                            new PurgeCommand(),       // Fixed: Added Comma
                            new ERLCVehicleGuard()    // Fixed: Added Comma
                    )
                    .build()
                    .awaitReady();

            // Step 3: Register all slash commands with Discord
            jda.updateCommands().addCommands(
                    ERLCSetupCommand.getCommandData(),
                    ERLCRemoteCommand.getCommandData(),
                    JoinCommand.getCommandData(),
                    ERLCStatusCommand.getCommandData(),
                    ERLCPlayersCommand.getCommandData(),
                    PurgeCommand.getCommandData(),
                    ERLCVehicleGuard.getCommandData()
            ).queue();

            System.out.println("Bot is online as: " + jda.getSelfUser().getName());
        } catch (Exception e) {
            System.err.println("Failed to login! Your token in bot-token.properties is likely wrong.");
            e.printStackTrace();
        }
    }

    private static boolean loadConfig() {
        Properties prop = new Properties();
        File configFile = new File("bot-token.properties");

        try {
            if (!configFile.exists()) {
                try (OutputStream output = new FileOutputStream(configFile)) {
                    prop.setProperty("bot_token", "INSERT_TOKEN_HERE");
                    prop.store(output, "Discord Bot Token");
                }
                return false;
            }

            try (InputStream input = new FileInputStream(configFile)) {
                prop.load(input);
                token = prop.getProperty("bot_token");
                return token != null && !token.equals("INSERT_TOKEN_HERE") && !token.isEmpty();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }
}
