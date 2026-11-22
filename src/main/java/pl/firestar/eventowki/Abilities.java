package pl.firestar.eventowki;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/** Wszystkie zdolnosci eventowek uzywanych PPM / uderzeniem / rzutem. */
public class Abilities implements Listener {
    private final EventowkiPlugin pl;
    private final Items items;
    private final NamespacedKey thrownKey;
    private final Map<UUID, Long> noJump = new HashMap<>();
    private final Map<UUID, Long> noGlide = new HashMap<>();
    private final Random rnd = new Random();

    public Abilities(EventowkiPlugin pl) {
        this.pl = pl;
        this.items = pl.items();
        this.thrownKey = new NamespacedKey(pl, "rzucona");
    }

    private double cfgD(String path, double def) { return pl.getConfig().getDouble("settings." + path, def); }
    private int cfgI(String path, int def) { return pl.getConfig().getInt("settings." + path, def); }

    private void consume(Player p) {
        ItemStack h = p.getInventory().getItemInMainHand();
        h.setAmount(h.getAmount() - 1);
    }

    private void fx(Player p, Sound s, float pitch) { p.playSound(p.getLocation(), s, 1f, pitch); }

    // ================= BLOKADA STAWIANIA / PICIA =================
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (items.idOf(e.getItemInHand()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        String id = items.idOf(e.getItem());
        if (id == null) return;
        if (!id.equals("kokos")) { e.setCancelled(true); return; }
        e.setCancelled(true);
        Player p = e.getPlayer();
        EquipmentSlot hand = e.getHand();
        ItemStack stack = p.getInventory().getItem(hand);
        stack.setAmount(stack.getAmount() - 1);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 120 * 20, 4));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10 * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300 * 20, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300 * 20, 0));
        fx(p, Sound.ENTITY_PLAYER_BURP, 1.2f);
    }

    // ================= PPM =================
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        String id = items.idOf(it);
        if (id == null) return;
        Player p = e.getPlayer();

        switch (id) {
            case "zamek_z_piasku" -> {
                e.setCancelled(true);
                consume(p);
                pl.castle().build(p.getLocation(), cfgI("zamek_z_piasku.duration", 10));
            }
            case "lampion" -> {
                e.setCancelled(true);
                if (!pl.checkCooldown(p, id, cfgI("lampion.cooldown", 120))) return;
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 7 * 20, 3));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 7 * 20, 1));
                fx(p, Sound.BLOCK_BEACON_ACTIVATE, 1.5f);
            }
            case "kostka_wyroku" -> {
                e.setCancelled(true);
                consume(p);
                if (rnd.nextBoolean()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 10 * 20, 2));
                    p.sendActionBar(Items.color("&d&lKostka Wyroku: &cSiła III &7na 10s!"));
                } else {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10 * 20, 0));
                    p.sendActionBar(Items.color("&d&lKostka Wyroku: &bSpowolnienie I &7na 10s..."));
                }
                fx(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f);
            }
            case "bomba", "pilka_plazowa" -> {
                e.setCancelled(true);
                consume(p);
                throwItem(p, id);
            }
            case "mistyczne_serce" -> {
                e.setCancelled(true);
                if (pl.hearts().use(p)) consume(p);
            }
            case "plomienna_torba" -> {
                e.setCancelled(true);
                pl.gear().openBag(p);
            }
            case "dynamit" -> {
                e.setCancelled(true);
                Block b = e.getClickedBlock();
                if (b == null || b.getType() != Material.BEDROCK) {
                    p.sendActionBar(Items.color("&cKliknij dynamitem na &7bedrock&c!"));
                    return;
                }
                if (b.getY() <= b.getWorld().getMinHeight()) {
                    p.sendActionBar(Items.color("&cTego bedrocku nie można zniszczyć!"));
                    return;
                }
                consume(p);
                b.setType(Material.AIR);
                b.getWorld().spawnParticle(Particle.EXPLOSION, b.getLocation().add(0.5, 0.5, 0.5), 1);
                b.getWorld().playSound(b.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
            }
            case "czerwona_mikstura" -> {
                e.setCancelled(true);
                if (!pl.checkCooldown(p, id, cfgI("czerwona_mikstura.cooldown", 120))) return;
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 10 * 20, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10 * 20, 0));
                fx(p, Sound.ENTITY_GENERIC_DRINK, 1f);
            }
            case "esencja_obrazen_krytycznych", "esencja_ogniodpornosci" -> {
                e.setCancelled(true);
                p.sendActionBar(Items.color("&7Połóż esencję w &adrugim slocie kowadła&7!"));
            }
            case "lasso" -> {
                e.setCancelled(true);
                double range = cfgD("lasso.range", 3.1);
                Location eye = p.getEyeLocation();
                RayTraceResult r = p.getWorld().rayTraceEntities(eye, eye.getDirection(), range, 0.4,
                        en -> en instanceof Player && !en.equals(p));
                if (r != null && r.getHitEntity() instanceof Player t) lasso(p, t);
                else p.sendActionBar(Items.color("&cNie ma gracza w zasięgu lassa!"));
            }
            case "skorka_od_banana", "lod_na_patyku", "mozg_zombie" ->
                    e.setCancelled(true);   // dzialaja na uderzenie
            case "kokos" -> { /* jedzenie obsluguje PlayerItemConsumeEvent */ }
            default -> { }
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if ("lasso".equals(items.idOf(p.getInventory().getItemInMainHand())) && e.getRightClicked() instanceof Player t) {
            e.setCancelled(true);
            lasso(p, t);
        }
    }

    // ================= LASSO =================
    private void lasso(Player p, Player t) {
        double range = cfgD("lasso.range", 3.1);
        if (p.getLocation().distance(t.getLocation()) > range + 1.0) return;
        if (!pl.checkCooldown(p, "lasso", cfgI("lasso.cooldown", 150))) return;
        int dur = cfgI("lasso.duration", 5);
        noGlide.put(t.getUniqueId(), System.currentTimeMillis() + dur * 1000L);
        if (t.isGliding()) t.setGliding(false);
        t.sendActionBar(Items.color("&c&lZłapany lassem! &7Elytry zablokowane."));
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline() || !t.isOnline() || p.isDead() || t.isDead() || ticks >= dur * 20) { cancel(); return; }
                if (ticks % 40 == 0) {                          // co 2 sekundy
                    Vector d = p.getLocation().toVector().subtract(t.getLocation().toVector());
                    double len = d.length();
                    if (len > 1.2) {
                        d.normalize().multiply(Math.min(1.3, 0.3 + len * 0.25));
                        d.setY(0.3);
                        t.setVelocity(d);
                        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_LEASH_KNOT_PLACE, 1f, 1f);
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(pl, 0L, 5L);
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        if (!e.isGliding() || !(e.getEntity() instanceof Player p)) return;
        Long until = noGlide.get(p.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) e.setCancelled(true);
    }

    // ================= UDERZENIA =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        PlayerInventory inv = p.getInventory();
        ItemStack w = inv.getItemInMainHand();

        Double luck = p.getPersistentDataContainer().get(pl.gear().luckKey(), PersistentDataType.DOUBLE);
        if (luck != null) e.setDamage(e.getDamage() + luck);

        if (pl.gear().hasKey(w, pl.gear().critKey()))
            e.setDamage(e.getDamage() * cfgD("esencja_obrazen_krytycznych.damage-multiplier", 1.2));

        String id = items.idOf(w);
        if (id == null) return;

        if (id.equals("piekielny_miecz")) {
            if (p.getWorld().getEnvironment() == World.Environment.NETHER)
                e.setDamage(e.getDamage() * cfgD("piekielny_miecz.nether-damage-multiplier", 1.25));
            if (e.getEntity() instanceof LivingEntity le && rnd.nextDouble() < cfgD("piekielny_miecz.negate-fire-res-chance", 0.3)
                    && le.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
                le.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
                if (le instanceof Player vp) vp.sendActionBar(Items.color("&6Piekielny Miecz zniwelował Twoją odporność na ogień!"));
            }
            return;
        }

        if (!(e.getEntity() instanceof Player v)) return;
        switch (id) {
            case "skorka_od_banana" -> {
                e.setCancelled(true);
                if (!pl.checkCooldown(p, id, cfgI("skorka_od_banana.cooldown", 30))) return;
                consume(p);
                PlayerInventory vi = v.getInventory();
                List<ItemStack> bar = new ArrayList<>();
                for (int i = 0; i < 9; i++) bar.add(vi.getItem(i));
                Collections.shuffle(bar);
                for (int i = 0; i < 9; i++) vi.setItem(i, bar.get(i));
                v.sendActionBar(Items.color("&e&lSkórka od banana! &7Twój pasek został pomieszany."));
                v.playSound(v.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 1f);
            }
            case "lod_na_patyku" -> {
                if (!pl.checkCooldown(p, id, cfgI("lod_na_patyku.cooldown", 20))) return;
                int secs = cfgI("lod_na_patyku.freeze-seconds", 5);
                noJump.put(v.getUniqueId(), System.currentTimeMillis() + secs * 1000L);
                v.setFreezeTicks(Math.min(v.getMaxFreezeTicks() + 40, secs * 20));
                v.sendActionBar(Items.color("&b&lZamrożony! &7Nie możesz skakać przez &e" + secs + "s"));
                v.playSound(v.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.5f);
            }
            case "mozg_zombie" -> {
                if (!pl.checkCooldown(p, id, cfgI("mozg_zombie.cooldown", 120))) return;
                v.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 5 * 20, 0));
                v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5 * 20, 1));
                v.playSound(v.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1f, 0.6f);
            }
            default -> { }
        }
    }

    @EventHandler
    public void onJump(PlayerJumpEvent e) {
        Long until = noJump.get(e.getPlayer().getUniqueId());
        if (until != null && until > System.currentTimeMillis()) e.setCancelled(true);
    }

    // ================= RZUCANE (bomba, pilka) =================
    private void throwItem(Player p, String id) {
        Snowball s = p.launchProjectile(Snowball.class);
        s.setItem(items.create(id, 1));
        s.setVelocity(p.getLocation().getDirection().multiply(1.4));
        s.getPersistentDataContainer().set(thrownKey, PersistentDataType.STRING, id);
        p.playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball s)) return;
        String id = s.getPersistentDataContainer().get(thrownKey, PersistentDataType.STRING);
        if (id == null) return;
        Location at = s.getLocation();
        if (id.equals("bomba")) {
            int r = cfgI("bomba.radius", 3);
            World w = at.getWorld();
            for (int x = -r; x <= r; x++)
                for (int y = -r; y <= r; y++)
                    for (int z = -r; z <= r; z++) {
                        if (x * x + y * y + z * z > r * r + 1) continue;
                        Block b = at.clone().add(x, y, z).getBlock();
                        if (b.getType().isAir() || b.getType().getHardness() < 0) continue;
                        if (pl.castle().isCastleBlock(b.getLocation())) continue;
                        b.setType(Material.AIR, false);
                    }
            w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
            w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.9f);
        } else if (id.equals("pilka_plazowa")
                && e.getHitEntity() instanceof Player victim
                && s.getShooter() instanceof Player shooter && !victim.equals(shooter)) {
            Location a = shooter.getLocation(), b = victim.getLocation();
            shooter.teleport(b);
            victim.teleport(a);
            shooter.playSound(b, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
            victim.playSound(a, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
            victim.sendActionBar(Items.color("&d&lPiłka Plażowa! &7Zamieniono Cię miejscami."));
        }
    }
}
