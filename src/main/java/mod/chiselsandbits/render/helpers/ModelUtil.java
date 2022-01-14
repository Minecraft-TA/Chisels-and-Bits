package mod.chiselsandbits.render.helpers;

import mod.chiselsandbits.chiseledblock.BlockBitInfo;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ReflectionWrapper;
import mod.chiselsandbits.helpers.DeprecationHelper;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.interfaces.ICacheClearable;
import mod.chiselsandbits.render.chiseledblock.ChiselLayer;
import mod.chiselsandbits.render.chiseledblock.ChiseledBlockBaked;
import mod.chiselsandbits.render.helpers.ModelQuadLayer.ModelQuadLayerBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fluids.Fluid;

import java.util.*;

@SuppressWarnings( "unchecked" )
public class ModelUtil implements ICacheClearable
{
	private final static HashMap<Integer, String> blockToTexture[];
	private static HashMap<Integer, ModelQuadLayer[]> cache = new HashMap<Integer, ModelQuadLayer[]>();
	private static HashMap<Integer, ChiseledBlockBaked> breakCache = new HashMap<Integer, ChiseledBlockBaked>();

	@SuppressWarnings( "unused" )
	private static ModelUtil instance = new ModelUtil();

	static
	{
		blockToTexture = new HashMap[EnumFacing.VALUES.length * BlockRenderLayer.values().length];

		for ( int x = 0; x < blockToTexture.length; x++ )
		{
			blockToTexture[x] = new HashMap<Integer, String>();
		}
	}

	@Override
	public void clearCache()
	{
		for ( int x = 0; x < blockToTexture.length; x++ )
		{
			blockToTexture[x].clear();
		}

		cache.clear();
		breakCache.clear();
	}

	public static ModelQuadLayer[] getCachedFace(
			final int stateID,
			final long weight,
			final EnumFacing face,
			final BlockRenderLayer layer )
	{
		if ( layer == null )
		{
			return null;
		}

		final int cacheVal = stateID << 6 | layer.ordinal() << 4 | face.ordinal();

		final ModelQuadLayer[] mpc = cache.get( cacheVal );
		if ( mpc != null )
		{
			return mpc;
		}

		final BlockRenderLayer original = net.minecraftforge.client.MinecraftForgeClient.getRenderLayer();
		try
		{
			ForgeHooksClient.setRenderLayer( layer );
			return getInnerCachedFace( cacheVal, stateID, weight, face, layer );
		}
		finally
		{
			// restore previous layer.
			ForgeHooksClient.setRenderLayer( original );
		}
	}

