package mod.chiselsandbits.chiseledblock.serialization;

import com.google.common.base.Optional;
import mod.chiselsandbits.core.Log;
import mod.chiselsandbits.helpers.ModUtil;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.ResourceLocation;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class StringStates
{

	public static int getStateIDFromName(
			final String name )
	{
		final String parts[] = name.split( "[?&]" );

		try
		{
			parts[0] = URLDecoder.decode( parts[0], "UTF-8" );
		}
		catch ( final UnsupportedEncodingException e )
		{
			Log.logError( "Failed to reload Property from store data : " + name, e );
		}

		final Block blk = Block.REGISTRY.getObject( new ResourceLocation( parts[0] ) );

		if ( blk == null || blk == Blocks.AIR )
		{
			return 0;
		}

		IBlockState state = blk.getDefaultState();

		if ( state == null )
		{
			return 0;
		}

		// rebuild state...
		for ( int x = 1; x < parts.length; ++x )
		{
			try
			{
				if ( parts[x].length() > 0 )
				{
					final String nameval[] = parts[x].split( "[=]" );
					if ( nameval.length == 2 )
					{
						nameval[0] = URLDecoder.decode( nameval[0], "UTF-8" );
						nameval[1] = URLDecoder.decode( nameval[1], "UTF-8" );

						state = withState( state, blk, nameval );
					}
				}
			}
			catch ( final Exception err )
			{
				Log.logError( "Failed to reload Property from store data : " + name, err );
			}
		}

		return ModUtil.getStateId( state );
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	private static IBlockState withState(
			final IBlockState state,
			final Block blk,
			final String[] nameval )
	{
		final IProperty prop = blk.getBlockState().getProperty( nameval[0] );
		if ( prop == null )
		{
			Log.info( nameval[0] + " is not a valid property for " + Block.REGISTRY.getNameForObject( blk ) );
			return state;
		}

		final Optional pv = prop.parseValue( nameval[1] );
		if ( pv.isPresent() )
		{
			return state.withProperty( prop, (Comparable) pv.get() );
		}
		else
		{
			Log.info( nameval[1] + " is not a valid value of " + nameval[0] + " for " + Block.REGISTRY.getNameForObject( blk ) );
			return state;
		}
	}

	public static String getNameFromStateID(
			final int key )
	{
		final IBlockState state = ModUtil.getStateById( key );
		final Block blk = state.getBlock();

		String sname = "air?";

		try
		{
			final StringBuilder stateName = new StringBuilder( URLEncoder.encode( Block.REGISTRY.getNameForObject( blk ).toString(), "UTF-8" ) );
			stateName.append( '?' );

			boolean first = true;
			for ( final IProperty<?> P : state.getPropertyKeys() )
			{
				if ( !first )
				{
					stateName.append( '&' );
				}

				first = false;

				final Comparable<?> propVal = state.getProperties().get( P );

				String saveAs;
				if ( propVal instanceof IStringSerializable )
				{
					saveAs = ( (IStringSerializable) propVal ).getName();
				}
				else
				{
					saveAs = propVal.toString();
				}

				stateName.append( URLEncoder.encode( P.getName(), "UTF-8" ) );
				stateName.append( '=' );
				stateName.append( URLEncoder.encode( saveAs, "UTF-8" ) );
			}

			sname = stateName.toString();
		}
		catch ( final UnsupportedEncodingException e )
		{
			Log.logError( "Failed to Serialize State", e );
		}

		return sname;
	}

}
