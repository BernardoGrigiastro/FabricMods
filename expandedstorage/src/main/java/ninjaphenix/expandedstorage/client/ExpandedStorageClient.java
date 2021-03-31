package ninjaphenix.expandedstorage.client;

import blue.endless.jankson.JsonPrimitive;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendereregistry.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;
import net.fabricmc.fabric.api.event.client.ClientSpriteRegistryCallback;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.BlockItem;
import ninjaphenix.chainmail.api.config.JanksonConfigParser;
import ninjaphenix.expandedstorage.client.config.ContainerConfig;
import ninjaphenix.expandedstorage.client.screen.PagedScreen;
import ninjaphenix.expandedstorage.client.screen.ScrollableScreen;
import ninjaphenix.expandedstorage.client.screen.SelectContainerScreen;
import ninjaphenix.expandedstorage.client.screen.SingleScreen;
import ninjaphenix.expandedstorage.common.Const;
import ninjaphenix.expandedstorage.common.ExpandedStorage;
import ninjaphenix.expandedstorage.common.ModContent;
import ninjaphenix.expandedstorage.common.Registries;
import ninjaphenix.expandedstorage.common.block.CursedChestBlock;
import ninjaphenix.expandedstorage.common.block.entity.CursedChestBlockEntity;
import ninjaphenix.expandedstorage.common.misc.CursedChestType;
import org.apache.logging.log4j.MarkerManager;

import static ninjaphenix.expandedstorage.common.ModContent.*;

public final class ExpandedStorageClient implements ClientModInitializer
{
    @SuppressWarnings({"unused", "RedundantSuppression"})
    public static final ExpandedStorageClient INSTANCE = new ExpandedStorageClient();
    private static final CursedChestBlockEntity CURSED_CHEST_RENDER_ENTITY = new CursedChestBlockEntity(BlockPos.ZERO, DIAMOND_CHEST.defaultBlockState(), null);
    public static final ContainerConfig CONFIG = getConfigParser().load(ContainerConfig.class, ContainerConfig::new, getConfigPath(),
                                                                        new MarkerManager.Log4jMarker(Const.MOD_ID));

    static
    {
        if (CONFIG.preferred_container_type.getNamespace().equals("ninjaphenix-container-lib"))
        {
            setPreference(new ResourceLocation(Const.MOD_ID, CONFIG.preferred_container_type.getPath()));
        }
    }

    @Override
    public void onInitializeClient()
    {
        ClientSidePacketRegistry.INSTANCE.register(Const.SCREEN_SELECT, (context, buffer) ->
        {
            final int count = buffer.readInt();
            final HashMap<ResourceLocation, Tuple<ResourceLocation, Component>> allowed = new HashMap<>();
            for (int i = 0; i < count; i++)
            {
                final ResourceLocation containerFactoryId = buffer.readResourceLocation();
                if (ExpandedStorage.INSTANCE.isContainerTypeDeclared(containerFactoryId))
                {
                    allowed.put(containerFactoryId, ExpandedStorage.INSTANCE.getScreenSettings(containerFactoryId));
                }
            }
            final Minecraft minecraft = Minecraft.getInstance();
            minecraft.submit(() -> minecraft.setScreen(new SelectContainerScreen(allowed)));
        });
        ClientSpriteRegistryCallback.event(Sheets.CHEST_SHEET).register(
                (atlas, registry) -> Registries.CHEST.stream().forEach(data -> Arrays.stream(CursedChestType.values())
                        .map(data::getChestTexture).forEach(registry::register)));
        BlockEntityRendererRegistry.INSTANCE.register(ModContent.CHEST, CursedChestBlockEntityRenderer::new);
        ModContent.CHEST.validBlocks.forEach(block -> BuiltinItemRendererRegistry.INSTANCE.register(
                block.asItem(), (itemStack, type, stack, vertexConsumers, light, overlay) ->
                {
                    CursedChestBlock renderBlock = (CursedChestBlock) ((BlockItem) itemStack.getItem()).getBlock();
                    CURSED_CHEST_RENDER_ENTITY.setBlock(renderBlock.TIER_ID);
                    Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(CURSED_CHEST_RENDER_ENTITY, stack, vertexConsumers, light, overlay);
                }));
        ScreenRegistry.register(SCROLLABLE_HANDLER_TYPE, ScrollableScreen::new);
        ScreenRegistry.register(PAGED_HANDLER_TYPE, PagedScreen::new);
        ScreenRegistry.register(SINGLE_HANDLER_TYPE, SingleScreen::new);

        registerModelLayer(Const.SINGLE_LAYER);
        registerModelLayer(Const.VANILLA_LEFT_LAYER);
        registerModelLayer(Const.VANILLA_RIGHT_LAYER);
        registerModelLayer(Const.TALL_TOP_LAYER);
        registerModelLayer(Const.TALL_BOTTOM_LAYER);
        registerModelLayer(Const.LONG_FRONT_LAYER);
        registerModelLayer(Const.LONG_BACK_LAYER);
    }

    private void registerModelLayer(final ModelLayerLocation layer) { ModelLayers.ALL_MODELS.add(layer); }

    private static JanksonConfigParser getConfigParser()
    {
        return new JanksonConfigParser.Builder().deSerializer(
                JsonPrimitive.class, ResourceLocation.class, (it, marshaller) -> new ResourceLocation(it.asString()),
                ((identifier, marshaller) -> marshaller.serialize(identifier.toString()))).build();
    }

    private static Path getConfigPath() { return FabricLoader.getInstance().getConfigDir().resolve("ninjaphenix-container-library.json"); }

    public static void sendPreferencesToServer()
    {
        ClientSidePacketRegistry.INSTANCE.sendToServer(Const.SCREEN_SELECT, new FriendlyByteBuf(Unpooled.buffer())
                .writeResourceLocation(CONFIG.preferred_container_type));
    }

    public static void sendCallbackRemoveToServer()
    {
        ClientSidePacketRegistry.INSTANCE.sendToServer(Const.SCREEN_SELECT, new FriendlyByteBuf(Unpooled.buffer())
                .writeResourceLocation(Const.resloc("auto")));
    }

    public static void setPreference(final ResourceLocation handlerType)
    {
        CONFIG.preferred_container_type = handlerType;
        getConfigParser().save(CONFIG, getConfigPath(), new MarkerManager.Log4jMarker(Const.MOD_ID));
    }
}