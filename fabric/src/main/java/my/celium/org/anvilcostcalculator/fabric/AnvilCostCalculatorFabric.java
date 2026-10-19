package my.celium.org.anvilcostcalculator.fabric;

import my.celium.org.anvilcostcalculator.AnvilCostCalculatorMod;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric entrypoint: server-safe bootstrap only. Client rendering hooks live
 * in {@link AnvilCostCalculatorFabricClient} so dedicated servers never load
 * client classes.
 */
public final class AnvilCostCalculatorFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AnvilCostCalculatorMod.init();
    }
}
