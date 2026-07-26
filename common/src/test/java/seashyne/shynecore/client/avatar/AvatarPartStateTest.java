package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AvatarPartStateTest {
    @Test
    void sanitizesNonFiniteTransformsBeforeRenderOrNetworkUse() {
        AvatarPartState state = new AvatarPartState();

        state.setPosition(Float.NaN, Float.POSITIVE_INFINITY, 3f);
        state.setRotation(Float.NEGATIVE_INFINITY, 4f, Float.NaN);
        state.setScale(Float.NaN, 2f, Float.POSITIVE_INFINITY);
        state.setAdditiveRotation(Float.NaN, Float.NEGATIVE_INFINITY, 5f);

        assertEquals(0f, state.posX());
        assertEquals(0f, state.posY());
        assertEquals(0f, state.rotX());
        assertEquals(0f, state.rotZ());
        assertEquals(0f, state.scaleX());
        assertEquals(0f, state.scaleZ());
        assertEquals(0f, state.additiveRotX());
        assertEquals(0f, state.additiveRotY());
        assertTrue(Float.isFinite(state.additiveRotZ()));
    }

    @Test
    void interpolatesRemoteRotationAcrossTheShortestArc() {
        AvatarPartState previous = new AvatarPartState();
        previous.setRotation(0f, 170f, 0f);
        previous.setAdditiveRotation(0f, 170f, 0f);
        AvatarPartState next = new AvatarPartState();
        next.setRotation(0f, -170f, 0f);
        next.setAdditiveRotation(0f, -170f, 0f);

        AvatarPartState halfway = AvatarPartState.interpolate(previous, next, 0.5f);

        assertEquals(180f, halfway.rotY(), 0.0001f);
        assertEquals(180f, halfway.additiveRotY(), 0.0001f);
    }
}
