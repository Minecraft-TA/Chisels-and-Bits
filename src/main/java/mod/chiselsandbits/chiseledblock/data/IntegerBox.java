package mod.chiselsandbits.chiseledblock.data;

import net.minecraft.util.EnumFacing;

public final class IntegerBox
{
	public IntegerBox(
			final int x1,
			final int y1,
			final int z1,
			final int x2,
			final int y2,
			final int z2 )
	{
		minX = x1;
		maxX = x2;

		minY = y1;
		maxY = y2;

		minZ = z1;
		maxZ = z2;
	}

	public int minX;
	public int minY;
	public int minZ;
	public int maxX;
	public int maxY;
	public int maxZ;

	public void move(
			final EnumFacing side,
			final int scale )
	{
		minX += side.getXOffset() * scale;
		maxX += side.getXOffset() * scale;
		minY += side.getYOffset() * scale;
		maxY += side.getYOffset() * scale;
		minZ += side.getZOffset() * scale;
		maxZ += side.getZOffset() * scale;
	}

	public boolean isBadBitPositions()
	{
		return minX < 0 || minY < 0 || minZ < 0 || maxX >= 16 || maxY >= 16 || maxZ >= 16;
	}
}
