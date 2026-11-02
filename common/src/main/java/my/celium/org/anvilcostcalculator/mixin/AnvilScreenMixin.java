package my.celium.org.anvilcostcalculator.mixin;

import my.celium.org.anvilcostcalculator.AnvilCostCalculatorMod;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Shows the cost instead of Too Expensive, but only when the server actually offers a result:
 * a server without the mod still refuses, and then vanilla's message is the honest one.
 */
@Mixin(AnvilScreen.class)
abstract class AnvilScreenMixin extends ItemCombinerScreen<AnvilMenu> {
    private AnvilScreenMixin(AnvilMenu menu, Inventory inventory, Component title,
            net.minecraft.resources.Identifier background) {
        super(menu, inventory, title, background);
    }

    @ModifyConstant(method = "extractLabels", constant = @Constant(intValue = 40))
    private int anvilcostcalculator$levelLimit(int limit) {
        return AnvilCostCalculatorMod.liftsLevelLimit() && this.menu.getSlot(2).hasItem() ? Integer.MAX_VALUE : limit;
    }
}
