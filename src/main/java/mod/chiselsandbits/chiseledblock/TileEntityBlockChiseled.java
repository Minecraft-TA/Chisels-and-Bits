package mod.chiselsandbits.chiseledblock;

import mod.chiselsandbits.api.*;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.chiseledblock.data.VoxelBlobStateReference;
import mod.chiselsandbits.chiseledblock.data.VoxelNeighborRenderTracker;
import mod.chiselsandbits.client.UndoTracker;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.Log;
import mod.chiselsandbits.core.api.BitAccess;
import mod.chiselsandbits.helpers.DeprecationHelper;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.integration.mcmultipart.MCMultipartProxy;
import mod.chiselsandbits.interfaces.IChiseledTileContainer;
import mod.chiselsandbits.render.chiseledblock.ChiseledBlockSmartModel;
import mod.chiselsandbits.render.chiseledblock.tesr.ChisledBlockRenderChunkTESR;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

public class TileEntityBlockChiseled extends TileEntity implements IChiseledTileContainer, IChiseledBlockTileEntity
{

	public static class TileEntityBlockChiseledDummy extends TileEntityBlockChiseled
	{

	};

	private IExtendedBlockState state;
	public IChiseledTileContainer occlusionState;

	boolean isNormalCube = false;
	int sideState = 0;
	int lightlevel = -1;

	// used to initialize light level before I can properly feed things into the
	// tile entity, half 2 of fixing inital lighting issues.
	private static ThreadLocal<Integer> localLightLevel = new ThreadLocal<Integer>();

	public TileEntityBlockChiseled()
	{

	}

	public IChiseledTileContainer getTileContainer()
	{
		if ( occlusionState != null )
		{
			return occlusionState;
		}

		return this;
	}

	@Override
	public boolean isBlobOccluded(
			final VoxelBlob blob )
	{
		return false;
	}

	@Override
	public void saveData()
	{
		super.markDirty();
	}

	@Override
	public void sendUpdate()
	{
		ModUtil.sendUpdate( world, pos );
	}

	public void copyFrom(
			final TileEntityBlockChiseled src )
	{
		state = src.state;
		isNormalCube = src.isNormalCube;
		sideState = src.sideState;
		lightlevel = src.lightlevel;
	}

	public IExtendedBlockState getBasicState()
	{
		return getState( false, 0, world );
	}

	public IExtendedBlockState getRenderState(
			final IBlockAccess access )
	{
		return getState( true, 1, access );
	}

	protected boolean supportsSwapping()
	{
		return true;
	}

	@Nonnull
	protected IExtendedBlockState getState(
			final boolean updateNeightbors,
			final int updateCost,
			final IBlockAccess access )
	{
		if ( state == null )
		{
			return (IExtendedBlockState) ChiselsAndBits.getBlocks().getChiseledDefaultState();
		}

		if ( updateNeightbors )
		{
			final boolean isDyanmic = this instanceof TileEntityBlockChiseledTESR;

			final VoxelNeighborRenderTracker vns = state.getValue( BlockChiseled.UProperty_VoxelNeighborState );
			if ( vns == null )
			{
				return state;
			}

			vns.update( isDyanmic, access, pos );
			tesrUpdate( access, vns );

			final TileEntityBlockChiseled self = this;
			if ( supportsSwapping() && vns.isAboveLimit() && !isDyanmic )
			{
				ChisledBlockRenderChunkTESR.addNextFrameTask( new Runnable() {

					@Override
					public void run()
					{
						if ( self.world != null && self.pos != null )
						{
							final TileEntity current = self.world.getTileEntity( self.pos );
							final TileEntityBlockChiseled dat = MCMultipartProxy.proxyMCMultiPart.getChiseledTileEntity( self.world, self.pos, false );

							if ( current == null || self.isInvalid() )
							{
								return;
							}

							if ( current == self )
							{
								current.invalidate();
								final TileEntityBlockChiseledTESR TESR = new TileEntityBlockChiseledTESR();
								TESR.copyFrom( self );
								self.world.removeTileEntity( self.pos );
								self.world.setTileEntity( self.pos, TESR );
								self.world.markBlockRangeForRenderUpdate( self.pos, self.pos );
								vns.unlockDynamic();
							}
							else if ( dat == self )
							{
								MCMultipartProxy.proxyMCMultiPart.convertTo( current, new TileEntityBlockChiseledTESR() );
								vns.unlockDynamic();
							}
						}
					}

				} );
			}
			else if ( supportsSwapping() && !vns.isAboveLimit() && isDyanmic )
			{
				ChisledBlockRenderChunkTESR.addNextFrameTask( new Runnable() {

					@Override
					public void run()
					{
						if ( self.world != null && self.pos != null )
						{
							final TileEntity current = self.world.getTileEntity( self.pos );
							final TileEntityBlockChiseled dat = MCMultipartProxy.proxyMCMultiPart.getChiseledTileEntity( self.world, self.pos, false );

							if ( current == null || self.isInvalid() )
							{
								return;
							}

							if ( current == self )
							{
								current.invalidate();
								final TileEntityBlockChiseled nonTesr = new TileEntityBlockChiseled();
								nonTesr.copyFrom( self );
								self.world.removeTileEntity( self.pos );
								self.world.setTileEntity( self.pos, nonTesr );
								self.world.markBlockRangeForRenderUpdate( self.pos, self.pos );
								vns.unlockDynamic();
							}
							else if ( dat == self )
							{
								MCMultipartProxy.proxyMCMultiPart.convertTo( current, new TileEntityBlockChiseled() );
								vns.unlockDynamic();
							}
						}
					}

				} );
			}
		}

		return state;
	}

