package seashyne.shynecore.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import seashyne.shynecore.model.BbCubeDefinition;
import seashyne.shynecore.model.BbFaceUvDefinition;
import seashyne.shynecore.model.BbMeshDefinition;
import seashyne.shynecore.model.BbMeshFaceDefinition;
import seashyne.shynecore.model.BbMeshUvDefinition;
import seashyne.shynecore.model.BbMeshVertexDefinition;

import java.util.List;

/**
 * Stateless Blockbench vertex emission shared by avatar and item renderers.
 * Transform selection, animation and visibility belong to the caller.
 * โมดูลสร้าง vertex ของ Blockbench ที่ไม่เก็บ state ใช้ร่วมกันระหว่าง avatar และ item;
 * ผู้เรียกเป็นผู้กำหนด transform, animation และ visibility.
 */
public final class BbModelGeometryRenderer {
    private BbModelGeometryRenderer() {}

    public static void emitCube(
        VertexConsumer vertices,
        Matrix4f transform,
        BbCubeDefinition cube,
        int targetTextureIndex,
        int textureCount,
        int textureWidth,
        int textureHeight,
        int lightCoords,
        int colorArgb
    ) {
        float inflate = cube.inflate();
        float x1 = Math.min(cube.fromX(), cube.toX()) - inflate;
        float y1 = Math.min(cube.fromY(), cube.toY()) - inflate;
        float z1 = Math.min(cube.fromZ(), cube.toZ()) - inflate;
        float x2 = Math.max(cube.fromX(), cube.toX()) + inflate;
        float y2 = Math.max(cube.fromY(), cube.toY()) + inflate;
        float z2 = Math.max(cube.fromZ(), cube.toZ()) + inflate;

        Matrix3f normalMatrix = new Matrix3f(transform).invert().transpose();
        Vector3f normal = new Vector3f();
        face(vertices, transform, normalMatrix, normal, cube.faces().get("north"), cube.textureIndex(), targetTextureIndex, textureCount, textureWidth, textureHeight, lightCoords, colorArgb, 0, 0, -1,
            x2, y1, z1, x2, y2, z1, x1, y2, z1, x1, y1, z1);
        face(vertices, transform, normalMatrix, normal, cube.faces().get("south"), cube.textureIndex(), targetTextureIndex, textureCount, textureWidth, textureHeight, lightCoords, colorArgb, 0, 0, 1,
            x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2);
        face(vertices, transform, normalMatrix, normal, cube.faces().get("west"), cube.textureIndex(), targetTextureIndex, textureCount, textureWidth, textureHeight, lightCoords, colorArgb, -1, 0, 0,
            x1, y1, z1, x1, y2, z1, x1, y2, z2, x1, y1, z2);
        face(vertices, transform, normalMatrix, normal, cube.faces().get("east"), cube.textureIndex(), targetTextureIndex, textureCount, textureWidth, textureHeight, lightCoords, colorArgb, 1, 0, 0,
            x2, y1, z2, x2, y2, z2, x2, y2, z1, x2, y1, z1);
        face(vertices, transform, normalMatrix, normal, cube.faces().get("up"), cube.textureIndex(), targetTextureIndex, textureCount, textureWidth, textureHeight, lightCoords, colorArgb, 0, 1, 0,
            x1, y2, z2, x1, y2, z1, x2, y2, z1, x2, y2, z2);
        face(vertices, transform, normalMatrix, normal, cube.faces().get("down"), cube.textureIndex(), targetTextureIndex, textureCount, textureWidth, textureHeight, lightCoords, colorArgb, 0, -1, 0,
            x1, y1, z1, x1, y1, z2, x2, y1, z2, x2, y1, z1);
    }

    /**
     * Emits polygons as degenerate-quad triangle fans for Minecraft entity passes.
     * ปล่อย polygon เป็น triangle fan แบบ degenerate-quad สำหรับ entity render pass ของ Minecraft.
     */
    public static void emitMesh(
        VertexConsumer vertices,
        Matrix4f transform,
        BbMeshDefinition mesh,
        int targetTextureIndex,
        int textureCount,
        int textureWidth,
        int textureHeight,
        int lightCoords,
        int colorArgb
    ) {
        Matrix3f normalMatrix = new Matrix3f(transform).invert().transpose();
        Vector3f normal = new Vector3f();
        for (BbMeshFaceDefinition face : mesh.faces()) {
            if (!face.enabled() || face.vertexIds().size() < 3) continue;
            int faceTextureIndex = face.textureIndex();
            if (faceTextureIndex < 0 || faceTextureIndex >= textureCount) faceTextureIndex = 0;
            if (faceTextureIndex != targetTextureIndex) continue;

            List<String> ids = face.vertexIds();
            BbMeshVertexDefinition first = mesh.vertex(ids.get(0));
            if (first == null) continue;
            for (int i = ids.size() - 1; i >= 2; i--) {
                BbMeshVertexDefinition second = mesh.vertex(ids.get(i));
                BbMeshVertexDefinition third = mesh.vertex(ids.get(i - 1));
                if (second == null || third == null) continue;
                meshTriangle(vertices, transform, normalMatrix, normal, first, second, third,
                    face.uv(first.id()), face.uv(second.id()), face.uv(third.id()),
                    textureWidth, textureHeight, lightCoords, colorArgb);
            }
        }
    }

