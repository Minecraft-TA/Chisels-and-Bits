package mod.chiselsandbits.debug;

import mod.chiselsandbits.api.APIExceptions.CannotBeChiseled;
import mod.chiselsandbits.api.APIExceptions.InvalidBitItem;
import mod.chiselsandbits.api.APIExceptions.SpaceOccupied;
import mod.chiselsandbits.api.*;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.Log;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.integration.mcmultipart.MCMultipartProxy;
import mod.chiselsandbits.items.ItemChiseledBit;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map.Entry;

public abstract class DebugAction
{

	public static IChiselAndBitsAPI api;

	static enum Tests
	{
		canBeChiseled( new DebugAction.canBeChiseled() ),
		createBitItem( new DebugAction.createBitItem() ),
		getBit( new DebugAction.getBit() ),
		listContents( new DebugAction.listContents() ),
		getBitAccess( new DebugAction.getBitAccess() ),
		setBitAccess( new DebugAction.setBitAccess() ),
		isBlockChiseled( new DebugAction.isBlockChiseled() ),
		ItemTests( new DebugAction.ItemTests() ),
		Randomize( new DebugAction.Randomize() ),
		Spin( new DebugAction.Spin() ),
		Mirror( new DebugAction.Mirror() ),
		getTileClass( new DebugAction.getTileClass() ),
		occusionTest( new DebugAction.occlusionTest() ),
		queryTest( new DebugAction.queryTest() );

		final DebugAction which;

		private Tests(
				final DebugAction action )
		{
			which = action;
		}
	};

	protected static void Msg(
			final EntityPlayer player,
			final String msg )
	{
		final String side = FMLCommonHandler.instance().getEffectiveSide().name() + ": ";
		player.sendStatusMessage( new TextComponentString( side + msg ), true );
	}

	private static void apiAssert(
			final String name,
			final EntityPlayer player,
			final boolean must_be_true )
	{
		if ( must_be_true != true )
		{
			Msg( player, name + " = false" );
		}
	}

	public abstract void run(
			final @Nonnull World w,
			final @Nonnull BlockPos pos,
			final @Nonnull EnumFacing side,
			final float hitX,
			final float hitY,
			final float hitZ,
			@Nonnull EntityPlayer player );

	static class ItemTests extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			final IBitAccess access = api.createBitItem( ModUtil.getEmptyStack() );
			assert access != null;

			apiAssert( "BIT_BAG", player, api.getItemType( new ItemStack( ChiselsAndBits.getItems().itemBitBag ) ) == ItemType.BIT_BAG );
			apiAssert( "CHISEL", player, api.getItemType( new ItemStack( ChiselsAndBits.getItems().itemChiselDiamond ) ) == ItemType.CHISEL );
			apiAssert( "MIRROR_DESIGN 1", player, api.getItemType( new ItemStack( ChiselsAndBits.getItems().itemMirrorprint ) ) == ItemType.MIRROR_DESIGN );
			apiAssert( "NEGATIVE_DESIGN 1", player, api.getItemType( new ItemStack( ChiselsAndBits.getItems().itemNegativeprint ) ) == ItemType.NEGATIVE_DESIGN );
			apiAssert( "POSITIVE_DESIGN 1", player, api.getItemType( new ItemStack( ChiselsAndBits.getItems().itemPositiveprint ) ) == ItemType.POSITIVE_DESIGN );
			apiAssert( "WRENCH", player, api.getItemType( new ItemStack( ChiselsAndBits.getItems().itemWrench ) ) == ItemType.WRENCH );
			apiAssert( "CHISLED_BIT-cobblestone", player, api.getItemType( ItemChiseledBit.createStack( ModUtil.getStateId( Blocks.COBBLESTONE.getDefaultState() ), 1, true ) ) == ItemType.CHISLED_BIT );
			apiAssert( "CHISLED_BLOCK", player, api.getItemType( access.getBitsAsItem( EnumFacing.UP, ItemType.CHISLED_BLOCK, false ) ) == null );
			apiAssert( "MIRROR_DESIGN 2", player, api.getItemType( access.getBitsAsItem( null, ItemType.MIRROR_DESIGN, false ) ) == null );
			apiAssert( "NEGATIVE_DESIGN 2", player, api.getItemType( access.getBitsAsItem( null, ItemType.NEGATIVE_DESIGN, false ) ) == null );
			apiAssert( "POSITIVE_DESIGN 2", player, api.getItemType( access.getBitsAsItem( null, ItemType.POSITIVE_DESIGN, false ) ) == null );

			try
			{
				final ItemStack bitItem = api.getBitItem( Blocks.COBBLESTONE.getDefaultState() );
				final IBitBrush brush = api.createBrush( bitItem );
				access.setBitAt( 0, 0, 0, brush );
			}
			catch ( final InvalidBitItem e )
			{
				apiAssert( "createBrush/getBitItem", player, false );
			}
			catch ( final SpaceOccupied e )
			{
				apiAssert( "setBitAt", player, false );
			}

