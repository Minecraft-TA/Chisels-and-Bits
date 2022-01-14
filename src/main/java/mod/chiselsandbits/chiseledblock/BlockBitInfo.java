package mod.chiselsandbits.chiseledblock;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import mod.chiselsandbits.api.IgnoreBlockLogic;
import mod.chiselsandbits.chiseledblock.data.VoxelType;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.Log;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.render.helpers.ModelUtil;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import java.util.HashMap;
import java.util.Random;

public class BlockBitInfo
{
	// imc api...
	private static HashMap<Block, Boolean> ignoreLogicBlocks = new HashMap<Block, Boolean>();

	static
	{
		ignoreLogicBlocks.put( Blocks.LEAVES, true );
		ignoreLogicBlocks.put( Blocks.LEAVES2, true );
		ignoreLogicBlocks.put( Blocks.SNOW, true );
	}

	// cache data..
	private static HashMap<IBlockState, BlockBitInfo> stateBitInfo = new HashMap<IBlockState, BlockBitInfo>();
	private static HashMap<Block, Boolean> supportedBlocks = new HashMap<Block, Boolean>();
	private static HashMap<IBlockState, Boolean> forcedStates = new HashMap<IBlockState, Boolean>();
	private static HashMap<Block, Fluid> fluidBlocks = new HashMap<Block, Fluid>();
	private static TIntObjectMap<Fluid> fluidStates = new TIntObjectHashMap<Fluid>();
	private static HashMap<IBlockState, Integer> bitColor = new HashMap<IBlockState, Integer>();

	public static int getColorFor(
			final IBlockState state,
			final int tint )
	{
		Integer out = bitColor.get( state );

		if ( out == null )
		{
			final Block blk = state.getBlock();

			final Fluid fluid = BlockBitInfo.getFluidFromBlock( blk );
			if ( fluid != null )
			{
				out = fluid.getColor();
			}
			else
			{
				final ItemStack target = ModUtil.getItemFromBlock( state );

				if ( ModUtil.isEmpty( target ) )
				{
					out = 0xffffff;
				}
				else
				{
					out = ModelUtil.getItemStackColor( target, tint );
				}
			}

			bitColor.put( state, out );
		}

		return out;
	}

	public static void recalculateFluidBlocks()
	{
		fluidBlocks.clear();

		for ( final Fluid o : FluidRegistry.getRegisteredFluids().values() )
		{
			if ( o.canBePlacedInWorld() )
			{
				BlockBitInfo.addFluidBlock( o.getBlock(), o );
			}
		}
	}

	public static void addFluidBlock(
			final Block blk,
			final Fluid fluid )
	{
		if ( blk == null )
		{
			return;
		}

		fluidBlocks.put( blk, fluid );

		for ( final IBlockState state : blk.getBlockState().getValidStates() )
		{
			try
			{
				fluidStates.put( ModUtil.getStateId( state ), fluid );
			}
			catch ( final Throwable t )
			{
				Log.logError( "Error while determining fluid state.", t );
			}
		}

		stateBitInfo.clear();
		supportedBlocks.clear();
	}

	static public Fluid getFluidFromBlock(
			final Block blk )
	{
		return fluidBlocks.get( blk );
	}

	public static VoxelType getTypeFromStateID(
			final int bit )
	{
		if ( bit == 0 )
		{
			return VoxelType.AIR;
		}

		return fluidStates.containsKey( bit ) ? VoxelType.FLUID : VoxelType.SOLID;
	}

	public static void ignoreBlockLogic(
			final Block which )
	{
		ignoreLogicBlocks.put( which, true );
		reset();
	}

	public static void forceStateCompatibility(
			final IBlockState which,
			final boolean forceStatus )
	{
		forcedStates.put( which, forceStatus );
		reset();
	}

	public static void reset()
	{
		stateBitInfo.clear();
		supportedBlocks.clear();
	}

