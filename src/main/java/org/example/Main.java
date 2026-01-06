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
            ERLCVehicleGuard vehicleGuard = new ERLCVehicleGuard();

            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .setMemberCachePolicy(MemberCachePolicy.ALL) // Cache everyone
                    .setChunkingFilter(ChunkingFilter.ALL)      // Download everyone on start
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

            jda.updateCommands().addCommands(
                    ERLCSetupCommand.getCommandData(),
                    ERLCRemoteCommand.getCommandData(),
                    JoinCommand.getCommandData(),
                    ERLCStatusCommand.getCommandData(),
                    ERLCPlayersCommand.getCommandData(),
                    PurgeCommand.getCommandData(),
                    ERLCVehicleGuard.getCommandData()
            ).queue();

            // Background Loop
            ScheduledExecutorService scannerLoop = Executors.newSingleThreadScheduledExecutor();
            scannerLoop.scheduleAtFixedRate(() -> {
                for (Guild guild : jda.getGuilds()) {
                    vehicleGuard.performScan(guild);
                }
            }, 10, 20, TimeUnit.SECONDS);

            System.out.println("Bot Started! Monitoring vehicles every 20 seconds.");

        } catch (Exception e) { e.printStackTrace(); }
    }

    private static boolean loadConfig() {
        Properties prop = new Properties();
        File f = new File("bot-token.properties");
        try {
            if (!f.exists()) {
                try (OutputStream o = new FileOutputStream(f)) {
                    prop.setProperty("bot_token", "TOKEN_HERE");
                    prop.store(o, null);
                }
                return false;
            }
            try (InputStream i = new FileInputStream(f)) {
                prop.load(i);
                token = prop.getProperty("bot_token");
                return token != null && !token.equals("TOKEN_HERE");
            }
        } catch (IOException e) { return false; }
    }
}
