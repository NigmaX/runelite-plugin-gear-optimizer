# Gear Optimizer

A RuneLite plugin that finds the best gear **you own** for any monster, and shows what to bring.

Pick a monster and Gear Optimizer goes through your bank, inventory and worn equipment to build the
highest-DPS setup for melee, ranged and magic. It handles slayer bonuses, required protection and
the items a task needs, then lays the setup out the way you'd see it in game: equipment, a full
inventory, the spell to autocast, and what it costs per hour.

Everything runs locally. Monster and item stats ship with the plugin, so it never queries the wiki.

## Features

- **Uses what you own.** Your bank, inventory, worn gear, rune pouch, levels and prayer unlocks sync
  automatically while you play. Each account keeps its own saved data, and you can look at your
  alts' gear from the window.
- **All three styles.** Melee stances, ammo that actually fits the weapon (arrow and bolt tiers,
  javelins, blowpipe darts, atlatl darts), autocast spells across the standard, Ancient and Arceuus
  spellbooks, powered staves, and two-handed weapons versus main hand plus shield.
- **Bonuses and weaknesses.** Slayer helm and black mask (on task, imbued for ranged and magic),
  salve amulets, void, obsidian, crystal armour, inquisitor's, Virtus with Ancients, Twisted bow
  scaling, dragonbane, demonbane, golembane (granite hammer on gargoyles), vampyrebane, keris,
  leaf-bladed weapons, wilderness weapons, enchanted bolts, tomes and elemental weaknesses.
- **Never unsafe.** Earmuffs, nose peg, facemask, mirror shield, spiny helmet, insulated boots,
  boots of stone, witchwood icon and dragonfire protection always keep their slot, even when a
  stronger item would fit there. Required slots show a padlock.
- **Knows what to bring.** Rock hammer (not needed with a granite hammer or the gargoyle unlock),
  bag of salt, ice cooler, fungicide, antifire, antipoison, runes, prayer potions, food and
  potions, laid out as a 28-slot inventory.
- **Try anything.** Click any slot to see every option for it, owned or not, sorted by DPS with GE
  prices. Click one to try it, and the numbers update straight away.
- **Max DPS view.** Shows the best gear in the game within your levels and what it would cost to
  buy. Minigame-only items are left out.
- **Bank tools.** Outline the setup in your bank, or open a bank tab holding only the gear and
  supplies you need.
- **Dwarf multicannon.** Optional: adds the cannon's damage, cannonballs and parts to the setup.

## How to use

1. Turn on the plugin, then click the **Gear Optimizer** button in RuneLite's sidebar to open the
   window.
2. Open your bank once, so the plugin knows what you own. The top right shows when the bank was
   last synced.
3. Choose a monster, in any of these ways:
   - type its name in the search box;
   - right-click it in game and pick **Gear setup**;
   - attack it and press **Use target**;
   - turn on **Auto-select target** in the plugin settings to follow whatever you attack.
4. Read the recommendation on the **Melee**, **Ranged** and **Magic** tabs. The best style is
   highlighted in orange.

### The style tabs

Each style tab has four parts.

**The header** shows DPS, max hit, accuracy, kill time, attack speed, your defence against the
monster's attacks, and supplies cost per hour. Hover the DPS for the full calculation.

Under the numbers are the settings that change them. They apply to every tab:

| Setting | What it does |
|---|---|
| Prayer | Your best unlocked offensive prayer for that style (Piety, Rigour, Augury), or none. |
| Slayer task | **Auto** reads your current task from the Slayer plugin; **On** and **Off** override it. |
| Wilderness | Turns on the bonuses of Craw's bow, Viggora's chainmace, Thammaron's sceptre and their upgrades. |
| Cannon | Adds a dwarf multicannon firing alongside you. |
| Targets hit | How many monsters are stacked together, for burst/barrage, chinchompas and the cannon. **Auto** assumes 4 only on tasks that are known to stack (dust devils, nechryael, abyssal demons, smoke devils, maniacal monkeys). |
| Priority | Slide from pure DPS towards defence. |

**Equipment** is laid out like the in-game equipment screen.
- An orange dot marks a slot where an item you don't own would add at least 1% DPS.
- A padlock marks protection the monster requires.
- A blue dot marks a slot you changed.
- Right-click an item to try another one, exclude it, or always use it.

