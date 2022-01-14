package mod.chiselsandbits.integration.mcmultipart;

import mcmultipart.RayTraceHelper;
import mcmultipart.api.container.IMultipartContainer;
import mcmultipart.api.container.IPartInfo;
import mcmultipart.api.multipart.IMultipartTile;
import mcmultipart.api.multipart.MultipartHelper;
import mcmultipart.api.slot.IPartSlot;
import mcmultipart.multipart.PartInfo;
import mcmultipart.util.MCMPWorldWrapper;
import mod.chiselsandbits.chiseledblock.TileEntityBlockChiseled;
import mod.chiselsandbits.chiseledblock.data.BitCollisionIterator;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.helpers.ModUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MCMultipart2Proxy implements IMCMultiPart
{

	@Override
	public void swapRenderIfPossible(
			final TileEntity current,
			final TileEntityBlockChiseled newTileEntity )
	{
		// TODO Auto-generated method stub
	}

	@Override
	public void removePartIfPossible(
			TileEntity te )
	{
		if ( te.getWorld() instanceof MCMPWorldWrapper )
		{
			final MCMPWorldWrapper wrapper = (MCMPWorldWrapper) te.getWorld();
			te = ModUtil.getTileEntitySafely( wrapper.getActualWorld(), te.getPos() );
		}

		if ( te instanceof IMultipartContainer && !te.getWorld().isRemote )
		{
			final IMultipartContainer container = (IMultipartContainer) te;
			container.removePart( MultiPartSlots.BITS );
		}
	}

	@Override
	public TileEntityBlockChiseled getPartIfPossible(
			final World w,
			final BlockPos pos,
			final boolean create )
	{
		final Optional<IMultipartContainer> container = MultipartHelper.getOrConvertContainer( w, pos );

		if ( container.isPresent() )
		{
			final Optional<IMultipartTile> part = container.get().getPartTile( MultiPartSlots.BITS );
			if ( part.isPresent() && part.get() instanceof TileEntityBlockChiseled )
			{
				return (TileEntityBlockChiseled) part.get();
			}

			if ( MultipartHelper.addPart( w, pos, MultiPartSlots.BITS, ChiselsAndBits.getBlocks().getChiseledDefaultState(), true ) )
			{
				if ( create && !w.isRemote )
				{
					final ChiseledBlockPart tx = new ChiseledBlockPart( null );
					tx.occlusionState = new MultipartContainerBuilder( w, pos, tx, container.get() );
					tx.setWorld( w );
					tx.setPos( pos );
					return tx;
				}
				else if ( create )
				{
					final ChiseledBlockPart tx = new ChiseledBlockPart( null );
					tx.setWorld( w );
					tx.setPos( pos );
					return tx;
				}
			}
		}

		return null;
	}

	@Override
	public void triggerPartChange(
			final TileEntity te )
	{
		if ( te instanceof IMultipartContainer && !te.getWorld().isRemote )
		{
			final Optional<IPartInfo> part = ( (IMultipartContainer) te ).get( MultiPartSlots.BITS );
			if ( part.isPresent() )
			{
				part.get().notifyChange();
			}
		}
	}

	@Override
	public boolean isMultiPart(
			final World w,
			final BlockPos pos )
	{
		return MultipartHelper.getContainer( w, pos ) != null ||
				MultipartHelper.addPart( w, pos, MultiPartSlots.BITS, ChiselsAndBits.getBlocks().getChiseledDefaultState(), true );
	}

	@Override
	public void populateBlobWithUsedSpace(
			final World w,
			final BlockPos pos,
			final VoxelBlob vb )
	{
		if ( isMultiPart( w, pos ) )
		{
			final Optional<IMultipartContainer> mc = MultipartHelper.getOrConvertContainer( w, pos );
			if ( mc.isPresent() )
			{
				final IMultipartContainer mcc = mc.get();
				Predicate<IPartSlot> ignoreMe = which -> MultiPartSlots.BITS == which;

				List<AxisAlignedBB> partBoxes = mcc.getParts().values().stream().filter( i -> !ignoreMe.test( i.getSlot() ) ).map( i -> i.getPart().getOcclusionBoxes( i ) ).flatMap( List::stream ).collect( Collectors.toList() );

				if ( partBoxes.isEmpty() )
					return;

				final BitCollisionIterator bci = new BitCollisionIterator();
				while ( bci.hasNext() )
				{
					AxisAlignedBB bitBox = bci.getBoundingBox();

					for ( AxisAlignedBB b : partBoxes )
					{
						if ( b.intersects( bitBox ) )
						{
							bci.setNext( vb, 1 );
							break;
						}
					}
				}
			}
		}
	}

	@Override
	public boolean rotate(
			final World world,
			final BlockPos pos,
			final EntityPlayer player )
	{
		final IMultipartContainer container = MultipartHelper.getContainer( world, pos ).orElse( null );
		if ( container != null )
		{
			final IBlockState state = world.getBlockState( pos );
			final Block blk = state.getBlock();

			if ( blk != null )
			{
				final Pair<Vec3d, Vec3d> atob = RayTraceHelper.getRayTraceVectors( player );
				final RayTraceResult crt = blk.collisionRayTrace( state, world, pos, atob.getKey(), atob.getValue() );

				if ( crt.hitInfo instanceof PartInfo )
				{
					// TODO : rotate the parts.
				}
			}
		}
		return false;
	}

	@Override
	public TileEntityBlockChiseled getPartFromBlockAccess(
			final IBlockAccess w,
			final BlockPos pos )
	{
		final TileEntity te = ModUtil.getTileEntitySafely( w, pos );
		IMultipartContainer container = null;

		if ( te instanceof IMultipartContainer )
		{
			container = (IMultipartContainer) te;
		}

		if ( container != null )
		{
			return (TileEntityBlockChiseled) container.getPartTile( MultiPartSlots.BITS ).orElse( null );
		}

		return null;
	}

}
