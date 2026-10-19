package my.celium.org.anvilcostcalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import my.celium.org.anvilcostcalculator.AnvilMath.Merge;
import my.celium.org.anvilcostcalculator.AnvilMath.Piece;
import my.celium.org.anvilcostcalculator.AnvilMath.Plan;
import my.celium.org.anvilcostcalculator.AnvilMath.Step;

/** Headless tests for the vanilla cost formula and the combine-order planner. */
class AnvilMathTest {
    /** Vanilla sword enchantments: max level and anvil cost; protection is not for swords. */
    static final Map<String, int[]> ENCHANTS = Map.of(
            "sharpness", new int[] {5, 1},
            "smite", new int[] {5, 2},
            "unbreaking", new int[] {3, 2},
            "mending", new int[] {1, 4},
            "looting", new int[] {3, 4},
            "fire_aspect", new int[] {2, 4},
            "knockback", new int[] {2, 2},
            "sweeping_edge", new int[] {3, 4},
            "protection", new int[] {4, 1});

    static final AnvilMath.Rules SWORD = new AnvilMath.Rules() {
        @Override
        public int maxLevel(String id) {
            return ENCHANTS.get(id)[0];
        }

        @Override
        public int anvilCost(String id) {
            return ENCHANTS.get(id)[1];
        }

        @Override
        public boolean compatible(String a, String b) {
            return !(Set.of(a, b).equals(Set.of("sharpness", "smite")));
        }

        @Override
        public boolean canApply(String id) {
            return !id.equals("protection");
        }
    };

    static Piece sword(int repairCost, Map<String, Integer> enchants) {
        return new Piece("Sword", enchants, repairCost, false);
    }

    static Piece book(String id, int level) {
        return new Piece(id + " " + level, Map.of(id, level), 0, true);
    }

    @Test
    void bookOntoSwordHalvesTheFee() {
        Merge merge = AnvilMath.combine(sword(0, Map.of()), book("sharpness", 5), SWORD);
        assertEquals(5, merge.levels());
        assertEquals(1, merge.result().repairCost());
        assertEquals(5, merge.result().enchants().get("sharpness"));

        assertEquals(2, AnvilMath.combine(sword(0, Map.of()), book("mending", 1), SWORD).levels());
    }

    @Test
    void priorWorkOfBothSidesIsAdded() {
        Piece worn = sword(3, Map.of());
        Piece usedBook = new Piece("Unbreaking III", Map.of("unbreaking", 3), 1, true);
        Merge merge = AnvilMath.combine(worn, usedBook, SWORD);
        assertEquals(3 + 1 + 3, merge.levels());
        assertEquals(7, merge.result().repairCost());
    }

    @Test
    void equalLevelsMergeUpAndCapAtMax() {
        Merge merge = AnvilMath.combine(book("sharpness", 4), book("sharpness", 4), SWORD);
        assertEquals(5, merge.result().enchants().get("sharpness"));
        assertEquals(5, merge.levels());
        Merge capped = AnvilMath.combine(book("sharpness", 5), book("sharpness", 5), SWORD);
        assertEquals(5, capped.result().enchants().get("sharpness"));
    }

    @Test
    void conflictsCostOneAndAreDropped() {
        Piece sharp = sword(0, Map.of("sharpness", 5));
        assertNull(AnvilMath.combine(sharp, book("smite", 5), SWORD));

        Piece mixed = new Piece("Smite V, Unbreaking III", Map.of("smite", 5, "unbreaking", 3), 0, true);
        Merge merge = AnvilMath.combine(sharp, mixed, SWORD);
        assertEquals(1 + 3, merge.levels());
        assertFalse(merge.result().enchants().containsKey("smite"));
    }

    @Test
    void enchantmentsForOtherItemsAreIgnoredOnTools() {
        assertNull(AnvilMath.combine(sword(0, Map.of()), book("protection", 4), SWORD));
        Merge onBook = AnvilMath.combine(book("sharpness", 1), book("protection", 4), SWORD);
        assertEquals(4, onBook.result().enchants().get("protection"));
    }

    @Test
    void twoBooksPlanMatchesHandCount() {
        Plan plan = AnvilMath.plan(sword(0, Map.of()),
                List.of(book("mending", 1), book("unbreaking", 3)), SWORD, true);
        assertEquals(2, plan.steps().size());
        assertEquals(6, plan.totalLevels());
        assertFalse(plan.tooExpensive());
    }

