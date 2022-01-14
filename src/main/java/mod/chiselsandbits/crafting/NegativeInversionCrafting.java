package mod.chiselsandbits.crafting;

import mod.chiselsandbits.chiseledblock.NBTBlobConverter;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.helpers.ModUtil;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

public class NegativeInversionCrafting extends CustomRecipe
{

	public NegativeInversionCrafting(
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

		for ( int x = 0; x < craftingInv.getSizeInventory(); x++ )
		{
			final ItemStack f = craftingInv.getStackInSlot( x );
			if ( f == null )
			{
				continue;
			}

			if ( f.getItem() == ChiselsAndBits.getItems().itemNegativeprint )
			{
				if ( f.hasTagCompound() )
				{
					if ( targetA != null )
					{
						return null;
					}

					targetA = f;
				}
				else
				{
					if ( targetB != null )
					{
						return null;
					}

					targetB = f;
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
			bestBlob.binaryReplacement( ModUtil.getStateId( Blocks.STONE.getDefaultState() ), 0 );

			tmp.setBlob( bestBlob );

			final NBTTagCompound comp = ModUtil.getTagCompound( targetA ).copy();
			tmp.writeChisleData( comp, false );

			final ItemStack outputPattern = new ItemStack( targetA.getItem() );
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
			if ( itemstack != null && itemstack.getItem() == ChiselsAndBits.getItems().itemNegativeprint && itemstack.hasTagCompound() )
			{
				ModUtil.adjustStackSize( itemstack, 1 );
			}
		}

		return aitemstack;
	}

}
