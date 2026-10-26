package com.shadowclangaming.computer.api;

/**
 * One thing a computer can talk to: a screen, a disk, a redstone port, a cable, our own hardware.
 *
 * The shape is deliberately the same one OpenComputers' scripts expect ({@code address}, {@code type},
 * {@code invoke}), because MineOS is written against it and because it keeps the API small: a component is
 * a bag of methods behind a string name, and the machine never needs to know what it is talking to.
 */
public interface Component {

    /** Stable address used by {@code component.get(...)}. Unique within a machine's network. */
    String address();

    /** Component type, e.g. {@code "screen"}, {@code "gpu"}, {@code "filesystem"}, {@code "redstone"}. */
    String type();

    /**
     * Call a method.
     *
     * @return the results, an empty array for a void method
     * @throws IllegalArgumentException when the method does not exist, so the Lua side can raise a real
     *         error instead of silently returning nil
     */
    Object[] invoke(String method, Object[] args);

    /** Method names this component answers to, for {@code component.methods(address)}. */
    default String[] methods() {
        return new String[0];
    }
}
