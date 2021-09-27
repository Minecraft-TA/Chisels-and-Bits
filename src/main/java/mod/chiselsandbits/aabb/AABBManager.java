package mod.chiselsandbits.aabb;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import mod.chiselsandbits.api.multistate.accessor.IAreaAccessor;
import mod.chiselsandbits.api.multistate.accessor.IStateEntryInfo;
import mod.chiselsandbits.api.multistate.accessor.identifier.IAreaShapeIdentifier;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

public class AABBManager
{
    private static final AABBManager INSTANCE = new AABBManager();
    private static final Logger LOGGER = LogManager.getLogger();

    public static AABBManager getInstance()
    {
        return INSTANCE;
    }

    private final Cache<Key, Collection<AABB>> cache = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build();

    private AABBManager()
    {
    }

    public Collection<AABB> get(
      final IAreaAccessor accessor,
      final Function<IAreaAccessor, Predicate<IStateEntryInfo>> selectablePredicateBuilder
    ) {
        final Predicate<IStateEntryInfo> selectablePredicate = selectablePredicateBuilder.apply(accessor);

        final Key cacheKey = new Key(
          accessor.createNewShapeIdentifier(),
          selectablePredicate
        );

        try
        {
            return cache.get(
              cacheKey,
              () -> AABBCompressor.compressStates(accessor, selectablePredicate)
            );
        }
        catch (ExecutionException e)
        {
            LOGGER.warn("Failed to calculate the bounding boxes for an area.", e);
            return Collections.emptyList();
        }
    }

    public void clearCache()
    {
        this.cache.asMap().clear();
    }

    private static final class Key {
        private final IAreaShapeIdentifier identifier;
        private final Predicate<IStateEntryInfo> predicate;

        private Key(final IAreaShapeIdentifier identifier, final Predicate<IStateEntryInfo> predicate) {
            this.identifier = identifier;
            this.predicate = predicate;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (!(o instanceof Key))
            {
                return false;
            }
            final Key key = (Key) o;
            return Objects.equals(identifier, key.identifier) && Objects.equals(predicate, key.predicate);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(identifier, predicate);
        }
    }
}
