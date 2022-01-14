package mod.chiselsandbits.items;

import mod.chiselsandbits.chiseledblock.NBTBlobConverter;
import mod.chiselsandbits.chiseledblock.TileEntityBlockChiseled;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.helpers.LocalStrings;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.interfaces.IPatternItem;
import mod.chiselsandbits.render.helpers.SimpleInstanceCache;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

public class ItemMirrorPrint extends Item implements IPatternItem
{

	public ItemMirrorPrint()
	{

	}

	SimpleInstanceCache<ItemStack, List<String>> toolTipCache = new SimpleInstanceCache<ItemStack, List<String>>( null, new ArrayList<String>() );

	@Override
	@SideOnly( Side.CLIENT )
	public void addInformation(
			final ItemStack stack,
			final World worldIn,
			final List<String> tooltip,
			final ITooltipFlag advanced )
	{
		super.addInformation( stack, worldIn, tooltip, advanced );
		ChiselsAndBits.getConfig().helpText( LocalStrings.HelpMirrorPrint, tooltip,
				ClientSide.instance.getKeyName( Minecraft.getMinecraft().gameSettings.keyBindUseItem ) );

		if ( isWritten( stack ) )
		{
			if ( ClientSide.instance.holdingShift() )
			{
				if ( toolTipCache.needsUpdate( stack ) )
				{
					final VoxelBlob blob = ModUtil.getBlobFromStack( stack, null );
					toolTipCache.updateCachedValue( blob.listContents( new ArrayList<String>() ) );
				}

				tooltip.addAll( toolTipCache.getCached() );
			}
			else
			{
				tooltip.add( LocalStrings.ShiftDetails.getLocal() );
			}
		}
	}

	@Override
	public String getTranslationKey(
			final ItemStack stack )
	{
		if ( isWritten( stack ) )
		{
			return super.getTranslationKey( stack ) + "_written";
		}

		return super.getTranslationKey( stack );
	}

	@Override
	public EnumActionResult onItemUse(
			final EntityPlayer player,
			final World world,
			final BlockPos pos,
			final EnumHand hand,
			final EnumFacing side,
			final float hitX,
			final float hitY,
			final float hitZ )
	{
		final ItemStack stack = player.getHeldItem( hand );

		if ( !player.canPlayerEdit( pos, side, stack ) )
		{
			return EnumActionResult.SUCCESS;
		}

		if ( !isWritten( stack ) )
		{
			final NBTTagCompound comp = getCompoundFromBlock( world, pos, player, side );
			if ( comp != null )
			{
				stack.setTagCompound( comp );
				return EnumActionResult.SUCCESS;
			}

			return EnumActionResult.FAIL;
		}

		return EnumActionResult.FAIL;
	}

	protected NBTTagCompound getCompoundFromBlock(
			final World world,
			final BlockPos pos,
			final EntityPlayer player,
			final EnumFacing face )
	{
		final TileEntityBlockChiseled te = ModUtil.getChiseledTileEntity( world, pos, false );

		if ( te != null )
		{
			final NBTTagCompound comp = new NBTTagCompound();
			te.writeChisleData( comp );

			final TileEntityBlockChiseled tmp = new TileEntityBlockChiseled();
			tmp.readChisleData( comp );

			final VoxelBlob bestBlob = tmp.getBlob();
			tmp.setBlob( bestBlob.mirror( face.getAxis() ) );
			tmp.writeChisleData( comp );

			comp.setByte( ModUtil.NBT_SIDE, (byte) ModUtil.getPlaceFace( player ).ordinal() );
			return comp;
		}

		return null;
	}

	@Override
	public ItemStack getPatternedItem(
			final ItemStack stack,
			final boolean wantRealItems )
	{
		if ( !isWritten( stack ) )
		{
			return null;
		}

		final NBTTagCompound tag = ModUtil.getTagCompound( stack );

		// Detect and provide full blocks if pattern solid full and solid.
		final NBTBlobConverter conv = new NBTBlobConverter();
		conv.readChisleData( tag, VoxelBlob.VERSION_ANY );

		final IBlockState blk = conv.getPrimaryBlockState();
		final ItemStack itemstack = new ItemStack( ChiselsAndBits.getBlocks().getConversionWithDefault( blk ), 1 );

		itemstack.setTagInfo( ModUtil.NBT_BLOCKENTITYTAG, tag );
		return itemstack;
	}

	@Override
	public boolean isWritten(
			final ItemStack stack )
	{
		return stack != null && stack.hasTagCompound();
	}

}
