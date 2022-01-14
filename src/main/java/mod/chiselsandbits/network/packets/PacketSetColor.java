package mod.chiselsandbits.network.packets;

import mod.chiselsandbits.helpers.ChiselToolType;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.interfaces.IChiselModeItem;
import mod.chiselsandbits.network.ModPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentTranslation;

public class PacketSetColor extends ModPacket
{

	public EnumDyeColor newColor = EnumDyeColor.WHITE;
	public ChiselToolType type = ChiselToolType.TAPEMEASURE;
	public boolean chatNotification = false;

	@Override
	public void server(
			final EntityPlayerMP player )
	{
		final ItemStack ei = player.getHeldItemMainhand();
		if ( ei != null && ei.getItem() instanceof IChiselModeItem )
		{
			final EnumDyeColor originalMode = getColor( ei );
			setColor( ei, newColor );

			if ( originalMode != newColor && chatNotification )
			{
				Minecraft.getMinecraft().player.sendStatusMessage( new TextComponentTranslation("chiselsandbits.color." + newColor.getTranslationKey()), true );
			}
		}
	}

	private void setColor(
			final ItemStack ei,
			final EnumDyeColor newColor2 )
	{
		if ( ei != null )
		{
			ei.setTagInfo( "color", new NBTTagString( newColor2.name() ) );
		}
	}

	private EnumDyeColor getColor(
			final ItemStack ei )
	{
		try
		{
			if ( ei != null && ei.hasTagCompound() )
			{
				return EnumDyeColor.valueOf( ModUtil.getTagCompound( ei ).getString( "color" ) );
			}
		}
		catch ( final IllegalArgumentException e )
		{
			// nope!
		}

		return EnumDyeColor.WHITE;
	}

	@Override
	public void getPayload(
			final PacketBuffer buffer )
	{
		buffer.writeBoolean( chatNotification );
		buffer.writeEnumValue( type );
		buffer.writeEnumValue( newColor );
	}

	@Override
	public void readPayload(
			final PacketBuffer buffer )
	{
		chatNotification = buffer.readBoolean();
		type = buffer.readEnumValue( ChiselToolType.class );
		newColor = buffer.readEnumValue( EnumDyeColor.class );
	}

}
