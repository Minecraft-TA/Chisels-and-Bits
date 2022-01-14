package mod.chiselsandbits.render.chiseledblock.tesr;

import com.google.common.base.Stopwatch;
import mod.chiselsandbits.chiseledblock.EnumTESRRenderState;
import mod.chiselsandbits.chiseledblock.TileEntityBlockChiseled;
import mod.chiselsandbits.chiseledblock.TileEntityBlockChiseledTESR;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.core.Log;
import mod.chiselsandbits.render.chiseledblock.ChiselLayer;
import mod.chiselsandbits.render.chiseledblock.ChiseledBlockBaked;
import mod.chiselsandbits.render.chiseledblock.ChiseledBlockSmartModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.SimpleBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ChisledBlockRenderChunkTESR extends TileEntitySpecialRenderer<TileEntityBlockChiseledTESR>
{
	public final static AtomicInteger pendingTess = new AtomicInteger( 0 );
	public final static AtomicInteger activeTess = new AtomicInteger( 0 );

	private final static ThreadPoolExecutor pool;
	private static ChisledBlockRenderChunkTESR instance;

	static int TESR_Regions_rendered = 0;
	static int TESR_SI_Regions_rendered = 0;

	private void markRendered(
			final boolean singleInstanceMode )
	{
		if ( singleInstanceMode )
		{
			++TESR_SI_Regions_rendered;
		}
		else
		{
			++TESR_Regions_rendered;
		}
	}

	public static ChisledBlockRenderChunkTESR getInstance()
	{
		return instance;
	}

	private static class WorldTracker
	{
		private final LinkedList<FutureTracker> futureTrackers = new LinkedList<FutureTracker>();
		private final Queue<UploadTracker> uploaders = new ConcurrentLinkedQueue<UploadTracker>();
		private final Queue<Runnable> nextFrameTasks = new ConcurrentLinkedQueue<Runnable>();
	};

	private static final WeakHashMap<World, WorldTracker> worldTrackers = new WeakHashMap<World, WorldTracker>();

	private static WorldTracker getTracker()
	{
		final World w = ClientSide.instance.getPlayer().world;
		WorldTracker t = worldTrackers.get( w );

		if ( t == null )
		{
			worldTrackers.put( w, t = new WorldTracker() );
		}

		return t;
	}

	public static void addNextFrameTask(
			final Runnable r )
	{
		getTracker().nextFrameTasks.offer( r );
	}

	private static class FutureTracker
	{
		final TileLayerRenderCache tlrc;
		final TileRenderCache renderCache;
		final BlockRenderLayer layer;
		final FutureTask<Tessellator> future;

		public FutureTracker(
				final TileLayerRenderCache tlrc,
				final TileRenderCache renderCache,
				final BlockRenderLayer layer )
		{
			this.tlrc = tlrc;
			this.renderCache = renderCache;
			this.layer = layer;
			future = tlrc.future;
		}

		public void done()
		{
			pendingTess.decrementAndGet();
		}
	};

	private void addFutureTracker(
			final TileLayerRenderCache tlrc,
			final TileRenderCache renderCache,
			final BlockRenderLayer layer )
	{
		getTracker().futureTrackers.add( new FutureTracker( tlrc, renderCache, layer ) );
	}

	private boolean handleFutureTracker(
			final FutureTracker ft )
	{
		// next frame..?
		if ( ft.future != null && ft.future.isDone() )
		{
			try
			{
				final Tessellator t = ft.future.get();

				if ( ft.future == ft.tlrc.future )
				{
					ft.tlrc.waiting = true;
					getTracker().uploaders.offer( new UploadTracker( ft.renderCache, ft.layer, t ) );
				}
				else
				{
					try
					{
						t.getBuffer().finishDrawing();
					}
					catch ( final IllegalStateException e )
					{
						Log.logError( "Bad Tessellator Behavior.", e );
					}

					ChisledBlockBackgroundRender.submitTessellator( t );
				}
			}
			catch ( final InterruptedException e )
			{
				Log.logError( "Failed to get TESR Future - C", e );
			}
			catch ( final ExecutionException e )
			{
				Log.logError( "Failed to get TESR Future - D", e );
			}
			catch ( final CancellationException e )
			{
				// no issues here.
			}
			finally
			{
				if ( ft.future == ft.tlrc.future )
				{
					ft.tlrc.future = null;
				}
			}

			ft.done();
			return true;
		}

		return false;
	}

	boolean runUpload = false;

	@SubscribeEvent
	public void debugScreen(
			final RenderGameOverlayEvent.Text t )
	{
		if ( Minecraft.getMinecraft().gameSettings.showDebugInfo )
		{
			if ( TESR_Regions_rendered > 0 || TESR_SI_Regions_rendered > 0 )
			{
				t.getRight().add( "C&B DynRender: " + TESR_Regions_rendered + ":" + TESR_SI_Regions_rendered + " - " + ( GfxRenderState.useVBO() ? "VBO" : "DspList" ) );
				TESR_Regions_rendered = 0;
				TESR_SI_Regions_rendered = 0;
			}
		}
		else
		{
			TESR_Regions_rendered = 0;
			TESR_SI_Regions_rendered = 0;
		}
	}

	int lastFancy = -1;

	@SubscribeEvent
	public void nextFrame(
			final RenderWorldLastEvent e )
	{
		runJobs( getTracker().nextFrameTasks );

		uploadDisplaylists();

		// this seemingly stupid check fixes leaves, other wise we use fast
		// until the atlas refreshes.
		final int currentFancy = Minecraft.getMinecraft().gameSettings.fancyGraphics ? 1 : 0;
		if ( currentFancy != lastFancy )
		{
			lastFancy = currentFancy;

			// destroy the cache, and start over.
			ChiselsAndBits.getInstance().clearCache();

			// another dumb thing, MC has probobly already tried reloading
			// things, so we need to tell it to start that over again.
			Minecraft mc = Minecraft.getMinecraft();
			mc.renderGlobal.loadRenderers();
		}
	}

	private void uploadDisplaylists()
	{
		final WorldTracker trackers = getTracker();

		final Iterator<FutureTracker> i = trackers.futureTrackers.iterator();
		while ( i.hasNext() )
		{
			if ( handleFutureTracker( i.next() ) )
			{
				i.remove();
			}
		}

		final Stopwatch w = Stopwatch.createStarted();
		final boolean dynamicRenderFullChunksOnly = ChiselsAndBits.getConfig().dynamicRenderFullChunksOnly;
		final int maxMillisecondsPerBlock = ChiselsAndBits.getConfig().maxMillisecondsPerBlock;
		final int maxMillisecondsUploadingPerFrame = ChiselsAndBits.getConfig().maxMillisecondsUploadingPerFrame;

		do
		{
			final UploadTracker t = trackers.uploaders.poll();

			if ( t == null )
			{
				return;
			}

			if ( t.trc instanceof TileRenderChunk )
			{
				final Stopwatch sw = Stopwatch.createStarted();
				uploadDisplayList( t );

				if ( !dynamicRenderFullChunksOnly && sw.elapsed( TimeUnit.MILLISECONDS ) > maxMillisecondsPerBlock )
				{
					( (TileRenderChunk) t.trc ).singleInstanceMode = true;
				}
			}
			else
			{
				uploadDisplayList( t );
			}

			t.trc.getLayer( t.layer ).waiting = false;
		}
		while ( w.elapsed( TimeUnit.MILLISECONDS ) < maxMillisecondsUploadingPerFrame );

	}

	private void runJobs(
			final Queue<Runnable> tasks )
	{
		do
		{
			final Runnable x = tasks.poll();

			if ( x == null )
			{
				break;
			}

			x.run();
		}
		while ( true );
	}

	private void uploadDisplayList(
			final UploadTracker t )
	{
		final BlockRenderLayer layer = t.layer;
		final TileLayerRenderCache tlrc = t.trc.getLayer( layer );

		final Tessellator tx = t.getTessellator();

		if ( tlrc.displayList == null )
		{
			tlrc.displayList = GfxRenderState.getNewState( tx.getBuffer().getVertexCount() );
		}

		tlrc.displayList = tlrc.displayList.prepare( tx );

		t.submitForReuse();
	}

	public ChisledBlockRenderChunkTESR()
	{
		instance = this;
		ChiselsAndBits.registerWithBus( this );
	}

	static
	{
		final ThreadFactory threadFactory = new ThreadFactory() {

			@Override
			public Thread newThread(
					final Runnable r )
			{
				final Thread t = new Thread( r );
				t.setPriority( Thread.NORM_PRIORITY - 1 );
				t.setName( "C&B Dynamic Render Thread" );
				return t;
			}
		};

		int processors = Runtime.getRuntime().availableProcessors();
		if ( ChiselsAndBits.getConfig().lowMemoryMode )
		{
			processors = 1;
		}

		pool = new ThreadPoolExecutor( 1, processors, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>( 64 ), threadFactory );
		pool.allowCoreThreadTimeOut( false );
	}

	public void renderBreakingEffects(
			final TileEntityBlockChiseled te,
			final double x,
			final double y,
			final double z,
			final float partialTicks,
			final int destroyStage )
	{
		bindTexture( TextureMap.LOCATION_BLOCKS_TEXTURE );
		final String file = DESTROY_STAGES[destroyStage].toString().replace( "textures/", "" ).replace( ".png", "" );
		final TextureAtlasSprite damageTexture = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite( file );

		GlStateManager.pushMatrix();
		GlStateManager.depthFunc( GL11.GL_LEQUAL );
		final BlockPos cp = te.getPos();
		GlStateManager.translate( x - cp.getX(), y - cp.getY(), z - cp.getZ() );

		final Tessellator tessellator = Tessellator.getInstance();
		final BufferBuilder buffer = tessellator.getBuffer();

		buffer.begin( GL11.GL_QUADS, DefaultVertexFormats.BLOCK );
		buffer.setTranslation( 0, 0, 0 );

		final BlockRendererDispatcher blockRenderer = Minecraft.getMinecraft().getBlockRendererDispatcher();
		final IExtendedBlockState estate = te.getRenderState( te.getWorld() );

		for ( final ChiselLayer lx : ChiselLayer.values() )
		{
			final ChiseledBlockBaked model = ChiseledBlockSmartModel.getCachedModel( te, lx );

			if ( !model.isEmpty() )
			{
				final IBakedModel damageModel = new SimpleBakedModel.Builder( estate, model, damageTexture, cp ).makeBakedModel();
				blockRenderer.getBlockModelRenderer().renderModel( te.getWorld(), damageModel, estate, te.getPos(), buffer, false );
			}
		}

		tessellator.draw();
		buffer.setTranslation( 0.0D, 0.0D, 0.0D );

		GlStateManager.resetColor();
		GlStateManager.popMatrix();
		return;
	}

	private void renderTileEntityInner(
			final TileEntityBlockChiseledTESR te,
			final double x,
			final double y,
			final double z,
			final float partialTicks,
			final int destroyStage,
			final BufferBuilder worldRenderer )
	{
		if ( destroyStage > 0 )
		{
			renderLogic( te, x, y, z, partialTicks, destroyStage, false );
			return;
		}

		renderLogic( te, x, y, z, partialTicks, destroyStage, true );
	}

	private void renderLogic(
			final TileEntityBlockChiseledTESR te,
			final double x,
			final double y,
			final double z,
			final float partialTicks,
			final int destroyStage,
			final boolean groupLogic )
	{
		final BlockRenderLayer layer = MinecraftForgeClient.getRenderPass() == 0 ? BlockRenderLayer.SOLID : BlockRenderLayer.TRANSLUCENT;
		final TileRenderChunk renderChunk = te.getRenderChunk();
		TileRenderCache renderCache = renderChunk;

		/// how????
		if ( renderChunk == null )
		{
			return;
		}

		if ( destroyStage >= 0 )
		{
			if ( layer == BlockRenderLayer.SOLID )
			{
				return;
			}

			renderBreakingEffects( te, x, y, z, partialTicks, destroyStage );
			return;
		}

		// cache at the tile level rather than the chunk level.
		if ( renderChunk.singleInstanceMode )
		{
			if ( groupLogic )
			{
				final EnumTESRRenderState state = renderCache.update( layer, 0 );
				if ( renderCache == null || state == EnumTESRRenderState.SKIP )
				{
					return;
				}

				final TileList tiles = renderChunk.getTiles();
				tiles.getReadLock().lock();

				try
				{
					for ( final TileEntityBlockChiseledTESR e : tiles )
					{
						configureGLState( layer );
						renderLogic( e, x, y, z, partialTicks, destroyStage, false );
						unconfigureGLState();
					}
				}
				finally
				{
					tiles.getReadLock().unlock();
				}

				return;
			}

			renderCache = te.getCache();
		}

		final EnumTESRRenderState state = renderCache.update( layer, 0 );
		if ( renderCache == null || state == EnumTESRRenderState.SKIP )
		{
			return;
		}

		final BlockPos chunkOffset = renderChunk.chunkOffset();

		final TileLayerRenderCache tlrc = renderCache.getLayer( layer );
		final boolean isNew = tlrc.isNew();
		boolean hasSubmitted = false;

		if ( tlrc.displayList == null || tlrc.rebuild )
		{
			final int dynamicTess = getMaxTessalators();

			if ( pendingTess.get() < dynamicTess && tlrc.future == null && !tlrc.waiting || isNew )
			{
				// copy the tiles for the thread..
				final ChunkCache cache = new ChunkCache( getWorld(), chunkOffset, chunkOffset.add( 16, 16, 16 ), 1 );
				final FutureTask<Tessellator> newFuture = new FutureTask<Tessellator>( new ChisledBlockBackgroundRender( cache, chunkOffset, renderCache.getTileList(), layer ) );

				try
				{
					pool.submit( newFuture );
					hasSubmitted = true;

					if ( tlrc.future != null )
					{
						tlrc.future.cancel( true );
					}

					tlrc.rebuild = false;
					tlrc.future = newFuture;
					pendingTess.incrementAndGet();
				}
				catch ( final RejectedExecutionException err )
				{
					// Yar...
				}
			}
		}

		// now..
		if ( tlrc.future != null && isNew && hasSubmitted )
		{
			try
			{
				final Tessellator tess = tlrc.future.get( ChiselsAndBits.getConfig().minimizeLatancyMaxTime, TimeUnit.MILLISECONDS );
				tlrc.future = null;
				pendingTess.decrementAndGet();

				uploadDisplayList( new UploadTracker( renderCache, layer, tess ) );

				tlrc.waiting = false;
			}
			catch ( final InterruptedException e )
			{
				Log.logError( "Failed to get TESR Future - A", e );
				tlrc.future = null;
			}
			catch ( final ExecutionException e )
			{
				Log.logError( "Failed to get TESR Future - B", e );
				tlrc.future = null;
			}
			catch ( final TimeoutException e )
			{
				addFutureTracker( tlrc, renderCache, layer );
			}
		}
		else if ( tlrc.future != null && hasSubmitted )
		{
			addFutureTracker( tlrc, renderCache, layer );
		}

		final GfxRenderState dl = tlrc.displayList;
		if ( dl != null && dl.shouldRender() )
		{
			if ( !dl.validForUse() )
			{
				tlrc.displayList = null;
				return;
			}

			GL11.glPushMatrix();
			GL11.glTranslated( -TileEntityRendererDispatcher.staticPlayerX + chunkOffset.getX(),
					-TileEntityRendererDispatcher.staticPlayerY + chunkOffset.getY(),
					-TileEntityRendererDispatcher.staticPlayerZ + chunkOffset.getZ() );

			configureGLState( layer );

			if ( dl.render() )
			{
				markRendered( renderChunk.singleInstanceMode );
			}

			unconfigureGLState();

			GL11.glPopMatrix();
		}
	}

	public static int getMaxTessalators()
	{
		int dynamicTess = ChiselsAndBits.getConfig().dynamicMaxConcurrentTessalators;

		if ( ChiselsAndBits.getConfig().lowMemoryMode )
		{
			dynamicTess = Math.min( 2, dynamicTess );
		}

		return dynamicTess;
	}

	int isConfigured = 0;

	private void configureGLState(
			final BlockRenderLayer layer )
	{
		isConfigured++;

		if ( isConfigured == 1 )
		{
			OpenGlHelper.setLightmapTextureCoords( OpenGlHelper.lightmapTexUnit, 0, 0 );

			GlStateManager.color( 1.0f, 1.0f, 1.0f, 1.0f );
			bindTexture( TextureMap.LOCATION_BLOCKS_TEXTURE );

			RenderHelper.disableStandardItemLighting();
			GlStateManager.blendFunc( GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA );
			GlStateManager.color( 1.0f, 1.0f, 1.0f, 1.0f );

			if ( layer == BlockRenderLayer.TRANSLUCENT )
			{
				GlStateManager.enableBlend();
				GlStateManager.disableAlpha();
			}
			else
			{
				GlStateManager.disableBlend();
				GlStateManager.enableAlpha();
			}

			GlStateManager.enableCull();
			GlStateManager.enableTexture2D();

			if ( Minecraft.isAmbientOcclusionEnabled() )
			{
				GlStateManager.shadeModel( GL11.GL_SMOOTH );
			}
			else
			{
				GlStateManager.shadeModel( GL11.GL_FLAT );
			}
		}
	}

	private void unconfigureGLState()
	{
		isConfigured--;

		if ( isConfigured > 0 )
		{
			return;
		}

		GlStateManager.resetColor(); // required to be called after drawing the
										// display list cause the post render
										// method usually calls it.

		GlStateManager.enableAlpha();
		GlStateManager.enableBlend();

		RenderHelper.enableStandardItemLighting();
	}

	@Override
	public void renderTileEntityFast(
			final TileEntityBlockChiseledTESR te,
			final double x,
			final double y,
			final double z,
			final float partialTicks,
			final int destroyStage,
			final float partial,
			final BufferBuilder buffer )
	{
		renderTileEntityInner( te, x, y, z, partialTicks, destroyStage, buffer );
	}

	@Override
	public void render(
			final TileEntityBlockChiseledTESR te,
			final double x,
			final double y,
			final double z,
			final float partialTicks,
			final int destroyStage,
			float partial )
	{
		if ( destroyStage > 0 )
		{
			renderTileEntityInner( te, x, y, z, partialTicks, destroyStage, null );
		}
	}

}
