package fi.dy.masa.litematica.gui;

import java.util.Comparator;
import fi.dy.masa.litematica.schematic.edit.BlockReplacement;
import fi.dy.masa.malilib.util.position.PositionUtils.CoordinateType;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.level.block.Block;
import fi.dy.masa.litematica.gui.canvas.CanvasCamera;
import fi.dy.masa.litematica.gui.canvas.CanvasMesh;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.input.ScanCodes;

/** An orbitable 3D workspace. Dragging only rebuilds the placement on release. */
public class GuiPlacementCanvas extends GuiWorkflowBase
{
    private final CanvasCamera camera = new CanvasCamera();
    private final Map<SchematicPlacement, CanvasMesh> meshes = new LinkedHashMap<>();
    private List<CanvasMesh.ProjectedFace> projected = List.of();
    private ViewState projectedView;
    private long meshRevision;
    private final List<float[]> lines = new ArrayList<>();
    private boolean fitted;
    private boolean panning;
    private boolean orbiting;
    private BlockReplacement.Target pickedTarget;
    private SchematicPlacement pickedPlacement;
    private SchematicPlacement dragged;
    private BlockPos dragOrigin;
    private double startX;
    private double startY;
    private int offsetX;
    private int offsetZ;
    private SchematicPlacement undoPlacement;
    private BlockPos undoOrigin;
    private BlockPos redoOrigin;
    private Rotation undoRotation;
    private Rotation redoRotation;

    public GuiPlacementCanvas() { this.title = text("title"); this.useTitleHierarchy = false; }
    private static String text(String key, Object... args) { return StringUtils.translate("litematica.gui.canvas." + key, args); }
    private List<SchematicPlacement> placements() { return DataManager.getSchematicPlacementManager().getAllSchematicsPlacements(); }
    private SchematicPlacement selected() { return DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement(); }
    private int bottom() { return this.getScreenHeight() - 86; }
    private boolean inside(double x, double y) { return x >= 12 && x < this.getScreenWidth() - 12 && y >= 58 && y < this.bottom(); }

    @Override
    public void initGui()
    {
        super.initGui();
        this.camera.screenX = this.getScreenWidth() / 2.0;
        this.camera.screenY = (58 + this.bottom()) / 2.0;
        if (!this.fitted) { this.fit(); this.fitted = true; }
        int w = (this.getScreenWidth() - 36) / 4;
        this.addDashboardButton(12, 30, w, 22, text("load"), text("load.help"), true, (b, m) -> this.open(new GuiSchematicLoad()));
        this.addDashboardButton(16 + w, 30, w, 22, text("fit"), text("fit.help"), false, (b, m) -> this.fit());
        this.addDashboardButton(20 + w * 2, 30, w, 22, text("list"), "", false, (b, m) -> this.open(new GuiSchematicPlacementsList()));
        this.addDashboardButton(24 + w * 3, 30, w, 22, text("undo"), text("undo.help"), false, (b, m) -> this.undo()).setEnabled(this.canUndo());
        SchematicPlacement p = this.selected();
        int y = this.getScreenHeight() - 56;
        this.addDashboardButton(12, y, w, 22, text("rotate"), "", false, (b, m) -> {
            this.remember(p); p.setRotation(PositionUtils.cycleRotation(p.getRotation(), m == ScanCodes.OFFSET_MOUSE_RIGHT), this); this.rememberResult(p); this.initGui();
        }).setEnabled(p != null && !p.isLocked());
        this.addDashboardButton(16 + w, y, w, 22, text("height.down"), text("height.help"), false, (b, m) -> this.height(p, -1)).setEnabled(p != null && !p.isLocked());
        this.addDashboardButton(20 + w * 2, y, w, 22, text("height.up"), text("height.help"), false, (b, m) -> this.height(p, 1)).setEnabled(p != null && !p.isLocked());
        this.addDashboardButton(24 + w * 3, y, w, 22, text("duplicate"), text("duplicate.help"), false, (b, m) -> {
            SchematicPlacement copy = p.duplicate(p.getOrigin().offset(2, 0, 2), text("copy", p.getName()));
            DataManager.getSchematicPlacementManager().addSchematicPlacement(copy, true);
            DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(copy);
            this.initGui();
        }).setEnabled(p != null);
        y += 28;
        this.addDashboardButton(12, y, w, 22, text("back"), "", false, (b, m) -> this.closeGui(true));
        this.addDashboardButton(16 + w, y, w, 22, text("replace"), text("replace.help"), true,
                (b, m) -> this.replaceBlocks(p)).setEnabled(p != null);
        this.addDashboardButton(20 + w * 2, y, w, 22, text("materials"), "", false, (b, m) -> {
            MaterialListBase list = p.getMaterialList(); list.reCreateMaterialList(); this.open(new GuiMaterialList(list));
        }).setEnabled(p != null);
        this.addDashboardButton(24 + w * 3, y, w, 22, text("details"), "", false,
                (b, m) -> this.open(new GuiPlacementWorkspace(p))).setEnabled(p != null);
    }

