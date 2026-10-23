# Changelog

All notable changes to Anvil Cost Calculator are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Anvil Cost Calculator is versioned semantically (`MAJOR.MINOR.PATCH`).
It runs on Minecraft 26.3 (Fabric + Forge + NeoForge, Java 25) and requires
[Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`).

## [1.0.2] - Unreleased

### Changed

- Requires Mycel 1.3.1. The planner and the tooltip line use Mycel's shared
  wiki theme instead of private copies of the same colors.

## [1.0.1] - 2026-10-19

### Changed

- Updated Mycel lib to 1.3.0: Mycel's chat messages now use the MyCelium wiki's
  red style.
- Checks for new versions through the MyCelium wiki (one log line when an
  update is out; turn off with `enableUpdateChecker` in `config/mycel.json`).

## [1.0.0] - 2026-10-18

### Added

- Initial release.
- **XP** button on the anvil opens a planner (the anvil stays open):
  pick any enchantable item - every tool, weapon, armor piece and oddity
  like flint and steel or elytra - or an Enchanted Book to combine books
  only; set the enchantments it already has, the books to add and its prior
  anvil uses; get the cheapest combine order with per-step and total level
  costs, your level and the total in XP points.
- Pre-filled from the anvil's left slot; conflicting enchantments locked;
  last setup remembered for the session.
- Vanilla-exact cost formula (prior work, halved book fees, level merging,
  conflict penalty). Exhaustive search for up to 8 books, balanced pairing
  beyond; survival plans avoid 40+ level steps when possible.
- Tooltip line on anvil-used items: anvil uses and prior work penalty.
- Config (`config/anvilcostcalculator.json`): `enabled`, `showButton`,
  `showTooltip`, `showXpPoints`. Reload live with
  `/mycel reload anvilcostcalculator`; config screen available from the mod
  list on Forge and NeoForge.
- Client-side only, no keybinds. Built on Mycel 1.2.1
  (`com.github.MyCelium-GG:MyCel-Lib:1.2.1` via JitPack).
