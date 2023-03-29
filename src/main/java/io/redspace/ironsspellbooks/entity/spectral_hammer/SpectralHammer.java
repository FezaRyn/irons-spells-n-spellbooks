package io.redspace.ironsspellbooks.entity.spectral_hammer;

import io.redspace.ironsspellbooks.IronsSpellbooks;
import io.redspace.ironsspellbooks.registries.EntityRegistry;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import software.bernie.geckolib3.core.AnimationState;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SpectralHammer extends LivingEntity implements IAnimatable {

    private final int ticksToLive = 25;
    private final int doDamageTick = 13;
    private boolean didDamage = false;
    private int ticksAlive = 0;
    private boolean playSwingAnimation = true;
    private BlockHitResult blockHitResult;
    private float damageAmount;

    public SpectralHammer(EntityType<? extends SpectralHammer> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public SpectralHammer(Level levelIn, LivingEntity owner, BlockHitResult blockHitResult, float damageAmount) {
        this(EntityRegistry.SPECTRAL_HAMMER.get(), levelIn);

        this.blockHitResult = blockHitResult;
        this.damageAmount = damageAmount;

        var xRot = owner.getXRot();
        var yRot = owner.getYRot();
        var yHeadRot = owner.getYHeadRot();

        this.setYRot(yRot);
        this.setXRot(xRot);
        this.setYBodyRot(yRot);
        this.setYHeadRot(yHeadRot);

        IronsSpellbooks.LOGGER.debug("SpectralHammer: owner - xRot:{}, yRot:{}, yHeadRot:{}", xRot, yRot, yHeadRot);
        IronsSpellbooks.LOGGER.debug("SpectralHammer: this - xRot:{}, yRot:{}, look:{}", this.getXRot(), this.getYRot(), this.getLookAngle());
        IronsSpellbooks.LOGGER.debug("SpectralHammer: blockHitResult.dir:{}, damageAmount:{}", blockHitResult.getDirection(), damageAmount);
    }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        return false;
    }

    @Override
    public void tick() {
        if (++ticksAlive >= ticksToLive) {
            discard();
        }

        if (ticksAlive >= doDamageTick && !didDamage) {
            if (blockHitResult != null && blockHitResult.getType() != HitResult.Type.MISS) {
                var blockPos = blockHitResult.getBlockPos();
                var blockState = level.getBlockState(blockPos);

                if (blockState.is(BlockTags.STONE_ORE_REPLACEABLES)) {
                    var blockCollector = getBlockCollector(blockPos, blockHitResult.getDirection(), (int) damageAmount / 2, (int) damageAmount, new HashSet<>(), new HashSet<>());
                    collectBlocks(blockPos, blockCollector);

                    /*
                     * Sets a block state into this world.Flags are as follows:
                     * 1 will cause a block update.
                     * 2 will send the change to clients.
                     * 4 will prevent the block from being re-rendered.
                     * 8 will force any re-renders to run on the main thread instead
                     * 16 will prevent neighbor reactions (e.g. fences connecting, observers pulsing).
                     * 32 will prevent neighbor reactions from spawning drops.
                     * 64 will signify the block is being moved.
                     * Flags can be OR-ed
                     */
                    final int flags = 1 | 2;// | 16 | 32;

                    blockCollector.blocksToRemove.forEach(pos -> {
                        IronsSpellbooks.LOGGER.debug("SpectralHammer.tick: removing blockPos{}", pos);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
                    });
                }
            }

            didDamage = true;
        }

        super.tick();
    }

    private void collectBlocks(BlockPos blockPos, BlockCollectorHelper bch) {
        IronsSpellbooks.LOGGER.debug("SpectralHammer.collectBlocks: blockPos:{} checked:{} toRemove:{}", blockPos, bch.blocksChecked.size(), bch.blocksToRemove.size());

        if (bch.blocksChecked.contains(blockPos) || bch.blocksToRemove.contains(blockPos)) {
            return;
        }

        if (bch.isValidBlockToCollect(level, blockPos)) {
            IronsSpellbooks.LOGGER.debug("SpectralHammer.collectBlocks: blockPos{} is valid", blockPos);
            bch.blocksToRemove.add(blockPos);
            collectBlocks(blockPos.above(), bch);
            collectBlocks(blockPos.below(), bch);
            collectBlocks(blockPos.north(), bch);
            collectBlocks(blockPos.south(), bch);
            collectBlocks(blockPos.east(), bch);
            collectBlocks(blockPos.west(), bch);
        } else {
            IronsSpellbooks.LOGGER.debug("SpectralHammer.collectBlocks: blockPos{} is not valid", blockPos);
            bch.blocksChecked.add(blockPos);
        }
    }

    private BlockCollectorHelper getBlockCollector(BlockPos origin, Direction direction, int radius, int depth, Set<BlockPos> blocksToRemove, Set<BlockPos> blocksChecked) {
        if (direction == Direction.WEST) {
            int minX = origin.getX();
            int maxX = origin.getX() + depth;
            int minY = origin.getY() - radius;
            int maxY = origin.getY() + radius;
            int minZ = origin.getZ() - radius;
            int maxZ = origin.getZ() + radius;

            return new BlockCollectorHelper(origin, direction, minX, maxX, minY, maxY, minZ, maxZ, blocksToRemove, blocksChecked);
        }

        return null;
    }

    private record BlockCollectorHelper(
            BlockPos origin,
            Direction originVector,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            Set<BlockPos> blocksToRemove,
            Set<BlockPos> blocksChecked) {

        public boolean isValidBlockToCollect(Level level, BlockPos bp) {
            return level.getBlockState(bp).is(BlockTags.STONE_ORE_REPLACEABLES)
                    && bp.getX() >= minX
                    && bp.getX() <= maxX
                    && bp.getY() >= minY
                    && bp.getY() <= maxY
                    && bp.getZ() >= minZ
                    && bp.getZ() <= maxZ;
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    protected float getStandingEyeHeight(Pose pPose, EntityDimensions pDimensions) {
        return pDimensions.height * 0.6F;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    public static AttributeSupplier.Builder prepareAttributes() {
        return LivingEntity.createLivingAttributes();
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.singleton(ItemStack.EMPTY);
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot pSlot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot pSlot, ItemStack pStack) {

    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.LEFT;
    }

    //https://forge.gemwire.uk/wiki/Particles
    public void spawnParticles() {
        for (int i = 0; i < 2; i++) {
            double speed = .02;
            double dx = level.random.nextDouble() * 2 * speed - speed;
            double dy = level.random.nextDouble() * 2 * speed - speed;
            double dz = level.random.nextDouble() * 2 * speed - speed;
            var tmp = ParticleHelper.UNSTABLE_ENDER;
            IronsSpellbooks.LOGGER.debug("WispEntity.spawnParticles isClientSide:{}, position:{}, {} {} {}", this.level.isClientSide, this.position(), dx, dy, dz);
            level.addParticle(ParticleHelper.WISP, this.xOld - dx, this.position().y + .3, this.zOld - dz, dx, dy, dz);
            //level.addParticle(ParticleHelper.UNSTABLE_ENDER, this.getX() + dx / 2, this.getY() + dy / 2, this.getZ() + dz / 2, dx, dy, dz);
        }
    }

    @SuppressWarnings("removal")
    private final AnimationFactory factory = new AnimationFactory(this);

    @SuppressWarnings("removal")
    private final AnimationBuilder animationBuilder = new AnimationBuilder().addAnimation("hammer_swing", false);
    private final AnimationController animationController = new AnimationController(this, "controller", 0, this::predicate);

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(animationController);
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {

        if (event.getController().getAnimationState() == AnimationState.Stopped) {
            if (playSwingAnimation) {
                event.getController().setAnimation(animationBuilder);
                playSwingAnimation = false;
            }
        }

        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }
}