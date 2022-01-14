package mod.chiselsandbits.bitbag;

import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.helpers.LocalStrings;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.items.ItemBitBag;
import mod.chiselsandbits.items.ItemChiseledBit;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.Map.Entry;

public class BagInventory implements IInventory {

    // internal storage, the capability.
    BagStorage inv;

    // tmp storage, the IInventory
    ItemStack[] stackSlots;

    public BagInventory(
            final ItemStack is) {
        inv = (BagStorage) is.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        stackSlots = new ItemStack[BagStorage.BAG_STORAGE_SLOTS];

        // the cap is missing? then just make and load it ourselves.
        if (inv == null) {
            inv = new BagStorage();
            inv.stack = is;
            inv.setStorage(BagCapabilityProvider.getStorageArray(is, BagStorage.BAG_STORAGE_SLOTS * ItemBitBag.INTS_PER_BIT_TYPE));
        }

        for (int x = 0; x < stackSlots.length; ++x) {
            stackSlots[x] = ModUtil.getEmptyStack();
        }
    }

    public ItemStack getItemStack() {
        return inv.stack;
    }

    @Override
    public String getName() {
        return "container.inventory";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public ITextComponent getDisplayName() {
        return hasCustomName() ? new TextComponentString(getName()) : new TextComponentTranslation(getName(), new Object[0]);
    }

    @Override
    public int getSizeInventory() {
        return stackSlots.length;
    }

    private int getStateInSlot(
            int index) {
        final int qty = inv.contents[ItemBitBag.INTS_PER_BIT_TYPE * index + ItemBitBag.OFFSET_QUANTITY];
        final int id = inv.contents[ItemBitBag.INTS_PER_BIT_TYPE * index + ItemBitBag.OFFSET_STATE_ID];

        if (qty > 0) {
            return id;
        }

        return 0;
    }

    @Override
    public @Nonnull
    ItemStack getStackInSlot(
            final int index) {
        final int qty = inv.contents[ItemBitBag.INTS_PER_BIT_TYPE * index + ItemBitBag.OFFSET_QUANTITY];
        final int id = inv.contents[ItemBitBag.INTS_PER_BIT_TYPE * index + ItemBitBag.OFFSET_STATE_ID];

        if (ModUtil.notEmpty(stackSlots[index])) {
            final ItemStack which = ModUtil.nonNull(stackSlots[index]);
            ModUtil.setStackSize(which, qty);
            return which;
        }

        if (qty == 0 || id == 0) {
            return ModUtil.getEmptyStack();
        }

        return stackSlots[index] = ItemChiseledBit.createStack(id, qty, false);
    }

    @Override
    public ItemStack decrStackSize(
            final int index,
            int count) {
        final int qty = inv.contents[ItemBitBag.INTS_PER_BIT_TYPE * index + ItemBitBag.OFFSET_QUANTITY];
        final int id = inv.contents[ItemBitBag.INTS_PER_BIT_TYPE * index + ItemBitBag.OFFSET_STATE_ID];

        if (qty == 0 || id == 0) {
            return ModUtil.getEmptyStack();
        }

        if (count > qty) {
            count = qty;
        }

        inv.contents[ItemBitBag.INTS_PER_BIT_TYPE * index + ItemBitBag.OFFSET_QUANTITY] -= count;
        inv.onChange();

        if (ModUtil.notEmpty(stackSlots[index])) {
            ModUtil.adjustStackSize(ModUtil.nonNull(stackSlots[index]), -count);
        }

        return ItemChiseledBit.createStack(id, count, false);
    }

    @Override
    public ItemStack removeStackFromSlot(
            final int index) {
        return ModUtil.getEmptyStack();
    }

    @Override
    public void setInventorySlotContents(
            final int index,
            final ItemStack stack) {
        stackSlots[index] = ModUtil.getEmptyStack();

        if (stack != null && stack.getItem() instanceof ItemChiseledBit) {
            inv.contents[ItemBitBag.INTS_PER_BIT_TYPE * index + ItemBitBag.OFFSET_QUANTITY] = ModUtil.getStackSize(stack);
            inv.contents[ItemBitBag.INTS_PER_BIT_TYPE * index + ItemBitBag.OFFSET_STATE_ID] = ItemChiseledBit.getStackState(stack);
        } else {
            inv.contents[ItemBitBag.INTS_PER_BIT_TYPE * index + ItemBitBag.OFFSET_QUANTITY] = 0;
            inv.contents[ItemBitBag.INTS_PER_BIT_TYPE * index + ItemBitBag.OFFSET_STATE_ID] = 0;
        }

        inv.onChange();
    }

    @Override
    public int getInventoryStackLimit() {
        return ChiselsAndBits.getConfig().bagStackSize;
    }

    @Override
    public void markDirty() {
        for (int x = 0; x < getSizeInventory(); x++) {
            if (ModUtil.notEmpty(stackSlots[x])) {
                inv.contents[ItemBitBag.INTS_PER_BIT_TYPE * x + ItemBitBag.OFFSET_QUANTITY] = ModUtil.getStackSize(stackSlots[x]);
                stackSlots[x] = ModUtil.getEmptyStack();
                inv.onChange();
            }
        }
    }

    @Override
    public boolean isUsableByPlayer(
            final EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory(
            final EntityPlayer player) {
    }

    @Override
    public void closeInventory(
            final EntityPlayer player) {
    }

    @Override
    public boolean isItemValidForSlot(
            final int index,
            final ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemChiseledBit;
    }

    @Override
    public int getField(
            final int id) {
        return 0;
    }

    @Override
    public void setField(
            final int id,
            final int value) {

    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    private static class StateQtyPair {

        public StateQtyPair(
                int state,
                int qty) {
            this.qty = qty;
            this.state = state;
        }

        int qty;
        int state;
    }

    ;

    public void sort() {
        List<StateQtyPair> stacks = new ArrayList<StateQtyPair>();

        for (int x = 0; x < stackSlots.length; ++x) {
            int state = inv.contents[x * ItemBitBag.INTS_PER_BIT_TYPE + ItemBitBag.OFFSET_STATE_ID];
            int qty = inv.contents[x * ItemBitBag.INTS_PER_BIT_TYPE + ItemBitBag.OFFSET_QUANTITY];

            if (state > 0 && qty > 0) {
                stacks.add(new StateQtyPair(state, qty));
            }
        }

        stacks.sort(new Comparator<StateQtyPair>() {

            @Override
            public int compare(
                    StateQtyPair o1,
                    StateQtyPair o2) {
                if (o1.state < o2.state)
                    return 1;

                if (o1.state > o2.state)
                    return -1;

                if (o1.qty < o2.qty)
                    return 1;

                if (o1.qty > o2.qty)
                    return -1;

                return 0;
            }
        });

        for (int x = 0; x < stacks.size() - 1; x++) {
            StateQtyPair a = stacks.get(x);
            StateQtyPair b = stacks.get(x + 1);

            if (a.state == b.state) {
                if (a.qty < getInventoryStackLimit()) {
                    int shiftSize = getInventoryStackLimit() - a.qty;
                    shiftSize = Math.min(shiftSize, b.qty);

                    a.qty += shiftSize;
                    b.qty -= shiftSize;

                    if (b.qty <= 0)
                        stacks.remove(x + 1);

                    --x;
                }
            }
        }

        for (int x = 0; x < stackSlots.length; ++x) {
            int state = 0;
            int qty = 0;

            if (stacks.size() > x) {
                state = stacks.get(x).state;
                qty = stacks.get(x).qty;
            }

            inv.contents[x * ItemBitBag.INTS_PER_BIT_TYPE + ItemBitBag.OFFSET_STATE_ID] = state;
            inv.contents[x * ItemBitBag.INTS_PER_BIT_TYPE + ItemBitBag.OFFSET_QUANTITY] = qty;

            stackSlots[x] = ModUtil.getEmptyStack();
        }

        inv.onChange();
    }

    public void clear(
            final ItemStack stack) {
        for (int x = 0; x < stackSlots.length; ++x) {
            if (matches(stack, stackSlots[x])) {
                stackSlots[x] = ModUtil.getEmptyStack();
                inv.contents[x * ItemBitBag.INTS_PER_BIT_TYPE + ItemBitBag.OFFSET_STATE_ID] = 0;
                inv.contents[x * ItemBitBag.INTS_PER_BIT_TYPE + ItemBitBag.OFFSET_QUANTITY] = 0;
            }
        }

        inv.onChange();
    }

    public boolean matches(
            final ItemStack cmpStack,
            final ItemStack invStack) {
        if (ModUtil.isEmpty(cmpStack) || invStack == null) {
            return true;
        }

        return cmpStack.getItem() == invStack.getItem() && ItemStack.areItemStackTagsEqual(cmpStack, invStack);
    }

    public ItemStack restockItem(
            final ItemStack target,
            final ItemStack targetType) {
        int outSize = ModUtil.getStackSize(target);

        for (int x = getSizeInventory() - 1; x >= 0; x--) {
            if (ItemChiseledBit.sameBit(targetType, getStateInSlot(x))) {
                final ItemStack is = getStackInSlot(x);

                outSize += ModUtil.getStackSize(is);
                final int total = outSize;
                outSize = Math.min(is.getMaxStackSize(), outSize);
                final int overage = total - outSize;

                if (overage > 0) {
                    ModUtil.setStackSize(is, overage);
                } else {
                    setInventorySlotContents(x, ModUtil.getEmptyStack());
                }

                markDirty();

                if (outSize == is.getMaxStackSize()) {
                    // done!
                    break;
                }
            }
        }

        final ItemStack out = ModUtil.copy(targetType);
        ModUtil.setStackSize(out, outSize);
        return out;
    }

    public @Nonnull
    ItemStack insertItem(
            final @Nonnull ItemStack which) {
        for (int x = 0; x < getSizeInventory(); x++) {
            final ItemStack is = getStackInSlot(x);
            if (!ModUtil.isEmpty(is) && ItemChiseledBit.getStackState(which) == ItemChiseledBit.getStackState(is)) {
                ModUtil.adjustStackSize(is, ModUtil.getStackSize(which));
                final int total = ModUtil.getStackSize(is);
                ModUtil.setStackSize(is, Math.min(getInventoryStackLimit(), ModUtil.getStackSize(is)));
                final int overage = total - ModUtil.getStackSize(is);
                if (overage > 0) {
                    ModUtil.setStackSize(which, overage);
                    markDirty();
                } else {
                    markDirty();
                    return ModUtil.getEmptyStack();
                }
            } else if (ModUtil.isEmpty(is)) {
                setInventorySlotContents(x, which);
                markDirty();
                return ModUtil.getEmptyStack();
            }
        }

        return which;
    }

    public int extractBit(
            final int bitMeta,
            int total) {
        int used = 0;

        for (int index = stackSlots.length - 1; index >= 0; index--) {
            final int qty_idx = ItemBitBag.INTS_PER_BIT_TYPE * index + ItemBitBag.OFFSET_QUANTITY;

            final int qty = inv.contents[qty_idx];
            final int id = inv.contents[ItemBitBag.INTS_PER_BIT_TYPE * index + ItemBitBag.OFFSET_STATE_ID];

            if (id == bitMeta && qty > 0) {
                inv.contents[qty_idx] -= total;

                if (inv.contents[qty_idx] < 0) {
                    inv.contents[qty_idx] = 0;
                }

                inv.onChange();

                final int diff = qty - inv.contents[qty_idx];
                used += diff;
                total -= diff;

                if (0 == total) {
                    return used;
                }
            }
        }

        return used;
    }

    @SideOnly(Side.CLIENT)
    public List<String> listContents(
            final List<String> details) {
        final TreeMap<String, Integer> contents = new TreeMap<String, Integer>();

        for (int x = 0; x < getSizeInventory(); x++) {
            final ItemStack is = getStackInSlot(x);
            if (!ModUtil.isEmpty(is)) {
                final IBlockState state = ModUtil.getStateById(ItemChiseledBit.getStackState(is));
                if (state == null) {
                    continue;
                }

                final String name = ItemChiseledBit.getBitStateName(state);

                if (name != null) {
                    Integer count = contents.get(name);
                    if (count == null) {
                        count = ModUtil.getStackSize(is);
                    } else {
                        count += ModUtil.getStackSize(is);
                    }

                    contents.put(name, count);
                }
            }
        }

        if (contents.isEmpty()) {
            details.add(LocalStrings.Empty.getLocal());
        }

        final List<Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>();
        list.addAll(contents.entrySet());

        Collections.sort(list, new Comparator<Entry<String, Integer>>() {

            @Override
            public int compare(
                    final Entry<String, Integer> o1,
                    final Entry<String, Integer> o2) {
                final int y = o1.getValue();
                final int x = o2.getValue();

                return x < y ? -1 : x == y ? 0 : 1;
            }

        });

        for (final Entry<String, Integer> e : list) {
            details.add(new StringBuilder().append(e.getValue()).append(' ').append(e.getKey()).toString());
        }

        return details;
    }

    @Override
    public void clear() {
        clear(null);
    }

    @Override
    public boolean isEmpty() {
        for (final ItemStack itemstack : stackSlots) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

}
