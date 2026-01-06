package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import java.io.*;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    private static String token;

    public static void main(String[] args) throws Exception {
        if (!loadConfig()) return;

        try {
            // 1. Create the instance of the guard
            ERLCVehicleGuard vehicleGuard = new ERLCVehicleGuard();

            // 2. Build JDA with required Privileged Intents
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(
                        GatewayIntent.GUILD_MEMBERS, 
                        GatewayIntent.GUILD_MESSAGES, 
                        GatewayIntent.MESSAGE_CONTENT
                    )
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .addEventListeners(
                            new ERLCSetupCommand(),
                            new ERLCRemoteCommand(),
                            new JoinCommand(),
                            new ERLCStatusCommand(),
                            new ERLCPlayersCommand(),
                            new PurgeCommand(),
                            vehicleGuard // Register the guard instance
                    )
                    .build()
                    .awaitReady();

            // 3. Register Slash Commands
            jda.updateCommands().addCommands(
                    ERLCSetupCommand.getCommandData(),
                    ERLCRemoteCommand.getCommandData(),
                    JoinCommand.getCommandData(),
                    ERLCStatusCommand.getCommandData(),
                    ERLCPlayersCommand.getCommandData(),
                    PurgeCommand.getCommandData(),
                    ERLCVehicleGuard.getCommandData()
            ).queue();

            // 4. THE 20-SECOND AUTO-SCAN LOOP
            ScheduledExecutorService scannerLoop = Executors.newSingleThreadScheduledExecutor();
            scannerLoop.scheduleAtFixedRate(() -> {
                for (Guild guild : jda.getGuilds()) {
                    try {
                        vehicleGuard.performScan(guild);
                    } catch (Exception e) {
                        System.err.println("Scan error for " + guild.getName() + ": " + e.getMessage());
                    }
                }
            }, 10, 20, TimeUnit.SECONDS);

            System.out.println("Bot Online! Vehicle Guard scanning every 20 seconds.");

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
                System.out.println("Please set your token in bot-token.properties");
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
