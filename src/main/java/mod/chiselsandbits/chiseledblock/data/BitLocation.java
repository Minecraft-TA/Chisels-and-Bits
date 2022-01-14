package mod.chiselsandbits.chiseledblock.data;

import mod.chiselsandbits.api.IBitLocation;
import mod.chiselsandbits.helpers.BitOperation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

import javax.annotation.Nonnull;

public class BitLocation implements IBitLocation
{
	private static final double One32nd = 0.5 / VoxelBlob.dim;

	@Nonnull
	public final BlockPos blockPos;
	public final int bitX, bitY, bitZ;

	@Override
	public BlockPos getBlockPos()
	{
		return blockPos;
	}

	@Override
	public int getBitX()
	{
		return bitX;
	}

	@Override
	public int getBitY()
	{
		return bitY;
	}

	@Override
	public int getBitZ()
	{
		return bitZ;
	}

	public int snapToValid(
			final int x )
	{
		// rounding can sometimes create -1 or 16, just snap int to the nearest
		// valid position and move on.
		return Math.min( Math.max( 0, x ), 15 );
	}

	public BitLocation(
			final RayTraceResult mop,
			final boolean absHit,
			final BitOperation type )
	{
		final BlockPos absOffset = absHit ? mop.getBlockPos() : BlockPos.ORIGIN;

		if ( !type.usePlacementOffset() )
		{
			blockPos = mop.getBlockPos();

			final double x = mop.hitVec.x - absOffset.getX() - mop.sideHit.getXOffset() * One32nd;
			final double y = mop.hitVec.y - absOffset.getY() - mop.sideHit.getYOffset() * One32nd;
			final double z = mop.hitVec.z - absOffset.getZ() - mop.sideHit.getZOffset() * One32nd;

			bitX = snapToValid( (int) Math.floor( x * VoxelBlob.dim ) );
			bitY = snapToValid( (int) Math.floor( y * VoxelBlob.dim ) );
			bitZ = snapToValid( (int) Math.floor( z * VoxelBlob.dim ) );
		}
		else
		{
			final double x = mop.hitVec.x - absOffset.getX() + mop.sideHit.getXOffset() * One32nd;
			final double y = mop.hitVec.y - absOffset.getY() + mop.sideHit.getYOffset() * One32nd;
			final double z = mop.hitVec.z - absOffset.getZ() + mop.sideHit.getZOffset() * One32nd;

			final int bitXi = (int) Math.floor( x * VoxelBlob.dim );
			final int bitYi = (int) Math.floor( y * VoxelBlob.dim );
			final int bitZi = (int) Math.floor( z * VoxelBlob.dim );

			if ( bitXi < 0 || bitYi < 0 || bitZi < 0 || bitXi >= VoxelBlob.dim || bitYi >= VoxelBlob.dim || bitZi >= VoxelBlob.dim )
			{
				blockPos = mop.getBlockPos().offset( mop.sideHit );
				bitX = snapToValid( bitXi - mop.sideHit.getXOffset() * VoxelBlob.dim );
				bitY = snapToValid( bitYi - mop.sideHit.getYOffset() * VoxelBlob.dim );
				bitZ = snapToValid( bitZi - mop.sideHit.getZOffset() * VoxelBlob.dim );
			}
			else
			{
				blockPos = mop.getBlockPos();
				bitX = snapToValid( bitXi );
				bitY = snapToValid( bitYi );
				bitZ = snapToValid( bitZi );
			}
		}
	}

	public BitLocation(
			final BlockPos pos,
			final int x,
			final int y,
			final int z )
	{
		blockPos = pos;
		bitX = x;
		bitY = y;
		bitZ = z;
	}

	public static BitLocation min(
			final BitLocation from,
			final BitLocation to )
	{
		final int bitX = Min( from.blockPos.getX(), to.blockPos.getX(), from.bitX, to.bitX );
		final int bitY = Min( from.blockPos.getY(), to.blockPos.getY(), from.bitY, to.bitY );
		final int bitZ = Min( from.blockPos.getZ(), to.blockPos.getZ(), from.bitZ, to.bitZ );

		return new BitLocation( new BlockPos(
				Math.min( from.blockPos.getX(), to.blockPos.getX() ),
				Math.min( from.blockPos.getY(), to.blockPos.getY() ),
				Math.min( from.blockPos.getZ(), to.blockPos.getZ() ) ),
				bitX, bitY, bitZ );
	}

	public static BitLocation max(
			final BitLocation from,
			final BitLocation to )
	{
		final int bitX = Max( from.blockPos.getX(), to.blockPos.getX(), from.bitX, to.bitX );
		final int bitY = Max( from.blockPos.getY(), to.blockPos.getY(), from.bitY, to.bitY );
		final int bitZ = Max( from.blockPos.getZ(), to.blockPos.getZ(), from.bitZ, to.bitZ );

		return new BitLocation( new BlockPos(
				Math.max( from.blockPos.getX(), to.blockPos.getX() ),
				Math.max( from.blockPos.getY(), to.blockPos.getY() ),
				Math.max( from.blockPos.getZ(), to.blockPos.getZ() ) ),
				bitX, bitY, bitZ );
	}

	private static int Min(
			final int x,
			final int x2,
			final int bitX2,
			final int bitX3 )
	{
		if ( x < x2 )
		{
			return bitX2;
		}
		if ( x2 == x )
		{
			return Math.min( bitX2, bitX3 );
		}

		return bitX3;
	}

	private static int Max(
			final int x,
			final int x2,
			final int bitX2,
			final int bitX3 )
	{
		if ( x > x2 )
		{
			return bitX2;
		}
		if ( x2 == x )
		{
			return Math.max( bitX2, bitX3 );
		}

		return bitX3;
	}

}
