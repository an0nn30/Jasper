package dev.jasper.app.layout;

/** A node of a layout's tree: a split of nodes, or a group of tabs at a leaf. */
public sealed interface Node permits Split, Group { }
