package mod.chiselsandbits.chiseledblock.data;

import io.netty.buffer.Unpooled;
import mod.chiselsandbits.api.StateCount;
import mod.chiselsandbits.api.VoxelStats;
import mod.chiselsandbits.chiseledblock.BlockBitInfo;
import mod.chiselsandbits.chiseledblock.serialization.*;
import mod.chiselsandbits.client.culling.ICullTest;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.Log;
import mod.chiselsandbits.helpers.DeprecationHelper;
import mod.chiselsandbits.helpers.IVoxelSrc;
import mod.chiselsandbits.helpers.LocalStrings;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.items.ItemChiseledBit;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.EnumFacing.AxisDirection;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.Map.Entry;
import java.util.zip.*;

public final class VoxelBlob implements IVoxelSrc
{

	private static final BitSet fluidFilterState;
	private static final Map<BlockRenderLayer, BitSet> layerFilters;

	static
	{
		fluidFilterState = new BitSet( 256 );

		if ( FMLCommonHandler.instance().getSide() == Side.CLIENT )
		{
			layerFilters = new EnumMap<BlockRenderLayer, BitSet>( BlockRenderLayer.class );
		}
		else
		{
			layerFilters = null;
		}

		clearCache();
	}

	public static synchronized void clearCache()
	{
		fluidFilterState.clear();

		for ( final Iterator<Block> it = Block.REGISTRY.iterator(); it.hasNext(); )
		{
			final Block block = it.next();
			final int blockId = Block.REGISTRY.getIDForObject( block );

			if ( BlockBitInfo.getFluidFromBlock( block ) != null )
			{
				fluidFilterState.set( blockId );
			}
		}

		if ( FMLCommonHandler.instance().getSide() == Side.CLIENT )
		{
			updateCacheClient();
			ModUtil.cacheFastStates();
		}
	}

	private static void updateCacheClient()
	{
		layerFilters.clear();

		final Map<BlockRenderLayer, BitSet> layerFilters = VoxelBlob.layerFilters;
		final BlockRenderLayer[] layers = BlockRenderLayer.values();

		for ( final BlockRenderLayer layer : layers )
		{
			layerFilters.put( layer, new BitSet( 4096 ) );
		}

		for ( final Iterator<Block> it = Block.REGISTRY.iterator(); it.hasNext(); )
		{
			final Block block = it.next();

			for ( final IBlockState state : block.getBlockState().getValidStates() )
			{
				final int id = ModUtil.getStateId( state );
				if ( state == null || state.getBlock() != block )
				{
					// reverse mapping is broken, so just skip over this state.
					continue;
				}

				for ( final BlockRenderLayer layer : layers )
				{
					if ( block.canRenderInLayer( state, layer ) )
					{
						layerFilters.get( layer ).set( id );
					}
				}
			}
		}
	}

	static final int SHORT_BYTES = Short.SIZE / 8;

	public final static int dim = 16;
	public final static int dim2 = dim * dim;
	public final static int full_size = dim2 * dim;

	public final static int dim_minus_one = dim - 1;

	private final static int array_size = full_size;

	public static VoxelBlob NULL_BLOB = new VoxelBlob();

	private final int[] values = new int[array_size];

	public int detail = dim;

	public VoxelBlob()
	{
		// nothing specific here...
	}

	@Override
	public boolean equals(
			final Object obj )
	{
		if ( obj instanceof VoxelBlob )
		{
			final VoxelBlob a = (VoxelBlob) obj;
			return Arrays.equals( a.values, values );
		}

		return false;
	}

	public VoxelBlob(
			final VoxelBlob vb )
	{
		for ( int x = 0; x < values.length; ++x )
		{
			values[x] = vb.values[x];
		}
	}

	public boolean canMerge(
			final VoxelBlob second )
	{
		final int sv[] = second.values;

		for ( int x = 0; x < values.length; ++x )
		{
			if ( values[x] != 0 && sv[x] != 0 )
			{
				return false;
			}
		}

		return true;
	}

