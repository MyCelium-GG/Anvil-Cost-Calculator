package my.celium.org.anvilcostcalculator.client;

import java.lang.reflect.Field;

import my.celium.org.anvilcostcalculator.AnvilCostCalculatorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;

/**
 * The small "XP" button in the anvil's top-right corner that opens the
 * {@link PlannerScreen}. Each loader adds it after the anvil screen
 * initialises. Position comes from the screen's {@code leftPos}/
 * {@code topPos}/{@code imageWidth}, read reflectively. Client only.
 */
public final class AnvilButton {
    static final int WIDTH = 20;
    static final int HEIGHT = 14;
    static final int INSET = 4;

    private AnvilButton() {
    }

    /** The button for {@code screen}, or {@code null} when it is not an anvil or the button is off. */
    public static Button create(Screen screen) {
        try {
            if (!(screen instanceof AnvilScreen anvil) || !AnvilCostCalculatorMod.isModEnabled()
                    || !AnvilCostCalculatorMod.showButton.get()) {
                return null;
            }
            int left = readInt(anvil, "leftPos");
            int top = readInt(anvil, "topPos");
            int imageWidth = readInt(anvil, "imageWidth");
            AnvilCostCalculatorMod.debug("Anvil button added at ({}, {}) for anvil at ({}, {}) width {}",
                    left + imageWidth - WIDTH - INSET, top + INSET, left, top, imageWidth);
            return Button.builder(Component.literal("XP"),
                            button -> {
                                AnvilCostCalculatorMod.debug("Anvil button pressed, opening planner");
                                Minecraft.getInstance().gui.setScreen(new PlannerScreen(anvil));
                            })
                    .bounds(left + imageWidth - WIDTH - INSET, top + INSET, WIDTH, HEIGHT)
                    .tooltip(Tooltip.create(Component.literal("Anvil Cost Calculator: plan the cheapest enchant order")))
                    .build();
        } catch (RuntimeException | ReflectiveOperationException e) {
            AnvilCostCalculatorMod.debug("Anvil button not added: {}", e.toString());
            return null;
        }
    }

    private static int readInt(AbstractContainerScreen<?> screen, String name) throws ReflectiveOperationException {
        Field field = AbstractContainerScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(screen);
    }
}
