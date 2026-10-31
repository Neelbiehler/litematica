package fi.dy.masa.litematica.gui.canvas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import fi.dy.masa.litematica.schematic.edit.BlockReplacement;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.malilib.render.GuiContext;

/** Incrementally extracts model geometry, independently of world chunk loading. */
public final class CanvasMesh
{
    private final int faceLimit;
    private final SchematicPlacement placement;
    private final List<Region> regions = new ArrayList<>();
    private final List<Face> faces = new ArrayList<>();
    private final Map<BlockState, List<ModelFace>> models = new HashMap<>();
    private final BlockPos origin;
    private final Rotation rotation;
    private final Mirror mirror;
    private int regionIndex;
    private long cursor;
    private boolean limited;

    public CanvasMesh(SchematicPlacement placement, int faceLimit)
    {
        this.faceLimit = faceLimit;
        this.placement = placement; this.origin = placement.getOrigin(); this.rotation = placement.getRotation(); this.mirror = placement.getMirror();
        for (SubRegionPlacement sub : placement.getAllSubRegionsPlacements())
        {
            var container = placement.getSchematic().getSubRegionContainer(sub.getName());
            Vec3i signedSize = placement.getSchematic().getAreaSizeAsVec3i(sub.getName());
            if (container != null && signedSize != null && sub.isEnabled())
                this.regions.add(new Region(sub, container, signedSize));
        }
    }
    public boolean matches(int faceLimit)
    {
        if (this.faceLimit != faceLimit) return false;
        if (!this.origin.equals(this.placement.getOrigin()) || this.rotation != this.placement.getRotation() || this.mirror != this.placement.getMirror()) return false;
        long enabled = this.placement.getAllSubRegionsPlacements().stream().filter(SubRegionPlacement::isEnabled).count();
        if (enabled != this.regions.size()) return false;
        for (Region region : this.regions)
        {
            if (this.placement.getSchematic().getSubRegionContainer(region.sub.getName()) != region.container ||
                region.container.getRevision() != region.revision || !region.sub.getPos().equals(region.position) ||
                region.sub.getRotation() != region.rotation || region.sub.getMirror() != region.mirror || !region.sub.isEnabled()) return false;
        }
        return true;
    }
    public boolean isReady() { return this.limited || this.regionIndex >= this.regions.size(); }
    public boolean isLimited() { return this.limited; }

    public void advance(int budget)
    {
        Minecraft mc = Minecraft.getInstance();
        while (budget-- > 0 && !this.isReady())
        {
            Region region = this.regions.get(this.regionIndex);
            Vec3i size = region.container.getSize();
            int x = (int) (this.cursor % size.getX());
            int z = (int) (this.cursor / size.getX() % size.getZ());
            int y = (int) (this.cursor / ((long) size.getX() * size.getZ()));
            BlockState state = region.container.get(x, y, z);
            if (!state.isAir())
            {
                BlockPos local = region.offset.offset(x, y, z);
                BlockPos world = PositionUtils.getTransformedPlacementPosition(local, this.placement, region.sub).offset(region.worldOrigin);
                List<ModelFace> model = this.models.computeIfAbsent(state, value -> modelFaces(mc, value));
                BlockReplacement.Target target = new BlockReplacement.Target(region.sub.getName(), new BlockPos(x, y, z));
                for (ModelFace face : model)
                {
                    if (face.cull != null)
                    {
                        int nx = x + face.cull.getStepX(), ny = y + face.cull.getStepY(), nz = z + face.cull.getStepZ();
                        if (nx >= 0 && ny >= 0 && nz >= 0 && nx < size.getX() && ny < size.getY() && nz < size.getZ())
                        {
                            BlockState neighbor = region.container.get(nx, ny, nz);
                            if (neighbor.canOcclude() && Block.isShapeFullBlock(neighbor.getOcclusionShape())) continue;
                        }
                    }
                    double[] vertices = new double[12];
                    boolean reflected = (this.mirror != Mirror.NONE) != (region.mirror != Mirror.NONE);
                    float[] uv = reflected ? new float[8] : face.uv;
                    for (int i = 0; i < 4; ++i)
                    {
                        // A reflection reverses face winding; reverse UVs with the vertices.
                        int source = reflected ? (4 - i) % 4 : i;
                        Vec3 v = transformVertex(new Vec3(face.vertices[source * 3], face.vertices[source * 3 + 1], face.vertices[source * 3 + 2]),
                                this.mirror, this.rotation, region.mirror, region.rotation);
                        vertices[i * 3] = world.getX() + v.x;
                        vertices[i * 3 + 1] = world.getY() + v.y;
                        vertices[i * 3 + 2] = world.getZ() + v.z;
                        if (reflected) { uv[i * 2] = face.uv[source * 2]; uv[i * 2 + 1] = face.uv[source * 2 + 1]; }
                    }
                    this.faces.add(new Face(vertices, uv, face.color, state.getBlock(), target));
                    if (this.faces.size() >= this.faceLimit) { this.limited = true; break; }
                }
            }
            if (++this.cursor == (long) size.getX() * size.getY() * size.getZ()) { ++this.regionIndex; this.cursor = 0; }
        }
    }