	protected void tesrUpdate(
			final IBlockAccess access,
			final VoxelNeighborRenderTracker vns )
	{

	}

	public BlockBitInfo getBlockInfo(
			final Block alternative )
	{
		return BlockBitInfo.getBlockInfo( getBlockState( alternative ) );
	}

	public IBlockState getBlockState(
			final Block alternative )
	{
		final Integer stateID = getBasicState().getValue( BlockChiseled.UProperty_Primary_BlockState );

		if ( stateID != null )
		{
			final IBlockState state = ModUtil.getStateById( stateID );
			if ( state != null )
			{
				return state;
			}
		}

		return alternative.getDefaultState();
	}

	public void setState(
			final IExtendedBlockState state )
	{
		final VoxelBlobStateReference originalRef = getBasicState().getValue( BlockChiseled.UProperty_VoxelBlob );
		final VoxelBlobStateReference newRef = state.getValue( BlockChiseled.UProperty_VoxelBlob );

		this.state = state;

		if ( originalRef != null && newRef != null && !newRef.equals( originalRef ) )
		{
			final EventBlockBitPostModification bmm = new EventBlockBitPostModification( getWorld(), getPos() );
			MinecraftForge.EVENT_BUS.post( bmm );
		}
	}

	@Override
	public boolean shouldRefresh(
			final World world,
			final BlockPos pos,
			final IBlockState oldState,
			final IBlockState newState )
	{
		return oldState.getBlock() != newState.getBlock();
	}

	@Override
	public SPacketUpdateTileEntity getUpdatePacket()
	{
		final NBTTagCompound nbttagcompound = new NBTTagCompound();
		writeChisleData( nbttagcompound );

		if ( nbttagcompound.isEmpty() )
		{
			return null;
		}

		return new SPacketUpdateTileEntity( pos, 255, nbttagcompound );
	}

	@Override
	public NBTTagCompound getUpdateTag()
	{
		final NBTTagCompound nbttagcompound = new NBTTagCompound();

		nbttagcompound.setInteger( "x", pos.getX() );
		nbttagcompound.setInteger( "y", pos.getY() );
		nbttagcompound.setInteger( "z", pos.getZ() );

		writeChisleData( nbttagcompound );

		return nbttagcompound;
	}

	@Override
	public void handleUpdateTag(
			final NBTTagCompound tag )
	{
		readChisleData( tag );
	}

	@Override
	public void onDataPacket(
			final NetworkManager net,
			final SPacketUpdateTileEntity pkt )
	{
		final int oldLight = lightlevel;
		final boolean changed = readChisleData( pkt.getNbtCompound() );

		if ( world != null && changed )
		{
			world.markBlockRangeForRenderUpdate( pos, pos );
			triggerDynamicUpdates();

			// fixes lighting on placement when tile packet arrives.
			if ( oldLight != lightlevel )
			{
				world.checkLight( pos );
			}
		}
	}

