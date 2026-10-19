package my.celium.org.anvilcostcalculator.neoforge;

import my.celium.org.client.ClientHooks;
import my.celium.org.anvilcostcalculator.AnvilCostCalculatorMod;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * NeoForge entrypoint: server-safe bootstrap; client hooks (config screen,
 * anvil panel, tooltip) are isolated in {@link ClientAnvilCostCalculator} so
 * dedicated servers never load client classes.
 */
@Mod(AnvilCostCalculatorMod.ID)
public final class AnvilCostCalculatorNeoForge {
    public AnvilCostCalculatorNeoForge(@SuppressWarnings("unused") IEventBus modBus,
            @SuppressWarnings("unused") ModContainer container) {
        AnvilCostCalculatorMod.init();
        if (FMLEnvironment.getDist().isClient()) {
            ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                    () -> (ModContainer ignored, Screen parent) -> ClientHooks.createConfigScreen(parent,
                            AnvilCostCalculatorMod.ID));
            ClientAnvilCostCalculator.register();
        }
    }
}
