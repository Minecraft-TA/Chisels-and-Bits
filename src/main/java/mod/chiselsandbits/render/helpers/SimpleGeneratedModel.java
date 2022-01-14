package mod.chiselsandbits.render.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.util.vector.Vector3f;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.client.renderer.block.model.BlockPartRotation;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad.Builder;

public class SimpleGeneratedModel implements IBakedModel
{

	@SuppressWarnings( "unchecked" )
	final List<BakedQuad>[] face = new List[6];

	final TextureAtlasSprite texture;

	public SimpleGeneratedModel(
			final TextureAtlasSprite texture )
	{
		// create lists...
		face[0] = new ArrayList<BakedQuad>();
		face[1] = new ArrayList<BakedQuad>();
		face[2] = new ArrayList<BakedQuad>();
		face[3] = new ArrayList<BakedQuad>();
		face[4] = new ArrayList<BakedQuad>();
		face[5] = new ArrayList<BakedQuad>();

		this.texture = texture;

		final float[] afloat = new float[] { 0, 0, 16, 16 };
		final BlockFaceUV uv = new BlockFaceUV( afloat, 0 );
		final FaceBakery faceBakery = new FaceBakery();

		final Vector3f to = new Vector3f( 0.0f, 0.0f, 0.0f );
		final Vector3f from = new Vector3f( 16.0f, 16.0f, 16.0f );

		final BlockPartRotation bpr = null;
		final ModelRotation mr = ModelRotation.X0_Y0;

		for ( final EnumFacing side : EnumFacing.VALUES )
		{
			final BlockPartFace bpf = new BlockPartFace( side, 1, "", uv );

			Vector3f toB, fromB;

			switch ( side )
			{
				case UP:
					toB = new Vector3f( to.x, from.y, to.z );
					fromB = new Vector3f( from.x, from.y, from.z );
					break;
				case EAST:
					toB = new Vector3f( from.x, to.y, to.z );
					fromB = new Vector3f( from.x, from.y, from.z );
					break;
				case NORTH:
					toB = new Vector3f( to.x, to.y, to.z );
					fromB = new Vector3f( from.x, from.y, to.z );
					break;
				case SOUTH:
					toB = new Vector3f( to.x, to.y, from.z );
					fromB = new Vector3f( from.x, from.y, from.z );
					break;
				case DOWN:
					toB = new Vector3f( to.x, to.y, to.z );
					fromB = new Vector3f( from.x, to.y, from.z );
					break;
				case WEST:
					toB = new Vector3f( to.x, to.y, to.z );
					fromB = new Vector3f( to.x, from.y, from.z );
					break;
				default:
					throw new NullPointerException();
			}

			final BakedQuad g = faceBakery.makeBakedQuad( toB, fromB, bpf, texture, side, mr, bpr, false, true );
			face[side.ordinal()].add( finishFace( g, side, DefaultVertexFormats.BLOCK ) );
		}
	}

	private BakedQuad finishFace(
			final BakedQuad g,
			final EnumFacing myFace,
			final VertexFormat format )
	{
		final int[] vertData = g.getVertexData();
		final int wrapAt = vertData.length / 4;

		final UnpackedBakedQuad.Builder b = new Builder( format );
		b.setQuadOrientation( myFace );
		b.setQuadTint( 1 );
		b.setTexture( g.getSprite() );

		for ( int vertNum = 0; vertNum < 4; vertNum++ )
		{
			for ( int elementIndex = 0; elementIndex < format.getElementCount(); elementIndex++ )
			{
				final VertexFormatElement element = format.getElement( elementIndex );
				switch ( element.getUsage() )
				{
					case POSITION:
						b.put( elementIndex, Float.intBitsToFloat( vertData[0 + wrapAt * vertNum] ), Float.intBitsToFloat( vertData[1 + wrapAt * vertNum] ), Float.intBitsToFloat( vertData[2 + wrapAt * vertNum] ) );
						break;

					case COLOR:
						final float light = LightUtil.diffuseLight( myFace );
						b.put( elementIndex, light, light, light, 1f );
						break;

					case NORMAL:
						b.put( elementIndex, myFace.getXOffset(), myFace.getYOffset(), myFace.getZOffset() );
						break;

					case UV:

						if ( element.getIndex() == 1 )
						{
							b.put( elementIndex, 0, 0 );
						}
						else
						{
							final float u = Float.intBitsToFloat( vertData[4 + wrapAt * vertNum] );
							final float v = Float.intBitsToFloat( vertData[5 + wrapAt * vertNum] );
							b.put( elementIndex, u, v );
						}

						break;

					default:
						b.put( elementIndex );
						break;
				}
			}
		}

		return b.build();
	}

	public List<BakedQuad>[] getFace()
	{
		return face;
	}

	@Override
	public List<BakedQuad> getQuads(
			final IBlockState state,
			final EnumFacing side,
			final long rand )
	{
		if ( side == null )
		{
			return Collections.emptyList();
		}

		return face[side.ordinal()];
	}

	@Override
	public boolean isAmbientOcclusion()
	{
		return true;
	}

	@Override
	public boolean isGui3d()
	{
		return true;
	}

	@Override
	public ItemCameraTransforms getItemCameraTransforms()
	{
		return ItemCameraTransforms.DEFAULT;
	}

	@Override
	public TextureAtlasSprite getParticleTexture()
	{
		return texture;
	}

	@Override
	public boolean isBuiltInRenderer()
	{
		return false;
	}

	@Override
	public ItemOverrideList getOverrides()
	{
		return ItemOverrideList.NONE;
	}
}
