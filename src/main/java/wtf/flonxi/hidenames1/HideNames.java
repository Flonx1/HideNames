package wtf.flonxi.hidenames1;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HideNames extends JavaPlugin implements Listener {

    private FileConfiguration engConfig;
    private FileConfiguration ruConfig;
    private FileConfiguration config;
    private Map<UUID, String> playerLanguages = new HashMap<>();
    private Map<UUID, String> playerCodes = new HashMap<>();
    private Team hiddenNamesTeam;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLanguageFiles();
        setupScoreboardTeam();

        getServer().getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            setupPlayer(player);
        }
    }

    @Override
    public void onDisable() {
        if (hiddenNamesTeam != null) {
            hiddenNamesTeam.unregister();
        }
    }

    private void loadLanguageFiles() {
        File engFile = new File(getDataFolder(), "lang/eng.yml");
        File ruFile = new File(getDataFolder(), "lang/ru.yml");

        if (!engFile.exists()) saveResource("lang/eng.yml", false);
        if (!ruFile.exists()) saveResource("lang/ru.yml", false);

        engConfig = YamlConfiguration.loadConfiguration(engFile);
        ruConfig = YamlConfiguration.loadConfiguration(ruFile);
        config = getConfig();
    }

    private void setupScoreboardTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        Team existingTeam = scoreboard.getTeam("hiddenNames");
        if (existingTeam != null) {
            existingTeam.unregister();
        }

        hiddenNamesTeam = scoreboard.registerNewTeam("hiddenNames");
        hiddenNamesTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        hiddenNamesTeam.setCanSeeFriendlyInvisibles(false);

        for (Player player : Bukkit.getOnlinePlayers()) {
            hiddenNamesTeam.addEntry(player.getName());
        }
    }

    private void setupPlayer(Player player) {
        String language = config.getString("language", "auto");

        if (language.equals("auto")) {
            String locale = player.getLocale().toLowerCase();
            if (locale.startsWith("ru")) {
                playerLanguages.put(player.getUniqueId(), "ru");
            } else {
                playerLanguages.put(player.getUniqueId(), "eng");
            }
        } else {
            playerLanguages.put(player.getUniqueId(), language);
        }

        String code = generateCode();
        playerCodes.put(player.getUniqueId(), code);

        if (config.getBoolean("modules.nametag", true)) {
            hiddenNamesTeam.addEntry(player.getName());
        }

        updatePlayerDisplay(player);
    }

    private String generateCode() {
        return ChatColor.MAGIC + "########" + ChatColor.RESET;
    }

    private String getMessage(Player player, String path) {
        String lang = playerLanguages.get(player.getUniqueId());
        FileConfiguration langConfig = lang.equals("ru") ? ruConfig : engConfig;
        return ChatColor.translateAlternateColorCodes('&', langConfig.getString(path, path));
    }

    private void updatePlayerDisplay(Player player) {
        String code = playerCodes.get(player.getUniqueId());

        if (config.getBoolean("modules.tab", true)) {
            player.setPlayerListName(code);
        }

        if (config.getBoolean("modules.displayname", true)) {
            player.setDisplayName(code);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        setupPlayer(player);

        if (config.getBoolean("modules.join-quit", true)) {
            String code = playerCodes.get(player.getUniqueId());
            String message = getMessage(player, "join-message")
                    .replace("%player%", code);
            event.setJoinMessage(message);
        } else {
            event.setJoinMessage(null);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (config.getBoolean("modules.join-quit", true)) {
            String code = playerCodes.get(player.getUniqueId());
            String message = getMessage(player, "quit-message")
                    .replace("%player%", code);
            event.setQuitMessage(message);
        } else {
            event.setQuitMessage(null);
        }

        if (config.getBoolean("modules.nametag", true)) {
            hiddenNamesTeam.removeEntry(player.getName());
        }

        playerLanguages.remove(player.getUniqueId());
        playerCodes.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!config.getBoolean("modules.chat", true)) return;

        Player player = event.getPlayer();
        String code = playerCodes.get(player.getUniqueId());

        String format = getMessage(player, "chat-format")
                .replace("%player%", code)
                .replace("%message%", event.getMessage());

        event.setFormat(format);

        for (Player online : Bukkit.getOnlinePlayers()) {
            String onlineCode = playerCodes.get(online.getUniqueId());
            event.setMessage(event.getMessage().replace(online.getName(), onlineCode));
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!config.getBoolean("modules.death", true)) return;

        Player player = event.getEntity();
        String code = playerCodes.get(player.getUniqueId());

        if (event.getDeathMessage() != null) {
            String deathMessage = event.getDeathMessage()
                    .replace(player.getName(), code);

            for (Player online : Bukkit.getOnlinePlayers()) {
                String onlineCode = playerCodes.get(online.getUniqueId());
                deathMessage = deathMessage.replace(online.getName(), onlineCode);
            }

            event.setDeathMessage(deathMessage);
        }
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (!config.getBoolean("modules.tabcomplete", true)) return;

        if (event.getSender() instanceof Player) {
            if (event.getBuffer().startsWith("/")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    String onlineCode = playerCodes.get(online.getUniqueId());
                    if (event.getCompletions().contains(online.getName())) {
                        event.getCompletions().remove(online.getName());
                        event.getCompletions().add(onlineCode);
                    }
                }
            }
        }
    }
}