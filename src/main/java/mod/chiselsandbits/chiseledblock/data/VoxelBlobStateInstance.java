package mod.chiselsandbits.chiseledblock.data;

import io.netty.buffer.Unpooled;
import mod.chiselsandbits.api.BoxType;
import mod.chiselsandbits.chiseledblock.BoxCollection;
import mod.chiselsandbits.core.Log;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.AxisAlignedBB;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.zip.InflaterInputStream;

public final class VoxelBlobStateInstance implements Comparable<VoxelBlobStateInstance>
{

	public final int hash;
	public final byte[] voxelBytes;

	private static final int HAS_FLUIDS = 1;
	private static final int HAS_SOLIDS = 2;

	// Separate fluids and solids, and use both for occlusion.
	private int generated = 0;
	private SoftReference<AxisAlignedBB[]> fluidBoxes = null;
	private SoftReference<AxisAlignedBB[]> solidBoxes = null;

	protected SoftReference<VoxelBlob> blob;

	public VoxelBlobStateInstance(
			final byte[] data )
	{
		voxelBytes = data;
		hash = Arrays.hashCode( voxelBytes );
	}

	@Override
	public boolean equals(
			final Object obj )
	{
		return compareTo( (VoxelBlobStateInstance) obj ) == 0;
	}

	@Override
	public int hashCode()
	{
		return hash;
	}

	@Override
	public int compareTo(
			final VoxelBlobStateInstance o )
	{
		if ( o == null )
		{
			return -1;
		}

		int r = Integer.compare( hash, o.hash );

		// length?
		if ( r == 0 )
		{
			r = voxelBytes.length - o.voxelBytes.length;
		}

		// for real then...
		if ( r == 0 )
		{
			for ( int x = 0; x < voxelBytes.length && r == 0; x++ )
			{
				r = voxelBytes[x] - o.voxelBytes[x];
			}
		}

		return r;
	}

	public VoxelBlob getBlob()
	{
		try
		{
			return getBlobCatchable();
		}
		catch ( final Exception e )
		{
			Log.logError( "Unable to read blob.", e );
			return new VoxelBlob();
		}
	}

	public VoxelBlob getBlobCatchable() throws Exception
	{
		VoxelBlob vb = blob == null ? null : blob.get();

		if ( vb == null )
		{
			vb = new VoxelBlob();
			vb.blobFromBytes( voxelBytes );
			blob = new SoftReference<VoxelBlob>( vb );
		}

		return new VoxelBlob( vb );
	}

	private AxisAlignedBB[] getBoxType(
			final int type )
	{
		// if they are not generated, then generate them.
		if ( ( generated & type ) == 0 )
		{
			switch ( type )
			{
				case HAS_FLUIDS:

					final VoxelBlob fluidBlob = getBlob();
					generated |= HAS_FLUIDS;

					if ( fluidBlob.filterFluids( true ) )
					{
						final AxisAlignedBB[] out = generateBoxes( fluidBlob );
						fluidBoxes = new SoftReference<AxisAlignedBB[]>( out );
						return out;
					}

					fluidBoxes = null;
					return null;

				case HAS_SOLIDS:

					final VoxelBlob solidBlob = getBlob();
					generated |= HAS_SOLIDS;

					if ( solidBlob.filterFluids( false ) )
					{
						final AxisAlignedBB[] out = generateBoxes( solidBlob );
						solidBoxes = new SoftReference<AxisAlignedBB[]>( out );
						return out;
					}

					solidBoxes = null;
					return null;

			}

		}

		// snag the boxes we want.
		AxisAlignedBB[] out = null;
		switch ( type )
		{
			case HAS_FLUIDS:

				if ( fluidBoxes == null )
				{
					return null;
				}

				out = fluidBoxes.get();
				break;

			case HAS_SOLIDS:

				if ( solidBoxes == null )
				{
					return null;
				}

				out = solidBoxes.get();
				break;
		}

		// did they expire?
		if ( out != null )
		{
			return out;
		}

		// regenerate boxes...
		generated = generated & ~type;
		return getBoxType( type );
	}

	public Collection<AxisAlignedBB> getBoxes(
			final BoxType type )
	{
		switch ( type )
		{
			case COLLISION:
				return new BoxCollection( getBoxType( HAS_SOLIDS ) );

			case OCCLUSION:
				return new BoxCollection( getBoxType( HAS_SOLIDS ), getBoxType( HAS_FLUIDS ) );

			case SWIMMING:
				return new BoxCollection( getBoxType( HAS_FLUIDS ) );

		}

		return Collections.emptyList();
	}

	private AxisAlignedBB[] generateBoxes(
			final VoxelBlob blob )
	{
		final List<AxisAlignedBB> cache = new ArrayList<AxisAlignedBB>();
		final BitOcclusionIterator boi = new BitOcclusionIterator( cache );

		while ( boi.hasNext() )
		{
			if ( boi.getNext( blob ) != 0 )
			{
				boi.add();
			}
			else
			{
				boi.drop();
			}
		}

		return cache.toArray( new AxisAlignedBB[cache.size()] );
	}

	// cache the format after reading it once.
	private int format = Integer.MIN_VALUE;

	public int getFormat()
	{
		if ( format == Integer.MIN_VALUE )
		{
			if ( voxelBytes == null || voxelBytes.length == 0 )
			{
				format = -1;
			}
			else
			{
				try
				{
					final InflaterInputStream arrayPeek = new InflaterInputStream( new ByteArrayInputStream( voxelBytes ) );
					final byte[] peekBytes = new byte[5];
					arrayPeek.read( peekBytes );

					final PacketBuffer header = new PacketBuffer( Unpooled.wrappedBuffer( peekBytes ) );
					format = header.readVarInt();
				}
				catch ( final IOException e )
				{
					format = 0;
				}
			}
		}

		return format;
	}
}
