package mod.chiselsandbits.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.block.state.BlockState;

public class BlockStateSerializationUtils
{

    private static final Gson GSON = new Gson();

    private BlockStateSerializationUtils()
    {
        throw new IllegalStateException("Can not instantiate an instance of: BlockStateSerializationUtils. This is a utility class");
    }

    public static DataResult<BlockState> deserialize(final String string) {
        final Dynamic<JsonElement> parsingInput = new Dynamic<>(JsonOps.INSTANCE, GSON.fromJson(string, JsonElement.class));
        return BlockState.CODEC.parse(parsingInput);
    }

    public static String serialize(final BlockState blockState) {
        final DataResult<JsonElement> encodedElement = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, blockState);
        if (encodedElement.result().isEmpty()) {
            throw new IllegalStateException("Could not encode BlockState: " + blockState + ". Resulting error: " + encodedElement.error().orElseThrow().message());
        }
        return GSON.toJson(encodedElement.result().get());
    }
}
