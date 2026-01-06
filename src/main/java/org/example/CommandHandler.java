package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        // ... (Your config loading logic here)

        JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(new CommandHandler())
                .build()
                .awaitReady(); // Important: wait for bot to be ready before registering

        // Tell the handler to register all commands defined in that class
        CommandHandler.registerCommands(jda);
        
        System.out.println("Bot is online and commands are registered!");
    }
}
