package mod.chiselsandbits.bitbag;

import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.helpers.LocalStrings;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.network.NetworkRouter;
import mod.chiselsandbits.network.packets.PacketBagGui;
import mod.chiselsandbits.network.packets.PacketClearBagGui;
import mod.chiselsandbits.network.packets.PacketSortBagGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class BagGui extends GuiContainer {

    private static final ResourceLocation BAG_GUI_TEXTURE = new ResourceLocation(ChiselsAndBits.MODID, "textures/gui/container/bitbag.png");
    private static int INNER_SLOT_SIZE = 16;

    private static GuiBagFontRenderer specialFontRenderer = null;
    private GuiIconButton trashBtn;
    private GuiIconButton sortBtn;

    public BagGui(
            final EntityPlayer player,
            final World world,
            final int x,
            final int y,
            final int z) {
        super(new BagContainer(player, world, x, y, z));

        allowUserInput = false;
        ySize = 239;
    }

    @Override
    public void initGui() {
        super.initGui();

        buttonList.add(trashBtn = new GuiIconButton(1, guiLeft - 18, guiTop, "help.trash", ClientSide.trashIcon));
        buttonList.add(sortBtn = new GuiIconButton(1, guiLeft - 18, guiTop + 18, "help.sort", ClientSide.sortIcon));
    }

    BagContainer getBagContainer() {
        return (BagContainer) inventorySlots;
    }

    @Override
    protected boolean checkHotbarKeys(final int keyCode) {
        if (getSlotUnderMouse() instanceof SlotBit) {
            final Slot s = inventorySlots.getSlotFromInventory(getBagContainer().thePlayer.inventory, 0);

            if (s != null) {
                hoveredSlot = s;
            }
        }

        return super.checkHotbarKeys(keyCode);
    }

    @Override
    public void drawScreen(
            final int mouseX,
            final int mouseY,
            final float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(
            final float partialTicks,
            final int mouseX,
            final int mouseY) {
        final int xOffset = (width - xSize) / 2;
        final int yOffset = (height - ySize) / 2;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(BAG_GUI_TEXTURE);
        this.drawTexturedModalRect(xOffset, yOffset, 0, 0, xSize, ySize);
    }

    private Slot getSlotAtPosition(
            final int x,
            final int y) {
        for (int slotIdx = 0; slotIdx < getBagContainer().customSlots.size(); ++slotIdx) {
            final Slot slot = getBagContainer().customSlots.get(slotIdx);
            if (isMouseOverSlot(slot, x, y)) {
                return slot;
            }
        }

        return null;
    }

    @Override
    protected void mouseClicked(
            final int mouseX,
            final int mouseY,
            final int mouseButton) throws IOException {
        // This is what vanilla does...
        final boolean duplicateButton = mouseButton == mc.gameSettings.keyBindPickBlock.getKeyCode() + 100;

        final Slot slot = getSlotAtPosition(mouseX, mouseY);
        if (slot != null) {
            final PacketBagGui bagGuiPacket = new PacketBagGui();

            bagGuiPacket.slotNumber = slot.slotNumber;
            bagGuiPacket.mouseButton = mouseButton;
            bagGuiPacket.duplicateButton = duplicateButton;
            bagGuiPacket.holdingShift = ClientSide.instance.holdingShift();

            bagGuiPacket.doAction(ClientSide.instance.getPlayer());
            NetworkRouter.instance.sendToServer(bagGuiPacket);

            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(
            final int mouseX,
            final int mouseY) {
        fontRenderer.drawString(ChiselsAndBits.getItems().itemBitBag.getItemStackDisplayName(ModUtil.getEmptyStack()), 8, 6, 0x404040);
		fontRenderer.drawString(I18n.format("container.inventory"), 8, ySize - 93, 0x404040);

        RenderHelper.enableGUIStandardItemLighting();

        if (specialFontRenderer == null) {
            specialFontRenderer = new GuiBagFontRenderer(fontRenderer, ChiselsAndBits.getConfig().bagStackSize);
        }

        for (int slotIdx = 0; slotIdx < getBagContainer().customSlots.size(); ++slotIdx) {
            final Slot slot = getBagContainer().customSlots.get(slotIdx);

            final FontRenderer defaultFontRenderer = fontRenderer;

            try {
				fontRenderer = specialFontRenderer;
                drawSlot(slot);
            } finally {
				fontRenderer = defaultFontRenderer;
            }

            if (isMouseOverSlot(slot, mouseX, mouseY) && slot.isEnabled()) {
                final int xDisplayPos = slot.xPos;
                final int yDisplayPos = slot.yPos;
                hoveredSlot = slot;

                GlStateManager.disableLighting();
                GlStateManager.disableDepth();
                GlStateManager.colorMask(true, true, true, false);
                drawGradientRect(xDisplayPos, yDisplayPos, xDisplayPos + INNER_SLOT_SIZE, yDisplayPos + INNER_SLOT_SIZE, 0x80FFFFFF, 0x80FFFFFF);
                GlStateManager.colorMask(true, true, true, true);
                GlStateManager.enableLighting();
                GlStateManager.enableDepth();
            }
        }

        if (sortBtn.isMouseOver()) {
            final List<String> text = Arrays
                    .asList(new String[]{LocalStrings.Sort.getLocal()});
            drawHoveringText(text, mouseX - guiLeft, mouseY - guiTop, fontRenderer);
        }

        if (trashBtn.isMouseOver()) {
            if (isValidBitItem()) {
                final String msgNotConfirm = ModUtil.notEmpty(getInHandItem()) ? LocalStrings.TrashItem.getLocal(getInHandItem().getDisplayName()) : LocalStrings.Trash.getLocal();
                final String msgConfirm = ModUtil.notEmpty(getInHandItem()) ? LocalStrings.ReallyTrashItem.getLocal(getInHandItem().getDisplayName()) : LocalStrings.ReallyTrash.getLocal();

                final List<String> text = Arrays
                        .asList(new String[]{requireConfirm ? msgNotConfirm : msgConfirm});
                drawHoveringText(text, mouseX - guiLeft, mouseY - guiTop, fontRenderer);
            } else {
                final List<String> text = Arrays
                        .asList(new String[]{LocalStrings.TrashInvalidItem.getLocal(getInHandItem().getDisplayName())});
                drawHoveringText(text, mouseX - guiLeft, mouseY - guiTop, fontRenderer);
            }
        } else {
            requireConfirm = true;
        }
    }

    private ItemStack getInHandItem() {
        return getBagContainer().thePlayer.inventory.getItemStack();
    }

    boolean requireConfirm = true;
    boolean dontThrow = false;

    @Override
    protected void actionPerformed(
            final GuiButton button) throws IOException {
        if (button == sortBtn) {
            NetworkRouter.instance.sendToServer(new PacketSortBagGui());
        }

        if (button == trashBtn) {
            if (requireConfirm) {
                dontThrow = true;
                if (isValidBitItem()) {
                    requireConfirm = false;
                }
            } else {
                requireConfirm = true;
                // server side!
                NetworkRouter.instance.sendToServer(new PacketClearBagGui(getInHandItem()));
                dontThrow = false;
            }
        }
    }

    private boolean isValidBitItem() {
        return ModUtil.isEmpty(getInHandItem()) || getInHandItem().getItem() == ChiselsAndBits.getItems().itemBlockBit;
    }

    @Override
    protected void handleMouseClick(
            final Slot slotIn,
            final int slotId,
            final int mouseButton,
            final ClickType type) {
        if ((type == ClickType.PICKUP || type == ClickType.THROW) && dontThrow) {
            dontThrow = false;
            return;
        }

        super.handleMouseClick(slotIn, slotId, mouseButton, type);
    }

}
