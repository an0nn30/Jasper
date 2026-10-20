/**
 * Custom VM options (spec section 6.4): the launcher's reading of {@code jasper.vmoptions}, mirrored for the
 * application; editing the file; what this JVM was launched with; the bundled launcher configuration; the check
 * that runs the bundled {@code java} with the options; the record of a start-up cache that made Java fail at launch
 * (start-up recovery spec 6.2); and the memory readings and low-memory watch of spec sections 5.3 and 5.4. JDK-only: no
 * Swing or AWT ({@code com.sun.management} for the physical memory); {@code verifyApplicationArchitecture} enforces it.
 * Values and pure functions, plus file I/O and a check that run on the caller's thread; the one resource is
 * {@code HeapThreshold}, which its creator closes and whose callback runs on the JMX notification thread.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.vmoptions;
