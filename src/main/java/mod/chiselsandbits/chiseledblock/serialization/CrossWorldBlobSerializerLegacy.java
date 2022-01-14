package mod.chiselsandbits.chiseledblock.serialization;

import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.helpers.DeprecationHelper;
import mod.chiselsandbits.helpers.ModUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

public class CrossWorldBlobSerializerLegacy extends BlobSerializer
{

	public CrossWorldBlobSerializerLegacy(
			final PacketBuffer toInflate )
	{
		super( toInflate );
	}

	public CrossWorldBlobSerializerLegacy(
			final VoxelBlob toDeflate )
	{
		super( toDeflate );
	}

	@Override
	protected int readStateID(
			final PacketBuffer buffer )
	{
		final String name = buffer.readString( 512 );
		final int meta = buffer.readVarInt();

		final Block blk = Block.REGISTRY.getObject( new ResourceLocation( name ) );

		if ( blk == null || blk == Blocks.AIR )
		{
			return 0;
		}

		final IBlockState state = DeprecationHelper.getStateFromMeta( blk, meta );
		if ( state == null )
		{
			return 0;
		}

		return ModUtil.getStateId( state );
	}

	@Override
	protected void writeStateID(
			final PacketBuffer buffer,
			final int key )
	{
		final IBlockState state = ModUtil.getStateById( key );
		final Block blk = state.getBlock();

		final String name = Block.REGISTRY.getNameForObject( blk ).toString();
		final int meta = blk.getMetaFromState( state );

		buffer.writeString( name );
		buffer.writeVarInt( meta );
	}

	@Override
	public int getVersion()
	{
		return VoxelBlob.VERSION_CROSSWORLD;
	}
}