	private static ModelQuadLayer[] getInnerCachedFace(
			final int cacheVal,
			final int stateID,
			final long weight,
			final EnumFacing face,
			final BlockRenderLayer layer )
	{
		final IBlockState state = ModUtil.getStateById( stateID );
		final IBakedModel model = ModelUtil.solveModel( state, weight, Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState( state ), layer );
		final int lv = ChiselsAndBits.getConfig().useGetLightValue ? DeprecationHelper.getLightValue( state ) : 0;

		final Fluid fluid = BlockBitInfo.getFluidFromBlock( state.getBlock() );
		if ( fluid != null )
		{
			for ( final EnumFacing xf : EnumFacing.VALUES )
			{
				final ModelQuadLayer[] mp = new ModelQuadLayer[1];
				mp[0] = new ModelQuadLayer();
				mp[0].color = fluid.getColor();
				mp[0].light = lv;

				final float V = 0.5f;
				final float Uf = 1.0f;
				final float U = 0.5f;
				final float Vf = 1.0f;

				if ( xf.getAxis() == Axis.Y )
				{
					mp[0].sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite( fluid.getStill().toString() );
					mp[0].uvs = new float[] { Uf, Vf, 0, Vf, Uf, 0, 0, 0 };
				}
				else if ( xf.getAxis() == Axis.X )
				{
					mp[0].sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite( fluid.getFlowing().toString() );
					mp[0].uvs = new float[] { U, 0, U, V, 0, 0, 0, V };
				}
				else
				{
					mp[0].sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite( fluid.getFlowing().toString() );
					mp[0].uvs = new float[] { U, 0, 0, 0, U, V, 0, V };
				}

				mp[0].tint = 0;

				final int cacheV = stateID << 6 | layer.ordinal() << 4 | xf.ordinal();
				cache.put( cacheV, mp );
			}

			return cache.get( cacheVal );
		}

		final HashMap<EnumFacing, ArrayList<ModelQuadLayerBuilder>> tmp = new HashMap<EnumFacing, ArrayList<ModelQuadLayerBuilder>>();
		final int color = BlockBitInfo.getColorFor( state, 0 );

		for ( final EnumFacing f : EnumFacing.VALUES )
		{
			tmp.put( f, new ArrayList<ModelQuadLayer.ModelQuadLayerBuilder>() );
		}

		if ( model != null )
		{
			for ( final EnumFacing f : EnumFacing.VALUES )
			{
				final List<BakedQuad> quads = ModelUtil.getModelQuads( model, state, f, 0 );
				processFaces( tmp, quads, state );
			}

			processFaces( tmp, ModelUtil.getModelQuads( model, state, null, 0 ), state );
		}

		for ( final EnumFacing f : EnumFacing.VALUES )
		{
			final int cacheV = stateID << 6 | layer.ordinal() << 4 | f.ordinal();
			final ArrayList<ModelQuadLayerBuilder> x = tmp.get( f );
			final ModelQuadLayer[] mp = new ModelQuadLayer[x.size()];

			for ( int z = 0; z < x.size(); z++ )
			{
				mp[z] = x.get( z ).build( stateID, color, lv );
			}

			cache.put( cacheV, mp );
		}

		return cache.get( cacheVal );
	}

	private static List<BakedQuad> getModelQuads(
			final IBakedModel model,
			final IBlockState state,
			final EnumFacing f,
			final long rand )
	{
		try
		{
			// try to get block model...
			return model.getQuads( state, f, rand );
		}
		catch ( final Throwable t )
		{

		}

		try
		{
			// try to get item model?
			return model.getQuads( null, f, rand );
		}
		catch ( final Throwable t )
		{

		}

		final ItemStack is = ModUtil.getItemFromBlock( state );
		if ( !ModUtil.isEmpty( is ) )
		{
			final IBakedModel secondModel = getOverrides( model ).handleItemState( model, is, Minecraft.getMinecraft().world, Minecraft.getMinecraft().player );

			if ( secondModel != null )
			{
				try
				{
					return secondModel.getQuads( null, f, rand );
				}
				catch ( final Throwable t )
				{

				}
			}
		}

		// try to not crash...
		return Collections.emptyList();
	}

	private static ItemOverrideList getOverrides(
			final IBakedModel model )
	{
		if ( model != null )
		{
			final ItemOverrideList modelOverrides = model.getOverrides();
			return modelOverrides == null ? ItemOverrideList.NONE : modelOverrides;
		}
		return ItemOverrideList.NONE;
	}

	private static void processFaces(
			final HashMap<EnumFacing, ArrayList<ModelQuadLayerBuilder>> tmp,
			final List<BakedQuad> quads,
			final IBlockState state )
	{
		for ( final BakedQuad q : quads )
		{
			final EnumFacing face = q.getFace();

			if ( face == null )
			{
				continue;
			}

			try
			{
				final TextureAtlasSprite sprite = findQuadTexture( q, state );
				final ArrayList<ModelQuadLayerBuilder> l = tmp.get( face );

				ModelQuadLayerBuilder b = null;
				for ( final ModelQuadLayerBuilder lx : l )
				{
					if ( lx.cache.sprite == sprite )
					{
						b = lx;
						break;
					}
				}

				if ( b == null )
				{
					// top/bottom
					int uCoord = 0;
					int vCoord = 2;

					switch ( face )
					{
						case NORTH:
						case SOUTH:
							uCoord = 0;
							vCoord = 1;
							break;
						case EAST:
						case WEST:
							uCoord = 1;
							vCoord = 2;
							break;
						default:
					}

					b = new ModelQuadLayerBuilder( sprite, uCoord, vCoord );
					b.cache.tint = q.getTintIndex();
					l.add( b );
				}

				q.pipe( b.uvr );

				if ( ChiselsAndBits.getConfig().enableFaceLightmapExtraction )
				{
					b.lv.setVertexFormat( q.getFormat() );
					q.pipe( b.lv );
				}
			}
			catch ( final Exception e )
			{

			}
		}
	}

