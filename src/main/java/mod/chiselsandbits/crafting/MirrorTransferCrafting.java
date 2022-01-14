package mod.chiselsandbits.crafting;

import mod.chiselsandbits.chiseledblock.NBTBlobConverter;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.helpers.ModUtil;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

public class MirrorTransferCrafting extends CustomRecipe
{

	public MirrorTransferCrafting(
			ResourceLocation name )
	{
		super( name );
	}

	@Override
	public boolean matches(
			final InventoryCrafting craftingInv,
			final World worldIn )
	{
		return analzyeCraftingInventory( craftingInv, true ) != null;
	}

	public ItemStack analzyeCraftingInventory(
			final InventoryCrafting craftingInv,
			final boolean generatePattern )
	{
		ItemStack targetA = null;
		ItemStack targetB = null;

		boolean isNegative = false;

		for ( int x = 0; x < craftingInv.getSizeInventory(); x++ )
		{
			final ItemStack f = craftingInv.getStackInSlot( x );
			if ( f == null )
			{
				continue;
			}

			if ( f.getItem() == ChiselsAndBits.getItems().itemMirrorprint )
			{
				if ( ChiselsAndBits.getItems().itemMirrorprint.isWritten( f ) )
				{
					if ( targetA != null )
					{
						return null;
					}

					targetA = f;
				}
				else
				{
					return null;
				}
			}

			else if ( f.getItem() == ChiselsAndBits.getItems().itemNegativeprint )
			{
				if ( !ChiselsAndBits.getItems().itemNegativeprint.isWritten( f ) )
				{
					if ( targetB != null )
					{
						return null;
					}

					isNegative = true;
					targetB = f;
				}
				else
				{
					return null;
				}
			}
			else if ( f.getItem() == ChiselsAndBits.getItems().itemPositiveprint )
			{
				if ( !ChiselsAndBits.getItems().itemPositiveprint.isWritten( f ) )
				{
					if ( targetB != null )
					{
						return null;
					}

					isNegative = false;
					targetB = f;
				}
				else
				{
					return null;
				}
			}
			else if ( !ModUtil.isEmpty( f ) )
			{
				return null;
			}
		}

		if ( targetA != null && targetB != null )
		{
			if ( generatePattern )
			{
				return targetA;
			}

			final NBTBlobConverter tmp = new NBTBlobConverter();
			tmp.readChisleData( targetA.getTagCompound(), VoxelBlob.VERSION_ANY );

			final VoxelBlob bestBlob = tmp.getBlob();

			if ( isNegative )
			{
				bestBlob.binaryReplacement( 0, ModUtil.getStateId( Blocks.STONE.getDefaultState() ) );
			}

			tmp.setBlob( bestBlob );

			final NBTBase copied = ModUtil.getTagCompound( targetA ).copy();
			final NBTTagCompound comp = (NBTTagCompound) copied;
			tmp.writeChisleData( comp, false );

			final ItemStack outputPattern = new ItemStack( targetB.getItem() );
			outputPattern.setTagCompound( comp );

			return outputPattern;
		}

		return null;
	}

	@Override
	public ItemStack getCraftingResult(
			final InventoryCrafting craftingInv )
	{
		return analzyeCraftingInventory( craftingInv, false );
	}

	@Override
	public boolean canFit(
			final int width,
			final int height )
	{
		return width > 1 || height > 1;
	}

	@Override
	public ItemStack getRecipeOutput()
	{
		return ModUtil.getEmptyStack(); // nope
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(
			final InventoryCrafting craftingInv )
	{
		final NonNullList<ItemStack> aitemstack = NonNullList.withSize( craftingInv.getSizeInventory(), ItemStack.EMPTY );

		for ( int i = 0; i < aitemstack.size(); ++i )
		{
			final ItemStack itemstack = craftingInv.getStackInSlot( i );
			if ( itemstack != null && itemstack.getItem() == ChiselsAndBits.getItems().itemMirrorprint && itemstack.hasTagCompound() )
			{
				ModUtil.adjustStackSize( itemstack, 1 );
			}
		}

		return aitemstack;
	}

}
