package mod.chiselsandbits.helpers;

import io.netty.util.internal.shaded.org.jctools.util.UnsafeAccess;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import sun.misc.Unsafe;

public class UnsafeItemStack {

    private static final long ITEM_FIELD_OFFSET;
    private static final long ITEMDAMAGE_FIELD_OFFSET;
    private static final long STACKSIZE_FIELD_OFFSET;
    private static final long ISEMPTY_FIELD_OFFSET;
    private static final long DELEGATE_FIELD_OFFSET;
    static {
        Unsafe unsafe = UnsafeAccess.UNSAFE;
        try {
            ITEM_FIELD_OFFSET = unsafe.objectFieldOffset(ItemStack.class.getDeclaredField("item"));
            ITEMDAMAGE_FIELD_OFFSET = unsafe.objectFieldOffset(ItemStack.class.getDeclaredField("itemDamage"));
            STACKSIZE_FIELD_OFFSET = unsafe.objectFieldOffset(ItemStack.class.getDeclaredField("stackSize"));
            //isEmpty
            ISEMPTY_FIELD_OFFSET = unsafe.objectFieldOffset(ItemStack.class.getDeclaredField("field_190928_g"));
            DELEGATE_FIELD_OFFSET = unsafe.objectFieldOffset(ItemStack.class.getDeclaredField("delegate"));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static ItemStack copy(ItemStack stack) {
        //getCount()
        ItemStack itemStack = create(stack.getItem(), stack.func_190916_E(), stack.getItemDamage());
        //setAnimationsToGo() and getAnimationsToGo()
        itemStack.func_190915_d(itemStack.func_190921_D());

        if (stack.getTagCompound() != null) {
            itemStack.setTagCompound(stack.getTagCompound().copy());
        }

        return itemStack;
    }

    public static ItemStack create(Item itemIn, int amount) {
        return create(itemIn, amount, 0);
    }

    public static ItemStack create(Item itemIn, int amount, int meta) {
        Unsafe unsafe = UnsafeAccess.UNSAFE;
        try {
            ItemStack instance = (ItemStack) unsafe.allocateInstance(ItemStack.class);
            unsafe.putObject(instance, ITEM_FIELD_OFFSET, itemIn);
            unsafe.putInt(instance, ITEMDAMAGE_FIELD_OFFSET, meta);
            unsafe.putInt(instance, STACKSIZE_FIELD_OFFSET, amount);
            unsafe.putObject(instance, DELEGATE_FIELD_OFFSET, itemIn.delegate);
            //isEmpty()
            unsafe.putBoolean(instance, ISEMPTY_FIELD_OFFSET, instance.func_190926_b());
            return instance;
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }
}
