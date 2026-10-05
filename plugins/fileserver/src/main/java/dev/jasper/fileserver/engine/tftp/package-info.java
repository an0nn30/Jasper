/**
 * The TFTP engine (RFC 1350, with the blksize and tsize options of RFC 2347–2349), written on the JDK's
 * DatagramSocket: packets, netascii, the server socket and one ephemeral socket per transfer. It may depend on
 * {@code engine}, {@code share}, {@code definition} and the JDK.
 */
package dev.jasper.fileserver.engine.tftp;
