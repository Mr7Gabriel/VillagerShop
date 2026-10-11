package id.vshop;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Locale;
import java.util.function.Consumer;

public final class MenuListener implements Listener {

    private final VillagerShopPlugin plugin;

    public MenuListener(VillagerShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ chat prompt

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Consumer<String> handler = plugin.takePending(e.getPlayer().getUniqueId());
        if (handler == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handler.accept(text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.takePending(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ clicks

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (holder instanceof Menus.MainHolder mh) {
            e.setCancelled(true);
            if (e.getRawSlot() < 0 || e.getRawSlot() >= top.getSize()) return;
            handleMain(p, mh, e);
        } else if (holder instanceof Menus.EditorHolder eh) {
            if (e.isShiftClick() || e.getClick() == ClickType.DOUBLE_CLICK) {
                e.setCancelled(true);
                return;
            }
            int raw = e.getRawSlot();
            if (raw < 0 || raw >= top.getSize()) return; // klik di inventory pemain: bebas
            if (Menus.isLocked(raw)) {
                e.setCancelled(true);
                handleButton(p, eh, raw);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (holder instanceof Menus.MainHolder) {
            e.setCancelled(true);
        } else if (holder instanceof Menus.EditorHolder) {
            for (int raw : e.getRawSlots()) {
                if (raw < top.getSize() && Menus.isLocked(raw)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menus.EditorHolder eh && !eh.detached) {
            Menus.sync(eh);
            plugin.saveShops();
            eh.detached = true;
        }
    }

    // ------------------------------------------------------------ main menu

    private void handleMain(Player p, Menus.MainHolder mh, InventoryClickEvent e) {
        int slot = e.getRawSlot();

        if (slot == 49) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.closeInventory();
                askNewShop(p);
            });
            return;
        }
        if (slot >= mh.names.size()) return;

        Shop shop = plugin.shops().get(mh.names.get(slot));
        if (shop == null) return;

        if (e.isRightClick() && e.isShiftClick()) {
            plugin.deleteShop(shop.name);
            p.sendMessage("Shop '" + shop.name + "' dihapus.");
            Bukkit.getScheduler().runTask(plugin, () -> Menus.openMain(plugin, p));
        } else if (e.isRightClick()) {
            Bukkit.getScheduler().runTask(plugin, () -> Menus.openEditor(plugin, p, shop, 0));
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.closeInventory();
                plugin.spawn(p, shop);
            });
        }
    }

    private void askNewShop(Player p) {
        plugin.prompt(p, "Ketik nama shop baru di chat (huruf/angka/_/-), atau 'batal'.", text -> {
            if (text.equalsIgnoreCase("batal")) {
                Menus.openMain(plugin, p);
                return;
            }
            String name = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
            if (name.isEmpty() || plugin.shops().containsKey(name)) {
                p.sendMessage("Nama kosong atau sudah dipakai.");
                Menus.openMain(plugin, p);
                return;
            }
            Shop s = new Shop(name);
            s.displayName = text.trim();
            plugin.shops().put(name, s);
            plugin.saveShops();
            Menus.openEditor(plugin, p, s, 0);
        });
    }

    // ------------------------------------------------------------ editor buttons

    private void handleButton(Player p, Menus.EditorHolder eh, int slot) {
        switch (slot) {
            case 45 -> {
                if (eh.page > 0) changePage(p, eh, eh.page - 1);
            }
            case 46 -> {
                Menus.sync(eh);
                if (eh.page < eh.shop.trades.size() / Menus.PER_PAGE) changePage(p, eh, eh.page + 1);
            }
            case 47 -> {
                leave(eh);
                int page = eh.page;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.closeInventory();
                    askAllMax(p, eh.shop, page);
                });
            }
            case 48 -> {
                int i = VillagerShopPlugin.PROFESSIONS.indexOf(eh.shop.profession);
                eh.shop.profession = VillagerShopPlugin.PROFESSIONS.get((i + 1) % VillagerShopPlugin.PROFESSIONS.size());
                Menus.refreshButtons(eh);
            }
            case 50 -> {
                eh.shop.noAI = !eh.shop.noAI;
                Menus.refreshButtons(eh);
            }
            case 51 -> {
                eh.shop.invulnerable = !eh.shop.invulnerable;
                Menus.refreshButtons(eh);
            }
            case 52 -> {
                leave(eh);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.closeInventory();
                    plugin.spawn(p, eh.shop);
                });
            }
            case 53 -> {
                leave(eh);
                Bukkit.getScheduler().runTask(plugin, () -> Menus.openMain(plugin, p));
            }
            default -> {
            }
        }
    }

    /** Simpan isi editor lalu tandai supaya event close tidak menyimpan dua kali. */
    private void leave(Menus.EditorHolder eh) {
        Menus.sync(eh);
        plugin.saveShops();
        eh.detached = true;
    }

    private void changePage(Player p, Menus.EditorHolder eh, int newPage) {
        leave(eh);
        Bukkit.getScheduler().runTask(plugin, () -> Menus.openEditor(plugin, p, eh.shop, newPage));
    }

    private void askAllMax(Player p, Shop shop, int page) {
        plugin.prompt(p, "Ketik harga emerald per buku (1-64) untuk semua enchant level maks, atau 'batal'.", text -> {
            if (text.equalsIgnoreCase("batal")) {
                Menus.openEditor(plugin, p, shop, page);
                return;
            }
            int price;
            try {
                price = Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                p.sendMessage("Harga harus angka 1-64.");
                Menus.openEditor(plugin, p, shop, page);
                return;
            }
            if (price < 1 || price > 64) {
                p.sendMessage("Harga harus angka 1-64.");
                Menus.openEditor(plugin, p, shop, page);
                return;
            }
            int added = plugin.addAllMaxBooks(shop, price);
            plugin.saveShops();
            p.sendMessage(added + " trade ditambahkan.");
            Menus.openEditor(plugin, p, shop, shop.trades.size() / Menus.PER_PAGE);
        });
    }
}
