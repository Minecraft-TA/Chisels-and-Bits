package mod.chiselsandbits.network.packets;

import mod.chiselsandbits.bitbag.BagContainer;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.items.ItemChiseledBit;
import mod.chiselsandbits.network.ModPacket;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

public class PacketBagGuiStack extends ModPacket
{
	public int index = -1;
	public ItemStack is;

	@Override
	public void client()
	{
		final Container cc = ClientSide.instance.getPlayer().openContainer;
		if ( cc instanceof BagContainer )
		{
			( (BagContainer) cc ).customSlots.get( index ).putStack( is );
		}
	}

	@Override
	public void getPayload(
			final PacketBuffer buffer )
	{
		buffer.writeVarInt( index );

		if ( is == null )
		{
			buffer.writeVarInt( 0 );
		}
		else
		{
			buffer.writeVarInt( ModUtil.getStackSize( is ) );
			buffer.writeVarInt( ItemChiseledBit.getStackState( is ) );
		}
	}

	@Override
	public void readPayload(
			final PacketBuffer buffer )
	{
		index = buffer.readVarInt();

		final int size = buffer.readVarInt();

		if ( size <= 0 )
		{
			is = null;
		}
		else
		{
			is = ItemChiseledBit.createStack( buffer.readVarInt(), size, false );
		}
	}

}
