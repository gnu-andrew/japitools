all: src/net/wuffies/japi/*.java
	cd src && jikes -d .. net/wuffies/japi/*.java

jar: all
	jar -0cvf japitools.jar net/

tarball: jar
	tar zcvf ../japitools.tar.gz *

