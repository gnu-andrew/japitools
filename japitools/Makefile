all: jar

classes: src/net/wuffies/japi/*.java
	cd src && jikes -d .. -classpath $(BOOT_CLASSPATH):$(CLASSPATH) net/wuffies/japi/*.java jode/*.java jode/bytecode/*.java jode/util/*.java

jar: classes
	jar -0cvf share/java/japitools.jar net/wuffies/japi/*.class jode/*.class jode/bytecode/*.class jode/util/*.class

tarball: jar
	cd .. && tar zcvf japitools.tar.gz japi/

