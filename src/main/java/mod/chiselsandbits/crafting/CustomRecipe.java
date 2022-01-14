package mod.chiselsandbits.crafting;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;

public abstract class CustomRecipe implements IRecipe
{

	private ResourceLocation registeryName;

	public CustomRecipe(
			ResourceLocation name )
	{
		registeryName = name;
	}

	@Override
	public IRecipe setRegistryName(
			ResourceLocation name )
	{
		registeryName = name;
		return this;
	}

	@Override
	public ResourceLocation getRegistryName()
	{
		return registeryName;
	}

	@Override
	public Class<IRecipe> getRegistryType()
	{
		return IRecipe.class;
	}

	@Override
	public boolean isDynamic()
	{
		return true; // hide recipe
	}

}
