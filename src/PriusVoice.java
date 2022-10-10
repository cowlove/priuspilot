import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Vector;
import javax.speech.Central;
import javax.speech.Engine;
import javax.speech.EngineList;
import javax.speech.synthesis.Synthesizer;
import javax.speech.synthesis.SynthesizerModeDesc;
import javax.speech.synthesis.SynthesizerProperties;

/**
 * Copyright 2003 Sun Microsystems, Inc.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 */
import java.io.File;
import java.util.Locale;
import java.util.Vector;

import javax.speech.Central;
import javax.speech.Engine;
import javax.speech.EngineList;
import javax.speech.synthesis.Synthesizer;
import javax.speech.synthesis.SynthesizerModeDesc;
import javax.speech.synthesis.SynthesizerProperties;
import javax.speech.synthesis.Voice;
import java.util.ArrayList;

abstract class QueueThread<T> implements Runnable {
	ArrayList<T> queue = new ArrayList<T>();
	Thread t;
	boolean done = false;

	synchronized void enqueue(T item) { 
		synchronized(queue) { 
			queue.add(item);
			queue.notify();
		}
	}
	QueueThread() { 
		t = new Thread(this);
		t.start();
	}
	abstract void onItem(T item);
	public void run() { 
		while(!done) {
	        synchronized(queue) {
		        try {
		        	if (queue.size() == 0) 
		        		queue.wait();
		        } catch (InterruptedException e1) {
		        	// TODO Auto-generated catch block
		        	e1.printStackTrace();
		        }
		        if (queue.size() > 0) { 
		        	onItem(queue.remove(0));
		        }
	        }
		}
	}
}	        
	        
public class PriusVoice extends QueueThread<String> {
	void speak(String s) { this.enqueue(s); } 
	void onItem(String voice) { 
		System.out.println("VOICE: " + voice);
		try {
			Runtime.getRuntime().exec(new String[]{"/bin/sh","-c", "echo " + voice + " | espeak -v en-swedish-f"});
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
