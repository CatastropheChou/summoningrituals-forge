package com.almostreliable.summoningrituals.util;

import com.mojang.math.Vector3f;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

public final class MathUtils {

    private MathUtils() {}

    public static Vec3 vectorFromPos(Vec3i pos) {
        return new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }

    public static Vec3 shiftToCenter(Vec3 pos) {
        return new Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
    }

    public static Vec3 shiftToCenter(Vec3i pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static int flipCircle(Number degree) {
        return 360 - degree.intValue();
    }

    public static float singleRotation(Number degree) {
        return Math.abs(degree.floatValue()) % 360f;
    }

    public static Vector3f[][] getHorizontalVectors(Vector3f... north) {
        Vector3f[] south = Arrays.stream(north).map(MathUtils::getOppositeVector).toArray(Vector3f[]::new);
        Vector3f[] east = Arrays.stream(north).map(MathUtils::getNeighborVector).toArray(Vector3f[]::new);
        Vector3f[] west = Arrays.stream(east).map(MathUtils::getOppositeVector).toArray(Vector3f[]::new);
        return new Vector3f[][]{north, south, west, east};
    }

    public static float modifier(float current, float max, float fallback) {
        return max == 0 ? fallback : current / max;
    }

    private static Vector3f getOppositeVector(Vector3f vector) {
        return new Vector3f(16 - vector.x(), vector.y(), 16 - vector.z());
    }

    private static Vector3f getNeighborVector(Vector3f vector1) {
        var vector2 = getOppositeVector(vector1);
        return new Vector3f(vector2.z(), vector1.y(), vector1.x());
    }
}
