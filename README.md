# Baritone Attack Addon — Minecraft 26.2

Client-side Fabric addon for the unofficial Baritone 26.2 Fabric port.

## What it does

Adds a real Baritone `#attack` command. It targets mobs locally and uses Minecraft's normal client attack method, rather than spawning a second bot or entity.

Examples:

    #attack zombie skeleton creeper
    #attack hostile
    #attack all
    #attack range 5 zombie skeleton
    #attack status
    #attack stop

Only a sword in the main hand is used.

## Build

Requirements:
- Minecraft 26.2
- Java/JDK 25
- Fabric Loader 0.19.3+
- Fabric API
- The exact Baritone JAR used by your instance

Put this file into `libs/`:

    baritone-fabric-26.2-SNAPSHOT.jar

Then run:

    gradlew.bat build

The output will be in:

    build/libs/baritone-attack-addon-1.0.0.jar

Copy that JAR into the same `mods` folder as Baritone.

### Important

The addon currently declares Baritone's 26.2 mod id as `baritone-meteor`, matching the DeeKahy 26.2 bootleg branch. If your particular Baritone JAR reports a different mod id in Mod Menu / latest.log, change the `baritone-meteor` dependency in `fabric.mod.json` accordingly.
