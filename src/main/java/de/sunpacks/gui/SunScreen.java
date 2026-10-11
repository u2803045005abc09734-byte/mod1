package de.sunpacks.gui;

import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_4185;
import net.minecraft.class_437;

/** Small standalone screen base for the in-game Modrinth pack browser. */
public abstract class SunScreen extends class_437 {
    protected final class_437 parent;

    protected SunScreen(String title, class_437 parent) {
        super(class_2561.method_43470(title));
        this.parent = parent;
    }

    protected static class_310 mc() {
        return class_310.method_1551();
    }

    protected class_4185 addButton(String label, int x, int y, int w, Runnable action) {
        return method_37063(class_4185.method_46430(class_2561.method_43470(label), b -> action.run())
            .method_46434(x, y, w, 20).method_46431());
    }

    @Override
    public void method_25394(net.minecraft.class_332 g, int mouseX, int mouseY, float delta) {
        super.method_25394(g, mouseX, mouseY, delta);
        int center = this.field_22789 / 2;
        int left = Math.max(8, center - 180);
        int right = Math.min(this.field_22789 - 8, center + 180);
        int bottom = Math.max(36, this.field_22790 - 20);
        g.method_25294(left, 2, right, 30, 0xDD151B27);
        g.method_25294(left, 2, left + 2, 30, 0xFFFFC107);
        g.method_25294(left, 30, right, 31, 0xFFFFC107);
        g.method_25294(left, 31, left + 1, bottom, 0x663F4B60);
        g.method_25294(right - 1, 31, right, bottom, 0x663F4B60);
        g.method_25294(left, bottom, right, bottom + 1, 0x663F4B60);
        g.method_27534(this.field_22793, this.field_22785, center, 10, 0xFFFFC107);
    }

    @Override
    public void method_25419() {
        mc().method_1507(parent);
    }
}
