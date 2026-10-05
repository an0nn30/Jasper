/**
 * The SFTP engine on Apache MINA sshd: the host key, a jail that routes every file operation through the share,
 * logins through the login throttle, and activity reporting. The only package that names sshd types. It may depend
 * on {@code engine}, {@code share}, {@code auth}, {@code definition} and the JDK, and on BouncyCastle only to give
 * sshd this plugin's own provider.
 */
package dev.jasper.fileserver.engine.sftp;
