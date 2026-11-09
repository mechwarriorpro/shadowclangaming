package com.shadowclangaming.block;

/** Which of the two chemistry machines a {@link ProcessorBlock} is. */
public enum ProcessorKind {
    /** Item in, element vials out. */
    DECOMPOSER,
    /** Element vials in, useful items out. */
    SYNTHESIZER
}