	public VoxelBlob merge(
			final VoxelBlob second )
	{
		final VoxelBlob out = new VoxelBlob();

		final int secondValues[] = second.values;
		final int ov[] = out.values;

		for ( int x = 0; x < values.length; ++x )
		{
			final int firstValue = values[x];
			ov[x] = firstValue == 0 ? secondValues[x] : firstValue;
		}

		return out;
	}

	public VoxelBlob mirror(
			final Axis axis )
	{
		final VoxelBlob out = new VoxelBlob();

		final BitIterator bi = new BitIterator();
		while ( bi.hasNext() )
		{
			if ( bi.getNext( this ) != 0 )
			{
				switch ( axis )
				{
					case X:
						out.set( dim_minus_one - bi.x, bi.y, bi.z, bi.getNext( this ) );
						break;
					case Y:
						out.set( bi.x, dim_minus_one - bi.y, bi.z, bi.getNext( this ) );
						break;
					case Z:
						out.set( bi.x, bi.y, dim_minus_one - bi.z, bi.getNext( this ) );
						break;
					default:
						throw new NullPointerException();
				}
			}
		}

		return out;
	}

	public BlockPos getCenter()
	{
		boolean found = false;
		int min_x = 0, min_y = 0, min_z = 0;
		int max_x = 0, max_y = 0, max_z = 0;

		final BitIterator bi = new BitIterator();
		while ( bi.hasNext() )
		{
			if ( bi.getNext( this ) != 0 )
			{
				if ( found )
				{
					min_x = Math.min( min_x, bi.x );
					min_y = Math.min( min_y, bi.y );
					min_z = Math.min( min_z, bi.z );

					max_x = Math.max( max_x, bi.x );
					max_y = Math.max( max_y, bi.y );
					max_z = Math.max( max_z, bi.z );
				}
				else
				{
					found = true;

					min_x = bi.x;
					min_y = bi.y;
					min_z = bi.z;

					max_x = bi.x;
					max_y = bi.y;
					max_z = bi.z;
				}
			}
		}

		return found ? new BlockPos( ( min_x + max_x ) / 2, ( min_y + max_y ) / 2, ( min_z + max_z ) / 2 ) : null;
	}

	public IntegerBox getBounds()
	{
		boolean found = false;
		int min_x = 0, min_y = 0, min_z = 0;
		int max_x = 0, max_y = 0, max_z = 0;

		final BitIterator bi = new BitIterator();
		while ( bi.hasNext() )
		{
			if ( bi.getNext( this ) != 0 )
			{
				if ( found )
				{
					min_x = Math.min( min_x, bi.x );
					min_y = Math.min( min_y, bi.y );
					min_z = Math.min( min_z, bi.z );

					max_x = Math.max( max_x, bi.x );
					max_y = Math.max( max_y, bi.y );
					max_z = Math.max( max_z, bi.z );
				}
				else
				{
					found = true;

					min_x = bi.x;
					min_y = bi.y;
					min_z = bi.z;

					max_x = bi.x;
					max_y = bi.y;
					max_z = bi.z;
				}
			}
		}

		return found ? new IntegerBox( min_x, min_y, min_z, max_x, max_y, max_z ) : null;
	}

	public VoxelBlob spin(
			final Axis axis )
	{
		final VoxelBlob d = new VoxelBlob();

		/*
		 * Rotate by -90 Degrees: x' = y y' = - x
		 */

		final BitIterator bi = new BitIterator();
		while ( bi.hasNext() )
		{
			switch ( axis )
			{
				case X:
					d.set( bi.x, dim_minus_one - bi.z, bi.y, bi.getNext( this ) );
					break;
				case Y:
					d.set( bi.z, bi.y, dim_minus_one - bi.x, bi.getNext( this ) );
					break;
				case Z:
					d.set( dim_minus_one - bi.y, bi.x, bi.z, bi.getNext( this ) );
					break;
				default:
					throw new NullPointerException();
			}
		}

		return d;
	}

