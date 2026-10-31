package fi.dy.masa.litematica.gui.canvas;

/** Orthographic orbit camera; all coordinates remain doubles until GUI submission. */
public final class CanvasCamera
{
    public double x, y, z;
    public double zoom = 4;
    public double yaw = Math.PI / 4;
    public double pitch = Math.PI / 5;
    public double screenX, screenY;

    public double[] project(double x, double y, double z)
    {
        double dx = x - this.x, dy = y - this.y, dz = z - this.z;
        double right = Math.cos(this.yaw) * dx - Math.sin(this.yaw) * dz;
        double forward = Math.sin(this.yaw) * dx + Math.cos(this.yaw) * dz;
        return new double[] {this.screenX + this.zoom * right,
                this.screenY + this.zoom * (Math.sin(this.pitch) * forward - Math.cos(this.pitch) * dy),
                Math.cos(this.pitch) * forward + Math.sin(this.pitch) * dy};
    }

    /** Converts a screen displacement to a displacement on a horizontal block plane. */
    public double[] groundDelta(double dx, double dy)
    {
        double right = dx / this.zoom;
        double forward = dy / (this.zoom * Math.sin(this.pitch));
        return new double[] {Math.cos(this.yaw) * right + Math.sin(this.yaw) * forward,
                -Math.sin(this.yaw) * right + Math.cos(this.yaw) * forward};
    }

    public void orbit(double dx, double dy)
    {
        this.yaw = Math.IEEEremainder(this.yaw + dx * 0.01, Math.PI * 2);
        this.pitch = Math.clamp(this.pitch + dy * 0.01, Math.toRadians(10), Math.toRadians(85));
    }

    public void zoomAt(double amount, double x, double y)
    {
        double[] before = this.groundDelta(x - this.screenX, y - this.screenY);
        this.zoom = Math.clamp(this.zoom * Math.pow(1.2, amount), 0.02, 48);
        double[] after = this.groundDelta(x - this.screenX, y - this.screenY);
        this.x += before[0] - after[0]; this.z += before[1] - after[1];
    }
}
