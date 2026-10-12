package id.vshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

final class Menus {

    private Menus() {
    }

    /** Jumlah trade per halaman editor. */
    static final int PER_PAGE = 10;

    /** SLOTS[t] = {bayar1, bayar2, hasil} untuk trade ke-t di halaman. */
    static final int[][] SLOTS = new int[PER_PAGE][3];

    static {
        for (int t = 0; t < PER_PAGE; t++) {
            int row = t / 2;
            int base = (t % 2 == 0) ? 0 : 4;
            for (int k = 0; k < 3; k++) {
                SLOTS[t][k] = row * 9 + base + k;
            }
        }
    }

    // ------------------------------------------------------------ holders

    static final class MainHolder implements InventoryHolder {
        final List<String> names = new ArrayList<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static final class EditorHolder implements InventoryHolder {
        final Shop shop;
        final int page;
        boolean detached;
        Inventory inv;

        EditorHolder(Shop shop, int page) {
            this.shop = shop;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ------------------------------------------------------------ helpers

    static ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) {
                l.add(Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    static boolean isEmpty(ItemStack i) {
        return i == null || i.getType().isAir();
    }

    /** Slot yang tidak boleh diubah pemain (pembatas dan baris tombol). */
    static boolean isLocked(int slot) {
        if (slot >= 45) return true;
        int col = slot % 9;
        return col == 3 || col >= 7;
    }

    // ------------------------------------------------------------ main menu

    static void openMain(VillagerShopPlugin plugin, Player p) {
        MainHolder h = new MainHolder();
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("VillagerShop"));
        h.inv = inv;
        int i = 0;
        for (Shop s : plugin.shops().values()) {
            if (i >= 45) break;
            h.names.add(s.name);
            inv.setItem(i++, named(Material.VILLAGER_SPAWN_EGG, s.name,
                    "Trade: " + s.trades.size(),
                    "Klik kiri: spawn di posisimu",
                    "Klik kanan: edit trade",
                    "Shift + klik kanan: hapus shop"));
        }
        inv.setItem(49, named(Material.NETHER_STAR, "+ Shop baru", "Klik, lalu ketik nama di chat"));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------ editor

    static void openEditor(VillagerShopPlugin plugin, Player p, Shop shop, int page) {
        int maxPage = shop.trades.size() / PER_PAGE;
        int pg = Math.max(0, Math.min(page, maxPage));

        EditorHolder h = new EditorHolder(shop, pg);
        Inventory inv = Bukkit.createInventory(h, 54,
                Component.text("Edit: " + shop.name + " (" + (pg + 1) + "/" + (maxPage + 1) + ")"));
        h.inv = inv;

        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 0; s < 54; s++) {
            if (isLocked(s)) inv.setItem(s, pane.clone());
        }
        for (int t = 0; t < PER_PAGE; t++) {
            int idx = pg * PER_PAGE + t;
            if (idx >= shop.trades.size()) break;
            Trade tr = shop.trades.get(idx);
            inv.setItem(SLOTS[t][0], tr.a().clone());
            if (tr.b() != null) inv.setItem(SLOTS[t][1], tr.b().clone());
            inv.setItem(SLOTS[t][2], tr.result().clone());
        }
        refreshButtons(h);
        p.openInventory(inv);
    }

    static void refreshButtons(EditorHolder h) {
        Inventory inv = h.inv;
        Shop s = h.shop;
        inv.setItem(45, named(Material.ARROW, "< Halaman sebelumnya"));
        inv.setItem(46, named(Material.ARROW, "Halaman berikutnya >"));
        inv.setItem(47, named(Material.ENCHANTED_BOOK, "Isi semua enchant level maks",
                "Klik, lalu ketik harga emerald di chat",
                "Enchant yang sudah ada tidak diubah"));
        Material ws = VillagerShopPlugin.workstationOf(s.profession);
        inv.setItem(48, named(ws, "Profesi: " + s.profession,
                ws == Material.NAME_TAG ? "Tanpa blok kerja" : "Blok kerja: " + ws.name().toLowerCase(),
                "Klik untuk ganti"));
        inv.setItem(7, named(Material.LECTERN, "Set blok kerja",
                "Klik, lalu klik kanan blok kerja di dunia",
                "Profesi ikut berubah sesuai blok itu",
                "Sekarang: " + (s.jobSite.isBlank() ? "belum diatur" : s.jobSite.replace(';', ' '))));
        inv.setItem(16, named(Material.BARRIER, "Lepas blok kerja", "Villager cari blok kerja sendiri"));
        inv.setItem(17, named(Material.SPYGLASS, "Coba buka menu", "Buka trade seperti pemain biasa"));
        inv.setItem(25, named(Material.EXPERIENCE_BOTTLE, "Level: " + s.level,
                "Klik untuk ganti (1-5, 5 = Master)"));
        inv.setItem(26, named(Material.OAK_SAPLING, "Biome: " + s.type, "Klik untuk ganti"));
        inv.setItem(34, named(Material.NAME_TAG, "Nama: " + (s.displayName.isBlank() ? "-" : s.displayName),
                "Klik, lalu ketik nama di chat",
                "Pakai & untuk warna, ketik 'hapus' untuk kosongkan"));
        inv.setItem(8, named(Material.PAPER, "Cara pakai",
                "Kiri: bayar 1",
                "Tengah: bayar 2 (boleh kosong)",
                "Kanan: item yang dijual",
                "Taruh item dari inventory-mu",
                "Kosongkan slot kiri/kanan untuk hapus trade"));
        inv.setItem(49, named(Material.ENDER_EYE, "Isi acak",
                "Klik, lalu ketik: <jumlah> <harga> [max]",
                "Contoh: 10 32 = 10 enchant acak, level acak",
                "Contoh: 10 32 max = level maksimal",
                "Enchant yang sudah ada tidak diubah"));
        inv.setItem(50, named(s.noAI ? Material.LIME_DYE : Material.GRAY_DYE,
                "Diam di tempat: " + (s.noAI ? "ON" : "OFF"), "Klik untuk ganti"));
        inv.setItem(51, named(s.invulnerable ? Material.LIME_DYE : Material.GRAY_DYE,
                "Tidak bisa mati: " + (s.invulnerable ? "ON" : "OFF"), "Klik untuk ganti"));
        inv.setItem(52, named(Material.VILLAGER_SPAWN_EGG, "Spawn villager",
                "Villager normal, muncul di posisimu"));
        inv.setItem(53, named(Material.EMERALD, "Simpan & kembali"));
    }

    /** Baca isi halaman editor dan masukkan ke daftar trade shop. */
    static void sync(EditorHolder h) {
        Shop shop = h.shop;
        List<Trade> pageTrades = new ArrayList<>();
        for (int t = 0; t < PER_PAGE; t++) {
            ItemStack a = h.inv.getItem(SLOTS[t][0]);
            ItemStack b = h.inv.getItem(SLOTS[t][1]);
            ItemStack r = h.inv.getItem(SLOTS[t][2]);
            if (isEmpty(a) || isEmpty(r)) continue;
            pageTrades.add(new Trade(a.clone(), isEmpty(b) ? null : b.clone(), r.clone()));
        }
        int from = Math.min(h.page * PER_PAGE, shop.trades.size());
        int to = Math.min(from + PER_PAGE, shop.trades.size());
        shop.trades.subList(from, to).clear();
        shop.trades.addAll(from, pageTrades);
    }
}
