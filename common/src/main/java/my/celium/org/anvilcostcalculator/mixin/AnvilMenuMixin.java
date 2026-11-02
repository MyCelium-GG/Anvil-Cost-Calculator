package my.celium.org.anvilcostcalculator.mixin;

import my.celium.org.anvilcostcalculator.AnvilCostCalculatorMod;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The server's Too Expensive check: the third 40 in the result method, after the item-stack and
 * rename ones. NeoForge moves vanilla's createResult body into createResultInternal, so both are
 * named; each loader has the constants in exactly one of them.
 */
@Mixin(AnvilMenu.class)
abstract class AnvilMenuMixin {
    @ModifyConstant(method = {"createResult", "createResultInternal"}, constant = @Constant(intValue = 40, ordinal = 2))
    private int anvilcostcalculator$levelLimit(int limit) {
        return AnvilCostCalculatorMod.liftsLevelLimit() ? Integer.MAX_VALUE : limit;
    }
}