	public void fill(
			final int value )
	{
		for ( int x = 0; x < array_size; x++ )
		{
			values[x] = value;
		}
	}

	public void fill(
			final VoxelBlob src )
	{
		for ( int x = 0; x < array_size; x++ )
		{
			values[x] = src.values[x];
		}
	}

	public void clear()
	{
		fill( 0 );
	}

	public int air()
	{
		int p = 0;

		for ( int x = 0; x < array_size; x++ )
		{
			if ( values[x] == 0 )
			{
				p++;
			}
		}

		return p;
	}

	public void binaryReplacement(
			final int airReplacement,
			final int solidReplacement )
	{
		for ( int x = 0; x < array_size; x++ )
		{
			values[x] = values[x] == 0 ? airReplacement : solidReplacement;
		}
	}

	public int filled()
	{
		int p = 0;

		for ( int x = 0; x < array_size; x++ )
		{
			if ( values[x] != 0 )
			{
				p++;
			}
		}

		return p;
	}

	protected int getBit(
			final int offset )
	{
		return values[offset];
	}

	protected void putBit(
			final int offset,
			final int newValue )
	{
		values[offset] = newValue;
	}

	public int get(
			final int x,
			final int y,
			final int z )
	{
		return getBit( x | y << 4 | z << 8 );
	}

	public VoxelType getVoxelType(
			final int x,
			final int y,
			final int z )
	{
		return BlockBitInfo.getTypeFromStateID( get( x, y, z ) );
	}

	public void set(
			final int x,
			final int y,
			final int z,
			final int value )
	{
		putBit( x | y << 4 | z << 8, value );
	}

	public void clear(
			final int x,
			final int y,
			final int z )
	{
		putBit( x | y << 4 | z << 8, 0 );
	}

	private void legacyRead(
			final ByteArrayInputStream o ) throws IOException
	{
		final GZIPInputStream w = new GZIPInputStream( o );
		final ByteBuffer bb = ByteBuffer.allocate( values.length * SHORT_BYTES );

		w.read( bb.array() );
		final ShortBuffer src = bb.asShortBuffer();

		for ( int x = 0; x < array_size; x++ )
		{
			values[x] = fixShorts( src.get() );
		}

		w.close();
	}

	private int fixShorts(
			final short s )
	{
		return s & 0xffff;
	}

	private void legacyWrite(
			final ByteArrayOutputStream o )
	{
		try
		{
			final GZIPOutputStream w = new GZIPOutputStream( o );

			final ByteBuffer bb = ByteBuffer.allocate( values.length * SHORT_BYTES );
			final ShortBuffer sb = bb.asShortBuffer();

			for ( int x = 0; x < array_size; x++ )
			{
				sb.put( (short) values[x] );
			}

			w.write( bb.array() );

			w.finish();
			w.close();

			o.close();
		}
		catch ( final IOException e )
		{
			Log.logError( "Unable to write blob.", e );
			throw new RuntimeException( e );
		}
	}

	public byte[] toLegacyByteArray()
	{
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		legacyWrite( out );
		return out.toByteArray();
	}

	public void fromLegacyByteArray(
			final byte[] i ) throws IOException
	{
		final ByteArrayInputStream out = new ByteArrayInputStream( i );
		legacyRead( out );
	}

	@Override
	public int getSafe(
			final int x,
			final int y,
			final int z )
	{
		if ( x >= 0 && x < dim && y >= 0 && y < dim && z >= 0 && z < dim )
		{
			return get( x, y, z );
		}

		return 0;
	}

	public static class VisibleFace
	{
		public boolean isEdge;
		public boolean visibleFace;
		public int state;
	};

