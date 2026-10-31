package fi.dy.masa.litematica.gui.canvas;

import org.junit.jupiter.api.Test;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import static org.junit.jupiter.api.Assertions.*;

class CanvasMeshTest
{
    @Test
    void rotatingAndMirroringKeepsVerticesInsideTheirBlock()
    {
        for (Mirror mirror : Mirror.values()) for (Rotation rotation : Rotation.values())
        for (Mirror subMirror : Mirror.values()) for (Rotation subRotation : Rotation.values())
        {
            assertEquals(new Vec3(0.5, 0.5, 0.5), CanvasMesh.transformVertex(new Vec3(0.5, 0.5, 0.5), mirror, rotation, subMirror, subRotation));
            for (int x = 0; x <= 1; ++x) for (int z = 0; z <= 1; ++z)
            {
                Vec3 transformed = CanvasMesh.transformVertex(new Vec3(x, 0.25, z), mirror, rotation, subMirror, subRotation);
                assertTrue(transformed.x >= 0 && transformed.x <= 1);
                assertEquals(0.25, transformed.y);
                assertTrue(transformed.z >= 0 && transformed.z <= 1);
            }
        }
    }
}
