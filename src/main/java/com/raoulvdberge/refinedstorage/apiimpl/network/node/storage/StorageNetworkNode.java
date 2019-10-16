package com.raoulvdberge.refinedstorage.apiimpl.network.node.storage;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.IStorageProvider;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskContainerContext;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.IStorageScreen;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;
import com.raoulvdberge.refinedstorage.apiimpl.storage.ItemStorageType;
import com.raoulvdberge.refinedstorage.apiimpl.storage.cache.ItemStorageCache;
import com.raoulvdberge.refinedstorage.inventory.item.BaseItemHandler;
import com.raoulvdberge.refinedstorage.inventory.listener.NetworkNodeListener;
import com.raoulvdberge.refinedstorage.tile.StorageTile;
import com.raoulvdberge.refinedstorage.tile.config.IAccessType;
import com.raoulvdberge.refinedstorage.tile.config.IComparable;
import com.raoulvdberge.refinedstorage.tile.config.IPrioritizable;
import com.raoulvdberge.refinedstorage.tile.config.IWhitelistBlacklist;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameter;
import com.raoulvdberge.refinedstorage.util.AccessTypeUtils;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import com.raoulvdberge.refinedstorage.util.StorageBlockUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.UUID;

public class StorageNetworkNode extends NetworkNode implements IStorageScreen, IStorageProvider, IComparable, IWhitelistBlacklist, IPrioritizable, IAccessType, IStorageDiskContainerContext {
    public static final ResourceLocation ONE_K_STORAGE_BLOCK_ID = new ResourceLocation(RS.ID, "1k_storage_block");
    public static final ResourceLocation FOUR_K_STORAGE_BLOCK_ID = new ResourceLocation(RS.ID, "4k_storage_block");
    public static final ResourceLocation SIXTEEN_K_STORAGE_BLOCK_ID = new ResourceLocation(RS.ID, "16k_storage_block");
    public static final ResourceLocation SIXTY_FOUR_K_STORAGE_BLOCK_ID = new ResourceLocation(RS.ID, "64k_storage_block");
    public static final ResourceLocation CREATIVE_STORAGE_BLOCK_ID = new ResourceLocation(RS.ID, "creative_storage_block");

    private static final String NBT_PRIORITY = "Priority";
    private static final String NBT_COMPARE = "Compare";
    private static final String NBT_MODE = "Mode";
    public static final String NBT_ID = "Id";

    private BaseItemHandler filters = new BaseItemHandler(9, new NetworkNodeListener(this));

    private final ItemStorageType type;

    private AccessType accessType = AccessType.INSERT_EXTRACT;
    private int priority = 0;
    private int compare = IComparer.COMPARE_NBT;
    private int mode = IWhitelistBlacklist.BLACKLIST;

    private UUID storageId = UUID.randomUUID();
    private IStorageDisk<ItemStack> storage;

    public StorageNetworkNode(World world, BlockPos pos, ItemStorageType type) {
        super(world, pos);

        this.type = type;
    }

    @Override
    public int getEnergyUsage() {
        switch (type) {
            case ONE_K:
                return RS.SERVER_CONFIG.getStorageBlock().getOneKUsage();
            case FOUR_K:
                return RS.SERVER_CONFIG.getStorageBlock().getFourKUsage();
            case SIXTEEN_K:
                return RS.SERVER_CONFIG.getStorageBlock().getSixteenKUsage();
            case SIXTY_FOUR_K:
                return RS.SERVER_CONFIG.getStorageBlock().getSixtyFourKUsage();
            case CREATIVE:
                return RS.SERVER_CONFIG.getStorageBlock().getCreativeUsage();
            default:
                return 0;
        }
    }

    @Override
    public void onConnectedStateChange(INetwork network, boolean state) {
        super.onConnectedStateChange(network, state);

        network.getNodeGraph().runActionWhenPossible(ItemStorageCache.INVALIDATE);
    }

    @Override
    public void addItemStorages(List<IStorage<ItemStack>> storages) {
        if (storage == null) {
            loadStorage();
        }

        storages.add(storage);
    }

    @Override
    public void addFluidStorages(List<IStorage<FluidStack>> storages) {
        // NO OP
    }

