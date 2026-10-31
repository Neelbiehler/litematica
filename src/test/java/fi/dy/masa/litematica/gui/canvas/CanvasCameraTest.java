package fi.dy.masa.litematica.gui.canvas;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanvasCameraTest
{
    @Test
    void depthOrdersFacesAlongTheViewingDirection()
    {
        CanvasCamera camera = new CanvasCamera();
        double[] near = camera.project(Math.sin(camera.yaw) * Math.cos(camera.pitch), Math.sin(camera.pitch), Math.cos(camera.yaw) * Math.cos(camera.pitch));
        assertEquals(0, near[0], 0.00001);
        assertEquals(0, near[1], 0.00001);
        assertEquals(1, near[2], 0.00001);
    }

    @Test
    void draggingFollowsTheGroundPlaneAtEveryCameraAngle()
    {
        CanvasCamera camera = new CanvasCamera();
        camera.x = 29_000_000; camera.y = 80; camera.z = -29_000_000;
        for (double yaw : new double[] {0, Math.PI / 4, Math.PI / 2, Math.PI, -Math.PI / 3})
        {
            camera.yaw = yaw;
            for (double pitch : new double[] {0.2, 0.7, 1.4})
            {
                camera.pitch = pitch;
                double[] delta = camera.groundDelta(38, -17);
                double[] origin = camera.project(camera.x, camera.y, camera.z);
                double[] moved = camera.project(camera.x + delta[0], camera.y, camera.z + delta[1]);
                assertEquals(38, moved[0] - origin[0], 0.00001);
                assertEquals(-17, moved[1] - origin[1], 0.00001);
            }
        }
    }

    @Test
    void zoomKeepsThePointUnderTheCursorFixed()
    {
        CanvasCamera camera = new CanvasCamera();
        camera.screenX = 200; camera.screenY = 100;
        double[] before = camera.groundDelta(30, 20);
        camera.zoomAt(3, 230, 120);
        double[] projected = camera.project(before[0], 0, before[1]);
        assertEquals(230, projected[0], 0.00001);
        assertEquals(120, projected[1], 0.00001);
    }

    @Test
    void orbitCannotReachASingularDragPlane()
    {
        CanvasCamera camera = new CanvasCamera();
        camera.orbit(100000, -100000);
        assertTrue(camera.pitch > 0);
        double[] delta = camera.groundDelta(1, 1);
        assertTrue(Double.isFinite(delta[0]) && Double.isFinite(delta[1]));
        camera.orbit(0, 100000);
        assertTrue(camera.pitch < Math.PI / 2);
    }
}
