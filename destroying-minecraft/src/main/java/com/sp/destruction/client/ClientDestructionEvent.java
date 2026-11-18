package com.sp.destruction.client;

import com.sp.destruction.DestructionEvent;
import com.sp.destruction.DestructionType;

import java.util.Vector;

public abstract class ClientDestructionEvent extends DestructionEvent {
    private static final Vector<DestructionEvent> clientInstances = new Vector<>();
    private final Object postShader; // PostShader type — Object to avoid hard Veil dependency

    public ClientDestructionEvent(DestructionType destructionType, Object postShader, int duration) {
        super(destructionType, duration, true);
        clientInstances.add(this);
        this.postShader = postShader;
        if (postShader != null) {
            try { postShader.getClass().getMethod("setUniformCallback", DestructionUniformCallback.class).invoke(postShader, (DestructionUniformCallback) this::setUniforms); } catch (Exception ignored) {}
        }
    }

    @Override protected void skipKeyframe() {}

    public Object getPostShader() { return this.postShader; }

    public abstract void setUniforms(Object shaderProgram, float tickDelta);

    public static <T extends ClientDestructionEvent> T register(T event) { clientInstances.add(event); return event; }

    public static synchronized Vector<DestructionEvent> getAllClientInstances() { return (Vector<DestructionEvent>) clientInstances.clone(); }

    @FunctionalInterface
    public interface DestructionUniformCallback { void setUniforms(Object shaderProgram, float tickDelta); }
}