	public void visibleFace(
			final EnumFacing face,
			int x,
			int y,
			int z,
			final VisibleFace dest,
			final VoxelBlob secondBlob,
			final ICullTest cullVisTest )
	{
		final int mySpot = get( x, y, z );
		dest.state = mySpot;

		x += face.getXOffset();
		y += face.getYOffset();
		z += face.getZOffset();

		if ( x >= 0 && x < dim && y >= 0 && y < dim && z >= 0 && z < dim )
		{
			dest.isEdge = false;
			dest.visibleFace = cullVisTest.isVisible( mySpot, get( x, y, z ) );
		}
		else if ( secondBlob != null )
		{
			dest.isEdge = true;
			dest.visibleFace = cullVisTest.isVisible( mySpot, secondBlob.get( x - face.getXOffset() * dim, y - face.getYOffset() * dim, z - face.getZOffset() * dim ) );
		}
		else
		{
			dest.isEdge = true;
			dest.visibleFace = mySpot != 0;
		}
	}

	public Map<Integer, Integer> getBlockSums()
	{
		final Map<Integer, Integer> counts = new HashMap<Integer, Integer>();

		int lastType = values[0];
		int firstOfType = 0;

		for ( int x = 1; x < array_size; x++ )
		{
			final int v = values[x];

			if ( lastType != v )
			{
				final Integer sumx = counts.get( lastType );

				if ( sumx == null )
				{
					counts.put( lastType, x - firstOfType );
				}
				else
				{
					counts.put( lastType, sumx + ( x - firstOfType ) );
				}

				// new count.
				firstOfType = x;
				lastType = v;
			}
		}

		final Integer sumx = counts.get( lastType );

		if ( sumx == null )
		{
			counts.put( lastType, array_size - firstOfType );
		}
		else
		{
			counts.put( lastType, sumx + ( array_size - firstOfType ) );
		}

		return counts;
	}

	public List<StateCount> getStateCounts()
	{
		final Map<Integer, Integer> count = getBlockSums();

		final List<StateCount> out;
		out = new ArrayList<StateCount>( count.size() );

		for ( final Entry<Integer, Integer> o : count.entrySet() )
		{
			out.add( new StateCount( o.getKey(), o.getValue() ) );
		}
		return out;
	}

	public VoxelStats getVoxelStats()
	{
		final VoxelStats cb = new VoxelStats();
		cb.isNormalBlock = true;

		int nonAirBits = 0;
		for ( final Entry<Integer, Integer> o : getBlockSums().entrySet() )
		{
			final int quantity = o.getValue();
			final int r = o.getKey();

			if ( quantity > cb.mostCommonStateTotal && r != 0 )
			{
				cb.mostCommonState = r;
				cb.mostCommonStateTotal = quantity;
			}

			final IBlockState state = ModUtil.getStateById( r );
			if ( state != null && r != 0 )
			{
				nonAirBits += quantity;
				cb.isNormalBlock = cb.isNormalBlock && ModUtil.isNormalCube( state );
				cb.blockLight += quantity * DeprecationHelper.getLightValue( state );
			}
		}

		cb.isFullBlock = cb.mostCommonStateTotal == array_size;
		cb.isNormalBlock = cb.isNormalBlock && array_size == nonAirBits;

		final float light_size = ChiselsAndBits.getConfig().bitLightPercentage * array_size * 15.0f / 100.0f;
		cb.blockLight = cb.blockLight / light_size;

		return cb;
	}

	public VoxelBlob offset(
			final int xx,
			final int yy,
			final int zz )
	{
		final VoxelBlob out = new VoxelBlob();

		for ( int z = 0; z < dim; z++ )
		{
			for ( int y = 0; y < dim; y++ )
			{
				for ( int x = 0; x < dim; x++ )
				{
					out.set( x, y, z, getSafe( x - xx, y - yy, z - zz ) );
				}
			}
		}

		return out;
	}

