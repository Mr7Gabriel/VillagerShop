package id.vshop;

import java.util.ArrayList;
import java.util.List;

public final class Shop {
    public final String name;
    public String displayName = "";
    public String profession = "librarian";
    public String type = "plains";
    public int level = 5;
    public boolean noAI = true;
    public boolean invulnerable = true;
    public int maxUses = 9999999;
    public final List<Trade> trades = new ArrayList<>();

    public Shop(String name) {
        this.name = name;
    }
}
