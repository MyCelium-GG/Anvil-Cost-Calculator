package my.celium.org.anvilcostcalculator;

import java.util.ArrayList;
import java.util.List;

import my.celium.org.stats.ModStats;

/**
 * Anonymous stats for the FastStats project {@code anvil-cost-calculator}, sent through Mycel under
 * its consent. The planner reports one summary when it closes: the item, the enchantments
 * added, the cost range and whether it was too expensive.
 */
public final class AnvilStats {
    private static final String TOKEN = "2d71540c179c38a48c648bfadf623c16";

    private static volatile ModStats stats;

    private AnvilStats() {
    }

    static void start() {
        stats = ModStats.builder(AnvilCostCalculatorMod.META, TOKEN)
                .bool("no_level_limit", () -> AnvilCostCalculatorMod.noLevelLimit.get())
                .stringArray("settings_off", AnvilStats::settingsOff)
                .counters("planned_items")
                .counters("planned_enchantments")
                .counters("plan_levels")
                .errors("my.celium.org.anvilcostcalculator.")
                .start();
    }

    /** Counts a feature use, like {@code planner_open}. */
    public static void count(String what) {
        ModStats current = stats;
        if (current != null) {
            current.count(what);
        }
    }

    /** Counts {@code key} in the counter map {@code id}. */
    public static void count(String id, String key) {
        ModStats current = stats;
        if (current != null) {
            current.count(id, key);
        }
    }

    /** {@code "0-9"}, {@code "10-19"} ... {@code "40+"}: the total level cost of a plan. */
    public static String levelRange(int levels) {
        if (levels >= 40) {
            return "40+";
        }
        int low = levels / 10 * 10;
        return low + "-" + (low + 9);
    }

    private static String[] settingsOff() {
        List<String> off = new ArrayList<>();
        if (!AnvilCostCalculatorMod.enabled.get()) {
            off.add("enabled");
        }
        if (!AnvilCostCalculatorMod.showButton.get()) {
            off.add("showButton");
        }
        if (!AnvilCostCalculatorMod.showTooltip.get()) {
            off.add("showTooltip");
        }
        if (!AnvilCostCalculatorMod.showXpPoints.get()) {
            off.add("showXpPoints");
        }
        return off.isEmpty() ? null : off.toArray(String[]::new);
    }
}