	/**
	 * look at near by TESRs and re-render them.
	 */
	private void triggerDynamicUpdates()
	{
		if ( world.isRemote && state != null )
		{
			final VoxelNeighborRenderTracker vns = state.getValue( BlockChiseled.UProperty_VoxelNeighborState );

			// will it update anyway?
			if ( vns != null && vns.isDynamic() )
			{
				return;
			}

			for ( final EnumFacing f : EnumFacing.VALUES )
			{
				final BlockPos p = getPos().offset( f );
				if ( world.isBlockLoaded( p ) )
				{
					final TileEntity te = world.getTileEntity( p );
					if ( te instanceof TileEntityBlockChiseledTESR )
					{
						final TileEntityBlockChiseledTESR tesr = (TileEntityBlockChiseledTESR) te;

						if ( tesr.getRenderChunk() != null )
						{
							tesr.getRenderChunk().rebuild( false );
						}
					}
				}
			}
		}
	}

	public boolean readChisleData(
			final NBTTagCompound tag )
	{
		final NBTBlobConverter converter = new NBTBlobConverter( false, this );
		final boolean changed = converter.readChisleData( tag, VoxelBlob.VERSION_COMPACT );

		final VoxelNeighborRenderTracker vns = state.getValue( BlockChiseled.UProperty_VoxelNeighborState );

		if ( vns != null )
		{
			vns.triggerUpdate();
		}

		return changed;
	}

	public void writeChisleData(
			final NBTTagCompound tag )
	{
		new NBTBlobConverter( false, this ).writeChisleData( tag, false );
	}

	@Override
	public NBTTagCompound writeToNBT(
			final NBTTagCompound compound )
	{
		super.writeToNBT( compound );
		writeChisleData( compound );
		return compound;
	}

	@Override
	public void readFromNBT(
			final NBTTagCompound compound )
	{
		super.readFromNBT( compound );
		readChisleData( compound );
	}

	@Override
	public NBTTagCompound writeTileEntityToTag(
			final NBTTagCompound tag,
			final boolean crossWorld )
	{
		super.writeToNBT( tag );
		new NBTBlobConverter( false, this ).writeChisleData( tag, crossWorld );
		tag.setBoolean( "cw", crossWorld );
		return tag;
	}

	@Override
	public void mirror(
			final Mirror p_189668_1_ )
	{
		switch ( p_189668_1_ )
		{
			case FRONT_BACK:
				setBlob( getBlob().mirror( Axis.X ), true );
				break;
			case LEFT_RIGHT:
				setBlob( getBlob().mirror( Axis.Z ), true );
				break;
			case NONE:
			default:
				break;

		}
	}

	@Override
	public void rotate(
			final Rotation p_189667_1_ )
	{
		VoxelBlob blob = ModUtil.rotate( getBlob(), Axis.Y, p_189667_1_ );
		if ( blob != null )
		{
			setBlob( blob, true );
		}
	}

	public void fillWith(
			final IBlockState blockType )
	{
		final int ref = ModUtil.getStateId( blockType );

		sideState = 0xff;
		lightlevel = DeprecationHelper.getLightValue( blockType );
		isNormalCube = ModUtil.isNormalCube( blockType );

		IExtendedBlockState state = getBasicState()
				.withProperty( BlockChiseled.UProperty_VoxelBlob, new VoxelBlobStateReference( ModUtil.getStateId( blockType ), getPositionRandom( pos ) ) );

		final VoxelNeighborRenderTracker tracker = state.getValue( BlockChiseled.UProperty_VoxelNeighborState );

		if ( tracker == null )
		{
			state = state.withProperty( BlockChiseled.UProperty_VoxelNeighborState, new VoxelNeighborRenderTracker() );
		}
		else
		{
			tracker.isDynamic();
		}

		// required for placing bits
		if ( ref != 0 )
		{
			state = state.withProperty( BlockChiseled.UProperty_Primary_BlockState, ref );
		}

		setState( state );

		getTileContainer().saveData();
	}

	public static long getPositionRandom(
			final BlockPos pos )
	{
		if ( pos != null && FMLCommonHandler.instance().getSide() == Side.CLIENT )
		{
			return MathHelper.getPositionRandom( pos );
		}

		return 0;
	}

	public VoxelBlobStateReference getBlobStateReference()
	{
		return getBasicState().getValue( BlockChiseled.UProperty_VoxelBlob );
	}

	public VoxelBlob getBlob()
	{
		VoxelBlob vb = null;
		final VoxelBlobStateReference vbs = getBlobStateReference();

		if ( vbs != null )
		{
			vb = vbs.getVoxelBlob();

			if ( vb == null )
			{
				vb = new VoxelBlob();
				vb.fill( ModUtil.getStateId( Blocks.COBBLESTONE.getDefaultState() ) );
			}
		}
		else
		{
			vb = new VoxelBlob();
		}

		return vb;
	}