    @Override
    public ResourceLocation getId() {
        return StorageBlockUtils.getNetworkNodeId(type);
    }

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        super.write(tag);

        tag.putUniqueId(NBT_ID, storageId);

        return tag;
    }

    @Override
    public void read(CompoundNBT tag) {
        super.read(tag);

        if (tag.hasUniqueId(NBT_ID)) {
            storageId = tag.getUniqueId(NBT_ID);

            loadStorage();
        }
    }

    public void loadStorage() {
        IStorageDisk disk = API.instance().getStorageDiskManager((ServerWorld) world).get(storageId);

        if (disk == null) {
            API.instance().getStorageDiskManager((ServerWorld) world).set(storageId, disk = API.instance().createDefaultItemDisk((ServerWorld) world, type.getCapacity()));
            API.instance().getStorageDiskManager((ServerWorld) world).markForSaving();
        }

        this.storage = new ItemStorageWrapperStorageDisk(this, disk);
    }

    public void setStorageId(UUID id) {
        this.storageId = id;

        markDirty();
    }

    public UUID getStorageId() {
        return storageId;
    }

    public IStorageDisk<ItemStack> getStorage() {
        return storage;
    }

    @Override
    public CompoundNBT writeConfiguration(CompoundNBT tag) {
        super.writeConfiguration(tag);

        StackUtils.writeItems(filters, 0, tag);

        tag.putInt(NBT_PRIORITY, priority);
        tag.putInt(NBT_COMPARE, compare);
        tag.putInt(NBT_MODE, mode);

        AccessTypeUtils.writeAccessType(tag, accessType);

        return tag;
    }

    @Override
    public void readConfiguration(CompoundNBT tag) {
        super.readConfiguration(tag);

        StackUtils.readItems(filters, 0, tag);

        if (tag.contains(NBT_PRIORITY)) {
            priority = tag.getInt(NBT_PRIORITY);
        }

        if (tag.contains(NBT_COMPARE)) {
            compare = tag.getInt(NBT_COMPARE);
        }

        if (tag.contains(NBT_MODE)) {
            mode = tag.getInt(NBT_MODE);
        }

        accessType = AccessTypeUtils.readAccessType(tag);
    }

    @Override
    public int getCompare() {
        return compare;
    }

    @Override
    public void setCompare(int compare) {
        this.compare = compare;

        markDirty();
    }

    @Override
    public int getWhitelistBlacklistMode() {
        return mode;
    }

    @Override
    public void setWhitelistBlacklistMode(int mode) {
        this.mode = mode;

        markDirty();
    }

    public BaseItemHandler getFilters() {
        return filters;
    }

    @Override
    public ITextComponent getTitle() {
        return new TranslationTextComponent("block.refinedstorage." + type.getName() + "_storage_block");
    }

    @Override
    public TileDataParameter<Integer, ?> getTypeParameter() {
        return null;
    }

    @Override
    public TileDataParameter<Integer, ?> getRedstoneModeParameter() {
        return StorageTile.REDSTONE_MODE;
    }

    @Override
    public TileDataParameter<Integer, ?> getCompareParameter() {
        return StorageTile.COMPARE;
    }

    @Override
    public TileDataParameter<Integer, ?> getWhitelistBlacklistParameter() {
        return StorageTile.WHITELIST_BLACKLIST;
    }

    @Override
    public TileDataParameter<Integer, ?> getPriorityParameter() {
        return StorageTile.PRIORITY;
    }

    @Override
    public TileDataParameter<AccessType, ?> getAccessTypeParameter() {
        return StorageTile.ACCESS_TYPE;
    }

    @Override
    public long getStored() {
        return StorageTile.STORED.getValue();
    }

    @Override
    public long getCapacity() {
        return type.getCapacity();
    }

    @Override
    public AccessType getAccessType() {
        return accessType;
    }

    @Override
    public void setAccessType(AccessType value) {
        this.accessType = value;

        if (network != null) {
            network.getItemStorageCache().invalidate();
        }

        markDirty();
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;

        markDirty();

        if (network != null) {
            network.getItemStorageCache().sort();
        }
    }
}
