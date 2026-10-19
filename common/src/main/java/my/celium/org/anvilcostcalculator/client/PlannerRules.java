package my.celium.org.anvilcostcalculator.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import my.celium.org.anvilcostcalculator.AnvilMath;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Answers {@link AnvilMath.Rules} from real enchantment holders, keyed by
 * registry id, for one target item (an enchanted book target accepts
 * everything, which {@link AnvilMath} already handles). Client only.
 */
public final class PlannerRules implements AnvilMath.Rules {
    private final ItemStack target;
    private final Map<String, Holder<Enchantment>> holders = new HashMap<>();

    public PlannerRules(ItemStack target, List<Holder<Enchantment>> enchantments) {
        this.target = target;
        for (Holder<Enchantment> holder : enchantments) {
            holders.put(id(holder), holder);
        }
    }

    /** Registry id used as the key everywhere, e.g. {@code "minecraft:sharpness"}. */
    public static String id(Holder<Enchantment> holder) {
        return holder.getRegisteredName();
    }

    @Override
    public int maxLevel(String id) {
        Holder<Enchantment> holder = holders.get(id);
        return holder == null ? 1 : holder.value().getMaxLevel();
    }

    @Override
    public int anvilCost(String id) {
        Holder<Enchantment> holder = holders.get(id);
        return holder == null ? 1 : holder.value().getAnvilCost();
    }

    @Override
    public boolean compatible(String a, String b) {
        Holder<Enchantment> first = holders.get(a);
        Holder<Enchantment> second = holders.get(b);
        return first == null || second == null || Enchantment.areCompatible(first, second);
    }

    @Override
    public boolean canApply(String id) {
        Holder<Enchantment> holder = holders.get(id);
        return holder != null && holder.value().canEnchant(target);
    }
}
