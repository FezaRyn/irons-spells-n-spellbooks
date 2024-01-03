package io.redspace.ironsspellbooks.item;

import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.util.MinecraftInstanceHelper;
import io.redspace.ironsspellbooks.util.SpellbookModCreativeTabs;
import io.redspace.ironsspellbooks.util.TooltipsUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Scroll extends Item implements IScroll, IContainSpells {

    public Scroll() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    private AbstractSpell getSpellFromStack(ItemStack itemStack) {
        return new SpellSlotContainer(itemStack).getSlotAtIndex(0).getSpell();
    }

    private SpellSlot getSpellSlotFromStack(ItemStack itemStack) {
        return new SpellSlotContainer(itemStack).getSlotAtIndex(0);
    }

    @Override
    public void fillItemCategory(@NotNull CreativeModeTab category, @NotNull NonNullList<ItemStack> itemStackList) {
        if (category == CreativeModeTab.TAB_SEARCH) {
            SpellRegistry.REGISTRY.get().getValues().stream()
                    .filter(AbstractSpell::isEnabled)
                    .forEach(spell -> {
                        int min = category == SpellbookModCreativeTabs.SPELL_EQUIPMENT_TAB ? spell.getMaxLevel() : spell.getMinLevel();

                        for (int i = min; i <= spell.getMaxLevel(); i++) {
                            var itemstack = new ItemStack(ItemRegistry.SCROLL.get());
                            var ssc = createSpellSlotContainer(spell, i, itemstack);
                            SpellSlotContainer.setNbtOnStack(itemstack, ssc);
                            itemStackList.add(itemstack);
                        }
                    });
        }
    }

    protected void removeScrollAfterCast(ServerPlayer serverPlayer, ItemStack stack) {
        if (!serverPlayer.isCreative()) {
            stack.shrink(1);
        }
    }

    public static SpellSlotContainer createSpellSlotContainer(AbstractSpell spell, int spellLevel, ItemStack itemStack) {
        var ssc = new SpellSlotContainer(1, CastSource.SCROLL);
        ssc.addSpellAtSlot(spell, spellLevel, 0, true, itemStack);
        return ssc;
    }

    public static boolean attemptRemoveScrollAfterCast(ServerPlayer serverPlayer) {
        ItemStack potentialScroll = MagicData.getPlayerMagicData(serverPlayer).getPlayerCastingItem();
        if (potentialScroll.getItem() instanceof Scroll scroll) {
            scroll.removeScrollAfterCast(serverPlayer, potentialScroll);
            return true;
        } else
            return false;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var spellSlot = getSpellSlotFromStack(stack);
        var spell = spellSlot.getSpell();

        if (level.isClientSide) {
            if (ClientMagicData.isCasting()) {
                return InteractionResultHolder.consume(stack);
            } else if (!ClientMagicData.getSyncedSpellData(player).isSpellLearned(spell)) {
                return InteractionResultHolder.pass(stack);
            } else {
                return InteractionResultHolder.consume(stack);
            }
        }

        if (spell.attemptInitiateCast(stack, spellSlot.getLevel(), level, player, CastSource.SCROLL, false)) {
            if (spell.getCastType() == CastType.INSTANT) {
                //TODO: i think magic manager should handle this
                removeScrollAfterCast((ServerPlayer) player, stack);
            }
            if (spell.getCastType().holdToCast()) {
                player.startUsingItem(hand);
            }
            return InteractionResultHolder.consume(stack);
        } else {
            return InteractionResultHolder.fail(stack);
        }
    }

    @Override
    public int getUseDuration(@NotNull ItemStack itemStack) {
        return 7200;//return getScrollData(itemStack).getSpell().getCastTime();
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack pStack) {
        return UseAnim.BOW;
    }

    @Override
    public void releaseUsing(@NotNull ItemStack itemStack, @NotNull Level level, @NotNull LivingEntity entity, int ticksUsed) {
        if (getSpellFromStack(itemStack).getCastType() != CastType.CONTINUOUS || getUseDuration(itemStack) - ticksUsed >= 4) {
            Utils.releaseUsingHelper(entity, itemStack, ticksUsed);
        }
        super.releaseUsing(itemStack, level, entity, ticksUsed);
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack itemStack) {
        return getSpellSlotFromStack(itemStack).getDisplayName();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack itemStack, @Nullable Level level, @NotNull List<Component> lines, @NotNull TooltipFlag flag) {
        if (MinecraftInstanceHelper.instance.player() instanceof LocalPlayer localPlayer)
            lines.addAll(TooltipsUtils.formatScrollTooltip(itemStack, localPlayer));
        super.appendHoverText(itemStack, level, lines, flag);
    }

    @Override
    public ISpellSlotContainer getSpellSlotContainer(ItemStack itemStack) {
        return new SpellSlotContainer(itemStack);
    }

    @Override
    public boolean mustBeEquipped() {
        return false;
    }

    @Override
    public boolean includeInSpellWheel() {
        return false;
    }
}
