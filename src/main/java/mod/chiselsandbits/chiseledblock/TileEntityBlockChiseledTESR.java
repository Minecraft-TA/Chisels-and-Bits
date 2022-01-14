package mod.chiselsandbits.chiseledblock;

import java.util.Collections;
import java.util.List;

import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.chiseledblock.data.VoxelNeighborRenderTracker;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.render.chiseledblock.tesr.TileRenderCache;
import mod.chiselsandbits.render.chiseledblock.tesr.TileRenderChunk;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.IExtendedBlockState;

public class TileEntityBlockChiseledTESR extends TileEntityBlockChiseled
{
	private TileRenderChunk renderChunk;
	private TileRenderCache singleCache;
	private int previousLightLevel = -1;

	@Override
	public boolean canRenderBreaking()
	{
		return true;
	}

	public TileRenderChunk getRenderChunk()
	{
		return renderChunk;
	}

	@Override
	public boolean hasFastRenderer()
	{
		return true;
	}

	@Override
	protected void tesrUpdate(
			final IBlockAccess access,
			final VoxelNeighborRenderTracker vns )
	{
		if ( renderChunk == null )
		{
			renderChunk = findRenderChunk( access );
			renderChunk.register( this );
		}

		renderChunk.update( null, 1 );

		final int old = previousLightLevel;
		previousLightLevel = world.getLightFromNeighborsFor( EnumSkyBlock.BLOCK, getPos() );

		if ( previousLightLevel != old )
		{
			vns.triggerUpdate();
		}

		if ( vns.isShouldUpdate() )
		{
			renderChunk.rebuild( false );
		}
	}

	private TileRenderChunk findRenderChunk(
			final IBlockAccess access )
	{
		int chunkPosX = getPos().getX();
		int chunkPosY = getPos().getY();
		int chunkPosZ = getPos().getZ();

		final int mask = ~0xf;
		chunkPosX = chunkPosX & mask;
		chunkPosY = chunkPosY & mask;
		chunkPosZ = chunkPosZ & mask;

		for ( int x = 0; x < 16; ++x )
		{
			for ( int y = 0; y < 16; ++y )
			{
				for ( int z = 0; z < 16; ++z )
				{
					final TileEntityBlockChiseled te = ModUtil.getChiseledTileEntity( access, new BlockPos( chunkPosX + x, chunkPosY + y, chunkPosZ + z ) );
					if ( te instanceof TileEntityBlockChiseledTESR )
					{
						final TileRenderChunk trc = ( (TileEntityBlockChiseledTESR) te ).renderChunk;
						if ( trc != null )
						{
							return trc;
						}
					}
				}
			}
		}

		return new TileRenderChunk();
	}

	public TileRenderCache getCache()
	{
		final TileEntityBlockChiseledTESR self = this;

		if ( singleCache == null )
		{
			singleCache = new TileRenderCache() {

				@Override
				public List<TileEntityBlockChiseledTESR> getTileList()
				{
					return Collections.singletonList( self );
				}

			};
		}

		return singleCache;
	}

	private void detatchRenderer()
	{
		if ( renderChunk != null )
		{
			renderChunk.unregister( this );
			renderChunk = null;
		}
	}

	@Override
	public void invalidate()
	{
		super.invalidate();
		detatchRenderer();
	}

	@Override
	public void onChunkUnload()
	{
		detatchRenderer();
	}

	@Override
	protected void finalize() throws Throwable
	{
		// in a perfect world this would never happen...
		detatchRenderer();
	}

	public IExtendedBlockState getTileRenderState(
			final IBlockAccess world )
	{
		return getState( true, 0, world );
	}

	@Override
	public boolean shouldRenderInPass(
			final int pass )
	{
		return true;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox()
	{
		if ( getRenderChunk() != null )
		{
			return getRenderChunk().getBounds();
		}

		return super.getRenderBoundingBox();
	}

	@Override
	public boolean isSideOpaque(
			final EnumFacing side )
	{
		return false; // since TESRs can blink out of existence never block..
	}

	@Override
	public double getMaxRenderDistanceSquared()
	{
		return ChiselsAndBits.getConfig().dynamicModelRange * ChiselsAndBits.getConfig().dynamicModelRange;
	}

	@Override
	public void completeEditOperation(
			final VoxelBlob vb )
	{
		super.completeEditOperation( vb );
		finishUpdate();
	}

	@Override
	public void finishUpdate()
	{
		if ( renderChunk != null )
		{
			if ( renderChunk.singleInstanceMode )
			{
				getCache().rebuild( true );
			}
			else
			{
				renderChunk.rebuild( true );
			}
		}
	}

}
