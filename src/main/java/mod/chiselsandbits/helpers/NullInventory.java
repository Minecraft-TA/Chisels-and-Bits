package mod.chiselsandbits.helpers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

public class NullInventory implements IInventory
{

	final int size;

	public NullInventory(
			final int size )
	{
		this.size = size;
	}

	@Override
	public String getName()
	{
		return "container.inventory";
	}

	@Override
	public boolean hasCustomName()
	{
		return false;
	}

	@Override
	public ITextComponent getDisplayName()
	{
		return hasCustomName() ? new TextComponentString( getName() ) : new TextComponentTranslation( getName(), new Object[0] );
	}

	@Override
	public int getSizeInventory()
	{
		return size;
	}

	@Override
	public ItemStack getStackInSlot(
			final int index )
	{
		return ModUtil.getEmptyStack();
	}

	@Override
	public ItemStack decrStackSize(
			final int index,
			final int count )
	{
		return ModUtil.getEmptyStack();
	}

	@Override
	public ItemStack removeStackFromSlot(
			final int index )
	{
		return ModUtil.getEmptyStack();
	}

	@Override
	public void setInventorySlotContents(
			final int index,
			final ItemStack stack )
	{

	}

	@Override
	public int getInventoryStackLimit()
	{
		return 64;
	}

	@Override
	public void markDirty()
	{

	}

	@Override
	public boolean isUsableByPlayer(
			final EntityPlayer player )
	{
		return false;
	}

	@Override
	public void openInventory(
			final EntityPlayer player )
	{

	}

	@Override
	public void closeInventory(
			final EntityPlayer player )
	{

	}

	@Override
	public boolean isItemValidForSlot(
			final int index,
			final ItemStack stack )
	{
		return false;
	}

	@Override
	public int getField(
			final int id )
	{
		return 0;
	}

	@Override
	public void setField(
			final int id,
			final int value )
	{

	}

	@Override
	public int getFieldCount()
	{
		return 0;
	}

	@Override
	public void clear()
	{

	}

	@Override
	public boolean isEmpty()
	{
		return true;
	}

}
