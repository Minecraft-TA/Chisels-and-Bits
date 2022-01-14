package mod.chiselsandbits.core;

import com.google.common.base.Stopwatch;
import mod.chiselsandbits.api.APIExceptions.CannotBeChiseled;
import mod.chiselsandbits.api.IBitAccess;
import mod.chiselsandbits.api.IBitBrush;
import mod.chiselsandbits.api.ItemType;
import mod.chiselsandbits.api.ModKeyBinding;
import mod.chiselsandbits.bittank.TileEntityBitTank;
import mod.chiselsandbits.bittank.TileEntitySpecialRenderBitTank;
import mod.chiselsandbits.chiseledblock.*;
import mod.chiselsandbits.chiseledblock.data.*;
import mod.chiselsandbits.chiseledblock.iterators.ChiselIterator;
import mod.chiselsandbits.chiseledblock.iterators.ChiselTypeIterator;
import mod.chiselsandbits.client.*;
import mod.chiselsandbits.client.gui.ChiselsAndBitsMenu;
import mod.chiselsandbits.client.gui.SpriteIconPositioning;
import mod.chiselsandbits.commands.JsonModelExport;
import mod.chiselsandbits.helpers.*;
import mod.chiselsandbits.integration.mcmultipart.MCMultipartProxy;
import mod.chiselsandbits.interfaces.IItemScrollWheel;
import mod.chiselsandbits.interfaces.IPatternItem;
import mod.chiselsandbits.items.ItemChisel;
import mod.chiselsandbits.items.ItemChiseledBit;
import mod.chiselsandbits.modes.ChiselMode;
import mod.chiselsandbits.modes.IToolMode;
import mod.chiselsandbits.modes.PositivePatternMode;
import mod.chiselsandbits.modes.TapeMeasureModes;
import mod.chiselsandbits.network.NetworkRouter;
import mod.chiselsandbits.network.packets.PacketChisel;
import mod.chiselsandbits.network.packets.PacketRotateVoxelBlob;
import mod.chiselsandbits.network.packets.PacketSetColor;
import mod.chiselsandbits.network.packets.PacketSuppressInteraction;
import mod.chiselsandbits.registry.ModItems;
import mod.chiselsandbits.render.SmartModelManager;
import mod.chiselsandbits.render.chiseledblock.tesr.ChisledBlockRenderChunkTESR;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemMeshDefinition;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.IStateMapper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.Type;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ClientSide {

    private static final Random RANDOM = new Random();
    public static final ClientSide instance = new ClientSide();

    ReadyState readyState = ReadyState.PENDING_PRE;

    private final HashMap<IToolMode, SpriteIconPositioning> chiselModeIcons = new HashMap<IToolMode, SpriteIconPositioning>();
    private KeyBinding rotateCCW;
    private KeyBinding rotateCW;
    private KeyBinding undo;
    private KeyBinding redo;
    private KeyBinding modeMenu;
    private KeyBinding addToClipboard;
    private KeyBinding pickBit;
    private KeyBinding offgridPlacement;
    private Stopwatch rotateTimer;

    final public TapeMeasures tapeMeasures = new TapeMeasures();

    public KeyBinding getKeyBinding(
            ModKeyBinding modKeyBinding) {
        switch (modKeyBinding) {
            case ROTATE_CCW:
                return rotateCCW;
            case ROTATE_CW:
                return rotateCW;
            case UNDO:
                return undo;
            case REDO:
                return redo;
            case ADD_TO_CLIPBOARD:
                return addToClipboard;
            case PICK_BIT:
                return pickBit;
            case OFFGRID_PLACEMENT:
                return ClientSide.getOffGridPlacementKey();
            default:
                return modeMenu;
        }
    }

    public void preinit(
            final ChiselsAndBits mod) {
        readyState = readyState.updateState(ReadyState.TRIGGER_PRE);

        ChiselsAndBits.registerWithBus(new SmartModelManager());
        ChiselsAndBits.registerWithBus(instance);
    }

    public void init(
            final ChiselsAndBits chiselsandbits) {
        readyState = readyState.updateState(ReadyState.TRIGGER_INIT);

        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBlockChiseledTESR.class, new ChisledBlockRenderChunkTESR());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBitTank.class, new TileEntitySpecialRenderBitTank());

        for (final ChiselMode mode : ChiselMode.values()) {
            mode.binding = registerKeybind(mode.string.toString(), 0, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_CHISEL);
        }

        for (final PositivePatternMode mode : PositivePatternMode.values()) {
            mode.binding = registerKeybind(mode.string.toString(), 0, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_POSTIVEPATTERN);
        }

        for (final TapeMeasureModes mode : TapeMeasureModes.values()) {
            mode.binding = registerKeybind(mode.string.toString(), 0, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_TAPEMEASURE);
        }

        modeMenu = registerKeybind("mod.chiselsandbits.other.mode", 56, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_MENUITEM);
        rotateCCW = registerKeybind("mod.chiselsandbits.other.rotate.ccw", 0, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_ROTATEABLE);
        rotateCW = registerKeybind("mod.chiselsandbits.other.rotate.cw", 0, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_ROTATEABLE);
        pickBit = registerKeybind("mod.chiselsandbits.other.pickbit", 0, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_ROTATEABLE);
        offgridPlacement = registerKeybind("mod.chiselsandbits.other.offgrid", 0, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_OFFGRID);
        undo = registerKeybind("mod.chiselsandbits.other.undo", 0, "itemGroup.chiselsandbits", KeyConflictContext.IN_GAME);
        redo = registerKeybind("mod.chiselsandbits.other.redo", 0, "itemGroup.chiselsandbits", KeyConflictContext.IN_GAME);
        addToClipboard = registerKeybind("mod.chiselsandbits.other.add_to_clipboard", 0, "itemGroup.chiselsandbits", KeyConflictContext.IN_GAME);

        ClientCommandHandler.instance.registerCommand(new JsonModelExport());
    }

    private KeyBinding registerKeybind(
            final String bindingName,
            final int defaultKey,
            final String groupName,
            final IKeyConflictContext context) {
        final KeyBinding kb = new KeyBinding(bindingName, context, defaultKey, groupName);
        ClientRegistry.registerKeyBinding(kb);
        return kb;
    }

    public void postinit(
            final ChiselsAndBits mod) {
        readyState = readyState.updateState(ReadyState.TRIGGER_POST);

        final ModItems modItems = ChiselsAndBits.getItems();

        if (modItems.itemBitBag != null) {
            Minecraft.getMinecraft().getItemColors().registerItemColorHandler(new ItemColorBitBag(), modItems.itemBitBag);
        }

        if (modItems.itemBlockBit != null) {
            Minecraft.getMinecraft().getItemColors().registerItemColorHandler(new ItemColorBits(), modItems.itemBlockBit);
        }

        if (modItems.itemPositiveprint != null) {
            Minecraft.getMinecraft().getItemColors().registerItemColorHandler(new ItemColorPatterns(), modItems.itemPositiveprint);
        }

        if (modItems.itemNegativeprint != null) {
            Minecraft.getMinecraft().getItemColors().registerItemColorHandler(new ItemColorPatterns(), modItems.itemNegativeprint);
        }

        if (modItems.itemMirrorprint != null) {
            Minecraft.getMinecraft().getItemColors().registerItemColorHandler(new ItemColorPatterns(), modItems.itemMirrorprint);
        }

        for (final BlockChiseled blk : ChiselsAndBits.getBlocks().getConversions().values()) {
            final Item item = Item.getItemFromBlock(blk);
            Minecraft.getMinecraft().getItemColors().registerItemColorHandler(new ItemColorChisled(), item);
            Minecraft.getMinecraft().getBlockColors().registerBlockColorHandler(new BlockColorChisled(), blk);
        }
    }

    public void registerBlockModels() {
        final String modId = ChiselsAndBits.MODID;

        for (final BlockChiseled blk : new HashSet<BlockChiseled>(ChiselsAndBits.getBlocks().getConversions().values())) {
            registerMesh(blk, new ModelResourceLocation(new ResourceLocation(modId, "block_chiseled"), "normal"));
        }
    }

    public void registerItemModels() {
        final String modId = ChiselsAndBits.MODID;
        final ModItems modItems = ChiselsAndBits.getItems();

        registerMesh(modItems.itemChiselStone, 0, new ModelResourceLocation(new ResourceLocation(modId, "chisel_stone"), "inventory"));
        registerMesh(modItems.itemChiselIron, 0, new ModelResourceLocation(new ResourceLocation(modId, "chisel_iron"), "inventory"));
        registerMesh(modItems.itemChiselGold, 0, new ModelResourceLocation(new ResourceLocation(modId, "chisel_gold"), "inventory"));
        registerMesh(modItems.itemChiselDiamond, 0, new ModelResourceLocation(new ResourceLocation(modId, "chisel_diamond"), "inventory"));
        registerMesh(modItems.itemWrench, 0, new ModelResourceLocation(new ResourceLocation(modId, "wrench_wood"), "inventory"));
        registerMesh(modItems.itemBitSawDiamond, 0, new ModelResourceLocation(new ResourceLocation(modId, "bitsaw_diamond"), "inventory"));
        registerMesh(modItems.itemTapeMeasure, 0, new ModelResourceLocation(new ResourceLocation(modId, "tape_measure"), "inventory"));

        if (ChiselsAndBits.getBlocks().itemBitTank != null) {
            registerMesh(ChiselsAndBits.getBlocks().itemBitTank, 0, new ModelResourceLocation(new ResourceLocation(modId, "bittank"), "inventory"));
        }

        if (modItems.itemBitBag != null) {
            ModelBakery.registerItemVariants(modItems.itemBitBag, new ResourceLocation(modId, "bit_bag"), new ResourceLocation(modId, "bit_bag_dyed"));
            ModelLoader.setCustomMeshDefinition(modItems.itemBitBag, new ItemMeshDefinition() {

                @Override
                public ModelResourceLocation getModelLocation(
                        final ItemStack stack) {
                    return new ModelResourceLocation(new ResourceLocation(modId, modItems.itemBitBag.getDyedColor(stack) != null ? "bit_bag_dyed" : "bit_bag"), "inventory");
                }

            });
        }

        if (modItems.itemPositiveprint != null) {
            ModelBakery.registerItemVariants(modItems.itemPositiveprint, new ResourceLocation(modId, "positiveprint"), new ResourceLocation(modId, "positiveprint_written"));
            ModelLoader.setCustomMeshDefinition(modItems.itemPositiveprint, new ItemMeshDefinition() {

                @Override
                public ModelResourceLocation getModelLocation(
                        final ItemStack stack) {
                    return new ModelResourceLocation(new ResourceLocation(modId, modItems.itemPositiveprint.isWritten(stack) ? "positiveprint_written_preview" : "positiveprint"), "inventory");
                }

            });
        }

        if (modItems.itemNegativeprint != null) {
            ModelBakery.registerItemVariants(modItems.itemNegativeprint, new ResourceLocation(modId, "negativeprint"), new ResourceLocation(modId, "negativeprint_written"));
            ModelLoader.setCustomMeshDefinition(modItems.itemNegativeprint, new ItemMeshDefinition() {

                @Override
                public ModelResourceLocation getModelLocation(
                        final ItemStack stack) {
                    return new ModelResourceLocation(new ResourceLocation(modId, modItems.itemNegativeprint.isWritten(stack) ? "negativeprint_written_preview" : "negativeprint"), "inventory");
                }

            });
        }

        if (modItems.itemMirrorprint != null) {
            ModelBakery.registerItemVariants(modItems.itemMirrorprint, new ResourceLocation(modId, "mirrorprint"), new ResourceLocation(modId, "mirrorprint_written"));
            ModelLoader.setCustomMeshDefinition(modItems.itemMirrorprint, new ItemMeshDefinition() {

                @Override
                public ModelResourceLocation getModelLocation(
                        final ItemStack stack) {
                    return new ModelResourceLocation(new ResourceLocation(modId, modItems.itemMirrorprint.isWritten(stack) ? "mirrorprint_written_preview" : "mirrorprint"), "inventory");
                }

            });
        }

        if (modItems.itemBlockBit != null) {
            ModelLoader.setCustomMeshDefinition(modItems.itemBlockBit, new ItemMeshDefinition() {

                @Override
                public ModelResourceLocation getModelLocation(
                        final ItemStack stack) {
                    return new ModelResourceLocation(new ResourceLocation(modId, "block_bit"), "inventory");
                }

            });
        }

        for (final Item item : new HashSet<Item>(ChiselsAndBits.getBlocks().getItemConversions().values())) {
            registerMesh(item, 0, new ModelResourceLocation(new ResourceLocation(modId, "block_chiseled"), "inventory"));
        }
    }

    private void registerMesh(
            final Item item,
            final int meta,
            final ModelResourceLocation loctaion) {
        if (item != null) {
            ModelLoader.setCustomModelResourceLocation(item, meta, loctaion);
        }
    }

    private void registerMesh(
            final Block blk,
            final ModelResourceLocation loctaion) {
        if (blk != null) {
            ModelLoader.setCustomStateMapper(blk, new IStateMapper() {

                @Override
                public Map<IBlockState, ModelResourceLocation> putStateModelLocations(
                        Block blockIn) {
                    Map<IBlockState, ModelResourceLocation> map = new HashMap<IBlockState, ModelResourceLocation>();

                    for (IBlockState o : blk.getBlockState().getValidStates())
                        map.put(o, loctaion);

                    return map;
                }
            });
        }
    }

    public static TextureAtlasSprite undoIcon;
    public static TextureAtlasSprite redoIcon;
    public static TextureAtlasSprite trashIcon;

    public static TextureAtlasSprite sortIcon;

    public static TextureAtlasSprite swapIcon;
    public static TextureAtlasSprite placeIcon;

    public static TextureAtlasSprite roll_x;
    public static TextureAtlasSprite roll_z;

    @SubscribeEvent
    void registerIconTextures(
            final TextureStitchEvent.Pre ev) {
        final TextureMap map = ev.getMap();
        swapIcon = map.registerSprite(new ResourceLocation("chiselsandbits", "icons/swap"));
        placeIcon = map.registerSprite(new ResourceLocation("chiselsandbits", "icons/place"));
        undoIcon = map.registerSprite(new ResourceLocation("chiselsandbits", "icons/undo"));
        redoIcon = map.registerSprite(new ResourceLocation("chiselsandbits", "icons/redo"));
        trashIcon = map.registerSprite(new ResourceLocation("chiselsandbits", "icons/trash"));
        sortIcon = map.registerSprite(new ResourceLocation("chiselsandbits", "icons/sort"));
        roll_x = map.registerSprite(new ResourceLocation("chiselsandbits", "icons/roll_x"));
        roll_z = map.registerSprite(new ResourceLocation("chiselsandbits", "icons/roll_z"));

        for (final ChiselMode mode : ChiselMode.values()) {
            loadIcon(map, mode);
        }

        for (final PositivePatternMode mode : PositivePatternMode.values()) {
            loadIcon(map, mode);
        }

        for (final TapeMeasureModes mode : TapeMeasureModes.values()) {
            loadIcon(map, mode);
        }
    }

    void loadIcon(
            final TextureMap map,
            final IToolMode mode) {
        final SpriteIconPositioning sip = new SpriteIconPositioning();

        final ResourceLocation sprite = new ResourceLocation("chiselsandbits", "icons/" + mode.name().toLowerCase());
        final ResourceLocation png = new ResourceLocation("chiselsandbits", "textures/icons/" + mode.name().toLowerCase() + ".png");

        sip.sprite = map.registerSprite(sprite);

        try {
            final IResource iresource = Minecraft.getMinecraft().getResourceManager().getResource(png);
            final BufferedImage bi = TextureUtil.readBufferedImage(iresource.getInputStream());

            if (bi == null) {
                System.out.println("null " + png);
                return;
            }
            int bottom = 0;
            int right = 0;
            sip.left = bi.getWidth();
            sip.top = bi.getHeight();

            for (int x = 0; x < bi.getWidth(); x++) {
                for (int y = 0; y < bi.getHeight(); y++) {
                    final int color = bi.getRGB(x, y);
                    final int a = color >> 24 & 0xff;
                    if (a > 0) {
                        sip.left = Math.min(sip.left, x);
                        right = Math.max(right, x);

                        sip.top = Math.min(sip.top, y);
                        bottom = Math.max(bottom, y);
                    }
                }
            }

            sip.height = bottom - sip.top + 1;
            sip.width = right - sip.left + 1;

            sip.left /= bi.getWidth();
            sip.width /= bi.getWidth();
            sip.top /= bi.getHeight();
            sip.height /= bi.getHeight();
        } catch (final IOException e) {
            sip.height = 1;
            sip.width = 1;
            sip.left = 0;
            sip.top = 0;
        }

        chiselModeIcons.put(mode, sip);
    }

    public SpriteIconPositioning getIconForMode(
            final IToolMode mode) {
        return chiselModeIcons.get(mode);
    }

    @SubscribeEvent
    public void onRenderGUI(
            final RenderGameOverlayEvent.Post event) {
        final ChiselToolType tool = getHeldToolType(lastHand);
        final ElementType type = event.getType();
        if (type == ElementType.ALL && tool != null && tool.hasMenu()) {
            final boolean wasVisible = ChiselsAndBitsMenu.instance.isVisible();

            if (modeMenu.isKeyDown()) {
                ChiselsAndBitsMenu.instance.actionUsed = false;
                ChiselsAndBitsMenu.instance.raiseVisibility();
            } else {
                if (!ChiselsAndBitsMenu.instance.actionUsed) {
                    if (ChiselsAndBitsMenu.instance.switchTo != null) {
                        ClientSide.instance.playRadialMenu();
                        ChiselModeManager.changeChiselMode(tool, ChiselModeManager.getChiselMode(getPlayer(), tool, EnumHand.MAIN_HAND), ChiselsAndBitsMenu.instance.switchTo);
                    }

                    if (ChiselsAndBitsMenu.instance.doAction != null) {
                        ClientSide.instance.playRadialMenu();
                        switch (ChiselsAndBitsMenu.instance.doAction) {
                            case ROLL_X:
                                PacketRotateVoxelBlob pri = new PacketRotateVoxelBlob();
                                pri.axis = Axis.X;
                                pri.rotation = Rotation.CLOCKWISE_90;
                                NetworkRouter.instance.sendToServer(pri);
                                break;

                            case ROLL_Z:
                                PacketRotateVoxelBlob pri2 = new PacketRotateVoxelBlob();
                                pri2.axis = Axis.Z;
                                pri2.rotation = Rotation.CLOCKWISE_90;
                                NetworkRouter.instance.sendToServer(pri2);
                                break;

                            case REPLACE_TOGGLE:
                                ChiselsAndBits.getConfig().replaceingBits = !ChiselsAndBits.getConfig().replaceingBits;
                                ReflectionWrapper.instance.clearHighlightedStack();
                                break;

                            case UNDO:
                                UndoTracker.getInstance().undo();
                                break;

                            case REDO:
                                UndoTracker.getInstance().redo();
                                break;

                            case BLACK:
                            case BLUE:
                            case BROWN:
                            case CYAN:
                            case GRAY:
                            case GREEN:
                            case LIGHT_BLUE:
                            case LIME:
                            case MAGENTA:
                            case ORANGE:
                            case PINK:
                            case PURPLE:
                            case RED:
                            case SILVER:
                            case WHITE:
                            case YELLOW:

                                final PacketSetColor setColor = new PacketSetColor();
                                setColor.type = getHeldToolType(EnumHand.MAIN_HAND);
                                setColor.newColor = EnumDyeColor.valueOf(ChiselsAndBitsMenu.instance.doAction.name());
                                setColor.chatNotification = ChiselsAndBits.getConfig().chatModeNotification;
                                NetworkRouter.instance.sendToServer(setColor);
                                ReflectionWrapper.instance.clearHighlightedStack();

                                break;

                        }
                    }
                }

                ChiselsAndBitsMenu.instance.actionUsed = true;
                ChiselsAndBitsMenu.instance.decreaseVisibility();
            }

            if (ChiselsAndBitsMenu.instance.isVisible()) {
                final ScaledResolution res = event.getResolution();
                ChiselsAndBitsMenu.instance.configure(res.getScaledWidth(), res.getScaledHeight());

                if (wasVisible == false) {
                    if (ChiselsAndBits.getConfig().enableVivecraftCompatibility) {
                        ChiselsAndBitsMenu.instance.mc.currentScreen = (GuiScreen) ChiselsAndBitsMenu.instance;
                    }
                    ChiselsAndBitsMenu.instance.mc.inGameHasFocus = false;
                    ChiselsAndBitsMenu.instance.mc.mouseHelper.ungrabMouseCursor();
                }

                if (ChiselsAndBitsMenu.instance.mc.inGameHasFocus) {
                    KeyBinding.unPressAllKeys();
                }

                final int k1 = Mouse.getX() * res.getScaledWidth() / ChiselsAndBitsMenu.instance.mc.displayWidth;
                final int l1 = res.getScaledHeight() - Mouse.getY() * res.getScaledHeight() / ChiselsAndBitsMenu.instance.mc.displayHeight - 1;

                net.minecraftforge.client.ForgeHooksClient.drawScreen(ChiselsAndBitsMenu.instance, k1, l1, event.getPartialTicks());
            } else {
                if (wasVisible) {
                    ChiselsAndBitsMenu.instance.mc.setIngameFocus();
                }
            }
        }

        if (undo.isPressed()) {
            UndoTracker.getInstance().undo();
        }

        if (redo.isPressed()) {
            UndoTracker.getInstance().redo();
        }

        if (addToClipboard.isPressed()) {
            final Minecraft mc = Minecraft.getMinecraft();
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                try {
                    final IBitAccess access = ChiselsAndBits.getApi().getBitAccess(mc.world, mc.objectMouseOver.getBlockPos());
                    final ItemStack is = access.getBitsAsItem(null, ItemType.CHISLED_BLOCK, false);

                    CreativeClipboardTab.addItem(is);
                } catch (final CannotBeChiseled e) {
                    // nope.
                }
            }
        }

        if (pickBit.isPressed()) {
            final Minecraft mc = Minecraft.getMinecraft();
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                try {
                    final BitLocation bl = new BitLocation(mc.objectMouseOver, true, BitOperation.CHISEL);
                    final IBitAccess access = ChiselsAndBits.getApi().getBitAccess(mc.world, bl.getBlockPos());
                    final IBitBrush brush = access.getBitAt(bl.getBitX(), bl.getBitY(), bl.getBitZ());
                    if (brush != null) {
                        final ItemStack is = brush.getItemStack(1);
                        if (is != null) {
                            doPick(is);
                        }
                    }
                } catch (final CannotBeChiseled e) {
                    // nope.
                }
            }
        }

        if (type == ElementType.HOTBAR && ChiselsAndBits.getConfig().enableToolbarIcons) {
            final Minecraft mc = Minecraft.getMinecraft();
            final ScaledResolution res = event.getResolution();

            if (!mc.player.isSpectator()) {
                final GuiIngame sc = mc.ingameGUI;

                for (int slot = 0; slot < 9; ++slot) {
                    final ItemStack stack = mc.player.inventory.mainInventory.get(slot);
                    if (stack != null && stack.getItem() instanceof ItemChisel) {
                        final ChiselToolType toolType = getToolTypeForItem(stack);
                        IToolMode mode = toolType.getMode(stack);

                        if (!ChiselsAndBits.getConfig().perChiselMode && tool == ChiselToolType.CHISEL) {
                            mode = ChiselModeManager.getChiselMode(mc.player, ChiselToolType.CHISEL, lastHand);
                        }

                        final int x = res.getScaledWidth() / 2 - 90 + slot * 20 + 2;
                        final int y = res.getScaledHeight() - 16 - 3;

                        GlStateManager.color(1, 1, 1, 1.0f);
                        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                        final TextureAtlasSprite sprite = chiselModeIcons.get(mode) == null ? getMissingIcon() : chiselModeIcons.get(mode).sprite;

                        GlStateManager.enableBlend();
                        sc.drawTexturedModalRect(x + 1, y + 1, sprite, 8, 8);
                        GlStateManager.disableBlend();
                    }
                }
            }
        }
    }

    public void playRadialMenu() {
        final float volume = ChiselsAndBits.getConfig().radialMenuVolume;
        if (volume >= 0.0001f) {
            final PositionedSoundRecord psr = new PositionedSoundRecord(SoundEvents.UI_BUTTON_CLICK, SoundCategory.MASTER, volume, 1.0f, getPlayer().getPosition());
            Minecraft.getMinecraft().getSoundHandler().playSound(psr);
        }
    }

    private boolean doPick(
            final @Nonnull ItemStack result) {
        final EntityPlayer player = getPlayer();

        for (int x = 0; x < 9; x++) {
            final ItemStack stack = player.inventory.getStackInSlot(x);
            if (stack != null && stack.isItemEqual(result) && ItemStack.areItemStackTagsEqual(stack, result)) {
                player.inventory.currentItem = x;
                return true;
            }
        }

        if (!player.capabilities.isCreativeMode) {
            return false;
        }

        int slot = player.inventory.getFirstEmptyStack();
        if (slot < 0 || slot >= 9) {
            slot = player.inventory.currentItem;
        }

        // update inventory..
        player.inventory.setInventorySlotContents(slot, result);
        player.inventory.currentItem = slot;

        // update server...
        final int j = player.inventoryContainer.inventorySlots.size() - 9 + player.inventory.currentItem;
        Minecraft.getMinecraft().playerController.sendSlotPacket(player.inventory.getStackInSlot(player.inventory.currentItem), j);
        return true;
    }

    public ChiselToolType getHeldToolType(
            final EnumHand enumHand) {
        final EntityPlayer player = getPlayer();

        if (player == null) {
            return null;
        }

        final ItemStack is = player.getHeldItem(enumHand);
        return getToolTypeForItem(is);
    }

    private ChiselToolType getToolTypeForItem(
            final ItemStack is) {
        if (is != null && is.getItem() instanceof ItemChisel) {
            return ChiselToolType.CHISEL;
        }

        if (is != null && is.getItem() instanceof ItemChiseledBit) {
            return ChiselToolType.BIT;
        }

        if (is != null && is.getItem() instanceof ItemBlockChiseled) {
            return ChiselToolType.CHISELED_BLOCK;
        }

        if (is != null && is.getItem() == ChiselsAndBits.getItems().itemTapeMeasure) {
            return ChiselToolType.TAPEMEASURE;
        }

        if (is != null && is.getItem() == ChiselsAndBits.getItems().itemPositiveprint) {
            return ChiselToolType.POSITIVEPATTERN;
        }

        if (is != null && is.getItem() == ChiselsAndBits.getItems().itemNegativeprint) {
            return ChiselToolType.NEGATIVEPATTERN;
        }

        if (is != null && is.getItem() == ChiselsAndBits.getItems().itemMirrorprint) {
            return ChiselToolType.MIRRORPATTERN;
        }

        return null;
    }

    @SubscribeEvent
    public void drawingInteractionPrevention(
            final RightClickBlock pie) {
        if (pie.getWorld() != null && pie.getWorld().isRemote) {
            final ChiselToolType tool = getHeldToolType(pie.getHand());
            final IToolMode chMode = ChiselModeManager.getChiselMode(getPlayer(), tool, pie.getHand());

            final BitLocation other = getStartPos();
            if ((chMode == ChiselMode.DRAWN_REGION || tool == ChiselToolType.TAPEMEASURE) && other != null) {
                // this handles the client side, but the server side will fire
                // separately.
                pie.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void interaction(
            final TickEvent.ClientTickEvent event) {
        if (!readyState.isReady())
            return;

        // used to prevent hyper chisels.. its actually far worse then you might
        // think...
        if (event.side == Side.CLIENT && event.type == Type.CLIENT && event.phase == Phase.START && !Minecraft.getMinecraft().gameSettings.keyBindAttack.isKeyDown()) {
            ItemChisel.resetDelay();
        }

        if (!getToolKey().isKeyDown()) {
            if (loopDeath) {
                if (drawStart != null) {
                    drawStart = null;
                    lastHand = EnumHand.MAIN_HAND;
                }

                lastTool = ChiselToolType.CHISEL;
            } else {
                loopDeath = true;
            }
        } else {
            loopDeath = false;
        }

        if (rotateCCW.isKeyDown()) {
            if (rotateTimer == null || rotateTimer.elapsed(TimeUnit.MILLISECONDS) > 200) {
                rotateTimer = Stopwatch.createStarted();
                final PacketRotateVoxelBlob p = new PacketRotateVoxelBlob();
                p.axis = Axis.Y;
                p.rotation = Rotation.COUNTERCLOCKWISE_90;
                NetworkRouter.instance.sendToServer(p);
            }
        }

        if (rotateCW.isKeyDown()) {
            if (rotateTimer == null || rotateTimer.elapsed(TimeUnit.MILLISECONDS) > 200) {
                rotateTimer = Stopwatch.createStarted();
                final PacketRotateVoxelBlob p = new PacketRotateVoxelBlob();
                p.axis = Axis.Y;
                p.rotation = Rotation.CLOCKWISE_90;
                NetworkRouter.instance.sendToServer(p);
            }
        }

        for (final ChiselMode mode : ChiselMode.values()) {
            final KeyBinding kb = (KeyBinding) mode.binding;
            if (kb.isKeyDown()) {
                final ChiselToolType tool = getHeldToolType(lastHand);
                if (tool.isBitOrChisel()) {
                    ChiselModeManager.changeChiselMode(tool, ChiselModeManager.getChiselMode(getPlayer(), tool, lastHand), mode);
                }
            }
        }

        for (final PositivePatternMode mode : PositivePatternMode.values()) {
            final KeyBinding kb = (KeyBinding) mode.binding;
            if (kb.isKeyDown()) {
                final ChiselToolType tool = getHeldToolType(lastHand);
                if (tool == ChiselToolType.POSITIVEPATTERN) {
                    ChiselModeManager.changeChiselMode(tool, ChiselModeManager.getChiselMode(getPlayer(), tool, lastHand), mode);
                }
            }
        }

        for (final TapeMeasureModes mode : TapeMeasureModes.values()) {
            final KeyBinding kb = (KeyBinding) mode.binding;
            if (kb.isKeyDown()) {
                final ChiselToolType tool = getHeldToolType(lastHand);
                if (tool == ChiselToolType.TAPEMEASURE) {
                    ChiselModeManager.changeChiselMode(tool, ChiselModeManager.getChiselMode(getPlayer(), tool, lastHand), mode);
                }
            }
        }
    }

    boolean wasDrawing = false;

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void drawHighlight(
            final RenderWorldLastEvent event) {
        ChiselToolType tool = getHeldToolType(lastHand);
        final IToolMode chMode = ChiselModeManager.getChiselMode(getPlayer(), tool, lastHand);
        if (chMode == ChiselMode.DRAWN_REGION) {
            tool = lastTool;
        }

        tapeMeasures.setPreviewMeasure(null, null, chMode, null);

        if (tool != null && tool == ChiselToolType.TAPEMEASURE) {
            final EntityPlayer player = getPlayer();
            final RayTraceResult mop = Minecraft.getMinecraft().objectMouseOver;
            final World theWorld = player.world;

            if (mop != null && mop.typeOfHit == RayTraceResult.Type.BLOCK) {
                final BitLocation location = new BitLocation(mop, true, BitOperation.CHISEL);
                if (theWorld.getWorldBorder().contains(location.blockPos)) {
                    final BitLocation other = getStartPos();
                    if (other != null) {
                        tapeMeasures.setPreviewMeasure(other, location, chMode, getPlayer().getHeldItem(lastHand));

                        if (!getToolKey().isKeyDown()) {
                            tapeMeasures.addMeasure(other, location, chMode, getPlayer().getHeldItem(lastHand));
                            drawStart = null;
                            lastHand = EnumHand.MAIN_HAND;
                        }
                    }
                }
            }
        }

        tapeMeasures.render(event.getPartialTicks());

        final boolean isDrawing = (chMode == ChiselMode.DRAWN_REGION || tool == ChiselToolType.TAPEMEASURE) && getStartPos() != null;
        if (isDrawing != wasDrawing) {
            wasDrawing = isDrawing;
            final PacketSuppressInteraction packet = new PacketSuppressInteraction();
            packet.newSetting = isDrawing;
            NetworkRouter.instance.sendToServer(packet);
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void drawHighlight(
            final DrawBlockHighlightEvent event) {
        ChiselToolType tool = getHeldToolType(lastHand);
        final IToolMode chMode = ChiselModeManager.getChiselMode(getPlayer(), tool, lastHand);
        if (chMode == ChiselMode.DRAWN_REGION) {
            tool = lastTool;
        }

        if (tool != null && tool.isBitOrChisel() && chMode != null) {
            final EntityPlayer player = event.getPlayer();
            final float partialTicks = event.getPartialTicks();
            final RayTraceResult mop = Minecraft.getMinecraft().objectMouseOver;
            final World theWorld = player.world;

            if (mop == null || mop.typeOfHit != RayTraceResult.Type.BLOCK) {
                return;
            }

            boolean showBox = false;
            if (mop.typeOfHit == RayTraceResult.Type.BLOCK) {
                final BitLocation location = new BitLocation(mop, true, getLastBitOperation(player, lastHand, getPlayer().getHeldItem(lastHand)));
                if (theWorld.getWorldBorder().contains(location.blockPos)) {
                    // this logic originated in the vanilla bounding box...
                    final IBlockState state = theWorld.getBlockState(location.blockPos);

                    final boolean isChisel = getDrawnTool() == ChiselToolType.CHISEL;
                    final boolean isBit = getHeldToolType(EnumHand.MAIN_HAND) == ChiselToolType.BIT;
                    final TileEntityBlockChiseled data = ModUtil.getChiseledTileEntity(theWorld, location.blockPos, false);

                    final VoxelRegionSrc region = new VoxelRegionSrc(theWorld, location.blockPos, 1);
                    final VoxelBlob vb = data != null ? data.getBlob() : new VoxelBlob();

                    if (isChisel && data == null) {
                        showBox = true;
                        vb.fill(1);
                    }

                    final BitLocation other = getStartPos();
                    if (chMode == ChiselMode.DRAWN_REGION && other != null) {
                        final ChiselIterator oneEnd = ChiselTypeIterator.create(VoxelBlob.dim, location.bitX, location.bitY, location.bitZ, VoxelBlob.NULL_BLOB, ChiselMode.SINGLE, EnumFacing.UP, tool == ChiselToolType.BIT);
                        final ChiselIterator otherEnd = ChiselTypeIterator.create(VoxelBlob.dim, other.bitX, other.bitY, other.bitZ, VoxelBlob.NULL_BLOB, ChiselMode.SINGLE, EnumFacing.UP, tool == ChiselToolType.BIT);

                        final AxisAlignedBB a = oneEnd.getBoundingBox(VoxelBlob.NULL_BLOB, false).offset(location.blockPos.getX(), location.blockPos.getY(), location.blockPos.getZ());
                        final AxisAlignedBB b = otherEnd.getBoundingBox(VoxelBlob.NULL_BLOB, false).offset(other.blockPos.getX(), other.blockPos.getY(), other.blockPos.getZ());

                        final AxisAlignedBB bb = a.union(b);

                        final double maxChiseSize = ChiselsAndBits.getConfig().maxDrawnRegionSize + 0.001;
                        if (bb.maxX - bb.minX <= maxChiseSize && bb.maxY - bb.minY <= maxChiseSize && bb.maxZ - bb.minZ <= maxChiseSize) {
                            RenderHelper.drawSelectionBoundingBoxIfExists(bb, BlockPos.ORIGIN, player, partialTicks, false);

                            if (!getToolKey().isKeyDown()) {
                                final PacketChisel pc = new PacketChisel(getLastBitOperation(player, lastHand, player.getHeldItem(lastHand)), location, other,
                                        EnumFacing.UP,
                                        ChiselMode.DRAWN_REGION, lastHand);

                                if (pc.doAction(getPlayer()) > 0) {
                                    NetworkRouter.instance.sendToServer(pc);
                                    ClientSide.placeSound(theWorld, location.blockPos, 0);
                                }

                                drawStart = null;
                                lastHand = EnumHand.MAIN_HAND;
                                lastTool = ChiselToolType.CHISEL;
                            }
                        }
                    } else {
                        final TileEntity te = theWorld.getTileEntity(location.blockPos);
                        boolean isBitBlock = te instanceof TileEntityBlockChiseled;
                        final boolean isBlockSupported = BlockBitInfo.supportsBlock(state);

                        if (!(isBitBlock || isBlockSupported)) {
                            final TileEntityBlockChiseled tebc = ModUtil.getChiseledTileEntity(theWorld, location.blockPos, false);
                            if (tebc != null) {
                                final VoxelBlob vx = tebc.getBlob();
                                if (vx.get(location.bitX, location.bitY, location.bitZ) != 0) {
                                    isBitBlock = true;
                                }
                            }
                        }

                        if (theWorld.isAirBlock(location.blockPos) || isBitBlock || isBlockSupported) {
                            final ChiselIterator i = ChiselTypeIterator.create(VoxelBlob.dim, location.bitX, location.bitY, location.bitZ, region, ChiselMode.castMode(chMode), mop.sideHit, !isChisel);
                            final AxisAlignedBB bb = i.getBoundingBox(vb, isChisel);
                            RenderHelper.drawSelectionBoundingBoxIfExists(bb, location.blockPos, player, partialTicks, false);
                            showBox = false;
                        } else if (isBit) {
                            final VoxelBlob j = new VoxelBlob();
                            j.fill(1);
                            final ChiselIterator i = ChiselTypeIterator.create(VoxelBlob.dim, location.bitX, location.bitY, location.bitZ, j, ChiselMode.castMode(chMode), mop.sideHit, !isChisel);
                            final AxisAlignedBB bb = snapToSide(i.getBoundingBox(j, isChisel), mop.sideHit);
                            RenderHelper.drawSelectionBoundingBoxIfExists(bb, location.blockPos, player, partialTicks, false);
                        }
                    }
                }

                if (!showBox) {
                    event.setCanceled(true);
                }

            }
        }

    }

    private BitOperation getLastBitOperation(
            final EntityPlayer player,
            final EnumHand lastHand2,
            final ItemStack heldItem) {
        return lastTool == ChiselToolType.BIT ? ItemChiseledBit.getBitOperation(player, lastHand, player.getHeldItem(lastHand)) : BitOperation.CHISEL;
    }

    private AxisAlignedBB snapToSide(
            final AxisAlignedBB boundingBox,
            final EnumFacing sideHit) {
        if (boundingBox != null) {
            switch (sideHit) {
                case DOWN:
                    return new AxisAlignedBB(boundingBox.minX, boundingBox.minY, boundingBox.minZ, boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
                case EAST:
                    return new AxisAlignedBB(boundingBox.maxX, boundingBox.minY, boundingBox.minZ, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
                case NORTH:
                    return new AxisAlignedBB(boundingBox.minX, boundingBox.minY, boundingBox.minZ, boundingBox.maxX, boundingBox.maxY, boundingBox.minZ);
                case SOUTH:
                    return new AxisAlignedBB(boundingBox.minX, boundingBox.minY, boundingBox.maxZ, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
                case UP:
                    return new AxisAlignedBB(boundingBox.minX, boundingBox.maxY, boundingBox.minZ, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
                case WEST:
                    return new AxisAlignedBB(boundingBox.minX, boundingBox.minY, boundingBox.minZ, boundingBox.minX, boundingBox.maxY, boundingBox.maxZ);
                default:
                    break;
            }
        }

        return boundingBox;
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void drawLast(
            final RenderWorldLastEvent event) {
        // important and used for tesr / block rendering.
        ++lastRenderedFrame;

        if (Minecraft.getMinecraft().gameSettings.hideGUI) {
            return;
        }

        // now render the ghosts...
        final EntityPlayer player = Minecraft.getMinecraft().player;
        final float partialTicks = event.getPartialTicks();
        final RayTraceResult mop = Minecraft.getMinecraft().objectMouseOver;
        final World theWorld = player.world;
        final ItemStack currentItem = player.getHeldItemMainhand();

        final double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        final double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        final double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        if (mop == null) {
            return;
        }

        if (ModUtil.isHoldingPattern(player)) {
            if (mop.typeOfHit != RayTraceResult.Type.BLOCK) {
                return;
            }

            final IToolMode mode = ChiselModeManager.getChiselMode(player, ChiselToolType.POSITIVEPATTERN, EnumHand.MAIN_HAND);

            final BlockPos pos = mop.getBlockPos();
            final BlockPos partial = null;

            final IBlockState s = theWorld.getBlockState(pos);
            if (!(s.getBlock() instanceof BlockChiseled) && !BlockBitInfo.supportsBlock(s) && !MCMultipartProxy.proxyMCMultiPart.isMultiPartTileEntity(theWorld, mop.getBlockPos())) {
                return;
            }

            if (!ChiselsAndBits.getItems().itemNegativeprint.isWritten(currentItem)) {
                return;
            }

            final ItemStack item = ChiselsAndBits.getItems().itemNegativeprint.getPatternedItem(currentItem, false);
            if (item == null || !item.hasTagCompound()) {
                return;
            }

            final int rotations = ModUtil.getRotations(player, ModUtil.getSide(currentItem));

            if (mode == PositivePatternMode.PLACEMENT) {
                doGhostForChiseledBlock(x, y, z, theWorld, player, mop, item, item, rotations);
                return;
            }

            if (item != null) {
                final TileEntityBlockChiseled tebc = ModUtil.getChiseledTileEntity(theWorld, pos, false);
                Object cacheRef = tebc != null ? tebc : s;
                if (cacheRef instanceof TileEntityBlockChiseled) {
                    cacheRef = ((TileEntityBlockChiseled) cacheRef).getBlobStateReference();
                }

                GlStateManager.depthFunc(GL11.GL_ALWAYS);
                showGhost(currentItem, item, mop.getBlockPos(), player, rotations, x, y, z, mop.sideHit, partial, cacheRef);
                GlStateManager.depthFunc(GL11.GL_LEQUAL);
            }
        } else if (ModUtil.isHoldingChiseledBlock(player)) {
            if (mop.typeOfHit != RayTraceResult.Type.BLOCK) {
                return;
            }

            final ItemStack item = currentItem;
            if (!item.hasTagCompound()) {
                return;
            }

            final int rotations = ModUtil.getRotations(player, ModUtil.getSide(item));
            doGhostForChiseledBlock(x, y, z, theWorld, player, mop, currentItem, item, rotations);
        }
    }

    private void doGhostForChiseledBlock(
            final double x,
            final double y,
            final double z,
            final World theWorld,
            final EntityPlayer player,
            final RayTraceResult mop,
            final ItemStack currentItem,
            final ItemStack item,
            final int rotations) {
        final BlockPos offset = mop.getBlockPos();

        if (ClientSide.offGridPlacement(player)) {
            final BitLocation bl = new BitLocation(mop, true, BitOperation.PLACE);
            showGhost(currentItem, item, bl.blockPos, player, rotations, x, y, z, mop.sideHit, new BlockPos(bl.bitX, bl.bitY, bl.bitZ), null);
        } else {
            boolean canMerge = false;
            if (currentItem.hasTagCompound()) {
                final TileEntityBlockChiseled tebc = ModUtil.getChiseledTileEntity(theWorld, offset, true);

                if (tebc != null) {
                    final VoxelBlob blob = ModUtil.getBlobFromStack(currentItem, player);
                    canMerge = tebc.canMerge(blob);
                }
            }

            BlockPos newOffset = offset;
            final Block block = theWorld.getBlockState(newOffset).getBlock();
            if (!canMerge && !ClientSide.offGridPlacement(player) && !block.isReplaceable(theWorld, newOffset)) {
                newOffset = offset.offset(mop.sideHit);
            }

            final TileEntity newTarget = theWorld.getTileEntity(newOffset);

            if (theWorld.isAirBlock(newOffset) || theWorld.getBlockState(newOffset).getBlock().isReplaceable(theWorld, newOffset) || newTarget instanceof TileEntityBlockChiseled
                || MCMultipartProxy.proxyMCMultiPart.isMultiPartTileEntity(theWorld, newOffset)) {
                final TileEntityBlockChiseled test = ModUtil.getChiseledTileEntity(theWorld, newOffset, false);
                showGhost(currentItem, item, newOffset, player, rotations, x, y, z, mop.sideHit, null, test == null ? null : test.getBlobStateReference());
            }
        }
    }

    private ItemStack previousItem;
    private int previousRotations;
    private Object previousModel;
    private Object previousCacheRef;
    private IntegerBox modelBounds;
    private boolean isVisible = true;
    private boolean isUnplaceable = true;
    private BlockPos lastPartial;
    private BlockPos lastPos;
    int displayStatus = 0;

    private void showGhost(
            final ItemStack refItem,
            final ItemStack item,
            final BlockPos blockPos,
            final EntityPlayer player,
            final int rotationCount,
            final double x,
            final double y,
            final double z,
            final EnumFacing side,
            final BlockPos partial,
            final Object cacheRef) {
        IBakedModel baked = null;

        if (previousCacheRef == cacheRef && samePos(lastPos, blockPos) && previousItem == refItem && previousRotations == rotationCount && previousModel != null && samePos(lastPartial, partial)) {
            baked = (IBakedModel) previousModel;
        } else {
            int rotations = rotationCount;

            previousItem = refItem;
            previousRotations = rotations;
            previousCacheRef = cacheRef;
            lastPos = blockPos;
            lastPartial = partial;

            final NBTBlobConverter c = new NBTBlobConverter();
            c.readChisleData(ModUtil.getSubCompound(item, ModUtil.NBT_BLOCKENTITYTAG, false), VoxelBlob.VERSION_ANY);
            VoxelBlob blob = c.getBlob();

            while (rotations-- > 0) {
                blob = blob.spin(Axis.Y);
            }

            modelBounds = blob.getBounds();

            fail:
            if (refItem.getItem() == ChiselsAndBits.getItems().itemNegativeprint) {
                final VoxelBlob pattern = blob;

                if (cacheRef instanceof VoxelBlobStateReference) {
                    blob = ((VoxelBlobStateReference) cacheRef).getVoxelBlob();
                } else if (cacheRef instanceof IBlockState) {
                    blob = new VoxelBlob();
                    blob.fill(ModUtil.getStateId((IBlockState) cacheRef));
                } else {
                    break fail;
                }

                final BitIterator it = new BitIterator();
                while (it.hasNext()) {
                    if (it.getNext(pattern) == 0) {
                        it.setNext(blob, 0);
                    }
                }
            }

            c.setBlob(blob);

            final Block blk = Block.getBlockFromItem(item.getItem());
            final ItemStack is = c.getItemStack(false);

            if (is == null || is.getItem() == null) {
                isVisible = false;
            } else {
                baked = Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getItemModel(is);
                previousModel = baked = baked.getOverrides().handleItemState(baked, is, player.getEntityWorld(), player);

                if (displayStatus != 0) {
                    GlStateManager.glDeleteLists(displayStatus, 1);
                    displayStatus = 0;
                }

                if (refItem.getItem() instanceof IPatternItem) {
                    isVisible = true;
                } else {
                    isVisible = true;
                    isUnplaceable = !ItemBlockChiseled.tryPlaceBlockAt(blk, item, player, player.getEntityWorld(), blockPos, side, EnumHand.MAIN_HAND, partial, false);
                }
            }
        }

        if (!isVisible) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(blockPos.getX() - x, blockPos.getY() - y, blockPos.getZ() - z);
        if (partial != null) {
            final BlockPos t = ModUtil.getPartialOffset(side, partial, modelBounds);
            final double fullScale = 1.0 / VoxelBlob.dim;
            GlStateManager.translate(t.getX() * fullScale, t.getY() * fullScale, t.getZ() * fullScale);
        }

        if (displayStatus == 0) {
            displayStatus = GLAllocation.generateDisplayLists(1);
            GlStateManager.glNewList(displayStatus, GL11.GL_COMPILE_AND_EXECUTE);
            RenderHelper.renderGhostModel(baked, player.world, blockPos, isUnplaceable);
            GlStateManager.glEndList();
        } else {
            GlStateManager.callList(displayStatus);
        }

        GlStateManager.popMatrix();
    }

    private boolean samePos(
            final BlockPos lastPartial2,
            final BlockPos partial) {
        if (lastPartial2 == partial) {
            return true;
        }

        if (lastPartial2 == null || partial == null) {
            return false;
        }

        return partial.equals(lastPartial2);
    }

    public EntityPlayer getPlayer() {
        return Minecraft.getMinecraft().player;
    }

    public boolean addHitEffects(
            final World world,
            final RayTraceResult target,
            final IBlockState state,
            final ParticleManager effectRenderer) {
        final ItemStack hitWith = getPlayer().getHeldItemMainhand();
        if (hitWith != null && (hitWith.getItem() instanceof ItemChisel || hitWith.getItem() instanceof ItemChiseledBit)) {
            return true; // no
            // effects!
        }

        final BlockPos pos = target.getBlockPos();
        final float boxOffset = 0.1F;

        AxisAlignedBB bb = world.getBlockState(pos).getBlock().getSelectedBoundingBox(state, world, pos);

        if (bb == null) {
            bb = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        }

        double x = RANDOM.nextDouble() * (bb.maxX - bb.minX - boxOffset * 2.0F) + boxOffset + bb.minX;
        double y = RANDOM.nextDouble() * (bb.maxY - bb.minY - boxOffset * 2.0F) + boxOffset + bb.minY;
        double z = RANDOM.nextDouble() * (bb.maxZ - bb.minZ - boxOffset * 2.0F) + boxOffset + bb.minZ;

        switch (target.sideHit) {
            case DOWN:
                y = bb.minY - boxOffset;
                break;
            case EAST:
                x = bb.maxX + boxOffset;
                break;
            case NORTH:
                z = bb.minZ - boxOffset;
                break;
            case SOUTH:
                z = bb.maxZ + boxOffset;
                break;
            case UP:
                y = bb.maxY + boxOffset;
                break;
            case WEST:
                x = bb.minX - boxOffset;
                break;
            default:
                break;

        }

        final Particle fx = effectRenderer.spawnEffectParticle(EnumParticleTypes.BLOCK_DUST.getParticleID(), x, y, z, 0.0D, 0.0D, 0.0D, new int[]{ModUtil.getStateId(state)});

        if (fx != null) {
            fx.multiplyVelocity(0.2F).multipleParticleScaleBy(0.6F);
        }

        return true;
    }

    @SubscribeEvent
    public void wheelEvent(
            final MouseEvent me) {
        final int dwheel = me.getDwheel();
        if (me.isCanceled() || dwheel == 0) {
            return;
        }

        final EntityPlayer player = ClientSide.instance.getPlayer();
        final ItemStack is = player.getHeldItemMainhand();

        if (dwheel != 0 && is != null && is.getItem() instanceof IItemScrollWheel && player.isSneaking()) {
            ((IItemScrollWheel) is.getItem()).scroll(player, is, dwheel);
            me.setCanceled(true);
        }
    }

    public static void placeSound(
            final World world,
            final BlockPos pos,
            final int stateID) {
        final IBlockState state = ModUtil.getStateById(stateID);
        final Block block = state.getBlock();
        world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, DeprecationHelper.getSoundType(block).getPlaceSound(), SoundCategory.BLOCKS, (DeprecationHelper.getSoundType(block).getVolume() + 1.0F) / 16.0F,
                DeprecationHelper.getSoundType(block).getPitch() * 0.9F, false);
    }

    public static void breakSound(
            final World world,
            final BlockPos pos,
            final int extractedState) {
        final IBlockState state = ModUtil.getStateById(extractedState);
        final Block block = state.getBlock();
        world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, DeprecationHelper.getSoundType(block).getBreakSound(), SoundCategory.BLOCKS, (DeprecationHelper.getSoundType(block).getVolume() + 1.0F) / 16.0F,
                DeprecationHelper.getSoundType(block).getPitch() * 0.9F, false);
    }

    private BitLocation drawStart;
    private boolean loopDeath = false;
    private int lastRenderedFrame = Integer.MIN_VALUE;

    public int getLastRenderedFrame() {
        return lastRenderedFrame;
    }

    public BitLocation getStartPos() {
        return drawStart;
    }

    public void pointAt(
            @Nonnull final ChiselToolType type,
            @Nonnull final BitLocation pos,
            @Nonnull final EnumHand hand) {
        if (drawStart == null) {
            drawStart = pos;
            lastTool = type;
            lastHand = hand;
        }
    }

    @Nonnull
    ChiselToolType lastTool = ChiselToolType.CHISEL;

    @Nonnull
    EnumHand lastHand = EnumHand.MAIN_HAND;

    KeyBinding getToolKey() {
        if (lastTool == ChiselToolType.CHISEL) {
            return Minecraft.getMinecraft().gameSettings.keyBindAttack;
        } else {
            return Minecraft.getMinecraft().gameSettings.keyBindUseItem;
        }
    }

    public boolean addBlockDestroyEffects(
            @Nonnull final World world,
            @Nonnull final BlockPos pos,
            IBlockState state,
            final ParticleManager effectRenderer) {
        if (!state.getBlock().isAir(state, world, pos)) {
            state = state.getActualState(new HarvestWorld(state), pos);
            final int StateID = ModUtil.getStateId(state);

            final int i = 4;

            for (int j = 0; j < i; ++j) {
                for (int k = 0; k < i; ++k) {
                    for (int l = 0; l < i; ++l) {
                        final double d0 = pos.getX() + (j + 0.5D) / i;
                        final double d1 = pos.getY() + (k + 0.5D) / i;
                        final double d2 = pos.getZ() + (l + 0.5D) / i;
                        effectRenderer.spawnEffectParticle(EnumParticleTypes.BLOCK_CRACK.getParticleID(), d0, d1, d2, d0 - pos.getX() - 0.5D, d1 - pos.getY() - 0.5D, d2 - pos.getZ() - 0.5D, StateID);
                    }
                }
            }
        }

        return true;
    }

    public TextureAtlasSprite getMissingIcon() {
        return Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
    }

    public String getModeKey() {
        return getKeyName(modeMenu);

    }

    public ChiselToolType getDrawnTool() {
        return lastTool;
    }

    public boolean holdingShift() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    public String getKeyName(
            KeyBinding bind) {
        if (bind == null) {
            return LocalStrings.noBind.getLocal();
        }

        if (bind.getKeyCode() == 0 && bind.getKeyCodeDefault() != 0) {
            return makeMoreFrendly(GameSettings.getKeyDisplayString(bind.getKeyCodeDefault()));
        }

        if (bind.getKeyCode() == 0) {
            return '"' + DeprecationHelper.translateToLocal(bind.getKeyDescription());
        }

        return makeMoreFrendly(bind.getDisplayName());
    }

    private String makeMoreFrendly(
            String displayName) {
        return displayName
                .replace("LMENU", LocalStrings.leftAlt.getLocal())
                .replace("RMENU", LocalStrings.rightAlt.getLocal())
                .replace("LSHIFT", LocalStrings.leftShift.getLocal())
                .replace("RSHIFT", LocalStrings.rightShift.getLocal());
    }

    public static boolean offGridPlacement(
            EntityPlayer player) {
        if (player instanceof FakePlayer)
            return false;

        if (player.getEntityWorld().isRemote)
            return getOffGridPlacementKey().isKeyDown();

        throw new RuntimeException("checking keybinds on server.");
    }

    public static KeyBinding getOffGridPlacementKey() {
        if (ClientSide.instance.offgridPlacement.isSetToDefaultValue())
            return Minecraft.getMinecraft().gameSettings.keyBindSneak;

        return ClientSide.instance.offgridPlacement;
    }

}