    private static void meshTriangle(
        VertexConsumer vertices, Matrix4f transform, Matrix3f normalMatrix, Vector3f normal,
        BbMeshVertexDefinition a, BbMeshVertexDefinition b, BbMeshVertexDefinition c,
        BbMeshUvDefinition uvA, BbMeshUvDefinition uvB, BbMeshUvDefinition uvC,
        int textureWidth, int textureHeight, int lightCoords, int colorArgb
    ) {
        float abX = b.x() - a.x(), abY = b.y() - a.y(), abZ = b.z() - a.z();
        float acX = c.x() - a.x(), acY = c.y() - a.y(), acZ = c.z() - a.z();
        normal.set(abY * acZ - abZ * acY, abZ * acX - abX * acZ, abX * acY - abY * acX);
        if (normal.lengthSquared() <= 1.0e-12f) return;
        normalMatrix.transform(normal).normalize();
        meshVertex(vertices, transform, normal, a, uvA, textureWidth, textureHeight, lightCoords, colorArgb);
        meshVertex(vertices, transform, normal, b, uvB, textureWidth, textureHeight, lightCoords, colorArgb);
        meshVertex(vertices, transform, normal, c, uvC, textureWidth, textureHeight, lightCoords, colorArgb);
        meshVertex(vertices, transform, normal, c, uvC, textureWidth, textureHeight, lightCoords, colorArgb);
    }

    private static void meshVertex(
        VertexConsumer vertices, Matrix4f transform, Vector3f normal, BbMeshVertexDefinition vertex,
        BbMeshUvDefinition uv, int textureWidth, int textureHeight, int lightCoords, int colorArgb
    ) {
        vertices.addVertex(transform, vertex.x(), vertex.y(), vertex.z())
            .setColor(colorArgb)
            .setUv(uv.u() / Math.max(1, textureWidth), uv.v() / Math.max(1, textureHeight))
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(lightCoords)
            .setNormal(normal.x, normal.y, normal.z);
    }

    private static void face(
        VertexConsumer vertices, Matrix4f transform, Matrix3f normalMatrix, Vector3f normal,
        BbFaceUvDefinition uv, int fallbackTextureIndex, int targetTextureIndex,
        int textureCount, int textureWidth, int textureHeight, int lightCoords, int colorArgb,
        float nx, float ny, float nz,
        float x0, float y0, float z0, float x1, float y1, float z1,
        float x2, float y2, float z2, float x3, float y3, float z3
    ) {
        if (uv == null || !uv.enabled()) return;
        int faceTextureIndex = uv.textureIndex() >= 0 ? uv.textureIndex() : fallbackTextureIndex;
        if (faceTextureIndex < 0 || faceTextureIndex >= textureCount) faceTextureIndex = 0;
        if (faceTextureIndex != targetTextureIndex) return;
        float u1 = uv.u1() / Math.max(1, textureWidth);
        float v1 = uv.v1() / Math.max(1, textureHeight);
        float u2 = uv.u2() / Math.max(1, textureWidth);
        float v2 = uv.v2() / Math.max(1, textureHeight);
        int shift = Math.floorMod(uv.rotation() / 90, 4);
        normal.set(nx, ny, nz);
        normalMatrix.transform(normal).normalize();
        for (int i = 0; i < 4; i++) {
            int textureCorner = (i + shift) & 3;
            float textureU = textureCorner < 2 ? u1 : u2;
            float textureV = textureCorner == 0 || textureCorner == 3 ? v2 : v1;
            float x = switch (i) { case 0 -> x0; case 1 -> x1; case 2 -> x2; default -> x3; };
            float y = switch (i) { case 0 -> y0; case 1 -> y1; case 2 -> y2; default -> y3; };
            float z = switch (i) { case 0 -> z0; case 1 -> z1; case 2 -> z2; default -> z3; };
            vertices.addVertex(transform, x, y, z)
                .setColor(colorArgb)
                .setUv(textureU, textureV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(normal.x, normal.y, normal.z);
        }
    }
}