    @Test
    void planBeatsOneByOne() {
        List<Piece> books = List.of(book("sharpness", 5), book("unbreaking", 3), book("mending", 1),
                book("looting", 3), book("sweeping_edge", 3), book("fire_aspect", 2), book("knockback", 2));
        Plan plan = AnvilMath.plan(sword(0, Map.of()), books, SWORD, true);

        int naive = 0;
        Piece current = sword(0, Map.of());
        boolean naiveTooExpensive = false;
        for (Piece book : books) {
            Merge merge = AnvilMath.combine(current, book, SWORD);
            naive += merge.levels();
            naiveTooExpensive |= merge.levels() >= AnvilMath.TOO_EXPENSIVE;
            current = merge.result();
        }
        assertTrue(naiveTooExpensive, "one-by-one should hit Too Expensive");
        assertFalse(plan.tooExpensive());
        assertTrue(plan.totalLevels() < naive);
        assertEquals(books.size(), plan.steps().size());
        assertEquals(7, plan.result().enchants().size());
        for (Step step : plan.steps()) {
            assertTrue(step.levels() < AnvilMath.TOO_EXPENSIVE);
        }
    }

    @Test
    void stepsReferToEarlierResults() {
        Plan plan = AnvilMath.plan(sword(0, Map.of()),
                List.of(book("sharpness", 5), book("unbreaking", 3), book("mending", 1), book("looting", 3)),
                SWORD, true);
        Step last = plan.steps().get(plan.steps().size() - 1);
        assertTrue(last.left().equals("Sword") || last.left().startsWith("#"));
    }

    @Test
    void wornOutItemIsFlaggedTooExpensive() {
        Plan plan = AnvilMath.plan(sword(39, Map.of()), List.of(book("mending", 1)), SWORD, true);
        assertTrue(plan.tooExpensive());
        Plan creative = AnvilMath.plan(sword(39, Map.of()), List.of(book("mending", 1)), SWORD, false);
        assertFalse(creative.tooExpensive());
    }

    @Test
    void largePilesUseTheBalancedPlan() {
        List<Piece> books = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            books.add(book("sharpness", 1));
        }
        Plan plan = AnvilMath.plan(book("unbreaking", 1), books, SWORD, true);
        assertEquals(10, plan.steps().size());
        assertTrue(plan.result().enchants().get("sharpness") >= 4);
    }

    @Test
    void goldenSwordExample() {
        // Golden sword with Sharpness IV + Looting I, adding Sharpness V and Knockback II books.
        Piece sword = sword(0, Map.of("sharpness", 4, "looting", 1));
        Plan plan = AnvilMath.plan(sword, List.of(book("sharpness", 5), book("knockback", 2)), SWORD, true);
        // Best: books straight onto the sword (5, then 1 prior work + 2 = 3) for 8;
        // merging the books first costs 2 + (1 + 5 + 2) = 10.
        assertEquals(8, plan.totalLevels());
        assertEquals(2, plan.steps().size());
        assertEquals("Sword", plan.steps().get(0).left());
        assertEquals("#1", plan.steps().get(1).left());
        assertEquals(5, plan.result().enchants().get("sharpness"));
        assertEquals(1, plan.result().enchants().get("looting"));
        assertEquals(2, plan.result().enchants().get("knockback"));
    }

    @Test
    void bookOnlyCombine() {
        Piece base = book("unbreaking", 3);
        Plan plan = AnvilMath.plan(base, List.of(book("protection", 4), book("mending", 1)), SWORD, true);
        assertEquals(3, plan.result().enchants().size());
        assertFalse(plan.tooExpensive());
    }

    @Test
    void romanNumerals() {
        assertEquals("I", AnvilMath.roman(1));
        assertEquals("IV", AnvilMath.roman(4));
        assertEquals("X", AnvilMath.roman(10));
        assertEquals("11", AnvilMath.roman(11));
    }

    @Test
    void priorWorkHelpers() {
        assertEquals(0, AnvilMath.anvilUses(0));
        assertEquals(1, AnvilMath.anvilUses(1));
        assertEquals(2, AnvilMath.anvilUses(3));
        assertEquals(5, AnvilMath.anvilUses(31));
        assertEquals(63, AnvilMath.increasedRepairCost(31));
    }

    @Test
    void xpPointsMatchVanilla() {
        assertEquals(7, AnvilMath.pointsForLevel(0));
        assertEquals(37, AnvilMath.pointsForLevel(15));
        assertEquals(112, AnvilMath.pointsForLevel(30));
        assertEquals(1395, AnvilMath.pointsToSpend(30, 30));
        assertEquals(AnvilMath.pointsForLevel(29), AnvilMath.pointsToSpend(30, 1));
        assertEquals(AnvilMath.pointsToSpend(5, 5), AnvilMath.pointsToSpend(2, 5));
    }
}
