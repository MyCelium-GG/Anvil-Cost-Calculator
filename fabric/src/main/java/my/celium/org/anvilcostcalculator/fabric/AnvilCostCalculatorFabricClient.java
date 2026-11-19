package my.celium.org.anvilcostcalculator.fabric;

import my.celium.org.anvilcostcalculator.client.AnvilButton;
import my.celium.org.anvilcostcalculator.client.TooltipLines;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class AnvilCostCalculatorFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mycel")) {
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                    my.celium.org.anvilcostcalculator.MissingMycel::tick);
            return;
        }
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            Button button = AnvilButton.create(screen);
            if (button != null) {
                Screens.getWidgets(screen).add(button);
            }
        });
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            Component line = TooltipLines.lineFor(stack);
            if (line != null) {
                lines.add(line);
            }
        });
    }
}
