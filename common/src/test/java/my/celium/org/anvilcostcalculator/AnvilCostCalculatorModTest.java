package my.celium.org.anvilcostcalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import my.celium.org.Mycel;
import my.celium.org.anvilcostcalculator.client.AnvilButton;
import my.celium.org.anvilcostcalculator.client.TooltipLines;
import my.celium.org.testing.MycelTesting;

/** The mod boots on Mycel with a tiny, well-defaulted config. */
class AnvilCostCalculatorModTest {
    @BeforeAll
    static void initOnce(@TempDir Path tempDir) {
        MycelTesting.useConfigDir(tempDir);
        AnvilCostCalculatorMod.init();
    }

    @Test
    void registersWithMycel() {
        assertTrue(Mycel.getRegisteredMods().contains(AnvilCostCalculatorMod.META));
        assertEquals("anvilcostcalculator", AnvilCostCalculatorMod.META.id());
    }

    @Test
    void configDefaults() {
        assertTrue(AnvilCostCalculatorMod.enabled.get());
        assertTrue(AnvilCostCalculatorMod.showButton.get());
        assertTrue(AnvilCostCalculatorMod.showTooltip.get());
        assertTrue(AnvilCostCalculatorMod.showXpPoints.get());
    }

    @Test
    void enableToggleFlowsThrough() {
        assertTrue(AnvilCostCalculatorMod.isModEnabled());
        Mycel.setEnabled(AnvilCostCalculatorMod.ID, false);
        try {
            assertTrue(!AnvilCostCalculatorMod.isModEnabled());
        } finally {
            Mycel.setEnabled(AnvilCostCalculatorMod.ID, true);
        }
        assertTrue(AnvilCostCalculatorMod.isModEnabled());
    }

    @Test
    void hooksTolerateNulls() {
        // Loader screen and tooltip hooks must never crash the frame.
        assertNull(AnvilButton.create(null));
        assertNull(TooltipLines.lineFor(null));
    }

    @Test
    void tooltipLineText() {
        assertNull(TooltipLines.line(0));
        // No language is loaded in tests, so the labels read as keys; the numbers are what matters.
        String three = TooltipLines.line(3).getString();
        assertTrue(three.contains(" 2 · ") && three.contains("tooltip.lv") && !three.contains("maxed"), three);
        String maxed = TooltipLines.line(63).getString();
        assertTrue(maxed.contains(" 6 · ") && maxed.contains("tooltip.maxed"), maxed);
    }
}
