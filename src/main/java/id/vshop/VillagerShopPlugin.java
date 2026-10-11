package id.vshop;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class VillagerShopPlugin extends JavaPlugin implements TabExecutor {

    public static final List<String> PROFESSIONS = List.of(
            "librarian", "cleric", "farmer", "armorer", "butcher", "cartographer", "fisherman",
            "fletcher", "leatherworker", "mason", "shepherd", "toolsmith", "weaponsmith", "nitwit");

    private static final Set<String> SKIP_ENCHANTS = Set.of("binding_curse", "vanishing_curse");

    private final Map<String, Shop> shops = new LinkedHashMap<>();
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();
    private File file;

    // ------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        file = new File(getDataFolder(), "shops.yml");
        loadShops();
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getCommand("vshop").setExecutor(this);
        getCommand("vshop").setTabCompleter(this);
    }

    public Map<String, Shop> shops() {
        return shops;
    }

    // ------------------------------------------------------------ chat prompt

    public void prompt(Player p, String message, Consumer<String> handler) {
        p.sendMessage(message);
        pending.put(p.getUniqueId(), handler);
    }

    public Consumer<String> takePending(UUID id) {
        return pending.remove(id);
    }

    // ------------------------------------------------------------ storage

    public void loadShops() {
        shops.clear();
        if (!file.exists()) {
            createDefault();
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("shops");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            Shop shop = new Shop(key);
            shop.displayName = s.getString("name", "");
            shop.profession = s.getString("profession", "librarian");
            shop.type = s.getString("type", "plains");
            shop.level = s.getInt("level", 5);
            shop.noAI = s.getBoolean("no-ai", true);
            shop.invulnerable = s.getBoolean("invulnerable", true);
            shop.maxUses = s.getInt("max-uses", 9999999);

            ConfigurationSection ts = s.getConfigurationSection("trades");
            if (ts != null) {
                List<Integer> idx = new ArrayList<>();
                for (String k : ts.getKeys(false)) {
                    try {
                        idx.add(Integer.parseInt(k));
                    } catch (NumberFormatException ignored) {
                    }
                }
                idx.sort(Comparator.naturalOrder());
                for (int i : idx) {
                    ItemStack a = ts.getItemStack(i + ".a");
                    ItemStack b = ts.getItemStack(i + ".b");
                    ItemStack r = ts.getItemStack(i + ".result");
                    if (a == null || r == null) continue;
                    shop.trades.add(new Trade(a, b, r));
                }
            }
            shops.put(key, shop);
        }
    }

    public void saveShops() {
        YamlConfiguration y = new YamlConfiguration();
        for (Shop s : shops.values()) {
            String p = "shops." + s.name;
            y.set(p + ".name", s.displayName);
            y.set(p + ".profession", s.profession);
            y.set(p + ".type", s.type);
            y.set(p + ".level", s.level);
            y.set(p + ".no-ai", s.noAI);
            y.set(p + ".invulnerable", s.invulnerable);
            y.set(p + ".max-uses", s.maxUses);
            for (int i = 0; i < s.trades.size(); i++) {
                Trade t = s.trades.get(i);
                String tp = p + ".trades." + i;
                y.set(tp + ".a", t.a());
                if (t.b() != null) y.set(tp + ".b", t.b());
                y.set(tp + ".result", t.result());
            }
        }
        try {
            getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            getLogger().severe("Gagal menyimpan shops.yml: " + e.getMessage());
        }
    }

    private void createDefault() {
        Shop s = new Shop("contoh");
        s.displayName = "&6Enchanter";
        Enchantment fa = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("fire_aspect"));
        if (fa != null) {
            s.trades.add(new Trade(new ItemStack(Material.EMERALD, 13), null, book(fa, 2)));
        }
        shops.put(s.name, s);
        saveShops();
    }

    public void deleteShop(String name) {
        shops.remove(name);
        saveShops();
    }

    // ------------------------------------------------------------ helpers

    public static ItemStack book(Enchantment e, int level) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
        meta.addStoredEnchant(e, level, true);
        item.setItemMeta(meta);
        return item;
    }

    /** Tambah buku level maksimal untuk semua enchant yang belum ada di shop. Return jumlah trade baru. */
    public int addAllMaxBooks(Shop shop, int price) {
        Set<String> existing = new HashSet<>();
        for (Trade t : shop.trades) {
            if (t.result().getItemMeta() instanceof EnchantmentStorageMeta meta) {
                for (Enchantment e : meta.getStoredEnchants().keySet()) {
                    existing.add(e.getKey().getKey());
                }
            }
        }
        List<Enchantment> all = new ArrayList<>();
        for (Enchantment e : Registry.ENCHANTMENT) all.add(e);
        all.sort(Comparator.comparing(e -> e.getKey().getKey()));

        int added = 0;
        for (Enchantment e : all) {
            String key = e.getKey().getKey();
            if (SKIP_ENCHANTS.contains(key) || existing.contains(key)) continue;
            shop.trades.add(new Trade(new ItemStack(Material.EMERALD, price), null, book(e, e.getMaxLevel())));
            added++;
        }
        return added;
    }

    // ------------------------------------------------------------ spawn

    public void spawn(Player player, Shop shop) {
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (Trade t : shop.trades) {
            MerchantRecipe r = new MerchantRecipe(t.result().clone(), 0, shop.maxUses, false);
            r.setIgnoreDiscounts(true);
            r.addIngredient(t.a().clone());
            if (t.b() != null) r.addIngredient(t.b().clone());
            recipes.add(r);
        }
        if (recipes.isEmpty()) {
            player.sendMessage("Shop '" + shop.name + "' belum punya trade.");
            return;
        }

        Location loc = player.getLocation();
        loc.getWorld().spawn(loc, Villager.class, v -> {
            Villager.Profession prof = Registry.VILLAGER_PROFESSION.get(
                    NamespacedKey.minecraft(shop.profession.toLowerCase(Locale.ROOT)));
            if (prof != null) v.setProfession(prof);

            Villager.Type type = Registry.VILLAGER_TYPE.get(
                    NamespacedKey.minecraft(shop.type.toLowerCase(Locale.ROOT)));
            if (type != null) v.setVillagerType(type);

            v.setVillagerLevel(Math.max(1, Math.min(5, shop.level)));
            v.setAI(!shop.noAI);
            v.setInvulnerable(shop.invulnerable);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);

            if (shop.displayName != null && !shop.displayName.isBlank()) {
                v.customName(LegacyComponentSerializer.legacyAmpersand().deserialize(shop.displayName));
                v.setCustomNameVisible(true);
            }
            v.setRecipes(recipes);
        });
        player.sendMessage("Villager '" + shop.name + "' muncul (" + recipes.size() + " trade).");
    }

    // ------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (sender instanceof Player p) {
                Menus.openMain(this, p);
            } else {
                sender.sendMessage("Menu hanya untuk pemain. Pakai: /vshop list | reload");
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                loadShops();
                sender.sendMessage("shops.yml dimuat ulang (" + shops.size() + " shop).");
            }
            case "list" -> sender.sendMessage(shops.isEmpty()
                    ? "Belum ada shop."
                    : "Shop: " + String.join(", ", shops.keySet()));
            case "spawn" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Hanya untuk pemain.");
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage("Pakai: /vshop spawn <nama_shop>");
                    return true;
                }
                Shop shop = shops.get(args[1].toLowerCase(Locale.ROOT));
                if (shop == null) {
                    p.sendMessage("Shop tidak ada. Cek /vshop list");
                    return true;
                }
                spawn(p, shop);
            }
            default -> sender.sendMessage("Pakai: /vshop [menu|spawn <nama>|list|reload]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("menu", "spawn", "list", "reload")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            for (String k : shops.keySet()) {
                if (k.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(k);
            }
        }
        return out;
    }
}
