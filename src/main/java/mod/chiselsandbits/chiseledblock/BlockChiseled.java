package mod.chiselsandbits.chiseledblock;

import mod.chiselsandbits.api.BoxType;
import mod.chiselsandbits.api.IMultiStateBlock;
import mod.chiselsandbits.chiseledblock.data.*;
import mod.chiselsandbits.chiseledblock.properties.UnlistedBlockStateID;
import mod.chiselsandbits.chiseledblock.properties.UnlistedVoxelBlob;
import mod.chiselsandbits.chiseledblock.properties.UnlistedVoxelNeighborState;
import mod.chiselsandbits.client.CreativeClipboardTab;
import mod.chiselsandbits.client.UndoTracker;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.core.Log;
import mod.chiselsandbits.helpers.BitOperation;
import mod.chiselsandbits.helpers.ChiselToolType;
import mod.chiselsandbits.helpers.ExceptionNoTileEntity;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.integration.mcmultipart.MCMultipartProxy;
import mod.chiselsandbits.items.ItemChiseledBit;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class BlockChiseled extends Block implements ITileEntityProvider, IMultiStateBlock
{

	private static final AxisAlignedBB BAD_AABB = new AxisAlignedBB( 0, Double.MIN_NORMAL, 0, 0, Double.MIN_NORMAL, 0 );

	private static ThreadLocal<IBlockState> actingAs = new ThreadLocal<IBlockState>();

	public static final IUnlistedProperty<VoxelNeighborRenderTracker> UProperty_VoxelNeighborState = new UnlistedVoxelNeighborState();
	public static final IUnlistedProperty<VoxelBlobStateReference> UProperty_VoxelBlob = new UnlistedVoxelBlob();
	public static final IUnlistedProperty<Integer> UProperty_Primary_BlockState = new UnlistedBlockStateID();
	public static final PropertyBool LProperty_FullBlock = PropertyBool.create( "full_block" );

	public final String name;

	@Override
	public BlockFaceShape getBlockFaceShape(
			IBlockAccess world,
			IBlockState state,
			BlockPos pos,
			EnumFacing facing ) // getBlockFaceShape
	{
		try
		{
			TileEntityBlockChiseled te = getTileEntity( world, pos );
			return te.isSideSolid( facing ) ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
		}
		catch ( ExceptionNoTileEntity e )
		{
			return BlockFaceShape.UNDEFINED;
		}
	}

	@Override
	public Boolean isAABBInsideMaterial(
			final World world,
			final BlockPos pos,
			final AxisAlignedBB bb,
			final Material materialIn )
	{
		try
		{
			return sharedIsAABBInsideMaterial( getTileEntity( world, pos ), bb, materialIn );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return null;
		}
	}

	@Override
	public Boolean isEntityInsideMaterial(
			final IBlockAccess world,
			final BlockPos pos,
			final IBlockState iblockstate,
			final Entity entity,
			final double yToTest,
			final Material materialIn,
			final boolean testingHead )
	{
		try
		{
			return sharedIsEntityInsideMaterial( getTileEntity( world, pos ), pos, entity, yToTest, materialIn, testingHead );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return null;
		}
	}

	@Override
	public boolean removedByPlayer(
			final IBlockState state,
			final World world,
			final BlockPos pos,
			final EntityPlayer player,
			final boolean willHarvest )
	{
		if ( !willHarvest && ChiselsAndBits.getConfig().addBrokenBlocksToCreativeClipboard )
		{

			try
			{
				final TileEntityBlockChiseled tebc = getTileEntity( world, pos );
				CreativeClipboardTab.addItem( tebc.getItemStack( player ) );

				UndoTracker.getInstance().add( world, pos, tebc.getBlobStateReference(), new VoxelBlobStateReference( 0, 0 ) );
			}
			catch ( final ExceptionNoTileEntity e )
			{
				Log.noTileError( e );
			}
		}

		return super.removedByPlayer( state, world, pos, player, willHarvest );
	}

	@Override
	public boolean shouldCheckWeakPower(
			final IBlockState state,
			final IBlockAccess world,
			final BlockPos pos,
			final EnumFacing side )
	{
		return isOpaqueCube( state );
	}

	@Override
	public int getLightOpacity(
			final IBlockState state,
			final IBlockAccess world,
			final BlockPos pos )
	{
		return isOpaqueCube( state ) ? 255 : 0;
	}

	@Override
	public boolean isNormalCube(
			final IBlockState state,
			final IBlockAccess world,
			final BlockPos pos )
	{
		return isFullCube( state );
	}

	@Override
	public boolean isReplaceable(
			final IBlockAccess worldIn,
			final BlockPos pos )
	{
		try
		{
			return getTileEntity( worldIn, pos ).getBlob().filled() == 0;
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return super.isReplaceable( worldIn, pos );
		}
	}

	@Override
	public boolean doesSideBlockRendering(
			final IBlockState state,
			final IBlockAccess world,
			final BlockPos pos,
			final EnumFacing face )
	{
		try
		{
			return getTileEntity( world, pos ).isSideOpaque( face );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return false;
		}
	}

	public BlockChiseled(
			final Material mat,
			final String BlockName )
	{
		super( new SubMaterial( mat ) );

		configureSound( mat );

		setLightOpacity( 0 );
		setHardness( 1 );
		setHarvestLevel( "pickaxe", 0 );
		name = BlockName;

		setDefaultState( getDefaultState().withProperty( LProperty_FullBlock, false ) );
	}

	@Override
	public float getSlipperiness(
			IBlockState state,
			IBlockAccess world,
			BlockPos pos,
			Entity entity )
	{
		try
		{
			IBlockState texture = getTileEntity( world, pos ).getBlockState( Blocks.STONE );

			if ( texture != null )
			{
				return texture.getBlock().getSlipperiness( texture, new HarvestWorld( texture ), pos, entity );
			}
		}
		catch ( ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
		}

		return super.getSlipperiness( state, world, pos, entity );
	}

	private void configureSound(
			final Material mat )
	{
		if ( mat == Material.WOOD )
		{
			setSoundType( SoundType.WOOD );
		}
		else if ( mat == Material.ROCK )
		{
			setSoundType( SoundType.STONE );
		}
		else if ( mat == Material.IRON )
		{
			setSoundType( SoundType.METAL );
		}
		else if ( mat == Material.CLOTH )
		{
			setSoundType( SoundType.CLOTH );
		}
		else if ( mat == Material.ICE )
		{
			setSoundType( SoundType.GLASS );
		}
		else if ( mat == Material.PACKED_ICE )
		{
			setSoundType( SoundType.GLASS );
		}
		else if ( mat == Material.CLAY )
		{
			setSoundType( SoundType.GROUND );
		}
		else if ( mat == Material.GLASS )
		{
			setSoundType( SoundType.GLASS );
		}
	}

	@Override
	public int getMetaFromState(
			IBlockState state )
	{
		return state.getValue( LProperty_FullBlock ) ? 1 : 0;
	}

	@Override
	public IBlockState getStateFromMeta(
			int meta )
	{
		return getDefaultState().withProperty( LProperty_FullBlock, meta == 1 );
	}

	@Override
	public boolean canRenderInLayer(
			final IBlockState state,
			final BlockRenderLayer layer )
	{
		return true;
	}

	static ExceptionNoTileEntity noTileEntity = new ExceptionNoTileEntity();

	public static @Nonnull TileEntityBlockChiseled getTileEntity(
			final TileEntity te ) throws ExceptionNoTileEntity
	{
		if ( te == null )
		{
			throw noTileEntity;
		}

		try
		{
			return (TileEntityBlockChiseled) te;
		}
		catch ( final ClassCastException e )
		{
			throw noTileEntity;
		}
	}

	public static @Nonnull TileEntityBlockChiseled getTileEntity(
			final @Nonnull IBlockAccess world,
			final @Nonnull BlockPos pos ) throws ExceptionNoTileEntity
	{
		final TileEntity te = ModUtil.getTileEntitySafely( world, pos );

		if ( te instanceof TileEntityBlockChiseled )
		{
			return (TileEntityBlockChiseled) te;
		}

		return getTileEntity( MCMultipartProxy.proxyMCMultiPart.getPartFromBlockAccess( world, pos ) );
	}

	@Override
	public float getAmbientOcclusionLightValue(
			final IBlockState state )
	{
		return isFullCube( state ) ? 0.2F : 1.0F;
	}

	@Override
	public boolean isOpaqueCube(
			final IBlockState state )
	{
		return state.getValue( LProperty_FullBlock );
	}

	@Override
	public boolean isFullCube(
			final IBlockState state )
	{
		return state.getValue( LProperty_FullBlock );
	}

	@Override
	public IBlockState getExtendedState(
			final IBlockState state,
			final IBlockAccess world,
			final BlockPos pos )
	{
		try
		{
			return getTileEntity( world, pos ).getRenderState( world );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return state;
		}
		catch ( final Throwable err )
		{
			Log.logError( "Unable to get extended state...", err );
			return state;
		}
	}

	@Override
	public void dropBlockAsItemWithChance(
			final World worldIn,
			final BlockPos pos,
			final IBlockState state,
			final float chance,
			final int fortune )
	{
		try
		{
			spawnAsEntity( worldIn, pos, getTileEntity( worldIn, pos ).getItemStack( null ) );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
		}
	}

	@Override
	public void harvestBlock(
			final World worldIn,
			final EntityPlayer player,
			final BlockPos pos,
			final IBlockState state,
			final TileEntity te,
			final ItemStack stack )
	{
		try
		{
			spawnAsEntity( worldIn, pos, getTileEntity( te ).getItemStack( player ) );

		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			super.harvestBlock( worldIn, player, pos, state, (TileEntity) null, stack );
		}
	}

	@Override
	public List<ItemStack> getDrops(
			final IBlockAccess world,
			final BlockPos pos,
			final IBlockState state,
			final int fortune )
	{
		try
		{
			return Collections.singletonList( getTileEntity( world, pos ).getItemStack( null ) );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return Collections.emptyList();
		}
	}

	@Override
	public void onBlockPlacedBy(
			final World worldIn,
			final BlockPos pos,
			final IBlockState state,
			final EntityLivingBase placer,
			final ItemStack stack )
	{
		try
		{
			if ( stack == null || placer == null || !stack.hasTagCompound() )
			{
				return;
			}

			final TileEntityBlockChiseled bc = getTileEntity( worldIn, pos );
			int rotations = ModUtil.getRotations( placer, ModUtil.getSide( stack ) );

			VoxelBlob blob = bc.getBlob();
			while ( rotations-- > 0 )
			{
				blob = blob.spin( Axis.Y );
			}
			bc.setBlob( blob );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
		}
	}

	@Override
	public ItemStack getPickBlock(
			final IBlockState state,
			final RayTraceResult target,
			final World world,
			final BlockPos pos,
			final EntityPlayer player )
	{
		try
		{
			return getPickBlock( target, pos, getTileEntity( world, pos ) );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return ModUtil.getEmptyStack();
		}
	}

	/**
	 * Client side method.
	 */
	private ChiselToolType getClientHeldTool()
	{
		return ClientSide.instance.getHeldToolType( EnumHand.MAIN_HAND );
	}

	public ItemStack getPickBlock(
			final RayTraceResult target,
			final BlockPos pos,
			final TileEntityBlockChiseled te )
	{
		if ( te.getWorld().isRemote )
		{
			if ( getClientHeldTool() != null )
			{
				final VoxelBlob vb = te.getBlob();

				final BitLocation bitLoc = new BitLocation( target, true, BitOperation.CHISEL );

				final int itemBlock = vb.get( bitLoc.bitX, bitLoc.bitY, bitLoc.bitZ );
				if ( itemBlock == 0 )
				{
					return ModUtil.getEmptyStack();
				}

				return ItemChiseledBit.createStack( itemBlock, 1, false );
			}

			return te.getItemStack( ClientSide.instance.getPlayer() );
		}

		return te.getItemStack( null );
	}

	@Override
	protected BlockStateContainer createBlockState()
	{
		return new ExtendedBlockState( this, new IProperty[] { LProperty_FullBlock }, new IUnlistedProperty[] { UProperty_VoxelBlob, UProperty_Primary_BlockState, UProperty_VoxelNeighborState } );
	}

	@Override
	public TileEntity createNewTileEntity(
			final World worldIn,
			final int meta )
	{
		return new TileEntityBlockChiseled();
	}

	@Override
	public void breakBlock(
			final World worldIn,
			final BlockPos pos,
			final IBlockState state )
	{
		try
		{
			final TileEntityBlockChiseled tebc = getTileEntity( worldIn, pos );
			tebc.setNormalCube( false );

			worldIn.checkLight( pos );
		}
		catch ( final ExceptionNoTileEntity e )
		{

		}
		finally
		{
			super.breakBlock( worldIn, pos, state );
		}
	}

	@Override
	public boolean addLandingEffects(
			final IBlockState state,
			final WorldServer world,
			final BlockPos blockPosition,
			final IBlockState iblockstate,
			final EntityLivingBase entity,
			final int numberOfParticles )
	{
		try
		{
			final IBlockState texture = getTileEntity( world, blockPosition ).getBlockState( Blocks.STONE );
			world.spawnParticle( EnumParticleTypes.BLOCK_DUST, entity.posX, entity.posY, entity.posZ, numberOfParticles, 0.0D, 0.0D, 0.0D, 0.15000000596046448D, new int[] { ModUtil.getStateId( texture ) } );
			return true;
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return false;
		}
	}

	@Override
	@SideOnly( Side.CLIENT )
	public boolean addDestroyEffects(
			final World world,
			final BlockPos pos,
			final ParticleManager effectRenderer )
	{
		try
		{
			final IBlockState state = getTileEntity( world, pos ).getBlockState( this );
			return ClientSide.instance.addBlockDestroyEffects( world, pos, state, effectRenderer );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
		}

		return true;
	}

	@Override
	@SideOnly( Side.CLIENT )
	public boolean addHitEffects(
			final IBlockState state,
			final World world,
			final RayTraceResult target,
			final ParticleManager effectRenderer )
	{
		try
		{
			final BlockPos pos = target.getBlockPos();
			final IBlockState bs = getTileEntity( world, pos ).getBlockState( this );
			return ClientSide.instance.addHitEffects( world, target, bs, effectRenderer );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return true;
		}
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBox(
			final IBlockState blockState,
			final IBlockAccess worldIn,
			final BlockPos pos )
	{
		AxisAlignedBB r = null;

		try
		{
			for ( final AxisAlignedBB bb : getTileEntity( worldIn, pos ).getBoxes( BoxType.COLLISION ) )
			{
				if ( r == null )
				{
					r = bb;
				}
				else
				{
					r = r.union( bb );
				}
			}
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
		}

		if ( r == null )
		{
			return BAD_AABB;
		}

		return r.offset( pos.getX(), pos.getY(), pos.getZ() );
	}

	@Override
	public void addCollisionBoxToList(
			IBlockState state,
			World worldIn,
			BlockPos pos,
			AxisAlignedBB mask,
			List<AxisAlignedBB> list,
			Entity collidingEntity,
			boolean p_185477_7_ )
	{
		try
		{
			addCollisionBoxesToList( getTileEntity( worldIn, pos ), pos, mask, list, collidingEntity );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
		}
	}

	public void addCollisionBoxesToList(
			final TileEntityBlockChiseled te,
			final BlockPos pos,
			final AxisAlignedBB mask,
			final List<AxisAlignedBB> list,
			final Entity collidingEntity )
	{
		final AxisAlignedBB localMask = mask.offset( -pos.getX(), -pos.getY(), -pos.getZ() );

		for ( final AxisAlignedBB bb : te.getBoxes( BoxType.COLLISION ) )
		{
			if ( bb.intersects( localMask ) )
			{
				list.add( bb.offset( pos.getX(), pos.getY(), pos.getZ() ) );
			}
		}
	}

	/**
	 * this method dosn't use AxisAlignedBB internally to prevent GC thrashing.
	 *
	 * @param pos
	 *
	 *            mask and list should be null if not looking for collisions
	 *
	 * @return if the method results in a non-full cube box.
	 */
	@Nonnull
	private AxisAlignedBB setBounds(
			final TileEntityBlockChiseled tec,
			final BlockPos pos,
			final AxisAlignedBB mask,
			final List<AxisAlignedBB> list,
			final boolean includePosition )
	{
		boolean started = false;

		float minX = 0.0f;
		float minY = 0.0f;
		float minZ = 0.0f;

		float maxX = 1.0f;
		float maxY = 1.0f;
		float maxZ = 1.0f;

		final VoxelBlob vb = tec.getBlob();

		final BitCollisionIterator bi = new BitCollisionIterator();
		while ( bi.hasNext() )
		{
			if ( bi.getNext( vb ) != 0 )
			{
				if ( started )
				{
					minX = Math.min( minX, bi.physicalX );
					minY = Math.min( minY, bi.physicalY );
					minZ = Math.min( minZ, bi.physicalZ );
					maxX = Math.max( maxX, bi.physicalX + BitCollisionIterator.One16thf );
					maxY = Math.max( maxY, bi.physicalYp1 );
					maxZ = Math.max( maxZ, bi.physicalZp1 );
				}
				else
				{
					started = true;
					minX = bi.physicalX;
					minY = bi.physicalY;
					minZ = bi.physicalZ;
					maxX = bi.physicalX + BitCollisionIterator.One16thf;
					maxY = bi.physicalYp1;
					maxZ = bi.physicalZp1;
				}
			}

			// VERY hackey collision extraction to do 2 bounding boxes, one
			// for top and one for the bottom.
			if ( list != null && started && ( bi.y == 8 || bi.y == VoxelBlob.dim_minus_one ) )
			{
				final AxisAlignedBB bb = new AxisAlignedBB(
						(double) minX + pos.getX(),
						(double) minY + pos.getY(),
						(double) minZ + pos.getZ(),
						(double) maxX + pos.getX(),
						(double) maxY + pos.getY(),
						(double) maxZ + pos.getZ() );

				if ( mask.intersects( bb ) )
				{
					list.add( bb );
				}

				started = false;
				minX = 0.0f;
				minY = 0.0f;
				minZ = 0.0f;
				maxX = 1.0f;
				maxY = 1.0f;
				maxZ = 1.0f;
			}
		}

		if ( includePosition )
		{
			return new AxisAlignedBB(
					(double) minX + pos.getX(),
					(double) minY + pos.getY(),
					(double) minZ + pos.getZ(),
					(double) maxX + pos.getX(),
					(double) maxY + pos.getY(),
					(double) maxZ + pos.getZ() );
		}

		return new AxisAlignedBB(
				minX,
				minY,
				minZ,
				maxX,
				maxY,
				maxZ );
	}

	@Override
	@Deprecated
	public AxisAlignedBB getSelectedBoundingBox(
			final IBlockState state,
			final World worldIn,
			final BlockPos pos )
	{
		try
		{
			return getSelectedBoundingBox( getTileEntity( worldIn, pos ), pos );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
		}

		return super.getSelectedBoundingBox( state, worldIn, pos );
	}

	@Nonnull
	public AxisAlignedBB getSelectedBoundingBox(
			final TileEntityBlockChiseled tec,
			final BlockPos pos )
	{
		return setBounds( tec, pos, null, null, true );
	}

	@Override
	@Deprecated
	public RayTraceResult collisionRayTrace(
			final IBlockState blockState,
			final World worldIn,
			final BlockPos pos,
			final Vec3d a,
			final Vec3d b )
	{
		try
		{
			return collisionRayTrace( getTileEntity( worldIn, pos ), pos, a, b, worldIn.isRemote );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
		}

		return super.collisionRayTrace( blockState, worldIn, pos, a, b );
	}

	public RayTraceResult collisionRayTrace(
			final TileEntityBlockChiseled tec,
			final BlockPos pos,
			final Vec3d a,
			final Vec3d b,
			final boolean realTest )
	{
		RayTraceResult br = null;
		double lastDist = 0;

		boolean occlusion = true;
		if ( FMLCommonHandler.instance().getEffectiveSide().isClient() && tec.getWorld() != null && tec.getWorld().isRemote )
		{
			occlusion = !ChiselsAndBits.getConfig().fluidBitsAreClickThrough || ClientSide.instance.getHeldToolType( EnumHand.MAIN_HAND ) != null;
		}

		for ( final AxisAlignedBB box : tec.getBoxes( occlusion ? BoxType.OCCLUSION : BoxType.COLLISION ) )
		{
			final RayTraceResult r = rayTrace( pos, a, b, box );

			if ( r != null )
			{
				final double xLen = a.x - r.hitVec.x;
				final double yLen = a.y - r.hitVec.y;
				final double zLen = a.z - r.hitVec.z;

				final double thisDist = xLen * xLen + yLen * yLen + zLen * zLen;
				if ( br == null || lastDist > thisDist && r != null )
				{
					lastDist = thisDist;
					br = r;
				}

			}
		}

		return br;
	}

	@Override
	@Deprecated
	public float getBlockHardness(
			final IBlockState state,
			final World worldIn,
			final BlockPos pos )
	{
		try
		{
			return getTileEntity( worldIn, pos ).getBlockInfo( this ).hardness;
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return super.getBlockHardness( state, worldIn, pos );
		}
	}

	@Override
	public float getExplosionResistance(
			final World world,
			final BlockPos pos,
			final Entity exploder,
			final Explosion explosion )
	{
		try
		{
			return getTileEntity( world, pos ).getBlockInfo( this ).explosionResistance;
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return super.getExplosionResistance( world, pos, exploder, explosion );
		}
	}

	public static boolean replaceWithChisled(
			final World world,
			final BlockPos pos,
			final IBlockState originalState,
			final boolean triggerUpdate )
	{
		return replaceWithChisled( world, pos, originalState, 0, triggerUpdate ).success;
	}

	@Override
	public boolean canPlaceTorchOnTop(
			final IBlockState state,
			final IBlockAccess world,
			final BlockPos pos )
	{
		return isSideSolid( state, world, pos, EnumFacing.UP );
	}

	@Override
	public boolean isSideSolid(
			final IBlockState base_state,
			final IBlockAccess world,
			final BlockPos pos,
			final EnumFacing side )
	{
		try
		{
			return getTileEntity( world, pos ).isSideSolid( side );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return false;
		}
	}

	@Override
	public boolean rotateBlock(
			final World world,
			final BlockPos pos,
			final EnumFacing axis )
	{
		try
		{
			getTileEntity( world, pos ).rotateBlock( axis );
			return true;
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return false;
		}
	}

	public static class ReplaceWithChisledValue {
		public boolean success = false;
		public TileEntityBlockChiseled te = null;
	};

	public static ReplaceWithChisledValue replaceWithChisled(
			final @Nonnull World world,
			final @Nonnull BlockPos pos,
			final IBlockState originalState,
			final int fragmentBlockStateID,
			final boolean triggerUpdate )
	{
		IBlockState actingState = originalState;
		Block target = originalState.getBlock();
		final boolean isAir = world.isAirBlock( pos ) || actingState.getBlock().isReplaceable( world, pos );
		ReplaceWithChisledValue rv = new ReplaceWithChisledValue();

		if ( BlockBitInfo.supportsBlock( actingState ) || isAir )
		{
			BlockChiseled blk = ChiselsAndBits.getBlocks().getConversion( originalState );

			int BlockID = ModUtil.getStateId( actingState );

			if ( isAir )
			{
				actingState = ModUtil.getStateById( fragmentBlockStateID );
				target = actingState.getBlock();
				BlockID = ModUtil.getStateId( actingState );
				blk = ChiselsAndBits.getBlocks().getConversion( actingState );
				// its still air tho..
				actingState = Blocks.AIR.getDefaultState();
			}

			if ( BlockID == 0 )
			{
				return rv;
			}

			if ( blk != null && blk != target )
			{
				TileEntityBlockChiseled.setLightFromBlock( actingState );
				world.setBlockState( pos, blk.getDefaultState(), triggerUpdate ? 3 : 0 );
				TileEntityBlockChiseled.setLightFromBlock( null );
				final TileEntity te = world.getTileEntity( pos );

				TileEntityBlockChiseled tec;
				if ( !( te instanceof TileEntityBlockChiseled ) )
				{
					tec = (TileEntityBlockChiseled) blk.createTileEntity( world, blk.getDefaultState() );
					world.setTileEntity( pos, tec );
				}
				else
				{
					tec = (TileEntityBlockChiseled) te;
				}

				if ( tec != null )
				{
					tec.fillWith( actingState );
					tec.setState( tec.getBasicState().withProperty( BlockChiseled.UProperty_Primary_BlockState, BlockID ) );
				}

				rv.success = true;
				rv.te = tec;

				return rv;
			}
		}

		return rv;
	}

	public IBlockState getCommonState(
			final IExtendedBlockState myState )
	{
		final VoxelBlobStateReference data = myState.getValue( BlockChiseled.UProperty_VoxelBlob );

		if ( data != null )
		{
			final VoxelBlob vb = data.getVoxelBlob();
			if ( vb != null )
			{
				return ModUtil.getStateById( vb.getVoxelStats().mostCommonState );
			}
		}

		return null;
	}

	@Override
	public int getLightValue(
			final IBlockState state,
			final IBlockAccess world,
			final BlockPos pos )
	{
		// is this the right block?
		final IBlockState realState = world.getBlockState( pos );
		final Block realBlock = realState.getBlock();
		if ( realBlock != this )
		{
			return realBlock.getLightValue( realState, world, pos );
		}

		// enabled?
		if ( ChiselsAndBits.getConfig().enableBitLightSource )
		{
			try
			{
				return getTileEntity( world, pos ).getLightValue();
			}
			catch ( final ExceptionNoTileEntity e )
			{
				Log.noTileError( e );
			}
		}

		return 0;
	}

	public static void setActingAs(
			final IBlockState state )
	{
		actingAs.set( state );
	}

	@Override
	public boolean canHarvestBlock(
			final IBlockAccess world,
			final BlockPos pos,
			final EntityPlayer player )
	{
		if ( ChiselsAndBits.getConfig().enableToolHarvestLevels )
		{
			IBlockState state = actingAs.get();

			if ( state == null )
			{
				state = getPrimaryState( world, pos );
			}

			return state.getBlock().canHarvestBlock( new HarvestWorld( state ), pos, player );
		}

		return true;
	}

	@Override
	@Deprecated
	public float getPlayerRelativeBlockHardness(
			final IBlockState passedState,
			final EntityPlayer player,
			final World world,
			final BlockPos pos )
	{
		if ( ChiselsAndBits.getConfig().enableToolHarvestLevels )
		{
			IBlockState state = actingAs.get();

			if ( state == null )
			{
				state = getPrimaryState( world, pos );
			}

			final float hardness = getBlockHardness( state, world, pos );
			if ( hardness < 0.0F )
			{
				return 0.0F;
			}

			// since we can't call getDigSpeed on the acting state, we can just
			// do some math to try and roughly estimate it.
			float denom = player.inventory.getDestroySpeed( passedState );
			float numer = player.inventory.getDestroySpeed( state );

			if ( !state.getBlock().canHarvestBlock( new HarvestWorld( state ), pos, player ) )
			{
				return player.getDigSpeed( passedState, pos ) / hardness / 100F * ( numer / denom );
			}
			else
			{
				return player.getDigSpeed( passedState, pos ) / hardness / 30F * ( numer / denom );
			}
		}

		return super.getPlayerRelativeBlockHardness( passedState, player, world, pos );
	}

	@Override
	public boolean isToolEffective(
			final String type,
			final IBlockState state )
	{
		return Blocks.STONE.isToolEffective( type, Blocks.STONE.getDefaultState() );
	}

	@Override
	public String getHarvestTool(
			final IBlockState state )
	{
		return Blocks.STONE.getHarvestTool( Blocks.STONE.getDefaultState() );
	}

	@Override
	public int getHarvestLevel(
			final IBlockState state )
	{
		return Blocks.STONE.getHarvestLevel( Blocks.STONE.getDefaultState() );
	}

	public ResourceLocation getModel()
	{
		return new ResourceLocation( ChiselsAndBits.MODID, name );
	}

	@Override
	public void getSubBlocks(
			final CreativeTabs tab,
			final NonNullList<ItemStack> list )
	{
		// no items.
	}

	// shared for part and block.
	public static Boolean sharedIsAABBInsideMaterial(
			final TileEntityBlockChiseled tebc,
			final AxisAlignedBB bx,
			final Material materialIn )
	{
		if ( materialIn == Material.WATER )
		{
			for ( final AxisAlignedBB b : tebc.getBoxes( BoxType.SWIMMING ) )
			{
				if ( b.intersects( bx ) )
				{
					return true;
				}
			}
		}

		return false;
	}

	// shared for part and block.
	public static Boolean sharedIsEntityInsideMaterial(
			final TileEntityBlockChiseled tebc,
			final BlockPos pos,
			final Entity entity,
			final double yToTest,
			final Material materialIn,
			final boolean testingHead )
	{
		if ( testingHead && materialIn == Material.WATER )
		{
			Vec3d head = entity.getPositionVector();
			head = new Vec3d( head.x - pos.getX(), yToTest - pos.getY(), head.z - pos.getZ() );

			for ( final AxisAlignedBB b : tebc.getBoxes( BoxType.SWIMMING ) )
			{
				if ( b.contains( head ) )
				{
					return true;
				}
			}
		}
		else if ( !testingHead && materialIn == Material.WATER )
		{
			AxisAlignedBB what = entity.getCollisionBoundingBox();

			if ( what == null )
			{
				what = entity.getEntityBoundingBox();
			}

			if ( what != null )
			{
				what = what.offset( -pos.getX(), -pos.getY(), -pos.getZ() );
				for ( final AxisAlignedBB b : tebc.getBoxes( BoxType.SWIMMING ) )
				{
					if ( b.intersects( what ) )
					{
						return true;
					}
				}
			}
		}

		return false;
	}

	@Override
	public IBlockState getPrimaryState(
			final IBlockAccess world,
			final BlockPos pos )
	{
		try
		{
			return getTileEntity( world, pos ).getBlockState( Blocks.STONE );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return Blocks.STONE.getDefaultState();
		}
	}

	public boolean basicHarvestBlockTest(
			World world,
			BlockPos pos,
			EntityPlayer player )
	{
		return super.canHarvestBlock( world, pos, player );
	}

}
