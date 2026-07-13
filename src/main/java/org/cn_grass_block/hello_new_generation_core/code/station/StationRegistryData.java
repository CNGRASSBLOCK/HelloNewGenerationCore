package org.cn_grass_block.hello_new_generation_core.code.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-world persistent registry of every TongDa Railway station we have observed being generated.
 *
 * <p>Populated by {@code MixinRailwayFeature}, which records each station the moment TongDa places it during
 * world generation. We deliberately store ONLY lightweight coordinate metadata here — never any block placement
 * happens on the worldgen thread (that is left to a main-thread chunk-load event in a later milestone), so this
 * class is completely deadlock-free under C2ME.
 *
 * <p>Backed by vanilla {@link SavedData}: it is attached to the {@link ServerLevel}'s data storage, saved
 * automatically with the world, and reloaded on world load — so captured stations survive a restart.
 */
public class StationRegistryData extends SavedData {

    /** Storage key under {@code <world>/data/}. */
    public static final String DATA_NAME = "hello_new_generation_core_stations";

    /**
     * One observed station. {@code typeOrdinal} is {@code StationType.ordinal()} (0 = NORMAL, 1 = UNDER_GROUND);
     * we store the ordinal rather than the enum so this class has zero compile-time dependency on TongDa.
     */
    public record StationRecord(BlockPos pos, int typeOrdinal, int stationId, int exitCount) {
    }

    private final List<StationRecord> stations = new ArrayList<>();

    public StationRegistryData() {
    }

    /** Get (or create) the registry for the given level. */
    public static StationRegistryData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(StationRegistryData::new, StationRegistryData::load),
            DATA_NAME);
    }

    /** Record a station if we have not already seen one at this exact position. Returns true if newly added. */
    public boolean addStation(final BlockPos pos, final int typeOrdinal, final int stationId, final int exitCount) {
        final BlockPos immutable = pos.immutable();
        for (final StationRecord existing : stations) {
            if (existing.pos().equals(immutable)) {
                return false;
            }
        }
        stations.add(new StationRecord(immutable, typeOrdinal, stationId, exitCount));
        setDirty();
        return true;
    }

    /** Unmodifiable view of all captured stations. */
    public List<StationRecord> getStations() {
        return List.copyOf(stations);
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider) {
        final ListTag list = new ListTag();
        for (final StationRecord record : stations) {
            final CompoundTag entry = new CompoundTag();
            entry.put("pos", NbtUtils.writeBlockPos(record.pos()));
            entry.putInt("type", record.typeOrdinal());
            entry.putInt("id", record.stationId());
            entry.putInt("exits", record.exitCount());
            list.add(entry);
        }
        tag.put("stations", list);
        return tag;
    }

    public static StationRegistryData load(final CompoundTag tag, final HolderLookup.Provider provider) {
        final StationRegistryData data = new StationRegistryData();
        final ListTag list = tag.getList("stations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag entry = list.getCompound(i);
            final BlockPos pos = NbtUtils.readBlockPos(entry, "pos").orElse(BlockPos.ZERO);
            data.stations.add(new StationRecord(
                pos,
                entry.getInt("type"),
                entry.getInt("id"),
                entry.getInt("exits")));
        }
        return data;
    }
}
