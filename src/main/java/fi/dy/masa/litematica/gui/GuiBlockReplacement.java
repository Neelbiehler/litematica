package fi.dy.masa.litematica.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.widgets.WidgetDashboardButton;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.edit.BlockReplacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.input.ScanCodes;

/** One replacement workflow shared by placement planning and the farming list. */
public class GuiBlockReplacement extends GuiWorkflowBase
{
    private final LitematicaSchematic schematic;
    @Nullable private final MaterialListBase materialList;
    private final List<Block> sourceBlocks;
    private final List<Block> targetBlocks;
    private final List<List<Block>> filtered = new ArrayList<>();
    private final GuiTextFieldGeneric[] search = new GuiTextFieldGeneric[2];
    private final String[] queries = { "", "" };
    private final int[] scroll = new int[2];
    private final Block[] selection = new Block[2];
    private BlockReplacement preview;
    private final BlockReplacement.Target pickedTarget;
    private boolean singleBlock;
    private WidgetDashboardButton previewButton;
    private WidgetDashboardButton applyButton;
    private WidgetDashboardButton undoButton;
    private String status = "";

    public GuiBlockReplacement(LitematicaSchematic schematic, @Nullable Block source, @Nullable MaterialListBase materialList)
    {
        this(schematic, source, materialList, null);
    }

