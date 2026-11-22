package pl.firestar.eventowki;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/** Zamek z Piasku: 9x9, wieze, blanki, brama, strzelnice, flagi + boss bar z odliczaniem. */
public class SandCastle implements Listener {
    private final EventowkiPlugin pl;
    private final Map<Location, BlockData> saved = new HashMap<>();
    private final List<Location> centers = new ArrayList<>();

    public SandCastle(EventowkiPlugin pl) { this.pl = pl; }

    /** null = nie ruszaj bloku. */
    private Material at(int x, int y, int z) {
        int ax = Math.abs(x), az = Math.abs(z);
        boolean tower = ax >= 3 && az >= 3;
        boolean wall = (ax == 4 || az == 4);
        boolean inside = ax <= 3 && az <= 3;

        if (y == -1) return Material.SMOOTH_SANDSTONE;
        if (ax == 4 && az == 4) {
            if (y == 9 || y == 10) return Material.OAK_FENCE;
            if (y == 11) return Material.RED_BANNER;
        }
        if (tower) {
            if (y <= 7) return ((x + y + z) & 1) == 0 ? Material.SANDSTONE : Material.CUT_SANDSTONE;
            if (y == 8) return ((x + z) & 1) == 0 ? Material.SANDSTONE_WALL : null;
            return null;
        }
        if (wall) {
            if (z == -4 && ax <= 1 && y <= 2)
                return (y == 2 && ax == 1) ? Material.SANDSTONE : Material.DARK_OAK_PLANKS;
            if (y == 3 && ((ax == 4 && az == 2) || (az == 4 && ax == 2))) return Material.IRON_BARS;
            if (y <= 4) return ((x + y + z) & 1) == 0 ? Material.SANDSTONE : Material.CUT_SANDSTONE;
            if (y == 5) return ((x + z) & 1) == 0 ? Material.SANDSTONE_WALL : null;
            return null;
        }
        if (inside && y >= 0 && y <= 11) return Material.AIR;
        return null;
    }

    public void build(Location where, int seconds) {
        final Location base = where.getBlock().getLocation();
        final World w = base.getWorld();
        final Location mid = base.clone().add(0.5, 0, 0.5);
        final Set<Player> trapped = new HashSet<>();

        for (Player t : w.getPlayers()) {
            Location l = t.getLocation();
            int dx = l.getBlockX() - base.getBlockX();
            int dz = l.getBlockZ() - base.getBlockZ();
            int dy = l.getBlockY() - base.getBlockY();
            int m = Math.max(Math.abs(dx), Math.abs(dz));
            if (m > 4 || dy < -2 || dy > 11) continue;
            trapped.add(t);
            // gracze stojacy w murze/wiezy zostaja przeniesieni do srodka
            if (m == 4 || (Math.abs(dx) >= 3 && Math.abs(dz) >= 3)) t.teleport(mid);
        }

        centers.add(base);
        final List<Location> changed = new ArrayList<>();
        for (int y = -1; y <= 11; y++) {
            final int fy = y;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                for (int x = -4; x <= 4; x++)
                    for (int z = -4; z <= 4; z++) {
                        Material m = at(x, fy, z);
                        if (m == null) continue;
                        Block b = base.clone().add(x, fy, z).getBlock();
                        if (b.getType().getHardness() < 0) continue;       // bedrock itp.
                        if (m == Material.AIR && b.getType().isAir()) continue;
                        saved.putIfAbsent(b.getLocation(), b.getBlockData());
                        b.setType(m, false);
                        changed.add(b.getLocation());
                    }
                w.playSound(base, Sound.BLOCK_SAND_PLACE, 1f, 0.6f + (fy + 1) * 0.05f);
                w.spawnParticle(Particle.BLOCK, base.clone().add(0.5, fy + 0.5, 0.5), 40, 3, 0.2, 3,
                        Material.SAND.createBlockData());
            }, (y + 1) * 2L);
        }

        // ---- boss bar z odliczaniem ----
        BossBar bar = Bukkit.createBossBar("§6§l" + Items.smallCaps("zamek") + " §8» §e" + seconds + "s",
                BarColor.YELLOW, BarStyle.SEGMENTED_10);
        for (Player t : trapped) {
            bar.addPlayer(t);
            t.sendTitle("§6§l" + Items.smallCaps("zamkniety w zamku!"), "§7na " + seconds + " sekund", 5, 40, 10);
        }
        new BukkitRunnable() {
            int left = seconds;
            @Override public void run() {
                if (left <= 0) {
                    bar.removeAll();
                    remove(changed, w, base);
                    cancel();
                    return;
                }
                bar.setProgress(Math.max(0.0, Math.min(1.0, left / (double) seconds)));
                bar.setTitle("§6§l" + Items.smallCaps("zamek") + " §8» " + (left <= 3 ? "§c" : "§e") + left + "s");
                if (left <= 3) {
                    bar.setColor(BarColor.RED);
                    for (Player t : bar.getPlayers()) t.playSound(t.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
                }
                left--;
            }
        }.runTaskTimer(pl, 30L, 20L);
    }

    private void remove(List<Location> locs, World w, Location base) {
        locs.sort((a, b) -> Integer.compare(b.getBlockY(), a.getBlockY()));   // znika od gory
        for (int i = 0; i < locs.size(); i++) {
            final Location l = locs.get(i);
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                BlockData old = saved.remove(l);
                if (old != null) l.getBlock().setBlockData(old, false);
            }, i / 40);
        }
        w.playSound(base, Sound.BLOCK_SAND_BREAK, 1f, 0.7f);
        long done = locs.size() / 40 + 2;
        Bukkit.getScheduler().runTaskLater(pl, () -> centers.remove(base), done);
    }

    public void restoreAll() {
        for (Map.Entry<Location, BlockData> e : saved.entrySet()) e.getKey().getBlock().setBlockData(e.getValue(), false);
        saved.clear();
        centers.clear();
    }

    private boolean inCastle(Location l) {
        for (Location c : centers) {
            if (!c.getWorld().equals(l.getWorld())) continue;
            int dx = Math.abs(l.getBlockX() - c.getBlockX());
            int dz = Math.abs(l.getBlockZ() - c.getBlockZ());
            int dy = l.getBlockY() - c.getBlockY();
            if (dx <= 4 && dz <= 4 && dy >= -1 && dy <= 11) return true;
        }
        return false;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (saved.containsKey(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) { e.blockList().removeIf(b -> saved.containsKey(b.getLocation())); }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) { e.blockList().removeIf(b -> saved.containsKey(b.getLocation())); }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if ((c == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || c == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT)
                && inCastle(e.getFrom())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Items.color("&cNie możesz uciec z zamku!"));
        }
    }

    /** uzywane przez bombe/dynamit - blokady dla blokow zamku. */
    public boolean isCastleBlock(Location l) { return saved.containsKey(l); }
}