	public static BlockBitInfo getBlockInfo(
			final IBlockState state )
	{
		BlockBitInfo bit = stateBitInfo.get( state );

		if ( bit == null )
		{
			bit = BlockBitInfo.createFromState( state );
			stateBitInfo.put( state, bit );
		}

		return bit;
	}

	@SuppressWarnings( "deprecation" )
	public static boolean supportsBlock(
			final IBlockState state )
	{
		if ( forcedStates.containsKey( state ) )
		{
			return forcedStates.get( state );
		}

		final Block blk = state.getBlock();
		if ( supportedBlocks.containsKey( blk ) )
		{
			return supportedBlocks.get( blk );
		}

		try
		{
			// require basic hardness behavior...
			final ReflectionHelperBlock pb = new ReflectionHelperBlock();
			final Class<? extends Block> blkClass = blk.getClass();

			// custom dropping behavior?
			pb.quantityDropped( null );
			final Class<?> wc = getDeclaringClass( blkClass, pb.MethodName, Random.class );
			final boolean quantityDroppedTest = wc == Block.class || wc == BlockGlowstone.class || wc == BlockStainedGlass.class || wc == BlockGlass.class || wc == BlockSnowBlock.class;

			pb.quantityDroppedWithBonus( 0, null );
			final boolean quantityDroppedWithBonusTest = getDeclaringClass( blkClass, pb.MethodName, int.class, Random.class ) == Block.class || wc == BlockGlowstone.class;

			pb.quantityDropped( null, 0, null );
			final boolean quantityDropped2Test = getDeclaringClass( blkClass, pb.MethodName, IBlockState.class, int.class, Random.class ) == Block.class;

			final boolean isNotSlab = Item.getItemFromBlock( blk ) != null;
			boolean itemExistsOrNotSpecialDrops = quantityDroppedTest && quantityDroppedWithBonusTest && quantityDropped2Test || isNotSlab;

			// ignore blocks with custom collision.
			pb.onEntityCollision( null, null, null, null );
			boolean noCustomCollision = getDeclaringClass( blkClass, pb.MethodName, World.class, BlockPos.class, IBlockState.class, Entity.class ) == Block.class || blkClass == BlockSlime.class;

			// full cube specifically is tied to lighting... so for glass
			// Compatibility use isFullBlock which can be true for glass.
			boolean isFullBlock = blk.isFullBlock( state ) || blkClass == BlockStainedGlass.class || blkClass == BlockGlass.class || blk == Blocks.SLIME_BLOCK || blk == Blocks.ICE;

			final BlockBitInfo info = BlockBitInfo.createFromState( state );

			final boolean tickingBehavior = blk.getTickRandomly() && ChiselsAndBits.getConfig().blacklistTickingBlocks;
			boolean hasBehavior = ( blk.hasTileEntity( state ) || tickingBehavior ) && blkClass != BlockGrass.class && blkClass != BlockMycelium.class && blkClass != BlockIce.class;
			final boolean hasItem = Item.getItemFromBlock( blk ) != null;

			final boolean supportedMaterial = ChiselsAndBits.getBlocks().getConversion( state ) != null;

			final Boolean IgnoredLogic = ignoreLogicBlocks.get( blk );
			if ( blkClass.isAnnotationPresent( IgnoreBlockLogic.class ) || IgnoredLogic != null && IgnoredLogic )
			{
				isFullBlock = true;
				noCustomCollision = true;
				hasBehavior = false;
				itemExistsOrNotSpecialDrops = true;
			}

			if ( info.isCompatiable && noCustomCollision && info.hardness >= -0.01f && isFullBlock && supportedMaterial && !hasBehavior && itemExistsOrNotSpecialDrops )
			{
				final boolean result = hasItem && ChiselsAndBits.getConfig().isEnabled( blkClass.getName() );
				supportedBlocks.put( blk, result );

				if ( result )
				{
					stateBitInfo.put( state, info );
				}

				return result;
			}

			if ( fluidBlocks.containsKey( blk ) )
			{
				stateBitInfo.put( state, info );
				supportedBlocks.put( blk, true );
				return true;
			}

			supportedBlocks.put( blk, false );
			return false;
		}
		catch ( final Throwable t )
		{
			// if the above test fails for any reason, then the block cannot be
			// supported.
			supportedBlocks.put( blk, false );
			return false;
		}
	}

