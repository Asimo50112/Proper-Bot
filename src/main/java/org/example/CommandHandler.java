package org.example;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import java.io.*;
import java.util.Properties;

public class CommandHandler extends ListenerAdapter {
    private static final String KEYS_FILE = "guild-keys.properties";
    private final ERLCService erlcService = new ERLCService();

    // ... (Your existing registerCommands and onSlashCommandInteraction) ...

    private void checkFile() {
        File file = new File(KEYS_FILE);
        if (!file.exists()) {
            try {
                if (file.createNewFile()) {
                    System.out.println("Successfully created " + KEYS_FILE);
                }
            } catch (IOException e) {
                System.err.println("Could not create API key file: " + e.getMessage());
            }
        }
    }

    private void saveKey(String guildId, String key) {
        checkFile(); // Ensure file exists
        Properties props = new Properties();
        
        // 1. Load existing keys so we don't overwrite other servers
        try (InputStream in = new FileInputStream(KEYS_FILE)) {
            props.load(in);
        } catch (IOException e) {
            // It's okay if it fails to load (empty file)
        }

        // 2. Add the new key
        props.setProperty(guildId, key);

        // 3. Save back to file
        try (OutputStream out = new FileOutputStream(KEYS_FILE)) {
            props.store(out, "Guild-Specific ERLC API Keys");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getKey(String guildId) {
        File file = new File(KEYS_FILE);
        if (!file.exists()) return null;

        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
            return props.getProperty(guildId);
        } catch (IOException e) {
            return null;
        }
    }
}
