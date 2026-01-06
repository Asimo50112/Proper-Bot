package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import java.io.*;
import java.util.Properties;

public class Main {

    private static final String CONFIG_FILE = "bot-token.properties";
    private static String token;

    public static void main(String[] args) {
        handleConfig();

        // Initialize JDA and register the new CommandHandler listener
        JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(new CommandHandler()) // <--- Registering the class
                .build();

        // Define Global Commands
        var globalCommands = jda.updateCommands().addCommands(
            Commands.slash("echo", "Repeat a message")
                .addOption(OptionType.STRING, "message", "The words to repeat", true)
        );

        globalCommands.queue();
    }

    private static void handleConfig() {
        Properties prop = new Properties();
        File file = new File(CONFIG_FILE);

        if (!file.exists()) {
            try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
                prop.setProperty("bot_token", "YOUR_TOKEN_HERE");
                prop.store(output, "Discord Bot Configuration");
                System.out.println("Created bot-token.properties. Please add your token and restart.");
                System.exit(0); 
            } catch (IOException io) {
                io.printStackTrace();
            }
        } else {
            try (InputStream input = new FileInputStream(CONFIG_FILE)) {
                prop.load(input);
                token = prop.getProperty("bot_token");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
