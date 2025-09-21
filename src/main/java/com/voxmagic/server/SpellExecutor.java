package com.voxmagic.server;

import com.voxmagic.common.config.ModConfig;
import com.voxmagic.content.ModItems;
import com.voxmagic.network.NetworkInit;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

public final class SpellExecutor {
    private static final Random RNG = new Random();

    public static boolean canCastNow(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(ModItems.MAGIC_BOOK) || player.getOffHandStack().isOf(ModItems.MAGIC_BOOK);
    }

    public static boolean execute(ServerPlayerEntity player, NetworkInit.SpellCastPayload payload) {
        String spell = payload.spellId;
        boolean success;
        switch (spell) {
            case "lightning" -> success = castLightning(player);
            case "web" -> success = castWeb(player);
            case "heal" -> success = castHeal(player);
            case "ghost" -> success = castGhost(player);
            case "wall" -> success = castWall(player);
            case "fireball" -> success = castFireball(player);
            case "slime" -> success = castSlimePlatform(player);
            case "dome" -> success = castGlassDome(player);
            case "shockwave" -> success = castShockwave(player);
            case "levitate" -> success = castLevitate(player);
            case "meteor" -> success = castMeteor(player);
            case "push" -> success = castPush(player);
            case "teleport" -> success = castTeleport(player);
            case "pull" -> success = castPull(player);
            default -> success = false;
        }
        if (success) {
            spawnCastParticles(player);
        }
        return success;
    }

    private static BlockHitResult raycast(PlayerEntity player, double max) {
        Vec3d from = player.getCameraPosVec(1f);
        Vec3d dir = player.getRotationVec(1f);
        Vec3d to = from.add(dir.multiply(max));
        return player.getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
    }

    private static boolean castLightning(ServerPlayerEntity p) {
        double max = ModConfig.INSTANCE.raycast_max_distance;
        HitResult hr = raycast(p, max);
        if (hr.getType() == HitResult.Type.MISS) return false;
        Vec3d pos = hr.getPos();
        ServerWorld sw = (ServerWorld) p.getWorld();
        LightningEntity l1 = EntityType.LIGHTNING_BOLT.create(sw, e -> {}, BlockPos.ofFloored(pos), SpawnReason.TRIGGERED, false, false);
        if (l1 != null) {
            l1.setChanneler(p);
            sw.spawnEntity(l1);
        }
        double dx = (RNG.nextBoolean() ? 1 : -1) * RNG.nextDouble();
        double dz = (RNG.nextBoolean() ? 1 : -1) * RNG.nextDouble();
        LightningEntity l2 = EntityType.LIGHTNING_BOLT.create(sw, e -> {}, BlockPos.ofFloored(pos.x + dx, pos.y, pos.z + dz), SpawnReason.TRIGGERED, false, false);
        if (l2 != null) {
            l2.setChanneler(p);
            sw.spawnEntity(l2);
        }
        return true;
    }

