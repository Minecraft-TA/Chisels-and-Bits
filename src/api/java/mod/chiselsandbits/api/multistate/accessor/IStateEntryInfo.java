package mod.chiselsandbits.api.multistate.accessor;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.vector.Vector3d;

/**
 * Represents a single entry inside an area which can have multiple states.
 *
 * @see IAreaAccessor
 * @see mod.chiselsandbits.api.multistate.accessor.world.IWorldAreaAccessor
 * @see mod.chiselsandbits.api.multistate.accessor.world.IInWorldStateEntryInfo
 */
public interface IStateEntryInfo
{
    /**
     * The state that this entry represents.
     *
     * @return The state.
     */
    BlockState getState();

    /**
     * The start (lowest on all three axi) position of the state that this entry occupies.
     *
     * @return The start position of this entry in the given block.
     */
    Vector3d getStartPoint();

    /**
     * The end (highest on all three axi) position of the state that this entry occupies.
     *
     * @return The start position of this entry in the given block.
     */
    Vector3d getEndPoint();
}
