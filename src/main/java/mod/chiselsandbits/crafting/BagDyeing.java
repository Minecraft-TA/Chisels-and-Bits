package mod.chiselsandbits.crafting;

import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.items.ItemBitBag;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

public class BagDyeing extends CustomRecipe
{

	public BagDyeing(
			ResourceLocation name )
	{
		super( name );
	}

	private static class dyed_output
	{
		ItemStack bag;
		EnumDyeColor color;

		public dyed_output(
				ItemStack bag,
				EnumDyeColor dye )
		{
			this.bag = bag;
			this.color = dye;
		}

	};

	@Override
	public boolean matches(
			InventoryCrafting inv,
			World worldIn )
	{
		return getOutput( inv ) != null;
	}

	@Override
	public ItemStack getCraftingResult(
			InventoryCrafting inv )
	{
		dyed_output output = getOutput( inv );

		if ( output != null )
		{
			return ChiselsAndBits.getItems().itemBitBag.dyeBag( output.bag, output.color );
		}

		return ModUtil.getEmptyStack();
	}

	private dyed_output getOutput(
			InventoryCrafting inv )
	{
		ItemStack bag = null;
		ItemStack dye = null;

		for ( int x = 0; x < inv.getSizeInventory(); ++x )
		{
			ItemStack is = inv.getStackInSlot( x );
			if ( is != null && !ModUtil.isEmpty( is ) )
			{
				if ( is.getItem() == Items.WATER_BUCKET || getDye( is ) != null )
				{
					if ( dye == null )
						dye = is;
					else
						return null;
				}
				else if ( is.getItem() instanceof ItemBitBag )
				{
					if ( bag == null )
						bag = is;
					else
						return null;
				}
				else
					return null;
			}
		}

		if ( bag != null && dye != null )
		{
			return new dyed_output( bag, getDye( dye ) );
		}

		return null;
	}

	private EnumDyeColor getDye(
			ItemStack is )
	{
		if ( testDye( "dyeWhite", is ) )
			return EnumDyeColor.WHITE;
		if ( testDye( "dyeOrange", is ) )
			return EnumDyeColor.ORANGE;
		if ( testDye( "dyeMagenta", is ) )
			return EnumDyeColor.MAGENTA;
		if ( testDye( "dyeLightBlue", is ) )
			return EnumDyeColor.LIGHT_BLUE;
		if ( testDye( "dyeLime", is ) )
			return EnumDyeColor.LIME;
		if ( testDye( "dyePink", is ) )
			return EnumDyeColor.PINK;
		if ( testDye( "dyeGray", is ) )
			return EnumDyeColor.GRAY;
		if ( testDye( "dyeLightGray", is ) )
			return EnumDyeColor.SILVER;
		if ( testDye( "dyeCyan", is ) )
			return EnumDyeColor.CYAN;
		if ( testDye( "dyePurple", is ) )
			return EnumDyeColor.PURPLE;
		if ( testDye( "dyeBlue", is ) )
			return EnumDyeColor.BLUE;
		if ( testDye( "dyeBrown", is ) )
			return EnumDyeColor.BROWN;
		if ( testDye( "dyeGreen", is ) )
			return EnumDyeColor.GREEN;
		if ( testDye( "dyeRed", is ) )
			return EnumDyeColor.RED;
		if ( testDye( "dyeBlack", is ) )
			return EnumDyeColor.BLACK;

		return null;
	}

	private boolean testDye(
			String string,
			ItemStack is )
	{
		if ( OreDictionary.doesOreNameExist( string ) )
		{
			int ore = OreDictionary.getOreID( string );
			int[] list = OreDictionary.getOreIDs( is );
			for ( int x = 0; x < list.length; ++x )
				if ( list[x] == ore )
					return true;
			return false;
		}
		else
			throw new RuntimeException( "Invalid dye: " + string );
	}

	@Override
	public boolean canFit(
			int p_194133_1_,
			int p_194133_2_ )
	{
		return p_194133_1_ * p_194133_2_ >= 2;
	}

	@Override
	public ItemStack getRecipeOutput()
	{
		return ModUtil.getEmptyStack();
	}

}
