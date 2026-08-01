# Clipboard Anywhere

[简体中文](README_zh_CN.md)

Clipboard Anywhere is a Minecraft Forge 1.7.10 mod that adds a movable overlay for BiblioCraft clipboards. It lets you view pages, cycle task states, and manage multiple bound clipboards without returning to the physical clipboard.

## Features

- Access BiblioCraft clipboards through a movable in-game overlay.
- Bind and manage multiple clipboards for each player.
- View pages, change pages, and cycle task states from the overlay.
- Use clipboards from the player inventory or from loaded placed locations, including across dimensions.
- Keep the last synchronized page visible while a clipboard is temporarily unavailable.
- Move, resize, customize, or collapse the overlay.
- Use the overlay from the HUD and supported GUI screens.
- Configure optional keyboard shortcuts, including modifier combinations provided by ModernKeyBinding.
- English and Simplified Chinese localization.

## Requirements

- Minecraft 1.7.10
- Minecraft Forge 10.13.4.1614
- BiblioCraft 1.11.7
- A compatible Mixin bootstrap for Minecraft 1.7.10, such as UniMixins

Clipboard Anywhere must be installed on both the client and server.

## Usage

### Bind a clipboard

Hold a BiblioCraft clipboard, sneak, and right-click the air. The clipboard is assigned a persistent identity and becomes the active binding. Repeating the action with an already bound clipboard selects it instead of creating a duplicate binding.

The overlay can resolve a clipboard from:

- any slot in the owning player's main inventory; or
- its last known placed position, provided that the target dimension and chunk are already loaded on the server.

Clipboard Anywhere deliberately does **not** force-load dimensions or chunks. Clipboards in containers, other players' inventories, or dropped-item entities are treated as disconnected.

### Use the overlay

- Hover the expanded overlay to replace the title row with its controls. Moving the pointer away restores the title without changing the overlay's height or position.
- Click the binding name to open the clipboard list.
- Use the pencil icon to rename the active binding.
- Use the × icon to unbind it. Unbinding does not delete the physical clipboard or its tasks.
- Drag the empty area in the control row to move the overlay.
- Use the gear icon to enter layout mode. Layout mode supports dragging, proportional scaling, separate background/text opacity sliders, confirm, and cancel.
- Use the collapse icon to reduce the overlay to a fixed 8 × 8 icon. Click the icon to expand it or drag it to reposition it.
- Click a task checkbox to cycle its BiblioCraft task state.
- Click task text to edit it in place. Press Enter to save, Escape to cancel, or Tab/Shift+Tab to save and move between adjacent tasks. Task text follows BiblioCraft's 23-character limit.
- Use the footer arrows to change pages.

When no other GUI is open, assign and use **Toggle clipboard interaction** to open a non-pausing interaction screen. When a supported GUI is already open, the overlay accepts mouse input directly.

### Disconnected clipboards

A disconnected binding displays its last synchronized page as a read-only cache. If another readable binding exists, the overlay normally switches to it automatically. A disconnected binding can still be selected for inspection or unbound, and it reconnects automatically when its physical clipboard becomes readable again.

## Shortcuts

All shortcuts are **unbound by default** to avoid conflicts in large modpacks.

| Action | Default |
| --- | --- |
| Previous clipboard page | `NONE` |
| Next clipboard page | `NONE` |
| Collapse/expand clipboard overlay | `NONE` |
| Toggle clipboard interaction | `NONE` |

Configure them under **Options → Controls → Clipboard Anywhere**. Tooltips display `NONE` for unbound actions. If ModernKeyBinding provides a modifier-based binding, the tooltip displays the full combination instead of only the base key.

Overlay shortcuts are ignored while a supported text field has focus, so normal typing and combinations such as copy, paste, cut, and select-all continue to work.

## Configuration and saved data

Client layout settings are stored in `config/clipboardanywhere.cfg`. The in-game layout editor is the recommended way to change them.

| Setting | Range / behavior |
| --- | --- |
| Scale | `0.5` to `2.0` |
| Background opacity | `0.10` to `1.0` |
| Text opacity | `0.10` to `1.0` |
| Position | Keeps a fixed margin when near an edge; otherwise preserves its relative position across window sizes |
| Collapsed state | Persisted between sessions |

Clipboard identities, per-player bindings, names, active selections, and cached pages are stored in server-side world data.

## Compatibility notes

- The overlay uses low-priority, optional GUI input hooks so custom GUI libraries can keep their own input handling.
- When ModularUI2 is installed, Clipboard Anywhere uses its cancellable pre-input events instead of competing with its GUI redirects.
- Minecraft options screens and known Angelica, Sodium/Reese, NotFine, and Iris configuration screens suppress the overlay to avoid obstructing their controls.
- The physical BiblioCraft clipboard remains authoritative. The overlay can edit individual task text on the current page, but it does not replace BiblioCraft's full-page editor.

## Building

From the repository root on Windows:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat check
.\gradlew.bat jar
```

On Linux or macOS, use `./gradlew` instead of `.\gradlew.bat`.

The reobfuscated mod jar is written to `build/libs`.

Useful development tasks:

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
```

Running the dedicated server requires accepting Mojang's EULA in the generated server run directory. The repository contains no automated test source set; `check` currently validates formatting, compilation, and configured static checks.

## License

Clipboard Anywhere is released under the [MIT License](LICENSE).

Copyright (c) 2026 liansishen.
