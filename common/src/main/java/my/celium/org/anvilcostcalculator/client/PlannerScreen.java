package my.celium.org.anvilcostcalculator.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import my.celium.org.anvilcostcalculator.AnvilCostCalculatorMod;
import my.celium.org.anvilcostcalculator.AnvilMath;
import my.celium.org.anvilcostcalculator.AnvilMath.Piece;
import my.celium.org.anvilcostcalculator.AnvilMath.Plan;
import my.celium.org.anvilcostcalculator.AnvilMath.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The planner opened from the anvil button. Page one picks the item: every
 * item at least one enchantment can go on, plus "Enchanted Book" for
 * combining books only. Page two lists each enchantment that item accepts
 * with two level selectors - what the item already has and which book to
 * add - plus its prior anvil uses, and shows the cheapest combine order live.
 *
 * <p>Opening it over the anvil keeps the anvil open (replacing a container
 * screen sends nothing to the server), so closing it returns to the anvil
 * with its items in place. Client only.
 */
public final class PlannerScreen extends Screen {
    // MyCelium wiki dark theme (MyCelium Web/public/assets/style.css): flat neutrals,
    // one red accent, 2px borders, hard offset card shadows. Green/orange are the
    // wiki's code-highlight tokens, used only for cost.
    static final int COLOR_SHADE = 0xD8171717;
    static final int COLOR_BG = 0xFF171717;
    static final int COLOR_SURFACE = 0xFF262626;
    static final int COLOR_BORDER = 0xFF383838;
    static final int COLOR_SHADOW = 0x66000000;
    static final int COLOR_HEADER_EDGE = 0x40000000;
    static final int COLOR_ACCENT = 0xFFEF5350;
    static final int COLOR_ACCENT_DARK = 0xFFB71C1C;
    static final int COLOR_NOTE = 0xFF2B1A1A;
    static final int COLOR_HEADING = 0xFFFFFFFF;
    static final int COLOR_TEXT = 0xFFE6E6E6;
    static final int COLOR_DIM = 0xFFA3A3A3;
    static final int COLOR_OFF = 0xFF5F5F5F;
    static final int COLOR_GOOD = 0xFF7EE787;
    static final int COLOR_WARN = 0xFFFFA657;
    static final int COLOR_BAD = COLOR_ACCENT;
    static final int COLOR_ZEBRA = 0x08FFFFFF;
    static final int COLOR_ROW_HOVER = COLOR_SURFACE;
    static final int COLOR_CHIP_BAD = COLOR_NOTE;
    /** Steps up to this many levels count as cheap (green). */
    static final int CHEAP_LEVELS = 15;

    static final int TITLE_BAR = 18;
    static final int MARGIN = 8;
    static final int CELL = 18;
    static final int MAX_COLUMNS = 20;
    static final int ROW_H = 12;
    static final int STEP_BOX = 9;
    static final int STEP_VALUE = 18;
    static final int STEP_W = STEP_BOX * 2 + STEP_VALUE;
    static final int MAX_USES = 6;
    static final int HEADER_Y = 24;
    static final int GRID_TOP = 44;
    static final int LIST_HEAD_Y = 46;
    static final int LIST_TOP = 59;
    static final int FOOTER = 32;

    private final AnvilScreen parent;
    private final PlannerState state = PlannerState.CURRENT;
    private final List<Holder<Enchantment>> allEnchantments;
    private final List<ItemStack> allItems;
    private List<ItemStack> filtered = List.of();
    private boolean picking;
    private int pickScroll;
    private int rowScroll;
    private int stepScroll;
    private EditBox search;

    private ItemStack target = ItemStack.EMPTY;
    private List<Holder<Enchantment>> enchantments = List.of();
    private PlannerRules rules;
    private Plan plan = Plan.EMPTY;
    private boolean dirty = true;

