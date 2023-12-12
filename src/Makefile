
CFLAGS=-I /usr/lib/jvm/java-11-openjdk-*/include/linux -I /usr/lib/jvm/java-11-openjdk-*/include/ -I ESPNOW_lib/src/Includes/ -O3 -g -fpermissive -fPIC -shared 
JARS=../pplib


JAVASRC=CarSim.java TargetFinderUnchanging.java TargetFinderExp.java AdvisorySounds.java AutoFocusParameter.java BufferedImageDisplay.java CannyEdgeDetector.java CannyEdgeDetectorOriginal.java CurvatureAnalyzer.java FrameCaptureJNI.java FrameGrabberThread.java FrameProcessor.java FrameProcessorTunableParameters.java GaussianKernel.java GnuplotWrapper.java HoughTransform.java ImageFileWriter.java IntervalTimer.java JavaStreamingAudioPlayerX.java JoystickControl.java LaneDetector.java LazyHslConvert.java Logfile.java NonmaxSuppression.java OriginalImage.java Pauser.java PidControl.java PixelGroup.java PriusVoice.java RemoteTcpCommandSocket.java RunningAveragePoint.java ScanZonePair.java GPSSerial.java Main.java SteeringLogic.java SteeringTestPulseGenerator.java SteeringWheelResolverCam.java TargetFinder.java TargetFinderLines.java TargetFinderRectangle2.java TargetFinderRectangle.java TargetFinderRed.java TargetFinderRoadColor.java TargetFinderRoundAndRed.java TargetFinderSymmetry.java TemplateDetectRGB.java Tracker.java TunableParameterList.java Average.java Complex.java FFT.java Geometry.java JamaLeastSquaresFit.java LeastSquares.java RunningAverage.java RunningLeastSquares.java RunningLeastSquaresSine.java RunningQuadraticLeastSquares.java Snippet.java GPSTrimCheat.java   ESPNowJNI.java 
JAVAOBJ=$(JAVASRC:%.java=./bin/%.class)

DEPEND_OBJS     := $(JAVASRC:.java=.u)



LIBS= 
FASTSRC=fast-C-src-2.1
DEPS = 
OBJ = FrameCaptureJNI.o 
TARGET=libFrameCaptureJNI.so

LIB=../pplib
CP=$(LIB)/Joystick.jar:$(LIB)/jna-3.2.4.jar:/home/jim/workspace/priuspilot/bin:$(LIB)/Jama-1.0.2.jar:$(LIB)/mbrola.jar:$(LIB)/freetts-jsapi10.jar:$(LIB)/jsapi.jar:$(LIB)/freetts.jar
        

#./bin/%.class: %.java
#	javac  -d ./bin/ -classpath $(CP) $<




all: libFrameCaptureJNI.so priuspilot.jar

%.o: %.c $(DEPS)
	gcc -c -o $@ $< $(CFLAGS)

%.o: %.cpp $(DEPS)
	g++ -c -o $@ $< $(CFLAGS)

%.h:  %.java
	javac $<
	(javah -jni FrameCaptureJNI)
	
ESPNOW_manager.o: ESPNOW_lib/src/ESPNOW_manager.cpp
	$(CC) $(CFLAGS) $(INCLUDES) -o $@ -c $^
ESPNOW_types.o: ESPNOW_lib/src/ESPNOW_types.cpp
	$(CC) $(CFLAGS) $(INCLUDES) -o $@ -c $^

$(TARGET): $(OBJ) ESPNOW_manager.o ESPNOW_types.o
	g++ -lrt -shared -o $@ $^ $(CFLAGS) $(LIBS) 

main: 	FrameCaptureJNI.cpp
	g++ -g -o $@ $<  -DUSE_MAIN

clean:
	rm -f *.o  core $(TARGET) ./*.class priuspilot.jar 
	rm -rf ./bin/

priuspilot.jar:   $(JAVASRC)
	javac -d ./bin/ -classpath $(CP) *.java 
	cd ./bin/ && jar cf ../$@ *.class 

start:	


# ----------------------------------------
# Include the dependency graph if it exist
# ----------------------------------------
MAKEFILE_DEPEND = makefile.dep
DEPEND  = $(wildcard $(MAKEFILE_DEPEND))
ifneq ($(DEPEND),)
        include $(MAKEFILE_DEPEND)
endif

