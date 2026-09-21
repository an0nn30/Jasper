/**
 * Unsupported JediTerm adapter and locked buffer queries. All live buffer reads hold the buffer lock; rendering and regex work use detached data outside it.
 */
package dev.jasper.terminal.internal.emulation;
