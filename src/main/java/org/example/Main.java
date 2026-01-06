package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import java.io.*;
import java.util.Properties;

public class Main {
    private static String token;

    public static void main(String[] args) throws Exception {
        // Step 1: Load config
        if (!loadConfig()) {
            // This message triggers if the file was just created
            System.out.println("-------------------------------------------------------");
            System.out.println("CONFIG CREATED: Please put your token in bot-token.properties");
            System.out.println("-------------------------------------------------------");
            return;
        }

        // Step 2: Initialize JDA
        try {
            JDA jda = JDABuilder.createDefault(token)
                    .addEventListeners(new ERLCCommandHandler())
                    .build()
                    .awaitReady();

            ERLCCommandHandler.registerCommands(jda);
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
                // Create the file if it doesn't exist
                try (OutputStream output = new FileOutputStream(configFile)) {
                    prop.setProperty("bot_token", "INSERT_TOKEN_HERE");
                    prop.store(output, "Discord Bot Token");
                }
                return false; 
            }

            // Load the existing file
            try (InputStream input = new FileInputStream(configFile)) {
                prop.load(input);
                token = prop.getProperty("bot_token");
                
                // Return true only if the token is valid and not the placeholder
                return token != null && !token.equals("INSERT_TOKEN_HERE") && !token.isEmpty();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }
}
