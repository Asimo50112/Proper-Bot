package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import java.io.*;
import java.util.Properties;

public class Main {

    private static final String CONFIG_FILE = "bot-token.properties";
    private static String token;

    public static void main(String[] args) throws InterruptedException {
        // Step 1: Initialize configuration file and load token
        if (!loadConfig()) {
            System.out.println("Configuration file created or invalid. Please add your token to " + CONFIG_FILE + " and restart.");
            return;
        }

        // Step 2: Build JDA instance
        JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(new CommandHandler())
                .build()
                .awaitReady();

        // Step 3: Register slash commands through the CommandHandler
        CommandHandler.registerCommands(jda);

        System.out.println("Bot is online as: " + jda.getSelfUser().getName());
    }

    private static boolean loadConfig() {
        Properties prop = new Properties();
        File file = new File(CONFIG_FILE);

        if (!file.exists()) {
            try (OutputStream out = new FileOutputStream(file)) {
                prop.setProperty("bot_token", "INSERT_TOKEN_HERE");
                prop.store(out, "Discord Bot Configuration");
                return false;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            try (InputStream in = new FileInputStream(file)) {
                prop.load(in);
                token = prop.getProperty("bot_token");
                return token != null && !token.equals("INSERT_TOKEN_HERE");
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}
