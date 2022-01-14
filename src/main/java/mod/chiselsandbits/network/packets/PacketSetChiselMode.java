package mod.chiselsandbits.network.packets;

import mod.chiselsandbits.helpers.ChiselToolType;
import mod.chiselsandbits.interfaces.IChiselModeItem;
import mod.chiselsandbits.modes.ChiselMode;
import mod.chiselsandbits.modes.IToolMode;
import mod.chiselsandbits.modes.PositivePatternMode;
import mod.chiselsandbits.modes.TapeMeasureModes;
import mod.chiselsandbits.network.ModPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentTranslation;

public class PacketSetChiselMode extends ModPacket
{

	public IToolMode mode = ChiselMode.SINGLE;
	public ChiselToolType type = ChiselToolType.CHISEL;
	public boolean chatNotification = false;

	@Override
	public void server(
			final EntityPlayerMP player )
	{
		final ItemStack ei = player.getHeldItemMainhand();
		if ( ei != null && ei.getItem() instanceof IChiselModeItem )
		{
			final IToolMode originalMode = type.getMode( ei );
			mode.setMode( ei );

			if ( originalMode != mode && chatNotification )
			{
				Minecraft.getMinecraft().player.sendStatusMessage( new TextComponentTranslation( mode.getName().toString() ), true );
			}
		}
	}

	@Override
	public void getPayload(
			final PacketBuffer buffer )
	{
		buffer.writeBoolean( chatNotification );
		buffer.writeEnumValue( type );
		buffer.writeEnumValue( (Enum<?>) mode );
	}

	@Override
	public void readPayload(
			final PacketBuffer buffer )
	{
		chatNotification = buffer.readBoolean();
		type = buffer.readEnumValue( ChiselToolType.class );

		if ( type == ChiselToolType.BIT || type == ChiselToolType.CHISEL )
		{
			mode = buffer.readEnumValue( ChiselMode.class );
		}
		else if ( type == ChiselToolType.POSITIVEPATTERN )
		{
			mode = buffer.readEnumValue( PositivePatternMode.class );
		}
		else if ( type == ChiselToolType.TAPEMEASURE )
		{
			mode = buffer.readEnumValue( TapeMeasureModes.class );
		}
	}

}