    public GuiBlockReplacement(LitematicaSchematic schematic, @Nullable Block source, @Nullable MaterialListBase materialList, @Nullable BlockReplacement.Target pickedTarget)
    {
        this.pickedTarget = pickedTarget;
        this.singleBlock = pickedTarget != null;
        this.schematic = schematic;
        this.materialList = materialList;
        this.title = text("title");
        Set<Block> blocks = new LinkedHashSet<>();
        for (String region : schematic.getAreas().keySet())
        {
            var container = schematic.getSubRegionContainer(region);
            if (container != null) container.getPalette().fromMapping().forEach(state -> { if (!state.isAir()) blocks.add(state.getBlock()); });
        }
        Comparator<Block> order = Comparator.comparing(GuiBlockReplacement::name).thenComparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString());
        this.sourceBlocks = new ArrayList<>(blocks.stream().sorted(order).toList());
        this.targetBlocks = new ArrayList<>();
        BuiltInRegistries.BLOCK.forEach(block -> { if (block == Blocks.AIR || block.asItem() != Items.AIR) this.targetBlocks.add(block); });
        this.targetBlocks.sort(order);
        this.filtered.add(this.sourceBlocks); this.filtered.add(this.targetBlocks);
        if (source != null && blocks.contains(source)) this.selection[0] = source;
    }

    public static String text(String key, Object... args) { return StringUtils.translate("litematica.gui.replace." + key, args); }
    private static String name(Block block) { return block.getName().getString(); }
    private int columnWidth() { return (this.getScreenWidth() - 32) / 2; }
    private int columnX(int column) { return 12 + column * (this.columnWidth() + 8); }
    private int rows() { return Math.max(1, (this.getScreenHeight() - 180) / 24); }
    private int listBottom() { return 86 + this.rows() * 24; }

    @Override
    public void initGui()
    {
        super.initGui();
        if (this.pickedTarget != null)
        {
            this.addDashboardButton(this.getScreenWidth() - 122, 8, 110, 22, text(this.singleBlock ? "mode.single" : "mode.all"),
                    text("mode.help"), false, (button, mouse) -> {
                        this.singleBlock = !this.singleBlock; this.preview = null; this.status = ""; this.initGui();
                    });
        }
        for (int column = 0; column < 2; ++column)
        {
            final int index = column;
            this.search[column] = new GuiTextFieldGeneric(this.columnX(column), 62, this.columnWidth(), 18, this.font);
            this.search[column].setValueWrapper(this.queries[column]);
            this.addTextField(this.search[column], field -> {
                this.queries[index] = field.getValueWrapper(); this.filter(index); return true;
            });
            this.filter(column);
        }
        int w = (this.getScreenWidth() - 36) / 3;
        int y = this.getScreenHeight() - 54;
        this.previewButton = this.addDashboardButton(12, y, w, 22, text("preview"), text("preview.help"), false, (b, m) -> this.preview());
        this.applyButton = this.addDashboardButton(18 + w, y, w, 22, text(this.singleBlock ? "apply.single" : "apply"), text("apply.help"), true, (b, m) -> {
            if (this.preview != null && this.preview.apply())
            {
                this.schematic.setLastBlockReplacement(this.preview);
                this.status = text("applied", this.preview.getChangedCount()); this.preview = null;
                this.refreshMaterials();
            }
            else this.status = text("stale");
            this.updateButtons();
        });
        this.undoButton = this.addDashboardButton(24 + w * 2, y, w, 22, text("undo"), text("undo.help"), false, (b, m) -> {
            BlockReplacement edit = this.schematic.getLastBlockReplacement();
            if (edit != null && edit.undo())
            {
                this.schematic.setLastBlockReplacement(null); this.preview = null;
                this.status = text("undone"); this.refreshMaterials();
            }
            else this.status = text("stale");
            this.updateButtons();
        });
        this.addDashboardButton(12, y + 28, w, 22, text("back"), "", false, (b, m) -> this.closeGui(true));
        this.addDashboardButton(18 + w, y + 28, w * 2 + 6, 22, text("save"), text("save.help"), false, (b, m) -> {
            GuiSchematicSave screen = new GuiSchematicSave(this.schematic); screen.setParent(this); GuiBase.openGui(screen);
        });
        this.updateButtons();
    }

    private void filter(int column)
    {
        String query = this.queries[column].strip().toLowerCase(Locale.ROOT);
        List<Block> blocks = column == 0 ? this.sourceBlocks : this.targetBlocks;
        this.filtered.set(column, blocks.stream().filter(block -> name(block).toLowerCase(Locale.ROOT).contains(query) ||
                BuiltInRegistries.BLOCK.getKey(block).toString().contains(query)).toList());
        this.scroll[column] = 0;
    }
    private void select(int column, Block block)
    {
        this.selection[column] = block; this.preview = null; this.status = ""; this.updateButtons();
    }
    private void preview()
    {
        if (this.selection[0] == null || this.selection[1] == null) return;
        try
        {
            this.preview = new BlockReplacement(this.schematic, this.selection[0], this.selection[1], this.singleBlock ? this.pickedTarget : null);
            this.status = "";
        }
        catch (IllegalArgumentException e) { this.preview = null; this.status = text(this.singleBlock ? "incompatible.single" : "incompatible"); }
        this.updateButtons();
    }
    private void updateButtons()
    {
        if (this.previewButton == null) return;
        this.previewButton.setEnabled(this.selection[0] != null && this.selection[1] != null && this.selection[0] != this.selection[1] && (this.preview == null || this.preview.isReady()));
        this.applyButton.setEnabled(this.preview != null && this.preview.isReady() && this.preview.getChangedCount() > 0);
        BlockReplacement last = this.schematic.getLastBlockReplacement();
        this.undoButton.setEnabled(last != null && last.canUndo());
    }
    @Override
    public void tick()
    {
        super.tick();
        if (this.preview != null && !this.preview.isReady())
        {
            this.preview.advance(65536);
            if (this.preview.isReady()) this.updateButtons();
        }
    }

    private void refreshMaterials()
    {
        this.sourceBlocks.clear();
        Set<Block> blocks = new LinkedHashSet<>();
        for (String region : this.schematic.getAreas().keySet())
        {
            var container = this.schematic.getSubRegionContainer(region);
            if (container != null) container.getPalette().fromMapping().forEach(state -> { if (!state.isAir()) blocks.add(state.getBlock()); });
        }
        this.sourceBlocks.addAll(blocks.stream().sorted(Comparator.comparing(GuiBlockReplacement::name)).toList());
        this.filter(0);
        Set<MaterialListBase> lists = new LinkedHashSet<>();
        if (this.materialList != null) lists.add(this.materialList);
        MaterialListBase active = DataManager.getMaterialList();
        if (active != null && active.getEditableSchematic() == this.schematic) lists.add(active);
        for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements())
        {
            if (placement.getSchematic() != this.schematic) continue;
            if (placement.getCachedMaterialList() != null) lists.add(placement.getCachedMaterialList());
            if (placement.hasVerifier()) placement.getSchematicVerifier().reset();
        }
        for (MaterialListBase list : lists)
        {
            if (list instanceof MaterialListPlacement placementList) placementList.invalidateForSchematicEdit();
        }
        DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(this.schematic);
        lists.forEach(MaterialListBase::reCreateMaterialList);
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick)
    {
        if (click.button() == ScanCodes.OFFSET_MOUSE_LEFT && click.y() >= 86 && click.y() < this.listBottom())
        {
            for (int column = 0; column < 2; ++column) if (click.x() >= this.columnX(column) && click.x() < this.columnX(column) + this.columnWidth())
            {
                int index = this.scroll[column] + (int) (click.y() - 86) / 24;
                if (index < this.filtered.get(column).size()) this.select(column, this.filtered.get(column).get(index));
                this.search[0].setFocusedWrapper(false); this.search[1].setFocusedWrapper(false);
                return true;
            }
        }
        boolean handled = super.onMouseClicked(click, doubleClick);
        if (this.search[0].isFocusedWrapper() || this.search[1].isFocusedWrapper())
        {
            this.focusedIndex = -1;
            this.updateKeyboardFocus();
        }
        return handled;
    }
    @Override
    public boolean onMouseScrolled(double x, double y, double horizontal, double vertical)
    {
        if (y >= 86 && y < this.listBottom()) for (int column = 0; column < 2; ++column)
        {
            if (x >= this.columnX(column) && x < this.columnX(column) + this.columnWidth())
            {
                this.scroll[column] = Math.clamp(this.scroll[column] - (int) Math.signum(vertical) * 3, 0, Math.max(0, this.filtered.get(column).size() - this.rows()));
                return true;
            }
        }
        return super.onMouseScrolled(x, y, horizontal, vertical);
    }
    @Override
    public boolean onKeyTyped(KeyEvent input)
    {
        for (int column = 0; column < 2; ++column) if (this.search[column].isFocusedWrapper())
        {
            if (input.key() == ScanCodes.SCAN_TAB)
            {
                this.search[column].setFocusedWrapper(false);
                if (column == 0 && !input.hasShiftDown()) { this.search[1].setFocusedWrapper(true); return true; }
                this.focusedIndex = -1; return super.onKeyTyped(input);
            }
            if (input.key() == ScanCodes.SCAN_DOWN || input.key() == ScanCodes.SCAN_UP)
            {
                List<Block> blocks = this.filtered.get(column);
                if (!blocks.isEmpty())
                {
                    int index = Math.clamp(blocks.indexOf(this.selection[column]) + (input.key() == ScanCodes.SCAN_DOWN ? 1 : -1), 0, blocks.size() - 1);
                    this.select(column, blocks.get(index));
                    this.scroll[column] = Math.clamp(index - this.rows() + 1, 0, Math.max(0, blocks.size() - this.rows()));
                }
                return true;
            }
            if (this.search[column].keyPressedWrapper(input)) return true;
        }
        return super.onKeyTyped(input);
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        this.drawString(ctx, this.font.plainSubstrByWidth(text("title"), this.getScreenWidth() - (this.pickedTarget != null ? 146 : 24)), 12, 12, UiTheme.TEXT);
        long placements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().stream().filter(p -> p.getSchematic() == this.schematic).count();
        this.drawString(ctx, this.font.plainSubstrByWidth(text(this.singleBlock ? "scope.single" : "scope", placements), this.getScreenWidth() - 24), 12, 28, UiTheme.MUTED);
    }
    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        for (int column = 0; column < 2; ++column)
        {
            int x = this.columnX(column), w = this.columnWidth();
            this.drawString(ctx, text(column == 0 ? "source" : "target"), x, 48, UiTheme.ACCENT);
            List<Block> blocks = this.filtered.get(column);
            for (int row = 0; row < this.rows(); ++row)
            {
                int index = this.scroll[column] + row, y = 86 + row * 24;
                RenderUtils.drawRect(ctx, x, y, w, 23, UiTheme.SURFACE);
                if (index >= blocks.size()) continue;
                Block block = blocks.get(index);
                if (block == this.selection[column]) RenderUtils.drawRect(ctx, x, y, 2, 23, UiTheme.ACCENT);
                if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 23) RenderUtils.drawRect(ctx, x + 2, y, w - 2, 23, UiTheme.HOVER);
                if (block.asItem() != Items.AIR) ctx.renderItem(new ItemStack(block), x + 4, y + 3);
                this.drawString(ctx, this.font.plainSubstrByWidth(name(block), w - 30), x + 24, y + 3, block == this.selection[column] ? UiTheme.ACCENT : UiTheme.TEXT);
                this.drawString(ctx, this.font.plainSubstrByWidth(BuiltInRegistries.BLOCK.getKey(block).toString(), w - 30), x + 24, y + 13, UiTheme.MUTED);
            }
            if (blocks.isEmpty()) this.drawString(ctx, text("empty"), x + 6, 94, UiTheme.MUTED);
            if (blocks.size() > this.rows())
            {
                int height = this.rows() * 24;
                int thumb = Math.max(8, height * this.rows() / blocks.size());
                int thumbY = 86 + (height - thumb) * this.scroll[column] / (blocks.size() - this.rows());
                RenderUtils.drawRect(ctx, x + w - 2, thumbY, 2, thumb, UiTheme.ACCENT);
            }
        }
        String chosen = (this.selection[0] == null ? text("choose.source") : name(this.selection[0])) + " → " + (this.selection[1] == null ? text("choose.target") : name(this.selection[1]));
        this.drawString(ctx, this.font.plainSubstrByWidth(chosen, this.getScreenWidth() - 24), 12, this.getScreenHeight() - 82, UiTheme.TEXT);
        String message = this.preview != null ? this.preview.isReady() ? text(this.preview.getChangedCount() == 1 ? "count.single" : "count", this.preview.getChangedCount()) : text("progress", this.preview.getProgress()) : this.status.isEmpty() ? text("hint") : this.status;
        this.drawString(ctx, this.font.plainSubstrByWidth(message, this.getScreenWidth() - 24), 12, this.getScreenHeight() - 68, UiTheme.MUTED);
    }
}
