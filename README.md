# Anvil Cost Calculator

[![License](https://img.shields.io/github/license/MyCelium-GG/MyCel-Lib)](LICENSE)

Know the XP before you pay. Click the small **XP** button on the anvil to
open a planner: pick any enchantable item, say what it already has and which
books you want to add, and get the cheapest order to apply them - each step's
level cost and the total. Client side, no keybinds.

Built on [Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`), the shared
core for the MyCelium ecosystem: configuration, platform abstraction and
diagnostics are reused.

| | |
|---|---|
| Mod id | `anvilcostcalculator` |
| Minecraft | **26.3**, **26.2**, **26.1** (26.1-26.1.2) · Java **25** |
| Loaders | Fabric, Forge, NeoForge |

## Behavior

- An **XP** button sits in the anvil's top-right corner. It opens the
  planner without closing the anvil; "Back to anvil" (or Esc) returns to it
  with your items still in the slots.
- **Pick the item**: a searchable grid of every item at least one
  enchantment supports - every tool, weapon and armor piece, plus flint and
  steel, shears, fishing rods, elytra, shields, brushes, maces, carrot on a
  stick, and anything modded. Items that only take curses (pumpkins,
  mob heads, compasses) are left out. Choose
  **Enchanted Book** to combine books only.
- **Set it up**: every enchantment that item accepts gets a row with two
  level selectors - **On item** (what it already has) and **Book** (the book
  you want to add) - plus the item's prior **anvil uses**. Shift-click jumps
  to max or zero. Enchantments that conflict with your choices (Sharpness vs
  Smite, Mending vs Infinity, Protection types...) are locked.
- If the anvil's left slot holds an item, the planner starts from it: same
  item, its enchantments and its anvil uses.
- **Read the plan**: the right side shows the cheapest order, live. Example
  - a golden sword with Sharpness IV and Looting I, adding Sharpness V and
  Knockback II:

  ```
  1. Golden Sword + Sharpness V     5 lv
  2. #1 + Knockback II              3 lv
  Total                             8 lv
  Result: Knockback II, Looting I, Sharpness V
  ```

  Each step is "left slot + right slot"; `#n` means "the result of step n".
  Bigger plans merge books into each other first when that is cheaper, and
  avoid any 40+ level step ("Too Expensive!") whenever an order exists.
- Costs use vanilla's exact anvil formula: prior work penalty of both
  inputs, each enchantment's anvil cost (halved for books) times its
  resulting level, +1 per conflicting enchantment, and the doubled penalty
  after every use. Matching levels merge up (`Sharpness IV + IV = V`).
- Up to 8 books are searched exhaustively (every possible merge tree);
  more use a balanced pairing plan. Shows your level and the total in XP
  points too. Your last setup is remembered for the session.
- Tooltips on anvil-used items show `Anvil uses: 2 · Prior work +3 lv`,
  in red once the item can no longer be worked in survival.

## Configuration (`config/anvilcostcalculator.json`)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch (also toggleable live via `Mycel.setEnabled`). |
| `showButton` | `true` | Show the planner button on the anvil. |
| `showTooltip` | `true` | Prior work line on item tooltips. |
| `showXpPoints` | `true` | Also show the plan's total in XP points. |

Reload live with `/mycel reload anvilcostcalculator`. A config screen is
available from the mod list on Forge and NeoForge.

## Building

JDK 25, then:

```sh
./gradlew build            # all loaders + tests
./gradlew :common:test     # unit tests only
```

Mycel itself comes from [JitPack](https://jitpack.io/#MyCelium-GG/MyCel-Lib)
as one universal jar for all loaders
(`com.github.MyCelium-GG:MyCel-Lib:1.4.0`). No local setup needed.
