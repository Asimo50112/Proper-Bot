package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import java.io.*;
import java.util.Properties;

public class Main {

    private static final String CONFIG_FILE = "bot-token.properties";
    private static String token;

    public static void main(String[] args) throws InterruptedException {
        // 1. Handle Configuration
        if (!loadConfig()) {
            System.out.println("Fill in your token and restart!");
            return;
        }

        // 2. Build the Bot
        JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(new CommandHandler())
                .build()
                .awaitReady();

        // 3. Register Commands (Modular)
        CommandHandler.registerCommands(jda);

        System.out.println("Bot is online as: " + jda.getSelfUser().getName());
    }

    private static boolean loadConfig() {
        Properties prop = new Properties();
        File file = new File(CONFIG_FILE);

        if (!file.exists()) {
            try (OutputStream out = new FileOutputStream(file)) {
                prop.setProperty("bot_token", "PASTE_YOUR_DISCORD_TOKEN_HERE");
                prop.store(out, "Discord Bot Configuration");
                System.out.println("File created: " + CONFIG_FILE);
                return false;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            try (InputStream in = new FileInputStream(file)) {
                prop.load(in);
                token = prop.getProperty("bot_token");
                return token != null && !token.equals("PASTE_YOUR_DISCORD_TOKEN_HERE");
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}