    static Vec3 transformVertex(Vec3 vertex, Mirror mirror, Rotation rotation, Mirror subMirror, Rotation subRotation)
    {
        // This helper already rotates around the block centre, unlike the BlockPos transform.
        return PositionUtils.getTransformedPosition(PositionUtils.getTransformedPosition(vertex, mirror, rotation), subMirror, subRotation);
    }

    private static List<ModelFace> modelFaces(Minecraft mc, BlockState state)
    {
        List<ModelFace> result = new ArrayList<>();
        List<BlockStateModelPart> parts = new ArrayList<>();
        var model = mc.getModelManager().getBlockStateModelSet().get(state);
        model.collectParts(RandomSource.create(0), parts);
        for (BlockStateModelPart part : parts)
        {
            for (Direction direction : Direction.values()) for (BakedQuad quad : part.getQuads(direction)) result.add(modelFace(mc, state, quad, direction));
            for (BakedQuad quad : part.getQuads(null)) result.add(modelFace(mc, state, quad, null));
        }
        // Fluids and block entities have separate world renderers. Keep them visible as textured cubes.
        if (result.isEmpty())
        {
            var sprite = model.particleMaterial().sprite();
            double[][] sides = {
                {0,1,0, 0,1,1, 1,1,1, 1,1,0}, {0,0,1, 0,0,0, 1,0,0, 1,0,1},
                {0,0,0, 0,1,0, 1,1,0, 1,0,0}, {1,0,1, 1,1,1, 0,1,1, 0,0,1},
                {0,0,1, 0,1,1, 0,1,0, 0,0,0}, {1,0,0, 1,1,0, 1,1,1, 1,0,1}};
            Direction[] directions = {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
            float[] uv = {sprite.getU0(), sprite.getV1(), sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV0(), sprite.getU1(), sprite.getV1()};
            for (int i = 0; i < sides.length; ++i) result.add(new ModelFace(sides[i], uv, 0xFFFFFFFF, directions[i]));
        }
        return result;
    }
    private static ModelFace modelFace(Minecraft mc, BlockState state, BakedQuad quad, Direction cull)
    {
        double[] vertices = new double[12]; float[] uv = new float[8];
        for (int i = 0; i < 4; ++i)
        {
            var v = quad.position(i); vertices[i * 3] = v.x(); vertices[i * 3 + 1] = v.y(); vertices[i * 3 + 2] = v.z();
            uv[i * 2] = UVPair.unpackU(quad.packedUV(i)); uv[i * 2 + 1] = UVPair.unpackV(quad.packedUV(i));
        }
        int color = 0xFFFFFFFF;
        if (quad.materialInfo().isTinted()) color = mc.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex()).color(state) | 0xFF000000;
        return new ModelFace(vertices, uv, color, cull);
    }

    public void project(CanvasCamera camera, int dx, int dz, ScreenRectangle viewport, List<ProjectedFace> output)
    {
        for (Face face : this.faces)
        {
            float[] xy = new float[8]; double depth = 0;
            float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = Float.NEGATIVE_INFINITY, maxY = maxX;
            for (int i = 0; i < 4; ++i)
            {
                double[] p = camera.project(face.vertices[i * 3] + dx, face.vertices[i * 3 + 1], face.vertices[i * 3 + 2] + dz);
                xy[i * 2] = (float) p[0]; xy[i * 2 + 1] = (float) p[1]; depth += p[2];
                minX = Math.min(minX, xy[i * 2]); maxX = Math.max(maxX, xy[i * 2]);
                minY = Math.min(minY, xy[i * 2 + 1]); maxY = Math.max(maxY, xy[i * 2 + 1]);
            }
            // Backface removal also keeps the picking surface consistent with what is drawn.
            float cross = (xy[2] - xy[0]) * (xy[5] - xy[1]) - (xy[3] - xy[1]) * (xy[4] - xy[0]);
            if (cross >= -0.001f || maxX < viewport.left() || minX > viewport.right() || maxY < viewport.top() || minY > viewport.bottom()) continue;
            double ax = face.vertices[3] - face.vertices[0], ay = face.vertices[4] - face.vertices[1], az = face.vertices[5] - face.vertices[2];
            double bx = face.vertices[6] - face.vertices[0], by = face.vertices[7] - face.vertices[1], bz = face.vertices[8] - face.vertices[2];
            double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            double shade = length == 0 ? 1 : 0.66 + 0.28 * Math.abs(ny / length) + 0.06 * Math.abs(nx / length);
            int color = 0xFF000000 | (int) (((face.color >> 16) & 255) * shade) << 16 | (int) (((face.color >> 8) & 255) * shade) << 8 | (int) ((face.color & 255) * shade);
            output.add(new ProjectedFace(xy, face.uv, color, depth / 4, this.placement, face.block, face.target));
        }
    }

