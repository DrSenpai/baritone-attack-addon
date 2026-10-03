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
            Stream.of("stop", "off", "status", "all", "hostile", "friendly", "range "),
            COMMON_MOBS.stream()
    ).toList();

    private enum TargetMode {
        SPECIFIC,
        HOSTILE,
        FRIENDLY,
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

            if (targetMode == TargetMode.FRIENDLY && mob instanceof Monster) {
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
            // Handle commands that stop the attack.

            if (first.equals("stop") || first.equals("off")) {

                stop();

                logDirect("Attack stopped.");

                return;
            }

            // Report the current attack settings.

            if (first.equals("status")) {

                String mode = active
                        ? switch (targetMode) {
                            case ALL -> "all";
                            case HOSTILE -> "hostile";
                            case FRIENDLY -> "friendly";
                            case SPECIFIC -> "specific";
                        }
                        : "none";
                String targets = !active
                        ? "none"
                        : switch (targetMode) {
                            case ALL -> "all mobs";
                            case HOSTILE -> "hostile mobs";
                            case FRIENDLY -> "friendly mobs";
                            case SPECIFIC -> String.join(
                                    ", ",
                                    TARGETS.stream()
                                            .map(id -> id.toString())
                                            .sorted()
                                            .toList()
                            );
                        };

                logDirect(
                        "Attack status: "
                                + (active ? "ON" : "OFF")
                                + ", mode="
                                + mode
                                + ", range="
                                + range
                                + " blocks"
                                + ", targets="
                                + targets
                );

                return;
            }

            // Update only the distance used to detect eligible targets.

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

                if (args.hasAny()) {
                    logDirect("Range accepts only one number.");
                    return;
                }

                range = newRange;
                logDirect(
                        "Range changed to "
                                + range
                                + " blocks."
                );

                return;
            }

            // Enable attacks against every mob.

            if (first.equals("all")) {

                if (args.hasAny()) {
                    logDirect("'all' cannot be combined with specific mobs.");
                    return;
                }

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

                if (args.hasAny()) {
                    logDirect("'hostile' cannot be combined with specific mobs.");
                    return;
                }

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

            // Enable attacks against friendly mobs only.

            if (first.equals("friendly")) {

                if (args.hasAny()) {
                    logDirect("'friendly' cannot be combined with specific mobs.");
                    return;
                }

                TARGETS.clear();
                targetMode = TargetMode.FRIENDLY;
                active = true;

                logDirect(
                        "Attacking friendly mobs within "
                                + range
                                + " blocks."
                );

                return;
            }

            // Treat the arguments as specific entity types.

            Set<Identifier> newTargets = new HashSet<>();
            addTarget(first, newTargets);

            while (args.hasAny()) {

                String target = args.getString().toLowerCase(Locale.ROOT);
                if (target.equals("all")
                        || target.equals("hostile")
                        || target.equals("friendly")) {
                    logDirect("Choose specific mobs or one target mode, not both.");
                    return;
                }

                addTarget(target, newTargets);
            }

            if (newTargets.isEmpty()) {
                return;
            }

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

            // Suggest top-level commands and common entity types.
            if (entered.isEmpty()) {
                return TOP_LEVEL_SUGGESTIONS.stream();
            }

            String first = entered.get(0);

            if (first.equals("range")) {
                if (entered.size() == 1
                        || (entered.size() == 2 && entered.get(1).isEmpty())) {
                    String prefix = entered.size() == 1 ? first : "";
                    return java.util.stream.IntStream.rangeClosed(1, MAX_RANGE)
                            .mapToObj(value -> "range " + value)
                            .filter(suggestion -> suggestion.contains(prefix));
                }

                return Stream.empty();
            }

            if (entered.size() == 1) {
                if (first.equals("stop")
                        || first.equals("status")
                        || first.equals("off")
                        || first.equals("all")
                        || first.equals("hostile")
                        || first.equals("friendly")) {
                    return Stream.empty();
                }

                return Stream.concat(
                                TOP_LEVEL_SUGGESTIONS.stream(),
                                COMMON_MOBS.stream()
                        )
                        .filter(suggestion -> suggestion.contains(first));
            }

            // These target modes do not take additional arguments.
            if (first.equals("stop")
                    || first.equals("status")
                    || first.equals("off")
                    || first.equals("all")
                    || first.equals("hostile")
                    || first.equals("friendly")) {
                return Stream.empty();
            }

            // Once a specific mob is selected, only additional mob names are valid.
            String current = entered.get(entered.size() - 1);
            return COMMON_MOBS.stream()
                    .filter(suggestion -> suggestion.contains(current));
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
                    "#attack friendly",
                    "#attack all",
                    "#attack range 5",
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