
CFLAGS=-I /usr/lib/jvm/java-11-openjdk-amd64/include/linux -I /usr/lib/jvm/java-11-openjdk-amd64/include/ -O3 -g -fpermissive -fPIC -shared 
JARS=../pplib


JAVASRC=AdvisorySounds.java AutoFocusParameter.java BufferedImageDisplay.java CannyEdgeDetector.java CannyEdgeDetectorOriginal.java CurvatureAnalyzer.java FrameCaptureJNI.java FrameGrabberThread.java FrameProcessor.java FrameProcessorTunableParameters.java GaussianKernel.java GnuplotWrapper.java HoughTransform.java ImageFileWriter.java IntervalTimer.java JavaStreamingAudioPlayerX.java JoystickControl.java LabJackJNI.java LaneDetector.java LazyHslConvert.java Logfile.java NonmaxSuppression.java OriginalImage.java Pauser.java PidControl.java PixelGroup.java PriusVoice.java RemoteTcpCommandSocket.java RunningAveragePoint.java ScanZonePair.java SerialCommandBus.java Silly.java SteeringLogic.java SteeringTestPulseGenerator.java SteeringWheelResolverCam.java TargetFinder.java TargetFinderLines.java TargetFinderRectangle2.java TargetFinderRectangle.java TargetFinderRed.java TargetFinderRoadColor.java TargetFinderRoundAndRed.java TargetFinderSymmetry.java TemplateDetectRGB.java Tracker.java TunableParameterList.java math/Average.java math/Complex.java math/FFT.java math/Geometry.java math/JamaLeastSquaresFit.java math/LeastSquares.java math/RunningAverage.java math/RunningLeastSquares.java math/RunningLeastSquaresSine.java math/RunningQuadraticLeastSquares.java math/Snippet.java 
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
	
$(TARGET): $(OBJ)
	g++ -lrt -shared -o $@ $^ $(CFLAGS) $(LIBS)


clean:
	rm -f *.o  core $(TARGET) ./*.class ./math/*.class priuspilot.jar 
	rm -rf ./bin/

priuspilot.jar:   $(JAVASRC)
	javac -d ./bin/ -classpath $(CP) *.java math/*.java
	cd ./bin/ && jar cf ../$@ *.class */*.class

start:	


# ----------------------------------------
# Include the dependency graph if it exist
# ----------------------------------------
MAKEFILE_DEPEND = makefile.dep
DEPEND  = $(wildcard $(MAKEFILE_DEPEND))
ifneq ($(DEPEND),)
        include $(MAKEFILE_DEPEND)
endif

