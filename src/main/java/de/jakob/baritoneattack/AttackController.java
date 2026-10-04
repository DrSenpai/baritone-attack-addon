package de.jakob.baritoneattack;

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
import java.util.Set;

final class AttackController {

    static final int MAX_RANGE = 5;

    private static final Minecraft MC = Minecraft.getInstance();

    private boolean active;
    private TargetMode targetMode = TargetMode.SPECIFIC;
    private double range = MAX_RANGE;
    private int pauseSeconds;
    private long lastAttackTimeNanos;
    private boolean hasAttacked;
    private final Set<Identifier> targets = new HashSet<>();

    void tick() {

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

        if (hasAttacked && System.nanoTime() - lastAttackTimeNanos
                < pauseSeconds * 1_000_000_000L) {
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
        lastAttackTimeNanos = System.nanoTime();
        hasAttacked = true;
    }

    private Mob findTarget(
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

                if (!targets.contains(id)) {
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

    void start(TargetMode mode, Set<Identifier> targetTypes) {
        targets.clear();
        targets.addAll(targetTypes);
        targetMode = mode;
        active = true;
    }

    void stop() {
        active = false;
        targets.clear();
        targetMode = TargetMode.SPECIFIC;
    }

    boolean isActive() {
        return active;
    }

    TargetMode getTargetMode() {
        return targetMode;
    }

    Set<Identifier> getTargets() {
        return Set.copyOf(targets);
    }

    double getRange() {
        return range;
    }

    void setRange(double range) {
        this.range = range;
    }

    int getPauseSeconds() {
        return pauseSeconds;
    }

    void setPauseSeconds(int pauseSeconds) {
        this.pauseSeconds = pauseSeconds;
    }

    enum TargetMode {
        SPECIFIC,
        HOSTILE,
        FRIENDLY,
        ALL
    }
}
