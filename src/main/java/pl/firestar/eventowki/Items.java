package pl.firestar.eventowki;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Rejestr eventowek: wczytuje config, tworzy itemy i rozpoznaje je po PDC. */
public final class Items {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public record Def(String id, Material material, int cmd, String name,
                      List<String> lore, Map<String, Integer> enchants) {}

    private final EventowkiPlugin plugin;
    private final NamespacedKey idKey;
    private final Map<String, Def> defs = new LinkedHashMap<>();

    public Items(EventowkiPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "eventowka");
    }

    public static Component color(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private static final String FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String TO = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    public static String smallCaps(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase().toCharArray()) {
            int i = FROM.indexOf(c);
            sb.append(i >= 0 ? TO.charAt(i) : c);
        }
        return sb.toString();
    }

    public void load() {
        defs.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("eventowki");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            Material m = Material.matchMaterial(s.getString("material", "STONE"));
            if (m == null) {
                plugin.getLogger().warning("Zly material w eventowce " + id);
                continue;
            }
            Map<String, Integer> en = new LinkedHashMap<>();
            ConfigurationSection es = s.getConfigurationSection("enchants");
            if (es != null) for (String k : es.getKeys(false)) en.put(k, es.getInt(k));
            defs.put(id, new Def(id, m, s.getInt("custom-model-data"), s.getString("name", id),
                    s.getStringList("lore"), en));
        }
    }

    public Set<String> ids() { return defs.keySet(); }
    public Def def(String id) { return defs.get(id); }
    public NamespacedKey idKey() { return idKey; }

    public String idOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public static Map<String, String> excaliburPh(int kills) {
        int n = 20;
        int f = (int) Math.round(Math.min(kills, 50) / 50.0 * n);
        String bar = "&a" + "▌".repeat(f) + "&7" + "▌".repeat(n - f);
        Map<String, String> ph = new HashMap<>();
        ph.put("%kills%", String.valueOf(Math.min(kills, 50)));
        ph.put("%progress_bar%", bar);
        ph.put("%damage%", kills >= 50 ? "12" : "11.5");
        return ph;
    }

    public List<Component> renderLore(Def d, Map<String, String> ph) {
        List<Component> out = new ArrayList<>();
        for (String l : d.lore()) {
            String line = l;
            for (Map.Entry<String, String> e : ph.entrySet()) line = line.replace(e.getKey(), e.getValue());
            out.add(color(line));
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    public ItemStack create(String id, int amount) {
        Def d = defs.get(id);
        if (d == null) return null;
        ItemStack it = new ItemStack(d.material(), amount);
        ItemMeta m = it.getItemMeta();
        m.displayName(color(d.name()));
        m.lore(renderLore(d, id.equals("excalibur") ? excaliburPh(0) : Map.of()));
        m.setCustomModelData(d.cmd());
        m.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        for (Map.Entry<String, Integer> e : d.enchants().entrySet()) {
            Enchantment en = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e.getKey()));
            if (en != null) m.addEnchant(en, e.getValue(), true);
        }
        it.setItemMeta(m);
        return it;
    }
}