    public static void render(GuiContext ctx, ScreenRectangle viewport, List<ProjectedFace> faces)
    {
        ctx.addSimpleElement(new MeshState(faces, ctx.setupTextureOrEmpty(TextureAtlas.LOCATION_BLOCKS), viewport));
    }

    public record ProjectedFace(float[] xy, float[] uv, int color, double depth, SchematicPlacement placement, Block block, BlockReplacement.Target target)
    {
        public boolean contains(double x, double y)
        {
            boolean inside = false;
            for (int i = 0, j = 3; i < 4; j = i++)
                if ((this.xy[i * 2 + 1] > y) != (this.xy[j * 2 + 1] > y) &&
                    x < (this.xy[j * 2] - this.xy[i * 2]) * (y - this.xy[i * 2 + 1]) / (this.xy[j * 2 + 1] - this.xy[i * 2 + 1]) + this.xy[i * 2]) inside = !inside;
            return inside;
        }
    }
    public static void renderLines(GuiContext ctx, ScreenRectangle viewport, List<float[]> lines)
    {
        ctx.addSimpleElement(new LineState(List.copyOf(lines), viewport));
    }

    private record LineState(List<float[]> lines, ScreenRectangle bounds) implements GuiElementRenderState
    {
        @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
        @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        @Override public ScreenRectangle scissorArea() { return this.bounds; }
        @Override public void buildVertices(VertexConsumer out)
        {
            for (float[] line : this.lines)
            {
                float dx = line[2] - line[0], dy = line[3] - line[1];
                float length = (float) Math.sqrt(dx * dx + dy * dy);
                if (length < 0.01f) continue;
                float nx = -dy / length * 0.5f, ny = dx / length * 0.5f;
                int color = Float.floatToRawIntBits(line[4]);
                out.addVertex(line[0] - nx, line[1] - ny, 0).setColor(color);
                out.addVertex(line[0] + nx, line[1] + ny, 0).setColor(color);
                out.addVertex(line[2] + nx, line[3] + ny, 0).setColor(color);
                out.addVertex(line[2] - nx, line[3] - ny, 0).setColor(color);
            }
        }
    }

    private record MeshState(List<ProjectedFace> faces, TextureSetup textureSetup, ScreenRectangle bounds) implements GuiElementRenderState
    {
        @Override public RenderPipeline pipeline() { return RenderPipelines.GUI_TEXTURED; }
        @Override public ScreenRectangle scissorArea() { return this.bounds; }
        @Override public void buildVertices(VertexConsumer out)
        {
            for (ProjectedFace face : this.faces) for (int i = 0; i < 4; ++i)
                out.addVertex(face.xy[i * 2], face.xy[i * 2 + 1], 0).setUv(face.uv[i * 2], face.uv[i * 2 + 1]).setColor(face.color);
        }
    }
    private record Face(double[] vertices, float[] uv, int color, Block block, BlockReplacement.Target target) {}
    private record ModelFace(double[] vertices, float[] uv, int color, Direction cull) {}
    private final class Region
    {
        final SubRegionPlacement sub;
        final LitematicaBlockStateContainer container;
        final long revision;
        final BlockPos position, offset, worldOrigin;
        final Rotation rotation;
        final Mirror mirror;
        Region(SubRegionPlacement sub, LitematicaBlockStateContainer container, Vec3i signedSize)
        {
            this.sub = sub; this.container = container; this.revision = container.getRevision();
            this.position = sub.getPos(); this.rotation = sub.getRotation(); this.mirror = sub.getMirror();
            BlockPos end = PositionUtils.getRelativeEndPositionFromAreaSize(signedSize);
            this.offset = new BlockPos(Math.min(0, end.getX()), Math.min(0, end.getY()), Math.min(0, end.getZ()));
            this.worldOrigin = PositionUtils.getTransformedBlockPos(this.position, CanvasMesh.this.mirror, CanvasMesh.this.rotation).offset(CanvasMesh.this.origin);
        }
    }
}
