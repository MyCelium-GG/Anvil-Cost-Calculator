package my.celium.org.anvilcostcalculator.forge;

import my.celium.org.client.ClientHooks;
import my.celium.org.anvilcostcalculator.AnvilCostCalculatorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint: server-safe bootstrap; client hooks (config screen,
 * anvil panel, tooltip) are isolated in {@link ClientAnvilCostCalculator} so
 * dedicated servers never load client classes.
 */
@Mod(AnvilCostCalculatorMod.ID)
public final class AnvilCostCalculatorForge {
    public AnvilCostCalculatorForge(@SuppressWarnings("unused") FMLJavaModLoadingContext context) {
        AnvilCostCalculatorMod.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (Minecraft minecraft, Screen parent) -> ClientHooks.createConfigScreen(parent,
                                    AnvilCostCalculatorMod.ID)));
            ClientAnvilCostCalculator.register();
        }
    }
}