    private static boolean castWeb(ServerPlayerEntity p) {
        int baseRadius = Math.max(2, intValue("web", cfg -> cfg.cross_radius, 2));
        double max = ModConfig.INSTANCE.raycast_max_distance;
        HitResult hr = raycast(p, max);
        if (hr.getType() == HitResult.Type.MISS) return false;
        BlockPos center = BlockPos.ofFloored(hr.getPos());
        World world = p.getWorld();
        Map<BlockPos, BlockState> restore = new HashMap<>();
        int placed = 0;
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -baseRadius; dx <= baseRadius; dx++) {
                for (int dz = -baseRadius; dz <= baseRadius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > baseRadius) continue;
                    BlockPos pos = center.add(dx, dy, dz);
                    BlockState previous = world.getBlockState(pos);
                    if (!previous.isAir()) continue;
                    if (!world.setBlockState(pos, Blocks.COBWEB.getDefaultState(), 3)) continue;
                    restore.put(pos.toImmutable(), previous);
                    placed++;
                }
            }
        }
        if (placed > 0) {
            TemporaryBlockManager.scheduleRestore(world, restore, doubleValue("web", cfg -> cfg.lifetime_sec, 3.5));
            return true;
        }
        return false;
    }

    private static boolean castHeal(ServerPlayerEntity p) {
        int regenSec = intValue("heal", cfg -> cfg.regen_sec, 3);
        int satSec = intValue("heal", cfg -> cfg.saturation_sec, 3);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, regenSec * 20, 0, false, true, true));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, satSec * 20, 0, false, true, true));
        return true;
    }

    private static boolean castGhost(ServerPlayerEntity p) {
        double duration = doubleValue("ghost", cfg -> cfg.duration_sec, 1.5);
        GhostManager.enterGhost(p, duration);
        return true;
    }

    private static boolean castWall(ServerPlayerEntity p) {
        int size = Math.max(1, intValue("wall", cfg -> cfg.size, 6));
        double max = ModConfig.INSTANCE.raycast_max_distance;
        HitResult hr = raycast(p, max);
        if (hr.getType() == HitResult.Type.MISS) return false;
        BlockPos center = BlockPos.ofFloored(hr.getPos());
        float yaw = p.getYaw();
        double rad = Math.toRadians(yaw);
        int rx = (int) Math.round(Math.cos(rad));
        int rz = (int) Math.round(Math.sin(rad));
        int half = size / 2;
        Map<BlockPos, BlockState> restore = new HashMap<>();
        int placed = 0;
        for (int u = -half; u < -half + size + 4; u++) {
            for (int v = -half - 1; v < -half + size + 1; v++) {
                BlockPos pos = center.add(rx * v, u, rz * v);
                BlockState prev = p.getWorld().getBlockState(pos);
                if (!prev.isAir()) continue;
                if (!p.getWorld().setBlockState(pos, Blocks.STONE.getDefaultState(), 3)) continue;
                restore.put(pos.toImmutable(), prev);
                placed++;
            }
        }
        if (placed > 0) {
            TemporaryBlockManager.scheduleRestore(p.getWorld(), restore, doubleValue("wall", cfg -> cfg.lifetime_sec, 4.0));
            return true;
        }
        return false;
    }

    private static boolean castFireball(ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVec(1f).normalize();
        double speed = 1.6;
        double powerVal = doubleValue("fireball", cfg -> cfg.power, 2.5);
        SmallFireballEntity fb = new ExplodingFireball(p.getWorld(), p, dir.x * speed, dir.y * speed, dir.z * speed, (float) powerVal);
        fb.setPos(eye.x, eye.y - 0.1, eye.z);
        fb.setVelocity(dir.x * speed, dir.y * speed, dir.z * speed);
        p.getWorld().spawnEntity(fb);
        return true;
    }

    private static boolean castSlimePlatform(ServerPlayerEntity p) {
        ServerWorld world = (ServerWorld) p.getWorld();
        BlockPos base = BlockPos.ofFloored(p.getPos()).down();
        Map<BlockPos, BlockState> restore = new HashMap<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = base.add(dx, 0, dz);
                BlockState previous = world.getBlockState(pos);
                if (previous.isOf(Blocks.BEDROCK)) continue;
                if (!world.setBlockState(pos, Blocks.SLIME_BLOCK.getDefaultState(), 3)) continue;
                restore.put(pos.toImmutable(), previous);
            }
        }
        if (restore.isEmpty()) return false;
        TemporaryBlockManager.scheduleRestore(world, restore, doubleValue("slime", cfg -> cfg.lifetime_sec, 2.0));
        return true;
    }

    private static boolean castGlassDome(ServerPlayerEntity p) {
        ServerWorld world = (ServerWorld) p.getWorld();
        BlockPos origin = BlockPos.ofFloored(p.getPos());
        int radius = Math.max(2, intValue("dome", cfg -> cfg.size, 3));
        double radiusSq = radius * radius;
        double innerSq = (radius - 1.0) * (radius - 1.0);
        Map<BlockPos, BlockState> restore = new HashMap<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = 0; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > radiusSq || distSq < innerSq) continue;
                    BlockPos pos = origin.add(dx, dy, dz);
                    BlockState prev = world.getBlockState(pos);
                    if (prev.isOf(Blocks.BEDROCK)) continue;
                    if (!world.setBlockState(pos, Blocks.GLASS.getDefaultState(), 3)) continue;
                    restore.put(pos.toImmutable(), prev);
                }
            }
        }
        if (restore.isEmpty()) return false;
        TemporaryBlockManager.scheduleRestore(world, restore, doubleValue("dome", cfg -> cfg.lifetime_sec, 2.0));
        return true;
    }

    private static boolean castShockwave(ServerPlayerEntity p) {
        double radius = doubleValue("shockwave", cfg -> cfg.power, 6.0);
        ServerWorld world = (ServerWorld) p.getWorld();
        List<Entity> targets = world.getOtherEntities(p, p.getBoundingBox().expand(radius), entity -> entity.isAlive());
        if (targets.isEmpty()) return false;
        Vec3d origin = p.getPos();
        for (Entity e : targets) {
            Vec3d offset = e.getPos().subtract(origin);
            if (offset.lengthSquared() < 1.0E-4) offset = new Vec3d(0, 0.1, 0);
            Vec3d impulse = offset.normalize().multiply(1.2);
            e.addVelocity(impulse.x, 0.5, impulse.z);
            e.velocityModified = true;
        }
        return true;
    }

    private static boolean castLevitate(ServerPlayerEntity p) {
        double range = doubleValue("levitate", cfg -> cfg.power, 15.0);
        EntityHitResult hit = raycastEntity(p, range);
        if (hit == null) return false;
        Entity target = hit.getEntity();
        if (!(target instanceof LivingEntity living)) return false;
        living.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 60, 1, false, true, true));
        return true;
    }

    private static boolean castMeteor(ServerPlayerEntity p) {
        double max = doubleValue("meteor", cfg -> cfg.power, 20.0);
        HitResult hr = raycast(p, max);
        Vec3d target = hr.getType() == HitResult.Type.MISS ? p.getEyePos().add(p.getRotationVec(1f).multiply(max)) : hr.getPos();
        Vec3d spawn = target.add(0, 12, 0);
        ServerWorld world = (ServerWorld) p.getWorld();
        TntEntity tnt = new TntEntity(world, spawn.x, spawn.y, spawn.z, p);
        tnt.setFuse(40);
        world.spawnEntity(tnt);
        return true;
    }

    private static boolean castPush(ServerPlayerEntity p) {
        Vec3d dir = p.getRotationVec(1f).normalize();
        double push = doubleValue("push", cfg -> cfg.power, 1.5);
        p.addVelocity(dir.x * push, dir.y * (push * 0.5), dir.z * push);
        p.velocityModified = true;
        return true;
    }

    private static boolean castTeleport(ServerPlayerEntity p) {
        double max = doubleValue("teleport", cfg -> cfg.power, 10.0);
        HitResult hr = raycast(p, max);
        Vec3d start = p.getPos();
        Vec3d dir = p.getRotationVec(1f);
        double distance = max;
        if (hr.getType() != HitResult.Type.MISS) {
            distance = Math.max(0, hr.getPos().distanceTo(p.getEyePos()) - 1.0);
        }
        ServerWorld world = (ServerWorld) p.getWorld();
        for (double d = Math.min(distance, max); d >= 0.5; d -= 0.5) {
            Vec3d candidate = start.add(dir.multiply(d));
            if (isSafeTeleportLocation(world, p, candidate)) {
                p.requestTeleport(candidate.x, candidate.y, candidate.z);
                p.setVelocity(Vec3d.ZERO);
                return true;
            }
        }
        return false;
    }

    private static boolean isSafeTeleportLocation(World world, ServerPlayerEntity player, Vec3d candidate) {
        Vec3d offset = candidate.subtract(player.getPos());
        return world.isSpaceEmpty(player, player.getBoundingBox().offset(offset)) && !world.containsFluid(player.getBoundingBox().offset(offset));
    }

    private static boolean castPull(ServerPlayerEntity p) {
        double range = doubleValue("pull", cfg -> cfg.power, 5.0);
        EntityHitResult hit = raycastEntity(p, range);
        if (hit == null) return false;
        Entity target = hit.getEntity();
        if (target == p) return false;
        if (target.squaredDistanceTo(p) > range * range) return false;
        Vec3d toward = p.getPos().add(0, p.getStandingEyeHeight(), 0).subtract(target.getPos());
        if (toward.lengthSquared() < 1.0E-4) return false;
        Vec3d pull = toward.normalize().multiply(0.8);
        target.addVelocity(pull.x, pull.y + 0.1, pull.z);
        target.velocityModified = true;
        return true;
    }

    private static EntityHitResult raycastEntity(PlayerEntity player, double maxDistance) {
        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(1f);
        Vec3d end = start.add(dir.multiply(maxDistance));
        Box box = player.getBoundingBox().stretch(dir.multiply(maxDistance)).expand(1.0);
        return ProjectileUtil.getEntityCollision(player.getWorld(), player, start, end, box, entity -> !entity.isSpectator() && entity.isAlive(), ProjectileUtil.DEFAULT_MARGIN);
    }

    private static double doubleValue(String id, Function<ModConfig.SpellCfg, Double> getter, double fallback) {
        ModConfig.SpellCfg cfg = ModConfig.INSTANCE.spells.get(id);
        if (cfg == null) return fallback;
        Double value = getter.apply(cfg);
        return value != null ? value : fallback;
    }

    private static int intValue(String id, Function<ModConfig.SpellCfg, Integer> getter, int fallback) {
        ModConfig.SpellCfg cfg = ModConfig.INSTANCE.spells.get(id);
        if (cfg == null) return fallback;
        Integer value = getter.apply(cfg);
        return value != null ? value : fallback;
    }

    private static void spawnCastParticles(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d origin = player.getPos().add(0, 1.0, 0);
        for (int i = 0; i < 24; i++) {
            double ox = origin.x + (world.random.nextDouble() - 0.5) * 2.0;
            double oy = origin.y + world.random.nextDouble() * 1.5;
            double oz = origin.z + (world.random.nextDouble() - 0.5) * 2.0;
            world.spawnParticles(ParticleTypes.ENCHANT, ox, oy, oz, 1, 0, 0, 0, 0);
        }
    }

    private static class ExplodingFireball extends SmallFireballEntity {
        private final float power;

        ExplodingFireball(World world, ServerPlayerEntity owner, double velX, double velY, double velZ, float power) {
            super(world, owner, new Vec3d(velX, velY, velZ));
            this.power = power;
        }

        @Override
        protected void onCollision(HitResult hitResult) {
            super.onCollision(hitResult);
            if (!this.getWorld().isClient()) {
                boolean blockDamage = ModConfig.INSTANCE.spells.get("fireball").blockDamage != null && ModConfig.INSTANCE.spells.get("fireball").blockDamage;
                this.getWorld().createExplosion(this, getX(), getY(), getZ(), power, blockDamage, blockDamage ? World.ExplosionSourceType.MOB : World.ExplosionSourceType.NONE);
                this.discard();
            }
        }
    }
}