    private void replaceBlocks(SchematicPlacement placement)
    {
        BlockReplacement.Target target = placement == this.pickedPlacement ? this.pickedTarget : null;
        Block source = null;
        if (target != null)
        {
            var container = placement.getSchematic().getSubRegionContainer(target.region());
            if (container != null) source = container.get(target.position().getX(), target.position().getY(), target.position().getZ()).getBlock();
            else target = null;
        }
        this.open(new GuiBlockReplacement(placement.getSchematic(), source, null, target));
    }

    private void open(GuiBase screen) { screen.setParent(this); GuiBase.openGui(screen); }
    private void height(SchematicPlacement p, int direction)
    {
        this.remember(p);
        p.setOrigin(p.getOrigin().offset(0, direction * (GuiBase.isShiftDown() ? 10 : 1), 0), this);
        this.rememberResult(p);
        this.initGui();
    }
    private void remember(SchematicPlacement p) { this.undoPlacement = p; this.undoOrigin = p.getOrigin(); this.undoRotation = p.getRotation(); }
    private void rememberResult(SchematicPlacement p) { this.redoOrigin = p.getOrigin(); this.redoRotation = p.getRotation(); }
    private boolean canUndo()
    {
        SchematicPlacement p = this.undoPlacement;
        if (p == null || !this.placements().contains(p) || p.isLocked() || !p.getOrigin().equals(this.redoOrigin) || p.getRotation() != this.redoRotation) return false;
        if (this.undoOrigin.equals(this.redoOrigin) && this.undoRotation == this.redoRotation) return false;
        return (!p.isCoordinateLocked(CoordinateType.X) || this.undoOrigin.getX() == this.redoOrigin.getX()) &&
               (!p.isCoordinateLocked(CoordinateType.Y) || this.undoOrigin.getY() == this.redoOrigin.getY()) &&
               (!p.isCoordinateLocked(CoordinateType.Z) || this.undoOrigin.getZ() == this.redoOrigin.getZ());
    }
    private void undo()
    {
        if (this.canUndo())
        {
            this.undoPlacement.setOrigin(this.undoOrigin, this);
            this.undoPlacement.setRotation(this.undoRotation, this);
            this.undoPlacement = null;
        }
        this.initGui();
    }

    private void fit()
    {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (SchematicPlacement p : this.placements()) for (Box b : p.getSubRegionBoxes(RequiredEnabled.ANY).values())
        {
            if (b.getPos1() == null || b.getPos2() == null) continue;
            minX = Math.min(minX, Math.min(b.getPos1().getX(), b.getPos2().getX()));
            minY = Math.min(minY, Math.min(b.getPos1().getY(), b.getPos2().getY()));
            minZ = Math.min(minZ, Math.min(b.getPos1().getZ(), b.getPos2().getZ()));
            maxX = Math.max(maxX, Math.max(b.getPos1().getX(), b.getPos2().getX()) + 1.0);
            maxY = Math.max(maxY, Math.max(b.getPos1().getY(), b.getPos2().getY()) + 1.0);
            maxZ = Math.max(maxZ, Math.max(b.getPos1().getZ(), b.getPos2().getZ()) + 1.0);
        }
        if (Double.isFinite(minX))
        {
            this.camera.x = (minX + maxX) / 2; this.camera.y = (minY + maxY) / 2; this.camera.z = (minZ + maxZ) / 2;
            double diagonal = Math.sqrt(Math.pow(maxX - minX, 2) + Math.pow(maxY - minY, 2) + Math.pow(maxZ - minZ, 2));
            this.camera.zoom = Math.clamp(Math.min(this.getScreenWidth() - 64, this.bottom() - 74) / (diagonal + 2), 0.02, 24);
        }
        else if (this.mc.player != null)
        {
            this.camera.x = this.mc.player.getX(); this.camera.y = this.mc.player.getY(); this.camera.z = this.mc.player.getZ();
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.meshes.keySet().retainAll(this.placements())) ++this.meshRevision;
        int faceLimit = Math.max(1, 160_000 / Math.max(1, this.placements().size()));
        int budget = Math.max(128, 8192 / Math.max(1, this.placements().size()));
        for (SchematicPlacement p : this.placements())
        {
            CanvasMesh mesh = this.meshes.get(p);
            if (mesh == null || !mesh.matches(faceLimit))
            {
                mesh = new CanvasMesh(p, faceLimit); this.meshes.put(p, mesh); ++this.meshRevision;
            }
            if (!mesh.isReady()) { mesh.advance(budget); ++this.meshRevision; }
        }
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick)
    {
        if (!this.inside(click.x(), click.y())) return super.onMouseClicked(click, doubleClick);
        if (this.dragged != null || this.panning || this.orbiting) return true;
        this.startX = click.x(); this.startY = click.y();
        if (click.button() == ScanCodes.OFFSET_MOUSE_RIGHT) { this.orbiting = true; return true; }
        if (click.button() == ScanCodes.OFFSET_MOUSE_MIDDLE) { this.panning = true; return true; }
        if (click.button() != ScanCodes.OFFSET_MOUSE_LEFT) return false;
        for (int i = this.projected.size() - 1; i >= 0; --i)
        {
            CanvasMesh.ProjectedFace face = this.projected.get(i);
            if (face.contains(click.x(), click.y()))
            {
                SchematicPlacement p = face.placement();
                DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(p);
                this.pickedTarget = face.target();
                this.pickedPlacement = p;
                if (!p.isLocked()) { this.dragged = p; this.dragOrigin = p.getOrigin(); this.offsetX = this.offsetZ = 0; }
                this.initGui(); return true;
            }
        }
        this.pickedTarget = null;
        DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(null);
        this.initGui(); return true;
    }