	public IBlockState getPreferedBlock()
	{
		return ChiselsAndBits.getBlocks().getConversionWithDefault( getBlockState( Blocks.STONE ) ).getDefaultState();
	}

	public void setBlob(
			final VoxelBlob vb )
	{
		setBlob( vb, true );
	}

	public boolean updateBlob(
			final NBTBlobConverter converter,
			final boolean triggerUpdates )
	{
		final int oldLV = getLightValue();
		final boolean oldNC = isNormalCube();
		final int oldSides = sideState;

		final VoxelBlobStateReference originalRef = getBasicState().getValue( BlockChiseled.UProperty_VoxelBlob );

		VoxelBlobStateReference voxelRef = null;

		sideState = converter.getSideState();
		final int b = converter.getPrimaryBlockStateID();
		lightlevel = converter.getLightValue();
		isNormalCube = converter.isNormalCube();

		try
		{
			voxelRef = converter.getVoxelRef( VoxelBlob.VERSION_COMPACT, getPositionRandom( pos ) );
		}
		catch ( final Exception e )
		{
			if ( getPos() != null )
			{
				Log.logError( "Unable to read blob at " + getPos(), e );
			}
			else
			{
				Log.logError( "Unable to read blob.", e );
			}

			voxelRef = new VoxelBlobStateReference( 0, getPositionRandom( pos ) );
		}

		IExtendedBlockState newstate = getBasicState()
				.withProperty( BlockChiseled.UProperty_Primary_BlockState, b )
				.withProperty( BlockChiseled.UProperty_VoxelBlob, voxelRef );

		final VoxelNeighborRenderTracker tracker = newstate.getValue( BlockChiseled.UProperty_VoxelNeighborState );

		if ( tracker == null )
		{
			newstate = newstate.withProperty( BlockChiseled.UProperty_VoxelNeighborState, new VoxelNeighborRenderTracker() );
		}
		else
		{
			tracker.isDynamic();
		}

		setState( newstate );

		if ( hasWorld() && triggerUpdates )
		{
			if ( oldLV != getLightValue() || oldNC != isNormalCube() )
			{
				world.checkLight( pos );

				// update block state to reflect lighting characteristics
				final IBlockState state = world.getBlockState( pos );
				if ( state.isFullCube() != isNormalCube && state.getBlock() instanceof BlockChiseled )
				{
					world.setBlockState( pos, state.withProperty( BlockChiseled.LProperty_FullBlock, isNormalCube ) );
				}
			}

			if ( oldSides != sideState )
			{
				world.notifyNeighborsOfStateChange( pos, world.getBlockState( pos ).getBlock(), false );
			}
		}

		return voxelRef != null ? !voxelRef.equals( originalRef ) : true;
	}