Below the grid you'll see the spell to autocast, its spellbook and the runes per cast, or the darts
loaded in a blowpipe (click to change them).

**Bring with you** is the planned inventory. Choose potions here:
- **Off** means no potion boost.
- **Bank** uses the best combat potion you own.
- **Best** uses the strongest potion, even if you don't own it.

**Budget supplies** swaps in cheaper ammo and runes that keep at least 90% of the DPS. Hover the rune
pouch to see what to fill it with.

**The right panel** has three views:
- **Slot options:** every item for the selected slot, with filters (All, Owned, Better only),
  sortable columns and pages. Click a row to try it. **Search all items** finds anything in the game.
  When you've changed something, a banner shows the DPS difference, and **Back to recommended** undoes it.
- **Supplies & budget:** the ammo, darts or spells you could use, with DPS and cost per hour.
- **Special attack:** the best special attack weapon to switch to.

### Other tabs

- **Compare** puts the three styles side by side: DPS, kill time, cost and what differs.
- **Data & exclusions** shows where the data comes from, lets you refresh or reset it, and lists
  the items you excluded or forced.

### Max DPS

Press **Max DPS** on the Equipment card to see the best setup in the game within your levels, with
a shopping list and GE prices for the pieces you don't own. It follows the same settings as the
normal view, including the slayer task.

### Bank tools

- **Highlight in bank** outlines the selected setup and its supplies while the bank is open.
- **Show in bank** under the inventory opens a bank tab called "gear optimizer". It holds only the
  gear, laid out like the equipment screen, and the supplies, laid out like the inventory.
- The bank's title bar gets a **Gear Optimizer** button next to "?" that does the same for the style
  tab you're viewing.

These use RuneLite's Bank Tags plugin, which must be turned on.

### Cannon and stacked monsters

- **Cannon accuracy.** The cannon uses your own accuracy: ranged with a ranged weapon, otherwise
  melee with your attack style. A powered staff uses the better of the two.
- **Damage and fire rate.** Max hit is 30, or 35 with granite cannonballs. It shoots each monster
  around it about every 4.8 seconds, up to 8 monsters.
- **Placement.** The plugin can't tell whether your spot allows a cannon. Many places don't, for
  example the Catacombs of Kourend. It also can't tell whether your spot is multi-combat. Turn the
  cannon on, and set **Targets hit**, to match where you fight.
- **Big monsters.** A 3x3 area attack fits fewer of them: at most 4 of size 2, and only one of
  size 3 or more.

## Settings

In RuneLite's plugin settings:

| Setting | Default | |
|---|---|---|
| Highlight in bank | On | Outline the setup and supplies in the bank. |
| Highlight colour | Green | Colour of the outline. |
| Auto-select target | Off | Switch to the monster you attack. |
| Right-click 'Gear setup' | On | Add the menu option to monsters. |
| Show upgrade suggestions | On | Find items you don't own that would raise your DPS. |
| Allow data refresh | Off | Enables **Refresh data**, which downloads a newer dataset from GitHub. |

## Privacy

The plugin makes no network requests unless you turn on **Allow data refresh** and press
**Refresh data**. That downloads two files from GitHub (the OSRS Wiki DPS calculator dataset) and
saves them in `.runelite/gear-optimizer`. Your account data is stored in your RuneLite profile.

## Limitations

- DPS follows the formulas of the OSRS Wiki DPS calculator. Numbers are expectations, not
  guarantees, and they don't model boss mechanics.
- Whether a spot is multi-combat or allows a cannon depends on where you fight, so those settings
  are yours to set.
- Gear stored outside the bank, inventory and worn slots (POH costume room, looting bag) isn't
  seen until it's back in one of those.

## Development

Requires a JDK (11 or newer). The plugin targets Java 11.

```bash
./gradlew run
```

This starts a development RuneLite client with the plugin loaded. To log in, follow
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

```bash
./gradlew test
```

Runs the DPS and optimizer tests.

To update the bundled data, run:

```bash
scripts/update_data.py
```

It downloads the OSRS Wiki DPS calculator dataset and writes trimmed copies to
`src/main/resources/com/gearoptimizer/data/`.

## Credits

Monster and equipment data: OSRS Wiki contributors, [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/),
via [weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc). DPS formulas follow that
calculator.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
