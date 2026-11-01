# Copyright 2026 Jasper contributors
# Distributed under the terms of the GNU General Public License v2

EAPI=8

PYTHON_COMPAT=( python3_{11..14} )

inherit desktop multiprocessing python-any-r1 toolchain-funcs xdg

DESCRIPTION="Jasper DevOps workstation with a private JetBrains Runtime"
HOMEPAGE="https://github.com/an0nn30/Jasper"
JASPER_COMMIT="be557f600d984232cc9060c8488b563286b3ee28"
JASPER_GRADLE="9.7.0"
JASPER_JBR="jbrsdk_jcef-25.0.4.1-linux-aarch64-b635.75"
SRC_URI="
	https://github.com/an0nn30/Jasper/archive/${JASPER_COMMIT}.tar.gz -> ${PN}-${JASPER_COMMIT}.tar.gz
	https://services.gradle.org/distributions/gradle-${JASPER_GRADLE}-bin.zip
	https://cache-redirector.jetbrains.com/intellij-jbr/${JASPER_JBR}.tar.gz
	https://repo.maven.apache.org/maven2/com/fifesoft/rsyntaxtextarea/3.6.0/rsyntaxtextarea-3.6.0-sources.jar -> jasper-maven-a6701e400e2c8cde64ba8bc06b6cd2b58fbce155fa80312e01a11bbc91fd2cd7-rsyntaxtextarea-3.6.0-sources.jar
	https://repo.maven.apache.org/maven2/com/fifesoft/rsyntaxtextarea/3.6.0/rsyntaxtextarea-3.6.0.jar -> jasper-maven-701ff74da1c4914e66731d4791081d758012571b52846760157792b7a2d160cd-rsyntaxtextarea-3.6.0.jar
	https://repo.maven.apache.org/maven2/com/fifesoft/rsyntaxtextarea/3.6.0/rsyntaxtextarea-3.6.0.module -> jasper-maven-7ad41691ebdf65e64397f71be839803375765874bcf5cd99eb380b81f6a4c3a4-rsyntaxtextarea-3.6.0.module
	https://repo.maven.apache.org/maven2/com/fifesoft/rsyntaxtextarea/3.6.0/rsyntaxtextarea-3.6.0.pom -> jasper-maven-76330f7909c918e4c3fea5934a88f3869dbad7aaae5793479694e0c52ae090a0-rsyntaxtextarea-3.6.0.pom
	https://repo.maven.apache.org/maven2/com/formdev/flatlaf-extras/3.7/flatlaf-extras-3.7-sources.jar -> jasper-maven-1b2b8f533a556addec50338db64e64d28cc42726d8d9cccf5921accd718303e5-flatlaf-extras-3.7-sources.jar
	https://repo.maven.apache.org/maven2/com/formdev/flatlaf-extras/3.7/flatlaf-extras-3.7.jar -> jasper-maven-ffe0bad74ed5efa493ec19a83fce41a6d41b058b6c6e22e642087ea8dc42df94-flatlaf-extras-3.7.jar
	https://repo.maven.apache.org/maven2/com/formdev/flatlaf-extras/3.7/flatlaf-extras-3.7.module -> jasper-maven-6a9b72d25fda2b9d4cfdab0e2ed753e03450f2679b6d92ab9ca6fd25d92f8855-flatlaf-extras-3.7.module
	https://repo.maven.apache.org/maven2/com/formdev/flatlaf-extras/3.7/flatlaf-extras-3.7.pom -> jasper-maven-7647ccf84e76106da95957da59484dfeea309bc4e920091a9bc04218c15ecd45-flatlaf-extras-3.7.pom
	https://repo.maven.apache.org/maven2/com/formdev/flatlaf/3.7/flatlaf-3.7-sources.jar -> jasper-maven-c697a8d59aec0b3567f56b63d3cde98a1a8124f1b22b18e437288a804c96efbb-flatlaf-3.7-sources.jar
	https://repo.maven.apache.org/maven2/com/formdev/flatlaf/3.7/flatlaf-3.7.jar -> jasper-maven-7184e32c22484888e5a13baadb94a1ac7140d3b48d68f1f34740deb72e3e7f77-flatlaf-3.7.jar
	https://repo.maven.apache.org/maven2/com/formdev/flatlaf/3.7/flatlaf-3.7.module -> jasper-maven-ae26246e292199e24402febc5dee5df65aaa73fd255ef935e4ee62d11820a388-flatlaf-3.7.module
	https://repo.maven.apache.org/maven2/com/formdev/flatlaf/3.7/flatlaf-3.7.pom -> jasper-maven-4611b6f4b3a6a678cc67c4149151163fed94b3a29b56e7e3211388c4398fa78b-flatlaf-3.7.pom
	https://repo.maven.apache.org/maven2/com/github/hypfvieh/dbus-java-core/5.2.0/dbus-java-core-5.2.0-sources.jar -> jasper-maven-d36166a2d420771890aa64e145f736ec5142c3c363b2c7ddd1bca16fdaddaf06-dbus-java-core-5.2.0-sources.jar
	https://repo.maven.apache.org/maven2/com/github/hypfvieh/dbus-java-core/5.2.0/dbus-java-core-5.2.0.jar -> jasper-maven-e998d79023ef2d12dc26c71094b5c5afcdff96d431bee5b715206cc0b773cd46-dbus-java-core-5.2.0.jar
	https://repo.maven.apache.org/maven2/com/github/hypfvieh/dbus-java-core/5.2.0/dbus-java-core-5.2.0.pom -> jasper-maven-24e2f92039b680cb528ac2c415c9b4f7cd32f34faad2719bf964c3834721ceaa-dbus-java-core-5.2.0.pom
	https://repo.maven.apache.org/maven2/com/github/hypfvieh/dbus-java-parent/5.2.0/dbus-java-parent-5.2.0.pom -> jasper-maven-76c34568453e83b30dfd843e667e9dd91e6cc0725f6da4d5a49d55911d4ed7e2-dbus-java-parent-5.2.0.pom
	https://repo.maven.apache.org/maven2/com/github/hypfvieh/dbus-java-transport-junixsocket/5.2.0/dbus-java-transport-junixsocket-5.2.0-sources.jar -> jasper-maven-ec873a96d5de24d3fe5a324017cf96351143ed5ae3945cc2a9abf7c3800baf38-dbus-java-transport-junixsocket-5.2.0-sources.jar
	https://repo.maven.apache.org/maven2/com/github/hypfvieh/dbus-java-transport-junixsocket/5.2.0/dbus-java-transport-junixsocket-5.2.0.jar -> jasper-maven-c6f09e16e36bb08a51594e382f331c21872fbcc58cbb16558a095f7a7e0cf69f-dbus-java-transport-junixsocket-5.2.0.jar
	https://repo.maven.apache.org/maven2/com/github/hypfvieh/dbus-java-transport-junixsocket/5.2.0/dbus-java-transport-junixsocket-5.2.0.pom -> jasper-maven-c3ebfea4aa26ebccba542e3bc047f512809248c253d4b658ddf5052ff7c851d1-dbus-java-transport-junixsocket-5.2.0.pom
	https://repo.maven.apache.org/maven2/com/github/weisj/jsvg/2.0.0/jsvg-2.0.0-sources.jar -> jasper-maven-374459a9eb2e84f905fe758a68e73fd3dc10f5d191f82d9ff904b192cdaabe3b-jsvg-2.0.0-sources.jar
	https://repo.maven.apache.org/maven2/com/github/weisj/jsvg/2.0.0/jsvg-2.0.0.jar -> jasper-maven-057f4ec698bdae20c0b89e3cdc61549185482da2866336b9c7cecc0a2ae7e4be-jsvg-2.0.0.jar
	https://repo.maven.apache.org/maven2/com/github/weisj/jsvg/2.0.0/jsvg-2.0.0.pom -> jasper-maven-70a5f4d8afc8ea47d6b19f1a0f06fb7ebf33c1ac669ccbc36039c26bb1480704-jsvg-2.0.0.pom
	https://repo.maven.apache.org/maven2/com/kohlschutter/junixsocket/junixsocket-common/2.10.1/junixsocket-common-2.10.1-sources.jar -> jasper-maven-810695c42b68023df1101252e6b884d4535aa4b8ddc9a8bf2f24a1847da1c821-junixsocket-common-2.10.1-sources.jar
	https://repo.maven.apache.org/maven2/com/kohlschutter/junixsocket/junixsocket-common/2.10.1/junixsocket-common-2.10.1.jar -> jasper-maven-23849a3d03254deac7212d0faa7d7802ff1fffab1362fb629d8f180dbc508748-junixsocket-common-2.10.1.jar
	https://repo.maven.apache.org/maven2/com/kohlschutter/junixsocket/junixsocket-common/2.10.1/junixsocket-common-2.10.1.pom -> jasper-maven-8a21f66bbbd2e670acb44cb401fdb58e7718b0a19054ae1072701d2395579108-junixsocket-common-2.10.1.pom
	https://repo.maven.apache.org/maven2/com/kohlschutter/junixsocket/junixsocket-native-common/2.10.1/junixsocket-native-common-2.10.1-sources.jar -> jasper-maven-75d67b2b15a9e2fc2b526ac88f2f89864d9fde8c3fbc5f4764ceebbcbc5be199-junixsocket-native-common-2.10.1-sources.jar
	https://repo.maven.apache.org/maven2/com/kohlschutter/junixsocket/junixsocket-native-common/2.10.1/junixsocket-native-common-2.10.1.jar -> jasper-maven-20b6c6bb54a838fbb179e728202e1cab353dd57f75840b897266dfc6c9908fb4-junixsocket-native-common-2.10.1.jar
	https://repo.maven.apache.org/maven2/com/kohlschutter/junixsocket/junixsocket-native-common/2.10.1/junixsocket-native-common-2.10.1.pom -> jasper-maven-79ef0b14871cab6300ea4517e49a31f6d41e340d1ac34a9c29f8ef69f7a6d524-junixsocket-native-common-2.10.1.pom
	https://repo.maven.apache.org/maven2/com/kohlschutter/junixsocket/junixsocket/2.10.1/junixsocket-2.10.1.pom -> jasper-maven-7eda5253ba6bced858b9fa41303ccddfe16be0a6c25157f12d25d7b63e3a965a-junixsocket-2.10.1.pom
	https://repo.maven.apache.org/maven2/com/kohlschutter/kohlschutter-parent-multirelease/1.7.4/kohlschutter-parent-multirelease-1.7.4.pom -> jasper-maven-f8d52471a4449beb7b5ea055b9ca92bedc7af45f8fb830c855563c8b6c1decad-kohlschutter-parent-multirelease-1.7.4.pom
	https://repo.maven.apache.org/maven2/com/kohlschutter/kohlschutter-parent/1.7.4/kohlschutter-parent-1.7.4.pom -> jasper-maven-92b39c721fca1847143645217e5ff87abbb31a2fe5150a18fd421dbf5938087e-kohlschutter-parent-1.7.4.pom
	https://repo.maven.apache.org/maven2/commons-io/commons-io/2.21.0/commons-io-2.21.0-sources.jar -> jasper-maven-fafe9d708809e8e636568e3748297526e4c88daf6738c809767c3adde1e952cd-commons-io-2.21.0-sources.jar
	https://repo.maven.apache.org/maven2/commons-io/commons-io/2.21.0/commons-io-2.21.0.jar -> jasper-maven-a342913dbd8942b852c038fa992c500c61a820849e18a8134a483be94f2c3234-commons-io-2.21.0.jar
	https://repo.maven.apache.org/maven2/commons-io/commons-io/2.21.0/commons-io-2.21.0.pom -> jasper-maven-92a7b1350356600cb75bbd28bb3d1be4ba51b0396ce007fedf50d80239162f46-commons-io-2.21.0.pom
	https://repo.maven.apache.org/maven2/commons-net/commons-net/3.13.0/commons-net-3.13.0-sources.jar -> jasper-maven-9a62f965ac4c673eb1ba0d63f291fe9296ae31354121ff6f4f919f16dde5d7fc-commons-net-3.13.0-sources.jar
	https://repo.maven.apache.org/maven2/commons-net/commons-net/3.13.0/commons-net-3.13.0.jar -> jasper-maven-11e62ba5c0f32060a1719873a4a71201ddd811d87a8c50e2bff3d2f43c6ac48c-commons-net-3.13.0.jar
	https://repo.maven.apache.org/maven2/commons-net/commons-net/3.13.0/commons-net-3.13.0.pom -> jasper-maven-05d5f2c84fab78f69c79d2bd796d1a17908c5a885f1b2860df5e4a6492c94661-commons-net-3.13.0.pom
	https://repo.maven.apache.org/maven2/net/bytebuddy/byte-buddy-parent/1.18.3/byte-buddy-parent-1.18.3.pom -> jasper-maven-5650ce7aeba0883c0ad4fe97073f7e15db51c86ca1e6ac5358f993c2f04a9f30-byte-buddy-parent-1.18.3.pom
	https://repo.maven.apache.org/maven2/net/bytebuddy/byte-buddy/1.18.3/byte-buddy-1.18.3-sources.jar -> jasper-maven-f4c92000378f4b1ac3140b8f0da518c358c946329a55aa0f2faf63f95ce1207a-byte-buddy-1.18.3-sources.jar
	https://repo.maven.apache.org/maven2/net/bytebuddy/byte-buddy/1.18.3/byte-buddy-1.18.3.jar -> jasper-maven-cffdf0c45f678afa5381a232c9b6e6aaf3c9ae8fd2f5f6d113dda0895c5c0fe9-byte-buddy-1.18.3.jar
	https://repo.maven.apache.org/maven2/net/bytebuddy/byte-buddy/1.18.3/byte-buddy-1.18.3.pom -> jasper-maven-ed7bcab000bbebd08a760ca2602a3a6f118186cbf4156dd76b9965daadddd196-byte-buddy-1.18.3.pom
	https://repo.maven.apache.org/maven2/net/java/dev/jna/jna-platform/5.14.0/jna-platform-5.14.0-sources.jar -> jasper-maven-31eea05c7246af0436942018e6fef2cdcba0ebfe69dc0ded59f11438c27a3770-jna-platform-5.14.0-sources.jar
	https://repo.maven.apache.org/maven2/net/java/dev/jna/jna-platform/5.14.0/jna-platform-5.14.0.jar -> jasper-maven-8e039c7dadc270f067258413d62dd493fd158eb10a2a0edf89fdc533dd85c146-jna-platform-5.14.0.jar
	https://repo.maven.apache.org/maven2/net/java/dev/jna/jna-platform/5.14.0/jna-platform-5.14.0.pom -> jasper-maven-7c8e5002bbd2a828107d077c2c5990a076d5504ab9a449a00ba4528e35e14713-jna-platform-5.14.0.pom
	https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0-sources.jar -> jasper-maven-cab4af28bba3b06b1453495111749d14bd5e1bf873835e874c407686aaa937f7-jna-5.14.0-sources.jar
	https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar -> jasper-maven-d4988d43bf90ef4c7484db16c4150a873ab815e47c74aaf057d6ca76cd0a087c-jna-5.14.0.jar
	https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.pom -> jasper-maven-586e40f80ff671e3b0bb1efa46cafd3060ca1a17f82ffc4d9e767c8ff0346d4d-jna-5.14.0.pom
	https://repo.maven.apache.org/maven2/org/antlr/antlr4-master/4.11.1/antlr4-master-4.11.1.pom -> jasper-maven-d0187bf18346750d5aa8656928a4ab50c556427d177708277195cd412cec35b7-antlr4-master-4.11.1.pom
	https://repo.maven.apache.org/maven2/org/antlr/antlr4-runtime/4.11.1/antlr4-runtime-4.11.1-sources.jar -> jasper-maven-637c5e525c172d0e8e9a7fec80e4ddd00f76893ab0733428e2e8c53c02cca424-antlr4-runtime-4.11.1-sources.jar
	https://repo.maven.apache.org/maven2/org/antlr/antlr4-runtime/4.11.1/antlr4-runtime-4.11.1.jar -> jasper-maven-9724a9e05c4180c8a47eb449bcb0c4e230f09cff6d3755ba75f4eb27036fdbff-antlr4-runtime-4.11.1.jar
	https://repo.maven.apache.org/maven2/org/antlr/antlr4-runtime/4.11.1/antlr4-runtime-4.11.1.pom -> jasper-maven-f4822186a8964f04f003efd43f98d0659b1bedd94088dede7b0360651c9b93eb-antlr4-runtime-4.11.1.pom
	https://repo.maven.apache.org/maven2/org/apache/apache/30/apache-30.pom -> jasper-maven-ec9322f347382a2dff4882930dc90bd968c939f54697bc0cce9db4f07e372924-apache-30.pom
	https://repo.maven.apache.org/maven2/org/apache/apache/33/apache-33.pom -> jasper-maven-3b499a6533d1106253d3104fd840407860534cb299e9d15367961f8a4119dace-apache-33.pom
	https://repo.maven.apache.org/maven2/org/apache/apache/35/apache-35.pom -> jasper-maven-f9509afb0bee7e9d8f8daaa07611bace0a171ca8e2417f443b98b2a6f12476cb-apache-35.pom
	https://repo.maven.apache.org/maven2/org/apache/apache/37/apache-37.pom -> jasper-maven-63b2f11d4acfb108aa8df3228032fa04824a3b18a6fac41bdbaf0408c7ef4441-apache-37.pom
	https://repo.maven.apache.org/maven2/org/apache/commons/commons-parent/91/commons-parent-91.pom -> jasper-maven-07c0e24863046eb1dfc48c14b0a2cd5a667538e2e8dac705569c26fe35b0448d-commons-parent-91.pom
	https://repo.maven.apache.org/maven2/org/apache/commons/commons-parent/97/commons-parent-97.pom -> jasper-maven-7c110feafc1d83f30f6b3c559e2614d04353c01fdab6c3232e446fe4ca8b6dc0-commons-parent-97.pom
	https://repo.maven.apache.org/maven2/org/apache/ftpserver/ftplet-api/1.2.1/ftplet-api-1.2.1-sources.jar -> jasper-maven-ab6f26b95522b2964a1d63315732923928d84c8cc6bf53b1cf96de4c1484e1e7-ftplet-api-1.2.1-sources.jar
	https://repo.maven.apache.org/maven2/org/apache/ftpserver/ftplet-api/1.2.1/ftplet-api-1.2.1.jar -> jasper-maven-511ec75872bde78cd63d00516768a6ed24f35e56c7beaff1a1b5a3c0177b33f1-ftplet-api-1.2.1.jar
	https://repo.maven.apache.org/maven2/org/apache/ftpserver/ftplet-api/1.2.1/ftplet-api-1.2.1.pom -> jasper-maven-ef3db8b2c52f3c416524179cb79813c9c44b9df8dbe4f7377a76c9891267059f-ftplet-api-1.2.1.pom
	https://repo.maven.apache.org/maven2/org/apache/ftpserver/ftpserver-core/1.2.1/ftpserver-core-1.2.1-sources.jar -> jasper-maven-9c91e3abc0548ae5263154c422e2e7dbd3b0833aeca6ce6387f21254986e2552-ftpserver-core-1.2.1-sources.jar
	https://repo.maven.apache.org/maven2/org/apache/ftpserver/ftpserver-core/1.2.1/ftpserver-core-1.2.1.jar -> jasper-maven-9ef05572aae059f17c26a8369382a90fc9403b3bb475a476ee4eb7c711e769c4-ftpserver-core-1.2.1.jar
	https://repo.maven.apache.org/maven2/org/apache/ftpserver/ftpserver-core/1.2.1/ftpserver-core-1.2.1.pom -> jasper-maven-415202d6731eb7aada40b180e86062518357187299266cdddb8f853829662e0a-ftpserver-core-1.2.1.pom
	https://repo.maven.apache.org/maven2/org/apache/ftpserver/ftpserver-parent/1.2.1/ftpserver-parent-1.2.1.pom -> jasper-maven-ec5b058bb6736acf0949fccdf14d1578284d26b1361370f9784ef4d78b15cde7-ftpserver-parent-1.2.1.pom
	https://repo.maven.apache.org/maven2/org/apache/mina/mina-core/2.2.4/mina-core-2.2.4-sources.jar -> jasper-maven-97c4f3cb13c29255e7ad54fda5dbb4e3b6ac39bfc12230c27ea3e54b4a22e085-mina-core-2.2.4-sources.jar
	https://repo.maven.apache.org/maven2/org/apache/mina/mina-core/2.2.4/mina-core-2.2.4.jar -> jasper-maven-68c81220e4e383c0e32f8014966752788cf8980bcdea26fdee036a73af17dbf7-mina-core-2.2.4.jar
	https://repo.maven.apache.org/maven2/org/apache/mina/mina-core/2.2.4/mina-core-2.2.4.pom -> jasper-maven-07876de256862a20585578195b0510a12d2718c092bace1978cdd76f385cc564-mina-core-2.2.4.pom
	https://repo.maven.apache.org/maven2/org/apache/mina/mina-parent/2.2.4/mina-parent-2.2.4.pom -> jasper-maven-75400ee84b71d98e581065b3daf39f7ba7e50db81f09b0983c8d58a51aa4f318-mina-parent-2.2.4.pom
	https://repo.maven.apache.org/maven2/org/apache/sshd/sshd-common/2.19.0/sshd-common-2.19.0-sources.jar -> jasper-maven-32d41d34621f3d34dffaf9690ca18cf4c5055391ae2f59e7bf1702711b161321-sshd-common-2.19.0-sources.jar
	https://repo.maven.apache.org/maven2/org/apache/sshd/sshd-common/2.19.0/sshd-common-2.19.0.jar -> jasper-maven-1278e701b0ab576ac4fa0fc3ac3cfa1e7f0d7c05e07b9ec7abf92cef6739375b-sshd-common-2.19.0.jar
	https://repo.maven.apache.org/maven2/org/apache/sshd/sshd-common/2.19.0/sshd-common-2.19.0.pom -> jasper-maven-86ef1ac1fb16dfa3db7f6eb4a4e11d167e68dc61d526b75ea347d692a8c09b4e-sshd-common-2.19.0.pom
	https://repo.maven.apache.org/maven2/org/apache/sshd/sshd-core/2.19.0/sshd-core-2.19.0-sources.jar -> jasper-maven-002aeb7a4275d103797519fdfc5ec8f89597ace486f25bd60f5d126a32a2fa01-sshd-core-2.19.0-sources.jar
	https://repo.maven.apache.org/maven2/org/apache/sshd/sshd-core/2.19.0/sshd-core-2.19.0.jar -> jasper-maven-ee80e8a0c999721917ee460aac6e3943763480f5d76aa2a9abe15d0b757a5e7b-sshd-core-2.19.0.jar
	https://repo.maven.apache.org/maven2/org/apache/sshd/sshd-core/2.19.0/sshd-core-2.19.0.pom -> jasper-maven-4cf82153f1cd3ad76758de0af4bf5af85f0d03a135efd8b658d7e6cd2abe482c-sshd-core-2.19.0.pom
	https://repo.maven.apache.org/maven2/org/apache/sshd/sshd-sftp/2.19.0/sshd-sftp-2.19.0-sources.jar -> jasper-maven-22ee9fdf598d0b6e2342ec4229a2e63f7401ff47f724872985785b1fdbd677ca-sshd-sftp-2.19.0-sources.jar
	https://repo.maven.apache.org/maven2/org/apache/sshd/sshd-sftp/2.19.0/sshd-sftp-2.19.0.jar -> jasper-maven-1f97d0d80036b35a4badb9b8b2f9b214ede74f5a6c30bf404e47f38e78ac381f-sshd-sftp-2.19.0.jar
	https://repo.maven.apache.org/maven2/org/apache/sshd/sshd-sftp/2.19.0/sshd-sftp-2.19.0.pom -> jasper-maven-0986907787a89e822b977aacacf78a50ea5ed0b52765601552254f08eed74c03-sshd-sftp-2.19.0.pom
	https://repo.maven.apache.org/maven2/org/apache/sshd/sshd/2.19.0/sshd-2.19.0.pom -> jasper-maven-bb06eb8de027271808f6cb7740616263f72b274d9959dba48af6e006bc0abd55-sshd-2.19.0.pom
	https://repo.maven.apache.org/maven2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2-sources.jar -> jasper-maven-d90e5ccba7962b4f0d52d73296642a0c239080a5fa55ba372c317db75780e004-apiguardian-api-1.1.2-sources.jar
	https://repo.maven.apache.org/maven2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar -> jasper-maven-0f9b05d5054db59d8bf23b1679bada06f1a1f064b110516ab5425f5a06d8b30f-apiguardian-api-1.1.2.jar
	https://repo.maven.apache.org/maven2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.module -> jasper-maven-be1ebd8cf04cb7763e6d67f961ee37b17b5b7d646d616f3d2836f945b90f4573-apiguardian-api-1.1.2.module
	https://repo.maven.apache.org/maven2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.pom -> jasper-maven-2f839ed86afc8f5c8470c6805ffe4afa0ba42ac32c3dbaa71d86c271ebdea31d-apiguardian-api-1.1.2.pom
	https://repo.maven.apache.org/maven2/org/assertj/assertj-core/3.27.7/assertj-core-3.27.7-sources.jar -> jasper-maven-3d57c99bc63b6bb28bd150d83eeca34325eca71c46a16d829a07974e2a89de61-assertj-core-3.27.7-sources.jar
	https://repo.maven.apache.org/maven2/org/assertj/assertj-core/3.27.7/assertj-core-3.27.7.jar -> jasper-maven-3ef39afc6b4fc0010feca32d40bbabea55499fa666b2e7a54f1c60258110a834-assertj-core-3.27.7.jar
	https://repo.maven.apache.org/maven2/org/assertj/assertj-core/3.27.7/assertj-core-3.27.7.pom -> jasper-maven-7f6e3bc29517eb67179fdee5a35e6072562a0bd9914e1249c83fb3daeb964fac-assertj-core-3.27.7.pom
	https://repo.maven.apache.org/maven2/org/bouncycastle/bcpkix-jdk18on/1.85/bcpkix-jdk18on-1.85-sources.jar -> jasper-maven-a9827f5e2070dcf605d8be7476500aba390e4457cca340f29ae57d504e06229c-bcpkix-jdk18on-1.85-sources.jar
	https://repo.maven.apache.org/maven2/org/bouncycastle/bcpkix-jdk18on/1.85/bcpkix-jdk18on-1.85.jar -> jasper-maven-f5fed335c8dd6bb7954ad66f0a8b5a4d2d0d2f4bc18ad8cfec77365e8c12d57a-bcpkix-jdk18on-1.85.jar
	https://repo.maven.apache.org/maven2/org/bouncycastle/bcpkix-jdk18on/1.85/bcpkix-jdk18on-1.85.pom -> jasper-maven-8ef10a1fdbe3400f38e0f3e0f2293cf9af0c1c87de37228c574a28191694fd7c-bcpkix-jdk18on-1.85.pom
	https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/1.85.2/bcprov-jdk18on-1.85.2-sources.jar -> jasper-maven-58b4d0bcfa5700d03ea75db59daa0b7a8be1729ec48378ffd9df971ed08528e0-bcprov-jdk18on-1.85.2-sources.jar
	https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/1.85.2/bcprov-jdk18on-1.85.2.jar -> jasper-maven-fca7f132e7968e197d00b8f4a8fb1fe5affbf67b645eff9b765915338571441f-bcprov-jdk18on-1.85.2.jar
	https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/1.85.2/bcprov-jdk18on-1.85.2.pom -> jasper-maven-55c88ebb578ef5efdd5a73578a62d97fb4d02ab616f612aa76cb1606a093121c-bcprov-jdk18on-1.85.2.pom
	https://repo.maven.apache.org/maven2/org/bouncycastle/bcutil-jdk18on/1.85/bcutil-jdk18on-1.85-sources.jar -> jasper-maven-bc96a3e702c65a58c429fbf7eb50b52afeed93325cdcc54cd526b5d93e64399c-bcutil-jdk18on-1.85-sources.jar
	https://repo.maven.apache.org/maven2/org/bouncycastle/bcutil-jdk18on/1.85/bcutil-jdk18on-1.85.jar -> jasper-maven-25ef4b9e8d98f9873b57615fa397b73d34b257acb0b47af642bdaecb06930466-bcutil-jdk18on-1.85.jar
	https://repo.maven.apache.org/maven2/org/bouncycastle/bcutil-jdk18on/1.85/bcutil-jdk18on-1.85.pom -> jasper-maven-289be06d5454eeab1de14ed9b6f194addd70e09ef3938f8cc77c248d82cccaee-bcutil-jdk18on-1.85.pom
	https://repo.maven.apache.org/maven2/org/checkerframework/checker-qual/3.21.2/checker-qual-3.21.2-sources.jar -> jasper-maven-fdb1f53fa22c46e02c6ddcd95ec893172ce8db8880d154fdcab8bc443296b76d-checker-qual-3.21.2-sources.jar
	https://repo.maven.apache.org/maven2/org/checkerframework/checker-qual/3.21.2/checker-qual-3.21.2.jar -> jasper-maven-027f3830dca71824be3cbd46bd7d78fb8f7e2a01afa73bd2c3cad509e9a2796e-checker-qual-3.21.2.jar
	https://repo.maven.apache.org/maven2/org/checkerframework/checker-qual/3.21.2/checker-qual-3.21.2.module -> jasper-maven-743f99226a59a3cdde7c903fcb61e9b1ec3c8cc954ea58a00c0d949366b453e3-checker-qual-3.21.2.module
	https://repo.maven.apache.org/maven2/org/checkerframework/checker-qual/3.21.2/checker-qual-3.21.2.pom -> jasper-maven-ea21dc1c4472de0c4b604214dd7ba8251f1c72869841f56ebe3f9e7164122d99-checker-qual-3.21.2.pom
	https://repo.maven.apache.org/maven2/org/commonmark/commonmark-ext-gfm-strikethrough/0.30.0/commonmark-ext-gfm-strikethrough-0.30.0-sources.jar -> jasper-maven-9146b59bdb0bd7bd582c6a1eaa36adad8cdbe7bceb4b9d59445d37b1eba41db7-commonmark-ext-gfm-strikethrough-0.30.0-sources.jar
	https://repo.maven.apache.org/maven2/org/commonmark/commonmark-ext-gfm-strikethrough/0.30.0/commonmark-ext-gfm-strikethrough-0.30.0.jar -> jasper-maven-5666f27050ab8c606abac276dcf8b4665e4bef0fc8943e7f461b7097220869a1-commonmark-ext-gfm-strikethrough-0.30.0.jar
	https://repo.maven.apache.org/maven2/org/commonmark/commonmark-ext-gfm-strikethrough/0.30.0/commonmark-ext-gfm-strikethrough-0.30.0.pom -> jasper-maven-b43f45a57a0e0a4b04704db9c402ae29d9dcecd0bb9e45cd72206b0721713981-commonmark-ext-gfm-strikethrough-0.30.0.pom
	https://repo.maven.apache.org/maven2/org/commonmark/commonmark-ext-gfm-tables/0.30.0/commonmark-ext-gfm-tables-0.30.0-sources.jar -> jasper-maven-7c6fc44a8f6c4fc02bc031ec91a47cdced5aac5de18def21593c092d6324b29a-commonmark-ext-gfm-tables-0.30.0-sources.jar
	https://repo.maven.apache.org/maven2/org/commonmark/commonmark-ext-gfm-tables/0.30.0/commonmark-ext-gfm-tables-0.30.0.jar -> jasper-maven-a20267561c1a45d77d498cf1e7ebcd2f547c3e1b9f8cf3a78e20aa4f8010b3d9-commonmark-ext-gfm-tables-0.30.0.jar
	https://repo.maven.apache.org/maven2/org/commonmark/commonmark-ext-gfm-tables/0.30.0/commonmark-ext-gfm-tables-0.30.0.pom -> jasper-maven-ec840a1486ef7c8ca3ec812bd35af2b8c34fd1a0a5138f2c076a679ec567f956-commonmark-ext-gfm-tables-0.30.0.pom
	https://repo.maven.apache.org/maven2/org/commonmark/commonmark-ext-task-list-items/0.30.0/commonmark-ext-task-list-items-0.30.0-sources.jar -> jasper-maven-7ae11e117c9f837d86571cc213c20f881dfadabb46eecedac8570f4c143f4c18-commonmark-ext-task-list-items-0.30.0-sources.jar
	https://repo.maven.apache.org/maven2/org/commonmark/commonmark-ext-task-list-items/0.30.0/commonmark-ext-task-list-items-0.30.0.jar -> jasper-maven-1c2371710cfc523450bdaa0719155f0078cbf432b83c54ab861fc3bae3bc586b-commonmark-ext-task-list-items-0.30.0.jar
	https://repo.maven.apache.org/maven2/org/commonmark/commonmark-ext-task-list-items/0.30.0/commonmark-ext-task-list-items-0.30.0.pom -> jasper-maven-e3a4b6673ffd6582bcf4411fb4cdb05f595ad857e9d700581df5163eee5c0d17-commonmark-ext-task-list-items-0.30.0.pom
	https://repo.maven.apache.org/maven2/org/commonmark/commonmark-parent/0.30.0/commonmark-parent-0.30.0.pom -> jasper-maven-c0578c294fe694ecc05b41e102c9e258a571049bf4a57935b25733975e186727-commonmark-parent-0.30.0.pom
	https://repo.maven.apache.org/maven2/org/commonmark/commonmark/0.30.0/commonmark-0.30.0-sources.jar -> jasper-maven-10dc855b6c7039bbfae666c02ff51a3a95009c140a6f524612172fba00337653-commonmark-0.30.0-sources.jar
	https://repo.maven.apache.org/maven2/org/commonmark/commonmark/0.30.0/commonmark-0.30.0.jar -> jasper-maven-efd5e3d49c4381507b6c60badefe47e020321c2bdcab87fbafa8920064269866-commonmark-0.30.0.jar
	https://repo.maven.apache.org/maven2/org/commonmark/commonmark/0.30.0/commonmark-0.30.0.pom -> jasper-maven-2fbfa86a34e5072d4d023ba2611d3fb1cff616bfbfcbc980a8877a749b9b8f81-commonmark-0.30.0.pom
	https://repo.maven.apache.org/maven2/org/jetbrains/annotations/13.0/annotations-13.0-sources.jar -> jasper-maven-ecb7fc93f157cff4aa682373ad06da95266f313fa4c7b9b98272cf25500e662e-annotations-13.0-sources.jar
	https://repo.maven.apache.org/maven2/org/jetbrains/annotations/13.0/annotations-13.0.jar -> jasper-maven-a5abecba96f492087720782207e31ad2507c5a7a30b687abe6154951b36dadf5-annotations-13.0.jar
	https://repo.maven.apache.org/maven2/org/jetbrains/annotations/13.0/annotations-13.0.pom -> jasper-maven-e2c1c8c1537b007dc7666befc6aa71f60f19e4fb6fc2ee47bfdbceb6d517f2d4-annotations-13.0.pom
	https://repo.maven.apache.org/maven2/org/jetbrains/annotations/24.0.1/annotations-24.0.1-sources.jar -> jasper-maven-cdcfcfc48b4078ccc2ac7e591dab94dc2583f8d6cffbb231a27ad0435ad3a1e1-annotations-24.0.1-sources.jar
	https://repo.maven.apache.org/maven2/org/jetbrains/annotations/24.0.1/annotations-24.0.1.jar -> jasper-maven-bd4fcac0055b877fbff978d3c60d77586975b6951e5b683e0384565db9f53beb-annotations-24.0.1.jar
	https://repo.maven.apache.org/maven2/org/jetbrains/annotations/24.0.1/annotations-24.0.1.pom -> jasper-maven-af77f9c208e4c414485ac9ff3543f73b3666091ec171bdd25d46aa61cfe97463-annotations-24.0.1.pom
	https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/jediterm/jediterm-core/3.76/jediterm-core-3.76-sources.jar -> jasper-maven-b997b09a254349f24d37f716485ad48ee98056227b366373b22026d0e7910687-jediterm-core-3.76-sources.jar
	https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/jediterm/jediterm-core/3.76/jediterm-core-3.76.jar -> jasper-maven-05fb10c14eeaa1c324e1a53c84a5991f6fa2d1a585b21565fdc91b751123710f-jediterm-core-3.76.jar
	https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/jediterm/jediterm-core/3.76/jediterm-core-3.76.module -> jasper-maven-2462468023b3d27de7205b81bf7914c56a12e733d47b4a2c65e6f4691dde7e0f-jediterm-core-3.76.module
	https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/jediterm/jediterm-core/3.76/jediterm-core-3.76.pom -> jasper-maven-4227dc805355856824f6a9329a9c0ecc23f21bf12de9ee90858de45164933db3-jediterm-core-3.76.pom
	https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-reflect/2.4.0/kotlin-reflect-2.4.0-sources.jar -> jasper-maven-268422a42082caa820d8a527f14a9936596b51cb53fefa339a5fd4e542d8342d-kotlin-reflect-2.4.0-sources.jar
	https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-reflect/2.4.0/kotlin-reflect-2.4.0.pom -> jasper-maven-923fa428409b7d7466668ed93138c6c7be56a1572c309ea0e3f3ae1f6d6bd82a-kotlin-reflect-2.4.0.pom
	https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.4.0/kotlin-stdlib-2.4.0-sources.jar -> jasper-maven-ceea0b3b56572607d12aca8612c3e254c1695f62829f98f7fcbcdba039a1dcd6-kotlin-stdlib-2.4.0-sources.jar
	https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.4.0/kotlin-stdlib-2.4.0.jar -> jasper-maven-ac1868752952a33189cef02194086e5226789cb7231ffa6a7077942ff03663c9-kotlin-stdlib-2.4.0.jar
	https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.4.0/kotlin-stdlib-2.4.0.module -> jasper-maven-3a60389ff5ab38ee8c1836eee43ce52281e865dccbf30fd35b8ce08fb1ae4b8a-kotlin-stdlib-2.4.0.module
	https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.4.0/kotlin-stdlib-2.4.0.pom -> jasper-maven-557f15627b88016dcd6f6b1b6283c09e5770ff0e9807e1370100730282953306-kotlin-stdlib-2.4.0.pom
	https://repo.maven.apache.org/maven2/org/jetbrains/pty4j/pty4j/0.13.10/pty4j-0.13.10-sources.jar -> jasper-maven-c0df60d0d98fc536734eb2b67e5cba125ef3ea088816212350888c3abc07d748-pty4j-0.13.10-sources.jar
	https://repo.maven.apache.org/maven2/org/jetbrains/pty4j/pty4j/0.13.10/pty4j-0.13.10.jar -> jasper-maven-33dad6aa61dcf9fbd1122754afccf9a1766e1592145814b02d42a43b97f8fae1-pty4j-0.13.10.jar
	https://repo.maven.apache.org/maven2/org/jetbrains/pty4j/pty4j/0.13.10/pty4j-0.13.10.module -> jasper-maven-1cd0bd4d52d77b7e706a31ffbba0359d532685533b2791263c2544d7cdbab7ce-pty4j-0.13.10.module
	https://repo.maven.apache.org/maven2/org/jetbrains/pty4j/pty4j/0.13.10/pty4j-0.13.10.pom -> jasper-maven-479ef2412f46186f1ed3837061ab178b5e7c80375b118aa1d5143e2c0795d798-pty4j-0.13.10.pom
	https://repo.maven.apache.org/maven2/org/jetbrains/runtime/jbr-api/1.9.0/jbr-api-1.9.0-sources.jar -> jasper-maven-097fe9dbcbbb16f3ec043aac3f7847c6217f41cf51e08128354410006660540f-jbr-api-1.9.0-sources.jar
	https://repo.maven.apache.org/maven2/org/jetbrains/runtime/jbr-api/1.9.0/jbr-api-1.9.0.jar -> jasper-maven-075bfd298b7f4e4a49df36c558abaf603271ecfbe87670ee000086091f60ab34-jbr-api-1.9.0.jar
	https://repo.maven.apache.org/maven2/org/jetbrains/runtime/jbr-api/1.9.0/jbr-api-1.9.0.pom -> jasper-maven-60fc4481e191a125bd81b997249736d3ee01819d53e5e2db749527d1d5a48160-jbr-api-1.9.0.pom
	https://repo.maven.apache.org/maven2/org/jspecify/jspecify/1.0.0/jspecify-1.0.0-sources.jar -> jasper-maven-c0c60e827fa5fe509ad7a4a92aeb1ff62f894557abd24526a4ca7b1b2565db43-jspecify-1.0.0-sources.jar
	https://repo.maven.apache.org/maven2/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar -> jasper-maven-3f3f254ee91b4f62fa8f5247353c7c32d40b19a0190d8d674dd0580511701a1a-jspecify-1.0.0.jar
	https://repo.maven.apache.org/maven2/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.module -> jasper-maven-c5f42f3b608955ebd5cef09434a2a708d39391ad93bcdcc40276c69f6f5062f5-jspecify-1.0.0.module
	https://repo.maven.apache.org/maven2/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.pom -> jasper-maven-df2e439e0a90816f4bd43f378d4f4a23ccdf164b08aa0d8382a6576fcc9d125c-jspecify-1.0.0.pom
	https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.11.0/junit-bom-5.11.0.module -> jasper-maven-bf940d3c765d66acf8dcecb07a95072409654586cb49549f1e13fac69272051d-junit-bom-5.11.0.module
	https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.11.0/junit-bom-5.11.0.pom -> jasper-maven-64e6e032377114ab1ac068952b9864c22c4329b526a42278dc22f6a47ba9c056-junit-bom-5.11.0.pom
	https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.12.2/junit-bom-5.12.2.module -> jasper-maven-a4dcd7696a74eb8f4d88cb8ee71e69eeff5f5aedad130b556acbb684960e8fdd-junit-bom-5.12.2.module
	https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.12.2/junit-bom-5.12.2.pom -> jasper-maven-ed535fbbb2d4b627b0718e9d12bac33399f85dd50c801fa0e116ba1c6b2d69a1-junit-bom-5.12.2.pom
	https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.13.4/junit-bom-5.13.4.module -> jasper-maven-bf07db029828b3642d2710b7b545c7e8700482512e2b310b091fda0523e729db-junit-bom-5.13.4.module
	https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.13.4/junit-bom-5.13.4.pom -> jasper-maven-1603b22bec39009e8722141a297e2ae68ee15b004874ac25e86b28af6978c4c9-junit-bom-5.13.4.pom
	https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.14.2/junit-bom-5.14.2.module -> jasper-maven-784877530f01f6ec2264846b9c6f1f933f714b2857d644cdf0de5a7ade9e79c1-junit-bom-5.14.2.module
	https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.14.2/junit-bom-5.14.2.pom -> jasper-maven-d042ca5920a1aefc6dfe4500a04c5781f50ec772a455fac6e0ae28d1f100b813-junit-bom-5.14.2.pom
	https://repo.maven.apache.org/maven2/org/junit/junit-bom/6.1.3/junit-bom-6.1.3.module -> jasper-maven-914e0fd6971d323ecbaf7b7a304729aeb60d85fcb0a3ee8216a1cecbbdf5d07b-junit-bom-6.1.3.module
	https://repo.maven.apache.org/maven2/org/junit/junit-bom/6.1.3/junit-bom-6.1.3.pom -> jasper-maven-3b914d24e834e206f9f24d3958fdcdb38cf7ff1be280a9edfd4b9385e6ac4a03-junit-bom-6.1.3.pom
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-api/6.1.3/junit-jupiter-api-6.1.3-sources.jar -> jasper-maven-dfd8f8634bf9d29a3bc65ecde24e878c35771e0d296eeef81db34f69ef358087-junit-jupiter-api-6.1.3-sources.jar
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-api/6.1.3/junit-jupiter-api-6.1.3.jar -> jasper-maven-a65d7a1edb59065332edf64cd23ae4186fa1dc9d8b0ff4a37df7487c380c7b50-junit-jupiter-api-6.1.3.jar
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-api/6.1.3/junit-jupiter-api-6.1.3.module -> jasper-maven-8414dde43199e2a722e5346411896df6792fe3bfa6ec34c71404786799f7a5b5-junit-jupiter-api-6.1.3.module
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-api/6.1.3/junit-jupiter-api-6.1.3.pom -> jasper-maven-96af11ea1be25f1c5e75be64947cd3ba92e34b955517220e2c33a6fdaea83a99-junit-jupiter-api-6.1.3.pom
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-engine/6.1.3/junit-jupiter-engine-6.1.3-sources.jar -> jasper-maven-2428937dd8f66c7420f2f55df133f8a9d23bb5205c6b0dc7bd644f92528608e2-junit-jupiter-engine-6.1.3-sources.jar
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-engine/6.1.3/junit-jupiter-engine-6.1.3.jar -> jasper-maven-af2da2fd1b2c80403937e99f88918ee95cfe842c7b4bcd182b06ec637682b807-junit-jupiter-engine-6.1.3.jar
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-engine/6.1.3/junit-jupiter-engine-6.1.3.module -> jasper-maven-ba9f40dce8c28c0bc78085b66a5e34669713403ba946edd3edb369346093e9be-junit-jupiter-engine-6.1.3.module
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-engine/6.1.3/junit-jupiter-engine-6.1.3.pom -> jasper-maven-3c71602bbd1c2e506d2b8926c6d641388eb57a8d892e797359232ec39e382138-junit-jupiter-engine-6.1.3.pom
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-params/6.1.3/junit-jupiter-params-6.1.3-sources.jar -> jasper-maven-f134e37aba8d86fb1728785b2f48abc75344a0f191ed55d880e80358e8513571-junit-jupiter-params-6.1.3-sources.jar
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-params/6.1.3/junit-jupiter-params-6.1.3.jar -> jasper-maven-9d198fb68c81ccc7891dea20a1ca023211fb7999365cb8aa523ad79b71071b13-junit-jupiter-params-6.1.3.jar
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-params/6.1.3/junit-jupiter-params-6.1.3.module -> jasper-maven-f3624aa8a0e6f83c13185cd3fb84caef2fdcd06b9fdbb58895743a019c121f0f-junit-jupiter-params-6.1.3.module
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-params/6.1.3/junit-jupiter-params-6.1.3.pom -> jasper-maven-524b65e6895329c2debad9a9fbbd667e6d9ddc0e8f9adfa0f76db4def211a8af-junit-jupiter-params-6.1.3.pom
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter/6.1.3/junit-jupiter-6.1.3-sources.jar -> jasper-maven-e7a536cd29834810f9dad1ce89827316547dc69e9010286bc770ef537d5a3be8-junit-jupiter-6.1.3-sources.jar
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter/6.1.3/junit-jupiter-6.1.3.jar -> jasper-maven-a3ac68bc7b3df98ed7564eb6ee070eff854c152702d6a79495d241b9224d4314-junit-jupiter-6.1.3.jar
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter/6.1.3/junit-jupiter-6.1.3.module -> jasper-maven-c4b96747bf037244c8cbe27a31babaf980715444c974152212a37812ba9e37c5-junit-jupiter-6.1.3.module
	https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter/6.1.3/junit-jupiter-6.1.3.pom -> jasper-maven-e564d9562e4af1f4a2bda4d9c7c0027feb1498dc3ea4ca580427a2aacf6e535b-junit-jupiter-6.1.3.pom
	https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-commons/6.1.3/junit-platform-commons-6.1.3-sources.jar -> jasper-maven-028bf131210e53eef7ecd25229137a996249a604759d5e4d241a6454207f5de7-junit-platform-commons-6.1.3-sources.jar
	https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-commons/6.1.3/junit-platform-commons-6.1.3.jar -> jasper-maven-feb131cb9f71d9e9318489733e3c4e04593d442f277539c632993621e167438d-junit-platform-commons-6.1.3.jar
	https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-commons/6.1.3/junit-platform-commons-6.1.3.module -> jasper-maven-4062a8a03bc585515eb37d48d9138d51b2d32314f18714f6010782eae258bdfc-junit-platform-commons-6.1.3.module
	https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-commons/6.1.3/junit-platform-commons-6.1.3.pom -> jasper-maven-e5d0dc703bc8bfc417d27e8383bb7f1512a565c3b627a0f22157b7d38115d323-junit-platform-commons-6.1.3.pom
	https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-engine/6.1.3/junit-platform-engine-6.1.3-sources.jar -> jasper-maven-42554e62ae1d0e3b27cec5bce8ce69f0f803a5b1667e2384f2253eef92216902-junit-platform-engine-6.1.3-sources.jar
	https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-engine/6.1.3/junit-platform-engine-6.1.3.jar -> jasper-maven-159455074eb91f20b74818cd642c2f719d063845eca0f49a76c26bd478e2f48b-junit-platform-engine-6.1.3.jar
	https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-engine/6.1.3/junit-platform-engine-6.1.3.module -> jasper-maven-735a6f9ccb4b85369efeb9c50e9b3b40e299da180978916953bd724b3593d380-junit-platform-engine-6.1.3.module
	https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-engine/6.1.3/junit-platform-engine-6.1.3.pom -> jasper-maven-cd17bfa66d2472e6665cd44697fa44169e70732d22fd4257d8a5f492063b6f83-junit-platform-engine-6.1.3.pom
	https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-launcher/6.1.3/junit-platform-launcher-6.1.3-sources.jar -> jasper-maven-9f8ee397036f30b549f3334c3feb0cf20f55f7f6b6daa572b20e37d39c27b6a2-junit-platform-launcher-6.1.3-sources.jar
	https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-launcher/6.1.3/junit-platform-launcher-6.1.3.jar -> jasper-maven-9ad830eee30a67ab2be64b126d8535daea0130e080cb8948350f4e82678f4822-junit-platform-launcher-6.1.3.jar
	https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-launcher/6.1.3/junit-platform-launcher-6.1.3.module -> jasper-maven-1c244e1ea64c5a6e98100009cb9f2e2211ee031b22352d99367c2b2e7912dbc9-junit-platform-launcher-6.1.3.module
	https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-launcher/6.1.3/junit-platform-launcher-6.1.3.pom -> jasper-maven-65950093fc5e9450a9c8b6e4db8258b46f8c751f6f42f0c38de127d3e3c12090-junit-platform-launcher-6.1.3.pom
	https://repo.maven.apache.org/maven2/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0-sources.jar -> jasper-maven-05bf4c0c1a8ba40fbc2ddaafb42178fcc8a3dab5712b324e1ca2f1e71380ae2a-opentest4j-1.3.0-sources.jar
	https://repo.maven.apache.org/maven2/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar -> jasper-maven-091d4b6942b78379e856db247da41c0b3236a128829a8dfa0934eb6efc956b5f-opentest4j-1.3.0.jar
	https://repo.maven.apache.org/maven2/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.module -> jasper-maven-92ba005e0013684f0d20e26d63890b00f6a595f21322ed9bddf4c7e5bbe6a379-opentest4j-1.3.0.module
	https://repo.maven.apache.org/maven2/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.pom -> jasper-maven-9ce17f2f493a5b2c38391c03867435be00f2b426ea1098d33e1801a65db2ef31-opentest4j-1.3.0.pom
	https://repo.maven.apache.org/maven2/org/slf4j/jcl-over-slf4j/1.7.36/jcl-over-slf4j-1.7.36-sources.jar -> jasper-maven-ecdd3341819a90bb131163db25c9be8dba80c8dde4ea6bfd96f1e7857991bfda-jcl-over-slf4j-1.7.36-sources.jar
	https://repo.maven.apache.org/maven2/org/slf4j/jcl-over-slf4j/1.7.36/jcl-over-slf4j-1.7.36.jar -> jasper-maven-16c0df650e4e96bfb2acea3718e1c224d6ff37622f6d508c190ad85713a464bf-jcl-over-slf4j-1.7.36.jar
	https://repo.maven.apache.org/maven2/org/slf4j/jcl-over-slf4j/1.7.36/jcl-over-slf4j-1.7.36.pom -> jasper-maven-c2610e2003b609cd803a1ca63336c942688c9883e0c9e87f04f59e1dae443212-jcl-over-slf4j-1.7.36.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.pom -> jasper-maven-04c62068d0403f25592909aeaf303f272f94cbcc36ecdc6cc2301404029c88da-slf4j-api-1.7.36.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13-sources.jar -> jasper-maven-75dbfd6b6b6c19d0ecb2df7b2ed4e1dff5736b1998431369eed4eb5c096fe25a-slf4j-api-2.0.13-sources.jar
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar -> jasper-maven-c7d1f01d7452896d9735dca4a595a7ed9686ab52fb58a4239a962cc2a4a999a9-slf4j-api-2.0.13.jar
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.pom -> jasper-maven-3fe372884f6762d9f54ab9ce109db59c19760aaeca9c6daecdd4b43664cc8a06-slf4j-api-2.0.13.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17-sources.jar -> jasper-maven-c7580555bc86e2ee16016059a3ef7e6b62ba9d7688995a21b76a50cf0f8330b9-slf4j-api-2.0.17-sources.jar
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar -> jasper-maven-683808887f45a271eec78a63c62b0be2623160e3e703267da39f580de922498e-slf4j-api-2.0.17.jar
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.pom -> jasper-maven-5a87e163d3d9eccc487dfe62c1a7ddb3e7ef90f77f2da9147178e13d1fad2f1b-slf4j-api-2.0.17.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.pom -> jasper-maven-1d438ef5d64cce847b4158b114c7d7bb5eb8fcdcf1323dde6e1b02caf65563f5-slf4j-api-2.0.9.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-bom/2.0.13/slf4j-bom-2.0.13.pom -> jasper-maven-9222bab87caa0e322043515837e58bf7caf0619dca5f538a384e7be5f49dac9b-slf4j-bom-2.0.13.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-bom/2.0.17/slf4j-bom-2.0.17.pom -> jasper-maven-fe04ba2ec4bbc319be313c9d11c5dd9322702d27770be450c5672d9a51b34e46-slf4j-bom-2.0.17.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-bom/2.0.9/slf4j-bom-2.0.9.pom -> jasper-maven-d7455d80ae50aeaaab81515cefd6cbc30dde3f87d157a2868ca96f6a0f064e24-slf4j-bom-2.0.9.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-jdk14/2.0.13/slf4j-jdk14-2.0.13-sources.jar -> jasper-maven-7c34cb2d82de9a641ea2e9d283e7d8ce592f5eac9eba08a813afb9ebc102f3a1-slf4j-jdk14-2.0.13-sources.jar
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-jdk14/2.0.13/slf4j-jdk14-2.0.13.jar -> jasper-maven-ea089a336fe570942881acb76ab52806dfb2ff2258c5f097fcfa974d245c8858-slf4j-jdk14-2.0.13.jar
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-jdk14/2.0.13/slf4j-jdk14-2.0.13.pom -> jasper-maven-af1c577e7a768492347a60c635a09ad5379af3e856eed325d309536def7c784c-slf4j-jdk14-2.0.13.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-nop/2.0.13/slf4j-nop-2.0.13-sources.jar -> jasper-maven-671eeba1b7f35979b3191a4c5416dd3150cc402cfb9f75d9882cd2667299739f-slf4j-nop-2.0.13-sources.jar
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-nop/2.0.13/slf4j-nop-2.0.13.jar -> jasper-maven-54ecfd8ea9699ec5059411e6915ccb74ce403fdd25bd483bde1c7123b124220a-slf4j-nop-2.0.13.jar
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-nop/2.0.13/slf4j-nop-2.0.13.pom -> jasper-maven-55a3a300c17257e8ee5076584761af2122cf8567f12a02fd416263c947f8d19c-slf4j-nop-2.0.13.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-nop/2.0.17/slf4j-nop-2.0.17-sources.jar -> jasper-maven-40941796578c26e83a3bd6d322725a1c95a5646d6f7286b479fd9b4922f4a1b3-slf4j-nop-2.0.17-sources.jar
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-nop/2.0.17/slf4j-nop-2.0.17.jar -> jasper-maven-51e9bb391844fb9fc2dcbca54c16cb0d217ad2ef7fe7b79363352db6e2edd22c-slf4j-nop-2.0.17.jar
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-nop/2.0.17/slf4j-nop-2.0.17.pom -> jasper-maven-7e8b35a7068786c012cc444b0537347535f7c766676e6728824133732def3876-slf4j-nop-2.0.17.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-parent/1.7.36/slf4j-parent-1.7.36.pom -> jasper-maven-c48c380231f8e48c8af1041977deac9347417d0e02f461fe3a4d715d62da47ea-slf4j-parent-1.7.36.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-parent/2.0.13/slf4j-parent-2.0.13.pom -> jasper-maven-430249f3dc7ca54e67362330acc99e6bea1a772f8c5070b64d3bf5600967a0fb-slf4j-parent-2.0.13.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-parent/2.0.17/slf4j-parent-2.0.17.pom -> jasper-maven-ba1aa0fe6acc3524b6b747cd4551ed9ea1e9655551a48c20098cd1f3ed3adf4b-slf4j-parent-2.0.17.pom
	https://repo.maven.apache.org/maven2/org/slf4j/slf4j-parent/2.0.9/slf4j-parent-2.0.9.pom -> jasper-maven-94db578f3a43c2e752097d4957eb2f8a724a1628a13232c47edbf7a1d4e76db8-slf4j-parent-2.0.9.pom
	https://repo.maven.apache.org/maven2/org/sonatype/oss/oss-parent/9/oss-parent-9.pom -> jasper-maven-990b4aa0e8019b1cdbed89f84b239745e71ab2caf290474c2f8ec0049cd3f190-oss-parent-9.pom
	https://repo.maven.apache.org/maven2/org/springframework/spring-framework-bom/5.3.39/spring-framework-bom-5.3.39.module -> jasper-maven-2a56edc6747d69a98e7a8b227b351c8a436005045a9b85a768b7dce7534922a0-spring-framework-bom-5.3.39.module
	https://repo.maven.apache.org/maven2/org/springframework/spring-framework-bom/5.3.39/spring-framework-bom-5.3.39.pom -> jasper-maven-d1f37f710aaa1543ad1209c3e3820de54bc0f309829d6fbb874af95d729c7491-spring-framework-bom-5.3.39.pom
	https://repo.maven.apache.org/maven2/org/testcontainers/testcontainers-bom/1.21.3/testcontainers-bom-1.21.3.pom -> jasper-maven-19dce3bf904bc942e43ff4fb0cbd70e5be4651ed0e2247a0f797f334a0ba25e2-testcontainers-bom-1.21.3.pom
	https://repo.maven.apache.org/maven2/org/tomlj/tomlj/1.1.1/tomlj-1.1.1-sources.jar -> jasper-maven-d73e9b68fb14dc496ea73edde4d61e0581fa78e33c536b080c44ae6506f7603b-tomlj-1.1.1-sources.jar
	https://repo.maven.apache.org/maven2/org/tomlj/tomlj/1.1.1/tomlj-1.1.1.jar -> jasper-maven-28454d1cab7f98c9f89550dd554317d14c7c4c791777434131e0141439680c52-tomlj-1.1.1.jar
	https://repo.maven.apache.org/maven2/org/tomlj/tomlj/1.1.1/tomlj-1.1.1.pom -> jasper-maven-15ef7839e0bb32db258f5fb7d37dfa6fb2e10f9fe5e6e1c566cfc3461b6a98c0-tomlj-1.1.1.pom
	https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0-sources.jar -> jasper-maven-5ccba8d71766dda3648a284b599e3dc7d27748df1ebf16e1bcfbfd32055cc99f-sqlite-jdbc-3.53.4.0-sources.jar
	https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.jar -> jasper-maven-2ea49cf97df82ed2fc3b08f18173305002413f7a2d43a22ef9fec347a09cbcb2-sqlite-jdbc-3.53.4.0.jar
	https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.pom -> jasper-maven-4ce4fda6f7291c42855262406cd09a3862f07e0c3376e6e5d94df3d85d8db0ab-sqlite-jdbc-3.53.4.0.pom
