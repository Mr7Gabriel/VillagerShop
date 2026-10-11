package id.vshop;

import org.bukkit.inventory.ItemStack;

/** Satu trade: bayar a (+ b opsional) => result. */
public record Trade(ItemStack a, ItemStack b, ItemStack result) {
}
