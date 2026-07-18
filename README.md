# Clipboard Anywhere

Clipboard Anywhere is a Forge mod for Minecraft 1.7.10 that adds a movable overlay for BiblioCraft clipboards. Bound clipboards can be viewed and operated from the HUD or other GUI screens while their physical target remains readable.

## Features

- Bind multiple BiblioCraft clipboards per player by sneaking and right-clicking air.
- Read clipboard items from any player inventory slot.
- Read placed clipboards from any server-loaded dimension and chunk without force-loading either.
- Switch bindings, rename their local display names, and unbind with confirmation.
- Change pages and cycle task states from the overlay.
- Keep a cached current page while a clipboard is disconnected.
- Automatically reconnect clipboards that return to a readable inventory or loaded placed position.
- Collapse the overlay to the BiblioCraft clipboard icon.
- Move, proportionally resize, and adjust the opacity of the overlay in layout mode.
- Persist world binding data and client layout settings.
- Provide English and Simplified Chinese interface text.

## Requirements

- Minecraft 1.7.10
- Forge 10.13.4.1614
- BiblioCraft 1.11.7

BiblioCraft is a required runtime dependency and is not bundled in the produced mod jar.

## Controls

The previous-page, next-page, collapse/expand, and interaction-mode actions are available in Minecraft's Controls menu. Their default keys are currently unbound so they do not conflict with an existing modpack configuration.

To bind the clipboard currently in hand, sneak and right-click air. Open the interaction mode to use overlay controls when no other GUI is open. When another GUI is already open, the overlay accepts mouse input directly.

## Building

From the repository root:

```powershell
.\gradlew.bat build
```

The reobfuscated mod jar is written to `build/libs`.

Useful development commands:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat test
.\gradlew.bat runClient
.\gradlew.bat runServer
```

Running the dedicated server requires accepting Mojang's EULA in the generated server run directory.

## License

Clipboard Anywhere is released under the MIT License. See `LICENSE`.

Copyright (c) 2026 liansishen.