	private ModelUtil()
	{
		ChiselsAndBits.getInstance().addClearable( this );
	}

	public static TextureAtlasSprite findQuadTexture(
			final BakedQuad q,
			final IBlockState state ) throws IllegalArgumentException, IllegalAccessException, NullPointerException
	{
		final TextureMap map = Minecraft.getMinecraft().getTextureMapBlocks();
		final Map<String, TextureAtlasSprite> mapRegisteredSprites = ReflectionWrapper.instance.getRegSprite( map );

		if ( mapRegisteredSprites == null )
		{
			throw new RuntimeException( "Unable to lookup textures." );
		}

		final ModelUVAverager av = new ModelUVAverager();
		q.pipe( av );

		final float U = av.getU();
		final float V = av.getV();

		final Iterator<?> iterator1 = mapRegisteredSprites.values().iterator();
		while ( iterator1.hasNext() )
		{
			final TextureAtlasSprite sprite = (TextureAtlasSprite) iterator1.next();
			if ( sprite.getMinU() <= U && U <= sprite.getMaxU() && sprite.getMinV() <= V && V <= sprite.getMaxV() )
			{
				return sprite;
			}
		}

		TextureAtlasSprite texture = null;

		try
		{
			if ( q.getSprite() != null )
			{
				texture = q.getSprite();
			}
		}
		catch ( final Exception e )
		{
		}

		if ( isMissing( texture ) && state != null )
		{
			try
			{
				texture = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getTexture( state );
			}
			catch ( final Exception err )
			{
			}
		}

		if ( texture == null )
		{
			return Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
		}

		return texture;
	}

	public static IBakedModel solveModel(
			final IBlockState state,
			final long weight,
			final IBakedModel originalModel,
			final BlockRenderLayer layer )
	{
		boolean hasFaces = false;

		try
		{
			hasFaces = hasFaces( originalModel, state, null, weight );

			for ( final EnumFacing f : EnumFacing.VALUES )
			{
				hasFaces = hasFaces || hasFaces( originalModel, state, f, weight );
			}
		}
		catch ( final Exception e )
		{
			// an exception was thrown.. use the item model and hope...
			hasFaces = false;
		}

		if ( !hasFaces )
		{
			// if the model is empty then lets grab an item and try that...
			final ItemStack is = ModUtil.getItemFromBlock( state );
			if ( !ModUtil.isEmpty( is ) )
			{
				final IBakedModel itemModel = Minecraft.getMinecraft().getRenderItem().getItemModelWithOverrides( is, Minecraft.getMinecraft().world, Minecraft.getMinecraft().player );

				try
				{
					hasFaces = hasFaces( originalModel, state, null, weight );

					for ( final EnumFacing f : EnumFacing.VALUES )
					{
						hasFaces = hasFaces || hasFaces( originalModel, state, f, weight );
					}
				}
				catch ( final Exception e )
				{
					// an exception was thrown.. use the item model and hope...
					hasFaces = false;
				}

				if ( hasFaces )
				{
					return itemModel;
				}
				else
				{
					return new SimpleGeneratedModel( findTexture( Block.getStateId( state ), originalModel, EnumFacing.UP, layer ) );
				}
			}
		}

		return originalModel;
	}

