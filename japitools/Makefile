all: src/net/wuffies/japi/*.java
	cd src && jikes -d .. net/wuffies/japi/*.java

jarball: all
	jar -0cvf ../japitools.jar *