"
S="${WORKDIR}/Jasper-${JASPER_COMMIT}"

LICENSE="PolyForm-Noncommercial-1.0.0 Apache-2.0 BSD-2 BSD MIT Boost-1.0
	EPL-1.0 GPL-2-with-classpath-exception OFL-1.1 Jasper-JogAmp Jasper-Nerd-Fonts"
SLOT="0"
KEYWORDS="~arm64"
IUSE="gtk gtk4 qt6 kde"
REQUIRED_USE="elibc_glibc kde? ( qt6 )"
# The private JBR and dependency jars contain prebuilt native code.
RESTRICT="bindist mirror strip"

RDEPEND="
	app-accessibility/at-spi2-core:2
	dev-libs/expat
	dev-libs/glib:2
	dev-libs/nspr
	dev-libs/nss
	dev-libs/wayland
	media-libs/alsa-lib
	media-libs/fontconfig
	media-libs/freetype
	media-libs/mesa
	net-print/cups
	sys-apps/dbus
	>=sys-libs/glibc-2.28
	virtual/udev
	virtual/zlib
	x11-libs/cairo
	x11-libs/libX11
	x11-libs/libXcomposite
	x11-libs/libXdamage
	x11-libs/libXext
	x11-libs/libXfixes
	x11-libs/libXi
	x11-libs/libXrandr
	x11-libs/libXrender
	x11-libs/libXtst
	x11-libs/libxcb
	x11-libs/libxkbcommon
	x11-libs/pango
	x11-misc/xdg-utils
	gtk? ( >=x11-libs/gtk+-3.24:3 )
	gtk4? ( >=gui-libs/gtk-4.10:4 gui-libs/libadwaita:1 )
	qt6? ( >=dev-qt/qtbase-6.2:6[gui,widgets] )
	kde? ( >=dev-qt/qtbase-6.5:6 >=kde-frameworks/kconfig-6:6 >=kde-frameworks/kcolorscheme-6:6 )
