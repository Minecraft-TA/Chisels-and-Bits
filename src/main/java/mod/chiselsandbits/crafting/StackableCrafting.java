package mod.chiselsandbits.crafting;

import javax.annotation.Nonnull;

import mod.chiselsandbits.chiseledblock.ItemBlockChiseled;
import mod.chiselsandbits.chiseledblock.NBTBlobConverter;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.helpers.ModUtil;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

public class StackableCrafting extends CustomRecipe
{

	public StackableCrafting(
			ResourceLocation name )
	{
		super( name );
	}

	@Override
	public boolean matches(
			final InventoryCrafting craftingInv,
			final World worldIn )
	{
		ItemStack target = null;

		for ( int x = 0; x < craftingInv.getSizeInventory(); x++ )
		{
			final ItemStack f = craftingInv.getStackInSlot( x );
			if ( ModUtil.isEmpty( f ) )
			{
				continue;
			}

			if ( target == null )
			{
				target = f;
			}
			else
			{
				return false;
			}
		}

		if ( target == null || !target.hasTagCompound() || !( target.getItem() instanceof ItemBlockChiseled ) )
		{
			return false;
		}

		return true;
	}

	@Override
	public ItemStack getCraftingResult(
			final InventoryCrafting craftingInv )
	{
		ItemStack target = null;

		for ( int x = 0; x < craftingInv.getSizeInventory(); x++ )
		{
			final ItemStack f = craftingInv.getStackInSlot( x );
			if ( ModUtil.isEmpty( f ) )
			{
				continue;
			}

			if ( target == null )
			{
				target = f;
			}
			else
			{
				return ModUtil.getEmptyStack();
			}
		}

		if ( target == null || !target.hasTagCompound() || !( target.getItem() instanceof ItemBlockChiseled ) )
		{
			return ModUtil.getEmptyStack();
		}

		return getSortedVersion( target );
	}

	private ItemStack getSortedVersion(
			final @Nonnull ItemStack stack )
	{
		final NBTBlobConverter tmp = new NBTBlobConverter();
		tmp.readChisleData( ModUtil.getSubCompound( stack, ModUtil.NBT_BLOCKENTITYTAG, false ), VoxelBlob.VERSION_ANY );

		VoxelBlob bestBlob = tmp.getBlob();
		byte[] bestValue = bestBlob.toLegacyByteArray();

		VoxelBlob lastBlob = bestBlob;
		for ( int x = 0; x < 34; x++ )
		{
			lastBlob = lastBlob.spin( Axis.Y );
			final byte[] aValue = lastBlob.toLegacyByteArray();

			if ( arrayCompare( bestValue, aValue ) )
			{
				bestBlob = lastBlob;
				bestValue = aValue;
			}
		}

		tmp.setBlob( bestBlob );
		return tmp.getItemStack( false );
	}

	private boolean arrayCompare(
			final byte[] bestValue,
			final byte[] aValue )
	{
		if ( aValue.length < bestValue.length )
		{
			return true;
		}

		if ( aValue.length > bestValue.length )
		{
			return false;
		}

		for ( int x = 0; x < aValue.length; x++ )
		{
			if ( aValue[x] < bestValue[x] )
			{
				return true;
			}

			if ( aValue[x] > bestValue[x] )
			{
				return false;
			}
		}

		return false;
	}

	@Override
	public boolean canFit(
			final int width,
			final int height )
	{
		return true;
	}

	@Override
	public ItemStack getRecipeOutput()
	{
		return ModUtil.getEmptyStack(); // nope
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(
			final InventoryCrafting inv )
	{
		final NonNullList<ItemStack> aitemstack = NonNullList.withSize( inv.getSizeInventory(), ItemStack.EMPTY );

		for ( int i = 0; i < aitemstack.size(); ++i )
		{
			final ItemStack itemstack = ModUtil.nonNull( inv.getStackInSlot( i ) );
			aitemstack.set( i, net.minecraftforge.common.ForgeHooks.getContainerItem( itemstack ) );
		}

		return aitemstack;
	}

}
