package de.jakob.baritoneattack;

import baritone.api.BaritoneAPI;
import baritone.api.command.ICommand;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandNotEnoughArgumentsException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class AttackAddonClient implements ClientModInitializer {

    private static final Minecraft MC = Minecraft.getInstance();

    private static boolean active = false;
    private static TargetMode targetMode = TargetMode.SPECIFIC;

    private static final int MAX_RANGE = 5;
    private static double range = MAX_RANGE;

    private static final Set<Identifier> TARGETS = new HashSet<>();
    private static final List<String> COMMON_MOBS = List.of(
            "zombie",
            "skeleton",
            "creeper",
            "spider",
            "enderman",
            "witch",
            "pillager",
            "vindicator",
            "blaze",
            "magma_cube"
    );
    private static final List<String> TOP_LEVEL_SUGGESTIONS = Stream.concat(
            Stream.of("stop", "status", "all", "hostile", "range "),
            COMMON_MOBS.stream()
    ).toList();

    private enum TargetMode {
        SPECIFIC,
        HOSTILE,
        ALL
    }

    @Override
    public void onInitializeClient() {

        BaritoneAPI.getProvider()
                .getPrimaryBaritone()
                .getCommandManager()
                .getRegistry()
                .register(new AttackCommand());

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());

        System.out.println("[Baritone Attack Addon] loaded.");
    }

    private static void tick() {

        if (!active) {
            return;
        }

        var player = MC.player;
        var level = MC.level;
        var gameMode = MC.gameMode;

        if (player == null || level == null || gameMode == null) {
            return;
        }

        // Attack only while the player is holding an item in the main hand.
        if (player.getMainHandItem().isEmpty()) {
            return;
        }

        // Wait for Minecraft's normal attack cooldown.
        if (player.getAttackStrengthScale(0.0F) < 1.0F) {
            return;
        }

        Mob target = findTarget(player, level);

        if (target == null) {
            return;
        }

        // Turn the player toward the target before attacking.
        player.lookAt(
                EntityAnchorArgument.Anchor.EYES,
                target.getEyePosition()
        );

        // Use Minecraft's standard attack and swing animation.
        gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
    }

    private static Mob findTarget(
            LocalPlayer player,
            ClientLevel level
    ) {

        double maxDistanceSq = range * range;

        Mob best = null;
        double bestDistanceSq = maxDistanceSq;

        for (Entity entity : level.entitiesForRendering()) {

            if (!(entity instanceof Mob mob)) {
                continue;
            }

            if (!mob.isAlive()) {
                continue;
            }

            if (targetMode == TargetMode.HOSTILE && !(mob instanceof Monster)) {
                continue;
            }

            if (targetMode == TargetMode.SPECIFIC) {
                Identifier id =
                        BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());

                if (!TARGETS.contains(id)) {
                    continue;
                }
            }

            double distanceSq = player.distanceToSqr(mob);

            if (distanceSq <= bestDistanceSq) {
                best = mob;
                bestDistanceSq = distanceSq;
            }
        }

        return best;
    }

    private static void stop() {

        active = false;

        TARGETS.clear();
        targetMode = TargetMode.SPECIFIC;
    }

    private static final class AttackCommand implements ICommand {

        @Override
        public void execute(
                String label,
                IArgConsumer args
        ) throws CommandException {

            if (!args.hasAny()) {
                throw new CommandNotEnoughArgumentsException(1);
            }

            String first =
                    args.getString().toLowerCase(Locale.ROOT);
            double rangeForSelection = range;

            // Handle commands that stop the attack.

            if (first.equals("stop") || first.equals("off")) {

                stop();

                logDirect("Attack stopped.");

                return;
            }

            // Report the current attack settings.

            if (first.equals("status")) {

                String targets = switch (targetMode) {
                    case ALL -> "all mobs";
                    case HOSTILE -> "hostile mobs";
                    case SPECIFIC -> TARGETS.toString();
                };

                logDirect(
                        "Attack: "
                                + (active ? "ON" : "OFF")
                                + ", range="
                                + range
                                + ", targets="
                                + targets
                );

                return;
            }

            // Update the attack range, optionally with a new target selection.

            if (first.equals("range")) {

                if (!args.hasAny()) {
                    throw new CommandNotEnoughArgumentsException(2);
                }

                String rangeString = args.getString();

                double newRange;

                try {

                    newRange = Double.parseDouble(rangeString);

                } catch (NumberFormatException e) {

                    logDirect(
                            "Invalid range: " + rangeString
                    );

                    return;
                }

                if (!Double.isFinite(newRange)
                        || newRange < 1.0
                        || newRange > MAX_RANGE) {

                    logDirect(
                            "Range must be between 1 and "
                                    + MAX_RANGE
                                    + " blocks."
                    );

                    return;
                }

                rangeForSelection = newRange;

                // With no further argument, keep the current targets and only change the range.
                if (!args.hasAny()) {

                    range = newRange;
                    active = true;

                    logDirect(
                            "Range changed to "
                                    + range
                                    + " blocks."
                    );

                    return;
                }

                // Additional target arguments follow the range.
                first = args.getString()
                        .toLowerCase(Locale.ROOT);
            }

            // Enable attacks against every mob.

            if (first.equals("all")) {

                range = rangeForSelection;
                TARGETS.clear();
                targetMode = TargetMode.ALL;
                active = true;

                logDirect(
                        "Attacking all mobs within "
                                + range
                                + " blocks."
                );

                return;
            }

            // Enable attacks against hostile mobs only.

            if (first.equals("hostile")) {

                range = rangeForSelection;
                TARGETS.clear();
                targetMode = TargetMode.HOSTILE;
                active = true;

                logDirect(
                        "Attacking hostile mobs within "
                                + range
                                + " blocks."
                );

                return;
            }

            // Treat the arguments as specific entity types.

            Set<Identifier> newTargets = new HashSet<>();
            addTarget(first, newTargets);

            while (args.hasAny()) {

                addTarget(args.getString().toLowerCase(Locale.ROOT), newTargets);
            }

            if (newTargets.isEmpty()) {
                return;
            }

            range = rangeForSelection;
            TARGETS.clear();
            TARGETS.addAll(newTargets);
            targetMode = TargetMode.SPECIFIC;
            active = true;

            logDirect(
                    "Attack enabled for: "
                            + TARGETS
                            + " (range "
                            + range
                            + ")."
            );
        }

        // Add the entity type when the identifier is valid and registered.

        private static void addTarget(String raw, Set<Identifier> targets) {

            Identifier id;

            try {

                id = raw.contains(":")
                        ? Identifier.parse(raw)
                        : Identifier.withDefaultNamespace(raw);

            } catch (Exception e) {

                System.out.println(
                        "[Baritone Attack Addon] Invalid entity id: "
                                + raw
                );

                return;
            }

            if (BuiltInRegistries.ENTITY_TYPE
                    .getOptional(id)
                    .isEmpty()) {

                System.out.println(
                        "[Baritone Attack Addon] Unknown entity type: "
                                + id
                );

                return;
            }

            targets.add(id);
        }

        // Provide context-sensitive command suggestions.

        @Override
        public Stream<String> tabComplete(
                String label,
                IArgConsumer args
        ) {

            List<String> entered = args.getArgs()
                    .stream()
                    .map(argument -> argument.getValue())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .toList();

            String rawRest = args.rawRest();

            // Suggest top-level commands and common entity types.
            if (entered.isEmpty()) {
                return TOP_LEVEL_SUGGESTIONS.stream();
            }

            String first = entered.get(0);

            // These commands do not take additional arguments.
            if (first.equals("stop") || first.equals("status")) {
                return Stream.empty();
            }

            // Suggest valid range values and, optionally, target types.
            if (first.equals("range") && !rawRest.contains(" ")) {
                // Suggest each supported range value.
                return java.util.stream.IntStream.rangeClosed(1, MAX_RANGE)
                        .mapToObj(value -> "range " + value);
            }

            // These target modes do not take additional arguments.
            if (first.equals("all") || first.equals("hostile")) {
                return Stream.empty();
            }

            // Allow additional target modes or entity types after a specific target.
            return Stream.concat(
                    Stream.of("all", "hostile"),
                    COMMON_MOBS.stream()
            );
        }

        // Describe the command for Baritone's help output.

        @Override
        public String getShortDesc() {
            return "Automatically attacks selected mobs.";
        }

        @Override
        public List<String> getLongDesc() {

            return List.of(
                    "Automatically attacks selected mobs.",
                    "",
                    "Usage:",
                    "#attack zombie skeleton creeper",
                    "#attack hostile",
                    "#attack all",
                    "#attack range 5",
                    "#attack range 5 zombie skeleton",
                    "#attack status",
                    "#attack stop"
            );
        }

        // Register the command name used in chat.

        @Override
        public List<String> getNames() {
            return List.of("attack");
        }
    }
}