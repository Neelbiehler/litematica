[![](https://jitpack.io/v/sakura-ryoko/litematica.svg)](https://jitpack.io/#sakura-ryoko/litematica)

Litematica
==============
Litematica is a client-side Minecraft mod using LiteLoader.
It is more or less a re-creation of or a substitute for [Schematica](https://www.curseforge.com/minecraft/mc-mods/schematica),
for players who don't want to have Forge installed.
For compiled builds (= downloads), see https://www.curseforge.com/minecraft/mc-mods/litematica/files/all

3D workspace (this fork)
========================
Open **M → Build → 3D workspace**, or use the 3D tab after placing a file.

* Left-click a block to select its placement; drag to move it on the X/Z block grid.
* Right-drag to orbit, middle-drag to pan, and scroll to zoom around the pointer.
* Use **Height − / +**, **Rotate 90°**, **Duplicate**, and **Undo move** for precise adjustments. Shift changes height by ten blocks. Placement and coordinate locks are respected.
* **Swap blocks** preselects the block you clicked. Choose **This block** or **All matching** in the editor. The same editor is available from **Farm list → select a material → Replace blocks**.
* Choose a source and replacement by name or registry ID. **Preview** counts matches across the entire loaded template; **Replace all** applies the edit. Compatible block-state properties are retained. Replaced block entities and scheduled ticks are cleared, and **Undo swap** restores them.
* Edits affect every placement sharing that loaded template. They update previews and material counts, but do not modify world blocks or overwrite the original file. Use **Save edited template** and a new filename to keep a separate version.

The workspace uses an orthographic 3D camera and Minecraft's textured block models. Fluids and blocks that use special entity renderers have textured cube placeholders; entities are not displayed. Geometry builds incrementally, and a shared 160,000-face budget bounds preview memory. A visible notice indicates when a very large model has reached that limit. The detailed placement editor remains available under **Details**.

Compiling
=========
* Clone the repository
* Open a command prompt/terminal to the repository directory
* Use Java 25 for the Minecraft 26.3 branch
* Run `./gradlew build` (includes the regression tests)
* The built jar file will be in build/libs/
