package mod.chiselsandbits.commands;

import mod.chiselsandbits.api.APIExceptions.CannotBeChiseled;
import mod.chiselsandbits.api.APIExceptions.InvalidBitItem;
import mod.chiselsandbits.api.APIExceptions.SpaceOccupied;
import mod.chiselsandbits.api.IBitAccess;
import mod.chiselsandbits.api.IBitBrush;
import mod.chiselsandbits.api.IChiselAndBitsAPI;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.helpers.DeprecationHelper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class SetBit extends CommandBase
{

	@Override
	public String getName()
	{
		return "setbit";
	}

	@Override
	public int getRequiredPermissionLevel()
	{
		return 2;
	}

	@Override
	public String getUsage(
			final ICommandSender sender )
	{
		return "commands.setbit.usage";
	}

	@Override
	public void execute(
			final MinecraftServer server,
			final ICommandSender sender,
			final String[] args ) throws CommandException
	{
		if ( args.length < 7 )
		{
			throw new WrongUsageException( "commands.setbit.usage" );
		}
		else
		{
			final BlockPos blockpos = parseBlockPos( sender, args, 0, false );
			final BlockPos bitpos = parseBlockPos( sender, args, 3, false );

			final Block block = CommandBase.getBlockByText( sender, args[6] );
			int meta = 0;

			if ( args.length >= 8 )
			{
				meta = parseInt( args[7], 0, 15 );
			}

			final IBlockState state = DeprecationHelper.getStateFromMeta( block, meta );
			final World world = sender.getEntityWorld();

			if ( !world.isBlockLoaded( blockpos ) )
			{
				throw new CommandException( "commands.setbit.outOfWorld", new Object[0] );
			}
			else
			{
				try
				{
					final IChiselAndBitsAPI api = ChiselsAndBits.getApi();
					final IBitAccess ba = api.getBitAccess( world, blockpos );

					final IBitBrush bit = ba.getBitAt( bitpos.getX(), bitpos.getY(), bitpos.getZ() );
					final IBitBrush brush = api.createBrushFromState( state );

					if ( bit == brush )
					{
						throw new CommandException( "commands.setbit.noChange" );
					}
					else
					{
						ba.setBitAt( bitpos.getX(), bitpos.getY(), bitpos.getZ(), brush );
						ba.commitChanges( true );
						sender.setCommandStat( CommandResultStats.Type.AFFECTED_BLOCKS, 1 );
						notifyCommandListener( sender, this, "commands.setbit.success" );
					}
				}
				catch ( final CannotBeChiseled e )
				{
					throw new CommandException( "commands.setbit.cannotChiselBlock" );
				}
				catch ( final InvalidBitItem e )
				{
					throw new CommandException( "commands.setbit.invalidState" );
				}
				catch ( final SpaceOccupied e )
				{
					throw new CommandException( "commands.setbit.spaceOccupied" );
				}

			}
		}
	}

	@Override
	public List<String> getTabCompletions(
			final MinecraftServer server,
			final ICommandSender sender,
			final String[] args,
			@Nullable final BlockPos pos )
	{
		return args.length > 0 && args.length <= 3 ? getTabCompletionCoordinate( args, 0, pos )
				: args.length == 7 ? getListOfStringsMatchingLastWord( args, Block.REGISTRY.getKeys() ) : Collections.<String> emptyList();
	}
}
