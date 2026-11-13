package my.celium.org.anvilcostcalculator.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import my.celium.org.anvilcostcalculator.AnvilCostCalculatorMod;
import my.celium.org.anvilcostcalculator.AnvilMath;
import my.celium.org.anvilcostcalculator.AnvilMath.Piece;
import my.celium.org.anvilcostcalculator.AnvilMath.Plan;
import my.celium.org.anvilcostcalculator.AnvilMath.Step;
import my.celium.org.anvilcostcalculator.AnvilStats;
import my.celium.org.client.MycelScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The planner opened from the anvil button, laid out like a normal Minecraft menu screen: a
 * centred title, dark list areas, vanilla buttons, white and grey text, and the anvil's own green
 * and red for level costs.
 *
 * <p>Page one picks the item from every item an enchantment can go on (plus the Enchanted Book
 * for combining books only). Page two lists the item's enchantments, each with an "On item" and
 * a "Book" button that cycle through the levels, and shows the cheapest order as anvil steps.
 *
 * <p>Opening it over the anvil keeps the anvil open (replacing a container screen sends nothing
 * to the server), so closing it returns to the anvil with its items in place. Client only.
 */
public final class PlannerScreen extends Screen {
    // Vanilla menu colours.
    static final int WHITE = 0xFFFFFFFF;
    static final int GREY = 0xFFA0A0A0;
    static final int DARK = 0xFF707070;
    static final int CURSE = 0xFFFF5555;
    /** The anvil's "Enchantment Cost" green and its "Too Expensive!" red. */
    static final int COST = 0xFF80FF20;
    static final int TOO_EXPENSIVE = 0xFFFF6060;
    static final int LIST_BG = 0x98000000;
    static final int ROW_HOVER = 0x20FFFFFF;
    static final int SLOT_HOVER = 0x80FFFFFF;

    static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
    static final Identifier BUTTON_OFF = Identifier.withDefaultNamespace("widget/button_disabled");

    static final int HEADER = 33;
    static final int FOOTER = 33;
    static final int CELL = 18;
    static final int ROW_H = 24;
    static final int BUTTON_H = 20;
    static final int MAX_USES = 6;
    static final int MAX_W = 520;

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
        AnvilStats.count("planner_open");
        if (state.item != null) {
            AnvilStats.count("from_anvil_item");
        }
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

    // ---------------------------------------------------------------- setup

    @Override
    protected void init() {
        int by = height - FOOTER + 7;
        if (picking) {
            search = new EditBox(font, width / 2 - 100, 24, 200, 16, Component.literal("Search"));
            search.setHint(Component.literal("Search items"));
            search.setValue(state.search);
            search.setResponder(text -> {
                state.search = text;
                filter();
            });
            addRenderableWidget(search);
            if (state.item != null) {
                button(width / 2 - 154, by, 150, "Back to planner", () -> showPage(false));
                button(width / 2 + 4, by, 150, "Back to anvil", this::onClose);
            } else {
                button(width / 2 - 75, by, 150, "Back to anvil", this::onClose);
            }
        } else {
            search = null;
            button(width / 2 - 155, by, 100, "Change item", () -> showPage(true));
            button(width / 2 - 50, by, 100, "Clear books", () -> {
                state.books.clear();
                dirty = true;
            });
            button(width / 2 + 55, by, 100, "Back to anvil", this::onClose);
        }
    }