"
DEPEND="${RDEPEND}"
BDEPEND="
	app-arch/unzip
	dev-util/patchelf
	${PYTHON_DEPS}
	sys-devel/gcc[cxx]
	virtual/pkgconfig
	qt6? ( >=dev-build/cmake-3.21 dev-build/make )
"

src_unpack() {
	# Never unpack Maven jars as if they were source archives.
	unpack "${PN}-${JASPER_COMMIT}.tar.gz"
	mkdir "${WORKDIR}/jbr" || die
	cd "${WORKDIR}/jbr" || die
	unpack "${JASPER_JBR}.tar.gz"
}

src_prepare() {
	default
	"${EPYTHON}" "${FILESDIR}/maven.py" stage "${FILESDIR}/maven-lock.json" \
		"${DISTDIR}" "${WORKDIR}/maven" || die "Maven repository staging failed"
	# The wrapper opens this local distfile, and extracts Gradle into its new private home.
	sed -i "s|^distributionUrl=.*|distributionUrl=file\\\\:${DISTDIR}/gradle-${JASPER_GRADLE}-bin.zip|" \
		gradle/wrapper/gradle-wrapper.properties || die
	# Normalize the SDK's single top-level directory without assuming its name.
	local release
	local -a releases
	mapfile -t releases < <(find "${WORKDIR}/jbr" -maxdepth 3 -name release -type f)
	[[ ${#releases[@]} == 1 ]] || die "Expected one JBR SDK"
	release=${releases[0]}
	ln -s "${release%/release}" "${WORKDIR}/jdk" || die
	mkdir "${T}/compilers" || die
	tc-export CC CXX
	# Native upstream tasks call cc/gcc/g++; adapt them to Portage's toolchain and flags.
	cat > "${T}/compilers/gcc" <<-WRAPPER
	#!/bin/sh
	exec ${CC} ${CPPFLAGS} ${CFLAGS} ${LDFLAGS} "\$@"
	WRAPPER
	cat > "${T}/compilers/g++" <<-WRAPPER
	#!/bin/sh
	exec ${CXX} ${CPPFLAGS} ${CXXFLAGS} ${LDFLAGS} "\$@"
	WRAPPER
	chmod +x "${T}/compilers/gcc" "${T}/compilers/g++" || die
	ln -s gcc "${T}/compilers/cc" || die
	ln -s g++ "${T}/compilers/c++" || die
}

jasper_gradle() {
	# HotSpot adjusts its own core-dump filter at startup (Gentoo Java runtime policy).
	addpredict /proc/self/coredump_filter
	local -a properties=(
		-PjasperVersion=1.0.0 -Pjasper.aot=false
		-Pjasper.desktop.gtk3=$(usex gtk required off)
		-Pjasper.desktop.gtk4=$(usex gtk4 required off)
		-Pjasper.desktop.qt5=off -Pjasper.desktop.kde5=off
		-Pjasper.desktop.qt6=$(usex qt6 required off)
		-Pjasper.desktop.kde6=$(usex kde required off)
	)
	export JAVA_HOME="${WORKDIR}/jdk"
	export GRADLE_USER_HOME="${T}/gradle-home"
	export JASPER_MAVEN_REPO="${WORKDIR}/maven"
	local PATH="${T}/compilers:${JAVA_HOME}/bin:${PATH}"
	./gradlew --offline --no-daemon --no-build-cache --max-workers="$(makeopts_jobs)" \
		--console=plain -I "${FILESDIR}/offline.gradle" \
		-Dorg.gradle.java.installations.auto-download=false \
		-Dorg.gradle.java.installations.auto-detect=false \
		-Dorg.gradle.java.installations.paths="${JAVA_HOME}" \
		"${properties[@]}" "$@" || die "Offline Gradle failed"
}

src_compile() {
	jasper_gradle :jasper-app:packageApp
}

src_test() {
	jasper_gradle :jasper-app:verifyPackage
}

src_install() {
	local image="jasper-app/build/packaging/image/Jasper"
	insinto /opt/jasper-workstation
	# doins deliberately normalizes modes: cp -a preserves upstream runtime launchers and symlinks.
	mkdir -p "${ED}/opt/jasper-workstation" || die
	cp -a "${image}/." "${ED}/opt/jasper-workstation/" || die
	sh "${FILESDIR}/sanitize-rpath.sh" "${ED}/opt/jasper-workstation" || die "JCEF RPATH correction failed"
	dosym -r /opt/jasper-workstation/lib/bin/jasper /usr/bin/jasper
	make_desktop_entry /opt/jasper-workstation/bin/Jasper Jasper jasper-workstation \
		"System;TerminalEmulator;Network;RemoteAccess;" \
		"StartupWMClass=dev-jasper-app-Main"
	newicon packaging/icons/Jasper.png jasper-workstation.png
	dodoc LICENSE.md packaging/README.txt
	printf '%s\n' "Source commit: ${JASPER_COMMIT}" > "${T}/source-version" || die
	dodoc "${T}/source-version"
}
