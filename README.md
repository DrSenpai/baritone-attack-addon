# Baritone Attack Addon

A client-side Fabric addon that adds an `#attack` command to the unofficial
Baritone Fabric port for Minecraft 26.2. It selects nearby mobs and attacks
them using Minecraft's normal client attack interaction.

## Features

- Select one or more entity types, such as `zombie` or `minecraft:skeleton`.
- Target hostile mobs, friendly mobs, or all mobs.
- Choose a range from 1 to 5 blocks.
- Configure a 1- to 5-second delay between attacks.
- Automatically select the nearest eligible mob in range.
- Check the current mode, range, pause, and selected targets with `status`.
- Stop automatic attacks at any time with `stop` or `off`.

The addon does not pathfind to targets. It only considers living `Mob` entities
currently available to the client, and the server remains authoritative over
whether an attack succeeds.

## Commands

Run these commands through Baritone's chat command interface:

| Command | Description |
| --- | --- |
| `#attack <mob> [mob ...]` | Attack the nearest matching mob. Entity IDs without a namespace use the `minecraft` namespace. |
| `#attack hostile` | Attack the nearest hostile mob. |
| `#attack friendly` | Attack the nearest non-hostile mob. |
| `#attack all` | Attack the nearest mob of any type. |
| `#attack range <1-5>` | Set the detection range in blocks. |
| `#attack pause <1-5>` | Set the delay between attacks in seconds. The default is 0 (no additional delay). |
| `#attack status` | Show whether attacking is enabled, the target mode, range, pause, and selected targets. |
| `#attack stop` / `#attack off` | Disable attacking and clear the selected targets. |

Examples:

```text
#attack zombie skeleton creeper
#attack minecraft:zombie
#attack hostile
#attack friendly
#attack all
#attack range 5
#attack pause 3
#attack zombie skeleton
#attack friendly
#attack status
#attack stop
```

## How it works

While enabled, the addon checks at the end of each client tick for an eligible
mob within the configured range. It chooses the nearest matching mob, turns the
player toward it, and uses Minecraft's standard client attack and swing
interactions. An item must be present in the main hand, the normal attack
cooldown must be ready, and the configured pause must have elapsed since the
previous attack. The addon does not require that the item be a sword.

## Requirements

- Minecraft 26.2
- Java/JDK 25
- Fabric Loader 0.19.3 or newer
- Fabric API for Minecraft 26.2
- The Baritone Fabric 26.2 JAR used by your instance

The Baritone JAR is a compile-time-only dependency and is not bundled into the
addon. The project expects it at:

```text
libs/baritone-fabric-26.2-SNAPSHOT.jar
```

The declared Baritone mod ID in `fabric.mod.json` is `baritone-meteor`, matching
the DeeKahy 26.2 bootleg branch. If your Baritone build uses another mod ID,
update the dependency declaration in `fabric.mod.json` to match it.

## Build from source

On Windows:

```powershell
.\gradlew.bat build
```

On macOS or Linux:

```sh
bash ./gradlew build
```

The release-ready JAR is written to `build/libs/` and includes both the addon
version and Minecraft version in its filename. For example:

```text
build/libs/baritone-attack-addon-1.0.2-mc26.2.jar
```

The versions are configured in `gradle.properties`.

## Install

Download the JAR from the repository's **Releases** page and place it in the
same `mods` folder as the matching Baritone Fabric 26.2 build. Start Minecraft
with a Fabric profile for Minecraft 26.2.

## GitHub releases

Pushing a tag that matches the configured addon version starts the release
workflow. For version `1.0.2`, create and push the tag with:

```sh
git tag v1.0.2
git push origin v1.0.2
```

After the workflow succeeds, the built JAR is attached to the corresponding
GitHub Release under the repository's **Releases** page. The workflow rejects
tags whose version does not match `mod_version` in `gradle.properties`.
