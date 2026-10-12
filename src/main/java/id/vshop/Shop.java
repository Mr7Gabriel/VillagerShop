package id.vshop;

import java.util.ArrayList;
import java.util.List;

public final class Shop {
    public final String name;
    public String displayName = "";
    public String profession = "librarian";
    public String type = "plains";
    public int level = 5;
    public boolean noAI = false;
    public boolean invulnerable = false;
    public int maxUses = 9999999;
    public final List<Trade> trades = new ArrayList<>();
    /** Blok kerja (job site) villager, format "dunia;x;y;z". Kosong = tidak diatur. */
    public String jobSite = "";

    public Shop(String name) {
        this.name = name;
    }
}
