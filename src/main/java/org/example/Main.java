package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
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
            // 1. Initialize the Guard instance
            ERLCVehicleGuard vehicleGuard = new ERLCVehicleGuard();

            // 2. Build JDA with High-Authority Intents
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(
                        GatewayIntent.GUILD_MEMBERS, 
                        GatewayIntent.GUILD_MESSAGES, 
                        GatewayIntent.GUILD_PRESENCES, // Helps JDA track role updates
                        GatewayIntent.MESSAGE_CONTENT
                    )
                    // Ensure the bot caches EVERYONE immediately
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    // Enable role and presence caching
                    .enableCache(CacheFlag.ROLE_SETTING, CacheFlag.ONLINE_STATUS)
                    .addEventListeners(
                            new ERLCSetupCommand(),
                            new ERLCRemoteCommand(),
                            new JoinCommand(),
                            new ERLCStatusCommand(),
                            new ERLCPlayersCommand(),
                            new PurgeCommand(),
                            vehicleGuard 
                    )
                    .build()
                    .awaitReady();

            // 3. Register Commands
            jda.updateCommands().addCommands(
                    ERLCSetupCommand.getCommandData(),
                    ERLCRemoteCommand.getCommandData(),
                    JoinCommand.getCommandData(),
                    ERLCStatusCommand.getCommandData(),
                    ERLCPlayersCommand.getCommandData(),
                    PurgeCommand.getCommandData(),
                    ERLCVehicleGuard.getCommandData()
            ).queue(success -> System.out.println("Slash commands synced."));

            // 4. THE SCANNER LOOP
            ScheduledExecutorService scannerLoop = Executors.newSingleThreadScheduledExecutor();
            scannerLoop.scheduleAtFixedRate(() -> {
                System.out.println("[System] Initializing 20-second vehicle scan...");
                for (Guild guild : jda.getGuilds()) {
                    try {
                        vehicleGuard.performScan(guild);
                    } catch (Exception e) {
                        System.err.println("[Error] Scan failed for " + guild.getName() + ": " + e.getMessage());
                    }
                }
            }, 10, 20, TimeUnit.SECONDS);

            System.out.println("Bot is online as: " + jda.getSelfUser().getName());
            System.out.println("Presence Intent enabled. Member cache: " + jda.getUsers().size());

        } catch (Exception e) {
            System.err.println("CRITICAL ERROR: Bot failed to start.");
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
                return (token != null && !token.equals("INSERT_TOKEN_HERE") && !token.isEmpty());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }
}
