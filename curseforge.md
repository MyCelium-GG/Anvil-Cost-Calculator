# Anvil Cost Calculator

Know the XP before you pay. Click the **XP** button on the anvil, pick any
enchantable item (or just books), set what it already has and which books
to add, and get the cheapest order to apply them with every step's level
cost. Client side, no keybinds.

- Every enchantable item: tools, weapons, armor, flint and steel, elytra, ...
- Or combine enchanted books only
- Cheapest combine order with per-step and total level costs
- Vanilla-exact math: prior work, book discount, level merging, conflicts
- Avoids "Too Expensive!" steps whenever an order exists
- Pre-filled from the anvil's left slot; conflicts locked
- Tooltip shows anvil uses and prior work penalty
- Config: `enabled`, `showButton`, `showTooltip`, `showXpPoints`
- Requires **Mycel**: install it alongside this mod

Reload live with `/mycel reload anvilcostcalculator`.