	private static boolean hasFaces(
			final IBakedModel model,
			final IBlockState state,
			final EnumFacing f,
			final long weight )
	{
		final List<BakedQuad> l = getModelQuads( model, state, f, weight );
		if ( l == null || l.isEmpty() )
		{
			return false;
		}

		TextureAtlasSprite texture = null;

		try
		{
			texture = findTexture( null, l, f );
		}
		catch ( final Exception e )
		{
		}

		final ModelVertexRange mvr = new ModelVertexRange();

		for ( final BakedQuad q : l )
		{
			q.pipe( mvr );
		}

		return mvr.getLargestRange() > 0 && !isMissing( texture );
	}

	private static boolean isMissing(
			final TextureAtlasSprite texture )
	{
		return texture == null || texture == Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
	}

	public static TextureAtlasSprite findTexture(
			final int BlockRef,
			final IBakedModel model,
			final EnumFacing myFace,
			final BlockRenderLayer layer )
	{
		final int blockToWork = layer.ordinal() * EnumFacing.VALUES.length + myFace.ordinal();

		// didn't work? ok lets try scanning for the texture in the
		if ( blockToTexture[blockToWork].containsKey( BlockRef ) )
		{
			final String textureName = blockToTexture[blockToWork].get( BlockRef );
			return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite( textureName );
		}

		TextureAtlasSprite texture = null;
		final IBlockState state = ModUtil.getStateById( BlockRef );

		if ( model != null )
		{
			try
			{
				texture = findTexture( texture, getModelQuads( model, state, myFace, 0 ), myFace );

				if ( texture == null )
				{
					for ( final EnumFacing side : EnumFacing.VALUES )
					{
						texture = findTexture( texture, getModelQuads( model, state, side, 0 ), side );
					}

					texture = findTexture( texture, getModelQuads( model, state, null, 0 ), null );
				}
			}
			catch ( final Exception errr )
			{
			}
		}

		// who knows if that worked.. now lets try to get a texture...
		if ( isMissing( texture ) )
		{
			try
			{
				if ( model != null )
				{
					texture = model.getParticleTexture();
				}
			}
			catch ( final Exception err )
			{
			}
		}

		if ( isMissing( texture ) )
		{
			try
			{
				texture = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getTexture( state );
			}
			catch ( final Exception err )
			{
			}
		}

		if ( texture == null )
		{
			texture = Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
		}

		blockToTexture[blockToWork].put( BlockRef, texture.getIconName() );
		return texture;
	}

	private static TextureAtlasSprite findTexture(
			TextureAtlasSprite texture,
			final List<BakedQuad> faceQuads,
			final EnumFacing myFace ) throws IllegalArgumentException, IllegalAccessException, NullPointerException
	{
		for ( final BakedQuad q : faceQuads )
		{
			if ( q.getFace() == myFace )
			{
				texture = findQuadTexture( q, null );
			}
		}

		return texture;
	}

	public static boolean isOne(
			final float v )
	{
		return Math.abs( v ) < 0.01;
	}

	public static boolean isZero(
			final float v )
	{
		return Math.abs( v - 1.0f ) < 0.01;
	}

	public static Integer getItemStackColor(
			final ItemStack target,
			final int tint )
	{
		// don't send air though to MC, some mods have registered their custom
		// color handlers for it and it can crash.

		if ( ModUtil.isEmpty( target ) )
			return -1;

		return Minecraft.getMinecraft().getItemColors().colorMultiplier( target, tint );
	}

	public static ChiseledBlockBaked getBreakingModel(
			ChiselLayer layer,
			Integer blockStateID )
	{
		int key = layer.layer.ordinal() + ( blockStateID << 2 );
		ChiseledBlockBaked out = breakCache.get( key );

		if ( out == null )
		{
			final IBlockState state = ModUtil.getStateById( blockStateID );
			final IBakedModel model = ModelUtil.solveModel( state, 0, Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState( ModUtil.getStateById( blockStateID ) ), layer.layer );

			if ( model != null )
			{
				out = ChiseledBlockBaked.createFromTexture( ModelUtil.findTexture( blockStateID, model, EnumFacing.UP, layer.layer ), layer );
			}
			else
			{
				out = ChiseledBlockBaked.createFromTexture( null, null );
			}

			breakCache.put( key, out );
		}

		return out;
	}

}
