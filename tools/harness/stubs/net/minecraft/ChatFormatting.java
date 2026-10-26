package net.minecraft;

/**
 * Stand-in for Minecraft's ChatFormatting, so the pure radar maths can be compiled and exercised
 * outside the game. Nothing here is shipped: the harness script copies the real logic classes next to
 * this stub in a temp folder and throws it away afterwards.
 */
public enum ChatFormatting {
    RED, YELLOW, GOLD, LIGHT_PURPLE, GRAY, GREEN, DARK_RED, AQUA
}
