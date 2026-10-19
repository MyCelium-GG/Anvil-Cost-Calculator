package my.celium.org.anvilcostcalculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anvil cost math and combine-order planning.
 *
 * <p>{@link #combine} mirrors vanilla 26.3 {@code AnvilMenu.createResult} for
 * a book in the right slot: prior work penalty of both inputs, per-enchantment
 * fee (halved for books) times the resulting level, +1 per conflicting
 * enchantment, and the doubled-plus-one work penalty on the result.
 * {@link #plan} then finds the cheapest order to fold a pile of books into a
 * target: exact search over every merge tree for small piles, a balanced
 * pairing heuristic for big ones.
 *
 * <p>Pure logic over enchantment ids - no registries, safe for headless tests.
 */
public final class AnvilMath {
    /** Vanilla shows "Too Expensive!" (survival) at this cost or above. */
    public static final int TOO_EXPENSIVE = 40;
    /** Piles up to this size are searched exhaustively (3^n merges). */
    public static final int EXACT_SEARCH_LIMIT = 8;

    private AnvilMath() {
    }

    /** Enchantment facts the math needs, keyed by registry id. */
    public interface Rules {
        int maxLevel(String id);

        int anvilCost(String id);

        /** Whether two different enchantments may share an item. */
        boolean compatible(String a, String b);

        /** Whether the enchantment can go onto the (non-book) target item. */
        boolean canApply(String id);
    }

    /** One anvil input: a target item or an enchanted book. */
    public record Piece(String label, Map<String, Integer> enchants, int repairCost, boolean book) {
        public Piece {
            enchants = Collections.unmodifiableMap(new LinkedHashMap<>(enchants));
        }

        /** Sum of enchantment levels, used to prefer the richest result. */
        public int score() {
            int sum = 0;
            for (int level : enchants.values()) {
                sum += level;
            }
            return sum;
        }
    }

    /** Result of one anvil operation: the level cost and the output. */
    public record Merge(int levels, Piece result) {
    }

    /** One instruction of a plan, e.g. {@code "Sharpness V" + "Looting III"}. */
    public record Step(int index, String left, String right, int levels, boolean tooExpensive) {
    }

    /** A full combine order and its total level cost. */
    public record Plan(List<Step> steps, int totalLevels, Piece result, boolean tooExpensive) {
        public static final Plan EMPTY = new Plan(List.of(), 0, null, false);
    }

    /**
     * Combines {@code right} (a book) into {@code left} exactly like vanilla,
     * or returns {@code null} when the anvil would show no result.
     */
    public static Merge combine(Piece left, Piece right, Rules rules) {
        long tax = (long) left.repairCost() + right.repairCost();
        int price = 0;
        Map<String, Integer> result = new LinkedHashMap<>(left.enchants());
        boolean anyCompatible = false;
        boolean anyIncompatible = false;
        for (Map.Entry<String, Integer> entry : right.enchants().entrySet()) {
            String id = entry.getKey();
            int current = result.getOrDefault(id, 0);
            int level = entry.getValue();
            level = current == level ? level + 1 : Math.max(level, current);
            boolean compatible = left.book() || rules.canApply(id);
            for (String other : result.keySet()) {
                if (!other.equals(id) && !rules.compatible(id, other)) {
                    compatible = false;
                    price++;
                }
            }
            if (!compatible) {
                anyIncompatible = true;
                continue;
            }
            anyCompatible = true;
            level = Math.min(level, rules.maxLevel(id));
            result.put(id, level);
            int fee = rules.anvilCost(id);
            if (right.book()) {
                fee = Math.max(1, fee / 2);
            }
            price += fee * level;
        }
        if ((anyIncompatible && !anyCompatible) || price <= 0) {
            return null;
        }
        int levels = (int) Math.min(tax + price, Integer.MAX_VALUE);
        int repairCost = increasedRepairCost(Math.max(left.repairCost(), right.repairCost()));
        return new Merge(levels, new Piece(left.label(), result, repairCost, left.book()));
    }

    /** Vanilla's prior work progression: 0, 1, 3, 7, 15, 31, ... */
    public static int increasedRepairCost(int repairCost) {
        return (int) Math.min(repairCost * 2L + 1L, Integer.MAX_VALUE);
    }

    /** How many anvil operations produced this prior work penalty. */
    public static int anvilUses(int repairCost) {
        return repairCost <= 0 ? 0 : 32 - Integer.numberOfLeadingZeros(repairCost);
    }

    /**
     * Cheapest order to combine every book into {@code target}. In survival
     * ({@code survival = true}) orders with a step of 40+ levels are avoided
     * when possible; if none exists the cheapest order is returned flagged
     * {@link Plan#tooExpensive()}.
     */
    public static Plan plan(Piece target, List<Piece> books, Rules rules, boolean survival) {
        if (books.isEmpty()) {
            return Plan.EMPTY;
        }
        Node root;
        if (books.size() <= EXACT_SEARCH_LIMIT) {
            root = survival ? exact(target, books, rules, true) : null;
            if (root == null) {
                root = exact(target, books, rules, false);
            }
        } else {
            root = balanced(target, books, rules);
        }
        if (root == null) {
            return Plan.EMPTY;
        }
        return flatten(root, survival);
    }

    /** Merge-tree node: a leaf input or the result of combining two nodes. */
    private record Node(Piece piece, Node left, Node right, int levels, int total, int worstStep) {
        static Node leaf(Piece piece) {
            return new Node(piece, null, null, 0, 0, 0);
        }
    }

    /**
     * Exhaustive search. {@code pile[mask]} holds the best single book that
     * can be built from that subset, per resulting prior work penalty (a
     * pricier merge with a lower penalty can win later). {@code onto[mask]}
     * does the same for the target with that subset applied.
     */
    private static Node exact(Piece target, List<Piece> books, Rules rules, boolean capped) {
        int n = books.size();
        int full = (1 << n) - 1;
        @SuppressWarnings("unchecked")
        Map<Integer, Node>[] pile = new Map[full + 1];
        @SuppressWarnings("unchecked")
        Map<Integer, Node>[] onto = new Map[full + 1];
        for (int mask = 1; mask <= full; mask++) {
            pile[mask] = new HashMap<>();
            if (Integer.bitCount(mask) == 1) {
                Piece book = books.get(Integer.numberOfTrailingZeros(mask));
                pile[mask].put(book.repairCost(), Node.leaf(book));
                continue;
            }
            for (int sub = (mask - 1) & mask; sub > 0; sub = (sub - 1) & mask) {
                for (Node l : pile[sub].values()) {
                    for (Node r : pile[mask ^ sub].values()) {
                        offer(pile[mask], l, r, rules, capped);
                    }
                }
            }
        }
        onto[0] = new HashMap<>();
        onto[0].put(target.repairCost(), Node.leaf(target));
        for (int mask = 1; mask <= full; mask++) {
            onto[mask] = new HashMap<>();
            for (int sub = mask; sub > 0; sub = (sub - 1) & mask) {
                for (Node l : onto[mask ^ sub].values()) {
                    for (Node r : pile[sub].values()) {
                        offer(onto[mask], l, r, rules, capped);
                    }
                }
            }
        }
        Node best = null;
        for (Node node : onto[full].values()) {
            if (best == null || better(node, best)) {
                best = node;
            }
        }
        return best;
    }

    private static void offer(Map<Integer, Node> slot, Node l, Node r, Rules rules, boolean capped) {
        Merge merge = combine(l.piece(), r.piece(), rules);
        if (merge == null || (capped && merge.levels() >= TOO_EXPENSIVE)) {
            return;
        }
        Node node = new Node(merge.result(), l, r, merge.levels(), l.total() + r.total() + merge.levels(),
                Math.max(merge.levels(), Math.max(l.worstStep(), r.worstStep())));
        int key = merge.result().repairCost();
        Node current = slot.get(key);
        if (current == null || better(node, current)) {
            slot.put(key, node);
        }
    }

    /** Higher levels first, then fewer total levels, then a cheaper worst step. */
    private static boolean better(Node a, Node b) {
        if (a.piece().score() != b.piece().score()) {
            return a.piece().score() > b.piece().score();
        }
        if (a.total() != b.total()) {
            return a.total() < b.total();
        }
        if (a.worstStep() != b.worstStep()) {
            return a.worstStep() < b.worstStep();
        }
        return a.piece().repairCost() < b.piece().repairCost();
    }

    /**
     * Large piles: repeatedly merge the two books with the lowest prior work
     * (cheaper one on the right, where fees are charged), building a balanced
     * tree, then apply the finished book to the target.
     */
    private static Node balanced(Piece target, List<Piece> books, Rules rules) {
        List<Node> pool = new ArrayList<>();
        for (Piece book : books) {
            pool.add(Node.leaf(book));
        }
        Comparator<Node> order = Comparator.comparingInt((Node node) -> node.piece().repairCost())
                .thenComparingInt(node -> value(node.piece(), rules));
        while (pool.size() > 1) {
            pool.sort(order);
            Node a = pool.remove(0);
            Node b = pool.remove(0);
            Node left = value(a.piece(), rules) >= value(b.piece(), rules) ? a : b;
            Node right = left == a ? b : a;
            Node merged = join(left, right, rules);
            if (merged == null) {
                merged = join(right, left, rules);
            }
            if (merged == null) {
                return null;
            }
            pool.add(merged);
        }
        return join(Node.leaf(target), pool.get(0), rules);
    }

    private static Node join(Node l, Node r, Rules rules) {
        Merge merge = combine(l.piece(), r.piece(), rules);
        if (merge == null) {
            return null;
        }
        return new Node(merge.result(), l, r, merge.levels(), l.total() + r.total() + merge.levels(),
                Math.max(merge.levels(), Math.max(l.worstStep(), r.worstStep())));
    }

    /** What a piece adds to a merge's price when sacrificed as a book. */
    static int value(Piece piece, Rules rules) {
        int sum = 0;
        for (Map.Entry<String, Integer> entry : piece.enchants().entrySet()) {
            sum += Math.max(1, rules.anvilCost(entry.getKey()) / 2) * entry.getValue();
        }
        return sum;
    }

    /** Post-order walk: merged inputs are named after the step that made them. */
    private static Plan flatten(Node root, boolean survival) {
        List<Step> steps = new ArrayList<>();
        walk(root, steps, survival);
        boolean tooExpensive = false;
        for (Step step : steps) {
            tooExpensive |= step.tooExpensive();
        }
        return new Plan(List.copyOf(steps), root.total(), root.piece(), tooExpensive);
    }

    private static String walk(Node node, List<Step> steps, boolean survival) {
        if (node.left() == null) {
            return node.piece().label();
        }
        String left = walk(node.left(), steps, survival);
        String right = walk(node.right(), steps, survival);
        int index = steps.size() + 1;
        steps.add(new Step(index, left, right, node.levels(), survival && node.levels() >= TOO_EXPENSIVE));
        return "#" + index;
    }

    /** Enchantment level as shown in game (I-X), plain digits beyond. */
    public static String roman(int level) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 1 && level <= numerals.length ? numerals[level - 1] : String.valueOf(level);
    }

    /** XP points needed to go from {@code level} to {@code level + 1} (vanilla). */
    public static int pointsForLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }

    /**
     * XP points spent paying {@code levels} levels starting at
     * {@code fromLevel} (at least {@code levels}, so a short player still
     * sees the minimum). Partial level progress is ignored.
     */
    public static long pointsToSpend(int fromLevel, int levels) {
        int top = Math.max(fromLevel, levels);
        long sum = 0;
        for (int level = top - levels; level < top; level++) {
            sum += pointsForLevel(level);
        }
        return sum;
    }
}