			apiAssert( "CHISLED_BLOCK 2", player, api.getItemType( access.getBitsAsItem( null, ItemType.CHISLED_BLOCK, false ) ) == ItemType.CHISLED_BLOCK );
			apiAssert( "MIRROR_DESIGN 3", player, api.getItemType( access.getBitsAsItem( null, ItemType.MIRROR_DESIGN, false ) ) == ItemType.MIRROR_DESIGN );
			apiAssert( "NEGATIVE_DESIGN 3", player, api.getItemType( access.getBitsAsItem( null, ItemType.NEGATIVE_DESIGN, false ) ) == ItemType.NEGATIVE_DESIGN );
			apiAssert( "POSITIVE_DESIGN 3", player, api.getItemType( access.getBitsAsItem( null, ItemType.POSITIVE_DESIGN, false ) ) == ItemType.POSITIVE_DESIGN );
			apiAssert( "WRENCH", player, api.getItemType( access.getBitsAsItem( null, ItemType.WRENCH, false ) ) == null );
		}

	};

	static class getTileClass extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			final TileEntity te = w.getTileEntity( pos );
			if ( te != null )
			{
				Msg( player, te.getClass().getName() );
			}
		}

	};

	static class canBeChiseled extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			Msg( player, "canBeChiseled = " + ( api.canBeChiseled( w, pos ) ? "true" : "false" ) );
		}

	};

	static class isBlockChiseled extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			Msg( player, "isBlockChiseled = " + ( api.isBlockChiseled( w, pos ) ? "true" : "false" ) );
		}

	};

	static class getBitAccess extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			final IBitLocation loc = api.getBitPos( hitX, hitY, hitZ, side, pos, true );

			try
			{
				final IBitAccess access = api.getBitAccess( w, loc.getBlockPos() );
				final IBitBrush brush = api.createBrush( api.getBitItem( Blocks.COBBLESTONE.getDefaultState() ) );

				access.setBitAt( loc.getBitX(), loc.getBitY(), loc.getBitZ(), brush );
				access.commitChanges( true );
			}
			catch ( final CannotBeChiseled e )
			{
				Log.logError( "FAIL", e );
			}
			catch ( final SpaceOccupied e )
			{
				Log.logError( "FAIL", e );
			}
			catch ( final InvalidBitItem e )
			{
				Log.logError( "FAIL", e );
			}
		}

	};

	static class queryTest extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			final IBitLocation loc = api.getBitPos( hitX, hitY, hitZ, side, pos, false );

			try
			{
				final IBitAccess access = api.getBitAccess( w, loc.getBlockPos() );

				output( player, access.queryBitRange( new BlockPos( -1, -1, -1 ), new BlockPos( 16, 16, 16 ) ) );
				output( player, access.queryBitRange( new BlockPos( 1, 1, 1 ), new BlockPos( 14, 14, 14 ) ) );
				output( player, access.queryBitRange( new BlockPos( 0, 15, 0 ), new BlockPos( 15, 15, 15 ) ) );
				output( player, access.queryBitRange( new BlockPos( 0, 15, 15 ), new BlockPos( 15, 15, 15 ) ) );
				output( player, access.queryBitRange( new BlockPos( 0, 0, 0 ), new BlockPos( 15, 15, 0 ) ) );
			}
			catch ( final CannotBeChiseled e )
			{
				Log.logError( "FAIL", e );
			}
		}

		private void output(
				final EntityPlayer player,
				final BitQueryResults queryBitRange )
		{
			Msg( player, queryBitRange.total + " = e" + queryBitRange.empty + " + s" + queryBitRange.solid + " + f" + queryBitRange.fluid );
		}

	};

	static class setBitAccess extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			final IBitLocation loc = api.getBitPos( hitX, hitY, hitZ, side, pos, false );

			try
			{
				final IBitAccess access = api.getBitAccess( w, loc.getBlockPos() );
				final IBitBrush brush = api.createBrush( ModUtil.getEmptyStack() );

				access.setBitAt( loc.getBitX(), loc.getBitY(), loc.getBitZ(), brush );
				access.commitChanges( true );
			}
			catch ( final CannotBeChiseled e )
			{
				Log.logError( "FAIL", e );
			}
			catch ( final SpaceOccupied e )
			{
				Log.logError( "FAIL", e );
			}
			catch ( final InvalidBitItem e )
			{
				Log.logError( "FAIL", e );
			}
		}

	};

	static class Randomize extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			final IBitLocation loc = api.getBitPos( hitX, hitY, hitZ, side, pos, false );

			try
			{
				final IBitAccess access = api.getBitAccess( w, loc.getBlockPos() );

				access.visitBits( new IBitVisitor() {

					@Override
					public IBitBrush visitBit(
							final int x,
							final int y,
							final int z,
							final IBitBrush currentValue )
					{
						IBitBrush bit = currentValue;
						final IBlockState state = Blocks.WOOL.getDefaultState();

						try
						{
							bit = api.createBrush( api.getBitItem( state ) );
						}
						catch ( final InvalidBitItem e )
						{
						}

						return y % 2 == 0 ? currentValue : bit;
					}
				} );

				access.commitChanges( true );
			}
			catch ( final CannotBeChiseled e )
			{
				Log.logError( "FAIL", e );
			}
		}

	};

	static class Mirror extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			final IBitLocation loc = api.getBitPos( hitX, hitY, hitZ, side, pos, false );

			try
			{
				final IBitAccess access = api.getBitAccess( w, loc.getBlockPos() );

				access.mirror( side.getAxis() );

				access.commitChanges( true );
			}
			catch ( final CannotBeChiseled e )
			{
				Log.logError( "FAIL", e );
			}
		}

	};

	static class Spin extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			final IBitLocation loc = api.getBitPos( hitX, hitY, hitZ, side, pos, false );

			try
			{
				final IBitAccess access = api.getBitAccess( w, loc.getBlockPos() );

				access.rotate( side.getAxis(), Rotation.CLOCKWISE_90 );

				access.commitChanges( true );
			}
			catch ( final CannotBeChiseled e )
			{
				Log.logError( "FAIL", e );
			}
		}

	};

	static class listContents extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			final IBitLocation loc = api.getBitPos( hitX, hitY, hitZ, side, pos, false );

			try
			{
				final HashMap<Integer, ItemStack> bucket = new HashMap<Integer, ItemStack>();
				final IBitAccess access = api.getBitAccess( w, loc.getBlockPos() );

				access.visitBits( new IBitVisitor() {

					@Override
					public IBitBrush visitBit(
							final int x,
							final int y,
							final int z,
							final IBitBrush currentValue )
					{
						if ( bucket.containsKey( currentValue.getStateID() ) )
							ModUtil.adjustStackSize( bucket.get( currentValue.getStateID() ), 1 );
						else
							bucket.put( currentValue.getStateID(), currentValue.getItemStack( 1 ) );

						return currentValue;
					}
				} );

				for ( Entry<Integer, ItemStack> is : bucket.entrySet() )
				{
					player.inventory.addItemStackToInventory( is.getValue() );
				}
			}
			catch ( final CannotBeChiseled e )
			{
				Log.logError( "FAIL", e );
			}
		}

	};

	static class getBit extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			final IBitLocation loc = api.getBitPos( hitX, hitY, hitZ, side, pos, false );

			try
			{
				final IBitAccess access = api.getBitAccess( w, loc.getBlockPos() );
				final IBitBrush brush = access.getBitAt( loc.getBitX(), loc.getBitY(), loc.getBitZ() );

				if ( brush == null )
				{
					Msg( player, "AIR!" );
				}
				else
				{
					final IBlockState state = brush.getState();
					if ( state != null )
					{
						final Block blk = state.getBlock();

						final ItemStack it = brush.getItemStack( 1 );

						if ( it != null && it.getItem() != null )
						{
							player.inventory.addItemStackToInventory( it );
						}

						player.inventory.addItemStackToInventory( new ItemStack( blk, 1, blk.getMetaFromState( state ) ) );
					}
				}
			}
			catch ( final CannotBeChiseled e )
			{
				Log.logError( "FAIL", e );
			}
		}

	};

	static class occlusionTest extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			final IBitLocation loc = api.getBitPos( hitX, hitY, hitZ, side, pos, false );

			final VoxelBlob out = new VoxelBlob();
			MCMultipartProxy.proxyMCMultiPart.addFiller( w, loc.getBlockPos(), out );

			player.sendStatusMessage( new TextComponentString( out.filled() + " blocked" ), true );
			player.sendStatusMessage( new TextComponentString( out.air() + " not-blocked" ), true );

			final boolean isMultiPart = MCMultipartProxy.proxyMCMultiPart.isMultiPartTileEntity( w, loc.getBlockPos() );
			player.sendStatusMessage( new TextComponentString( isMultiPart ? "Multipart" : "Not-Multipart" ), true );
		}

	};

	static class createBitItem extends DebugAction
	{

		@Override
		public void run(
				final World w,
				final BlockPos pos,
				final EnumFacing side,
				final float hitX,
				final float hitY,
				final float hitZ,
				final EntityPlayer player )
		{
			final IBitLocation loc = api.getBitPos( hitX, hitY, hitZ, side, pos, false );

			try
			{
				final IBitAccess access = api.getBitAccess( w, loc.getBlockPos() );

				player.inventory.addItemStackToInventory( access.getBitsAsItem( side, ItemType.CHISLED_BLOCK, false ) );
				player.inventory.addItemStackToInventory( access.getBitsAsItem( side, ItemType.MIRROR_DESIGN, false ) );
				player.inventory.addItemStackToInventory( access.getBitsAsItem( side, ItemType.NEGATIVE_DESIGN, false ) );
				player.inventory.addItemStackToInventory( access.getBitsAsItem( side, ItemType.POSITIVE_DESIGN, false ) );
			}
			catch ( final CannotBeChiseled e )
			{
				Log.logError( "FAIL", e );
			}
		}

	};

}