    private void button(int x, int y, int w, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, 20).build());
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
        reportSession();
        LocalPlayer player = minecraft.player;
        boolean anvilOpen = player != null && player.containerMenu == parent.getMenu();
        AnvilCostCalculatorMod.debug("Planner closed, returning to {}", anvilOpen ? "anvil" : "game");
        if (anvilOpen) {
            MycelScreens.open(parent);
        } else {
            MycelScreens.open(null);
        }
    }

    // ---------------------------------------------------------------- layout

    private int contentX0() {
        return Math.max(8, (width - MAX_W) / 2);
    }

    private int contentX1() {
        return width - contentX0();
    }

    private int splitX() {
        return contentX0() + (contentX1() - contentX0()) * 56 / 100;
    }

    private int top() {
        return picking ? HEADER + 12 : HEADER;
    }

    private int bottom() {
        return height - FOOTER;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float a) {
        super.extractRenderState(gfx, mouseX, mouseY, a);
        gfx.centeredText(font, title, width / 2, picking ? 10 : 12, WHITE);
        separator(gfx, top() - 2);
        separator(gfx, bottom());
        if (picking) {
            renderPicker(gfx, mouseX, mouseY);
        } else {
            renderEditor(gfx, mouseX, mouseY);
        }
    }

    /** The two-pixel line vanilla draws under the header and above the footer. */
    private void separator(GuiGraphicsExtractor gfx, int y) {
        gfx.fill(0, y, width, y + 1, 0x60000000);
        gfx.fill(0, y + 1, width, y + 2, 0x30FFFFFF);
    }

    /** A vanilla button drawn in place (used inside the scrolling lists). */
    private void fakeButton(GuiGraphicsExtractor gfx, int x, int y, int w, Component label, boolean active,
            boolean hover) {
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, !active ? BUTTON_OFF : hover ? BUTTON_HOVER : BUTTON, x, y, w,
                BUTTON_H);
        int color = active ? WHITE : GREY;
        Component text = font.width(label) > w - 6 ? Component.literal(fit(label.getString(), w - 6)) : label;
        gfx.centeredText(font, text, x + w / 2, y + 6, color);
    }

    // ---------------------------------------------------------------- picker

    private int columns() {
        return Math.max(1, Math.min(22, (contentX1() - contentX0() - 8) / CELL));
    }

    private int gridX0() {
        return (width - columns() * CELL) / 2;
    }

    private int gridY0() {
        return top() + 6;
    }

    private int gridRows() {
        return Math.max(1, (bottom() - 4 - gridY0()) / CELL);
    }

    private int maxPickScroll() {
        int totalRows = (filtered.size() + columns() - 1) / columns();
        return Math.max(0, totalRows - gridRows());
    }

    private void renderPicker(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        gfx.fill(0, top(), width, bottom(), LIST_BG);
        if (filtered.isEmpty()) {
            gfx.centeredText(font, "Nothing enchantable matches", width / 2, gridY0() + 20, GREY);
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
                int x = gridX0() + col * CELL;
                int y = gridY0() + row * CELL;
                if (stack.getItem() == state.item) {
                    gfx.fill(x, y, x + CELL, y + CELL, 0x40FFFFFF);
                }
                gfx.item(stack, x + 1, y + 1);
                if (index == hovered) {
                    gfx.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, SLOT_HOVER);
                }
            }
        }
        if (hovered >= 0) {
            ItemStack stack = filtered.get(hovered);
            Component name = stack.is(Items.ENCHANTED_BOOK)
                    ? colored("Enchanted Book", WHITE).append(colored("  (combine books only)", GREY))
                    : colored(stack.getHoverName().getString(), WHITE);
            gfx.setTooltipForNextFrame(font, name, mouseX, mouseY);
        }
        scrollbar(gfx, gridX0() + columns() * CELL + 6, gridY0(), gridRows() * CELL, pickScroll, maxPickScroll());
    }

    private int itemAt(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - gridX0()) / CELL);
        int row = (int) Math.floor((mouseY - gridY0()) / CELL);
        if (mouseX < gridX0() || mouseY < gridY0() || col >= columns() || row >= gridRows()) {
            return -1;
        }
        int index = (row + pickScroll) * columns() + col;
        return index < filtered.size() ? index : -1;
    }

    // ---------------------------------------------------------------- editor: enchantments

    private int leftX0() {
        return contentX0();
    }

    private int leftX1() {
        return splitX() - 4;
    }

    private int bookButtonW() {
        return 60;
    }

    private int itemButtonW() {
        return 76;
    }

    private int bookButtonX() {
        return leftX1() - 6 - bookButtonW();
    }

    private int itemButtonX() {
        return bookButtonX() - 4 - itemButtonW();
    }

    /** The first row shows the item and its anvil uses; enchantments follow. */
    private int listTop() {
        return top() + 4 + ROW_H + 4;
    }

    private int visibleRows() {
        return Math.max(1, (bottom() - 2 - listTop()) / ROW_H);
    }

    private int maxRowScroll() {
        return Math.max(0, enchantments.size() - visibleRows());
    }

    private int usesButtonX() {
        return itemButtonX();
    }

    private int usesButtonW() {
        return itemButtonW() + 4 + bookButtonW();
    }

    private void renderEditor(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        gfx.fill(leftX0(), top(), leftX1(), bottom(), LIST_BG);
        gfx.fill(splitX() + 4, top(), contentX1(), bottom(), LIST_BG);

        // Item row: icon, name, anvil uses.
        int iy = top() + 4;
        gfx.item(target, leftX0() + 6, iy + 2);
        String name = target.is(Items.ENCHANTED_BOOK) ? "Enchanted Book" : target.getHoverName().getString();
        gfx.text(font, fit(name, usesButtonX() - leftX0() - 32), leftX0() + 26, iy + 6, WHITE);
        boolean usesHover = inside(mouseX, mouseY, usesButtonX(), iy, usesButtonW(), BUTTON_H);
        fakeButton(gfx, usesButtonX(), iy, usesButtonW(),
                Component.literal("Anvil uses: " + state.anvilUses), true, usesHover);
        if (usesHover) {
            int penalty = (1 << state.anvilUses) - 1;
            gfx.setTooltipForNextFrame(font, List.of(
                    colored("How often this item went through an anvil before", WHITE),
                    colored("Adds " + penalty + " levels to its next step", GREY),
                    colored("Click: more, right-click: less", DARK)), Optional.empty(), mouseX, mouseY);
        }
        gfx.fill(leftX0() + 4, listTop() - 3, leftX1() - 4, listTop() - 2, 0x30FFFFFF);

        if (enchantments.isEmpty()) {
            gfx.text(font, "Nothing can enchant this", leftX0() + 6, listTop() + 8, GREY);
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
            boolean locked = conflict != null && have == 0 && add == 0;
            int y = listTop() + i * ROW_H;
            boolean rowHover = inside(mouseX, mouseY, leftX0(), y, leftX1() - leftX0(), ROW_H);
            if (rowHover) {
                gfx.fill(leftX0(), y, leftX1(), y + ROW_H, ROW_HOVER);
            }
            String label = holder.value().description().getString();
            int color = locked ? DARK : holder.is(EnchantmentTags.CURSE) ? CURSE : WHITE;
            gfx.text(font, fit(label, itemButtonX() - leftX0() - 10), leftX0() + 6, y + 8, color);
            int by = y + 2;
            boolean itemHover = inside(mouseX, mouseY, itemButtonX(), by, itemButtonW(), BUTTON_H);
            boolean bookHover = inside(mouseX, mouseY, bookButtonX(), by, bookButtonW(), BUTTON_H);
            fakeButton(gfx, itemButtonX(), by, itemButtonW(), levelLabel("On item", have, max), !locked, itemHover);
            fakeButton(gfx, bookButtonX(), by, bookButtonW(), levelLabel("Book", add, max), !locked, bookHover);
            if (rowHover && mouseX < itemButtonX()) {
                List<Component> tip = new ArrayList<>();
                tip.add(colored(label, color == DARK ? WHITE : color));
                tip.add(colored("Max level " + AnvilMath.roman(max), GREY));
                if (conflict != null) {
                    tip.add(colored("Can't go with " + conflict.value().description().getString(), TOO_EXPENSIVE));
                }
                gfx.setTooltipForNextFrame(font, tip, Optional.empty(), mouseX, mouseY);
            } else if ((itemHover || bookHover) && !locked) {
                gfx.setTooltipForNextFrame(font, List.of(
                        colored(itemHover ? "Level the item already has" : "Book to add", WHITE),
                        colored("Click: higher, right-click: lower", GREY),
                        colored("Shift-click: max or off", GREY)), Optional.empty(), mouseX, mouseY);
            }
        }
        scrollbar(gfx, leftX1() - 3, listTop(), visibleRows() * ROW_H, rowScroll, maxRowScroll());
        renderPlan(gfx, mouseX, mouseY);
    }

    private static Component levelLabel(String what, int level, int max) {
        String value = level <= 0 ? "-" : AnvilMath.roman(level);
        MutableComponent text = Component.literal(what + ": ");
        return text.append(Component.literal(value).withStyle(s -> s.withColor(TextColor.fromRgb(
                level <= 0 ? 0xA0A0A0 : level >= max ? 0xFFFF55 : 0xFFFFFF))));
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- editor: plan

    private int planX0() {
        return splitX() + 10;
    }

    private int planX1() {
        return contentX1() - 8;
    }

    private void renderPlan(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        int x0 = planX0();
        int x1 = planX1();
        Plan current = plan();
        int y = top() + 10;
        gfx.text(font, "Cheapest order", x0, y, WHITE);
        if (!current.steps().isEmpty()) {
            String count = current.steps().size() + (current.steps().size() == 1 ? " step" : " steps");
            gfx.text(font, count, x1 - font.width(count), y, GREY);
        }
        gfx.text(font, fit("Left slot + right slot, top to bottom", x1 - x0), x0, y + 12, GREY);

        int totalH = 46;
        int stepsTop = top() + 36;
        int stepsBottom = bottom() - totalH;
        if (current.steps().isEmpty()) {
            int mid = (x0 + x1) / 2;
            int cy = (stepsTop + stepsBottom) / 2 - 10;
            gfx.centeredText(font, "Nothing to combine yet", mid, cy, WHITE);
            gfx.centeredText(font, fit("Set a Book level on the left", x1 - x0), mid, cy + 12, GREY);
        } else {
            int rows = Math.max(1, (stepsBottom - stepsTop) / ROW_H);
            int maxScroll = Math.max(0, current.steps().size() - rows);
            stepScroll = Math.min(stepScroll, maxScroll);
            for (int i = 0; i < rows && i + stepScroll < current.steps().size(); i++) {
                step(gfx, current.steps().get(i + stepScroll), x0, stepsTop + i * ROW_H, x1, mouseX, mouseY);
            }
            scrollbar(gfx, contentX1() - 3, stepsTop, rows * ROW_H, stepScroll, maxScroll);
        }
        renderTotal(gfx, current, x0, stepsBottom, x1);
    }

    /** One anvil operation: "1.", the two items as icons, what it adds, and the cost. */
    private void step(GuiGraphicsExtractor gfx, Step step, int x0, int y, int x1, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, splitX() + 4, y, contentX1() - splitX() - 4, ROW_H);
        if (hover) {
            gfx.fill(splitX() + 4, y, contentX1(), y + ROW_H, ROW_HOVER);
        }
        gfx.text(font, step.index() + ".", x0, y + 8, GREY);
        int ix = x0 + 14;
        icon(gfx, ix, y + 4, step.left());
        gfx.text(font, "+", ix + 19, y + 8, GREY);
        icon(gfx, ix + 27, y + 4, step.right());

        String cost = step.levels() + (step.levels() == 1 ? " level" : " levels");
        int costColor = costColor(step.levels(), step.tooExpensive());
        int costX = x1 - font.width(cost);
        gfx.text(font, cost, costX, y + 8, costColor);
        int tx = ix + 48;
        gfx.text(font, fit(describe(step), costX - tx - 6), tx, y + 8, WHITE);
        if (hover) {
            gfx.setTooltipForNextFrame(font, List.of(
                    colored("Step " + step.index(), WHITE),
                    colored("Left slot: " + slotName(step.left()), GREY),
                    colored("Right slot: " + slotName(step.right()), GREY),
                    colored("Costs " + step.levels() + " levels", costColor)), Optional.empty(), mouseX, mouseY);
        }
    }

    /** The item, a book, or the result of an earlier step (with its number). */
    private void icon(GuiGraphicsExtractor gfx, int x, int y, String label) {
        boolean earlier = label.startsWith("#");
        ItemStack stack;
        if (earlier) {
            stack = resultIsBook(label) ? new ItemStack(Items.ENCHANTED_BOOK) : target;
        } else if (isTargetLabel(label)) {
            stack = target.is(Items.ENCHANTED_BOOK) ? new ItemStack(Items.ENCHANTED_BOOK) : target;
        } else {
            stack = new ItemStack(Items.ENCHANTED_BOOK);
        }
        gfx.item(stack, x, y);
        if (earlier) {
            gfx.itemDecorations(font, stack, x, y, label);
        }
    }

    /** Whether "#n" is the result of a step that only merged books (so it is a book, not the item). */
    private boolean resultIsBook(String label) {
        try {
            int n = Integer.parseInt(label.substring(1));
            for (Step step : plan().steps()) {
                if (step.index() == n) {
                    String left = step.left();
                    if (left.startsWith("#")) {
                        return resultIsBook(left);
                    }
                    return !isTargetLabel(left) || target.is(Items.ENCHANTED_BOOK);
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }

    private boolean isTargetLabel(String label) {
        String name = target.is(Items.ENCHANTED_BOOK) ? "Book" : target.getHoverName().getString();
        return label.equals(name);
    }

    private String slotName(String label) {
        return label.startsWith("#") ? "the result of step " + label.substring(1) : label;
    }

    /** What a step does in words: the book it adds, the two books it merges, or an earlier result. */
    private String describe(Step step) {
        String left = step.left();
        String right = step.right();
        if (right.startsWith("#")) {
            return "the result of step " + right.substring(1);
        }
        if (!left.startsWith("#") && !isTargetLabel(left)) {
            return left + " + " + right;
        }
        return right;
    }

    private static int costColor(int levels, boolean tooExpensive) {
        boolean limited = !AnvilCostCalculatorMod.liftsLevelLimit();
        return tooExpensive || limited && levels >= AnvilMath.TOO_EXPENSIVE ? TOO_EXPENSIVE : COST;
    }

    /** Total levels, your levels, and what the item ends up with. */
    private void renderTotal(GuiGraphicsExtractor gfx, Plan current, int x0, int y, int x1) {
        LocalPlayer player = minecraft.player;
        boolean survival = player != null && !player.hasInfiniteMaterials();
        int total = current.totalLevels();
        gfx.fill(splitX() + 8, y, contentX1() - 4, y + 1, 0x30FFFFFF);
        int ty = y + 6;
        gfx.text(font, "Total", x0, ty, WHITE);
        String totalText = total + (total == 1 ? " level" : " levels");
        boolean affordable = !survival || total <= player.experienceLevel;
        int color = current.steps().isEmpty() ? GREY
                : current.tooExpensive() || !affordable ? TOO_EXPENSIVE : COST;
        gfx.text(font, totalText, x1 - font.width(totalText), ty, color);

        String note;
        if (current.tooExpensive()) {
            note = "Too Expensive! No order stays under 40";
        } else if (survival) {
            note = "You have " + player.experienceLevel + " levels";
            if (readFlag(() -> AnvilCostCalculatorMod.showXpPoints.get()) && total > 0) {
                note += String.format(Locale.ROOT, " (about %,d XP)",
                        AnvilMath.pointsToSpend(player.experienceLevel, total));
            }
        } else {
            note = "Creative mode: levels are free";
        }
        if (state.books.size() > AnvilMath.EXACT_SEARCH_LIMIT) {
            note += ", quick plan";
        }
        gfx.text(font, fit(note, x1 - x0), x0, ty + 12, current.tooExpensive() ? TOO_EXPENSIVE : GREY);
        if (!current.steps().isEmpty()) {
            gfx.text(font, fit("Result: " + resultText(current), x1 - x0), x0, ty + 24, GREY);
        }
    }

    private static MutableComponent colored(String text, int argb) {
        return Component.literal(text).withStyle(s -> s.withColor(TextColor.fromRgb(argb & 0xFFFFFF)));
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

    /** The thin vanilla list scrollbar. */
    private static void scrollbar(GuiGraphicsExtractor gfx, int x, int top, int height, int scroll, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        int thumb = Math.max(12, height / (maxScroll + 1));
        int y = top + (height - thumb) * scroll / maxScroll;
        gfx.fill(x, top, x + 3, top + height, 0x80000000);
        gfx.fill(x, y, x + 3, y + thumb, 0xFF808080);
        gfx.fill(x, y, x + 2, y + thumb - 1, 0xFFC0C0C0);
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        AnvilCostCalculatorMod.debug("Click button={} at ({}, {}) shift={} page={} screen={}x{}",
                event.button(), String.format(Locale.ROOT, "%.1f", event.x()),
                String.format(Locale.ROOT, "%.1f", event.y()), event.hasShiftDown(),
                picking ? "picker" : "editor", width, height);
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        // 26.x mouse input is SDL-based: left is 1 there, not GLFW's 0.
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (!left && !right) {
            return false;
        }
        double mx = event.x();
        double my = event.y();
        if (picking) {
            int index = left ? itemAt(mx, my) : -1;
            if (index < 0) {
                return false;
            }
            click();
            state.pick(filtered.get(index).getItem());
            loadItem();
            showPage(false);
            return true;
        }
        int step = left ? 1 : -1;
        boolean jump = event.hasShiftDown();
        if (inside(mx, my, usesButtonX(), top() + 4, usesButtonW(), BUTTON_H)) {
            state.anvilUses = jump ? (left ? MAX_USES : 0) : (state.anvilUses + step + MAX_USES + 1) % (MAX_USES + 1);
            AnvilCostCalculatorMod.debug("  -> anvil uses now {}", state.anvilUses);
            click();
            dirty = true;
            return true;
        }
        if (my < listTop() || mx < leftX0() || mx >= leftX1()) {
            return false;
        }
        int row = (int) ((my - listTop()) / ROW_H);
        int index = row + rowScroll;
        if (row >= visibleRows() || index >= enchantments.size()) {
            return false;
        }
        Holder<Enchantment> holder = enchantments.get(index);
        int by = listTop() + row * ROW_H + 2;
        if (inside(mx, my, itemButtonX(), by, itemButtonW(), BUTTON_H)) {
            return cycle(state.onItem, holder, step, jump);
        }
        if (inside(mx, my, bookButtonX(), by, bookButtonW(), BUTTON_H)) {
            return cycle(state.books, holder, step, jump);
        }
        return false;
    }

    /** Click: one level up (wrapping to off after max). Right-click: one down. Shift: max or off. */
    private boolean cycle(Map<String, Integer> levels, Holder<Enchantment> holder, int step, boolean jump) {
        String id = PlannerRules.id(holder);
        int current = levels.getOrDefault(id, 0);
        if (current == 0 && conflictFor(holder) != null) {
            AnvilCostCalculatorMod.debug("  -> {} locked: conflicts with {}", id, PlannerRules.id(conflictFor(holder)));
            return true;
        }
        int max = holder.value().getMaxLevel();
        int next = jump ? (current > 0 ? 0 : max) : (current + step + max + 1) % (max + 1);
        PlannerState.setLevel(levels, id, next);
        AnvilCostCalculatorMod.debug("  -> {} {} {} -> {}", levels == state.books ? "book" : "on item", id, current, next);
        click();
        dirty = true;
        return true;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
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
        } else if (x < splitX()) {
            rowScroll = Math.max(0, Math.min(maxRowScroll(), rowScroll + delta));
        } else {
            stepScroll = Math.max(0, stepScroll + delta);
        }
        return true;
    }

    // ------------------------------------------------------------------ stats

    /** One summary per planner session: the item, the books added, the cost range. */
    private void reportSession() {
        if (state.item == null || state.books.isEmpty()) {
            return;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(state.item);
        AnvilStats.count("planned_items", "minecraft".equals(key.getNamespace()) ? key.getPath() : key.toString());
        if (target.is(Items.ENCHANTED_BOOK)) {
            AnvilStats.count("book_mode");
        }
        for (String enchantment : state.books.keySet()) {
            AnvilStats.count("planned_enchantments",
                    enchantment.startsWith("minecraft:") ? enchantment.substring(10) : enchantment);
        }
        Plan current = plan();
        AnvilStats.count("plan_levels", AnvilStats.levelRange(current.totalLevels()));
        if (current.tooExpensive()) {
            AnvilStats.count("too_expensive");
        }
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
        plan = AnvilMath.plan(item, books, rules, survival && !AnvilCostCalculatorMod.liftsLevelLimit());
        dirty = false;
        AnvilCostCalculatorMod.debug("Plan for {} (+{} books, prior work {}, survival {}): {} steps, {} levels{}, {} ms",
                label, books.size(), item.repairCost(), survival, plan.steps().size(), plan.totalLevels(),
                plan.tooExpensive() ? ", TOO EXPENSIVE" : "",
                String.format(Locale.ROOT, "%.1f", (System.nanoTime() - start) / 1e6));
        for (Step s : plan.steps()) {
            AnvilCostCalculatorMod.debug("  {}. {} + {} = {} lv", s.index(), s.left(), s.right(), s.levels());
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
