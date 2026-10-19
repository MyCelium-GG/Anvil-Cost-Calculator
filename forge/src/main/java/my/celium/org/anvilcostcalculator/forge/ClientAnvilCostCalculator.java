package my.celium.org.anvilcostcalculator.forge;

import com.mojang.datafixers.util.Either;

import my.celium.org.anvilcostcalculator.client.AnvilButton;
import my.celium.org.anvilcostcalculator.client.TooltipLines;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;

public final class ClientAnvilCostCalculator {
    private ClientAnvilCostCalculator() {
    }

    public static void register() {
        ScreenEvent.Init.Post.BUS.addListener(ClientAnvilCostCalculator::onScreenInit);
        RenderTooltipEvent.GatherComponents.BUS.addListener(ClientAnvilCostCalculator::onGatherTooltip);
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        Button button = AnvilButton.create(event.getScreen());
        if (button != null) {
            event.addListener(button);
        }
    }

    private static void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        Component line = TooltipLines.lineFor(event.getItemStack());
        if (line != null) {
            event.getTooltipElements().add(Either.<FormattedText, TooltipComponent>left(line));
        }
    }
}
