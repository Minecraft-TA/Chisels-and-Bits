package mod.chiselsandbits.registry;

import mod.chiselsandbits.client.CreativeClipboardTab;
import mod.chiselsandbits.client.ModCreativeTab;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ClientSide;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.List;

public class ModRegistry
{

	public static final String unlocalizedPrefix = "mod." + ChiselsAndBits.MODID + ".";

	static ModCreativeTab creativeTab = new ModCreativeTab();
	static CreativeClipboardTab creativeClipboard = null;

	public ModRegistry()
	{
		ChiselsAndBits.registerWithBus( this );
	}

	static
	{
		if ( ChiselsAndBits.getConfig().creativeClipboardSize > 0 )
		{
			creativeClipboard = new CreativeClipboardTab();
		}
	}

	List<Item> registeredItems = new ArrayList<Item>();
	List<Block> registeredBlocks = new ArrayList<Block>();

	@SubscribeEvent
	public void registerItems(
			RegistryEvent.Register<Item> e )
	{
		IForgeRegistry<Item> r = e.getRegistry();
		for ( Item b : registeredItems )
		{
			r.register( b );
		}

		if ( !registeredItems.isEmpty() && ChiselsAndBits.getInstance().loadClientAssets() )
		{
			ClientSide.instance.registerItemModels();
		}
	}

	@SubscribeEvent
	public void registerBlocks(
			RegistryEvent.Register<Block> e )
	{
		IForgeRegistry<Block> r = e.getRegistry();
		for ( Block b : registeredBlocks )
		{
			r.register( b );
		}

		if ( !registeredBlocks.isEmpty() && ChiselsAndBits.getInstance().loadClientAssets() )
		{
			ClientSide.instance.registerBlockModels();
		}
	}

	protected <T extends Item> T registerItem(
			final boolean enabled,
			final T item,
			final String name )
	{
		if ( enabled )
		{
			item.setCreativeTab( creativeTab );

			item.setTranslationKey( unlocalizedPrefix + name );
			item.setRegistryName( ChiselsAndBits.MODID, name );

			registeredItems.add( item );
			return item;
		}

		return null;
	}

	protected void registerBlock(
			final Block block,
			final ItemBlock item,
			final String name )
	{
		block.setCreativeTab( creativeTab );

		item.setRegistryName( ChiselsAndBits.MODID, name );
		block.setRegistryName( ChiselsAndBits.MODID, name );

		block.setTranslationKey( unlocalizedPrefix + name );
		item.setTranslationKey( unlocalizedPrefix + name );

		registeredBlocks.add( block );
		registeredItems.add( item );
	}
}
