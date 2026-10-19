package my.celium.org.anvilcostcalculator.client;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.item.Item;

/**
 * What the planner shows, kept for the whole session so reopening the
 * planner (or coming back from the anvil) restores the last setup.
 * Enchantments are keyed by registry id; levels of 0 are never stored.
 */
public final class PlannerState {
    /** The item being planned, or {@code null} while none is picked. */
    Item item;
    /** Enchantments the item already has. */
    final Map<String, Integer> onItem = new LinkedHashMap<>();
    /** One book per enchantment to add, at this level. */
    final Map<String, Integer> books = new LinkedHashMap<>();
    /** Prior anvil uses of the item (its work penalty is 2^uses - 1). */
    int anvilUses;
    String search = "";

    static final PlannerState CURRENT = new PlannerState();

    /** Switches to another item: its old levels no longer apply. */
    void pick(Item next) {
        if (next != item) {
            onItem.clear();
            books.clear();
            anvilUses = 0;
        }
        item = next;
    }

    static void setLevel(Map<String, Integer> levels, String id, int level) {
        if (level <= 0) {
            levels.remove(id);
        } else {
            levels.put(id, level);
        }
    }
}