	public void setBlob(
			final VoxelBlob vb,
			final boolean triggerUpdates )
	{
		final Integer olv = getLightValue();
		final Boolean oldNC = isNormalCube();

		final VoxelStats common = vb.getVoxelStats();
		final float light = common.blockLight;
		final boolean nc = common.isNormalBlock;
		final int lv = Math.max( 0, Math.min( 15, (int) ( light * 15 ) ) );

		// are most of the bits in the center solid?
		final int sideFlags = vb.getSideFlags( 5, 11, 4 * 4 );

		if ( !hasWorld() )
		{
			if ( common.mostCommonState == 0 )
			{
				Integer i = getBasicState().getValue( BlockChiseled.UProperty_Primary_BlockState );
				if ( i != null )
				{
					common.mostCommonState = i;
				}
				else
				{
					// default to some other non-zero state.
					common.mostCommonState = ModUtil.getStateId( Blocks.STONE.getDefaultState() );
				}
			}

			sideState = sideFlags;
			lightlevel = lv;
			isNormalCube = nc;

			IExtendedBlockState newState = getBasicState()
					.withProperty( BlockChiseled.UProperty_VoxelBlob, new VoxelBlobStateReference( vb.blobToBytes( VoxelBlob.VERSION_COMPACT ), getPositionRandom( pos ) ) )
					.withProperty( BlockChiseled.UProperty_VoxelNeighborState, new VoxelNeighborRenderTracker() )
					.withProperty( BlockChiseled.UProperty_Primary_BlockState, common.mostCommonState );

			final VoxelNeighborRenderTracker tracker = newState.getValue( BlockChiseled.UProperty_VoxelNeighborState );

			if ( tracker == null )
			{
				newState = newState.withProperty( BlockChiseled.UProperty_VoxelNeighborState, new VoxelNeighborRenderTracker() );
			}
			else
			{
				tracker.isDynamic();
			}

			setState( newState );
			return;
		}

		if ( common.isFullBlock )
		{
			setState( getBasicState()
					.withProperty( BlockChiseled.UProperty_VoxelBlob, new VoxelBlobStateReference( common.mostCommonState, getPositionRandom( pos ) ) ) );

			final IBlockState newState = ModUtil.getStateById( common.mostCommonState );
			if ( ChiselsAndBits.getConfig().canRevertToBlock( newState ) )
			{
				if ( !MinecraftForge.EVENT_BUS.post( new EventFullBlockRestoration( world, pos, newState ) ) )
				{
					world.setBlockState( pos, newState, triggerUpdates ? 3 : 0 );
				}
			}
		}
		else if ( common.mostCommonState != 0 )
		{
			sideState = sideFlags;
			lightlevel = lv;
			isNormalCube = nc;

			setState( getBasicState()
					.withProperty( BlockChiseled.UProperty_VoxelBlob, new VoxelBlobStateReference( vb.blobToBytes( VoxelBlob.VERSION_COMPACT ), getPositionRandom( pos ) ) )
					.withProperty( BlockChiseled.UProperty_Primary_BlockState, common.mostCommonState ) );

			getTileContainer().saveData();
			getTileContainer().sendUpdate();

			// since its possible for bits to occlude parts.. update every time.
			final Block blk = world.getBlockState( pos ).getBlock();
			MCMultipartProxy.proxyMCMultiPart.triggerPartChange( world.getTileEntity( pos ) );
			// world.notifyBlockOfStateChange( pos, blk, false );

			if ( triggerUpdates )
			{
				world.notifyNeighborsOfStateChange( pos, blk, false );
			}
		}
		else
		{
			setState( getBasicState()
					.withProperty( BlockChiseled.UProperty_VoxelBlob, new VoxelBlobStateReference( 0, getPositionRandom( pos ) ) ) );

			ModUtil.removeChisledBlock( world, pos );
		}

		if ( olv != lv || oldNC != nc )
		{
			world.checkLight( pos );

			// update block state to reflect lighting characteristics
			final IBlockState state = world.getBlockState( pos );
			if ( state.isFullCube() != isNormalCube && state.getBlock() instanceof BlockChiseled )
			{
				world.setBlockState( pos, state.withProperty( BlockChiseled.LProperty_FullBlock, isNormalCube ) );
			}
		}
	}

	static private class ItemStackGeneratedCache
	{
		public ItemStackGeneratedCache(
				final ItemStack itemstack,
				final VoxelBlobStateReference blobStateReference,
				final int rotations2 )
		{
			out = itemstack == null ? null : itemstack.copy();
			ref = blobStateReference;
			rotations = rotations2;
		}

		final ItemStack out;
		final VoxelBlobStateReference ref;
		final int rotations;

		public ItemStack getItemStack()
		{
			return out == null ? null : out.copy();
		}
	};

	/**
	 * prevent mods that constantly ask for pick block from killing the
	 * client... ( looking at you waila )
	 **/
	private ItemStackGeneratedCache pickcache = null;

	public ItemStack getItemStack(
			final EntityPlayer player )
	{
		final ItemStackGeneratedCache cache = pickcache;

		if ( player != null )
		{
			EnumFacing enumfacing = ModUtil.getPlaceFace( player );
			final int rotations = ModUtil.getRotationIndex( enumfacing );

			if ( cache != null && cache.rotations == rotations && cache.ref == getBlobStateReference() && cache.out != null )
			{
				return cache.getItemStack();
			}

			VoxelBlob vb = getBlob();

			int countDown = rotations;
			while ( countDown > 0 )
			{
				countDown--;
				enumfacing = enumfacing.rotateYCCW();
				vb = vb.spin( Axis.Y );
			}

			final BitAccess ba = new BitAccess( null, null, vb, VoxelBlob.NULL_BLOB );
			final ItemStack itemstack = ba.getBitsAsItem( enumfacing, ItemType.CHISLED_BLOCK, false );

			pickcache = new ItemStackGeneratedCache( itemstack, getBlobStateReference(), rotations );
			return itemstack;
		}
		else
		{
			if ( cache != null && cache.rotations == 0 && cache.ref == getBlobStateReference() )
			{
				return cache.getItemStack();
			}

			final BitAccess ba = new BitAccess( null, null, getBlob(), VoxelBlob.NULL_BLOB );
			final ItemStack itemstack = ba.getBitsAsItem( null, ItemType.CHISLED_BLOCK, false );

			pickcache = new ItemStackGeneratedCache( itemstack, getBlobStateReference(), 0 );
			return itemstack;
		}
	}

