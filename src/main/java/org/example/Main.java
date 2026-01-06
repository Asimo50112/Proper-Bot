package org.example;

import net.dv8tion.jda.api.JDABuilder;

public class Main {
    public static void main(String[] args) throws Exception {
        var jda = JDABuilder.createDefault("YOUR_BOT_TOKEN")
                .addEventListeners(new ERLCCommandHandler())
                .build().awaitReady();
        ERLCCommandHandler.registerCommands(jda);
    }
}
