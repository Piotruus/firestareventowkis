package pl.firestar.eventowki;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;

/** Mistyczne Serce: +2 HP za uzycie, max 10 uzyc, -1 serce po smierci. */
public class Hearts implements Listener {
    public static final int MAX_USES = 10;
    private final EventowkiPlugin pl;
    private final NamespacedKey usedKey;
    private final NamespacedKey modKey;

    public Hearts(EventowkiPlugin pl) {
        this.pl = pl;
        this.usedKey = new NamespacedKey(pl, "serca_uzyte");
        this.modKey = new NamespacedKey(pl, "serca");
    }

    public int used(Player p) {
        return p.getPersistentDataContainer().getOrDefault(usedKey, PersistentDataType.INTEGER, 0);
    }

    private void setUsed(Player p, int v) {
        p.getPersistentDataContainer().set(usedKey, PersistentDataType.INTEGER, Math.max(0, v));
    }

    public void apply(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a == null) return;
        for (AttributeModifier m : new java.util.ArrayList<>(a.getModifiers()))
            if (m.getKey().equals(modKey)) a.removeModifier(m);
        int u = used(p);
        if (u > 0) a.addModifier(new AttributeModifier(modKey, 2.0 * u, AttributeModifier.Operation.ADD_NUMBER));
    }

    /** true = serce zuzyte. */
    public boolean use(Player p) {
        int u = used(p);
        if (u >= MAX_USES) {
            p.sendMessage(Items.color("&cOsiągnąłeś limit &e" + MAX_USES + " &cużyć Mistycznego Serca!"));
            return false;
        }
        setUsed(p, u + 1);
        apply(p);
        p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(), p.getHealth() + 2));
        p.sendMessage(Items.color("&5&l✦ &dZyskujesz &e+1 serce&d! &8(&7" + (u + 1) + "/" + MAX_USES + "&8)"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
        return true;
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { apply(e.getPlayer()); }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        int u = used(p);
        if (u > 0) {
            setUsed(p, u - 1);
            p.sendMessage(Items.color("&cPo śmierci tracisz 1 dodatkowe serce!"));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(pl, () -> { apply(p); p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue()); });
    }
}
