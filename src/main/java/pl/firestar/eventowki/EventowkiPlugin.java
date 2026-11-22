package pl.firestar.eventowki;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class EventowkiPlugin extends JavaPlugin implements Listener {
    private Items items;
    private SandCastle castle;
    private Hearts hearts;
    private Gear gear;
    private Gui gui;
    private Abilities abilities;
    private final Map<String, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new Items(this);
        items.load();
        castle = new SandCastle(this);
        hearts = new Hearts(this);
        gear = new Gear(this);
        gui = new Gui(this);
        abilities = new Abilities(this);
        for (Listener l : new Listener[]{this, castle, hearts, gear, gui, abilities})
            Bukkit.getPluginManager().registerEvents(l, this);
        getLogger().info("Wczytano " + items.ids().size() + " eventowek.");
    }

    @Override
    public void onDisable() {
        if (castle != null) castle.restoreAll();
    }

    public Items items() { return items; }
    public SandCastle castle() { return castle; }
    public Hearts hearts() { return hearts; }
    public Gear gear() { return gear; }
    public Gui gui() { return gui; }

    // ---------- cooldown ----------
    public long cooldownLeft(Player p, String key) {
        Long t = cooldowns.get(p.getUniqueId() + ":" + key);
        return t == null ? 0 : Math.max(0, t - System.currentTimeMillis());
    }

    /** true = mozna uzyc (i od razu startuje cooldown). */
    public boolean checkCooldown(Player p, String key, int seconds) {
        long left = cooldownLeft(p, key);
        if (left > 0) {
            p.sendActionBar(Items.color("&cPoczekaj jeszcze &e" + (left / 1000 + 1) + "s"));
            return false;
        }
        if (seconds > 0) cooldowns.put(p.getUniqueId() + ":" + key, System.currentTimeMillis() + seconds * 1000L);
        return true;
    }

    // ---------- resource pack ----------
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String url = getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank()) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            String sha = getConfig().getString("resource-pack.sha1", "");
            byte[] hash = null;
            if (sha != null && sha.length() == 40) {
                hash = new byte[20];
                for (int i = 0; i < 20; i++)
                    hash[i] = (byte) Integer.parseInt(sha.substring(i * 2, i * 2 + 2), 16);
            }
            Component prompt = Items.color(getConfig().getString("resource-pack.prompt", ""));
            p.setResourcePack(UUID.nameUUIDFromBytes(url.getBytes()), url, hash, prompt,
                    getConfig().getBoolean("resource-pack.force", true));
        }, 20L);
    }

    // ---------- komendy ----------
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("szczesciarz")) {
            if (s instanceof Player p) gear.szczesciarz(p);
            return true;
        }
        if (a.length == 0) {
            if (s instanceof Player p) gui.open(p, 0);
            else s.sendMessage("Tylko dla graczy.");
            return true;
        }
        if (!s.hasPermission("eventowki.admin")) {
            s.sendMessage(Items.color("&cBrak uprawnień."));
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                items.load();
                s.sendMessage(Items.color("&aPrzeładowano config (" + items.ids().size() + " eventówek)."));
            }
            case "list" -> s.sendMessage(Items.color("&dEventówki: &7" + String.join(", ", items.ids())));
            case "give" -> {
                if (a.length < 3) { s.sendMessage(Items.color("&cUżycie: /eventowki give <gracz> <id> [ilość]")); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { s.sendMessage(Items.color("&cGracza nie ma online.")); return true; }
                int n = 1;
                if (a.length > 3) try { n = Math.max(1, Math.min(64, Integer.parseInt(a[3]))); } catch (NumberFormatException ignored) {}
                ItemStack it = items.create(a[2], n);
                if (it == null) { s.sendMessage(Items.color("&cNie ma takiej eventówki. /eventowki list")); return true; }
                t.getInventory().addItem(it).values().forEach(r -> t.getWorld().dropItemNaturally(t.getLocation(), r));
                s.sendMessage(Items.color("&aDano &e" + n + "x " + a[2] + " &agraczowi &e" + t.getName()));
            }
            default -> s.sendMessage(Items.color("&cUżycie: /eventowki [give|list|reload]"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("eventowki.admin") || c.getName().equalsIgnoreCase("szczesciarz")) return out;
        if (a.length == 1) out.addAll(List.of("give", "list", "reload"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("give")) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        else if (a.length == 3 && a[0].equalsIgnoreCase("give")) out.addAll(items.ids());
        out.removeIf(x -> !x.toLowerCase().startsWith(a[a.length - 1].toLowerCase()));
        return out;
    }
}