    @Override
    public boolean onMouseDragged(MouseButtonEvent click, double dx, double dy)
    {
        if (this.orbiting) { this.camera.orbit(dx, dy); return true; }
        if (this.panning)
        {
            double[] delta = this.camera.groundDelta(dx, dy);
            this.camera.x -= delta[0]; this.camera.z -= delta[1]; return true;
        }
        if (this.dragged != null)
        {
            double[] delta = this.camera.groundDelta(click.x() - this.startX, click.y() - this.startY);
            this.offsetX = (int) Math.round(delta[0]);
            this.offsetZ = (int) Math.round(delta[1]);
            if (this.dragged.isCoordinateLocked(CoordinateType.X)) this.offsetX = 0;
            if (this.dragged.isCoordinateLocked(CoordinateType.Z)) this.offsetZ = 0;
            return true;
        }
        return super.onMouseDragged(click, dx, dy);
    }

    @Override
    public boolean onMouseReleased(MouseButtonEvent click)
    {
        if (this.dragged != null && click.button() == ScanCodes.OFFSET_MOUSE_LEFT)
        {
            if (this.dragged.getOrigin().equals(this.dragOrigin) && (this.offsetX != 0 || this.offsetZ != 0))
            {
                this.remember(this.dragged);
                this.dragged.setOrigin(this.dragOrigin.offset(this.offsetX, 0, this.offsetZ), this);
                this.rememberResult(this.dragged);
            }
            this.dragged = null; this.initGui(); return true;
        }
        if (this.orbiting && click.button() == ScanCodes.OFFSET_MOUSE_RIGHT) { this.orbiting = false; return true; }
        if (this.panning && click.button() == ScanCodes.OFFSET_MOUSE_MIDDLE) { this.panning = false; return true; }
        return super.onMouseReleased(click);
    }

    @Override
    public void removed()
    {
        this.dragged = null;
        this.panning = false;
        this.orbiting = false;
        super.removed();
    }

    @Override
    public boolean onMouseScrolled(double x, double y, double horizontal, double vertical)
    {
        if (!this.inside(x, y)) return super.onMouseScrolled(x, y, horizontal, vertical);
        if (this.dragged == null) this.camera.zoomAt(vertical, x, y);
        return true;
    }

    private void line(GuiContext ctx, double[] a, double[] b, int color)
    {
        // Clip before rasterising so distant world coordinates never produce huge loops.
        double dx = b[0] - a[0], dy = b[1] - a[1], start = 0, end = 1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {a[0] - 12, this.getScreenWidth() - 12 - a[0], a[1] - 58, this.bottom() - a[1]};
        for (int i = 0; i < 4; ++i)
        {
            if (p[i] == 0) { if (q[i] < 0) return; }
            else if (p[i] < 0) start = Math.max(start, q[i] / p[i]);
            else end = Math.min(end, q[i] / p[i]);
        }
        if (start > end) return;
        this.lines.add(new float[] {(float) (a[0] + dx * start), (float) (a[1] + dy * start),
                (float) (a[0] + dx * end), (float) (a[1] + dy * end), Float.intBitsToFloat(color)});
    }

