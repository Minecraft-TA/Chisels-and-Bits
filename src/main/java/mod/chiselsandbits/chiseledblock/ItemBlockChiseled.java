package mod.chiselsandbits.chiseledblock;

import mod.chiselsandbits.api.EventBlockBitModification;
import mod.chiselsandbits.api.IBitAccess;
import mod.chiselsandbits.chiseledblock.BlockChiseled.ReplaceWithChisledValue;
import mod.chiselsandbits.chiseledblock.data.BitLocation;
import mod.chiselsandbits.chiseledblock.data.IntegerBox;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.client.UndoTracker;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.helpers.BitOperation;
import mod.chiselsandbits.helpers.DeprecationHelper;
import mod.chiselsandbits.helpers.LocalStrings;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.interfaces.IItemScrollWheel;
import mod.chiselsandbits.interfaces.IVoxelBlobItem;
import mod.chiselsandbits.items.ItemChiseledBit;
import mod.chiselsandbits.network.NetworkRouter;
import mod.chiselsandbits.network.packets.PacketAccurateSneakPlace;
import mod.chiselsandbits.network.packets.PacketAccurateSneakPlace.IItemBlockAccurate;
import mod.chiselsandbits.network.packets.PacketRotateVoxelBlob;
import mod.chiselsandbits.render.helpers.SimpleInstanceCache;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ItemBlockChiseled extends ItemBlock implements IVoxelBlobItem, IItemScrollWheel, IItemBlockAccurate
{

	SimpleInstanceCache<ItemStack, List<String>> tooltipCache = new SimpleInstanceCache<ItemStack, List<String>>( null, new ArrayList<String>() );

	public ItemBlockChiseled(
			final Block block )
	{
		super( block );
	}

	@Override
	@SideOnly( Side.CLIENT )
	public void addInformation(
			final ItemStack stack,
			final World worldIn,
			final List<String> tooltip,
			final ITooltipFlag advanced )
	{
		super.addInformation( stack, worldIn, tooltip, advanced );
		ChiselsAndBits.getConfig().helpText( LocalStrings.HelpChiseledBlock, tooltip,
				ClientSide.instance.getKeyName( Minecraft.getMinecraft().gameSettings.keyBindUseItem ),
				ClientSide.instance.getKeyName( ClientSide.getOffGridPlacementKey() ) );

		if ( stack.hasTagCompound() )
		{
			if ( ClientSide.instance.holdingShift() )
			{
				if ( tooltipCache.needsUpdate( stack ) )
				{
					final VoxelBlob blob = ModUtil.getBlobFromStack( stack, null );
					tooltipCache.updateCachedValue( blob.listContents( new ArrayList<String>() ) );
				}

				tooltip.addAll( tooltipCache.getCached() );
			}
			else
			{
				tooltip.add( LocalStrings.ShiftDetails.getLocal() );
			}
		}
	}

	@Override
	@SideOnly( Side.CLIENT )
	public boolean canPlaceBlockOnSide(
			final World worldIn,
			final BlockPos pos,
			final EnumFacing side,
			final EntityPlayer player,
			final ItemStack stack )
	{
		return canPlaceBlockHere( worldIn, pos, side, player, stack, ClientSide.offGridPlacement( player ) );
	}

	public boolean vanillaStylePlacementTest(
			final @Nonnull World worldIn,
			@Nonnull BlockPos pos,
			@Nonnull EnumFacing side,
			final EntityPlayer player,
			final ItemStack stack )
	{
		final Block block = worldIn.getBlockState( pos ).getBlock();

		if ( block == Blocks.SNOW_LAYER )
		{
			side = EnumFacing.UP;
		}
		else if ( !block.isReplaceable( worldIn, pos ) )
		{
			pos = pos.offset( side );
		}

		return worldIn.mayPlace( this.block, pos, false, side, null );
	}

	public boolean canPlaceBlockHere(
			final @Nonnull World worldIn,
			final @Nonnull BlockPos pos,
			final @Nonnull EnumFacing side,
			final EntityPlayer player,
			final ItemStack stack,
			boolean offgrid )
	{
		if ( vanillaStylePlacementTest( worldIn, pos, side, player, stack ) )
		{
			return true;
		}

		if ( offgrid )
		{
			return true;
		}

		if ( tryPlaceBlockAt( block, stack, player, worldIn, pos, side, EnumHand.MAIN_HAND, null, false ) )
		{
			return true;
		}

		return tryPlaceBlockAt( block, stack, player, worldIn, pos.offset( side ), side, EnumHand.MAIN_HAND, null, false );
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

		if ( !world.isRemote && !(player instanceof FakePlayer))
		{
			// Say it "worked", Don't do anything we'll get a better packet.
			return EnumActionResult.SUCCESS;
		}

		// send accurate packet.
		final PacketAccurateSneakPlace pasp = new PacketAccurateSneakPlace();

		pasp.hand = hand;
		pasp.pos = pos;
		pasp.side = side;
		pasp.stack = stack;
		pasp.offgrid =  ClientSide.offGridPlacement( player );
		pasp.hitX = hitX;
		pasp.hitY = hitY;
		pasp.hitZ = hitZ;

		NetworkRouter.instance.sendToServer( pasp );
		return placeItem( stack, player, world, pos, hand, side, hitX, hitY, hitZ, ClientSide.offGridPlacement( player ) );
	}

	@Override
	public EnumActionResult placeItem(
			final ItemStack stack,
			final EntityPlayer playerIn,
			final World worldIn,
			BlockPos pos,
			final EnumHand hand,
			EnumFacing side,
			final float hitX,
			final float hitY,
			final float hitZ,
			boolean offgrid )
	{
		final IBlockState state = worldIn.getBlockState( pos );
		final Block block = state.getBlock();

		if ( block == Blocks.SNOW_LAYER && state.getValue( BlockSnow.LAYERS ).intValue() < 1 )
		{
			side = EnumFacing.UP;
		}
		else
		{
			boolean canMerge = false;
			if ( stack.hasTagCompound() )
			{
				final TileEntityBlockChiseled tebc = ModUtil.getChiseledTileEntity( worldIn, pos, true );

				if ( tebc != null )
				{
					final VoxelBlob blob = ModUtil.getBlobFromStack( stack, playerIn );
					canMerge = tebc.canMerge( blob );
				}
			}

			if ( !canMerge && !offgrid && !block.isReplaceable( worldIn, pos ) )
			{
				pos = pos.offset( side );
			}
		}

		if ( ModUtil.isEmpty( stack ) )
		{
			return EnumActionResult.FAIL;
		}
		else if ( !playerIn.canPlayerEdit( pos, side, stack ) )
		{
			return EnumActionResult.FAIL;
		}
		else if ( pos.getY() == 255 && DeprecationHelper.getStateFromItem( stack ).getMaterial().isSolid() )
		{
			return EnumActionResult.FAIL;
		}
		else if ( canPlaceBlockHere( worldIn, pos, side, playerIn, stack, offgrid ) )
		{
			final int i = this.getMetadata( stack.getMetadata() );
			final IBlockState iblockstate1 = this.block.getStateForPlacement( worldIn, pos, side, hitX, hitY, hitZ, i, playerIn, hand );

			if ( placeBitBlock( stack, playerIn, worldIn, pos, side, hitX, hitY, hitZ, iblockstate1, offgrid ) )
			{
				worldIn.playSound( pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, DeprecationHelper.getSoundType( this.block ).getPlaceSound(), SoundCategory.BLOCKS,
						( DeprecationHelper.getSoundType( this.block ).getVolume() + 1.0F ) / 2.0F,
						DeprecationHelper.getSoundType( this.block ).getPitch() * 0.8F, false );
				ModUtil.adjustStackSize( stack, -1 );

				return EnumActionResult.SUCCESS;
			}

			return EnumActionResult.FAIL;
		}
		else
		{
			return EnumActionResult.FAIL;
		}
	}

	@Override
	public boolean placeBlockAt(
			final ItemStack stack,
			final EntityPlayer player,
			final World world,
			final BlockPos pos,
			final EnumFacing side,
			final float hitX,
			final float hitY,
			final float hitZ,
			final IBlockState newState )
	{
		return placeBitBlock( stack, player, world, pos, side, hitX, hitY, hitZ, newState, false );
	}

	public boolean placeBitBlock(
			final ItemStack stack,
			final EntityPlayer player,
			final World world,
			final BlockPos pos,
			final EnumFacing side,
			final float hitX,
			final float hitY,
			final float hitZ,
			final IBlockState newState,
			boolean offgrid )
	{
		if ( offgrid )
		{
			final BitLocation bl = new BitLocation( new RayTraceResult( RayTraceResult.Type.BLOCK, new Vec3d( hitX, hitY, hitZ ), side, pos ), false, BitOperation.PLACE );
			return tryPlaceBlockAt( block, stack, player, world, bl.blockPos, side, EnumHand.MAIN_HAND, new BlockPos( bl.bitX, bl.bitY, bl.bitZ ), true );
		}
		else
		{
			return tryPlaceBlockAt( block, stack, player, world, pos, side, EnumHand.MAIN_HAND, null, true );
		}
	}

	static public boolean tryPlaceBlockAt(
			final @Nonnull Block block,
			final @Nonnull ItemStack stack,
			final @Nonnull EntityPlayer player,
			final @Nonnull World world,
			@Nonnull BlockPos pos,
			final @Nonnull EnumFacing side,
			final @Nonnull EnumHand hand,
			final BlockPos partial,
			final boolean modulateWorld )
	{
		final VoxelBlob[][][] blobs = new VoxelBlob[2][2][2];

		// you can't place empty blocks...
		if ( !stack.hasTagCompound() )
		{
			return false;
		}

		final VoxelBlob source = ModUtil.getBlobFromStack( stack, player );

		final IntegerBox modelBounds = source.getBounds();
		BlockPos offset = partial == null ? new BlockPos( 0, 0, 0 ) : ModUtil.getPartialOffset( side, partial, modelBounds );

		if ( offset.getX() < 0 )
		{
			pos = pos.add( -1, 0, 0 );
			offset = offset.add( VoxelBlob.dim, 0, 0 );
		}

		if ( offset.getY() < 0 )
		{
			pos = pos.add( 0, -1, 0 );
			offset = offset.add( 0, VoxelBlob.dim, 0 );
		}

		if ( offset.getZ() < 0 )
		{
			pos = pos.add( 0, 0, -1 );
			offset = offset.add( 0, 0, VoxelBlob.dim );
		}

		for ( int x = 0; x < 2; x++ )
		{
			for ( int y = 0; y < 2; y++ )
			{
				for ( int z = 0; z < 2; z++ )
				{
					blobs[x][y][z] = source.offset( offset.getX() - source.detail * x, offset.getY() - source.detail * y, offset.getZ() - source.detail * z );
					final int solids = blobs[x][y][z].filled();
					if ( solids > 0 )
					{
						final BlockPos bp = pos.add( x, y, z );

						final EventBlockBitModification bmm = new EventBlockBitModification( world, bp, player, hand, stack, true );
						MinecraftForge.EVENT_BUS.post( bmm );

						// test permissions.
						if ( !world.isBlockModifiable( player, bp ) || bmm.isCanceled() )
						{
							return false;
						}

						if ( world.isAirBlock( bp ) || world.getBlockState( bp ).getBlock().isReplaceable( world, bp ) )
						{
							continue;
						}

						final TileEntityBlockChiseled target = ModUtil.getChiseledTileEntity( world, bp, true );
						if ( target != null )
						{
							if ( !target.canMerge( blobs[x][y][z] ) )
							{
								return false;
							}

							blobs[x][y][z] = blobs[x][y][z].merge( target.getBlob() );
							continue;
						}

						return false;
					}
				}
			}
		}

		if ( modulateWorld )
		{
			UndoTracker.getInstance().beginGroup( player );
			try
			{
				for ( int x = 0; x < 2; x++ )
				{
					for ( int y = 0; y < 2; y++ )
					{
						for ( int z = 0; z < 2; z++ )
						{
							if ( blobs[x][y][z].filled() > 0 )
							{
								final BlockPos bp = pos.add( x, y, z );
								final IBlockState state = world.getBlockState( bp );

								if ( world.getBlockState( bp ).getBlock().isReplaceable( world, bp ) )
								{
									// clear it...
									world.setBlockToAir( bp );
								}

								if ( world.isAirBlock( bp ) )
								{
									final int commonBlock = blobs[x][y][z].getVoxelStats().mostCommonState;
									ReplaceWithChisledValue rv =  BlockChiseled.replaceWithChisled( world, bp, state, commonBlock, true );
									if ( rv.success && rv.te != null )
									{
										rv.te.completeEditOperation( blobs[x][y][z] );
									}

									continue;
								}

								final TileEntityBlockChiseled target = ModUtil.getChiseledTileEntity( world, bp, true );
								if ( target != null )
								{
									target.completeEditOperation( blobs[x][y][z] );
									continue;
								}

								return false;
							}
						}
					}
				}
			}
			finally
			{
				UndoTracker.getInstance().endGroup( player );
			}
		}

		return true;
	}

	@Override
	public String getItemStackDisplayName(
			final ItemStack stack )
	{
		final NBTTagCompound comp = stack.getTagCompound();

		if ( comp != null )
		{
			final NBTTagCompound BlockEntityTag = comp.getCompoundTag( ModUtil.NBT_BLOCKENTITYTAG );
			if ( BlockEntityTag != null )
			{
				final NBTBlobConverter c = new NBTBlobConverter();
				c.readChisleData( BlockEntityTag, VoxelBlob.VERSION_ANY );

				final IBlockState state = c.getPrimaryBlockState();
				String name = ItemChiseledBit.getBitStateName( state );

				if ( name != null )
				{
					return new StringBuilder().append( super.getItemStackDisplayName( stack ) ).append( " - " ).append( name ).toString();
				}
			}
		}

		return super.getItemStackDisplayName( stack );
	}

	@Override
	public void scroll(
			final EntityPlayer player,
			final ItemStack stack,
			final int dwheel )
	{
		final PacketRotateVoxelBlob p = new PacketRotateVoxelBlob();
		p.axis = Axis.Y;
		p.rotation = dwheel > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
		NetworkRouter.instance.sendToServer( p );
	}

	@Override
	public void rotate(
			final ItemStack stack,
			final Axis axis,
			final Rotation rotation )
	{
		EnumFacing side = ModUtil.getSide( stack );

		if ( axis == Axis.Y )
		{
			switch ( rotation )
			{
				case CLOCKWISE_180:
					side = side.rotateY();
				case CLOCKWISE_90:
					side = side.rotateY();
					break;
				case COUNTERCLOCKWISE_90:
					side = side.rotateYCCW();
					break;
				default:
				case NONE:
					break;
			}
		}
		else
		{
			IBitAccess ba = ChiselsAndBits.getApi().createBitItem( stack );
			ba.rotate( axis, rotation );
			stack.setTagCompound( ba.getBitsAsItem( side, ChiselsAndBits.getApi().getItemType( stack ), false ).getTagCompound() );
		}

		ModUtil.setSide( stack, side );
	}

}
