package mod.chiselsandbits.network.packets;

import mod.chiselsandbits.bitbag.BagContainer;
import mod.chiselsandbits.network.ModPacket;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import java.io.IOException;

public class PacketClearBagGui extends ModPacket
{
	ItemStack stack = null;

	public PacketClearBagGui()
	{
	}

	public PacketClearBagGui(
			final ItemStack inHandItem )
	{
		stack = inHandItem;
	}

	@Override
	public void server(
			final EntityPlayerMP player )
	{
		if ( player.openContainer instanceof BagContainer )
		{
			( (BagContainer) player.openContainer ).clear( stack );
		}
	}

	@Override
	public void getPayload(
			final PacketBuffer buffer )
	{
		buffer.writeItemStack( stack );
		// no data...
	}

	@Override
	public void readPayload(
			final PacketBuffer buffer )
	{
		try
		{
			stack = buffer.readItemStack();
		}
		catch ( final IOException e )
		{
			stack = null;
		}
	}

}
