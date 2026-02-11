# Sample Mod

This sample shows the minimal `mod.json` + `ModInitializer` flow for the new `/mods` loader.

## What it registers

- Item key: `sample:demo_item` (aliases vanilla stick)
- Block key: `sample:demo_block` (aliases vanilla dirt)
- Entity type key: `sample:demo_entity` (aliases vanilla pig)
- Recipe key: `sample:demo_recipe` (shapeless, outputs apple from stick)

## Event listeners

- `BlockBreakEvent`
- `UseItemEvent`

Both listeners log to server stdout so you can verify event routing quickly.
