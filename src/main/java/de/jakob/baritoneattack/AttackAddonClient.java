package de.jakob.baritoneattack;

import baritone.api.BaritoneAPI;
import baritone.api.command.ICommand;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandNotEnoughArgumentsException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class AttackAddonClient implements ClientModInitializer {

    private final AttackController attackController = new AttackController();

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
            Stream.of(
                    "stop",
                    "off",
                    "status",
                    "all",
                    "hostile",
                    "friendly",
                    "range ",
                    "pause "
            ),
            COMMON_MOBS.stream()
    ).toList();

    @Override
    public void onInitializeClient() {

        BaritoneAPI.getProvider()
                .getPrimaryBaritone()
                .getCommandManager()
                .getRegistry()
                .register(new AttackCommand(attackController));

        ClientTickEvents.END_CLIENT_TICK.register(client -> attackController.tick());

        System.out.println("[Baritone Attack Addon] loaded.");
    }

    private static final class AttackCommand implements ICommand {

        private final AttackController attackController;

        private AttackCommand(AttackController attackController) {
            this.attackController = attackController;
        }

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

                attackController.stop();

                logDirect("Attack stopped.");

                return;
            }

            // Report the current attack settings.

            if (first.equals("status")) {

                String mode = attackController.isActive()
                        ? switch (attackController.getTargetMode()) {
                            case ALL -> "all";
                            case HOSTILE -> "hostile";
                            case FRIENDLY -> "friendly";
                            case SPECIFIC -> "specific";
                        }
                        : "none";
                String targets = !attackController.isActive()
                        ? "none"
                        : switch (attackController.getTargetMode()) {
                            case ALL -> "all mobs";
                            case HOSTILE -> "hostile mobs";
                            case FRIENDLY -> "friendly mobs";
                            case SPECIFIC -> String.join(
                                    ", ",
                                    attackController.getTargets().stream()
                                            .map(id -> id.toString())
                                            .sorted()
                                            .toList()
                            );
                        };

                logDirect(
                        "Attack status: "
                                + (attackController.isActive() ? "ON" : "OFF")
                                + ", mode="
                                + mode
                                + ", range="
                                + attackController.getRange()
                                + " blocks"
                                + ", pause="
                                + attackController.getPauseSeconds()
                                + " seconds"
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
                        || newRange > AttackController.MAX_RANGE) {

                    logDirect(
                            "Range must be between 1 and "
                                    + AttackController.MAX_RANGE
                                    + " blocks."
                    );

                    return;
                }

                if (args.hasAny()) {
                    logDirect("Range accepts only one number.");
                    return;
                }

                attackController.setRange(newRange);
                logDirect(
                        "Range changed to "
                                + attackController.getRange()
                                + " blocks."
                );

                return;
            }

            if (first.equals("pause")) {

                if (!args.hasAny()) {
                    throw new CommandNotEnoughArgumentsException(2);
                }

                String pauseString = args.getString();
                int pauseSeconds;

                try {
                    pauseSeconds = Integer.parseInt(pauseString);
                } catch (NumberFormatException e) {
                    logDirect("Invalid pause: " + pauseString);
                    return;
                }

                if (pauseSeconds < 1 || pauseSeconds > 5) {
                    logDirect("Pause must be between 1 and 5 seconds.");
                    return;
                }

                if (args.hasAny()) {
                    logDirect("Pause accepts only one number.");
                    return;
                }

                attackController.setPauseSeconds(pauseSeconds);
                logDirect("Attack pause changed to " + pauseSeconds + " seconds.");
                return;
            }

            // Enable attacks against every mob.

            if (first.equals("all")) {

                if (args.hasAny()) {
                    logDirect("'all' cannot be combined with specific mobs.");
                    return;
                }

                attackController.start(AttackController.TargetMode.ALL, Set.of());

                logDirect(
                        "Attacking all mobs within "
                                + attackController.getRange()
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

                attackController.start(AttackController.TargetMode.HOSTILE, Set.of());

                logDirect(
                        "Attacking hostile mobs within "
                                + attackController.getRange()
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

                attackController.start(AttackController.TargetMode.FRIENDLY, Set.of());

                logDirect(
                        "Attacking friendly mobs within "
                                + attackController.getRange()
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

            attackController.start(AttackController.TargetMode.SPECIFIC, newTargets);

            logDirect(
                    "Attack enabled for: "
                            + attackController.getTargets()
                            + " (range "
                            + attackController.getRange()
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
                    return java.util.stream.IntStream.rangeClosed(
                                    1,
                                    AttackController.MAX_RANGE
                            )
                            .mapToObj(value -> "range " + value)
                            .filter(suggestion -> suggestion.contains(prefix));
                }

                return Stream.empty();
            }

            if (first.equals("pause")) {
                if (entered.size() == 1
                        || (entered.size() == 2 && entered.get(1).isEmpty())) {
                    String prefix = entered.size() == 1 ? first : "";
                    return java.util.stream.IntStream.rangeClosed(1, 5)
                            .mapToObj(value -> "pause " + value)
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
                    "#attack pause 3",
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