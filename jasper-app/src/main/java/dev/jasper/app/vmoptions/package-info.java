/**
 * Custom VM options (spec section 6.4): the launcher's reading of {@code jasper.vmoptions}, mirrored for the
 * application; editing the file; what this JVM was launched with; the bundled launcher configuration; and the check
 * that runs the bundled {@code java} with the options. JDK-only: no Swing or AWT ({@code com.sun.management} for the
 * physical memory); {@code verifyApplicationArchitecture} enforces it. No owner: values and pure functions, plus file
 * I/O and a check that run on the caller's thread.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.vmoptions;
