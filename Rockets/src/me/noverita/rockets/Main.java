package me.noverita.rockets;

import com.earth2me.essentials.Essentials;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    protected static Main instance;
    protected static Essentials essentials = Essentials.getPlugin(Essentials.class);

    public void onEnable() {
        instance = this;
        Bukkit.getPluginManager().registerEvents(new RocketListeners(), this);
    }

    public void onDisable() {

    }
}
