package my.celium.org.anvilcostcalculator.client;

import my.celium.org.anvilcostcalculator.AnvilCostCalculatorMod;
import my.celium.org.anvilcostcalculator.AnvilMath;
import my.celium.org.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Tooltip line shared by every loader's tooltip hook: shows how often an
 * item has been through an anvil and the prior work penalty it adds to its
 * next anvil step, e.g. {@code "Anvil uses: 2 · Prior work +3 lv"}. Never throws.
 */
public final class TooltipLines {
    static final int COLOR_LABEL = TextUtil.MUTED;
    static final int COLOR_VALUE = TextUtil.BODY;
    static final int COLOR_MAXED = TextUtil.ERROR;

    private TooltipLines() {
    }

    /** The line for a stack, or {@code null} when it has no prior work or the line is off. */
    public static Component lineFor(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty() || !AnvilCostCalculatorMod.isModEnabled()
                    || !AnvilCostCalculatorMod.showTooltip.get()) {
                return null;
            }
            return line(stack.getOrDefault(DataComponents.REPAIR_COST, 0));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Line for a raw prior work penalty; red once any further anvil step is Too Expensive. */
    public static Component line(int repairCost) {
        if (repairCost <= 0) {
            return null;
        }
        boolean maxed = repairCost >= AnvilMath.TOO_EXPENSIVE - 1;
        int value = maxed ? COLOR_MAXED : COLOR_VALUE;
        MutableComponent line = colored("Anvil uses: ", COLOR_LABEL)
                .append(colored(String.valueOf(AnvilMath.anvilUses(repairCost)), value))
                .append(colored(" · Prior work ", COLOR_LABEL))
                .append(colored("+" + repairCost + " lv", value));
        return maxed ? line.append(colored(" (maxed)", COLOR_MAXED)) : line;
    }

    private static MutableComponent colored(String text, int rgb) {
        return TextUtil.hex(text, rgb);
    }
}