    private void outline(GuiContext ctx, SchematicPlacement p)
    {
        int dx = p == this.dragged ? this.offsetX : 0, dz = p == this.dragged ? this.offsetZ : 0;
        for (Box box : p.getSubRegionBoxes(RequiredEnabled.ANY).values())
        {
            BlockPos a = box.getPos1(), b = box.getPos2(); if (a == null || b == null) continue;
            double[][] corners = new double[8][];
            for (int i = 0; i < 8; ++i) corners[i] = this.camera.project(
                ((i & 1) == 0 ? Math.min(a.getX(), b.getX()) : Math.max(a.getX(), b.getX()) + 1.0) + dx,
                (i & 2) == 0 ? Math.min(a.getY(), b.getY()) : Math.max(a.getY(), b.getY()) + 1.0,
                ((i & 4) == 0 ? Math.min(a.getZ(), b.getZ()) : Math.max(a.getZ(), b.getZ()) + 1.0) + dz);
            for (int i = 0; i < 8; ++i) for (int bit : new int[] {1, 2, 4}) if ((i & bit) == 0)
                this.line(ctx, corners[i], corners[i | bit], p == this.selected() ? UiTheme.ACCENT : UiTheme.BORDER);
        }
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        ScreenRectangle viewport = new ScreenRectangle(12, 58, this.getScreenWidth() - 24, Math.max(1, this.bottom() - 58));
        RenderUtils.drawRect(ctx, 12, 58, viewport.width(), viewport.height(), 0xFF121D29);
        int step = 1; while (step * this.camera.zoom < 24) step *= 2;
        double extent = Math.max(this.getScreenWidth(), this.getScreenHeight()) / this.camera.zoom;
        double floor = this.selected() == null ? Math.floor(this.camera.y) : this.selected().getOrigin().getY();
        for (int i = -16; i <= 16; ++i)
        {
            double x = Math.floor(this.camera.x / step) * step + i * step;
            double z = Math.floor(this.camera.z / step) * step + i * step;
            this.line(ctx, this.camera.project(x, floor, this.camera.z - extent), this.camera.project(x, floor, this.camera.z + extent), UiTheme.SURFACE);
            this.line(ctx, this.camera.project(this.camera.x - extent, floor, z), this.camera.project(this.camera.x + extent, floor, z), UiTheme.SURFACE);
        }
        CanvasMesh.renderLines(ctx, viewport, this.lines);
        this.lines.clear();
        ViewState view = new ViewState(this.camera.x, this.camera.y, this.camera.z, this.camera.zoom,
                this.camera.yaw, this.camera.pitch, this.camera.screenX, this.camera.screenY,
                this.dragged, this.offsetX, this.offsetZ, this.meshRevision);
        if (!view.equals(this.projectedView))
        {
            List<CanvasMesh.ProjectedFace> faces = new ArrayList<>();
            for (var entry : this.meshes.entrySet())
            {
                SchematicPlacement p = entry.getKey();
                entry.getValue().project(this.camera, p == this.dragged ? this.offsetX : 0, p == this.dragged ? this.offsetZ : 0, viewport, faces);
            }
            faces.sort(Comparator.comparingDouble(CanvasMesh.ProjectedFace::depth));
            this.projected = List.copyOf(faces);
            this.projectedView = view;
        }
        CanvasMesh.render(ctx, viewport, this.projected);
        // Outlines stay readable through the model and make the active placement explicit.
        ctx.elementUp();
        for (SchematicPlacement p : this.placements()) if (p == this.selected() || this.meshes.get(p) == null || !this.meshes.get(p).isReady()) this.outline(ctx, p);
        CanvasMesh.renderLines(ctx, viewport, this.lines);
        this.lines.clear();
        if (this.placements().isEmpty()) this.drawString(ctx, this.font.plainSubstrByWidth(text("empty"), this.getScreenWidth() - 48), 24, 78, UiTheme.MUTED);
        SchematicPlacement p = this.selected();
        String status = p == null ? text("hint") : p.getName() + "  ·  " +
                (p == this.dragged ? this.dragOrigin.offset(this.offsetX, 0, this.offsetZ) : p.getOrigin()).toShortString() + (p.isLocked() ? "  ·  " + text("locked") : "");
        if (this.meshes.values().stream().anyMatch(CanvasMesh::isLimited)) status = text("limited");
        else if (this.meshes.values().stream().anyMatch(mesh -> !mesh.isReady())) status = text("loading");
        this.drawString(ctx, this.font.plainSubstrByWidth(status, this.getScreenWidth() - 24), 12, this.bottom() + 5, UiTheme.TEXT);
        this.drawString(ctx, this.font.plainSubstrByWidth(text("navigation", step), this.getScreenWidth() - 24), 12, this.bottom() + 17, UiTheme.MUTED);
    }

    private record ViewState(double x, double y, double z, double zoom, double yaw, double pitch,
            double screenX, double screenY, SchematicPlacement dragged, int dx, int dz, long revision) {}
}