	@SideOnly( Side.CLIENT )
	public List<String> listContents(
			final List<String> details )
	{
		final HashMap<Integer, Integer> states = new HashMap<Integer, Integer>();
		final HashMap<String, Integer> contents = new HashMap<String, Integer>();

		final BitIterator bi = new BitIterator();
		while ( bi.hasNext() )
		{
			final int state = bi.getNext( this );
			if ( state == 0 )
			{
				continue;
			}

			Integer count = states.get( state );

			if ( count == null )
			{
				count = 1;
			}
			else
			{
				count++;
			}

			states.put( state, count );
		}

		for ( final Entry<Integer, Integer> e : states.entrySet() )
		{
			final String name = ItemChiseledBit.getBitTypeName( ItemChiseledBit.createStack( e.getKey(), 1, false ) );

			if ( name == null )
			{
				continue;
			}

			Integer count = contents.get( name );

			if ( count == null )
			{
				count = e.getValue();
			}
			else
			{
				count += e.getValue();
			}

			contents.put( name, count );
		}

		if ( contents.isEmpty() )
		{
			details.add( LocalStrings.Empty.getLocal() );
		}

		for ( final Entry<String, Integer> e : contents.entrySet() )
		{
			details.add( new StringBuilder().append( e.getValue() ).append( ' ' ).append( e.getKey() ).toString() );
		}

		return details;
	}

	public int getSideFlags(
			final int minRange,
			final int maxRange,
			final int totalRequired )
	{
		int output = 0x00;

		for ( final EnumFacing face : EnumFacing.VALUES )
		{
			final int edge = face.getAxisDirection() == AxisDirection.POSITIVE ? 15 : 0;
			int required = totalRequired;

			switch ( face.getAxis() )
			{
				case X:
					for ( int z = minRange; z <= maxRange; z++ )
					{
						for ( int y = minRange; y <= maxRange; y++ )
						{
							if ( getVoxelType( edge, y, z ) == VoxelType.SOLID )
							{
								required--;
							}
						}
					}
					break;
				case Y:
					for ( int z = minRange; z <= maxRange; z++ )
					{
						for ( int x = minRange; x <= maxRange; x++ )
						{
							if ( getVoxelType( x, edge, z ) == VoxelType.SOLID )
							{
								required--;
							}
						}
					}
					break;
				case Z:
					for ( int y = minRange; y <= maxRange; y++ )
					{
						for ( int x = minRange; x <= maxRange; x++ )
						{
							if ( getVoxelType( x, y, edge ) == VoxelType.SOLID )
							{
								required--;
							}
						}
					}
					break;
				default:
					throw new NullPointerException();
			}

			if ( required <= 0 )
			{
				output |= 1 << face.ordinal();
			}
		}

		return output;
	}

	public static boolean isFluid(
			final int ref )
	{
		return fluidFilterState.get( ref & 0xffff );
	}

	public boolean filterFluids(
			final boolean wantsFluids )
	{
		boolean hasValues = false;

		for ( int x = 0; x < array_size; x++ )
		{
			final int ref = values[x];
			if ( ref == 0 )
			{
				continue;
			}

			if ( fluidFilterState.get( ref & 0xffff ) != wantsFluids )
			{
				values[x] = 0;
			}
			else
			{
				hasValues = true;
			}
		}

		return hasValues;
	}

	public boolean filter(
			final BlockRenderLayer layer )
	{
		final BitSet layerFilterState = layerFilters.get( layer );
		boolean hasValues = false;

		for ( int x = 0; x < array_size; x++ )
		{
			final int ref = values[x];
			if ( ref == 0 )
			{
				continue;
			}

			if ( !layerFilterState.get( ref ) )
			{
				values[x] = 0;
			}
			else
			{
				hasValues = true;
			}
		}

		return hasValues;
	}

	public static final int VERSION_ANY = -1;
	public static final int VERSION_COMPACT = 0;
	public static final int VERSION_CROSSWORLD_LEGACY = 1; // stored meta.
	public static final int VERSION_CROSSWORLD = 2;

