package mod.chiselsandbits.integration.mcmultipart;

import java.util.Collection;

import mcmultipart.api.multipart.IMultipartTile;
import mcmultipart.api.multipart.MultipartOcclusionHelper;
import mcmultipart.api.world.IMultipartWorld;
import mod.chiselsandbits.api.BoxType;
import mod.chiselsandbits.chiseledblock.TileEntityBlockChiseled;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

public class ChiseledBlockPart extends TileEntityBlockChiseled implements IMultipartTile
{

	public ChiseledBlockPart()
	{
		// required for loading.
	}

	@Override
	public void setPartWorld(
			World world )
	{
		if ( world instanceof IMultipartWorld )
		{
			getTileEntity().setWorld( ( (IMultipartWorld) world ).getActualWorld() );
			return;
		}

		getTileEntity().setWorld( world );
	}

	@Override
	public boolean isBlobOccluded(
			final VoxelBlob blob )
	{
		final ChiseledBlockPart part = new ChiseledBlockPart( null );
		part.setBlob( blob );

		// get new occlusion...
		final Collection<AxisAlignedBB> selfBoxes = part.getBoxes( BoxType.OCCLUSION );

		return MultipartOcclusionHelper.testContainerBoxIntersection( getWorld(), getPos(), selfBoxes );
	}

	@Override
	protected boolean supportsSwapping()
	{
		return false;
	}

	public ChiseledBlockPart(
			final TileEntity tileEntity )
	{
		if ( tileEntity != null )
		{
			copyFrom( (TileEntityBlockChiseled) tileEntity );

			// copy pos and world data.
			setWorld( tileEntity.getWorld() );
			setPos( tileEntity.getPos() );
		}
	}

	@Override
	public TileEntity getTileEntity()
	{
		return this;
	}

}
