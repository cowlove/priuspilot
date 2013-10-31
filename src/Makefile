
CFLAGS=-I /usr/lib/jvm/java-6-openjdk/include/ -O3 -shared 
JARS=~/pplib

LIBS= 
FASTSRC=fast-C-src-2.1
DEPS = 
OBJ = FrameCaptureJNI.o 
TARGET=libFrameCaptureJNI.so

LIB=$(HOME)/pplib
CP=$(LIB)/Joystick.jar:$(LIB)/jna-3.2.4.jar:/home/jim/workspace/priuspilot/bin:$(LIB)/Jama-1.0.2.jar:$(LIB)/mbrola.jar:$(LIB)/freetts-jsapi10.jar:$(LIB)/jsapi.jar:$(LIB)/freetts.jar
        

all: $(TARGET) 
%.o: %.c $(DEPS)
	gcc -c -o $@ $< $(CFLAGS)

%.o: %.cpp $(DEPS)
	g++ -c -o $@ $< $(CFLAGS)

%.h:  %.java
	javac $<
	(javah -jni FrameCaptureJNI)
	
$(TARGET): $(OBJ)
	g++ -lrt -shared -o $@ $^ $(CFLAGS) $(LIBS)


clean:
	rm -f *.o  core $(TARGET) *.class math/*.class priuspilot.jar

priuspilot.jar:   
	javac -d ../bin/ -classpath $(CP) *.java math/*.java
	cd ../bin && jar cf $@ -C ../bin *.class */*.class
	
