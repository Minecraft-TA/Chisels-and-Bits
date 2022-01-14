package mod.chiselsandbits.client;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;
import mod.chiselsandbits.core.ChiselsAndBits;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClipboardStorage extends Configuration
{

	public ClipboardStorage(
			final File file )
	{
		super( file );
	}

	public void write(
			final List<NBTTagCompound> myitems )
	{
		if ( !ChiselsAndBits.getConfig().persistCreativeClipboard )
		{
			return;
		}

		for ( final String name : getCategoryNames() )
		{
			removeCategory( getCategory( name ) );
		}

		int idx = 0;
		for ( final NBTTagCompound nbt : myitems )
		{
			final PacketBuffer b = new PacketBuffer( Unpooled.buffer() );
			b.writeCompoundTag( nbt );

			final int[] o = new int[b.writerIndex()];
			for ( int x = 0; x < b.writerIndex(); x++ )
			{
				o[x] = b.getByte( x );
			}

			get( "clipboard", "" + idx++, o ).set( o );
		}

		save();
	}

	public List<NBTTagCompound> read()
	{
		final List<NBTTagCompound> myItems = new ArrayList<NBTTagCompound>();

		if ( !ChiselsAndBits.getConfig().persistCreativeClipboard )
		{
			return myItems;
		}

		for ( final Property p : getCategory( "clipboard" ).values() )
		{
			final int[] bytes = p.getIntList();
			final byte[] o = new byte[bytes.length];

			for ( int x = 0; x < bytes.length; x++ )
			{
				o[x] = (byte) bytes[x];
			}

			try
			{
				final PacketBuffer b = new PacketBuffer( Unpooled.wrappedBuffer( o ) );
				final NBTTagCompound c = b.readCompoundTag();

				myItems.add( c );
			}
			catch ( final EncoderException e )
			{
				// :_ (
			}
			catch ( final EOFException e )
			{
				// :_ (
			}
			catch ( final IOException e )
			{
				// :_ (
			}

		}

		return myItems;
	}
}