    public PlannerScreen(AnvilScreen parent) {
        super(Component.literal("Anvil Cost Calculator"));
        this.parent = parent;
        Minecraft minecraft = Minecraft.getInstance();
        this.allEnchantments = new ArrayList<>(minecraft.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).listElements().toList());
        this.allItems = enchantableItems(allEnchantments);
        prefillFromAnvil();
        this.picking = state.item == null;
        loadItem();
        filter();
        AnvilCostCalculatorMod.debug("Planner opened: {} enchantments, {} enchantable items, item={}, page={}",
                allEnchantments.size(), allItems.size(), state.item, picking ? "picker" : "editor");
    }

    /**
     * Enchanted Book first, then every item some non-curse enchantment supports, in
     * registry order. Items that only take curses (pumpkins, heads, compasses) are left out.
     */
    static List<ItemStack> enchantableItems(List<Holder<Enchantment>> enchantments) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.ENCHANTED_BOOK));
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.BOOK || item == Items.ENCHANTED_BOOK) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) {
                continue;
            }
            for (Holder<Enchantment> holder : enchantments) {
                if (!holder.is(EnchantmentTags.CURSE) && holder.value().canEnchant(stack)) {
                    items.add(stack);
                    break;
                }
            }
        }
        return items;
    }

    /** Starts from whatever sits in the anvil's left slot: item, enchantments and anvil uses. */
    private void prefillFromAnvil() {
        ItemStack left = parent.getMenu().getSlot(AnvilMenu.INPUT_SLOT).getItem();
        if (left.isEmpty() || !EnchantmentHelper.canStoreEnchantments(left)) {
            return;
        }
        boolean known = false;
        for (ItemStack stack : allItems) {
            known |= stack.getItem() == left.getItem();
        }
        if (!known) {
            return;
        }
        state.pick(left.getItem());
        state.onItem.clear();
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(left).entrySet()) {
            state.onItem.put(PlannerRules.id(entry.getKey()), entry.getIntValue());
        }
        state.anvilUses = Math.min(MAX_USES,
                AnvilMath.anvilUses(left.getOrDefault(DataComponents.REPAIR_COST, 0)));
        AnvilCostCalculatorMod.debug("Prefilled from anvil: {} with {} and {} anvil uses",
                left.getItem(), state.onItem, state.anvilUses);
    }

    /** Builds the enchantment rows for the picked item and drops levels it cannot take. */
    private void loadItem() {
        if (state.item == null) {
            return;
        }
        target = new ItemStack(state.item);
        boolean book = target.is(Items.ENCHANTED_BOOK);
        List<Holder<Enchantment>> rows = new ArrayList<>();
        for (Holder<Enchantment> holder : allEnchantments) {
            if (book || holder.value().canEnchant(target)) {
                rows.add(holder);
            }
        }
        rows.sort(Comparator.comparing((Holder<Enchantment> h) -> h.is(EnchantmentTags.CURSE))
                .thenComparing(h -> h.value().description().getString()));
        enchantments = rows;
        rules = new PlannerRules(target, rows);
        List<String> ids = rows.stream().map(PlannerRules::id).toList();
        state.onItem.keySet().retainAll(ids);
        state.books.keySet().retainAll(ids);
        rowScroll = 0;
        stepScroll = 0;
        dirty = true;
        AnvilCostCalculatorMod.debug("Loaded {}: {} enchantment rows, on item {}, books {}",
                state.item, rows.size(), state.onItem, state.books);
    }

    private void filter() {
        String query = state.search.trim().toLowerCase(Locale.ROOT);
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : allItems) {
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (query.isEmpty() || name.contains(query) || id.contains(query)) {
                out.add(stack);
            }
        }
        filtered = out;
        pickScroll = 0;
    }

    @Override
    protected void init() {
        int buttonY = height - FOOTER + 6;
        if (picking) {
            search = new EditBox(font, width / 2 - 80, HEADER_Y, 160, 14, Component.literal("Search"));
            search.setHint(Component.literal("Search items…"));
            search.setValue(state.search);
            search.setResponder(text -> {
                state.search = text;
                filter();
            });
            addRenderableWidget(search);
            if (state.item != null) {
                addRenderableWidget(Button.builder(Component.literal("Back to planner"), button -> showPage(false))
                        .bounds(width / 2 - 104, buttonY, 100, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Back to anvil"), button -> onClose())
                        .bounds(width / 2 + 4, buttonY, 100, 20).build());
            } else {
                addRenderableWidget(Button.builder(Component.literal("Back to anvil"), button -> onClose())
                        .bounds(width / 2 - 50, buttonY, 100, 20).build());
            }
        } else {
            search = null;
            addRenderableWidget(Button.builder(Component.literal("Change item"), button -> showPage(true))
                    .bounds(MARGIN, buttonY, 80, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Clear books"), button -> {
                state.books.clear();
                dirty = true;
            }).bounds(MARGIN + 84, buttonY, 80, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Back to anvil"), button -> onClose())
                    .bounds(width - MARGIN - 100, buttonY, 100, 20).build());
        }
    }

    @Override
    protected void setInitialFocus() {
        if (search != null) {
            setInitialFocus(search);
        } else {
            super.setInitialFocus();
        }
    }

    private void showPage(boolean pick) {
        AnvilCostCalculatorMod.debug("Page -> {}", pick ? "picker" : "editor");
        picking = pick;
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Back to the anvil if it is still open server-side, otherwise to the game. */
    @Override
    public void onClose() {
        LocalPlayer player = minecraft.player;
        boolean anvilOpen = player != null && player.containerMenu == parent.getMenu();
        AnvilCostCalculatorMod.debug("Planner closed, returning to {}", anvilOpen ? "anvil" : "game");
        if (anvilOpen) {
            minecraft.gui.setScreen(parent);
        } else {
            minecraft.gui.setScreen(null);
        }
    }

    // ---------------------------------------------------------------- render

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float a) {
        // Chrome first so widgets (search box, buttons) draw on top of the panels.
        gfx.fill(0, 0, width, height, COLOR_SHADE);
        gfx.fill(0, 0, width, TITLE_BAR, COLOR_ACCENT_DARK);
        gfx.fill(0, TITLE_BAR - 2, width, TITLE_BAR, COLOR_HEADER_EDGE);
        gfx.centeredText(font, title, width / 2, 4, COLOR_HEADING);
        if (picking) {
            panel(gfx, gridLeft() - 5, GRID_TOP - 5, gridLeft() + columns() * CELL + 8,
                    GRID_TOP + gridRows() * CELL + 5);
        } else {
            panel(gfx, 4, TITLE_BAR + 2, width / 2 - 1, height - FOOTER + 2);
            panel(gfx, width / 2 + 1, TITLE_BAR + 2, width - 4, height - FOOTER + 2);
        }
        super.extractRenderState(gfx, mouseX, mouseY, a);
        if (picking) {
            renderPicker(gfx, mouseX, mouseY);
        } else {
            renderEditor(gfx, mouseX, mouseY);
        }
    }

    /** A wiki card: hard offset shadow, 2px border, flat body. */
    private static void panel(GuiGraphicsExtractor gfx, int x0, int y0, int x1, int y1) {
        gfx.fill(x0 + 3, y0 + 3, x1 + 3, y1 + 3, COLOR_SHADOW);
        gfx.fill(x0, y0, x1, y1, COLOR_BORDER);
        gfx.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, COLOR_BG);
    }

    private int columns() {
        return Math.max(1, Math.min(MAX_COLUMNS, (width - MARGIN * 2 - 8) / CELL));
    }

    private int gridLeft() {
        return (width - columns() * CELL) / 2;
    }

    private int gridRows() {
        return Math.max(1, (height - FOOTER - GRID_TOP - 6) / CELL);
    }

    private int maxPickScroll() {
        int totalRows = (filtered.size() + columns() - 1) / columns();
        return Math.max(0, totalRows - gridRows());
    }

    private void renderPicker(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        if (filtered.isEmpty()) {
            gfx.centeredText(font, "No enchantable item matches", width / 2, GRID_TOP + 8, COLOR_DIM);
            return;
        }
        pickScroll = Math.min(pickScroll, maxPickScroll());
        int hovered = itemAt(mouseX, mouseY);
        for (int row = 0; row < gridRows(); row++) {
            for (int col = 0; col < columns(); col++) {
                int index = (row + pickScroll) * columns() + col;
                if (index >= filtered.size()) {
                    break;
                }
                ItemStack stack = filtered.get(index);
                int x = gridLeft() + col * CELL;
                int y = GRID_TOP + row * CELL;
                slot(gfx, x, y, stack.getItem() == state.item, index == hovered, stack.is(Items.ENCHANTED_BOOK));
                gfx.item(stack, x + 1, y + 1);
            }
        }
        if (hovered >= 0) {
            ItemStack stack = filtered.get(hovered);
            Component name = stack.is(Items.ENCHANTED_BOOK)
                    ? colored("Enchanted Book", COLOR_TEXT).append(colored("  combine books only", COLOR_DIM))
                    : colored(stack.getHoverName().getString(), COLOR_TEXT);
            gfx.setTooltipForNextFrame(font, name, mouseX, mouseY);
        }
        drawScrollbar(gfx, gridLeft() + columns() * CELL + 3, GRID_TOP, gridRows() * CELL, pickScroll,
                maxPickScroll());
    }

    /** Flat tile: surface with a border, red border on hover, red tint when picked. */
    private static void slot(GuiGraphicsExtractor gfx, int x, int y, boolean picked, boolean hovered,
            boolean special) {
        int x1 = x + CELL - 1;
        int y1 = y + CELL - 1;
        gfx.fill(x, y, x1, y1, picked || hovered ? COLOR_ACCENT : COLOR_BORDER);
        gfx.fill(x + 1, y + 1, x1 - 1, y1 - 1, picked || special ? COLOR_NOTE : COLOR_SURFACE);
    }

    private int itemAt(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - gridLeft()) / CELL);
        int row = (int) Math.floor((mouseY - GRID_TOP) / CELL);
        if (mouseX < gridLeft() || mouseY < GRID_TOP || col >= columns() || row >= gridRows()) {
            return -1;
        }
        int index = (row + pickScroll) * columns() + col;
        return index < filtered.size() ? index : -1;
    }

    private int leftEnd() {
        return width / 2 - 8;
    }

    private int onItemX() {
        return leftEnd() - STEP_W * 2 - 6;
    }

    private int bookX() {
        return leftEnd() - STEP_W;
    }

    private int usesY() {
        return HEADER_Y + 1;
    }

    private int visibleRows() {
        return Math.max(1, (height - FOOTER - LIST_TOP - 2) / ROW_H);
    }

    private int maxRowScroll() {
        return Math.max(0, enchantments.size() - visibleRows());
    }

    private void renderEditor(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        int x0 = MARGIN;
        int x1 = leftEnd();
        gfx.fill(6, TITLE_BAR + 4, width / 2 - 3, LIST_HEAD_Y - 4, COLOR_SURFACE);
        gfx.item(target, x0, HEADER_Y - 2);
        String usesLabel = "Anvil uses";
        int usesLabelX = x1 - STEP_W - 4 - font.width(usesLabel);
        String name = target.is(Items.ENCHANTED_BOOK) ? "Enchanted Book" : target.getHoverName().getString();
        gfx.text(font, fit(name, usesLabelX - x0 - 24), x0 + 20, HEADER_Y + 2, COLOR_HEADING);
        gfx.text(font, usesLabel, usesLabelX, HEADER_Y + 2, COLOR_DIM);
        drawStepper(gfx, x1 - STEP_W, usesY(), String.valueOf(state.anvilUses),
                state.anvilUses > 0 ? COLOR_WARN : COLOR_OFF, state.anvilUses > 0, state.anvilUses < MAX_USES,
                mouseX, mouseY);
        if (mouseY >= usesY() && mouseY < usesY() + 10 && mouseX >= usesLabelX && mouseX < x1) {
            int penalty = (1 << state.anvilUses) - 1;
            gfx.setTooltipForNextFrame(font, colored("Times this item went through an anvil before: ", COLOR_TEXT)
                    .append(colored("+" + penalty + " levels", COLOR_WARN))
                    .append(colored(" prior work on its next step", COLOR_TEXT)), mouseX, mouseY);
        }

        gfx.text(font, "Enchantment", x0, LIST_HEAD_Y, COLOR_DIM);
        gfx.centeredText(font, "On item", onItemX() + STEP_W / 2, LIST_HEAD_Y, COLOR_DIM);
        gfx.centeredText(font, "Book", bookX() + STEP_W / 2, LIST_HEAD_Y, COLOR_TEXT);
        gfx.fill(x0, LIST_HEAD_Y + 10, x1, LIST_HEAD_Y + 12, COLOR_BORDER);

        if (enchantments.isEmpty()) {
            gfx.text(font, "Nothing can enchant this", x0, LIST_TOP + 2, COLOR_DIM);
        }
        rowScroll = Math.min(rowScroll, maxRowScroll());
        for (int i = 0; i < visibleRows(); i++) {
            int index = i + rowScroll;
            if (index >= enchantments.size()) {
                break;
            }
            Holder<Enchantment> holder = enchantments.get(index);
            String id = PlannerRules.id(holder);
            int max = holder.value().getMaxLevel();
            int have = state.onItem.getOrDefault(id, 0);
            int add = state.books.getOrDefault(id, 0);
            Holder<Enchantment> conflict = conflictFor(holder);
            int y = LIST_TOP + i * ROW_H;
            boolean rowHovered = mouseY >= y && mouseY < y + ROW_H && mouseX >= x0 && mouseX < x1;
            if (index % 2 == 1) {
                gfx.fill(x0 - 2, y - 1, x1 + 2, y + ROW_H - 1, COLOR_ZEBRA);
            }
            if (rowHovered) {
                gfx.fill(x0 - 2, y - 1, x1 + 2, y + ROW_H - 1, COLOR_ROW_HOVER);
            }
            if (have > 0 || add > 0) {
                gfx.fill(x0 - 3, y - 1, x0 - 1, y + ROW_H - 1, add > 0 ? COLOR_ACCENT : COLOR_OFF);
            }
            boolean blocked = conflict != null && have == 0 && add == 0;
            int color = blocked ? COLOR_OFF : holder.is(EnchantmentTags.CURSE) ? COLOR_ACCENT : COLOR_TEXT;
            String label = holder.value().description().getString();
            gfx.text(font, fit(label, onItemX() - x0 - 4), x0 + 1, y + 2, color);
            drawStepper(gfx, onItemX(), y + 1, roman(have), levelColor(have, max, COLOR_TEXT), have > 0,
                    have < max && (conflict == null || have > 0), mouseX, mouseY);
            drawStepper(gfx, bookX(), y + 1, roman(add), levelColor(add, max, COLOR_ACCENT), add > 0,
                    add < max && (conflict == null || add > 0), mouseX, mouseY);
            if (rowHovered && mouseX < onItemX()) {
                Component tip = conflict != null
                        ? colored("Conflicts with ", COLOR_TEXT)
                                .append(colored(conflict.value().description().getString(), COLOR_BAD))
                        : colored("Max level ", COLOR_TEXT).append(colored(AnvilMath.roman(max), COLOR_GOOD));
                gfx.setTooltipForNextFrame(font, tip, mouseX, mouseY);
            }
        }
        drawScrollbar(gfx, x1 + 4, LIST_TOP, visibleRows() * ROW_H, rowScroll, maxRowScroll());
        renderPlan(gfx);
    }

    /** Level text color: off at 0, green at max, {@code normal} in between. */
    private static int levelColor(int level, int max, int normal) {
        return level <= 0 ? COLOR_OFF : level >= max ? COLOR_GOOD : normal;
    }

    private void renderPlan(GuiGraphicsExtractor gfx) {
        int x0 = width / 2 + 7;
        int x1 = width - MARGIN - 2;
        int bottom = height - FOOTER - 2;
        Plan current = plan();
        gfx.fill(width / 2 + 3, TITLE_BAR + 4, width - 6, LIST_HEAD_Y - 4, COLOR_SURFACE);
        gfx.text(font, "Cheapest order", x0, HEADER_Y + 2, COLOR_HEADING);
        if (current.steps().isEmpty()) {
            int mid = (x0 + x1) / 2;
            int y = (LIST_TOP + bottom) / 2 - 12;
            gfx.centeredText(font, "Nothing to combine yet", mid, y, COLOR_TEXT);
            gfx.centeredText(font, fit("Set a level under \"Book\" to add it", x1 - x0), mid, y + 12, COLOR_DIM);
            return;
        }
        String count = current.steps().size() + (current.steps().size() == 1 ? " step" : " steps");
        gfx.text(font, count, x1 - font.width(count), HEADER_Y + 2, COLOR_DIM);
        gfx.text(font, fit("Left slot + right slot, top to bottom", x1 - x0), x0, LIST_HEAD_Y, COLOR_DIM);
        gfx.fill(x0, LIST_HEAD_Y + 10, x1, LIST_HEAD_Y + 12, COLOR_BORDER);

        LocalPlayer player = minecraft.player;
        boolean survival = player != null && !player.hasInfiniteMaterials();
        List<Footer> notes = new ArrayList<>();
        if (survival) {
            String have = "You have " + player.experienceLevel + " levels";
            if (readFlag(() -> AnvilCostCalculatorMod.showXpPoints.get())) {
                have += String.format(Locale.ROOT, " · ≈ %,d XP",
                        AnvilMath.pointsToSpend(player.experienceLevel, current.totalLevels()));
            }
            notes.add(new Footer(have, COLOR_DIM));
        }
        if (current.tooExpensive()) {
            notes.add(new Footer("Too Expensive! No order stays under 40", COLOR_BAD));
        }
        if (state.books.size() > AnvilMath.EXACT_SEARCH_LIMIT) {
            notes.add(new Footer("Quick plan (over " + AnvilMath.EXACT_SEARCH_LIMIT + " books)", COLOR_DIM));
        }
        List<FormattedCharSequence> result = font.split(colored("Result: ", COLOR_DIM)
                .append(colored(resultText(current), COLOR_TEXT)), x1 - x0);

        int footerHeight = 6 + 14 + (notes.size() + result.size()) * ROW_H;
        int stepRows = Math.max(1, (bottom - LIST_TOP - footerHeight) / ROW_H);
        int maxScroll = Math.max(0, current.steps().size() - stepRows);
        stepScroll = Math.min(stepScroll, maxScroll);
        int y = LIST_TOP;
        for (int i = 0; i < stepRows && i + stepScroll < current.steps().size(); i++) {
            Step step = current.steps().get(i + stepScroll);
            if ((i + stepScroll) % 2 == 1) {
                gfx.fill(x0 - 2, y - 1, x1 + 2, y + ROW_H - 1, COLOR_ZEBRA);
            }
            String number = String.valueOf(step.index());
            int badge = Math.max(10, font.width(number) + 4);
            gfx.fill(x0, y, x0 + badge, y + 10, COLOR_ACCENT_DARK);
            gfx.centeredText(font, number, x0 + badge / 2 + 1, y + 1, COLOR_HEADING);
            int chipWidth = chip(gfx, x1, y, step.levels() + " lv", step.levels(), step.tooExpensive());
            int textX = x0 + badge + 4;
            gfx.text(font, stepLine(step, x1 - chipWidth - 6 - textX), textX, y + 1, COLOR_TEXT);
            y += ROW_H;
        }
        drawScrollbar(gfx, x1 + 4, LIST_TOP, stepRows * ROW_H, stepScroll, maxScroll);

        y = LIST_TOP + stepRows * ROW_H + 4;
        gfx.fill(x0 - 2, y, x1 + 2, y + 14, COLOR_SURFACE);
        gfx.fill(x0 - 2, y, x0 + 1, y + 14, COLOR_ACCENT);
        gfx.text(font, "Total", x0 + 5, y + 3, COLOR_HEADING);
        String total = current.totalLevels() + " levels";
        boolean affordable = !survival || current.totalLevels() <= player.experienceLevel;
        gfx.text(font, total, x1 - font.width(total) - 2, y + 3,
                current.tooExpensive() ? COLOR_BAD : affordable ? COLOR_GOOD : COLOR_WARN);
        y += 18;
        for (Footer note : notes) {
            gfx.text(font, fit(note.text(), x1 - x0), x0, y, note.color());
            y += ROW_H;
        }
        for (FormattedCharSequence line : result) {
            if (y + font.lineHeight > bottom) {
                break;
            }
            gfx.text(font, line, x0, y, COLOR_TEXT);
            y += ROW_H;
        }
    }

    /** "A + B" with the operands in white/violet and a dim plus, cut to {@code room} pixels. */
    private Component stepLine(Step step, int room) {
        String plain = step.left() + " + " + step.right();
        if (font.width(plain) > room) {
            return colored(fit(plain, room), COLOR_TEXT);
        }
        return colored(step.left(), step.left().startsWith("#") ? COLOR_ACCENT : COLOR_TEXT)
                .append(colored(" + ", COLOR_DIM))
                .append(colored(step.right(), step.right().startsWith("#") ? COLOR_ACCENT : COLOR_TEXT));
    }

    /** Right-aligned cost pill ending at {@code x1}, colored by price; returns its width. */
    private int chip(GuiGraphicsExtractor gfx, int x1, int y, String text, int levels, boolean tooExpensive) {
        int textColor;
        int fill;
        if (tooExpensive || levels >= AnvilMath.TOO_EXPENSIVE) {
            textColor = COLOR_BAD;
            fill = COLOR_CHIP_BAD;
        } else {
            textColor = levels <= CHEAP_LEVELS ? COLOR_GOOD : COLOR_WARN;
            fill = COLOR_SURFACE;
        }
        int width = font.width(text) + 6;
        gfx.fill(x1 - width, y, x1, y + 10, fill);
        gfx.text(font, text, x1 - width + 3, y + 1, textColor);
        return width;
    }

    private record Footer(String text, int color) {
    }

    private static MutableComponent colored(String text, int argb) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(argb & 0xFFFFFF)));
    }

    private String resultText(Plan current) {
        List<String> parts = new ArrayList<>();
        for (Holder<Enchantment> holder : enchantments) {
            Integer level = current.result().enchants().get(PlannerRules.id(holder));
            if (level != null) {
                parts.add(holder.value().description().getString() + " " + AnvilMath.roman(level));
            }
        }
        return String.join(", ", parts);
    }

    private void drawStepper(GuiGraphicsExtractor gfx, int x, int y, String value, int valueColor, boolean canDown,
            boolean canUp, int mouseX, int mouseY) {
        drawBox(gfx, x, y, "-", canDown, mouseX, mouseY);
        gfx.fill(x + STEP_BOX, y, x + STEP_BOX + STEP_VALUE, y + 10, COLOR_BG);
        gfx.centeredText(font, value, x + STEP_BOX + STEP_VALUE / 2, y + 1, valueColor);
        drawBox(gfx, x + STEP_BOX + STEP_VALUE, y, "+", canUp, mouseX, mouseY);
    }

    private void drawBox(GuiGraphicsExtractor gfx, int x, int y, String sign, boolean enabled, int mouseX,
            int mouseY) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + STEP_BOX && mouseY >= y && mouseY < y + 10;
        gfx.fill(x, y, x + STEP_BOX, y + 10, hovered ? COLOR_ACCENT_DARK : COLOR_BORDER);
        gfx.fill(x + 1, y + 1, x + STEP_BOX - 1, y + 9, hovered ? COLOR_ACCENT_DARK : COLOR_SURFACE);
        gfx.centeredText(font, sign, x + STEP_BOX / 2 + 1, y + 1,
                !enabled ? COLOR_OFF : hovered ? COLOR_HEADING : COLOR_DIM);
    }

    private void drawScrollbar(GuiGraphicsExtractor gfx, int x, int top, int height, int scroll, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        int thumb = Math.max(8, height / (maxScroll + 1));
        int y = top + (height - thumb) * scroll / maxScroll;
        gfx.fill(x, top, x + 2, top + height, COLOR_SURFACE);
        gfx.fill(x, y, x + 2, y + thumb, COLOR_OFF);
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    private static String roman(int level) {
        return level <= 0 ? "·" : AnvilMath.roman(level);
    }

    // ----------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        AnvilCostCalculatorMod.debug("Click button={} at ({}, {}) shift={} page={} screen={}x{}",
                event.button(), String.format(Locale.ROOT, "%.1f", event.x()),
                String.format(Locale.ROOT, "%.1f", event.y()), event.hasShiftDown(),
                picking ? "picker" : "editor", width, height);
        if (super.mouseClicked(event, doubleClick)) {
            AnvilCostCalculatorMod.debug("  -> handled by widget {}", getFocused());
            return true;
        }
        // 26.x mouse input is SDL-based: left is 1 there, not GLFW's 0.
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            AnvilCostCalculatorMod.debug("  -> ignored: not the left button ({})", InputConstants.MOUSE_BUTTON_LEFT);
            return false;
        }
        double mx = event.x();
        double my = event.y();
        if (picking) {
            int index = itemAt(mx, my);
            if (index < 0) {
                AnvilCostCalculatorMod.debug("  -> no item cell (grid left {}, top {}, {} cols x {} rows, scroll {}, {} items)",
                        gridLeft(), GRID_TOP, columns(), gridRows(), pickScroll, filtered.size());
                return false;
            }
            AnvilCostCalculatorMod.debug("  -> picked cell {}: {}", index, filtered.get(index).getItem());
            state.pick(filtered.get(index).getItem());
            loadItem();
            showPage(false);
            return true;
        }
        boolean jump = event.hasShiftDown();
        int usesStep = stepperHit(leftEnd() - STEP_W, usesY(), mx, my);
        if (usesStep != 0) {
            int next = jump ? (usesStep > 0 ? MAX_USES : 0) : state.anvilUses + usesStep;
            state.anvilUses = Math.max(0, Math.min(MAX_USES, next));
            AnvilCostCalculatorMod.debug("  -> anvil uses now {}", state.anvilUses);
            dirty = true;
            return true;
        }
        if (my < LIST_TOP || mx < MARGIN || mx >= leftEnd()) {
            AnvilCostCalculatorMod.debug("  -> outside the enchantment list");
            return false;
        }
        int index = (int) ((my - LIST_TOP) / ROW_H) + rowScroll;
        if (index >= enchantments.size() || (my - LIST_TOP) / ROW_H >= visibleRows()) {
            AnvilCostCalculatorMod.debug("  -> below the last row (row {}, {} rows)", index, enchantments.size());
            return false;
        }
        Holder<Enchantment> holder = enchantments.get(index);
        int y = LIST_TOP + (index - rowScroll) * ROW_H + 1;
        int onItem = stepperHit(onItemX(), y, mx, my);
        if (onItem != 0) {
            return adjust(state.onItem, holder, onItem, jump);
        }
        int book = stepperHit(bookX(), y, mx, my);
        if (book != 0) {
            return adjust(state.books, holder, book, jump);
        }
        AnvilCostCalculatorMod.debug("  -> row {} ({}) but not on a +/- box (on item x {}, book x {})",
                index, PlannerRules.id(holder), onItemX(), bookX());
        return false;
    }

    /** -1 on a stepper's minus box, +1 on its plus box, 0 elsewhere. */
    static int stepperHit(int x, int y, double mx, double my) {
        if (my < y || my >= y + 10) {
            return 0;
        }
        if (mx >= x && mx < x + STEP_BOX) {
            return -1;
        }
        int plusX = x + STEP_BOX + STEP_VALUE;
        return mx >= plusX && mx < plusX + STEP_BOX ? 1 : 0;
    }

    private boolean adjust(Map<String, Integer> levels, Holder<Enchantment> holder, int delta, boolean jump) {
        String id = PlannerRules.id(holder);
        int current = levels.getOrDefault(id, 0);
        if (delta > 0 && current == 0 && conflictFor(holder) != null) {
            AnvilCostCalculatorMod.debug("  -> {} locked: conflicts with {}", id,
                    PlannerRules.id(conflictFor(holder)));
            return true;
        }
        int max = holder.value().getMaxLevel();
        int next = jump ? (delta > 0 ? max : 0) : current + delta;
        PlannerState.setLevel(levels, id, Math.max(0, Math.min(max, next)));
        AnvilCostCalculatorMod.debug("  -> {} {} {} -> {} (on item {}, books {})",
                levels == state.books ? "book" : "on item", id, current, levels.getOrDefault(id, 0),
                state.onItem, state.books);
        dirty = true;
        return true;
    }

    /** The first chosen enchantment (on the item or as a book) this one cannot coexist with. */
    private Holder<Enchantment> conflictFor(Holder<Enchantment> holder) {
        String id = PlannerRules.id(holder);
        for (Holder<Enchantment> other : enchantments) {
            String otherId = PlannerRules.id(other);
            if (otherId.equals(id)) {
                continue;
            }
            boolean chosen = state.onItem.containsKey(otherId) || state.books.containsKey(otherId);
            if (chosen && !Enchantment.areCompatible(holder, other)) {
                return other;
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) {
            return true;
        }
        int delta = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
        if (picking) {
            pickScroll = Math.max(0, Math.min(maxPickScroll(), pickScroll + delta));
        } else if (x < width / 2.0) {
            rowScroll = Math.max(0, Math.min(maxRowScroll(), rowScroll + delta));
        } else {
            stepScroll = Math.max(0, stepScroll + delta);
        }
        return true;
    }

    // ------------------------------------------------------------------ plan

    private Plan plan() {
        if (!dirty) {
            return plan;
        }
        boolean book = target.is(Items.ENCHANTED_BOOK);
        String label = book ? "Book" : target.getHoverName().getString();
        Piece item = new Piece(label, state.onItem, (1 << state.anvilUses) - 1, book);
        List<Piece> books = new ArrayList<>();
        for (Holder<Enchantment> holder : enchantments) {
            Integer level = state.books.get(PlannerRules.id(holder));
            if (level != null) {
                books.add(new Piece(Enchantment.getFullname(holder, level).getString(),
                        Map.of(PlannerRules.id(holder), level), 0, true));
            }
        }
        LocalPlayer player = minecraft.player;
        boolean survival = player == null || !player.hasInfiniteMaterials();
        long start = System.nanoTime();
        plan = AnvilMath.plan(item, books, rules, survival);
        dirty = false;
        AnvilCostCalculatorMod.debug("Plan for {} (+{} books, prior work {}, survival {}): {} steps, {} levels{}, {} ms",
                label, books.size(), item.repairCost(), survival, plan.steps().size(), plan.totalLevels(),
                plan.tooExpensive() ? ", TOO EXPENSIVE" : "",
                String.format(Locale.ROOT, "%.1f", (System.nanoTime() - start) / 1e6));
        for (Step step : plan.steps()) {
            AnvilCostCalculatorMod.debug("  {}. {} + {} = {} lv", step.index(), step.left(), step.right(),
                    step.levels());
        }
        return plan;
    }

    private static boolean readFlag(java.util.function.BooleanSupplier reader) {
        try {
            return reader.getAsBoolean();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
