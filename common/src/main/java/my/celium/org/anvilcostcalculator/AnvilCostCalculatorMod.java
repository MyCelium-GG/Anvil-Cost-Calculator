package my.celium.org.anvilcostcalculator;

import my.celium.org.Mycel;
import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.config.MycelConfig;
import my.celium.org.logging.MycelLog;
import my.celium.org.metadata.ModMetadata;

/**
 * Anvil Cost Calculator: a button on the anvil opens a planner where you
 * pick any enchantable item (or a plain book combine), set what it already
 * has and which books to add, and get the cheapest combine order with
 * per-step and total level costs.
 *
 * <p>Client-side only. {@link #init()} registers metadata and config with
 * Mycel; each loader's <em>client</em> entrypoint then adds the button to
 * anvil screens and the prior work line to tooltips. Dedicated servers never
 * touch client classes.
 */
public final class AnvilCostCalculatorMod {
    public static final String ID = "anvilcostcalculator";

    public static final ModMetadata META = ModMetadata.builder(ID, "Anvil Cost Calculator", "1.0.2")
            .author("MyCelium")
            .website("https://www.curseforge.com/minecraft/mc-mods/anvil-cost-calculator")
            .updateUrl("https://wiki.mycelium-gg.org/updates/anvilcostcalculator.json")
            .build();

    public static ConfigValue<Boolean> enabled;
    public static ConfigValue<Boolean> showButton;
    public static ConfigValue<Boolean> showTooltip;
    public static ConfigValue<Boolean> showXpPoints;
    public static ConfigValue<Boolean> debug;
    public static MycelConfig config;

    private AnvilCostCalculatorMod() {
    }

    /** Called once from each loader's entrypoint. */
    public static void init() {
        Mycel.initialize();
        Mycel.registerMod(META);

        ConfigBuilder builder = Mycel.config(META);
        enabled = builder.booleanValue("enabled", true,
                "Whether the mod does anything at all.");
        showButton = builder.booleanValue("showButton", true,
                "Show the planner button on the anvil screen.");
        showTooltip = builder.booleanValue("showTooltip", true,
                "Show anvil uses and prior work penalty on item tooltips.");
        showXpPoints = builder.booleanValue("showXpPoints", true,
                "Also show the plan's total in XP points, counted down from your current level.");
        debug = builder.booleanValue("debug", true,
                "Log planner clicks, picks and plan results to the game log (turn off for release).");
        config = builder.build();
    }

    /** Info-level log line tagged {@code [debug]}, only while the {@code debug} option is on. */
    public static void debug(String message, Object... args) {
        try {
            if (debug == null || !debug.get()) {
                return;
            }
            MycelLog.infoMod(ID, "[debug] " + message, args);
        } catch (RuntimeException e) {
            // Logging must never break the caller.
        }
    }

    public static boolean isModEnabled() {
        return Mycel.isEnabled(ID) && enabled.get();
    }
}