	private static Class<?> getDeclaringClass(
			final Class<?> blkClass,
			final String methodName,
			final Class<?>... args )
	{
		try
		{
			blkClass.getDeclaredMethod( methodName, args );
			return blkClass;
		}
		catch ( final NoSuchMethodException e )
		{
			// nothing here...
		}
		catch ( final SecurityException e )
		{
			// nothing here..
		}
		catch ( final NoClassDefFoundError e )
		{
			Log.eligibility( "Unable to determine blocks eligibility for chiseling, " + blkClass.getName() + " attempted to load " + e.getMessage() + " missing @SideOnly( Side.CLIENT ) or @Optional?" );
			return blkClass;
		}
		catch ( final Throwable t )
		{
			return blkClass;
		}

		return getDeclaringClass(
				blkClass.getSuperclass(),
				methodName,
				args );
	}

	public final boolean isCompatiable;
	public final float hardness;
	public final float explosionResistance;

	private BlockBitInfo(
			final boolean isCompatiable,
			final float hardness,
			final float explosionResistance )
	{
		this.isCompatiable = isCompatiable;
		this.hardness = hardness;
		this.explosionResistance = explosionResistance;
	}

	public static BlockBitInfo createFromState(
			final IBlockState state )
	{
		try
		{
			// require basic hardness behavior...
			final ReflectionHelperBlock reflectBlock = new ReflectionHelperBlock();
			final Block blk = state.getBlock();
			final Class<? extends Block> blkClass = blk.getClass();

			reflectBlock.getBlockHardness( null, null, null );
			final Class<?> hardnessMethodClass = getDeclaringClass( blkClass, reflectBlock.MethodName, IBlockState.class, World.class, BlockPos.class );
			final boolean test_a = hardnessMethodClass == Block.class;

			reflectBlock.getPlayerRelativeBlockHardness( null, null, null, null );
			final boolean test_b = getDeclaringClass( blkClass, reflectBlock.MethodName, IBlockState.class, EntityPlayer.class, World.class, BlockPos.class ) == Block.class;

			reflectBlock.getExplosionResistance( null );
			final Class<?> exploResistanceClz = getDeclaringClass( blkClass, reflectBlock.MethodName, Entity.class );
			final boolean test_c = exploResistanceClz == Block.class;

			reflectBlock.getExplosionResistance( null, null, null, null );
			final boolean test_d = getDeclaringClass( blkClass, reflectBlock.MethodName, World.class, BlockPos.class, Entity.class, Explosion.class ) == Block.class;

			final boolean isFluid = fluidStates.containsKey( ModUtil.getStateId( state ) );

			// is it perfect?
			if ( test_a && test_b && test_c && test_d && !isFluid )
			{
				final float blockHardness = blk.blockHardness;
				final float resistance = blk.blockResistance;

				return new BlockBitInfo( true, blockHardness, resistance );
			}
			else
			{
				// less accurate, we can just pretend they are some fixed
				// hardness... say like stone?

				final Block stone = Blocks.STONE;
				return new BlockBitInfo( ChiselsAndBits.getConfig().compatabilityMode, stone.blockHardness, stone.blockResistance );
			}
		}
		catch ( final Exception err )
		{
			return new BlockBitInfo( false, -1, -1 );
		}
	}

	public static boolean canChisel(
			final IBlockState state )
	{
		return state.getBlock() instanceof BlockChiseled || supportsBlock( state );
	}

}