	public void blobFromBytes(
			final byte[] bytes ) throws IOException
	{
		final ByteArrayInputStream out = new ByteArrayInputStream( bytes );
		read( out );
	}

	private void read(
			final ByteArrayInputStream o ) throws IOException, RuntimeException
	{
		final InflaterInputStream w = new InflaterInputStream( o );
		final ByteBuffer bb = BlobSerilizationCache.getCacheBuffer();

		int usedBytes = 0;
		int rv = 0;

		do
		{
			usedBytes += rv;
			rv = w.read( bb.array(), usedBytes, bb.limit() - usedBytes );
		}
		while ( rv > 0 );

		final PacketBuffer header = new PacketBuffer( Unpooled.wrappedBuffer( bb ) );

		final int version = header.readVarInt();

		BlobSerializer bs = null;

		if ( version == VERSION_COMPACT )
		{
			bs = new BlobSerializer( header );
		}
		else if ( version == VERSION_CROSSWORLD_LEGACY )
		{
			bs = new CrossWorldBlobSerializerLegacy( header );
		}
		else if ( version == VERSION_CROSSWORLD )
		{
			bs = new CrossWorldBlobSerializer( header );
		}
		else
		{
			throw new RuntimeException( "Invalid Version: " + version );
		}

		final int byteOffset = header.readVarInt();
		final int bytesOfInterest = header.readVarInt();

		final BitStream bits = BitStream.valueOf( byteOffset, ByteBuffer.wrap( bb.array(), header.readerIndex(), bytesOfInterest ) );
		for ( int x = 0; x < array_size; x++ )
		{
			values[x] = bs.readVoxelStateID( bits );// src.get();
		}

		w.close();
	}

	static int bestBufferSize = 26;

	public byte[] blobToBytes(
			final int version )
	{
		final ByteArrayOutputStream out = new ByteArrayOutputStream( bestBufferSize );
		write( out, getSerializer( version ) );
		final byte[] o = out.toByteArray();

		if ( bestBufferSize < o.length )
		{
			bestBufferSize = o.length;
		}

		return o;
	}

	private BlobSerializer getSerializer(
			final int version )
	{
		if ( version == VERSION_COMPACT )
		{
			return new BlobSerializer( this );
		}

		if ( version == VERSION_CROSSWORLD_LEGACY )
		{
			return new CrossWorldBlobSerializerLegacy( this );
		}

		if ( version == VERSION_CROSSWORLD )
		{
			return new CrossWorldBlobSerializer( this );
		}

		throw new RuntimeException( "Invalid Version: " + version );
	}

	private void write(
			final ByteArrayOutputStream o,
			final BlobSerializer bs )
	{
		try
		{
			final Deflater def = BlobSerilizationCache.getCacheDeflater();
			final DeflaterOutputStream w = new DeflaterOutputStream( o, def, bestBufferSize );

			final PacketBuffer pb = BlobSerilizationCache.getCachePacketBuffer();
			pb.writeVarInt( bs.getVersion() );
			bs.write( pb );

			final BitStream set = BlobSerilizationCache.getCacheBitStream();
			for ( int x = 0; x < array_size; x++ )
			{
				bs.writeVoxelState( values[x], set );
			}

			final byte[] arrayContents = set.toByteArray();
			final int bytesToWrite = arrayContents.length;
			final int byteOffset = set.byteOffset();

			pb.writeVarInt( byteOffset );
			pb.writeVarInt( bytesToWrite - byteOffset );

			w.write( pb.array(), 0, pb.writerIndex() );

			w.write( arrayContents, byteOffset, bytesToWrite - byteOffset );

			w.finish();
			w.close();

			def.reset();

			o.close();
		}
		catch ( final IOException e )
		{
			throw new RuntimeException( e );
		}
	}
}
