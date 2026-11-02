package my.celium.org.anvilcostcalculator.mixin;

import my.celium.org.anvilcostcalculator.AnvilCostCalculatorMod;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** The server's Too Expensive check: the third 40 in createResult, after the item-stack and rename ones. */
@Mixin(AnvilMenu.class)
abstract class AnvilMenuMixin {
    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40, ordinal = 2))
    private int anvilcostcalculator$levelLimit(int limit) {
        return AnvilCostCalculatorMod.liftsLevelLimit() ? Integer.MAX_VALUE : limit;
    }
}
