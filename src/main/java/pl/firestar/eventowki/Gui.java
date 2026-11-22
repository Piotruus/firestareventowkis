package pl.firestar.eventowki;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** GUI /eventowki - 54 sloty, 21 eventowek na strone. */
public class Gui implements Listener {
    private static final int[] SLOTS = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};

    static class Holder implements InventoryHolder {
        final int page;
        Inventory inv;
        Holder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final EventowkiPlugin pl;

    public Gui(EventowkiPlugin pl) { this.pl = pl; }

    private ItemStack named(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Items.color(name));
        it.setItemMeta(meta);
        return it;
    }

    public void open(Player p, int page) {
        List<String> ids = new ArrayList<>(pl.items().ids());
        int pages = Math.max(1, (ids.size() + SLOTS.length - 1) / SLOTS.length);
        page = Math.max(0, Math.min(page, pages - 1));
        Holder h = new Holder(page);
        Inventory inv = Bukkit.createInventory(h, 54,
                Items.color("&5&l✦ &d&l" + Items.smallCaps("eventowki firestar") + " &5&l✦ &8(" + (page + 1) + "/" + pages + ")"));
        h.inv = inv;
        ItemStack pane = named(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);
        for (int i = 0; i < SLOTS.length; i++) {
            int idx = page * SLOTS.length + i;
            inv.setItem(SLOTS[i], idx < ids.size() ? pl.items().create(ids.get(idx), 1) : null);
        }
        if (page > 0) inv.setItem(45, named(Material.ARROW, "&e« Poprzednia strona"));
        if (page < pages - 1) inv.setItem(53, named(Material.ARROW, "&eNastępna strona »"));
        inv.setItem(49, named(Material.BARRIER, "&cZamknij"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;
        int slot = e.getRawSlot();
        if (slot == 49) { p.closeInventory(); return; }
        if (slot == 45 && h.page > 0) { open(p, h.page - 1); return; }
        if (slot == 53) { open(p, h.page + 1); return; }
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            ItemStack it = e.getCurrentItem();
            String id = pl.items().idOf(it);
            if (id == null) return;
            if (!p.hasPermission("eventowki.admin")) {
                p.sendActionBar(Items.color("&cTo tylko podgląd eventówek."));
                return;
            }
            int amt = e.isShiftClick() ? it.getMaxStackSize() : 1;
            ItemStack give = pl.items().create(id, amt);
            p.getInventory().addItem(give).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