	public boolean isNormalCube()
	{
		return isNormalCube;
	}

	public boolean isSideSolid(
			final EnumFacing side )
	{
		return ( sideState & 1 << side.ordinal() ) != 0;
	}

	public boolean isSideOpaque(
			final EnumFacing side )
	{
		if ( this.getWorld() != null && this.getWorld().isRemote )
		{
			return isInnerSideOpaque( side );
		}

		return false;
	}

	@SideOnly( Side.CLIENT )
	public boolean isInnerSideOpaque(
			final EnumFacing side )
	{
		final VoxelNeighborRenderTracker vns = state != null ? state.getValue( BlockChiseled.UProperty_VoxelNeighborState ) : null;
		if ( vns != null && vns.isDynamic() )
		{
			return false;
		}

		final Integer sideFlags = ChiseledBlockSmartModel.getSides( this );
		return ( sideFlags & 1 << side.ordinal() ) != 0;
	}

	public void completeEditOperation(
			final VoxelBlob vb )
	{
		final VoxelBlobStateReference before = getBlobStateReference();
		setBlob( vb );
		final VoxelBlobStateReference after = getBlobStateReference();

		if ( world != null )
		{
			world.markBlockRangeForRenderUpdate( pos, pos );
			triggerDynamicUpdates();
		}

		UndoTracker.getInstance().add( getWorld(), getPos(), before, after );
	}

	public void rotateBlock(
			final EnumFacing axis )
	{
		final VoxelBlob occluded = new VoxelBlob();
		MCMultipartProxy.proxyMCMultiPart.addFiller( getWorld(), getPos(), occluded );

		VoxelBlob postRotation = getBlob();
		int maxRotations = 4;
		while ( --maxRotations > 0 )
		{
			postRotation = postRotation.spin( axis.getAxis() );

			if ( occluded.canMerge( postRotation ) )
			{
				setBlob( postRotation );
				return;
			}
		}
	}

	public boolean canMerge(
			final VoxelBlob voxelBlob )
	{
		final VoxelBlob vb = getBlob();
		final IChiseledTileContainer occ = getTileContainer();

		if ( vb.canMerge( voxelBlob ) && !occ.isBlobOccluded( voxelBlob ) )
		{
			return true;
		}

		return false;
	}

	@Override
	public Collection<AxisAlignedBB> getBoxes(
			final BoxType type )
	{
		final VoxelBlobStateReference ref = getBlobStateReference();

		if ( ref != null )
		{
			return ref.getBoxes( type );
		}
		else
		{
			return Collections.emptyList();
		}
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox()
	{
		final BlockPos p = getPos();
		return new AxisAlignedBB( p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1 );
	}

	public void setNormalCube(
			final boolean b )
	{
		isNormalCube = b;
	}

	public static void setLightFromBlock(
			final IBlockState defaultState )
	{
		if ( defaultState == null )
		{
			localLightLevel.remove();
		}
		else
		{
			localLightLevel.set( DeprecationHelper.getLightValue( defaultState ) );
		}
	}

	public int getLightValue()
	{
		// first time requested, pull from local, or default to 0
		if ( lightlevel < 0 )
		{
			final Integer tmp = localLightLevel.get();
			lightlevel = tmp == null ? 0 : tmp;
		}

		return lightlevel;
	}

	@Override
	public void invalidate()
	{
		if ( world != null )
		{
			triggerDynamicUpdates();
		}
	}

	public void finishUpdate()
	{
		// nothin.
	}

	@Override
	public IBitAccess getBitAccess()
	{
		VoxelBlob mask = VoxelBlob.NULL_BLOB;

		if ( world != null )
		{
			mask = new VoxelBlob();
			MCMultipartProxy.proxyMCMultiPart.addFiller( getWorld(), getPos(), mask );
		}

		return new BitAccess( world, pos, getBlob(), mask );
	}

}
