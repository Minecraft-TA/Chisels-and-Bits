package mod.chiselsandbits.client;

import mod.chiselsandbits.chiseledblock.BlockBitInfo;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.items.ItemChiseledBit;
import mod.chiselsandbits.render.helpers.ModelUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ItemColorBits implements IItemColor
{

	@Override
	public int colorMultiplier(
			final ItemStack stack,
			final int tint )
	{
		if ( ClientSide.instance.holdingShift() )
		{
			final IBlockState state = ModUtil.getStateById( tint >> BlockColorChisled.TINT_BITS );
			final Block blk = state.getBlock();
			final Item i = Item.getItemFromBlock( blk );
			int tintValue = tint & BlockColorChisled.TINT_MASK;

			if ( i != null )
			{
				return ModelUtil.getItemStackColor( new ItemStack( i, 1, blk.getMetaFromState( state ) ), tintValue );
			}

			return 0xffffff;
		}

		final IBlockState state = ModUtil.getStateById( ItemChiseledBit.getStackState( stack ) );
		return state == null ? 0xffffffff : BlockBitInfo.getColorFor( state, tint );
	}

}
