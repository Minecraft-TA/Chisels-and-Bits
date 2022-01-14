package mod.chiselsandbits.chiseledblock;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

class ReflectionHelperBlock extends Block
{
	public String MethodName;

	private void markMethod()
	{
		MethodName = new Throwable().fillInStackTrace().getStackTrace()[1].getMethodName();
	}

	protected ReflectionHelperBlock()
	{
		super( Material.AIR );
	}

	@Override
	public float getBlockHardness(
			final @Nullable IBlockState state,
			final @Nullable World world,
			final @Nullable BlockPos pos )
	{
		markMethod();
		return 0;
	}

@Override
public void addCollisionBoxToList(IBlockState state, World worldIn, BlockPos pos, AxisAlignedBB entityBox,
		List<AxisAlignedBB> collidingBoxes, Entity entityIn, boolean p_185477_7_) {
	markMethod();
}
	@Override
	public float getPlayerRelativeBlockHardness(
			final @Nullable IBlockState state,
			final @Nullable EntityPlayer player,
			final @Nullable World world,
			final @Nullable BlockPos pos )
	{
		markMethod();
		return 0;
	}

	@Override
	public float getExplosionResistance(
			final @Nullable Entity exploder )
	{
		markMethod();
		return 0;
	}

	@Override
	public float getExplosionResistance(
			final @Nullable World world,
			final @Nullable BlockPos pos,
			final @Nullable Entity exploder,
			final @Nullable Explosion explosion )
	{
		markMethod();
		return 0;
	}

	@Override
	public int quantityDropped(
			final @Nullable IBlockState state,
			final int fortune,
			final @Nullable Random random )
	{

		markMethod();
		return 0;
	}

	@Override
	public int quantityDropped(
			final @Nullable Random random )
	{
		markMethod();
		return 0;
	}

	@Override
	public int quantityDroppedWithBonus(
			final int fortune,
			final @Nullable Random random )
	{
		markMethod();
		return 0;
	}

	@Override
	public void onEntityCollision(
			final @Nullable World worldIn,
			final @Nullable BlockPos pos,
			final @Nullable IBlockState state,
			final @Nullable Entity entityIn )
	{
		markMethod();
	}
}
