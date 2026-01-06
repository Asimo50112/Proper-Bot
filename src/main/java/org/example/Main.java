package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import java.io.*;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    private static String token;

    public static void main(String[] args) throws Exception {
        if (!loadConfig()) {
            System.out.println("-------------------------------------------------------");
            System.out.println("CONFIG CREATED: Please put your token in bot-token.properties");
            System.out.println("-------------------------------------------------------");
            return;
        }

        try {
            // 1. Create the Vehicle Guard instance FIRST so we can use it in the loop
            ERLCVehicleGuard vehicleGuard = new ERLCVehicleGuard();

            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES) 
                    .addEventListeners(
                            new ERLCSetupCommand(),
                            new ERLCRemoteCommand(),
                            new JoinCommand(),
                            new ERLCStatusCommand(),
                            new ERLCPlayersCommand(),
                            new PurgeCommand(),
                            vehicleGuard // Use the instance created above
                    )
                    .build()
                    .awaitReady();

            // 2. Register Slash Commands
            jda.updateCommands().addCommands(
                    ERLCSetupCommand.getCommandData(),
                    ERLCRemoteCommand.getCommandData(),
                    JoinCommand.getCommandData(),
                    ERLCStatusCommand.getCommandData(),
                    ERLCPlayersCommand.getCommandData(),
                    PurgeCommand.getCommandData(),
                    ERLCVehicleGuard.getCommandData()
            ).queue();

            // 3. START THE 20-SECOND LOOP
            ScheduledExecutorService scannerLoop = Executors.newSingleThreadScheduledExecutor();
            scannerLoop.scheduleAtFixedRate(() -> {
                for (Guild guild : jda.getGuilds()) {
                    try {
                        // We call the scan method directly for every guild the bot is in
                        vehicleGuard.performScan(guild, null);
                    } catch (Exception e) {
                        System.err.println("Error scanning guild " + guild.getName() + ": " + e.getMessage());
                    }
                }
            }, 10, 20, TimeUnit.SECONDS);

            System.out.println("Bot is online as: " + jda.getSelfUser().getName());
            System.out.println("Vehicle Guard Auto-Scan started (20s interval)");

        } catch (Exception e) {
            System.err.println("Critical Error during startup:");
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
